package net;

public class InventoryAction {
    public int playerId;
    public String actionType; // "equip", "swap", "unequip_to_bag", "unequip_to_world", "drop", "use"
    public int sourceIndex;
    public int targetIndex;
    public int slotType; // Player.EQUIP_WEAPON / Player.EQUIP_SHIELD, when relevant
}