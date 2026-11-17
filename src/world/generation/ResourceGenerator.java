package world.generation;

import java.util.*;

/**
 * Stable coordinate-addressed pickups; progression guarantees are separate from ecological
 * densities.
 */
final class ResourceGenerator {
    /** Performs the generate operation for ResourceGenerator. */
    void generate(GeneratedWorld w, NoiseGenerator n, PlacementRules rules) {
        var resources = new ArrayList<GeneratedWorld.Resource>();
        var reachable = rules.reachable();
        int width = w.config.width();
        var start = new ArrayList<Integer>();
        for (int y = 1; y < w.config.height() - 1; y++)
            for (int x = 1; x < width - 1; x++) {
                double distance = Math.hypot(x - w.spawnCol, y - w.spawnRow);
                if (distance > 0 && distance <= 3 && reachable[x][y] && w.reserved(x, y))
                    start.add(y * width + x);
            }
        start.sort(
                Comparator.comparingDouble((Integer p) ->
                                Math.hypot(p % width - w.spawnCol, p / width - w.spawnRow))
                        .thenComparingInt(Integer::intValue));
        if (start.size() < 12)
            throw new IllegalStateException("Starter reservation has insufficient walkable ground");
        for (int i = 0; i < 12; i++) {
            int p = start.get(i);
            resources.add(
                    new GeneratedWorld.Resource(i < 7 ? "twig" : "stone", p % width, p / width));
        }
        // Natural loose twigs/stones are already existing harvestable scenery in the ecological
        // passes.
        for (int y = 1; y < w.config.height() - 1; y++)
            for (int x = 1; x < width - 1; x++) {
                if (!reachable[x][y]
                        || w.reserved(x, y)
                        || w.regions().golden(x, y) > 0
                        || !rules.meadowFits(x, y, false)) continue;
                if (w.regions().forest(x, y) > .45
                        && n.sample(x * .18, y * .18, 895) > .55
                        && n.random(x, y, 896) < .008)
                    resources.add(new GeneratedWorld.Resource("iron", x, y));
            }
        w.resources(resources);
    }
}
