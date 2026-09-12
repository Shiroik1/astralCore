package entity;

import main.Gamepanel;
import main.KeyHandler;
import main.UtilityTool;
import object.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Map;

public class Player extends Entity{
    KeyHandler keyH;

    public final int screenX;
    public final int screenY;

    public boolean attackCanceled = false;
    private final int attackWindupFrameDelay = 2;   // slower hold on anticipation frames — gives the hit time to be read/reacted to
    private final int attackActiveFrameDelay = 2;   // fast during the actual hit window — makes the strike itself feel sharp
    private final int attackRecoveryFrameDelay = 4; // frames after the hit window, before returning to idle/run
    private final int attackHitStartFrame = 4;
    private final int attackHitEndFrame = 6;

    private int attackFrameHoldCounter = 0;
    private boolean attackBuffered = false;

    public Entity leftHandItem;
    public Entity rightHandItem;

    private int bounceCounter = 0;
    private int trailCounter = 0;
    private final int hitStopOnHitMonster = 6; // ticks frozen when the player lands a hit — tune to taste
    private final int hitStopOnTakingDamage = 8; // slightly longer than dealing a hit — getting hit should read heavier
    private final int trailInterval = 8; // ticks between trail particles — lower = denser trail
    private int bodySize; // canvas size shared by every player body animation (idle/run/attack)

    public ArrayList<Entity> inventory = new ArrayList<>();
    public final int inventorySize = 20;

    //SKILL PARAMETERS
    public Skill[] skills = new Skill[5];
    private boolean powerStrikePending = false;
    private int powerStrikeBonus = 0;
    private boolean rendPending = false;

    public Player(Gamepanel gp, KeyHandler keyH){
        super(gp);
        this.keyH = keyH;

        screenX = gp.screenWidth/2 - (gp.tileSize/2);
        screenY = gp.screenHeight/2 - (gp.tileSize/2);

        solidArea = new Rectangle();
        solidArea.x = 8;
        solidArea.y = 20;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;
        solidArea.width = 32;
        solidArea.height = 32;

        setDefaultValues();
        getPlayerImage();
        getPlayerAttackImage();
        setItems();
    }

    public void setDefaultValues(){
        worldX = gp.tileSize * 12;
        worldY = gp.tileSize * 20;
        speed = 4;
        direction = "down";

        //PLAYER STATS
        maxHP = 10;
        HP = maxHP;
        maxMP = 5;
        MP = maxMP;
        level = 1;
        strength = 1;
        dexterity = 1;
        exp = 0;
        nextLevelExp = 5;
        coin = 0;
        currentWeapon = new OBJ_sword_normal(gp);
        rightHandItem = currentWeapon;
        currentShield = new OBJ_shield_wood(gp);
        projectile = new OBJ_fireball(gp);
        attack = getAttack();
        defense = getDefense();
    }

    public void setDefaultPosition(){
        worldX = gp.tileSize * 12;
        worldY = gp.tileSize * 20;
        direction = "down";
    }

    public void resetHPandMP(){
        HP = maxHP;
        MP = maxMP;
        invincible = false;
    }

    public void setItems(){
        inventory.clear();
        inventory.add(currentWeapon);
        inventory.add(currentShield);
        inventory.add(new OBJ_potion_red(gp));
        setupSkills();
    }

    private int getDefense() {
        return defense = dexterity * currentShield.defenseValue;
    }

    private int getAttack() {
        attackArea = currentWeapon.attackArea;
        return attack = strength * currentWeapon.attackValue;
    }

