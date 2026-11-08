package object;

import entity.Entity;
import main.Gamepanel;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public final class OBJ_slimeessence extends Entity {
    private static BufferedImage icon;
    public OBJ_slimeessence(Gamepanel gp){
        super(gp);name="Slime Essence";stackable=true;maxStackSize=99;
        description="(Slime Essence)\nOffer at the well altar\nto summon Hell Slime.\nConsumed on summoning.";
        synchronized(OBJ_slimeessence.class){
            if(icon==null){
                icon=new BufferedImage(gp.tileSize,gp.tileSize,BufferedImage.TYPE_INT_ARGB);var g=icon.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Path2D drop=new Path2D.Float();drop.moveTo(24,5);drop.curveTo(18,17,9,23,11,33);
                drop.curveTo(13,45,35,45,37,33);drop.curveTo(39,23,30,17,24,5);drop.closePath();
                g.setColor(new Color(0x344A3B));g.setStroke(new BasicStroke(4));g.draw(drop);
                g.setColor(new Color(0x8CD6A6));g.fill(drop);g.setColor(new Color(0xF5F1CB));g.fillOval(16,23,7,11);g.dispose();
            }down1=icon;
        }
    }
}
