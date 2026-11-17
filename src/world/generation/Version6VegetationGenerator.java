package world.generation;

import world.GrassLayer;
import world.WorldObjectType;

/** Frozen version-six placement for existing saved worlds. New worlds use VegetationGenerator. */
final class Version6VegetationGenerator {
    private static final WorldObjectType[] TREES = {
        WorldObjectType.OAK_1, WorldObjectType.OAK_2, WorldObjectType.PINE_1, WorldObjectType.PINE_2
    };
    private static final WorldObjectType[] FLOWERS = {
        WorldObjectType.FLOWERS_WHITE,
        WorldObjectType.FLOWERS_PINK,
        WorldObjectType.FLOWERS_PURPLE,
        WorldObjectType.FLOWERS_YELLOW,
        WorldObjectType.FLOWERS_BLUE,
        WorldObjectType.FLOWERS_MIXED
    };
    private static final WorldObjectType[] MUSHROOMS = {
        WorldObjectType.MUSHROOM_RED,
        WorldObjectType.MUSHROOM_BROWN,
        WorldObjectType.MUSHROOM_IVORY,
        WorldObjectType.MUSHROOMS_RED,
        WorldObjectType.MUSHROOMS_BROWN,
        WorldObjectType.MUSHROOMS_IVORY
    };

