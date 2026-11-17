package entity;

import art.ArtKit;
import art.Palette;
import art.RangerGear;
import main.Gamepanel;
import net.RangerEffectState;

import java.awt.*;
import java.awt.geom.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Swept Hunter projectiles and delayed volleys. The host resolves hits. */
public final class RangerEffect extends Entity {
    public enum Kind { QUICK, FULL, CLEAN, PIN, BACKSTEP, RAIN, DEADEYE, KNIFE }
    private static final Color SAGE=new Color(0xC7DCA5);
    private static final Color GOLD=new Color(0xEBC076);
    private static final Color OUTLINE=new Color(38,44,38,210);
    private static final Stroke WARNING=new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,new float[]{11,7},0);
    private static final Stroke WARNING_OUTLINE=new BasicStroke(6,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,new float[]{11,7},0);
    private static final int RAIN_ARROWS=6;
    private static final AtomicInteger NEXT_ID=new AtomicInteger();
    private static final int FIRST_WAVE=24,WAVE_GAP=13,RAIN_END=62;
    private final CombatHit arrowHit=new CombatHit("arrow");
    private final Kind kind;
    private final Player owner;
    private final Set<Monster> hit=new HashSet<>();
    private Set<Monster> knifeVolley;
    private final int damage,focus;
    private final double heading;
    private float x,y,remaining,launchLift;
    private int age,contacts,id,lastTrailAge=-1,lastRainWave=-1;

    private RangerEffect(Gamepanel gp,Player owner,Kind kind,float x,float y,double heading,float distance,int damage,int focus){
        super(gp);this.owner=owner;this.kind=kind;arrowHit.mastery(kind==Kind.KNIFE?type_sword:type_bow);this.x=x;this.y=y;this.heading=heading;
        this.remaining=distance;this.damage=damage;this.focus=focus;
        if(owner!=null)id=NEXT_ID.incrementAndGet();
        worldX=Math.round(x);worldY=Math.round(y);solidArea.setBounds(0,0,0,0);
    }

    static RangerEffect arrow(Player owner,Kind kind,double heading,int range,int damage,int focus){
        var effect=new RangerEffect(owner.gp,owner,kind,owner.ranger.originX(),owner.ranger.originY(),
                heading,range*owner.gp.tileSize,damage,focus);
        effect.launchLift=owner.ranger.hopLift();
        effect.trail(true);
        return effect;
    }

    static RangerEffect knife(Player owner,int x,int y,double heading,int damage,Set<Monster> volley){
        var effect=new RangerEffect(owner.gp,owner,Kind.KNIFE,x,y,heading,owner.gp.tileSize*3.5f,damage,0);
        effect.knifeVolley=volley;
        effect.trail(true);
        return effect;
    }

    static RangerEffect rain(Player owner,int x,int y,int damage){
        return new RangerEffect(owner.gp,owner,Kind.RAIN,x,y,0,0,damage,0);
    }

    public RangerEffectState snapshot(){
        var s=new RangerEffectState();s.kind=kind.ordinal();s.age=age;s.x=x;s.y=y;s.heading=heading;s.launchLift=launchLift;s.id=id;
        return s;
    }

    public static RangerEffect replica(Gamepanel gp,RangerEffectState state){
        var effect=new RangerEffect(gp,null,Kind.values()[state.kind],state.x,state.y,state.heading,0,0,0);
        effect.id=state.id;
        effect.syncReplica(state);
        if(effect.kind!=Kind.RAIN && state.age<=3)effect.trail(true);
        return effect;
    }

    public boolean isReplica(){return owner==null;}
    public int id(){return id;}

    public void syncReplica(RangerEffectState state){
        x=state.x;y=state.y;worldX=Math.round(x);worldY=Math.round(y);
        age=state.age;launchLift=state.launchLift;alive=true;
        if(kind==Kind.RAIN)updateRainParticles();
    }

    @Override public void update(){
        age++;
        if(kind==Kind.RAIN){
            if(owner!=null && age>=FIRST_WAVE && age<=FIRST_WAVE+2*WAVE_GAP && (age-FIRST_WAVE)%WAVE_GAP==0)rainHit();
            updateRainParticles();
            alive=age<RAIN_END;
            return;
        }

        float speed=kind==Kind.DEADEYE?30:kind==Kind.CLEAN || kind==Kind.KNIFE?21:17;
        if(owner==null){
            move(speed);alive=age<45;
            if(alive)trail(false);
            return;
        }
        float travel=Math.min(speed,remaining);
        int steps=Math.max(1,(int)Math.ceil(travel/3));
        for(int i=0;i<steps && alive;i++){
            move(travel/steps);
            if(blocked(gp,worldX,worldY,1,1)){
                Particle.burst(this,Particle.Style.SQUARE,feather(),5,5,14,3.6f);
                alive=false;break;
            }
            arrowHit();
        }
        remaining-=travel;
        if(remaining<=0)alive=false;
        if(alive)trail(false);
    }

    private Color feather(){
        return switch(kind){case PIN,RAIN->SAGE;case CLEAN,DEADEYE->GOLD;default->Palette.PLASTER;};
    }

    private float visualLift(){return launchLift*Math.max(0,1-age/7f);}

    private void trail(boolean release){
        boolean strong=kind==Kind.DEADEYE || kind==Kind.CLEAN;
        if(!release && !strong && kind!=Kind.PIN)return;
        if(!release && (age%2!=0 || age<=lastTrailAge))return;
        lastTrailAge=age;
        Particle.spray(this,Particle.Style.SQUARE,feather(),release?(strong?8:4):strong?2:1,
                strong?6:4.6f,strong?16:11,release?4.1f:2.1f,heading+(release?0:Math.PI),0.55,0,-visualLift());
        if(release && strong)Particle.emit(this,Particle.Style.SPARK,Palette.PLASTER,11,12,0,0,0,-visualLift());
    }

    private void move(float distance){
        x+=(float)Math.cos(heading)*distance;y+=(float)Math.sin(heading)*distance;
        worldX=Math.round(x);worldY=Math.round(y);
    }

    private void arrowHit(){
        for(int i=0;i<gp.monster.length;i++){
            Monster m=gp.monster[i];
            if(m==null || !m.alive || m.dying || hit.contains(m)
                    || !m.solidArea.intersects(x-m.worldX-2,y-m.worldY-2,4,4))continue;
            hit.add(m);contacts++;
            if(kind==Kind.KNIFE){
                if(knifeVolley.add(m))owner.assassinHit(i,damage,direction(heading));
                alive=false;
                return;
            }
            int bonus=kind==Kind.CLEAN?owner.ranger.markBonus(m):0;
            int base=kind==Kind.DEADEYE && contacts>1?Math.max(1,Math.round(damage*0.35f)):damage;
            boolean precise=kind==Kind.CLEAN || kind==Kind.DEADEYE && contacts==1;
            if(owner.rangerArrowDamage(i,base+bonus,direction(heading),precise,arrowHit)){
                if(focus>0)owner.gainRangerFocus(focus);
                if(bonus>0)owner.ranger.consumeMark(m);
                if(kind==Kind.PIN && !m.isBoss())RangerCombat.pin(m);
            }
            if(kind!=Kind.DEADEYE || contacts>=3){alive=false;return;}
        }
    }

    private void rainHit(){
        CombatHit wave=new CombatHit("arrow-rain-wave-"+((age-FIRST_WAVE)/WAVE_GAP)).mastery(type_bow);
        float radius=gp.tileSize*1.2f;
        for(int i=0;i<gp.monster.length;i++){
            Monster m=gp.monster[i];
            if(m==null || !m.alive || m.dying)continue;
            int mx=m.worldX+(int)m.solidArea.getCenterX(),my=m.worldY+(int)m.solidArea.getCenterY();
            if(Math.hypot(mx-x,my-y)>radius || !clearLine(gp,worldX,worldY,mx,my))continue;
            double angle=Math.atan2(my-y,mx-x);
            if(owner.rangerWaveDamage(i,damage,direction(angle),hit.contains(m),wave))hit.add(m);
        }
    }

    private double rainAngle(int wave,int i){return ArtKit.hash01(809+wave,worldX+i,worldY)*Math.PI*2;}
    private float rainDistance(int wave,int i){return gp.tileSize*1.2f*(0.19f+0.64f*(float)ArtKit.hash01(910+wave,worldX,worldY+i));}

    private void updateRainParticles(){
        int phase=age-FIRST_WAVE;
        if(phase<0 || phase>2*WAVE_GAP+5 || phase%WAVE_GAP>5)return;
        int wave=phase/WAVE_GAP;
        if(wave<=lastRainWave)return;
        lastRainWave=wave;
        rainParticles(wave);
    }

    private void rainParticles(int wave){
        Particle.emit(this,Particle.Style.RING,SAGE,37,18,0,0,0,0);
        for(int i=0;i<RAIN_ARROWS;i++){
            double angle=rainAngle(wave,i);
            float distance=rainDistance(wave,i), dx=(float)Math.cos(angle)*distance,dy=(float)Math.sin(angle)*distance;
            Particle.emit(this,Particle.Style.SPARK,Palette.PLASTER,9,11,0,0,dx,dy);
            Particle.spray(this,Particle.Style.SQUARE,i%2==0?GOLD:SAGE,3,6,16,4.6f,angle,1.4,dx,dy);
        }
    }

    static String direction(double angle){
        double dx=Math.cos(angle),dy=Math.sin(angle);
        return Math.abs(dx)>=Math.abs(dy)?(dx>=0?"right":"left"):(dy>=0?"down":"up");
    }

    static boolean blocked(Gamepanel gp,int x,int y,int width,int height){
        if(gp.tileM.blocksMovement(x,y,x+width-1,y+height-1))return true;
        for(var object:gp.worldObjects){
            if(object.blocksArea(x,y,width,height))return true;
        }
        for(var object:gp.interactable){
            if(object!=null && object.collision && object.solidArea.intersects(x-object.worldX,y-object.worldY,width,height))return true;
        }
        return false;
    }

    static boolean blocksDash(Player owner,int x,int y,int width,int height){
        Gamepanel gp=owner.gp;
        if(blocked(gp,x,y,width,height))return true;
        for(var monster:gp.monster){
            if(monster!=null && monster.alive && !monster.dying && overlaps(monster,x,y,width,height))return true;
        }
        for(var npc:gp.npc){
            if(npc!=null && overlaps(npc,x,y,width,height))return true;
        }
        for(var player:gp.players){
            if(player!=null && player!=owner && player.inWell==owner.inWell && !player.isDead && overlaps(player,x,y,width,height))return true;
        }
        return false;
    }

    private static boolean overlaps(Entity entity,int x,int y,int width,int height){
        return entity.solidArea.intersects(x-entity.worldX,y-entity.worldY,width,height);
    }

    static boolean clearLine(Gamepanel gp,int x,int y,int tx,int ty){
        double distance=Math.hypot(tx-x,ty-y);
        return clearDistance(gp,x,y,Math.atan2(ty-y,tx-x),(float)distance)>=distance-0.001;
    }

    static float clearDistance(Gamepanel gp,int x,int y,double heading,float distance){
        int steps=Math.max(1,(int)Math.ceil(distance/4));
        double dx=Math.cos(heading),dy=Math.sin(heading);
        for(int i=1;i<=steps;i++){
            float step=distance*i/steps;
            if(blocked(gp,x+(int)Math.round(dx*step),y+(int)Math.round(dy*step),1,1))return distance*(i-1)/steps;
        }
        return distance;
    }

    @Override public int sortY(){return kind==Kind.RAIN?Integer.MIN_VALUE+1:worldY+gp.tileSize;}

    @Override public void draw(Graphics2D g){
        Player camera=gp.localPlayer();
        float sx=x-camera.worldX+camera.screenX,sy=y-camera.worldY+camera.screenY;
        if(sx<-150 || sy<-150 || sx>gp.screenWidth+150 || sy>gp.screenHeight+150)return;
        Graphics2D fx=(Graphics2D)g.create();
        fx.translate(sx,sy-visualLift());
        if(kind==Kind.RAIN){
            drawRain(fx);
        }else{
            fx.rotate(heading);
            if(kind==Kind.KNIFE){
                fx.setColor(new Color(207,151,173,125));fx.fillRoundRect(-38,-2,38,4,4,4);
                var blade=new Path2D.Float();
                blade.moveTo(-21,-4);blade.lineTo(-2,-3);blade.lineTo(5,0);blade.lineTo(-2,3);blade.lineTo(-21,4);
                blade.closePath();
                fx.setColor(new Color(0x523A44));fx.setStroke(new BasicStroke(3));fx.draw(blade);
                fx.setColor(Palette.PLASTER);fx.fill(blade);
                fx.setColor(GOLD);fx.fillRect(-24,-6,4,12);
                fx.dispose();
                return;
            }
            float muzzleLead=Math.max(0,1-age/4f)*(RangerCombat.bowReach(heading)-15);
            fx.translate(muzzleLead,0);
            boolean deadeye=kind==Kind.DEADEYE;
            float size=deadeye?2.25f:kind==Kind.PIN || kind==Kind.CLEAN?1.85f:1.55f;
            float length=deadeye?49:32;
            if(deadeye || kind==Kind.CLEAN){
                fx.setColor(new Color(244,192,93,deadeye?195:150));
                fx.fill(new RoundRectangle2D.Float(deadeye?-103:-65,-3,deadeye?105:69,6,6,6));
                fx.setColor(new Color(255,249,221,235));
                fx.fill(new RoundRectangle2D.Float(deadeye?-75:-48,-1.3f,deadeye?81:52,2.6f,3,3));
            }
            RangerGear.drawArrow(fx,-length-5*size,0,length,feather(),size);
        }
        fx.dispose();
    }

    private void drawRain(Graphics2D g){
        float r=gp.tileSize*1.2f;
        Shape circle=new Ellipse2D.Float(-r,-r,r*2,r*2);
        int phase=age-FIRST_WAVE;
        boolean impact=phase>=0 && phase<=2*WAVE_GAP+4 && phase%WAVE_GAP<=4;
        g.setColor(impact?new Color(255,222,137,95):new Color(44,65,46,87));
        g.fill(circle);
        g.setStroke(WARNING_OUTLINE);g.setColor(OUTLINE);g.draw(circle);
        g.setStroke(WARNING);g.setColor(impact?GOLD:SAGE);g.draw(circle);

        for(int wave=0;wave<3;wave++){
            int elapsed=age-(FIRST_WAVE+wave*WAVE_GAP);
            if(elapsed < -11 || elapsed>4)continue;
            for(int i=0;i<RAIN_ARROWS;i++){
                double angle=rainAngle(wave,i);
                float distance=rainDistance(wave,i);
                float ax=(float)Math.cos(angle)*distance,ay=(float)Math.sin(angle)*distance;
                if(elapsed<0){
                    Graphics2D arrow=(Graphics2D)g.create();
                    arrow.translate(ax,ay+elapsed*9);arrow.rotate(Math.PI/2+0.15);
                    RangerGear.drawArrow(arrow,-32,0,24,i%2==0?GOLD:SAGE,1.6f);
                    arrow.dispose();
                }else{
                    float radius=5+elapsed*3;
                    g.setColor(new Color(255,236,180,220-elapsed*35));
                    g.setStroke(new BasicStroke(2.5f));
                    g.draw(new Ellipse2D.Float(ax-radius,ay-radius*0.5f,radius*2,radius));
                }
            }
        }
    }
}
