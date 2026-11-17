package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.function.BiPredicate;

public class GroundTiles {
    public static final int LEVELS = 4; // ground tones 0..3
    public static final int COBBLE_VARIANTS = 3;

    // neighbour mask bits
    public static final int UP = 1, RIGHT = 2, DOWN = 4, LEFT = 8;
    public static final int UP_RIGHT = 16, DOWN_RIGHT = 32, DOWN_LEFT = 64, UP_LEFT = 128;
    public static final int FULL = 255;

    public static final int ART_TILE_SIZE = 48;
    public static final int GRASS_INSET = 6;
    public static final int LEDGE_LEFT = 1, LEDGE_RIGHT = 2;
    public static final int PATH_VARIANTS = 3;
    public static final int MOSS_VARIANTS = 6;
    public static final int GRASS_VARIANTS = 6;
    private static final double[] LEVEL_THRESHOLDS = {
        0.42, 0.52, 0.60
    }; // noise cut-offs between tones

    // The world the renderer reads from. Out-of-range cells should return the nearest valid cell's
    // value.
    public interface World {
        /** Resolves the terrain result used by World. */
        Terrain terrain(int col, int row);

        /** Resolves the level result used by World. */
        int level(int col, int row);

        /** Resolves the grass variant result used by World. */
        default int grassVariant(int col, int row) {
            return (int) (ArtKit.hash01(717, col, row) * GRASS_VARIANTS) % GRASS_VARIANTS;
        }
    }

    private final int size;
    private final GroundTextures textures = new GroundTextures();

    /** Creates a GroundTiles instance and initializes the state required by its collaborators. */
    public GroundTiles(int size) {
        this.size = size;
    }

    // large smooth regions of lighter and darker tone
    /** Resolves the ground level result used by GroundTiles. */
    public int groundLevel(int col, int row) {
        double n = ArtKit.fbm(col * 0.08, row * 0.08, 7);
        int level = 0;
        for (double t : LEVEL_THRESHOLDS) {
            if (n >= t) level++;
        }
        return level;
    }

    /** Resolves the cobble result used by GroundTiles. */
    public BufferedImage cobble(int variant) {
        return textures.tile("cobble", variant);
    }

    /** Resolves the level overlay result used by GroundTiles. */
    public BufferedImage levelOverlay(int level, int mask) {
        return textures.tile("grass-edge-" + level, mask & 255);
    }

    /** Resolves the path overlay result used by GroundTiles. */
    public BufferedImage pathOverlay(int mask, int variant) {
        return textures.tile("path-" + variant, mask & 255);
    }

    // DRAWING ONE CELL
    /** Draws the cell for GroundTiles. */
    public void drawCell(Graphics2D g, World w, int col, int row, int px, int py) {
        Terrain t = w.terrain(col, row);
        if (t == Terrain.LEDGE || t == Terrain.STEPS) {
            draw(g, textures.tile("grass", Math.max(0, w.level(col, row) - 1)), px, py);
            if (t == Terrain.STEPS) {
                draw(g, textures.tile("steps", w.level(col, row)), px, py);
            } else {
                int connections = ledgeConnections(w, col, row);
                draw(g, textures.tile("ledge", w.level(col, row) * 4 + connections), px, py);
            }
            return;
        }
        if (t == Terrain.TERRACE || t == Terrain.RIM) {
            int level = w.level(col, row);
            int neighbors = mask((c, r) -> joinsRaised(w, c, r, level), col, row);
            draw(g, textures.tile("grass", Math.max(0, level - 1)), px, py);
            draw(
                    g,
                    level == 0 ? textures.tile("grass", 0) : levelOverlay(level, neighbors),
                    px,
                    py);
            return;
        }

        // Tones are nested: a tile at tone 3 sits inside tone 2, which sits inside tone 1. Start
        // from the highest
        // tone that fully covers the tile and only draw the overlays above it.
        int level = w.level(col, row);
        int[] masks = new int[LEVELS];
        int startK = 0;
        for (int k = level; k >= 1; k--) {
            final int tone = k;
            masks[k] = mask((c, r) -> w.level(c, r) >= tone, col, row);
            if (masks[k] == FULL) {
                startK = k;
                break;
            }
        }
        int grassVariant =
                t == Terrain.GRASS
                        ? Math.max(0, Math.min(GRASS_VARIANTS - 1, w.grassVariant(col, row)))
                        : 0;
        boolean baked = t == Terrain.GRASS && startK == level;
        draw(
                g,
                baked
                        ? textures.tile("grass-variants", level * GRASS_VARIANTS + grassVariant)
                        : textures.tile("grass", startK),
                px,
                py);
        for (int k = startK + 1; k <= level; k++) {
            draw(g, levelOverlay(k, masks[k]), px, py);
        }
        // Transition cells retain the existing blended tones, with matching pre-baked detail on
        // top.
        if (t == Terrain.GRASS && !baked)
            draw(g, textures.tile("grass-detail", level * GRASS_VARIANTS + grassVariant), px, py);

        if (t == Terrain.GRASS) {
            int fringe = mask((c, r) -> pavingLike(w, c, r), col, row);
            if (fringe != 0) draw(g, textures.tile("cobble-fringe", fringe), px, py);
        } else if (t == Terrain.COBBLE) {
            int m = mask((c, r) -> pathLike(w, c, r), col, row);
            draw(g, textures.tile("cobble-connect-" + cobbleVariant(col, row), m), px, py);
        } else if (t == Terrain.MOSS_PATH) {
            int m = mask((c, r) -> pathLike(w, c, r), col, row);
            int variant = (int) (ArtKit.hash01(901, col, row) * MOSS_VARIANTS) % MOSS_VARIANTS;
            draw(g, textures.tile("moss-path-" + variant, m), px, py);
        } else if (t == Terrain.PATH) {
            int m = mask((c, r) -> pathLike(w, c, r), col, row);
            draw(g, pathOverlay(m, pathVariant(col, row)), px, py);
        } else if (t == Terrain.WATER) {
            int m = mask((c, r) -> w.terrain(c, r) == Terrain.WATER, col, row);
            double variation = ArtKit.hash01(32, col, row);
            int variant = variation < 0.64 ? 0 : variation < 0.86 ? 1 : 2;
            draw(g, textures.tile("water-" + variant, m), px, py);
            if (m == FULL && ArtKit.hash01(57, col, row) < 0.18)
                draw(g, textures.tile("lilies", ArtKit.hash01(58, col, row) < 0.4 ? 1 : 0), px, py);
        }
    }

