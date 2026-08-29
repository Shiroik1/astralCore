package main;

import entity.NPC_oldman;
import monster.MON_GreenSlime;
import object.*;

public class AssetSetter {
    Gamepanel gp;

    public AssetSetter(Gamepanel gp){
        this.gp = gp;
    }

    public void setObject(){
        int i = 0;

        gp.obj[i] = new OBJ_potion_red(gp);
        gp.obj[i].worldX = 60 * gp.tileSize;
        gp.obj[i].worldY = 80 * gp.tileSize;
        i++;

        gp.obj[i] = new OBJ_bronzecoin(gp);
        gp.obj[i].worldX = 62 * gp.tileSize;
        gp.obj[i].worldY = 80 * gp.tileSize;
        i++;

        gp.obj[i] = new OBJ_axe(gp);
        gp.obj[i].worldX = 64 * gp.tileSize;
        gp.obj[i].worldY = 80 * gp.tileSize;
        i++;

        gp.obj[i] = new OBJ_heart(gp);
        gp.obj[i].worldX = 66 * gp.tileSize;
        gp.obj[i].worldY = 80 * gp.tileSize;
        i++;

        gp.obj[i] = new OBJ_crystal(gp);
        gp.obj[i].worldX = 68 * gp.tileSize;
        gp.obj[i].worldY = 80 * gp.tileSize;
        i++;
    }

    public void setNPC(){
        int i = 0;

        gp.npc[i] = new NPC_oldman(gp);
        gp.npc[i].worldX = 71 * gp.tileSize;
        gp.npc[i].worldY = 66 * gp.tileSize;
        i++;
    }

    public void setMonster(){
        int i = 0;

        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 10;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;

        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 12;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;

        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 14;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;

        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 16;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;

        gp.monster[i] = new MON_GreenSlime(gp);
        gp.monster[i].worldX = gp.tileSize * 18;
        gp.monster[i].worldY = gp.tileSize * 36;
        i++;
    }
}
