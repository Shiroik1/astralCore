package object;

import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_door extends SuperObject{
    Gamepanel gp;

    public OBJ_door(Gamepanel gp){
        this.gp = gp;
        name = "Door";
        try{
            image = ImageIO.read(getClass().getResourceAsStream("/object/door.png"));
            utilityTool.scaleImage(image, gp.tileSize, gp.tileSize);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        collision = true;
    }
}
