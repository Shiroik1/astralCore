package object;

import art.PaladinGear;
import art.WeaponArt;
import entity.Entity;
import main.Gamepanel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

/** A Paladin's paired sword or shield. The armor uses the standard apparel slot. */
public final class OBJ_paladinGear extends Entity {
    private static final EnumMap<ItemType,BufferedImage> ICONS=new EnumMap<>(ItemType.class);
    public final ItemType itemType;
    public final PaladinGear gear;

    OBJ_paladinGear(Gamepanel gp,ItemType itemType,PaladinGear gear,boolean shield){
        super(gp);
        this.itemType=itemType;this.gear=gear;
        name=shield?gear.shield:gear.sword;
        type=shield?type_shield:type_sword;
        if(shield)defenseValue=gear.shieldArmor;
        else{attackValue=gear.power;attackArea.setSize(62,59);}
        description="("+name+")\nPaladin · Level "+gear.level+"\n+"+(shield?gear.shieldArmor+" Armor":gear.power+" Power");
        synchronized(ICONS){
            down1=ICONS.computeIfAbsent(itemType,key->{
                var icon=new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);
                var g=icon.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                if(shield)gear.drawShield(g,24,22,1.53f,false);
                else WeaponArt.draw(g,WeaponArt.Kind.forPaladin(gear),17,34,35,1,0,0,0,1.05f);
                g.dispose();return icon;
            });
        }
    }
}
