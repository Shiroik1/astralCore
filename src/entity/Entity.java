package entity;

import main.Gamepanel;
import main.UtilityTool;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import javax.imageio.ImageIO;

public class Entity {
    public Gamepanel gp;
    public int worldX, worldY;
    public int speed;

    public BufferedImage up1, up2, down1, down2, left1, left2, right1, right2;
    public BufferedImage attackUp1,
            attackUp2,
            attackDown1,
            attackDown2,
            attackLeft1,
            attackLeft2,
            attackRight1,
            attackRight2;
    public String direction = "down";

    public int spriteCounter = 0;
    public int spriteNum = 1;

    /**
     * Player hit feedback imposed by this damage source; negative knockback keeps the player
     * default.
     */
    public int incomingHitImmunity = 60, incomingHitKnockback = -1;

    public int invincibilityDuration = 40;
    public boolean invincible = false;
    public int invincibleCounter = 0;
    public boolean flashing = false;
    public int flashCounter = 0;
    public int flashDuration = 6; // ticks — short blip, independent of invincibility length
    public boolean knockbackActive = false;
    public String knockbackDirection;
    public int knockbackRemaining = 0;
    public int knockbackSpeed = 10; // px per tick — lower = smoother/slower, higher = snappier
    public int knockbackDistance = 80; // pixels — tunable per hit weight

    public List<StatusEffect> statusEffects = new ArrayList<>();
    private static final Map<BufferedImage, BufferedImage> flashCache =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private Map<Long, BufferedImage> tintCache = new HashMap<>();

    public boolean auraActive = false;
    public Color auraColor = null;
    public int auraDurationRemaining = 0;
    private int auraParticleCounter = 0;
    private final int auraParticleInterval = 8;

    public String npcId = "";
    public java.util.List<String> dialogueLines = new java.util.ArrayList<>();
    public int activeConversationCount = 0;

    // SPRITES
    public Map<String, SpriteAnimation> sprites = new HashMap<>();
    public String animState = "idle";
    public boolean moving = false;

    public int groundElevation = -1; // Optional fixed ground anchor for transient visual effects.
    public Rectangle solidArea = new Rectangle(0, 0, 48, 48);
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
    public boolean pickupBlocked =
            false; // true right after a player-initiated drop, until the player's hitbox leaves
    // this item's hitbox
    public boolean groundLoot;
    public int lootAge;
    public double lootVX, lootVY;
    public float lootLift;
    public boolean alive = true;
    public boolean dying = false;
    public boolean hpBarOn = false;
    public boolean highlighted = false;

    public int comboStacks = 0;
    public final int maxComboStacks = 2;

    public boolean attackState = false;

    // TYPES
    public static final int type_none = -1;
    public static final int type_player = 0;
    public static final int type_npc = 1;
    public static final int type_monster = 2;
    public static final int type_sword = 3;
    public static final int type_axe = 4;
    public static final int type_shield = 5;
    public static final int type_consumable = 6;
    public static final int type_pickuponly = 7;
    public static final int type_staff = 8;
    public static final int type_apparel = 9;
    public static final int type_bow = 10;
    public int type = type_none;

    // CHARACTER STATUS
    public int maxHP;
    public int HP;
    public int maxMP;
    public int MP;
    public int maxRage;
    public int rage;
    public int level;
    public int strength;
    public int dexterity;
    public int intelligence;
    public int vitality;
    public int attack;
    public int defense;
    public int exp;
    public int nextLevelExp;
    public int coin;
    public Entity currentWeapon;
    public Entity currentShield;
    public Projectile projectile;

    // ITEM STATS
    public int value;
    public int attackValue;
    public int defenseValue;
    public String description = "";
    public int useCost;

    // STACKING
    public boolean stackable = false;
    public int stackCount = 1;
    public int maxStackSize = 1;

    public boolean isPlaceholder =
            false; // true for fallback items (unarmed/no-shield) that can't be dragged or dropped

    /** Creates a Entity instance and initializes the state required by its collaborators. */
    public Entity(Gamepanel gp) {
        this.gp = gp;
    }

