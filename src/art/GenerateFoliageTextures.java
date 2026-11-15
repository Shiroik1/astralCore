package art;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Exports the transparent foliage sprites. Seasonal trees are kept in separate directories. */
public final class GenerateFoliageTextures {
    private static final int TILE = GroundTiles.ART_TILE_SIZE;
    private static final Color BARK = Palette.TRUNK;
    private static final Color BARK_SHADE = ArtKit.shade(BARK);
    private static final Color BARK_LIGHT = ArtKit.light(BARK);
    private static final Color SHADOW = new Color(46, 84, 70, 52);
    private static final Shape LEAF_CLUSTER = leafCluster();
    private static final Shape NEEDLES = needleTier();

    private enum Season { GREEN, SNOWY, YELLOW }

    private record Leaves(Color deep, Color shade, Color base, Color light, Color tips) {}

    private static final Color PINE_UPPER = ArtKit.mix(Palette.FOLIAGE_DARK, Palette.FOLIAGE, 0.6f);
    private static final Leaves PINE = new Leaves(ArtKit.shade(Palette.FOLIAGE_DARK), Palette.FOLIAGE_DARK,
            ArtKit.mix(Palette.FOLIAGE_DARK, Palette.FOLIAGE, 0.3f), PINE_UPPER, ArtKit.light(PINE_UPPER));
    private static final Leaves OAK = new Leaves(ArtKit.shade(Palette.FOLIAGE), Palette.FOLIAGE_DARK,
            Palette.FOLIAGE, ArtKit.mix(Palette.FOLIAGE, Palette.FOLIAGE_LIGHT, 0.42f), Palette.FOLIAGE_LIGHT);
    private static final Leaves GOLD_PINE = leaves(0x786D50, 0x91815A, 0xAA925E, 0xC5A466, 0xD8B779);
    private static final Leaves GOLD_OAK = leaves(0x7A6449, 0x9A7751, 0xB98D58, 0xD0A365, 0xE1B677);
    private static final Leaves SNOW = leaves(0x646E77, 0x8996A3, 0xB8C4CE, 0xD7E1E6, 0xEDF0ED);

