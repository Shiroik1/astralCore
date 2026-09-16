package entity;

import main.Gamepanel;
import main.UtilityTool;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

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
    public boolean flashing = false;
    public int flashCounter = 0;
    public int flashDuration = 6; // ticks — short blip, independent of invincibility length
    public boolean knockbackActive = false;
    public String knockbackDirection;
    public int knockbackRemaining = 0;
    public int knockbackSpeed = 10; // px per tick — lower = smoother/slower, higher = snappier
    public int knockbackDistance = 50; // pixels — tunable per hit weight

    public List<StatusEffect> statusEffects = new ArrayList<>();
    private Map<BufferedImage, BufferedImage> flashCache = new IdentityHashMap<>();

    public String npcId = "";
    public java.util.List<String> dialogueLines = new java.util.ArrayList<>();

    //SPRITES
    public Map<String, SpriteAnimation> sprites = new HashMap<>();
    public String animState = "idle";
    public boolean moving = false;

    public Rectangle solidArea = new Rectangle(0,0,48,48);
    public Rectangle attackArea = new Rectangle(0, 0, 0, 0);
    public int solidAreaDefaultX, solidAreaDefaultY;
    public boolean collisionOn = false;
    public int actionLockCounter = 0;
    public int dyingCounter = 0;
    public int hpBarCounter = 0;
    public int shotAvailableCounter = 0;
    public boolean groundEffect = false;

    public BufferedImage image, image1, image2;
    public String name;
    public boolean collision = false;
    public boolean attacking = false;
    public boolean pickupBlocked = false; // true right after a player-initiated drop, until the player's hitbox leaves this item's hitbox
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

    //STACKING
    public boolean stackable = false;
    public int stackCount = 1;
    public int maxStackSize = 1;

    public boolean isPlaceholder = false; // true for fallback items (unarmed/no-shield) that can't be dragged or dropped

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

    protected void loadDialogue(String jsonResourcePath){
        dialogueLines = main.DialogueLoader.loadLines(jsonResourcePath);
    }

    public void update(){
        if(dying){
            updateAnimation();
            if(dyingCounter > 40){
                alive = false;
            }
            return;
        }

        setAction();

        if(knockbackActive){
            updateKnockback();
        }

        collisionOn = false;
        gp.collisionChecker.checkTile(this);
        gp.collisionChecker.checkObject(this, false);
        gp.collisionChecker.checkEntity(this, gp.npc);
        gp.collisionChecker.checkEntity(this, gp.monster);
        gp.collisionChecker.checkEntity(this, gp.interactable);

        Player contactedPlayer = gp.collisionChecker.checkPlayer(this);

        if(this.type == type_monster && contactedPlayer != null){
            damagePlayer(contactedPlayer, attack);
        }

        moving = !collisionOn;
        animState = moving ? "walk" : "idle";

        //IF COLLISION IS FALSE, ENTITY CAN MOVE
        if(!collisionOn && !knockbackActive){
            switch (direction){
                case "up" -> worldY -= speed;
                case "down" -> worldY += speed;
                case "left" -> worldX -= speed;
                case "right" -> worldX += speed;
            }
        }

        updateAnimation();

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

        updateStatusEffects();
    }

    public void updateAnimation(){
        if(gp.hitStopCounter > 0) return;

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

        if(flashing){
            flashCounter++;
            if(flashCounter > flashDuration){
                flashing = false;
                flashCounter = 0;
            }
        }

        if(hpBarOn){
            hpBarCounter++;
            if(hpBarCounter > 600){
                hpBarCounter = 0;
                hpBarOn = false;
            }
        }

        if(dying){
            dyingCounter++;
        }
    }

    public void damagePlayer(Player target, int attack){
        if(!target.invincible){
            int damage = attack - target.defense;
            if(damage < 0){
                damage = 0;
            }
            target.HP -= damage;
            target.invincible = true;
            target.flashing = true;
            target.flashCounter = 0;
            target.spawnHitParticles();
//            target.startKnockback(this.direction, target.knockbackDistance);
//            gp.startHitStop(8);
            gp.startScreenShake(8, 6);
        }
    }

    public void draw(Graphics2D g2){
        BufferedImage image = null;
        int screenX = worldX - gp.localPlayer().worldX + gp.localPlayer().screenX;
        int screenY = worldY - gp.localPlayer().worldY + gp.localPlayer().screenY;

        if(worldX + gp.tileSize > gp.localPlayer().worldX - gp.localPlayer().screenX &&
                worldX - gp.tileSize < gp.localPlayer().worldX + gp.localPlayer().screenX &&
                worldY + gp.tileSize > gp.localPlayer().worldY - gp.localPlayer().screenY &&
                worldY - gp.tileSize < gp.localPlayer().worldY + gp.localPlayer().screenY){

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
            }

            if(dying){
                drawDyingAlpha(g2);
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

    public void drawDyingAlpha(Graphics2D g2) {
        int i = 5;
        int c = dyingCounter;

        if(c <= i)            changeAlpha(g2, 0f);
        else if(c <= i*2)     changeAlpha(g2, 1f);
        else if(c <= i*3)     changeAlpha(g2, 0f);
        else if(c <= i*4)     changeAlpha(g2, 1f);
        else if(c <= i*5)     changeAlpha(g2, 0f);
        else if(c <= i*6)     changeAlpha(g2, 1f);
        else if(c <= i*7)     changeAlpha(g2, 0f);
        else if(c <= i*8)     changeAlpha(g2, 1f);
        else                  changeAlpha(g2, 0f);
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
        BufferedImage frame = (anim == null) ? getLegacyFrame() : anim.frames[Math.min(spriteNum - 1, anim.frames.length - 1)];
        return flashing ? getFlashVersion(frame) : frame;
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
        int screenX = worldX - gp.localPlayer().worldX + gp.localPlayer().screenX + solidArea.x;
        int screenY = worldY - gp.localPlayer().worldY + gp.localPlayer().screenY + solidArea.y;

        Color color = switch (type){
            case type_monster -> Color.red;
            case type_npc -> Color.cyan;
            case type_player -> Color.green;
            default -> Color.magenta;
        };

        g2.setColor(color);
        g2.drawRect(screenX, screenY, solidArea.width, solidArea.height);
    }

    public void addStatusEffect(StatusEffect effect){
        statusEffects.add(effect);
    }

    public void updateStatusEffects(){
        for(int i = statusEffects.size() - 1; i >= 0; i--){
            StatusEffect e = statusEffects.get(i);
            e.duration--;

            if(e.type.equals("bleed")){
                e.tickCounter++;
                if(e.tickCounter >= e.tickInterval){
                    HP -= e.value;
                    if(HP < 0) HP = 0;
                    e.tickCounter = 0;

                    if(HP <= 0 && type == type_monster){
                        dying = true; // matches the death flag damageMonster() sets - but skips EXP/kill message, since that logic lives in Player currently
                    }
                }
            }

            if(e.duration <= 0){
                statusEffects.remove(i);
            }
        }
    }

    public boolean hasStatusEffect(String statusType){
        for(StatusEffect e : statusEffects){
            if(e.type.equals(statusType)) return true;
        }
        return false;
    }

    public int getStatusEffectValue(String statusType){
        for(StatusEffect e : statusEffects){
            if(e.type.equals(statusType)) return e.value;
        }
        return 0;
    }

    public void spawnHitParticles(){
        Color color = Color.white;
        int size = 5;
        int speed = 2;
        int maxHP = 15;

        for(int i = 0; i < 4; i++){
            double angle = (2 * Math.PI / 4) * i + (Math.PI / 4); // 45° offset so it doesn't align with movement axes
            int xd = (int) Math.round(Math.cos(angle) * 2);
            int yd = (int) Math.round(Math.sin(angle) * 2);
            Particle p = new Particle(gp, this, color, size, speed, maxHP, xd, yd);
            gp.particleList.add(p);
        }
    }

    protected Player findNearestPlayer(){
        Player nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        for(Player p : gp.players){
            if(p == null) continue;
            int dist = Math.abs(worldX - p.worldX) + Math.abs(worldY - p.worldY);
            if(dist < nearestDist){
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    // Add near the other CHARACTER STATUS fields
    public int aggroRange = 0; // tiles; 0 = chase disabled, monster subclasses override

    protected boolean isPlayerInAggroRange(){
        if(aggroRange <= 0) return false;
        Player target = findNearestPlayer();
        if(target == null) return false;

        int xDistance = Math.abs(worldX - target.worldX);
        int yDistance = Math.abs(worldY - target.worldY);
        int tileDistance = Math.max(xDistance, yDistance) / gp.tileSize;
        return tileDistance < aggroRange;
    }

    protected void chasePlayer(){
        Player target = findNearestPlayer();
        if(target == null) return;

        int xDist = target.worldX - worldX;
        int yDist = target.worldY - worldY;

        if(Math.abs(xDist) > Math.abs(yDist)){
            direction = xDist > 0 ? "right" : "left";
        } else {
            direction = yDist > 0 ? "down" : "up";
        }
    }

    protected BufferedImage getFlashVersion(BufferedImage src){
        if(src == null) return null;
        return flashCache.computeIfAbsent(src, SpriteAnimation::whiteFlash);
    }

    public void startKnockback(String fromDirection, int distance){
        knockbackActive = true;
        knockbackDirection = fromDirection;
        knockbackRemaining = distance;
    }

    public void updateKnockback(){
        if(knockbackRemaining <= 0){
            knockbackActive = false;
            return;
        }

        int step = Math.min(knockbackSpeed, knockbackRemaining);

        String savedDirection = direction;
        int savedSpeed = speed;
        direction = knockbackDirection;
        speed = step;

        collisionOn = false;
        gp.collisionChecker.checkTile(this);
        gp.collisionChecker.checkObject(this, false);

        // Check against whichever entity lists are relevant, so knockback stops
        // at the edge of another entity instead of overlapping it
        if(type == type_player){
            gp.collisionChecker.checkEntity(this, gp.monster);
            gp.collisionChecker.checkEntity(this, gp.npc);
        } else {
            gp.collisionChecker.checkEntity(this, gp.monster);
            gp.collisionChecker.checkEntity(this, gp.npc);
            gp.collisionChecker.checkPlayer(this);
        }

        if(!collisionOn){
            switch (knockbackDirection){
                case "up" -> worldY -= step;
                case "down" -> worldY += step;
                case "left" -> worldX -= step;
                case "right" -> worldX += step;
            }
            knockbackRemaining -= step;
        } else {
            knockbackRemaining = 0; // hit something — stop cleanly here, don't tunnel or overlap
        }

        direction = savedDirection;
        speed = savedSpeed;
        collisionOn = false;

        if(knockbackRemaining <= 0){
            knockbackActive = false;
        }
    }

    public boolean canStackWith(Entity other){
        return stackable && other != null && other.stackable && this.getClass() == other.getClass();
    }


}
