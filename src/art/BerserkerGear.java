package art;

import java.awt.*;
import java.awt.geom.*;

/** Tiered forged greatswords with a shared two-handed grip. */
public enum BerserkerGear {
    ROAD("Road Reaver","Fieldbreaker",1,0,4,2,0x565052,0x98544E,0x75635F,0xAA7A63),
    IRON("Iron Roamer","Ironwake Claymore",5,2,6,4,0x5D4F4C,0xA35C51,0x705D5B,0xBB896D),
    RIDGE("Ridge Vanguard","Ridgecleaver",10,6,10,6,0x565157,0xA4534E,0x6E5F62,0xC39777),
    CINDER("Cinder Reaver","Cindermaw",17,10,14,9,0x50464C,0x9C4B46,0xA3574D,0xD0986E),
    FAULT("Faultline Champion","Faultline Greatsword",25,15,21,12,0x443B43,0x93443F,0x56494E,0xEAB37D);

    public final String outfit,sword;
    public final int level,health,power,armor;
    public final Color armorColor,scarf,steel,trim;

    BerserkerGear(String outfit,String sword,int level,int health,int power,int armor,int metal,int cloth,int steel,int trim){
        this.outfit=outfit;this.sword=sword;this.level=level;this.health=health;this.power=power;this.armor=armor;
        armorColor=new Color(metal);scarf=new Color(cloth);this.steel=new Color(steel);this.trim=new Color(trim);
    }

    public Color pants(){return ArtKit.mix(armorColor,new Color(0x352C33),0.51f);}
    public Color boots(){return new Color(0x715248);}
    public Color gloves(){return new Color(0x88685B);}

    void drawSword(Graphics2D graphics,AffineTransform transform){
        var g=(Graphics2D)graphics.create();
        g.transform(transform);
        float width=switch(this){case ROAD->6.1f;case IRON->6.45f;case RIDGE->6.65f;case CINDER->6.9f;case FAULT->7.3f;};
        float length=switch(this){case ROAD->32;case IRON->33;case RIDGE->34;case CINDER->35;case FAULT->37;};
        var blade=new Path2D.Float();
        blade.moveTo(-width,-5);blade.lineTo(-width,-length+9);blade.lineTo(0,-length);
        blade.lineTo(width,-length+9);blade.lineTo(width,-5);blade.closePath();
        g.setStroke(new BasicStroke(1.25f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x49383B));g.draw(blade);
        g.setColor(new Color(0xE3C6B1));g.fill(blade);

        float inner=width-2.15f;
        var spine=new Path2D.Float();
        spine.moveTo(-inner,-5);spine.lineTo(-inner,-length+10);spine.lineTo(0,-length+4);
        spine.lineTo(inner,-length+10);spine.lineTo(inner,-5);spine.closePath();
        g.setColor(steel);g.fill(spine);
        var facet=new Path2D.Float();
        facet.moveTo(0,-5);facet.lineTo(0,-length+4);facet.lineTo(inner,-length+10);facet.lineTo(inner,-5);facet.closePath();
        g.setColor(ArtKit.mix(steel,new Color(0x443238),0.36f));g.fill(facet);
        g.setColor(new Color(0xC4A592));g.setStroke(new BasicStroke(0.75f));
        g.draw(new Line2D.Float(-inner,-8,-inner,-length+10));

        g.setColor(new Color(0x6D4B43));g.fill(new RoundRectangle2D.Float(-2,-4,4,13,2,2));
        g.setColor(new Color(0xAE7E62));g.setStroke(new BasicStroke(0.85f));
        for(int i=0;i<4;i++)g.draw(new Line2D.Float(-1.45f,i*2,1.45f,i*2+1));
        float wing=this==ROAD?7.2f:this==FAULT?9.6f:8.6f;
        var guard=new Path2D.Float();
        guard.moveTo(-wing,-5);guard.lineTo(-wing+1.7f,-8);guard.lineTo(-3.2f,-7.3f);
        guard.lineTo(0,-8.5f);guard.lineTo(3.2f,-7.3f);guard.lineTo(wing-1.7f,-8);
        guard.lineTo(wing,-5);guard.lineTo(wing-1.1f,-3);guard.lineTo(-wing+1.1f,-3);guard.closePath();
        g.setColor(new Color(0x553E3A));g.setStroke(new BasicStroke(1.4f));g.draw(guard);
        g.setColor(trim);g.fill(guard);
        g.setColor(ArtKit.mix(trim,new Color(0xFFE1B7),0.5f));
        g.draw(new Line2D.Float(-wing+2,-6.5f,wing-2,-6.5f));
        g.fill(gem(0,8.5f,3.2f,2.5f));

        if(this!=ROAD){
            float lengthMark=this==CINDER || this==FAULT?5.7f:4.2f;
            g.setColor(this==FAULT?new Color(0xC87C59):trim);
            g.fill(gem(0,-11.1f,3.1f,lengthMark));
            g.setColor(this==CINDER?new Color(0x86473F):new Color(0x4D3940));
            g.fill(gem(0,-11.5f,1.45f,lengthMark-2));
        }
        if(this==FAULT){
            g.setColor(new Color(0xF5CA89));g.fill(gem(0,-5.4f,3.7f,4.5f));
            g.setColor(new Color(0xA86D50));g.fill(gem(0,-5.4f,1.5f,2.2f));
        }
        g.dispose();
    }

    private static Shape gem(float x,float y,float width,float height){
        var shape=new Path2D.Float();
        shape.moveTo(x,y-height);shape.lineTo(x+width,y);shape.lineTo(x,y+height);
        shape.lineTo(x-width,y);shape.closePath();
        return shape;
    }
}
