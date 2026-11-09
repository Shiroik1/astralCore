import entity.*;
import main.*;
import monster.*;
import net.*;
import object.*;
import world.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.*;

public class NavigationCombatMerchantTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static class Walker extends MON_GreenSlime {
        Walker(Gamepanel gp){super(gp);aggroRange=40;}
        @Override public void setAction(){speed=2;chasePlayer();}
    }
    static class WideWalker extends MON_SlimeBoss {
        WideWalker(Gamepanel gp){super(gp);}
        @Override public void setAction(){speed=2;chasePlayer();}
    }
    static void walk(Gamepanel g,Monster m,int ticks){
        for(int i=0;i<ticks;i++){
            m.update();
            for(int row=(m.worldY+m.solidArea.y)/g.tileSize;row<=(m.worldY+m.solidArea.y+m.solidArea.height)/g.tileSize;row++)
                for(int col=(m.worldX+m.solidArea.x)/g.tileSize;col<=(m.worldX+m.solidArea.x+m.solidArea.width)/g.tileSize;col++)
                    require(!g.tileM.isSolid(col,row),"route never enters wall or pillar");
            if(Math.hypot(m.worldX-g.localPlayer().worldX,m.worldY-g.localPlayer().worldY)<95)return;
        }
        throw new AssertionError("enemy failed to route around obstacle: "+m.worldX+","+m.worldY);
    }
    static InventoryAction action(int player,InventoryActionType kind,int index){var a=new InventoryAction();a.playerId=player;a.actionType=kind;a.sourceIndex=index;return a;}
    static int items(Player p,ItemType type){return Arrays.stream(p.inventorySlots).filter(e->ItemRegistry.typeFor(e)==type).mapToInt(e->e.stackCount).sum();}
    static void projectile(Gamepanel g,Player p,int strength,double angle){
        p.invincible=false;p.knockbackActive=false;p.HP=p.maxHP;
        var king=new MON_SlimeBoss(g);var shot=new OBJ_slimebolt(g);shot.attack=strength;
        shot.setAngled(p.worldX,p.worldY,angle,true,king);shot.update();require(p.HP<p.maxHP,"projectiles damage "+p.playerClass+" despite shield/level defense");
    }
    public static void main(String[] args)throws Exception{
        var g=new AppearanceEquipmentTest.QuietGame();Player p=g.localPlayer();g.preloadGameplayAssets();
        g.quests.onOldManFinished(p);g.quests.enterWell(p);Arrays.fill(g.monster,null);p.invincible=true;
        p.worldX=24*g.tileSize;p.worldY=18*g.tileSize;
        var narrow=new Walker(g);narrow.worldX=18*g.tileSize;narrow.worldY=18*g.tileSize;walk(g,narrow,1200);
        var wide=new WideWalker(g);wide.worldX=18*g.tileSize;wide.worldY=18*g.tileSize;walk(g,wide,1500);
        g.worldObjects.add(new WorldObject(g,WorldObjectType.ROCK_LARGE,20,21,null));
        p.worldX=24*g.tileSize;p.worldY=21*g.tileSize;narrow=new Walker(g);narrow.worldX=18*g.tileSize;narrow.worldY=21*g.tileSize;walk(g,narrow,1200);
        // A changing target invalidates the reused route; searches remain bounded.
        p.worldX=24*g.tileSize;p.worldY=18*g.tileSize;narrow=new Walker(g);narrow.worldX=18*g.tileSize;narrow.worldY=18*g.tileSize;
        for(int i=0;i<90;i++)narrow.update();p.worldX=16*g.tileSize;p.worldY=25*g.tileSize;walk(g,narrow,1200);
        g.quests.reset();g.assetSetter.setNPC();Arrays.fill(g.obj,null);p.worldX=1000;p.worldY=1000;
        for(String id:new String[]{"warrior","hunter","mage"}){
            p.setCharacterDesign(id,"male");p.setDefaultValues();p.defense=100;
            projectile(g,p,2,Math.PI/4);require(p.maxHP-p.HP==1,"armor still leaves one damage minimum");
        }
        // An invulnerable party member must not shield another overlapping player from a droplet.
        p.invincible=true;Player other=new Player(g,g.keyH);other.playerId=1;other.isLocal=false;other.worldX=p.worldX;other.worldY=p.worldY;g.players[1]=other;
        var bolt=new OBJ_slimebolt(g);bolt.setAngled(p.worldX,p.worldY,Math.PI/4,true,new MON_SlimeBoss(g));int hp=other.HP;bolt.update();require(other.HP<hp,"invulnerable player does not swallow another player's hit");g.players[1]=null;
        p.setCharacterDesign("warrior","male");
        p.setDefaultValues();p.grantExperience(29);require(p.level==1,"no level in first 29 XP");p.grantExperience(1);require(p.level==2 && p.nextLevelExp==60,"new first threshold");
        p.setDefaultValues();p.grantExperience(30+20+15);require(p.level==3,"tutorial ends around level three");p.grantExperience(25);require(p.level==3,"one Hell kill does not grant another level");
        var hell=new MON_HellSlime(g);require(hell.maxHP>=300 && hell.attack==13 && hell.exp==25,"stronger Hell with reduced XP reward");
        p.setDefaultValues();p.grantExperience(1000);require(p.level==7,"bulk XP processes every earned level consistently");
        SceneryLoader.load(g);
        g.gameState=g.playState;p.setDefaultValues();p.setCharacterDesign("warrior","male");
        Entity merchant=g.npc[1];require("merchant".equals(merchant.npcId) && Math.abs(merchant.worldX-g.npc[0].worldX)<=g.tileSize*2,"merchant beside old man");
        p.resetForSceneTransition();p.worldX=merchant.worldX;p.worldY=merchant.worldY+g.tileSize-p.solidArea.y+1;
        p.direction="up";p.currentInput=new InputState();p.currentInput.ePressed=true;p.update();require(p.merchantOpen && !p.inDialogue,"E opens actual shop");
        int position=p.worldX;p.currentInput.right=true;p.update();require(p.worldX==position,"shop prevents movement and attacks");
        p.coin=30;int red=items(p,ItemType.POTION_RED);g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,0));
        require(p.coin==22 && items(p,ItemType.POTION_RED)==red+1,"purchase transfers item and deducts exact price");
        g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,1));require(p.coin==14 && items(p,ItemType.POTION_MANA)==1,"mana supply bought");
        int funds=p.coin;g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,2));require(p.coin==funds && items(p,ItemType.POTION_GREATER)==0,"insufficient coins cannot buy");
        Entity[] original=p.inventorySlots.clone();Arrays.fill(p.inventorySlots,new OBJ_key(g));p.coin=100;
        g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,0));require(p.coin==100,"full bag cannot lose coins");
        var potion=new OBJ_potion_red(g);potion.stackCount=4;p.inventorySlots[0]=potion;g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,0));require(potion.stackCount==5 && p.coin==92,"full bag permits compatible stacking");
        p.inventorySlots[0]=new OBJ_supply(g,ItemType.POTION_MANA);p.inventorySlots[0].stackCount=4;
        g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,2));require(items(p,ItemType.POTION_GREATER)==0 && p.coin==92,"different supplies never merge");
        p.inventorySlots[1]=ItemRegistry.create(ItemType.HELL_SWORD,g);g.applyInventoryActionLocally(action(0,InventoryActionType.SELL,1));require(p.coin==132 && p.inventorySlots[1]==null,"sell Hell weapon at shown price");
        g.applyInventoryActionLocally(action(0,InventoryActionType.SELL,1));require(p.coin==132,"empty slot cannot sell twice");
        p.inventorySlots[1]=new OBJ_slimeessence(g);g.applyInventoryActionLocally(action(0,InventoryActionType.SELL,1));require(p.coin==132 && p.inventorySlots[1]!=null,"essence cannot accidentally be sold");
        g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,-1));g.applyInventoryActionLocally(action(0,InventoryActionType.SELL,999));require(p.coin==132,"invalid item indices are rejected");
        p.worldX+=g.tileSize*5;g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,0));require(p.coin==132,"out-of-range trade rejected");p.worldX=merchant.worldX;
        g.keyH.playState(KeyEvent.VK_ESCAPE);require(!p.merchantOpen && !g.optionsOpen,"Escape closes shop without opening options");
        g.applyInventoryActionLocally(action(0,InventoryActionType.BUY,0));require(p.coin==132,"closed shop cannot trade");
        // Supplies restore their matching resource and survive registry round trips.
        p.HP=1;p.MP=0;p.maxMP=10;new OBJ_supply(g,ItemType.POTION_MANA).use(p);require(p.MP==8 && p.HP==1,"mana potion restores only mana");
        new OBJ_supply(g,ItemType.POTION_GREATER).use(p);require(p.HP==p.maxHP,"greater potion is capped at max HP");
        for(ItemType type:new ItemType[]{ItemType.POTION_MANA,ItemType.POTION_GREATER})require(ItemRegistry.typeFor(ItemRegistry.create(type,g))==type,"supply item identity");
        // Exercise the menu's actual purchase button and produce a reviewable preview.
        System.arraycopy(original,0,p.inventorySlots,0,original.length);p.coin=100;g.merchant.open(p);
        var bounds=Merchant.class.getDeclaredMethod("buyBounds",int.class);bounds.setAccessible(true);Rectangle button=(Rectangle)bounds.invoke(g.merchant,0);
        int x=button.x+20,y=button.y+20;
        int before=items(p,ItemType.POTION_RED);g.ui.update(x,y,true);require(items(p,ItemType.POTION_RED)==before+1,"shop UI button purchases");
        var image=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();g.ui.draw(graphics);graphics.dispose();new File("/tmp/astral-merchant").mkdirs();ImageIO.write(image,"png",new File("/tmp/astral-merchant/shop.png"));
        var client=new AppearanceEquipmentTest.QuietGame();client.isNetworked=true;client.isHost=false;SceneryLoader.load(client);client.assetSetter.setNPC();var apply=GameClient.class.getDeclaredMethod("applySnapshot",WorldSnapshot.class);apply.setAccessible(true);apply.invoke(new GameClient(client),SnapshotBuilder.build(g));
        require(client.localPlayer().merchantOpen && client.localPlayer().coin==p.coin && items(client.localPlayer(),ItemType.POTION_RED)==items(p,ItemType.POTION_RED),"shop money and supplies synchronize to clients");
        System.out.println("PASS: narrow/wide enemy routes, scenery and moving targets, armored projectile damage, party collision, slower XP, Hell tuning, merchant interaction/UI, atomic buys and sales, full bags, supply identity/use, and client balances.");
    }
}
