package object;

import entity.Entity;

import main.Gamepanel;

public class OBJ_shield_wood extends Entity {
    /**
     * Creates a OBJ_shield_wood instance and initializes the state required by its collaborators.
     */
    public OBJ_shield_wood(Gamepanel gp) {
        super(gp);
        name = "Wood Shield";
        type = type_shield;
        down1 = setup("/object/shield_wood", gp.tileSize, gp.tileSize);
        defenseValue = 1;
        description = "(" + name + ")\nA wooden shield";
    }
}
