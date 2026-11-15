package entity;

import art.Palette;
import object.OBJ_rangerbow;
import net.InputState;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Bow input, Focus spends, and short-lived targeting state for one Ranger. */
public final class RangerCombat {
    private static final int FULL_DRAW = 33, CLEAN_END = 41;
    private static final int HOP_TICKS=15, HOP_RELEASE=6;
    private static final int QUICK_RANGE=5, FULL_RANGE=7, DEADEYE_RANGE=8;
    private static final Color INK=new Color(45,51,44,205);
    private static final Color SAGE = new Color(0xB7DBA3);
    private static final Color GOLD = new Color(0xF0C574);
    private final Player player;
    private int draw = -1, recovery, deadeye, markTicks;
    private int hopAge,hopStartX,hopStartY,hopDistance;
    private boolean hopBlocked;
    private Monster marked;
    private double heading;
    private int markIndex = -1;
    private boolean releaseLocked;
    private static final class Icons {
        static final java.util.Map<String,java.awt.image.BufferedImage> IMAGES = new java.util.HashMap<>();
        static synchronized java.awt.image.BufferedImage load(String id){
            return IMAGES.computeIfAbsent(id,key->{
                String path="/skill_icon/ranger/"+key+".png";
                try(var in=RangerCombat.class.getResourceAsStream(path)){
                    if(in==null)throw new IOException("Missing "+path);
                    return ImageIO.read(in);
                }catch(IOException e){throw new UncheckedIOException(e);}
            });
        }
    }

    RangerCombat(Player player){this.player=player;}

    void setSkills(Skill[] slots){
        slots[0]=skill("Pinning Shot","pinning_shot",12,300,"Slow a regular enemy by 25% for 2s.",
                ()->{
                    heading=aim();draw=-1;recovery=12;
                    launch(RangerEffect.Kind.PIN,heading,6,0.75f,0);
                });
        slots[1]=skill("Rangefinder","rangefinder",10,480,"Mark an enemy for 5s. Your next clean shot deals extra damage.",()->{
            markIndex=player.currentInput.targetedMonsterIndex;
            marked=player.gp.monster[markIndex];markTicks=300;
            markBurst(marked);
        });
        slots[2]=skill("Backstep Shot","backstep_shot",0,480,"Hop backward and fire a light arrow.",this::backstep);
        slots[3]=skill("Arrow Rain","arrow_rain",25,540,"After a short warning, three volleys hit the aimed area.",()->{
            InputState in=player.currentInput;
            player.gp.projectileList.add(RangerEffect.rain(player,in.aimWorldX,in.aimWorldY,damage(0.30f)));
        });
        slots[4]=skill("Deadeye","deadeye",45,720,"Plant your feet, then fire through up to three enemies.",()->{
            draw=-1;deadeye=FULL_DRAW;heading=aim();releaseLocked=true;
            chargeCue(GOLD,12);
        });
    }

    private Skill skill(String name,String icon,int cost,int cooldown,String description,Runnable cast){
        Skill result=new Skill(name,cost,cooldown,()->{cast.run();player.announceSkill(name);},Icons.load(icon));
        result.description=description;
        return result;
    }

    boolean canUse(int slot){
        if(!player.hasBow() || hopAge>0 || deadeye>0 || player.knockbackActive || player.currentInput.ePressed || player.currentInput.gameplaySuppressed)return false;
        if(slot==1){
            int i=player.currentInput.targetedMonsterIndex;
            if(i<0 || i>=player.gp.monster.length)return false;
            Monster m=player.gp.monster[i];
            if(m==null || !m.alive || m.dying)return false;
            return canTarget(m.worldX+m.solidArea.x+m.solidArea.width/2,m.worldY+m.solidArea.y+m.solidArea.height/2);
        }
        return slot!=3 || canTarget(player.currentInput.aimWorldX,player.currentInput.aimWorldY);
    }

    private boolean canTarget(int x,int y){
        return Math.hypot(x-originX(),y-originY())<=FULL_RANGE*player.gp.tileSize
                && RangerEffect.clearLine(player.gp,originX(),originY(),x,y);
    }

