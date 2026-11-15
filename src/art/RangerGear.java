package art;

import java.awt.*;
import java.awt.geom.*;

/** Ranger tier values and the matching bow and apparel palette. */
public enum RangerGear {
    TRAIL("Trail Scout", "Trailwood Shortbow", 1, 1, 2, 2, 0xB78859, 0xE7C99F, 0x4F7161, 0xF0DEC4, 17, 0),
    BRIAR("Briar Stalker", "Briar Recurve", 5, 3, 4, 5, 0x85624B, 0xA5AD78, 0x49624D, 0xB3AE80, 17, 5),
    RIDGE("Ridgewatch", "Ridgewatch Longbow", 10, 6, 7, 8, 0xAD7D57, 0xEFE1BF, 0x5A6561, 0xC6A789, 23, 1),
    MOON("Moonbough", "Moonbough Reflex Bow", 17, 10, 11, 11, 0x526D65, 0xD8DAD3, 0x536D71, 0x9FB2A5, 21, 7),
    SUN("Suncrest", "Suncrest Warbow", 25, 14, 16, 14, 0x765444, 0xE9BA70, 0x3E5E52, 0xEEE0B8, 23, 6);

    public final String outfit, bow;
    public final int level, health, power, armor;
    public final Color wood, trim, cloak, tunic;
    private final int height, hook;

    RangerGear(String outfit,String bow,int level,int health,int power,int armor,
               int wood,int trim,int cloak,int tunic,int height,int hook){
        this.outfit=outfit;this.bow=bow;this.level=level;this.health=health;this.power=power;this.armor=armor;
        this.wood=new Color(wood);this.trim=new Color(trim);this.cloak=new Color(cloak);this.tunic=new Color(tunic);
        this.height=height;this.hook=hook;
    }

    public Color pants(){return ArtKit.mix(cloak,wood,0.35f);}
    public Color boots(){return ArtKit.mix(wood,new Color(0x413636),0.6f);}
    public Color gloves(){return ArtKit.mix(wood,new Color(0x524039),0.45f);}

    private float tip(){return -10+hook*0.65f;}
    float nock(float draw){return tip()*(1-draw)-15*draw;}

    public void drawBow(Graphics2D g, float x, float y, float scale, float draw){
        var old = g.getTransform();
        var stroke = g.getStroke();
        g.translate(x,y);g.scale(scale,scale);
        float tip=tip(), nock=nock(draw);
        Path2D limb=new Path2D.Float();
        limb.moveTo(tip,height);
        limb.curveTo(-12,height-5,5-hook*0.5f,9,0,0);
        limb.curveTo(5-hook*0.5f,-9,-12,-height+5,tip,-height);
        g.setStroke(new BasicStroke(1.1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xEFE5CD));
        Path2D string=new Path2D.Float();string.moveTo(tip,-height);string.lineTo(nock,0);string.lineTo(tip,height);g.draw(string);
        g.setColor(wood);g.setStroke(new BasicStroke(4.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(limb);
        g.setColor(trim);g.setStroke(new BasicStroke(2.3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(tip,-height,tip-hook*0.4f,-height+4));
        g.draw(new Line2D.Float(tip,height,tip-hook*0.4f,height-4));
        g.setColor(new Color(0x584139));g.fill(new RoundRectangle2D.Float(-2.5f,-4,5,8,2,2));
        if(this==MOON || this==SUN){
            g.setColor(trim);
            Path2D inset=new Path2D.Float();
            inset.moveTo(0,-5);inset.lineTo(4,0);inset.lineTo(0,5);inset.lineTo(-4,0);
            inset.closePath();g.fill(inset);
        }
        if(draw>0.15f) drawArrow(g,nock-2,0,12-nock,trim,1);
        g.setStroke(stroke);g.setTransform(old);
    }

    /** The caller points the positive x axis along the flight path. */
    public static void drawArrow(Graphics2D g,float x,float y,float length,Color feather,float scale){
        g.setColor(new Color(0x554334));
        g.fill(new RoundRectangle2D.Float(x-scale,y-scale*1.65f,length+scale*2,scale*3.3f,scale,scale));
        g.setColor(new Color(0xECD1A4));
        g.fill(new Rectangle2D.Float(x,y-scale*0.8f,length,scale*1.6f));
        Path2D tip=new Path2D.Float();tip.moveTo(x+length+5*scale,y);
        tip.lineTo(x+length-2*scale,y-2.8*scale);tip.lineTo(x+length-2*scale,y+2.8*scale);tip.closePath();
        Stroke old=g.getStroke();g.setStroke(new BasicStroke(scale*1.05f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x554334));g.draw(tip);
        g.setColor(Palette.PLASTER);g.fill(tip);g.setStroke(old);
        Path2D tail=new Path2D.Float();tail.moveTo(x,y-2.4*scale);tail.lineTo(x+6*scale,y-scale);
        tail.lineTo(x+6*scale,y+scale);tail.lineTo(x,y+2.4*scale);tail.closePath();
        g.setColor(feather);g.fill(tail);
    }
}
