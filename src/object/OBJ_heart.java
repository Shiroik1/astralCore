package object;

import main.Gamepanel;

import javax.imageio.ImageIO;

public class OBJ_heart extends SuperObject{
    Gamepanel gp;
    public OBJ_heart(Gamepanel gp){
        this.gp = gp;
        name = "Boots";
        try{
            image = ImageIO.read(getClass().getResourceAsStream("/object/heart_full.png"));
            image1 = ImageIO.read(getClass().getResourceAsStream("/object/heart_half.png"));
            image2 = ImageIO.read(getClass().getResourceAsStream("/object/heart_blank.png"));
            image = utilityTool.scaleImage(image, gp.tileSize, gp.tileSize);
            image1 = utilityTool.scaleImage(image1, gp.tileSize, gp.tileSize);
            image2 = utilityTool.scaleImage(image2, gp.tileSize, gp.tileSize);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