    void update(){
        if(player.canResolveWorldActions() && markTicks>0 && (--markTicks==0 || marked==null || !marked.alive || marked.dying
                || player.gp.monster[markIndex]!=marked))clearMark();
        if(recovery>0)recovery--;
        InputState input=player.currentInput;
        if(!player.hasBow() || player.knockbackActive || input.gameplaySuppressed || input.ePressed){
            cancelCast();return;
        }
        if(hopAge>0){
            if(player.canResolveWorldActions())advanceBackstep();
            else advanceVisual();
            return;
        }
        if(deadeye>0){
            if(--deadeye==0){
                launch(RangerEffect.Kind.DEADEYE,heading,DEADEYE_RANGE,2,0);
                recovery=15;
            }else if(deadeye%8==0)chargeCue(GOLD,3);
            return;
        }
        if(!input.leftHeld)releaseLocked=false;
        if(draw<0 && recovery==0 && input.leftClicked && !releaseLocked)draw=0;
        if(draw<0)return;
        heading=aim();
        if(input.leftHeld){
            draw=Math.min(CLEAN_END+1,draw+1);
            if(draw==FULL_DRAW)chargeCue(GOLD,8);
        }else{
            boolean full=draw>=FULL_DRAW, clean=full && draw<=CLEAN_END;
            launch(clean?RangerEffect.Kind.CLEAN:full?RangerEffect.Kind.FULL:RangerEffect.Kind.QUICK,
                    heading,full?FULL_RANGE:QUICK_RANGE,clean?1.30f:full?1.05f:0.60f,clean?16:full?12:8);
            recovery=full?12:24;
            draw=-1;
        }
    }

    boolean braced(){return hopAge>0 || deadeye>0 || draw>=FULL_DRAW;}

    int hopLift(){
        return hopAge==0?0:Math.round(16*(float)Math.sin(Math.PI*(hopAge-1)/HOP_TICKS));
    }

    public int hopAge(){return hopAge;}

    void advanceVisual(){
        if(hopAge>0 && ++hopAge>HOP_TICKS){
            hopAge=0;
            hopDust(9,3);
        }
    }

    void pose(){
        if(!player.hasBow() || draw<0 && deadeye==0 && recovery==0 && hopAge==0)return;
        player.animState="attack";
        player.direction=RangerEffect.direction(heading);
        player.spriteNum=hopAge>0?(hopAge<=HOP_RELEASE?3:5):deadeye>0?Math.min(4,1+(FULL_DRAW-deadeye)/9)
                :draw>=0?Math.min(4,1+draw/9):Math.max(5,8-recovery/5);
    }

    private double aim(){
        InputState in=player.currentInput;
        int dx=in.aimWorldX-originX(),dy=in.aimWorldY-originY();
        if(dx!=0 || dy!=0)return Math.atan2(dy,dx);
        return switch(player.direction){case "up"->-Math.PI/2;case "down"->Math.PI/2;case "left"->Math.PI;default->0;};
    }

    int originX(){return player.worldX+player.solidArea.x+player.solidArea.width/2;}
    int originY(){return player.worldY+player.solidArea.y+6;}
    private int damage(float multiplier){return Math.max(1,Math.round(player.getEffectiveAttack()*multiplier));}

    private void launch(RangerEffect.Kind kind,double angle,int range,float multiplier,int focus){
        if(!player.canResolveWorldActions() || !player.hasBow())return;
        player.gp.projectileList.add(RangerEffect.arrow(player,kind,angle,range,damage(multiplier),focus));
    }

    private void backstep(){
        heading=aim();
        draw=-1;releaseLocked=true;
        hopStartX=player.worldX;hopStartY=player.worldY;
        hopAge=1;hopDistance=0;hopBlocked=false;
        hopDust(11,4);
    }

