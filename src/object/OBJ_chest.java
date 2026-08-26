package object;

import entity.Entity;
import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_chest extends Entity {

    public OBJ_chest(Gamepanel gp){
        super(gp);
        name = "Chest";

        down1 = setup("/object/chest");
    }
}
