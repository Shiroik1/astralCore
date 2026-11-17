package object;

import entity.Entity;

import main.Gamepanel;

public class OBJ_chest extends Entity {

    /** Creates a OBJ_chest instance and initializes the state required by its collaborators. */
    public OBJ_chest(Gamepanel gp) {
        super(gp);
        name = "Chest";

        down1 = setup("/object/chest", gp.tileSize, gp.tileSize);
    }
}