    private void advanceBackstep(){
        int tick=hopAge++;
        double t=tick/(double)HOP_TICKS;
        int targetDistance=(int)Math.round(player.gp.tileSize*t*(1.45-0.45*t));
        double dx=-Math.cos(heading),dy=-Math.sin(heading);
        var area=player.solidArea;
        while(!hopBlocked && hopDistance<targetDistance){
            int distance=hopDistance+1;
            int x=hopStartX+(int)Math.round(dx*distance),y=hopStartY+(int)Math.round(dy*distance);
            if(RangerEffect.blocksDash(player,x+area.x,y+area.y,area.width,area.height)){
                hopBlocked=true;
                break;
            }
            player.worldX=x;player.worldY=y;
            hopDistance=distance;
        }
        if(tick==HOP_RELEASE)launch(RangerEffect.Kind.BACKSTEP,heading,QUICK_RANGE,0.45f,0);
        if(tick==HOP_TICKS){
            hopAge=0;recovery=6;
            hopDust(9,3);
        }
    }

    private void hopDust(float size,int count){
        Particle.spray(player,Particle.Style.DUST,Color.WHITE,count,size,18,2.1f,heading,1,0,-3);
    }

    static float bowReach(double heading){return 26+22*Math.max(0,-(float)Math.sin(heading));}

    private void chargeCue(Color color,int count){
        float reach=bowReach(heading),dx=(float)Math.cos(heading)*reach,dy=(float)Math.sin(heading)*reach;
        Particle.spray(player,Particle.Style.SQUARE,color,count,5.5f,16,2.1f,heading,1.2,dx,dy-10-hopLift());
        if(count>=8)Particle.emit(player,Particle.Style.SPARK,Palette.PLASTER,11,12,0,0,dx,dy-10-hopLift());
    }

    private static void markBurst(Monster target){
        Particle.emit(target,Particle.Style.RING,GOLD,23,22,0,0,0,-5);
        Particle.spray(target,Particle.Style.SQUARE,GOLD,9,5.5f,20,3.6f,-Math.PI/2,1.05,0,-12);
    }

    int markBonus(Monster target){
        return markTicks>0 && marked==target?Math.max(1,Math.round(player.getEffectiveAttack()*0.45f)):0;
    }

    void consumeMark(Monster target){
        if(markTicks>0 && marked==target){
            Particle.burst(target,Particle.Style.SQUARE,GOLD,7,4.6f,16,3.5f);
            clearMark();
        }
    }

    private void clearMark(){marked=null;markTicks=0;markIndex=-1;}
    void cancelCast(){
        hopAge=0;
        cancelAim();
    }

    void cancelAim(){
        draw=-1;deadeye=0;recovery=0;releaseLocked=true;
        if(hopAge==0 && player.isRanger() && "attack".equals(player.animState)){
            player.animState="idle";
            player.spriteNum=1;
        }
    }

    void reset(){cancelCast();releaseLocked=false;clearMark();}

    public int markIndex(){return markIndex;}
    public int markTicks(){return markTicks;}
    public int drawTicks(){return draw;}
    public int deadeyeTicks(){return deadeye;}
    public double heading(){return heading;}
    public void applyVisual(int markIndex,int markTicks,int draw,int deadeye,int hopAge,int recovery,double heading,boolean remote){
        if(this.hopAge==0 && hopAge>0){
            this.heading=heading;
            hopDust(11,4);
        }else if(this.hopAge>0 && hopAge==0 && recovery>0){
            hopDust(9,3);
        }
        if(markIndex>=0 && markIndex<player.gp.monster.length && markTicks>0
                && (this.markIndex!=markIndex || markTicks>this.markTicks)){
            Monster target=player.gp.monster[markIndex];
            if(target!=null && target.alive && !target.dying)markBurst(target);
        }
        if(this.deadeye==0 && deadeye>0){
            this.heading=heading;
            chargeCue(GOLD,12);
        }
        this.markIndex=markIndex;
        this.markTicks=markTicks;
        this.deadeye=deadeye;
        this.hopAge=hopAge;
        if(remote || deadeye>0 || hopAge>0 || this.draw<0 && recovery>0){
            this.draw=draw;
            this.heading=heading;
            this.recovery=recovery;
        }
    }

    public int recoveryTicks(){return recovery;}

