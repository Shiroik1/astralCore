package object;

import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_chest extends SuperObject{
    Gamepanel gp;
    public OBJ_chest(Gamepanel gp){
        this.gp = gp;
        name = "Chest";
        try{
            image = ImageIO.read(getClass().getResourceAsStream("/object/chest.png"));
            utilityTool.scaleImage(image, gp.tileSize, gp.tileSize);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
