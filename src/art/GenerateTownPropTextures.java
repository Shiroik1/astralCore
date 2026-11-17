package art;

import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** Offline source for Astravale's town prop PNGs. */
public final class GenerateTownPropTextures {
    private static final int WOOD = Palette.WOOD.getRGB(), TIMBER = Palette.TIMBER.getRGB();
    private static final int WOOD_LIGHT = 0xC9A17A, END_GRAIN = 0xDBB587;
    private static final int STONE = 0xB5A99D, STONE_LIGHT = 0xC9BCAE, STONE_DARK = 0x9B8C81;
    private static final int TERRACOTTA = 0xC48578, ROOF_SHADE = 0xAB6E63;
    private static final int IRON = 0x706372, GLOW = 0xF9D791;
    private static final Color SHADOW = new Color(46, 84, 70, 60);

    /** Prevents instantiation of this utility class. */
    private GenerateTownPropTextures() {}

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length == 0 ? "res/scenery/props" : args[0]);
        Files.createDirectories(output);
        for (TownProp prop : TownProp.values()) {
            var surface =
                    new ArtKit.Surface(
                            prop.width * TownProp.EXPORT_SCALE,
                            prop.height * TownProp.EXPORT_SCALE);
            surface.g.scale(TownProp.EXPORT_SCALE, TownProp.EXPORT_SCALE);
            paint(surface.g, prop);
            ImageIO.write(
                    surface.finish(), "png", output.resolve(prop.assetName() + ".png").toFile());
        }
    }

    /** Performs the paint operation for GenerateTownPropTextures. */
    private static void paint(Graphics2D g, TownProp prop) {
        if (prop != TownProp.FENCE_H && prop != TownProp.FENCE_V) {
            g.setColor(SHADOW);
            double width = prop.solidWidth * 1.08;
            g.fill(new Ellipse2D.Double(prop.footX - width / 2, prop.footY - 4, width, 8));
        }
        switch (prop) {
            case WELL -> well(g);
            case LANTERN, LANTERN_HANGING -> lanternPost(g, prop == TownProp.LANTERN_HANGING);
            case CRATE -> crate(g, 7, 10, 34, 32);
            case CRATE_STACK -> {
                crate(g, 9, 29, 38, 32);
                crate(g, 14, 8, 29, 28);
            }
            case BARREL -> barrel(g, 24, 7, 30, 35);
            case BARREL_STACK -> {
                barrel(g, 22, 29, 29, 32);
                barrel(g, 50, 29, 29, 32);
                barrel(g, 35, 6, 29, 33);
            }
            case FENCE_SHORT, FENCE_TALL -> fence(g, prop == TownProp.FENCE_TALL);
            case FENCE_H -> {
                rail(g, 0, 19, 48);
                rail(g, 0, 30, 48);
                fencePost(g, 24, 12, 44);
            }
            case FENCE_V -> {
                round(g, 21, -5, 6, 58, 3, TIMBER);
                round(g, 21, -5, 3, 58, 2, WOOD);
                fencePost(g, 24, 12, 42);
            }
            case LOGS_COVERED, LOGS_STACKED -> woodpile(g, prop == TownProp.LOGS_COVERED);
            case ROCK_SMALL, ROCK_SMALL_MOSS -> {
                rock(g, 6, 22, 29, 19, prop == TownProp.ROCK_SMALL_MOSS);
                rock(g, 31, 32, 13, 9, false);
            }
            case ROCK_LARGE, ROCK_LARGE_MOSS -> {
                rock(g, 22, 38, 56, 50, prop == TownProp.ROCK_LARGE_MOSS);
                rock(g, 10, 69, 27, 19, false);
                rock(g, 67, 66, 21, 22, false);
            }
        }
    }

    /** Performs the well operation for GenerateTownPropTextures. */
    private static void well(Graphics2D g) {
        round(g, 18, 55, 60, 27, 16, STONE_DARK);
        round(g, 18, 54, 60, 20, 13, STONE);
        line(g, STONE_DARK, 1.2f, 21, 69, 30, 73, 48, 76, 66, 73, 75, 69);
        for (int x : new int[] {30, 48, 66}) line(g, STONE_DARK, 1.1f, x, 72, x, 80);
        oval(g, 18, 45, 60, 24, STONE_LIGHT);
        oval(g, 27, 50, 42, 13, 0x645963);
        oval(g, 33, 54, 30, 7, 0x758F99);
        for (int x : new int[] {24, 65}) {
            round(g, x, 25, 8, 34, 2, WOOD);
            round(g, x + 5, 26, 3, 32, 2, TIMBER);
        }
        round(g, 31, 39, 34, 5, 2, TIMBER);
        for (int x = 43; x <= 52; x += 3) round(g, x, 38, 2.5, 7, 2, END_GRAIN);
        line(g, 0xB99269, 1.4f, 48, 43, 48, 52);
        line(g, END_GRAIN, 1.3f, 43, 56, 48, 51, 53, 56);
        round(g, 42, 55, 12, 9, 4, WOOD);
        oval(g, 42, 54, 12, 4, END_GRAIN);
        oval(g, 44, 55, 8, 2, TIMBER);

        round(g, 13, 25, 70, 6, 4, WOOD_LIGHT);
        polygon(g, ROOF_SHADE, 21, 7, 75, 7, 85, 25, 84, 28, 12, 28, 11, 25);
        polygon(g, TERRACOTTA, 21, 7, 75, 7, 84, 24, 12, 24);
        round(g, 20, 6, 56, 4, 4, 0xD09283);
        line(g, 0xB27A6F, 1.1f, 17, 16, 79, 16);
        line(g, 0xB27A6F, 1.0f, 34, 10, 32, 16);
        line(g, 0xB27A6F, 1.0f, 60, 10, 62, 16);
        for (int x : new int[] {24, 47, 70}) line(g, 0xB27A6F, 1.0f, x, 16, x - 1, 23);
    }

    /** Performs the lantern post operation for GenerateTownPropTextures. */
    private static void lanternPost(Graphics2D g, boolean hanging) {
        int x = hanging ? 21 : 24;
        round(g, x - 10, 83, 20, 7, 4, STONE_DARK);
        round(g, x - 7, 77, 14, 10, 4, STONE_LIGHT);
        round(g, x - 4, hanging ? 10 : 32, 8, hanging ? 69 : 47, 2, WOOD);
        round(g, x + 1, hanging ? 12 : 34, 3, hanging ? 66 : 44, 2, TIMBER);
        if (hanging) {
            round(g, 17, 10, 35, 7, 3, WOOD_LIGHT);
            polygon(g, TIMBER, 22, 27, 22, 21, 34, 13, 41, 13);
            round(g, 16, 9, 11, 9, 3, STONE);
            round(g, 43, 11, 6, 10, 2, STONE);
            line(g, IRON, 2, 46, 20, 46, 28);
            lanternHead(g, 46, 37);
        } else {
            lanternHead(g, 24, 24);
        }
    }

    /** Performs the lantern head operation for GenerateTownPropTextures. */
    private static void lanternHead(Graphics2D g, int x, int y) {
        polygon(g, IRON, x - 9, y - 8, x + 9, y - 8, x + 6, y + 9, x - 6, y + 9);
        polygon(g, 0xDBA967, x - 6, y - 6, x + 6, y - 6, x + 4, y + 7, x - 4, y + 7);
        round(g, x - 3, y - 4, 6, 10, 3, GLOW);
        polygon(g, 0x795F63, x - 10, y - 8, x, y - 16, x + 10, y - 8);
        round(g, x - 3, y - 18, 6, 5, 4, WOOD_LIGHT);
        round(g, x - 7, y + 7, 14, 4, 3, IRON);
    }

    /** Performs the crate operation for GenerateTownPropTextures. */
    private static void crate(Graphics2D g, double x, double y, double w, double h) {
        double depth = w * .21;
        polygon(g, TIMBER, x + w - depth, y + 6, x + w, y, x + w, y + h - 7, x + w - depth, y + h);
        polygon(g, WOOD_LIGHT, x, y + 7, x + depth, y, x + w, y, x + w - depth, y + 7);
        polygon(
                g,
                0x9D795B,
                x + 6,
                y + 5,
                x + depth + 3,
                y + 2,
                x + w - 5,
                y + 2,
                x + w - depth - 4,
                y + 5);
        round(g, x, y + 6, w - depth, h - 6, 3, WOOD);
        rect(g, x + 4, y + 11, w - depth - 8, h - 16, 0x9F795B);
        line(g, WOOD, 1.2f, x + 10, y + 11, x + 10, y + h - 5);
        line(g, WOOD, 1.2f, x + w - depth - 10, y + 11, x + w - depth - 10, y + h - 5);
        polygon(
                g,
                WOOD_LIGHT,
                x + 4,
                y + h - 6,
                x + 8,
                y + h - 5,
                x + w - depth - 4,
                y + 15,
                x + w - depth - 5,
                y + 10);
        round(g, x, y + 6, w - depth, 4, 3, WOOD_LIGHT);
        round(g, x, y + h - 5, w - depth, 5, 3, WOOD_LIGHT);
        for (double peg : new double[] {x + 3, x + w - depth - 5})
            oval(g, peg, y + h - 3.5, 2, 2, TIMBER);
    }

    /** Performs the barrel operation for GenerateTownPropTextures. */
    private static void barrel(Graphics2D g, int x, int y, int w, int h) {
        double r = w / 2.0;
        var body = new Path2D.Double();
        body.moveTo(x - r + 3, y + 5);
        body.curveTo(x - r - 1, y + h * .4, x - r, y + h * .79, x - r + 5, y + h - 3);
        body.quadTo(x, y + h + 3, x + r - 5, y + h - 3);
        body.curveTo(x + r, y + h * .79, x + r + 1, y + h * .4, x + r - 3, y + 5);
        body.closePath();
        fill(g, body, WOOD);
        Shape clip = g.getClip();
        g.clip(body);
        polygon(g, 0x9F795B, x + 2, y, x + r + 2, y, x + r + 3, y + h, x + 7, y + h);
        line(g, 0x8C6B58, 1.0f, x - 6, y + 8, x - 7, y + h * .57, x - 6, y + h - 2);
        line(g, 0x8C6B58, 1.0f, x + 7, y + 8, x + 8, y + h * .57, x + 6, y + h - 2);
        for (double band : new double[] {y + h * .33, y + h * .76}) {
            g.setColor(new Color(STONE_DARK));
            g.setStroke(new BasicStroke(4.2f));
            g.draw(new QuadCurve2D.Double(x - r - 2, band - 3, x, band + 6, x + r + 2, band - 3));
            oval(g, x - 1, band + 1, 2, 2, STONE_LIGHT);
        }
        g.setClip(clip);
        oval(g, x - r + 3, y, w - 6, 11, END_GRAIN);
        oval(g, x - r + 6, y + 2, w - 12, 7, TIMBER);
        oval(g, x - r + 7, y + 5, w - 14, 4, 0xA68062);
    }

    /** Performs the fence operation for GenerateTownPropTextures. */
    private static void fence(Graphics2D g, boolean tall) {
        rail(g, 11, tall ? 15 : 21, 58);
        if (tall) rail(g, 11, 23, 58);
        rail(g, 11, 31, 58);
        fencePost(g, 10, 12, 43);
        fencePost(g, 70, 12, 43);
    }

    /** Performs the rail operation for GenerateTownPropTextures. */
    private static void rail(Graphics2D g, int x, int y, int width) {
        round(g, x, y, width, 6, 2, TIMBER);
        rect(g, x, y, width, 2, WOOD_LIGHT);
    }

    /** Performs the fence post operation for GenerateTownPropTextures. */
    private static void fencePost(Graphics2D g, int x, int y, int bottom) {
        round(g, x - 6, bottom - 8, 12, 8, 4, STONE);
        round(g, x - 4, y + 4, 8, bottom - y - 10, 2, WOOD);
        round(g, x - 5, y, 10, 7, 4, WOOD_LIGHT);
    }

    /** Performs the woodpile operation for GenerateTownPropTextures. */
    private static void woodpile(Graphics2D g, boolean covered) {
        round(g, 65, 19, 7, 30, 3, WOOD);
        log(g, 21, 47, 9);
        log(g, 39, 47, 9);
        log(g, 57, 47, 9);
        log(g, 29, 32, 9);
        log(g, 47, 32, 9);
        log(g, 38, 18, 9);
        if (covered) {
            var cloth = new Path2D.Double();
            cloth.moveTo(21, 21);
            cloth.quadTo(25, 11, 37, 11);
            cloth.lineTo(61, 11);
            cloth.quadTo(73, 12, 74, 24);
            cloth.lineTo(69, 43);
            cloth.quadTo(68, 46, 65, 43);
            cloth.lineTo(57, 31);
            cloth.lineTo(48, 37);
            cloth.quadTo(46, 39, 43, 36);
            cloth.quadTo(30, 21, 21, 21);
            cloth.closePath();
            fill(g, cloth, 0xE8D9C0);
            polygon(g, 0xD2BEA4, 57, 31, 66, 20, 72, 21, 69, 43, 66, 43);
        }
        round(g, 8, 36, 7, 18, 3, WOOD);
        round(g, 66, 36, 7, 18, 3, WOOD);
        round(g, 8, 36, 7, 3, 2, WOOD_LIGHT);
        round(g, 66, 36, 7, 3, 2, WOOD_LIGHT);
    }

    /** Performs the log operation for GenerateTownPropTextures. */
    private static void log(Graphics2D g, int x, int y, int r) {
        polygon(
                g,
                TIMBER,
                x - r,
                y - 3,
                x + 4,
                y - r - 11,
                x + 19,
                y - r - 11,
                x + 20,
                y - 4,
                x + r,
                y + 3);
        oval(g, x - r, y - r, 2 * r, 2 * r, 0x946C54);
        oval(g, x - r + 2, y - r + 2, 2 * r - 4, 2 * r - 4, END_GRAIN);
        g.setColor(new Color(0xB7916D));
        g.setStroke(new BasicStroke(1));
        g.draw(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
    }

    /** Performs the rock operation for GenerateTownPropTextures. */
    private static void rock(Graphics2D g, int x, int y, int w, int h, boolean moss) {
        var original = g.getTransform();
        g.translate(x, y);
        g.scale(w / 36.0, h / 26.0);
        var shape = new Path2D.Double();
        shape.moveTo(0, 21);
        shape.quadTo(1, 14, 8, 6);
        shape.quadTo(13, 0, 20, 0);
        shape.quadTo(28, 0, 32, 6);
        shape.lineTo(36, 21);
        shape.quadTo(36, 26, 26, 26);
        shape.lineTo(7, 26);
        shape.quadTo(0, 25, 0, 21);
        shape.closePath();
        fill(g, shape, 0xADA4AE);
        Shape clip = g.getClip();
        g.clip(shape);
        polygon(g, 0xC7BEC4, 0, 16, 14, 0, 25, -1, 32, 7, 21, 13, 11, 16);
        polygon(g, 0x978C99, 32, 6, 36, 20, 36, 26, 22, 27, 21, 13);
        if (moss) {
            polygon(
                    g, 0x5F9D65, 0, 11, 10, -1, 27, -2, 34, 7, 29, 9, 26, 8, 22, 12, 17, 11, 13, 14,
                    8, 12, 4, 14);
            polygon(g, 0x82B876, 5, 6, 14, -1, 26, -2, 33, 4, 23, 7, 13, 9);
        }
        g.setClip(clip);
        g.setTransform(original);
    }

    /** Performs the rect operation for GenerateTownPropTextures. */
    private static void rect(Graphics2D g, double x, double y, double w, double h, int color) {
        fill(g, new Rectangle2D.Double(x, y, w, h), color);
    }

    /** Performs the round operation for GenerateTownPropTextures. */
    private static void round(
            Graphics2D g, double x, double y, double w, double h, double arc, int color) {
        fill(g, new RoundRectangle2D.Double(x, y, w, h, arc, arc), color);
    }

    /** Performs the oval operation for GenerateTownPropTextures. */
    private static void oval(Graphics2D g, double x, double y, double w, double h, int color) {
        fill(g, new Ellipse2D.Double(x, y, w, h), color);
    }

    /** Draws or constructs the polygon shape used by this artwork. */
    private static void polygon(Graphics2D g, int color, double... points) {
        Path2D path = path(points);
        path.closePath();
        fill(g, path, color);
    }

    /** Performs the line operation for GenerateTownPropTextures. */
    private static void line(Graphics2D g, int color, float width, double... points) {
        g.setColor(new Color(color));
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path(points));
    }

    /** Performs the fill operation for GenerateTownPropTextures. */
    private static void fill(Graphics2D g, Shape shape, int color) {
        g.setColor(new Color(color));
        g.fill(shape);
    }

    /** Resolves the path result used by GenerateTownPropTextures. */
    private static Path2D path(double[] points) {
        var path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        return path;
    }
}
