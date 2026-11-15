package art;

import java.awt.*;
import java.awt.geom.*;

/** Tier-specific sword and shield shapes with a shared grip and a slate-blue tabard palette. */
public enum PaladinGear {
    GATE("Gate Sentinel","Gatekeeper Sword","Roundshield",1,0,3,1,3,0x697F90,0x526B83,0xC9B097),
    ROAD("Road Warden","Wayguard Sword","Kite Shield",5,3,4,2,5,0x667D90,0x52697C,0xC7AE8E),
    VOW("Iron Vow","Vowsteel Sword","Heater Shield",10,8,7,3,7,0x76838C,0x607F91,0xDEBF91),
    DAWN("Dawn Bastion","Dawnward Sword","Suncrest Shield",17,13,9,4,10,0x52657D,0x536C87,0xDFB67D),
    CITADEL("Citadel Keeper","Citadel Blade","Bastion Shield",25,22,13,5,13,0x49596E,0x48617E,0xF0BF7C);

    public enum Piece { SWORD, SHIELD, ARMOR }
    public final String outfit,sword,shield;
    public final int level,health,power,armor,shieldArmor;
    public final Color plate,blue,trim;

    PaladinGear(String outfit,String sword,String shield,int level,int health,int power,int armor,int shieldArmor,int plate,int blue,int trim){
        this.outfit=outfit;this.sword=sword;this.shield=shield;this.level=level;this.health=health;this.power=power;
        this.armor=armor;this.shieldArmor=shieldArmor;this.plate=new Color(plate);this.blue=new Color(blue);this.trim=new Color(trim);
    }

    public Color pants(){return ArtKit.mix(plate,new Color(0x443F46),0.6f);}
    public Color boots(){return new Color(0x6B554C);}
    public Color gloves(){return new Color(0x8A6F5A);}
    public Color mantle(){return ArtKit.mix(blue,new Color(0x444F69),0.35f);}

    void drawSword(Graphics2D graphics,AffineTransform transform){
        var g=(Graphics2D)graphics.create();g.transform(transform);
        float length=this==GATE?24:this==ROAD?26:29;
        float w=this==GATE || this==ROAD?3.7f:4.8f;
        var blade=new Path2D.Float();
        blade.moveTo(-w,-4);blade.lineTo(-w,-length+6);blade.lineTo(0,-length);
        blade.lineTo(w,-length+6);blade.lineTo(w,-4);blade.closePath();
        paint(g,blade,new Color(0xF2E2CB));
        g.setColor(ordinal()>=VOW.ordinal()?blue:new Color(0xBEB1AB));
        g.fill(new Polygon(new int[]{0,2,2,0},new int[]{-6,-8,(int)-length+7,(int)-length+4},4));
        paint(g,new RoundRectangle2D.Float(-2,0,4,8,2,2),new Color(0x69534D));
        var guard=new Path2D.Float();
        guard.moveTo(-6,-5);guard.lineTo(-2,-4);guard.lineTo(0,-7);
        guard.lineTo(2,-4);guard.lineTo(6,-5);guard.lineTo(5,-1);
        guard.lineTo(0,1);guard.lineTo(-5,-1);guard.closePath();
        paint(g,guard,trim);g.setColor(trim);g.fill(new Ellipse2D.Float(-2.6f,6,5.2f,4));
        g.dispose();
    }

    public void drawShield(Graphics2D graphics,float x,float y,float size,boolean profile){
        var g=(Graphics2D)graphics.create();g.translate(x,y);g.scale(size*(profile?0.66:1),size);
        if(this==GATE){
            paint(g,new Ellipse2D.Float(-10,-10,20,20),new Color(0xD1BBA0));
            paint(g,new Ellipse2D.Float(-8,-8,16,16),new Color(0x785D52));
            g.setColor(new Color(0xA68873));g.setStroke(new BasicStroke(0.65f));
            for(int i:new int[]{-4,4})g.drawLine(i,-6,i,6);
            paint(g,new Ellipse2D.Float(-3.2f,-3.2f,6.4f,6.4f),new Color(0xDFBA88));
        }else{
            float tip=this==ROAD?13.5f:12f;
            var shell=new Path2D.Float();
            shell.moveTo(-10,-8);
            if(this==CITADEL){shell.lineTo(-7,-10);shell.lineTo(0,-12.5f);shell.lineTo(7,-10);shell.lineTo(10,-8);}
            else if(this==ROAD){shell.lineTo(0,-11);shell.lineTo(10,-8);}
            else shell.quadTo(0,-13,10,-8);
            shell.quadTo(9,5,0,tip);shell.quadTo(-9,5,-10,-8);shell.closePath();
            paint(g,shell,trim);
            var inset=AffineTransform.getScaleInstance(0.76,0.79).createTransformedShape(shell);
            paint(g,inset,blue);
            g.setColor(new Color(0xF5DDB3));g.fill(star(0,-1,4.8f,7.6f));
            if(ordinal()>=DAWN.ordinal()){
                var pose=g.getTransform();g.rotate(Math.PI/4,0,-1);
                g.fill(star(0,-1,4.1f,4.1f));g.setTransform(pose);
            }
        }
        g.dispose();
    }

    public static Shape star(float x,float y,float w,float h){
        var p=new Path2D.Float();
        p.moveTo(x,y-h);p.lineTo(x+w*0.25f,y-h*0.22f);p.lineTo(x+w,y);
        p.lineTo(x+w*0.25f,y+h*0.22f);p.lineTo(x,y+h);p.lineTo(x-w*0.25f,y+h*0.22f);
        p.lineTo(x-w,y);p.lineTo(x-w*0.25f,y-h*0.22f);p.closePath();
        return p;
    }

    private static void paint(Graphics2D g,Shape shape,Color color){
        g.setStroke(new BasicStroke(1.3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x51454A));g.draw(shape);g.setColor(color);g.fill(shape);
    }
}
