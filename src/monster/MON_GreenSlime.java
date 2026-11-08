package monster;

import entity.Monster;
import entity.Player;
import entity.SpriteAnimation;
import main.Gamepanel;
import object.OBJ_bronzecoin;
import object.OBJ_crystal;
import object.OBJ_heart;
import object.OBJ_rock;
import art.SlimeArt;

import java.awt.image.BufferedImage;
import java.util.Random;

public class MON_GreenSlime extends Monster {

    public MON_GreenSlime(Gamepanel gp) {
        super(gp);

        monsterAttackFrameDelay = 6;
        attackDamageStartFrame = 6;   // frame the hit window opens (out of 10 total frames)
        attackDamageEndFrame = 8;     // frame the hit window closes
        monsterAttackCooldown = 40;
        name = "Green Slime";
        speed = 2;
        maxHP = 10;
        HP = maxHP;
        attack = 5;
        defense = 0;
        exp = 3;
        projectile = new OBJ_rock(gp);
        aggroRange = 5; // tiles — tunable

        solidArea.x = 3;
        solidArea.y = 18;
        solidArea.width = 48;
        solidArea.height = 30;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        getImage();
    }

    public void getImage() {
        int size = SlimeArt.CANVAS_SIZE;
        SlimeArt.buildAnimations(sprites, size, 1.10f);
        down1 = sprites.get("idle_down").frames[0];
    }

    @Override
    protected SpriteAnimation getCurrentAnimation(){
        if(flashing && !dying && !attackState)return sprites.get("hurt_"+direction);
        return super.getCurrentAnimation();
    }

    @Override
    protected BufferedImage getCurrentFrame(){
        if(flashing && !dying && !attackState){
            SpriteAnimation hurt=getCurrentAnimation();
            BufferedImage frame=hurt.frames[Math.min(hurt.frames.length-1,flashCounter*hurt.frames.length/Math.max(1,flashDuration))];
            if(flashCounter<2)return getFlashVersion(frame);
            java.awt.Color tint=getActiveTintColor();
            return tint!=null?getTintVersion(frame,tint):frame;
        }
        return super.getCurrentFrame();
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
        Player nearest = findNearestPlayer();
        if(nearest != null){
            direction = nearest.direction;
        }
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
