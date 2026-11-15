package art;

import java.awt.*;
import java.awt.geom.*;

/** The five lantern, leaf, bell, ring, and crown silhouettes and their matching robes. */
public enum PriestGear {
    WAY("Wayfarer","Waylight Staff",1,0,1,1,0,0xECD8B5,0x8B9775,0xB78B60,0xF0C783),
    GROVE("Grove Keeper","Greenbough Staff",5,2,3,3,1,0xDFD5B8,0x768C6C,0x9E815C,0xD0C486),
    BELL("Bellwarden","Vesper Bellstaff",10,4,5,5,3,0xCAC4B2,0x7F8F80,0x98755A,0xDBB075),
    DAWN("Dawn Herald","Dawnring Staff",17,7,7,7,6,0xECE1C4,0x647F73,0xAD876A,0xEBC587),
    HEARTH("Sanctuary Keeper","Hearthcrown Staff",25,9,10,10,10,0xE5D0A8,0x546F67,0xA47C5F,0xE6BA73);

    public final String outfit,staff;
    public final int level,health,power,armor,mana;
    public final Color robe,mantle,wood,trim;

    PriestGear(String outfit,String staff,int level,int health,int power,int armor,int mana,int robe,int mantle,int wood,int trim){
        this.outfit=outfit;this.staff=staff;this.level=level;this.health=health;this.power=power;this.armor=armor;this.mana=mana;
        this.robe=new Color(robe);this.mantle=new Color(mantle);this.wood=new Color(wood);this.trim=new Color(trim);
    }

    public Color pants(){return ArtKit.mix(mantle,new Color(0x493D37),0.46f);}
    public Color boots(){return ArtKit.mix(wood,new Color(0x53413B),0.55f);}
    public Color gloves(){return ArtKit.mix(robe,wood,0.47f);}

    void drawStaff(Graphics2D graphics,AffineTransform transform){
        var g=(Graphics2D)graphics.create();
        g.transform(transform);
        g.setStroke(new BasicStroke(3.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x493D36));g.draw(new Line2D.Float(0,-22,0,13));
        g.setStroke(new BasicStroke(2.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(wood);g.draw(new Line2D.Float(0,-22,0,13));
        g.setColor(trim);g.fill(diamond(0,12,2.1f,3));
        g.fill(new RoundRectangle2D.Float(-2,-16,4,3.5f,2,2));
        switch(this){
            case WAY -> {
                g.draw(new Arc2D.Float(-7,-35,14,15,8,164,Arc2D.OPEN));
                g.fill(new RoundRectangle2D.Float(-5,-32,10,11,3,3));
                g.setColor(Palette.PLASTER);g.fill(new RoundRectangle2D.Float(-3.3f,-30,6.6f,7.8f,2,2));
                g.setColor(trim);g.fill(diamond(0,-36,2.6f,4));
            }
            case GROVE -> {
                g.setColor(new Color(0x9EB186));
                for(int side:new int[]{-1,1}){
                    var leaf=new Path2D.Float();
                    leaf.moveTo(0,-20);leaf.quadTo(side*2,-31,side*8,-33);
                    leaf.quadTo(side*8,-24,0,-20);g.fill(leaf);
                }
                g.setColor(new Color(0xE6E9BF));g.fill(diamond(0,-32,4,9));
            }
            case BELL -> {
                g.draw(new Arc2D.Float(-8,-37,16,18,0,180,Arc2D.OPEN));
                g.fill(diamond(0,-37,3,4));
                var bell=new Path2D.Float();
                bell.moveTo(-4,-32);bell.quadTo(-5,-25,-7,-23);bell.lineTo(7,-23);
                bell.quadTo(5,-25,4,-32);bell.quadTo(0,-36,-4,-32);bell.closePath();
                g.setColor(new Color(0xB28452));g.fill(bell);
                g.setColor(trim);g.fill(new RoundRectangle2D.Float(-7,-25,14,3.3f,3,3));
                g.fill(new Ellipse2D.Float(-1.9f,-23,3.8f,4));
            }
            case DAWN -> {
                g.draw(new Ellipse2D.Float(-8,-39,16,16));
                g.fill(diamond(0,-39,2.5f,4));
                g.setColor(Palette.PLASTER);g.fill(diamond(0,-31,4.5f,6));
            }
            case HEARTH -> {
                for(int i=-2;i<=2;i++){
                    var pose=g.getTransform();
                    g.rotate(i*0.34,0,-21);
                    var petal=new Path2D.Float();
                    petal.moveTo(0,-21);petal.quadTo(-7,-27,0,-39);
                    petal.quadTo(7,-27,0,-21);g.fill(petal);
                    g.setTransform(pose);
                }
                g.setColor(Palette.PLASTER);g.fill(diamond(0,-30,4.8f,8));
            }
        }
        g.dispose();
    }

    private static Shape diamond(float x,float y,float rx,float ry){
        var diamond=new Path2D.Float();
        diamond.moveTo(x,y-ry);diamond.lineTo(x+rx,y);diamond.lineTo(x,y+ry);diamond.lineTo(x-rx,y);diamond.closePath();
        return diamond;
    }
}