    private static final java.util.Map<String, BufferedImage> spriteCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Returns the current cached value. */
    public BufferedImage setup(String imagePath, int width, int height) {
        String key = imagePath + "@" + width + "x" + height;
        BufferedImage cached = spriteCache.get(key);
        if (cached != null) {
            return cached;
        }

        UtilityTool utilityTool = new UtilityTool();
        BufferedImage image = null;

        try {
            image = ImageIO.read(getClass().getResourceAsStream(imagePath + ".png"));
            image = utilityTool.scaleImage(image, width, height);
            spriteCache.put(key, image);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return image;
    }

    /** Updates the action for Entity. */
    public void setAction() {}

    /** Performs the damage reaction operation for Entity. */
    public void damageReaction() {}

    /** Performs the use operation for Entity. */
    public void use(Entity entity) {}

    /** Performs the check drop operation for Entity. */
    public void checkDrop() {}

    /** Performs the drop item operation for Entity. */
    public void dropItem(Entity droppedItem) {
        gp.quests.dropLoot(droppedItem, worldX, worldY);
    }

    /** Loads the dialogue for Entity. */
    protected void loadDialogue(String jsonResourcePath) {
        dialogueLines = main.DialogueLoader.loadLines(jsonResourcePath);
    }

    private float slowRemainder;

    /** Advances this component for one simulation tick. */
    public void update() {

        setAction();

        if (knockbackActive) {
            updateKnockback();
        }

        int normalSpeed = speed;
        int slow =
                Math.max(
                        getStatusEffectValue("rangerSlow"),
                        getStatusEffectValue(MageEffect.FROST_SLOW));
        if (type == type_monster && slow > 0 && speed > 0) {
            float adjusted = speed * (100 - slow) / 100f + slowRemainder;
            speed = (int) adjusted;
            slowRemainder = adjusted - speed;
        } else if (slow == 0) {
            slowRemainder = 0;
        }
        collisionOn = false;
        gp.collisionChecker.checkTile(this);
        gp.collisionChecker.checkObject(this, false);
        gp.collisionChecker.checkEntity(this, gp.npc);
        if (type != type_monster) {
            gp.collisionChecker.checkEntity(this, gp.monster);
        }
        gp.collisionChecker.checkEntity(this, gp.interactable);

        gp.collisionChecker.checkPlayer(this);

        moving = speed > 0 && !collisionOn;
        animState = moving ? "walk" : "idle";

        if (!collisionOn && !knockbackActive) {
            switch (direction) {
                case "up" -> worldY -= speed;
                case "down" -> worldY += speed;
                case "left" -> worldX -= speed;
                case "right" -> worldX += speed;
            }
        }

        speed = normalSpeed;
        updateAnimation();

        if (invincible) {
            invincibleCounter++;
            if (invincibleCounter > invincibilityDuration) {
                invincible = false;
                invincibleCounter = 0;
            }
        }

        if (shotAvailableCounter < 30) {
            shotAvailableCounter++;
        }

        updateStatusEffects();
        updateAura();
    }

    /** Updates the animation for Entity. */
    public void updateAnimation() {
        if (gp.hitStopCounter > 0) return;

        if (!attackState) {
            spriteCounter++;
            SpriteAnimation anim = getCurrentAnimation();
            int frameDelay = (anim != null) ? anim.frameDelay : 12;
            if (spriteCounter > frameDelay) {
                int frameCount = (anim != null) ? anim.frames.length : 2;
                spriteNum++;
                if (spriteNum > frameCount) {
                    spriteNum = 1;
                }
                spriteCounter = 0;
            }
        }

        if (flashing) {
            flashCounter++;
            if (flashCounter > flashDuration) {
                flashing = false;
                flashCounter = 0;
            }
        }

        if (hpBarOn) {
            hpBarCounter++;
            if (hpBarCounter > 600) {
                hpBarCounter = 0;
                hpBarOn = false;
            }
        }

        if (dying) {
            dyingCounter++;
        }
    }

    /** Performs the damage player operation for Entity. */
    public void damagePlayer(Player target, int attack) {
        target.receiveHit(this, attack, false);
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(Graphics2D g2) {
        if (type == type_monster && hasStatusEffect("frogMorph")) {
            Player cam = gp.localPlayer();
            g2.drawImage(
                    art.MorphArt.FROG,
                    worldX - cam.worldX + cam.screenX,
                    worldY - cam.worldY + cam.screenY,
                    null);
            return;
        }
        BufferedImage image = null;
        int screenX = worldX - gp.localPlayer().worldX + gp.localPlayer().screenX;
        int screenY = worldY - gp.localPlayer().worldY + gp.localPlayer().screenY;

        Player camera = gp.localPlayer();
        int viewLeft = camera.worldX - camera.screenX, viewTop = camera.worldY - camera.screenY;
        int groundLift = world.TerrainHeightResolver.lift(gp, this);
        if (worldX + gp.tileSize > viewLeft
                && worldX - gp.tileSize < viewLeft + gp.screenWidth
                && worldY + gp.tileSize - groundLift > viewTop
                && worldY - gp.tileSize - groundLift < viewTop + gp.screenHeight) {

            SpriteAnimation anim = getCurrentAnimation();
            image = getCurrentFrame();

            if (!dying) {
                drawShadow(g2, screenX, screenY);
            }

            // HP Bar
            if (type == type_monster
                    && (hpBarOn || highlighted)
                    && !(this instanceof Monster m && m.isBoss())) {
                ui.UITheme.bar(
                        g2,
                        new Rectangle(
                                screenX,
                                screenY - 16 - Math.round(spriteLiftPixels()),
                                gp.tileSize,
                                6),
                        HP,
                        maxHP,
                        ui.UITheme.HP,
                        null);
            }

            // COMBO STACK PIPS
            int visibleStacks = this instanceof Monster monster ? monster.visibleComboStacks() : 0;
            if (visibleStacks > 0) {
                int pipSize = 6;
                int pipSpacing = 3;
                int totalWidth = maxComboStacks * pipSize + (maxComboStacks - 1) * pipSpacing;
                int pipStartX = screenX + (gp.tileSize - totalWidth) / 2;
                int pipY = screenY - 26;
                for (int i = 0; i < maxComboStacks; i++) {
                    g2.setColor(i < visibleStacks ? art.Palette.GOLD : new Color(0, 0, 0, 150));
                    g2.fillOval(pipStartX + i * (pipSize + pipSpacing), pipY, pipSize, pipSize);
                }
            }

            int drawX = screenX;
            int drawY = screenY;
            if (anim != null) {
                drawX = screenX + (gp.tileSize - anim.width) / 2;
                drawY = screenY + (gp.tileSize - anim.height) / 2;
                float footFraction = spriteFootFraction();
                if (!Float.isNaN(footFraction)) {
                    drawY =
                            screenY
                                    + solidArea.y
                                    + solidArea.height
                                    - 2
                                    - (int) (anim.height * footFraction);
                }
            }
            drawY -= Math.round(spriteLiftPixels() + lootLift);
            if (object.ItemRegistry.typeFor(this) != null && image != null)
                art.ItemOutline.draw(g2, image, drawX, drawY, image.getWidth(), image.getHeight());
            if (highlighted) {
                if (type == type_monster && !gp.interactions.blocked())
                    drawOutline(g2, getFlashVersion(image), drawX, drawY);
                else if (gp.interactions.selected(this))
                    art.InteractionOutline.draw(g2, image, drawX, drawY);
            }
            g2.drawImage(image, drawX, drawY, null);
            changeAlpha(g2, 1f);
        }

        if (gp.keyH.showDebug) {
            drawHitbox(g2);
        }
    }

    /** Performs the change alpha operation for Entity. */
    public void changeAlpha(Graphics2D g2, float alphaValue) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaValue));
    }

    // depth-sorting key: the bottom of the collision box, so things in front draw over things
    // behind
    /** Resolves the sort y result used by Entity. */
    public int sortY() {
        return worldY + solidArea.y + solidArea.height;
    }

    /** Returns the particle color for Entity. */
    public Color getParticleColor() {
        Color color = null;
        return color;
    }

    /** Returns the particle size for Entity. */
    public int getParticleSize() {
        int size = 0;
        return size;
    }

    /** Returns the particle speed for Entity. */
    public int getParticleSpeed() {
        int speed = 0;
        return speed;
    }

    /** Returns the particle max hp for Entity. */
    public int getParticleMaxHP() {
        int maxHP = 0;
        return maxHP;
    }

    /** Performs the generate particle operation for Entity. */
    public void generateParticle(Entity generator, Entity target) {
        Color color = generator.getParticleColor();
        int size = generator.getParticleSize();
        int speed = generator.getParticleSpeed();
        int maxHP = generator.getParticleMaxHP();

        if (color == null || size <= 0 || maxHP <= 0) return;
        Particle.Style style =
                generator instanceof Projectile ? Particle.Style.SPARK : Particle.Style.CHIP;
        Particle.burst(target, style, color, 5, size * 0.65f, maxHP, speed * 2.4f);
    }

    /** NaN keeps legacy centred sprites; generated humanoids anchor their feet to the hitbox. */
    protected float spriteFootFraction() {
        return Float.NaN;
    }

    /** Returns the current 0 value. */
    protected float spriteLiftPixels() {
        return 0;
    }

    /** Returns the current 1 value. */
    protected float shadowScale() {
        return 1;
    }

    /** Returns the current animation for Entity. */
    protected SpriteAnimation getCurrentAnimation() {
        SpriteAnimation anim = sprites.get(animState + "_" + direction);
        if (anim == null) {
            anim = sprites.get("idle_" + direction);
        }
        return anim;
    }

    /** Resolves the animation frame result used by Entity. */
    protected BufferedImage animationFrame(SpriteAnimation anim) {
        return anim.frames[Math.min(spriteNum - 1, anim.frames.length - 1)];
    }

    /** Returns the current frame for Entity. */
    protected BufferedImage getCurrentFrame() {
        SpriteAnimation anim = getCurrentAnimation();
        BufferedImage frame = (anim == null) ? getLegacyFrame() : animationFrame(anim);

        if (flashing && !dying) {
            return getFlashVersion(frame);
        }

        Color tint = getActiveTintColor();
        if (tint != null) {
            return getTintVersion(frame, tint);
        }

        return frame;
    }

    /** Returns the legacy frame for Entity. */
    protected BufferedImage getLegacyFrame() {
        BufferedImage image = null;
        switch (direction) {
            case "up" -> image = (spriteNum == 1) ? up1 : up2;
            case "down" -> image = (spriteNum == 1) ? down1 : down2;
            case "left" -> image = (spriteNum == 1) ? left1 : left2;
            case "right" -> image = (spriteNum == 1) ? right1 : right2;
        }
        return image;
    }

    /** Draws the hitbox for Entity. */
    public void drawHitbox(Graphics2D g2) {
        int screenX = worldX - gp.localPlayer().worldX + gp.localPlayer().screenX + solidArea.x;
        int screenY = worldY - gp.localPlayer().worldY + gp.localPlayer().screenY + solidArea.y;

        Color color =
                switch (type) {
                    case type_monster -> Color.red;
                    case type_npc -> Color.cyan;
                    case type_player -> Color.green;
                    default -> Color.magenta;
                };

        g2.setColor(color);
        g2.drawRect(screenX, screenY, solidArea.width, solidArea.height);
    }

    /** Adds the status effect for Entity. */
    public void addStatusEffect(StatusEffect effect) {
        statusEffects.add(effect);
    }

    /** Updates the status effects for Entity. */
    public void updateStatusEffects() {
        for (int i = statusEffects.size() - 1; i >= 0; i--) {
            StatusEffect e = statusEffects.get(i);
            e.duration--;

            if (e.type.equals("bleed")) {
                e.tickCounter++;
                if (e.tickCounter >= e.tickInterval) {
                    HP -= e.value;
                    if (HP < 0) HP = 0;
                    e.tickCounter = 0;

                    if (HP <= 0 && type == type_monster) {
                        dying = true; // matches the death flag damageMonster() sets - but skips
                        // EXP/kill message, since that logic lives in Player
                        // currently
                    }
                }
            }

            if (e.type.equals(AssassinCombat.BLEED)
                    && this instanceof Monster target
                    && ++e.tickCounter >= e.tickInterval) {
                e.tickCounter = 0;
                if (e.source != null) e.source.assassinBleedTick(target, e.value);
            }

            if (e.duration <= 0) {
                statusEffects.remove(i);
            }
        }
    }

    /** Reports whether this component has status effect for Entity. */
    public boolean hasStatusEffect(String statusType) {
        for (StatusEffect e : statusEffects) {
            if (e.type.equals(statusType)) return true;
        }
        return false;
    }

    /** Returns the status effect value for Entity. */
    public int getStatusEffectValue(String statusType) {
        for (StatusEffect e : statusEffects) {
            if (e.type.equals(statusType)) return e.value;
        }
        return 0;
    }

    /** Spawns the hit particles for Entity. */
    public void spawnHitParticles(String direction) {
        Particle.impact(this, direction, null);
    }

    /** Starts the aura for Entity. */
    public void startAura(Color color, int durationTicks) {
        auraActive = true;
        auraColor = color;
        auraDurationRemaining = durationTicks;
    }

    /** Updates the aura for Entity. */
    public void updateAura() {
        if (!auraActive) return;

        auraDurationRemaining--;
        if (auraDurationRemaining <= 0) {
            auraActive = false;
            return;
        }

        auraParticleCounter++;
        if (auraParticleCounter >= auraParticleInterval) {
            spawnAuraParticle();
            auraParticleCounter = 0;
        }
    }

    /** Spawns the aura particle for Entity. */
    private void spawnAuraParticle() {
        double angle = Math.random() * 2 * Math.PI;
        int radius = gp.tileSize * 5 / 12;
        int offsetX = (int) Math.round(Math.cos(angle) * radius);
        int offsetY = (int) Math.round(Math.sin(angle) * radius);
        Color color = art.ArtKit.mix(auraColor, art.Palette.PLASTER, 0.25f);
        Particle.emit(
                this,
                Particle.Style.MOTE,
                color,
                3.6f,
                22,
                offsetX * 0.016f,
                -0.9f,
                offsetX,
                offsetY);
    }

    /** Warm common hit/selection variants during loading, rather than on the first combat event. */
    public static void preloadSpriteEffects(java.util.Map<String, SpriteAnimation> sprites) {
        for (SpriteAnimation animation : sprites.values())
            for (BufferedImage image : animation.frames) {
                flashCache.computeIfAbsent(image, SpriteAnimation::whiteFlash);
            }
    }

    /** Returns the flash version for Entity. */
    protected BufferedImage getFlashVersion(BufferedImage src) {
        if (src == null) return null;
        return flashCache.computeIfAbsent(src, SpriteAnimation::whiteFlash);
    }

    /** Reports whether this component can be knocked back for Entity. */
    public boolean canBeKnockedBack() {
        return true;
    }

    /** Starts the knockback for Entity. */
    public void startKnockback(String fromDirection, int distance) {
        if (!canBeKnockedBack()) return;
        knockbackActive = true;
        knockbackDirection = fromDirection;
        knockbackRemaining = distance;
    }

    /** Updates the knockback for Entity. */
    public void updateKnockback() {
        if (!canBeKnockedBack()) {
            knockbackActive = false;
            knockbackRemaining = 0;
            return;
        }
        if (knockbackRemaining <= 0) {
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
        if (type == type_player) {
            gp.collisionChecker.checkEntity(this, gp.monster);
            gp.collisionChecker.checkEntity(this, gp.npc);
        } else {
            gp.collisionChecker.checkEntity(this, gp.npc);
            gp.collisionChecker.checkPlayer(this);
        }

        if (!collisionOn) {
            switch (knockbackDirection) {
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

        if (knockbackRemaining <= 0) {
            knockbackActive = false;
        }
    }

    /** Reports whether this component can stack with for Entity. */
    public boolean canStackWith(Entity other) {
        return stackable && other != null && other.stackable && this.getClass() == other.getClass();
    }

    /** Returns the active tint color for Entity. */
    protected Color getActiveTintColor() {
        for (StatusEffect e : statusEffects) {
            if (e.tintColor != null) return e.tintColor;
        }
        return null;
    }

    /** Returns the tint version for Entity. */
    protected BufferedImage getTintVersion(BufferedImage src, Color tint) {
        if (src == null) return null;
        long key = ((long) System.identityHashCode(src) << 32) | (tint.getRGB() & 0xffffffffL);
        BufferedImage cached = tintCache.get(key);
        if (cached != null) return cached;
        BufferedImage tinted = SpriteAnimation.applyTint(src, tint);
        tintCache.put(key, tinted);
        return tinted;
    }

    /** Draws the shadow for Entity. */
    public void drawShadow(Graphics2D g2, int screenX, int screenY) {
        int shadowWidth = Math.round(solidArea.width * shadowScale());
        int shadowHeight = shadowWidth / 2;
        int shadowX = screenX + solidArea.x + (solidArea.width - shadowWidth) / 2;
        int shadowY = screenY + solidArea.y + solidArea.height - shadowHeight / 2;

        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillOval(shadowX, shadowY, shadowWidth, shadowHeight);
    }

    /** Draws the outline for Entity. */
    public static void drawOutline(Graphics2D g2, BufferedImage silhouette, int drawX, int drawY) {
        if (silhouette == null) return;
        int o = 2;
        int[][] offsets = {{-o, 0}, {o, 0}, {0, -o}, {0, o}, {-o, -o}, {o, -o}, {-o, o}, {o, o}};
        for (int[] off : offsets) {
            g2.drawImage(silhouette, drawX + off[0], drawY + off[1], null);
        }
    }
}
