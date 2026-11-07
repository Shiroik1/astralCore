package art;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

// Tufts of grass baked at nine lean angles, so swaying and bending cost one plain drawImage per tuft.
public class GrassArt {
    public static final int TYPES = 3;                    // 0 short, 1 tall, 2 flowers
    public static final int[] VARIANTS = {4, 4, 5};
    public static final int BEND_FRAMES = 9;              // index 4 = upright, 0 = leaning left, 8 = leaning right

    private static final double W = 40, H = 42;           // canvas, in 48-unit tile space
    private final double k;
    public final int footX, footY;                        // the tuft's root, in pixels inside every frame
    private final BufferedImage[][][] frames = new BufferedImage[TYPES][][];

    public GrassArt(int tileSize){
        k = tileSize / 48.0;
        footX = (int) Math.round(W / 2 * k);
        footY = (int) Math.round((H - 3) * k);
        for(int t = 0; t < TYPES; t++){
            frames[t] = new BufferedImage[VARIANTS[t]][BEND_FRAMES];
            for(int v = 0; v < VARIANTS[t]; v++){
                for(int b = 0; b < BEND_FRAMES; b++){
                    double bend = (b - (BEND_FRAMES - 1) / 2.0) / ((BEND_FRAMES - 1) / 2.0); // -1 .. 1
                    frames[t][v][b] = bake(t, v, bend);
                }
            }
        }
    }

    public BufferedImage frame(int type, int variant, int bend){
        return frames[type][variant][bend];
    }

    private BufferedImage bake(int type, int variant, double bend){
        ArtKit.Surface s = new ArtKit.Surface((int) Math.round(W * k), (int) Math.round(H * k));
        Graphics2D g = s.g;
        g.scale(k, k);

        Random rnd = new Random(type * 1000L + variant * 37L + 11); // same seed for every lean, so only the lean changes
        int blades = (type == 1) ? 7 : 5;
        double baseH = (type == 1) ? 26 : 13;
        double spread = (type == 1) ? 13 : 10;
        double cx = W / 2, rootY = H - 3;

        double[] rootX = new double[blades], height = new double[blades], natural = new double[blades];
        for(int i = 0; i < blades; i++){
            double t = (blades == 1) ? 0.5 : i / (double) (blades - 1);
            rootX[i] = cx + (t - 0.5) * spread + (rnd.nextDouble() - 0.5) * 2;
            height[i] = baseH * (0.72 + rnd.nextDouble() * 0.38);
            natural[i] = (t - 0.5) * 6 + (rnd.nextDouble() - 0.5) * 3;
        }

        boolean[] flowered = new boolean[blades];
        if(type == 2){                                   // three blades grow taller and carry a flower
            for(int n = 0; n < 3; n++){
                int i = (variant + n * 2) % blades;
                flowered[i] = true;
                height[i] = 17 + n * 1.5;
            }
        }

        Color[] tones = (type == 1)
                ? new Color[]{Palette.FOLIAGE_DARK, ArtKit.mix(Palette.FOLIAGE_DARK, Palette.FOLIAGE, 0.5f), Palette.FOLIAGE}
                : new Color[]{ArtKit.mix(Palette.FOLIAGE_DARK, Palette.FOLIAGE, 0.35f), Palette.FOLIAGE,
                ArtKit.mix(Palette.FOLIAGE, Palette.FOLIAGE_LIGHT, 0.5f)};

        double[] tipX = new double[blades], tipY = new double[blades];
        for(int pass = 0; pass < 3; pass++){             // dark blades first, bright ones in front
            for(int i = pass; i < blades; i += 3){
                double lean = natural[i] + bend * height[i] * 0.55;
                double h = height[i] * (1 - 0.15 * Math.abs(bend));
                ArtKit.blade(g, rootX[i], rootY, h, lean, 2.8, tones[pass]);
                tipX[i] = rootX[i] + lean;
                tipY[i] = rootY - h;
            }
        }
        for(int i = 0; i < blades; i++){
            if(flowered[i]){
                Color petal = Palette.FLOWERS[(variant + i) % Palette.FLOWERS.length];
                ArtKit.flower(g, tipX[i], tipY[i] - 0.5, 2.7, petal, Palette.GOLD);
            }
        }
        return s.finish();
    }
}