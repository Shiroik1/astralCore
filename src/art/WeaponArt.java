package art;

import java.awt.*;
import java.awt.geom.*;

/**
 * Held weapons for the cutout rig. Each weapon is built around its GRIP at (0,0) with the tip pointing up (-y).
 * The rig places the grip on the hand and rotates the whole weapon, so it follows idle / run / attack poses.
 * Self-contained apart from CharacterArt's painter, so adding a weapon = one enum entry + one shape method.
 */
public final class WeaponArt {
    private WeaponArt() {}

    /**
     * Swing keyframes in degrees.
     * front*: weapon angle seen from front/back (0 = straight up, + = clockwise on screen)
     * side*:  weapon angle in profile (facing right)
     * arm*:   profile arm angle (0 = hanging down, 180 = straight up, 270 = straight forward)
     */
    public enum Kind {
        // front view chops from over the head DOWN toward the camera; back view chops UP and away from it
        NONE (0, 0, 0,    0, 0, 0,     25, -85,   0, 0),
        SWORD(14, 8, 165,   28, -30, 120,   150, 300,   170, 15),
        AXE  (12, 5, 170,   30, -40, 125,   150, 300,   175, 10),
        STAFF(5, -10, 22,    10, 18, 42,    -140, -105,   -10, 22);

        public final float restFront, windFront, strikeFront, restSide, windSide, strikeSide, windArm, strikeArm, windBack, strikeBack;
        Kind(float rf, float wf, float sf, float rs, float ws, float ss, float wa, float sa, float wb, float sb) {
            restFront = rf; windFront = wf; strikeFront = sf; restSide = rs; windSide = ws; strikeSide = ss; windArm = wa; strikeArm = sa;
            windBack = wb; strikeBack = sb;
        }
    }

    /** Pose values for one frame. */
    public static final class Swing {
        public float lift;        // 0..1 how far the weapon arm is raised (front/back view)
        public float front;       // weapon angle, front/back view
        public float side;        // weapon angle, profile
        public float arm;         // arm angle, profile (NaN = not attacking, use the walk swing)
        public float reach;       // 0..1 weapon pushes forward and grows during the strike
        public float smear;       // 0..1 intensity of the slash smear (peaks on the first hit frames)
        public float back, prevBack;        // weapon angle seen from behind
        public float prevFront, prevSide;   // angles a moment earlier: the smear sweeps from these to the current angle
    }

    /** t = attack progress 0..1: wind-up 0-0.35, strike 0.35-0.75 (matches hit frames 4-6 of 8), recover 0.75-1. */
    public static Swing swing(Kind k, boolean attacking, float t) {
        Swing s = new Swing();
        if (!attacking) {
            s.lift = 0; s.front = k.restFront; s.back = k.restFront; s.side = k.restSide; s.arm = Float.NaN;
            return s;
        }
        t = Math.max(0, Math.min(1, t));
        if (k != Kind.STAFF) return meleeSwing(k, t);
        if (t < 0.35f) {
            float e = smooth(t / 0.35f);
            s.lift = e;
            s.front = lerp(k.restFront, k.windFront, e);
            s.back = lerp(k.restFront, k.windBack, e);
            s.side = lerp(k.restSide, k.windSide, e);
            s.arm = lerp(0, k.windArm, e);
        } else if (t < 0.75f) {
            float e = (t - 0.35f) / 0.40f; e = e * e * (1.6f - 0.6f * e);   // ease-in: accelerates into the hit
            s.lift = 1 - 0.5f * e;
            s.front = lerp(k.windFront, k.strikeFront, e);
            s.back = lerp(k.windBack, k.strikeBack, e);
            s.side = lerp(k.windSide, k.strikeSide, e);
            s.arm = lerp(k.windArm, k.strikeArm, e);
        } else {
            float e = smooth((t - 0.75f) / 0.25f);
            s.lift = 0.5f * (1 - e);
            s.front = lerp(k.strikeFront, k.restFront, e);
            s.back = lerp(k.strikeBack, k.restFront, e);
            s.side = lerp(k.strikeSide, k.restSide, e);
            s.arm = lerp(k.strikeArm, k.strikeArm > 0 ? 360 : 0, e);
        }
        // strike extras: reach + smear bump up to a peak at t = 0.57 (the middle hit frame)
        float bump = Math.max(0f, 1f - Math.abs(t - 0.60f) / 0.32f);
        s.reach = bump;
        s.smear = bump;
        Swing before = swingAt(k, Math.max(0.35f, t - 0.22f));
        s.prevFront = before.front;
        s.prevBack = before.back;
        s.prevSide = before.side;
        return s;
    }

