package world;

import entity.Entity;
import entity.Player;

import main.Gamepanel;

import monster.SlimeMotion;

import java.awt.Rectangle;
import java.util.concurrent.ThreadLocalRandom;

/** Cosmetic burst and authoritative collection of items in the existing ground-item array. */
public final class GroundLoot {
    /** Prevents instantiation of this utility class. */
    private GroundLoot() {}

    private static final int LAND_TICKS = 24;
    private static final double PICKUP_RADIUS_TILES = 2.5;

    /** Optional fields default to zero/false for older saves and snapshots. */
    public static void restore(Entity item, net.ObjectState state) {
        item.groundLoot = state.groundLoot;
        item.lootAge = Math.clamp(state.lootAge, 0, LAND_TICKS);
        item.lootVX = Double.isFinite(state.lootVX) ? Math.clamp(state.lootVX, -8, 8) : 0;
        item.lootVY = Double.isFinite(state.lootVY) ? Math.clamp(state.lootVY, -8, 8) : 0;
        item.lootLift = Float.isFinite(state.lootLift) ? Math.clamp(state.lootLift, 0, 18) : 0;
    }

    /** Performs the launch operation for GroundLoot. */
    public static void launch(Entity item) {
        if (item.down1 != null)
            art.ItemOutline.prepare(item.down1, item.down1.getWidth(), item.down1.getHeight());
        var random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2), speed = random.nextDouble(1.8, 3.2);
        item.groundLoot = true;
        item.lootAge = 0;
        item.lootLift = 0;
        item.lootVX = Math.cos(angle) * speed;
        item.lootVY = Math.sin(angle) * speed;
    }

    /** Resolves the x result used by GroundLoot. */
    private static int x(Entity e) {
        return e.worldX + e.gp.tileSize / 2;
    }

    /** Resolves the y result used by GroundLoot. */
    private static int y(Entity e) {
        return e.worldY + e.gp.tileSize / 2;
    }

    /**
     * Samples the loot movement segment and rejects blocked terrain or changes in logical
     * elevation.
     */
    private static boolean path(Gamepanel g, int x, int y, int tx, int ty) {
        if (x < 0 || y < 0 || x >= g.maxWorldCol * g.tileSize || y >= g.maxWorldRow * g.tileSize)
            return false;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(tx - x, ty - y) / 4));
        int level =
                g.tileM.buildingElevation(
                        Math.floorDiv(x, g.tileSize), Math.floorDiv(y, g.tileSize));
        for (int i = 1; i <= steps; i++) {
            int px = x + (tx - x) * i / steps, py = y + (ty - y) * i / steps;
            if (px < 0
                    || py < 0
                    || px >= g.maxWorldCol * g.tileSize
                    || py >= g.maxWorldRow * g.tileSize) return false;
            if (!g.quests.inDungeon()
                    && g.tileM.buildingElevation(px / g.tileSize, py / g.tileSize) != level)
                return false;
            if (!SlimeMotion.clear(g, new Rectangle(px - 3, py - 3, 6, 6))) return false;
        }
        return true;
    }

    /** Reports whether this component has room for GroundLoot. */
    public static boolean hasRoom(Player player, Entity item) {
        if (item.type == Entity.type_pickuponly) return true;
        int remaining = item.stackCount;
        for (Entity slot : player.inventorySlots) {
            if (slot == null) return true;
            if (item.stackable && slot.canStackWith(item))
                remaining -= Math.max(0, slot.maxStackSize - slot.stackCount);
            if (remaining <= 0) return true;
        }
        return false;
    }

    /** Reports whether this component can collect for GroundLoot. */
    public static boolean canCollect(Gamepanel g, Player player, Entity item) {
        return item.groundLoot
                && item.lootAge >= LAND_TICKS
                && !item.pickupBlocked
                && SlimeMotion.activePlayer(g, player)
                && !player.inDialogue
                && !player.merchantOpen
                && Math.hypot(
                                x(item) - SlimeMotion.centerX(player),
                                y(item) - SlimeMotion.centerY(player))
                        <= 12
                && path(
                        g,
                        x(item),
                        y(item),
                        SlimeMotion.centerX(player),
                        SlimeMotion.centerY(player))
                && hasRoom(player, item);
    }

    /**
     * Advances active loot through its landing burst and collision-aware attraction to a player
     * with inventory space.
     */
    public static void update(Gamepanel g) {
        if (g.isNetworked && !g.isHost || g.quests.isBossIntro()) return;
        for (int slot = 0; slot < g.obj.length; slot++) {
            Entity item = g.obj[slot];
            if (item == null || !item.groundLoot || item.pickupBlocked) continue;
            if (!g.quests.inDungeon() && !g.chunks.activeAt(item.worldX, item.worldY)) continue;
            if (item.lootAge < LAND_TICKS) {
                item.lootAge++;
                int dx = (int) Math.round(item.lootVX), dy = (int) Math.round(item.lootVY);
                if (path(g, x(item), y(item), x(item) + dx, y(item) + dy)) {
                    item.worldX += dx;
                    item.worldY += dy;
                } else {
                    item.lootVX = 0;
                    item.lootVY = 0;
                }
                item.lootVX *= .91;
                item.lootVY *= .91;
                item.lootLift = (float) (Math.sin(item.lootAge * Math.PI / LAND_TICKS) * 18);
                continue;
            }
            Player nearest = null;
            double best = g.tileSize * PICKUP_RADIUS_TILES;
            for (Player player : g.players) {
                if (!SlimeMotion.activePlayer(g, player)
                        || player.inDialogue
                        || player.merchantOpen) continue;
                double distance =
                        Math.hypot(
                                x(item) - SlimeMotion.centerX(player),
                                y(item) - SlimeMotion.centerY(player));
                if (distance < best
                        && hasRoom(player, item)
                        && path(
                                g,
                                x(item),
                                y(item),
                                SlimeMotion.centerX(player),
                                SlimeMotion.centerY(player))) {
                    nearest = player;
                    best = distance;
                }
            }
            if (nearest == null) {
                item.lootLift = 0;
                item.lootVX = item.lootVY = 0;
                continue;
            }
            if (best <= 12) {
                nearest.pickUpFlyingLoot(slot);
                continue;
            }
            double speed = Math.min(8, Math.hypot(item.lootVX, item.lootVY) + .65);
            item.lootVX = (SlimeMotion.centerX(nearest) - x(item)) / best * speed;
            item.lootVY = (SlimeMotion.centerY(nearest) - y(item)) / best * speed;
            int dx = (int) Math.round(item.lootVX), dy = (int) Math.round(item.lootVY);
            if (path(g, x(item), y(item), x(item) + dx, y(item) + dy)) {
                item.worldX += dx;
                item.worldY += dy;
                item.lootLift = Math.min(8, item.lootLift + 1);
            }
        }
    }
}
