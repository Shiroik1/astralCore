package entity;

import art.Palette;
import main.Gamepanel;
import net.MageEffectState;

import java.awt.*;
import java.awt.geom.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Caster bolts, ground spells, and support cues. Guest replicas animate the same phases without resolving hits. */
public final class MageEffect extends Entity {
    public enum Kind {
        BOLT(0,22,0), EMBER(27,46,1.20f), FROST(8,128,1.40f), DETONATION(4,22,1.5f), COMET(48,72,1.70f),
        RADIANT(0,22,0), HALLOWED(1,180,1.42f), MEND(2,24,0.5f), CHORUS(1,23,2.5f),
        WARD_PULSE(1,13,0.55f), WARD_BREAK(1,18,0.65f), CHORUS_SPARK(1,14,0.48f), SANCTUARY_BLOOM(1,27,2.5f);
        final int impact,end;
        final float radius;
        Kind(int impact,int end,float radius){this.impact=impact;this.end=end;this.radius=radius;}
    }

    private static final Kind[] KINDS=Kind.values();
    private static final AtomicInteger IDS=new AtomicInteger();
    private static final float BOLT_SPEED=18;
    static final String FROST_SLOW="mageFrost";
    static final Color LILAC=new Color(0xD5B3F0), FROST=new Color(0xB5E4EF), EMBER=new Color(0xFFAB87);
    private static final Color GOLD=new Color(0xFFE0A2), INK=new Color(49,39,60,225);
    private static final Stroke DASH=new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,new float[]{10,7},0);
    private static final Stroke DASH_BACK=new BasicStroke(6,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,new float[]{10,7},0);

    private final Kind kind;
    private final Player owner;
    private final int damage,runes;
    private final double heading;
    private float x,y,remaining;
    private int id,age,lastTrail=-1,lastHealCue=-1;
    private boolean impactCue;

    private MageEffect(Gamepanel gp,Player owner,Kind kind,float x,float y,double heading,int damage,int runes){
        super(gp);
        this.kind=kind;this.owner=owner;this.x=x;this.y=y;this.heading=heading;
        this.damage=damage;this.runes=runes;
        if(owner!=null)id=IDS.incrementAndGet();
        worldX=Math.round(x);worldY=Math.round(y);solidArea.setBounds(0,0,0,0);
    }

    static MageEffect bolt(Player owner,int x,int y,double heading,int damage){
        Kind kind=owner.isPriest()?Kind.RADIANT:Kind.BOLT;
        var effect=new MageEffect(owner.gp,owner,kind,x,y,heading,damage,0);
        effect.remaining=owner.gp.tileSize*(kind==Kind.RADIANT?5:6);
        effect.releaseCue();
        return effect;
    }

    static MageEffect mend(Player owner,Player target){
        int x=PriestCombat.centerX(target),y=PriestCombat.centerY(target);
        int dx=x-PriestCombat.centerX(owner),dy=y-PriestCombat.centerY(owner);
        var effect=new MageEffect(owner.gp,owner,Kind.MEND,x,y-11,Math.atan2(dy,dx),0,0);
        effect.remaining=(float)Math.hypot(dx,dy);
        return effect;
    }

    static MageEffect area(Player owner,Kind kind,int x,int y,int damage,int runes){
        return new MageEffect(owner.gp,owner,kind,x,y,0,damage,runes);
    }

    private boolean bolt(){return kind==Kind.BOLT || kind==Kind.RADIANT;}
    private boolean support(){return kind.ordinal()>=Kind.HALLOWED.ordinal();}

    public int id(){return id;}
    public boolean isReplica(){return owner==null;}

    public MageEffectState snapshot(){
        var state=new MageEffectState();
        state.id=id;state.kind=kind.ordinal();state.age=age;state.runes=runes;state.x=x;state.y=y;
        state.remaining=remaining;state.heading=heading;
        return state;
    }

    public static MageEffect replica(Gamepanel gp,MageEffectState state){
        var effect=new MageEffect(gp,null,KINDS[state.kind],state.x,state.y,state.heading,0,state.runes);
        effect.id=state.id;
        effect.syncReplica(state);
        if(effect.bolt() && state.age<=3)effect.releaseCue();
        return effect;
    }

    public void syncReplica(MageEffectState state){
        x=state.x;y=state.y;worldX=Math.round(x);worldY=Math.round(y);
        age=state.age;remaining=state.remaining;alive=true;
        emitImpact();
        emitHealPulse();
    }

    @Override public void update(){
        age++;
        if(bolt()){
            advanceBolt();
            return;
        }
        if(owner!=null){
            if(age==kind.impact && (!support() || kind==Kind.HALLOWED))applyAreaDamage();
            if(kind==Kind.HALLOWED && healingPulse())healArea();
            if(kind==Kind.FROST && age>=kind.impact && age%6==2)refreshFrost();
        }
        emitImpact();
        emitHealPulse();
        alive=age<kind.end;
    }

    private void emitHealPulse(){
        if(kind!=Kind.HALLOWED || age<30 || (age-30)%60>8)return;
        int pulse=(age-30)/60;
        if(pulse<=lastHealCue)return;
        lastHealCue=pulse;
        Particle.emit(this,Particle.Style.RING,PriestCombat.GOLD,radius()*0.65f,20,0,0,0,0);
        Particle.spray(this,Particle.Style.SQUARE,PriestCombat.GOLD,9,6,18,4,-Math.PI/2,1.4,0,-5);
    }

    private void advanceBolt(){
        float travel=Math.min(BOLT_SPEED,remaining);
        int steps=owner==null?1:Math.max(1,(int)Math.ceil(travel/3));
        float dx=(float)Math.cos(heading)*travel/steps,dy=(float)Math.sin(heading)*travel/steps;
        for(int i=0;i<steps && alive;i++){
            x+=dx;y+=dy;worldX=Math.round(x);worldY=Math.round(y);
            if(owner!=null){
                if(RangerEffect.blocked(gp,worldX,worldY,1,1)){
                    Particle.spray(this,Particle.Style.SQUARE,tint(),6,5,15,3,heading+Math.PI,0.8,0,-10);
                    alive=false;
                }else{
                    hitAlongBolt();
                }
            }
        }
        remaining-=travel;
        if(remaining<=0 || age>=kind.end)alive=false;
        if(alive && age%2==0 && age>lastTrail){
            lastTrail=age;
            Particle.spray(this,Particle.Style.SQUARE,tint(),2,4.8f,11,2.5f,heading+Math.PI,0.55,0,-12);
        }
    }

    private void hitAlongBolt(){
        for(int i=0;i<gp.monster.length;i++){
            Monster m=gp.monster[i];
            if(m==null || !m.alive || m.dying || !m.solidArea.intersects(x-m.worldX-3,y-m.worldY-3,6,6))continue;
            if(owner.mageDamage(i,damage,RangerEffect.direction(heading),false,false)){
                if(kind==Kind.RADIANT)owner.priest.onBoltHit();
                else owner.mage.gainRune();
                Particle.spray(m,Particle.Style.SQUARE,tint(),5,5.5f,17,4.5f,heading,0.55,0,-8);
            }
            alive=false;
            return;
        }
    }

    private boolean inArea(Monster m){
        return m!=null && m.alive && !m.dying
                && Math.hypot(m.worldX+m.solidArea.getCenterX()-x,m.worldY+m.solidArea.getCenterY()-y)<=radius()
                && RangerEffect.clearLine(gp,worldX,worldY,m.worldX+(int)m.solidArea.getCenterX(),m.worldY+(int)m.solidArea.getCenterY());
    }

    private void applyAreaDamage(){
        boolean strong=kind==Kind.COMET || kind==Kind.DETONATION && runes==owner.maxArcaneCharge;
        for(int i=0;i<gp.monster.length;i++){
            Monster m=gp.monster[i];
            if(!inArea(m))continue;
            double angle=Math.atan2(m.worldY+m.solidArea.getCenterY()-y,m.worldX+m.solidArea.getCenterX()-x);
            String direction=RangerEffect.direction(angle);
            if(owner.mageDamage(i,damage,direction,true,strong) && kind==Kind.DETONATION && !m.isBoss() && !m.dying){
                m.startKnockback(direction,gp.tileSize/3);
            }
        }
        if(kind==Kind.COMET)gp.startScreenShake(12,9);
    }

    private boolean healingPulse(){return age==30 || age==90 || age==150;}

    private void healArea(){
        for(Player ally:gp.players){
            if(ally==null || ally.inWell!=gp.quests.inDungeon() || ally.isDead || ally.HP<=0)continue;
            int ax=PriestCombat.centerX(ally),ay=PriestCombat.centerY(ally);
            if(Math.hypot(ax-x,ay-y)<=radius() && RangerEffect.clearLine(gp,worldX,worldY,ax,ay))PriestCombat.heal(ally,runes);
        }
    }

    private void refreshFrost(){
        for(Monster m:gp.monster){
            if(m==null || m.isBoss() || !inArea(m))continue;
            StatusEffect slow=m.statusEffects.stream().filter(effect->effect.type.equals(FROST_SLOW)).findFirst().orElse(null);
            if(slow==null)m.addStatusEffect(new StatusEffect(FROST_SLOW,14,32,0));
            else slow.duration=14;
        }
    }

    private float radius(){return gp.tileSize*kind.radius;}
    private Color tint(){
        return switch(kind){
            case EMBER->EMBER;case FROST->FROST;case COMET->GOLD;case HALLOWED->PriestCombat.SAGE;
            case RADIANT,MEND,CHORUS,WARD_PULSE,WARD_BREAK,CHORUS_SPARK,SANCTUARY_BLOOM->PriestCombat.GOLD;
            default->LILAC;
        };
    }

    private void releaseCue(){
        Particle.spray(this,Particle.Style.SQUARE,tint(),5,5,14,3.8f,heading,0.7,0,-12);
    }

    private void emitImpact(){
        if(bolt() || impactCue || age<kind.impact || age>kind.impact+7)return;
        impactCue=true;
        boolean comet=kind==Kind.COMET;
        if(support() && kind!=Kind.HALLOWED){
            Particle.burst(this,Particle.Style.SQUARE,tint(),kind==Kind.WARD_BREAK?13:7,5.5f,19,4.5f);
            if(kind!=Kind.MEND)Particle.emit(this,Particle.Style.RING,tint(),radius()*0.6f,15,0,0,0,-5);
            return;
        }
        Particle.emit(this,Particle.Style.RING,tint(),radius()*0.55f,comet?24:18,0,0,0,0);
        Particle.emit(this,Particle.Style.SPARK,Palette.PLASTER,comet?26:15,comet?16:11,0,0,0,-7);
        Particle.burst(this,Particle.Style.SQUARE,tint(),comet?19:kind==Kind.DETONATION?7+runes*2:10,
                comet?8:6,comet?26:19,comet?8.4f:5.2f);
        if(comet)Particle.burst(this,Particle.Style.SQUARE,LILAC,10,6.5f,22,7);
    }

    @Override public int sortY(){return bolt() || (support() && kind!=Kind.HALLOWED)?worldY+gp.tileSize:Integer.MIN_VALUE+2;}

    @Override public void draw(Graphics2D graphics){
        Player camera=gp.localPlayer();
        float sx=x-camera.worldX+camera.screenX,sy=y-camera.worldY+camera.screenY;
        if(sx<-180 || sx>gp.screenWidth+180 || sy<-180 || sy>gp.screenHeight+180)return;
        var g=(Graphics2D)graphics.create();
        g.translate(sx,sy);
        if(bolt()){
            g.translate(0,-12);g.rotate(heading);
            drawBolt(g,tint());
        }else if(support()){
            drawSupport(g);
        }else{
            drawGround(g);
            if(kind==Kind.COMET && age>=kind.impact-14 && age<kind.impact){
                float height=(kind.impact-age)*10;
                g.translate(-height*0.42f,-height);
                g.rotate(1.16);
                drawComet(g);
            }
        }
        g.dispose();
    }

    private static void drawBolt(Graphics2D g,Color glow){
        g.setColor(new Color(glow.getRed(),glow.getGreen(),glow.getBlue(),155));g.fillRoundRect(-43,-4,42,8,8,8);
        var head=new Path2D.Float();
        head.moveTo(-14,-5);head.lineTo(2,-7);head.lineTo(13,0);
        head.lineTo(2,7);head.lineTo(-14,5);head.lineTo(-6,0);head.closePath();
        g.setColor(INK);g.setStroke(new BasicStroke(3));g.draw(head);
        g.setColor(glow);g.fill(head);
        g.setColor(Palette.PLASTER);g.fill(diamond(3,0,6,4));
    }

    private void drawSupport(Graphics2D g){
        float r=radius();
        float progress=age/(float)kind.end;
        if(kind==Kind.HALLOWED){
            var ring=new Ellipse2D.Float(-r,-r,r*2,r*2);
            int pulse=age<30?60:(age-30)%60;
            g.setColor(new Color(189,210,158,pulse<9?87:47));g.fill(ring);
            g.setColor(INK);g.setStroke(new BasicStroke(5));g.draw(ring);
            g.setColor(PriestCombat.GOLD);g.setStroke(new BasicStroke(2.8f));g.draw(ring);
            g.setColor(pulse<9?Palette.PLASTER:PriestCombat.SAGE);
            float petal=r*0.56f;
            for(int i=0;i<4;i++){
                var pose=g.getTransform();g.rotate(i*Math.PI/2);
                var leaf=new Path2D.Float();
                leaf.moveTo(0,-3);leaf.quadTo(-petal*0.38f,-petal*0.57f,0,-petal);
                leaf.quadTo(petal*0.38f,-petal*0.57f,0,-3);leaf.closePath();g.fill(leaf);
                g.setTransform(pose);
            }
            g.fill(diamond(0,0,4.5f,4.5f));
            return;
        }
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0,1-progress)));
        g.setColor(PriestCombat.GOLD);
        if(kind==Kind.MEND){
            g.rotate(heading);
            float length=remaining*(1-progress);
            if(length>3){
                var ribbon=new QuadCurve2D.Float(-length,0,-length*0.45f,-Math.min(24,length*0.3f),0,0);
                g.setStroke(new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(ribbon);
                g.setColor(Palette.PLASTER);g.setStroke(new BasicStroke(2));g.draw(ribbon);
            }
            g.fill(diamond(0,0,5,7));
            g.rotate(-heading);
            if(age>=4){
                g.setColor(Palette.PLASTER);
                plus(g,12,-11-progress*16,4);
                plus(g,-10,-7-progress*13,3);
            }
        }else if(kind==Kind.WARD_PULSE || kind==Kind.WARD_BREAK){
            g.setStroke(new BasicStroke(kind==Kind.WARD_BREAK?4:2.5f));
            g.draw(new Arc2D.Float(-r,-r*1.55f,r*2,r*2,10,160,Arc2D.OPEN));
        }else{
            float ring=r*(0.42f+progress*0.58f);
            g.setStroke(new BasicStroke(3));g.draw(new Ellipse2D.Float(-ring,-ring,ring*2,ring*2));
            if(kind==Kind.SANCTUARY_BLOOM){
                g.setColor(Palette.PLASTER);
                for(int i=0;i<6;i++){
                    double angle=i*Math.PI/3;
                    plus(g,(float)Math.cos(angle)*ring,(float)Math.sin(angle)*ring-12*progress,4);
                }
            }else if(kind==Kind.CHORUS){
                for(int i=0;i<8;i++){
                    var pose=g.getTransform();
                    g.rotate(i*Math.PI/4);
                    g.fill(diamond(ring+7,0,8,3));
                    g.setTransform(pose);
                }
            }
        }
    }

    private static void plus(Graphics2D g,float x,float y,float r){
        g.fill(new Rectangle2D.Float(x-r,y-r/3,r*2,r*2/3));
        g.fill(new Rectangle2D.Float(x-r/3,y-r,r*2/3,r*2));
    }

    private static void drawComet(Graphics2D g){
        var tail=new Path2D.Float();
        tail.moveTo(-106,-20);tail.curveTo(-64,-12,-39,-27,2,-17);
        tail.curveTo(26,-12,27,10,11,19);
        tail.curveTo(-20,32,-65,11,-106,-20);tail.closePath();
        g.setColor(new Color(103,68,142,215));g.fill(tail);
        var glow=new Path2D.Float();
        glow.moveTo(-77,-14);glow.curveTo(-37,-6,-25,-18,6,-13);
        glow.curveTo(25,-9,22,12,8,15);
        glow.curveTo(-16,21,-50,4,-77,-14);glow.closePath();
        g.setColor(LILAC);g.fill(glow);
        g.setColor(Palette.PLASTER);g.fill(new Ellipse2D.Float(-7,-10,23,20));
        g.setColor(GOLD);g.fill(diamond(9,-2,4,5));
    }

    private void drawGround(Graphics2D g){
        float r=radius();
        var ring=new Ellipse2D.Float(-r,-r,r*2,r*2);
        int phase=age-kind.impact;
        float fade=Math.min(1,(kind.end-age)/12f);
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0,fade)));
        Color color=kind==Kind.COMET && phase<0?LILAC:tint();
        g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),phase>=0 && phase<5?97:47));
        g.fill(ring);
        g.setStroke(DASH_BACK);g.setColor(INK);g.draw(ring);
        g.setStroke(DASH);g.setColor(color);g.draw(ring);
        if(kind==Kind.FROST){
            g.setStroke(new BasicStroke(2.5f));
            for(int i=0;i<6;i++){
                double a=i*Math.PI/3;
                float dx=(float)Math.cos(a),dy=(float)Math.sin(a);
                float px=dx*r*0.47f,py=dy*r*0.47f;
                g.draw(new Line2D.Float(0,0,dx*r*0.8f,dy*r*0.8f));
                g.draw(new Line2D.Float(px,py,px+(-dy+dx)*r*0.19f,py+(dx+dy)*r*0.19f));
                if(phase>=0)g.fill(diamond(dx*r*0.6f,dy*r*0.6f,4,8));
            }
        }else{
            float mark=kind==Kind.DETONATION?7+runes*2:10;
            g.draw(diamond(0,0,mark,mark));
            if(phase>=0 && phase<=10)drawBurst(g,color,phase);
        }
    }

    private void drawBurst(Graphics2D g,Color color,int phase){
        float strength=1-phase/11f;
        g.setComposite(AlphaComposite.SrcOver.derive(strength));
        float r=radius();
        var burst=new Path2D.Float();
        burst.moveTo(-r*0.68f,4);burst.lineTo(-r*0.45f,-12);
        burst.lineTo(-r*0.48f,-r*0.62f);burst.lineTo(-r*0.17f,-r*0.29f);
        burst.lineTo(0,-r*(kind==Kind.COMET?1.1f:0.73f));
        burst.lineTo(r*0.17f,-r*0.27f);burst.lineTo(r*0.47f,-r*0.56f);
        burst.lineTo(r*0.40f,-12);burst.lineTo(r*0.69f,4);
        burst.quadTo(0,20,-r*0.68f,4);burst.closePath();
        g.setColor(color);g.fill(burst);
        g.translate(0,1);g.scale(0.62,0.74);
        g.setColor(Palette.PLASTER);g.fill(burst);
    }

    static Shape diamond(float x,float y,float rx,float ry){
        var shape=new Path2D.Float();
        shape.moveTo(x,y-ry);shape.lineTo(x+rx,y);shape.lineTo(x,y+ry);shape.lineTo(x-rx,y);shape.closePath();
        return shape;
    }
}