    /** Eight-frame melee beat: anticipate, snap forward on frame 4, impact on 5,
     * follow through on 6, then settle. These are Player's active hit frames 4–6.
     */
    private static Swing meleeSwing(Kind k, float t) {
        Swing s = new Swing();
        float wind = keyed(t, 0, 0.55f, 1, 0.55f, 0.12f, 0, 0, 0);
        float cut = keyed(t, 0, 0, 0, 0.65f, 1, 1, 0.45f, 0);
        s.lift = wind;
        s.reach = keyed(t, 0, 0, 0, 0.75f, 1, 0.6f, 0.2f, 0);
        s.smear = keyed(t, 0, 0, 0, 0.7f, 1, 0.4f, 0, 0);
        s.front = k.restFront + (k.windFront - k.restFront) * wind
                + (k.strikeFront - k.restFront) * cut;
        s.back = k.restFront + (k.windBack - k.restFront) * wind
                + (k.strikeBack - k.restFront) * cut;
        s.side = k.restSide + (k.windSide - k.restSide) * wind
                + (k.strikeSide - k.restSide) * cut;
        // Punch from the shoulder; armed strikes sweep from overhead into a forward extension.
        s.arm = (k == Kind.NONE ? 25 : 145) * wind - 80 * cut;
        return s;
    }

    private static float keyed(float t, float... values) {
        float frame = t * (values.length - 1);
        int index = Math.min((int) frame, values.length - 2);
        return lerp(values[index], values[index + 1], smooth(frame - index));
    }

