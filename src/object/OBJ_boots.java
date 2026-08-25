package object;

import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_boots extends SuperObject{
    Gamepanel gp;
    public OBJ_boots(Gamepanel gp){
        this.gp = gp;
        name = "Boots";
        try{
            image = ImageIO.read(getClass().getResourceAsStream("/object/boots.png"));
            utilityTool.scaleImage(image, gp.tileSize, gp.tileSize);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
