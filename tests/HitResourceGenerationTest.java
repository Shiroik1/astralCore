import entity.*;
import main.*;
import monster.*;
import object.*;
import net.*;
import java.util.*;

public class HitResourceGenerationTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Projectile cast(Gamepanel g,Player p){
        g.hitStopCounter=0;p.resetForSceneTransition();p.attacking=true;p.animState="attack";p.spriteNum=4;p.attacking();
        return (Projectile)g.projectileList.getLast();
    }
    static void impact(Projectile shot,Player owner,Monster enemy){
        shot.set(enemy.worldX+enemy.solidArea.x,enemy.worldY+enemy.solidArea.y,"right",true,owner);shot.update();
    }
    public static void main(String[] args)throws Exception{
        var g=new AppearanceEquipmentTest.QuietGame();Player p=g.localPlayer();g.preloadGameplayAssets();
        for(String melee:new String[]{"warrior","hunter"})for(boolean hell:new boolean[]{false,true})for(String direction:new String[]{"up","down","left","right"}){
            Arrays.fill(g.monster,null);p.setCharacterDesign(melee,"male");p.setDefaultValues();p.refreshWeaponAnimation();p.resetForSceneTransition();
            Monster boss=hell?new MON_HellSlime(g):new MON_SlimeBoss(g);boss.worldX=25*g.tileSize;boss.worldY=25*g.tileSize;g.monster[0]=boss;
            int left=boss.worldX+boss.solidArea.x,top=boss.worldY+boss.solidArea.y;
            p.worldX=left+boss.solidArea.width/2-p.solidArea.x-p.solidArea.width/2;
            p.worldY=top+boss.solidArea.height/2-p.solidArea.y-p.solidArea.height/2;
            switch(direction){
                case "up" -> p.worldY=top+boss.solidArea.height-p.solidArea.y+1;
                case "down" -> p.worldY=top-p.solidArea.y-p.solidArea.height-1;
                case "left" -> p.worldX=left+boss.solidArea.width-p.solidArea.x+1;
                case "right" -> p.worldX=left-p.solidArea.x-p.solidArea.width-1;
            }
            p.direction=direction;p.rage=0;p.attacking=true;p.animState="attack";p.spriteNum=4;g.hitStopCounter=0;
            int x=p.worldX,y=p.worldY,hp=boss.HP;p.attacking();
            require(boss.HP==hp-1 && p.rage==10,"starter "+melee+" hurts "+boss.name+" and gains rage facing "+direction);
            require(p.worldX==x && p.worldY==y && p.solidArea.width==32 && p.solidArea.height==32,"swing restores player collision and position");
            g.hitStopCounter=0;p.attacking();require(boss.HP==hp-1 && p.rage==10,"invincibility prevents duplicate damage/rage in active frames");
            boss.invincible=false;p.worldX+=500;g.hitStopCounter=0;p.attacking();require(boss.HP==hp-1 && p.rage==10,"misses grant no rage");
        }
        var boss=new MON_SlimeBoss(g);boss.worldX=25*g.tileSize;boss.worldY=25*g.tileSize;g.monster[0]=boss;
        p.setCharacterDesign("mage","female");p.setDefaultValues();p.setItems();p.refreshWeaponAnimation();p.statusEffects.clear();p.arcaneCharge=0;
        Arrays.fill(g.monster,null);Projectile miss=cast(g,p);require(p.arcaneCharge==0,"cast does not generate runes");
        for(int tick=0;tick<85;tick++)miss.update();require(p.arcaneCharge==0 && !miss.alive,"missed projectile expires with no rune gain");
        g.monster[0]=boss;Projectile shot=cast(g,p);require(p.arcaneCharge==0,"new cast still waits for impact");
        int hp=boss.HP;impact(shot,p,boss);require(p.arcaneCharge==1 && boss.HP<hp,"actual spell damage generates one rune");
        boss.invincible=false;hp=boss.HP;shot.update();require(p.arcaneCharge==1 && boss.HP==hp,"dead projectile cannot generate runes twice");
        boss.invincible=true;shot=cast(g,p);impact(shot,p,boss);require(p.arcaneCharge==1,"blocked impact cannot generate runes");
        boss.invincible=false;boss.dying=true;shot=cast(g,p);impact(shot,p,boss);require(p.arcaneCharge==1,"dying enemies cannot generate runes");boss.dying=false;
        p.arcaneCharge=0;p.skills[0].onUse.run();require(p.arcaneCharge==0 && p.hasStatusEffect("runeInfusion"),"Mana Infusion requires subsequent hits");
        shot=cast(g,p);require(p.arcaneCharge==0,"infused cast into empty space grants nothing");
        impact(shot,p,boss);require(p.arcaneCharge==2,"infusion increases rune gain on a confirmed hit");
        boss.invincible=false;shot=cast(g,p);impact(shot,p,boss);require(p.arcaneCharge==p.maxArcaneCharge && p.auraActive,"successful hits fill runes and activate full-charge aura");
        boss.invincible=false;shot=cast(g,p);require(shot.empowered && p.arcaneCharge==0,"full charge is consumed by empowered cast");
        impact(shot,p,boss);require(p.arcaneCharge==0,"empowered spell does not refund its own runes");
        p.statusEffects.clear();boss.invincible=false;p.arcaneCharge=0;shot=cast(g,p);
        g.isNetworked=true;g.isHost=false;hp=boss.HP;impact(shot,p,boss);require(p.arcaneCharge==0 && boss.HP==hp,"client cannot resolve damage or mint charges");
        g.isNetworked=false;boss.invincible=false;boss.HP=1;int xp=p.exp;
        require(p.damageMonster(0,1,false,null),"positive weak hit can finish armored boss");require(boss.dying && p.exp==xp+boss.exp,"kill rewards once");
        require(!p.damageMonster(0,1,false,null) && p.exp==xp+boss.exp,"dead boss cannot yield repeated rewards");
        require(!p.damageMonster(999,1,false,null) && !p.damageMonster(-1,1,false,null),"invalid targets cannot count as hits");
        // Charge and rage still travel in the existing authoritative player snapshot.
        p.arcaneCharge=2;WorldSnapshot state=SnapshotBuilder.build(g);require(state.players[0].arcaneCharge==2,"earned charges are synchronized");
        System.out.println("PASS: both melee classes hit both bosses in all four directions, armor minimum damage, hit-only rage, spell-impact runes, no miss/duplicate/blocked farming, hit-based Mana Infusion, empowered consumption, authority and one-time kill rewards.");
    }
}
