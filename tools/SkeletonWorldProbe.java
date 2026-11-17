package main;

import entity.*;

import monster.*;

import world.*;
import world.generation.*;

import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/**
 * Actual generated habitat, snapshot/disk and steady update costs, kept separate from fixture
 * tests.
 */
public final class SkeletonWorldProbe {
    /** Performs the set operation for SkeletonWorldProbe. */
    static void set(Object o, String n, Object v) throws Exception {
        var f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        f.set(o, v);
    }

    /** Performs the check operation for SkeletonWorldProbe. */
    static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws Exception {
        if (args.length > 0) System.setProperty("astral.world.seed", args[0]);
        System.setProperty("astral.dev", "true");
        System.setProperty(
                "astral.dataDir", Files.createTempDirectory("astral-skeleton-world-").toString());
        Gamepanel g = new Gamepanel();
        g.setUpGame();
        g.gameState = g.playState;
        var p = g.localPlayer();
        var disk = new save.SaveStore(g, Files.createTempDirectory("astral-skeleton-disk-"));
        disk.create(0, "Skeleton", 0, 0);
        p = g.localPlayer();
        g.gameState = g.playState;
        set(g.slimeSpawner, "clock", 10000L);
        set(g.slimeSpawner, "respawnUntil", 0L);
        g.lighting.cycle.restore(8, 22 / 24f, 0);
        boolean spawned = false;
        int dense = 0;
        outer:
        for (int y = 12; y < g.maxWorldRow - 12; y += 6)
            for (int x = 12; x < g.maxWorldCol - 12; x += 6) {
                p.worldX = x * 48;
                p.worldY = y * 48;
                TerrainHeightResolver.updateCameras(g);
                g.chunks.update();
                if (g.generatedWorld.regions().dominant(x, y) == EnvironmentRegion.FOREST_GROVE) {
                    boolean shaded = g.slimeSpawner.skeletonShade(x * 48, y * 48);
                    if (shaded) dense++;
                }
                if (!SlimeMotion.clear(g, SlimeMotion.body(p, p.worldX, p.worldY))) continue;
                System.out.println(
                        "TRY " + x + "," + y + " " + g.slimeSpawner.skeletonCommand("now"));
                if (g.slimeSpawner.nearbySkeletons(p) > 0) {
                    spawned = true;
                    break outer;
                }
            }
        check(
                spawned,
                "night encounters in actual generated terrain; last="
                        + g.slimeSpawner.skeletonResult);
        MON_Skeleton skeleton =
                Arrays.stream(g.monster)
                        .filter(m -> m instanceof MON_Skeleton)
                        .map(m -> (MON_Skeleton) m)
                        .findFirst()
                        .orElseThrow();
        int birth = skeleton.spawnDay;
        for (int i = 0; i < 200; i++) {
            g.update();
            g.drawToTempscreen();
        }
        skeleton.HP = Math.max(1, skeleton.HP / 2);
        int hp = skeleton.HP;
        g.lighting.cycle.restore(8, 23 / 24f, 8);
        check(disk.saveActive(), "disk write");
        disk.load(0);
        p = g.localPlayer();
        check(g.lighting.cycle.day() == 8 && g.lighting.cycle.fullMoon(8), "day and moon saved");
        check(!g.lighting.cycle.claimMoonWarning(8), "no duplicate warning after load");
        MON_Skeleton restored =
                Arrays.stream(g.monster)
                        .filter(m -> m instanceof MON_Skeleton)
                        .map(m -> (MON_Skeleton) m)
                        .findFirst()
                        .orElseThrow();
        check(restored.HP == hp && restored.spawnDay == birth, "skeleton encounter HP/day saved");
        check(
                Arrays.stream(g.monster).filter(m -> m instanceof MON_Skeleton).count()
                        <= SkeletonConfig.NIGHT_MOON_CAP,
                "no load duplicates");
        long[] update = new long[180], render = new long[180];
        g.performance.reset();
        for (int i = 0; i < update.length; i++) {
            p.HP = p.maxHP;
            long at = System.nanoTime();
            g.update();
            update[i] = System.nanoTime() - at;
            at = System.nanoTime();
            g.drawToTempscreen();
            render[i] = System.nanoTime() - at;
        }
        Arrays.sort(update);
        Arrays.sort(render);
        System.out.printf(
                Locale.ROOT,
                "SKELETON PERFORMANCE update mean=%.3f p95=%.3f max=%.3f; render mean=%.3f p95=%.3f"
                        + " max=%.3f ms%n",
                Arrays.stream(update).average().orElse(0) / 1e6,
                update[171] / 1e6,
                update[179] / 1e6,
                Arrays.stream(render).average().orElse(0) / 1e6,
                render[171] / 1e6,
                render[179] / 1e6);
        // Daytime qualification measured on real forest canopies, without changing the terrain
        // generator.
        set(g.slimeSpawner, "clock", 10000L);
        set(g.slimeSpawner, "respawnUntil", 0L);
        set(g.slimeSpawner, "quietUntil", 0L);
        g.lighting.cycle.restore(9, 12 / 24f, 8);
        dense = 0;
        int forest = 0;
        boolean daySpawn = false;
        for (int y = 10; y < g.maxWorldRow - 10; y += 4)
            for (int x = 10; x < g.maxWorldCol - 10; x += 4) {
                if (g.generatedWorld.regions().dominant(x, y) != EnvironmentRegion.FOREST_GROVE)
                    continue;
                forest++;
                p.worldX = x * 48;
                p.worldY = y * 48;
                TerrainHeightResolver.updateCameras(g);
                g.chunks.update();
                if (g.slimeSpawner.skeletonShade(x * 48, y * 48)) {
                    dense++;
                    if (!daySpawn
                            && SlimeMotion.clear(g, SlimeMotion.body(p, p.worldX, p.worldY))) {
                        g.slimeSpawner.skeletonCommand("now");
                        daySpawn = g.slimeSpawner.skeletonResult.startsWith("SUCCESS");
                    }
                }
            }
        System.out.println(
                "REAL FOREST SHADE "
                        + dense
                        + " / "
                        + forest
                        + " sampled forest cells exceed configured coverage");
        check(dense > 0, "real procedural forests support daytime habitat");
        check(daySpawn, "actual dense forest daytime emergence: " + g.slimeSpawner.skeletonResult);
        System.out.println("DAYTIME DENSE FOREST SPAWN SUCCESS");
        System.out.println("SKELETON WORLD/SAVE/PERFORMANCE CHECKS PASS");
    }
}
