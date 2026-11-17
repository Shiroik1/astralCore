package world.generation;

/** Low-frequency domain-warped value noise in global tile coordinates. */
final class RegionGenerator {
    /** Resolves the generate result used by RegionGenerator. */
    RegionMap generate(GeneratedWorld w, NoiseGenerator n) {
        var cfg = w.generation;
        var map = new RegionMap(w.config.width(), w.config.height());
        double scale = cfg.regionScale();
        for (int y = 0; y < map.height; y++)
            for (int x = 0; x < map.width; x++) {
                double wx =
                        x + (n.value(x * scale * .6, y * scale * .6, 801) - .5) * cfg.warpTiles();
                double wy =
                        y + (n.value(x * scale * .6, y * scale * .6, 802) - .5) * cfg.warpTiles();
                double moisture = n.sample(wx * .045, wy * .045, 810);
                double forest, flower;
                if (w.generatorVersion >= 7) {
                    forest = smooth(.32, .64, n.value(wx * scale, wy * scale, 820)) * .94;
                    flower =
                            (.2
                                            + .8
                                                    * smooth(
                                                            .30,
                                                            .65,
                                                            n.value(
                                                                    wx * scale * .92,
                                                                    wy * scale * .92,
                                                                    830)))
                                    * (1 - forest)
                                    * .95;
                } else {
                    forest = smooth(.40, .70, n.value(wx * scale, wy * scale, 820)) * .88;
                    flower =
                            smooth(.40, .70, n.value(wx * scale * .92, wy * scale * .92, 830))
                                    * (1 - forest)
                                    * .9;
                }
                map.forest[x][y] = (float) forest;
                map.flowers[x][y] = (float) flower;
                map.moisture[x][y] = (float) moisture;
            }
        return map;
    }

    /** Summed-area density for diagnostics; performed once after all prop/field placements. */
    void finishDensity(GeneratedWorld w) {
        var m = w.regions();
        float[][] counts = new float[m.width][m.height];
        for (var p : w.objects()) counts[p.col()][p.row()] += PlacementRules.tree(p.type()) ? 2 : 1;
        double[][] sum = new double[m.width + 1][m.height + 1];
        for (int y = 0; y < m.height; y++)
            for (int x = 0; x < m.width; x++) {
                double value = counts[x][y] + (w.meadow(x, y) > 12 ? .75 : 0);
                sum[x + 1][y + 1] = value + sum[x][y + 1] + sum[x + 1][y] - sum[x][y];
            }
        for (int y = 0; y < m.height; y++)
            for (int x = 0; x < m.width; x++) {
                int left = Math.max(0, x - 3),
                        right = Math.min(m.width, x + 4),
                        top = Math.max(0, y - 3),
                        bottom = Math.min(m.height, y + 4);
                double value =
                        sum[right][bottom] - sum[left][bottom] - sum[right][top] + sum[left][top];
                m.density[x][y] =
                        (float) Math.min(1, value / ((right - left) * (bottom - top)) / .4);
            }
    }

    /** Resolves the smooth result used by RegionGenerator. */
    static double smooth(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
