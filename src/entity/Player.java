package entity;

import art.WeaponArt;
import art.Palette;
import main.Gamepanel;
import main.KeyHandler;
import main.UtilityTool;
import net.GameEventType;
import object.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import net.InputState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import world.WorldObject;

public class Player extends Entity{
    KeyHandler keyH;

    public int playerId;
    public String playerName = "";
    public String playerClass = "warrior";
    public final RangerCombat ranger = new RangerCombat(this);
    public final AssassinCombat assassin = new AssassinCombat(this);
    public final MageCombat mage = new MageCombat(this);
    public final PriestCombat priest = new PriestCombat(this);
    public final BerserkerCombat berserker = new BerserkerCombat(this);
    public final PaladinCombat paladin = new PaladinCombat(this);
    public String gender = "male";
    public int hairStyle=-1,hairColor=-1;

    public boolean isLocal = true; // remote players (Step 3+) will explicitly set this false
    public int dialogueNpcIndex = -1;
    public int dialoguePage = 0;
    public boolean inDialogue = false;
    public boolean inWell;
    public int resolve;
    public final int maxResolve=5;
    public boolean merchantOpen;
    public long shopChangedInputTick;
    public String shopMessage="";
    public String dialogueText = "";
    public String dialogueTag = "";
    private java.util.List<String> sessionDialogue = java.util.List.of();
    private int dialogueRevealCounter = 0;
    private final int dialogueRevealSpeed = 2; // ticks per revealed character — lower = faster typing
    public boolean isDead = false;

    public InputState currentInput;
    private ArrayDeque<InputState> inputHistory = new ArrayDeque<>();
    private final int inputHistoryCapacity = 128; // ~2s at 60fps — this isn't consumed yet
    public long lastProcessedInputTick = 0;
    public int previousEventX = -9999, previousEventY = -9999;
    public boolean canTouchEvent = true;

    public int screenX;
    public int screenY;

    public boolean attackCanceled = false;
    private final int attackWindupFrameDelay = 2;   // slower hold on anticipation frames — gives the hit time to be read/reacted to
    private final int attackActiveFrameDelay = 2;   // fast during the actual hit window — makes the strike itself feel sharp
    private final int attackRecoveryFrameDelay = 4; // frames after the hit window, before returning to idle/run
    private final int attackHitStartFrame = 4;
    private final int attackHitEndFrame = 6;

    private int attackFrameHoldCounter = 0;
    private boolean attackBuffered = false;
    private int attackBufferTicks;
    private enum DaggerCut { FIRST, SECOND }
    private CombatHit swingHit,firstCutHit,secondCutHit;
    private final ArrayList<Entity> firstDaggerTargets = new ArrayList<>();

    public Entity leftHandItem;
    public Entity rightHandItem;

    private int bounceCounter = 0;
    private static final int TRAIL_START_DELAY = 8;
    private int trailCounter = TRAIL_START_DELAY;
    private final int hitStopOnHitMonster = 3;
    private final int hitStopOnTakingDamage = 5;
    private int bodySize; // canvas size shared by every player body animation (idle/run/attack)
    public Entity hoveredTarget = null;
    private boolean mageProjectileFired = false;
    private int mageAimX, mageAimY;

    public int arcaneCharge = 0;
    public final int maxArcaneCharge = 4;

    public final inventory.Hotbar hotbar=new inventory.Hotbar();
    public final crafting.CraftingProgression craftingProgress=new crafting.CraftingProgression();
    public int meleeMasteryXP,magicMasteryXP,bowMasteryXP;
    public Entity[] inventorySlots = new Entity[20];
    public final int inventorySize = 20;

    public static final int EQUIP_WEAPON = 1;
    public static final int EQUIP_SHIELD = 2;

    public static final int EQUIP_HAIR = 3, EQUIP_TORSO = 4, EQUIP_PANTS = 5,
            EQUIP_BOOTS = 6, EQUIP_GLOVES = 7, EQUIP_CLOAK = 8, EQUIP_HEADPIECE = 9;
    public final Entity[] apparelSlots = new Entity[7];

    //SKILL PARAMETERS
    public Skill[] skills = new Skill[5];
    private boolean powerStrikePending = false;
    private int powerStrikeBonus = 0;
    private boolean armorBreakPending;
    private int ticksSinceSkillUse = 0;
    private final int mpRegenDelayTicks = 120;   // ticks after last skill use before MP starts regenerating
    private final int mpRegenIntervalTicks = 90; // ticks between each +1 MP once regen is active
    private int mpRegenCounter = 0;
    private int ticksSinceRageGain = 0;
    private final int rageDecayDelayTicks = 180;   // ~3s of no hits/damage before rage starts dropping
    private final int rageDecayIntervalTicks = 60; // ticks between each -1 rage once decaying
    private int rageDecayCounter = 0;
    public Map<String, BufferedImage> statusEffectIcons = new HashMap<>();

    public Player(Gamepanel gp, KeyHandler keyH){
        super(gp);invincibilityDuration=60;
        this.keyH = keyH;
        currentInput = new InputState();

        screenX = gp.screenWidth/2 - (gp.tileSize/2);
        screenY = gp.screenHeight/2 - (gp.tileSize/2);

        solidArea = new Rectangle();
        solidArea.x = 8;
        solidArea.y = 20;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;
        solidArea.width = 32;
        solidArea.height = 32;

        applyStarterAppearance();
        setDefaultValues();
        if(usesMana()){
            getMagePlayerImage();
        } else {
            getPlayerImage();
        }
        refreshWeaponAnimation();
        setItems();
        getPlayerAttackImage();
        setItems();
    }

    public void setDefaultValues(){
        weaponSkills.clear();
        type = type_player;
        setDefaultPosition();
        speed = 4;
        direction = "down";

        //PLAYER STATS
        level = 1;
        exp = 0;
        nextLevelExp = 30;
        coin = 0;

        maxRage=usesMana() || isPaladin()?0:100;rage=0;arcaneCharge=0;resolve=0;
        currentWeapon=new OBJ_fists(gp);rightHandItem=currentWeapon;currentShield=new OBJ_no_shield(gp);

        applyBaseStats();
        attack = getAttack();
        defense = getDefense();
    }

    /** Level-1 HP / MP / attributes from the class table; torso gear is added on top. */
    private void applyBaseStats(){
        ClassStats table = ClassStats.of(playerClass);
        Entity torso = equippedItem(EQUIP_TORSO);
        maxHP = table.startHP + outfitHealth(torso);
        HP = maxHP;
        maxMP = table.startMP + outfitMana(torso);
        MP = maxMP;
        strength = table.startStat(ClassStats.Primary.STR);
        dexterity = table.startStat(ClassStats.Primary.DEX);
        intelligence = table.startStat(ClassStats.Primary.INT);
        vitality = ClassStats.START_VIT;
    }

    /** The attribute this class's abilities and weapon damage scale with. */
    public int primaryStat(){
        return switch(ClassStats.of(playerClass).primary){
            case STR -> strength;
            case DEX -> dexterity;
            case INT -> intelligence;
        };
    }

    public void setDefaultPosition(){
        worldX = gp.tileSize * (gp.generatedWorld == null ? 25 : gp.generatedWorld.spawnCol);
        worldY = gp.tileSize * (gp.generatedWorld == null ? 28 : gp.generatedWorld.spawnRow);
        direction = "down";
    }

    public void resetHPandMP(){
        HP = maxHP;
        MP = maxMP;
        invincible = false;
    }

    public void setItems(){
        java.util.Arrays.fill(inventorySlots, null);
        setupSkills();
    }

    public boolean isPaladin(){return playerClass.equals("paladin");}
    boolean hasPaladinSword(){return currentWeapon instanceof OBJ_paladinGear && currentWeapon.type==type_sword;}

    public boolean isBerserker(){return playerClass.equals("berserker");}
    boolean hasGreatsword(){return isBerserker() && currentWeapon instanceof OBJ_greatsword;}

    public boolean isMage(){return playerClass.equals("mage");}
    public boolean isPriest(){return playerClass.equals("priest");}
    public boolean usesMana(){return isMage() || isPriest();}
    public boolean hasStaff(){return usesMana() && currentWeapon!=null && !currentWeapon.isPlaceholder && currentWeapon.type==type_staff;}

    public boolean isRanger(){return playerClass.equals("ranger");}
    boolean hasBow(){return isRanger() && currentWeapon instanceof OBJ_rangerbow;}
    public boolean isAssassin(){return playerClass.equals("hunter");}
    boolean hasDaggers(){return isAssassin() && weaponKind().isDaggers();}
    private boolean usesFocus(){return isRanger() || isAssassin();}
    public String resourceName(){return usesMana()?"MP":isPaladin()?"Resolve":usesFocus()?"Focus":"Rage";}
    public int skillResource(){return usesMana()?MP:isPaladin()?resolve:rage;}
    public Color resourceBarColor(){return usesFocus()?new Color(235,198,60):new Color(220,60,30);}
    public Color resourceBarBackground(){return usesFocus()?new Color(60,49,12):new Color(50,15,15);}

    private static art.RangerGear rangerOutfit(Entity item){
        return item instanceof OBJ_apparel apparel?apparel.rangerGear:null;
    }

    private static int rangerHealth(Entity item){
        var gear=rangerOutfit(item);
        return gear==null?0:gear.health;
    }

    private static art.AssassinGear assassinOutfit(Entity item){
        return item instanceof OBJ_apparel apparel?apparel.assassinGear:null;
    }

    private static art.MageGear mageOutfit(Entity item){
        return item instanceof OBJ_apparel apparel?apparel.mageGear:null;
    }

    private static art.PriestGear priestOutfit(Entity item){
        return item instanceof OBJ_apparel apparel?apparel.priestGear:null;
    }

    private static int outfitMana(Entity item){
        var priest=priestOutfit(item);
        if(priest!=null)return priest.mana;
        var gear=mageOutfit(item);
        return gear==null?0:gear.mana;
    }

    private static art.BerserkerGear berserkerOutfit(Entity item){
        return item instanceof OBJ_apparel apparel?apparel.berserkerGear:null;
    }

    private static art.PaladinGear paladinOutfit(Entity item){
        return item instanceof OBJ_apparel apparel?apparel.paladinGear:null;
    }

    private static int outfitHealth(Entity item){
        var plate=paladinOutfit(item);
        if(plate!=null)return plate.health;
        var armor=berserkerOutfit(item);
        if(armor!=null)return armor.health;
        var priest=priestOutfit(item);
        if(priest!=null)return priest.health;
        var robe=mageOutfit(item);
        if(robe!=null)return robe.health;
        var gear=assassinOutfit(item);
        return gear==null?rangerHealth(item):gear.health;
    }

    public int getDefense() {
        if(isPaladin()){
            var plate=paladinOutfit(equippedItem(EQUIP_TORSO));
            return defense=(plate==null?0:plate.armor)+(currentShield==null?0:currentShield.defenseValue);
        }
        if(isBerserker()){
            var gear=berserkerOutfit(equippedItem(EQUIP_TORSO));
            return defense=gear==null?0:gear.armor;
        }
        if(isPriest()){
            var gear=priestOutfit(equippedItem(EQUIP_TORSO));
            return defense=gear==null?0:gear.armor;
        }
        if(usesMana()){
            var gear=mageOutfit(equippedItem(EQUIP_TORSO));
            return defense=gear==null?0:gear.armor;
        }
        if(isAssassin()){
            var gear=assassinOutfit(equippedItem(EQUIP_TORSO));
            return defense=gear==null?0:gear.armor;
        }
        if(isRanger()){
            var gear=rangerOutfit(equippedItem(EQUIP_TORSO));
            return defense=gear==null?0:gear.armor;
        }
        return defense = dexterity * currentShield.defenseValue;
    }

