package art;

import java.awt.Shape;

/**
 * Same rounded eight-neighbour connection geometry as ground tiles; independent of building masks.
 */
public final class CliffTiles {
    public static final int TILE = 48;

    /** Prevents instantiation of this utility class. */
    private CliffTiles() {}

    /** Resolves the surface result used by CliffTiles. */
    public static Shape surface(int mask) {
        return GroundTiles.blobShape(mask, GroundTiles.GRASS_INSET, 7, 5, -1);
    }
}
