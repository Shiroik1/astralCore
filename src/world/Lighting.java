package world;

import art.ArtKit;
import entity.Player;
import main.Gamepanel;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// One half-resolution "lightmap": ambient colour + cloud shadows + vignette, with warm holes punched out for the
// lights, scaled up over the world once per frame.
public class Lighting {

    public enum Mood {
        //      ambient r, g,   b,   a      glow   vignette leaves flies clouds
        DAY(    255, 238, 205,  16,     0.00f, 0.30f,   10,  0, 0.9f),
        GOLDEN( 255, 176,  98,  44,     0.55f, 0.45f,   18,  0, 1.0f),
        DUSK(    78,  58, 130,  92,     1.00f, 0.55f,   10, 14, 0.7f),
        NIGHT(   26,  32,  88, 150,     1.20f, 0.65f,    4, 24, 0.0f);

        public final int r, g, b, a, leaves, flies;
        public final float glow, vignette, clouds;

        Mood(int r, int g, int b, int a, float glow, float vignette, int leaves, int flies, float clouds){
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.glow = glow;
            this.vignette = vignette;
            this.leaves = leaves;
            this.flies = flies;
            this.clouds = clouds;
        }

        public static Mood fromName(String name){
            for(Mood m : values()){
                if(m.name().equalsIgnoreCase(name)) return m;
            }
            return null;
        }
    }

    private static final int MAX_LIGHTS = 96;
    private static final int CLOUD_TILE = 512; // lightmap pixels, covering 1024 world pixels

    public boolean enabled = true;
    public float leafLevel, flyLevel;          // 0..1, read by AmbientFX

    private final Gamepanel gp;
    private final int lw, lh;
    private final BufferedImage lightmap, vignette, cloudTile;
    private final Graphics2D lg;
    private final Map<Integer, BufferedImage> punchSprites = new HashMap<>();
    private final Map<Integer, BufferedImage> glowSprites = new HashMap<>();

    private Mood mood = Mood.GOLDEN;
    private float ar, ag, ab, aa, glow, vig, clouds;
    private long time = 0;

    // reusable light list
    private final float[] sx = new float[MAX_LIGHTS], sy = new float[MAX_LIGHTS], rad = new float[MAX_LIGHTS];
    private final float[] pow = new float[MAX_LIGHTS], warm = new float[MAX_LIGHTS];

    public Lighting(Gamepanel gp){
        this.gp = gp;
        lw = gp.screenWidth / 2;
        lh = gp.screenHeight / 2;
        lightmap = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
        lg = lightmap.createGraphics();
        vignette = bakeVignette();
        cloudTile = bakeClouds();
        setMood(Mood.GOLDEN, true);
    }

    public Mood mood(){
        return mood;
    }

    public void setMood(Mood m, boolean instant){
        mood = m;
        if(instant){
            ar = m.r; ag = m.g; ab = m.b; aa = m.a;
            glow = m.glow; vig = m.vignette; clouds = m.clouds;
            leafLevel = m.leaves / (float) AmbientFX.MAX_LEAVES;
            flyLevel = m.flies / (float) AmbientFX.MAX_FLIES;
        }
    }

    // fades toward the current mood
    public void update(){
        time++;
        float k = 0.03f;
        ar += (mood.r - ar) * k;
        ag += (mood.g - ag) * k;
        ab += (mood.b - ab) * k;
        aa += (mood.a - aa) * k;
        glow += (mood.glow - glow) * k;
        vig += (mood.vignette - vig) * k;
        clouds += (mood.clouds - clouds) * k;
        leafLevel += (mood.leaves / (float) AmbientFX.MAX_LEAVES - leafLevel) * k;
        flyLevel += (mood.flies / (float) AmbientFX.MAX_FLIES - flyLevel) * k;
    }

