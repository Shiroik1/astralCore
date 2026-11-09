package world;

import art.Palette;
import entity.Player;
import main.Gamepanel;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

// Local, purely visual: falling leaves and fireflies, plus the shared wind. Never networked.
public class AmbientFX {
    public static final int MAX_LEAVES = 28;
    public static final int MAX_FLIES = 26;

    private final Gamepanel gp;
    private final Random rnd = new Random(77);
    public float wind = 0.5f;           // 0..1, read by the grass and the cloud shadows
    private long time = 0;

    private final float[] lx = new float[MAX_LEAVES], ly = new float[MAX_LEAVES], lphase = new float[MAX_LEAVES];
    private final float[] lspin = new float[MAX_LEAVES], lrot = new float[MAX_LEAVES], lsize = new float[MAX_LEAVES];
    private final float[] la = new float[MAX_LEAVES];
    private final Color[] leafColor = new Color[MAX_LEAVES];

    private final float[] fx = new float[MAX_FLIES], fy = new float[MAX_FLIES], fangle = new float[MAX_FLIES];
    private final float[] fphase = new float[MAX_FLIES], fa = new float[MAX_FLIES];

    private final BufferedImage flyGlow;
    private final Ellipse2D.Float leafShape = new Ellipse2D.Float();

    public AmbientFX(Gamepanel gp){
        this.gp = gp;
        Color[] palette = {Palette.ROOF_THATCH, Palette.ROOF_CORAL, Palette.FOLIAGE_LIGHT, Palette.GOLD};
        for(int i = 0; i < MAX_LEAVES; i++){
            lx[i] = rnd.nextFloat() * gp.screenWidth;
            ly[i] = rnd.nextFloat() * gp.screenHeight;
            lphase[i] = rnd.nextFloat() * 6.28f;
            lspin[i] = (rnd.nextFloat() - 0.5f) * 0.08f;
            lrot[i] = rnd.nextFloat() * 6.28f;
            lsize[i] = 2.6f + rnd.nextFloat() * 2f;
            leafColor[i] = palette[i % palette.length];
        }
        for(int i = 0; i < MAX_FLIES; i++){
            fx[i] = rnd.nextFloat() * gp.screenWidth;
            fy[i] = rnd.nextFloat() * gp.screenHeight;
            fangle[i] = rnd.nextFloat() * 6.28f;
            fphase[i] = rnd.nextFloat() * 6.28f;
        }

        flyGlow = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = flyGlow.createGraphics();
        g.setPaint(new RadialGradientPaint(new Point2D.Float(16, 16), 16, new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(255, 245, 150, 230), new Color(255, 245, 150, 90), new Color(255, 245, 150, 0)}));
        g.fillRect(0, 0, 32, 32);
        g.dispose();
    }

    private static double wrap(double v, double min, double span){
        double m = (v - min) % span;
        if(m < 0) m += span;
        return min + m;
    }

    // A gentle breeze moves across the map, so nearby trees lean with slightly different timing.
    double treeSway(int x, int y){
        double phase = time * 0.035 - x * 0.011 - y * 0.007;
        return (0.009 + 0.009 * wind) * Math.sin(phase)
                + 0.004 * Math.sin(time * 0.017 + x * 0.003);
    }

    public void update(){
        time++;
        Player cam = gp.localPlayer();
        if(cam == null) return;

        wind = (float) Math.max(0, Math.min(1, 0.45 + 0.30 * Math.sin(time * 0.0021) + 0.18 * Math.sin(time * 0.0113 + 1.7)));

        // particles live in an area a little bigger than the screen and wrap around it as the camera moves
        double w = gp.screenWidth + 240, h = gp.screenHeight + 240;
        double left = cam.worldX - cam.screenX - 120, top = cam.worldY - cam.screenY - 120;

        int leafActive = Math.round(gp.lighting.leafLevel * MAX_LEAVES);
        for(int i = 0; i < MAX_LEAVES; i++){
            float target = i < leafActive ? 1f : 0f;
            la[i] += (target - la[i]) * 0.03f;
            if(la[i] < 0.01f && target == 0f){
                la[i] = 0;
                continue;
            }
            lx[i] += wind * 1.1f + 0.25f + (float) Math.sin(time * 0.02 + lphase[i]) * 0.5f;
            ly[i] += 0.35f + 0.25f * (float) Math.cos(time * 0.017 + lphase[i] * 1.3);
            lrot[i] += lspin[i];
            lx[i] = (float) wrap(lx[i], left, w);
            ly[i] = (float) wrap(ly[i], top, h);
        }

        int flyActive = Math.round(gp.lighting.flyLevel * MAX_FLIES);
        for(int i = 0; i < MAX_FLIES; i++){
            float target = i < flyActive ? 1f : 0f;
            fa[i] += (target - fa[i]) * 0.03f;
            if(fa[i] < 0.01f && target == 0f){
                fa[i] = 0;
                continue;
            }
            fangle[i] += (rnd.nextFloat() - 0.5f) * 0.18f;
            fx[i] += (float) Math.cos(fangle[i]) * 0.45f + wind * 0.15f;
            fy[i] += (float) Math.sin(fangle[i]) * 0.35f + (float) Math.sin(time * 0.03 + fphase[i]) * 0.12f;
            fx[i] = (float) wrap(fx[i], left, w);
            fy[i] = (float) wrap(fy[i], top, h);
        }
    }

    // in the world, above the entities and below the lighting
    public void drawLeaves(Graphics2D g2){
        Player cam = gp.localPlayer();
        if(cam == null) return;

        AffineTransform old = g2.getTransform();
        for(int i = 0; i < MAX_LEAVES; i++){
            if(la[i] < 0.02f) continue;
            int sx = Math.round(lx[i]) - cam.worldX + cam.screenX;
            int sy = Math.round(ly[i]) - cam.worldY + cam.screenY;
            if(sx < -10 || sx > gp.screenWidth + 10 || sy < -10 || sy > gp.screenHeight + 10) continue;

            Color c = leafColor[i];
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (215 * la[i])));
            g2.translate(sx, sy);
            g2.rotate(lrot[i]);
            leafShape.setFrame(-lsize[i], -lsize[i] * 0.55f, lsize[i] * 2, lsize[i] * 1.1f);
            g2.fill(leafShape);
            g2.setTransform(old);
        }
    }

    // screen space, above the darkness so they actually glow
    public void drawFireflies(Graphics2D g2, int camLeft, int camTop){
        Composite old = g2.getComposite();
        for(int i = 0; i < MAX_FLIES; i++){
            if(fa[i] < 0.02f) continue;
            float sx = fx[i] - camLeft, sy = fy[i] - camTop;
            if(sx < -20 || sx > gp.screenWidth + 20 || sy < -20 || sy > gp.screenHeight + 20) continue;

            float pulse = 0.55f + 0.45f * (float) Math.sin(time * 0.06 + fphase[i]);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, fa[i] * pulse)));
            g2.drawImage(flyGlow, Math.round(sx) - 16, Math.round(sy) - 16, null);
            g2.setColor(new Color(255, 252, 200));
            g2.fillOval(Math.round(sx) - 1, Math.round(sy) - 1, 3, 3);
        }
        g2.setComposite(old);
    }
}
