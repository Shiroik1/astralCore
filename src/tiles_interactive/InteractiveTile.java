package tiles_interactive;

import entity.Entity;
import main.Gamepanel;

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
}
