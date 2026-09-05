package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_sword_normal extends Entity {
    public OBJ_sword_normal(Gamepanel gp) {
        super(gp);
        name = "Normal Sword";
        type = type_sword;
        down1 = setup("/player/weapon/sword", gp.tileSize, gp.tileSize);
        attackValue = 1;
        attackArea.width = 36;
        attackArea.height = 36;
        description = "(" + name + ")\nAn old sword";
    }
}
