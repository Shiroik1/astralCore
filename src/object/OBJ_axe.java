package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_axe extends Entity {
    public OBJ_axe(Gamepanel gp) {
        super(gp);
        name = "Hunter's Axe";
        type = type_axe;
        down1 = setup("/object/axe", gp.tileSize, gp.tileSize);
        attackValue = 2;
        attackArea.width = 30;
        attackArea.height = 30;
        description = "(" + name + ")\nA woodcutter axe";
    }
}
