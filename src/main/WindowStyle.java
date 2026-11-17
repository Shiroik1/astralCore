package main;
import java.awt.*;
/** Shared opaque, black-and-white window chrome. */
public final class WindowStyle {
    private WindowStyle(){}
    public static void draw(Graphics2D g,int x,int y,int width,int height){
        ui.UITheme.panel(g,new Rectangle(x,y,width,height));
    }
}
