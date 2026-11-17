package net;

public class InventoryAction {
    public object.ItemType hotbarType;
    public int playerId;
    public InventoryActionType actionType;
    public int sourceIndex;
    public int targetIndex;
    public int slotType; // Player.EQUIP_* slot, including visual apparel
}