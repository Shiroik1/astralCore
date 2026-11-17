package object;

import entity.Entity;

import main.Gamepanel;

/** Reuses the established jelly icon; no new runtime texture load. */
public final class OBJ_slimegel extends Entity {
    /** Creates a OBJ_slimegel instance and initializes the state required by its collaborators. */
    public OBJ_slimegel(Gamepanel gp) {
        super(gp);
        name = "Slime Gel";
        stackable = true;
        maxStackSize = 99;
        down1 = new OBJ_slimeessence(gp).down1;
        description = "(Slime Gel)\nA soft glob of wild slime jelly.";
    }
}