    public void getPlayerImage(){
        bodySize = (int)(gp.tileSize * 3); // tunable — re-check against your art
        int size = bodySize;

        BufferedImage[] idleDown = { setup("/player/melee_idle/00_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/01_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/02_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/03_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/04_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/05_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/06_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/07_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/08_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/09_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/10_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/11_Swordsman_lvl2_Idle_without_shadow", size, size)};
        BufferedImage[] idleUp = { setup("/player/melee_idle/36_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/37_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/38_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/39_Swordsman_lvl2_Idle_without_shadow", size, size),};
        BufferedImage[] idleLeft = { setup("/player/melee_idle/12_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/13_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/14_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/15_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/16_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/17_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/18_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/19_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/20_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/21_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/22_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/23_Swordsman_lvl2_Idle_without_shadow", size, size)};
        BufferedImage[] idleRight = { setup("/player/melee_idle/24_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/25_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/26_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/27_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/28_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/29_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/30_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/31_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/32_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/33_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/34_Swordsman_lvl2_Idle_without_shadow", size, size),
                setup("/player/melee_idle/35_Swordsman_lvl2_Idle_without_shadow", size, size) };

        sprites.put("idle_down", new SpriteAnimation(idleDown, size, size));
        sprites.put("idle_up", new SpriteAnimation(idleUp, size, size));
        sprites.put("idle_left", new SpriteAnimation(idleLeft, size, size));
        sprites.put("idle_right", new SpriteAnimation(idleRight, size, size));

        BufferedImage[] runDown = { setup("/player/melee_run/00_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/01_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/02_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/03_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/04_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/05_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/06_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/07_Swordsman_lvl2_Run_without_shadow", size, size)};
        BufferedImage[] runUp = { setup("/player/melee_run/24_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/25_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/26_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/27_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/28_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/29_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/30_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/31_Swordsman_lvl2_Run_without_shadow", size, size) };
        BufferedImage[] runLeft = { setup("/player/melee_run/08_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/09_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/10_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/11_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/12_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/13_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/14_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/15_Swordsman_lvl2_Run_without_shadow", size, size) };
        BufferedImage[] runRight = { setup("/player/melee_run/16_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/17_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/18_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/19_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/20_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/21_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/22_Swordsman_lvl2_Run_without_shadow", size, size),
                setup("/player/melee_run/23_Swordsman_lvl2_Run_without_shadow", size, size) };

        SpriteAnimation runDownAnim = new SpriteAnimation(runDown, size, size);
        runDownAnim.frameDelay = 5; // lower than the default 12 = faster cycling

        SpriteAnimation runUpAnim = new SpriteAnimation(runUp, size, size);
        runUpAnim.frameDelay = 5;

        SpriteAnimation runLeftAnim = new SpriteAnimation(runLeft, size, size);
        runLeftAnim.frameDelay = 5;

        SpriteAnimation runRightAnim = new SpriteAnimation(runRight, size, size);
        runRightAnim.frameDelay = 5;

        sprites.put("run_down", runDownAnim);
        sprites.put("run_up", runUpAnim);
        sprites.put("run_left", runLeftAnim);
        sprites.put("run_right", runRightAnim);

        down1 = idleDown[0]; // still used by UI.drawTitleScreen() for the title portrait
    }

