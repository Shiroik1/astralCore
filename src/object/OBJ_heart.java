package object;

import entity.Entity;
import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_heart extends Entity {

    public OBJ_heart(Gamepanel gp){
        super(gp);
        name = "Heart";
        type = type_pickuponly;
        value = 2;

        down1 = setup("/object/heart_full", gp.tileSize, gp.tileSize);
        image = setup("/object/heart_full", gp.tileSize, gp.tileSize);
        image1 = setup("/object/heart_half", gp.tileSize, gp.tileSize);
        image2 = setup("/object/heart_blank", gp.tileSize, gp.tileSize);
    }

    public void use(Entity entity){
        gp.playSE(2);
        entity.HP += value;
    }
}