    public int getAttack() {
        attackArea = currentWeapon.attackArea;
        if(currentWeapon instanceof OBJ_crafted c)return attack=c.attackValue+(c.type==type_staff?intelligence:c.type==type_bow?dexterity:strength);
        if(isPaladin())return attack=hasPaladinSword()?strength+currentWeapon.attackValue:0;
        if(isBerserker())return attack=hasGreatsword()?strength+currentWeapon.attackValue:0;
        if(isRanger())return attack=hasBow()?dexterity+currentWeapon.attackValue:0;
        if(isAssassin() && hasDaggers()){
            int power=currentWeapon instanceof OBJ_rewardweapon reward?(reward.kind.isHell()?13:9):currentWeapon.attackValue;
            return attack=dexterity+power;
        }
        if(usesMana()){
            if(!hasStaff())return attack=0;
            int power=currentWeapon instanceof OBJ_rewardweapon reward?(reward.kind.isHell()?12:7):currentWeapon.attackValue;
            return attack=intelligence+power;
        }
        return attack=strength*currentWeapon.attackValue;
    }

    public void getPlayerImage(){
        if(USE_GENERATED_BODY){buildGeneratedBody(); return;}
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

    public static final boolean USE_GENERATED_BODY = true;
    private final art.CharacterArt.Look bodyLook = new art.CharacterArt.Look();

    private BodyKey builtBodyKey;
    private record BodyKey(art.WeaponArt.Kind weapon, art.RangerGear bow, art.RangerGear rangerOutfit, art.AssassinGear assassinOutfit, art.MageGear mageOutfit, art.PriestGear priestOutfit, art.BerserkerGear berserkerOutfit, art.PaladinGear paladinOutfit, art.PaladinGear paladinShield, int shield, boolean pixel, Color skin, Color eye,
                           Color blush, art.CharacterOutfit outfit) {}
    // Bounded because independent apparel slots allow many outfit combinations.
    private static final java.util.Map<BodyKey, java.util.Map<String, SpriteAnimation>> BODY_CACHE =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                protected boolean removeEldestEntry(java.util.Map.Entry<BodyKey, java.util.Map<String, SpriteAnimation>> e) {
                    return size() > 16;
                }
            };

    public void setCharacterDesign(String classId, String genderId){
        hairStyle=-1;hairColor=-1;
        playerClass=CharacterClass.fromId(classId).id;
        gender="female".equals(genderId)?"female":"male";
        applyStarterAppearance();
        refreshWeaponAnimation();
    }

    private final java.util.Map<String,Skill[]> weaponSkills=new java.util.HashMap<>();

    public void applySyncedClass(String classId){
        String id=CharacterClass.fromId(classId).id;
        if(playerClass.equals(id))return;
        weaponSkills.put(playerClass,skills);
        playerClass=id;
        ranger.cancelCast();assassin.cancel();mage.cancel();berserker.cancel();paladin.cancel();
        Skill[] cached=weaponSkills.get(id);
        skills=new Skill[5];setupSkills();
        if(cached!=null)for(int i=0;i<skills.length;i++)if(skills[i]!=null && cached[i]!=null)skills[i].cooldownRemaining=cached[i].cooldownRemaining;
    }

    /** Starter wardrobe for the chosen class and preset. */
    private void applyStarterAppearance(){
        boolean female="female".equals(gender);
        ItemType[] items=switch(CharacterClass.fromId(playerClass)){
            case RANGER -> new ItemType[]{female?ItemType.LONG_HAIR:ItemType.HAIR,
                    ItemType.TRAIL_SCOUT_OUTFIT,ItemType.PANTS,ItemType.BOOTS,ItemType.GLOVES,ItemType.CLOAK};
            case HUNTER -> new ItemType[]{female?ItemType.LONG_HAIR:ItemType.HAIR,
                    ItemType.ALLEY_SCOUT_OUTFIT,ItemType.PANTS,ItemType.BOOTS,ItemType.GLOVES,ItemType.CLOAK};
            case PRIEST -> new ItemType[]{female?ItemType.MAGE_LONG_HAIR:ItemType.MAGE_HAIR,
                    ItemType.WAYFARER_ROBE,ItemType.MAGE_PANTS,ItemType.MAGE_BOOTS,ItemType.MAGE_GLOVES,ItemType.MAGE_CLOAK};
            case MAGE -> new ItemType[]{female?ItemType.MAGE_LONG_HAIR:ItemType.MAGE_HAIR,
                    ItemType.HEARTH_ROBE,ItemType.MAGE_PANTS,ItemType.MAGE_BOOTS,ItemType.MAGE_GLOVES,ItemType.MAGE_CLOAK};
            case PALADIN -> new ItemType[]{female?ItemType.WARRIOR_LONG_HAIR:ItemType.WARRIOR_HAIR,
                    ItemType.GATE_SENTINEL_ARMOR,ItemType.WARRIOR_PANTS,ItemType.WARRIOR_BOOTS,ItemType.WARRIOR_GLOVES,ItemType.WARRIOR_CLOAK};
            case BERSERKER -> new ItemType[]{female?ItemType.WARRIOR_LONG_HAIR:ItemType.WARRIOR_HAIR,
                    ItemType.ROAD_REAVER_ARMOR,ItemType.WARRIOR_PANTS,ItemType.WARRIOR_BOOTS,ItemType.WARRIOR_GLOVES,ItemType.WARRIOR_CLOAK};
            case WARRIOR -> new ItemType[]{female?ItemType.WARRIOR_LONG_HAIR:ItemType.WARRIOR_HAIR,
                    ItemType.WARRIOR_ARMOR,ItemType.WARRIOR_PANTS,ItemType.WARRIOR_BOOTS,ItemType.WARRIOR_GLOVES,ItemType.WARRIOR_CLOAK};
        };
        for(int i=0;i<items.length;i++)apparelSlots[i]=ItemRegistry.create(items[i],gp);
        apparelSlots[6]=null;
    }

    public art.CharacterOutfit currentOutfit(){
        Color[] colors=new Color[7];
        art.CharacterOutfit.Theme[] themes=new art.CharacterOutfit.Theme[6];
        java.util.Arrays.fill(themes,art.CharacterOutfit.Theme.LEATHER);
        boolean longHair=false;
        for(int i=0;i<colors.length;i++)if(apparelSlots[i] instanceof OBJ_apparel item){
            colors[i]=item.color;if(i<themes.length)themes[i]=item.theme;
            if(i==0)longHair=item.longHair;
        }
        var rangerGear=rangerOutfit(equippedItem(EQUIP_TORSO));
        if(rangerGear!=null){
            if(colors[2]!=null)colors[2]=rangerGear.pants();
            if(colors[3]!=null)colors[3]=rangerGear.boots();
            if(colors[4]!=null)colors[4]=rangerGear.gloves();
            if(colors[5]!=null)colors[5]=rangerGear.cloak;
        }
        var assassinGear=assassinOutfit(equippedItem(EQUIP_TORSO));
        if(assassinGear!=null){
            if(colors[2]!=null)colors[2]=assassinGear.pants();
            if(colors[3]!=null)colors[3]=assassinGear.boots();
            if(colors[4]!=null)colors[4]=assassinGear.gloves();
            if(colors[5]!=null)colors[5]=assassinGear.cloak;
        }
        var paladinGear=paladinOutfit(equippedItem(EQUIP_TORSO));
        if(paladinGear!=null){
            if(colors[2]!=null)colors[2]=paladinGear.pants();
            if(colors[3]!=null)colors[3]=paladinGear.boots();
            if(colors[4]!=null)colors[4]=paladinGear.gloves();
            if(colors[5]!=null)colors[5]=paladinGear.mantle();
        }
        var berserkerGear=berserkerOutfit(equippedItem(EQUIP_TORSO));
        if(berserkerGear!=null){
            if(colors[2]!=null)colors[2]=berserkerGear.pants();
            if(colors[3]!=null)colors[3]=berserkerGear.boots();
            if(colors[4]!=null)colors[4]=berserkerGear.gloves();
            if(colors[5]!=null)colors[5]=berserkerGear.scarf;
        }
        var priestGear=priestOutfit(equippedItem(EQUIP_TORSO));
        if(priestGear!=null){
            if(colors[2]!=null)colors[2]=priestGear.pants();
            if(colors[3]!=null)colors[3]=priestGear.boots();
            if(colors[4]!=null)colors[4]=priestGear.gloves();
            if(colors[5]!=null)colors[5]=priestGear.mantle;
        }
        var mageGear=mageOutfit(equippedItem(EQUIP_TORSO));
        if(mageGear!=null){
            if(colors[2]!=null)colors[2]=mageGear.pants();
            if(colors[3]!=null)colors[3]=mageGear.boots();
            if(colors[4]!=null)colors[4]=mageGear.gloves();
            if(colors[5]!=null)colors[5]=mageGear.mantle;
        }
        art.CharacterOutfit outfit=new art.CharacterOutfit(colors[0],colors[1],colors[2],colors[3],colors[4],colors[5],
                new art.CharacterOutfit.Details(themes[0],themes[1],themes[2],themes[3],themes[4],themes[5],longHair),colors[6]);
        return art.Hairstyles.valid(hairStyle)&&art.Hairstyles.validColor(hairColor)?art.Hairstyles.outfit(outfit,hairStyle,hairColor):outfit;
    }

    public void customizeHair(int style,int color){
        if(!art.Hairstyles.valid(style)||!art.Hairstyles.validColor(color))throw new IllegalArgumentException("Unknown hairstyle or hair color");
        hairStyle=style;hairColor=color;refreshWeaponAnimation();
    }

    private art.WeaponArt.Kind weaponKind(){
        if(currentWeapon == null || currentWeapon.isPlaceholder) return art.WeaponArt.Kind.NONE; // fists
        if(currentWeapon instanceof OBJ_dualdaggers daggers)return daggers.kind;
        if(currentWeapon instanceof OBJ_prieststaff staff)return staff.kind;
        if(currentWeapon instanceof OBJ_paladinGear sword)return art.WeaponArt.Kind.forPaladin(sword.gear);
        if(currentWeapon instanceof OBJ_greatsword sword)return sword.kind;
        if(currentWeapon instanceof OBJ_staff staff)return staff.kind;
        if(currentWeapon instanceof OBJ_rewardweapon reward)return reward.kind;
        return switch(currentWeapon.type){
            case type_sword -> art.WeaponArt.Kind.SWORD;
            case type_axe -> art.WeaponArt.Kind.AXE;
            case type_staff -> art.WeaponArt.Kind.STAFF;
            default -> art.WeaponArt.Kind.NONE;
        };
    }

    private void buildGeneratedBody(){
        art.CharacterOutfit outfit = currentOutfit();
        BodyKey key = new BodyKey(weaponKind(),currentWeapon instanceof OBJ_rangerbow bow?bow.gear:null,
                rangerOutfit(equippedItem(EQUIP_TORSO)),assassinOutfit(equippedItem(EQUIP_TORSO)),mageOutfit(equippedItem(EQUIP_TORSO)),priestOutfit(equippedItem(EQUIP_TORSO)),berserkerOutfit(equippedItem(EQUIP_TORSO)),paladinOutfit(equippedItem(EQUIP_TORSO)),currentShield instanceof OBJ_paladinGear shield?shield.gear:null,currentShield==null || currentShield.isPlaceholder?0:currentShield instanceof OBJ_blueshield?2:1, CharacterSprites.pixelMode, bodyLook.skin,
                bodyLook.eye, bodyLook.blush, outfit);
        if(key.equals(builtBodyKey) && sprites.containsKey("idle_down")) return;
        java.util.Map<String, SpriteAnimation> generated;
        synchronized(BODY_CACHE){
            generated = BODY_CACHE.get(key);
            if(generated == null){
                art.CharacterArt.Look look = new art.CharacterArt.Look();
                look.skin=key.skin();look.eye=key.eye();look.blush=key.blush();
                look.weapon=key.weapon();look.rangerBow=key.bow();look.rangerOutfit=key.rangerOutfit();look.assassinOutfit=key.assassinOutfit();look.mageOutfit=key.mageOutfit();look.priestOutfit=key.priestOutfit();look.berserkerOutfit=key.berserkerOutfit();look.paladinOutfit=key.paladinOutfit();look.paladinShield=key.paladinShield();look.shieldStyle=key.shield();look.outfit=outfit;
                generated = new java.util.HashMap<>();
                CharacterSprites.build(generated, look);
                BODY_CACHE.put(key, generated);
            }
        }
        bodySize=CharacterSprites.canvasSize();
        sprites.putAll(generated);
        down1=sprites.get("idle_down").frames[0];
        builtBodyKey=key;
    }

