package entity;

import art.Palette;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/** Spell targeting, cast poses and hit-earned runes for the Mage. */
public final class MageCombat {
    public static final String INFUSION="runeInfusion";
    private static final Map<String,BufferedImage> ICONS=new HashMap<>();
    private final Player player;
    private int castTicks,castLength;
    private double heading;

    MageCombat(Player player){this.player=player;}

    void setup(Skill[] slots){
        slots[0]=skill("Ember Sigil","ember_sigil",2,5,"Place a sigil that erupts after a short warning.",
                ()->place(MageEffect.Kind.EMBER,1.25f,12));
        slots[1]=skill("Frost Lattice","frost_lattice",3,8,"A frost patch damages on cast and slows regular enemies.",
                ()->place(MageEffect.Kind.FROST,0.65f,12));
        slots[2]=skill("Mana Infusion","mana_infusion",2,10,"For 6s, the next two landed bolts build two runes each.",()->{
            poseFor(10,facing());
            player.statusEffects.removeIf(effect->effect.type.equals(INFUSION));
            player.addStatusEffect(new StatusEffect(INFUSION,360,2,0));
            infusionCue();
        });
        slots[3]=skill("Arcane Detonation","arcane_detonation",2,7,"Spend at least one rune to burst around yourself.",()->{
            int runes=player.arcaneCharge;
            if(runes==0)return;
            player.arcaneCharge=0;
            poseFor(14,facing());
            player.gp.projectileList.add(MageEffect.area(player,MageEffect.Kind.DETONATION,x(),y(),power(0.65f+0.47f*runes),runes));
        });
        slots[4]=skill("Cometfall","cometfall",5,15,"Spend four runes to call down a comet at the cursor.",()->{
            if(player.arcaneCharge<player.maxArcaneCharge)return;
            player.arcaneCharge=0;
            place(MageEffect.Kind.COMET,2.35f,28);
        });
    }

    private Skill skill(String name,String icon,int cost,int seconds,String description,Runnable action){
        var skill=new Skill(name,cost,seconds*60,()->{
            action.run();player.announceSkill(name);
        },load(icon));
        skill.description=description;
        return skill;
    }

    private static synchronized BufferedImage load(String key){
        return ICONS.computeIfAbsent(key,id->{
            try(var in=MageCombat.class.getResourceAsStream("/skill_icon/mage/"+id+".png")){
                if(in==null)throw new IOException("Missing Mage icon "+id);
                return ImageIO.read(in);
            }catch(IOException e){throw new UncheckedIOException(e);}
        });
    }

    public int runesRequired(int slot){
        return switch(slot){case 3->1;case 4->player.maxArcaneCharge;default->0;};
    }

    boolean canUse(int slot){
        if(!canStartCast() || player.arcaneCharge<runesRequired(slot))return false;
        return (slot!=0 && slot!=1 && slot!=4) || canAimGround(6.5f);
    }

    boolean canStartCast(){
        return !active() && player.hasStaff() && !player.knockbackActive && !player.currentInput.gameplaySuppressed
                && !player.currentInput.ePressed;
    }

    boolean canAimGround(float range){
        int tx=player.currentInput.aimWorldX,ty=player.currentInput.aimWorldY;
        return Math.hypot(tx-x(),ty-y())<=player.gp.tileSize*range
                && !RangerEffect.blocked(player.gp,tx,ty,1,1) && RangerEffect.clearLine(player.gp,x(),y(),tx,ty);
    }

    private void place(MageEffect.Kind kind,float multiplier,int ticks){
        int tx=player.currentInput.aimWorldX,ty=player.currentInput.aimWorldY;
        poseFor(ticks,aim(tx,ty));
        player.gp.projectileList.add(MageEffect.area(player,kind,tx,ty,power(multiplier),0));
        Particle.spray(player,Particle.Style.SQUARE,kind==MageEffect.Kind.FROST?MageEffect.FROST:
                kind==MageEffect.Kind.EMBER?MageEffect.EMBER:MageEffect.LILAC,7,5,18,3,heading,0.8,0,-17);
    }

    private void infusionCue(){
        Particle.emit(player,Particle.Style.RING,MageEffect.LILAC,18,22,0,0,0,-10);
        Particle.burst(player,Particle.Style.SQUARE,MageEffect.LILAC,9,5.5f,20,3.8f);
    }

    void fireBolt(int aimX,int aimY){
        if(!player.canResolveWorldActions() || !player.hasStaff())return;
        player.gp.projectileList.add(MageEffect.bolt(player,x(),y(),aim(aimX,aimY),power(1)));
    }

