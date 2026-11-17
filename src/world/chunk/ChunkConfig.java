package world.chunk;

/** Finite-world streaming settings, independent of the saved generator version. */
public final class ChunkConfig {
    /** Prevents instantiation of this utility class. */
    private ChunkConfig() {}

    public static final int SIZE = setting("size", 32, 8, 64);
    public static final int BUILD_CACHE_SIZE = setting("buildCacheSize", 8, 4, 32);
    public static final int ACTIVE_RADIUS = setting("activeRadius", 1, 1, 4);
    public static final int PRELOAD_RADIUS = setting("preloadRadius", 2, 1, 6);
    public static final int CACHE_LIMIT = setting("cacheLimit", 49, 9, 256);
    public static final int IMAGE_LIMIT = setting("imageLimit", 12, 4, 64);
    public static final int KEEP_TICKS = setting("keepSeconds", 10, 0, 120) * 60;

    /** Resolves the setting result used by ChunkConfig. */
    private static int setting(String key, int fallback, int min, int max) {
        int v = Integer.getInteger("astral.chunks." + key, fallback);
        if (v < min || v > max) throw new IllegalArgumentException("Invalid chunk setting: " + key);
        return v;
    }
}
