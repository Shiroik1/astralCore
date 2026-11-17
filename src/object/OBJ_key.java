package object;

import entity.Entity;

import main.Gamepanel;

public class OBJ_key extends Entity {

    /** Creates a OBJ_key instance and initializes the state required by its collaborators. */
    public OBJ_key(Gamepanel gp) {
        super(gp);
        name = "Key";

        down1 = setup("/object/key", gp.tileSize, gp.tileSize);

        description = "(" + name + ")\nUse it to unlock\ndoors";
    }
}
