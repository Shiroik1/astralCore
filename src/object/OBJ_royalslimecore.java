package object;

import entity.Entity;

import main.Gamepanel;

/** A trophy material using the existing crystal icon, separate from the well's essence. */
public final class OBJ_royalslimecore extends Entity {
    /**
     * Creates a OBJ_royalslimecore instance and initializes the state required by its
     * collaborators.
     */
    public OBJ_royalslimecore(Gamepanel gp) {
        super(gp);
        name = "Royal Slime Core";
        stackable = true;
        maxStackSize = 99;
        down1 = new OBJ_crystal(gp).down1;
        description =
                "(Royal Slime Core)\n"
                        + "A trophy from the Crowned Slime.\n"
                        + "Not required for basic crafting.";
    }
}
