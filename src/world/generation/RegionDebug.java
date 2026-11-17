package world.generation;

import main.Gamepanel;

import java.awt.*;

/** Opt-in diagnostics, never active in a regular launch. No generation or asset loading in draw. */
public final class RegionDebug {
    private static final boolean ENABLED =
            Boolean.getBoolean("astral.dev") && Boolean.getBoolean("astral.regions.debug");
    private static final boolean DENSITY = Boolean.getBoolean("astral.regions.debugDensity");
    private static final Color[] COLORS = {
        new Color(110, 156, 108, 75),
        new Color(227, 159, 188, 115),
        new Color(54, 110, 91, 115),
        new Color(102, 186, 207, 125),
        new Color(232, 190, 74, 140)
    };

    /** Prevents instantiation of this utility class. */
    private RegionDebug() {}

    /** Renders this component into the supplied Java2D graphics context. */
    public static void draw(Gamepanel gp, Graphics2D original) {
        if (!ENABLED
                || gp.generatedWorld == null
                || gp.generatedWorld.regions() == null
                || gp.quests.inDungeon()) return;
        var map = gp.generatedWorld.regions();
        var p = gp.localPlayer();
        if (p == null) return;
        int ts = gp.tileSize;
        int left = Math.max(0, (p.worldX - p.screenX) / ts),
                top = Math.max(0, (p.worldY - p.screenY) / ts);
        int right = Math.min(map.width - 1, (p.worldX - p.screenX + gp.screenWidth) / ts + 1),
                bottom =
                        Math.min(map.height - 1, (p.worldY - p.screenY + gp.screenHeight) / ts + 1);
        var g = (Graphics2D) original.create();
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                if (!PlacementRules.walkableTerrain(gp.generatedWorld.terrain(x, y))) continue;
                if (DENSITY) {
                    int alpha = (int) (40 + 140 * map.vegetationDensity(x, y));
                    g.setColor(new Color(172, 105, 214, alpha));
                } else g.setColor(COLORS[map.dominant(x, y).ordinal()]);
                g.fillRect(x * ts - p.worldX + p.screenX, y * ts - p.worldY + p.screenY, ts, ts);
            }
        g.dispose();
    }

    /** Performs the log operation for RegionDebug. */
    static void log(GeneratedWorld w) {
        if (!ENABLED) return;
        var counts = new int[EnvironmentRegion.values().length];
        for (int y = 0; y < w.config.height(); y++)
            for (int x = 0; x < w.config.width(); x++)
                if (w.terrain(x, y) == art.Terrain.GRASS)
                    counts[w.regions().dominant(x, y).ordinal()]++;
        System.out.println(
                "[REGIONS] seed="
                        + w.config.seed()
                        + " v"
                        + w.generatorVersion
                        + " grass/flower/forest/shore/gold="
                        + java.util.Arrays.toString(counts));
    }
}
