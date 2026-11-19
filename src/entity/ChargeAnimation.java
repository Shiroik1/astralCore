package entity;

import art.Palette;

import monster.SlimeMotion;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** Cursor-directed Charge uses cached rig poses and the existing terrain/status systems. */
public final class ChargeAnimation {
    public enum Phase {
        IDLE,
        ANTICIPATION,
        DASH,
        IMPACT,
        RECOVERY
    }

    public static final double ANTICIPATION = setting("anticipation", .10, .03, .3);
    public static final double DASH = setting("dash", .24, .08, .5);
    public static final double IMPACT = setting("impact", .15, .08, .4);
    public static final double RECOVERY = setting("recovery", .15, .10, .4);
    public static final double RANGE = setting("range", 8, 1, 12);
    public static final int SLOW = (int) setting("slowPercent", 25, 0, 80);
    public static final int SLOW_TICKS = (int) Math.round(setting("slowSeconds", 3, .1, 10) * 60);
    private final SwordCombat combat;
    private Player p;
    private Entity weapon;
    private Phase phase = Phase.IDLE;
    private double elapsed, dx, dy, distance, dashDuration, pause, ghostClock;
    private int startX, startY, area, nextGhost;
    private String facing;
    private boolean impacted, connected;
    private CombatHit hit;
    private final Rectangle body = new Rectangle();
    private final Ghost[] ghosts = {new Ghost(), new Ghost(), new Ghost()};
    private static final Shape ARC = arc();

    private static final class Ghost {
        BufferedImage image;
        int x, y;
        double age = 1;
    }

    /** Allocates a bounded set of cosmetic records for this player's Charge. */
    ChargeAnimation(SwordCombat combat) {
        this.combat = combat;
    }

