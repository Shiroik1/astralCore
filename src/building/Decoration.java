package building;

import java.util.*;

/** Stable identity and sub-tile position, independent of a floor/wall cell. */
public record Decoration(
        String id, String element, double x, double y, boolean flipped, int rotation) {
    /** Creates a Decoration instance and initializes the state required by its collaborators. */
    public Decoration {
        if (id == null
                || !id.matches("[a-zA-Z0-9_-]{1,100}")
                || !Double.isFinite(x)
                || !Double.isFinite(y))
            throw new IllegalArgumentException("Invalid decoration position/identity");
        rotation = Math.floorMod(rotation, 4);
        if (!PropCatalog.get(element).rotatable()) rotation = 0;
    }

    /** Creates a Decoration instance and initializes the state required by its collaborators. */
    public Decoration(String id, String element, double x, double y, boolean flipped) {
        this(id, element, x, y, flipped, 0);
    }

    /** Resolves the create result used by Decoration. */
    public static Decoration create(
            String element, double x, double y, boolean flipped, int rotation) {
        return new Decoration(UUID.randomUUID().toString(), element, x, y, flipped, rotation);
    }

    /** Resolves the create result used by Decoration. */
    public static Decoration create(String element, double x, double y, boolean flipped) {
        return new Decoration(UUID.randomUUID().toString(), element, x, y, flipped);
    }

    /** Resolves the offset result used by Decoration. */
    public Decoration offset(double x, double y) {
        return new Decoration(id, element, this.x + x, this.y + y, flipped, rotation);
    }

    /** Resolves the parse result used by Decoration. */
    public static List<Decoration> parse(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list))
            throw new IllegalArgumentException("Invalid decorations");
        List<Decoration> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> e)
                    || !(e.get("x") instanceof Number x)
                    || !(e.get("y") instanceof Number y))
                throw new IllegalArgumentException("Invalid decoration entry");
            Decoration d =
                    new Decoration(
                            (String) e.get("id"),
                            (String) e.get("element"),
                            x.doubleValue(),
                            y.doubleValue(),
                            Boolean.TRUE.equals(e.get("flipped")),
                            rotation(e));
            if (!ids.add(d.id)) throw new IllegalArgumentException("Duplicate decoration id");
            result.add(d);
        }
        return List.copyOf(result);
    }

    /** Resolves the rotation result used by Decoration. */
    private static int rotation(Map<?, ?> e) {
        if (!e.containsKey("rotation")) return 0;
        if (!(e.get("rotation") instanceof Number n)
                || n.doubleValue() != n.intValue()
                || n.intValue() < 0
                || n.intValue() > 3)
            throw new IllegalArgumentException("Invalid furniture rotation");
        return n.intValue();
    }

    /** Resolves the json result used by Decoration. */
    public static String json(Collection<Decoration> props) {
        List<String> result = new ArrayList<>();
        for (Decoration d : props)
            result.add(
                    "    {\"id\": "
                            + quote(d.id)
                            + ", \"element\": "
                            + quote(d.element)
                            + ", \"x\": "
                            + d.x
                            + ", \"y\": "
                            + d.y
                            + ", \"flipped\": "
                            + d.flipped
                            + ", \"rotation\": "
                            + d.rotation
                            + "}");
        return "[\n" + String.join(",\n", result) + "\n  ]";
    }

    /** Resolves the quote result used by Decoration. */
    private static String quote(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                + "\"";
    }
}
