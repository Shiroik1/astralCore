package main;

import art.*;
import building.*;
import entity.*;
import world.*;
import world.chunk.*;
import world.generation.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Explicitly requested streaming/persistence acceptance checks. Only temporary saves are used. */
public final class ChunkStreamingCheck {
    private static int checks;
    private static Gamepanel g;
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void visit(int col,int row){var p=g.localPlayer();p.worldX=col*g.tileSize;p.worldY=row*g.tileSize;TerrainHeightResolver.updateCameras(g);g.chunks.update();g.crafting.ensureWorld();g.buildings.prepareVisibleCaches();}
    private static long hashPlan(){long h=17;for(int y=0;y<g.maxWorldRow;y++)for(int x=0;x<g.maxWorldCol;x++)h=h*31+g.tileM.terrain(x,y).ordinal()*100+g.tileM.buildingElevation(x,y)*10+g.generatedWorld.meadow(x,y);return h;}
    private static void coverage()throws Exception{
        Map<?,?> images=(Map<?,?>)field(g.tileM,"chunks");Rectangle view=g.chunks.camera(0);int px=ChunkConfig.SIZE*g.tileSize;
        for(int y=Math.max(0,Math.floorDiv(view.y,px));y<=Math.min((g.maxWorldRow-1)/ChunkConfig.SIZE,Math.floorDiv(view.y+view.height-1,px));y++)for(int x=Math.max(0,Math.floorDiv(view.x,px));x<=Math.min((g.maxWorldCol-1)/ChunkConfig.SIZE,Math.floorDiv(view.x+view.width-1,px));x++){
            long key=((long)y<<32)|(x&0xffffffffL);check(images.containsKey(key),"complete camera imagery "+x+","+y);check(g.chunks.loadedAt(x*ChunkConfig.SIZE,y*ChunkConfig.SIZE),"camera metadata loaded");
        }
        int needed=(Math.floorDiv(view.x+view.width-1,px)-Math.floorDiv(view.x,px)+1)*(Math.floorDiv(view.y+view.height-1,px)-Math.floorDiv(view.y,px)+2);
        check(g.tileM.cachedChunks()<=Math.max(ChunkConfig.IMAGE_LIMIT,needed),"bounded image cache");
    }
    private static void buildings()throws Exception{
        visit(32,42);BuildingLayout layout=new BuildingLayout();StringBuilder json=new StringBuilder("{\"version\":2,\"elements\":[");boolean comma=false;
        for(int row=39;row<=45;row++)for(int col=29;col<=35;col++){
            if(comma)json.append(',');comma=true;json.append("{\"kind\":\"FLOOR\",\"col\":").append(col).append(",\"row\":").append(row).append('}');layout.put(new BuildingLayout.Cell(col,row),new BuildingLayout.Tile("floor",false,false));
            if(row==39 || row==45 || col==29 || col==35){String element=row==45&&col==32?"door":"wall";boolean vertical=row!=39&&row!=45;
                json.append(",{\"kind\":\"").append(element.toUpperCase()).append("\",\"col\":").append(col).append(",\"row\":").append(row).append(",\"vertical\":").append(vertical).append('}');layout.put(new BuildingLayout.Cell(col,row),new BuildingLayout.Tile(element,vertical,false));}
        }
        json.append("],\"decorations\":[],\"cleared\":[]}");g.buildings.restore(json.toString());g.buildings.prepareVisibleCaches();String snapshot=g.buildings.snapshot();
        BufferedImage actual=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB),reference=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);
        Graphics2D a=actual.createGraphics(),r=reference.createGraphics();g.buildings.drawFloors(a);List<Entity> pieces=new ArrayList<>();g.buildings.addDrawLayers(pieces);pieces.sort(Comparator.comparingInt(Entity::sortY));for(Entity p:pieces)p.draw(a);a.dispose();
        var p=g.localPlayer();BuildingRenderer full=new BuildingRenderer(layout,g.tileSize,new BuildTextures(g.tileSize));
        for(var c:full.groundCells()){Graphics2D t=(Graphics2D)r.create();t.translate(c.col()*g.tileSize-p.worldX+p.screenX,c.row()*g.tileSize-p.worldY+p.screenY);full.drawFloor(t,c);t.dispose();}
        List<BuildingLayout.Cell> cells=new ArrayList<>(layout.structures().keySet());cells.sort(Comparator.comparingInt(BuildingLayout.Cell::row));for(var c:cells){Graphics2D t=(Graphics2D)r.create();t.translate(c.col()*g.tileSize-p.worldX+p.screenX,c.row()*g.tileSize-p.worldY+p.screenY);full.drawStructure(t,c);t.dispose();}r.dispose();
        int different=0;for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++)if(actual.getRGB(x,y)!=reference.getRGB(x,y))different++;
        check(different==0,"cross-chunk outlines/shadows match authoritative whole-building renderer: "+different+" pixels");
        @SuppressWarnings("unchecked") Map<Object,String> floorElements=(Map<Object,String>)field(g.buildings,"floorElements");
        Object beneathWall=floorElements.keySet().stream().filter(c->{try{return (int)field(c,"col")==31 && (int)field(c,"row")==39;}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") Map<Long,BuildingRenderer> cache=(Map<Long,BuildingRenderer>)field(g.buildings,"surfaces");
        long changedKey=world.spatial.SpatialIndex.key(Math.floorDiv(31,ChunkConfig.BUILD_CACHE_SIZE),Math.floorDiv(39,ChunkConfig.BUILD_CACHE_SIZE));BuildingRenderer old=cache.get(changedKey);
        String material=BuildElements.all().stream().filter(e->e.shape()==BuildElements.Shape.FLOOR && !e.id().equals("floor")).findFirst().orElseThrow().id();floorElements.put(beneathWall,material);
        Method changed=g.buildings.getClass().getDeclaredMethod("changed");changed.setAccessible(true);changed.invoke(g.buildings);check(cache.get(changedKey)!=old,"floor material under a wall invalidates its local visual snapshot");snapshot=g.buildings.snapshot();
        check(g.buildings.coversGround(31,42) && g.buildings.coversGround(32,42),"foundation across chunk border");check(g.buildings.blocksArea(31*48,39*48+12,96,32),"cross-chunk wall collision");
        visit(235,235);for(int i=0;i<=ChunkConfig.KEEP_TICKS;i++)g.chunks.update();check(!g.chunks.loadedAt(31,42),"old building chunk unloads");visit(32,42);check(snapshot.equals(g.buildings.snapshot()),"buildings survive unload without duplication");
        g.buildings.restore(snapshot);check(snapshot.equals(g.buildings.snapshot()),"building save snapshot roundtrip");
    }
    @SuppressWarnings("unchecked") private static void resources()throws Exception{
        visit(g.generatedWorld.spawnCol,g.generatedWorld.spawnRow);
        Map<String,Object> nodes=(Map<String,Object>)field(g.crafting,"nodes");Map<String,Integer> hits=(Map<String,Integer>)field(g.crafting,"savedHits");
        Object n=nodes.values().stream().filter(v->{try{return field(v,"kind").equals("twig");}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();String id=(String)field(n,"id");int x=(int)field(n,"x"),y=(int)field(n,"y"),col=Math.floorDiv(x,48),row=Math.floorDiv(y,48);
        // A resource modification is committed to the same map the real harvesting transaction writes.
        set(n,"hits",0);hits.put(id,0);String saved=g.crafting.worldJson();visit(235,235);for(int i=0;i<=ChunkConfig.KEEP_TICKS;i++)g.chunks.update();check(!nodes.containsKey(id),"resource entity released with chunk");check(hits.get(id)==0,"dirty resource state retained independently");
        visit(col,row);check((int)field(nodes.get(id),"hits")==0,"collected resource stays depleted after reload");g.crafting.restoreWorld(saved);check((int)field(nodes.get(id),"hits")==0,"resource depletion restored from serialized save");
        var tree=g.worldObjects.stream().filter(o->o.kind.swaysInWind()).findFirst().orElseThrow();String treeId="scenery:"+tree.kind+":"+tree.col+":"+tree.row;Object tn=nodes.get(treeId);check(tn!=null,"resident tree node registered");set(tn,"hits",1);hits=(Map<String,Integer>)field(g.crafting,"savedHits");hits.put(treeId,1);int tc=tree.col,tr=tree.row;
        visit(235,235);for(int i=0;i<=ChunkConfig.KEEP_TICKS;i++)g.chunks.update();visit(tc,tr);check((int)field(nodes.get(treeId),"hits")==1,"partial tree harvesting survives unloading");
    }
    private static void distantValidation(){
        visit(32,42);for(int i=0;i<=ChunkConfig.KEEP_TICKS;i++)g.chunks.update();
        var rock=g.generatedWorld.objects().stream().filter(p->p.type()==WorldObjectType.ROCK_LARGE && !g.chunks.loadedAt(p.col(),p.row())).findFirst().orElseThrow();
        g.chunks.ensureTiles(rock.col()-4,rock.row()-4,rock.col()+4,rock.row()+4);check(g.chunks.loadedAt(rock.col(),rock.row()),"off-screen blueprint validation loads collision metadata");
        check(g.worldObjects.stream().anyMatch(o->o.col==rock.col() && o.row==rock.row() && o.kind==rock.type()),"unloaded solid rocks cannot disappear from remote blueprint validation");
    }
    private static void furniture()throws Exception{
        visit(32,42);String house=g.buildings.snapshot();var near=new Decoration("near-bed","bed_blue",32*48,43*48,false,1);var far=new Decoration("far-bed","bed_coral",230*48,230*48,false,0);
        String rag=PropCatalog.all().stream().filter(PropCatalog.Prop::ground).findFirst().orElseThrow().id();var rug=new Decoration("near-rug",rag,32*48,42*48,false,0);
        String decorated=house.replace("\"decorations\": "+Decoration.json(List.of()),"\"decorations\": "+Decoration.json(List.of(near,far,rug)));g.buildings.restore(decorated);
        List<Entity> layers=new ArrayList<>();g.buildings.addDrawLayers(layers);check(layers.stream().filter(e->e.getClass().getSimpleName().equals("Furnishing")).count()==1,"only visible furniture enters the depth sort");
        for(Decoration d:List.of(near,far)){var box=PropCatalog.get(d.element()).bounds(d.x(),d.y(),d.flipped(),d.rotation());check(g.buildings.blocksArea((int)box.getCenterX(),(int)box.getCenterY(),1,1),"furniture spatial lookup respects rotated collision "+d.id());}
        @SuppressWarnings("unchecked") Map<String,?> actors=(Map<String,?>)field(g.buildings,"furniture");Object before=actors.get("near-bed");Method changed=g.buildings.getClass().getDeclaredMethod("changed");changed.setAccessible(true);changed.invoke(g.buildings);check(actors.get("near-bed")==before,"unrelated building edit reuses furnishing actors");
        g.buildings.restore(house);var box=PropCatalog.get(far.element()).bounds(far.x(),far.y(),false,0);check(!g.buildings.blocksArea((int)box.getCenterX(),(int)box.getCenterY(),1,1),"removed furnishings leave no stale collision entries");g.buildings.restore(decorated);
    }
    private static void stationAndDisk()throws Exception{
        String crafting=g.crafting.worldJson().replace("\"stations\":[]","\"stations\":[{\"id\":\"stream-workbench\",\"item\":\"workbench\",\"x\":1536,\"y\":2016}]");g.crafting.restoreWorld(crafting);
        Object station=((List<?>)field(g.crafting,"stations")).getFirst();visit(235,235);for(int i=0;i<=ChunkConfig.KEEP_TICKS;i++)g.chunks.update();visit(32,42);check(((List<?>)field(g.crafting,"stations")).getFirst()==station,"station retains single canonical identity across chunks");check(g.crafting.worldJson().equals(crafting),"station/resource state independent of residency");
        String house=g.buildings.snapshot(),encounter=g.slimeNest.snapshot();var disk=new save.SaveStore(g,Files.createTempDirectory("astral-stream-save-"));disk.create(0,"Streaming",0,0);g.buildings.restore(house);g.crafting.restoreWorld(crafting);g.slimeNest.restore(encounter);check(disk.saveActive(),"atomic disk save of streamed world modifications");disk.load(0);check(g.buildings.snapshot().equals(house),"disk roundtrip retains cross-chunk building and material");check(g.crafting.worldJson().equals(crafting),"disk roundtrip retains harvest/station records outside resident area");check(g.slimeNest.snapshot().equals(encounter),"disk roundtrip retains unique boss defeat");
    }
    private static void settleWorkingSet()throws Exception{
        visit(32,42);for(int i=0;i<=ChunkConfig.KEEP_TICKS;i++)g.chunks.update();
        for(int i=0;i<100;i++){g.chunks.update();if(((Map<?,?>)field(g.tileM,"pending")).isEmpty())return;Thread.sleep(10);}
        throw new AssertionError("terrain worker did not settle");
    }
    private static long retainedHeap()throws Exception{System.gc();Thread.sleep(100);return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    private static void boss()throws Exception{
        var nest=g.generatedWorld.slimeNest();check(nest!=null,"planned nest retained");
        var boss=Arrays.stream(g.monster).filter(m->m instanceof monster.MON_CrownedSlime).map(m->(monster.MON_CrownedSlime)m).findFirst().orElseThrow();
        visit(235,235);check(!g.slimeSpawner.shouldSimulate(boss),"distant dormant boss retains state without AI");
        visit(nest.col()+nest.size()/2,nest.row()+nest.size()/2-3);check(g.slimeSpawner.shouldSimulate(boss),"approaching activates boss");
        int added=g.slimeNest.summon(boss,5);check(added==5 && g.slimeNest.livingSummons(boss.nestId)==5,"five-slime summon remains intact");
        boss.state=monster.MON_CrownedSlime.State.AWAKEN;visit(235,235);check(g.slimeSpawner.shouldSimulate(boss),"active boss receives disengagement tick");boss.update();check(boss.state==monster.MON_CrownedSlime.State.DORMANT && !g.slimeSpawner.shouldSimulate(boss),"distant boss disengages before dormancy");
        check(g.slimeNest.livingSummons(boss.nestId)==0,"disengagement clears summoned minions");
        visit(nest.col()+nest.size()/2,nest.row()+nest.size()/2-3);g.slimeNest.defeated(boss);String save=g.slimeNest.snapshot();g.slimeNest.restore(save);visit(235,235);visit(nest.col()+nest.size()/2,nest.row()+nest.size()/2-3);g.slimeNest.ensurePopulation(true);check(Arrays.stream(g.monster).noneMatch(m->m instanceof monster.MON_CrownedSlime),"defeated unique boss stays defeated across reload");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("astral.dev","true");System.setProperty("astral.dataDir",Files.createTempDirectory("astral-chunk-check-").toString());System.setProperty("astral.world.width","256");System.setProperty("astral.world.height","256");
        g=new Gamepanel();g.setUpGame();g.gameState=g.playState;g.crafting.ensureWorld();long hash=hashPlan();check(ChunkConfig.SIZE==32,"32-tile default");check(g.chunks.loaded()<64,"partial finite world residency");check(g.worldObjects.size()<g.generatedWorld.objects().size(),"distant scenery not materialized");coverage();
        check(Math.floorDiv(-1,32)==-1 && Math.floorMod(-1,32)==31,"negative coordinate convention");
        long initialRetained=retainedHeap();boss();resources();buildings();
        long biggest=0;for(int i=0;i<40;i++){visit(8+(i*37)%240,8+(i*71)%240);coverage();biggest=Math.max(biggest,g.tileM.cacheBytes());check(g.chunks.loaded()<=Math.max(ChunkConfig.CACHE_LIMIT,25),"bounded resident metadata cache");check(g.tileM.cachedChunks()<=ChunkConfig.IMAGE_LIMIT,"bounded image count");Set<String> ids=new HashSet<>();for(var o:g.worldObjects)check(ids.add(o.kind+":"+o.col+":"+o.row),"scenery unique after migration");}
        settleWorkingSet();long firstRetained=retainedHeap();for(int i=0;i<40;i++)visit(8+(i*61)%240,8+(i*97)%240);settleWorkingSet();long secondRetained=retainedHeap();System.out.println("CACHE_PLATEAU firstMiB="+firstRetained/1048576+" secondMiB="+secondRetained/1048576);check(secondRetained<=firstRetained+48L*1048576,"retained heap plateaus at the same settled camera position after exploration");
        check(hashPlan()==hash,"terrain/elevation/meadows independent of load order");
        var slime=new monster.MON_GreenSlime(g);g.monster[3]=slime;slime.worldX=31*48;slime.worldY=42*48;g.chunks.indexEntities();long before=g.chunks.entityChunk(slime);slime.worldX=32*48;g.chunks.indexEntities();check(before!=g.chunks.entityChunk(slime),"moving entity ownership follows boundary");check(Arrays.stream(g.monster).filter(e->e==slime).count()==1,"moving entity remains single canonical instance");
        visit(220,220);slime.worldX=2*48;slime.worldY=2*48;check(!g.slimeSpawner.shouldSimulate(slime),"distant slime simulation dormant");slime.worldX=g.localPlayer().worldX+g.screenWidth/2+5*48;slime.worldY=g.localPlayer().worldY;slime.investigate(g.localPlayer().worldX,g.localPlayer().worldY,900);check(g.slimeSpawner.shouldSimulate(slime),"offscreen investigation remains active");
        distantValidation();furniture();stationAndDisk();
        System.out.println("RETAINED_HEAP initialMiB="+initialRetained/1048576+" exploredMiB="+retainedHeap()/1048576);
        g.performance.command("on");g.performance.command("grid");g.drawToTempscreen();javax.imageio.ImageIO.write(g.tempScreen,"png",Path.of("/tmp/astral-stream-debug.png").toFile());
        System.out.println("CHUNK CHECKS PASS: "+checks+"; maximum terrain cache MiB="+biggest/1048576+" loaded="+g.chunks.loaded()+" scenery="+g.worldObjects.size());
    }
}