    public void getPlayerAttackImage(){
        String weaponFolder = getWeaponFolder();

        BufferedImage[] attackDown = {
                setup("/player/melee_" + weaponFolder + "_attack/00_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/01_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/02_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/03_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/04_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/05_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/06_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/07_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize)
        };
        BufferedImage[] attackUp = {
                setup("/player/melee_" + weaponFolder + "_attack/24_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/25_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/26_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/27_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/28_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/29_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/30_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/31_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize)
        };
        BufferedImage[] attackLeft = {
                setup("/player/melee_" + weaponFolder + "_attack/08_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/09_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/10_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/11_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/12_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/13_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/14_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/15_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize)
        };
        BufferedImage[] attackRight = {
                setup("/player/melee_" + weaponFolder + "_attack/16_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/17_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/18_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/19_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/20_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/21_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/22_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize),
                setup("/player/melee_" + weaponFolder + "_attack/23_Swordsman_lvl2_Run_Attack_without_shadow", bodySize, bodySize)
        };

        sprites.put("attack_down", new SpriteAnimation(attackDown, bodySize, bodySize));
        sprites.put("attack_up", new SpriteAnimation(attackUp, bodySize, bodySize));
        sprites.put("attack_left", new SpriteAnimation(attackLeft, bodySize, bodySize));
        sprites.put("attack_right", new SpriteAnimation(attackRight, bodySize, bodySize));
    }

    public void update(){

        boolean movingHoriz = keyH.leftPressed || keyH.rightPressed;
        boolean movingVert = keyH.upPressed || keyH.downPressed;
        moving = movingHoriz || movingVert;
        String horizDir = null;
        String vertDir = null;

        if(moving){
            bounceCounter++;
        }
        else{
            bounceCounter = 0;
        }

        if (keyH.leftPressed) {
            horizDir = "left";
        }
        else if(keyH.rightPressed){
            horizDir = "right";
        }

        if(keyH.upPressed){
            vertDir = "up";
        }
        else if(keyH.downPressed){
            vertDir = "down";
        }

        direction = (vertDir != null) ? vertDir : direction;
        direction = (horizDir != null) ? horizDir : direction;

        animState = attacking ? "attack" : (moving ? "run" : "idle");

        updateStatusEffects();

        for(int i = 0; i < skills.length; i++){
            if(skills[i] != null){
                skills[i].update();
                if(keyH.skillKeyPressed[i]){
                    if(skills[i].isReady() && MP >= skills[i].mpCost){
                        MP -= skills[i].mpCost;
                        skills[i].trigger();
                    }
                    keyH.skillKeyPressed[i] = false;
                }
            }
        }

        if(!attacking){
            spriteCounter++;
            SpriteAnimation anim = getCurrentAnimation();
            int frameDelay = (anim != null) ? anim.frameDelay : 12;
            if(spriteCounter > frameDelay){
                int frameCount = (anim != null) ? anim.frames.length : 1;
                spriteNum++;
                if(spriteNum > frameCount){
                    spriteNum = 1;
                }
                spriteCounter = 0;
            }
        }

        if(moving && !attacking){
            trailCounter++;
            if(trailCounter >= trailInterval){
                spawnTrailParticle();
                trailCounter = 0;
            }
        }

        if(knockbackActive){
            updateKnockback();
        }
        else if(attacking){
            if(gp.mouseH.leftClicked){
                attackBuffered = true;   // remember the click, don't let the flag wipe reach anywhere
                gp.mouseH.leftClicked = false;
            }
            attacking();
        }
        else if(moving || keyH.ePressed || gp.mouseH.leftClicked){

                //CHECK TILE COLLISION
                collisionOn = false;
                gp.collisionChecker.checkTile(this);

                //CHECK OBJECT COLLISION
                int objIndex = gp.collisionChecker.checkObject(this, true);
                pickUpObject(objIndex);

                //CHECK NPC COLLISION
                int npcIndex = gp.collisionChecker.checkEntity(this, gp.npc);
                interactNPC(npcIndex);

                //CHECK MONSTER COLLISION
                int monsterIndex = gp.collisionChecker.checkEntity(this, gp.monster);
                contactMonster(monsterIndex);

                //CHECK INTERACTABLES
                int interactableIndex = gp.collisionChecker.checkEntity(this, gp.interactable);

                //CHECK EVENT
                gp.eventHandler.checkEvent();

                //IF COLLISION IS FALSE, PLAYER CAN MOVE
                if(!keyH.ePressed){

                    boolean diagonal = (horizDir != null && vertDir != null);
                    int moveSpeed = diagonal ? (int)Math.round(speed * 0.7071) : speed;
                    if(diagonal && moveSpeed < 1) moveSpeed = 1;

                    if(horizDir != null){
                        direction = horizDir;
                        collisionOn = false;
                        gp.collisionChecker.checkTile(this);
                        if(!collisionOn){
                            worldX += horizDir.equals("left") ? -moveSpeed : moveSpeed;
                        }
                    }

                    if(vertDir != null){
                        direction = vertDir;
                        collisionOn = false;
                        gp.collisionChecker.checkTile(this);
                        if(!collisionOn){
                            worldY += vertDir.equals("up") ? -moveSpeed : moveSpeed;
                        }
                    }

                    direction = (vertDir != null) ? vertDir : direction;
                    direction = (horizDir != null) ? horizDir: direction;
                }


                if((keyH.ePressed || gp.mouseH.leftClicked) && !attackCanceled){
                    attacking = true;
                    spriteNum = 1;
                    attackFrameHoldCounter = 0;
                }

                attackCanceled = false;
                gp.keyH.ePressed = false;
                gp.mouseH.leftClicked = false;
                attackBuffered = false;
        }

        if(gp.keyH.shotKeyPressed && !projectile.alive && shotAvailableCounter == 30 && projectile.hasResource(this)){
            //SET DEFAULT POSITION, DIRECTION AND USER
            projectile.set(worldX, worldY, direction, true, this);

            projectile.subtractResource(this);
            gp.projectileList.add(projectile);
            gp.playSE(9);
            shotAvailableCounter = 0;
        }

        if(invincible){
            invincibleCounter++;
            if(invincibleCounter > 60){
                invincible = false;
                invincibleCounter = 0;
            }
        }

        if(flashing){
            flashCounter++;
            if(flashCounter > flashDuration){
                flashing = false;
                flashCounter = 0;
            }
        }

        if(shotAvailableCounter < 30){
            shotAvailableCounter++;
        }

        if(HP > maxHP){
            HP = maxHP;
        }
        if(MP > maxMP){
            MP = maxMP;
        }

        if(HP <= 0){
            gp.gameState = gp.gameOverState;
            gp.ui.commandNum = -1;
            gp.stopMusic();
            gp.playSE(11);
        }

    }

    public void attacking() {
        SpriteAnimation anim = getCurrentAnimation();
        int totalFrames = (anim != null) ? anim.frames.length : 1;

        int delayForCurrentFrame = getAttackFrameDelay(spriteNum);

        attackFrameHoldCounter++;
        if(attackFrameHoldCounter >= delayForCurrentFrame){
            attackFrameHoldCounter = 0;
            spriteNum++;

            if(spriteNum > totalFrames){
                spriteNum = 1;
                attacking = false;
                return;
            }
        }

        if(spriteNum >= attackHitStartFrame && spriteNum <= attackHitEndFrame){
            //Save current WorldX, WorldY, SolidArea
            int currentWorldX = worldX;
            int currentWorldY = worldY;
            int solidAreaWidth = solidArea.width;
            int solidAreaHeight = solidArea.height;

            switch (direction){
                case "up" -> worldY -= attackArea.height;
                case "down" -> worldY += attackArea.height;
                case "left" -> worldX -= attackArea.width;
                case "right" -> worldX += attackArea.width;
            }

            solidArea.width = attackArea.width;
            solidArea.height = attackArea.height;

            int monsterIndex = gp.collisionChecker.checkEntity(this, gp.monster);
            int bonusDamage = 0;
            if(powerStrikePending){
                bonusDamage = powerStrikeBonus;
                powerStrikePending = false;
            }
            damageMonster(monsterIndex, getEffectiveAttack() + bonusDamage);

            if(rendPending && monsterIndex != 999){
                gp.monster[monsterIndex].addStatusEffect(new StatusEffect("bleed", 180, 1, 30));
                rendPending = false;
            }

            int interactableIndex = gp.collisionChecker.checkEntity(this, gp.interactable);
            objectInteract(interactableIndex);

            worldX = currentWorldX;
            worldY = currentWorldY;
            solidArea.width = solidAreaWidth;
            solidArea.height = solidAreaHeight;
        }
    }


    public void damageMonster(int index, int attack) {
        if(index != 999){
            if(!gp.monster[index].invincible){
                gp.playSE(5);
                int damage = attack - gp.monster[index].defense;
                if(damage < 0){
                    damage = 0;
                }
                gp.monster[index].HP -= damage;
                gp.ui.addMessage(damage + " damage!");
                gp.monster[index].invincible = true;
                gp.monster[index].flashing = true;
                gp.monster[index].flashCounter = 0;
                gp.monster[index].startKnockback(direction, gp.monster[index].knockbackDistance);
                gp.startScreenShake(hitStopOnHitMonster, 4);
                gp.monster[index].spawnHitParticles();
                gp.monster[index].damageReaction();
                gp.startHitStop(hitStopOnHitMonster);

                if(gp.monster[index].HP <= 0){
                    gp.monster[index].dying = true;
                    exp += gp.monster[index].exp;
                    gp.ui.addMessage("Killed the " + gp.monster[index].name + "!");
                    gp.ui.addMessage("Gained " + gp.monster[index].exp + " EXP" );
                    checkLevelUp();
                }
            }
        }
    }

    private void checkLevelUp() {
        if(exp >= nextLevelExp){
            level++;
            nextLevelExp = nextLevelExp * 2;
            maxHP += 2;
            strength++;
            dexterity++;
            attack = getAttack();
            defense = getDefense();

            gp.playSE(7);
            gp.gameState = gp.dialogueState;
            gp.ui.currentDialogue = "You are level " + level + " now!\nYour will is stronger than ever!";
        }
    }

    private void contactMonster(int index) {
        if(index != 999){
            if(!invincible && !gp.monster[index].dying){
                gp.playSE(6);
                int damage = gp.monster[index].attack - getEffectiveDefense();
                if(damage < 0){
                    damage = 0;
                }
                HP -= damage;
                invincible = true;
                flashing = true;
                flashCounter = 0;
                startKnockback(gp.monster[index].direction, knockbackDistance);
                gp.startScreenShake(hitStopOnTakingDamage, 6);
                spawnHitParticles();
                gp.startHitStop(hitStopOnTakingDamage);
            }
        }
    }

    public void pickUpObject(int index){
        if(index != 999){

            if(gp.obj[index].type == type_pickuponly){
                //PICK UP ONLY ITEM
                gp.obj[index].use(this);
                gp.obj[index] = null;
            }
            else{
                //INVENTORY ITEM
                String text;
                if(inventory.size() != inventorySize){
                    inventory.add(gp.obj[index]);
                    gp.playSE(1);
                    text = "Picked up a " + gp.obj[index].name + "!";
                }
                else{
                    text = "Inventory is full!";
                }
                gp.ui.addMessage(text);
                gp.obj[index] = null;
            }


        }
    }

    public void interactNPC(int index){
        if(gp.keyH.ePressed){
            if(index != 999){
                attackCanceled = true;
                gp.gameState = gp.dialogueState;
                gp.npc[index].speak();
            }
        }
    }

    public void objectInteract(int index){
        if(index != 999 && gp.interactable[index].destructible && gp.interactable[index].isCorrectItem(this) && !gp.interactable[index].invincible){
            gp.interactable[index].playSE();
            gp.interactable[index].HP--;
            gp.interactable[index].invincible = true;
            generateParticle(gp.interactable[index], gp.interactable[index]);

            if(gp.interactable[index].HP <= 0){
                gp.interactable[index] = gp.interactable[index].getDestroyedForm();
            }
        }
    }

    public void selectItem(){
        int itemIndex = gp.ui.getItemIndexOnSlot();
        if(itemIndex < inventory.size()){

            Entity selectedItem = inventory.get(itemIndex);

            if(selectedItem.type == type_sword || selectedItem.type == type_axe){
                currentWeapon = selectedItem;
                attack = getAttack();
                getPlayerAttackImage();
            }
            if(selectedItem.type == type_shield){
                currentShield = selectedItem;
                defense = getDefense();
            }
            if(selectedItem.type == type_consumable){
                selectedItem.use(this);
                inventory.remove(itemIndex);
            }
        }
    }

    public void draw(Graphics2D g2){
        SpriteAnimation anim = getCurrentAnimation();
        BufferedImage bodyImage = getCurrentFrame();
        if(bodyImage == null) return;

        int bodyWidth = (anim != null) ? anim.width : bodyImage.getWidth();
        int bodyHeight = (anim != null) ? anim.height : bodyImage.getHeight();

        int drawX = screenX + (gp.tileSize - bodyWidth) / 2;
        int drawY = screenY + (gp.tileSize - bodyHeight) / 2;

        double bounce = 0;
        if(moving && !attacking){
            bounce = Math.abs(Math.sin(bounceCounter * 0.2)) * -4;
        }

        AffineTransform originalTransform = g2.getTransform();
        g2.translate(0, bounce);

        if(invincible){
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        }

        g2.drawImage(bodyImage, drawX, drawY, null);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        g2.setTransform(originalTransform);
    }


    private void drawAttackHitbox(Graphics2D g2){
        if(!attacking || spriteCounter <= 5 || spriteCounter > 25) return;

        int hbWorldX = worldX;
        int hbWorldY = worldY;
        switch (direction){
            case "up" -> hbWorldY -= attackArea.height;
            case "down" -> hbWorldY += attackArea.height;
            case "left" -> hbWorldX -= attackArea.width;
            case "right" -> hbWorldX += attackArea.width;
        }

        int screenX = hbWorldX - gp.player.worldX + gp.player.screenX + solidArea.x;
        int screenY = hbWorldY - gp.player.worldY + gp.player.screenY + solidArea.y;

        g2.setColor(Color.yellow);
        g2.drawRect(screenX, screenY, attackArea.width, attackArea.height);
    }

    private String getWeaponFolder(){
        return switch (currentWeapon.type){
            case type_axe -> "axe";
            // case type_spear -> "spear";
            // case type_mace -> "mace";
            default -> "sword";
        };
    }

    private void spawnTrailParticle(){
        int feetOffsetY = (solidArea.y + solidArea.height) - (gp.tileSize / 2);
        Particle p = new Particle(gp, this, getParticleColor(), getParticleSize(), getParticleSpeed(), getParticleMaxHP(), 0, 0, 0, feetOffsetY);
        p.groundEffect = true;
        gp.particleList.add(p);
    }

    @Override
    public Color getParticleColor(){
        return new Color(223, 223, 223); // dust color — tune to your art
    }

    @Override
    public int getParticleSize(){
        return 6;
    }

    @Override
    public int getParticleSpeed(){
        return 0; // trail particles stay put and just fade/expire, don't fly outward
    }

    @Override
    public int getParticleMaxHP(){
        return 20; // lifespan in ticks before it disappears
    }

    private void setupSkills(){
        skills[0] = new Skill("Power Strike", 2, 300, () -> {
            powerStrikePending = true;
            powerStrikeBonus = attack; // roughly doubles next hit - tune to taste
            spawnSkillParticles(new Color(255, 200, 0), 8);
            gp.ui.addMessage("Power Strike!");
        }, loadSkillIcon("/skill_icon/sword_powerstrike"));

        skills[1] = new Skill("War Cry", 1, 600, () -> {
            addStatusEffect(new StatusEffect("attackBoost", 300, 3, 0));
            spawnSkillParticles(new Color(255, 80, 80), 10);
            gp.ui.addMessage("War Cry!");
        },loadSkillIcon("/skill_icon/sword_warcry"));

        skills[2] = new Skill("Adrenaline Rush", 1, 600, () -> {
            addStatusEffect(new StatusEffect("attackSpeedBoost", 300, 2, 0));
            spawnSkillParticles(new Color(255, 255, 0), 10);
            gp.ui.addMessage("Adrenaline Rush!");
        },loadSkillIcon("/skill_icon/sword_adrenaline"));

        skills[3] = new Skill("Rend", 1, 480, () -> {
            rendPending = true;
            spawnSkillParticles(new Color(150, 0, 0), 8);
            gp.ui.addMessage("Rend!");
        },loadSkillIcon("/skill_icon/sword_rend"));

        skills[4] = new Skill("Fortify", 1, 600, () -> {
            addStatusEffect(new StatusEffect("defenseBoost", 300, 3, 0));
            spawnSkillParticles(new Color(120, 180, 255), 10);
            gp.ui.addMessage("Fortify!");
        },loadSkillIcon("/skill_icon/fortify"));
    }

    private BufferedImage loadSkillIcon(String path){
        BufferedImage img = null;
        try{
            img = ImageIO.read(getClass().getResourceAsStream(path + ".png"));
        } catch(Exception e){
            e.printStackTrace();
        }
        return img;
    }

    private void spawnSkillParticles(Color color, int count){
        for(int i = 0; i < count; i++){
            double angle = (2 * Math.PI / count) * i;
            int xd = (int)Math.round(Math.cos(angle) * 2);
            int yd = (int)Math.round(Math.sin(angle) * 2);
            Particle p = new Particle(gp, this, color, 6, 2, 20, xd, yd);
            gp.particleList.add(p);
        }
    }

    public int getEffectiveAttack(){
        return attack + getStatusEffectValue("attackBoost");
    }

    public int getEffectiveDefense(){
        return defense + getStatusEffectValue("defenseBoost");
    }

    private int getAttackFrameDelay(int frameNumber){
        if(frameNumber < attackHitStartFrame){
            return attackWindupFrameDelay;
        }
        if(frameNumber <= attackHitEndFrame){
            return getEffectiveAttackFrameDelay(); // active window — still respects Adrenaline Rush's attackSpeedBoost
        }
        return attackRecoveryFrameDelay;
    }

    public int getEffectiveAttackFrameDelay(){
        int delay = attackActiveFrameDelay - getStatusEffectValue("attackSpeedBoost");
        return Math.max(1, delay);
    }
}
