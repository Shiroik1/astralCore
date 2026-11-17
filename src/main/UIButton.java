package main;

import java.awt.*;

public class UIButton {
    public Rectangle bounds = new Rectangle();
    public boolean hovered;

    /** Updates the bounds for UIButton. */
    public void setBounds(int x, int y, int width, int height) {
        bounds.setBounds(x, y, width, height);
    }

    /** Evaluates the contains condition for UIButton. */
    public boolean contains(int mouseX, int mouseY) {
        return bounds.contains(mouseX, mouseY);
    }
}
