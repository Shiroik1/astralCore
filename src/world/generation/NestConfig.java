package world.generation;

/** Layout-affecting settings are recorded in the world's generation descriptor. */
public record NestConfig(
        int arenaSize,
        int smallArenaSize,
        int minDistance,
        int preferredMaxDistance,
        int spawnSafety) {
    /** Creates a NestConfig instance and initializes the state required by its collaborators. */
    public NestConfig {
        if (arenaSize < 8
                || arenaSize > 32
                || smallArenaSize < 8
                || smallArenaSize > arenaSize
                || minDistance < 8
                || preferredMaxDistance < minDistance
                || spawnSafety < 5
                || spawnSafety > 32)
            throw new IllegalArgumentException("Invalid slime nest layout settings");
    }

    /** Resolves the defaults result used by NestConfig. */
    public static NestConfig defaults() {
        return new NestConfig(14, 8, 25, 40, 8);
    }

    /** Resolves the development defaults result used by NestConfig. */
    public static NestConfig developmentDefaults() {
        return new NestConfig(
                i("arenaSize", 14),
                i("smallArenaSize", 8),
                i("minDistance", 25),
                i("maxDistance", 40),
                i("spawnSafety", 8));
    }

    /** Reads a typed configuration value and applies the local conversion or validation rules. */
    private static int i(String key, int d) {
        return Integer.parseInt(System.getProperty("astral.nest." + key, Integer.toString(d)));
    }
}
