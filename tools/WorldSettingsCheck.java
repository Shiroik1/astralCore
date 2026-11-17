package world.generation;

/**
 * Opt-in regression check for jsonbeans 0.5's Double-to-float narrowing. Requires owner approval to
 * run.
 */
public final class WorldSettingsCheck {
    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) {
        run();
        System.out.println("World settings precision checks passed.");
    }

    /** Performs the run operation for WorldSettingsCheck. */
    static void run() {
        var defaults = WorldConfig.developmentDefaults();
        var regions = GenerationConfig.defaults();
        var nest = NestConfig.defaults();
        var elevation = ElevationConfig.defaults();
        var cliffs = CliffFormationConfig.defaults();
        for (long seed : new long[] {123456789L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var config = defaults.withSeed(seed);
            var encoded = WorldSettings.of(config, 10, regions, nest, elevation, cliffs).encode();
            var restored = WorldSettings.decode(encoded);
            require(restored.config().equals(config), "Terrain/seed precision changed");
            require(restored.generation().equals(regions), "Region precision changed");
            require(restored.elevation().equals(elevation), "Elevation precision changed");
            require(restored.cliffs().equals(cliffs), "Cliff precision changed");
            require(
                    restored.cliffs[1] == .10 && restored.cliffs[4] == .95,
                    "Default limits lost precision");
            var repeated = WorldSettings.decode(restored.encode());
            require(
                    repeated.encode().equals(restored.encode()),
                    "Save/load causes descriptor drift");
            restored.cliffs[1] = .100001;
            boolean rejected = false;
            try {
                WorldSettings.decode(restored.encode());
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "A genuinely excessive maximum was accepted");
        }
    }

    /** Performs the require operation for WorldSettingsCheck. */
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
