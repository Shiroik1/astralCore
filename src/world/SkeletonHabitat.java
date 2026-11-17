package world;

import main.Gamepanel;

import monster.SkeletonConfig;

import world.generation.EnvironmentRegion;

import java.awt.*;
import java.util.*;

/** Bounded, short-lived tile cache. Only scheduled spawn/daylight checks call this. */
public final class SkeletonHabitat {
    private record Shade(long tick, long revision, double coverage) {}

    private final Gamepanel g;
    private final Map<Long, Shade> cache = new LinkedHashMap<>(64, .75f, true);
    public long calculations;

    /**
     * Creates a SkeletonHabitat instance and initializes the state required by its collaborators.
     */
    public SkeletonHabitat(Gamepanel g) {
        this.g = g;
    }

    /** Restores this component to its initial runtime state. */
    public void reset() {
        cache.clear();
        calculations = 0;
    }

    /** Resolves the coverage result used by SkeletonHabitat. */
    public double coverage(int col, int row) {
        if (g.generatedWorld == null
                || g.generatedWorld.regions() == null
                || col < 0
                || row < 0
                || col >= g.maxWorldCol
                || row >= g.maxWorldRow
                || g.generatedWorld.regions().dominant(col, row) != EnvironmentRegion.FOREST_GROVE)
            return 0;
        long key = world.spatial.SpatialIndex.key(col, row);
        Shade old = cache.get(key);
        if (old != null
                && g.currentTick - old.tick < 120
                && old.revision == g.worldObjects.revision()) return old.coverage;
        int r = SkeletonConfig.FOREST_RADIUS, ts = g.tileSize;
        Rectangle area =
                new Rectangle((col - r) * ts, (row - r) * ts, (r * 2 + 1) * ts, (r * 2 + 1) * ts);
        var trees =
                g.worldObjects.query(area).stream()
                        .filter(o -> o.kind.swaysInWind() && !o.resourceDepleted)
                        .map(WorldObject::spatialBounds)
                        .toList();
        int shaded = 0, total = 0;
        for (int y = row - r; y <= row + r; y++)
            for (int x = col - r; x <= col + r; x++) {
                total++;
                int px = x * ts + ts / 2, py = y * ts + ts / 2;
                for (Rectangle tree : trees)
                    if (tree.contains(px, py)) {
                        shaded++;
                        break;
                    }
            }
        double value = shaded / (double) total;
        cache.put(key, new Shade(g.currentTick, g.worldObjects.revision(), value));
        if (cache.size() > 1024) cache.remove(cache.keySet().iterator().next());
        calculations++;
        return value;
    }

    /** Evaluates the shaded condition for SkeletonHabitat. */
    public boolean shaded(int x, int y) {
        return coverage(Math.floorDiv(x, g.tileSize), Math.floorDiv(y, g.tileSize))
                >= SkeletonConfig.FOREST_COVER;
    }
}
