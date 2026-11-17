package main;

import entity.NPC_oldman;

import object.*;

public class AssetSetter {
    Gamepanel gp;

    /** Creates a AssetSetter instance and initializes the state required by its collaborators. */
    public AssetSetter(Gamepanel gp) {
        this.gp = gp;
    }

    /** Updates the object for AssetSetter. */
    public void setObject() {
        int i = 0;

        //        gp.obj[i] = new OBJ_potion_red(gp);
        //        gp.obj[i].worldX = 60 * gp.tileSize;
        //        gp.obj[i].worldY = 80 * gp.tileSize;
        //        i++;
    }

    /** Updates the npc for AssetSetter. */
    public void setNPC() {
        if (gp.worldConfig.procedural()) {
            java.util.Arrays.fill(gp.npc, null);
            return;
        }
        int i = 0;

        gp.npc[i] = new NPC_oldman(gp);
        gp.npc[i].worldX = 27 * gp.tileSize;
        gp.npc[i].worldY = 23 * gp.tileSize;
        i++;
        gp.npc[i] = new entity.NPC_merchant(gp);
        gp.npc[i].worldX = 29 * gp.tileSize;
        gp.npc[i].worldY = 23 * gp.tileSize;
    }

    /** Updates the monster for AssetSetter. */
    public void setMonster() {
        // The authored town stays peaceful. Procedural overworlds get the bounded slime population.
        if (gp.quests.inDungeon()) return;
        java.util.Arrays.fill(gp.monster, null);
        gp.slimeSpawner.resetPopulation();
    }

    /** Updates the interactive tile for AssetSetter. */
    public void setInteractiveTile() {
        int i = 0;

        //        gp.interactable[i] = new IT_drytree(gp, 6, 47);
        //        i++;

    }
}
