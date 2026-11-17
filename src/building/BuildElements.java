package building;

import main.MiniJson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared data-driven palette. Shape defines behavior; id defines the saved material. */
public final class BuildElements {
    public enum Shape {
        FLOOR,
        WALL,
        DOOR,
        STAIRS;

        /** Evaluates the ground condition for Shape. */
        public boolean ground() {
            return this == FLOOR || this == STAIRS;
        }
    }

    public record Element(String id, String name, Shape shape, String atlas, String openAtlas) {
        /** Returns a textual representation of this value. */
        @Override
        public String toString() {
            return name;
        }
    }

    private static final Map<String, Element> ELEMENTS = load();

    /** Prevents instantiation of this utility class. */
    private BuildElements() {}

    /** Resolves the all result used by BuildElements. */
    public static Collection<Element> all() {
        return ELEMENTS.values();
    }

    /** Resolves the get result used by BuildElements. */
    public static Element get(String id) {
        Element result = ELEMENTS.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown building element: " + id);
        return result;
    }

    /** Resolves the load result used by BuildElements. */
    private static Map<String, Element> load() {
        try (InputStream in = BuildElements.class.getResourceAsStream("/building/elements.json")) {
            if (in == null) throw new IOException("Missing /building/elements.json");
            Object parsed = MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> root)
                    || !(root.get("elements") instanceof List<?> entries))
                throw new IOException("Invalid building catalog");
            Map<String, Element> result = new LinkedHashMap<>();
            for (Object value : entries) {
                if (!(value instanceof Map<?, ?> e)) throw new IOException("Invalid catalog entry");
                String id = text(e, "id"),
                        name = text(e, "name"),
                        atlas = resource(text(e, "atlas"));
                if (!id.matches("[a-z][a-z0-9_-]*"))
                    throw new IOException("Invalid element id: " + id);
                Shape shape = Shape.valueOf(text(e, "shape"));
                String open = shape == Shape.DOOR ? resource(text(e, "openAtlas")) : null;
                if (result.putIfAbsent(id, new Element(id, name, shape, atlas, open)) != null)
                    throw new IOException("Duplicate element: " + id);
            }
            for (Shape shape : List.of(Shape.FLOOR, Shape.WALL, Shape.DOOR)) {
                Element e = result.get(shape.name().toLowerCase(Locale.ROOT));
                if (e == null || e.shape() != shape)
                    throw new IOException("Missing default element for " + shape);
            }
            return Collections.unmodifiableMap(result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Resolves the text result used by BuildElements. */
    private static String text(Map<?, ?> e, String key) throws IOException {
        if (e.get(key) instanceof String s && !s.isBlank()) return s;
        throw new IOException("Missing catalog field: " + key);
    }

    /** Resolves the resource result used by BuildElements. */
    private static String resource(String value) throws IOException {
        if (!value.startsWith("/building/") || value.contains(".."))
            throw new IOException("Invalid building resource: " + value);
        return value;
    }
}
