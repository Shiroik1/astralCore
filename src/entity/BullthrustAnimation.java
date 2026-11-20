package entity;

import art.Palette;

import monster.SlimeMotion;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Bullthrust presentation and collision-safe travel, driven by the existing simulation clock. */
public final class BullthrustAnimation {
    public enum Phase {
        IDLE,
        BACKSTEP,
        CHARGING,
        RELEASE,
        RECOVERY
    }

    public static final double BACKSTEP_SECONDS = setting("backstepSeconds", .18, .05, 1);
    public static final double CHARGE_SECONDS = setting("chargeSeconds", 1.5, .1, 5);
    public static final double HOLD_SECONDS = setting("holdSeconds", 4, 0, 10);
    public static final double RELEASE_SECONDS = setting("releaseSeconds", .12, .06, 1);
    public static final double RECOVERY_SECONDS = setting("recoverySeconds", .25, .1, 1);
    public static final double BACKSTEP_TILES = setting("backstepTiles", .75, 0, 1);
    public static final double RANGE_TILES = setting("rangeTiles", 10, 1, 16);
    private static final double TRAIL_LIFE = .18;
    private static final Shape CRESCENT = crescent();
    private static final Shape FINISH = finishShape();
    private final SwordCombat combat;
    private Player p;
    private Entity weapon;
    private Phase phase = Phase.IDLE;
    private double elapsed, charged, fxClock, effectTimer;
    private int slot, startX, startY, areaVersion, arcCount, ghostCount;
    private double dx, dy, cursorX, cursorY, releaseDistance;
    private boolean queuedRelease, blocked, fullCue;
    private String facing;
    private final Trail[] trails = new Trail[6];
    private final Ghost[] ghosts = new Ghost[2];
    private final Rectangle body = new Rectangle(), damage = new Rectangle();

    /** A bounded effect record stores world positions, never damaging entities. */
    private static final class Trail {
        double x, y, age, strength;
        int side;
        boolean finish;
    }

    /** Keeps references to cached appearance frames rather than copying images. */
    private static final class Ghost {
        int x, y;
        double age;
        BufferedImage image;
    }

    /** Initializes fixed-size effect storage once per player. */
    BullthrustAnimation(SwordCombat combat) {
        this.combat = combat;
        for (int i = 0; i < trails.length; i++) {
            trails[i] = new Trail();
            trails[i].age = 1;
        }
        for (int i = 0; i < ghosts.length; i++) {
            ghosts[i] = new Ghost();
            ghosts[i].age = 1;
        }
    }

