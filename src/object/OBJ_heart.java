package object;

import entity.Entity;

import main.Gamepanel;

public class OBJ_heart extends Entity {

    /** Creates a OBJ_heart instance and initializes the state required by its collaborators. */
    public OBJ_heart(Gamepanel gp) {
        super(gp);
        name = "Heart";
        type = type_pickuponly;
        value = 2;

        down1 = setup("/object/heart_full", gp.tileSize, gp.tileSize);
        image = setup("/object/heart_full", gp.tileSize, gp.tileSize);
        image1 = setup("/object/heart_half", gp.tileSize, gp.tileSize);
        image2 = setup("/object/heart_blank", gp.tileSize, gp.tileSize);
    }

    /** Performs the use operation for OBJ_heart. */
    public void use(Entity entity) {
        gp.playSE(2);
        entity.HP += value;
    }
}
