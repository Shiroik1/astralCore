package world.generation;

import art.Terrain;

import java.util.*;

/**
 * Version-eight arena reservation. Generated dirt/paving may be replaced; water/protected ground
 * may not.
 */
final class SlimeNestPlanner {
    /** Evaluates the starter buffer condition for SlimeNestPlanner. */
    private boolean starterBuffer(GeneratedWorld w, int x, int y) {
        for (int r = Math.max(0, y - 1); r <= Math.min(w.config.height() - 1, y + 1); r++)
            for (int c = Math.max(0, x - 1); c <= Math.min(w.config.width() - 1, x + 1); c++)
                if (w.starterReserved(c, r) && !w.coreReserved(c, r)) return true;
        return false;
    }

    /** Performs the generate operation for SlimeNestPlanner. */
    void generate(GeneratedWorld w, NoiseGenerator noise) {
        int width = w.config.width(), height = w.config.height();
        var cfg = w.nestConfig;
        int[][] parent = new int[width][height];
        boolean[][] reached = new boolean[width][height];
        var queue = new ArrayDeque<Integer>();
        queue.add(w.spawnRow * width + w.spawnCol);
        reached[w.spawnCol][w.spawnRow] = true;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int p = queue.remove(), x = p % width, y = p / width;
            for (int i = 0; i < 4; i++) {
                int c = x + dx[i], r = y + dy[i];
                if (c < 0
                        || r < 0
                        || c >= width
                        || r >= height
                        || reached[c][r]
                        || starterBuffer(w, c, r)
                        || (!PlacementRules.walkableTerrain(w.terrain(c, r))
                                || w.heights() != null && !w.heights().flat(c, r, 1))) continue;
                reached[c][r] = true;
                parent[c][r] = p;
                queue.add(r * width + c);
            }
        }
        int bestX = -1, bestY = 0, chosenSize = 0;
        double best = -Double.MAX_VALUE;
        int[] sizes =
                cfg.arenaSize() == cfg.smallArenaSize()
                        ? new int[] {cfg.arenaSize()}
                        : new int[] {cfg.arenaSize(), cfg.smallArenaSize()};
        for (int size : sizes) {
            if (width < size + 4 || height < size + 4) continue;
            for (int preference = 0; preference < 2 && bestX < 0; preference++) {
                for (int y = 2; y + size <= height - 2; y++)
                    for (int x = 2; x + size <= width - 2; x++) {
                        int cx = x + size / 2, cy = y + size / 2;
                        double distance = Math.hypot(cx - w.spawnCol, cy - w.spawnRow);
                        if (!reached[cx][cy]
                                || preference == 0
                                        && (distance < cfg.minDistance()
                                                || distance > cfg.preferredMaxDistance())) continue;
                        double closestX = Math.max(x, Math.min(w.spawnCol, x + size)),
                                closestY = Math.max(y, Math.min(w.spawnRow, y + size));
                        if (Math.hypot(closestX - w.spawnCol, closestY - w.spawnRow)
                                < cfg.spawnSafety()) continue;
                        boolean fits = true;
                        for (int r = y; r < y + size && fits; r++)
                            for (int c = x; c < x + size; c++)
                                if (w.reserved(c, r)
                                        || !PlacementRules.walkableTerrain(w.terrain(c, r))
                                        || w.heights() != null && !w.heights().flat(c, r, 2)) {
                                    fits = false;
                                    break;
                                }
                        if (!fits) continue;
                        var region = w.regions();
                        double forest = region == null ? 0 : region.forest(cx, cy),
                                flowers = region == null ? 0 : region.flowers(cx, cy);
                        double score = noise.random(x, y, 903) + .2 * forest - .15 * flowers;
                        if (score > best) {
                            best = score;
                            bestX = x;
                            bestY = y;
                            chosenSize = size;
                        }
                    }
            }
            if (bestX >= 0) break;
        }
        if (bestX < 0) {
            String reason =
                    "No reachable water-free "
                            + cfg.arenaSize()
                            + " or "
                            + cfg.smallArenaSize()
                            + " tile arena fits outside starter/crossing reservations in "
                            + width
                            + "x"
                            + height
                            + " seed="
                            + w.config.seed();
            w.nestDiagnostic(reason);
            main.Log.warn("[NEST] " + reason);
            return;
        }
        var plan =
                new SlimeNestPlan(
                        "slime-nest-v2:g"
                                + w.generatorVersion
                                + ":"
                                + w.config.seed()
                                + ":"
                                + width
                                + ":"
                                + height
                                + ":"
                                + bestX
                                + ":"
                                + bestY,
                        bestX,
                        bestY,
                        chosenSize);
        w.reserveNest(plan);
        // Ground is generated and unoccupied here: replace only safe natural dirt/paving, never
        // persisted construction.
        for (int r = bestY; r < bestY + chosenSize; r++)
            for (int c = bestX; c < bestX + chosenSize; c++)
                w.ground(c, r, Terrain.GRASS, w.tone(c, r));
        int at = (bestY + chosenSize / 2) * width + bestX + chosenSize / 2,
                spawn = w.spawnRow * width + w.spawnCol;
        while (at != spawn) {
            int c = at % width, r = at / width;
            if (w.generatorVersion >= 11) w.reserveAccess(c, r);
            else
                for (int y = Math.max(0, r - 1); y <= Math.min(height - 1, r + 1); y++)
                    for (int x = Math.max(0, c - 1); x <= Math.min(width - 1, c + 1); x++)
                        w.reserveAccess(x, y);
            at = parent[c][r];
        }
        w.nestDiagnostic(
                plan.id()
                        + " at tile "
                        + (bestX + chosenSize / 2)
                        + ","
                        + (bestY + chosenSize / 2)
                        + " arena="
                        + chosenSize);
        if (Boolean.getBoolean("astral.dev")) System.out.println("[NEST] " + w.nestDiagnostic());
    }
}
