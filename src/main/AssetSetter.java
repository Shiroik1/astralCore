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
        gp.npc[i].worldX = 22 * gp.tileSize;
        gp.npc[i].worldY = 14 * gp.tileSize;
        i++;
    }

    public void setMonster(){
        int i = 0;

        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 16;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;
        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 20;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;
        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 24;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;
        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 28;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;
        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 32;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;

    }

    public void setInteractiveTile(){
        int i = 0;

//        gp.interactable[i] = new IT_drytree(gp, 6, 47);
//        i++;

    }
}
