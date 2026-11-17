package object;

import entity.Entity;

import main.Gamepanel;

public class OBJ_bronzecoin extends Entity {
    /**
     * Creates a OBJ_bronzecoin instance and initializes the state required by its collaborators.
     */
    public OBJ_bronzecoin(Gamepanel gp) {
        super(gp);
        name = "Bronze Coin";
        value = 1;
        type = type_pickuponly;
        down1 = setup("/object/coin_bronze", gp.tileSize, gp.tileSize);
    }

    /** Performs the use operation for OBJ_bronzecoin. */
    public void use(Entity entity) {
        gp.playSE(1);
        entity.coin += value;
        if (entity == gp.localPlayer()) {
            gp.ui.addMessage("+" + value + " coin!");
        }
    }
}