    void gainRune(){
        if(!player.canResolveWorldActions() || !player.isMage() || player.isDead)return;
        int count=1;
        for(var iterator=player.statusEffects.iterator();iterator.hasNext();){
            StatusEffect effect=iterator.next();
            if(!effect.type.equals(INFUSION))continue;
            count=2;
            if(--effect.value<=0)iterator.remove();
            break;
        }
        int before=player.arcaneCharge;
        player.arcaneCharge=Math.min(player.maxArcaneCharge,before+count);
        if(before<player.maxArcaneCharge && player.arcaneCharge==player.maxArcaneCharge){
            Particle.emit(player,Particle.Style.RING,MageEffect.LILAC,22,18,0,0,0,-22);
            Particle.burst(player,Particle.Style.SQUARE,MageEffect.LILAC,8,5.5f,18,3.6f);
        }
    }

    void poseFor(int ticks,double angle){
        player.interruptForMageSpell();
        castTicks=castLength=ticks;
        heading=angle;
    }

    boolean active(){return castTicks>0;}
    public int castTicks(){return castTicks;}
    public int castLength(){return castLength;}
    public double heading(){return heading;}

    void cancel(){castTicks=0;}

    void update(){
        if(!player.hasStaff() || player.knockbackActive || player.currentInput.gameplaySuppressed){cancel();return;}
        if(castTicks>0)castTicks--;
    }

    void pose(){
        if(!active())return;
        player.animState="attack";
        player.direction=RangerEffect.direction(heading);
        float progress=1-castTicks/(float)castLength;
        player.spriteNum=1+Math.min(CharacterSprites.ATTACK_FRAMES-1,(int)(progress*CharacterSprites.ATTACK_FRAMES));
    }

    public void syncVisual(int ticks,int length,double angle,int runes,boolean infused){
        if(infused && !player.hasStatusEffect(INFUSION))infusionCue();
        if(player.arcaneCharge<player.maxArcaneCharge && runes>=player.maxArcaneCharge){
            Particle.emit(player,Particle.Style.RING,MageEffect.LILAC,22,18,0,0,0,-22);
        }
        castTicks=ticks;castLength=length;heading=angle;
    }

    void draw(Graphics2D graphics){
        if(player.isDead || !player.hasStaff())return;
        int infusion=player.getStatusEffectValue(INFUSION);
        int runes=player.arcaneCharge;
        if(runes<=0 && infusion<=0)return;
        Player camera=player.gp.localPlayer();
        float cx=x()-camera.worldX+camera.screenX;
        float cy=y()-camera.worldY+camera.screenY-44;
        cx+=switch(player.direction){case "left"->-14;case "right"->17;case "up"->-14;default->15;};
        var g=(Graphics2D)graphics.create();
        if(runes>=player.maxArcaneCharge){
            g.setColor(new Color(213,179,240,150));g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Float(cx-12,cy-7,24,14));
        }
        for(int i=0;i<runes;i++){
            double angle=player.gp.currentTick*0.085+i*Math.PI/2;
            float dx=(float)Math.cos(angle)*12,dy=(float)Math.sin(angle)*7;
            g.setColor(MageEffect.LILAC);
            g.fill(MageEffect.diamond(cx+dx,cy+dy,3.3f,4.2f));
            g.setColor(Palette.PLASTER);g.fill(MageEffect.diamond(cx+dx,cy+dy,1.5f,2.2f));
        }
        for(int i=0;i<infusion;i++){
            double angle=-player.gp.currentTick*0.12+i*Math.PI;
            g.setColor(MageEffect.FROST);
            g.fill(MageEffect.diamond(cx+(float)Math.cos(angle)*18,cy+(float)Math.sin(angle)*11,3,4));
        }
        g.dispose();
    }

    private int power(float multiplier){return Math.max(1,Math.round(player.getEffectiveAttack()*multiplier));}
    private int x(){return player.worldX+(int)player.solidArea.getCenterX();}
    private int y(){return player.worldY+(int)player.solidArea.getCenterY();}
    String aimDirection(int tx,int ty){return RangerEffect.direction(aim(tx,ty));}

    double aim(int tx,int ty){
        int dx=tx-x(),dy=ty-y();
        return (tx==0 && ty==0 || dx==0 && dy==0)?facing():Math.atan2(dy,dx);
    }
    double facing(){
        return switch(player.direction){case "up"->-Math.PI/2;case "down"->Math.PI/2;case "left"->Math.PI;default->0;};
    }
}