    public BufferedImage portraitFrame(){return getCurrentFrame();}

    public Entity equippedItem(int slot){
        if(slot==EQUIP_WEAPON) return currentWeapon;
        if(slot==EQUIP_SHIELD) return currentShield;
        return slot>=EQUIP_HAIR && slot<=EQUIP_HEADPIECE ? apparelSlots[slot-EQUIP_HAIR] : null;
    }

    private boolean fitsEquipment(Entity item, int slot){
        if(item==null)return false;
        if(item instanceof OBJ_crafted crafted)return slot==EQUIP_WEAPON && crafted.definition.equipment();
        if(item instanceof OBJ_paladinGear gear)
            return level>=gear.gear.level && (slot==EQUIP_WEAPON && gear.type==type_sword || slot==EQUIP_SHIELD && gear.type==type_shield);
        if(slot==EQUIP_WEAPON){
            if(item instanceof OBJ_greatsword sword)return level>=sword.gear.level;
            if(item instanceof OBJ_rangerbow bow)return level>=bow.gear.level;
            if(item instanceof OBJ_dualdaggers daggers)return level>=daggers.gear.level;
            if(item instanceof OBJ_prieststaff staff)return level>=staff.gear.level;
            if(item instanceof OBJ_staff staff)return level>=staff.gear.level;
            return inventory.Hotbar.isWeapon(item);
        }
        if(slot==EQUIP_SHIELD)return item.type==type_shield;
        var gear=rangerOutfit(item);if(gear!=null && level<gear.level)return false;
        var leathers=assassinOutfit(item);if(leathers!=null && level<leathers.level)return false;
        var robe=mageOutfit(item);if(robe!=null && level<robe.level)return false;
        var plate=paladinOutfit(item);if(plate!=null && level<plate.level)return false;
        var armor=berserkerOutfit(item);if(armor!=null && level<armor.level)return false;
        var vestment=priestOutfit(item);if(vestment!=null && level<vestment.level)return false;
        return item instanceof OBJ_apparel clothing && slot==EQUIP_HAIR+clothing.slot.ordinal();
    }

    /** Legacy class IDs now select existing weapon combat kits, never character eligibility or stats. */
    public void syncWeaponProfile(){
        String profile=currentWeapon instanceof OBJ_prieststaff?"priest":
            currentWeapon instanceof OBJ_paladinGear?"paladin":
            currentWeapon instanceof OBJ_greatsword?"berserker":
            currentWeapon instanceof OBJ_dualdaggers || currentWeapon instanceof OBJ_rewardweapon r && r.kind.isDaggers()?"hunter":
            currentWeapon!=null && currentWeapon.type==type_bow?"ranger":
            currentWeapon!=null && currentWeapon.type==type_staff?"mage":"warrior";
        applySyncedClass(profile);
        if(!usesMana() && !isPaladin() && maxRage==0)maxRage=100; // Existing non-mana kit capacity, including legacy caster saves.
    }

    private void setEquipment(int slot, Entity item){
        if(slot==EQUIP_WEAPON){currentWeapon=item!=null?item:new OBJ_fists(gp);syncWeaponProfile();attack=getAttack();defense=getDefense();firstDaggerTargets.clear();ranger.cancelCast();assassin.cancel();mage.cancel();berserker.cancel();paladin.cancel();}
        else if(slot==EQUIP_SHIELD){currentShield=item!=null?item:new OBJ_no_shield(gp);defense=getDefense();paladin.cancel();}
        else if(slot>=EQUIP_HAIR && slot<=EQUIP_HEADPIECE){
            if(slot==EQUIP_TORSO){
                Entity previous=equippedItem(slot);
                maxHP+=outfitHealth(item)-outfitHealth(previous);
                HP=Math.min(HP,maxHP);
                maxMP+=outfitMana(item)-outfitMana(previous);
                MP=Math.min(MP,maxMP);
            }
            apparelSlots[slot-EQUIP_HAIR]=item;
            if(usesFocus() || usesMana() || isBerserker() || isPaladin())defense=getDefense();
        }
        refreshWeaponAnimation();
    }

