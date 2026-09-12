package monster;

import entity.Entity;
import main.Gamepanel;
import object.OBJ_bronzecoin;
import object.OBJ_crystal;
import object.OBJ_heart;
import object.OBJ_rock;

import java.util.Random;

public class MON_GreenSlime extends Entity {

    public MON_GreenSlime(Gamepanel gp) {
        super(gp);

        type = type_monster;
        name = "Green Slime";
        speed = 2;
        maxHP = 5;
        HP = maxHP;
        attack = 5;
        defense = 0;
        exp = 3;
        projectile = new OBJ_rock(gp);
        aggroRange = 5; // tiles — tunable

        solidArea.x = 3;
        solidArea.y = 18;
        solidArea.width = 42;
        solidArea.height = 30;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        getImage();
    }

    public void getImage(){
        int size = gp.tileSize;
        up1 = setup("/monster/greenslime_down_1", size, size);
        up2 = setup("/monster/greenslime_down_2", size, size);
        down1 = setup("/monster/greenslime_down_1", size, size);
        down2 = setup("/monster/greenslime_down_2", size, size);
        left1 = setup("/monster/greenslime_down_1", size, size);
        left2 = setup("/monster/greenslime_down_2", size, size);
        right1 = setup("/monster/greenslime_down_1", size, size);
        right2 = setup("/monster/greenslime_down_2", size, size);
    }

    public void setAction(){

        if(isPlayerInAggroRange()){
            chasePlayer();
            actionLockCounter = 0; // so it doesn't immediately resume wandering the instant it loses aggro
        }
        else{
            actionLockCounter++;
            if(actionLockCounter == 120){
                Random random = new Random();
                int i = random.nextInt(100) + 1;

                if(i <= 25){
                    direction = "up";
                }
                if(i > 25 && i <= 50){
                    direction = "down";
                }
                if(i > 50 && i <= 75){
                    direction = "left";
                }
                if(i > 75 && i <= 100){
                    direction = "right";
                }

                actionLockCounter = 0;
            }
        }

//        int i = new Random().nextInt(100)+1;
//
//        if(i > 99 && !projectile.alive && shotAvailableCounter == 30){
//            projectile.set(worldX, worldY, direction, true, this);
//            gp.projectileList.add(projectile);
//            shotAvailableCounter = 0;
//        }
    }

    public void damageReaction(){
        actionLockCounter = 0;
        direction = gp.player.direction;
    }

    public void checkDrop(){
        int i = new Random().nextInt(100)+1;

        if(i < 50){
            dropItem(new OBJ_bronzecoin(gp));
        }
        if(i >= 50 && i < 75){
            dropItem(new OBJ_heart(gp));
        }
        if(i >= 75 && i < 100){
            dropItem(new OBJ_crystal(gp));
        }
    }
}
