package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Shared vector border shapes and grass-side connectors for both kinds of paving. */
public final class CobbleTransitions {
    private static final Map<Integer, float[]> EDGE_COVERAGE = new HashMap<>();

    /** Prevents instantiation of this utility class. */
    private CobbleTransitions() {}

    /** Resolves the canonical mask result used by CobbleTransitions. */
    public static int canonicalMask(int mask) {
        int result = mask & 15;
        if ((result & 3) == 3) result |= mask & GroundTiles.UP_RIGHT;
        if ((result & 6) == 6) result |= mask & GroundTiles.DOWN_RIGHT;
        if ((result & 12) == 12) result |= mask & GroundTiles.DOWN_LEFT;
        if ((result & 9) == 9) result |= mask & GroundTiles.UP_LEFT;
        return result;
    }

    /**
     * Exposed grass grows into the paving; connected edges remain open right across the tile seam.
     */
    public static Area footprint(int connections) {
        int mask = canonicalMask(connections);
        Area shape = GroundTiles.blobShape(mask, 4, 13, 11, -1);
        int[] sides = {GroundTiles.UP, GroundTiles.RIGHT, GroundTiles.DOWN, GroundTiles.LEFT};
        for (int side = 0; side < sides.length; side++)
            if ((mask & sides[side]) == 0) {
                AffineTransform rotate =
                        AffineTransform.getRotateInstance(side * Math.PI / 2, 24, 24);
                // A shallow rounded grass inlet avoids repeated sharp notches along a long border.
                shape.subtract(
                        new Area(
                                rotate.createTransformedShape(
                                        new Ellipse2D.Double(18, 1, 12, 5.5))));
            }
        return shape;
    }

    /**
     * Export-only edge feathering reveals the real blended grass below, with no fixed green border.
     */
    public static BufferedImage finish(ArtKit.Surface surface, int mask) {
        BufferedImage image = surface.finish();
        float[] coverage =
                EDGE_COVERAGE.computeIfAbsent(canonicalMask(mask), CobbleTransitions::coverage);
        for (int y = 0; y < 48; y++)
            for (int x = 0; x < 48; x++) {
                int pixel = image.getRGB(x, y), alpha = pixel >>> 24;
                if (alpha == 0) continue;
                int faded = Math.round(alpha * coverage[y * 48 + x]);
                image.setRGB(x, y, (faded << 24) | (pixel & 0xffffff));
            }
        return image;
    }

    /** Resolves the coverage result used by CobbleTransitions. */
    private static float[] coverage(int mask) {
        // Padding preserves coverage across connected tile seams; only exposed contours fade.
        int pad = 5, size = 48 + pad * 2;
        float[] distance = new float[size * size];
        Area shape = footprint(mask);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                distance[y * size + x] = shape.contains(x - pad + .5, y - pad + .5) ? 1000 : 0;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                if (x > 0) distance[i] = Math.min(distance[i], distance[i - 1] + 1);
                if (y > 0) {
                    distance[i] = Math.min(distance[i], distance[i - size] + 1);
                    if (x > 0)
                        distance[i] = Math.min(distance[i], distance[i - size - 1] + 1.414214f);
                    if (x + 1 < size)
                        distance[i] = Math.min(distance[i], distance[i - size + 1] + 1.414214f);
                }
            }
        for (int y = size - 1; y >= 0; y--)
            for (int x = size - 1; x >= 0; x--) {
                int i = y * size + x;
                if (x + 1 < size) distance[i] = Math.min(distance[i], distance[i + 1] + 1);
                if (y + 1 < size) {
                    distance[i] = Math.min(distance[i], distance[i + size] + 1);
                    if (x > 0)
                        distance[i] = Math.min(distance[i], distance[i + size - 1] + 1.414214f);
                    if (x + 1 < size)
                        distance[i] = Math.min(distance[i], distance[i + size + 1] + 1.414214f);
                }
            }
        float[] result = new float[48 * 48];
        for (int y = 0; y < 48; y++)
            for (int x = 0; x < 48; x++) {
                float t = Math.min(1, distance[(y + pad) * size + x + pad] / 3.25f);
                result[y * 48 + x] = t * t * (3 - 2 * t);
            }
        return result;
    }

    /**
     * Sparse, complete edge stones taper into adjacent grass instead of ending at a square grid
     * border.
     */
    public static BufferedImage fringe(int neighbors) {
        ArtKit.Surface surface = new ArtKit.Surface(48, 48);
        Graphics2D g = surface.g;
        int[] sides = {GroundTiles.UP, GroundTiles.RIGHT, GroundTiles.DOWN, GroundTiles.LEFT};
        for (int side = 0; side < sides.length; side++)
            if ((neighbors & sides[side]) != 0) {
                AffineTransform rotate =
                        AffineTransform.getRotateInstance(side * Math.PI / 2, 24, 24);
                pebble(g, rotate, 14, 3, 6.5, 4.2);
                pebble(g, rotate, 33, 6, 4.5, 3.2);
            }
        int[] diagonals = {
            GroundTiles.UP_LEFT, GroundTiles.UP_RIGHT, GroundTiles.DOWN_RIGHT, GroundTiles.DOWN_LEFT
        };
        int[] adjoining = {
            GroundTiles.UP | GroundTiles.LEFT,
            GroundTiles.UP | GroundTiles.RIGHT,
            GroundTiles.DOWN | GroundTiles.RIGHT,
            GroundTiles.DOWN | GroundTiles.LEFT
        };
        for (int corner = 0; corner < diagonals.length; corner++) {
            if ((neighbors & diagonals[corner]) == 0 || (neighbors & adjoining[corner]) != 0)
                continue;
            AffineTransform rotate =
                    AffineTransform.getRotateInstance(corner * Math.PI / 2, 24, 24);
            pebble(g, rotate, 4, 4, 5.5, 3.5);
        }
        return surface.finish();
    }

    /** Performs the pebble operation for CobbleTransitions. */
    private static void pebble(
            Graphics2D g, AffineTransform rotate, double x, double y, double width, double height) {
        Shape stone =
                rotate.createTransformedShape(
                        new RoundRectangle2D.Double(
                                x - width / 2, y - height / 2, width, height, 3, 3));
        Color base = ArtKit.mix(Palette.ROCK, Palette.PATH, .24f);
        g.setColor(ArtKit.alpha(base, 140));
        g.fill(stone);
        Shape light =
                rotate.createTransformedShape(
                        new RoundRectangle2D.Double(
                                x - width * .3, y - height * .25, width * .6, height * .3, 2, 2));
        g.setColor(ArtKit.alpha(ArtKit.mix(base, Palette.PLASTER, .28f), 110));
        g.fill(light);
    }
}
