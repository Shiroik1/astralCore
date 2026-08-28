package entity;

import main.Gamepanel;
import main.KeyHandler;
import main.UtilityTool;
import object.OBJ_key;
import object.OBJ_shield_wood;
import object.OBJ_sword_normal;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class Player extends Entity{
    KeyHandler keyH;

    public final int screenX;
    public final int screenY;

    public boolean attackCanceled = false;

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
        worldX = gp.tileSize * 71;
        worldY = gp.tileSize * 78;
        speed = 4;
        direction = "down";

        //PLAYER STATS
        maxHP = 10;
        HP = maxHP;
        level = 1;
        strength = 1;
        dexterity = 1;
        exp = 0;
        nextLevelExp = 5;
        coin = 0;
        currentWeapon = new OBJ_sword_normal(gp);
        currentShield = new OBJ_shield_wood(gp);
        attack = getAttack();
        defense = getDefense();
    }

    public void setItems(){
        inventory.add(currentWeapon);
        inventory.add(currentShield);
        inventory.add(new OBJ_key(gp));
        inventory.add(new OBJ_key(gp));
    }

    private int getDefense() {
        return defense = dexterity * currentShield.defenseValue;
    }

    private int getAttack() {
        attackArea = currentWeapon.attackArea;
        return attack = strength * currentWeapon.attackValue;
    }

    public void getPlayerImage(){

        up1 = setup("/player/boy_up_1", gp.tileSize, gp.tileSize);
        up2 = setup("/player/boy_up_2", gp.tileSize, gp.tileSize);
        down1 = setup("/player/boy_down_1", gp.tileSize, gp.tileSize);
        down2 = setup("/player/boy_down_2", gp.tileSize, gp.tileSize);
        left1 = setup("/player/boy_left_1", gp.tileSize, gp.tileSize);
        left2 = setup("/player/boy_left_2", gp.tileSize, gp.tileSize);
        right1 = setup("/player/boy_right_1", gp.tileSize, gp.tileSize);
        right2 = setup("/player/boy_right_2", gp.tileSize, gp.tileSize);

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

        if(attacking){
            attacking();
        }
        else if(keyH.upPressed || keyH.downPressed || keyH.leftPressed || keyH.rightPressed || keyH.ePressed){
            if(keyH.upPressed){
                direction = "up";
            }
            else if(keyH.downPressed){
                direction = "down";
            }
            else if(keyH.leftPressed){
                direction = "left";
            }
            else if(keyH.rightPressed){
                direction = "right";
            }

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

            //CHECK EVENT
            gp.eventHandler.checkEvent();

            //IF COLLISION IS FALSE, PLAYER CAN MOVE
            if(!collisionOn && !keyH.ePressed){
                switch (direction){
                    case "up" -> worldY -= speed;
                    case "down" -> worldY += speed;
                    case "left" -> worldX -= speed;
                    case "right" -> worldX += speed;
                }
            }

            if(keyH.ePressed && !attackCanceled){
                attacking = true;
                spriteCounter = 0;
            }

            attackCanceled = false;
            gp.keyH.ePressed = false;

            spriteCounter++;
            if(spriteCounter > 12){
                if(spriteNum == 1){
                    spriteNum = 2;
                }
                else if(spriteNum == 2){
                    spriteNum = 1;
                }
                spriteCounter = 0;
            }
        }

        if(invincible){
            invincibleCounter++;
            if(invincibleCounter > 60){
                invincible = false;
                invincibleCounter = 0;
            }
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
            damageMonster(monsterIndex);

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

    private void damageMonster(int index) {
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
            if(!invincible){
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

    public void interactNPC(int index){
        if(gp.keyH.ePressed){
            if(index != 999){
                attackCanceled = true;
                gp.gameState = gp.dialogueState;
                gp.npc[index].speak();
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
        BufferedImage image = null;

        int tempScreenX = screenX;
        int tempScreenY = screenY;

        switch (direction){
            case "up" -> {
                if(!attacking){
                    if(spriteNum == 1){image = up1;}
                    if(spriteNum == 2){image = up2;}
                }
                if(attacking){
                    tempScreenY = screenY - gp.tileSize;
                    if(spriteNum == 1){image = attackUp1;}
                    if(spriteNum == 2){image = attackUp2;}
                }
            }
            case "down" -> {
                if(!attacking){
                    if(spriteNum == 1){image = down1;}
                    if(spriteNum == 2){image = down2;}
                }
                if(attacking){
                    if(spriteNum == 1){image = attackDown1;}
                    if(spriteNum == 2){image = attackDown2;}
                }
            }
            case "left" -> {
                if(!attacking){
                    if(spriteNum == 1){image = left1;}
                    if(spriteNum == 2){image = left2;}
                }
                if(attacking){
                    tempScreenX = screenX - gp.tileSize;
                    if(spriteNum == 1){image = attackLeft1;}
                    if(spriteNum == 2){image = attackLeft2;}
                }
            }
            case "right" -> {
                if(!attacking){
                    if(spriteNum == 1){image = right1;}
                    if(spriteNum == 2){image = right2;}
                }
                if(attacking){
                    if(spriteNum == 1){image = attackRight1;}
                    if(spriteNum == 2){image = attackRight2;}
                }
            }
        }

        if(invincible){
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        }

        g2.drawImage(image,tempScreenX,tempScreenY,null);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}
