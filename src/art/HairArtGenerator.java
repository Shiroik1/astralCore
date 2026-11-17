package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Hand-authored curved silhouettes, matching the reference's numbered styles. Front silhouettes are
 * authored individually. Side/back views retain the same part, length, ties and braids using the
 * game's four-direction layered character rig.
 */
public final class HairArtGenerator {
    /** Prevents instantiation of this utility class. */
    private HairArtGenerator() {}

    /** Resolves the thumbnail result used by HairArtGenerator. */
    public static BufferedImage thumbnail(CharacterOutfit outfit) {
        return thumbnail(outfit, 105);
    }

    /** Resolves the thumbnail result used by HairArtGenerator. */
    public static BufferedImage thumbnail(CharacterOutfit outfit, int width) {
        if (width < 1) throw new IllegalArgumentException("Thumbnail width must be positive");
        ArtKit.Surface surface = new ArtKit.Surface(width, (int) Math.ceil(width * 64 / 80.0));
        Graphics2D d = surface.g;
        d.scale(width / 80.0, width / 80.0);
        d.translate(8, 10);
        CharacterArt.Look look = new CharacterArt.Look();
        look.outfit = outfit;
        rear(d, outfit, false, false, 0);
        CharacterClothing.collar(d, outfit, false);
        CharacterArt.drawHead(d, false, false, look);
        crown(d, outfit, false, false);
        return surface.finish();
    }

    /** Resolves the path result used by HairArtGenerator. */
    private static Path2D path(String commands) {
        Path2D p = new Path2D.Float();
        String[] t = commands.trim().split("\\s+");
        int i = 0;
        while (i < t.length) {
            String op = t[i++];
            switch (op) {
                case "M" -> p.moveTo(Double.parseDouble(t[i++]), Double.parseDouble(t[i++]));
                case "L" -> p.lineTo(Double.parseDouble(t[i++]), Double.parseDouble(t[i++]));
                case "Q" ->
                        p.quadTo(
                                Double.parseDouble(t[i++]),
                                Double.parseDouble(t[i++]),
                                Double.parseDouble(t[i++]),
                                Double.parseDouble(t[i++]));
                case "C" ->
                        p.curveTo(
                                Double.parseDouble(t[i++]),
                                Double.parseDouble(t[i++]),
                                Double.parseDouble(t[i++]),
                                Double.parseDouble(t[i++]),
                                Double.parseDouble(t[i++]),
                                Double.parseDouble(t[i++]));
                case "Z" -> p.closePath();
                default -> throw new IllegalArgumentException(op);
            }
        }
        return p;
    }

