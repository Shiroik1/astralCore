package monster;

import entity.Entity;
import entity.Player;
import entity.SpriteAnimation;
import main.Gamepanel;
import object.OBJ_bronzecoin;
import object.OBJ_crystal;
import object.OBJ_heart;
import object.OBJ_rock;

import java.awt.image.BufferedImage;
import java.util.Random;

public class MON_GreenSlime extends Entity {

    public MON_GreenSlime(Gamepanel gp) {
        super(gp);

        monsterAttackFrameDelay = 10;
        attackDamageStartFrame = 6;   // frame the hit window opens (out of 10 total frames)
        attackDamageEndFrame = 8;     // frame the hit window closes
        monsterAttackCooldown = 40;
        type = type_monster;
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

    public void getImage(){
        int size = gp.tileSize * 3;

        BufferedImage[] idleDown = {
                setup("/monster/green_slime/00_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/01_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/02_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/03_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/04_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/05_Slime1_Idle_without_shadow", size, size)};
        BufferedImage[] idleUp = {
                setup("/monster/green_slime/06_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/07_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/08_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/09_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/10_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/11_Slime1_Idle_without_shadow", size, size)};
        BufferedImage[] idleLeft = {
                setup("/monster/green_slime/12_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/13_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/14_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/15_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/16_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/17_Slime1_Idle_without_shadow", size, size)};
        BufferedImage[] idleRight = {
                setup("/monster/green_slime/18_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/19_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/20_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/21_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/22_Slime1_Idle_without_shadow", size, size),
                setup("/monster/green_slime/23_Slime1_Idle_without_shadow", size, size)};

        sprites.put("idle_down", new SpriteAnimation(idleDown, size, size));
        sprites.put("idle_up", new SpriteAnimation(idleUp, size, size));
        sprites.put("idle_left", new SpriteAnimation(idleLeft, size, size));
        sprites.put("idle_right", new SpriteAnimation(idleRight, size, size));

        BufferedImage[] walkDown = new BufferedImage[8];
        BufferedImage[] walkUp = new BufferedImage[8];
        BufferedImage[] walkLeft = new BufferedImage[8];
        BufferedImage[] walkRight = new BufferedImage[8];
        for(int i = 0; i < 8; i++){
            walkDown[i]  = setup(String.format("/monster/green_slime/%02d_Slime1_Walk_without_shadow", i), size, size);
            walkUp[i]    = setup(String.format("/monster/green_slime/%02d_Slime1_Walk_without_shadow", i + 8), size, size);
            walkLeft[i]  = setup(String.format("/monster/green_slime/%02d_Slime1_Walk_without_shadow", i + 16), size, size);
            walkRight[i] = setup(String.format("/monster/green_slime/%02d_Slime1_Walk_without_shadow", i + 24), size, size);
        }

        SpriteAnimation walkDownAnim = new SpriteAnimation(walkDown, size, size);
        walkDownAnim.frameDelay = 5; // lower than the default 12 = faster cycling

        SpriteAnimation walkUpAnim = new SpriteAnimation(walkUp, size, size);
        walkUpAnim.frameDelay = 5;

        SpriteAnimation walkLeftAnim = new SpriteAnimation(walkLeft, size, size);
        walkLeftAnim.frameDelay = 5;

        SpriteAnimation walkRightAnim = new SpriteAnimation(walkRight, size, size);
        walkRightAnim.frameDelay = 5;

        sprites.put("walk_down", walkDownAnim);
        sprites.put("walk_up", walkUpAnim);
        sprites.put("walk_left", walkLeftAnim);
        sprites.put("walk_right", walkRightAnim);

        BufferedImage[] attackDown = new BufferedImage[10];
        BufferedImage[] attackUp = new BufferedImage[10];
        BufferedImage[] attackLeft = new BufferedImage[10];
        BufferedImage[] attackRight = new BufferedImage[10];
        for(int i = 0; i < 10; i++){
            attackDown[i]  = setup(String.format("/monster/green_slime/%02d_Slime1_Attack_without_shadow", i), size, size);
            attackUp[i]    = setup(String.format("/monster/green_slime/%02d_Slime1_Attack_without_shadow", i + 10), size, size);
            attackLeft[i]  = setup(String.format("/monster/green_slime/%02d_Slime1_Attack_without_shadow", i + 20), size, size);
            attackRight[i] = setup(String.format("/monster/green_slime/%02d_Slime1_Attack_without_shadow", i + 30), size, size);
        }
        sprites.put("attack_down", new SpriteAnimation(attackDown, size, size));
        sprites.put("attack_up", new SpriteAnimation(attackUp, size, size));
        sprites.put("attack_left", new SpriteAnimation(attackLeft, size, size));
        sprites.put("attack_right", new SpriteAnimation(attackRight, size, size));

        BufferedImage[] deathDown = new BufferedImage[10];
        BufferedImage[] deathUp = new BufferedImage[10];
        BufferedImage[] deathLeft = new BufferedImage[10];
        BufferedImage[] deathRight = new BufferedImage[10];
        for(int i = 0; i < 10; i++){
            deathDown[i]  = setup(String.format("/monster/green_slime/%02d_Slime1_Death_without_shadow", i), size, size);
            deathUp[i]    = setup(String.format("/monster/green_slime/%02d_Slime1_Death_without_shadow", i + 10), size, size);
            deathLeft[i]  = setup(String.format("/monster/green_slime/%02d_Slime1_Death_without_shadow", i + 20), size, size);
            deathRight[i] = setup(String.format("/monster/green_slime/%02d_Slime1_Death_without_shadow", i + 30), size, size);
        }
        sprites.put("death_down", new SpriteAnimation(deathDown, size, size));
        sprites.put("death_up", new SpriteAnimation(deathUp, size, size));
        sprites.put("death_left", new SpriteAnimation(deathLeft, size, size));
        sprites.put("death_right", new SpriteAnimation(deathRight, size, size));

        down1 = idleDown[0];
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