    private static Swing swingAt(Kind k, float t) {
        Swing s = new Swing();
        float e = (t - 0.35f) / 0.40f; e = Math.max(0, Math.min(1, e)); e = e * e * (1.6f - 0.6f * e);
        s.front = lerp(k.windFront, k.strikeFront, e);
        s.back = lerp(k.windBack, k.strikeBack, e);
        s.side = lerp(k.windSide, k.strikeSide, e);
        return s;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    // ------------------------------------------------------------ drawing
    private static final Color STEEL = new Color(0xD3DCE9), STEEL_DARK = new Color(0x9FAFC6);
    private static final Color WOOD = new Color(0xB48B62), WOOD_DARK = new Color(0x8A6B58);
    private static final Color GOLD = new Color(0xE2B84E), LEATHER = new Color(0x7A5A48);
    private static final Color ORB = new Color(0xB894FF);

    /**
     * Draw a weapon with its grip at (hx,hy). angle in degrees; mirror = -1 draws it flipped (left-hand use).
     * reach (0..1) grows the blade during extension. The directional impact crescent
     * is rendered separately by the body rig so it stays on the forward-facing side.
     */
    public static void draw(Graphics2D g, Kind kind, float hx, float hy, float angle, int mirror,
                            float reach, float smear, float prevAngle, float size) {
        if (kind == Kind.NONE) return;
        AffineTransform at = new AffineTransform();
        at.translate(hx, hy);
        at.scale(mirror, 1);
        at.rotate(Math.toRadians(angle));
        float grow = size * (1f + 0.28f * reach);
        at.scale(grow, grow);
        switch (kind) {
            case SWORD -> sword(g, at);
            case AXE   -> axe(g, at);
            case STAFF -> staff(g, at);
            default -> {}
        }
    }

    /** A broad tapered crescent in the facing direction, kept inside the 64-unit canvas.
     * It is drawn in body space, so the slash stays in front of the target-facing side
     * instead of orbiting around the character's weapon hand.
     */
    static void drawMeleeSmear(Graphics2D g, Kind kind, boolean side, boolean back, float t, float strength) {
        if (kind == Kind.STAFF || strength <= 0.01f) return;
        float cx = 32, cy = side ? 39 : back ? 25 : 39;
        float rx = side ? 28 : 24, ry = side ? 20 : 22;
        if (kind == Kind.NONE) { rx *= 0.7f; ry *= 0.7f; }
        double facing = side ? 0 : back ? -Math.PI / 2 : Math.PI / 2;
        float sweep = Math.max(0, Math.min(1, (t - 3f / 7) * 7));
        double from = facing + Math.toRadians(-78 + 42 * sweep);
        double to = facing + Math.toRadians(12 + 66 * sweep);
        Path2D crescent = new Path2D.Float();
        int steps = 32;
        for (int i = 0; i <= steps; i++) {
            double a = from + (to - from) * i / steps;
            double x = cx + rx * Math.cos(a), y = cy + ry * Math.sin(a);
            if (i == 0) crescent.moveTo(x, y); else crescent.lineTo(x, y);
        }
        for (int i = steps; i >= 0; i--) {
            double a = from + (to - from) * i / steps;
            double taper = Math.sin(Math.PI * i / steps);
            double width = (kind == Kind.NONE ? 3 : 6) * taper;
            crescent.lineTo(cx + (rx - width) * Math.cos(a), cy + (ry - width) * Math.sin(a));
        }
        crescent.closePath();
        Paint paint = g.getPaint();
        g.setPaint(new Color(255, 229, 170, Math.round(240 * strength)));
        g.fill(crescent);
        // Crisp ivory outer edge survives downsampling; the warm inner band gives it weight.
        Path2D rim = new Path2D.Float();
        for (int i = 0; i <= steps; i++) {
            double a = from + (to - from) * i / steps;
            double x = cx + rx * Math.cos(a), y = cy + ry * Math.sin(a);
            if (i == 0) rim.moveTo(x, y); else rim.lineTo(x, y);
        }
        Stroke stroke = g.getStroke();
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 253, 240, Math.round(255 * strength)));
        g.draw(rim);
        g.setStroke(stroke);
        g.setPaint(paint);
    }

    private static void P(Graphics2D g, AffineTransform at, Shape s, Color c, int seed) {
        CharacterArt.softPart(g, at.createTransformedShape(s), c);
    }

    private static void sword(Graphics2D g, AffineTransform at) {
        Path2D blade = new Path2D.Float();
        blade.moveTo(-1.55, -1.6); blade.lineTo(-1.55, -13.2);
        blade.lineTo(0, -16.0);
        blade.lineTo(1.55, -13.2); blade.lineTo(1.55, -1.6); blade.closePath();
        P(g, at, blade, STEEL, 41);
        // fuller (centre groove)
        g.setColor(CharacterArt.withAlpha(STEEL_DARK, 150));
        g.fill(at.createTransformedShape(new Rectangle2D.Float(-0.3f, -12.2f, 0.6f, 10.2f)));
        P(g, at, new RoundRectangle2D.Float(-1.5f, 2.2f, 3.0f, 3.2f, 1.2f, 1.2f), LEATHER, 42);           // grip below the hand
        P(g, at, new RoundRectangle2D.Float(-3.5f, -2.6f, 7.0f, 1.9f, 1.2f, 1.2f), GOLD, 43);             // cross-guard
        P(g, at, new Ellipse2D.Float(-1.2f, 4.9f, 2.4f, 2.4f), GOLD, 44);                                 // pommel
    }

    private static void axe(Graphics2D g, AffineTransform at) {
        P(g, at, new RoundRectangle2D.Float(-0.95f, -13.6f, 1.9f, 20.0f, 1.2f, 1.2f), WOOD, 51);          // haft
        Path2D head = new Path2D.Float();
        head.moveTo(0.9, -13.8);
        head.curveTo(3.5, -16.2, 7.6, -15.4, 8.0, -12.0);
        head.curveTo(8.4, -8.6, 5.0, -6.2, 0.9, -8.0);
        head.closePath();
        P(g, at, head, STEEL, 52);
        Path2D spike = new Path2D.Float();
        spike.moveTo(-0.9, -13.0); spike.lineTo(-3.4, -11.4); spike.lineTo(-0.9, -9.6); spike.closePath();
        P(g, at, spike, STEEL_DARK, 53);
        P(g, at, new RoundRectangle2D.Float(-1.3f, -1.2f, 2.6f, 1.6f, 0.8f, 0.8f), LEATHER, 54);           // wrap
    }

    private static void staff(Graphics2D g, AffineTransform at) {
        P(g, at, new RoundRectangle2D.Float(-0.9f, -15.0f, 1.8f, 30.0f, 1.2f, 1.2f), WOOD, 61);           // long haft to the ground
        // halo behind the orb
        g.setColor(CharacterArt.withAlpha(ORB, 70));
        g.fill(at.createTransformedShape(new Ellipse2D.Float(-5.6f, -23.4f, 11.2f, 11.2f)));
        P(g, at, new RoundRectangle2D.Float(-2.4f, -16.0f, 4.8f, 2.0f, 1.0f, 1.0f), GOLD, 62);            // ring
        P(g, at, new Ellipse2D.Float(-3.2f, -21.2f, 6.4f, 6.4f), ORB, 63);                                // orb
        g.setColor(new Color(255, 255, 255, 190));
        g.fill(at.createTransformedShape(new Ellipse2D.Float(-1.6f, -20.2f, 1.8f, 1.8f)));
    }
}
