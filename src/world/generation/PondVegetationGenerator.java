package world.generation;

import art.Terrain;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Multi-source distance transform, performed once after the existing pond generator. */
final class PondVegetationGenerator {
    /** Performs the generate operation for PondVegetationGenerator. */
    void generate(GeneratedWorld w, NoiseGenerator n) {
        var m = w.regions();
        var cfg = w.generation;
        var queue = new ArrayDeque<Integer>();
        for (int x = 0; x < m.width; x++)
            Arrays.fill(m.waterDistance[x], (short) (cfg.shoreFar() + 1));
        for (int y = 0; y < m.height; y++)
            for (int x = 0; x < m.width; x++)
                if (w.terrain(x, y) == Terrain.WATER) {
                    m.waterDistance[x][y] = 0;
                    queue.add(y * m.width + x);
                }
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int p = queue.remove(), x = p % m.width, y = p / m.width, d = m.waterDistance[x][y] + 1;
            if (d > cfg.shoreFar()) continue;
            for (int i = 0; i < 4; i++) {
                int xx = x + dx[i], yy = y + dy[i];
                if (xx < 0
                        || yy < 0
                        || xx >= m.width
                        || yy >= m.height
                        || m.waterDistance[xx][yy] <= d) continue;
                m.waterDistance[xx][yy] = (short) d;
                queue.add(yy * m.width + xx);
            }
        }
        for (int y = 0; y < m.height; y++)
            for (int x = 0; x < m.width; x++) {
                int d = m.waterDistance[x][y];
                if (d == 0 || d > cfg.shoreFar()) continue;
                double band =
                        d <= cfg.shoreNear()
                                ? 1
                                : d <= cfg.shoreMiddle()
                                        ? 1
                                                - .35
                                                        * RegionGenerator.smooth(
                                                                cfg.shoreNear(),
                                                                cfg.shoreMiddle(),
                                                                d)
                                        : .65
                                                * (1
                                                        - RegionGenerator.smooth(
                                                                cfg.shoreMiddle(),
                                                                cfg.shoreFar() + 1,
                                                                d));
                // Broad wet clumps with gaps, rather than a uniform geometric ring.
                double breaks =
                        w.generatorVersion >= 7
                                ? .3
                                        + .7
                                                * RegionGenerator.smooth(
                                                        .18, .48, n.value(x * .16, y * .16, 840))
                                : RegionGenerator.smooth(.22, .52, n.value(x * .16, y * .16, 840));
                m.shore[x][y] = (float) (band * breaks);
            }
    }
}
