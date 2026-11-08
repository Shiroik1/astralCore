package monster;

import entity.Monster;
import entity.PlaceholderSprites;
import main.Gamepanel;
import object.OBJ_bronzecoin;

import java.awt.Color;
import java.util.Random;

public class MON_Imp extends Monster {

    public MON_Imp(Gamepanel gp){
        super(gp);
        name = "Imp";
        speed = 2;
        maxHP = 8;
        HP = maxHP;
        attack = 3;
        defense = 0;
        exp = 2;
        aggroRange = 6;

        // the placeholder attack animation has 6 frames; the hit lands on frames 3-4
        monsterAttackFrameDelay = 6;
        attackDamageStartFrame = 3;
        attackDamageEndFrame = 4;

        int size = gp.tileSize * 2;
        PlaceholderSprites.fill(sprites, size, new Color(200, 70, 70), "IMP");
        down1 = sprites.get("idle_down").frames[0];

        solidArea.x = 8;
        solidArea.y = 12;
        solidArea.width = 32;
        solidArea.height = 30;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;
    }

    public void setAction(){
        if(isPlayerInAggroRange()){
            speed=2;chasePlayer();
            actionLockCounter = 0;
        } else {
            actionLockCounter++;
            if(actionLockCounter == 90){
                String[] dirs = {"up", "down", "left", "right"};
                direction = dirs[new Random().nextInt(4)];
                actionLockCounter = 0;
            }
        }
    }

    public void damageReaction(){
        actionLockCounter = 0;
    }

    public void checkDrop(){
        dropItem(new OBJ_bronzecoin(gp));
    }
}