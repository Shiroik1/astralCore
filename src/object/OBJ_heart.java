package object;

import entity.Entity;
import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_heart extends Entity {

    public OBJ_heart(Gamepanel gp){
        super(gp);
        name = "Boots";

        image = setup("/object/heart_full", gp.tileSize, gp.tileSize);
        image1 = setup("/object/heart_half", gp.tileSize, gp.tileSize);
        image2 = setup("/object/heart_blank", gp.tileSize, gp.tileSize);
    }
}
