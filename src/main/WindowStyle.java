package main;
import java.awt.*;
/** Shared opaque, black-and-white window chrome. */
public final class WindowStyle {
    private WindowStyle(){}
    public static void draw(Graphics2D g,int x,int y,int width,int height){
        g.setColor(Color.BLACK);g.fillRoundRect(x,y,width,height,12,12);
        g.setColor(Color.WHITE);g.setStroke(new BasicStroke(2));g.drawRoundRect(x+1,y+1,width-2,height-2,10,10);
    }
}
