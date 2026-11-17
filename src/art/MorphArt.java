package art;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Shared temporary frog silhouette; no per-frame image allocation. */
public final class MorphArt {
    public static final BufferedImage FROG = create();

    /** Prevents instantiation of this utility class. */
    private MorphArt() {}

    /** Resolves the create result used by MorphArt. */
    private static BufferedImage create() {
        BufferedImage out = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Palette.FOLIAGE_DARK);
        g.fillOval(7, 25, 34, 18);
        g.setColor(Palette.FOLIAGE_LIGHT);
        g.fillOval(10, 22, 28, 19);
        g.fillOval(11, 16, 10, 12);
        g.fillOval(27, 16, 10, 12);
        g.setColor(Palette.PLASTER);
        g.fillOval(13, 18, 6, 7);
        g.fillOval(29, 18, 6, 7);
        g.setColor(Palette.IRON);
        g.fillOval(15, 20, 3, 4);
        g.fillOval(30, 20, 3, 4);
        g.dispose();
        return out;
    }
}
