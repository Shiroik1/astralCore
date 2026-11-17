package art;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Shared design-space geometry for exported art, placement and movement. */
public final class BuildGeometry {
    public static final int TILE = 48, HEIGHT = 72, THICKNESS = 16, IMAGE_HEIGHT = TILE + HEIGHT;
    public static final int NORTH = 1, EAST = 2, SOUTH = 4, WEST = 8;
    public static final int DOOR_BORROW = 8;

    /** Prevents instantiation of this utility class. */
    private BuildGeometry() {}

    /** Evaluates the vertical condition for BuildGeometry. */
    public static boolean vertical(int mask, boolean preferred) {
        return preferred || (mask & (NORTH | SOUTH)) != 0 && (mask & (EAST | WEST)) == 0;
    }

    /** Axis-aligned ground footprints, before elevating their top surface by HEIGHT. */
    public static List<Rectangle> footprint(
            int mask, boolean preferred, boolean door, boolean open) {
        if (door) {
            boolean v = vertical(mask, preferred);
            if (!open)
                return List.of(v ? new Rectangle(16, 0, 16, 48) : new Rectangle(0, 32, 48, 16));
            return v
                    ? List.of(new Rectangle(16, 0, 16, 5), new Rectangle(16, 43, 16, 5))
                    : List.of(new Rectangle(0, 32, 5, 16), new Rectangle(43, 32, 5, 16));
        }
        boolean horizontal = (mask & (EAST | WEST)) != 0 || mask == 0 && !preferred;
        boolean vertical = (mask & (NORTH | SOUTH)) != 0 || mask == 0 && preferred;
        List<Rectangle> result = new ArrayList<>(2);
        if (horizontal) {
            // Corners stop at the perpendicular beam; isolated end pieces remain full modules.
            int left = vertical && (mask & WEST) == 0 ? 16 : 0;
            int right = vertical && (mask & EAST) == 0 ? 32 : 48;
            result.add(new Rectangle(left, 32, right - left, 16));
        }
        if (vertical) {
            int top = horizontal && (mask & NORTH) == 0 ? 32 : 0;
            result.add(new Rectangle(16, top, 16, 48 - top));
        }
        return result;
    }

    /** Resolves the scale result used by BuildGeometry. */
    public static Rectangle scale(Rectangle r, int tileSize) {
        int x = r.x * tileSize / TILE, y = r.y * tileSize / TILE;
        return new Rectangle(
                x,
                y,
                (r.x + r.width) * tileSize / TILE - x,
                (r.y + r.height) * tileSize / TILE - y);
    }
}
