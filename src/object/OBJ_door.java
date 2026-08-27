package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_door extends Entity {


    public OBJ_door(Gamepanel gp){
        super(gp);
        name = "Door";

        down1 = setup("/object/door", gp.tileSize, gp.tileSize);

        collision = true;

        solidArea.x = 0;
        solidArea.y = 16;
        solidArea.width = 48;
        solidArea.height = 32;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;
    }
}
