package entity;

import main.Gamepanel;
import java.awt.*;
import java.awt.geom.*;
import java.util.concurrent.ThreadLocalRandom;

/** Short-lived vector effects. Skill events replay these on each peer without applying gameplay twice. */
public final class WarriorSkillEffect extends Entity {
    public enum Kind { BASH, BULWARK, RETALIATION, BREAKER, RALLY }
    public final Kind kind;
    private final Player owner;
    private final boolean area;
    private final double facing;
    private int age;
    private final int lifetime;
    private WarriorSkillEffect(Gamepanel gp,Player owner,Kind kind){
        super(gp);this.owner=owner;this.kind=kind;area=owner.inWell;
        worldX=owner.worldX+24;worldY=owner.worldY+36;
        lifetime=switch(kind){case RALLY->42;case BREAKER->16;default->32;};
        facing=switch(owner.direction){case "up"->-Math.PI/2;case "down"->Math.PI/2;case "left"->Math.PI;default->0;};
        groundEffect=kind==Kind.RALLY;
    }
    public static void spawn(Gamepanel gp,Player owner,String name){
        if(!"warrior".equals(owner.playerClass) || name==null)return;
        Kind kind=switch(name){case "Shield Bash"->Kind.BASH;case "Bulwark"->Kind.BULWARK;case "Retaliation"->Kind.RETALIATION;case "Shield Breaker"->Kind.BREAKER;case "Rally"->Kind.RALLY;default->null;};
        if(kind==null)return;
        gp.particleList.add(new WarriorSkillEffect(gp,owner,kind));
        Color color=switch(kind){case BASH,BULWARK->new Color(155,214,255);case RETALIATION->new Color(220,237,255);case BREAKER->new Color(255,194,107);case RALLY->new Color(145,235,168);};
        var random = ThreadLocalRandom.current();
        for(int i=0;i<12;i++){
            double angle=kind==Kind.BASH || kind==Kind.BREAKER?facing(owner)+random.nextDouble(-0.95,0.95):random.nextDouble(2*Math.PI);
            float x=(float)Math.cos(angle), y=(float)Math.sin(angle);
            float speed = random.nextFloat(1.6f, 2.8f);
            Particle.emit(owner, Particle.Style.SQUARE, color, random.nextFloat(2.8f, 4.8f), random.nextInt(13, 20),
                    x*speed, y*speed, x*15, y*15-4);
        }
    }
    private static double facing(Player p){return switch(p.direction){case "up"->-Math.PI/2;case "down"->Math.PI/2;case "left"->Math.PI;default->0;};}
    @Override public int sortY(){
        if(kind==Kind.BREAKER && facing!=-Math.PI/2)return owner.sortY()-1;
        return super.sortY();
    }

    @Override public void update(){
        if(++age>=lifetime || owner.inWell!=area || owner.isDead){alive=false;return;}
        if(kind==Kind.BULWARK || kind==Kind.RETALIATION){worldX=owner.worldX+24;worldY=owner.worldY+36;}
    }
    @Override public void draw(Graphics2D g){
        Player camera=gp.localPlayer();if(camera==null || camera.inWell!=area)return;
        float t=age/(float)lifetime;Composite composite=g.getComposite();Stroke stroke=g.getStroke();AffineTransform transform=g.getTransform();
        g.translate(worldX-camera.worldX+camera.screenX,worldY-camera.worldY+camera.screenY);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1-t));g.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(kind){
            case BASH -> {
                g.rotate(facing);g.setColor(new Color(160,220,255));float reach=16+t*45;
                g.draw(new Arc2D.Float(reach-20,-25,36,50,-80,160,Arc2D.OPEN));
                for(int i=-1;i<=1;i++)g.draw(new Line2D.Float(reach-15,i*10,reach+10,i*14));
            }
            case BULWARK -> {
                g.setColor(new Color(120,193,255));float size=18+8*t;Path2D shield=new Path2D.Float();
                shield.moveTo(-size,-20);shield.quadTo(0,-29,size,-20);shield.lineTo(size-4,9);shield.quadTo(0,30,-size+4,9);shield.closePath();g.draw(shield);
                g.setColor(new Color(235,249,255));g.drawLine(0,-16,0,13);g.drawLine(-10,-3,10,-3);
            }
            case RETALIATION -> {
                g.rotate(t*Math.PI*1.6);g.setColor(new Color(219,239,255));
                g.draw(new Arc2D.Float(-28,-28,56,56,20,125,Arc2D.OPEN));g.draw(new Arc2D.Float(-28,-28,56,56,200,125,Arc2D.OPEN));
                g.drawLine(24,-12,28,-24);g.drawLine(28,-24,15,-22);g.drawLine(-24,12,-28,24);g.drawLine(-28,24,-15,22);
            }
            case BREAKER -> art.MeleeSlash.draw(g,0,-7,facing,68,66,age/2-3,false);
            case RALLY -> {
                g.setColor(new Color(145,235,168));float r=20+t*100;g.draw(new Ellipse2D.Float(-r,-r*.45f,r*2,r*.9f));
                for(int i=-1;i<=1;i++){float x=i*20,y=-15-t*35-Math.abs(i)*8;g.draw(new Line2D.Float(x-5,y,x+5,y));g.draw(new Line2D.Float(x,y-5,x,y+5));}
            }
        }
        g.setTransform(transform);g.setStroke(stroke);g.setComposite(composite);
    }
}
