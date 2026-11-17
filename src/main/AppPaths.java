package main;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/**
 * Installed applications keep writable files out of the application bundle and install directory.
 */
public final class AppPaths {
    /** Prevents instantiation of this utility class. */
    private AppPaths() {}

    /** Evaluates the packaged condition for AppPaths. */
    public static boolean packaged() {
        return Boolean.getBoolean("astral.packaged");
    }

    /** Resolves the data directory result used by AppPaths. */
    public static Path dataDirectory() {
        String override = System.getProperty("astral.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath();
        Path home = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) return home.resolve("Library/Application Support/AstralCore");
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            return (local != null && !local.isBlank()
                            ? Path.of(local)
                            : home.resolve("AppData/Local"))
                    .resolve("AstralCore");
        }
        return home.resolve(".local/share/AstralCore");
    }

    /** Resolves the config file result used by AppPaths. */
    public static Path configFile() {
        return packaged() ? dataDirectory().resolve("config.txt") : Path.of("config.txt");
    }

    /** Resolves the map file result used by AppPaths. */
    public static File mapFile(String relative) {
        return (packaged() ? dataDirectory().resolve(relative) : Path.of(relative)).toFile();
    }

    /** Resolves the log file result used by AppPaths. */
    public static Path logFile() {
        return dataDirectory().resolve("logs/latest.log");
    }

    /** Creates the parent for AppPaths. */
    public static void createParent(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    /** Performs the initialize logging operation for AppPaths. */
    public static void initializeLogging() throws IOException {
        if (!packaged()) return;
        createParent(logFile());
        PrintStream log =
                new PrintStream(Files.newOutputStream(logFile()), true, StandardCharsets.UTF_8);
        System.setOut(log);
        System.setErr(log);
        System.out.println(
                "AstralCore "
                        + AppInfo.VERSION
                        + " | "
                        + System.getProperty("os.name")
                        + " | Java "
                        + System.getProperty("java.version"));
    }
}