    /**
     * Existing ledge artwork only: repeat its unscaled wall body (omit the grassy lip), clipped by
     * a connected silhouette.
     */
    public void drawCliffFace(Graphics2D original, Shape silhouette, int tone) {
        var g = (Graphics2D) original.create();
        g.clip(silhouette);
        Rectangle b = silhouette.getBounds();
        BufferedImage source =
                textures.tile("ledge", Math.max(0, Math.min(LEVELS - 1, tone)) * 4 + 3);
        int strip = Math.max(1, size / 3);
        for (int y = Math.floorDiv(b.y, strip) * strip; y < b.y + b.height; y += strip)
            for (int x = Math.floorDiv(b.x, size) * size; x < b.x + b.width; x += size)
                g.drawImage(source, x, y, x + size, y + strip, 0, 32, 48, 48, null);
        g.dispose();
    }

    /**
     * New modular sprites are decoded once by GroundTextures and drawn only while terrain caches
     * are baked.
     */
    public void drawCliffCap(
            Graphics2D original, World world, int col, int row, int mask, int x, int y) {
        // Bake the existing grass/tone transitions through the exported cap alpha, preserving
        // ground continuity.
        var colored = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = colored.createGraphics();
        drawCell(g, world, col, row, 0, 0);
        g.setComposite(AlphaComposite.DstIn);
        draw(
                g,
                textures.tile(
                        "cliff-top-" + Math.max(0, Math.min(LEVELS - 1, world.level(col, row))),
                        mask & 255),
                0,
                0);
        g.dispose();
        original.drawImage(colored, x, y, null);
    }

    /** Performs the cliff material operation for GroundTiles. */
    private void cliffMaterial(
            Graphics2D original, Shape area, String sheet, int variant, int anchorX, int anchorY) {
        if (area.getBounds().isEmpty()) return;
        var g = (Graphics2D) original.create();
        g.clip(area);
        var bounds = area.getBounds();
        var image = textures.tile(sheet, variant);
        for (int y = Math.floorDiv(bounds.y - anchorY, size) * size + anchorY;
                y < bounds.y + bounds.height;
                y += size)
            for (int x = Math.floorDiv(bounds.x - anchorX, size) * size + anchorX;
                    x < bounds.x + bounds.width;
                    x += size) draw(g, image, x, y);
        g.dispose();
    }

