package main;

public final class Log {
    private Log(){}
    public static final boolean DEBUG = Boolean.getBoolean("astral.debug");
    public static void debug(String message){ if(DEBUG) System.out.println(message); }
    public static void warn(String message){ System.out.println(message); }
}