    public void getPlayerAttackImage(){
        if(USE_GENERATED_BODY){ buildGeneratedBody(); return; }
        if(usesMana()) return;
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

    public void getMagePlayerImage(){
        if(USE_GENERATED_BODY){buildGeneratedBody(); return;}
        int size = bodySize > 0 ? bodySize : (int)(gp.tileSize * 3);
        bodySize = size;

        BufferedImage[] idleDown = {
                setup("/player/mage_idle/00_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/01_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/02_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/03_Vampires1_Idle_without_shadow", size, size)};
        BufferedImage[] idleUp = {
                setup("/player/mage_idle/04_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/05_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/06_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/07_Vampires1_Idle_without_shadow", size, size)};
        BufferedImage[] idleLeft = {
                setup("/player/mage_idle/08_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/09_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/10_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/11_Vampires1_Idle_without_shadow", size, size)};
        BufferedImage[] idleRight = {
                setup("/player/mage_idle/12_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/13_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/14_Vampires1_Idle_without_shadow", size, size),
                setup("/player/mage_idle/15_Vampires1_Idle_without_shadow", size, size)};

        sprites.put("idle_down", new SpriteAnimation(idleDown, size, size));
        sprites.put("idle_up", new SpriteAnimation(idleUp, size, size));
        sprites.put("idle_left", new SpriteAnimation(idleLeft, size, size));
        sprites.put("idle_right", new SpriteAnimation(idleRight, size, size));

        BufferedImage[] runDown = {
                setup("/player/mage_run/00_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/01_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/02_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/03_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/04_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/05_Vampires1_Walk_without_shadow", size, size)};
        BufferedImage[] runUp = {
                setup("/player/mage_run/06_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/07_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/08_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/09_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/10_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/11_Vampires1_Walk_without_shadow", size, size)};
        BufferedImage[] runLeft = {
                setup("/player/mage_run/12_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/13_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/14_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/15_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/16_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/17_Vampires1_Walk_without_shadow", size, size)};
        BufferedImage[] runRight = {
                setup("/player/mage_run/18_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/19_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/20_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/21_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/22_Vampires1_Walk_without_shadow", size, size),
                setup("/player/mage_run/23_Vampires1_Walk_without_shadow", size, size)};

        SpriteAnimation runDownAnim = new SpriteAnimation(runDown, size, size);
        runDownAnim.frameDelay = 5;
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

        down1 = idleDown[0];
    }

    public void update(){
        if(currentInput!=null && (currentInput.gameplaySuppressed || isDead || merchantOpen || inDialogue || gp.quests.isBossIntro())){
            attackBuffered=false;attackBufferTicks=0;
        }else if(attackBuffered && gp.hitStopCounter<=0 && --attackBufferTicks<=0)attackBuffered=false;
        if(!isLocal && !gp.isHost){
            updateAura(); // effects come from the snapshot
            if(isRanger())ranger.advanceVisual();
            if(isBerserker())berserker.advanceReplica();
            if(isPaladin())paladin.advanceReplica();
            if(!isDead){
                boolean swordSkill=isBerserker() && berserker.active() || isPaladin() && paladin.active();
                if(!swordSkill && !assassin.active() && !"attack".equals(animState)){
                    updateAnimation();
                }
            }
            return;
        }

        if(isDead){
            ranger.cancelCast();assassin.reset();mage.cancel();berserker.cancel();paladin.reset();priest.reset();
            merchantOpen=false;
            if(!isPredictedClient()) updateStatusEffects();
            return;
        }

        if(gp.isHost && currentInput != null){
            int idx = currentInput.targetedMonsterIndex;
            hoveredTarget = (idx >= 0 && idx < gp.monster.length) ? gp.monster[idx] : null;
        }

        if(isPaladin())paladin.tickTimers();

        if(!isPredictedClient() && (merchantOpen || inDialogue || gp.quests.isBossIntro()))PriestCombat.tickDuringInteraction(this);

        if(merchantOpen){moving=false;attacking=false;ranger.cancelCast();assassin.cancel();mage.cancel();berserker.cancel();paladin.cancel();return;}

        if(inDialogue){
            ranger.cancelCast();assassin.cancel();mage.cancel();berserker.cancel();paladin.cancel();
            updateDialogueReveal();
            interactNPC(999); // only checks for a close-via-E while talking — index 999 means "no NPC collision
            return; // freezes only THIS player's movement/attack — never touches gp.gameState, so nobody else is affected
        }

        if(gp.quests.isBossIntro()){
            moving=false;attacking=false;animState="idle";ranger.cancelCast();assassin.cancel();mage.cancel();berserker.cancel();paladin.cancel();return;
        }

        if(isLocal && gp.inventoryOpen){
            ranger.cancelCast();assassin.cancel();mage.cancel();berserker.cancel();paladin.cancel();
            if(!isPredictedClient()) updateStatusEffects();
            return;
        }

        boolean movingHoriz = currentInput.left || currentInput.right;
        boolean movingVert = currentInput.up || currentInput.down;
        moving = movingHoriz || movingVert;
        String horizDir = null;
        String vertDir = null;

        if(moving){
            bounceCounter++;
        }
        else{
            bounceCounter = 0;
        }

        if (currentInput.left) {
            horizDir = "left";
        }
        else if(currentInput.right){
            horizDir = "right";
        }

        if(currentInput.up){
            vertDir = "up";
        }
        else if(currentInput.down){
            vertDir = "down";
        }

        if(!attacking && !berserker.active() && !paladin.active()){
            direction = (vertDir != null) ? vertDir : direction;
            direction = (horizDir != null) ? horizDir : direction;
        }

        animState = attacking ? "attack" : (moving ? "run" : "idle");

        if(!isPredictedClient()){
            updateStatusEffects();
        }
        updateAura();
        updateDroppedItemPickupBlocks();
        if(isPaladin())paladin.setGuard(currentInput.rightHeld && !currentInput.gameplaySuppressed);

        if(!isPredictedClient()){
            for(var entry:weaponSkills.entrySet())if(!entry.getKey().equals(playerClass))for(Skill skill:entry.getValue())if(skill!=null)skill.update();
            for(int i = 0; i < skills.length; i++){
                if(skills[i] != null){
                    skills[i].update();
                    if(currentInput.skillKeyPressed[i]){
                        if(skills[i].isReady() && hasEnoughResource(skills[i].resourceCost) && (!isRanger() || ranger.canUse(i)) && (!isAssassin() || assassin.canUse(i)) && (!isMage() || mage.canUse(i)) && (!isPriest() || priest.canUse(i)) && (!isBerserker() || berserker.canUse()) && (!isPaladin() || paladin.canUse(i)) && (!playerClass.equals("warrior") || hasShield() || (i!=0 && i!=2))){
                            spendResource(skills[i].resourceCost);
                            skills[i].trigger();
                        }
                        currentInput.skillKeyPressed[i] = false;
                    }
                }
            }
        }

        currentInput.movementLocked=false;
        currentInput.heavyMovement=false;
        if(isPaladin()){
            boolean wasActing=paladin.active();
            paladin.update();
            currentInput.heavyMovement=paladin.guarding();
            currentInput.movementLocked=wasActing;
            if(currentInput.movementLocked){moving=false;bounceCounter=0;}
        }
        if(isBerserker()){
            boolean wasActing=berserker.active();
            boolean canMove=berserker.canMove();
            berserker.update();
            currentInput.heavyMovement=canMove && berserker.active();
            currentInput.movementLocked=wasActing && !currentInput.heavyMovement;
            if(currentInput.movementLocked){moving=false;bounceCounter=0;}
        }
        if(isRanger()){
            boolean wasLeaping=ranger.hopAge()>0;
            ranger.update();
            currentInput.movementLocked=wasLeaping || ranger.braced();
            if(currentInput.movementLocked){moving=false;bounceCounter=0;}
        }
        if(isAssassin()){
            boolean wasUsingSkill=assassin.active();
            assassin.update();
            currentInput.movementLocked=wasUsingSkill || assassin.active();
            if(currentInput.movementLocked){moving=false;bounceCounter=0;}
        }
        if(usesMana()){
            boolean wasCasting=mage.active();
            mage.update();
            currentInput.movementLocked=wasCasting || mage.active();
            if(currentInput.movementLocked){moving=false;bounceCounter=0;}
        }
        if(!attacking){
            if(gp.hitStopCounter <= 0){
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
        }

        if(!moving || attacking || knockbackActive) trailCounter = TRAIL_START_DELAY;

        if(knockbackActive){
            updateKnockback();
        }
        else if(attacking){
            if(currentInput.leftClicked){
                attackBuffered = true;
                attackBufferTicks = 15; // A quarter-second window for the next deliberate swing.
                if(isLocal){
                    gp.mouseH.leftClicked = false;
                }
                currentInput.leftClicked = false;
            }
            attacking();
        }
        else if(moving || currentInput.ePressed || currentInput.leftClicked || attackBuffered){

                //CHECK TILE COLLISION
                collisionOn = false;
                gp.collisionChecker.checkTile(this);

                //CHECK OBJECT COLLISION
                gp.collisionChecker.checkObject(this, true); // Physical collision only; pickup uses the directional resolver.

                //CHECK NPC COLLISION
                int npcIndex = gp.collisionChecker.checkEntity(this, gp.npc);
                if(inDialogue)interactNPC(npcIndex);
                int sceneVersion=gp.quests.areaVersion();
                int beforeInteractX=worldX, beforeInteractY=worldY;
                interactScenery();
                if(sceneVersion!=gp.quests.areaVersion() || worldX!=beforeInteractX || worldY!=beforeInteractY)return;

                //CHECK MONSTER COLLISION
                int monsterIndex = gp.collisionChecker.checkEntity(this, gp.monster);
                contactMonster(monsterIndex);

                //CHECK INTERACTABLES
                int interactableIndex = gp.collisionChecker.checkEntity(this, gp.interactable);

                //CHECK EVENT
                gp.eventHandler.checkEvent(this);

                int startX = worldX, startY = worldY;
                //IF COLLISION IS FALSE, PLAYER CAN MOVE
                if(!currentInput.ePressed && !currentInput.movementLocked){
                    currentInput.chorusSpeed=hasStatusEffect(PriestCombat.CHORUS);
                    moveForInput(horizDir,vertDir,currentInput.chorusSpeed,currentInput.heavyMovement,currentInput.tick);
                }

                if((!isRanger() || currentWeapon instanceof OBJ_crafted) && !currentInput.movementLocked && (currentInput.ePressed && !usesMana() || currentInput.leftClicked || attackBuffered) && !attackCanceled
                        && (currentWeapon instanceof OBJ_crafted || !usesMana() || hasStaff()) && (currentWeapon instanceof OBJ_crafted || !isBerserker() || hasGreatsword() && !berserker.active())
                        && (currentWeapon instanceof OBJ_crafted || !isPaladin() || hasPaladinSword() && !paladin.active() && !paladin.guarding())){
                    attacking = true;
                    animState = "attack";
                    spriteNum = 1;
                    attackFrameHoldCounter = 0;
                    swingHit=new CombatHit("melee-swing");firstCutHit=new CombatHit("dagger-first");secondCutHit=new CombatHit("dagger-second");
                    firstDaggerTargets.clear();
                    mageAimX = currentInput.aimWorldX;
                    mageAimY = currentInput.aimWorldY;
                    if(usesMana())direction=mage.aimDirection(mageAimX,mageAimY);
                    gustGrass();
                }

                if(!attacking) updateRunningTrail(worldX - startX, worldY - startY);

            if(isLocal){
                gp.keyH.ePressed = false;
                gp.mouseH.leftClicked = false;
            }
            currentInput.ePressed = false;
            currentInput.leftClicked = false;
            attackCanceled = false;
            attackBuffered = false;
        }

        if(isBerserker())berserker.pose();
        if(isPaladin())paladin.pose();
        if(isRanger())ranger.pose();
        if(isAssassin())assassin.pose();
        if(usesMana())mage.pose();

        if(invincible){
            invincibleCounter++;
            if(invincibleCounter > invincibilityDuration){
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

        if(!isPredictedClient()){
            if(usesMana()){
                ticksSinceSkillUse++;
                if(ticksSinceSkillUse >= mpRegenDelayTicks && MP < maxMP){
                    mpRegenCounter++;
                    if(mpRegenCounter >= mpRegenIntervalTicks){
                        MP++;
                        mpRegenCounter = 0;
                    }
                }
            }
            else if(!usesFocus()){
                ticksSinceRageGain++;
                if(ticksSinceRageGain >= rageDecayDelayTicks && rage > 0){
                    rageDecayCounter++;
                    if(rageDecayCounter >= rageDecayIntervalTicks){
                        rage--;
                        rageDecayCounter = 0;
                    }
                }
            }
        }

        if(HP > maxHP){
            HP = maxHP;
        }
        if(MP > maxMP){
            MP = maxMP;
        }

        if(HP <= 0 && !isDead){
            if(canResolveWorldActions()){
                isDead = true;
                HP = 0;
                direction = "down";
                animState = "idle";
                spriteNum = 1;
                attacking = false;
                gp.broadcastPlayerEvent(playerId, GameEventType.DEATH, "Player " + (playerId + 1) + " has fallen!", null);
            }
        }

    }

    private void gustGrass(){
        float dirX = direction.equals("left") ? -1 : direction.equals("right") ? 1 : 0;
        float dirY = direction.equals("up") ? -1 : direction.equals("down") ? 1 : 0;
        gp.grass.burst(worldX + gp.tileSize / 2f + dirX * 30, worldY + solidArea.y + solidArea.height + dirY * 24, 56, 0.6f);
    }

    public void attacking() {
        if(gp.hitStopCounter > 0) return;

        if(currentWeapon instanceof OBJ_crafted c && (c.type==type_staff || c.type==type_bow)){
            attacking=false;animState="idle";return;
        }
        if(usesMana() && !(currentWeapon instanceof OBJ_crafted)){
            mageAttacking();
            return;
        }

        SpriteAnimation anim = getCurrentAnimation();
        int totalFrames = (isBerserker() || isPaladin())?CharacterSprites.ATTACK_FRAMES
                :anim!=null?anim.frames.length:1;

        int previousFrame=spriteNum;
        advanceAttackFrame();
        if(spriteNum > totalFrames){
            spriteNum = 1;
            attacking = false;
            animState = moving ? "run" : "idle";
            return;
        }

        if(!canResolveWorldActions()) return;
        if(currentWeapon instanceof OBJ_crafted crafted){
            if(crafted.type==type_staff || crafted.type==type_bow)return;
            if(spriteNum>=attackHitStartFrame && spriteNum<=attackHitEndFrame)resolveMeleeStrike(null);
        }else if(isBerserker()){
            if(hasGreatsword() && spriteNum!=previousFrame && spriteNum==attackHitStartFrame)berserker.basicSweep();
        }else if(isPaladin()){
            if(hasPaladinSword() && spriteNum!=previousFrame && spriteNum==attackHitStartFrame)resolveMeleeStrike(null);
        }else if(weaponKind().isDaggers()){
            if(spriteNum == WeaponArt.DAGGER_FIRST_HIT_FRAME){
                resolveMeleeStrike(DaggerCut.FIRST);
            } else if(spriteNum == WeaponArt.DAGGER_SECOND_HIT_FRAME){
                resolveMeleeStrike(DaggerCut.SECOND);
                firstDaggerTargets.clear();
            }
        } else if(spriteNum >= attackHitStartFrame && spriteNum <= attackHitEndFrame){
            resolveMeleeStrike(null);
        }
    }

    private void resolveMeleeStrike(DaggerCut cut){
        Rectangle attackBounds = meleeAttackBounds();
        java.util.List<Integer> hitMonsters = gp.collisionChecker.checkEntityMultiple(attackBounds, gp.monster);
        int bonusDamage = powerStrikePending ? powerStrikeBonus : 0;
        powerStrikePending = false;
        for(int monsterIndex : hitMonsters){
            if(swingHit==null){swingHit=new CombatHit("melee-swing");firstCutHit=new CombatHit("dagger-first");secondCutHit=new CombatHit("dagger-second");}
            CombatHit event=cut==DaggerCut.FIRST?firstCutHit:cut==DaggerCut.SECOND?secondCutHit:swingHit;
            boolean landed = damageMonster(monsterIndex, getEffectiveAttack(), false, null, cut, bonusDamage, false,false,event,Double.NaN);
            if(!landed) continue;
            Entity target = gp.monster[monsterIndex];
            if(cut == DaggerCut.FIRST) firstDaggerTargets.add(target);
        }
        for(int index : gp.collisionChecker.checkEntityMultiple(attackBounds, gp.interactable)) objectInteract(index);
    }

    public Rectangle meleeAttackBounds(){
        int x=worldX+solidArea.x,y=worldY+solidArea.y,width=attackArea.width,reach=attackArea.height;
        return switch(direction){
            case "up" -> new Rectangle(x+solidArea.width/2-width/2,y-reach+2,width,reach);
            case "down" -> new Rectangle(x+solidArea.width/2-width/2,y+solidArea.height-2,width,reach);
            case "left" -> new Rectangle(x-reach+2,y+solidArea.height/2-width/2,reach,width);
            default -> new Rectangle(x+solidArea.width-2,y+solidArea.height/2-width/2,reach,width);
        };
    }

    public void damageMonster(int index, int attack) {
        damageMonster(index, attack, false, null);
    }

    public void damageMonster(int index, int attack, boolean forcedBigHit) {
        damageMonster(index, attack, forcedBigHit, null);
    }

    public boolean damageMonster(int index, int attack, boolean forcedBigHit, String knockbackDir) {
        return damageMonster(index, attack, forcedBigHit, knockbackDir, null, 0, false,false);
    }

    /** Projectile callers retain their event across all swept collision samples. */
    public boolean damageMonsterOnce(int index,int power,boolean strong,String direction,CombatHit event,double knockbackTiles){
        return damageMonster(index,power,strong,direction,null,0,false,false,event,knockbackTiles);
    }

    void gainBerserkerRage(int amount){
        rage=Math.min(maxRage,rage+amount);
        ticksSinceRageGain=0;
    }

    void beginBerserkerSkill(){attackBuffered=false;}

    boolean berserkerDamage(int index,int power,String direction,boolean heavy,boolean followUp){
        return damageMonster(index,power,heavy,direction,null,0,followUp,false);
    }

    boolean rangerDamage(int index,int damage,String direction,boolean precise,boolean followUp){
        return damageMonster(index,damage,precise,direction,null,0,followUp,false);
    }

    void gainRangerFocus(int amount){
        rage=Math.min(maxRage,rage+amount);
    }

    private boolean damageMonster(int index,int attack,boolean critical,String direction,DaggerCut cut,int bonus,boolean followUp){
        return damageMonster(index,attack,critical,direction,cut,bonus,followUp,false);
    }

    boolean mageDamage(int index,int power,String direction,boolean area,boolean strong){
        return damageMonster(index,power,strong,direction,null,0,area,false);
    }

    boolean mageBoltDamage(int index,int power,String direction,CombatHit event){
        return damageMonster(index,power,false,direction,null,0,false,false,event,CombatTuning.BOLT);
    }
    boolean rangerArrowDamage(int index,int power,String direction,boolean precise,CombatHit event){
        return damageMonster(index,power,precise,direction,null,0,false,false,event,CombatTuning.ARROW);
    }

    boolean mageAreaDamage(int index,int power,String direction,boolean area,boolean strong,CombatHit event){
        return damageMonster(index,power,strong,direction,null,0,area,false,event,Double.NaN);
    }
    boolean rangerWaveDamage(int index,int power,String direction,boolean followUp,CombatHit event){
        return damageMonster(index,power,false,direction,null,0,followUp,false,event,Double.NaN);
    }

    void interruptForMageSpell(){
        attacking=false;attackBuffered=false;mageProjectileFired=false;attackFrameHoldCounter=0;
    }

    boolean assassinHit(int index,int power,String direction){
        return damageMonster(index,power,false,direction,null,0,false,true);
    }

    boolean assassinCut(int index,int power,String direction,boolean second,boolean followUp){
        boolean landed=damageMonster(index,power,false,direction,second?DaggerCut.SECOND:DaggerCut.FIRST,0,followUp,true);
        if(landed && !second)firstDaggerTargets.add(gp.monster[index]);
        if(second)firstDaggerTargets.clear();
        return landed;
    }

    void interruptForAssassinSkill(){
        attacking=false;attackBuffered=false;attackFrameHoldCounter=0;firstDaggerTargets.clear();
    }

    void assassinBleedTick(Monster target,int damage){
        if(!canResolveWorldActions() || target.dying || !target.alive)return;
        target.HP=Math.max(0,target.HP-damage);
        target.hpBarOn=true;target.hpBarCounter=0;
        AssassinCombat.bleedCue(target,false);
        gp.spawnFloatingText(target.worldX+gp.tileSize/2,target.worldY,String.valueOf(damage),AssassinCombat.CORAL);
        finishMonster(target);
    }

    private void finishMonster(Monster target){
        if(target.HP>0 || target.dying)return;
        target.dying=true;
        gp.slimeSpawner.onMonsterDefeated(target);
        if(target instanceof monster.MON_CrownedSlime)gp.slimeSpawner.onEncounterEnded();
        if(playerId>=0 && playerId<gp.players.length && gp.players[playerId]==this){
            grantExperience(target.exp);
            gp.broadcastPlayerEvent(playerId,GameEventType.KILL,
                    "Player " + (playerId + 1) + " killed the " + target.name + "! (+" + target.exp + " EXP)",null);
        }
    }

    private boolean damageMonster(int index, int attack, boolean forcedBigHit, String knockbackDir,
                                  DaggerCut cut, int bonusDamage, boolean followUp,boolean assassinSkill) {
        return damageMonster(index,attack,forcedBigHit,knockbackDir,cut,bonusDamage,followUp,assassinSkill,new CombatHit("skill-hit"),Double.NaN);
    }
    private boolean damageMonster(int index,int attack,boolean forcedBigHit,String knockbackDir,
                                  DaggerCut cut,int bonusDamage,boolean followUp,boolean assassinSkill,CombatHit event,double knockbackTiles){
        if(!canResolveWorldActions() || attack+bonusDamage<=0 || index<0 || index>=gp.monster.length)return false;
        if(gp.monster[index]==null || !gp.monster[index].alive || gp.monster[index].dying || gp.monster[index].HP<=0)return false;
        if(index != 999){
            Monster target = gp.monster[index];
            if(event.contains(target)){event.log(this,target,0,"rejected-duplicate");return false;}
            // Only a target this combo already hit can take the second cut through hit immunity.
            boolean completedPair = cut == DaggerCut.SECOND && firstDaggerTargets.contains(target);
            if(!target.invincible || completedPair || followUp){
                gp.playSE(5);
                int armor = Math.max(0,target.defense-Math.max(target.getStatusEffectValue("armorBreak"),target.getStatusEffectValue(BerserkerCombat.SUNDERED)));
                int damage = cut == null ? Math.max(1, attack + bonusDamage - armor)
                        : Math.max(1, (attack - armor + (cut == DaggerCut.FIRST ? 1 : 0)) / 2 + bonusDamage);

                boolean isBigHit = forcedBigHit;
                boolean resolveStrike=false;
                if(!usesMana() && !isRanger() && !isBerserker() && !isPaladin() && !assassinSkill && (!isAssassin() || cut!=null)){
                    rage = Math.min(maxRage, rage + (cut == null ? 10 : 5));
                    ticksSinceRageGain = 0;

                    if(playerClass.equals("warrior") && resolve>=maxResolve && hasShield()){
                        damage+=Math.max(1,getEffectiveDefense()*2);resolve=0;isBigHit=true;resolveStrike=true;
                    }
                }
                if(isAssassin() && completedPair && target.completeAssassinPair(this)){
                    isBigHit=true;
                    damage=damage*2+assassin.consumeWound(target);
                    assassin.finisher(knockbackDir!=null?knockbackDir:direction);
                }

                if(armorBreakPending){target.addStatusEffect(new StatusEffect("armorBreak",180,2,0));armorBreakPending=false;}
                damage+=PriestCombat.consumeChorus(this,damage);
                event.record(target);
                event.log(this,target,damage,"accepted");
                target.HP -= damage;
                int mastery=event.masteryType>=0?event.masteryType:currentWeapon!=null && !currentWeapon.isPlaceholder?currentWeapon.type:-1;
                if(mastery>=0){if(mastery==type_staff)magicMasteryXP++;else if(mastery==type_bow)bowMasteryXP++;else meleeMasteryXP++;}
                target.invincibilityDuration=usesFocus() || usesMana() || isBerserker() || isPaladin()?12:22;target.invincibleCounter=0;
                target.invincible = true;
                target.flashing = true;
                target.flashCounter = 0;
                target.hpBarOn = true;
                target.hpBarCounter = 0;

                String hitDirection = knockbackDir != null ? knockbackDir : direction;
                double impactTiles=0;
                if(!Double.isNaN(knockbackTiles))impactTiles=knockbackTiles;
                else if(cut != DaggerCut.FIRST && !isRanger() && !usesMana() && !assassinSkill)
                    impactTiles=isBigHit?CombatTuning.HEAVY:CombatTuning.SWORD;
                target.reactToHit(hitDirection,impactTiles);

                if(event.claimImpactFeedback()){
                    int hitStop = isRanger() || usesMana()?1:cut == DaggerCut.FIRST ? 1 : cut == DaggerCut.SECOND ? 2 : hitStopOnHitMonster;
                    if(isBigHit) hitStop *= 2;
                    gp.startScreenShake(isBigHit ? 8 : 4, isBigHit ? 6 : 2);
                    gp.startHitStop(hitStop);
                }

                Color criticalColor = null;
                if(isBigHit){
                    gp.playSE(7);
                    criticalColor = isMage() ? new Color(0xC3A4EF) : Palette.GOLD;
                }
                target.hitSequence++;
                target.hitDirection = hitDirection;
                target.criticalHitRgb = criticalColor == null ? 0 : criticalColor.getRGB();
                Particle.impact(target, hitDirection, criticalColor);

                if(damage > 0){
                    String label = resolveStrike?"COUNTER! ":isBigHit ? (isRanger()?"PRECISE! ":isAssassin()?"FOURTH CUT! ":isMage()?"NOVA! ":isPriest()?"RADIANT! ":"CRUSH! ") : "";
                    Color textColor = isBigHit
                            ? (isMage() ? new Color(190, 120, 255) : isPriest()?Palette.GOLD:new Color(255, 70, 70))
                            : new Color(255, 220, 80);
                    gp.spawnFloatingText(target.worldX + gp.tileSize/2, target.worldY, label + damage, textColor);
                }

                finishMonster(target);
                return true;
            }else event.log(this,target,0,"rejected-immunity");
        }
        return false;
    }

    public void grantExperience(int amount){
        exp += Math.max(0, amount);
        while(level < 25 && exp >= nextLevelExp) checkLevelUp();
    }

    private void checkLevelUp() { levelUp(true); }

    private void levelUp(boolean announce) {
        if(level < ClassStats.MAX_LEVEL && exp >= nextLevelExp){
            level++;
            nextLevelExp = usesFocus() || usesMana() || isBerserker() || isPaladin()?nextLevelExp+30+4*(level-1):nextLevelExp*2;
            applyLevelGrowth();
            attack = getAttack();
            defense = getDefense();
            if(!announce) return;
            gp.playSE(7);
            String ambient = "Player " + (playerId + 1) + " reached level " + level + "!";
            String personal = "You are level " + level + " now!\nYour will is stronger than ever!";
            gp.broadcastPlayerEvent(playerId, GameEventType.LEVELUP, ambient, personal);
        }
    }

    /** Automatic per-level growth (called after level was incremented). Rows come from ClassStats. */
    private void applyLevelGrowth(){
        ClassStats table = ClassStats.of(playerClass);
        maxHP += table.hpPerLevel;
        HP = Math.min(maxHP, HP + table.hpPerLevel);
        if(table.mpPerLevel > 0){
            maxMP += table.mpPerLevel;
            MP = Math.min(maxMP, MP + table.mpPerLevel);
        }
        switch(table.primary){
            case STR -> strength++;
            case DEX -> dexterity++;
            case INT -> intelligence++;
        }
        if(level % ClassStats.VIT_EVERY == 0) vitality++;
    }

    public void debugGrantLevels(int count){ debugSetLevel(level + count); }

    /** Debug: jump to an exact level (1-25), up or down. Gear bonuses are kept; only one level-up message is shown. */
    public void debugSetLevel(int target){
        target = Math.max(1, Math.min(ClassStats.MAX_LEVEL, target));
        if(target < level){
            applyBaseStats();
            level = 1;
            exp = 0;
            nextLevelExp = 30;
        }
        while(level < target){
            exp = nextLevelExp;
            levelUp(level + 1 == target);
        }
        attack = getAttack();
        defense = getDefense();
    }

    public String statSummary(){
        return "Lv " + level + "  HP " + HP + "/" + maxHP + (maxMP > 0 ? "  MP " + MP + "/" + maxMP : "")
                + "  STR " + strength + "  DEX " + dexterity + "  INT " + intelligence + "  VIT " + vitality
                + "  ATK " + attack + "  DEF " + defense;
    }

    private void contactMonster(int index){
        if(index==999)return;
        Monster monster=gp.monster[index];
        if(!monster.dying && monster.canDealContactDamage())receiveHit(monster,monster.attack,true);
    }

    void receiveHit(Entity source,int incoming,boolean contact){
        if(!canResolveWorldActions() || HP<=0 || isDead || invincible || inWell!=gp.quests.inDungeon())return;
        Player recipient=PaladinCombat.interceptor(this);
        boolean braced=recipient!=this || recipient.isPaladin() && recipient.paladin.selfBrace();
        recipient.applyHit(source,incoming,contact,braced);
    }

    private void applyHit(Entity source,int incoming,boolean contact,boolean braced){
        if(contact)gp.playSE(6);
        int armored=damageTaken(incoming);
        recordShieldMitigation(incoming,armored);
        if(braced)armored=Math.max(1,Math.round(armored*0.65f));
        int guarded=isPaladin()?paladin.guard(source,armored):armored;
        int damage=PriestCombat.protect(this,guarded);
        invincible=true;
        invincibilityDuration=source.incomingHitImmunity;
        invincibleCounter=0;
        if(damage==0)return;
        if(contact && playerClass.equals("warrior")){
            rage=Math.min(maxRage,rage+15);
            ticksSinceRageGain=0;
        }
        HP-=damage;
        flashing=true;
        flashCounter=0;
        if(guarded==armored && !braced)startKnockback(source.direction,source.incomingHitKnockback<0?knockbackDistance:source.incomingHitKnockback);
        int stop=guarded<armored || braced?3:hitStopOnTakingDamage;
        gp.startScreenShake(stop,stop==3?3:6);
        spawnHitParticles(source.direction);
        gp.startHitStop(stop);
        if(!contact || guarded<armored || braced)
            gp.spawnFloatingText(worldX+gp.tileSize/2,worldY,String.valueOf(damage),Color.red);
    }

    public void pickUpObject(int index){
        collectGroundItem(index,false);
    }
    public void pickUpFlyingLoot(int index){
        collectGroundItem(index,true);
    }
    private void collectGroundItem(int index,boolean automatic){
        if(index != 999){
            if(!canResolveWorldActions()) return; // host-only — only the authoritative simulation removes items from the shared world

            if(index<0 || index>=gp.obj.length)return;
            Entity picked = gp.obj[index];
            if(picked==null || picked.pickupBlocked) return;
            if(automatic ? !world.GroundLoot.canCollect(gp,this,picked) : !gp.interactions.isTarget(this,picked))return;

            if(picked.type == type_pickuponly){
                picked.use(this);
                gp.obj[index] = null;
                return;
            }

            String ambient;
            int amount=picked.stackCount;
            if(addToInventory(picked)){
                picked.groundLoot=false;picked.lootLift=0;picked.lootVX=picked.lootVY=0;
                if(automatic)gp.spawnFloatingText(worldX+gp.tileSize/2,worldY,"+"+amount+" "+picked.name,new Color(220,231,185));
                gp.playSE(1);
                ambient = "Player " + (playerId + 1) + " picked up a " + picked.name + "!";
                gp.obj[index] = null;
            } else {
                ambient = "Player " + (playerId + 1) + "'s inventory is full!";
            }
            gp.broadcastPlayerEvent(playerId, GameEventType.PICKUP, ambient, null);
        }
    }

    public boolean addToInventory(Entity item){
        if(item.stackable){
            for(int i = 0; i < inventorySlots.length; i++){
                Entity slot = inventorySlots[i];
                if(slot != null && slot.canStackWith(item) && slot.stackCount < slot.maxStackSize){
                    int room = slot.maxStackSize - slot.stackCount;
                    int toAdd = Math.min(room, item.stackCount);
                    slot.stackCount += toAdd;
                    item.stackCount -= toAdd;
                    if(item.stackCount <= 0) return true;
                }
            }
        }

        for(int i = 0; i < inventorySlots.length; i++){
            if(inventorySlots[i] == null){
                inventorySlots[i] = item;
                return true;
            }
        }

        return false;
    }

    public void tryEquipFromBag(int bagIndex, int slotType){
        if(bagIndex<0 || bagIndex>=inventorySlots.length)return;
        Entity item = inventorySlots[bagIndex];
        if(item == null) return;

        if(!fitsEquipment(item, slotType)) return;
        Entity previous=equippedItem(slotType);
        setEquipment(slotType,item);
        inventorySlots[bagIndex]=previous!=null && !previous.isPlaceholder ? previous : null;
        gp.playSE(3);
    }

    public void swapOrMergeBagSlots(int fromIndex, int toIndex){
        if(fromIndex == toIndex) return;

        Entity fromItem = inventorySlots[fromIndex];
        Entity toItem = inventorySlots[toIndex];
        if(fromItem == null) return;

        if(toItem != null && fromItem.canStackWith(toItem) && toItem.stackCount < toItem.maxStackSize){
            int room = toItem.maxStackSize - toItem.stackCount;
            int toMove = Math.min(room, fromItem.stackCount);
            toItem.stackCount += toMove;
            fromItem.stackCount -= toMove;
            if(fromItem.stackCount <= 0){
                inventorySlots[fromIndex] = null;
            }
            return;
        }

        inventorySlots[fromIndex] = toItem;
        inventorySlots[toIndex] = fromItem;
    }

    public void unequipToBagIndex(int slotType, int bagIndex){
        if(bagIndex<0 || bagIndex>=inventorySlots.length)return;
        Entity equipped=equippedItem(slotType);
        if(equipped==null || equipped.isPlaceholder)return;
        Entity replacement=inventorySlots[bagIndex];
        if(replacement!=null && !fitsEquipment(replacement,slotType))return;
        setEquipment(slotType,replacement);
        inventorySlots[bagIndex]=equipped;
        gp.playSE(3);
    }

    public void unequipToWorld(int slotType){
        Entity equipped=equippedItem(slotType);
        if(equipped==null || equipped.isPlaceholder)return;
        if(java.util.Arrays.stream(gp.obj).noneMatch(item -> item==null)){
            gp.ui.addMessage("No room to drop equipment here.");return;
        }
        setEquipment(slotType,null);
        dropItemAtPlayer(equipped);
        gp.ui.addMessage("Dropped "+equipped.name+".");
    }

    /** All-or-nothing: never delete or drop gear when the bag is full. */
    public void undress(){
        int count=0,free=0;
        for(Entity item:inventorySlots)if(item==null)free++;
        for(int slot=EQUIP_WEAPON;slot<=EQUIP_HEADPIECE;slot++){
            Entity item=equippedItem(slot);if(item!=null && !item.isPlaceholder)count++;
        }
        if(free<count){gp.ui.addMessage("Make room for "+count+" items before undressing.");return;}
        for(int slot=EQUIP_WEAPON;slot<=EQUIP_HEADPIECE;slot++){
            Entity item=equippedItem(slot);if(item==null || item.isPlaceholder)continue;
            addToInventory(item);
            if(usesFocus() || usesMana() || isBerserker() || isPaladin())setEquipment(slot,null);
            else if(slot==EQUIP_WEAPON){currentWeapon=new OBJ_fists(gp);attack=getAttack();}
            else if(slot==EQUIP_SHIELD){currentShield=new OBJ_no_shield(gp);defense=getDefense();}
            else apparelSlots[slot-EQUIP_HAIR]=null;
        }
        refreshWeaponAnimation();gp.playSE(3);
    }

    public void dropBagItemToWorld(int index){
        Entity item = inventorySlots[index];
        if(item == null) return;

        inventorySlots[index] = null;
        dropItemAtPlayer(item);
        gp.ui.addMessage("Dropped " + item.name + ".");
    }

    private void dropItemAtPlayer(Entity item){
        item.pickupBlocked = true;

        for(int i = 0; i < gp.obj.length; i++){
            if(gp.obj[i] == null){
                gp.obj[i] = item;
                gp.obj[i].worldX = worldX;
                gp.obj[i].worldY = worldY;
                break;
            }
        }
    }

    public void useInventoryItem(int index){
        Entity item = inventorySlots[index];
        if(item == null || item.type != type_consumable) return;

        item.use(this);
        item.stackCount--;
        if(item.stackCount <= 0){
            inventorySlots[index] = null;
        }
    }

    private void updateDroppedItemPickupBlocks(){
        Rectangle playerBounds = new Rectangle(worldX + solidArea.x, worldY + solidArea.y, solidArea.width, solidArea.height);

        for(Entity obj : gp.obj){
            if(obj != null && obj.pickupBlocked){
                Rectangle objBounds = new Rectangle(obj.worldX + obj.solidArea.x, obj.worldY + obj.solidArea.y, obj.solidArea.width, obj.solidArea.height);
                if(!playerBounds.intersects(objBounds)){
                    obj.pickupBlocked = false; // player has physically stepped away — normal pickup rules resume
                }
            }
        }
    }

    public void interactNPCDirectional(int index){
        boolean pressed=currentInput.ePressed;currentInput.ePressed=true;interactNPC(index);currentInput.ePressed=pressed;
    }

    public void interactNPC(int index){
        if(inDialogue){
            if(currentInput.escapePressed){
                currentInput.escapePressed=false;
                forceCloseDialogue();
                return;
            }
            if(currentInput.ePressed || currentInput.leftClicked){
                currentInput.ePressed=false;
                if(!isDialogueFullyRevealed()){
                    dialogueRevealCounter = dialogueText.length() * dialogueRevealSpeed; // snap to fully shown
                } else {
                    advanceOrCloseDialogue();
                }
                if(isLocal){
                    gp.mouseH.leftClicked = false;
                }
                currentInput.leftClicked = false;
            }
            return;
        }

        if((currentInput.ePressed || currentInput.leftClicked) && index != 999){
            Entity npc = gp.npc[index];
            if(npc==null || !gp.interactions.isTarget(this,npc))return;
            attackCanceled = true;
            if("merchant".equals(npc.npcId)){gp.merchant.open(this);return;}
            startDialogueSession(index);
            currentInput.ePressed=false;

            if(canResolveWorldActions()){
                // Shared, networked side effect — only host-resolved to avoid two machines
                // fighting over the same NPC's facing direction
                switch (direction){
                    case "up" -> npc.direction = "down";
                    case "down" -> npc.direction = "up";
                    case "left" -> npc.direction = "right";
                    case "right" -> npc.direction = "left";
                }
                npc.activeConversationCount++;
            }

            if(isLocal){
                gp.mouseH.leftClicked = false;
            }
            currentInput.leftClicked = false;
        }
    }

    private void forceCloseDialogue(){
        int npcIdx = dialogueNpcIndex;
        String finishedTag=dialogueTag;
        closeDialogue();
        decrementNpcConversation(npcIdx);
        if(canResolveWorldActions() && quest.QuestManager.INTRO_TAG.equals(finishedTag))gp.quests.acknowledgeIntro(this);
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

    public void draw(Graphics2D g2){
        SpriteAnimation anim = getCurrentAnimation();
        BufferedImage bodyImage = getCurrentFrame();
        if(isPaladin() && !isDead && !attacking && hasShield() && paladin.guardFrame()>0){
            var guard=sprites.get("guard_"+direction);
            if(guard!=null)bodyImage=guard.frames[paladin.guardFrame()-1];
        }
        if(bodyImage == null) return;

        int bodyWidth = (anim != null) ? anim.width : bodyImage.getWidth();
        int bodyHeight = (anim != null) ? anim.height : bodyImage.getHeight();

        int drawX;
        int drawY;
        if(isLocal){
            drawX = screenX + (gp.tileSize - bodyWidth) / 2;
            drawY = screenY + (gp.tileSize - bodyHeight) / 2;
        } else {
            Player camera = gp.localPlayer();
            int relScreenX = worldX - camera.worldX + camera.screenX;
            int relScreenY = worldY - camera.worldY + camera.screenY;
            drawX = relScreenX + (gp.tileSize - bodyWidth) / 2;
            drawY = relScreenY + (gp.tileSize - bodyHeight) / 2;
        }

        if(USE_GENERATED_BODY){
            // put the feet on the bottom of the hitbox instead of centring the canvas on the tile
            int footPx = (int)(bodyHeight * CharacterSprites.FOOT_FRAC);
            drawY += (solidArea.y + solidArea.height - 2 - footPx) - (gp.tileSize - bodyHeight) / 2;
        }

        double bounce = 0;
        if(moving && !attacking){
            bounce = Math.abs(Math.sin(bounceCounter * 0.2)) * -4;
        }
        int hopLift=isRanger()?ranger.hopLift():0;
        bounce-=hopLift;

        double castScale = 1.0;
        if(attacking && usesMana() && spriteNum == attackHitStartFrame){
            double progress = Math.min(1.0, (double) attackFrameHoldCounter / getEffectiveAttackFrameDelay());
            castScale = 1.0 + Math.sin(progress * Math.PI) * 0.18;
        }

        if(!isDead){
            int shadowScreenX = worldX - gp.localPlayer().worldX + gp.localPlayer().screenX;
            int shadowScreenY = worldY - gp.localPlayer().worldY + gp.localPlayer().screenY;
            if(!isLocal){
                Player camera = gp.localPlayer();
                shadowScreenX = worldX - camera.worldX + camera.screenX;
                shadowScreenY = worldY - camera.worldY + camera.screenY;
            }
            drawShadow(g2, shadowScreenX, shadowScreenY);
        }
        if(isBerserker() && !isDead){
            berserker.drawGround(g2);
            berserker.drawAfterimage(g2,bodyImage,drawX,drawY);
        }
        if(isAssassin() && !isDead)assassin.drawAfterimages(g2,bodyImage,drawX,drawY);
        boolean up="up".equals(direction);
        boolean bowBehind=isRanger() && up;
        if(bowBehind)ranger.drawBow(g2);
        if(!up)drawMeleeSlashes(g2);
        AffineTransform originalTransform = g2.getTransform();
        g2.translate(0, bounce);
        if(hopLift>0)g2.rotate(-Math.cos(ranger.heading())*hopLift/100,
                drawX+bodyWidth/2.0,drawY+bodyHeight*0.78);
        if(castScale != 1.0){
            int pivotX = drawX + bodyWidth/2;
            int pivotY = drawY + bodyHeight/2;
            g2.translate(pivotX, pivotY);
            g2.scale(castScale, castScale);
            g2.translate(-pivotX, -pivotY);
        }
        g2.drawImage(bodyImage, drawX, drawY, null);
        g2.setTransform(originalTransform);

        if(currentWeapon instanceof OBJ_crafted crafted && crafted.type==type_bow){
            Player camera=gp.localPlayer();int hx=worldX-camera.worldX+camera.screenX+(int)solidArea.getCenterX(),hy=worldY-camera.worldY+camera.screenY+(int)solidArea.getCenterY();
            Graphics2D held=(Graphics2D)g2.create();if(direction.equals("left")){held.translate(hx-10,hy-16);held.scale(-1,1);}else held.translate(hx+10,hy-16);
            held.drawImage(crafted.down1,-16,-20,32,42,null);held.dispose();
        }
        if(up)drawMeleeSlashes(g2);
        drawNameLabel(g2, drawX + bodyWidth/2, drawY);
        if(isRanger()){
            if(!bowBehind)ranger.drawBow(g2);
            ranger.draw(g2);
        }
        if(isMage())mage.draw(g2);
        if(isBerserker())berserker.draw(g2);
        if(isPaladin())paladin.draw(g2);
        PriestCombat.drawSupport(this,g2);
    }


    public boolean usesMeleeSlash(){return !usesMana() && !isRanger();}

    private void drawMeleeSlashes(Graphics2D g){
        if(isDead || !usesMeleeSlash())return;
        if(attacking)drawBasicSlash(g);
        if(isAssassin())assassin.drawSlashes(g);
        if(isBerserker())berserker.drawSlash(g);
        if(isPaladin())paladin.drawSlash(g);
    }

    private void drawBasicSlash(Graphics2D g){
        var weapon=weaponKind();
        double heading=art.MeleeSlash.heading(direction);
        if(weapon.isDaggers()){
            drawSlash(g,heading,55,49,ticksFromAttackFrame(WeaponArt.DAGGER_FIRST_HIT_FRAME),false);
        }else{
            float reach=weapon==WeaponArt.Kind.NONE?32:isBerserker()?79:attackArea.height;
            float width=weapon==WeaponArt.Kind.NONE?32:isBerserker()?86:attackArea.width;
            drawSlash(g,heading,reach,width,ticksFromAttackFrame(attackHitStartFrame),false);
        }
    }

    void drawSlash(Graphics2D g,double heading,float reach,float width,int ticksFromHit,boolean reverse){
        Player camera=gp.localPlayer();
        float scale=gp.tileSize/48f;
        float offset=(isAssassin()?10:5)*scale;
        float x=worldX+(float)solidArea.getCenterX()-camera.worldX+camera.screenX+(float)Math.cos(heading)*offset;
        float y=worldY+(float)solidArea.getCenterY()-camera.worldY+camera.screenY-7*scale+(float)Math.sin(heading)*offset;
        art.MeleeSlash.draw(g,x,y,heading,reach*scale,width*scale,ticksFromHit,reverse);
    }

    private int ticksFromAttackFrame(int hitFrame){
        int delta=attackFrameHoldCounter;
        for(int frame=hitFrame;frame<spriteNum;frame++)delta+=getAttackFrameDelay(frame);
        for(int frame=spriteNum;frame<hitFrame;frame++)delta-=getAttackFrameDelay(frame);
        return delta;
    }

//    private void drawAttackHitbox(Graphics2D g2){
//        if(!attacking || spriteCounter <= 5 || spriteCounter > 25) return;
//
//        int hbWorldX = worldX;
//        int hbWorldY = worldY;
//        switch (direction){
//            case "up" -> hbWorldY -= attackArea.height;
//            case "down" -> hbWorldY += attackArea.height;
//            case "left" -> hbWorldX -= attackArea.width;
//            case "right" -> hbWorldX += attackArea.width;
//        }
//
//        int screenX = hbWorldX - gp.player.worldX + gp.player.screenX + solidArea.x;
//        int screenY = hbWorldY - gp.player.worldY + gp.player.screenY + solidArea.y;
//
//        g2.setColor(Color.yellow);
//        g2.drawRect(screenX, screenY, attackArea.width, attackArea.height);
//    }

    private String getWeaponFolder(){
        return switch (currentWeapon.type){
            case type_axe -> "axe";
            // case type_spear -> "spear";
            // case type_mace -> "mace";
            default -> "sword";
        };
    }

    private void updateRunningTrail(int dx, int dy){
        if(dx == 0 && dy == 0){
            trailCounter = TRAIL_START_DELAY;
            return;
        }
        if(trailCounter > 0){
            trailCounter--;
            return;
        }
        var random = ThreadLocalRandom.current();
        trailCounter = random.nextInt(7, 11) - 1;
        float length = (float)Math.hypot(dx, dy);
        float backX = -dx / length, backY = -dy / length;
        boolean runningDown = dx == 0 && dy > 0;
        float distance = runningDown ? 12 : random.nextFloat(16f, 22f);
        float lateral = runningDown ? 0 : random.nextFloat(-3.5f, 3.5f);
        float radius = random.nextFloat(7.8f, 10.4f);
        float drift = random.nextFloat(0.38f, 0.82f);
        int lifetime = runningDown ? random.nextInt(10, 13) : random.nextInt(17, 23);
        Particle.emit(this, Particle.Style.DUST, Color.WHITE, radius, lifetime,
                backX * drift, backY * drift * 0.65f - 0.16f,
                backX * distance - backY * lateral, backY * distance + backX * lateral - 5);
    }

    private void setupSkills(){
        statusEffectIcons.clear();
        if(isPaladin()){paladin.setup(skills);return;}
        if(isBerserker()){berserker.setup(skills);return;}
        if(isRanger()){ranger.setSkills(skills);return;}
        if(isAssassin()){assassin.setup(skills);return;}
        if(isPriest()){priest.setup(skills);return;}
        if(isMage()){
            mage.setup(skills);
            statusEffectIcons.put(MageCombat.INFUSION,skills[2].icon);
        }else setupWarriorSkills();
    }

    private void setupWarriorSkills(){
        skills[0]=new Skill("Shield Bash",20,240,()->{
            if(!hasShield())return;
            strikeFront(getEffectiveAttack()+getEffectiveDefense()*2);
            announceSkill("Shield Bash");
        },loadSkillIcon("warrior_bash"));
        skills[1]=new Skill("Bulwark",20,600,()->{
            statusEffects.removeIf(e->e.type.equals("defenseBoost"));addStatusEffect(new StatusEffect("defenseBoost",300,3,0));
            announceSkill("Bulwark");
        },loadSkillIcon("warrior_bulwark"));
        skills[2]=new Skill("Retaliation",25,480,()->{
            statusEffects.removeIf(e->e.type.equals("retaliation"));addStatusEffect(new StatusEffect("retaliation",300,1,0));
            announceSkill("Retaliation");
        },loadSkillIcon("warrior_retaliation"));
        skills[3]=new Skill("Shield Breaker",30,360,()->{
            powerStrikePending=true;powerStrikeBonus=getEffectiveDefense()+getEffectiveAttack();armorBreakPending=true;
            announceSkill("Shield Breaker");
        },loadSkillIcon("warrior_breaker"));
        skills[4]=new Skill("Rally",30,900,()->{
            int heal=3+getEffectiveDefense();
            for(Player ally:gp.players)if(ally!=null && ally.inWell==inWell && !ally.isDead && Math.hypot(ally.worldX-worldX,ally.worldY-worldY)<=gp.tileSize*3)
                ally.HP=Math.min(ally.maxHP,ally.HP+heal);
            announceSkill("Rally");
        },loadSkillIcon("warrior_rally"));
        statusEffectIcons.put("defenseBoost",skills[1].icon);statusEffectIcons.put("retaliation",skills[2].icon);
        skills[0].description="Shield hit: attack + twice defense.";
        skills[1].description="Gain +3 defense for 5 seconds.";
        skills[2].description="Next shield-mitigated hit fills Resolve.";
        skills[3].description="Empower next strike; weaken armor for 3s.";
        skills[4].description="Heal nearby allies by 3 + defense.";
    }
    public boolean hasShield(){return currentShield!=null && !currentShield.isPlaceholder;}
    public void recordShieldMitigation(int incoming,int damage){
        if(!canResolveWorldActions() || !playerClass.equals("warrior") || !hasShield() || damage>=incoming)return;
        if(hasStatusEffect("retaliation")){resolve=maxResolve;statusEffects.removeIf(e->e.type.equals("retaliation"));}
        else resolve=Math.min(maxResolve,resolve+1);
    }
    private void strikeFront(int damage){
        for(int i=0;i<gp.monster.length;i++){
            Entity m=gp.monster[i];if(m==null)continue;
            int dx=m.worldX+m.solidArea.x+m.solidArea.width/2-(worldX+24),dy=m.worldY+m.solidArea.y+m.solidArea.height/2-(worldY+36);
            boolean front=switch(direction){case "up"->dy<=0;case "down"->dy>=0;case "left"->dx<=0;default->dx>=0;};
            if(front && Math.hypot(dx,dy)<=96)damageMonster(i,damage);
        }
    }

    private BufferedImage loadSkillIcon(String path){return art.SkillArt.icon(path);}

    public int getEffectiveAttack(){
        return attack + getStatusEffectValue("attackBoost");
    }

    public int damageTaken(int incoming){
        int armor=getEffectiveDefense();
        return Math.max(1,(usesFocus() || usesMana() || isBerserker() || isPaladin())?Math.round(incoming*(100-Math.min(35,armor))/100f):incoming-armor);
    }

    public int getEffectiveDefense(){
        return defense + getStatusEffectValue("defenseBoost");
    }

    private boolean hasEnoughResource(int cost){
        return skillResource()>=cost;
    }

    private void spendResource(int cost){
        if(usesMana()){
            MP -= cost;
            ticksSinceSkillUse = 0;
            mpRegenCounter=0;
        }else if(isPaladin()){
            resolve-=cost;
        }else{
            rage-=cost;
        }
    }

    @Override protected BufferedImage animationFrame(SpriteAnimation anim){
        if(!"attack".equals(animState) || anim.frames.length!=CharacterSprites.SWORD_ATTACK_FRAMES)
            return super.animationFrame(anim);
        int frame=(spriteNum-1)*2;
        if(attacking){
            if(attackFrameHoldCounter*2>=getAttackFrameDelay(spriteNum))frame++;
        }else if(isPaladin()){
            frame=paladin.swordPose();
        }else if(isBerserker()){
            frame=berserker.windupPose();
        }
        return anim.frames[Math.clamp(frame,0,anim.frames.length-1)];
    }

    public int attackFrameTicks(){return attackFrameHoldCounter;}

    public void syncMeleeAttackFrame(int frame,int ticks){
        spriteNum=Math.clamp(frame,1,CharacterSprites.ATTACK_FRAMES);
        attackFrameHoldCounter=Math.clamp(ticks,0,getAttackFrameDelay(spriteNum)-1);
    }

    private int getAttackFrameDelay(int frameNumber){
        if(isPaladin())return frameNumber<=attackHitEndFrame?4:3;
        if(isBerserker())return frameNumber<attackHitStartFrame || frameNumber>attackHitEndFrame?5:4;
        if(weaponKind().isDaggers()){
            return frameNumber<attackHitStartFrame?3:frameNumber<=attackHitEndFrame?1:2;
        }
        if(frameNumber < attackHitStartFrame){
            return attackWindupFrameDelay;
        }
        if(frameNumber <= attackHitEndFrame){
            return getEffectiveAttackFrameDelay(); // active window — still respects Adrenaline Rush's attackSpeedBoost
        }
        return attackRecoveryFrameDelay;
    }

    public int getEffectiveAttackFrameDelay(){
        int delay = (weaponKind().isDaggers()?1:attackActiveFrameDelay) - getStatusEffectValue("attackSpeedBoost");
        return Math.max(1, delay);
    }

    private void advanceAttackFrame(){
        if(++attackFrameHoldCounter >= getAttackFrameDelay(spriteNum)){
            attackFrameHoldCounter = 0;
            spriteNum++;
        }
    }

    public void applyRemoteAnimation(String state, boolean attacking){
        if(!usesMana() && !usesFocus() && !isBerserker() && !isPaladin()){
            if(this.attacking != attacking){
                spriteNum = 1;
                attackFrameHoldCounter = 0;
            }
            animState = attacking ? "attack" : ("attack".equals(state) ? "idle" : state);
        } else {
            animState = state;
        }
        this.attacking = attacking;
    }

    public void cancelBowDraw(){ranger.cancelAim();}

    public void applyInput(InputState input){
        currentInput = input;
        lastProcessedInputTick = input.tick;
        if(isLocal){
            boolean movementSuppressedThisTick = merchantOpen || inDialogue || isDead || gp.inventoryOpen || gp.optionsOpen || gp.keyH.enteringChat;
            if(!movementSuppressedThisTick){
                inputHistory.addLast(input);
                while(inputHistory.size() > inputHistoryCapacity){
                    inputHistory.removeFirst();
                }
            }
        }
    }

    public void showPersonalNotification(String text){
        startScriptedDialogue("",java.util.List.of(text));
    }

    public void startScriptedDialogue(String tag,java.util.List<String> lines){
        dialogueNpcIndex=-1;dialoguePage=0;dialogueTag=tag;sessionDialogue=java.util.List.copyOf(lines);
        inDialogue=true;moving=false;attacking=false;attackBuffered=false;mageProjectileFired=false;
        dialogueText=currentDialogueLine();
        dialogueRevealCounter=quest.QuestManager.INTRO_TAG.equals(tag)?dialogueText.length()*dialogueRevealSpeed:0;
    }

    private void startDialogueSession(int npcIndex){
        dialogueNpcIndex=npcIndex;dialoguePage=0;dialogueTag="";inDialogue=true;
        Entity npc=gp.npc[npcIndex];
        sessionDialogue=java.util.List.copyOf(npc.npcId.equals("oldman")?gp.quests.oldManDialogue(npc.dialogueLines):npc.dialogueLines);
        dialogueText=currentDialogueLine();dialogueRevealCounter=0;
    }

    private String currentDialogueLine(){
        return sessionDialogue.isEmpty()?"...":sessionDialogue.get(Math.min(dialoguePage,sessionDialogue.size()-1));
    }

    public boolean isLastDialogueLine(){return sessionDialogue.isEmpty() || dialoguePage>=sessionDialogue.size()-1;}

    private void decrementNpcConversation(int npcIdx){
        if(canResolveWorldActions() && npcIdx >= 0 && gp.npc[npcIdx] != null){
            gp.npc[npcIdx].activeConversationCount = Math.max(0, gp.npc[npcIdx].activeConversationCount - 1);
        }
    }

    public void advanceOrCloseDialogue(){
        if(isLastDialogueLine()){
            int npcIdx=dialogueNpcIndex;String finishedTag=dialogueTag;
            boolean oldman=npcIdx>=0 && gp.npc[npcIdx]!=null && gp.npc[npcIdx].npcId.equals("oldman");
            closeDialogue();decrementNpcConversation(npcIdx);
            if(canResolveWorldActions()){
                if(oldman)gp.quests.onOldManFinished(this);
                if(quest.QuestManager.INTRO_TAG.equals(finishedTag))gp.quests.acknowledgeIntro(this);
            }
        }else{
            dialoguePage++;dialogueText=currentDialogueLine();
            dialogueRevealCounter=quest.QuestManager.INTRO_TAG.equals(dialogueTag)?dialogueText.length()*dialogueRevealSpeed:0;
        }
    }

    private void closeDialogue(){
        inDialogue=false;dialogueText="";dialogueTag="";dialogueNpcIndex=-1;dialoguePage=0;
        sessionDialogue=java.util.List.of();
    }

    public void resetForSceneTransition(){
        priest.reset();paladin.reset();
        resetForInteraction();
    }

    public void resetForInteraction(){
        merchantOpen=false;
        if(inDialogue)decrementNpcConversation(dialogueNpcIndex);
        closeDialogue();inputHistory.clear();moving=false;attacking=false;attackBuffered=false;attackCanceled=false;collisionOn=false;
        mageProjectileFired=false;hoveredTarget=null;spriteNum=1;spriteCounter=0;attackFrameHoldCounter=0;
        firstDaggerTargets.clear();
        trailCounter=TRAIL_START_DELAY;
        ranger.reset();assassin.reset();mage.cancel();berserker.cancel();paladin.cancel();
        knockbackActive=false;knockbackRemaining=0;
        if(currentInput!=null){currentInput.suppressGameplay();currentInput.escapePressed=false;}
        animState="idle";
    }

    public void performRespawn(){
        gp.quests.respawnAtPlaza(this);
        gp.playSE(11);
    }

    public boolean canResolveWorldActions(){
        return !gp.isNetworked || gp.isHost;
    }

    public void captureSavedCombat(net.PlayerState state){
        state.powerStrikePending=powerStrikePending;state.powerStrikeBonus=powerStrikeBonus;
        state.armorBreakPending=armorBreakPending;state.auraTicks=auraDurationRemaining;
        state.effectIntervals=statusEffects.stream().mapToInt(e->e.tickInterval).toArray();
        state.effectCounters=statusEffects.stream().mapToInt(e->e.tickCounter).toArray();
    }

    public void restoreSavedCombat(net.PlayerState state){
        powerStrikePending=!isAssassin() && state.powerStrikePending;powerStrikeBonus=state.powerStrikeBonus;
        armorBreakPending=state.armorBreakPending;
        auraDurationRemaining=Math.max(0,state.auraTicks);
        for(int i=0;i<statusEffects.size();i++){
            StatusEffect effect=statusEffects.get(i);
            effect.tickInterval=state.effectIntervals!=null && i<state.effectIntervals.length?Math.max(0,state.effectIntervals[i]):0;
            effect.tickCounter=state.effectCounters!=null && i<state.effectCounters.length?Math.max(0,state.effectCounters[i]):0;
        }
    }

    private boolean isPredictedClient(){
        return isLocal && gp.isNetworked && !gp.isHost;
    }

    public void applySyncedEffects(String[] types, int[] durations, int[] values, int auraRgb){
        statusEffects.clear();
        if(types != null){
            for(int i = 0; i < types.length; i++){
                statusEffects.add(new StatusEffect(types[i], durations[i], values[i], 0));
            }
        }
        if(auraRgb < 0){
            auraActive = false;
        } else if(!auraActive || auraColor == null || (auraColor.getRGB() & 0xFFFFFF) != auraRgb){
            startAura(new Color(auraRgb), 999999); // the host clears it; the client never times it out itself
        }
    }

    void announceSkill(String skillName){
        String who = (playerName != null && !playerName.isEmpty()) ? playerName : "Player " + (playerId + 1);
        gp.broadcastPlayerEvent(playerId, GameEventType.SKILL, who + " used " + skillName + "!", null);
        if(playerClass.equals("warrior"))gp.broadcastPlayerEvent(playerId,GameEventType.SKILL_EFFECT,null,skillName);
    }

    public void simulateMovement(InputState input){
        if(gp.quests.isBossIntro())return;
        if(input.ePressed || input.movementLocked) return; // matches the live rule: holding E suppresses movement that tick

        String horizDir = input.left ? "left" : (input.right ? "right" : null);
        String vertDir = input.up ? "up" : (input.down ? "down" : null);

        moveForInput(horizDir,vertDir,input.chorusSpeed,input.heavyMovement,input.tick);
    }

    private void moveForInput(String horizDir,String vertDir,boolean chorusSpeed,boolean heavyMovement,long inputTick){
        int boostedSpeed=speed+(chorusSpeed?1:0);
        if(heavyMovement)boostedSpeed=Math.max(1,boostedSpeed/2);
        boolean diagonal = (horizDir != null && vertDir != null);
        // Normalize before rounding, retaining the recent 12.5% running-speed reduction.
        // Fixed-point steps need no extra mutable state during client prediction/replay.
        int units=(int)Math.round(boostedSpeed*.875*(diagonal?Math.sqrt(.5):1)*1024);
        int phase=(int)Math.floorMod(inputTick,1024);
        int moveSpeed=((phase+1)*units)/1024-(phase*units)/1024;

        if(horizDir != null){
            direction = horizDir;
            collisionOn = false;
            gp.collisionChecker.checkTile(this,moveSpeed);
            if(!collisionOn){
                worldX += horizDir.equals("left") ? -moveSpeed : moveSpeed;
            }
        }

        if(vertDir != null){
            direction = vertDir;
            collisionOn = false;
            gp.collisionChecker.checkTile(this,moveSpeed);
            if(!collisionOn){
                worldY += vertDir.equals("up") ? -moveSpeed : moveSpeed;
            }
        }

        direction = (vertDir != null) ? vertDir : direction;
        direction = (horizDir != null) ? horizDir : direction;
    }

    public void reconcile(int authoritativeX, int authoritativeY, String authoritativeDirection,
                          int hp, int maxHp, int mp, int maxMp, boolean deadState, long ackTick){
        int previousHP = HP;

        HP = hp;
        maxHP = maxHp;
        MP = mp;
        maxMP = maxMp;
        isDead = deadState;

        if(!isDead && hp != previousHP){
            int delta = hp - previousHP;
            Color color = delta > 0 ? new Color(120, 220, 120) : Color.red;
            gp.spawnFloatingText(worldX, worldY, (delta > 0 ? "+" : "") + delta, color);
        }

        while(!inputHistory.isEmpty() && inputHistory.peekFirst().tick <= ackTick){
            inputHistory.pollFirst();
        }

        worldX = authoritativeX;
        worldY = authoritativeY;
        direction = authoritativeDirection;

        for(InputState buffered : inputHistory){
            simulateMovement(buffered);
        }
    }

    public void updateDialogueReveal(){
        if(!inDialogue) return;
        if(dialogueRevealCounter < dialogueText.length() * dialogueRevealSpeed){
            dialogueRevealCounter++;
        }
    }

    public String getRevealedDialogueText(){
        int visibleChars = Math.min(dialogueText.length(), dialogueRevealCounter / dialogueRevealSpeed);
        return dialogueText.substring(0, visibleChars);
    }

    public boolean isDialogueFullyRevealed(){
        return dialogueRevealCounter >= dialogueText.length() * dialogueRevealSpeed;
    }

    private void drawNameLabel(Graphics2D g2, int centerX, int topY){
        if(playerName == null || playerName.isEmpty()) return;

        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(playerName);
        int textX = centerX - textWidth/2;
        int textY = topY - 6;

        g2.setColor(Color.black);
        g2.drawString(playerName, textX + 1, textY + 1);
        g2.setColor(isLocal ? new Color(255, 230, 120) : Color.white);
        g2.drawString(playerName, textX, textY);
    }



    private void mageAttacking(){
        if(!hasStaff()){
            interruptForMageSpell();
            animState="idle";spriteNum=1;
            return;
        }
        direction=mage.aimDirection(mageAimX,mageAimY);
        advanceAttackFrame();
        if(spriteNum>CharacterSprites.ATTACK_FRAMES){
            spriteNum=1;attacking=false;mageProjectileFired=false;
            animState=moving?"run":"idle";
            return;
        }
        if(spriteNum==attackHitStartFrame && !mageProjectileFired){
            mage.fireBolt(mageAimX,mageAimY);
            mageProjectileFired=true;
        }
    }

    public void refreshWeaponAnimation(){
        if(USE_GENERATED_BODY){ buildGeneratedBody(); return; }
        if(!usesMana()){
            getPlayerAttackImage();
        } else {
            sprites.remove("attack_down");
            sprites.remove("attack_up");
            sprites.remove("attack_left");
            sprites.remove("attack_right");
        }
    }

    public void spawnLevelUpBurst(){
        startAura(Palette.GOLD, 90);
        Particle.burst(this, Particle.Style.SPARK, Palette.GOLD, 12, 5, 22, 3.8f);
        Particle.emit(this, Particle.Style.RING, Palette.GOLD, 24, 18, 0, 0, 0, 5);
    }

    private void interactScenery(){
        if(!currentInput.ePressed || attackCanceled) return;

        var target=gp.interactions.resolve(this);
        if(target==null)return;
        attackCanceled=true;
        if(canResolveWorldActions())gp.interactions.interact(this);
    }
}
