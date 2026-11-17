package building;

import static building.BuildElements.Shape.*;

import art.BuildGeometry;

import java.awt.Rectangle;
import java.util.*;
import java.util.function.Function;

/** Game-independent tile data and connection rules. Floors and structures are separate layers. */
public final class BuildingLayout {
    public record Cell(int col, int row) {
        /** Resolves the offset result used by Cell. */
        public Cell offset(int dc, int dr) {
            return new Cell(Math.addExact(col, dc), Math.addExact(row, dr));
        }
    }

    public record Tile(String element, boolean vertical, boolean open) {
        /** Creates a Tile instance and initializes the state required by its collaborators. */
        public Tile {
            BuildElements.Element e = BuildElements.get(element);
            if (e.shape().ground() && (vertical || open) || e.shape() != DOOR && open)
                throw new IllegalArgumentException("Invalid orientation/open state for " + element);
        }

        /** Resolves the shape result used by Tile. */
        public BuildElements.Shape shape() {
            return BuildElements.get(element).shape();
        }
    }

    public static final int[] DC = {0, 1, 0, -1}, DR = {-1, 0, 1, 0};
    private final List<Decoration> decorations = new ArrayList<>();
    private final Map<Cell, Tile> floors = new LinkedHashMap<>(),
            structures = new LinkedHashMap<>();

    /**
     * Creates a BuildingLayout instance and initializes the state required by its collaborators.
     */
    public BuildingLayout() {}

    /**
     * Creates a BuildingLayout instance and initializes the state required by its collaborators.
     */
    public BuildingLayout(BuildingLayout other) {
        floors.putAll(other.floors);
        structures.putAll(other.structures);
        decorations.addAll(other.decorations);
    }

    /** Resolves the floors result used by BuildingLayout. */
    public Map<Cell, Tile> floors() {
        return Collections.unmodifiableMap(floors);
    }

    /** Resolves the structures result used by BuildingLayout. */
    public Map<Cell, Tile> structures() {
        return Collections.unmodifiableMap(structures);
    }

    /** Evaluates the empty condition for BuildingLayout. */
    public boolean empty() {
        return floors.isEmpty() && structures.isEmpty() && decorations.isEmpty();
    }

    /** Performs the put operation for BuildingLayout. */
    public void put(Cell cell, Tile tile) {
        (tile.shape().ground() ? floors : structures).put(cell, tile);
    }

    /** Performs the erase operation for BuildingLayout. */
    public void erase(Cell cell, boolean floor) {
        (floor ? floors : structures).remove(cell);
    }

    /** Resolves the decorations result used by BuildingLayout. */
    public List<Decoration> decorations() {
        return Collections.unmodifiableList(decorations);
    }

    /** Adds the decoration for BuildingLayout. */
    public void addDecoration(Decoration item) {
        if (decorations.stream().anyMatch(d -> d.id().equals(item.id())))
            throw new IllegalArgumentException("Duplicate furnishing identity");
        decorations.add(item);
    }

    /** Removes the decoration for BuildingLayout. */
    public void removeDecoration(String id) {
        decorations.removeIf(d -> d.id().equals(id));
    }

    /** Shared editor/export validation so furnished presets remain placeable by the game. */
    public String decorationConflict() {
        for (int i = 0; i < decorations.size(); i++) {
            Decoration d = decorations.get(i);
            var def = PropCatalog.get(d.element());
            if (def.mounted()) {
                boolean supported = false;
                for (var e : structures.entrySet())
                    if (e.getValue().shape() == WALL) {
                        Cell c = e.getKey();
                        if (d.x() >= c.col * 48
                                && d.x() <= (c.col + 1) * 48
                                && d.y() >= c.row * 48 + 48 - BuildGeometry.HEIGHT
                                && d.y() <= (c.row + 1) * 48) supported = true;
                    }
                if (!supported) return "A wall-mounted lamp needs a supporting wall";
            }
            if (def.collisionWidth() == 0) continue;
            var bounds = def.bounds(d.x(), d.y(), d.flipped(), d.rotation());
            for (int j = 0; j < i; j++) {
                Decoration other = decorations.get(j);
                var p = PropCatalog.get(other.element());
                if (p.collisionWidth() > 0
                        && bounds.intersects(
                                p.bounds(other.x(), other.y(), other.flipped(), other.rotation())))
                    return "Solid furnishings overlap";
            }
            for (var e : structures.entrySet())
                for (Rectangle r : footprint(e.getKey(), e.getValue(), structures::get)) {
                    Cell at = e.getKey();
                    r.translate(at.col * 48, at.row * 48);
                    if (r.intersects(bounds)) return "A furnishing overlaps a wall or closed door";
                }
        }
        return null;
    }

    /** Resolves the structure mask result used by BuildingLayout. */
    public int structureMask(Cell cell) {
        return mask(cell, structures.keySet());
    }

    /** Resolves the mask result used by BuildingLayout. */
    public static int mask(Cell cell, Set<Cell> cells) {
        int mask = 0;
        for (int i = 0; i < 4; i++) if (cells.contains(cell.offset(DC[i], DR[i]))) mask |= 1 << i;
        return mask;
    }

    /** Includes the automatic diagonal corner, without mutating the layout. */
    public Map<Cell, Tile> plan(Cell cell, Tile tile) {
        return plan(structures, cell, tile);
    }

    /** Resolves the plan result used by BuildingLayout. */
    public static Map<Cell, Tile> plan(Map<Cell, Tile> structures, Cell cell, Tile tile) {
        return plan(structures::get, cell, tile);
    }

