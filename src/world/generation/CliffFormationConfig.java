package world.generation;

/** Saved layout controls. The boundary mask never contributes to the inland budget. */
public record CliffFormationConfig(
        double targetCoverage,
        double maximumCoverage,
        int minimumTiles,
        double formationAreaFraction,
        double minimumLowlandAccess) {
    public static final int MAX_FORMATIONS = 8;

    /**
     * Creates a CliffFormationConfig instance and initializes the state required by its
     * collaborators.
     */
    public CliffFormationConfig {
        if (!Double.isFinite(targetCoverage)
                || !Double.isFinite(maximumCoverage)
                || targetCoverage < 0
                || targetCoverage > maximumCoverage
                || maximumCoverage > .10
                || minimumTiles < 9
                || minimumTiles > 512
                || !Double.isFinite(formationAreaFraction)
                || formationAreaFraction < .002
                || formationAreaFraction > .08
                || !Double.isFinite(minimumLowlandAccess)
                || minimumLowlandAccess < .95
                || minimumLowlandAccess > 1)
            throw new IllegalArgumentException(
                    "Invalid cliff formation settings (maximum coverage <= 10%, access >= 95%)");
    }

    /** Resolves the defaults result used by CliffFormationConfig. */
    public static CliffFormationConfig defaults() {
        return new CliffFormationConfig(.08, .10, 24, .025, .95);
    }

    /** Resolves the development defaults result used by CliffFormationConfig. */
    public static CliffFormationConfig developmentDefaults() {
        return new CliffFormationConfig(
                n("target", .08),
                n("maximum", .10),
                Integer.getInteger("astral.cliffs.minimumTiles", 24),
                n("formationAreaFraction", .025),
                n("minimumAccess", .95));
    }

    /** Reads a typed configuration value and applies the local conversion or validation rules. */
    private static double n(String key, double value) {
        return Double.parseDouble(
                System.getProperty("astral.cliffs." + key, Double.toString(value)));
    }
}
