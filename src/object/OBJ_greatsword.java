package object;

import art.BerserkerGear;
import art.WeaponArt;
import entity.Entity;
import main.Gamepanel;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

public final class OBJ_greatsword extends Entity {
    private static final EnumMap<BerserkerGear,BufferedImage> ICONS=new EnumMap<>(BerserkerGear.class);
    public final ItemType itemType;
    public final BerserkerGear gear;
    public final WeaponArt.Kind kind;

    OBJ_greatsword(Gamepanel gp,ItemType itemType,BerserkerGear gear){
        super(gp);this.itemType=itemType;this.gear=gear;kind=WeaponArt.Kind.forBerserker(gear);
        name=gear.sword;type=type_sword;attackValue=gear.power;
        attackArea.setSize(92,78);
        description="("+name+")\nBerserker · Level "+gear.level+"\n+"+gear.power+" Power";
        synchronized(ICONS){
            down1=ICONS.computeIfAbsent(gear,tier->{
                var icon=new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);
                var g=icon.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                WeaponArt.draw(g,kind,16,34,37,1,0,0,0,1.03f);
                g.dispose();
                return icon;
            });
        }
    }
}