    /** Performs the generate operation for Version6VegetationGenerator. */
    void generate(GeneratedWorld w, NoiseGenerator n, PlacementRules rules) {
        var m = w.regions();
        var cfg = w.generation;
        // The empty-inventory start must still progress even at zero optional decoration density.
        for (var prop : w.starterProps())
            if (!rules.tryPlace(prop.type(), prop.col(), prop.row(), true))
                throw new IllegalStateException("Reserved starter resource could not be placed");
        for (int pass = 0; pass < 4; pass++)
            for (int y = 1; y < m.height - 1; y++)
                for (int x = 1; x < m.width - 1; x++) {
                    if (!rules.grass(x, y) || w.reserved(x, y) || m.golden(x, y) > 0) continue;
                    double forest = m.forest(x, y),
                            flower = m.flowers(x, y),
                            shore = m.shore(x, y) * cfg.shoreDensity(),
                            near = pass >= 2 ? rules.nearTrees(x, y) : 0;
                    double grove =
                            RegionGenerator.smooth(.28, .68, n.sample(x * .13, y * .13, 860))
                                    * (.7 + .6 * m.moisture(x, y));
                    double clearing =
                            RegionGenerator.smooth(.24, .4, n.value(x * .095, y * .095, 864));
                    WorldObjectType type = null;
                    if (pass == 0) {
                        double chance =
                                (.008 * (1 - flower) + .19 * forest * grove * clearing)
                                        * cfg.forestDensity()
                                        * w.config.treeDensity()
                                        / .18;
                        // Keep the immediate shore approachable; forest influence remains outside
                        // that margin.
                        if (m.waterDistance(x, y) > cfg.shoreNear() && n.random(x, y, 870) < chance)
                            type =
                                    TREES[
                                            Math.min(
                                                    3,
                                                    (int)
                                                            (n.value(x * .09, y * .09, 871)
                                                                    * TREES.length))];
                    } else if (pass == 1) {
                        double stoneBed =
                                RegionGenerator.smooth(.43, .65, n.sample(x * .18, y * .18, 872));
                        double chance =
                                (.003 * (1 - flower) + .045 * forest)
                                        * stoneBed
                                        * w.config.rockDensity()
                                        / .008;
                        if (m.waterDistance(x, y) > cfg.shoreNear() && n.random(x, y, 873) < chance)
                            type =
                                    n.random(x, y, 874) < .35
                                            ? WorldObjectType.ROCK_LARGE_MOSS
                                            : WorldObjectType.ROCK_LARGE;
                    } else if (pass == 2) {
                        double gaps = .35 + .65 * near;
                        double chance =
                                (.002 * (1 - flower) + .09 * forest * gaps + .045 * shore)
                                        * cfg.forestDensity()
                                        * w.config.vegetationDensity()
                                        / .65;
                        if (m.waterDistance(x, y) > cfg.shoreNear() && n.random(x, y, 875) < chance)
                            type = WorldObjectType.BUSH;
                        else if (rules.shade(x, y) > .15
                                && n.sample(x * .23, y * .23, 876) > .48
                                && n.random(x, y, 877)
                                        < .06 * rules.shade(x, y) * w.config.detailDensity() / .65)
                            type =
                                    MUSHROOMS[
                                            Math.min(
                                                    MUSHROOMS.length - 1,
                                                    (int)
                                                            (n.random(x, y, 878)
                                                                    * MUSHROOMS.length))];
                        else if (near > .3
                                && n.random(x, y, 879) < .004 * w.config.detailDensity() / .65)
                            type = WorldObjectType.STUMP_OAK_MOSS;
                    } else {
                        double bed =
                                RegionGenerator.smooth(.40, .64, n.sample(x * .19, y * .19, 880));
                        double blossoms =
                                (.018 * (1 - forest) + .30 * flower + .02 * shore)
                                        * bed
                                        * cfg.flowerDensity()
                                        * w.config.flowerDensity()
                                        / .65;
                        if (n.random(x, y, 881) < blossoms) {
                            int color =
                                    Math.min(
                                            FLOWERS.length - 1,
                                            (int)
                                                    (n.value(x * .075, y * .075, 882)
                                                            * FLOWERS.length));
                            type = FLOWERS[color];
                        } else if (n.random(x, y, 883)
                                < (.012 * (1 - flower) + .06 * forest * (.2 + .8 * near))
                                        * w.config.detailDensity()
                                        / .65) type = WorldObjectType.TWIG;
                        else if (n.sample(x * .21, y * .21, 884) > .43
                                && n.random(x, y, 885)
                                        < (.009 * (1 - flower) + .045 * forest)
                                                * w.config.rockDensity()
                                                / .008)
                            type =
                                    n.random(x, y, 886) < .35
                                            ? WorldObjectType.ROCK_SMALL_MOSS
                                            : WorldObjectType.ROCK_SMALL;
                    }
                    if (type != null) rules.tryPlace(type, x, y);
                }
        rules.finish();
        m.accessible = rules.reachable();
        // Reject a whole inaccessible footprint rather than clipping it into disconnected
        // fragments.
        var rejected = new java.util.HashSet<Integer>();
        for (int y = 0; y < m.height; y++)
            for (int x = 0; x < m.width; x++)
                if (m.golden(x, y) > 0 && !m.accessible[x][y]) rejected.add(m.golden(x, y));
        if (!rejected.isEmpty())
            for (int y = 0; y < m.height; y++)
                for (int x = 0; x < m.width; x++)
                    if (rejected.contains(m.golden(x, y))) m.golden[x][y] = 0;
        // The golden plan was protected from every prop's actual sprite bounds: no late clipping or
        // holes.
        for (int y = 0; y < m.height; y++)
            for (int x = 0; x < m.width; x++) {
                if (m.golden(x, y) > 0) {
                    w.meadow(
                            x, y, GrassLayer.FIELD_GOLDEN, 2 + (n.random(x, y, 890) < .35 ? 1 : 0));
                    continue;
                }
                if (!m.accessible[x][y]
                        || !rules.meadowFits(x, y, false)
                        || w.config.fieldDensity() == 0
                        || cfg.grassDensity() == 0) continue;
                double threshold =
                        .69
                                + .07 * m.flowers(x, y)
                                - .025 * m.forest(x, y)
                                - .26 * m.shore(x, y) * cfg.shoreDensity()
                                - .04 * (m.moisture(x, y) - .5);
                double local = n.sample(x * .135, y * .135, 891);
                double density = Math.min(2, cfg.grassDensity() * w.config.fieldDensity() / .75);
                if (local > 1 - (1 - threshold) * density) {
                    int type =
                            n.value(x * .18, y * .18, 892) < .4
                                    ? GrassLayer.FIELD_DARK
                                    : GrassLayer.FIELD_GREEN;
                    w.meadow(x, y, type, m.shore(x, y) > .45 ? 3 : 2);
                }
            }
        int fiber = 0;
        for (int id : w.starterFiber()) {
            int x = id % m.width, y = id / m.width;
            if (rules.meadowFits(x, y, false, true) && rules.reachable()[x][y]) {
                w.meadow(x, y, GrassLayer.FIELD_GREEN, 2);
                fiber++;
            }
        }
        if (!rules.hasTreeNearSpawn() || !rules.hasRockNearSpawn() || fiber < 1)
            throw new IllegalStateException(
                    "No safe starter resource area fits this world configuration");
    }
}