    //DRAW (screen space: call after the shake translate is undone)
    public void draw(Graphics2D g2, int shakeX, int shakeY){
        if(!enabled) return;
        Player cam = gp.localPlayer();
        if(cam == null) return;
        int camLeft = cam.worldX - cam.screenX - shakeX;
        int camTop = cam.worldY - cam.screenY - shakeY;

        //1. ambient colour
        lg.setComposite(AlphaComposite.Src);
        lg.setColor(new Color(c255(ar), c255(ag), c255(ab), c255(aa)));
        lg.fillRect(0, 0, lw, lh);

        //2. cloud shadows, drifting with the wind
        if(clouds > 0.02f){
            lg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, clouds * 0.45f)));
            double cloudX = time * (0.16 + 0.30 * gp.ambient.wind);
            double cloudY = time * 0.06;
            double ox = ((cloudX - camLeft) / 2.0) % CLOUD_TILE;
            double oy = ((cloudY - camTop) / 2.0) % CLOUD_TILE;
            if(ox < 0) ox += CLOUD_TILE;
            if(oy < 0) oy += CLOUD_TILE;
            for(int x = (int) ox - CLOUD_TILE; x < lw; x += CLOUD_TILE){
                for(int y = (int) oy - CLOUD_TILE; y < lh; y += CLOUD_TILE){
                    lg.drawImage(cloudTile, x, y, null);
                }
            }
        }

        //3. vignette
        lg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, vig)));
        lg.drawImage(vignette, 0, 0, null);

        //4. punch the lights out of the darkness
        int n = gatherLights(cam, camLeft, camTop);
        for(int i = 0; i < n; i++){
            float a = Math.min(1f, pow[i]);
            if(a < 0.02f) continue;
            int r = Math.max(4, Math.round(rad[i] / 2f));
            lg.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, a));
            lg.drawImage(punchSprite(r), Math.round(sx[i] / 2f) - r, Math.round(sy[i] / 2f) - r, null);
        }

        //5. warm halo on top of the holes
        for(int i = 0; i < n; i++){
            float a = Math.min(1f, warm[i]);
            if(a < 0.02f) continue;
            int r = Math.max(4, Math.round(rad[i] / 2f));
            lg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            lg.drawImage(glowSprite(r), Math.round(sx[i] / 2f) - r, Math.round(sy[i] / 2f) - r, null);
        }

        //6. one scaled blit over the world
        Composite oldComposite = g2.getComposite();
        Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(lightmap, 0, 0, gp.screenWidth, gp.screenHeight, null);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                oldInterp != null ? oldInterp : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setComposite(oldComposite);

        //7. fireflies glow above the darkness
        gp.ambient.drawFireflies(g2, camLeft, camTop);
    }

    private int gatherLights(Player cam, int camLeft, int camTop){
        int n = 0;
        double s = gp.tileSize / 48.0;
        float objectPower = Math.min(1f, glow);

        if(glow > 0.02f){
            for(WorldObject o : gp.worldObjects){
                double[][] defs = o.kind.lights();
                if(defs.length == 0) continue;
                if(Math.abs(o.worldX - cam.worldX) > 800 || Math.abs(o.worldY - cam.worldY) > 600) continue;
                for(double[] d : defs){
                    if(n >= MAX_LIGHTS) return n;
                    float flicker = (float) (1 + 0.05 * Math.sin(time * 0.13 + o.col * 1.7)
                            + 0.03 * Math.sin(time * 0.31 + o.row * 2.3));
                    sx[n] = (float) (o.worldX + d[0] * s - camLeft);
                    sy[n] = (float) (o.worldY + d[1] * s - camTop);
                    rad[n] = (float) (d[2] * s);
                    pow[n] = (float) d[3] * objectPower * flicker;
                    warm[n] = (float) d[3] * glow * flicker;
                    if(onScreen(n)) n++;
                }
            }
        }

        float need = Math.min(1f, aa / 140f);     // players carry a soft light that grows as the world darkens
        for(Player p : gp.players){
            if(p == null || p.isDead || n >= MAX_LIGHTS) continue;
            sx[n] = p.worldX + gp.tileSize / 2f - camLeft;
            sy[n] = p.worldY + gp.tileSize / 3f - camTop;
            rad[n] = (float) (135 * s);
            pow[n] = 0.55f * need;
            warm[n] = 0;
            if(onScreen(n)) n++;
        }
        return n;
    }

    private boolean onScreen(int i){
        return sx[i] + rad[i] > 0 && sx[i] - rad[i] < gp.screenWidth && sy[i] + rad[i] > 0 && sy[i] - rad[i] < gp.screenHeight;
    }

    private static int c255(float v){
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    //BAKED IMAGES
    private BufferedImage punchSprite(int r){
        return punchSprites.computeIfAbsent(r, k -> radial(k, 255, 255, 255, new int[]{255, 190, 70, 0}));
    }

    private BufferedImage glowSprite(int r){
        return glowSprites.computeIfAbsent(r, k -> radial(k, 255, 205, 120, new int[]{120, 90, 35, 0}));
    }

    private static BufferedImage radial(int r, int cr, int cg, int cb, int[] alphas){
        BufferedImage img = new BufferedImage(r * 2, r * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new RadialGradientPaint(new Point2D.Float(r, r), r, new float[]{0f, 0.35f, 0.7f, 1f}, new Color[]{
                new Color(cr, cg, cb, alphas[0]), new Color(cr, cg, cb, alphas[1]),
                new Color(cr, cg, cb, alphas[2]), new Color(cr, cg, cb, alphas[3])}));
        g.fillRect(0, 0, r * 2, r * 2);
        g.dispose();
        return img;
    }

    private BufferedImage bakeVignette(){
        BufferedImage img = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        float radius = (float) Math.hypot(lw, lh) / 2f;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(lw / 2f, lh / 2f), radius, new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(18, 22, 40, 0), new Color(18, 22, 40, 0), new Color(18, 22, 40, 200)}));
        g.fillRect(0, 0, lw, lh);
        g.dispose();
        return img;
    }

    // seamless soft blobs: periodic noise, so the tile repeats without seams
    private BufferedImage bakeClouds(){
        int n = CLOUD_TILE;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[n * n];
        for(int y = 0; y < n; y++){
            for(int x = 0; x < n; x++){
                double u = x / (double) n * 3, v = y / (double) n * 3;
                double value = 0.7 * periodicNoise(u, v, 3, 501) + 0.3 * periodicNoise(u * 2 + 0.37, v * 2 + 0.11, 6, 503);
                double t = Math.max(0, Math.min(1, (value - 0.50) / 0.20));
                double alpha = t * t * (3 - 2 * t);
                pixels[y * n + x] = ((int) (alpha * 110) << 24) | 0x1C3440;
            }
        }
        img.setRGB(0, 0, n, n, pixels, 0, n);
        return img;
    }

    private static double periodicNoise(double x, double y, int period, long seed){
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        double sxm = fx * fx * (3 - 2 * fx), sym = fy * fy * (3 - 2 * fy);
        int xa = Math.floorMod(x0, period), xb = Math.floorMod(x0 + 1, period);
        int ya = Math.floorMod(y0, period), yb = Math.floorMod(y0 + 1, period);
        double a = ArtKit.hash01(seed, xa, ya), b = ArtKit.hash01(seed, xb, ya);
        double c = ArtKit.hash01(seed, xa, yb), d = ArtKit.hash01(seed, xb, yb);
        double top = a + (b - a) * sxm, bottom = c + (d - c) * sxm;
        return top + (bottom - top) * sym;
    }
}