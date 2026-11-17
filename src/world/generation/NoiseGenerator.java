package world.generation;

/**
 * Seeded value noise and coordinate hashes; no mutable random sequence or local chunk coordinates.
 */
public final class NoiseGenerator {
    private final long seed;

    /**
     * Creates a NoiseGenerator instance and initializes the state required by its collaborators.
     */
    public NoiseGenerator(long seed) {
        this.seed = seed;
    }

    /** Resolves the random result used by NoiseGenerator. */
    public double random(int x, int y, long channel) {
        long h =
                seed
                        ^ channel * 0x9E3779B97F4A7C15L
                        ^ (long) x * 0xD1B54A32D192ED03L
                        ^ (long) y * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return ((h ^ (h >>> 31)) >>> 11) * 0x1.0p-53;
    }

    /** Resolves the value result used by NoiseGenerator. */
    public double value(double x, double y, long channel) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = smooth(x - ix), fy = smooth(y - iy);
        double top = lerp(random(ix, iy, channel), random(ix + 1, iy, channel), fx);
        double bottom = lerp(random(ix, iy + 1, channel), random(ix + 1, iy + 1, channel), fx);
        return lerp(top, bottom, fy);
    }

    /** Resolves the sample result used by NoiseGenerator. */
    public double sample(double x, double y, long channel) {
        return (value(x, y, channel) * 4
                        + value(x * 2, y * 2, channel + 1) * 2
                        + value(x * 4, y * 4, channel + 2))
                / 7;
    }

    /** Resolves the smooth result used by NoiseGenerator. */
    private static double smooth(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** Resolves the lerp result used by NoiseGenerator. */
    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
