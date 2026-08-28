package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_crystal extends Entity {
    public OBJ_crystal(Gamepanel gp) {
        super(gp);
        name = "MP";
        image = setup("/object/manacrystal_full", gp.tileSize, gp.tileSize);
        image2 = setup("/object/manacrystal_blank", gp.tileSize, gp.tileSize);
    }
}
