package art;

import java.awt.*;
import java.awt.geom.*;

/** Staff silhouettes and robe palettes from the Mage concept sheet. */
public enum MageGear {
    HEARTH("Hearth Apprentice","Ember Ash Staff",1,0,1,0,0,0xEAD7BD,0x786184,0xA88362,0xE9AB70,0xD2A36C),
    FROST("Frost Scribe","Rimeglass Staff",5,2,3,2,1,0xC1B9CC,0x686A87,0x786773,0xB0DDE7,0xB6BDC9),
    PRISM("Prism Adept","Prismweave Staff",10,3,6,4,3,0x847298,0x5D506C,0x826A83,0xD5C0EB,0xD0B38C),
    STAR("Starward","Starward Crook",17,5,10,6,5,0x69647F,0x4F4B68,0x705D76,0xBBDDF2,0xCCC5D7),
    ASTRAL("Astral Regent","Astral Crown Staff",25,6,15,8,8,0x60506B,0x4C405B,0xA48065,0xFFF0CF,0xE3B47D);

    public final String outfit,staff;
    public final int level,health,power,armor,mana;
    public final Color robe,mantle,wood,crystal,trim;

    MageGear(String outfit,String staff,int level,int health,int power,int armor,int mana,
             int robe,int mantle,int wood,int crystal,int trim){
        this.outfit=outfit;this.staff=staff;this.level=level;this.health=health;this.power=power;
        this.armor=armor;this.mana=mana;this.robe=new Color(robe);this.mantle=new Color(mantle);
        this.wood=new Color(wood);this.crystal=new Color(crystal);this.trim=new Color(trim);
    }

    public Color pants(){return ArtKit.mix(mantle,new Color(0x413846),0.47f);}
    public Color boots(){return ArtKit.mix(mantle,new Color(0x5F443D),0.62f);}
    public Color gloves(){return ArtKit.mix(mantle,new Color(0x866A6A),0.48f);}

    void drawStaff(Graphics2D graphics,AffineTransform transform){
        var g=(Graphics2D)graphics.create();
        g.transform(transform);
        g.setStroke(new BasicStroke(3.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x443641));g.draw(new Line2D.Float(0,-22,0,13));
        g.setStroke(new BasicStroke(2.1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(wood);g.draw(new Line2D.Float(0,-22,0,13));
        g.setColor(trim);
        g.fill(new RoundRectangle2D.Float(-1.8f,9.5f,3.6f,4.8f,2,2));
        g.fill(new RoundRectangle2D.Float(-1.8f,-15,3.6f,3.8f,2,2));
        var frame=new Path2D.Float();
        switch(this){
            case HEARTH -> {frame.moveTo(-5,-26);frame.quadTo(-5,-21,0,-20);frame.quadTo(5,-21,5,-26);}
            case FROST -> {frame.moveTo(-5,-27);frame.lineTo(-4,-23);frame.lineTo(0,-20);frame.lineTo(4,-23);frame.lineTo(5,-27);}
            case PRISM -> {frame.moveTo(0,-35);frame.lineTo(7,-27);frame.lineTo(0,-19);frame.lineTo(-7,-27);frame.closePath();}
            case STAR -> {frame.moveTo(4,-34);frame.curveTo(-8,-38,-11,-21,-1,-20);frame.quadTo(2,-20,5,-22);}
            case ASTRAL -> {frame.moveTo(-7,-33);frame.lineTo(-8,-28);frame.lineTo(0,-19);frame.lineTo(8,-28);frame.lineTo(7,-33);}
        }
        g.setStroke(new BasicStroke(4,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x554451));g.draw(frame);
        g.setStroke(new BasicStroke(2.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(trim);g.draw(frame);
        int x=this==STAR?2:0;
        var gem=new Path2D.Float();
        gem.moveTo(x,-33);gem.lineTo(x+4,-27);gem.lineTo(x,-22);gem.lineTo(x-4,-27);gem.closePath();
        g.setColor(crystal);g.fill(gem);
        g.setColor(ArtKit.mix(crystal,Color.WHITE,0.65f));
        g.fill(new Polygon(new int[]{x,x,x-4},new int[]{-33,-27,-27},3));
        if(this==ASTRAL)g.fill(new Polygon(new int[]{0,2,0,-2},new int[]{-40,-37,-34,-37},4));
        g.dispose();
    }
}
