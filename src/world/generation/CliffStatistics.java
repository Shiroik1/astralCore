package world.generation;

/**
 * Cached generation diagnostics; terrain access excludes decorative obstacles, flora access does
 * not.
 */
public record CliffStatistics(
        int interiorLand,
        int footprint,
        int exposedEdges,
        int inlandEdges,
        int formations,
        int disconnectedFragments,
        int lowlandTiles,
        int reachableLowland,
        int clearLowland,
        int reachableClearLowland) {
    /** Resolves the coverage result used by CliffStatistics. */
    public double coverage() {
        return interiorLand == 0 ? 0 : footprint / (double) interiorLand;
    }

    /** Resolves the access result used by CliffStatistics. */
    public double access() {
        return lowlandTiles == 0 ? 1 : reachableLowland / (double) lowlandTiles;
    }

    /** Resolves the flora access result used by CliffStatistics. */
    public double floraAccess() {
        return clearLowland == 0 ? 1 : reachableClearLowland / (double) clearLowland;
    }
}
