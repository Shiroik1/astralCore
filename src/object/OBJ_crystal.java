package object;

import entity.Entity;

import main.Gamepanel;

public class OBJ_crystal extends Entity {
    /** Creates a OBJ_crystal instance and initializes the state required by its collaborators. */
    public OBJ_crystal(Gamepanel gp) {
        super(gp);
        name = "Crystal";
        type = type_pickuponly;
        value = 1;

        down1 = setup("/object/manacrystal_full", gp.tileSize, gp.tileSize);
        image = setup("/object/manacrystal_full", gp.tileSize, gp.tileSize);
        image2 = setup("/object/manacrystal_blank", gp.tileSize, gp.tileSize);
    }

    /** Performs the use operation for OBJ_crystal. */
    public void use(Entity entity) {
        gp.playSE(2);
        entity.MP += value;
    }
}
