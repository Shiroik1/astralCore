package main;

import entity.NPC_oldman;
import monster.MON_GreenSlime;
import object.*;
import tiles_interactive.IT_drytree;

public class AssetSetter {
    Gamepanel gp;

    public AssetSetter(Gamepanel gp){
        this.gp = gp;
    }

    public void setObject(){
        int i = 0;

//        gp.obj[i] = new OBJ_potion_red(gp);
//        gp.obj[i].worldX = 60 * gp.tileSize;
//        gp.obj[i].worldY = 80 * gp.tileSize;
//        i++;
    }

    public void setNPC(){
        int i = 0;

        gp.npc[i] = new NPC_oldman(gp);
        gp.npc[i].worldX = 27 * gp.tileSize;
        gp.npc[i].worldY = 23 * gp.tileSize;
        i++;
        gp.npc[i]=new entity.NPC_merchant(gp);
        gp.npc[i].worldX=29*gp.tileSize;gp.npc[i].worldY=23*gp.tileSize;
    }

    public void setMonster(){
        // Combat encounters live in the well. Refreshing the town must not create monsters.
        if(!gp.quests.inDungeon())java.util.Arrays.fill(gp.monster,null);
    }

    public void setInteractiveTile(){
        int i = 0;

//        gp.interactable[i] = new IT_drytree(gp, 6, 47);
//        i++;

    }
}
