package main;

import entity.*;

import jdk.jfr.*;
import jdk.jfr.consumer.*;

import object.*;

import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Explicitly requested reproducible software-render CPU/JFR probe. No real saves are opened. */
public final class PerformanceProbe {
    /** Resolves the field result used by PerformanceProbe. */
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }

    /** Performs the field operation for PerformanceProbe. */
    private static void field(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(object, value);
    }

    /** Performs the row operation for PerformanceProbe. */
    private static void row(String label, long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(samples).average().orElse(0) / 1e6;
        System.out.printf(
                Locale.ROOT,
                "TIMING %s mean=%.3f p95=%.3f max=%.3f ms%n",
                label,
                mean,
                sorted[Math.min(sorted.length - 1, (int) (sorted.length * .95))] / 1e6,
                sorted[sorted.length - 1] / 1e6);
    }

    /** Performs the sample operation for PerformanceProbe. */
    private static void sample(Gamepanel g, String scenario, int frames) throws Exception {
        long[] updates = new long[frames],
                renders = new long[frames],
                interactions = new long[frames];
        for (int i = 0; i < 12; i++) {
            g.update();
            g.drawToTempscreen();
        }
        for (int i = 0; i < frames; i++) {
            long at = System.nanoTime();
            g.update();
            updates[i] = System.nanoTime() - at;
            at = System.nanoTime();
            g.drawToTempscreen();
            renders[i] = System.nanoTime() - at;
            at = System.nanoTime();
            g.interactions.refresh();
            interactions[i] = System.nanoTime() - at;
        }
        row(scenario + ".update", updates);
        row(scenario + ".render_including_ui", renders);
        row(scenario + ".isolated_interaction", interactions);
        double mean =
                (Arrays.stream(updates).average().orElse(0)
                                + Arrays.stream(renders).average().orElse(0))
                        / 1e6;
        System.out.printf(
                Locale.ROOT,
                "THROUGHPUT %s %.1f work_frames_per_second (not display FPS)%n",
                scenario,
                1000 / mean);
    }

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws Exception {
        System.setProperty(
                "astral.dataDir", Files.createTempDirectory("astral-profile-").toString());
        int frames = args.length > 1 ? Integer.parseInt(args[1]) : 90;
        String label = args.length > 0 ? args[0] : "probe";
        Gamepanel g = new Gamepanel();
        g.setUpGame();
        g.gameState = g.playState;
        g.localPlayer().playerName = "Profile";
        g.setSize(1248, 720);
        g.crafting.ensureWorld();
        System.out.println(
                "PROFILE "
                        + label
                        + " java="
                        + System.getProperty("java.version")
                        + " world="
                        + g.maxWorldCol
                        + "x"
                        + g.maxWorldRow
                        + " scenery="
                        + g.worldObjects.size()
                        + " resourceNodes="
                        + ((Map<?, ?>) field(g.crafting, "nodes")).size());
        Path recording = Path.of("/tmp/astral-" + label + ".jfr");
        try (Recording jfr = new Recording()) {
            jfr.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
            jfr.enable("jdk.ObjectAllocationSample");
            jfr.enable("jdk.GarbageCollection");
            jfr.start();
            sample(g, "idle", frames);
            g.localPlayer().currentWeapon = ItemRegistry.create(ItemType.WOODEN_AXE, g);
            g.keyH.rightPressed = true;
            sample(g, "forest_walk_axe", frames);
            g.keyH.rightPressed = false;
            g.ui.game.open(ui.GameUI.Window.INVENTORY);
            sample(g, "inventory", Math.max(20, frames / 2));
            g.ui.game.reset();
            if (Boolean.getBoolean("astral.dev")) {
                field(g.slimeSpawner, "clock", 10000L);
                field(g.slimeSpawner, "respawnUntil", 0L);
                for (int row = g.maxWorldRow / 3; row < g.maxWorldRow - 12; row += 8) {
                    g.localPlayer().worldX = g.maxWorldCol * 48 / 2;
                    g.localPlayer().worldY = row * 48;
                    world.TerrainHeightResolver.updateCameras(g);
                    g.chunks.update();
                    long at = System.nanoTime();
                    g.slimeSpawner.debugCommand("now");
                    System.out.printf(
                            Locale.ROOT,
                            "SPAWN %.3f ms %s%n",
                            (System.nanoTime() - at) / 1e6,
                            g.slimeSpawner.debug.result);
                    if (g.slimeSpawner.debug.spawned > 0) break;
                }
            }
            jfr.stop();
            jfr.dump(recording);
        }
        Map<String, Integer> cpu = new HashMap<>();
        Map<String, Long> allocation = new HashMap<>();
        long gc = 0;
        try (RecordingFile events = new RecordingFile(recording)) {
            while (events.hasMoreEvents()) {
                RecordedEvent event = events.readEvent();
                String type = event.getEventType().getName();
                if (type.equals("jdk.GarbageCollection")) {
                    gc++;
                    continue;
                }
                var stack = event.getStackTrace();
                if (stack == null || stack.getFrames().isEmpty()) continue;
                if (type.equals("jdk.ExecutionSample")) {
                    RecordedThread thread = event.getThread("sampledThread");
                    if (!thread.getJavaName().equals("main")) continue;
                    var frame = stack.getFrames().getFirst();
                    String key =
                            frame.getMethod().getType().getName()
                                    + "."
                                    + frame.getMethod().getName();
                    String owner =
                            stack.getFrames().stream()
                                    .filter(f ->
                                            f.getMethod()
                                                            .getType()
                                                            .getName()
                                                            .startsWith("crafting.")
                                                    || f.getMethod()
                                                            .getType()
                                                            .getName()
                                                            .startsWith("interaction.")
                                                    || f.getMethod()
                                                            .getType()
                                                            .getName()
                                                            .startsWith("world."))
                                    .findFirst()
                                    .map(f ->
                                            " via "
                                                    + f.getMethod().getType().getName()
                                                    + "."
                                                    + f.getMethod().getName())
                                    .orElse("");
                    cpu.merge(key + owner, 1, Integer::sum);
                } else if (type.equals("jdk.ObjectAllocationSample")) {
                    var klass = event.getClass("objectClass");
                    String owner =
                            stack.getFrames().stream()
                                    .filter(f ->
                                            !f.getMethod().getType().getName().startsWith("java.")
                                                    && !f.getMethod()
                                                            .getType()
                                                            .getName()
                                                            .startsWith("jdk."))
                                    .findFirst()
                                    .map(f ->
                                            f.getMethod().getType().getName()
                                                    + "."
                                                    + f.getMethod().getName())
                                    .orElse("JDK");
                    allocation.merge(
                            klass.getName() + " via " + owner, event.getLong("weight"), Long::sum);
                }
            }
        }
        cpu.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(18)
                .forEach(e -> System.out.println("CPU_SAMPLES " + e));
        allocation.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(12)
                .forEach(e -> System.out.println("ALLOCATION_WEIGHT " + e));
        for (var section : PerformanceMetrics.Section.values())
            System.out.printf(
                    Locale.ROOT,
                    "SECTION %s mean=%.3f peak=%.3f ms%n",
                    section,
                    g.performance.mean(section),
                    g.performance.peak(section));
        System.out.println(
                "CACHE loaded="
                        + g.chunks.loaded()
                        + " active="
                        + g.chunks.active()
                        + " images="
                        + g.tileM.cachedChunks()
                        + " imageMiB="
                        + g.tileM.cacheBytes() / 1048576);
        System.out.println(
                "GC_EVENTS "
                        + gc
                        + " HEAP_MB "
                        + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                                / 1048576
                        + " JFR "
                        + recording);
    }
}
