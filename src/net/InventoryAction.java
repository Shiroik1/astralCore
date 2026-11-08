package net;

public class InventoryAction {
    public int playerId;
    public InventoryActionType actionType;
    public int sourceIndex;
    public int targetIndex;
    public int slotType; // Player.EQUIP_* slot, including visual apparel
}