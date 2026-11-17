package main;

import java.awt.*;

/** Shared opaque, black-and-white window chrome. */
public final class WindowStyle {
    /** Prevents instantiation of this utility class. */
    private WindowStyle() {}

    /** Renders this component into the supplied Java2D graphics context. */
    public static void draw(Graphics2D g, int x, int y, int width, int height) {
        ui.UITheme.panel(g, new Rectangle(x, y, width, height));
    }
}