    private GenerateFoliageTextures() {}

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length == 0 ? "res/scenery/foliage" : args[0]);
        for (Season season : Season.values()) {
            Path directory = season == Season.GREEN ? output : output.resolve(season.name().toLowerCase(Locale.ROOT));
            Files.createDirectories(directory);
            for (int variant = 1; variant <= 2; variant++) {
                write(directory, "pine_" + variant, pine(variant, season));
                write(directory, "oak_" + variant, oak(variant, season));
            }
        }
        write(output, "bush", bush(false));
        write(output, "flower_bush", bush(true));
    }

    private static void write(Path directory, String name, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
    }

    private static Color color(int rgb) {
        return new Color(rgb);
    }

    private static Leaves leaves(int deep, int shade, int base, int light, int tips) {
        return new Leaves(color(deep), color(shade), color(base), color(light), color(tips));
    }

    private static void oval(Graphics2D g, Color fill, double x, double y, double rx, double ry) {
        g.setColor(fill);
        g.fill(new Ellipse2D.Double(x - rx, y - ry, rx * 2, ry * 2));
    }

    private static void fill(Graphics2D g, Color color, Path2D path) {
        path.closePath();
        g.setColor(color);
        g.fill(path);
    }

    private static void stamp(Graphics2D g, Shape shape, Color fill, double x, double y, double sx, double sy) {
        g.setColor(fill);
        g.fill(new AffineTransform(sx, 0, 0, sy, x, y).createTransformedShape(shape));
    }

    private static BufferedImage pine(int variant, Season season) {
        ArtKit.Surface sprite = new ArtKit.Surface(2 * TILE, 4 * TILE);
        Graphics2D g = sprite.g;
        g.translate(48, 180);
        if (variant == 2) g.scale(-0.88, 0.87);
        oval(g, SHADOW, 4, 0, 34, 5.5);
        pineTrunk(g);

        Leaves pine = season == Season.YELLOW ? GOLD_PINE : PINE;
        pineBough(g, pine, -2, -98, 73, 44, pine.shade, false);
        if (season == Season.SNOWY) {
            cluster(g, SNOW.base, -24, -44, 8, 2.8);
            cluster(g, SNOW.light, 18, -39, -7, 2.6);
        }
        pineBough(g, pine, 1, -123, 75, 36, pine.base, season == Season.SNOWY);
        pineBough(g, pine, -1, -146, 75, 29, pine.light, false);
        pineBough(g, pine, -2, -172, 66, 21, pine.light, season == Season.SNOWY);
        if (season == Season.SNOWY) {
            oval(g, SNOW.light, -16, 0, 8, 2);
            oval(g, SNOW.tips, 13, 1, 9, 2);
        }
        return sprite.finish();
    }

    private static void pineTrunk(Graphics2D g) {
        Path2D trunk = new Path2D.Double();
        trunk.moveTo(-8, -48);
        trunk.curveTo(-4, -45, 4, -44, 8, -48);
        trunk.curveTo(9, -29, 6, -11, 15, -3);
        trunk.quadTo(17, -1, 15, 0);
        trunk.quadTo(9, 1, 5, -1);
        trunk.quadTo(1, 3, -5, 0);
        trunk.quadTo(-12, 2, -16, -1);
        trunk.curveTo(-6, -10, -9, -24, -8, -48);
        fill(g, BARK, trunk);

        Path2D shade = new Path2D.Double();
        shade.moveTo(-8, -42);
        shade.quadTo(-5, -43, 0, -40);
        shade.curveTo(-1, -28, -6, -10, -5, 0);
        shade.quadTo(-12, 2, -16, -1);
        shade.curveTo(-6, -10, -9, -24, -8, -42);
        fill(g, BARK_SHADE, shade);
    }

    private static void pineBough(Graphics2D g, Leaves colors, double x, double y, double height,
                                  double width, Color face, boolean snowy) {
        stamp(g, NEEDLES, ArtKit.mix(colors.deep, face, 0.45f), x, y + 5, width, height);
        stamp(g, NEEDLES, face, x - 1, y, width * 0.95, height * 0.91);
        // A shallow plane follows the lit left side of the bough.
        Path2D plane = new Path2D.Double();
        plane.moveTo(x - 2, y + 4);
        plane.curveTo(x - width * 0.34, y + height * 0.31, x - width * 0.62, y + height * 0.67,
                x - width * 0.90, y + height * 0.78);
        plane.curveTo(x - width * 0.69, y + height * 0.84, x - width * 0.41, y + height * 0.69,
                x - width * 0.31, y + height * 0.61);
        plane.quadTo(x - width * 0.30, y + height * 0.39, x - 2, y + 4);
        fill(g, ArtKit.mix(face, colors.tips, 0.40f), plane);
        if (snowy) {
            stamp(g, NEEDLES, SNOW.base, x - 1, y - 1, width * 0.92, height * 0.88);
            stamp(g, NEEDLES, SNOW.tips, x - 2, y - 2, width * 0.85, height * 0.81);
        }
    }

    private static Shape needleTier() {
        Path2D p = new Path2D.Double();
        p.moveTo(-0.04, 0.02);
        p.quadTo(0, -0.025, 0.08, 0.055);
        p.curveTo(0.25, 0.31, 0.60, 0.62, 0.94, 0.76);
        p.curveTo(1.10, 0.84, 0.87, 0.90, 0.71, 0.84);
        p.curveTo(0.86, 0.98, 0.63, 1.00, 0.39, 0.87);
        p.curveTo(0.49, 1.00, 0.31, 1.05, 0.09, 0.93);
        p.curveTo(0.03, 1.08, -0.13, 1.03, -0.27, 0.89);
        p.curveTo(-0.39, 1.00, -0.62, 1.01, -0.56, 0.85);
        p.curveTo(-0.75, 0.96, -0.92, 0.94, -0.83, 0.81);
        p.curveTo(-1.04, 0.85, -1.11, 0.77, -0.96, 0.71);
        p.curveTo(-0.58, 0.51, -0.26, 0.26, -0.04, 0.02);
        p.closePath();
        return p;
    }

    private static BufferedImage oak(int variant, Season season) {
        ArtKit.Surface sprite = new ArtKit.Surface(3 * TILE, 4 * TILE);
        Graphics2D g = sprite.g;
        g.translate(72, 178);
        if (variant == 2) g.scale(-0.85, 0.89);
        Leaves leaves = season == Season.YELLOW ? GOLD_OAK : OAK;
        boolean snowy = season == Season.SNOWY;
        oval(g, SHADOW, 6, 1, 47, 6);

        // The outer crown and lower leaf masses sit behind the branches.
        cluster(g, leaves.deep, -42, -74, 26, 24);
        cluster(g, leaves.shade, 37, -77, -30, 27);
        cluster(g, leaves.deep, 52, -92, -18, 24);
        cluster(g, leaves.deep, -17, -82, 35, 29);
        cluster(g, leaves.shade, -46, -110, 22, 28);
        cluster(g, leaves.base, 38, -119, -26, 30);
        cluster(g, leaves.base, -17, -134, 33, 29);
        cluster(g, leaves.light, 12, -141, -29, 31);

        oakTrunk(g);

        // Let the branch forks show between the scalloped foreground leaf groups.
        crown(g, leaves.shade, leaves.base, -42, -84, 25, 19);
        crown(g, leaves.shade, leaves.base, 45, -83, -22, 19);
        crown(g, leaves.base, snowy ? SNOW.base : leaves.light, -42, -112, 28, 21);
        crown(g, leaves.shade, snowy ? SNOW.light : leaves.light, 43, -111, -23, 20);
        crown(g, leaves.base, snowy ? SNOW.light : leaves.light, -10, -121, 32, 25);
        crown(g, leaves.shade, snowy ? SNOW.base : leaves.base, 25, -80, -20, 16);
        crown(g, leaves.base, snowy ? SNOW.tips : leaves.tips, -8, -147, -28, 21);
        crown(g, leaves.base, snowy ? SNOW.light : leaves.light, 25, -137, 26, 20);
        if (snowy) {
            oval(g, SNOW.light, -22, 0, 10, 2.5);
            oval(g, SNOW.tips, 21, 1, 12, 2);
        }
        return sprite.finish();
    }

    private static void oakTrunk(Graphics2D g) {
        Path2D bark = new Path2D.Double();
        bark.moveTo(-24, 0);
        bark.curveTo(-10, -9, -11, -23, -11, -40);
        bark.curveTo(-11, -48, -18, -55, -28, -61);
        bark.quadTo(-44, -63, -50, -84);
        bark.lineTo(-44, -89);
        bark.quadTo(-38, -73, -23, -71);
        bark.quadTo(-15, -68, -9, -59);
        bark.curveTo(-11, -77, -8, -90, -17, -107);
        bark.lineTo(-6, -111);
        bark.quadTo(1, -98, 2, -80);
        bark.quadTo(15, -87, 18, -103);
        bark.lineTo(28, -100);
        bark.curveTo(23, -82, 9, -76, 11, -56);
        bark.quadTo(26, -60, 32, -74);
        bark.lineTo(40, -72);
        bark.quadTo(36, -56, 17, -48);
        bark.curveTo(10, -42, 13, -24, 17, -12);
        bark.quadTo(20, -4, 28, -1);
        bark.quadTo(21, 1, 14, -1);
        bark.quadTo(9, 4, 4, 1);
        bark.quadTo(0, -4, -5, 0);
        bark.quadTo(-10, 3, -14, -1);
        bark.quadTo(-20, 2, -24, 0);
        fill(g, BARK, bark);

        Path2D shade = new Path2D.Double();
        shade.moveTo(-24, 0);
        shade.curveTo(-10, -9, -11, -23, -11, -40);
        shade.curveTo(-11, -48, -18, -55, -28, -61);
        shade.quadTo(-44, -63, -50, -84);
        shade.lineTo(-44, -89);
        shade.quadTo(-39, -71, -24, -67);
        shade.curveTo(-5, -62, -3, -49, -4, -31);
        shade.quadTo(-4, -13, -14, -1);
        shade.quadTo(-20, 2, -24, 0);
        fill(g, BARK_SHADE, shade);

        Path2D grain = new Path2D.Double();
        grain.moveTo(4, -51);
        grain.curveTo(0, -38, 0, -17, 5, -9);
        grain.curveTo(4, -22, 4, -42, 4, -51);
        fill(g, BARK_LIGHT, grain);
    }

    private static void cluster(Graphics2D g, Color fill, double x, double y, double width, double height) {
        stamp(g, LEAF_CLUSTER, fill, x, y, width, height);
    }

    private static void crown(Graphics2D g, Color shade, Color light, double x, double y, double w, double h) {
        cluster(g, shade, x, y, w, h);
        cluster(g, light, x - 2, y - 4, w * 0.89, h * 0.81);
    }

    private static Shape leafCluster() {
        Path2D p = new Path2D.Double();
        p.moveTo(-0.98, 0.07);
        p.curveTo(-1.13, -0.18, -0.94, -0.47, -0.70, -0.48);
        p.curveTo(-0.75, -0.73, -0.51, -0.91, -0.29, -0.78);
        p.curveTo(-0.17, -1.06, 0.20, -1.10, 0.35, -0.83);
        p.curveTo(0.56, -0.99, 0.80, -0.83, 0.79, -0.59);
        p.curveTo(1.03, -0.55, 1.09, -0.31, 0.95, -0.18);
        p.curveTo(1.17, -0.02, 1.06, 0.29, 0.83, 0.32);
        p.curveTo(0.94, 0.59, 0.63, 0.79, 0.43, 0.67);
        p.curveTo(0.42, 0.97, 0.12, 1.03, -0.08, 0.86);
        p.curveTo(-0.30, 1.07, -0.55, 0.89, -0.55, 0.71);
        p.curveTo(-0.85, 0.82, -1.07, 0.53, -0.90, 0.37);
        p.curveTo(-1.07, 0.33, -1.12, 0.15, -0.98, 0.07);
        p.closePath();
        return p;
    }

    private static BufferedImage bush(boolean flowers) {
        ArtKit.Surface sprite = new ArtKit.Surface(2 * TILE, TILE);
        Graphics2D g = sprite.g;
        g.translate(48, 44);
        if (flowers) g.scale(0.90, 1);
        oval(g, SHADOW, 4, 0, 35, 4);
        cluster(g, OAK.deep, -14, -15, 24, 15);
        cluster(g, OAK.deep, 16, -16, -25, 17);
        crown(g, OAK.shade, OAK.base, -22, -16, -16, 14);
        crown(g, OAK.shade, OAK.base, 22, -15, 17, 13);
        crown(g, OAK.base, OAK.light, -4, -24, 22, 18);
        cluster(g, OAK.shade, 12, -9, -15, 8);
        cluster(g, OAK.base, 10, -11, -13, 7);
        if (flowers) {
            GenerateGrassTextures.blossom(g, -17, -18, 3.1, Palette.FLOWERS[1]);
            GenerateGrassTextures.blossom(g, -5, -29, 3.6, Palette.FLOWERS[0]);
            GenerateGrassTextures.blossom(g, 17, -17, 3.3, Palette.FLOWERS[1]);
        }
        return sprite.finish();
    }
}
