import art.*;
import entity.*;
import main.*;
import monster.*;
import object.*;
import java.awt.image.BufferedImage;
import java.util.*;

public class SpawnAssetReuseTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var game=new AppearanceEquipmentTest.QuietGame();game.preloadGameplayAssets();
        require(Arrays.stream(game.monster).allMatch(Objects::isNull),"preloading does not spawn monsters");
        require(game.projectileList.isEmpty() && Arrays.stream(game.obj).allMatch(Objects::isNull),"preloading has no gameplay side effects");
        var flashes=Entity.class.getDeclaredField("flashCache");flashes.setAccessible(true);
        @SuppressWarnings("unchecked") Map<BufferedImage,BufferedImage> flashCache=(Map<BufferedImage,BufferedImage>)flashes.get(null);
        for(MonsterType type:MonsterType.values()){
            Monster first=type.create(game),second=type.create(game);
            for(String key:first.sprites.keySet()){
                SpriteAnimation a=first.sprites.get(key),b=second.sprites.get(key);
                require(a!=b && a.frames!=b.frames,"independent animation state: "+type+" "+key);
                for(int i=0;i<a.frames.length;i++){
                    require(a.frames[i]==b.frames[i],"immutable artwork reused: "+type+" "+key);
                    require(flashCache.containsKey(a.frames[i]),"hit effect already prepared: "+type+" "+key);
                }
            }
            var own=first.sprites.get("walk_down");var other=second.sprites.get("walk_down");
            own.frameDelay=999;own.frames[0]=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);first.HP=1;
            require(other.frameDelay==5 && other.frames[0].getWidth()>1 && second.HP>1,"individual enemy state remains isolated");
            require(type.create(game).sprites.get("walk_down").frames[0]==other.frames[0],"cached template remains intact");
        }
        var normal=new HashMap<String,SpriteAnimation>();var scaled=new HashMap<String,SpriteAnimation>();
        SlimeArt.buildAnimations(normal,72,1.1f);SlimeArt.buildAnimations(scaled,72,.9f);
        require(normal.get("idle_down").frames[0]!=scaled.get("idle_down").frames[0],"different art parameters have separate atlases");
        int effects=flashCache.size();for(int i=0;i<10;i++)MonsterType.SLIME_BOSS.create(game);
        require(flashCache.size()==effects,"repeated boss spawns do not allocate new hit effects");
        Player player=game.localPlayer();player.currentInput.ePressed=true;player.HP=1;
        game.eventHandler.healingPool(0,0,player);
        require(player.HP==player.maxHP,"healing event still restores HP");
        require(Arrays.stream(game.monster).allMatch(java.util.Objects::isNull),"healing does not repopulate the overworld");
        game.commands.execute("/spawn slime_boss 3");
        require(Arrays.stream(game.monster).filter(m->m instanceof MON_SlimeBoss).count()==3,"command spawns reuse assets without changing requested count");
        var drop1=new OBJ_slimebolt(game);var drop2=new OBJ_slimebolt(game);
        require(drop1.sprites.get("idle_right").frames[0]==drop2.sprites.get("idle_right").frames[0],"boss projectile artwork reused");
        System.out.println("PASS: prepared enemy artwork/hit effects, independent enemy state, cache keys, healing-event respawns and command spawn counts.");
    }
}
