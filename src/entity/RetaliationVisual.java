package entity;

import art.Palette;

import monster.SlimeMotion;

import java.awt.*;
import java.awt.geom.*;

/** Bounded cosmetic reaction and charge glyph; never performs damage or player movement. */
public final class RetaliationVisual {
    public static final double SWING = .18, TAIL = .12, GLYPH_FADE = .20;
    private static final Color BRONZE = new Color(0xB78076), IVORY = new Color(0xF1E8DE);
    private final Player p;
    private final Ellipse2D.Double circle = new Ellipse2D.Double();
    private final Arc2D.Double arc = new Arc2D.Double();
    private double reaction = SWING + TAIL, glyphAlpha, transition = 1, clock;
    private int charges, oldCharges, serial;
    private boolean pending;

    RetaliationVisual(Player player) {
        p = player;
    }

    /** Queues at most one extra visual; every gameplay hit is still resolved immediately. */
    void trigger() {
        serial++;
        if (reaction < SWING) pending = true;
        else reaction = 0;
    }

    /** Advances cosmetics independently of the underlying attack/movement state. */
    public void tick(double dt) {
        if (p.isDead || !p.sword.equipped()) {
            clear();
            return;
        }
        clock += dt;
        reaction = Math.min(SWING + TAIL, reaction + dt);
        if (reaction >= SWING && pending) {
            pending = false;
            reaction = 0;
        }
        int next = Math.max(0, p.getStatusEffectValue("warriorRetaliation"));
        if (next != charges) {
            oldCharges = charges;
            charges = next;
            transition = 0;
        }
        transition = Math.min(1, transition + dt / .15);
        glyphAlpha = Math.clamp(glyphAlpha + (charges > 0 ? dt / .10 : -dt / GLYPH_FADE), 0, 1);
    }

    /** Equipment/death cleanup never cancels normal combat or modifies status gameplay. */
    public void clear() {
        reaction = SWING + TAIL;
        pending = false;
        glyphAlpha = 0;
        charges = oldCharges = 0;
    }

    /** A short full-body presentation override; underlying actions continue advancing normally. */
    public int poseIndex() {
        return reaction < SWING ? Math.min(15, (int) (reaction / SWING * 16)) : -1;
    }

    /** Draws a following ground glyph with smoothly dimming charge segments. */
    public void drawGround(Graphics2D graphics) {
        if (glyphAlpha <= 0) return;
        Player camera = p.gp.localPlayer();
        if (camera == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double x = SlimeMotion.centerX(p) - camera.worldX + camera.screenX;
        double y = p.sortY() - camera.worldY + camera.screenY;
        double radius = p.gp.tileSize * .53;
        float opacity = (float) (glyphAlpha * (.58 + .04 * Math.sin(clock * 2)));
        g.setColor(BRONZE);
        g.setComposite(AlphaComposite.SrcOver.derive(opacity * .55f));
        circle.setFrame(x - radius, y - radius * .62, radius * 2, radius * 1.24);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(circle);
        circle.setFrame(x - radius * .70, y - radius * .43, radius * 1.40, radius * .86);
        g.draw(circle);
        double step = 360.0 / SwordCombat.RETALIATION_CHARGES;
        for (int i = 0; i < SwordCombat.RETALIATION_CHARGES; i++) {
            float lit = i < charges ? 1 : i < oldCharges ? (float) (1 - transition) : .10f;
            if (i >= charges && i < oldCharges)
                lit = Math.min(1, lit + (float) Math.sin(transition * Math.PI) * .25f);
            arc.setArc(
                    x - radius * .86,
                    y - radius * .53,
                    radius * 1.72,
                    radius * 1.06,
                    clock * 6 + i * step + 10,
                    Math.max(8, step - 22),
                    Arc2D.OPEN);
            g.setColor(IVORY);
            g.setComposite(AlphaComposite.SrcOver.derive(opacity * lit * .18f));
            g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arc);
            g.setColor(i >= charges && i < oldCharges && transition < .3 ? IVORY : BRONZE);
            g.setComposite(AlphaComposite.SrcOver.derive(opacity * lit));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arc);
        }
        g.dispose();
    }

    /** A short rotating blade tail, independent of all damage hitboxes. */
    public void drawTrail(Graphics2D graphics) {
        if (reaction < .03 || reaction >= SWING + TAIL) return;
        Player camera = p.gp.localPlayer();
        if (camera == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double progress = Math.clamp((reaction - .03) / .11, 0, 1);
        double heading = art.MeleeSlash.heading(p.direction) * 180 / Math.PI;
        double x = SlimeMotion.centerX(p) - camera.worldX + camera.screenX;
        double y = SlimeMotion.centerY(p) - camera.worldY + camera.screenY - 6;
        double radius = p.gp.tileSize * .75;
        float fade = (float) (reaction < SWING ? 1 : Math.max(0, 1 - (reaction - SWING) / TAIL));
        for (int i = 0; i < 5; i++) {
            double start = -heading + 90 - progress * 360 + i * 17;
            arc.setArc(
                    x - radius, y - radius * .73, radius * 2, radius * 1.46, start, 24, Arc2D.OPEN);
            g.setComposite(AlphaComposite.SrcOver.derive(fade * (.65f - i * .11f)));
            g.setColor(i == 0 ? IVORY : i < 3 ? Palette.STONES[0] : BRONZE);
            g.setStroke(
                    new BasicStroke(
                            3.5f - i * .45f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arc);
        }
        g.dispose();
    }

    /** Network mirrors cosmetics only, with a serial to avoid repeating the same reaction. */
    public void snapshot(net.PlayerState s) {
        s.retaliationSerial = serial;
        s.retaliationTime = reaction;
    }

    public void sync(net.PlayerState s) {
        if (s.retaliationSerial != serial) {
            serial = s.retaliationSerial;
            reaction = s.retaliationTime;
            pending = false;
        }
    }
}
