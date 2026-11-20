package world.memorial;

import entity.Entity;

import main.Gamepanel;

import object.*;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Versioned item state, retaining all current public scalar item properties rather than recipes.
 */
public final class StoredItem {
    private static final java.util.Set<String> BASE_FIELDS =
            java.util.Set.of(
                    "name",
                    "description",
                    "value",
                    "attackValue",
                    "defenseValue",
                    "useCost",
                    "stackable",
                    "maxStackSize");
    public ItemType type;
    public String label;
    public String id = java.util.UUID.randomUUID().toString();
    public int count;
    public LinkedHashMap<String, String> values = new LinkedHashMap<>();

    /** Captures the actual instance; unknown items fail before any inventory mutation. */
    public static StoredItem capture(Entity item) {
        StoredItem s = new StoredItem();
        s.type = ItemRegistry.typeFor(item);
        s.count = item.stackCount;
        s.label =
                item.name == null
                        ? "Item"
                        : item.name.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        if (s.type == null || s.count < 1)
            throw new IllegalStateException("Unserializable carried item: " + item.name);
        for (Field f : item.getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    || Modifier.isFinal(f.getModifiers())
                    || f.getName().equals("stackCount")) continue;
            if (f.getDeclaringClass() == Entity.class && !BASE_FIELDS.contains(f.getName()))
                continue;
            Class<?> t = f.getType();
            if (!(t.isPrimitive() || t == String.class || t.isEnum())) continue;
            try {
                Object value = f.get(item);
                if (value != null)
                    s.values.put(
                            f.getName(),
                            Base64.getEncoder()
                                    .encodeToString(
                                            value.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return s;
    }

    /** Restores the same registered item and saved properties; no crafting reconstruction. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Entity restore(Gamepanel gp) {
        Entity item = ItemRegistry.create(type, gp);
        if (item == null || count < 1) throw new IllegalArgumentException("Invalid memorial item");
        for (var entry : values.entrySet()) {
            try {
                Field f = item.getClass().getField(entry.getKey());
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()))
                    continue;
                String value =
                        new String(
                                Base64.getDecoder().decode(entry.getValue()),
                                StandardCharsets.UTF_8);
                Class<?> t = f.getType();
                Object v;
                if (t == String.class) v = value;
                else if (t == int.class) v = Integer.parseInt(value);
                else if (t == long.class) v = Long.parseLong(value);
                else if (t == double.class) v = Double.parseDouble(value);
                else if (t == float.class) v = Float.parseFloat(value);
                else if (t == boolean.class) v = Boolean.parseBoolean(value);
                else if (t == short.class) v = Short.parseShort(value);
                else if (t == byte.class) v = Byte.parseByte(value);
                else if (t == char.class) v = value.charAt(0);
                else if (t.isEnum()) v = Enum.valueOf((Class) t, value);
                else continue;
                f.set(item, v);
            } catch (NoSuchFieldException ignored) {
                /* Removed optional fields are forward compatible. */
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(e);
            }
        }
        item.stackCount = count;
        return item;
    }
}
