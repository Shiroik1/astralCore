package object;

import art.CharacterOutfit;
import entity.Entity;
import main.Gamepanel;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** A visual equipment item. Its slot and palette are independent of the base body. */
public class OBJ_apparel extends Entity {
    public final ItemType itemType;
    public final CharacterOutfit.Slot slot;
    public final Color color;
    public final CharacterOutfit.Theme theme;
    public final boolean longHair;

    public OBJ_apparel(Gamepanel gp, ItemType itemType, CharacterOutfit.Slot slot, String label) {
        this(gp,itemType,slot,label,CharacterOutfit.Theme.LEATHER,false);
    }

    public OBJ_apparel(Gamepanel gp, ItemType itemType, CharacterOutfit.Slot slot, String label,
                       CharacterOutfit.Theme theme, boolean longHair) {
        super(gp);this.itemType=itemType;this.slot=slot;this.theme=theme;this.longHair=longHair;
        color=CharacterOutfit.themed(theme,longHair).color(slot);
        name=label;type=type_apparel;
        description="("+name+")\n"+slot.name().toLowerCase()+" appearance\nDrag to the matching equipment slot.";
        int size=gp.tileSize;down1=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=down1.createGraphics();g.scale(size/48.0,size/48.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Shape shape=switch(slot){
            case HEADPIECE -> new Polygon(new int[]{7,15,20,27,35,41,35,13},new int[]{32,26,10,7,26,32,37,37},8);
            case HAIR -> new Polygon(new int[]{8,12,18,29,36,40,35,32,26,21,16,12},new int[]{21,12,9,9,14,22,33,23,28,21,31,25},12);
            case TORSO -> new Polygon(new int[]{8,17,31,40,35,31,34,14,17,13},new int[]{18,10,10,18,24,21,38,38,21,24},10);
            case PANTS -> new Polygon(new int[]{13,35,33,26,24,22,15},new int[]{10,10,38,38,22,38,38},7);
            case BOOTS -> new Polygon(new int[]{12,22,22,36,37,12},new int[]{10,10,28,30,38,38},6);
            case GLOVES -> new RoundRectangle2D.Float(12,10,24,29,9,9);
            case CLOAK -> new Polygon(new int[]{19,29,39,30,9},new int[]{10,10,37,40,37},5);
        };
        g.setColor(new Color(0x493C40));g.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(shape);
        g.setColor(color);g.fill(shape);g.dispose();
    }
}
