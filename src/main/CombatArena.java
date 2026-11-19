package main;

import entity.Player;

import monster.MON_GreenSlime;

import object.ItemRegistry;
import object.ItemType;

import progression.Talents;

import java.nio.file.Path;
import java.util.Arrays;

/** A disposable game session with its own world, player and combat simulation. */
public final class CombatArena {
    public static final int COLS = 28, ROWS = 18, START_X = 11, START_Y = 9;
    public final boolean training = Boolean.getBoolean("astral.arena");
    public boolean invulnerable = true;
    private final Gamepanel gp;
    private Process session;

    /** Binds the arena controls to one game instance. */
    CombatArena(Gamepanel gp) {
        this.gp = gp;
    }

    /** Keeps the original adventure paused while its separate practice window is alive. */
    public boolean visiting() {
        return session != null && session.isAlive();
    }

    /** Discards adventure input during practice and releases held controls before returning. */
    public boolean pauseAdventure() {
        if (session == null) return false;
        boolean running = session.isAlive();
        gp.inputQueue.clear();
        gp.keyH.upPressed = gp.keyH.downPressed = false;
        gp.keyH.leftPressed = gp.keyH.rightPressed = false;
        gp.keyH.ePressed = gp.keyH.escapePressed = false;
        Arrays.fill(gp.keyH.skillKeyPressed, false);
        gp.mouseH.leftPressed = gp.mouseH.rightPressed = false;
        gp.mouseH.leftClicked = gp.mouseH.rightClicked = false;
        gp.mouseH.wheelRotation = 0;
        if (!running) {
            int result = session.exitValue();
            session = null;
            gp.ui.addMessage(
                    result == 0
                            ? "Returned from practice."
                            : "Arena closed unexpectedly; adventure restored.");
        }
        return running;
    }

    /**
     * Starts the same game engine with an unsaved training character, or closes its practice
     * window.
     */
    public void enterOrLeave() {
        if (training) {
            gp.requestClose();
            return;
        }
        if (gp.isNetworked || visiting()) return;
        String launcher = System.getProperty("jpackage.app-path");
        ProcessBuilder builder;
        if (launcher != null && !launcher.isBlank())
            builder = new ProcessBuilder(launcher, "--combat-arena");
        else {
            String executable =
                    System.getProperty("os.name", "").startsWith("Windows") ? "javaw.exe" : "java";
            builder =
                    new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                            "-Dastral.packaged=" + AppPaths.packaged(),
                            "-cp",
                            System.getProperty("java.class.path"),
                            Main.class.getName(),
                            "--combat-arena");
        }
        try {
            session = builder.inheritIO().start();
            gp.ui.game.close();
            gp.ui.addMessage("Adventure paused while the combat arena is open.");
        } catch (java.io.IOException error) {
            gp.ui.addMessage("Could not open arena: " + error.getMessage());
        }
    }

    /** Releases a child window if the adventure itself is closed first. */
    public void closeChild() {
        if (visiting()) session.destroy();
    }

    /** Creates a fully progressed practice character without opening a save slot. */
    public void prepare() {
        Player p = gp.localPlayer();
        p.playerName = "Arena Mage";
        p.debugSetLevel(25);
        p.strength = p.dexterity = p.intelligence = p.progression.stamina = 60;
        p.meleeMasteryXP = p.magicMasteryXP = p.bowMasteryXP = 1_000_000;
        p.craftingProgress.level = crafting.RecipeRegistry.MAX_LEVEL;
        Arrays.fill(p.progression.lifeXP, 1_000_000);
        for (var t : Talents.ALL) p.progression.talents.put(t.id(), t.maxRank());
        p.progression.attributePoints = p.progression.talentPoints = 0;
        ItemType[] kit = {
            ItemType.WOODEN_STAFF, ItemType.WOODEN_SWORD, ItemType.WOODEN_BOW, ItemType.WOODEN_ARROW
        };
        for (int i = 0; i < kit.length; i++) {
            var item = ItemRegistry.create(kit[i], gp);
            if (item.stackable) item.stackCount = item.maxStackSize;
            p.inventorySlots[i] = item;
            p.hotbar.assign(p, i, kit[i]);
        }
        p.tryEquipFromBag(0, Player.EQUIP_WEAPON);
        p.setDefaultPosition();
        refill();
        opponents();
    }

    /**
     * Restores combat resources on demand so their normal costs remain measurable between resets.
     */
    public void refill() {
        Player p = gp.localPlayer();
        p.resetHPandMP();
        p.rage = p.maxRage;
        p.arcaneCharge = p.maxArcaneCharge;
        Arrays.fill(p.progression.staffCooldowns, 0);
        for (var skill : p.skills) if (skill != null) skill.cooldownRemaining = 0;
        for (var item : p.inventorySlots)
            if (ItemRegistry.typeFor(item) == ItemType.WOODEN_ARROW)
                item.stackCount = item.maxStackSize;
        if (!inventory.Hotbar.owned(p, ItemType.WOODEN_ARROW)) {
            var arrows = ItemRegistry.create(ItemType.WOODEN_ARROW, gp);
            arrows.stackCount = arrows.maxStackSize;
            p.addToInventory(arrows);
        }
    }

    /**
     * Replaces the practice wave with nearby durable targets for chains, freezes and shield tests.
     */
    public void opponents() {
        Arrays.fill(gp.monster, null);
        gp.projectileList.clear();
        for (int i = 0; i < 4; i++) {
            var slime = new MON_GreenSlime(gp);
            slime.worldX = (15 + i % 2) * gp.tileSize;
            slime.worldY = (8 + 2 * (i / 2)) * gp.tileSize;
            slime.HP = slime.maxHP = 850;
            slime.setHome();
            gp.monster[i] = slime;
        }
    }

    /** Dispatches only practice controls within the disposable room. */
    public void command(String action) {
        if (!training) {
            if (action.isEmpty() && !gp.isNetworked) enterOrLeave();
            else gp.ui.addMessage("Use /arena during solo play.");
            return;
        }
        switch (action) {
            case "level1" -> {
                Player p = gp.localPlayer();
                p.debugSetLevel(1);
                p.meleeMasteryXP = p.bowMasteryXP = p.magicMasteryXP = 0;
                p.progression.talents.clear();
                p.strength = p.dexterity = p.intelligence = 10;
                p.sword.setup(p.skills);
                refill();
            }
            case "max" -> prepare();
            case "boss" -> {
                Arrays.fill(gp.monster, null);
                var boss = new monster.MON_CrownedSlime(gp);
                boss.worldX = 16 * gp.tileSize;
                boss.worldY = 9 * gp.tileSize;
                boss.setHome();
                gp.monster[0] = boss;
            }
            case "god" -> {
                invulnerable = !invulnerable;
                gp.ui.addMessage("Arena invincibility " + (invulnerable ? "on" : "off"));
            }
            case "refill" -> {
                refill();
                gp.ui.addMessage("Combat resources refilled.");
            }
            case "wave" -> opponents();
            case "leave", "exit" -> enterOrLeave();
            default -> gp.ui.addMessage("/arena level1 · max · boss · god · refill · wave · leave");
        }
    }
}
