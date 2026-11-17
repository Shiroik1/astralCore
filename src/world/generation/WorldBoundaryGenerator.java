package world.generation;

import java.util.ArrayDeque;

/** Square outer ring for current worlds; seeded organic masks remain pinned for legacy saves. */
final class WorldBoundaryGenerator {
    /** Performs the generate operation for WorldBoundaryGenerator. */
    void generate(GeneratedWorld w, NoiseGenerator n) {
        var map = w.heights();
        var cfg = w.elevationConfig;
        int width = map.width, height = map.height;
        if (w.generatorVersion >= 11) {
            // A square/rectangular outer tile ring, independent of inland plateau placement.
            for (int y = 1; y < height - 1; y++)
                for (int x = 1; x < width - 1; x++) map.playable[x][y] = true;
            return;
        }
        double phase = n.random(0, 0, 1300) * Math.PI * 2,
                phase2 = n.random(0, 0, 1301) * Math.PI * 2;
        for (int y = 1; y < height - 1; y++)
            for (int x = 1; x < width - 1; x++) {
                double dx = (x - (width - 1) * .5) / (width * .5 - 1),
                        dy = (y - (height - 1) * .5) / (height * .5 - 1),
                        angle = Math.atan2(dy, dx);
                double radius =
                        cfg.boundaryRadius()
                                + cfg.boundaryIrregularity()
                                        * (.58 * Math.sin(3 * angle + phase)
                                                + .30 * Math.sin(5 * angle + phase2))
                                + (n.value(x * .045, y * .045, 1302) - .5) * .06;
                map.playable[x][y] = Math.hypot(dx, dy) < radius;
                // Artwork/resource reservations precede this pass; preserve their lowland
                // approaches.
                if (w.starterReserved(x, y) || Math.hypot(x - w.spawnCol, y - w.spawnRow) < 6)
                    map.playable[x][y] = true;
            }
        // Keep only the component containing the starting point. No detached playable pockets
        // outside it.
        boolean[][] seen = new boolean[width][height];
        var queue = new ArrayDeque<Integer>();
        queue.add(w.spawnRow * width + w.spawnCol);
        seen[w.spawnCol][w.spawnRow] = true;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int id = queue.remove(), x = id % width, y = id / width;
            for (int i = 0; i < 4; i++) {
                int xx = x + dx[i], yy = y + dy[i];
                if (!map.playable(xx, yy) || seen[xx][yy]) continue;
                seen[xx][yy] = true;
                queue.add(yy * width + xx);
            }
        }
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) map.playable[x][y] = seen[x][y];
    }
}