    private static final String[] FRONTS = {
        // Male reference 01–10: distinct hairlines and outer silhouettes.
        "M 12 19 L 8 18 L 14 12 L 11 11 L 20 6 Q 24 3 29 3 Q 25 -3 36 -3 Q 35 0 31 2 Q 42 2 48 8 L"
            + " 52 11 L 49 12 L 55 18 L 50 19 L 48 28 L 44 29 L 41 19 L 38 23 L 34 15 L 31 24 L 27"
            + " 22 L 25 17 L 20 24 L 19 28 L 14 27 Z",
        "M 12 17 Q 16 2 31 1 Q 46 0 51 16 L 49 27 L 45 28 L 44 18 L 41 23 L 38 22 L 36 15 L 33 24 L"
                + " 29 23 L 27 16 L 24 24 L 20 21 L 19 28 L 15 27 Z",
        "M 11 18 L 8 15 L 14 12 L 11 10 L 18 8 L 16 4 L 24 5 L 22 1 L 31 3 L 34 -2 L 39 3 L 46 2 L"
            + " 44 6 L 51 8 L 48 11 L 54 16 L 50 18 L 49 27 L 45 28 L 43 18 L 39 21 L 38 15 L 33 21"
            + " L 32 15 L 25 23 L 25 16 L 19 23 L 18 28 L 14 27 Z",
        "M 11 18 L 8 15 L 14 12 Q 15 5 29 3 Q 36 1 42 6 L 47 5 L 47 9 L 51 13 L 49 15 L 51 19 L 48"
            + " 28 L 44 28 L 43 16 Q 41 10 36 12 Q 35 19 29 22 L 28 18 Q 24 23 20 24 L 20 20 L 18"
            + " 28 L 14 27 Z",
        "M 11 18 Q 15 1 31 2 Q 46 0 51 16 L 54 19 L 49 19 L 49 27 L 45 28 L 43 21 L 40 23 L 38 18 L"
                + " 35 10 Q 32 7 31 18 Q 29 8 26 11 L 23 23 L 19 20 L 18 28 L 14 27 Z",
        "M 11 19 C 10 8 20 1 31 1 C 44 0 53 9 52 20 Q 50 23 45 21 L 44 18 L 42 21 L 35 21 L 34 18 L"
                + " 32 21 L 26 21 L 25 18 L 23 21 L 17 21 Q 12 22 11 19 Z",
        "M 11 17 L 8 16 L 13 12 Q 18 2 30 2 Q 35 -3 37 1 L 42 2 Q 49 6 51 14 L 54 17 L 49 19 L 52"
            + " 25 L 46 24 L 46 29 L 41 26 L 39 19 L 35 23 L 33 18 L 30 25 L 26 22 L 24 17 L 21 24"
            + " L 18 29 L 14 26 L 12 28 Z",
        "M 10 18 Q 12 15 14 13 Q 12 7 25 5 Q 27 -2 34 -2 L 32 2 Q 44 1 49 11 Q 48 15 53 17 L 50 21"
            + " L 51 26 L 46 27 L 43 21 Q 37 24 35 15 Q 34 11 31 12 L 30 23 L 26 21 L 24 16 Q 21 24"
            + " 18 24 L 18 28 L 13 26 Z",
        "M 13 18 L 9 15 L 14 11 L 12 8 L 21 6 L 19 2 L 31 4 Q 38 0 45 6 L 49 5 L 48 11 L 53 16 L 48"
            + " 19 L 47 27 L 43 25 L 43 16 L 39 14 Q 30 22 25 22 L 26 17 Q 20 23 17 21 L 17 27 L 13"
            + " 25 Z",
        "M 12 24 Q 8 18 13 11 Q 11 7 19 7 Q 18 2 27 3 Q 30 -1 37 1 Q 46 1 49 10 Q 54 14 50 20 L 48"
            + " 28 L 44 25 L 43 18 Q 40 12 35 12 L 32 15 L 29 12 Q 22 12 19 18 L 18 27 L 14 28 Z",
        // Male reference 11–20.
        "M 11 18 L 8 17 Q 15 1 31 2 Q 46 1 51 17 L 54 21 L 49 20 L 50 27 L 45 27 L 42 19 L 37 23 L"
                + " 33 15 L 29 24 L 24 20 L 22 17 L 20 27 L 15 28 Z",
        "M 11 18 Q 13 3 28 2 Q 42 0 49 12 L 53 18 L 49 20 L 48 29 L 43 27 L 42 18 Q 39 23 35 25 L"
                + " 33 18 L 30 22 L 27 17 L 22 23 L 19 28 L 15 27 Z",
        "M 12 19 Q 12 3 28 2 Q 44 0 50 14 L 50 25 L 46 28 L 43 22 Q 44 12 36 9 Q 31 13 32 22 L 28"
                + " 19 L 27 12 L 23 15 L 20 26 L 15 28 Z",
        "M 12 18 L 8 18 Q 15 13 14 10 Q 18 2 31 2 Q 47 1 50 16 L 53 19 L 49 20 L 49 28 L 45 28 L 42"
                + " 21 L 39 24 L 36 13 L 32 10 L 29 23 L 25 22 L 23 18 L 20 28 L 15 27 Z",
        "M 11 18 L 8 17 L 15 11 Q 18 4 28 3 Q 31 -2 38 -1 L 35 3 Q 46 3 49 12 L 54 18 L 49 20 L 52"
            + " 27 L 46 26 L 45 31 L 40 26 L 38 20 L 35 23 L 31 14 L 27 22 L 23 19 L 21 28 L 15 30"
            + " L 14 25 L 10 27 Z",
        "M 12 19 Q 14 2 31 2 Q 48 1 51 21 L 48 33 L 44 34 L 43 23 L 38 14 Q 33 10 32 21 Q 30 11 26"
                + " 14 L 21 23 L 20 34 L 15 33 Z",
        "M 11 18 Q 15 1 31 2 Q 45 1 51 18 Q 48 22 51 27 L 47 33 L 43 32 Q 44 27 40 24 Q 37 21 37 14"
                + " Q 32 9 31 20 Q 29 10 25 15 Q 25 21 20 25 L 20 32 L 15 33 L 12 28 Z",
        "M 12 18 Q 13 2 31 2 Q 48 1 51 19 L 48 32 L 44 33 L 43 22 L 38 13 Q 33 10 32 20 Q 30 12 26"
                + " 14 L 21 22 L 20 33 L 15 32 Z",
        "M 11 18 Q 15 1 29 2 Q 43 1 49 12 L 53 17 L 49 19 L 49 27 L 45 29 L 42 18 Q 38 24 34 25 L"
                + " 31 17 L 27 21 L 23 17 L 20 28 L 15 27 Z",
        "M 11 17 L 8 17 Q 15 9 17 7 Q 25 1 32 2 Q 45 1 49 13 L 54 18 L 49 19 L 52 28 L 47 27 L 45"
            + " 35 L 41 31 L 40 22 L 36 26 L 32 16 L 28 24 L 24 21 L 21 30 L 17 35 L 13 31 L 14 25"
            + " L 10 27 Z",
        // Female reference 01–10.
        "M 12 20 Q 12 2 31 1 Q 49 1 51 20 L 51 30 Q 49 35 44 32 L 43 22 Q 37 21 33 14 Q 28 22 22 24"
                + " L 21 32 Q 15 35 12 30 Z",
        "M 11 20 Q 12 1 31 1 Q 51 1 52 21 L 50 43 L 43 43 L 43 20 L 38 20 L 37 18 L 37 20 L 29 20 L"
                + " 28 18 L 28 20 L 20 20 L 20 43 L 13 43 Z",
        "M 12 18 Q 14 2 31 1 Q 47 1 50 16 L 53 20 L 49 22 L 51 28 L 46 28 L 44 34 L 40 31 L 40 22 L"
            + " 36 19 L 33 13 L 29 24 L 25 22 L 24 18 L 21 29 L 17 34 L 14 29 L 11 30 L 13 24 L 10"
            + " 23 Z",
        "M 10 20 Q 13 2 31 1 Q 48 1 51 19 L 50 32 L 44 35 L 43 22 Q 39 21 35 13 Q 28 22 19 24 L 17"
                + " 21 L 18 33 L 13 31 Z",
        "M 12 19 Q 14 2 31 2 Q 48 1 50 19 L 48 29 L 44 30 L 43 22 L 39 24 L 35 20 L 32 13 L 29 24 L"
                + " 25 22 L 23 18 L 20 30 L 15 29 Z",
        "M 12 20 Q 11 3 30 1 Q 47 0 51 17 Q 48 23 52 28 Q 48 34 43 32 L 41 23 Q 35 22 33 14 Q 29 23"
                + " 22 24 Q 23 32 18 34 Q 10 33 12 26 Z",
        "M 12 19 Q 11 12 16 8 Q 19 0 30 2 Q 39 -1 45 6 Q 53 8 51 19 Q 47 22 51 28 Q 49 34 44 33 Q"
                + " 39 29 41 23 Q 36 20 33 13 Q 29 22 23 24 Q 25 32 20 33 Q 12 34 12 27 Z",
        "M 12 18 Q 14 2 31 1 Q 49 0 51 19 L 49 33 L 44 35 L 43 23 Q 36 22 33 13 Q 29 22 22 24 L 21"
                + " 34 L 15 34 Z",
        "M 11 20 Q 11 1 31 0 Q 52 1 52 21 L 50 35 L 43 35 L 43 23 Q 36 21 33 13 Q 28 22 22 24 L 21"
                + " 35 L 13 34 Z",
        "M 12 19 Q 13 2 31 1 Q 49 1 51 20 L 50 33 Q 47 36 43 33 L 43 22 Q 36 23 33 14 Q 29 23 22 24"
                + " L 21 33 Q 15 36 12 32 Z",
        // Female reference 11–20.
        "M 11 18 Q 14 1 31 1 Q 48 1 51 19 L 49 30 L 44 33 L 43 22 Q 36 20 33 13 Q 29 24 22 25 L 20"
                + " 30 L 15 29 L 12 25 Z",
        "M 12 19 Q 14 2 31 1 Q 50 1 51 21 L 48 31 L 44 31 L 43 21 L 38 22 L 37 17 L 35 23 L 29 23 L"
                + " 28 18 L 25 23 L 21 22 L 20 31 L 15 30 Z",
        "M 12 19 Q 14 1 31 1 Q 49 1 51 20 L 48 31 L 44 31 L 43 21 L 38 22 L 37 17 L 35 23 L 29 23 L"
                + " 28 18 L 25 23 L 21 22 L 20 31 L 15 30 Z",
        "M 12 20 Q 13 2 31 1 Q 49 1 51 20 L 50 30 Q 46 36 42 31 L 42 23 Q 36 21 33 13 Q 29 23 22 25"
                + " L 21 31 Q 17 34 13 30 Z",
        "M 11 20 Q 12 1 31 1 Q 49 1 51 21 L 49 32 L 44 34 L 43 23 Q 37 20 34 12 Q 29 22 21 25 L 20"
                + " 32 L 15 32 Z",
        "M 11 19 Q 14 2 31 1 Q 47 1 51 20 L 54 24 L 49 25 L 47 32 L 43 31 L 42 23 Q 37 18 35 12 Q"
                + " 30 22 18 25 L 19 32 L 13 31 L 14 24 L 10 23 Z",
        "M 11 20 Q 12 2 30 1 Q 48 0 51 20 L 49 31 L 44 34 L 43 23 Q 35 21 33 13 Q 29 24 21 25 L 20"
                + " 30 L 15 29 Z",
        "M 11 20 Q 12 1 31 1 Q 51 1 52 21 L 50 44 L 43 44 L 43 23 L 39 25 L 35 21 L 32 13 L 29 24 L"
                + " 25 24 L 22 21 L 21 44 L 13 44 Z",
        "M 11 18 Q 12 2 30 1 Q 46 0 51 18 Q 48 23 52 28 L 48 35 L 44 34 Q 45 26 41 24 Q 35 21 33 13"
                + " Q 29 22 22 24 Q 25 30 20 35 L 14 34 Q 11 29 13 24 Z",
        "M 12 19 Q 13 2 31 1 Q 49 1 51 21 L 49 32 L 44 33 L 43 23 L 38 24 L 35 20 L 32 13 L 29 23 L"
                + " 25 24 L 22 21 L 21 32 L 15 33 Z"
    };
    private static final Shape[] FRONT = new Shape[40];

