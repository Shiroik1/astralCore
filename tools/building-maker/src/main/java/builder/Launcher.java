package builder;

/** Non-Application entry point for IDEs and the JavaFX Maven plugin. */
public final class Launcher {
    /** Prevents instantiation of this utility class. */
    private Launcher() {}

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) {
        javafx.application.Application.launch(BuilderApp.class, args);
    }
}