    /** Draws the cliff wall for GroundTiles. */
    public void drawCliffWall(
            Graphics2D g,
            Area wall,
            Shape highestCap,
            int tone,
            int footTone,
            int x,
            int y,
            int lift) {
        cliffMaterial(g, wall, "cliff-middle", 0, x, y);
        var left = new Area(wall);
        left.intersect(new Area(new Rectangle(x, y - lift, size / 6, size + lift)));
        cliffMaterial(g, left, "cliff-side-left", 0, x, y);
        var right = new Area(wall);
        right.intersect(
                new Area(new Rectangle(x + size - size / 6, y - lift, size / 6, size + lift)));
        cliffMaterial(g, right, "cliff-side-right", 0, x, y);
        var lip =
                new Area(
                        new BasicStroke(
                                        Math.max(2, size / 6f),
                                        BasicStroke.CAP_ROUND,
                                        BasicStroke.JOIN_ROUND)
                                .createStrokedShape(highestCap));
        lip.intersect(wall);
        cliffMaterial(g, lip, "cliff-lip", Math.max(0, Math.min(LEVELS - 1, tone)), x, y);
        var foot = new Area(wall);
        foot.subtract(
                new Area(
                        AffineTransform.getTranslateInstance(0, -Math.max(2, size / 10))
                                .createTransformedShape(wall)));
        // The union's bottom, not every repeated strip, receives the lower-ground transition.
        foot.subtract(lip);
        cliffMaterial(g, foot, "cliff-foot", Math.max(0, Math.min(LEVELS - 1, footTone)), x, y);
    }

    /** Evaluates the paving like condition for GroundTiles. */
    private static boolean pavingLike(World world, int col, int row) {
        Terrain terrain = world.terrain(col, row);
        return terrain == Terrain.COBBLE || terrain == Terrain.MOSS_PATH;
    }

    /** Evaluates the path like condition for GroundTiles. */
    private static boolean pathLike(World w, int col, int row) {
        Terrain t = w.terrain(col, row);
        return t == Terrain.PATH
                || t == Terrain.COBBLE
                || t == Terrain.STEPS
                || t == Terrain.MOSS_PATH;
    }

    /** Resolves the mask result used by GroundTiles. */
    private static int mask(BiPredicate<Integer, Integer> same, int col, int row) {
        int m = 0;
        if (same.test(col, row - 1)) m |= UP;
        if (same.test(col + 1, row)) m |= RIGHT;
        if (same.test(col, row + 1)) m |= DOWN;
        if (same.test(col - 1, row)) m |= LEFT;
        if (same.test(col + 1, row - 1)) m |= UP_RIGHT;
        if (same.test(col + 1, row + 1)) m |= DOWN_RIGHT;
        if (same.test(col - 1, row + 1)) m |= DOWN_LEFT;
        if (same.test(col - 1, row - 1)) m |= UP_LEFT;
        return m;
    }

    /** Resolves the cobble variant result used by GroundTiles. */
    private int cobbleVariant(int col, int row) {
        return (int) (ArtKit.hash01(3, col, row) * COBBLE_VARIANTS) % COBBLE_VARIANTS;
    }

    /** Resolves the path variant result used by GroundTiles. */
    private int pathVariant(int col, int row) {
        return (int) (ArtKit.hash01(11, col, row) * PATH_VARIANTS) % PATH_VARIANTS;
    }

    /** Renders this component into the supplied Java2D graphics context. */
    private void draw(Graphics2D g, BufferedImage image, int x, int y) {
        g.drawImage(image, x, y, size, size, null);
    }

    /** Evaluates the ledge like condition for GroundTiles. */
    private static boolean ledgeLike(Terrain terrain) {
        return terrain == Terrain.LEDGE || terrain == Terrain.STEPS;
    }

    /** Resolves the ledge connections result used by GroundTiles. */
    public static int ledgeConnections(World w, int col, int row) {
        int connections = ledgeLike(w.terrain(col - 1, row)) ? LEDGE_LEFT : 0;
        if (ledgeLike(w.terrain(col + 1, row))) connections |= LEDGE_RIGHT;
        return connections;
    }

    /** Evaluates the joins raised condition for GroundTiles. */
    public static boolean joinsRaised(World w, int col, int row, int level) {
        Terrain terrain = w.terrain(col, row);
        return w.level(col, row) >= level
                && (ledgeLike(terrain) || terrain == Terrain.TERRACE || terrain == Terrain.RIM);
    }

