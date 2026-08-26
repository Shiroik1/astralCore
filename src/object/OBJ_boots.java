package object;

import entity.Entity;
import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_boots extends Entity {

    public OBJ_boots(Gamepanel gp){
        super(gp);
        name = "Boots";

        down1 = setup("/object/boots");
    }
}
