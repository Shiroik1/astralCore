package building;

import static building.BuildingLayout.*;

import main.MiniJson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Versioned, portable authoring format. Does not contain game/world coordinates or vegetation
 * edits.
 */
public record BuildingBlueprint(
        String id,
        String name,
        String category,
        int width,
        int height,
        Cell anchor,
        BuildingLayout layout) {
    public static final int VERSION = 2, MAX_SIZE = 64;

    /**
     * Creates a BuildingBlueprint instance and initializes the state required by its collaborators.
     */
    public BuildingBlueprint {
        if (id == null || !id.matches("[a-z][a-z0-9_-]*"))
            throw new IllegalArgumentException(
                    "ID must start with a-z and contain only a-z, 0-9, - or _");
        if (name == null || name.isBlank() || category == null || category.isBlank())
            throw new IllegalArgumentException("Name and category are required");
        if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE)
            throw new IllegalArgumentException("Blueprint dimensions must be 1..64");
        if (anchor == null || !inside(anchor, width, height))
            throw new IllegalArgumentException("Anchor must be inside the blueprint");
        Objects.requireNonNull(layout);
        layout = new BuildingLayout(layout);
        for (Cell c : layout.floors().keySet())
            if (!inside(c, width, height))
                throw new IllegalArgumentException("Floor outside blueprint");
        for (Cell c : layout.structures().keySet())
            if (!inside(c, width, height))
                throw new IllegalArgumentException("Structure outside blueprint");
        for (Decoration d : layout.decorations())
            if (!decorationInside(d, width, height))
                throw new IllegalArgumentException("Furnishing outside blueprint");
        String conflict = layout.decorationConflict();
        if (conflict != null) throw new IllegalArgumentException(conflict);
    }

    /** Resolves the layout result used by BuildingBlueprint. */
    @Override
    public BuildingLayout layout() {
        return new BuildingLayout(layout);
    }

    /** Evaluates the inside condition for BuildingBlueprint. */
    public static boolean inside(Cell c, int width, int height) {
        return c.col() >= 0 && c.row() >= 0 && c.col() < width && c.row() < height;
    }

    /** Evaluates the decoration inside condition for BuildingBlueprint. */
    public static boolean decorationInside(Decoration d, int width, int height) {
        var prop = PropCatalog.get(d.element());
        var b = prop.placementBounds(d.x(), d.y(), d.flipped(), d.rotation());
        var visual = prop.visualBounds(d.x(), d.y(), d.flipped(), d.rotation());
        return b.getMinX() >= 0
                && b.getMinY() >= (prop.mounted() ? -art.BuildGeometry.HEIGHT : 0)
                && b.getMaxX() <= width * 48
                && b.getMaxY() <= height * 48
                && visual.getMinX() >= 0
                && visual.getMaxX() <= width * 48
                && visual.getMinY() >= -art.BuildGeometry.HEIGHT
                && visual.getMaxY() <= height * 48;
    }

    /** Resolves the decorations at result used by BuildingBlueprint. */
    public List<Decoration> decorationsAt(int col, int row) {
        double dx = Math.subtractExact(col, anchor.col()) * 48.0,
                dy = Math.subtractExact(row, anchor.row()) * 48.0;
        return layout.decorations().stream()
                .map(d ->
                        new Decoration(
                                UUID.randomUUID().toString(),
                                d.element(),
                                d.x() + dx,
                                d.y() + dy,
                                d.flipped(),
                                d.rotation()))
                .toList();
    }

    public record Placed(Cell cell, Tile tile) {}

    /** Anchor coordinates let settlement generators align an entrance with a road. */
    public List<Placed> placedAt(int anchorCol, int anchorRow) {
        List<Placed> entries = new ArrayList<>();
        for (var layer : List.of(layout.floors(), layout.structures()))
            layer.forEach((c, t) ->
                    entries.add(
                            new Placed(
                                    c.offset(
                                            Math.subtractExact(anchorCol, anchor.col()),
                                            Math.subtractExact(anchorRow, anchor.row())),
                                    t)));
        return List.copyOf(entries);
    }

    /** Resolves the read result used by BuildingBlueprint. */
    public static BuildingBlueprint read(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** Reads the resource for BuildingBlueprint. */
    public static BuildingBlueprint readResource(String resource) throws IOException {
        try (InputStream in = BuildingBlueprint.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Missing blueprint: " + resource);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Resolves the parse result used by BuildingBlueprint. */
    public static BuildingBlueprint parse(String json) throws IOException {
        try {
            if (!(MiniJson.parse(json) instanceof Map<?, ?> root)
                    || (integer(root, "version") < 1 || integer(root, "version") > VERSION))
                throw new IllegalArgumentException("Unsupported blueprint version");
            if (!(root.get("anchor") instanceof Map<?, ?> a)
                    || !(root.get("elements") instanceof List<?> elements))
                throw new IllegalArgumentException("Missing anchor/elements");
            BuildingLayout layout = new BuildingLayout();
            for (Object value : elements) {
                if (!(value instanceof Map<?, ?> e))
                    throw new IllegalArgumentException("Invalid element entry");
                Cell c = new Cell(integer(e, "col"), integer(e, "row"));
                Tile tile = new Tile(string(e, "element"), bool(e, "vertical"), bool(e, "open"));
                if ((tile.shape().ground() ? layout.floors() : layout.structures()).containsKey(c))
                    throw new IllegalArgumentException("Duplicate element on one layer at " + c);
                layout.put(c, tile);
            }
            for (Decoration d : Decoration.parse(root.get("decorations"))) layout.addDecoration(d);
            return new BuildingBlueprint(
                    string(root, "id"),
                    string(root, "name"),
                    string(root, "category"),
                    integer(root, "width"),
                    integer(root, "height"),
                    new Cell(integer(a, "col"), integer(a, "row")),
                    layout);
        } catch (RuntimeException e) {
            throw new IOException("Invalid blueprint: " + e.getMessage(), e);
        }
    }

    /** Resolves the integer result used by BuildingBlueprint. */
    private static int integer(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof Number n)
                || !Double.isFinite(n.doubleValue())
                || n.doubleValue() != n.intValue())
            throw new IllegalArgumentException("Invalid integer: " + key);
        return n.intValue();
    }

    /** Resolves the string result used by BuildingBlueprint. */
    private static String string(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String s) return s;
        throw new IllegalArgumentException("Missing string: " + key);
    }

    /** Evaluates the bool condition for BuildingBlueprint. */
    private static boolean bool(Map<?, ?> map, String key) {
        if (!map.containsKey(key)) return false;
        if (map.get(key) instanceof Boolean b) return b;
        throw new IllegalArgumentException("Invalid boolean: " + key);
    }

    /** Resolves the json result used by BuildingBlueprint. */
    public String json() {
        StringBuilder out =
                new StringBuilder("{\n  \"version\": 2,\n  \"id\": ")
                        .append(quote(id))
                        .append(",\n  \"name\": ")
                        .append(quote(name))
                        .append(",\n  \"category\": ")
                        .append(quote(category))
                        .append(",\n  \"width\": ")
                        .append(width)
                        .append(",\n  \"height\": ")
                        .append(height)
                        .append(",\n  \"anchor\": {\"col\": ")
                        .append(anchor.col())
                        .append(", \"row\": ")
                        .append(anchor.row())
                        .append("},\n  \"elements\": [\n");
        List<String> entries = new ArrayList<>();
        for (var layer : List.of(layout.floors(), layout.structures()))
            layer.entrySet().stream()
                    .sorted(
                            Comparator.<Map.Entry<Cell, Tile>>comparingInt(e -> e.getKey().row())
                                    .thenComparingInt(e -> e.getKey().col()))
                    .forEach(e -> {
                        Cell c = e.getKey();
                        Tile t = e.getValue();
                        entries.add(
                                "    {\"element\": "
                                        + quote(t.element())
                                        + ", \"col\": "
                                        + c.col()
                                        + ", \"row\": "
                                        + c.row()
                                        + ", \"vertical\": "
                                        + t.vertical()
                                        + ", \"open\": "
                                        + t.open()
                                        + "}");
                    });
        return out.append(String.join(",\n", entries))
                .append("\n  ],\n  \"decorations\": ")
                .append(Decoration.json(layout.decorations()))
                .append("\n}\n")
                .toString();
    }

    /** Resolves the quote result used by BuildingBlueprint. */
    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray())
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        return out.append('"').toString();
    }

    /** Performs the save operation for BuildingBlueprint. */
    public void save(Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "blueprint-", ".tmp");
        try {
            Files.writeString(temp, json(), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
