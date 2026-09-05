package entity;

import main.Gamepanel;
import main.UtilityTool;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class Entity {
    public Gamepanel gp;
    public int worldX, worldY;
    public int speed;

    public BufferedImage up1, up2, down1, down2, left1, left2, right1, right2;
    public BufferedImage attackUp1, attackUp2, attackDown1, attackDown2, attackLeft1, attackLeft2, attackRight1, attackRight2;
    public String direction = "down";

    public int spriteCounter = 0;
    public int spriteNum = 1;

    public boolean invincible = false;
    public int invincibleCounter = 0;

    //SPRITES
    public Map<String, SpriteAnimation> sprites = new HashMap<>();
    public String animState = "idle";
    public boolean moving = false;

    public Rectangle solidArea = new Rectangle(0,0,48,48);
    public Rectangle attackArea = new Rectangle(0, 0, 0, 0);
    public int solidAreaDefaultX, solidAreaDefaultY;
    public boolean collisionOn = false;
    public int actionLockCounter = 0;
    public int dialogueIndex = 0;
    public int dyingCounter = 0;
    public int hpBarCounter = 0;
    public int shotAvailableCounter = 0;

    String dialogues[] = new String[20];

    public BufferedImage image, image1, image2;
    public String name;
    public boolean collision = false;
    public boolean attacking = false;
    public boolean alive = true;
    public boolean dying = false;
    public boolean hpBarOn = false;

    //TYPES
    public int type;
    public final int type_player = 0;
    public final int type_npc = 1;
    public final int type_monster = 2;
    public final int type_sword = 3;
    public final int type_axe = 4;
    public final int type_shield = 5;
    public final int type_consumable = 6;
    public final int type_pickuponly = 7;

    //CHARACTER STATUS
    public int maxHP;
    public int HP;
    public int maxMP;
    public int MP;
    public int level;
    public int strength;
    public int dexterity;
    public int attack;
    public int defense;
    public int exp;
    public int nextLevelExp;
    public int coin;
    public Entity currentWeapon;
    public Entity currentShield;
    public Projectile projectile;

    //ITEM STATS
    public int value;
    public int attackValue;
    public int defenseValue;
    public String description = "";
    public int useCost;

    public Entity(Gamepanel gp){
        this.gp = gp;
    }

    public BufferedImage setup(String imagePath, int width, int height){
        UtilityTool utilityTool = new UtilityTool();
        BufferedImage image = null;

        try{
            image = ImageIO.read(getClass().getResourceAsStream(imagePath + ".png"));
            image = utilityTool.scaleImage(image, width, height);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return image;
    }

    public void setAction(){

    }

    public void damageReaction(){

    }

    public void use(Entity entity){

    }

    public void checkDrop(){

    }

    public void dropItem(Entity droppedItem){
        for(int i = 0; i < gp.obj.length; i++){
            if(gp.obj[i] == null){
                gp.obj[i] = droppedItem;
                gp.obj[i].worldX = worldX;
                gp.obj[i].worldY = worldY;
                break;
            }
        }
    }

    public void speak(){
        if(dialogues[dialogueIndex] == null){
            dialogueIndex = 0;
        }

        gp.ui.currentDialogue = dialogues[dialogueIndex];
        dialogueIndex++;

        switch (gp.player.direction){
            case "up" -> direction = "down";
            case "down" -> direction = "up";
            case "left" -> direction = "right";
            case "right" -> direction = "left";
        }
    }

    public void update(){
        setAction();
        collisionOn = false;
        gp.collisionChecker.checkTile(this);
        gp.collisionChecker.checkObject(this, false);
        gp.collisionChecker.checkEntity(this, gp.npc);
        gp.collisionChecker.checkEntity(this, gp.monster);
        gp.collisionChecker.checkEntity(this, gp.interactable);
        boolean contactPlayer = gp.collisionChecker.checkPlayer(this);

        if(this.type == type_monster && contactPlayer){
            damagePlayer(attack);
        }

        moving = !collisionOn;
        animState = moving ? "walk" : "idle";

        //IF COLLISION IS FALSE, PLAYER CAN MOVE
        if(!collisionOn){
            switch (direction){
                case "up" -> worldY -= speed;
                case "down" -> worldY += speed;
                case "left" -> worldX -= speed;
                case "right" -> worldX += speed;
            }
        }

        spriteCounter++;
        if(spriteCounter > 12){
            SpriteAnimation anim = getCurrentAnimation();
            int frameCount = (anim != null) ? anim.frames.length : 2;
            spriteNum++;
            if(spriteNum > frameCount){
                spriteNum = 1;
            }
            spriteCounter = 0;
        }

        if(invincible){
            invincibleCounter++;
            if(invincibleCounter > 40){
                invincible = false;
                invincibleCounter = 0;
            }
        }

        if(shotAvailableCounter < 30){
            shotAvailableCounter++;
        }
    }

    public void damagePlayer(int attack){
        if(!gp.player.invincible){
            int damage = attack - gp.player.defense;
            if(damage < 0){
                damage = 0;
            }
            gp.player.HP -= damage;
            gp.player.invincible = true;
        }
    }

    public void draw(Graphics2D g2){
        BufferedImage image = null;
        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY;

        if(worldX + gp.tileSize > gp.player.worldX - gp.player.screenX &&
                worldX - gp.tileSize < gp.player.worldX + gp.player.screenX &&
                worldY + gp.tileSize > gp.player.worldY - gp.player.screenY &&
                worldY - gp.tileSize < gp.player.worldY + gp.player.screenY){

            SpriteAnimation anim = getCurrentAnimation();
            image = getCurrentFrame();

            //HP Bar
            if(type == type_monster && hpBarOn){

                double oneScale = (double) gp.tileSize/maxHP;
                double hpBarValue = oneScale * HP;

                g2.setColor(Color.black);
                g2.fillRect(screenX - 1, screenY - 16, gp.tileSize + 2, 7);
                g2.setColor(Color.red);
                g2.fillRect(screenX, screenY - 15, (int) hpBarValue , 5);

                hpBarCounter++;

                if(hpBarCounter > 600){
                    hpBarCounter = 0;
                    hpBarOn = false;
                }
            }

            if(invincible){
                hpBarOn= true;
                hpBarCounter = 0;
                changeAlpha(g2, 0.4f);
            }

            if(dying){
                dyingAnimation(g2);
            }

            int drawX = screenX;
            int drawY = screenY;
            if(anim != null){
                drawX = screenX + (gp.tileSize - anim.width) / 2;
                drawY = screenY + (gp.tileSize - anim.height) / 2;
            }
            g2.drawImage(image, drawX, drawY, null);
            changeAlpha(g2, 1f);
        }

        if(gp.keyH.showDebug){
            drawHitbox(g2);
        }
    }

    public void dyingAnimation(Graphics2D g2) {
        dyingCounter++;
        int i = 5;

        if(dyingCounter <= i){
            changeAlpha(g2, 0f);
        }
        if(dyingCounter > i && dyingCounter <= i*2){
            changeAlpha(g2, 1f);
        }
        if(dyingCounter > i*2 && dyingCounter <= i*3){
            changeAlpha(g2, 0f);
        }
        if(dyingCounter > i*3 && dyingCounter <= i*4){
            changeAlpha(g2, 1f);
        }
        if(dyingCounter > i*4 && dyingCounter <= i*5){
            changeAlpha(g2, 0f);
        }
        if(dyingCounter > i*5 && dyingCounter <= i*6){
            changeAlpha(g2, 1f);
        }
        if(dyingCounter > i*6 && dyingCounter <= i*7){
            changeAlpha(g2, 0f);
        }
        if(dyingCounter > i*7 && dyingCounter <= i*8){
            changeAlpha(g2, 1f);
        }
        if(dyingCounter > i*8){
            alive = false;
        }
    }

    public void changeAlpha(Graphics2D g2, float alphaValue){
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaValue));
    }

    public Color getParticleColor(){
        Color color = null;
        return  color;
    }

    public int getParticleSize(){
        int size = 0;
        return size;
    }

    public int getParticleSpeed(){
        int speed = 0;
        return speed;
    }

    public int getParticleMaxHP(){
        int maxHP = 0;
        return maxHP;
    }

    public void generateParticle(Entity generator, Entity target){
        Color color = generator.getParticleColor();
        int size = generator.getParticleSize();
        int speed = generator.getParticleSpeed();
        int maxHP = generator.getParticleMaxHP();

        Particle p1 = new Particle(gp,target,color,size,speed,maxHP, -2, -1);
        Particle p2 = new Particle(gp,target,color,size,speed,maxHP, 2, -1);
        Particle p3 = new Particle(gp,target,color,size,speed,maxHP, -2, 1);
        Particle p4 = new Particle(gp,target,color,size,speed,maxHP, 2, 1);
        gp.particleList.add(p1);
        gp.particleList.add(p2);
        gp.particleList.add(p3);
        gp.particleList.add(p4);
    }


    protected SpriteAnimation getCurrentAnimation(){
        SpriteAnimation anim = sprites.get(animState + "_" + direction);
        if(anim == null){
            anim = sprites.get("idle_" + direction);
        }
        return anim;
    }

    protected BufferedImage getCurrentFrame(){
        SpriteAnimation anim = getCurrentAnimation();
        if(anim == null) return getLegacyFrame();
        int index = Math.min(spriteNum - 1, anim.frames.length - 1);
        return anim.frames[index];
    }

    protected BufferedImage getLegacyFrame(){
        BufferedImage image = null;
        switch (direction){
            case "up" -> image = (spriteNum == 1) ? up1 : up2;
            case "down" -> image = (spriteNum == 1) ? down1 : down2;
            case "left" -> image = (spriteNum == 1) ? left1 : left2;
            case "right" -> image = (spriteNum == 1) ? right1 : right2;
        }
        return image;
    }

    public void drawHitbox(Graphics2D g2){
        int screenX = worldX - gp.player.worldX + gp.player.screenX + solidArea.x;
        int screenY = worldY - gp.player.worldY + gp.player.screenY + solidArea.y;

        Color color = switch (type){
            case type_monster -> Color.red;
            case type_npc -> Color.cyan;
            case type_player -> Color.green;
            default -> Color.magenta;
        };

        g2.setColor(color);
        g2.drawRect(screenX, screenY, solidArea.width, solidArea.height);
    }
}
