package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_shield_wood extends Entity {
    public OBJ_shield_wood(Gamepanel gp) {
        super(gp);
        name = "Wood Shield";
        down1 = setup("/object/shield_wood", gp.tileSize, gp.tileSize);
        defenseValue = 1;
        description = "(" + name + ")\nA wooden shield";
    }
}
