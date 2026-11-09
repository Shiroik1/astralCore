import entity.*;
import main.*;
import monster.*;
import object.*;
import net.*;
import java.util.*;

public class HitResourceGenerationTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
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
            p.direction=direction;p.rage=0;p.attacking=true;p.animState="attack";p.spriteNum=3;g.hitStopCounter=0;
            if(melee.equals("warrior")){
                var bounds=p.meleeAttackBounds();boolean side=direction.equals("left") || direction.equals("right");
                require(bounds.width==(side?58:60) && bounds.height==(side?60:58),"longer, wider Warrior range rotates with facing");
            }
            int x=p.worldX,y=p.worldY,hp=boss.HP,resource=melee.equals("hunter")?5:10;
            int damage=melee.equals("hunter")?Math.max(1,(p.getAttack()-boss.defense+1)/2):1;
            while(p.spriteNum<4)p.attacking();
            require(boss.HP==hp-damage && p.rage==resource,"starter "+melee+" hurts "+boss.name+" and gains resource facing "+direction);
            require(p.worldX==x && p.worldY==y && p.solidArea.width==32 && p.solidArea.height==32,"swing restores player collision and position");
            g.hitStopCounter=0;p.attacking();require(boss.HP==hp-damage && p.rage==resource,"no duplicate hit between strikes");
            boss.invincible=false;p.worldX+=500;g.hitStopCounter=0;p.attacking();require(boss.HP==hp-damage && p.rage==resource,"misses grant no resource");
            require(boss.comboStacks==0,"a missed second cut earns no stack");
        }
        checkDaggerPair(g,p);
        var boss=new MON_SlimeBoss(g);
        Arrays.fill(g.monster,null);g.monster[0]=boss;
        p.setCharacterDesign("mage","female");p.setDefaultValues();p.setItems();p.refreshWeaponAnimation();
        g.isNetworked=false;boss.invincible=false;boss.HP=1;int xp=p.exp;
        require(p.damageMonster(0,1,false,null),"positive weak hit can finish armored boss");require(boss.dying && p.exp==xp+boss.exp,"kill rewards once");
        require(!p.damageMonster(0,1,false,null) && p.exp==xp+boss.exp,"dead boss cannot yield repeated rewards");
        require(!p.damageMonster(999,1,false,null) && !p.damageMonster(-1,1,false,null),"invalid targets cannot count as hits");
        // Charge and rage still travel in the existing authoritative player snapshot.
        p.arcaneCharge=2;WorldSnapshot state=SnapshotBuilder.build(g);require(state.players[0].arcaneCharge==2,"earned charges are synchronized");
        System.out.println("PASS: both melee classes hit both bosses in all four directions, paired cuts, armor minimum damage, hit-only melee resource and one-time kill rewards.");
    }

    static void checkDaggerPair(Gamepanel g,Player p){
        Arrays.fill(g.monster,null);
        p.resetForSceneTransition();p.setDefaultPosition();p.direction="right";p.rage=0;
        var target=new MON_GreenSlime(g);var immune=new MON_GreenSlime(g);
        var area=p.meleeAttackBounds();
        require(area.width==52 && area.height==52,"starter daggers use the old sword's range");
        for(Monster enemy:new Monster[]{target,immune}){
            enemy.worldX=area.x+area.width-1-enemy.solidArea.x;
            enemy.worldY=area.y-enemy.solidArea.y;
        }
        g.monster[0]=target;g.monster[1]=immune;immune.invincible=true;
        int hp=target.HP,immuneHp=immune.HP;
        p.attacking=true;p.animState="attack";p.spriteNum=3;g.hitStopCounter=0;
        while(p.spriteNum<4)p.attacking();
        require(target.HP<hp && target.invincible && !target.knockbackActive && p.rage==5 && target.comboStacks==0,
                "first cut lands at full reach without granting a stack");
        hp=target.HP;
        g.hitStopCounter=0;p.attacking();
        require(target.HP==hp,"beat between cuts");
        p.attacking();
        require(target.HP<hp && target.knockbackActive && p.rage==10 && target.comboStacks==1,
                "second cut lands through its own hit immunity and grants one stack");
        require(SnapshotBuilder.build(g).monsters[0].comboStacks==1,"earned stack is included in co-op snapshots");
        require(immune.HP==immuneHp && immune.invincible,"other hit immunity is respected");
        hp=target.HP;g.hitStopCounter=0;p.attacking();
        require(target.HP==hp && p.rage==10,"recovery cannot hit again");

        target.HP=target.maxHP=100;
        for(int pair=2;pair<=3;pair++){
            target.invincible=false;target.knockbackActive=false;
            p.resetForSceneTransition();p.attacking=true;p.animState="attack";p.spriteNum=3;g.hitStopCounter=0;
            while(p.spriteNum<4)p.attacking();
            require(target.comboStacks==(pair==2?1:0),"first cut leaves existing stacks alone");
            hp=target.HP;g.hitStopCounter=0;p.attacking();p.attacking();
            require(target.HP==hp-(pair==2?4:2) && target.comboStacks==(pair==2?0:1),
                    "the fourth cut crits, clears the stack and starts a new cycle");
        }
    }
}
