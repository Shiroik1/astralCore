package net;

public class InventoryAction {
    public int playerId;
    public InventoryActionType actionType;
    public int sourceIndex;
    public int targetIndex;
    public int slotType; // Player.EQUIP_WEAPON / Player.EQUIP_SHIELD, when relevant
}