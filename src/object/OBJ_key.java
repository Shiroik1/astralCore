package object;

import entity.Entity;
import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_key extends Entity {

    public OBJ_key(Gamepanel gp){
        super(gp);
        name = "Key";

        down1 = setup("/object/key", gp.tileSize, gp.tileSize);
    }
}