    static {
        for (int i = 0; i < 40; i++) FRONT[i] = path(FRONTS[i]);
    }

    /** Performs the paint operation for HairArtGenerator. */
    private static void paint(Graphics2D g, Shape shape, Color color) {
        CharacterArt.fillSoft(g, shape, color);
        g.setColor(CharacterArt.withAlpha(CharacterArt.shade(color, .78f), 235));
        g.setStroke(new BasicStroke(.95f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    /** Renders this component into the supplied Java2D graphics context. */
    private static void draw(Graphics2D g, String p, Color c) {
        paint(g, path(p), c);
    }

    /** Resolves the dark result used by HairArtGenerator. */
    private static Color dark(Color c) {
        return CharacterArt.shade(c, .94f);
    }

    /** Performs the pony operation for HairArtGenerator. */
    private static void pony(Graphics2D g, Color c, boolean high, boolean left, boolean shortTail) {
        Graphics2D d = (Graphics2D) g.create();
        if (left) {
            d.translate(64, 0);
            d.scale(-1, 1);
        }
        if (high && shortTail)
            draw(
                    d,
                    "M 43 10 Q 45 -4 54 -2 L 50 1 Q 60 -1 62 4 L 57 5 Q 64 9 63 15 L 58 12 L 58 20"
                            + " Q 52 19 51 11 Z",
                    dark(c));
        else if (high)
            draw(
                    d,
                    "M 43 10 Q 48 -6 55 -3 Q 64 0 63 15 Q 61 30 66 38 Q 59 42 55 36 Q 58 24 51 20 L"
                            + " 48 16 Z",
                    dark(c));
        else if (shortTail)
            draw(d, "M 46 25 Q 57 20 58 31 Q 54 40 60 42 Q 51 45 49 37 Q 45 33 46 25 Z", dark(c));
        else draw(d, "M 45 21 Q 57 17 59 29 Q 56 42 62 46 Q 54 50 50 42 Q 46 36 47 30 Z", dark(c));
        d.setColor(new Color(0x655A73));
        d.fill(new RoundRectangle2D.Float(high ? 46 : 46, high ? 8 : 25, 8, 3, 2, 2));
        d.dispose();
    }

    /** Performs the braid operation for HairArtGenerator. */
    private static void braid(Graphics2D g, Color c, float x, float y, int count, boolean bow) {
        draw(
                g,
                "M " + (x - 3) + " " + y + " Q " + (x - 8) + " " + (y - 5) + " " + (x - 3) + " "
                        + (y - 9) + " L " + (x + 4) + " " + (y - 7) + " L " + (x + 3) + " " + y
                        + " Z",
                dark(c));
        for (int i = 0; i < count; i++) {
            float bx = x + (i % 2 == 0 ? -1 : 1), by = y + i * 3.6f;
            Shape plait =
                    AffineTransform.getRotateInstance(i % 2 == 0 ? .42 : -.42, bx, by + .4)
                            .createTransformedShape(
                                    new Ellipse2D.Float(bx - 3.5f, by - 1.8f, 7, 4.5f));
            paint(g, plait, i % 2 == 0 ? c : dark(c));
        }
        float end = y + count * 3.6f;
        g.setColor(new Color(0x655A73));
        g.fillRoundRect(Math.round(x - 3), Math.round(end - 1), 6, 2, 1, 1);
        draw(
                g,
                "M " + (x - 2) + " " + (end + 1) + " Q " + (x - 4) + " " + (end + 6) + " " + x + " "
                        + (end + 6) + " Q " + (x + 4) + " " + (end + 4) + " " + (x + 2) + " "
                        + (end + 1) + " Z",
                c);
        if (bow) bow(g, x, end, new Color(0x655A73));
    }

    /** Performs the bow operation for HairArtGenerator. */
    private static void bow(Graphics2D g, float x, float y, Color color) {
        draw(
                g,
                "M " + x + " " + y + " Q " + (x - 9) + " " + (y - 6) + " " + (x - 7) + " " + (y + 3)
                        + " L " + x + " " + (y + 1) + " Q " + (x + 9) + " " + (y + 6) + " "
                        + (x + 7) + " " + (y - 3) + " Z",
                color);
        g.setColor(CharacterArt.lighten(color, .18f));
        g.fill(new Ellipse2D.Float(x - 1.4f, y - 1, 2.8f, 3));
    }

    /** Performs the long shape operation for HairArtGenerator. */
    private static void longShape(Graphics2D g, Color c, int kind, boolean side) {
        String shape =
                switch (kind) {
                    case 1 ->
                            "M 15 17 Q 10 22 13 29 Q 7 36 13 41 Q 10 48 18 47 L 22 44 Q 32 49 41 44"
                                    + " Q 49 48 52 42 Q 57 36 51 30 Q 55 23 49 17 Z";
                    case 2 ->
                            "M 15 17 Q 9 19 12 25 Q 6 29 11 33 Q 7 39 12 42 Q 10 47 17 47 Q 22 50"
                                + " 26 45 Q 32 49 38 45 Q 43 50 49 46 Q 55 47 53 41 Q 59 37 53 32 Q"
                                + " 58 26 51 23 L 49 17 Z";
                    case 3 ->
                            "M 15 16 Q 11 30 10 41 L 15 39 L 16 47 L 22 44 L 24 48 Q 32 45 39 48 L"
                                    + " 42 43 L 48 47 L 48 40 L 54 42 Q 51 27 49 16 Z";
                    case 4 ->
                            "M 15 17 Q 10 26 13 35 Q 15 42 21 40 Q 31 44 43 40 Q 51 41 52 33 L 49"
                                    + " 17 Z";
                    default ->
                            "M 15 16 Q 12 30 12 46 Q 17 49 22 46 L 25 48 Q 32 46 40 48 L 43 46 L 51"
                                    + " 47 Q 50 29 49 16 Z";
                };
        if (side) {
            Graphics2D d = (Graphics2D) g.create();
            d.translate(4, 0);
            d.scale(.72, 1);
            draw(d, shape, dark(c));
            d.dispose();
        } else draw(g, shape, dark(c));
    }

    /** Performs the rear operation for HairArtGenerator. */
    public static void rear(
            Graphics2D g, CharacterOutfit outfit, boolean side, boolean back, float swing) {
        int id = outfit.details().hairStyle();
        if (!Hairstyles.valid(id) || outfit.hair() == null) return;
        Graphics2D d = (Graphics2D) g.create();
        Color c = outfit.hair();
        // Small anchored sway keeps tails attached while moving.
        if (id == 10 || id == 13 || id == 23 || id == 30 || id == 33 || id == 34 || id == 39)
            d.translate(Math.abs(swing) * .45, 0);
        switch (id) {
            case 10, 30 -> pony(d, c, false, side, id == 10);
            case 11 -> pony(d, c, true, side, true);
            case 12 -> {
                if (side) d.translate(-9, 0);
                draw(d, "M 28 5 Q 24 -5 35 -5 Q 43 -6 44 2 Q 43 9 34 10 Z", c);
                draw(d, "M 33 -3 Q 33 -10 40 -8 L 38 -5 Q 47 -7 48 -3 Q 42 -4 39 1 Z", c);
            }
            case 13 -> braid(d, c, side ? 20 : 46, 32, 3, false);
            case 14 -> longShape(d, c, 3, side);
            case 15, 21, 37 -> longShape(d, c, 0, side);
            case 16, 38 -> longShape(d, c, 1, side);
            case 17, 27 -> longShape(d, c, 0, side);
            case 18, 36 -> pony(d, c, true, side, false);
            case 19 -> longShape(d, c, 3, side);
            case 20, 23, 29 -> longShape(d, c, 4, side);
            case 22 -> {
                Graphics2D shortLocks = (Graphics2D) d.create();
                shortLocks.translate(0, 17);
                shortLocks.scale(1, .64);
                shortLocks.translate(0, -17);
                longShape(shortLocks, c, 3, side);
                shortLocks.dispose();
            }
            case 24 -> {
                pony(d, c, false, side, true);
                if (!side) pony(d, c, false, true, true);
            }
            case 25 -> {
                Graphics2D bob = (Graphics2D) d.create();
                bob.translate(0, 17);
                bob.scale(1, .72);
                bob.translate(0, -17);
                longShape(bob, c, 1, side);
                bob.dispose();
            }
            case 26 -> longShape(d, c, 2, side);
            case 28 -> longShape(d, c, 4, side);
            case 31 -> {
                pony(d, c, false, true, false);
                bow(d, 15, 18, new Color(0x655A73));
            }
            case 32 -> {
                pony(d, c, false, side, false);
                if (!side) pony(d, c, false, true, false);
                bow(d, side ? 16 : 48, 16, new Color(0x655A73));
                if (!side) bow(d, 15, 16, new Color(0x655A73));
            }
            case 33 -> {
                if (back || side) braid(d, c, side ? 22 : 32, 35, 3, true);
            }
            case 34 -> {
                longShape(d, c, 4, side);
                braid(d, c, side ? 20 : 47, 34, 3, false);
            }
            case 35 -> {
                longShape(d, c, 1, side);
                bow(d, 15, 16, new Color(0x655A73));
            }
            case 39 -> {
                braid(d, c, side ? 20 : 47, 34, 3, false);
                if (!side) braid(d, c, 17, 34, 3, false);
            }
            default -> {}
        }
        d.dispose();
    }

    /** Performs the crown operation for HairArtGenerator. */
    public static void crown(Graphics2D g, CharacterOutfit outfit, boolean side, boolean back) {
        int id = outfit.details().hairStyle();
        if (!Hairstyles.valid(id) || outfit.hair() == null) return;
        Graphics2D d = (Graphics2D) g.create();
        Color c = outfit.hair();
        Shape cap;
        if (back) {
            cap =
                    path(
                            "M 12 18 Q 12 0 31 1 Q 50 0 52 20 L 50 30 Q 44 36 38 32 Q 30 36 23 32 Q"
                                    + " 15 35 13 29 Z");
            if (id == 2 || id == 0 || id == 14 || id == 19)
                cap =
                        path(
                                "M 11 18 L 8 15 L 15 10 L 13 6 L 23 5 L 24 1 L 32 3 L 37 0 L 43 5 L"
                                    + " 48 5 L 48 10 L 54 17 L 50 20 L 52 28 L 47 28 L 44 34 L 39"
                                    + " 31 L 32 35 L 25 31 L 20 34 L 14 31 Z");
        } else if (side) {
            cap =
                    path(
                            "M 12 18 Q 12 1 30 1 Q 45 0 50 16 L 48 22 L 44 23 L 43 14 Q 40 21 35 22"
                                    + " L 32 28 L 28 24 L 24 29 L 21 33 L 15 31 L 13 25 Z");
            if (id == 8 || id == 9)
                cap =
                        path(
                                "M 13 22 Q 8 13 18 7 Q 22 1 34 1 Q 46 1 50 13 L 47 18 L 44 15 Q 38"
                                        + " 15 33 22 L 26 23 L 20 29 L 15 28 Z");
            if (id == 5 || id == 21)
                cap =
                        path(
                                "M 12 18 Q 12 1 31 1 Q 47 1 51 18 L 47 23 L 43 23 L 42 18 L 37 23 L"
                                        + " 27 24 L 25 30 L 18 31 L 14 26 Z");
        } else cap = FRONT[id];
        // A slightly fuller scalp shell covers the base head's thicker outline at the crown.
        // Union it with the authored cut so the fringe and outer locks retain their shape.
        Area volume = new Area(cap);
        volume.add(
                new Area(
                        path(
                                "M 32 -1 C 19 -1 12.8 6 12.8 18.5 L 17 16 Q 32 4 47 16 L 51.2 18.5"
                                        + " C 51.2 6 45 -1 32 -1 Z")));
        cap = volume;
        paint(d, cap, c);
        // Low-contrast curved locks make the silhouette readable without strand noise.
        Shape clip = d.getClip();
        d.clip(cap);
        d.setStroke(new BasicStroke(.55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        d.setColor(CharacterArt.withAlpha(CharacterArt.shade(c, .81f), 105));
        String locks;
        if (back) locks = "M 24 6 Q 19 17 21 28 M 33 5 Q 34 18 31 29 M 41 8 Q 47 20 43 29";
        else if (id == 9) locks = "M 18 19 Q 16 10 28 7 M 24 15 Q 26 6 37 4 M 34 12 Q 38 6 45 10";
        else if (id == 5 || id == 21 || id == 31 || id == 32)
            locks =
                    "M 22 8 Q 20 15 21 21 M 28 6 Q 27 14 28 21 M 35 6 Q 36 14 35 21 M 42 9 Q 44 16"
                            + " 43 21";
        else if (id == 4 || id == 15 || id == 16 || id == 17)
            locks =
                    "M 30 5 Q 22 4 18 18 M 34 5 Q 42 4 47 19 M 23 13 Q 22 20 20 24 M 40 13 Q 43 20"
                            + " 44 25";
        else locks = "M 26 5 Q 19 8 18 18 M 33 5 Q 27 12 26 18 M 38 6 Q 40 14 44 18";
        d.draw(path(locks));
        d.setColor(new Color(255, 255, 255, 30));
        d.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        d.draw(path("M 19 10 Q 25 4 33 5"));
        d.setClip(clip);
        if (id == 8) {
            d.setColor(CharacterArt.withAlpha(CharacterArt.lighten(c, .35f), 170));
            d.fill(new RoundRectangle2D.Float(13, 22, 3, 5, 2, 2));
        }
        if (id == 11 || id == 18 || id == 36) {
            d.setColor(new Color(0x655A73));
            d.setStroke(new BasicStroke(1.6f));
            d.draw(new Arc2D.Float(16, 1, 36, 29, 15, 140, Arc2D.OPEN));
        }
        if (id == 17 || id == 27) {
            for (int i = 0; i < 11; i++) {
                double a = Math.PI - (i * Math.PI / 10);
                float x = 32 + (float) Math.cos(a) * 18, y = 17 - (float) Math.sin(a) * 15;
                Shape plait =
                        AffineTransform.getRotateInstance(
                                        Math.PI / 2 - a + (i % 2 == 0 ? .35 : -.35), x, y)
                                .createTransformedShape(
                                        new Ellipse2D.Float(x - 3.3f, y - 2, 6.6f, 4));
                paint(d, plait, i % 2 == 0 ? c : CharacterArt.lighten(c, .07f));
            }
        }
        if (id == 28) {
            d.setColor(new Color(0x655A73));
            d.setStroke(new BasicStroke(3.2f));
            d.draw(new Arc2D.Float(13, 1, 38, 42, 8, 165, Arc2D.OPEN));
            d.setColor(new Color(0xF3E5CD));
            d.setStroke(new BasicStroke(1.3f));
            d.draw(new Arc2D.Float(13, 1, 38, 42, 8, 165, Arc2D.OPEN));
        }
        if (id == 29) {
            d.setColor(new Color(0xD9B77D));
            d.setStroke(new BasicStroke(.85f));
            d.draw(new Line2D.Float(16, 17, 21, 20));
            d.draw(new Line2D.Float(44, 16, 48, 20));
            d.draw(new Line2D.Float(48, 16, 44, 20));
        }
        if (id == 33 && !side && !back) braid(d, c, 49, 35, 2, true);
        d.dispose();
    }
}
