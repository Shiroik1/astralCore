package object;

import art.AssassinGear;
import art.WeaponArt;
import entity.Entity;
import main.Gamepanel;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

public final class OBJ_dualdaggers extends Entity {
    private static final EnumMap<AssassinGear,BufferedImage> ICONS=new EnumMap<>(AssassinGear.class);
    public final ItemType itemType;
    public final AssassinGear gear;
    public final WeaponArt.Kind kind;

    public OBJ_dualdaggers(Gamepanel gp){this(gp,ItemType.DUAL_DAGGERS,AssassinGear.ALLEY);}

    OBJ_dualdaggers(Gamepanel gp,ItemType itemType,AssassinGear gear){
        super(gp);
        this.itemType=itemType;this.gear=gear;kind=WeaponArt.Kind.forAssassin(gear);
        name=gear.daggers;type=type_sword;attackValue=gear.power;
        attackArea.width=52;attackArea.height=52;
        description="("+name+")\nAssassin · Level "+gear.level+"\n+"+gear.power+" Power";
        synchronized(ICONS){
            down1=ICONS.computeIfAbsent(gear,tier->{
                var icon=new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);
                var g=icon.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                WeaponArt.draw(g,kind,16,32,-18,1,0,0,0,1.35f);
                WeaponArt.draw(g,kind,32,32,-18,-1,0,0,0,1.35f);
                g.dispose();
                return icon;
            });
        }
    }
}
