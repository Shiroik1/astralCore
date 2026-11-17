import main.*;
import crafting.CraftingManager;
import entity.*;
import object.*;
import world.*;
import monster.*;
import world.generation.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Explicit Phase 3 acceptance checks. Uses temporary saves and existing game transactions/AI. */
public final class Phase3GameplayCheck {
    static Gamepanel g;static Player p;static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Object get(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    static void set(Object object,String name,Object value)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    static void clearNodes()throws Exception{((Map<?,?>)get(g.crafting,"nodes")).clear();((Map<?,?>)get(g.crafting,"savedHits")).clear();}
    static void feet(int col,int row,String direction){p.worldX=col*48+24-(int)p.solidArea.getCenterX();p.worldY=row*48+24-p.solidArea.y-p.solidArea.height+1;p.direction=direction;}
    static Object node(String id,String kind,int col,int row,WorldObject source)throws Exception{
        Method method=CraftingManager.class.getDeclaredMethod("addNode",String.class,String.class,int.class,int.class,WorldObject.class);method.setAccessible(true);method.invoke(g.crafting,id,kind,col*48+24,row*48+24,source);return ((Map<?,?>)get(g.crafting,"nodes")).get(id);
    }
    static void target(Object expected,String message){g.interactions.refresh();check(g.interactions.token()==expected,message);if(expected!=null)check(g.interactions.selected(expected),message+" outline uses same target");}
    static void emptyBuildings(){g.buildings.restore("{\"version\":2,\"elements\":[],\"decorations\":[],\"cleared\":[]}");}
    static void building(String kind,int col,int row){g.buildings.restore("{\"version\":2,\"elements\":[{\"kind\":\""+kind+"\",\"col\":"+col+",\"row\":"+row+",\"vertical\":"+kind.equals("WALL")+",\"open\":false}],\"decorations\":[],\"cleared\":[]}");}
    static void flatten()throws Exception{
        g.worldObjects.clear();Arrays.fill(g.obj,null);Arrays.fill(g.npc,null);Arrays.fill(g.monster,null);emptyBuildings();g.slimeNest.reset();g.ui.game.reset();g.gameState=g.playState;
        for(var column:(art.Terrain[][])get(g.tileM,"terrainGrid"))Arrays.fill(column,art.Terrain.GRASS);
        Object heights=g.generatedWorld.heights();for(var column:(byte[][])get(heights,"heights"))Arrays.fill(column,(byte)0);for(var column:(boolean[][])get(heights,"playable"))Arrays.fill(column,true);
        g.grass.load();g.crafting.reset();g.crafting.ensureWorld();clearNodes();
    }
    static void interactions()throws Exception{
        flatten();feet(50,50,"right");Object twig=node("generated:51:50","twig",51,50,null);target(twig,"adjacent twig");check(g.interactions.interact(),"twig action");check(crafting.InventoryTransaction.count(p,ItemType.TWIG)==1,"existing twig yield");target(null,"depleted resource clears immediately");
        clearNodes();node("generated:52:50","twig",52,50,null);target(null,"two tiles rejected");clearNodes();node("generated:49:50","twig",49,50,null);target(null,"behind rejected");clearNodes();node("generated:51:51","twig",51,51,null);target(null,"diagonal rejected");
        for(String direction:new String[]{"up","down","left","right"}){clearNodes();feet(50,50,direction);Point at=g.interactions.validator.front(p);Object n=node("generated:"+at.x+":"+at.y,"twig",at.x,at.y,null);target(n,"cardinal "+direction);}
        feet(50,50,"right");clearNodes();Object tree=node("generated:51:50","tree",51,50,null);p.currentWeapon=ItemRegistry.create(ItemType.FISTS,g);target(null,"tree without axe");p.currentWeapon=ItemRegistry.create(ItemType.WOODEN_AXE,g);target(tree,"tree with axe");
        set(g.crafting,"cooldown",0);check(g.interactions.interact(),"tree chop");check((int)get(tree,"hits")<crafting.RecipeRegistry.HARDNESS.get("tree"),"chop retains existing power");
        clearNodes();WorldObject actualTree=new WorldObject(g,WorldObjectType.OAK_1,52,50,null);g.worldObjects.add(actualTree);Object actualNode=node("scenery:OAK_1:52:50","tree",52,50,actualTree);
        target(null,"canopy does not extend trunk reach");feet(51,50,"right");target(actualNode,"actual trunk footprint");check(g.crafting.nearHarvestable(actualTree),"tree outline uses node selection");g.worldObjects.clear();feet(50,50,"right");
        clearNodes();Object rock=node("generated:51:50","rock",51,50,null);target(null,"axe cannot mine");p.currentWeapon=ItemRegistry.create(ItemType.CRUDE_PICKAXE,g);target(rock,"rock with pickaxe");
        Object heights=g.generatedWorld.heights();((byte[][])get(heights,"heights"))[51][50]=1;target(null,"elevation blocks");((byte[][])get(heights,"heights"))[51][50]=0;
        ((art.Terrain[][])get(g.tileM,"terrainGrid"))[51][50]=art.Terrain.WATER;target(null,"water blocks");((art.Terrain[][])get(g.tileM,"terrainGrid"))[51][50]=art.Terrain.GRASS;
        clearNodes();g.grass.paint(51,50,GrassLayer.FIELD_GREEN,3);g.crafting.reset();g.crafting.ensureWorld();Object grass=((Map<?,?>)get(g.crafting,"nodes")).get("tallgrass:51:50");p.currentWeapon=ItemRegistry.create(ItemType.WOODEN_SWORD,g);check(grass!=null,"grass fixture uses existing field layer");target(grass,"adjacent harvestable grass");set(g.crafting,"cooldown",0);check(g.interactions.interact(),"grass action");check(crafting.InventoryTransaction.count(p,ItemType.FIBER)==3,"grass existing fiber yield");
        clearNodes();Entity drop=ItemRegistry.create(ItemType.WOODEN_SWORD,g);drop.worldX=51*48;drop.worldY=50*48;g.obj[0]=drop;target(drop,"front dropped item");p.direction="left";target(null,"dropped item behind");p.direction="right";
        building("WALL",51,50);target(null,"solid wall blocks drop in same cell");emptyBuildings();check(g.interactions.interact(),"drop pickup");check(g.obj[0]==null,"pickup removed once");
        Entity first=ItemRegistry.create(ItemType.WOODEN_SWORD,g),second=ItemRegistry.create(ItemType.WOODEN_BOW,g);first.worldX=second.worldX=51*48;first.worldY=second.worldY=50*48;
        g.obj[1]=second;target(second,"overlap target initially stable");g.obj[0]=first;target(second,"equal-priority overlap retains highlighted item");check(g.interactions.interact()&&g.obj[1]==null&&g.obj[0]==first,"pickup action honors retained tie target");g.obj[0]=null;
        check(g.interactions.validator.valid(p,new Rectangle(51*48,49*48,3*48,3*48)),"multi tile footprint with far center");
        WorldObject large=new WorldObject(g,WorldObjectType.ROCK_LARGE,52,50,null);g.worldObjects.add(large);Object largeNode=node("scenery:ROCK_LARGE:52:50","rock",52,50,large);p.currentWeapon=ItemRegistry.create(ItemType.CRUDE_PICKAXE,g);
        Rectangle footprint=interaction.InteractionValidator.bounds(large);boolean found=false;
        for(int r=48;r<=52;r++)for(int c=49;c<=54;c++)for(String direction:new String[]{"up","down","left","right"}){feet(c,r,direction);if(g.interactions.accepts(p,footprint,largeNode)){target(largeNode,"large rock edge contact");found=true;break;}}
        check(found,"large physical rock has accessible footprint");g.worldObjects.clear();feet(50,50,"right");
        for(String stationType:new String[]{"workbench","forge","campfire","chest"}){
            String item=crafting.RecipeRegistry.ITEMS.values().stream().filter(def->def.place().equals(stationType)).findFirst().orElseThrow().id();
            g.crafting.restoreWorld("{\"seed\":"+g.worldConfig.seed()+",\"hits\":{},\"stations\":[{\"id\":\"test\",\"item\":\""+item+"\",\"x\":"+(51*48+24)+",\"y\":"+(50*48+40)+"}]}");clearNodes();Object station=((List<?>)get(g.crafting,"stations")).getFirst();target(station,item+" front footprint");check(g.interactions.interact()&&g.crafting.open,item+" existing interaction");g.crafting.open=false;
        }
        g.crafting.reset();g.crafting.ensureWorld();clearNodes();building("DOOR",51,50);target(getDoor(),"door front");String before=g.buildings.snapshot();check(g.interactions.interact(),"door action");check(!g.buildings.snapshot().equals(before),"door changed open state");emptyBuildings();
        // Existing sword reach remains greater than a single interaction cell where authored that way.
        p.currentWeapon=ItemRegistry.create(ItemType.WOODEN_SWORD,g);p.getAttack();int width=p.attackArea.width,reach=p.attackArea.height;node("generated:53:50","twig",53,50,null);target(null,"far resource rejected independently of attack");check(p.attackArea.width==width&&p.attackArea.height==reach,"combat hitbox untouched");
        clearNodes();System.out.println("DIRECTIONAL CHECKS PASS: "+checks);
    }
    static Object getDoor()throws Exception{return ((Map<?,?>)get(g.buildings,"structures")).values().iterator().next();}
    static void directorReset()throws Exception{
        Arrays.fill(g.monster,null);g.slimeNest.reset();set(g.generatedWorld,"slimeNest",null);g.slimeSpawner.resetPopulation();
        set(g.slimeSpawner,"clock",10000L);set(g.slimeSpawner,"respawnUntil",0L);set(g.slimeSpawner,"quietUntil",0L);
    }
    static List<MON_GreenSlime> wilderness(){List<MON_GreenSlime> list=new ArrayList<>();for(Monster monster:g.monster)if(monster instanceof MON_GreenSlime slime && slime.nestId.isEmpty())list.add(slime);return list;}
    static Object reachable()throws Exception{Method method=SlimeSpawner.class.getDeclaredMethod("reachable",Player.class);method.setAccessible(true);return method.invoke(g.slimeSpawner,p);}
    static SpawnDebugInfo.Reason rejection(MON_GreenSlime slime,Object reach)throws Exception{Method method=SlimeSpawner.class.getDeclaredMethod("validateCandidate",MON_GreenSlime.class,Player.class,reach.getClass());method.setAccessible(true);return (SpawnDebugInfo.Reason)method.invoke(g.slimeSpawner,slime,p,reach);}
    static void encounters()throws Exception{
        flatten();feet(50,50,"right");TerrainHeightResolver.updateCameras(g);directorReset();
        check(g.slimeSpawner.debug.lastCol==-1&&g.slimeSpawner.debug.lastDistance==0,"world reset clears spawn diagnostics");
        ((boolean[][])get(g.generatedWorld,"accessGround"))[50][50]=true;
        check(g.generatedWorld.reserved(50,50)&&!g.generatedWorld.encounterProtected(50,50),"generation route is not a wilderness safe zone");
        g.slimeSpawner.debugCommand("now");check(g.slimeSpawner.debug.spawned>=1&&g.slimeSpawner.debug.spawned<=2,"offscreen group registered: "+g.slimeSpawner.debug.result);
        check(g.slimeSpawner.debug.attempts<=EncounterSpawnConfig.ATTEMPTS,"bounded candidate budget");
        Rectangle camera=g.slimeSpawner.cameraView(p,EncounterSpawnConfig.MARGIN_TILES*48);
        for(MON_GreenSlime slime:wilderness()){
            check(!camera.intersects(g.slimeSpawner.spriteBounds(slime)),"complete sprite outside expanded camera");check(g.slimeSpawner.shouldSimulate(slime),"offscreen arrival remains active");check(slime.investigating(),"director supplies investigation");check(SlimeMotion.clear(g,SlimeMotion.body(slime,slime.worldX,slime.worldY)),"full spawn body fits terrain");
        }
        Rectangle view=g.slimeSpawner.cameraView(p,0);for(int[] size:new int[][]{{1366,768},{1920,1080}}){g.setSize(size[0],size[1]);check(view.equals(g.slimeSpawner.cameraView(p,0)),"physical resolution retains actual logical camera");}
        MON_GreenSlime approach=wilderness().getFirst();net.MonsterState captured=new net.MonsterState();approach.captureSlime(captured);double before=SlimeMotion.distance(approach,p);int oldX=p.worldX,oldY=p.worldY;
        p.worldX-=5*48;for(int i=0;i<60;i++){g.currentTick++;approach.update();}net.MonsterState unchanged=new net.MonsterState();approach.captureSlime(unchanged);check(captured.slimeInvestigationX==unchanged.slimeInvestigationX&&captured.slimeInvestigationY==unchanged.slimeInvestigationY,"investigation destination does not track moving player");p.worldX=oldX;p.worldY=oldY;
        boolean chased=false;for(int i=0;i<2400;i++){g.currentTick++;approach.update();if(approach.state==MON_GreenSlime.State.CHASE||approach.committed()){chased=true;break;}}
        check(SlimeMotion.distance(approach,p)<before,"investigation approaches naturally");check(chased,"investigation transitions to existing chase/attack");
        long spawned=g.slimeSpawner.debug.totalSpawned;g.slimeSpawner.debugCommand("now");check(g.slimeSpawner.debug.totalSpawned==spawned&&g.slimeSpawner.debug.result.contains("IDLE_TRAVEL"),"idle player cannot accumulate groups");
        // Save/network payload restores one existing entity's destination, not a fresh spawn callback.
        MON_GreenSlime restored=new MON_GreenSlime(g);restored.worldX=captured.worldX;restored.worldY=captured.worldY;restored.restoreSlime(captured,true);net.MonsterState after=new net.MonsterState();restored.captureSlime(after);check(restored.investigating()&&after.slimeInvestigationX==captured.slimeInvestigationX&&after.slimeInvestigationTicks==captured.slimeInvestigationTicks,"investigation save fields restore");
        net.MonsterState legacy=new net.MonsterState();legacy.slimeState="IDLE";legacy.nestId="";restored.restoreSlime(legacy,true);check(!restored.investigating(),"old monster payload without investigation accepted");
        directorReset();feet(50,50,"right");TerrainHeightResolver.updateCameras(g);Object reach=reachable();MON_GreenSlime candidate=new MON_GreenSlime(g);candidate.worldX=69*48;candidate.worldY=50*48;
        check(rejection(candidate,reach)==null,"valid offscreen candidate fixture");
        Player remote=new Player(g,g.keyH);remote.worldX=69*48;remote.worldY=50*48;g.players[1]=remote;TerrainHeightResolver.updateCameras(g);check(rejection(candidate,reach)==SpawnDebugInfo.Reason.INSIDE_CAMERA,"all active player cameras excluded");g.players[1]=null;
        candidate.worldX=50*48;check(rejection(candidate,reach)==SpawnDebugInfo.Reason.INSIDE_CAMERA,"inside-camera reason");candidate.worldX=0;check(rejection(candidate,reach)==SpawnDebugInfo.Reason.WORLD_BOUNDARY,"world-border reason");
        candidate.worldX=69*48;((art.Terrain[][])get(g.tileM,"terrainGrid"))[69][50]=art.Terrain.WATER;check(rejection(candidate,reach)==SpawnDebugInfo.Reason.WATER,"water reason");((art.Terrain[][])get(g.tileM,"terrainGrid"))[69][50]=art.Terrain.GRASS;
        ((byte[][])get(g.generatedWorld.heights(),"heights"))[70][50]=1;check(rejection(candidate,reach)==SpawnDebugInfo.Reason.CLIFF_COLLISION,"cliff body-straddle reason");((byte[][])get(g.generatedWorld.heights(),"heights"))[70][50]=0;
        building("WALL",69,50);check(rejection(candidate,reach)==SpawnDebugInfo.Reason.BUILDING,"building reason");emptyBuildings();
        WorldObject tree=new WorldObject(g,WorldObjectType.OAK_1,69,50,null);g.worldObjects.add(tree);check(rejection(candidate,reach)==SpawnDebugInfo.Reason.SOLID_OBJECT,"tree obstruction reason");g.worldObjects.clear();
        g.monster[0]=new MON_GreenSlime(g);g.monster[0].worldX=candidate.worldX;g.monster[0].worldY=candidate.worldY;check(rejection(candidate,reach)==SpawnDebugInfo.Reason.OCCUPIED,"occupied spawn reason");g.monster[0]=null;
        set(g.generatedWorld,"slimeNest",new SlimeNestPlan("fixture",69,50,8));check(rejection(candidate,reach)==SpawnDebugInfo.Reason.BOSS_ARENA,"nest arena reason");set(g.generatedWorld,"slimeNest",null);
        candidate.worldX=g.generatedWorld.spawnCol*48;candidate.worldY=g.generatedWorld.spawnRow*48;check(rejection(candidate,reach)==SpawnDebugInfo.Reason.PROTECTED_AREA,"starting safe zone reason");candidate.worldX=69*48;candidate.worldY=50*48;
        byte[][] heights=(byte[][])get(g.generatedWorld.heights(),"heights");for(int x=67;x<=73;x++)for(int y=48;y<=53;y++)heights[x][y]=1;check(rejection(candidate,reachable())==SpawnDebugInfo.Reason.UNREACHABLE_LOCATION,"inaccessible plateau rejected");for(var column:heights)Arrays.fill(column,(byte)0);
        StringBuilder stationBarrier=new StringBuilder("{\"seed\":").append(g.worldConfig.seed()).append(",\"hits\":{},\"stations\":[");
        for(int col=0;col<g.maxWorldCol;col++){if(col>0)stationBarrier.append(',');stationBarrier.append("{\"id\":\"barrier").append(col).append("\",\"item\":\"workbench\",\"x\":").append(col*48+24).append(",\"y\":").append(45*48+16).append('}');}stationBarrier.append("]}");g.crafting.restoreWorld(stationBarrier.toString());
        candidate.worldY=38*48;check(rejection(candidate,reachable())==SpawnDebugInfo.Reason.UNREACHABLE_LOCATION,"reachable search cannot hop through a thin station barrier");g.crafting.reset();g.crafting.ensureWorld();clearNodes();candidate.worldY=50*48;
        // Eight ordinary nearby slimes cap the director; nest guardians/adds use their separate budget.
        directorReset();for(int i=0;i<8;i++){MON_GreenSlime slime=new MON_GreenSlime(g);slime.worldX=(48+i%4)*48;slime.worldY=(48+i/4)*48;g.monster[i]=slime;}
        MON_GreenSlime add=new MON_GreenSlime(g);add.nestId="arena/add";add.worldX=p.worldX;add.worldY=p.worldY;g.monster[8]=add;check(g.slimeSpawner.nearby(p)==8,"summons excluded from nearby cap");g.slimeSpawner.debugCommand("now");check(g.slimeSpawner.debug.result.contains("POPULATION_CAP"),"nearby cap enforced");
        directorReset();MON_CrownedSlime boss=new MON_CrownedSlime(g);boss.state=MON_CrownedSlime.State.AWAKEN;g.monster[0]=boss;g.slimeSpawner.debugCommand("now");check(g.slimeSpawner.debug.result.contains("BOSS_ENCOUNTER")&&wilderness().isEmpty(),"active boss disables random spawning");
        directorReset();g.slimeSpawner.onRespawn(p);g.slimeSpawner.debugCommand("now");check(g.slimeSpawner.debug.result.contains("RESPAWN_GRACE"),"respawn grace cannot be bypassed by forced check");
        directorReset();g.slimeSpawner.debugCommand("fast");long checksBefore=g.slimeSpawner.debug.checks;for(int i=0;i<181;i++){g.currentTick++;g.slimeSpawner.update();}check(g.slimeSpawner.debug.checks>checksBefore,"periodic accelerated checks execute");g.slimeSpawner.debugCommand("normal");
        directorReset();for(int i=0;i<Gamepanel.ORDINARY_MONSTER_CAP;i++){MON_GreenSlime far=new MON_GreenSlime(g);far.worldX=2*48;far.worldY=2*48;g.monster[i]=far;}
        g.slimeSpawner.debugCommand("now");check(g.slimeSpawner.debug.spawned>0,"inactive distant population cannot monopolize storage: "+g.slimeSpawner.debug.result);check(wilderness().size()<=Gamepanel.ORDINARY_MONSTER_CAP,"global storage stays bounded");
        WorldConfig originalConfig=g.worldConfig;WorldSettings smallerBudget=WorldSettings.capture(g);smallerBudget.slimeCount=6;g.worldConfig=smallerBudget.config();directorReset();
        for(int i=0;i<6;i++){MON_GreenSlime far=new MON_GreenSlime(g);far.worldX=2*48;far.worldY=2*48;g.monster[i]=far;}g.slimeSpawner.debugCommand("now");check(g.slimeSpawner.debug.spawned>0&&wilderness().size()<=6,"custom ordinary storage budget is preserved without distant starvation");g.worldConfig=originalConfig;
        directorReset();MON_GreenSlime failed=new MON_GreenSlime(g);failed.worldX=69*48;failed.worldY=50*48;failed.investigationFailed=true;g.monster[0]=failed;((Map<Monster,Long>)get(g.slimeSpawner,"failedSince")).put(failed,10000L-EncounterSpawnConfig.UNREACHABLE_SECONDS*60-1);Method retire=SlimeSpawner.class.getDeclaredMethod("retireDistant");retire.setAccessible(true);retire.invoke(g.slimeSpawner);check(g.monster[0]==null,"unreachable offscreen enemy releases its slot without rewards");
        check(EncounterSpawnConfig.weight(EnvironmentRegion.FOREST_GROVE)>EncounterSpawnConfig.weight(EnvironmentRegion.FLOWER_PLAIN),"forest probability exceeds flower plain");check(EncounterSpawnConfig.weight(EnvironmentRegion.GOLDEN_MEADOW)==.7,"golden meadow retains peaceful weight");
        System.out.println("ENCOUNTER CHECKS PASS: "+checks);
    }
    static boolean findEncounter()throws Exception{
        int step=Math.max(3,g.maxWorldCol/10);for(int row=8;row<g.maxWorldRow-8;row+=step)for(int col=8;col<g.maxWorldCol-8;col+=step){
            p.worldX=col*48;p.worldY=row*48;if(!SlimeMotion.clear(g,SlimeMotion.body(p,p.worldX,p.worldY)))continue;
            TerrainHeightResolver.updateCameras(g);g.slimeSpawner.debugCommand("now");if(g.slimeSpawner.debug.spawned>0)return true;
        }return false;
    }
    static void seedsAndPersistence()throws Exception{
        for(long seed:new long[]{0,-17,987654321L}){
            WorldSettings settings=WorldSettings.capture(g);settings.width=seed==987654321L?128:64;settings.height=seed==987654321L?96:64;settings.seed=seed;g.installGeneration(settings);g.gameState=g.playState;p=g.localPlayer();p.isDead=false;p.inWell=false;p.merchantOpen=false;p.inDialogue=false;g.ui.game.reset();Arrays.fill(g.monster,null);g.slimeNest.reset();g.slimeSpawner.resetPopulation();set(g.slimeSpawner,"clock",10000L);set(g.slimeSpawner,"respawnUntil",0L);
            check(findEncounter(),"reachable wilderness encounter in generated seed "+seed+": "+g.slimeSpawner.debugLines());for(MON_GreenSlime slime:wilderness())check(!g.slimeSpawner.cameraView(p,96).intersects(g.slimeSpawner.spriteBounds(slime)),"seed sprite visibility "+seed);
            System.out.println("SEED "+seed+" "+g.maxWorldCol+"x"+g.maxWorldRow+" "+g.slimeSpawner.debugLines());
        }
        // Real disk save/load with actual generated terrain and existing monster registration.
        save.SaveStore disk=new save.SaveStore(g,Files.createTempDirectory("astral-phase3-save-"));disk.create(0,"Phase3",0,0);p=g.localPlayer();g.gameState=g.playState;set(g.slimeSpawner,"clock",10000L);set(g.slimeSpawner,"respawnUntil",0L);check(findEncounter(),"disk fixture encounter");int count=wilderness().size();check(disk.saveActive(),"write actual save");disk.load(0);p=g.localPlayer();check(wilderness().size()==count,"disk load does not duplicate ordinary runtime enemies");check(wilderness().stream().anyMatch(MON_GreenSlime::investigating),"disk load restores captured investigation destination");
        var packet=net.SnapshotBuilder.build(g);com.esotericsoftware.kryonet.Client endpoint=new com.esotericsoftware.kryonet.Client();net.NetworkRegistration.register(endpoint);var output=new com.esotericsoftware.kryo.io.Output(4096,-1);endpoint.getKryo().writeObject(output,packet);var input=new com.esotericsoftware.kryo.io.Input(output.toBytes());var decoded=endpoint.getKryo().readObject(input,net.WorldSnapshot.class);check(Arrays.stream(decoded.monsters).filter(Objects::nonNull).anyMatch(m->m.slimeInvestigationTicks>0),"network payload retains investigation");endpoint.close();
        WorldSettings small=WorldSettings.capture(g);small.width=16;small.height=16;small.seed=56;g.installGeneration(small);p=g.localPlayer();p.setDefaultPosition();TerrainHeightResolver.updateCameras(g);Arrays.fill(g.monster,null);g.slimeNest.reset();g.slimeSpawner.resetPopulation();set(g.slimeSpawner,"clock",10000L);set(g.slimeSpawner,"respawnUntil",0L);g.slimeSpawner.debugCommand("now");check(wilderness().isEmpty(),"entirely visible small world never forces an onscreen spawn");
        BufferedImage image=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();g.slimeSpawner.debugCommand("on");g.slimeSpawner.drawDebug(graphics);g.slimeSpawner.drawMarkers(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",Path.of("/tmp/astral-phase3-debug.png").toFile());
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("astral.dev","true");System.setProperty("astral.dataDir",Files.createTempDirectory("astral-phase3-check-").toString());g=new Gamepanel();g.gameState=g.playState;p=g.localPlayer();p.playerName="Phase3";
        interactions();encounters();seedsAndPersistence();
        System.out.println("PHASE 3 CHECKS PASS: "+checks);
    }
}
