package tiles_interactive;

import entity.Entity;
import main.Gamepanel;

public class IT_drytree extends InteractiveTile{

    public IT_drytree(Gamepanel gp, int col, int row) {
        super(gp, col, row);
        HP = 3;

        this.worldX = gp.tileSize * col;
        this.worldY = gp.tileSize * row;

        down1 = setup("/tiles_Interactive/drytree", gp.tileSize, gp.tileSize);
        destructible = true;
    }

    public boolean isCorrectItem(Entity entity){
        boolean isCorrectItem = false;
        if(entity.currentWeapon.type == type_axe){
            isCorrectItem = true;
        }
        return isCorrectItem;
    }

    public InteractiveTile getDestroyedForm(){
        InteractiveTile tile = new IT_trunk(gp, worldX/gp.tileSize, worldY/gp.tileSize);
        return tile;
    }

    public void playSE(){
        gp.playSE(10);
    }

}
