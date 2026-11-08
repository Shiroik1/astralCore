package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Base chibi character, cutout-puppet rig, Upgraded Vector style.
 * Everything is designed in a 64-unit space (feet at y=60) and scaled to the requested size.
 * Self-contained: no dependency on ArtKit so it can be dropped in as-is.
 */
public final class CharacterArt {
    private CharacterArt() {}

    public enum Dir { DOWN, UP, LEFT, RIGHT }

    /** Look of the body. Colours only, so hair/clothes layers can reuse the same rig later. */
    public static final class Look {
        public Color skin = new Color(0xFBDDC6);
        public Color eye  = new Color(0x56403F);
        public Color blush = new Color(0xF4A99A);
        public boolean shadow = true;   // set false when the game draws its own ground shadow
    }

    /** Animation pose. phase 0..1 is the walk cycle, attack 0..1 is a swing (0 = none). */
    public static final class Pose {
        public float walk = 0f;     // 0..1 cycle phase
        public boolean moving = false;
        public float idle = 0f;     // 0..1 breathing phase
        public float attack = 0f;   // 0..1
        public Pose walk(float p) { walk = p; moving = true; return this; }
        public Pose idle(float p) { idle = p; return this; }
        public Pose attack(float a) { attack = a; return this; }
    }

    private static final float W = 64f, H = 64f, FOOT_Y = 60f;
    private static final int SS = 4;

    // ------------------------------------------------------------ public api
    public static BufferedImage render(Dir dir, Pose pose, Look look, int size) {
        int n = size * SS;
        BufferedImage hi = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float k = n / W;
        g.scale(k, k);
        draw(g, dir, pose, look);
        g.dispose();
        BufferedImage out = boxDown(hi, SS);
        grain(out, 6);
        return out;
    }

    // ------------------------------------------------------------ rig
    private static void draw(Graphics2D g, Dir dir, Pose p, Look look) {
        float swing = p.moving ? (float) Math.sin(p.walk * Math.PI * 2) : 0f;
        float lift  = p.moving ? Math.abs((float) Math.sin(p.walk * Math.PI * 2)) : 0f;
        float bob   = p.moving ? -lift * 1.3f : (float) Math.sin(p.idle * Math.PI * 2) * 0.35f;
        float squash = p.moving ? 0f : (float) Math.sin(p.idle * Math.PI * 2) * 0.012f;

        // soft ground shadow (stays on the ground; shrinks as body lifts)
        float sh = 1f - lift * 0.08f;
        if (look.shadow) softShadow(g, 32, FOOT_Y + 0.5f, 15 * sh, 3.6f * sh);

        AffineTransform base = g.getTransform();
        g.translate(0, bob);
        g.translate(32, FOOT_Y); g.scale(1 + squash, 1 - squash); g.translate(-32, -FOOT_Y);

        Color skin = look.skin;
        switch (dir) {
            case DOWN  -> front(g, look, skin, swing, p, false);
            case UP    -> front(g, look, skin, swing, p, true);
            case RIGHT -> side(g, look, skin, swing, p, false);
            case LEFT  -> side(g, look, skin, swing, p, true);
        }
        g.setTransform(base);
    }

