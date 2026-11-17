package main;

import entity.*;

import monster.*;

import world.*;
import world.generation.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Opt-in acceptance suite requested by the skeleton specification; only temporary save data. */
public final class SkeletonGameplayCheck {
    static Gamepanel g;
    static Player p;
    static int checks;

    /** Performs the check operation for SkeletonGameplayCheck. */
    static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
    }

    /** Resolves the get result used by SkeletonGameplayCheck. */
    static Object get(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    /** Performs the set operation for SkeletonGameplayCheck. */
    static void set(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }

    /** Advances this component for one simulation tick. */
    static void tick(MON_Skeleton s) {
        g.currentTick++;
        s.update();
    }

    /** Resolves the skeleton result used by SkeletonGameplayCheck. */
    static MON_Skeleton skeleton(int day) throws Exception {
        g.lighting.cycle.restore(day, 22 / 24f, 0);
        MON_Skeleton s = new MON_Skeleton(g);
        s.worldX = 50 * 48;
        s.worldY = 50 * 48;
        s.state = MON_Skeleton.State.IDLE;
        s.invincible = false;
        set(s, "timer", 0);
        g.monster[0] = s;
        p.worldX = 51 * 48;
        p.worldY = 50 * 48;
        p.direction = "left";
        p.HP = p.maxHP;
        p.invincible = false;
        p.isDead = false;
        return s;
    }

    /** Resolves the hits result used by SkeletonGameplayCheck. */
    static int hits(MON_Skeleton s, int ticks) {
        int events = 0;
        for (int i = 0; i < ticks; i++) {
            p.invincible = false;
            int hp = p.HP;
            tick(s);
            if (p.HP < hp) events++;
        }
        return events;
    }

    /** Performs the flatten operation for SkeletonGameplayCheck. */
    static void flatten() throws Exception {
        g.chunks.ensureTiles(0, 0, g.maxWorldCol - 1, g.maxWorldRow - 1);
        g.worldObjects.clear();
        Arrays.fill(g.monster, null);
        Arrays.fill(g.npc, null);
        Arrays.fill(g.obj, null);
        g.slimeNest.reset();
        g.ui.game.reset();
        g.buildings.restore("{\"version\":2,\"elements\":[],\"decorations\":[],\"cleared\":[]}");
        for (var col : (art.Terrain[][]) get(g.tileM, "terrainGrid"))
            Arrays.fill(col, art.Terrain.GRASS);
        for (var col : (byte[][]) get(g.generatedWorld.heights(), "heights"))
            Arrays.fill(col, (byte) 0);
        for (var col : (boolean[][]) get(g.generatedWorld.heights(), "playable"))
            Arrays.fill(col, true);
        var regions = g.generatedWorld.regions();
        for (String name : new String[] {"forest", "flowers", "shore"})
            for (var col : (float[][]) get(regions, name)) Arrays.fill(col, 0);
        for (var col : (int[][]) get(regions, "golden")) Arrays.fill(col, 0);
        p.worldX = p.worldY = 50 * 48;
        TerrainHeightResolver.updateCameras(g);
        g.chunks.update();
    }

    /** Performs the visual operation for SkeletonGameplayCheck. */
    static void visual() throws Exception {
        MON_Skeleton s = skeleton(1);
        check(!s.canDealContactDamage(), "contact is harmless");
        check(s.sprites.size() == 36, "all nine poses and four directions");
        BufferedImage sheet = new BufferedImage(720, 384, BufferedImage.TYPE_INT_ARGB);
        Graphics2D out = sheet.createGraphics();
        int col = 0;
        for (String pose :
                new String[] {
                    "idle", "walk", "emerge", "swipe", "lunge", "rake", "hurt", "death", "retreat"
                }) {
            int row = 0;
            for (String direction : new String[] {"down", "left", "up", "right"}) {
                var a = s.sprites.get(pose + "_" + direction);
                check(a.frames.length == 12, "cached frames " + pose + direction);
                out.drawImage(
                        a.frames[pose.equals("emerge") ? 8 : pose.equals("death") ? 8 : 4],
                        col * 80,
                        row++ * 96,
                        null);
            }
            col++;
        }
        out.dispose();
        Files.createDirectories(Path.of("assets/reference"));
        javax.imageio.ImageIO.write(
                sheet,
                "png",
                Path.of("assets/reference/skeleton-implementation-preview.png").toFile());
        MON_Skeleton other = skeleton(1);
        check(
                s.sprites.get("idle_down").frames[0] == other.sprites.get("idle_down").frames[0],
                "immutable art shared across enemies");
        double pace = (double) get(s, "paceScale");
        check(pace >= .88 && pace < 1.12, "individual pace remains modest");
        check(!get(s, "gaitFrame").equals(get(other, "gaitFrame")), "swarm gait phases differ");
        check(
                !get(s, "stridePixels").equals(get(other, "stridePixels")),
                "swarm stride lengths differ");
    }

    /** Performs the combat operation for SkeletonGameplayCheck. */
    static void combat() throws Exception {
        MON_Skeleton emerging = new MON_Skeleton(g);
        emerging.worldX = 50 * 48;
        emerging.worldY = 50 * 48;
        g.monster[0] = emerging;
        check(!emerging.beginAttack(MON_Skeleton.Attack.BONE_SWIPE, p), "cannot attack buried");
        int x = emerging.worldX, y = emerging.worldY;
        for (int i = 0; i < SkeletonConfig.EMERGE; i++) tick(emerging);
        check(
                !emerging.emerging() && emerging.worldX == x && emerging.worldY == y,
                "emergence completes stationary");
        check(
                !emerging.beginAttack(MON_Skeleton.Attack.RATTLING_LUNGE, p),
                "standing-up attack grace");
        MON_Skeleton s = skeleton(1);
        check(!s.beginAttack(MON_Skeleton.Attack.RATTLING_LUNGE, p), "day one no lunge");
        check(!s.beginAttack(MON_Skeleton.Attack.DOUBLE_RAKE, p), "day one no rake");
        check(s.beginAttack(MON_Skeleton.Attack.BONE_SWIPE, p), "swipe starts");
        check(hits(s, 30) == 1, "one swipe one damage event");
        check(s.state == MON_Skeleton.State.RECOVERING, "swipe recovery");
        check(!s.beginAttack(MON_Skeleton.Attack.BONE_SWIPE, p), "no recovery cancel");
        s = skeleton(8);
        check(s.beginAttack(MON_Skeleton.Attack.DOUBLE_RAKE, p), "rake unlocked");
        check(hits(s, 50) == 2, "rake at most two independent hit events");
        s = skeleton(8);
        s.beginAttack(MON_Skeleton.Attack.DOUBLE_RAKE, p);
        int before = p.HP;
        for (int i = 0; i < 50; i++) tick(s);
        check(p.HP < before, "first rake hit");
        check(
                before - p.HP
                        <= (int)
                                Math.round(
                                        s.baseRake
                                                * (1 + SkeletonConfig.NIGHT_DAMAGE)
                                                * (s.enraged ? 1 + SkeletonConfig.MOON_DAMAGE : 1)),
                "normal invulnerability blocks followup");
        s = skeleton(4);
        p.worldX = 53 * 48;
        s.beginAttack(MON_Skeleton.Attack.RATTLING_LUNGE, p);
        for (int i = 0; i < SkeletonConfig.LUNGE_WIND; i++) tick(s);
        p.worldY += 4 * 48;
        x = s.worldX;
        for (int i = 0; i < 30; i++) tick(s);
        check(s.worldX > x && s.worldY == 50 * 48, "lunge locks cardinal bearing");
        s = skeleton(8);
        p.worldX = 53 * 48;
        s.beginAttack(MON_Skeleton.Attack.RATTLING_LUNGE, p);
        for (int i = 0; i < 10; i++) tick(s);
        g.lighting.cycle.setHour(7);
        x = s.worldX;
        for (int i = 0; i < 60; i++) tick(s);
        check(
                s.worldX > x && s.worldY == 50 * 48,
                "moon transition preserves committed lunge timing");
        s = skeleton(4);
        p.worldX = 53 * 48;
        p.worldY = 50 * 48;
        ((art.Terrain[][]) get(g.tileM, "terrainGrid"))[52][50] = art.Terrain.WATER;
        s.beginAttack(MON_Skeleton.Attack.RATTLING_LUNGE, p);
        for (int i = 0; i < 70; i++) tick(s);
        check(s.worldX + 38 <= 52 * 48, "lunge stops at water");
        ((art.Terrain[][]) get(g.tileM, "terrainGrid"))[52][50] = art.Terrain.GRASS;
        s = skeleton(8);
        MON_Skeleton second = skeleton(8);
        g.monster[0] = s;
        g.monster[1] = second;
        check(s.beginAttack(MON_Skeleton.Attack.BONE_SWIPE, p), "group first attack");
        check(
                second.beginAttack(MON_Skeleton.Attack.BONE_SWIPE, p),
                "night group permits two attackers");
        MON_Skeleton third = new MON_Skeleton(g);
        third.worldX = s.worldX;
        third.worldY = s.worldY;
        third.state = MON_Skeleton.State.IDLE;
        g.monster[2] = third;
        check(
                !third.beginAttack(MON_Skeleton.Attack.BONE_SWIPE, p),
                "night group limits simultaneous attackers");
        g.monster[1] = g.monster[2] = null;
    }

    /** Performs the combat regression operation for SkeletonGameplayCheck. */
    static void combatRegression() throws Exception {
        MON_Skeleton s = skeleton(1);
        check(s.beginAttack(MON_Skeleton.Attack.BONE_SWIPE, p), "committed swipe fixture");
        s.reactToHit("left", .15);
        s.startKnockback("left", 12);
        check(s.committed() && !s.knockbackActive, "basic hit cannot interrupt committed swipe");
        s = skeleton(1);
        int hp = p.HP;
        for (int i = 0; i < 180; i++) {
            s.reactToHit("left", 0);
            tick(s);
        }
        check(p.HP < hp, "continuous ordinary impacts still allow retaliation");
        MON_GreenSlime slime = new MON_GreenSlime(g);
        slime.worldX = 50 * 48;
        slime.worldY = 50 * 48;
        slime.state = MON_GreenSlime.State.WINDUP;
        g.monster[0] = slime;
        for (int i = 0; i < MON_GreenSlime.WINDUP_TICKS; i++) {
            slime.reactToHit("left", .15);
            g.currentTick++;
            slime.update();
        }
        check(
                slime.state == MON_GreenSlime.State.LEAP_ATTACK,
                "slime windup survives continuous ordinary impacts");
        s = skeleton(1);
        g.lighting.cycle.restore(2, 12 / 24f, 0);
        for (int i = 0; i < SkeletonConfig.DAYLIGHT_GRACE + 180; i++) {
            p.HP = p.maxHP;
            tick(s);
        }
        check(!s.departing && s.alive, "daylight does not bury engaged skeleton");
        g.lighting.cycle.setHour(22);
        set(s, "daylightTicks", SkeletonConfig.DAYLIGHT_GRACE);
        tick(s);
        check(
                (int) get(s, "daylightTicks") == 0 && !s.departing,
                "night clears stale daylight retreat timer");
    }

    /** Performs the calendar and scaling operation for SkeletonGameplayCheck. */
    static void calendarAndScaling() throws Exception {
        var c = g.lighting.cycle;
        c.restore(8, 22 / 24f, 0);
        check(c.fullMoon(8), "day eight full moon");
        check(c.claimMoonWarning(8) && !c.claimMoonWarning(8), "one warning per moon night");
        c.setTime(1 - .5f / DayCycle.TICKS_PER_DAY);
        c.tick();
        check(c.day() == 9 && c.fullMoon(8), "full moon extends across midnight");
        c.setHour(7);
        check(!c.fullMoon(8), "sunrise ends enrage");
        MON_Skeleton a = skeleton(21), b = skeleton(100);
        check(
                a.baseHealth == b.baseHealth && a.baseLunge == b.baseLunge,
                "growth capped after twenty scaled days");
        int base = b.maxHP;
        b.HP = base / 2;
        c.restore(24, 22 / 24f, 0);
        b.refreshEnrage();
        int moon = b.maxHP, hp = b.HP;
        check(moon > base, "moon health bonus");
        b.refreshEnrage();
        check(b.maxHP == moon && b.HP == hp, "moon modifiers idempotent");
        c.setHour(8);
        b.refreshEnrage();
        check(
                b.maxHP == base && Math.abs(b.HP - base / 2) <= 1,
                "dawn restores scaled maximum without reset/heal");
        c.restore(12, 23 / 24f, 8);
        var snapshot = net.SnapshotBuilder.build(g);
        check(snapshot.worldDay == 12 && snapshot.moonWarningDay == 8, "clock and warning saved");
        var state = new net.MonsterState();
        b.capture(state);
        state.HP = b.HP;
        state.maxHP = b.maxHP;
        MON_Skeleton restored = new MON_Skeleton(g);
        restored.restore(state);
        check(
                restored.spawnDay == b.spawnDay && restored.HP == b.HP,
                "encounter scaling and HP restored");
    }

    /** Performs the spawning operation for SkeletonGameplayCheck. */
    static void spawning() throws Exception {
        Arrays.fill(g.monster, null);
        g.slimeSpawner.resetPopulation();
        set(g.slimeSpawner, "clock", 10000L);
        set(g.slimeSpawner, "respawnUntil", 0L);
        set(g.slimeSpawner, "quietUntil", 0L);
        g.lighting.cycle.restore(2, 12 / 24f, 0);
        g.slimeSpawner.skeletonCommand("now");
        check(g.slimeSpawner.nearbySkeletons(p) == 0, "no open-daylight spawns");
        for (var col : (float[][]) get(g.generatedWorld.regions(), "forest")) Arrays.fill(col, 1);
        for (int y = 46; y <= 54; y += 2)
            for (int x = 46; x <= 54; x += 2)
                g.worldObjects.add(new WorldObject(g, WorldObjectType.OAK_1, x, y, null));
        check(g.slimeSpawner.skeletonShade(50 * 48, 50 * 48), "dense tree canopy qualifies");
        g.worldObjects.clear();
        check(!g.slimeSpawner.skeletonShade(50 * 48, 50 * 48), "cleared canopy invalidates cache");
        g.lighting.cycle.restore(2, 22 / 24f, 0);
        boolean found = false;
        for (int i = 0; i < 8; i++) {
            g.slimeSpawner.skeletonCommand("now");
            if (g.slimeSpawner.nearbySkeletons(p) > 0) {
                found = true;
                break;
            }
        }
        check(found, "night emergence succeeds around player");
        for (Monster m : g.monster)
            if (m instanceof MON_Skeleton skeleton) {
                check(skeleton.emerging(), "spawn starts buried");
                check(g.chunks.activeAt(m.worldX, m.worldY), "spawn active chunk");
                double radius =
                        Math.hypot(
                                Math.floorDiv(m.worldX, 48)
                                        - Math.floorDiv(SlimeMotion.centerX(p), 48),
                                Math.floorDiv(m.worldY, 48)
                                        - Math.floorDiv(SlimeMotion.centerY(p), 48));
                check(
                        radius >= SkeletonConfig.EMERGE_MIN && radius <= SkeletonConfig.EMERGE_MAX,
                        "night emergence stays in safe player ring");
            }
        for (int i = 0; i < 12; i++) g.slimeSpawner.skeletonCommand("now");
        check(g.slimeSpawner.nearbySkeletons(p) <= SkeletonConfig.NIGHT_CAP, "skeleton cap");
        check(g.slimeSpawner.combined(p) <= SkeletonConfig.NIGHT_COMBINED_CAP, "combined cap");
        MON_Skeleton s = skeleton(1);
        g.lighting.cycle.restore(2, 12 / 24f, 0);
        p.worldX = 80 * 48;
        p.worldY = 80 * 48;
        s.worldX = 50 * 48;
        s.worldY = 50 * 48;
        for (int i = 0; i < SkeletonConfig.DAYLIGHT_GRACE + 120; i++) tick(s);
        check(s.departing && !s.alive, "open daylight withdraws without rewards");
    }

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws Exception {
        System.setProperty("astral.dev", "true");
        System.setProperty(
                "astral.dataDir", Files.createTempDirectory("astral-skeleton-check-").toString());
        g = new Gamepanel();
        g.setUpGame();
        g.gameState = g.playState;
        p = g.localPlayer();
        flatten();
        visual();
        combat();
        combatRegression();
        calendarAndScaling();
        spawning();
        System.out.println("SKELETON CHECKS PASS: " + checks);
    }
}
