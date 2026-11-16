package builder;

/** Non-Application entry point for IDEs and the JavaFX Maven plugin. */
public final class Launcher {
    private Launcher(){}
    public static void main(String[] args){javafx.application.Application.launch(BuilderApp.class,args);}
}
