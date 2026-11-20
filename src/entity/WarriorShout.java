package entity;

import art.Palette;

import monster.SlimeMotion;

import java.awt.*;
import java.awt.geom.Ellipse2D;

/** One shared, fixed-clock shout sequence; theme and radius are the only visual variations. */
public final class WarriorShout {
    public static final double PREPARATION = setting("preparation", .15, .05, .6);
    public static final double RELEASE = setting("release", .30, .05, .6);
    public static final double RECOVERY = setting("recovery", .15, .05, .6);
    public static final double WARCRY_RADIUS = setting("warcryRadius", 5, 1, 8);
    public static final double TAUNT_RADIUS = setting("tauntRadius", 4, 1, 8);
    private static final Color BRONZE = new Color(0xB78076), RETALIATE_ACCENT = new Color(0xF1E8DE);
    private static final Color CRIMSON = new Color(0xB96568), CRIMSON_ACCENT = new Color(0xD98985);
    public static final double RING_HOLD = setting("ringHold", .08, 0, .5);
    public static final double RING_FADE = setting("ringFade", .36, .05, .8);
    private static final double RING_LIFE = RELEASE + RING_HOLD + RING_FADE;
    private static final Color RING_WHITE = new Color(0xFFF8EB);
    private final Player p;
    private final SwordCombat combat;
    private final Ellipse2D.Double ring = new Ellipse2D.Double();
    private double elapsed, ringAge = RING_LIFE;
    private int skill, area;
    private double x, y;
    private Entity weapon;
    private String facing;
    private boolean active, released;

    WarriorShout(Player p, SwordCombat combat) {
        this.p = p;
        this.combat = combat;
    }

