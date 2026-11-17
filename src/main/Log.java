package main;

public final class Log {
    /** Prevents instantiation of this utility class. */
    private Log() {}

    public static final boolean DEBUG = Boolean.getBoolean("astral.debug");

    /** Performs the debug operation for Log. */
    public static void debug(String message) {
        if (DEBUG) System.out.println(message);
    }

    /** Performs the warn operation for Log. */
    public static void warn(String message) {
        System.out.println(message);
    }
}
