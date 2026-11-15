package object;

import art.PriestGear;
import art.WeaponArt;
import entity.Entity;
import main.Gamepanel;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

public final class OBJ_prieststaff extends Entity {
    private static final EnumMap<PriestGear,BufferedImage> ICONS=new EnumMap<>(PriestGear.class);
    public final ItemType itemType;
    public final PriestGear gear;
    public final WeaponArt.Kind kind;

    OBJ_prieststaff(Gamepanel gp,ItemType itemType,PriestGear gear){
        super(gp);
        this.itemType=itemType;this.gear=gear;kind=WeaponArt.Kind.forPriest(gear);
        name=gear.staff;type=type_staff;attackValue=gear.power;
        attackArea.width=40;attackArea.height=40;
        description="("+name+")\nPriest · Level "+gear.level+"\n+"+gear.power+" Power";
        synchronized(ICONS){
            down1=ICONS.computeIfAbsent(gear,tier->{
                var icon=new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);
                var g=icon.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                WeaponArt.draw(g,kind,20,32,28,1,0,0,0,0.85f);
                g.dispose();
                return icon;
            });
        }
    }
}
