package main;

import entity.*;
import monster.*;
import object.*;
import world.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Captures reproducible, reachable scenes using the release JAR's actual game renderer. */
public final class CaptureScreenshots {
    private static Path output;
    private static final class CaptureGame extends Gamepanel {
        CaptureGame()throws Exception{super();}
        @Override public void playSE(int index){}
    }
    private static CaptureGame scene(String lead)throws Exception{
        CaptureGame g=new CaptureGame();g.setSize(g.screenWidth,g.screenHeight);
        SceneryLoader.load(g);g.grass.load();g.preloadGameplayAssets();g.assetSetter.setNPC();
        String[] roles=lead.equals("hunter")?new String[]{"hunter","warrior","mage"}:lead.equals("mage")?new String[]{"mage","hunter","warrior"}:new String[]{"warrior","hunter","mage"};
        for(int i=0;i<3;i++){
            Player p=i==0?g.localPlayer():new Player(g,g.keyH);p.playerId=i;p.isLocal=i==0;
            p.setCharacterDesign(roles[i],roles[i].equals("mage")?"female":"male");p.setDefaultValues();p.setItems();p.refreshWeaponAnimation();p.playerName=CharacterClass.fromId(roles[i]).label;
            p.worldX=(24+i)*g.tileSize;p.worldY=(27+(i==1?1:0))*g.tileSize;p.direction="up";g.players[i]=p;
        }
        g.tempScreen=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);g.g2=g.tempScreen.createGraphics();
        g.g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.gameState=g.playState;g.mouseH.mouseX=-50;g.mouseH.mouseY=-50;return g;
    }
    private static void ageLog(Gamepanel g){for(int i=0;i<700;i++)g.ui.update(-1,-1,false);}
    private static void capture(CaptureGame g,String name)throws Exception{
        ageLog(g);for(int i=0;i<8;i++)g.lighting.update();g.drawToTempscreen();
        ImageIO.write(g.tempScreen,"png",output.resolve(name+".png").toFile());System.out.println("Captured "+name);
    }
    private static void close(CaptureGame g){g.soundEffect.close();g.music.close();g.g2.dispose();}
    private static void acceptAndDescend(CaptureGame g){
        g.quests.onOldManFinished(g.localPlayer());for(Player p:g.players)if(p!=null)g.quests.enterWell(p);
    }
    private static void remove(CaptureGame g,int index)throws Exception{
        g.monster[index].alive=false;var method=Gamepanel.class.getDeclaredMethod("removeDefeatedMonster",int.class);method.setAccessible(true);method.invoke(g,index);g.quests.update();
    }
    private static void finishDialogs(Gamepanel g){
        for(Player p:g.players)if(p!=null){int guard=30;while(p.inDialogue && guard-->0)p.advanceOrCloseDialogue();}
        g.quests.update();
    }
    private static int bossIndex(Gamepanel g){for(int i=0;i<g.monster.length;i++)if(g.monster[i] instanceof MON_SlimeBoss)return i;throw new IllegalStateException("No boss");}
    private static void king(CaptureGame g)throws Exception{acceptAndDescend(g);for(int i=0;i<10;i++)remove(g,i);finishDialogs(g);}
    private static void partyForBoss(Gamepanel g){
        Player p=g.localPlayer();p.worldX=24*g.tileSize;p.worldY=24*g.tileSize;p.direction="up";
        g.players[1].worldX=22*g.tileSize;g.players[1].worldY=24*g.tileSize;g.players[1].direction="right";
        g.players[2].worldX=26*g.tileSize;g.players[2].worldY=24*g.tileSize;g.players[2].direction="up";
    }
    public static void main(String[] args)throws Exception{
        output=Path.of(args[0]);Files.createDirectories(output);System.setProperty("astral.packaged","true");
        CaptureGame town=scene("warrior");town.quests.onOldManFinished(town.localPlayer());town.localPlayer().resolve=2;town.localPlayer().rage=40;capture(town,"astravale-plaza");close(town);
        CaptureGame classes=scene("warrior");classes.gameState=classes.characterState;classes.keyH.characterCreationStep=1;classes.keyH.selectedClassIndex=2;classes.keyH.selectedGenderIndex=0;capture(classes,"character-creation");close(classes);
        CaptureGame well=scene("hunter");acceptAndDescend(well);for(int i=0;i<4;i++)remove(well,i);
        Player hunter=well.localPlayer();hunter.worldX=24*well.tileSize;hunter.worldY=21*well.tileSize;hunter.direction="right";hunter.rage=50;
        well.players[1].worldX=25*well.tileSize;well.players[1].worldY=22*well.tileSize;well.players[2].worldX=23*well.tileSize;well.players[2].worldY=22*well.tileSize;
        int[][] positions={{22,20},{25,21},{27,20},{24,23},{27,22},{22,23}};
        for(int i=4;i<10;i++){well.monster[i].worldX=positions[i-4][0]*well.tileSize;well.monster[i].worldY=positions[i-4][1]*well.tileSize;well.monster[i].direction="down";}
        well.monster[5].worldX=hunter.worldX+58;well.monster[5].worldY=hunter.worldY-2;
        hunter.attacking=true;hunter.animState="attack";hunter.spriteNum=5;hunter.attacking();well.monster[5].flashCounter=3;
        capture(well,"well-depths");close(well);
        CaptureGame royal=scene("warrior");king(royal);partyForBoss(royal);
        MON_SlimeBoss slime=(MON_SlimeBoss)royal.monster[bossIndex(royal)];slime.worldX=25*royal.tileSize;slime.worldY=22*royal.tileSize;slime.HP=37;slime.bossState="telegraph_slam";slime.bossStateTimer=32;
        royal.localPlayer().rage=45;royal.localPlayer().resolve=3;royal.localPlayer().skills[1].onUse.run();
        capture(royal,"slime-king");close(royal);
        CaptureGame inferno=scene("mage");king(inferno);remove(inferno,bossIndex(inferno));finishDialogs(inferno);
        Player mage=inferno.localPlayer();inferno.quests.leaveWell(mage);inferno.quests.onOldManFinished(mage);finishDialogs(inferno);inferno.quests.enterWell(mage);
        for(int i=0;i<inferno.obj.length;i++)if(ItemRegistry.typeFor(inferno.obj[i])==ItemType.SLIME_ESSENCE){mage.worldX=inferno.obj[i].worldX;mage.worldY=inferno.obj[i].worldY;mage.pickUpObject(i);break;}
        for(Player p:inferno.players)if(p!=null){
            ItemType reward=OBJ_rewardweapon.forClass(p.playerClass,false);
            for(int i=0;i<p.inventorySlots.length;i++)if(ItemRegistry.typeFor(p.inventorySlots[i])==reward){p.tryEquipFromBag(i,Player.EQUIP_WEAPON);break;}
            p.grantExperience(50);p.HP=p.maxHP;p.invincible=true;p.flashing=false;
        }
        inferno.quests.summonHell(mage);finishDialogs(inferno);partyForBoss(inferno);
        MON_HellSlime hell=(MON_HellSlime)inferno.monster[bossIndex(inferno)];hell.worldX=25*inferno.tileSize;hell.worldY=21*inferno.tileSize;hell.HP=420;
        hell.bossState="telegraph_spray";hell.bossStateTimer=39;
        for(int tick=0;tick<37;tick++){
            hell.update();for(Entity bolt:inferno.projectileList)if(bolt.alive)bolt.update();
        }
        mage.arcaneCharge=3;mage.direction="up";mage.attacking=true;mage.animState="attack";mage.spriteNum=4;
        capture(inferno,"hell-slime");close(inferno);
        CaptureGame shop=scene("warrior");king(shop);remove(shop,bossIndex(shop));finishDialogs(shop);shop.quests.leaveWell(shop.localPlayer());shop.quests.onOldManFinished(shop.localPlayer());finishDialogs(shop);Player customer=shop.localPlayer();customer.worldX=shop.npc[1].worldX-24;customer.worldY=shop.npc[1].worldY+48;customer.direction="up";customer.coin=64;
        customer.inventorySlots[1]=ItemRegistry.create(ItemType.POTION_MANA,shop);customer.inventorySlots[1].stackCount=3;
        customer.inventorySlots[2]=ItemRegistry.create(ItemType.POTION_GREATER,shop);customer.inventorySlots[3]=ItemRegistry.create(ItemType.ROYAL_SWORD,shop);customer.inventorySlots[4]=ItemRegistry.create(ItemType.HELL_SWORD,shop);
        shop.merchant.open(customer);var bounds=Merchant.class.getDeclaredMethod("sellBounds",int.class);bounds.setAccessible(true);Rectangle r=(Rectangle)bounds.invoke(shop.merchant,4);
        shop.mouseH.mouseX=r.x+28;shop.mouseH.mouseY=r.y+22;capture(shop,"merchant");close(shop);
    }
}