    /** Resolves the plan result used by BuildingLayout. */
    public static Map<Cell, Tile> plan(Function<Cell, Tile> structure, Cell cell, Tile tile) {
        Map<Cell, Tile> plan = new LinkedHashMap<>();
        plan.put(cell, tile);
        if (tile.shape() != WALL || structureMask(cell, structure) != 0) return plan;
        for (int dy : new int[] {-1, 1})
            for (int dx : new int[] {-1, 1}) {
                Cell diagonal = cell.offset(dx, dy);
                Tile end = structure.apply(diagonal);
                if (end == null || end.shape() != WALL) continue;
                int joins = structureMask(diagonal, structure);
                int awayH = dx < 0 ? 8 : 2,
                        towardH = dx < 0 ? 2 : 8,
                        awayV = dy < 0 ? 1 : 4,
                        towardV = dy < 0 ? 4 : 1;
                boolean horizontalEnd =
                        (joins & awayH) != 0 && (joins & towardH) == 0
                                || joins == 0 && !end.vertical() && tile.vertical();
                boolean verticalEnd =
                        (joins & awayV) != 0 && (joins & towardV) == 0
                                || joins == 0 && end.vertical() && !tile.vertical();
                Cell corner =
                        horizontalEnd
                                ? new Cell(cell.col(), diagonal.row())
                                : verticalEnd ? new Cell(diagonal.col(), cell.row()) : null;
                if (corner != null && structure.apply(corner) == null)
                    plan.put(corner, new Tile(tile.element(), false, false));
            }
        return plan;
    }

    /** Resolves the structure mask result used by BuildingLayout. */
    public static int structureMask(Cell cell, Function<Cell, Tile> structure) {
        int mask = 0;
        for (int i = 0; i < 4; i++)
            if (structure.apply(cell.offset(DC[i], DR[i])) != null) mask |= 1 << i;
        return mask;
    }

    /** Amount borrowed from a wall before/after the door along its horizontal or vertical axis. */
    public static int[] doorExtensions(Cell cell, Tile door, Function<Cell, Tile> structure) {
        boolean vertical = BuildGeometry.vertical(structureMask(cell, structure), door.vertical());
        Tile before = structure.apply(cell.offset(vertical ? 0 : -1, vertical ? -1 : 0));
        Tile after = structure.apply(cell.offset(vertical ? 0 : 1, vertical ? 1 : 0));
        return new int[] {
            before != null && before.shape() == WALL ? BuildGeometry.DOOR_BORROW : 0,
            after != null && after.shape() == WALL ? BuildGeometry.DOOR_BORROW : 0
        };
    }

    /** Neighbor-aware geometry shared by movement, previews, shadows and the standalone Builder. */
    public static List<Rectangle> footprint(Cell cell, Tile tile, Function<Cell, Tile> structure) {
        int mask = structureMask(cell, structure);
        List<Rectangle> base =
                BuildGeometry.footprint(mask, tile.vertical(), tile.shape() == DOOR, tile.open());
        if (tile.shape() == DOOR) {
            int[] extension = doorExtensions(cell, tile, structure);
            boolean vertical = BuildGeometry.vertical(mask, tile.vertical());
            int length = 48 + extension[0] + extension[1];
            List<Rectangle> result = new ArrayList<>();
            for (Rectangle r : base) {
                int start = (vertical ? r.y : r.x) * length / 48 - extension[0];
                int end =
                        ((vertical ? r.y + r.height : r.x + r.width) * length + 47) / 48
                                - extension[0];
                result.add(
                        vertical
                                ? new Rectangle(r.x, start, r.width, end - start)
                                : new Rectangle(start, r.y, end - start, r.height));
            }
            return result;
        }
        int[] trim = wallTrims(cell, tile, structure);
        List<Rectangle> result = new ArrayList<>(base);
        Rectangle[] cuts = {
            new Rectangle(16, 0, 16, trim[0]),
            new Rectangle(48 - trim[1], 0, trim[1], 48),
            new Rectangle(16, 48 - trim[2], 16, trim[2]),
            new Rectangle(0, 0, trim[3], 48)
        };
        for (Rectangle cut : cuts)
            if (!cut.isEmpty()) {
                List<Rectangle> next = new ArrayList<>();
                for (Rectangle r : result) {
                    Rectangle hit = r.intersection(cut);
                    if (hit.isEmpty()) {
                        next.add(r);
                        continue;
                    }
                    if (r.y < hit.y) next.add(new Rectangle(r.x, r.y, r.width, hit.y - r.y));
                    if (hit.y + hit.height < r.y + r.height)
                        next.add(
                                new Rectangle(
                                        r.x,
                                        hit.y + hit.height,
                                        r.width,
                                        r.y + r.height - hit.y - hit.height));
                    if (r.x < hit.x) next.add(new Rectangle(r.x, hit.y, hit.x - r.x, hit.height));
                    if (hit.x + hit.width < r.x + r.width)
                        next.add(
                                new Rectangle(
                                        hit.x + hit.width,
                                        hit.y,
                                        r.x + r.width - hit.x - hit.width,
                                        hit.height));
                }
                result = next;
            }
        return result;
    }

    /** N/E/S/W wall strips owned by a neighboring wider door frame. */
    public static int[] wallTrims(Cell cell, Tile tile, Function<Cell, Tile> structure) {
        int[] trims = new int[4];
        if (tile.shape() != WALL) return trims;
        for (int i = 0; i < 4; i++) {
            Cell n = cell.offset(DC[i], DR[i]);
            Tile door = structure.apply(n);
            if (door == null || door.shape() != DOOR) continue;
            boolean vertical = BuildGeometry.vertical(structureMask(n, structure), door.vertical());
            if (vertical != (i == 0 || i == 2)) continue;
            int[] extension = doorExtensions(n, door, structure);
            trims[i] = extension[i == 0 || i == 3 ? 1 : 0];
        }
        return trims;
    }
}
