package main;

import entity.NPC_oldman;
import object.OBJ_boots;
import object.OBJ_chest;
import object.OBJ_door;
import object.OBJ_key;

public class AssetSetter {
    Gamepanel gp;

    public AssetSetter(Gamepanel gp){
        this.gp = gp;
    }

    public void setObject(){
        gp.obj[0] = new OBJ_door(gp);
        gp.obj[0].worldX = gp.tileSize * 61;
        gp.obj[0].worldY = gp.tileSize * 79;
    }

    public void setNPC(){
        gp.npc[0] = new NPC_oldman(gp);
        gp.npc[0].worldX = 71 * gp.tileSize;
        gp.npc[0].worldY = 66 * gp.tileSize;
    }
}
