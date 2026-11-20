package object;

import entity.Entity;

import main.MiniJson;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared, read-only equipment definitions loaded once from res/equipment/items.json. */
public final class EquipmentDatabase {
    public record Entry(
            ItemType id,
            String name,
            String slot,
            int minLevel,
            int attack,
            int armor,
            int hp,
            int mp,
            String color) {
        /** Cosmetic equipment changes appearance but grants no combat statistics. */
        public boolean cosmetic() {
            return attack == 0 && armor == 0 && hp == 0 && mp == 0;
        }
    }

    private static final Map<ItemType, Entry> ENTRIES = load();
    // Legacy hair definitions remain readable for migration, but are no longer equipment.
    private static final List<Entry> EQUIPMENT =
            ENTRIES.values().stream().filter(entry -> !entry.slot().equals("HAIR")).toList();

    /** Uses shared immutable definitions rather than per-player catalog instances. */
    private EquipmentDatabase() {}

    /** Stable IDs retain existing item factories, art, inventory and save compatibility. */
    public static Entry get(ItemType id) {
        return id == null ? null : ENTRIES.get(id);
    }

    /** Resolves an equipped runtime item's definition without allocating a new item. */
    public static Entry forItem(Entity item) {
        return get(ItemRegistry.typeFor(item));
    }

    /** Exposes immutable definitions for authoring tools or equipment browser interfaces. */
    public static Collection<Entry> all() {
        return EQUIPMENT;
    }

    /** Applies authored base statistics while retaining the existing image and item behavior. */
    static Entity apply(Entity item, ItemType id) {
        Entry e = get(id);
        if (item == null || e == null) return item;
        item.name = e.name;
        item.attackValue = e.attack;
        item.defenseValue = e.armor;
        item.description =
                "("
                        + e.name
                        + ")\n"
                        + e.slot
                        + " · Level "
                        + e.minLevel
                        + "\n"
                        + (e.cosmetic()
                                ? "Appearance only · No stat bonuses."
                                : "Power "
                                        + e.attack
                                        + " · Armor "
                                        + e.armor
                                        + "\nHP +"
                                        + e.hp
                                        + " · MP +"
                                        + e.mp);
        return item;
    }

    /** Supplies the authored garment color to the existing vector-art factory. */
    public static Color color(ItemType id, Color fallback) {
        Entry e = get(id);
        return e == null || e.color.isBlank() ? fallback : Color.decode(e.color);
    }

    /** Rejects malformed or duplicate definitions at startup rather than during combat. */
    private static Map<ItemType, Entry> load() {
        try (var in = EquipmentDatabase.class.getResourceAsStream("/equipment/items.json")) {
            if (in == null) throw new IllegalStateException("Missing equipment/items.json");
            var root =
                    (Map<?, ?>)
                            MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (((Number) root.get("version")).intValue() != 1)
                throw new IllegalArgumentException("Unsupported equipment database");
            Map<ItemType, Entry> out = new EnumMap<>(ItemType.class);
            for (Object value : (List<?>) root.get("equipment")) {
                var row = (Map<?, ?>) value;
                ItemType id = ItemType.valueOf((String) row.get("id"));
                String name = (String) row.get("name"),
                        slot = (String) row.get("slot"),
                        color = (String) row.get("color");
                if (name == null
                        || name.isBlank()
                        || !Set.of(
                                        "WEAPON",
                                        "SHIELD",
                                        "HAIR",
                                        "TORSO",
                                        "PANTS",
                                        "BOOTS",
                                        "GLOVES",
                                        "CLOAK",
                                        "HEADPIECE")
                                .contains(slot))
                    throw new IllegalArgumentException("Invalid equipment " + id);
                if (!color.isBlank()) Color.decode(color);
                Entry entry =
                        new Entry(
                                id,
                                name,
                                slot,
                                n(row, "minLevel", 1, 25),
                                n(row, "attack", 0, 10000),
                                n(row, "armor", 0, 10000),
                                n(row, "hp", 0, 100000),
                                n(row, "mp", 0, 100000),
                                color);
                if (out.put(id, entry) != null)
                    throw new IllegalArgumentException("Duplicate equipment " + id);
            }
            return Collections.unmodifiableMap(out);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid equipment database", e);
        }
    }

    /** Reads a bounded integral value from the authored database. */
    private static int n(Map<?, ?> row, String key, int min, int max) {
        Number value = (Number) row.get(key);
        int n = value.intValue();
        if (n < min || n > max || n != value.doubleValue())
            throw new IllegalArgumentException("Invalid " + key);
        return n;
    }
}