    /** Reads bounded durations in seconds once, outside the render loop. */
    private static double setting(String key, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(System.getProperty("astral.shout." + key, ""));
            return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Locks the current facing; the caller already committed cost and cooldown once. */
    void start(int id) {
        skill = id;
        elapsed = 0;
        released = false;
        active = true;
        ringAge = RING_LIFE;
        weapon = p.currentWeapon;
        facing = p.direction;
        area = p.gp.quests.areaVersion();
        p.attacking = true;
        p.animState = "attack";
        p.moving = false;
        p.currentInput.movementLocked = true;
    }

    public boolean active() {
        return active;
    }

    /** Advances the same phases for both skills; clients mirror presentation only. */
    void tick(double dt) {
        ringAge = Math.min(lifetime(), ringAge + dt);
        if (!active) return;
        if (p.isDead
                || p.currentInput.gameplaySuppressed
                || p.inDialogue
                || p.merchantOpen
                || p.currentWeapon != weapon
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
        elapsed += dt;
        if (!released && elapsed >= PREPARATION) {
            released = true;
            x = SlimeMotion.centerX(p);
            y = p.sortY();
            ringAge = 0;
            combat.releaseShout(skill);
            Particle.burst(p, Particle.Style.MOTE, accent(), 3, 3, 14, .5f);
        }
        if (elapsed >= PREPARATION + RELEASE + RECOVERY) {
            active = false;
            p.attacking = false;
            p.animState = "idle";
            p.spriteNum = 1;
            p.currentInput.movementLocked = false;
        }
    }

    /** Cancellation removes unreleased visuals; an already-applied status remains authoritative. */
    public void clear() {
        active = false;
        ringAge = RING_LIFE;
    }

    /** Selects one of 24 appearance-cached shared shout poses. */
    public int poseIndex() {
        if (!active) return -1;
        if (elapsed < PREPARATION) return Math.min(7, (int) (elapsed / PREPARATION * 8));
        if (elapsed < PREPARATION + RELEASE)
            return 8 + Math.min(7, (int) ((elapsed - PREPARATION) / RELEASE * 8));
        return 16 + Math.min(7, (int) ((elapsed - PREPARATION - RELEASE) / RECOVERY * 8));
    }

    /** One radius setting drives both the visual pulse and the authoritative release query. */
    public static double radiusTiles(int id) {
        return id == 5 ? 1.75 : id == 1 ? WARCRY_RADIUS : TAUNT_RADIUS;
    }

    /** Retaliate shares all ring geometry/easing but has a shorter, smaller visual pulse. */
    private double expansion() {
        return skill == 5 ? .23 : RELEASE;
    }

    private double linger() {
        return skill == 5 ? .10 : RING_HOLD;
    }

    private double fadeSeconds() {
        return skill == 5 ? .18 : RING_FADE;
    }

    private double lifetime() {
        return expansion() + linger() + fadeSeconds();
    }

    private Color main() {
        return skill == 5 ? BRONZE : skill == 1 ? Palette.ROOF_THATCH : CRIMSON;
    }

    private Color accent() {
        return skill == 5 ? RETALIATE_ACCENT : skill == 1 ? Palette.GLOW : CRIMSON_ACCENT;
    }

    /** Renders an anchored, non-damaging pulse under the body and short head accents. */
    public void drawGround(Graphics2D graphics) {
        if (!active && ringAge >= lifetime()) return;
        Player camera = p.gp.localPlayer();
        if (camera == null) return;
        boolean preparing = active && !released;
        double cx = preparing ? SlimeMotion.centerX(p) : x;
        double cy = preparing ? p.sortY() : y;
        double t = preparing ? 0 : Math.min(1, ringAge / expansion());
        double radius =
                p.gp.tileSize * (.35 + (radiusTiles(skill) - .35) * (1 - Math.pow(1 - t, 3)));
        double sx = cx - camera.worldX + camera.screenX, sy = cy - camera.worldY + camera.screenY;
        if (sx + radius < 0
                || sy + radius < 0
                || sx - radius > p.gp.screenWidth
                || sy - radius > p.gp.screenHeight) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double fade = Math.clamp((ringAge - expansion() - linger()) / fadeSeconds(), 0, 1);
        // Hold the completed ring briefly, then settle smoothly to zero opacity.
        float alpha = preparing ? .25f : (float) (.85 * (1 - fade * fade * (3 - 2 * fade)));
        ring.setFrame(sx - radius, sy - radius * .72, radius * 2, radius * 1.44);
        float width = (float) (3.5 - 2 * t);
        g.setColor(RING_WHITE);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha * .035f));
        g.setStroke(new BasicStroke(width + 13));
        g.draw(ring);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha * .075f));
        g.setStroke(new BasicStroke(width + 8));
        g.draw(ring);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha * .14f));
        g.setStroke(new BasicStroke(width + 4));
        g.draw(ring);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha * .32f));
        g.setStroke(new BasicStroke(width + 1.5f));
        g.draw(ring);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha * .8f));
        g.setColor(main());
        g.setStroke(new BasicStroke(width));
        g.draw(ring);
        if (!preparing && ringAge < .12) {
            g.setColor(accent());
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double hx = p.worldX - camera.worldX + camera.screenX + p.gp.tileSize * .5;
            double hy = p.worldY - camera.worldY + camera.screenY - 13;
            for (int i = -1; i <= 1; i++)
                g.drawLine((int) (hx + i * 9), (int) hy, (int) (hx + i * 13), (int) (hy - 6));
        }
        g.dispose();
    }

    /** Serializes only visuals; gameplay effects are never replayed by clients. */
    public void snapshot(net.PlayerState s) {
        s.shoutActive = active;
        s.shoutReleased = released;
        s.shoutSkill = skill;
        s.shoutElapsed = elapsed;
        s.shoutRingAge = ringAge;
        s.shoutX = x;
        s.shoutY = y;
    }

    public void sync(net.PlayerState s) {
        active = s.shoutActive;
        released = s.shoutReleased;
        skill = s.shoutSkill;
        elapsed = s.shoutElapsed;
        ringAge = s.shoutRingAge;
        x = s.shoutX;
        y = s.shoutY;
    }

    /** Cosmetic fade between network snapshots; never invokes gameplay release. */
    public void replicaTick() {
        ringAge = Math.min(lifetime(), ringAge + 1.0 / 60);
    }
}
