package world.generation;

import java.util.ArrayDeque;

/** Generation-time connectivity only. Rendering reads the cached result. */
final class TerrainAccessibilityValidator {
    /** Resolves the flood result used by TerrainAccessibilityValidator. */
    private int flood(GeneratedWorld w) {
        var h = w.heights();
        for (boolean[] col : h.reachable) java.util.Arrays.fill(col, false);
        var queue = new ArrayDeque<Integer>();
        queue.add(w.spawnRow * h.width + w.spawnCol);
        h.reachable[w.spawnCol][w.spawnRow] = true;
        int count = 0;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int id = queue.remove(), x = id % h.width, y = id / h.width;
            count++;
            for (int i = 0; i < 4; i++) {
                int c = x + dx[i], r = y + dy[i];
                if (!h.playable(c, r)
                        || h.reachable[c][r]
                        || h.at(c, r) != 0
                        || !PlacementRules.walkableTerrain(w.terrain(c, r))) continue;
                h.reachable[c][r] = true;
                queue.add(r * h.width + c);
            }
        }
        return count;
    }

    /** Performs the connect pond basins operation for TerrainAccessibilityValidator. */
    private void connectPondBasins(GeneratedWorld w) {
        var h = w.heights();
        int width = h.width, height = h.height, spawn = w.spawnRow * width + w.spawnCol;
        int[][] parent = new int[width][height];
        for (int[] col : parent) java.util.Arrays.fill(col, -1);
        var queue = new ArrayDeque<Integer>();
        queue.add(spawn);
        parent[w.spawnCol][w.spawnRow] = spawn;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int id = queue.remove(), x = id % width, y = id / width;
            for (int i = 0; i < 4; i++) {
                int c = x + dx[i], r = y + dy[i];
                if (!h.playable(c, r)
                        || parent[c][r] >= 0
                        || !PlacementRules.walkableTerrain(w.terrain(c, r))) continue;
                boolean margin = true;
                for (int yy = r - 2; yy <= r + 2; yy++)
                    for (int xx = c - 2; xx <= c + 2; xx++) {
                        if (Math.abs(xx - c) <= 1 && Math.abs(yy - r) <= 1 && !h.playable(xx, yy))
                            margin = false;
                        if (h.inside(xx, yy)
                                && w.starterReserved(xx, yy)
                                && !w.coreReserved(xx, yy)) margin = false;
                    }
                if (!margin) continue;
                parent[c][r] = id;
                queue.add(r * width + c);
            }
        }
        boolean[][] waterSeen = new boolean[width][height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                if (w.terrain(x, y) != art.Terrain.WATER || waterSeen[x][y]) continue;
                int shore = -1;
                queue.add(y * width + x);
                waterSeen[x][y] = true;
                while (!queue.isEmpty()) {
                    int id = queue.remove(), c = id % width, r = id / width;
                    for (int i = 0; i < 4; i++) {
                        int xx = c + dx[i], yy = r + dy[i];
                        if (!h.inside(xx, yy)) continue;
                        if (w.terrain(xx, yy) == art.Terrain.WATER) {
                            if (!waterSeen[xx][yy]) {
                                waterSeen[xx][yy] = true;
                                queue.add(yy * width + xx);
                            }
                        } else if (shore < 0 && parent[xx][yy] >= 0) shore = yy * width + xx;
                    }
                }
                // Water is never converted. Only the dry, pre-decoration approach is
                // lowered/reserved.
                while (shore >= 0 && shore != spawn) {
                    int c = shore % width, r = shore / width;
                    for (int yy = Math.max(1, r - 2); yy <= Math.min(height - 2, r + 2); yy++)
                        for (int xx = Math.max(1, c - 2); xx <= Math.min(width - 2, c + 2); xx++)
                            if (h.playable(xx, yy)) {
                                h.set(xx, yy, 0);
                                if (!w.starterReserved(xx, yy)) w.reserveAccess(xx, yy);
                            }
                    shore = parent[c][r];
                }
            }
    }

    /** Performs the prepare operation for TerrainAccessibilityValidator. */
    void prepare(GeneratedWorld w) {
        connectPondBasins(w);
        var h = w.heights();
        int playable = 0;
        for (int y = 0; y < h.height; y++)
            for (int x = 0; x < h.width; x++) if (h.playable(x, y)) playable++;
        int count = flood(w);
        for (int relax = 1;
                count < playable * .45 && relax <= w.elevationConfig.maxHeight();
                relax++) {
            // Accessibility takes precedence over nominal height coverage on difficult seeds.
            for (int y = 0; y < h.height; y++)
                for (int x = 0; x < h.width; x++)
                    if (h.playable(x, y) && h.at(x, y) <= relax) h.set(x, y, 0);
            count = flood(w);
        }
        // Unreachable lowland pockets become scenic raised ground; there are no invisible traversal
        // links.
        for (int y = 0; y < h.height; y++)
            for (int x = 0; x < h.width; x++)
                if (h.playable(x, y)
                        && h.at(x, y) == 0
                        && !h.reachable(x, y)
                        && PlacementRules.walkableTerrain(w.terrain(x, y))) {
                    boolean basin = false;
                    for (int r = Math.max(0, y - 3); r <= Math.min(h.height - 1, y + 3); r++)
                        for (int c = Math.max(0, x - 3); c <= Math.min(h.width - 1, x + 3); c++)
                            if (w.terrain(c, r) == art.Terrain.WATER) basin = true;
                    if (!basin) h.set(x, y, 1);
                }
        var p = w.slimeNest();
        if (!h.reachable(w.spawnCol, w.spawnRow)
                || p != null && !h.reachable(p.col() + p.size() / 2, p.row() + p.size() / 2))
            throw new IllegalStateException(
                    "Elevation obstructed a protected starting/nest reservation");
    }

    /** Performs the finish operation for TerrainAccessibilityValidator. */
    void finish(GeneratedWorld w) {
        var h = w.heights();
        var access = w.regions().accessible;
        int count = 0, cliffs = 0, plateaus = 0;
        boolean[][] seen = new boolean[h.width][h.height];
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        for (int y = 0; y < h.height; y++)
            for (int x = 0; x < h.width; x++) {
                h.reachable[x][y] = access[x][y];
                if (access[x][y]) count++;
                if (CliffTileResolver.edge(h, x, y)) cliffs++;
                if (!h.playable(x, y) || h.at(x, y) == 0 || seen[x][y]) continue;
                plateaus++;
                var q = new ArrayDeque<Integer>();
                q.add(y * h.width + x);
                seen[x][y] = true;
                while (!q.isEmpty()) {
                    int id = q.remove(), c = id % h.width, r = id / h.width;
                    for (int i = 0; i < 4; i++) {
                        int xx = c + dx[i], yy = r + dy[i];
                        if (!h.playable(xx, yy) || seen[xx][yy] || h.at(xx, yy) != h.at(c, r))
                            continue;
                        seen[xx][yy] = true;
                        q.add(yy * h.width + xx);
                    }
                }
            }
        for (var resource : w.resources())
            if (!h.reachable(resource.col(), resource.row()))
                throw new IllegalStateException("Inaccessible essential resource");
        var nest = w.slimeNest();
        boolean nestOK =
                nest == null
                        || h.reachable(nest.col() + nest.size() / 2, nest.row() + nest.size() / 2);
        if (!h.reachable(w.spawnCol, w.spawnRow) || !nestOK)
            throw new IllegalStateException("Final terrain accessibility failed");
        boolean boundaryValid = true;
        for (int x = 0; x < h.width; x++)
            if (h.playable(x, 0) || h.playable(x, h.height - 1)) boundaryValid = false;
        for (int y = 0; y < h.height; y++)
            if (h.playable(0, y) || h.playable(h.width - 1, y)) boundaryValid = false;
        if (!boundaryValid)
            throw new IllegalStateException("Playable boundary reaches rectangular storage edge");
        int[] coverage = new int[w.elevationConfig.maxHeight() + 1];
        for (int y = 0; y < h.height; y++)
            for (int x = 0; x < h.width; x++) if (h.playable(x, y)) coverage[h.at(x, y)]++;
        String message =
                "ELEVATION GENERATION seed="
                        + w.config.seed()
                        + " max="
                        + w.elevationConfig.maxHeight()
                        + " plateaus="
                        + plateaus
                        + " cliffTiles="
                        + cliffs
                        + " walkable="
                        + count
                        + " coverage="
                        + java.util.Arrays.toString(coverage)
                        + " boundaryValid=YES spawnAccessible=YES nestAccessible="
                        + (nest == null ? "not planned" : "YES");
        if (w.generatorVersion >= 10) {
            var s = h.cliffStatistics();
            message =
                    String.format(
                            java.util.Locale.ROOT,
                            "WORLD GENERATION DEBUG%nSeed: %d   Version: %d%nInterior Land Tiles:"
                                + " %d%nCliff Footprint Tiles: %d%nInland Cliff Coverage: %.2f%%  "
                                + " Maximum: %.2f%%%nCliff Formations: %d   Disconnected:"
                                + " %d%nExposed Edges: %d inland / %d total%nLargest Walkable"
                                + " Region: %.2f%%%nClear Lowland Accessible: %.2f%%%nWorld Border"
                                + " Coverage: Excluded%nSpawn Accessible: YES   Slime Nest: %s",
                            w.config.seed(),
                            w.generatorVersion,
                            s.interiorLand(),
                            s.footprint(),
                            s.coverage() * 100,
                            w.cliffConfig.maximumCoverage() * 100,
                            s.formations(),
                            s.disconnectedFragments(),
                            s.inlandEdges(),
                            s.exposedEdges(),
                            s.access() * 100,
                            s.floraAccess() * 100,
                            nest == null ? "not planned" : "YES");
        }
        h.diagnostic(message);
        if (Boolean.getBoolean("astral.dev")) System.out.println(message);
    }
}
