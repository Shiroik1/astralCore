package object;

import art.MageGear;
import art.WeaponArt;
import entity.Entity;
import main.Gamepanel;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

public class OBJ_staff extends Entity {
    private static final EnumMap<MageGear,BufferedImage> ICONS=new EnumMap<>(MageGear.class);
    public final ItemType itemType;
    public final MageGear gear;
    public final WeaponArt.Kind kind;

    public OBJ_staff(Gamepanel gp){this(gp,ItemType.STAFF,MageGear.HEARTH);}

    OBJ_staff(Gamepanel gp,ItemType itemType,MageGear gear){
        super(gp);
        this.itemType=itemType;this.gear=gear;kind=WeaponArt.Kind.forMage(gear);
        name=gear.staff;type=type_staff;attackValue=gear.power;
        attackArea.width=40;attackArea.height=40;
        description="("+name+")\nMage · Level "+gear.level+"\n+"+gear.power+" Power";
        synchronized(ICONS){
            down1=ICONS.computeIfAbsent(gear,tier->{
                var icon=new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);
                var g=icon.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                WeaponArt.draw(g,kind,21,31,28,1,0,0,0,0.87f);
                g.dispose();
                return icon;
            });
        }
    }
}