    // ---- front / back -------------------------------------------------------
    private static void front(Graphics2D g, Look look, Color skin, float swing, Pose p, boolean back) {
        float legL = swing * 1.8f, legR = -swing * 1.8f;          // vertical stride offsets
        float armL = -swing * 2.0f, armR = swing * 2.0f;
        float atk = (float) Math.sin(Math.min(1f, p.attack) * Math.PI);

        // legs (stubby, slightly tapered)
        part(g, leg(25.6f, 48.5f, 6.4f, 11.5f, legL), skin, 11);
        part(g, leg(32.0f, 48.5f, 6.4f, 11.5f, legR), skin, 12);
        // torso
        part(g, torso(), skin, 13);
        // neck shade just under the head
        // arms (angled mitten shapes; attacking arm lifts)
        part(g, arm(26.0f, 37.5f, -1, 8.0f, armL * 0.4f - (back ? 0 : 0)), skin, 14);
        part(g, arm(38.0f, 37.5f, +1, 8.0f - atk * 9f, armR * 0.4f), skin, 15);
        // ears (front only visible at sides; back too)
        part(g, ellipse(14.2f, 26.5f, 3.6f, 3.6f), skin, 16);
        part(g, ellipse(49.8f, 26.5f, 3.6f, 3.6f), skin, 17);
        // head
        part(g, head(), skin, 18);
        if (!back) {
            // eyes: tall dark ovals
            Shape eL = ellipse(25.2f, 25.3f, 1.75f, 3.5f), eR = ellipse(38.8f, 25.3f, 1.75f, 3.5f);
            g.setColor(look.eye); g.fill(eL); g.fill(eR);
            g.setColor(new Color(255, 255, 255, 150));
            g.fill(ellipse(25.8f, 23.6f, 0.55f, 0.75f)); g.fill(ellipse(39.4f, 23.6f, 0.55f, 0.75f));
            // blush
            g.setColor(withAlpha(look.blush, 70));
            g.fill(ellipse(21.0f, 30.2f, 2.6f, 1.5f)); g.fill(ellipse(43.0f, 30.2f, 2.6f, 1.5f));
        }
    }

    // ---- side ---------------------------------------------------------------
    private static void side(Graphics2D g, Look look, Color skin, float swing, Pose p, boolean left) {
        AffineTransform t = g.getTransform();
        if (left) { g.translate(W, 0); g.scale(-1, 1); }          // author facing right, mirror for left
        float atk = (float) Math.sin(Math.min(1f, p.attack) * Math.PI);

        // far leg & far arm first (darker)
        Color far = shade(skin, 0.90f);
        part(g, sideLeg(29.5f, 48.5f, -swing * 3.2f), far, 21);
        part(g, sideArm(32.5f, 38f, swing * 3.0f, 0), far, 22);
        // torso
        part(g, sideTorso(), skin, 23);
        // near leg
        part(g, sideLeg(32.0f, 48.5f, swing * 3.2f), skin, 24);
        // ear on the head
        // head (profile: nose-less rounded, face shifted right)
        part(g, sideHead(), skin, 25);
        part(g, ellipse(29.5f, 27.0f, 2.8f, 2.8f), shade(skin, 0.95f), 26);   // ear
        // near arm
        part(g, sideArm(32.5f, 38f, -swing * 3.0f, atk), skin, 27);
        // eye
        g.setColor(look.eye); g.fill(ellipse(41.0f, 25.3f, 1.75f, 3.5f));
        g.setColor(new Color(255, 255, 255, 150)); g.fill(ellipse(41.6f, 23.6f, 0.55f, 0.75f));
        g.setColor(withAlpha(look.blush, 70)); g.fill(ellipse(38.2f, 30.4f, 2.4f, 1.4f));
        g.setTransform(t);
    }

