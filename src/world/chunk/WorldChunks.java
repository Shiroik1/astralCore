package world.chunk;

import main.Gamepanel;
import world.*;
import world.generation.GeneratedWorld;
import world.spatial.SpatialIndex;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Game-thread residency over the existing authoritative finite world plan and save owners. */
public final class WorldChunks {
    public enum State {UNLOADED,REQUESTED,LOADING,READY,ACTIVE,DORMANT}
    public static final class Chunk {
        public final int x,y;
        public State state=State.UNLOADED;
        public long lastNeeded;
        public final List<GeneratedWorld.Placement> placements=new ArrayList<>();
        public final List<GeneratedWorld.Resource> resources=new ArrayList<>();
        private final List<WorldObject> scenery=new ArrayList<>();
        private Chunk(int x,int y){this.x=x;this.y=y;}
    }
    private final Gamepanel gp;
    private final Map<Long,Chunk> plan=new LinkedHashMap<>();
    private final Set<Long> resident=new HashSet<>();
    private final IdentityHashMap<entity.Entity,Long> moving=new IdentityHashMap<>();
    private boolean installed;
    private long tick;
    public WorldChunks(Gamepanel gp){this.gp=gp;}
    public boolean enabled(){return installed && gp.generatedWorld!=null;}
    private long key(int x,int y){return SpatialIndex.key(x,y);}
    public Chunk chunk(int x,int y){return plan.get(key(x,y));}
    public void resetPlan(){
        installed=gp.generatedWorld!=null;plan.clear();resident.clear();moving.clear();tick=0;
        if(!installed)return;
        int size=ChunkConfig.SIZE;
        for(int y=0;y<(gp.maxWorldRow+size-1)/size;y++)for(int x=0;x<(gp.maxWorldCol+size-1)/size;x++)plan.put(key(x,y),new Chunk(x,y));
        for(var p:gp.generatedWorld.objects())chunk(Math.floorDiv(p.col(),size),Math.floorDiv(p.row(),size)).placements.add(p);
        for(var r:gp.generatedWorld.resources())chunk(Math.floorDiv(r.col(),size),Math.floorDiv(r.row(),size)).resources.add(r);
    }
    public Rectangle camera(int marginTiles){var p=gp.localPlayer();int margin=marginTiles*gp.tileSize;return new Rectangle(p.worldX-p.screenX-margin,p.worldY-p.screenY-margin,gp.screenWidth+2*margin,gp.screenHeight+2*margin);}
    private void include(Set<Long> keys,Rectangle area){
        int px=gp.tileSize*ChunkConfig.SIZE;
        for(int y=Math.max(0,Math.floorDiv(area.y,px));y<=Math.min((gp.maxWorldRow-1)/ChunkConfig.SIZE,Math.floorDiv(area.y+area.height-1,px));y++)
            for(int x=Math.max(0,Math.floorDiv(area.x,px));x<=Math.min((gp.maxWorldCol-1)/ChunkConfig.SIZE,Math.floorDiv(area.x+area.width-1,px));x++)keys.add(key(x,y));
    }
    private void neighborhood(Set<Long> keys,entity.Player p,int radius){int px=ChunkConfig.SIZE*gp.tileSize,cx=Math.floorDiv(p.worldX+(int)p.solidArea.getCenterX(),px),cy=Math.floorDiv(p.worldY+p.solidArea.y+p.solidArea.height-1,px);
        for(int y=cy-radius;y<=cy+radius;y++)for(int x=cx-radius;x<=cx+radius;x++)if(plan.containsKey(key(x,y)))keys.add(key(x,y));}
    public void update(){
        if(gp.localPlayer()==null || gp.localPlayer().inWell)return;
        if(!enabled()){gp.tileM.prepareView();gp.grass.prepareView();return;}tick++;
        long start=gp.performance.start();
        Set<Long> active=new HashSet<>(),wanted=new HashSet<>();
        for(var p:gp.players)if(p!=null && !p.inWell && !p.isDead){
            neighborhood(active,p,ChunkConfig.ACTIVE_RADIUS);neighborhood(wanted,p,ChunkConfig.PRELOAD_RADIUS);
            int margin=gp.tileSize*12;
            include(active,new Rectangle(p.worldX-p.screenX-margin,p.worldY-p.screenY-margin,gp.screenWidth+margin*2,gp.screenHeight+margin*2));
        }
        wanted.addAll(active);include(wanted,camera(16));
        List<Chunk> ordered=new ArrayList<>();for(long k:wanted){Chunk c=plan.get(k);if(c!=null)ordered.add(c);}
        var player=gp.localPlayer();ordered.sort(Comparator.comparingDouble(c->Math.hypot(c.x*ChunkConfig.SIZE*gp.tileSize-player.worldX,c.y*ChunkConfig.SIZE*gp.tileSize-player.worldY)));
        // Required collision/scenery metadata is integrated only here, never by a render or worker thread.
        for(Chunk c:ordered){long k=key(c.x,c.y);if(!resident.contains(k))load(c);c.state=active.contains(k)?State.ACTIVE:State.READY;c.lastNeeded=tick;}
        for(long k:new ArrayList<>(resident))if(!wanted.contains(k)){Chunk c=plan.get(k);c.state=State.DORMANT;if(tick-c.lastNeeded>ChunkConfig.KEEP_TICKS)unload(c);}
        int limit=Math.max(ChunkConfig.CACHE_LIMIT,wanted.size());
        while(resident.size()>limit){Chunk oldest=null;for(long k:resident){Chunk c=plan.get(k);if(!wanted.contains(k) && (oldest==null || c.lastNeeded<oldest.lastNeeded))oldest=c;}if(oldest==null)break;unload(oldest);}
        // The identity map is an index only; canonical entity arrays remain the simulation owners.
        indexEntities();
        gp.tileM.prepareView();gp.grass.prepareView();gp.performance.end(main.PerformanceMetrics.Section.STREAMING,start);
    }
    private void load(Chunk c){
        c.state=State.REQUESTED;c.state=State.LOADING;
        for(var p:c.placements){WorldObject object=new WorldObject(gp,p.type(),p.col(),p.row(),null);if(gp.buildings.sceneryCleared(object))continue;c.scenery.add(object);gp.worldObjects.add(object);}
        resident.add(key(c.x,c.y));c.state=State.READY;gp.crafting.loadChunk(c); // Applies saved depletion before a frame can use the chunk.
    }
    private void unload(Chunk c){
        // Hits/cleared cells/construction already reside in authoritative save maps; they outlive residents.
        gp.crafting.unloadChunk(c);gp.worldObjects.removeAll(c.scenery);c.scenery.clear();
        gp.grass.releaseChunk(c.x,c.y);gp.tileM.releaseChunk(c.x,c.y);resident.remove(key(c.x,c.y));c.state=State.UNLOADED;gp.interactions.clear();
    }
    /** Explicit off-screen blueprint validation needs the same scenery collision data as local placement. */
    public void ensureTiles(int left,int top,int right,int bottom){
        if(!enabled())return;
        for(int y=Math.max(0,Math.floorDiv(top,ChunkConfig.SIZE));y<=Math.min((gp.maxWorldRow-1)/ChunkConfig.SIZE,Math.floorDiv(bottom,ChunkConfig.SIZE));y++)
            for(int x=Math.max(0,Math.floorDiv(left,ChunkConfig.SIZE));x<=Math.min((gp.maxWorldCol-1)/ChunkConfig.SIZE,Math.floorDiv(right,ChunkConfig.SIZE));x++){
                Chunk c=chunk(x,y);if(c!=null && !resident.contains(key(x,y))){load(c);c.lastNeeded=tick;}
            }
    }
    public void indexEntities(){moving.clear();for(var e:gp.monster)if(e!=null)moving.put(e,key(Math.floorDiv(e.worldX,gp.tileSize*ChunkConfig.SIZE),Math.floorDiv(e.worldY,gp.tileSize*ChunkConfig.SIZE)));}
    public long entityChunk(entity.Entity e){return moving.getOrDefault(e,Long.MIN_VALUE);}
    public void reloadResources(){for(long k:resident)gp.crafting.loadChunk(plan.get(k));}
    public boolean activeAt(int worldX,int worldY){if(!enabled())return true;Chunk c=chunk(Math.floorDiv(worldX,gp.tileSize*ChunkConfig.SIZE),Math.floorDiv(worldY,gp.tileSize*ChunkConfig.SIZE));return c!=null && c.state==State.ACTIVE;}
    public boolean loadedAt(int col,int row){if(!enabled())return true;return resident.contains(key(Math.floorDiv(col,ChunkConfig.SIZE),Math.floorDiv(row,ChunkConfig.SIZE)));}
    /** Rough compressed-reference estimate for global tile/region/grass arrays plus cached terrain pixels.
     * Excludes shared sprite assets and live entity objects; heap usage is reported independently. */
    public long estimatedTerrainBytes(){return gp.tileM.cacheBytes()+(long)gp.maxWorldCol*gp.maxWorldRow*128+(gp.generatedWorld==null?0:(long)(gp.generatedWorld.objects().size()+gp.generatedWorld.resources().size())*32);}
    public int activeEntities(){int n=0;for(var e:gp.monster)if(e!=null && gp.slimeSpawner.shouldSimulate(e))n++;for(var p:gp.players)if(p!=null && !p.isDead)n++;for(var e:gp.npc)if(e!=null && activeAt(e.worldX,e.worldY))n++;return n;}
    public int loaded(){return resident.size();}
    public long active(){return resident.stream().filter(k->plan.get(k).state==State.ACTIVE).count();}
    public int indexedEntities(){return moving.size();}
    public void drawGrid(Graphics2D g){if(!gp.performance.grid())return;Rectangle view=camera(0);int px=ChunkConfig.SIZE*gp.tileSize;for(Chunk c:plan.values()){
        Rectangle b=new Rectangle(c.x*px,c.y*px,px,px);if(!b.intersects(view))continue;g.setColor(switch(c.state){case ACTIVE->new Color(120,220,160,150);case READY->new Color(120,180,240,150);case DORMANT->new Color(230,180,90,150);default->new Color(230,100,100,150);});g.drawRect(b.x-view.x,b.y-view.y,px,px);g.drawString(c.x+","+c.y+" "+c.state,b.x-view.x+5,b.y-view.y+16);
    }}
}
