package object;

import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_key extends SuperObject{
    Gamepanel gp;

    public OBJ_key(Gamepanel gp){
        this.gp = gp;
        name = "Key";
        try{
            image = ImageIO.read(getClass().getResourceAsStream("/object/key.png"));
            utilityTool.scaleImage(image, gp.tileSize, gp.tileSize);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
