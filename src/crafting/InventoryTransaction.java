package crafting;

import entity.Entity;
import entity.Player;

import main.Gamepanel;

import object.*;

import java.util.*;

/** Simulate on detached stacks, then swap all slots once. Failure never mutates live items. */
public final class InventoryTransaction {
    /** Prevents instantiation of this utility class. */
    private InventoryTransaction() {}

    /** Resolves the count result used by InventoryTransaction. */
    public static int count(Player p, ItemType type) {
        int n = 0;
        for (Entity e : p.inventorySlots) if (ItemRegistry.typeFor(e) == type) n += e.stackCount;
        return n;
    }

    /** Resolves the plan result used by InventoryTransaction. */
    public static Entity[] plan(
            Gamepanel gp, Player p, Map<ItemType, Integer> costs, Map<ItemType, Integer> outputs) {
        Entity[] copy = new Entity[p.inventorySlots.length];
        for (int i = 0; i < copy.length; i++)
            if (p.inventorySlots[i] != null) {
                ItemType type = ItemRegistry.typeFor(p.inventorySlots[i]);
                if (type == null) return null;
                copy[i] = ItemRegistry.create(type, gp);
                copy[i].stackCount = p.inventorySlots[i].stackCount;
            }
        for (var cost : costs.entrySet()) {
            int left = cost.getValue();
            if (left < 0) return null;
            for (int i = 0; i < copy.length && left > 0; i++)
                if (ItemRegistry.typeFor(copy[i]) == cost.getKey()) {
                    int take = Math.min(left, copy[i].stackCount);
                    left -= take;
                    copy[i].stackCount -= take;
                    if (copy[i].stackCount == 0) copy[i] = null;
                }
            if (left > 0) return null;
        }
        for (var output : outputs.entrySet()) {
            int left = output.getValue();
            if (left < 0) return null;
            Entity sample = ItemRegistry.create(output.getKey(), gp);
            if (sample.stackable)
                for (Entity e : copy)
                    if (e != null && e.canStackWith(sample)) {
                        int add = Math.min(left, e.maxStackSize - e.stackCount);
                        e.stackCount += add;
                        left -= add;
                    }
            for (int i = 0; i < copy.length && left > 0; i++)
                if (copy[i] == null) {
                    Entity item = ItemRegistry.create(output.getKey(), gp);
                    item.stackCount = Math.min(left, item.maxStackSize);
                    copy[i] = item;
                    left -= item.stackCount;
                }
            if (left > 0) return null;
        }
        return copy;
    }

    /** Evaluates the commit condition for InventoryTransaction. */
    public static boolean commit(
            Gamepanel gp, Player p, Map<ItemType, Integer> costs, Map<ItemType, Integer> outputs) {
        Entity[] result = plan(gp, p, costs, outputs);
        if (result == null) return false;
        p.inventorySlots = result;
        return true;
    }
}
