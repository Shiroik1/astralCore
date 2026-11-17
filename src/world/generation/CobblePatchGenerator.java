package world.generation;

import art.Terrain;

import java.util.ArrayList;
import java.util.Comparator;

/** Irregular remnants of paving, using the same connected moss-cobble tiles as the trails. */
final class CobblePatchGenerator {
    private record Candidate(int col, int row, double priority) {}

    /** Performs the generate operation for CobblePatchGenerator. */
    void generate(GeneratedWorld world, NoiseGenerator noise) {
        WorldConfig config = world.config;
        if (config.cobblePatchDensity() == 0) return;
        var candidates = new ArrayList<Candidate>();
        for (int r = 6; r < config.height() - 6; r++)
            for (int c = 6; c < config.width() - 6; c++)
                if (noise.sample(c * .10, r * .10, 140) > .53 && noise.random(c, r, 143) < .025)
                    candidates.add(new Candidate(c, r, noise.random(c, r, 144)));
        candidates.sort(Comparator.comparingDouble(Candidate::priority));
        int budget =
                Math.max(
                        1,
                        (int)
                                Math.round(
                                        config.width()
                                                * (double) config.height()
                                                / 1600
                                                * config.cobblePatchDensity()));
        var centers = new ArrayList<Candidate>();
        for (Candidate candidate : candidates) {
            if (centers.size() >= budget) break;
            int x = candidate.col, y = candidate.row;
            boolean fits = true;
            for (Candidate center : centers)
                if (Math.hypot(x - center.col, y - center.row) < 13) fits = false;
            for (int r = y - 5; r <= y + 5 && fits; r++)
                for (int c = x - 5; c <= x + 5; c++)
                    if (world.reserved(c, r) || world.terrain(c, r) == Terrain.WATER) {
                        fits = false;
                        break;
                    }
            if (!fits) continue;
            double rx = 2.5 + noise.random(x, y, 145) * 1.5, ry = 2 + noise.random(x, y, 146) * 1.3;
            for (int r = y - 4; r <= y + 4; r++)
                for (int c = x - 4; c <= x + 4; c++) {
                    double dx = (c - x) / rx, dy = (r - y) / ry;
                    double edge = 1 + (noise.value(c * .38, r * .38, 147) - .5) * .35;
                    if (dx * dx + dy * dy < edge)
                        world.ground(c, r, Terrain.MOSS_PATH, world.tone(c, r));
                }
            centers.add(candidate);
        }
    }
}
