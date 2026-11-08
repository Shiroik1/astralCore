import entity.*;
import main.*;
import monster.*;
import net.*;
import quest.QuestManager.Stage;
import world.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Exercises the actual dialogue, scenery, monster-removal and snapshot paths. */
public class WellQuestTest {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void finish(Player p){int limit=100;while(p.inDialogue && limit-->0)p.advanceOrCloseDialogue();require(limit>0,"dialogue terminates");}
    static void talk(Player p,int index){p.currentInput=new InputState();p.currentInput.ePressed=true;p.interactNPC(index);}
    static void kill(Gamepanel g,int index)throws Exception{
        g.monster[index].alive=false;
        var remove=Gamepanel.class.getDeclaredMethod("removeDefeatedMonster",int.class);remove.setAccessible(true);remove.invoke(g,index);
        g.quests.update();
    }
    static void use(Gamepanel g,WorldObject object){
        Player p=g.localPlayer();p.resetForSceneTransition();
        p.worldX=object.worldX-p.solidArea.x-p.solidArea.width/2;
        p.worldY=object.blockRect.y+object.blockRect.height+3-p.solidArea.y;
        p.direction="up";p.currentInput=new InputState();p.currentInput.ePressed=true;
        require(g.collisionChecker.findInteractable(p)==object,"scenery is reachable through normal controls");
        p.update();
    }
    static long count(Gamepanel g,Class<?> type){return Arrays.stream(g.monster).filter(type::isInstance).count();}
    static void render(Gamepanel g,String name)throws Exception{
        BufferedImage image=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);
        var graphics=Gamepanel.class.getDeclaredField("g2");graphics.setAccessible(true);graphics.set(g,image.createGraphics());
        g.gameState=g.playState;g.drawToTempscreen();
        File dir=new File("/tmp/astral-well");dir.mkdirs();ImageIO.write(image,"png",new File(dir,name+".png"));
    }
    public static void main(String[] args)throws Exception{
        var g=new AppearanceEquipmentTest.QuietGame();Player p=g.localPlayer();
        g.assetSetter.setNPC();SceneryLoader.load(g);g.preloadGameplayAssets();
        Entity oldman=g.npc[0];var surface=List.copyOf(g.worldObjects);
        WorldObject well=surface.stream().filter(o->o.kind==WorldObjectType.WELL).findFirst().orElseThrow();
        well.interact(p);require(!g.quests.inDungeon(),"quest gates well entrance");finish(p);
        talk(p,0);p.currentInput.escapePressed=true;p.interactNPC(999);
        require(g.quests.stage()==Stage.NOT_STARTED && !p.inDialogue,"Escape does not accept quest");
        talk(p,0);finish(p);require(g.quests.stage()==Stage.SLAY,"finishing introduction accepts quest");
        p.worldX=well.worldX-p.solidArea.x-p.solidArea.width/2;
        p.worldY=well.blockRect.y+well.blockRect.height+3-p.solidArea.y;
        int townX=p.worldX,townY=p.worldY;use(g,well);
        require(g.quests.inDungeon() && g.npc[0]==null,"well enters separate dungeon");
        require(count(g,MON_GreenSlime.class)==10,"exactly ten dungeon slimes");
        require(g.tileM.isSolid(14,20) && g.tileM.isSolid(20,18) && !g.tileM.isSolid(25,27),"dungeon collision");
        require(!SceneryLoader.save(g),"dungeon cannot overwrite town scenery");
        require(!p.inDialogue,"transition clears dialogue");
        render(g,"entrance");
        for(int i=0;i<5;i++)kill(g,i);
        require(g.quests.kills()==5 && count(g,MON_SlimeBoss.class)==0,"no outdoor boss at five dungeon kills");
        Monster remaining=g.monster[9];
        WorldObject ladder=g.worldObjects.stream().filter(o->o.kind==WorldObjectType.WELL_LADDER).findFirst().orElseThrow();
        use(g,ladder);require(!g.quests.inDungeon() && g.npc[0]==oldman && g.worldObjects.equals(surface),"return restores village");
        require(p.worldX==townX && p.worldY==townY,"return restores player position");
        use(g,well);require(g.monster[9]==remaining && count(g,MON_GreenSlime.class)==5,"reentry preserves progress and enemies");
        g.isHost=true;
        Player companion=new Player(g,g.keyH);companion.playerId=1;companion.isLocal=false;g.players[1]=companion;g.quests.onPlayerJoined(companion);
        for(int i=5;i<10;i++)kill(g,i);
        require(g.quests.kills()==10 && count(g,MON_SlimeBoss.class)==1 && g.quests.stage()==Stage.BOSS_INTRO,"tenth kill triggers one King");
        require(p.inDialogue && p.dialogueTag.equals(quest.QuestManager.INTRO_TAG),"King event dialogue opens");
        int beforeX=p.worldX;p.currentInput.right=true;p.simulateMovement(p.currentInput);
        require(p.worldX==beforeX,"intro freezes movement");
        render(g,"king-intro");
        g.quests.update();require(count(g,MON_SlimeBoss.class)==1,"event cannot duplicate King");
        // Snapshot carries the complete encounter and lets late joiners see the intro.
        var client=new AppearanceEquipmentTest.QuietGame();client.isNetworked=true;client.isHost=false;
        var endpoint=new com.esotericsoftware.kryonet.Client();NetworkRegistration.register(endpoint);
        var output=new com.esotericsoftware.kryo.io.Output(8192,-1);
        endpoint.getKryo().writeObject(output,SnapshotBuilder.build(g));
        WorldSnapshot decoded=endpoint.getKryo().readObject(new com.esotericsoftware.kryo.io.Input(output.toBytes()),WorldSnapshot.class);
        var apply=GameClient.class.getDeclaredMethod("applySnapshot",WorldSnapshot.class);apply.setAccessible(true);
        apply.invoke(new GameClient(client),decoded);
        require(count(client,MON_SlimeBoss.class)==1,"King survives full network snapshot serialization");
        require(client.quests.inDungeon() && client.quests.kills()==10 && client.localPlayer().inDialogue,"client receives area, progress, intro");
        finish(p);g.quests.update();require(g.quests.stage()==Stage.BOSS_INTRO,"party encounter waits for companion dialogue");
        Player joined=new Player(g,g.keyH);joined.playerId=2;joined.isLocal=false;g.players[2]=joined;g.quests.onPlayerJoined(joined);
        require(joined.inDialogue,"mid-intro join receives dialogue");g.players[2]=null;
        finish(companion);g.quests.update();require(g.quests.stage()==Stage.KING,"party acknowledgement and disconnect release fight");
        p.isDead=true;p.performRespawn();require(!p.isDead && !p.inWell && p.worldY==28*g.tileSize && g.quests.stage()==Stage.KING,"respawn returns to plaza");
        g.quests.enterWell(p);
        int kingSlot=-1;for(int i=0;i<g.monster.length;i++)if(g.monster[i] instanceof MON_SlimeBoss)kingSlot=i;
        kill(g,kingSlot);require(g.quests.stage()==Stage.RETURN && p.inDialogue,"King defeat requests report");finish(p);
        ladder.interact(p);int coins=p.coin,exp=p.exp;
        talk(p,0);finish(p);require(g.quests.stage()==Stage.COMPLETE,"report completes quest");
        require(p.coin==coins+20 && p.exp==exp+15,"turn-in grants advertised rewards");
        talk(p,0);finish(p);require(p.coin==coins+20 && p.exp==exp+15,"reward cannot repeat");
        well.interact(p);require(count(g,MON_GreenSlime.class)==0 && count(g,MON_SlimeBoss.class)==0,"completed dungeon stays cleared");
        g.quests.reset();require(!g.quests.inDungeon() && g.quests.stage()==Stage.NOT_STARTED,"session reset restores town");
        InputState merged=new InputState(),edge=new InputState();edge.escapePressed=true;merged.mergeOneShotFlags(edge);require(merged.escapePressed,"remote Escape is preserved");
        System.out.println("PASS: dialogue quest acceptance, ten-kill dungeon, collision, persistent reentry, King intro, client state, respawn, return and one-time reward.");
    }
}
