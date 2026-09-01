package main;

import java.awt.*;

public class UIButton {
    public Rectangle bounds = new Rectangle();
    public boolean hovered;
    public boolean enabled = true;

    public void setBounds(int x, int y, int width, int height){
        bounds.setBounds(x, y, width, height);
    }

    public boolean checkHover(int mouseX, int mouseY){
        hovered = enabled && bounds.contains(mouseX, mouseY);
        return  hovered;
    }

    public boolean checkClick(int mouseX, int mouseY){
        return enabled && bounds.contains(mouseX, mouseY);
    }
}
