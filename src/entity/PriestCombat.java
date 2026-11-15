package entity;

import art.Palette;
import main.Gamepanel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/** Host-owned support spells and status hooks shared by all party members. */
public final class PriestCombat {
    public static final String WARD="priestAegis", CHORUS="priestChorus", SANCTUARY="priestSanctuary";
    static final Color GOLD=new Color(0xF7D58C), SAGE=new Color(0xB9D1AD);
    private static final Color SHADOW=new Color(0x494436), HEAL_TEXT=new Color(0xBEE2A9);
    private static final Map<String,BufferedImage> ICONS=new HashMap<>();
    private static final float BEACON_RANGE=5, SANCTUARY_RADIUS=2.5f;
    private final Player player;
    private Player beacon;
    private int replicaBeacon=-1;
    private long lastBoltHeal=-150;

    PriestCombat(Player player){this.player=player;}

    void setup(Skill[] slots){
        slots[0]=skill("Mend","mend",2,4,"Heal the ally under the cursor and link your Beacon to them.",this::mend);
        slots[1]=skill("Aegis","aegis",3,8,"Ward your Beacon target against the next hit for 4s.",this::aegis);
        slots[2]=skill("Hallowed Ground","hallowed_ground",4,11,"Place a seal that damages enemies once and pulses three heals.",this::hallowed);
        slots[3]=skill("Dawn Chorus","dawn_chorus",3,12,"Give nearby allies a speed boost and a stronger next hit.",this::chorus);
        slots[4]=skill("Sanctuary","sanctuary",6,20,"Heal nearby allies and carry a protective aura for 4s.",this::sanctuary);
    }

    private Skill skill(String name,String key,int mana,int seconds,String description,Runnable action){
        Skill skill=new Skill(name,mana,seconds*60,()->{action.run();player.announceSkill(name);},icon(key));
        skill.description=description;
        return skill;
    }

    public static BufferedImage statusIcon(String type){
        return switch(type){
            case WARD -> icon("aegis");
            case CHORUS -> icon("dawn_chorus");
            case SANCTUARY -> icon("sanctuary");
            default -> null;
        };
    }

    static void tickDuringInteraction(Player target){
        target.statusEffects.removeIf(effect->
                (WARD.equals(effect.type) || CHORUS.equals(effect.type) || SANCTUARY.equals(effect.type)) && --effect.duration<=0);
    }

    private static synchronized BufferedImage icon(String key){
        return ICONS.computeIfAbsent(key,id->{
            try(var in=PriestCombat.class.getResourceAsStream("/skill_icon/priest/"+id+".png")){
                if(in==null)throw new IOException("Missing Priest icon "+id);
                return ImageIO.read(in);
            }catch(IOException e){throw new UncheckedIOException(e);}
        });
    }

    boolean canUse(int slot){
        return player.mage.canStartCast() && (slot!=2 || player.mage.canAimGround(BEACON_RANGE));
    }

    private void mend(){
        Player target=hoveredAlly();
        beacon=target;
        poseToward(target);
        heal(target,3+scaled(0.50f));
        player.gp.projectileList.add(MageEffect.mend(player,target));
    }

    private void aegis(){
        Player linked=beaconTarget();
        Player target=within(player,linked,BEACON_RANGE)?linked:player;
        poseToward(target);
        applyBuff(target,WARD,240,2+scaled(0.65f));
        cue(target,MageEffect.Kind.WARD_PULSE);
    }

    private void hallowed(){
        int tx=player.currentInput.aimWorldX,ty=player.currentInput.aimWorldY;
        player.mage.poseFor(12,player.mage.aim(tx,ty));
        player.gp.projectileList.add(MageEffect.area(player,MageEffect.Kind.HALLOWED,tx,ty,
                Math.max(1,scaled(0.85f)),Math.max(1,scaled(0.22f))));
    }

    private void chorus(){
        player.mage.poseFor(12,player.mage.facing());
        for(Player ally:player.gp.players){
            if(within(player,ally,SANCTUARY_RADIUS))applyBuff(ally,CHORUS,240,1);
        }
        cue(player,MageEffect.Kind.CHORUS);
    }

    private void sanctuary(){
        player.mage.poseFor(14,player.mage.facing());
        for(Player ally:player.gp.players){
            if(within(player,ally,SANCTUARY_RADIUS))heal(ally,2+scaled(0.45f));
        }
        applyBuff(player,SANCTUARY,240,25);
        cue(player,MageEffect.Kind.SANCTUARY_BLOOM);
    }