    // A rounded square, inset from the tile edge, with an arm toward every connected neighbour.
    // Outer corners
    // are rounded with `radius`, inner corners with `fillet`. wobbleVariant >= 0 adds small bumps
    // and dents
    // along the straight edges (never near the tile border, so neighbouring tiles still join).
    /** Resolves the blob shape result used by GroundTiles. */
    public static Area blobShape(
            int mask, double inset, double radius, double fillet, int wobbleVariant) {
        double i = inset, r = radius, f = fillet, u = ART_TILE_SIZE, ext = 3;
        boolean up = (mask & UP) != 0,
                right = (mask & RIGHT) != 0,
                down = (mask & DOWN) != 0,
                left = (mask & LEFT) != 0;

        Area shape = new Area(new Rectangle2D.Double(i, i, u - 2 * i, u - 2 * i));

        if (!up && !left) shape.subtract(roundCut(i, i, i, i, r));
        if (!up && !right) shape.subtract(roundCut(u - i - r, i, u - i - 2 * r, i, r));
        if (!down && !right)
            shape.subtract(roundCut(u - i - r, u - i - r, u - i - 2 * r, u - i - 2 * r, r));
        if (!down && !left) shape.subtract(roundCut(i, u - i - r, i, u - i - 2 * r, r));

        // arms run past the tile border so neighbouring tiles join without a hairline
        if (up) shape.add(new Area(new Rectangle2D.Double(i, -ext, u - 2 * i, i + ext)));
        if (down) shape.add(new Area(new Rectangle2D.Double(i, u - i, u - 2 * i, i + ext)));
        if (left) shape.add(new Area(new Rectangle2D.Double(-ext, i, i + ext, u - 2 * i)));
        if (right) shape.add(new Area(new Rectangle2D.Double(u - i, i, i + ext, u - 2 * i)));

        // inner corners
        if (up && left) {
            if ((mask & UP_LEFT) != 0)
                shape.add(new Area(new Rectangle2D.Double(-ext, -ext, i + ext, i + ext)));
            else shape.add(fillet(i, i, -1, -1, f));
        }
        if (up && right) {
            if ((mask & UP_RIGHT) != 0)
                shape.add(new Area(new Rectangle2D.Double(u - i, -ext, i + ext, i + ext)));
            else shape.add(fillet(u - i, i, 1, -1, f));
        }
        if (down && right) {
            if ((mask & DOWN_RIGHT) != 0)
                shape.add(new Area(new Rectangle2D.Double(u - i, u - i, i + ext, i + ext)));
            else shape.add(fillet(u - i, u - i, 1, 1, f));
        }
        if (down && left) {
            if ((mask & DOWN_LEFT) != 0)
                shape.add(new Area(new Rectangle2D.Double(-ext, u - i, i + ext, i + ext)));
            else shape.add(fillet(i, u - i, -1, 1, f));
        }

        if (wobbleVariant >= 0) {
            addEdgeWobble(shape, mask, wobbleVariant, i, i + r + 3);
        }
        return shape;
    }

    /** Adds the edge wobble for GroundTiles. */
    private static void addEdgeWobble(
            Area shape, int mask, int variant, double inset, double margin) {
        Random rnd = new Random(mask * 977L + variant * 131L + 5);
        int[] bits = {UP, RIGHT, DOWN, LEFT};
        for (int side = 0; side < 4; side++) {
            if ((mask & bits[side]) != 0)
                continue; // this side has an arm, so no straight edge here
            int count = 1 + rnd.nextInt(2);
            for (int n = 0; n < count; n++) {
                double along = margin + rnd.nextDouble() * (ART_TILE_SIZE - 2 * margin);
                double rad = 1.4 + rnd.nextDouble() * 1.4;
                boolean outward = rnd.nextBoolean();
                double cx, cy;
                switch (side) {
                    case 0 -> {
                        cx = along;
                        cy = inset;
                    }
                    case 1 -> {
                        cx = ART_TILE_SIZE - inset;
                        cy = along;
                    }
                    case 2 -> {
                        cx = along;
                        cy = ART_TILE_SIZE - inset;
                    }
                    default -> {
                        cx = inset;
                        cy = along;
                    }
                }
                Area bump = new Area(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
                if (outward) shape.add(bump);
                else shape.subtract(bump);
            }
        }
    }

    // the part of a corner square outside the quarter circle: subtract it to round a convex corner
    /** Resolves the round cut result used by GroundTiles. */
    private static Area roundCut(double rectX, double rectY, double circX, double circY, double r) {
        Area cut = new Area(new Rectangle2D.Double(rectX, rectY, r, r));
        cut.subtract(new Area(new Ellipse2D.Double(circX, circY, 2 * r, 2 * r)));
        return cut;
    }

    // the filler for a concave corner whose point is (px, py); (sx, sy) points into the empty
    // quadrant
    /** Resolves the fillet result used by GroundTiles. */
    private static Area fillet(double px, double py, int sx, int sy, double f) {
        Area a =
                new Area(
                        new Rectangle2D.Double(
                                Math.min(px, px + sx * f), Math.min(py, py + sy * f), f, f));
        double cx = px + sx * f, cy = py + sy * f;
        a.subtract(new Area(new Ellipse2D.Double(cx - f, cy - f, 2 * f, 2 * f)));
        return a;
    }
}
