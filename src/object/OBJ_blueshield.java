package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_blueshield extends Entity {
    public OBJ_blueshield(Gamepanel gp) {
        super(gp);
        name = "Blue Shield";
        type = type_shield;
        down1 = setup("/object/shield_blue", gp.tileSize, gp.tileSize);
        defenseValue = 2;
        description = "(" + name + ")\nA shield that is\nBLUE";
    }
}
