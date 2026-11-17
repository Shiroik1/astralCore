package object;

import entity.Entity;

import main.Gamepanel;

public class OBJ_sword_normal extends Entity {
    /**
     * Creates a OBJ_sword_normal instance and initializes the state required by its collaborators.
     */
    public OBJ_sword_normal(Gamepanel gp) {
        super(gp);
        name = "Normal Sword";
        type = type_sword;
        down1 = setup("/player/weapon/sword", gp.tileSize, gp.tileSize);
        attackValue = 1;
        attackArea.width = 60;
        attackArea.height = 58;
        description = "(" + name + ")\nAn old sword";
    }
}
