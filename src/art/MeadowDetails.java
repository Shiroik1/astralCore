package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** Small flat meadow decorations, built once with the shared art palette. */
public final class MeadowDetails {
    private MeadowDetails() {}

    /**
     * Builds one three-blade tuft, with short, closely grouped blades tinted to the local ground.
     */
    public static BufferedImage grass(int shade, int level, int bend, int tileSize) {
        var image = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(tileSize / 48.0, tileSize / 48.0);
        Color ground = Palette.GROUND[level];
        g.setColor(
                ArtKit.mix(
                        ground,
                        shade == 2 ? Palette.ROOF_THATCH : Palette.FOLIAGE_DARK,
                        shade == 2 ? .58f : shade == 1 ? .29f : .17f));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double lean = (bend / 24.0 * 2 - 1) * 2;
        for (int i = -1; i <= 1; i++) {
            double height = (i == 0 ? 18 : 13) * 1.10;
            g.draw(
                    new QuadCurve2D.Double(
                            24 + i * 2, 44,
                            24 + i * 3 + lean * .4, 44 - height * .6,
                            24 + i * 5 + lean, 44 - height));
        }
        g.dispose();
        return image;
    }

    /** Draws a patch of blossoms only: no foliage, stems or ground backing. */
    public static BufferedImage flowers(int variant, int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(width / 64.0, height / 40.0);
        Color[] colors = {
            Palette.FLOWERS[1],
            Palette.FLOWERS[0],
            Palette.FLOWERS[3],
            Palette.FLOWERS[2],
            Palette.WATER
        };
        int count = variant == 5 ? 5 : 3 + variant % 2;
        double[] xs = {17, 39, 28, 48, 11};
        double[] ys = {19, 15, 30, 29, 32};
        for (int i = 0; i < count; i++) {
            double x = xs[i], y = ys[i];
            Color petals = colors[variant < 5 ? variant : i % colors.length];
            GenerateGrassTextures.blossom(g, x, y, 2.4 + (i * 3 + variant) % 5 * .55, petals);
        }
        g.dispose();
        return image;
    }
}
