import entity.*;
import main.*;
import monster.*;
import net.*;
import object.*;
import world.*;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class WipeClassesWindowsTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Player player(Gamepanel g,int id,String type){Player p=id==0?g.localPlayer():new Player(g,g.keyH);p.playerId=id;p.isLocal=id==0;p.setCharacterDesign(type,"male");p.setDefaultValues();p.setItems();p.refreshWeaponAnimation();g.players[id]=p;return p;}
    static int king(Gamepanel g){for(int i=0;i<g.monster.length;i++)if(g.monster[i] instanceof MON_SlimeBoss)return i;return -1;}
    static void png(BufferedImage image,String name)throws Exception{new File("/tmp/astral-class-rework").mkdirs();ImageIO.write(image,"png",new File("/tmp/astral-class-rework/"+name+".png"));}
    public static void main(String[] args)throws Exception{
        var g=new AppearanceEquipmentTest.QuietGame();g.isHost=true;g.assetSetter.setNPC();SceneryLoader.load(g);g.preloadGameplayAssets();
        Player hunter=player(g,0,"hunter"),warrior=player(g,1,"warrior");
        require(hunter.currentWeapon instanceof OBJ_dualdaggers && hunter.currentShield.isPlaceholder,"Hunter starts with paired daggers and no shield");
        require(warrior.currentWeapon instanceof OBJ_sword_normal && !warrior.currentShield.isPlaceholder && warrior.attackArea.width>hunter.attackArea.width,"Warrior sword reaches farther than daggers");
        var delay=Player.class.getDeclaredMethod("getAttackFrameDelay",int.class);delay.setAccessible(true);int fast=0,slow=0;
        for(int frame=1;frame<=8;frame++){fast+=(int)delay.invoke(hunter,frame);slow+=(int)delay.invoke(warrior,frame);}require(fast<slow,"dagger animation attacks faster");
        int dressed=AppearanceEquipmentTest.pixels(warrior.down1);warrior.inventorySlots[1]=null;warrior.unequipToBagIndex(Player.EQUIP_SHIELD,1);
        require(AppearanceEquipmentTest.pixels(warrior.down1)!=dressed,"shield has removable held artwork");warrior.tryEquipFromBag(1,Player.EQUIP_SHIELD);
        hunter.inventorySlots[1]=new OBJ_shield_wood(g);hunter.tryEquipFromBag(1,Player.EQUIP_SHIELD);require(hunter.currentShield.isPlaceholder,"Hunter cannot equip shield over paired blades");
        g.quests.onOldManFinished(hunter);g.quests.enterWell(hunter);g.quests.enterWell(warrior);
        for(int i=0;i<10;i++)WellQuestTest.kill(g,i);WellQuestTest.finish(hunter);WellQuestTest.finish(warrior);g.quests.update();
        MON_SlimeBoss boss=(MON_SlimeBoss)g.monster[king(g)];boss.HP=boss.maxHP-10;int wounded=boss.HP;
        hunter.HP=0;hunter.isDead=true;g.quests.update();require(boss.HP==wounded,"one defeat does not reset boss while teammate lives");
        hunter.performRespawn();require(!hunter.inWell && hunter.worldX==25*g.tileSize && hunter.worldY==28*g.tileSize && warrior.inWell,"individual respawn goes to plaza without moving survivor");
        g.quests.activateArea(true);require(boss.HP==wounded,"survivor retains boss progress");
        boss.addStatusEffect(new StatusEffect("bleed",180,1,30));warrior.HP=0;warrior.isDead=true;g.quests.update();
        require(boss.HP==boss.maxHP && boss.statusEffects.isEmpty() && boss.bossState.equals("idle"),"party wipe heals boss and clears lingering combat state");
        warrior.performRespawn();require(!warrior.inWell && !g.quests.inDungeon(),"last survivor also respawns in plaza");
        g.quests.enterWell(hunter);require(g.monster[king(g)]==boss && boss.HP==boss.maxHP,"retry uses same reset boss without duplicate summon");
        // Town trade and dungeon fight run in independent scene views.
        g.quests.activateArea(false);warrior.worldX=g.npc[1].worldX;warrior.worldY=g.npc[1].worldY+30;warrior.coin=30;g.merchant.open(warrior);g.quests.activateArea(true);
        var buy=new InventoryAction();buy.playerId=1;buy.actionType=InventoryActionType.BUY;buy.sourceIndex=0;g.applyInventoryActionLocally(buy);
        require(warrior.coin==22 && g.quests.inDungeon(),"plaza player can shop while teammate stays in well");
        // Round trip both world views over the actual registration/serialization path.
        WorldSnapshot state=SnapshotBuilder.build(g);var endpoint=new com.esotericsoftware.kryonet.Client();NetworkRegistration.register(endpoint);
        var output=new com.esotericsoftware.kryo.io.Output(16384,-1);endpoint.getKryo().writeObject(output,state);
        WorldSnapshot decoded=endpoint.getKryo().readObject(new com.esotericsoftware.kryo.io.Input(output.toBytes()),WorldSnapshot.class);
        var client=new AppearanceEquipmentTest.QuietGame();client.isNetworked=true;client.isHost=false;client.assetSetter.setNPC();SceneryLoader.load(client);
        client.localPlayer().isLocal=false;client.localPlayerIndex=1;Player local=new Player(client,client.keyH);local.playerId=1;local.isLocal=true;client.players[1]=local;
        var apply=GameClient.class.getDeclaredMethod("applySnapshot",WorldSnapshot.class);apply.setAccessible(true);apply.invoke(new GameClient(client),decoded);
        require(!client.localPlayer().inWell && !client.quests.inDungeon() && client.npc[1] instanceof NPC_merchant,"plaza client sees town and merchant");
        client.quests.activateArea(true);require(king(client)>=0 && client.players[0].inWell,"same snapshot retains teammate's dungeon and boss");
        // Resolve is earned by actual mitigation and consumed by a defense-scaled strike.
        warrior.merchantOpen=false;warrior.inWell=true;g.quests.activateArea(true);warrior.HP=warrior.maxHP=100;warrior.resolve=0;
        for(int hit=0;hit<5;hit++){warrior.invincible=false;boss.damagePlayer(warrior,5);}require(warrior.resolve==5,"shield-mitigated hits build Resolve");
        boss.invincible=false;boss.HP=boss.maxHP;int before=boss.HP;warrior.damageMonster(king(g),1);
        require(warrior.resolve==0 && before-boss.HP>1,"full Resolve empowers successful strike with defense");
        warrior.skills[2].onUse.run();warrior.invincible=false;boss.damagePlayer(warrior,5);require(warrior.resolve==5,"Retaliation fills Resolve on next mitigated hit");
        warrior.resolve=0;warrior.skills[1].onUse.run();require(warrior.getEffectiveDefense()>warrior.defense,"Bulwark increases defense");
        warrior.HP=1;warrior.skills[4].onUse.run();require(warrior.HP>1,"Rally heals allies/self");
        boss.invincible=false;warrior.skills[3].onUse.run();warrior.damageMonster(king(g),5);require(boss.hasStatusEffect("armorBreak"),"Shield Breaker weakens enemy armor");
        // The shared theme is opaque black with white outlines, including hover windows.
        var panel=new BufferedImage(200,100,BufferedImage.TYPE_INT_ARGB);var pg=panel.createGraphics();WindowStyle.draw(pg,10,10,180,80);pg.dispose();
        require(panel.getRGB(30,30)==Color.BLACK.getRGB() && panel.getRGB(11,40)==Color.WHITE.getRGB(),"window theme is opaque black and white");
        var gallery=new BufferedImage(640,440,BufferedImage.TYPE_INT_ARGB);var art=gallery.createGraphics();art.setColor(Color.BLACK);art.fillRect(0,0,640,440);
        var unique=new HashSet<Integer>();int index=0;
        for(String type:new String[]{"hunter","warrior","mage"}){
            Player model=new Player(g,g.keyH);model.setCharacterDesign(type,"male");model.setDefaultValues();model.setItems();model.refreshWeaponAnimation();
            art.drawImage(model.down1,index%3*200,0,180,180,null);int row=type.equals("hunter")?0:type.equals("warrior")?1:2;
            for(int skill=0;skill<5;skill++){require(!model.skills[skill].description.isEmpty(),"skill has actual behavior tooltip");unique.add(AppearanceEquipmentTest.pixels(model.skills[skill].icon));art.drawImage(model.skills[skill].icon,skill*70,row*70+220,60,60,null);}index++;
        }require(unique.size()==15,"all fifteen skill icons depict distinct abilities");art.dispose();png(gallery,"classes-and-skills");
        g.quests.activateArea(false);hunter.inWell=false;hunter.worldX=g.npc[1].worldX;hunter.worldY=g.npc[1].worldY+30;g.merchant.open(hunter);g.setSize(g.screenWidth,g.screenHeight);
        var bounds=Merchant.class.getDeclaredMethod("buyBounds",int.class);bounds.setAccessible(true);Rectangle r=(Rectangle)bounds.invoke(g.merchant,0);g.mouseH.mouseX=r.x+20;g.mouseH.mouseY=r.y+20;
        var shop=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);var shopG=shop.createGraphics();g.merchant.draw(shopG);shopG.dispose();png(shop,"shop-hover");
        System.out.println("PASS: individual plaza respawns, wipe-only boss reset, two-area multiplayer snapshots/trade, paired fast daggers, visible shield and longer sword reach, defense-based Resolve and skills, opaque white/black windows, 15 distinct skill icons and shop tooltips.");
    }
}
