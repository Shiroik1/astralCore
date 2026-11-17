package main;

public final class RawInput {
    public enum Kind {
        KEY_PRESSED,
        KEY_RELEASED,
        KEY_TYPED,
        MOUSE_PRESSED,
        MOUSE_RELEASED,
        MOUSE_WHEEL
    }

    public final Kind kind;
    public final int code; // key code, mouse button, or wheel rotation
    public final char ch; // KEY_TYPED only

    /** Creates a RawInput instance and initializes the state required by its collaborators. */
    private RawInput(Kind kind, int code, char ch) {
        this.kind = kind;
        this.code = code;
        this.ch = ch;
    }

    /** Handles a pressed key and updates the corresponding input or UI state. */
    public static RawInput keyPressed(int code) {
        return new RawInput(Kind.KEY_PRESSED, code, '\0');
    }

    /** Releases the corresponding keyboard input state. */
    public static RawInput keyReleased(int code) {
        return new RawInput(Kind.KEY_RELEASED, code, '\0');
    }

    /** Handles typed text input for the current input mode. */
    public static RawInput keyTyped(char ch) {
        return new RawInput(Kind.KEY_TYPED, 0, ch);
    }

    /** Records the pressed mouse button for the current input mode. */
    public static RawInput mousePressed(int button) {
        return new RawInput(Kind.MOUSE_PRESSED, button, '\0');
    }

    /** Releases the corresponding mouse input state. */
    public static RawInput mouseReleased(int button) {
        return new RawInput(Kind.MOUSE_RELEASED, button, '\0');
    }

    /** Resolves the mouse wheel result used by RawInput. */
    public static RawInput mouseWheel(int rotation) {
        return new RawInput(Kind.MOUSE_WHEEL, rotation, '\0');
    }
}
