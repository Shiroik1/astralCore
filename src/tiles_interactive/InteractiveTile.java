package tiles_interactive;

import entity.Entity;
import main.Gamepanel;

import java.awt.*;
import java.awt.image.BufferedImage;

public class InteractiveTile extends Entity {

    public boolean destructible = false;

    public InteractiveTile(Gamepanel gp, int col, int row) {
        super(gp);
    }

    public boolean isCorrectItem(Entity entity){
        boolean isCorrectItem = false;
        return isCorrectItem;
    }

    public InteractiveTile getDestroyedForm(){
        InteractiveTile tile = null;
        return tile;
    }

    public void playSE(){

    }

    public void update(){
        if(invincible){
            invincibleCounter++;
            if(invincibleCounter > 20){
                invincible = false;
                invincibleCounter = 0;
            }
        }
    }

    public void draw(Graphics2D g2){
        int screenX = worldX - gp.localPlayer().worldX + gp.localPlayer().screenX;
        int screenY = worldY - gp.localPlayer().worldY + gp.localPlayer().screenY;

        if(worldX + gp.tileSize > gp.localPlayer().worldX - gp.localPlayer().screenX &&
                worldX - gp.tileSize < gp.localPlayer().worldX + gp.localPlayer().screenX &&
                worldY + gp.tileSize > gp.localPlayer().worldY - gp.localPlayer().screenY &&
                worldY - gp.tileSize < gp.localPlayer().worldY + gp.localPlayer().screenY){

            g2.drawImage(down1, screenX, screenY, null);
        }

        if(gp.keyH.showDebug){
            drawHitbox(g2);
        }
    }
}
