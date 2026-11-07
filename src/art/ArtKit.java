package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

public final class ArtKit {
    private ArtKit(){}

    public static final int SS = 2; // supersampling factor: draw at 2x, average down for clean edges

    //OUTLINES
    public enum OutlineStyle { NONE, SOFT, INTERACTIVE }

    // Near-black, tinted slightly purple so it sits well in a soft palette. Set to Color.BLACK for pure black.
    public static final Color INTERACTIVE_OUTLINE = new Color(24, 18, 28);

    public static Color outline(Color fill, OutlineStyle style){
        return switch(style){
            case NONE -> new Color(0, 0, 0, 0);
            case SOFT -> shift(fill, 0.68f, 0.10f, 1.0f, 0.6f); // a darker, slightly cooler tone of the fill itself
            case INTERACTIVE -> INTERACTIVE_OUTLINE;
        };
    }

    //LIGHT: warm golden-hour sun from the top-left. Highlights drift yellow, shadows drift cool blue-violet.
    public static Color light(Color c){ return shift(c, 0.13f, 0.10f, 0.92f, 1.12f); }
    public static Color shade(Color c){ return shift(c, 0.68f, 0.14f, 1.02f, 0.70f); }

    private static Color shift(Color c, float hueTarget, float hueAmount, float satMul, float briMul){
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float h = lerpHue(hsb[0], hueTarget, hueAmount);
        float s = Math.max(0f, Math.min(1f, hsb[1] * satMul));
        float b = Math.max(0f, Math.min(1f, hsb[2] * briMul));
        Color out = new Color(Color.HSBtoRGB(h, s, b));
        return new Color(out.getRed(), out.getGreen(), out.getBlue(), c.getAlpha());
    }

    private static float lerpHue(float from, float to, float t){
        float d = to - from;
        if(d > 0.5f) d -= 1f;
        if(d < -0.5f) d += 1f;
        float h = from + d * t;
        if(h < 0f) h += 1f;
        if(h >= 1f) h -= 1f;
        return h;
    }

    public static Color alpha(Color c, int a){
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    public static Color mix(Color a, Color b, float t){
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    //NOISE: deterministic, so the world looks the same every run
    public static double hash01(long seed, int x, int y){
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private static double lerp(double a, double b, double t){
        return a + (b - a) * t;
    }

    public static double valueNoise(double x, double y, long seed){
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        double sx = fx * fx * (3 - 2 * fx);
        double sy = fy * fy * (3 - 2 * fy);
        double a = hash01(seed, x0, y0);
        double b = hash01(seed, x0 + 1, y0);
        double c = hash01(seed, x0, y0 + 1);
        double d = hash01(seed, x0 + 1, y0 + 1);
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
    }

    // two octaves of smooth noise, roughly 0..1
    public static double fbm(double x, double y, long seed){
        return 0.65 * valueNoise(x, y, seed) + 0.35 * valueNoise(x * 2.1 + 17.3, y * 2.1 + 9.1, seed + 1);
    }

    //SUPERSAMPLED DRAWING SURFACE: draw in 1x units, finish() averages down to the real size
    public static final class Surface {
        public final int w, h;
        private final BufferedImage big;
        public final Graphics2D g;

        public Surface(int w, int h){
            this.w = w;
            this.h = h;
            big = new BufferedImage(w * SS, h * SS, BufferedImage.TYPE_INT_ARGB);
            g = big.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(SS, SS);
        }

        public BufferedImage finish(){
            g.dispose();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int n = SS * SS;
            for(int y = 0; y < h; y++){
                for(int x = 0; x < w; x++){
                    long a = 0, r = 0, gr = 0, b = 0;
                    for(int sy = 0; sy < SS; sy++){
                        for(int sx = 0; sx < SS; sx++){
                            int p = big.getRGB(x * SS + sx, y * SS + sy);
                            int pa = (p >>> 24) & 0xFF;
                            a += pa;
                            r += (long) ((p >> 16) & 0xFF) * pa; // weight by alpha so transparent edges don't darken
                            gr += (long) ((p >> 8) & 0xFF) * pa;
                            b += (long) (p & 0xFF) * pa;
                        }
                    }
                    if(a == 0){
                        out.setRGB(x, y, 0);
                        continue;
                    }
                    int outA = (int) (a / n);
                    int outR = (int) (r / a);
                    int outG = (int) (gr / a);
                    int outB = (int) (b / a);
                    out.setRGB(x, y, (outA << 24) | (outR << 16) | (outG << 8) | outB);
                }
            }
            return out;
        }
    }

    //SEAMLESS STAMPING
    public interface Stamp {
        void draw(Graphics2D g, double x, double y);
    }

    // Draws a stamp at (x, y) and at the wrapped copies, so anything crossing a tile edge continues on the
    // opposite side. This is what makes the tile repeat without visible seams.
    public static void wrapped(Graphics2D g, double x, double y, double radius, int size, Stamp s){
        for(int dy = -1; dy <= 1; dy++){
            for(int dx = -1; dx <= 1; dx++){
                if(dx == 1 && x - radius >= 0) continue;          // crosses the left edge -> copy on the right
                if(dx == -1 && x + radius <= size) continue;      // crosses the right edge -> copy on the left
                if(dy == 1 && y - radius >= 0) continue;
                if(dy == -1 && y + radius <= size) continue;
                s.draw(g, x + dx * size, y + dy * size);
            }
        }
    }

    //SHAPES
    // soft radial patch, fading to transparent at its edge
    public static void softBlob(Graphics2D g, double cx, double cy, double rx, double ry, Color center){
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.scale(rx, ry);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), 1f,
                new float[]{0f, 1f}, new Color[]{center, alpha(center, 0)}));
        g.fill(new Ellipse2D.Double(-1, -1, 2, 2));
        g.setTransform(old);
    }

    // a short curved grass blade rooted at (x, y), leaning sideways by `lean`
    public static void blade(Graphics2D g, double x, double y, double h, double lean, double width, Color c){
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x - width / 2, y);
        p.quadTo(x - width / 2 + lean * 0.3, y - h * 0.6, x + lean, y - h);
        p.quadTo(x + width / 2 + lean * 0.3, y - h * 0.6, x + width / 2, y);
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    // five-petal flower; the shadow petals sit down-right because the light comes from the top-left
    public static void flower(Graphics2D g, double x, double y, double r, Color petal, Color center){
        for(int pass = 0; pass < 2; pass++){
            g.setColor(pass == 0 ? shade(petal) : petal);
            double off = pass == 0 ? 0.4 : 0.0;
            for(int i = 0; i < 5; i++){
                double a = i * Math.PI * 2 / 5 - Math.PI / 2;
                g.fill(new Ellipse2D.Double(
                        x + Math.cos(a) * r * 0.9 - r * 0.6 + off,
                        y + Math.sin(a) * r * 0.9 - r * 0.6 + off,
                        r * 1.2, r * 1.2));
            }
        }
        g.setColor(center);
        g.fill(new Ellipse2D.Double(x - r * 0.45, y - r * 0.45, r * 0.9, r * 0.9));
    }
}