    private void poseToward(Player target){
        player.mage.poseFor(12,player.mage.aim(centerX(target),centerY(target)));
    }

    void onBoltHit(){
        if(!player.isPriest() || !living(player) || player.gp.currentTick-lastBoltHeal<150)return;
        Player target=beaconTarget();
        if(within(player,target,BEACON_RANGE) && heal(target,Math.max(1,scaled(0.18f)))>0){
            lastBoltHeal=player.gp.currentTick;
            player.gp.projectileList.add(MageEffect.mend(player,target));
        }
    }

    void reset(){
        beacon=null;replicaBeacon=-1;
        // A scene transition clears the link, while the bolt-heal throttle follows the caster.
        if(lastBoltHeal>player.gp.currentTick)lastBoltHeal=player.gp.currentTick-150;
    }

    public int beaconId(){return beaconTarget().playerId;}
    public void syncVisual(int beaconId){replicaBeacon=beaconId;}

    private Player beaconTarget(){
        var gp=player.gp;
        if(!player.canResolveWorldActions()){
            Player target=replicaBeacon>=0 && replicaBeacon<gp.players.length?gp.players[replicaBeacon]:null;
            return living(target) && target.inWell==player.inWell?target:player;
        }
        if(!living(beacon) || beacon.inWell!=player.inWell || beacon.playerId<0
                || beacon.playerId>=gp.players.length || gp.players[beacon.playerId]!=beacon)beacon=player;
        return beacon;
    }

    private Player hoveredAlly(){
        int x=player.currentInput.aimWorldX,y=player.currentInput.aimWorldY;
        Player nearest=player;
        double best=Double.POSITIVE_INFINITY;
        for(Player ally:player.gp.players){
            if(ally==player || !within(player,ally,BEACON_RANGE))continue;
            if(Math.abs(x-centerX(ally))>24 || y<ally.worldY-20 || y>ally.worldY+ally.solidArea.y+ally.solidArea.height)continue;
            if(!RangerEffect.clearLine(player.gp,centerX(player),centerY(player),centerX(ally),centerY(ally)))continue;
            double distance=Math.hypot(x-centerX(ally),y-centerY(ally));
            if(distance<best){best=distance;nearest=ally;}
        }
        return nearest;
    }

    private int scaled(float fraction){return Math.round(player.getEffectiveAttack()*fraction);}

    static int centerX(Player p){return p.worldX+(int)p.solidArea.getCenterX();}
    static int centerY(Player p){return p.worldY+(int)p.solidArea.getCenterY();}
    private static boolean living(Player p){return p!=null && !p.isDead && p.HP>0;}

    private static boolean within(Player source,Player target,float radius){
        return living(target) && source.inWell==target.inWell
                && Math.hypot(centerX(source)-centerX(target),centerY(source)-centerY(target))<=radius*source.gp.tileSize;
    }

    private static void applyBuff(Player target,String type,int duration,int value){
        target.statusEffects.removeIf(effect->effect.type.equals(type));
        target.addStatusEffect(new StatusEffect(type,duration,value,0));
    }

    static int heal(Player target,int amount){
        if(!living(target) || !target.canResolveWorldActions())return 0;
        int restored=Math.max(0,Math.min(amount,target.maxHP-target.HP));
        if(restored>0){
            target.HP+=restored;
            target.gp.spawnFloatingText(centerX(target),target.worldY-8,"+"+restored,HEAL_TEXT);
            Particle.spray(target,Particle.Style.SQUARE,GOLD,5,5,20,2.4f,-Math.PI/2,0.75,0,-9);
        }
        return restored;
    }

    private static void cue(Player target,MageEffect.Kind kind){
        target.gp.projectileList.add(MageEffect.area(target,kind,centerX(target),centerY(target),0,0));
    }

    /** The strongest nearby Sanctuary applies once, then the target's one-hit ward absorbs the rest. */
    static int protect(Player target,int damage){
        if(!target.canResolveWorldActions() || damage<=0)return damage;
        int reduction=sanctuaryReduction(target);
        int afterSanctuary=Math.round(damage*(100-Math.min(75,reduction))/100f);
        for(var iterator=target.statusEffects.iterator();iterator.hasNext();){
            StatusEffect effect=iterator.next();
            if(effect.type.equals(WARD)){
                iterator.remove();
                cue(target,MageEffect.Kind.WARD_BREAK);
                return Math.max(0,afterSanctuary-effect.value);
            }
        }
        if(afterSanctuary<damage)cue(target,MageEffect.Kind.WARD_PULSE);
        return afterSanctuary;
    }

