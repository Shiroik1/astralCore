package entity;

import main.Gamepanel;

public class NPC_oldman extends Entity{

    public NPC_oldman(Gamepanel gp){
        super(gp);
        direction = "down";
        speed = 0;
        npcId = "oldman";
        type = type_npc;

        getImage();
        loadDialogue("/dialogue/oldman.json");
    }

    public void getImage(){

        up1 = setup("/npc/oldman_up_1", gp.tileSize, gp.tileSize);
        up2 = setup("/npc/oldman_up_2", gp.tileSize, gp.tileSize);
        down1 = setup("/npc/oldman_down_1", gp.tileSize, gp.tileSize);
        down2 = setup("/npc/oldman_down_2", gp.tileSize, gp.tileSize);
        left1 = setup("/npc/oldman_left_1", gp.tileSize, gp.tileSize);
        left2 = setup("/npc/oldman_left_2", gp.tileSize, gp.tileSize);
        right1 = setup("/npc/oldman_right_1", gp.tileSize, gp.tileSize);
        right2 = setup("/npc/oldman_right_2", gp.tileSize, gp.tileSize);

    }

    public void setAction(){
        direction = "down";
    }

}
