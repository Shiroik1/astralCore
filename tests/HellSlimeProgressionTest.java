import entity.*;
import main.*;
import monster.*;
import net.*;
import object.*;
import quest.QuestManager.Stage;
import world.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public class HellSlimeProgressionTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static int bag(Player p,ItemType type){for(int i=0;i<p.inventorySlots.length;i++)if(ItemRegistry.typeFor(p.inventorySlots[i])==type)return i;return -1;}
    static int ground(Gamepanel g,ItemType type){for(int i=0;i<g.obj.length;i++)if(ItemRegistry.typeFor(g.obj[i])==type)return i;return -1;}
    static void finishAll(Gamepanel g){for(Player p:g.players)if(p!=null)WellQuestTest.finish(p);g.quests.update();}
    static int boss(Gamepanel g){for(int i=0;i<g.monster.length;i++)if(g.monster[i]!=null && g.monster[i].isBoss())return i;return -1;}
    static void image(Gamepanel g,String name)throws Exception{
        BufferedImage image=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        var field=Gamepanel.class.getDeclaredField("g2");field.setAccessible(true);field.set(g,graphics);g.gameState=g.playState;g.drawToTempscreen();graphics.dispose();
        File dir=new File("/tmp/astral-hell");dir.mkdirs();ImageIO.write(image,"png",new File(dir,name+".png"));
    }
    public static void main(String[] args)throws Exception{
        var g=new AppearanceEquipmentTest.QuietGame();g.isHost=true;g.assetSetter.setNPC();g.preloadGameplayAssets();SceneryLoader.load(g);
        Player warrior=g.localPlayer();warrior.setCharacterDesign("warrior","male");
        Player hunter=new Player(g,g.keyH);hunter.playerId=1;hunter.isLocal=false;hunter.setCharacterDesign("hunter","female");g.players[1]=hunter;
        Player mage=new Player(g,g.keyH);mage.playerId=2;mage.isLocal=false;mage.setCharacterDesign("mage","female");g.players[2]=mage;
        require(g.npc[0].dialogueLines.size()==4,"short four-page old man introduction");
        g.assetSetter.setMonster();require(Arrays.stream(g.monster).allMatch(Objects::isNull),"empty overworld");
        g.quests.onOldManFinished(warrior);g.quests.enterWell(warrior);
        WorldObject altar=g.worldObjects.stream().filter(o->o.kind==WorldObjectType.WELL_ALTAR).findFirst().orElseThrow();
        altar.interact(warrior);require(boss(g)<0,"altar locked before tutorial complete");WellQuestTest.finish(warrior);
        for(int i=0;i<10;i++)WellQuestTest.kill(g,i);finishAll(g);
        MON_SlimeBoss king=(MON_SlimeBoss)g.monster[boss(g)];
        require(king.down1.getWidth()>144 && king.slamRadius()>120 && king.maxHP>0 && king.attack>8,"larger harder King");
        WellQuestTest.kill(g,boss(g));finishAll(g);
        require(ground(g,ItemType.SLIME_ESSENCE)>=0,"King drops essence");
        warrior.pickUpObject(ground(g,ItemType.SLIME_ESSENCE));require(bag(warrior,ItemType.SLIME_ESSENCE)>=0,"essence picked up into bag");
        g.quests.leaveWell(warrior);
        // A full inventory must keep a weapon pending without repeating coins or XP.
        Entity[] bagBefore=mage.inventorySlots.clone();Arrays.fill(mage.inventorySlots,new OBJ_key(g));
        g.quests.onOldManFinished(warrior);finishAll(g);
        require(bag(warrior,ItemType.ROYAL_SWORD)>=0 && bag(hunter,ItemType.ROYAL_DAGGER)>=0,"class-specific melee quest rewards");
        require(bag(mage,ItemType.ROYAL_STAFF)<0 && g.quests.snapshot().pendingRewards[2]==ItemType.ROYAL_STAFF,"full bag preserves pending reward");
        int coins=mage.coin,xp=mage.exp;mage.inventorySlots[0]=null;g.quests.update();
        require(bag(mage,ItemType.ROYAL_STAFF)==0 && mage.coin==coins && mage.exp==xp,"free slot delivers weapon once");
        finishAll(g);g.quests.onOldManFinished(warrior);require(mage.coin==coins,"no repeat quest payout");
        // Equip all rewards; weapon art and spellcasting use the existing equipment flow.
        for(Player p:g.players)if(p!=null){
            ItemType reward=OBJ_rewardweapon.forClass(p.playerClass,false);int slot=bag(p,reward);int before=AppearanceEquipmentTest.pixels(p.down1);
            p.tryEquipFromBag(slot,Player.EQUIP_WEAPON);
            require(p.currentWeapon.attackValue==4 && before!=AppearanceEquipmentTest.pixels(p.down1),"powerful reward changes held artwork");
        }
        require(mage.canCastFireball(),"Royal Staff casts spells");
        g.quests.enterWell(warrior);finishAll(g);
        altar.interact(hunter);require(boss(g)<0,"no essence cannot summon");WellQuestTest.finish(hunter);
        // Fill the ground loot slots to ensure the essence and class drops cannot disappear.
        Arrays.fill(g.obj,new OBJ_key(g));
        altar.interact(warrior);
        require(g.quests.stage()==Stage.HELL_INTRO && bag(warrior,ItemType.SLIME_ESSENCE)<0,"successful summon consumes exactly one essence");
        MON_HellSlime hell=(MON_HellSlime)g.monster[boss(g)];
        require(hell.maxHP>king.maxHP && hell.attack>king.attack && !hell.canBeKnockedBack(),"stronger immune Hell boss");
        require(hered(hell)>hered(king),"Hell art is red");
        g.applyGameEventLocally(warrior.playerId,GameEventType.PICKUP,null,"A pending weapon was delivered.");
        require(warrior.dialogueTag.equals(quest.QuestManager.INTRO_TAG),"item notifications cannot replace the encounter intro");
        int version=g.quests.snapshot().encounterVersion;
        altar.interact(warrior);require(g.quests.snapshot().encounterVersion==version,"active encounter cannot duplicate");
        // Do not let an invalid altar interaction replace a participant's intro and block the party.
        finishAll(g);require(g.quests.stage()==Stage.HELL,"party intro releases Hell fight");
        // Forced attack states drive the same live state machine and validate timing.
        g.projectileList.clear();hell.bossState="telegraph_spray";hell.bossStateTimer=39;hell.update();
        require(g.projectileList.size()==16 && hell.bossState.equals("burst"),"radial wave one");
        for(int i=0;i<17;i++)hell.update();require(g.projectileList.size()==16,"waves separated by recovery ticks");
        hell.update();require(g.projectileList.size()==32,"radial wave two");
        for(int i=0;i<18;i++)hell.update();require(g.projectileList.size()==48 && hell.bossState.equals("cooldown"),"exactly three radial waves");
        var directions=new HashSet<String>();for(Entity bolt:g.projectileList){require(((OBJ_slimebolt)bolt).hell,"red droplets");directions.add(bolt.direction);}
        require(directions.size()==4,"radial shots cover all directions");
        // Snapshots carry the red boss, red projectiles, attack cue and custom equipment.
        var endpoint=new com.esotericsoftware.kryonet.Client();NetworkRegistration.register(endpoint);var out=new com.esotericsoftware.kryo.io.Output(16384,-1);
        endpoint.getKryo().writeObject(out,SnapshotBuilder.build(g));WorldSnapshot decoded=endpoint.getKryo().readObject(new com.esotericsoftware.kryo.io.Input(out.toBytes()),WorldSnapshot.class);
        var client=new AppearanceEquipmentTest.QuietGame();client.isNetworked=true;client.isHost=false;var networkClient=new GameClient(client);
        var apply=GameClient.class.getDeclaredMethod("applySnapshot",WorldSnapshot.class);apply.setAccessible(true);apply.invoke(networkClient,decoded);
        require(client.monster[boss(g)] instanceof MON_HellSlime && client.projectileList.size()==48,"red boss and all volleys synchronize");
        require(ItemRegistry.typeFor(client.localPlayer().currentWeapon)==ItemType.ROYAL_SWORD,"reward equipment synchronizes");
        int health=client.localPlayer().HP;for(Entity bolt:client.projectileList)bolt.update();require(client.localPlayer().HP==health,"replica projectiles cannot damage players");
        g.projectileList.clear();hell.bossState="telegraph_charge";hell.bossStateTimer=0;hell.syncCharge(1,0);hell.worldX=24*g.tileSize;hell.worldY=15*g.tileSize;
        int x=hell.worldX;for(int i=0;i<42;i++)hell.update();require(hell.worldX==x && hell.bossState.equals("charge"),"charge has a stationary warning");
        hell.update();require(hell.worldX>x,"Hell performs forward dash");
        for(int i=0;i<24;i++)hell.update();require(!g.tileM.isSolid((hell.worldX+hell.solidArea.width)/g.tileSize,(hell.worldY+hell.solidArea.y)/g.tileSize),"dash respects dungeon walls");
        warrior.worldX=hell.worldX-180;warrior.worldY=hell.worldY+130;hell.bossState="telegraph_slam";hell.bossStateTimer=32;image(g,"hell-slam");
        hell.bossState="telegraph_charge";hell.bossStateTimer=20;image(g,"hell-charge");
        // Free exactly four slots. Important drops remain queued until space opens.
        WellQuestTest.kill(g,boss(g));require(g.quests.stage()==Stage.COMPLETE,"Hell completion permits a repeat summon");
        for(int i=0;i<4;i++)g.obj[i]=null;g.quests.update();
        require(ground(g,ItemType.SLIME_ESSENCE)>=0 && ground(g,ItemType.HELL_SWORD)>=0 && ground(g,ItemType.HELL_DAGGER)>=0 && ground(g,ItemType.HELL_STAFF)>=0,"guaranteed essence and class Hell loot despite full ground slots");
        // A full bag must leave dropped essence on the floor.
        Entity[] saved=warrior.inventorySlots.clone();Arrays.fill(warrior.inventorySlots,new OBJ_key(g));int essenceSlot=ground(g,ItemType.SLIME_ESSENCE);Entity essence=g.obj[essenceSlot];
        warrior.pickUpObject(essenceSlot);require(g.obj[essenceSlot]==essence,"failed pickup never deletes essence");
        System.arraycopy(saved,0,warrior.inventorySlots,0,saved.length);warrior.pickUpObject(essenceSlot);
        int hellWeaponSlot=ground(g,ItemType.HELL_SWORD);warrior.pickUpObject(hellWeaponSlot);int weaponSlot=bag(warrior,ItemType.HELL_SWORD);
        warrior.tryEquipFromBag(weaponSlot,Player.EQUIP_WEAPON);require(warrior.currentWeapon.attackValue>4,"Hell weapon upgrades Royal weapon");
        warrior.inventorySlots[bag(warrior,ItemType.SLIME_ESSENCE)].stackCount=2;
        altar.interact(warrior);require(warrior.inventorySlots[bag(warrior,ItemType.SLIME_ESSENCE)].stackCount==1,"stacked essence consumes one");
        require(g.quests.stage()==Stage.HELL_INTRO && warrior.inDialogue && g.quests.snapshot().encounterVersion>version,"same-room repeat summon has a fresh intro and consumes returned essence");
        finishAll(g);Monster active=g.monster[boss(g)];g.quests.leaveWell(warrior);g.quests.enterWell(warrior);
        require(g.monster[boss(g)]==active,"leaving and reentering resumes active fight without another essence");
        require(ItemRegistry.typeFor(new OBJ_slimeessence(g))==ItemType.SLIME_ESSENCE,"essence registry identity");
        System.out.println("PASS: no overworld enemies, short introduction, class reward delivery/equipment, guaranteed essence, repeat summons, red boss, three radial waves, charge collision, stronger Hell loot, full bags, and serialized multiplayer encounters.");
    }
    static int hered(MON_SlimeBoss boss){
        int total=0;BufferedImage image=boss.down1;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int rgb=image.getRGB(x,y);if((rgb>>>24)>150 && ((rgb>>>16)&255)>((rgb>>>8)&255)+20)total++;
        }return total;
    }
}
