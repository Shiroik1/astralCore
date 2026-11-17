package inventory;

import entity.*;

import main.Gamepanel;

import net.*;

import object.*;

import java.util.Arrays;

/**
 * Stable item-type shortcuts into the existing bag/equipment. Never owns or creates item stacks.
 */
public final class Hotbar {
    public static final int SIZE = 10;
    private final ItemType[] assignments = new ItemType[SIZE];
    public int selected;
    private boolean placement;

    /** Captures the current state in the representation used for persistence or replication. */
    public ItemType[] snapshot() {
        return assignments.clone();
    }

    /** Resolves the type result used by Hotbar. */
    public ItemType type(int slot) {
        return slot >= 0 && slot < SIZE ? assignments[slot] : null;
    }

    /** Evaluates the owned condition for Hotbar. */
    public static boolean owned(Player p, ItemType type) {
        return find(p, type) != null;
    }

    /** Resolves the find result used by Hotbar. */
    private static Entity find(Player p, ItemType type) {
        if (type == null) return null;
        for (Entity item : p.inventorySlots) if (ItemRegistry.typeFor(item) == type) return item;
        for (int slot = Player.EQUIP_WEAPON; slot <= Player.EQUIP_HEADPIECE; slot++) {
            Entity item = p.equippedItem(slot);
            if (item != null && !item.isPlaceholder && ItemRegistry.typeFor(item) == type)
                return item;
        }
        return null;
    }

    /** Resolves the item result used by Hotbar. */
    public Entity item(Player p, int slot) {
        return find(p, type(slot));
    }

    /** Resolves the count result used by Hotbar. */
    public int count(Player p, int slot) {
        ItemType type = type(slot);
        if (type == null) return 0;
        int n = 0;
        for (Entity item : p.inventorySlots)
            if (ItemRegistry.typeFor(item) == type) n += item.stackCount;
        return n == 0 && find(p, type) != null ? 1 : n;
    }

    /** Evaluates the assign condition for Hotbar. */
    public boolean assign(Player p, int slot, ItemType type) {
        if (slot < 0 || slot >= SIZE || type != null && !owned(p, type)) return false;
        assignments[slot] = type;
        return true;
    }

    /** Performs the clear operation for Hotbar. */
    public void clear(int slot) {
        if (slot >= 0 && slot < SIZE) assignments[slot] = null;
    }

    /** Performs the swap operation for Hotbar. */
    public void swap(int a, int b) {
        if (a < 0 || b < 0 || a >= SIZE || b >= SIZE) return;
        ItemType type = assignments[a];
        assignments[a] = assignments[b];
        assignments[b] = type;
    }

    /** Restores this component from its saved state. */
    public void restore(Player p, ItemType[] data, int selected) {
        Arrays.fill(assignments, null);
        if (data != null) System.arraycopy(data, 0, assignments, 0, Math.min(SIZE, data.length));
        this.selected = Math.max(0, Math.min(SIZE - 1, selected));
        normalize(p);
    }

    /** Performs the normalize operation for Hotbar. */
    public void normalize(Player p) {
        for (int i = 0; i < SIZE; i++)
            if (assignments[i] != null && !owned(p, assignments[i])) assignments[i] = null;
        if (placement && item(p, selected) == null) {
            if (p == p.gp.localPlayer()) {
                p.gp.buildings.stop();
                p.gp.crafting.cancelPlacement();
            }
            placement = false;
        }
    }

    /** Performs the select operation for Hotbar. */
    public void select(Player p, int slot) {
        select(p, slot, true);
    }

    /** Performs the select operation for Hotbar. */
    public void select(Player p, int slot, boolean activate) {
        if (slot < 0 || slot >= SIZE) return;
        selected = slot;
        if (!activate) return;
        Gamepanel gp = p.gp;
        if (p == gp.localPlayer()) {
            gp.buildings.stop();
            gp.crafting.cancelPlacement();
            placement = false;
        }
        Entity item = item(p, slot);
        if (item == null) return;
        if (isWeapon(item)) {
            if (ItemRegistry.typeFor(p.currentWeapon) != type(slot))
                for (int i = 0; i < p.inventorySlots.length; i++)
                    if (ItemRegistry.typeFor(p.inventorySlots[i]) == type(slot)) {
                        p.tryEquipFromBag(i, Player.EQUIP_WEAPON);
                        break;
                    }
        } else if (item instanceof OBJ_crafted crafted
                && !crafted.definition.place().isEmpty()
                && p == gp.localPlayer()
                && !gp.isNetworked) {
            gp.crafting.beginPlace(type(slot));
            placement = true;
        }
    }

    /** Reports whether weapon for Hotbar. */
    public static boolean isWeapon(Entity item) {
        return item != null
                && (item.type == Entity.type_sword
                        || item.type == Entity.type_axe
                        || item.type == Entity.type_staff
                        || item.type == Entity.type_bow);
    }

    /** Evaluates the use selected condition for Hotbar. */
    public boolean useSelected(Player p, InputState input) {
        Entity item = item(p, selected);
        if (item == null || isWeapon(item)) return false;
        input.leftHeld = false;
        if (!input.leftClicked) return true;
        if (item.type == Entity.type_consumable) {
            for (int i = 0; i < p.inventorySlots.length; i++)
                if (ItemRegistry.typeFor(p.inventorySlots[i]) == type(selected)) {
                    InventoryAction action = new InventoryAction();
                    action.playerId = p.playerId;
                    action.actionType = InventoryActionType.USE;
                    action.sourceIndex = i;
                    p.gp.requestInventoryAction(action);
                    break;
                }
        }
        input.leftClicked = false;
        input.leftHeld = false;
        return true;
    }
}
