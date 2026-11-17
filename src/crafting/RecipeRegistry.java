package crafting;

import main.MiniJson;

import object.ItemType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stable recipe/item ids and tunable balance, shared by validation, UI and saves. */
public final class RecipeRegistry {
    public record Item(
            String id,
            String name,
            String kind,
            String image,
            int power,
            String tool,
            int gather,
            String place) {
        /** Resolves the type result used by Item. */
        public ItemType type() {
            return ItemType.valueOf(id.toUpperCase(Locale.ROOT));
        }

        /** Evaluates the equipment condition for Item. */
        public boolean equipment() {
            return Set.of("axe", "sword", "staff", "bow").contains(kind);
        }
    }

    public record Recipe(
            String id,
            int level,
            String station,
            Map<ItemType, Integer> ingredients,
            ItemType output,
            int count,
            int xp,
            int firstBonus) {}

    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    public static final List<Recipe> RECIPES = new ArrayList<>();
    public static final Map<String, Integer> HARDNESS = new HashMap<>();
    public static final int[] XP;
    public static final int MAX_LEVEL;

    static {
        try (InputStream in = RecipeRegistry.class.getResourceAsStream("/crafting/recipes.json")) {
            if (in == null) throw new IOException("Missing crafting recipes");
            Map<?, ?> root =
                    (Map<?, ?>)
                            MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            MAX_LEVEL = n(root, "maxLevel");
            XP =
                    ((List<?>) root.get("xpToNext"))
                            .stream().mapToInt(v -> ((Number) v).intValue()).toArray();
            for (Object v : (List<?>) root.get("items")) {
                var m = (Map<?, ?>) v;
                Item def =
                        new Item(
                                s(m, "id"),
                                s(m, "name"),
                                s(m, "kind"),
                                s(m, "image"),
                                n(m, "power"),
                                s(m, "tool"),
                                n(m, "gather"),
                                s(m, "place"));
                if (ITEMS.putIfAbsent(def.id(), def) != null)
                    throw new IOException("Duplicate crafting item");
                def.type();
            }
            for (Object v : (List<?>) root.get("recipes")) {
                var m = (Map<?, ?>) v;
                Map<ItemType, Integer> ingredients = new LinkedHashMap<>();
                ((Map<?, ?>) m.get("ingredients"))
                        .forEach((k, q) ->
                                ingredients.put(item((String) k).type(), ((Number) q).intValue()));
                Recipe r =
                        new Recipe(
                                s(m, "id"),
                                n(m, "level"),
                                s(m, "station"),
                                Collections.unmodifiableMap(ingredients),
                                item(s(m, "output")).type(),
                                n(m, "count"),
                                n(m, "xp"),
                                n(m, "firstBonus"));
                if (r.level() < 1
                        || r.count() < 1
                        || r.xp() < 1
                        || ingredients.values().stream().anyMatch(q -> q < 1))
                    throw new IOException("Invalid recipe");
                RECIPES.add(r);
            }
            ((Map<?, ?>) root.get("nodeHardness"))
                    .forEach((k, v) -> HARDNESS.put((String) k, ((Number) v).intValue()));
            if (XP.length < MAX_LEVEL - 1 || Arrays.stream(XP).anyMatch(x -> x < 1))
                throw new IOException("Invalid XP curve");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Prevents instantiation of this utility class. */
    private RecipeRegistry() {}

    /** Reads a typed configuration value and applies the local conversion or validation rules. */
    private static int n(Map<?, ?> m, String k) {
        return ((Number) m.get(k)).intValue();
    }

    /** Reads a typed configuration value and applies the local conversion or validation rules. */
    private static String s(Map<?, ?> m, String k) {
        return (String) m.get(k);
    }

    /** Resolves the item result used by RecipeRegistry. */
    public static Item item(String id) {
        Item i = ITEMS.get(id);
        if (i == null) throw new IllegalArgumentException("Unknown crafting item: " + id);
        return i;
    }

    /** Resolves the item result used by RecipeRegistry. */
    public static Item item(ItemType type) {
        return type == null ? null : ITEMS.get(type.name().toLowerCase(Locale.ROOT));
    }
}