    // ------------------------------------------------------------ shapes
    private static Shape head() {
        // squircle: wide jaw, domed top (reference proportions: ~33 wide x 30 tall)
        return superellipse(32f, 19.2f, 16.8f, 15.2f, 2.6, 0.0);
    }
    private static Shape sideHead() {
        return superellipse(34.4f, 19.2f, 16.2f, 15.2f, 2.5, 0.0);
    }
    private static Shape torso() {
        Path2D p = new Path2D.Float();
        p.moveTo(25.5, 34.2);
        p.curveTo(25.0, 40, 24.4, 45, 24.0, 50.6);
        p.lineTo(40.0, 50.6);
        p.curveTo(39.6, 45, 39.0, 40, 38.5, 34.2);
        p.closePath();
        return rounded(p, 1.0f);
    }
    private static Shape sideTorso() {
        Path2D p = new Path2D.Float();
        p.moveTo(27.0, 34.2);
        p.curveTo(26.4, 40, 26.4, 45, 26.8, 50.6);
        p.lineTo(38.6, 50.6);
        p.curveTo(39.4, 45, 38.8, 40, 37.4, 34.2);
        p.closePath();
        return rounded(p, 1.0f);
    }
    private static Shape leg(float x, float y, float w, float h, float stride) {
        Path2D p = new Path2D.Float();
        float yy = y + Math.min(0, -stride * 0.0f);
        p.moveTo(x, yy);
        p.lineTo(x + w, yy);
        p.lineTo(x + w - 0.2f, yy + h + stride * 0.5f - 0.8f);
        p.quadTo(x + w - 0.4f, yy + h + stride * 0.5f + 0.5f, x + w - 1.6f, yy + h + stride * 0.5f + 0.5f);
        p.lineTo(x + 1.6f, yy + h + stride * 0.5f + 0.5f);
        p.quadTo(x + 0.4f, yy + h + stride * 0.5f + 0.5f, x + 0.2f, yy + h + stride * 0.5f - 0.8f);
        p.closePath();
        return p;
    }
    private static Shape sideLeg(float x, float y, float swing) {
        Path2D p = new Path2D.Float();
        float footX = x + swing * 0.8f;
        p.moveTo(x, y);
        p.lineTo(x + 6.2f, y);
        p.lineTo(footX + 6.0f, y + 10.0f);
        p.quadTo(footX + 6.0f, y + 11.5f, footX + 4.4f, y + 11.5f);
        p.lineTo(footX + 1.8f, y + 11.5f);
        p.quadTo(footX + 0.2f, y + 11.5f, footX + 0.2f, y + 10.0f);
        p.closePath();
        return p;
    }
    /** front arm: capsule from shoulder to mitten; dir -1 = left side of screen. */
    private static Shape arm(float sx, float sy, int dir, float len, float swing) {
        float ex = sx + dir * (4.2f + swing * 0.3f), ey = sy + len + swing;
        return new BasicStroke(4.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(new Line2D.Float(sx, sy, ex, ey));
    }
    private static Shape sideArm(float sx, float sy, float swing, float atk) {
        Shape cap = new BasicStroke(4.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(new Line2D.Float(0, 0, 0, 8.5f));
        AffineTransform at = AffineTransform.getTranslateInstance(sx, sy);
        at.rotate(Math.toRadians(swing * 5.5 - atk * 105));
        return at.createTransformedShape(cap);
    }
    private static Shape ellipse(float cx, float cy, float rx, float ry) {
        return new Ellipse2D.Float(cx - rx, cy - ry, rx * 2, ry * 2);
    }
    private static Shape superellipse(double cx, double cy, double rx, double ry, double n, double flatTop) {
        Path2D.Double p = new Path2D.Double();
        int N = 96;
        for (int i = 0; i < N; i++) {
            double a = i * Math.PI * 2 / N, c = Math.cos(a), s = Math.sin(a);
            double x = cx + rx * Math.signum(c) * Math.pow(Math.abs(c), 2 / n);
            double yy = Math.signum(s) * Math.pow(Math.abs(s), 2 / n);
            // dome the top a little more than the jaw
            double y = cy + ry * (yy < 0 ? yy * 1.0 : yy * 0.96);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }
    /** gently round hard corners by stroking+filling an offset (keeps a soft cutout look) */
    private static Shape rounded(Shape s, float r) {
        Area a = new Area(new BasicStroke(r * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(s));
        a.add(new Area(s));
        return a;
    }

    // ------------------------------------------------------------ painting
    /** one rig part: fill, shade crescent, rim light, wobbly soft outline */
    private static void part(Graphics2D g, Shape raw, Color fill, int seed) {
        Shape s = wobble(raw, seed, 0.22f);
        g.setColor(fill); g.fill(s);

        Area a = new Area(s);
        // shade: bottom-right crescent
        Area sh = new Area(s);
        sh.subtract(new Area(AffineTransform.getTranslateInstance(-1.5, -1.9).createTransformedShape(s)));
        g.setColor(withAlpha(shade(fill, 0.86f), 255));
        Shape old = g.getClip(); g.clip(a); g.fill(sh);
        // rim light: top-left thin crescent
        Area rim = new Area(s);
        rim.subtract(new Area(AffineTransform.getTranslateInstance(0.9, 1.1).createTransformedShape(s)));
        g.setColor(withAlpha(lighten(fill, 0.55f), 210)); g.fill(rim);
        g.setClip(old);

        g.setStroke(new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outline(fill));
        g.draw(s);
    }

    private static Shape wobble(Shape s, int seed, float amp) {
        Path2D.Float out = new Path2D.Float();
        PathIterator it = new FlatteningPathIterator(s.getPathIterator(null), 0.12);
        float[] c = new float[6]; boolean first = true;
        while (!it.isDone()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_CLOSE) { out.closePath(); }
            else {
                float x = c[0], y = c[1];
                float dx = (noise(x * 0.35f, y * 0.35f, seed) - 0.5f) * 2 * amp;
                float dy = (noise(x * 0.35f + 9.1f, y * 0.35f + 4.7f, seed) - 0.5f) * 2 * amp;
                if (t == PathIterator.SEG_MOVETO) { out.moveTo(x + dx, y + dy); first = false; }
                else out.lineTo(x + dx, y + dy);
            }
            it.next();
        }
        return out;
    }

    private static void softShadow(Graphics2D g, float cx, float cy, float rx, float ry) {
        for (int i = 6; i >= 1; i--) {
            float f = i / 6f;
            g.setColor(new Color(40, 30, 60, (int) (16 * (1.2 - f) + 6)));
            g.fill(ellipse(cx, cy, rx * (0.55f + 0.45f * f), ry * (0.55f + 0.45f * f)));
        }
    }

    // ------------------------------------------------------------ colour + noise
    static Color withAlpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }
    static Color lighten(Color c, float t) {
        return new Color(Math.round(c.getRed() + (255 - c.getRed()) * t), Math.round(c.getGreen() + (255 - c.getGreen()) * t), Math.round(c.getBlue() + (255 - c.getBlue()) * t));
    }
    /** warm-shift shade: darker, slightly more saturated and warmer (reads as cozy, not grey) */
    static Color shade(Color c, float k) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float b = Math.max(0, hsb[2] * k);
        float s = Math.min(1, hsb[1] * 1.18f + 0.03f);
        return Color.getHSBColor(hsb[0] - 0.012f, s, b);
    }
    static Color outline(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return Color.getHSBColor(hsb[0] - 0.01f, Math.min(1, hsb[1] * 1.25f + 0.08f), hsb[2] * 0.66f);
    }
    private static float noise(float x, float y, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        float a = h(xi, yi, seed), b = h(xi + 1, yi, seed), c = h(xi, yi + 1, seed), d = h(xi + 1, yi + 1, seed);
        float u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }
    private static float h(int x, int y, int seed) {
        int n = x * 374761393 + y * 668265263 + seed * 1442695041;
        n = (n ^ (n >>> 13)) * 1274126177; n ^= n >>> 16;
        return (n & 0xFFFF) / 65535f;
    }

    // ------------------------------------------------------------ image helpers
    static BufferedImage boxDown(BufferedImage src, int f) {
        int w = src.getWidth() / f, hgt = src.getHeight() / f;
        BufferedImage out = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < hgt; y++) for (int x = 0; x < w; x++) {
            float a = 0, r = 0, gg = 0, b = 0;
            for (int j = 0; j < f; j++) for (int i = 0; i < f; i++) {
                int p = src.getRGB(x * f + i, y * f + j);
                float pa = (p >>> 24) / 255f;
                a += pa; r += ((p >> 16) & 255) * pa; gg += ((p >> 8) & 255) * pa; b += (p & 255) * pa;
            }
            if (a > 0) { r /= a; gg /= a; b /= a; }
            a /= f * f;
            out.setRGB(x, y, (Math.round(a * 255) << 24) | (Math.round(r) << 16) | (Math.round(gg) << 8) | Math.round(b));
        }
        return out;
    }
    /** paper grain: tiny luminance noise on opaque pixels */
    static void grain(BufferedImage img, int amp) {
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
            int p = img.getRGB(x, y); int a = p >>> 24; if (a == 0) continue;
            int d = Math.round((h(x, y, 77) - 0.5f) * 2 * amp);
            int r = clamp(((p >> 16) & 255) + d), g = clamp(((p >> 8) & 255) + d), b = clamp((p & 255) + d);
            img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
        }
    }
    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }
}