    private static int sanctuaryReduction(Player target){
        int reduction=0;
        for(Player source:target.gp.players){
            if(living(source) && source.isPriest() && within(source,target,SANCTUARY_RADIUS)){
                reduction=Math.max(reduction,source.getStatusEffectValue(SANCTUARY));
            }
        }
        return reduction;
    }

    static int consumeChorus(Player attacker,int damage){
        boolean spent=attacker.statusEffects.removeIf(effect->effect.type.equals(CHORUS));
        if(!spent)return 0;
        cue(attacker,MageEffect.Kind.CHORUS_SPARK);
        return Math.max(1,Math.round(damage*0.15f));
    }

    public String beaconLabel(){
        Player target=beaconTarget();
        return target==player?"Self":target.playerName;
    }

    public static void drawSanctuaries(Gamepanel gp,Graphics2D graphics){
        Player camera=gp.localPlayer();
        if(camera==null)return;
        for(Player source:gp.players){
            if(!living(source) || !source.isPriest() || source.inWell!=gp.quests.inDungeon() || !source.hasStatusEffect(SANCTUARY))continue;
            float x=centerX(source)-camera.worldX+camera.screenX,y=centerY(source)-camera.worldY+camera.screenY;
            float r=gp.tileSize*SANCTUARY_RADIUS;
            var ring=new Ellipse2D.Float(x-r,y-r,r*2,r*2);
            var g=(Graphics2D)graphics.create();
            g.setColor(new Color(235,212,145,49));g.fill(ring);
            g.setStroke(new BasicStroke(6));g.setColor(new Color(80,76,46,130));g.draw(ring);
            g.setStroke(new BasicStroke(2.8f));g.setColor(GOLD);g.draw(ring);
            for(int i=0;i<6;i++){
                double angle=i*Math.PI/3;
                g.fill(MageEffect.diamond(x+(float)Math.cos(angle)*r,y+(float)Math.sin(angle)*r,3.5f,5.5f));
            }
            g.dispose();
        }
    }

    static void drawSupport(Player target,Graphics2D graphics){
        if(!living(target))return;
        Player camera=target.gp.localPlayer();
        float x=centerX(target)-camera.worldX+camera.screenX,y=centerY(target)-camera.worldY+camera.screenY;
        boolean marked=false;
        for(Player source:target.gp.players){
            if(living(source) && source.isPriest() && source.inWell==target.inWell && source.priest.beaconTarget()==target){
                marked=true;break;
            }
        }
        boolean warded=target.hasStatusEffect(WARD);
        boolean chorus=target.hasStatusEffect(CHORUS);
        boolean sheltered=sanctuaryReduction(target)>0;
        if(!marked && !warded && !chorus && !sheltered)return;
        var g=(Graphics2D)graphics.create();
        if(warded){
            var shell=new Arc2D.Float(x-24,y-41,48,54,0,180,Arc2D.OPEN);
            g.setColor(new Color(244,213,132,45));g.fill(shell);
            g.setStroke(new BasicStroke(4));g.setColor(SHADOW);g.draw(shell);
            g.setStroke(new BasicStroke(2));g.setColor(GOLD);g.draw(shell);
        }
        if(marked){
            var beacon=MageEffect.diamond(x-21,y-35,5,7);
            g.setColor(SHADOW);g.setStroke(new BasicStroke(4));g.draw(beacon);
            g.setColor(GOLD);g.fill(beacon);
            g.setColor(Palette.PLASTER);g.fill(MageEffect.diamond(x-21,y-35,2,3));
        }
        if(sheltered){
            var shield=new Path2D.Float();
            shield.moveTo(x+22,y-40);shield.lineTo(x+32,y-40);
            shield.quadTo(x+32,y-31,x+27,y-28);
            shield.quadTo(x+22,y-31,x+22,y-40);shield.closePath();
            g.setStroke(new BasicStroke(3));g.setColor(SHADOW);g.draw(shield);
            g.setColor(GOLD);g.fill(shield);
            g.setColor(Palette.PLASTER);g.fill(MageEffect.diamond(x+27,y-35,2,3));
        }
        if(chorus){
            float bob=(float)Math.sin(target.gp.currentTick*0.16)*3;
            g.setColor(GOLD);g.fill(MageEffect.diamond(x+24,y-20+bob,4,6));
        }
        g.dispose();
    }
}
