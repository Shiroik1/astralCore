package main;

public final class RawInput {
    public enum Kind { KEY_PRESSED, KEY_RELEASED, KEY_TYPED, MOUSE_PRESSED, MOUSE_RELEASED, MOUSE_WHEEL }

    public final Kind kind;
    public final int code;   // key code, mouse button, or wheel rotation
    public final char ch;    // KEY_TYPED only

    private RawInput(Kind kind, int code, char ch){
        this.kind = kind;
        this.code = code;
        this.ch = ch;
    }

    public static RawInput keyPressed(int code){ return new RawInput(Kind.KEY_PRESSED, code, '\0'); }
    public static RawInput keyReleased(int code){ return new RawInput(Kind.KEY_RELEASED, code, '\0'); }
    public static RawInput keyTyped(char ch){ return new RawInput(Kind.KEY_TYPED, 0, ch); }
    public static RawInput mousePressed(int button){ return new RawInput(Kind.MOUSE_PRESSED, button, '\0'); }
    public static RawInput mouseReleased(int button){ return new RawInput(Kind.MOUSE_RELEASED, button, '\0'); }
    public static RawInput mouseWheel(int rotation){ return new RawInput(Kind.MOUSE_WHEEL, rotation, '\0'); }
}