    /** Reads finite, bounded timing settings without changing any other Warrior ability. */
    private static double setting(String name, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(System.getProperty("astral.bullthrust." + name, ""));
            return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Locks facing and appearance after SwordCombat validates and pays for the activation. */
    void start(Player player, int heldSlot) {
        clear();
        p = player;
        weapon = p.currentWeapon;
        slot = heldSlot;
        cursorX = p.currentInput.aimWorldX - p.solidArea.getCenterX();
        cursorY = p.currentInput.aimWorldY - p.solidArea.getCenterY();
        aim(cursorX - p.worldX, cursorY - p.worldY);
        p.direction = facing;
        areaVersion = p.gp.quests.areaVersion();
        enter(Phase.BACKSTEP);
        dust(2);
    }

    /** Normalizes arbitrary cursor angles while selecting a compatible four-direction body pose. */
    private void aim(double vx, double vy) {
        double distance = Math.hypot(vx, vy);
        if (distance < .001) {
            vx = p.direction.equals("right") ? 1 : p.direction.equals("left") ? -1 : 0;
            vy = p.direction.equals("down") ? 1 : p.direction.equals("up") ? -1 : 0;
            distance = 1;
        }
        dx = vx / distance;
        dy = vy / distance;
        facing = SlimeMotion.facing(dx, dy);
    }

    /** Whether the committed sequence owns player movement and posing. */
    public boolean active() {
        return phase != Phase.IDLE;
    }

    /** Whether to show the near-player and existing HUD charge meters. */
    public boolean charging() {
        return phase == Phase.CHARGING;
    }

    /** Continuous charge factor used by both the existing damage formula and visuals. */
    public double charge() {
        return charged;
    }

    /** Exposes the current stage to diagnostics and snapshot presentation. */
    public Phase phase() {
        return phase;
    }

    /** Starts a phase from the actual current position, including a blocked backstep. */
    private void enter(Phase next) {
        phase = next;
        elapsed = effectTimer = 0;
        startX = p.worldX;
        startY = p.worldY;
        blocked = false;
        if (next == Phase.RELEASE) {
            double vx = cursorX - p.worldX, vy = cursorY - p.worldY;
            releaseDistance = RANGE_TILES * p.gp.tileSize;
            if (Math.hypot(vx, vy) > .001) aim(vx, vy);
            dust(5);
            arc(false);
        }
        if (next == Phase.RECOVERY) {
            dust(3);
            arc(true);
        }
    }

    /** Advances seconds on the fixed 60 Hz game simulation; rendering never changes state. */
    void update(double dt) {
        if (!active()) return;
        if (p.currentWeapon != weapon
                || p.isDead
                || p.knockbackActive
                || p.gp.quests.areaVersion() != areaVersion
                || p.hasStatusEffect("stun")
                || p.hasStatusEffect("frozen")) {
            combat.cancel();
            return;
        }
        p.direction = facing;
        p.moving = false;
        p.currentInput.movementLocked = true;
        elapsed += dt;
        fxClock += dt;
        effectTimer += dt;
        for (Trail trail : trails) trail.age += dt;
        for (Ghost ghost : ghosts) ghost.age += dt;
        if (!p.currentInput.skillHeld[slot]) queuedRelease = true;
        switch (phase) {
            case BACKSTEP -> {
                double t = Math.min(1, elapsed / BACKSTEP_SECONDS);
                if (!blocked) travel(-BACKSTEP_TILES * p.gp.tileSize * smooth(t), false);
                if (t >= 1) {
                    enter(Phase.CHARGING);
                    if (queuedRelease) enter(Phase.RELEASE);
                }
            }
            case CHARGING -> {
                charged = Math.min(1, elapsed / CHARGE_SECONDS);
                if (charged >= 1 && !fullCue) {
                    fullCue = true;
                    Particle.burst(p, Particle.Style.SPARK, Palette.WOOD_LIGHT, 3, 3, 10, .6f);
                }
                if (effectTimer >= .30 - charged * .12) {
                    dust(1);
                    effectTimer = 0;
                }
                if (queuedRelease || elapsed >= CHARGE_SECONDS + HOLD_SECONDS) enter(Phase.RELEASE);
            }
            case RELEASE -> {
                double t = Math.min(1, elapsed / RELEASE_SECONDS);
                int oldX = p.worldX, oldY = p.worldY;
                travel(releaseDistance * smooth(t), true);
                if (oldX != p.worldX || oldY != p.worldY) {
                    if (effectTimer >= RELEASE_SECONDS * (.26 - .08 * charged)) {
                        arc(t > .82);
                        effectTimer = 0;
                    }
                    if (charged >= .999 && ghostCount < 2 && t >= .22 + .26 * ghostCount) ghost();
                }
                if (blocked || t >= 1) enter(Phase.RECOVERY);
            }
            case RECOVERY -> {
                if (elapsed >= RECOVERY_SECONDS) combat.cancel();
            }
            default -> {}
        }
    }

    /** Smooth acceleration/deceleration covers the configured distance exactly. */
    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Uses small swept steps and a once-per-tick nearby candidate query for contact and damage. */
    private void travel(double distance, boolean attacking) {
        int tx = startX + (int) Math.round(dx * distance),
                ty = startY + (int) Math.round(dy * distance);
        int x = p.worldX, y = p.worldY;
        int steps = Math.max(Math.abs(tx - x), Math.abs(ty - y));
        Rectangle nearby =
                new Rectangle(
                        Math.min(x, tx),
                        Math.min(y, ty),
                        Math.abs(tx - x) + p.gp.tileSize,
                        Math.abs(ty - y) + p.gp.tileSize);
        nearby.grow(p.gp.tileSize, p.gp.tileSize);
        List<Integer> candidates = p.gp.collisionChecker.checkEntityMultiple(nearby, p.gp.monster);
        for (int n = 0; n <= steps; n++) {
            int nx = steps == 0 ? x : x + (tx - x) * n / steps;
            int ny = steps == 0 ? y : y + (ty - y) * n / steps;
            body.setBounds(
                    nx + p.solidArea.x, ny + p.solidArea.y, p.solidArea.width, p.solidArea.height);
            if (!SlimeMotion.clear(p.gp, body)) {
                blocked = true;
                break;
            }
            // Sweep every legal step; release passes through enemies without extra hit events.
            if (attacking && p.canResolveWorldActions()) {
                damage.setBounds(
                        p.worldX + p.solidArea.x,
                        p.worldY + p.solidArea.y,
                        p.solidArea.width,
                        p.solidArea.height);
                damage.grow(p.gp.tileSize / 3, p.gp.tileSize / 3);
                damage.translate(
                        (int) Math.round(dx * p.gp.tileSize / 2),
                        (int) Math.round(dy * p.gp.tileSize / 2));
                combat.strike(damage, candidates);
            }
            // Backstep remains physical; only the attacking dash ignores enemy bodies.
            if (!attacking)
                for (int index : candidates) {
                    Monster m = p.gp.monster[index];
                    if (m != null
                            && m.alive
                            && !m.dying
                            && m.HP > 0
                            && body.intersects(
                                    m.worldX + m.solidArea.x,
                                    m.worldY + m.solidArea.y,
                                    m.solidArea.width,
                                    m.solidArea.height)) {
                        blocked = true;
                        break;
                    }
                }
            if (blocked) break;
            p.worldX = nx;
            p.worldY = ny;
        }
    }

    /** Emits only a few shared-pool ground puffs per stage. */
    private void dust(int count) {
        for (int i = 0; i < count; i++)
            Particle.emit(
                    p,
                    Particle.Style.DUST,
                    Palette.PATH,
                    5,
                    18,
                    (float) ((i % 2 == 0 ? -.6f : .6f) - dx * .5f),
                    (float) (-dy * .4f),
                    i % 2 == 0 ? -7 : 7,
                    0);
    }

    /** Adds a world-anchored tapered arc along the real trajectory. */
    private void arc(boolean finish) {
        Trail trail = trails[arcCount % trails.length];
        trail.x = SlimeMotion.centerX(p) + dx * p.gp.tileSize * .55;
        trail.y = SlimeMotion.centerY(p) + dy * p.gp.tileSize * .55;
        trail.age = 0;
        trail.strength = charged;
        trail.side = arcCount++ % 2 == 0 ? 1 : -1;
        trail.finish = finish;
    }

    /** Captures the cached current release pose at its actual world position. */
    private void ghost() {
        SpriteAnimation frames = p.sprites.get("bullthrust_" + facing);
        if (frames == null) return;
        Ghost ghost = ghosts[ghostCount++];
        ghost.image = frames.frames[poseIndex()];
        ghost.x = p.worldX;
        ghost.y = p.worldY;
        ghost.age = 0;
    }

    /** Adds local physical impact feedback only after accepted damage. */
    void impact(Monster enemy) {
        Particle.burst(
                enemy, Particle.Style.SPARK, Palette.PLASTER, 3 + (int) (charged * 4), 4, 12, 2);
        if (charged >= .67) p.gp.startScreenShake(4, 2);
    }

    /** Chooses a cached rig pose: backstep 0–8, charge 9–33, release 34–50, recovery 51–63. */
    public int poseIndex() {
        return switch (phase) {
            case BACKSTEP -> (int) Math.round(Math.min(1, elapsed / BACKSTEP_SECONDS) * 8);
            case CHARGING -> 9 + (int) Math.round(charged * 5) * 4 + (int) (fxClock * 5) % 4;
            case RELEASE -> 34 + (int) Math.round(Math.min(1, elapsed / RELEASE_SECONDS) * 16);
            case RECOVERY -> 51 + (int) Math.round(Math.min(1, elapsed / RECOVERY_SECONDS) * 12);
            default -> -1;
        };
    }

    /** Includes only presentation scalars in the existing authoritative player snapshot. */
    public void snapshot(net.PlayerState state) {
        state.bullPhase = phase.ordinal();
        state.bullElapsed = elapsed;
        state.bullCharge = charged;
        state.bullHeading = Math.atan2(dy, dx);
    }

    /** Mirrors host presentation without spending resources, moving, or applying damage. */
    public void sync(Player player, net.PlayerState state) {
        if (state.bullPhase <= 0 || state.bullPhase >= Phase.values().length) {
            clear();
            return;
        }
        p = player;
        Phase next = Phase.values()[state.bullPhase];
        if (phase == Phase.IDLE || next == Phase.BACKSTEP && phase != next) clear();
        phase = next;
        elapsed = Math.max(0, state.bullElapsed);
        charged = Math.clamp(state.bullCharge, 0, 1);
        facing = p.direction;
        dx = Math.cos(state.bullHeading);
        dy = Math.sin(state.bullHeading);
        if (phase == Phase.RELEASE && (startX != p.worldX || startY != p.worldY)) {
            arc(elapsed / RELEASE_SECONDS > .82);
            if (charged >= .999 && ghostCount < 2) ghost();
        }
        startX = p.worldX;
        startY = p.worldY;
    }

    /** Ages client-side cosmetic records on the same simulation cadence as other replicas. */
    public void advanceReplica() {
        if (!active()) return;
        fxClock += 1.0 / 60;
        for (Trail trail : trails) trail.age += 1.0 / 60;
        for (Ghost ghost : ghosts) ghost.age += 1.0 / 60;
    }

    /** Clears all transient animation state without refunding committed Rage or cooldown. */
    public void clear() {
        phase = Phase.IDLE;
        elapsed = charged = fxClock = effectTimer = 0;
        queuedRelease = blocked = fullCue = false;
        arcCount = ghostCount = 0;
        for (Trail trail : trails) trail.age = 1;
        for (Ghost ghost : ghosts) {
            ghost.age = 1;
            ghost.image = null;
        }
    }

    /** Draws bounded fading effects behind the real character, with its existing projection. */
    public void drawBehind(Graphics2D graphics, int drawX, int drawY) {
        if (!active() || p == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Ghost ghost : ghosts)
            if (ghost.image != null && ghost.age < .16) {
                g.setComposite(
                        AlphaComposite.SrcOver.derive((float) (.20 * (1 - ghost.age / .16))));
                g.drawImage(
                        ghost.image, drawX + ghost.x - p.worldX, drawY + ghost.y - p.worldY, null);
            }
        Player camera = p.gp.localPlayer();
        if (phase == Phase.BACKSTEP || phase == Phase.CHARGING && charged > .33) {
            Graphics2D v = (Graphics2D) g.create();
            v.setComposite(
                    AlphaComposite.SrcOver.derive(
                            phase == Phase.BACKSTEP ? .18f : (float) (.12 + .15 * charged)));
            v.translate(
                    SlimeMotion.centerX(p) - camera.worldX + camera.screenX,
                    SlimeMotion.centerY(p) - camera.worldY + camera.screenY - 6);
            v.rotate(Math.atan2(dy, dx));
            v.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            v.setColor(Palette.PLASTER);
            v.drawLine(-18, -16, -6, -16);
            v.drawLine(-22, 16, -12, 16);
            v.dispose();
        }
        for (Trail trail : trails)
            if (trail.age < TRAIL_LIFE) {
                Graphics2D v = (Graphics2D) g.create();
                v.setComposite(
                        AlphaComposite.SrcOver.derive(
                                (float) (.70 * (1 - trail.age / TRAIL_LIFE))));
                v.translate(
                        trail.x - camera.worldX + camera.screenX,
                        trail.y - camera.worldY + camera.screenY - 6);
                v.rotate(Math.atan2(dy, dx));
                double size = p.gp.tileSize * (.70 + .35 * trail.strength);
                v.scale(size, size * trail.side);
                v.setColor(Palette.WOOD_LIGHT);
                v.fill(trail.finish ? FINISH : CRESCENT);
                v.scale(.90, .85);
                v.setColor(Palette.PLASTER);
                v.fill(trail.finish ? FINISH : CRESCENT);
                v.dispose();
            }
        g.dispose();
    }

    /** Draws a compact, player-following physical charge bar without a separate UI window. */
    public void drawMeter(Graphics2D graphics) {
        if (!charging() || p == null) return;
        Player camera = p.gp.localPlayer();
        int x = p.worldX - camera.worldX + camera.screenX + p.gp.tileSize / 2 - 23;
        int y = p.worldY - camera.worldY + camera.screenY + p.solidArea.y + p.solidArea.height + 7;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(new Color(28, 25, 29, 210));
        g.fillRoundRect(x, y, 46, 7, 4, 4);
        g.setColor(Palette.WOOD_LIGHT);
        g.fillRoundRect(x + 2, y + 2, (int) Math.round(42 * charged), 3, 2, 2);
        g.setColor(Palette.PLASTER);
        g.drawRoundRect(x, y, 46, 7, 4, 4);
        g.dispose();
    }

    /** Creates a tapered crescent once, in normalized forward/lateral coordinates. */
    private static Shape crescent() {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(-.65, -.60);
        path.curveTo(.95, -.68, 1.05, .55, -.55, .62);
        path.curveTo(.48, .30, .50, -.37, -.65, -.60);
        path.closePath();
        return path;
    }

    /** Creates the narrow finishing thrust silhouette once. */
    private static Shape finishShape() {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(-.8, 0);
        path.lineTo(.15, -.09);
        path.lineTo(1.1, 0);
        path.lineTo(.15, .09);
        path.closePath();
        return path;
    }
}
