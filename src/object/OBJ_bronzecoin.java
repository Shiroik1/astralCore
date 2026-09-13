package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_bronzecoin extends Entity {
    public OBJ_bronzecoin(Gamepanel gp) {
        super(gp);
        name = "Bronze Coin";
        value = 1;
        type = type_pickuponly;
        down1 = setup("/object/coin_bronze", gp.tileSize, gp.tileSize);
    }

    public void use(Entity entity){
        gp.playSE(1);
        gp.ui.addMessage("+" + value + " coin!");
        gp.localPlayer().coin += value;
    }
}