    void drawBow(Graphics2D g){
        if(!"attack".equals(player.animState) || !(player.currentWeapon instanceof OBJ_rangerbow bow) || player.isDead)return;
        Player camera=player.gp.localPlayer();
        float x=originX()-camera.worldX+camera.screenX, y=originY()-camera.worldY+camera.screenY;
        float pull=deadeye>0?1-deadeye/(float)FULL_DRAW:Math.max(0,Math.min(1,draw/(float)FULL_DRAW));
        Graphics2D aim=(Graphics2D)g.create();
        switch(player.direction){
            case "down" -> {
                aim.translate(x,y+15-hopLift());
                aim.rotate(Math.PI/2);
            }
            case "up" -> {
                aim.translate(x,y-22-hopLift());
                aim.rotate(-Math.PI/2);
            }
            default -> {
                float reach=bowReach(heading);
                aim.translate(x+Math.cos(heading)*reach,y+Math.sin(heading)*reach-hopLift());
                aim.rotate(heading);
            }
        }
        bow.gear.drawBow(aim,0,0,1.02f,pull);
        aim.dispose();
    }

    void draw(Graphics2D g){
        if(player.isDead)return;
        Player camera=player.gp.localPlayer();
        float left=camera.worldX-camera.screenX,top=camera.worldY-camera.screenY;
        Color old=g.getColor();
        Stroke stroke=g.getStroke();
        if(deadeye>0){
            float x=originX()-left,y=originY()-top;
            float length=RangerEffect.clearDistance(player.gp,originX(),originY(),heading,DEADEYE_RANGE*player.gp.tileSize);
            float dx=(float)Math.cos(heading),dy=(float)Math.sin(heading);
            if(length>28){
                var line=new Line2D.Float(x+dx*28,y+dy*28,x+dx*length,y+dy*length);
                g.setColor(INK);g.setStroke(new BasicStroke(7,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(line);
                g.setColor(GOLD);g.setStroke(new BasicStroke(deadeye<12?4:2.5f));g.draw(line);
                g.setColor(Palette.PLASTER);g.setStroke(new BasicStroke(1.2f));g.draw(line);
            }
        }else if(draw>=FULL_DRAW && draw<=CLEAN_END){
            float reach=bowReach(heading)+14;
            float x=originX()-left+(float)Math.cos(heading)*reach,y=originY()-top+(float)Math.sin(heading)*reach;
            g.setColor(GOLD);g.fill(new Ellipse2D.Float(x-3.5f,y-3.5f,7,7));
            g.setColor(Palette.PLASTER);
            g.fill(new Rectangle2D.Float(x-1.5f,y-1.5f,3,3));
            g.draw(new Line2D.Float(x-6,y,x-4,y));
            g.draw(new Line2D.Float(x+4,y,x+6,y));
        }
        if(markTicks>0 && markIndex>=0 && markIndex<player.gp.monster.length){
            Monster m=player.gp.monster[markIndex];
            if(m!=null && m.alive && !m.dying){
                float x=m.worldX+m.solidArea.x+m.solidArea.width/2f-left,y=m.worldY-20-top;
                y-=7+(float)Math.sin(markTicks*0.15)*2;
                Path2D diamond=new Path2D.Float();
                diamond.moveTo(x,y-11);diamond.lineTo(x+10,y);diamond.lineTo(x,y+11);diamond.lineTo(x-10,y);
                diamond.closePath();
                g.setColor(INK);g.fill(diamond);g.setStroke(new BasicStroke(5));g.draw(diamond);
                g.setColor(GOLD);g.setStroke(new BasicStroke(2.6f));g.draw(diamond);
                g.setColor(Palette.PLASTER);g.fill(new Rectangle2D.Float(x-2,y-2,4,4));
            }
        }
        g.setColor(old);g.setStroke(stroke);
    }

    static void pin(Monster target){
        target.statusEffects.removeIf(effect->effect.type.equals("rangerSlow"));
        target.addStatusEffect(new StatusEffect("rangerSlow",120,25,0));
        pinParticles(target);
    }

    static void pinParticles(Monster target){
        Particle.emit(target,Particle.Style.RING,SAGE,23,20,0,0,0,9);
        Particle.burst(target,Particle.Style.SQUARE,SAGE,11,6,22,4.5f);
    }
}
