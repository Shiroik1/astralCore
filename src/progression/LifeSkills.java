package progression;

import art.Terrain;

import crafting.*;

import entity.*;

import object.*;

import java.util.*;

/**
 * Single-player activities and derived profession perks; no parallel inventory or world ownership.
 */
public final class LifeSkills {
    public static final String[] NAMES = {"Crafting", "Mining", "Gathering", "Cooking", "Fishing"};
    private final Player p;
    private int wait, bite, startX, startY, waterCol, waterRow;
    private boolean fishing;

    /** Creates a LifeSkills instance and initializes the state required by its collaborators. */
    public LifeSkills(Player p) {
        this.p = p;
    }

    /**
     * Returns the existing Crafting level or derives another profession level from its cumulative
     * XP.
     */
    public static int level(Player p, int skill) {
        return skill == 0
                ? p.craftingProgress.level
                : Progression.skillLevel(p.progression.lifeXP[skill - 1]);
    }

    /**
     * Counts automatic five-level profession milestones without allocating combat talent points.
     */
    public static int perks(Player p, int skill) {
        return level(p, skill) / 5;
    }

    /** Returns the player-facing description for a profession milestone. */
    public static String perk(int skill, int tier) {
        return switch (skill) {
            case 0 ->
                    switch (tier) {
                        case 1 -> "Careful Cuts: 4% material saving";
                        case 2 -> "Efficient Assembly: 8% saving";
                        case 3 -> "Experienced Builder: 12% saving";
                        case 4 -> "Expert Workshop: 16% saving";
                        default -> "Master Carpenter: 20% saving";
                    };
            case 1 ->
                    switch (tier) {
                        case 1 -> "Economy: mining costs 1 energy";
                        case 2 -> "Stonecraft: +1 node yield";
                        case 3 -> "Swift Pick: faster swings";
                        case 4 -> "Deep Reserves: +2 node yield";
                        default -> "Master Smith: +1 mining power";
                    };
            case 2 ->
                    switch (tier) {
                        case 1 -> "Endurance: gathering costs 1 energy";
                        case 2 -> "Abundance: +1 plant/wood yield";
                        case 3 -> "Quick Hands: faster swings";
                        case 4 -> "Forest Expert: +2 yield";
                        default -> "Master Gatherer: +1 tool power";
                    };
            case 3 -> "Meal/drink duration +" + (tier * 20) + "%";
            default ->
                    switch (tier) {
                        case 1 -> "Patient Angler: +1 catch";
                        case 2 -> "Quick Cast: shorter wait";
                        case 3 -> "Regional Angler: rare fish available";
                        case 4 -> "Expert Angler: wider bite window";
                        default -> "Master Angler: +2 catch";
                    };
        };
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(java.awt.Graphics2D graphics) {
        if (!fishing) return;
        Player camera = p.gp.localPlayer();
        int ts = p.gp.tileSize,
                x = waterCol * ts + ts / 2 - camera.worldX + camera.screenX,
                y = waterRow * ts + ts / 2 - camera.worldY + camera.screenY;
        java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
        g.setColor(new java.awt.Color(242, 233, 214, 170));
        g.setStroke(new java.awt.BasicStroke(1.2f));
        g.drawLine(
                p.worldX - camera.worldX + camera.screenX + 24,
                p.worldY - camera.worldY + camera.screenY + 15,
                x,
                y);
        g.setColor(bite > 0 ? art.Palette.GOLD : art.Palette.ROOF_CORAL);
        g.fillOval(x - 4, y - 5 + (int) (Math.sin(p.gp.currentTick * .1) * 2), 8, 8);
        g.setColor(art.Palette.WATER);
        g.drawOval(x - 10, y - 3, 20, 8);
        g.dispose();
    }

    /** Returns the current fishing value. */
    public boolean fishing() {
        return fishing;
    }

    /** Returns the casting instructions, waiting cue or current bite prompt for the fishing HUD. */
    public String status() {
        return !fishing
                ? "V: fish while facing adjacent water (rod in bag)"
                : bite > 0 ? "BITE! Press V to catch!" : "Fishing… wait for the bite. V cancels.";
    }

    /**
     * Starts a legal shoreline cast, cancels an early cast or resolves the current bite reaction.
     */
    public void input() {
        if (p.gp.isNetworked || p.isDead || p.inWell || p.inDialogue) return;
        if (fishing) {
            if (bite > 0) catchFish();
            else cancel("Cast cancelled.");
            return;
        }
        if (InventoryTransaction.count(p, ItemType.FISHING_ROD) < 1) {
            message("Craft a Fishing Rod first.");
            return;
        }
        int ts = p.gp.tileSize,
                col = Math.floorDiv(p.worldX + p.solidArea.x + p.solidArea.width / 2, ts),
                row = Math.floorDiv(p.worldY + p.solidArea.y + p.solidArea.height - 1, ts);
        waterCol = col + (p.direction.equals("left") ? -1 : p.direction.equals("right") ? 1 : 0);
        waterRow = row + (p.direction.equals("up") ? -1 : p.direction.equals("down") ? 1 : 0);
        if (!water()
                || p.gp.tileM.buildingElevation(col, row)
                        != p.gp.tileM.buildingElevation(waterCol, waterRow)) {
            message("Face adjacent water on your elevation.");
            return;
        }
        if (!p.progression.spendEnergy(3)) {
            message("Recover some Gathering Energy first.");
            return;
        }
        startX = p.worldX;
        startY = p.worldY;
        fishing = true;
        bite = 0;
        wait =
                90
                        + java.util.concurrent.ThreadLocalRandom.current()
                                .nextInt(Math.max(30, 150 - perks(p, 4) * 18));
        message("Line cast. Wait for a bite…");
    }

    /** Evaluates the water condition for LifeSkills. */
    private boolean water() {
        if (waterCol < 1
                || waterRow < 1
                || waterCol >= p.gp.maxWorldCol - 1
                || waterRow >= p.gp.maxWorldRow - 1) return false;
        Terrain t = p.gp.tileM.terrain(waterCol, waterRow);
        return t == Terrain.WATER || t == Terrain.DEEP;
    }

    /**
     * Advances food timers and the active fishing attempt, cancelling casts interrupted by
     * gameplay.
     */
    public void tick() {
        if (p.gp.isNetworked) return;
        if (p.progression.mealTicks > 0) p.progression.mealTicks--;
        if (p.progression.drinkTicks > 0) p.progression.drinkTicks--;
        p.progression.energy = Math.min(p.progression.energy, p.progression.maxEnergy());
        if (p.progression.drinkTicks > 0 && p.gp.currentTick % 60 == 0)
            p.MP = Math.min(p.maxMP, p.MP + 1);
        if (!fishing) return;
        if (p.isDead
                || p.inDialogue
                || p.currentInput.gameplaySuppressed
                || p.attacking
                || p.knockbackActive
                || Math.abs(p.worldX - startX) > 4
                || Math.abs(p.worldY - startY) > 4
                || !water()) {
            cancel("Fishing interrupted.");
            return;
        }
        if (bite > 0) {
            if (--bite == 0) cancel("The fish escaped.");
        } else if (--wait <= 0) {
            bite = 60 + (level(p, 4) >= 3 ? 12 : 0) + (level(p, 4) >= 20 ? 30 : 0);
            message("Bite! Press V!");
            Particle.emit(p, Particle.Style.RING, art.Palette.WATER, 20, 18, 0, 0, 0, 0);
        }
    }

    /**
     * Commits the catch to inventory before awarding Fishing XP; full bags receive neither reward.
     */
    private void catchFish() {
        int rank = perks(p, 4), count = 1 + (rank >= 1 ? 1 : 0) + (rank >= 5 ? 1 : 0);
        ItemType fish =
                level(p, 4) >= 15
                                && java.util.concurrent.ThreadLocalRandom.current().nextInt(100)
                                        < 15 + (level(p, 4) >= 18 ? 10 : 0)
                        ? ItemType.RIVER_FISH
                        : ItemType.RAW_FISH;
        if (!InventoryTransaction.commit(p.gp, p, Map.of(), Map.of(fish, count))) {
            cancel("Bag full; no fish collected.");
            return;
        }
        p.progression.life(3, 12);
        p.progression.journal.event("gather:" + fish, count);
        cancel("Caught " + count + " " + RecipeRegistry.item(fish).name() + "! +12 Fishing XP");
    }

    /** Performs the message operation for LifeSkills. */
    private void message(String text) {
        p.gp.ui.addMessage(text);
    }

    /** Cancels the active operation and clears its temporary state. */
    private void cancel(String text) {
        fishing = false;
        wait = bite = 0;
        message(text);
    }

    /** Evaluates the food condition for LifeSkills. */
    public static boolean food(ItemType type) {
        return type == ItemType.GRILLED_FISH
                || type == ItemType.HEARTY_MEAL
                || type == ItemType.HERBAL_DRINK;
    }

    /** Reports whether this component can eat for LifeSkills. */
    public boolean canEat(ItemType type) {
        return type == ItemType.HERBAL_DRINK
                ? p.progression.drinkTicks <= 0 || p.MP < p.maxMP
                : p.progression.mealTicks <= 0 || p.HP < p.maxHP;
    }

    /**
     * Applies recovery and replaces the single active meal or drink duration without stacking
     * bonuses.
     */
    public void eat(ItemType type) {
        int duration = 1800 * (100 + perks(p, 3) * 20) / 100;
        if (type == ItemType.HERBAL_DRINK) {
            p.MP = Math.min(p.maxMP, p.MP + 15);
            p.progression.drinkTicks = duration;
        } else {
            p.HP = Math.min(p.maxHP, p.HP + (type == ItemType.HEARTY_MEAL ? 40 : 20));
            p.progression.mealTicks = duration;
        }
        message(
                "Enjoyed "
                        + RecipeRegistry.item(type).name()
                        + ". One meal and one drink can be active.");
    }
}