    /** Reads bounded timing settings in seconds; malformed settings use defaults. */
    private static double setting(String name, double fallback, double min, double max) {
        try {
            double v = Double.parseDouble(System.getProperty("astral.charge." + name, ""));
            return Double.isFinite(v) ? Math.clamp(v, min, max) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Resolves the explicitly hovered enemy before cooldown or animation is committed. */
    static Monster target(Player p) {
        int index = p.currentInput.targetedMonsterIndex;
        Monster m = index >= 0 && index < p.gp.monster.length ? p.gp.monster[index] : null;
        return m != null
                        && m.alive
                        && !m.dying
                        && m.HP > 0
                        && SlimeMotion.distance(p, m) <= RANGE * p.gp.tileSize
                ? m
                : null;
    }

    /** Captures the validated target position once; movement never homes or uses old facing. */
    void start(Player player) {
        Monster target = target(player);
        if (target == null) return;
        clear();
        p = player;
        weapon = p.currentWeapon;
        area = p.gp.quests.areaVersion();
        double vx = SlimeMotion.centerX(target) - SlimeMotion.centerX(p);
        double vy = SlimeMotion.centerY(target) - SlimeMotion.centerY(p);
        double length = Math.hypot(vx, vy);
        distance = Math.min(RANGE * p.gp.tileSize, length);
        dx = length < 1 ? 1 : vx / length;
        dy = length < 1 ? 0 : vy / length;
        facing = SlimeMotion.facing(dx, dy);
        p.direction = facing;
        dashDuration = Math.max(1.0 / 60, DASH * distance / (RANGE * p.gp.tileSize));
        hit = new CombatHit("charge-impact").mastery(Entity.type_sword);
        enter(Phase.ANTICIPATION);
        dust(2);
    }

    /** Whether Charge currently owns the player's pose and movement. */
    public boolean active() {
        return phase != Phase.IDLE;
    }

    /** Allows normal attack buffering only in the last 0.12 seconds of recovery. */
    public boolean bufferOpen() {
        return phase == Phase.RECOVERY && elapsed >= RECOVERY - .12;
    }

    /** Exposes the current stage for diagnostics. */
    public Phase phase() {
        return phase;
    }

    /** Transitions from the actual legal position, never snapping the collision body. */
    private void enter(Phase next) {
        phase = next;
        elapsed = 0;
        startX = p.worldX;
        startY = p.worldY;
        if (next == Phase.DASH) dust(4);
        if (next == Phase.IMPACT) dust(2);
    }

    /** Advances seconds on the engine's fixed simulation clock, including local-only hit-stop. */
    void update(double dt) {
        if (!active()) return;
        if (p.currentWeapon != weapon
                || p.isDead
                || p.knockbackActive
                || p.hasStatusEffect("stun")
                || p.hasStatusEffect("frozen")
                || p.gp.quests.areaVersion() != area) {
            combat.cancel();
            return;
        }
        p.direction = facing;
        p.moving = false;
        p.currentInput.movementLocked = true;
        advanceEffects(dt);
        if (pause > 0) {
            pause = Math.max(0, pause - dt);
            return;
        }
        elapsed += dt;
        switch (phase) {
            case ANTICIPATION -> {
                if (elapsed >= ANTICIPATION) enter(distance < 1 ? Phase.IMPACT : Phase.DASH);
            }
            case DASH -> {
                double t = Math.min(1, elapsed / dashDuration);
                // Nearly uniform travel with a short launch acceleration; short casts remain quick.
                double fraction = t < .15 ? t * t / (.15 * 1.85) : (2 * t - .15) / 1.85;
                boolean clear = move(distance * fraction);
                if (!clear || t >= 1) enter(Phase.IMPACT);
            }
            case IMPACT -> {
                if (!impacted && elapsed >= IMPACT * .35) {
                    impacted = true;
                    impact();
                }
                if (elapsed >= IMPACT) enter(Phase.RECOVERY);
            }
            case RECOVERY -> {
                if (elapsed >= RECOVERY) {
                    boolean hitEnemy = connected;
                    combat.cancel();
                    if (hitEnemy) combat.chargeConnected();
                }
            }
            default -> {}
        }
    }

    /** Sweeps pixel steps against terrain and nearby enemies, stopping in legal melee range. */
    private boolean move(double travel) {
        int tx = startX + (int) Math.round(dx * travel),
                ty = startY + (int) Math.round(dy * travel);
        int ox = p.worldX, oy = p.worldY, steps = Math.max(Math.abs(tx - ox), Math.abs(ty - oy));
        Rectangle search =
                new Rectangle(
                        Math.min(ox, tx),
                        Math.min(oy, ty),
                        Math.abs(tx - ox) + 64,
                        Math.abs(ty - oy) + 64);
        var candidates = p.gp.collisionChecker.checkEntityMultiple(search, p.gp.monster);
        for (int i = 1; i <= steps; i++) {
            int x = ox + (tx - ox) * i / steps, y = oy + (ty - oy) * i / steps;
            body.setBounds(
                    x + p.solidArea.x, y + p.solidArea.y, p.solidArea.width, p.solidArea.height);
            if (!SlimeMotion.clear(p.gp, body)) return false;
            body.grow(3, 3);
            for (int index : candidates) {
                Monster m = p.gp.monster[index];
                if (m != null
                        && m.HP > 0
                        && body.intersects(
                                m.worldX + m.solidArea.x,
                                m.worldY + m.solidArea.y,
                                m.solidArea.width,
                                m.solidArea.height)) return false;
            }
            p.worldX = x;
            p.worldY = y;
        }
        if (steps > 0 && ghostClock >= .045) {
            capture();
            dust(1);
            ghostClock = 0;
        }
        return true;
    }

    /** Deals Basic Slash power and applies Slow/Rhythm once within the finishing slash. */
    private void impact() {
        double cx = SlimeMotion.centerX(p), cy = SlimeMotion.centerY(p), ts = p.gp.tileSize;
        Rectangle query =
                new Rectangle((int) (cx - ts), (int) (cy - ts), (int) (2 * ts), (int) (2 * ts));
        Shape zone =
                AffineTransform.getTranslateInstance(cx, cy)
                        .createTransformedShape(
                                AffineTransform.getRotateInstance(Math.atan2(dy, dx))
                                        .createTransformedShape(
                                                new Rectangle2D.Double(
                                                        0, -ts * .48, ts, ts * .96)));
        boolean success = false;
        for (int index : p.gp.collisionChecker.checkEntityMultiple(query, p.gp.monster)) {
            Monster m = p.gp.monster[index];
            if (m == null
                    || m.HP <= 0
                    || m.invincible
                    || hit.contains(m)
                    || !zone.intersects(SlimeMotion.body(m, m.worldX, m.worldY))
                    || !SlimeMotion.sight(p.gp, p, m)) continue;
            if (!p.damageMonsterOnce(
                    index, combat.basicAttackDamage(), false, facing, hit, CombatTuning.SWORD))
                continue;
            MageCombat.mark(m, "warriorSlow", SLOW_TICKS, SLOW);
            // Charge adds pressure but only a Basic Slash can trigger the five-stack Bleed cycle.
            MageCombat.mark(
                    m,
                    "warriorRhythm",
                    180,
                    Math.min(5, m.getStatusEffectValue("warriorRhythm") + 1));
            Particle.burst(m, Particle.Style.SPARK, Palette.GLOW, 8, 4, 16, 2.2f);
            m.flashing = true;
            m.flashCounter = 0;
            success = true;
        }
        if (success) {
            pause = .04;
            connected = true;
            if (p.isLocal) {
                p.gp.startScreenShake(3, 1);
                p.gp.playSE(5);
            }
        }
    }

    /** Emits restrained dust into the existing bounded particle pool. */
    private void dust(int count) {
        Particle.burst(p, Particle.Style.DUST, Palette.PATH, count, 4, 15, .7f);
    }

    /** Retains the actual cached dash frame at a previously occupied world position. */
    private void capture() {
        var frames = p.sprites.get("charge_" + facing);
        if (frames == null) return;
        Ghost g = ghosts[nextGhost++ % ghosts.length];
        g.image = frames.frames[poseIndex()];
        g.x = p.worldX;
        g.y = p.worldY;
        g.age = 0;
    }

    /** Ages only three reusable visual records. */
    private void advanceEffects(double dt) {
        ghostClock += dt;
        for (Ghost g : ghosts) g.age += dt;
    }

    /** Selects dedicated anticipation, dash, slash and recovery pose samples. */
    public int poseIndex() {
        return switch (phase) {
            case ANTICIPATION -> (int) Math.round(Math.min(1, elapsed / ANTICIPATION) * 5);
            case DASH -> 6 + (int) Math.round(Math.min(1, elapsed / dashDuration) * 7);
            case IMPACT -> 14 + (int) Math.round(Math.min(1, elapsed / IMPACT) * 9);
            case RECOVERY -> 24 + (int) Math.round(Math.min(1, elapsed / RECOVERY) * 7);
            default -> -1;
        };
    }

    /** Releases temporary visual state; cooldown remains committed in SwordCombat. */
    public void clear() {
        phase = Phase.IDLE;
        elapsed = pause = ghostClock = 0;
        impacted = connected = false;
        nextGhost = 0;
        hit = null;
        for (Ghost g : ghosts) {
            g.image = null;
            g.age = 1;
        }
    }

    /** Draws appearance-matched afterimages and short physical speed lines behind the player. */
    public void drawBehind(Graphics2D graphics, int x, int y) {
        if (!active()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        for (Ghost ghost : ghosts)
            if (ghost.image != null && ghost.age < .18) {
                g.setComposite(
                        AlphaComposite.SrcOver.derive((float) (.45 * (1 - ghost.age / .18))));
                g.drawImage(ghost.image, x + ghost.x - p.worldX, y + ghost.y - p.worldY, null);
            }
        if (phase == Phase.DASH) {
            Player camera = p.gp.localPlayer();
            g.setComposite(AlphaComposite.SrcOver.derive(.24f));
            g.translate(
                    SlimeMotion.centerX(p) - camera.worldX + camera.screenX,
                    SlimeMotion.centerY(p) - camera.worldY + camera.screenY - 8);
            g.rotate(Math.atan2(dy, dx));
            g.setColor(Palette.PLASTER);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(-28, -13, -12, -13);
            g.drawLine(-34, 12, -17, 12);
        }
        g.dispose();
    }

    /** Draws one tapered finishing arc in the real movement direction. */
    public void drawImpact(Graphics2D graphics) {
        if (phase != Phase.IMPACT || elapsed < IMPACT * .25) return;
        Player camera = p.gp.localPlayer();
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(
                SlimeMotion.centerX(p) - camera.worldX + camera.screenX,
                SlimeMotion.centerY(p) - camera.worldY + camera.screenY);
        g.rotate(Math.atan2(dy, dx));
        g.scale(p.gp.tileSize, p.gp.tileSize);
        g.setComposite(
                AlphaComposite.SrcOver.derive((float) Math.max(0, 1 - elapsed / IMPACT * .7)));
        g.setColor(Palette.WOOD_LIGHT);
        g.fill(ARC);
        g.scale(.93, .90);
        g.setColor(Palette.PLASTER);
        g.fill(ARC);
        g.dispose();
    }

    /** Creates the immutable tapered slash geometry once. */
    private static Shape arc() {
        Path2D.Float s = new Path2D.Float();
        s.moveTo(.05, -.58);
        s.curveTo(1.25, -.40, 1.25, .4, .05, .58);
        s.curveTo(.72, .25, .72, -.25, .05, -.58);
        s.closePath();
        return s;
    }

    /** Adds presentation only to existing network snapshots. */
    public void snapshot(net.PlayerState s) {
        s.chargePhase = phase.ordinal();
        s.chargeElapsed = elapsed;
        s.chargeHeading = Math.atan2(dy, dx);
        s.chargeDuration = dashDuration;
    }

    /** Mirrors host poses without client movement or status application. */
    public void sync(Player player, net.PlayerState s) {
        if (s.chargePhase <= 0 || s.chargePhase >= Phase.values().length) {
            clear();
            return;
        }
        p = player;
        phase = Phase.values()[s.chargePhase];
        elapsed = s.chargeElapsed;
        dashDuration = Math.max(.01, s.chargeDuration);
        dx = Math.cos(s.chargeHeading);
        dy = Math.sin(s.chargeHeading);
        facing = SlimeMotion.facing(dx, dy);
        if (phase == Phase.DASH && ghostClock >= .045) {
            capture();
            ghostClock = 0;
        }
    }

    /** Advances replica cosmetic lifetimes only. */
    public void advanceReplica() {
        advanceEffects(1.0 / 60);
    }
}
