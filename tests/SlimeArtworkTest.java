import art.*;
import entity.*;
import monster.*;
import object.*;
import net.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Rendering contracts, existing hit timing, special attack poses and projectile replacement. */
public class SlimeArtworkTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static BufferedImage frame(Entity e)throws Exception{
        var method=Entity.class.getDeclaredMethod("getCurrentFrame");method.setAccessible(true);return (BufferedImage)method.invoke(e);
    }
    static int hash(BufferedImage img){return AppearanceEquipmentTest.pixels(img);}
    static void checkFrames(Map<String,SpriteAnimation> sprites,boolean king){
        for(String dir:new String[]{"down","up","left","right"}){
            require(sprites.get("idle_"+dir).frames.length==8,"eight idle frames");
            require(sprites.get("walk_"+dir).frames.length==8,"eight movement frames");
            require(sprites.get("attack_"+dir).frames.length==10,"contact hit frame timing remains valid");
            require(sprites.get("death_"+dir).frames.length==10,"death duration remains valid");
            for(var entry:sprites.entrySet())if(entry.getKey().endsWith("_"+dir)){
                for(BufferedImage img:entry.getValue().frames){
                    boolean opaque=false;
                    for(int color:img.getRGB(0,0,img.getWidth(),img.getHeight(),null,0,img.getWidth()))if((color>>>24)>150){opaque=true;break;}
                    require(opaque,"nonempty "+entry.getKey());
                    int size=img.getWidth();
                    for(int edge=0;edge<size;edge++){
                        require((img.getRGB(edge,0)>>>24)<100 && (img.getRGB(edge,size-1)>>>24)<100
                                && (img.getRGB(0,edge)>>>24)<100 && (img.getRGB(size-1,edge)>>>24)<100,
                                "unclipped "+entry.getKey());
                    }
                }
            }
            int count=0;
            for(int color:sprites.get("idle_"+dir).frames[0].getRGB(0,0,entrySize(sprites,dir),entrySize(sprites,dir),null,0,entrySize(sprites,dir))){
                int r=color>>16&255,g=color>>8&255,b=color&255;
                if((color>>>24)>180 && r>150 && r>g+18 && g>b+25)count++;
            }
            require(king?count>10:count==0,"only King has a crown");
        }
        require(!sprites.keySet().stream().anyMatch(k->k.contains("spit")),"no mouth-spit body animation");
    }
    static int entrySize(Map<String,SpriteAnimation> map,String dir){return map.get("idle_"+dir).width;}
    public static void main(String[] args)throws Exception{
        var game=new AppearanceEquipmentTest.QuietGame();var basic=new MON_GreenSlime(game);var king=new MON_SlimeBoss(game);
        checkFrames(basic.sprites,false);checkFrames(king.sprites,true);
        require(basic.maxHP==10 && basic.attack==5 && king.maxHP>0 && king.attack==9,"basic slime stats and harder King tuning");
        require(hash(basic.sprites.get("idle_left").frames[0])!=hash(basic.sprites.get("idle_right").frames[0]),"one-eye side views face correctly");
        king.bossState="telegraph_slam";king.bossStateTimer=0;require(frame(king)==king.sprites.get("slam_down").frames[0],"slam anticipation");
        king.bossStateTimer=59;require(frame(king)==king.sprites.get("slam_down").frames[3],"pre-impact jump");
        king.bossState="cooldown";king.lastAttackWasSlam=true;king.bossStateTimer=0;
        require(frame(king)==king.sprites.get("slam_down").frames[4],"impact art begins at damage time");
        king.bossStateTimer=19;require(frame(king)==king.sprites.get("slam_down").frames[7],"slam settles");
        king.lastAttackWasSlam=false;require(frame(king)!=king.sprites.get("slam_down").frames[7],"spray does not use slam landing");
        king.bossState="telegraph_spray";require(frame(king)==king.sprites.get("charge_down").frames[3],"projectile charge has no spitting pose");
        king.attackState=true;king.animState="attack";king.spriteNum=6;require(frame(king)==king.sprites.get("attack_down").frames[5],"contact hit window unaffected");king.attackState=false;
        var spray=MON_SlimeBoss.class.getDeclaredMethod("executeSpray");spray.setAccessible(true);
        king.worldX=game.localPlayer().worldX+250;king.worldY=game.localPlayer().worldY+250;spray.invoke(king);
        require(game.projectileList.size()==5,"same projectile fan count");
        for(Entity e:game.projectileList){
            require(e instanceof OBJ_slimebolt && e.speed==5 && e.maxHP==80 && e.attack==2,"slime artwork with same projectile stats");
            require(e.sprites.get("idle_right").frames.length==6,"animated slime droplets");
        }
        var droplet=(OBJ_slimebolt)game.projectileList.get(0);
        require(Arrays.stream(droplet.sprites.get("idle_right").frames).map(SlimeArtworkTest::hash).distinct().count()==6,
                "six visually distinct droplet frames");
        Set<Integer> shown=new HashSet<>();
        for(int i=0;i<24;i++){shown.add(droplet.spriteNum);droplet.update();}
        require(shown.size()==6,"projectile uses all six animation frames");
        king.lastAttackWasSlam=true;game.monster[0]=king;
        require(SnapshotBuilder.build(game).monsters[0].lastAttackWasSlam,"landing pose synchronizes");
        king.dying=true;king.animState="death";king.spriteNum=10;
        require(frame(king)==king.sprites.get("death_down").frames[9],"death overrides boss special pose");
        System.out.println("PASS: slime directional art, crown, frames, combat timing, slam/charge/hurt/death poses, projectile fan and six-frame flight.");
    }
}
