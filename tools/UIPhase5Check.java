package main;
import entity.*;import object.*;import crafting.*;import save.*;import net.*;import ui.*;
import java.util.*;import java.awt.*;import java.awt.event.*;import java.awt.image.*;import java.nio.file.*;import java.lang.reflect.*;
public class UIPhase5Check {
 static int checks; static Gamepanel g;static Player p;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static int slot(ItemType t){for(int i=0;i<20;i++)if(ItemRegistry.typeFor(p.inventorySlots[i])==t)return i;throw new AssertionError("missing "+t);}
 static void tick(){g.ui.update(g.mouseH.getScaledX(),g.mouseH.getScaledY(),g.mouseH.leftClicked);}
 static BufferedImage render()throws Exception{BufferedImage img=new BufferedImage(1248,720,2);Graphics2D d=img.createGraphics();d.setColor(new Color(0x78936d));d.fillRect(0,0,1248,720);g.ui.draw(d);d.dispose();return img;}
 static void settle()throws Exception{for(int i=0;i<12;i++)tick();render();}
 static void mouse(int x,int y,boolean down)throws Exception{g.mouseH.mouseX=x;g.mouseH.mouseY=y;g.mouseH.leftPressed=down;g.mouseH.leftClicked=down;tick();g.mouseH.leftClicked=false;}
 static void click(Rectangle r)throws Exception{mouse((int)r.getCenterX(),(int)r.getCenterY(),true);mouse((int)r.getCenterX(),(int)r.getCenterY(),false);}
 static void drag(Rectangle a,Rectangle b)throws Exception{mouse((int)a.getCenterX(),(int)a.getCenterY(),true);mouse((int)b.getCenterX(),(int)b.getCenterY(),false);}
 public static void main(String[] args)throws Exception{
  System.setProperty("astral.dataDir",Files.createTempDirectory("astral-ui-check-data-").toString());g=new Gamepanel();g.gameState=g.playState;g.setSize(1248,720);p=g.localPlayer();p.playerName="Phase5";
  check(Arrays.stream(p.inventorySlots).allMatch(Objects::isNull)&&p.currentWeapon.isPlaceholder,"empty new character");
  g.gameState=g.characterState;g.keyH.characterNameInput.append("Classless");g.ui.confirmCharacterName();check(g.keyH.characterCreationStep==2,"name skips class choice");g.ui.customization.key(KeyEvent.VK_ESCAPE);check(g.keyH.characterCreationStep==0,"appearance back returns to name");g.keyH.enteringCharacterName=false;g.gameState=g.playState;
  InventoryTransaction.commit(g,p,Map.of(),Map.of(ItemType.TWIG,3,ItemType.STONE,2));var axe=RecipeRegistry.RECIPES.getFirst();check(g.crafting.craft(axe,1),"starter axe craft");check(!g.crafting.craft(axe,1),"rapid repeat cannot duplicate axe without resources");check(p.craftingProgress.level==2,"existing first bonus unlocks crafting2");
  InventoryTransaction.commit(g,p,Map.of(),Map.of(ItemType.WOODEN_SWORD,1,ItemType.WOODEN_STAFF,1,ItemType.WOODEN_BOW,1,ItemType.WOODEN_WALL,5,ItemType.POTION_RED,2,ItemType.WORKBENCH,1));
  p.meleeMasteryXP=3;p.magicMasteryXP=4;p.bowMasteryXP=5;int str=p.strength,intel=p.intelligence,hp=p.maxHP;
  for(ItemType t:java.util.List.of(ItemType.WOODEN_SWORD,ItemType.WOODEN_STAFF,ItemType.WOODEN_BOW,ItemType.WOODEN_AXE)){p.tryEquipFromBag(slot(t),Player.EQUIP_WEAPON);check(ItemRegistry.typeFor(p.currentWeapon)==t,"equip any basic weapon "+t);check(p.strength==str&&p.intelligence==intel&&p.maxHP==hp,"switch preserves attributes");}
  p.maxRage=0;p.syncWeaponProfile();check(p.maxRage==100,"legacy caster switching to melee retains existing Rage capacity");
  check(p.meleeMasteryXP==3&&p.magicMasteryXP==4&&p.bowMasteryXP==5,"weapon switch preserves all mastery XP");
  p.hotbar.assign(p,0,ItemType.WOODEN_AXE);p.hotbar.assign(p,1,ItemType.WOODEN_STAFF);p.hotbar.assign(p,2,ItemType.WOODEN_BOW);p.hotbar.assign(p,3,ItemType.POTION_RED);p.hotbar.assign(p,4,ItemType.WOODEN_WALL);p.hotbar.assign(p,9,ItemType.WORKBENCH);
  g.keyH.playState(KeyEvent.VK_2);check(p.isMage()&&ItemRegistry.typeFor(p.currentWeapon)==ItemType.WOODEN_STAFF,"hotbar equips staff and selects magic kit");p.skills[0].cooldownRemaining=120;g.keyH.playState(KeyEvent.VK_3);check(p.isRanger(),"hotbar bow profile");g.keyH.playState(KeyEvent.VK_2);check(p.skills[0].cooldownRemaining==120,"weapon swap cannot reset ability cooldown");
  g.keyH.playState(KeyEvent.VK_0);check(p.hotbar.selected==9&&g.crafting.placingObject(),"0 selects tenth and existing station placement");g.keyH.playState(KeyEvent.VK_1);check(!g.crafting.placingObject(),"switch away cancels station placement");
  int from=slot(ItemType.WOODEN_WALL);p.swapOrMergeBagSlots(from,19);check(p.hotbar.count(p,4)==5,"shortcut survives moving bag stack");InventoryTransaction.commit(g,p,Map.of(ItemType.WOODEN_WALL,2),Map.of());check(p.hotbar.count(p,4)==3,"shortcut updates stack consumption");
  p.HP=Math.max(1,p.maxHP-20);g.keyH.playState(KeyEvent.VK_4);InputState in=new InputState();in.leftClicked=true;in.leftHeld=true;p.hotbar.useSelected(p,in);check(p.hotbar.count(p,3)==1&&!in.leftClicked&&!in.leftHeld,"consume existing potion once and suppress attack");
  settle();g.keyH.playState(KeyEvent.VK_E);settle();check(g.ui.game.window()==GameUI.Window.INVENTORY&&g.inventoryOpen,"E opens combined inventory");
  Rectangle[] bag=(Rectangle[])field(g.ui.game,"bag"),bar=(Rectangle[])field(g.ui.game,"hotbar");int sword=slot(ItemType.WOODEN_SWORD);drag(bag[sword],bar[5]);check(p.hotbar.type(5)==ItemType.WOODEN_SWORD&&p.inventorySlots[sword]!=null,"drag assigns reference without duplication");drag(bar[5],bar[6]);check(p.hotbar.type(6)==ItemType.WOODEN_SWORD&&p.hotbar.type(5)==null,"drag swaps shortcuts");drag(bar[6],new Rectangle(10,300,10,10));check(p.hotbar.type(6)==null&&p.inventorySlots[sword]!=null,"remove shortcut preserves owned item");
  Rectangle[] filters=(Rectangle[])field(g.ui.game,"filters");click(filters[2]);render();check((int)field(g.ui.game,"filter")==2,"locked recipe filter");for(int idx:((Map<Integer,Rectangle>)field(g.ui.game,"recipeRows")).keySet())check(RecipeRegistry.RECIPES.get(idx).level()>p.craftingProgress.level,"locked filter uses real crafting levels");
  g.keyH.playState(KeyEvent.VK_C);settle();check(g.ui.game.window()==GameUI.Window.CHARACTER&&!g.crafting.open,"C switches exclusive windows");Rectangle[] tabs=(Rectangle[])field(g.ui.game,"tabs");click(tabs[1]);render();check((int)field(g.ui.game,"tab")==1,"equipment tab");bag=(Rectangle[])field(g.ui.game,"bag");Rectangle[] equip=(Rectangle[])field(g.ui.game,"equipment");drag(bag[slot(ItemType.WOODEN_SWORD)],equip[0]);check(ItemRegistry.typeFor(p.currentWeapon)==ItemType.WOODEN_SWORD,"equipment UI delegates equip transaction");
  int free=-1;for(int i=0;i<20;i++)if(p.inventorySlots[i]==null){free=i;break;}check(free>=0,"free slot for unequip");render();drag(equip[0],bag[free]);check(p.currentWeapon.isPlaceholder&&ItemRegistry.typeFor(p.inventorySlots[free])==ItemType.WOODEN_SWORD,"equipment UI safely unequips");
  click(tabs[2]);render();check((int)field(g.ui.game,"tab")==2,"skills tab");
  g.keyH.playState(KeyEvent.VK_ESCAPE);check(g.ui.game.blocksWorld(),"closing fade captures world input");settle();check(!g.ui.game.blocksWorld(),"world input releases after fade");int oldSelection=p.hotbar.selected;g.mouseH.handleWheel(1);check(p.hotbar.selected==(oldSelection+1)%10,"wheel cycles actual ten-slot selection");
  // Real hit resolution awards each mastery once, including a delayed projectile after a weapon swap.
  for(ItemType t:java.util.List.of(ItemType.WOODEN_SWORD,ItemType.WOODEN_STAFF,ItemType.WOODEN_BOW)){
   p.tryEquipFromBag(slot(t),Player.EQUIP_WEAPON);g.monster[0]=new monster.MON_GreenSlime(g);g.monster[0].maxHP=g.monster[0].HP=1000;
   int before=t==ItemType.WOODEN_SWORD?p.meleeMasteryXP:t==ItemType.WOODEN_STAFF?p.magicMasteryXP:p.bowMasteryXP;
   CombatHit hit=new CombatHit("phase5-check").mastery(p.currentWeapon.type);check(p.damageMonsterOnce(0,1,false,"down",hit,0),"existing damage pipeline accepts "+t);check(!p.damageMonsterOnce(0,1,false,"down",hit,0),"same event cannot award duplicate XP");
   int after=t==ItemType.WOODEN_SWORD?p.meleeMasteryXP:t==ItemType.WOODEN_STAFF?p.magicMasteryXP:p.bowMasteryXP;check(after==before+1,"correct independent mastery "+t);
  }
  int magicBefore=p.magicMasteryXP;g.monster[0].invincible=false;p.damageMonsterOnce(0,1,false,"down",new CombatHit("delayed-staff").mastery(Entity.type_staff),0);check(p.magicMasteryXP==magicBefore+1,"in-flight staff hit retains Magic XP after bow equip");
  p.meleeMasteryXP=3;p.magicMasteryXP=4;p.bowMasteryXP=5;
  // Serialized state includes references and applies after the authoritative bag and equipment.
  PlayerState state=CharacterState.capture(g,p);state.hotbarTypes[9]=null;CharacterState.apply(g,p,state);check(p.hotbar.type(4)==ItemType.WOODEN_WALL&&p.hotbar.count(p,4)==3,"save-state hotbar restore");check(p.meleeMasteryXP==3&&p.magicMasteryXP==4&&p.bowMasteryXP==5,"save-state mastery restore");
  for(String legacy:new String[]{"warrior","hunter","mage","ranger","priest","berserker","paladin"}){state.playerClass=legacy;state.assassinKit=true;state.mageKit=true;CharacterState.apply(g,p,state);check(p.strength==state.strength&&p.intelligence==state.intelligence&&p.meleeMasteryXP==state.meleeMasteryXP,"legacy profile preserves saved values "+legacy);}
  state.hotbarTypes=null;CharacterState.apply(g,p,state);check(p.hotbar.type(4)==null,"legacy save missing hotbar accepted");
  SaveStore disk=new SaveStore(g,Files.createTempDirectory("astral-phase5-"));disk.create(0,"Phase5",0,0);p=g.localPlayer();InventoryTransaction.commit(g,p,Map.of(),Map.of(ItemType.WOODEN_STAFF,1,ItemType.WOODEN_WALL,7));p.hotbar.assign(p,0,ItemType.WOODEN_STAFF);p.hotbar.assign(p,4,ItemType.WOODEN_WALL);p.hotbar.select(p,0);p.magicMasteryXP=27;check(disk.saveActive(),"disk save");disk.load(0);p=g.localPlayer();check(p.hotbar.type(4)==ItemType.WOODEN_WALL&&p.hotbar.count(p,4)==7&&p.isMage()&&p.magicMasteryXP==27,"disk save/load retains equipment shortcuts mastery");
  InventoryTransaction.commit(g,p,Map.of(ItemType.WOODEN_WALL,7),Map.of());p.hotbar.normalize(p);check(p.hotbar.type(4)==null,"exhausted shortcut clears");
  // Crafting keeps authoritative station/capacity validation and real ingredient costs.
  p.craftingProgress.restore(3,0,new String[0]);RecipeRegistry.Recipe planks=RecipeRegistry.RECIPES.stream().filter(r->r.id().equals("plank")).findFirst().orElseThrow();check(g.crafting.craftError(planks,1)!=null,"missing station/materials cannot become craftable through UI");
  Entity[] savedBag=p.inventorySlots;p.inventorySlots=new Entity[20];for(int i=0;i<20;i++)p.inventorySlots[i]=ItemRegistry.create(ItemType.WOODEN_AXE,g);check(!InventoryTransaction.commit(g,p,Map.of(),Map.of(ItemType.STONE,1)),"full inventory rejects output atomically");p.inventorySlots=savedBag;
  p.craftingProgress.restore(9,0,new String[0]);InventoryTransaction.commit(g,p,Map.of(),Map.of(ItemType.WOODEN_AXE,1,ItemType.WOODEN_SWORD,1,ItemType.WOODEN_BOW,1,ItemType.WOODEN_WALL,8,ItemType.WOODEN_FLOOR,6,ItemType.POTION_RED,3,ItemType.LOG,10,ItemType.FIBER,12,ItemType.STONE,15));
  // Packet payloads serialize with the existing registration list; no parallel inventory state.
  com.esotericsoftware.kryonet.Client endpoint=new com.esotericsoftware.kryonet.Client();NetworkRegistration.register(endpoint);com.esotericsoftware.kryo.io.Output output=new com.esotericsoftware.kryo.io.Output(1024,-1);PlayerState packet=CharacterState.capture(g,p);endpoint.getKryo().writeObject(output,packet);com.esotericsoftware.kryo.io.Input input=new com.esotericsoftware.kryo.io.Input(output.toBytes());PlayerState decoded=endpoint.getKryo().readObject(input,PlayerState.class);check(decoded.hotbarTypes[0]==ItemType.WOODEN_STAFF,"network state preserves stable hotbar reference");endpoint.close();
  // Real rendered logical UI, transformed using the existing physical viewport mapping.
  g.gameState=g.playState;g.ui.game.open(GameUI.Window.INVENTORY);settle();BufferedImage logical=render();javax.imageio.ImageIO.write(logical,"png",Path.of("/tmp/astral-phase5-inventory.png").toFile());
  for(int[] size:new int[][]{{1366,768},{1920,1080}}){DisplaySettings.Viewport v=g.display.gameViewport(size[0],size[1]);check(v.x()>=0&&v.y()>=0&&v.x()+v.width()<=size[0]&&v.y()+v.height()<=size[1],"viewport fits "+Arrays.toString(size));BufferedImage physical=new BufferedImage(size[0],size[1],2);Graphics2D d=physical.createGraphics();d.drawImage(logical,v.x(),v.y(),v.width(),v.height(),null);d.dispose();javax.imageio.ImageIO.write(physical,"png",Path.of("/tmp/astral-phase5-"+size[0]+".png").toFile());}
  g.ui.game.open(GameUI.Window.CHARACTER);settle();javax.imageio.ImageIO.write(render(),"png",Path.of("/tmp/astral-phase5-character.png").toFile());
  System.out.println("PHASE 5 CHECKS PASS: "+checks);
 }
}
