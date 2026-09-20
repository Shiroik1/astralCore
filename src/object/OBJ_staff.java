package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_staff extends Entity {
    public OBJ_staff(Gamepanel gp) {
        super(gp);
        name = "Apprentice Staff";
        type = type_staff;
        down1 = setup("/object/axe", gp.tileSize, gp.tileSize);
        attackValue = 1;
        attackArea.width = 40;
        attackArea.height = 40;
        description = "(" + name + ")\nA simple wooden staff\nchanneling arcane energy.";
    }
}