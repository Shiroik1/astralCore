package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_sword_normal extends Entity {
    public OBJ_sword_normal(Gamepanel gp) {
        super(gp);
        name = "Normal Sword";
        down1 = setup("/object/sword_normal", gp.tileSize, gp.tileSize);
        attackValue = 1;
        description = "(" + name + ")\nAn old sword";
    }
}
