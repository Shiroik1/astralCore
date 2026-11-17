package world;

import main.Gamepanel;
import monster.*;
import object.*;
import entity.*;
import java.awt.Rectangle;
import java.util.*;

/** One authoritative saved encounter, independent of ordinary encounter rolls and terrain draw chunks. */
public final class SlimeNestEncounter {
    private final Gamepanel gp;
    private final Set<String> cleared=new TreeSet<>(),initialized=new TreeSet<>();
    private String blockedReason="";
    public SlimeNestEncounter(Gamepanel gp){this.gp=gp;}
    public void reset(){cleared.clear();initialized.clear();blockedReason="";}
    public boolean active(){return gp.worldConfig.procedural() && gp.generatedWorld!=null && gp.generatedWorld.slimeNest()!=null && !gp.quests.inDungeon() && (!gp.isNetworked || gp.isHost);}
    public int reservedSlots(){return active() && !cleared.contains(gp.generatedWorld.slimeNest().id())?Gamepanel.ENCOUNTER_MONSTER_SLOTS:0;}
    public boolean fighting(){for(var m:gp.monster)if(m instanceof MON_CrownedSlime b && b.showBossHealth())return true;return false;}
    public int livingSummons(String id){int count=0;for(var m:gp.monster)if(m instanceof MON_GreenSlime s && s.alive && !s.dying && s.HP>0 && (id+"/add").equals(s.nestId))count++;return count;}
    public String diagnostic(){
        if(gp.generatedWorld==null)return "No procedural overworld";
        var plan=gp.generatedWorld.slimeNest();
        String message=plan==null?"No nest in generator v"+gp.generatedWorld.generatorVersion+": "+gp.generatedWorld.nestDiagnostic()
                :plan.id()+" at tile "+(plan.col()+plan.size()/2)+","+(plan.row()+plan.size()/2)+" arena="+plan.size();
        return message+(blockedReason.isEmpty()?"":"; "+blockedReason);
    }
    public void ensurePopulation(){ensurePopulation(false);}
    public void ensurePopulation(boolean initial){
        if(!active())return;var plan=gp.generatedWorld.slimeNest();String id=plan.id();int kept=-1;
        // Stable encounter identity, not array membership or a second chunk callback, owns the boss.
        for(int i=0;i<gp.monster.length;i++)if(gp.monster[i] instanceof MON_CrownedSlime b && id.equals(b.nestId)){
            if(cleared.contains(id) || kept>=0){gp.monster[i]=null;continue;}kept=i;
        }
        if(cleared.contains(id)){removeSummons(id);return;}
        if(kept>=0){initialized.add(id);blockedReason="";return;}
        if(initialized.contains(id))return;
        for(int y=plan.row();y<plan.row()+plan.size();y++)for(int x=plan.col();x<plan.col()+plan.size();x++)
            if(gp.buildings.coversGround(x,y) || gp.crafting.blocks(new Rectangle(x*gp.tileSize,y*gp.tileSize,gp.tileSize,gp.tileSize))){blockedReason="Encounter blocked by saved/player construction; construction retained";return;}
        int cx=(plan.col()+plan.size()/2)*gp.tileSize+gp.tileSize/2,cy=(plan.row()+plan.size()/2)*gp.tileSize+gp.tileSize/2;
        var boss=new MON_CrownedSlime(gp);boss.worldX=cx-boss.solidArea.x-boss.solidArea.width/2;boss.worldY=cy-boss.solidArea.y-boss.solidArea.height/2;boss.nestId=id;boss.setHome();
        if(!initial)for(var p:gp.players)if(p!=null && !p.inWell && viewport(p,gp.tileSize).intersects(SlimeMotion.body(boss,boss.worldX,boss.worldY))){blockedReason="Waiting for an off-screen insertion into an older saved world";return;}
        if(!insert(boss)){blockedReason="Boss slot/footprint is occupied; will retry safely";return;}
        initialized.add(id);blockedReason="";
        int guardians=0;
        for(int r=2;r<=Math.max(2,plan.size()/2-2) && guardians<2;r++)for(int i=0;i<8 && guardians<2;i++){
            double angle=Math.PI*2*i/8;if(spawnGuard(id,cx+(int)(Math.cos(angle)*r*gp.tileSize),cy+(int)(Math.sin(angle)*r*gp.tileSize),false))guardians++;
        }
    }
    private Rectangle viewport(Player p,int margin){return new Rectangle(p.worldX-p.screenX-margin,p.worldY-p.screenY-margin,gp.screenWidth+margin*2,gp.screenHeight+margin*2);}
    private boolean insert(Monster m){
        var plan=gp.generatedWorld.slimeNest();var body=SlimeMotion.body(m,m.worldX,m.worldY);
        if(plan==null || !new Rectangle(plan.col()*gp.tileSize,plan.row()*gp.tileSize,plan.size()*gp.tileSize,plan.size()*gp.tileSize).contains(body) || !SlimeMotion.clear(gp,body))return false;
        for(int r=Math.floorDiv(body.y,gp.tileSize);r<=Math.floorDiv(body.y+body.height-1,gp.tileSize);r++)for(int c=Math.floorDiv(body.x,gp.tileSize);c<=Math.floorDiv(body.x+body.width-1,gp.tileSize);c++)if(gp.buildings.coversGround(c,r))return false;
        for(var p:gp.players)if(SlimeMotion.activePlayer(gp,p) && body.intersects(SlimeMotion.body(p,p.worldX,p.worldY)))return false;
        for(var other:gp.monster)if(other!=null && other.alive && body.intersects(SlimeMotion.body(other,other.worldX,other.worldY)))return false;
        for(int i=0;i<gp.monster.length;i++)if(gp.monster[i]==null){gp.monster[i]=m;return true;}return false;
    }
    private boolean spawnGuard(String id,int cx,int cy,boolean summoned){
        var slime=new MON_GreenSlime(gp);slime.worldX=cx-slime.solidArea.x-slime.solidArea.width/2;slime.worldY=cy-slime.solidArea.y-slime.solidArea.height/2;slime.nestId=id;
        if(!insert(slime))return false;slime.setHome();
        if(summoned){slime.delayAttacks(SlimeCombatConfig.SUMMON_ATTACK_GRACE);Particle.emit(slime,Particle.Style.RING,art.SlimeArt.SAGE,24,24,0,0,0,0);}
        return true;
    }
    public int summon(MON_CrownedSlime boss,int needed){
        if(!active() || boss.nestId.isEmpty())return 0;needed=Math.min(SlimeCombatConfig.SUMMONS,Math.max(0,needed));
        int added=0,ts=gp.tileSize;var plan=gp.generatedWorld.slimeNest();
        // Preferred five-fold ring around the boss, then bounded whole-arena alternatives.
        for(int center=0;center<2 && added<needed;center++)for(int r=2;r<=Math.max(2,plan.size()/2-1) && added<needed;r++)for(int i=0;i<10 && added<needed;i++){
            double angle=Math.PI*2*(i<5?i:i-5+.5)/5;
            int cx=center==0?SlimeMotion.centerX(boss):(plan.col()+plan.size()/2)*ts+ts/2;
            int cy=center==0?SlimeMotion.centerY(boss):(plan.row()+plan.size()/2)*ts+ts/2;
            int x=cx+(int)Math.round(Math.cos(angle)*r*ts),y=cy+(int)Math.round(Math.sin(angle)*r*ts);
            boolean near=false;for(var p:gp.players)if(SlimeMotion.activePlayer(gp,p) && Math.hypot(x-SlimeMotion.centerX(p),y-SlimeMotion.centerY(p))<ts*1.5)near=true;
            if(!near && spawnGuard(boss.nestId+"/add",x,y,true))added++;
        }return added;
    }
    public void removeSummons(String id){for(int i=0;i<gp.monster.length;i++)if(gp.monster[i] instanceof MON_GreenSlime s && (id+"/add").equals(s.nestId)){s.alive=false;gp.monster[i]=null;}}
    public void defeated(MON_CrownedSlime boss){
        if(!active() || boss.nestId.isEmpty() || !cleared.add(boss.nestId))return;
        removeSummons(boss.nestId);gp.slimeSpawner.onEncounterEnded();
        var gel=ItemRegistry.create(ItemType.SLIME_GEL,gp);gel.stackCount=6;gp.quests.dropLoot(gel,boss.worldX,boss.worldY);
        gp.quests.dropLoot(ItemRegistry.create(ItemType.ROYAL_SLIME_CORE,gp),boss.worldX,boss.worldY);
    }
    public String snapshot(){
        var lines=new ArrayList<String>();var p=gp.generatedWorld==null?null:gp.generatedWorld.slimeNest();
        if(p!=null)lines.add("P "+p.id()+" "+p.col()+" "+p.row()+" "+p.size());
        for(String id:initialized)lines.add("I "+id);for(String id:cleared)lines.add("C "+id);return String.join("\n",lines);
    }
    public void restore(String data){
        reset();String savedPlan=null;
        if(data!=null)for(String line:data.split("\n")){
            if(line.startsWith("I "))initialized.add(line.substring(2));if(line.startsWith("C "))cleared.add(line.substring(2));if(line.startsWith("P "))savedPlan=line;
        }
        var p=gp.generatedWorld==null?null:gp.generatedWorld.slimeNest();
        if(savedPlan!=null && (p==null || !savedPlan.equals("P "+p.id()+" "+p.col()+" "+p.row()+" "+p.size()))){
            blockedReason="Saved encounter plan differs: retaining existing entities/construction, no replacement spawn";
            if(p!=null)initialized.add(p.id());main.Log.warn("[NEST] "+blockedReason);return;
        }
        ensurePopulation();
    }
}
