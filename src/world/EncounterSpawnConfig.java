package world;

import world.generation.EnvironmentRegion;

/** Active 60 Hz game-tick settings; debug acceleration never relaxes spawn validation. */
public final class EncounterSpawnConfig {
    public static final double CHANCE = number("chance", .60, 0, 1);
    public static final int NEARBY_CAP = integer("nearbyCap", 8, 1, 20),
            ATTEMPTS = integer("attempts", 28, 1, 200);
    public static final int MARGIN_TILES = integer("marginTiles", 2, 2, 12),
            RING_TILES = integer("ringTiles", 6, 2, 16);
    public static final int SAFE_TILES = integer("safeTiles", 8, 5, 32),
            TRAVEL_TILES = integer("travelTiles", 4, 1, 20);
    public static final int KILL_GRACE_SECONDS = integer("killGraceSeconds", 25, 5, 180),
            RESPAWN_GRACE_SECONDS = integer("respawnGraceSeconds", 20, 5, 180);
    public static final int DESPAWN_SECONDS = integer("distantSeconds", 60, 15, 600),
            INVESTIGATE_SECONDS = integer("investigateSeconds", 45, 10, 120),
            UNREACHABLE_SECONDS = integer("unreachableSeconds", 30, 10, 180);
    private static final double GRASS = number("grasslandWeight", 1, 0, 3),
            FOREST = number("forestWeight", 1.3, 0, 3),
            FLOWERS = number("flowerWeight", .5, 0, 3),
            SHORE = number("shoreWeight", 1.1, 0, 3),
            GOLDEN = number("goldenWeight", .7, 0, 3);

    /** Prevents instantiation of this utility class. */
    private EncounterSpawnConfig() {}

    /** Resolves the weight result used by EncounterSpawnConfig. */
    public static double weight(EnvironmentRegion region) {
        return switch (region) {
            case FOREST_GROVE -> FOREST;
            case FLOWER_PLAIN -> FLOWERS;
            case POND_SHORE -> SHORE;
            case GOLDEN_MEADOW -> GOLDEN;
            default -> GRASS;
        };
    }

    /** Resolves the interval seconds result used by EncounterSpawnConfig. */
    public static int intervalSeconds(int fallback) {
        return integer("intervalSeconds", Math.max(5, Math.min(600, fallback)), 5, 600);
    }

    /** Resolves the integer result used by EncounterSpawnConfig. */
    private static int integer(String name, int fallback, int min, int max) {
        int v =
                Integer.parseInt(
                        System.getProperty(
                                "astral.encounters." + name, Integer.toString(fallback)));
        if (v < min || v > max)
            throw new IllegalArgumentException("Invalid encounter setting: " + name);
        return v;
    }

    /** Resolves the number result used by EncounterSpawnConfig. */
    private static double number(String name, double fallback, double min, double max) {
        double v =
                Double.parseDouble(
                        System.getProperty("astral.encounters." + name, Double.toString(fallback)));
        if (!Double.isFinite(v) || v < min || v > max)
            throw new IllegalArgumentException("Invalid encounter setting: " + name);
        return v;
    }
}
