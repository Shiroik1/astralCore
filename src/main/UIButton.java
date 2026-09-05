package main;

import java.awt.*;

public class UIButton {
    public Rectangle bounds = new Rectangle();
    public boolean hovered;

    public void setBounds(int x, int y, int width, int height){
        bounds.setBounds(x, y, width, height);
    }

    public boolean contains(int mouseX, int mouseY){
        return bounds.contains(mouseX, mouseY);
    }
}
