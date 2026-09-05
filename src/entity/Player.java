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
    public boolean facingLeft = false;

    public Animator bodyAnimator = new Animator();
    public Animator leftWeaponAnimator = new Animator();
    public Animator rightWeaponAnimator = new Animator();

    public Entity leftHandItem;
    public Entity rightHandItem;

    private int bounceCounter = 0;
    private BufferedImage currentWeaponFlipped;
    private BufferedImage currentShieldFlipped;
    private BufferedImage currentShieldFlippedDark;
    private BufferedImage currentShieldDark;

    public ArrayList<Entity> inventory = new ArrayList<>();
    public final int inventorySize = 20;

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
        updateWeaponSprite();
        rightHandItem = currentWeapon;
        currentShield = new OBJ_shield_wood(gp);
        updateShieldSprite();
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
    }

    private int getDefense() {
        return defense = dexterity * currentShield.defenseValue;
    }

    private int getAttack() {
        attackArea = currentWeapon.attackArea;
        return attack = strength * currentWeapon.attackValue;
    }

    public void getPlayerImage(){
        int size = (int)(gp.tileSize * 1.3);
        bodyAnimator.setFrameDelay(4);

        BufferedImage[] idleFrames = { setup("/player/female/idle_1", size, size),
                setup("/player/female/idle_2", size, size),
                setup("/player/female/idle_3", size, size),
                setup("/player/female/idle_4", size, size)};
        bodyAnimator.addAnimation("idle", new SpriteAnimation(idleFrames,size,size,true));

        BufferedImage[] runFrames = {
                setup("/player/female/run_1", size,size),
                setup("/player/female/run_2", size, size),
                setup("/player/female/run_3", size, size),
                setup("/player/female/run_4", size, size),
                setup("/player/female/run_5", size, size),
                setup("/player/female/run_6", size, size)
        };
        bodyAnimator.addAnimation("run",new SpriteAnimation(runFrames,size,size,true));

        down1 = idleFrames[0];
    }

    public void getPlayerAttackImage(){

        if(currentWeapon.type == type_sword){
            attackUp1 = setup("/player/boy_attack_up_1", gp.tileSize, gp.tileSize * 2);
            attackUp2 = setup("/player/boy_attack_up_2", gp.tileSize, gp.tileSize * 2);
            attackDown1 = setup("/player/boy_attack_down_1", gp.tileSize, gp.tileSize * 2);
            attackDown2 = setup("/player/boy_attack_down_2", gp.tileSize, gp.tileSize * 2);
            attackLeft1 = setup("/player/boy_attack_left_1", gp.tileSize * 2, gp.tileSize);
            attackLeft2 = setup("/player/boy_attack_left_2", gp.tileSize * 2, gp.tileSize);
            attackRight1 = setup("/player/boy_attack_right_1", gp.tileSize * 2, gp.tileSize);
            attackRight2 = setup("/player/boy_attack_right_2", gp.tileSize * 2, gp.tileSize);
        }
        if(currentWeapon.type == type_axe){
            attackUp1 = setup("/player/boy_axe_up_1", gp.tileSize, gp.tileSize * 2);
            attackUp2 = setup("/player/boy_axe_up_2", gp.tileSize, gp.tileSize * 2);
            attackDown1 = setup("/player/boy_axe_down_1", gp.tileSize, gp.tileSize * 2);
            attackDown2 = setup("/player/boy_axe_down_2", gp.tileSize, gp.tileSize * 2);
            attackLeft1 = setup("/player/boy_axe_left_1", gp.tileSize * 2, gp.tileSize);
            attackLeft2 = setup("/player/boy_axe_left_2", gp.tileSize * 2, gp.tileSize);
            attackRight1 = setup("/player/boy_axe_right_1", gp.tileSize * 2, gp.tileSize);
            attackRight2 = setup("/player/boy_axe_right_2", gp.tileSize * 2, gp.tileSize);
        }
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
            facingLeft = true;
        }
        else if(keyH.rightPressed){
            horizDir = "right";
            facingLeft = false;
        }

        if(keyH.upPressed){
            vertDir = "up";
        }
        else if(keyH.downPressed){
            vertDir = "down";
        }

        direction = (vertDir != null) ? vertDir : direction;
        direction = (horizDir != null) ? horizDir : direction;

        bodyAnimator.setState(moving ? "run" : "idle");
        bodyAnimator.update();

        if(attacking){
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
                    spriteCounter = 0;
                }

                attackCanceled = false;
                gp.keyH.ePressed = false;
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
        spriteCounter++;

        if(spriteCounter <= 5){
            spriteNum = 1;
        }
        if(spriteCounter > 5 && spriteCounter <= 25){
            spriteNum = 2;

            //Save current WorldX, WorldY, SolidArea
            int currentWorldX = worldX;
            int currentWorldY = worldY;
            int solidAreaWidth = solidArea.width;
            int solidAreaHeight = solidArea.height;

            //Adjust player's WorldX/Y for the Attack area
            switch (direction){
                case "up" -> worldY -= attackArea.height;
                case "down" -> worldY += attackArea.height;
                case "left" -> worldX -= attackArea.width;
                case "right" -> worldX += attackArea.width;
            }

            solidArea.width = attackArea.width;
            solidArea.height = attackArea.height;

            int monsterIndex = gp.collisionChecker.checkEntity(this, gp.monster);
            damageMonster(monsterIndex, attack);

            int interactableIndex = gp.collisionChecker.checkEntity(this, gp.interactable);
            objectInteract(interactableIndex);

            worldX = currentWorldX;
            worldY = currentWorldY;
            solidArea.width = solidAreaWidth;
            solidArea.height = solidAreaHeight;

        }
        if(spriteCounter > 25){
            spriteNum = 1;
            spriteCounter = 0;
            attacking = false;
        }
    }

    private String getDirectionFromMouse(){
        double dx = gp.mouseH.getScaledX() - (screenX + gp.tileSize / 2.0);
        double dy = gp.mouseH.getScaledY() - (screenY + gp.tileSize / 2.0);

        if(Math.abs(dx) > Math.abs(dy)){
            return dx > 0 ? "right" : "left";
        }
        else{
            return dy > 0 ? "down" : "up";
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
                gp.monster[index].damageReaction();

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
                int damage = gp.monster[index].attack - defense;
                if(damage < 0){
                    damage = 0;
                }
                HP -= damage;
                invincible = true;
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
                updateWeaponSprite();
                attack = getAttack();
                getPlayerAttackImage();
            }
            if(selectedItem.type == type_shield){
                currentShield = selectedItem;
                updateShieldSprite();
                defense = getDefense();
            }
            if(selectedItem.type == type_consumable){
                selectedItem.use(this);
                inventory.remove(itemIndex);
            }
        }
    }

    public void draw(Graphics2D g2){
        BufferedImage bodyImage;
        int bodyWidth, bodyHeight;

        if(attacking){
            SpriteAnimation attackAnim = sprites.get("attack_" + direction);
            if(attackAnim == null) return;
            bodyImage = attackAnim.frames[Math.min(spriteNum - 1, attackAnim.frames.length - 1)];
            bodyWidth = attackAnim.width;
            bodyHeight = attackAnim.height;
        }
        else{
            bodyImage = bodyAnimator.getCurrentFrame(facingLeft);
            if(bodyImage == null) return;
            bodyWidth = bodyImage.getWidth();
            bodyHeight = bodyImage.getHeight();
        }

        int drawX = screenX + (gp.tileSize - bodyWidth) / 2;
        int drawY = screenY + (gp.tileSize - bodyHeight) / 2;

        if(attacking){
            switch (direction){
                case "up" -> drawY = screenY + gp.tileSize - bodyHeight;
                case "down" -> drawY = screenY;
                case "left" -> drawX = screenX + gp.tileSize - bodyWidth;
                case "right" -> drawX = screenX;
            }
        }

        //BOUNCE - transform only, no extra frames, only while running
        double bounce = 0;
        if(moving && !attacking){
            bounce = Math.abs(Math.sin(bounceCounter * 0.2)) * -4;
        }

        AffineTransform originalTransform = g2.getTransform();
        g2.translate(0, bounce);

        if(invincible){
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        }

        boolean weaponInFront = isWeaponInFront();

        if(!attacking && !weaponInFront){
            drawWeapon(g2,drawX,drawY,bodyWidth,bodyHeight);
        }
        if(weaponInFront){
            drawShield(g2,drawX,drawY,bodyWidth,bodyHeight,true);
        }

        g2.drawImage(bodyImage,drawX,drawY,null);

        if(!attacking && weaponInFront){
            drawWeapon(g2,drawX,drawY,bodyWidth,bodyHeight);
        }
        if(!weaponInFront){
            drawShield(g2,drawX,drawY,bodyWidth,bodyHeight,false);
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1f));
        g2.setTransform(originalTransform);

        if(gp.keyH.showDebug){
            drawHitbox(g2);
            drawAttackHitbox(g2);
        }
    }

    private boolean isWeaponInFront(){
        return !facingLeft;
    }

    private void drawWeapon(Graphics2D g2, int bodyDrawX, int bodyDrawY, int bodyWidth, int bodyHeight){
        if(currentWeapon == null || currentWeapon.down1 == null) return;

        BufferedImage weaponImage = facingLeft ? currentWeaponFlipped:currentWeapon.down1;
        if(weaponImage == null) return;

        int weaponW = weaponImage.getWidth();
        int weaponH = weaponImage.getHeight();

        int offsetX = facingLeft ? leftHandOffsetX(bodyWidth, weaponW) : rightHandOffsetX(bodyWidth, weaponW);

        int weaponX = bodyDrawX + offsetX;
        int weaponY = bodyDrawY + (bodyHeight / 2);

        g2.drawImage(weaponImage, weaponX, weaponY, null);
    }

    private void drawShield(Graphics2D g2, int bodyDrawX, int bodyDrawY, int bodyWidth, int bodyHeight, boolean covered){
        if(currentShield == null || currentShield.down1 == null) return;

        BufferedImage shieldImage;
        if(facingLeft){
            shieldImage = covered ? currentShieldFlippedDark : currentShieldFlipped;
        }
        else{
            shieldImage = covered ? currentShieldDark : currentShield.down1;
        }
        if(shieldImage == null) return;

        int shieldW = shieldImage.getWidth();

        //Shield is the opposite hand from the sword, so the formulas are swapped
        int offsetX = facingLeft ? rightHandOffsetX(bodyWidth, shieldW) : leftHandOffsetX(bodyWidth, shieldW);
        int shieldX = bodyDrawX + offsetX;
        int shieldY = bodyDrawY + (bodyHeight / 2);

        g2.drawImage(shieldImage, shieldX, shieldY, null);
    }

    private int rightHandOffsetX(int bodyWidth, int itemWidth){
        return bodyWidth - (int)(itemWidth * 2.5 / 3);
    }

    private int leftHandOffsetX(int bodyWidth, int itemWidth){
        return bodyWidth - rightHandOffsetX(bodyWidth, itemWidth) - itemWidth;
    }

    private void updateWeaponSprite(){
        if(currentWeapon != null && currentWeapon.down1 != null){
            currentWeaponFlipped = SpriteAnimation.flipHorizontal(currentWeapon.down1);
        }
    }

    private void updateShieldSprite(){
        if(currentShield != null && currentShield.down1 != null){
            currentShieldFlipped = SpriteAnimation.flipHorizontal(currentShield.down1);
            currentShieldDark = SpriteAnimation.darken(currentShield.down1, 0.5f);
            currentShieldFlippedDark = SpriteAnimation.darken(currentShieldFlipped, 0.5f);
        }
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
}
