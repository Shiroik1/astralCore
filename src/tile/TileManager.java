package tile;

import art.GroundTiles;
import art.Palette;
import art.Terrain;
import entity.Player;
import main.Gamepanel;
import main.Log;
import world.InnInterior;
import world.TownMapGenerator;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileManager implements GroundTiles.World {
    private static final String TERRAIN_PATH = "maps/terrain.txt";
    private static final String LEVELS_PATH = "maps/levels.txt";
    private static final int CHUNK = world.chunk.ChunkConfig.SIZE; // tiles per chunk side

    private final Gamepanel gp;
    private final GroundTiles ground;
    private Terrain[][] terrainGrid;   // [col][row]
    private int[][] levelGrid;
    private final Map<Long, BufferedImage> chunks = new java.util.LinkedHashMap<>(16,.75f,true);
    private final Map<Long,java.util.List<world.CliffLayer>> cliffs=new HashMap<>();
    private final java.util.concurrent.ThreadPoolExecutor baker=new java.util.concurrent.ThreadPoolExecutor(1,1,10,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"terrain-cache");t.setDaemon(true);return t;});
    private final Map<Long,java.util.concurrent.Future<Visual>> pending=new HashMap<>();
    private record Edge(int col,int row,BufferedImage image){}
    private record Visual(BufferedImage ground,List<Edge> edges,long nanos){}
    private record Snapshot(Terrain[][] terrain,int[][] levels,world.generation.GeneratedWorld plan,int width,int height) implements GroundTiles.World {
        public Terrain terrain(int x,int y){return terrain[Math.max(0,Math.min(width-1,x))][Math.max(0,Math.min(height-1,y))];}
        public int level(int x,int y){return levels[Math.max(0,Math.min(width-1,x))][Math.max(0,Math.min(height-1,y))];}
        public int grassVariant(int x,int y){return plan==null?GroundTiles.World.super.grassVariant(x,y):plan.groundDetail(x,y);}
    }
    private Snapshot snapshot(){return new Snapshot(terrainGrid,levelGrid,gp.generatedWorld,gp.maxWorldCol,gp.maxWorldRow);}
    public int cachedChunks(){return chunks.size();}
    public long cacheBytes(){long n=0;for(var image:chunks.values())n+=(long)image.getWidth()*image.getHeight()*4;for(var list:cliffs.values())for(var layer:list)n+=layer.cacheBytes();return n;}
    public void releaseChunk(int x,int y){long key=chunkKey(x,y);chunks.remove(key);cliffs.remove(key);var task=pending.remove(key);if(task!=null)task.cancel(false);}
    private BufferedImage exteriorTile;

    public TileManager(Gamepanel gp) throws IOException {
        this.gp = gp;
        baker.allowCoreThreadTimeOut(true);
        this.ground = new GroundTiles(gp.tileSize);
        if(gp.generatedWorld != null) applyGenerated(gp.generatedWorld);
        else {
            loadMap();
            bakeAll();
        }
    }

    public void applyGenerated(world.generation.GeneratedWorld world){
        gp.maxWorldCol = world.config.width();
        gp.maxWorldRow = world.config.height();
        terrainGrid = new Terrain[gp.maxWorldCol][gp.maxWorldRow];
        levelGrid = new int[gp.maxWorldCol][gp.maxWorldRow];
        for(int c = 0; c < gp.maxWorldCol; c++) for(int r = 0; r < gp.maxWorldRow; r++){
            terrainGrid[c][r] = world.terrain(c,r);
            levelGrid[c][r] = world.tone(c,r);
        }
        chunks.clear();
        bakeAll();
    }

    public void reloadAuthored(){
        try{loadMap();chunks.clear();bakeAll();}
        catch(IOException e){throw new IllegalStateException("Could not restore authored terrain",e);}
    }
    private static long chunkKey(int col,int row){return ((long)row << 32) | (col & 0xffffffffL);}

    private void loadMap() throws IOException {
        if(!main.AppPaths.mapFile(TERRAIN_PATH).isFile() && getClass().getResource("/maps/terrain.txt") == null){
            Log.warn("[MAP] no terrain.txt found, generating the starter town");
            TownMapGenerator.writeFiles();
        }

        List<String> terrainLines = readLines(TERRAIN_PATH, "/maps/terrain.txt");
        List<String> levelLines = readLines(LEVELS_PATH, "/maps/levels.txt");

        int rows = terrainLines.size();
        int cols = terrainLines.get(0).length();
        gp.maxWorldCol = cols;
        gp.maxWorldRow = rows;

        terrainGrid = new Terrain[cols][rows];
        levelGrid = new int[cols][rows];
        for(int r = 0; r < rows; r++){
            String line = terrainLines.get(r);
            String levels = (r < levelLines.size()) ? levelLines.get(r) : "";
            for(int c = 0; c < cols; c++){
                terrainGrid[c][r] = parse(c < line.length() ? line.charAt(c) : '.');
                char lv = c < levels.length() ? levels.charAt(c) : '0';
                levelGrid[c][r] = Math.max(0, Math.min(GroundTiles.LEVELS - 1, lv - '0'));
            }
        }
    }

    private static Terrain parse(char ch){
        return switch(ch){
            case '=' -> Terrain.PATH;
            case '#' -> Terrain.COBBLE;
            case 'X' -> Terrain.DEEP;
            case '~' -> Terrain.WATER;
            case 'T' -> Terrain.TERRACE;
            case 'R' -> Terrain.RIM;
            case '^' -> Terrain.LEDGE;
            case '+' -> Terrain.STEPS;
            default -> Terrain.GRASS;
        };
    }

    // a file next to the game wins over a bundled copy, so hand edits take effect on the next launch
    private List<String> readLines(String externalPath, String resourcePath) throws IOException {
        String text;
        File f = main.AppPaths.mapFile(externalPath);
        if(f.isFile()){
            text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } else {
            try(InputStream is = getClass().getResourceAsStream(resourcePath)){
                if(is == null) throw new IOException("Missing map file: " + externalPath);
                text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        List<String> lines = new ArrayList<>();
        for(String line : text.split("\\R")){
            if(!line.isBlank()) lines.add(line.stripTrailing());
        }
        return lines;
    }

    //WORLD INTERFACE (read by GroundTiles; out-of-range cells return the nearest valid cell)
    @Override
    public Terrain terrain(int col, int row){
        return terrainGrid[Math.max(0, Math.min(gp.maxWorldCol - 1, col))][Math.max(0, Math.min(gp.maxWorldRow - 1, row))];
    }

    @Override
    public int level(int col, int row){
        return levelGrid[Math.max(0, Math.min(gp.maxWorldCol - 1, col))][Math.max(0, Math.min(gp.maxWorldRow - 1, row))];
    }

    @Override public int grassVariant(int col,int row){
        return gp.generatedWorld==null?GroundTiles.World.super.grassVariant(col,row):gp.generatedWorld.groundDetail(col,row);
    }

    /** Physical generated heights in v9; legacy authored terraces retain their existing elevation convention. */
    public int buildingElevation(int col,int row){
        if(world.TerrainHeightResolver.active(gp))return gp.generatedWorld.heights().at(col,row);
        return switch(terrain(col,row)){
            case TERRACE, RIM, LEDGE, STEPS -> level(col,row);
            default -> 0;
        };
    }

    // Coarse tile query for spawn placement. Movement uses the partial terrace boundaries below.
    public boolean isSolid(int col, int row){
        if(gp.quests!=null && gp.quests.inDungeon())return gp.quests.isDungeonSolid(col,row);
        if(!gp.worldConfig.procedural() && InnInterior.isTile(col, row)){
            int x = col * gp.tileSize + gp.tileSize / 2;
            int y = row * gp.tileSize + gp.tileSize / 2;
            return InnInterior.blocksMovement(gp, x, y, x, y);
        }
        if(col < 0 || row < 0 || col >= gp.maxWorldCol || row >= gp.maxWorldRow) return true;
        if(world.TerrainHeightResolver.active(gp) && !gp.generatedWorld.heights().playable(col,row))return true;
        return switch (terrainGrid[col][row]) {
            case DEEP, WATER, RIM, LEDGE -> true;
            default -> false;
        };
    }

    public boolean blocksMovement(int left, int top, int right, int bottom){
        if(gp.buildings!=null && gp.buildings.blocksArea(left,top,right-left+1,bottom-top+1))return true;
        return blocksTerrainMovement(left,top,right,bottom);
    }

    public boolean blocksTerrainMovement(int left,int top,int right,int bottom){
        int ts = gp.tileSize;
        boolean dungeon = gp.quests != null && gp.quests.inDungeon();
        if(!dungeon && InnInterior.containsPosition(gp, left, top)){
            return InnInterior.blocksMovement(gp, left, top, right, bottom);
        }
        if(!dungeon && world.TerrainHeightResolver.active(gp) && world.ElevationCollisionHandler.blocks(gp.generatedWorld.heights(),ts,left,top,right,bottom))return true;
        int inset = ts * GroundTiles.GRASS_INSET / GroundTiles.ART_TILE_SIZE;
        for (int row = Math.floorDiv(top, ts); row <= Math.floorDiv(bottom, ts); row++) {
            for (int col = Math.floorDiv(left, ts); col <= Math.floorDiv(right, ts); col++) {
                if (!isSolid(col, row)) continue;
                if (dungeon || col < 0 || row < 0 || col >= gp.maxWorldCol || row >= gp.maxWorldRow) return true;

                int x = col * ts;
                int y = row * ts;
                if (terrainGrid[col][row] == Terrain.LEDGE) {
                    // The grassy upper half is walkable. The face and exposed ends stop movement.
                    if (bottom >= y + ts / 2) return true;
                    int joins = GroundTiles.ledgeConnections(this, col, row);
                    if (left < x + inset && (joins & GroundTiles.LEDGE_LEFT) == 0
                            || right >= x + ts - inset && (joins & GroundTiles.LEDGE_RIGHT) == 0) return true;
                } else if (terrainGrid[col][row] == Terrain.RIM) {
                    int level = levelGrid[col][row];
                    if (left < x + inset && !GroundTiles.joinsRaised(this, col - 1, row, level)
                            || right >= x + ts - inset && !GroundTiles.joinsRaised(this, col + 1, row, level)
                            || top < y + inset && !GroundTiles.joinsRaised(this, col, row - 1, level)
                            || bottom >= y + ts - inset && !GroundTiles.joinsRaised(this, col, row + 1, level)) return true;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    //BAKING: the ground never changes, so draw it once into 16x16-tile images
    private void bakeAll(){
        cliffs.clear();
        exteriorTile=new BufferedImage(gp.tileSize,gp.tileSize,BufferedImage.TYPE_INT_RGB);
        var exterior=exteriorTile.createGraphics();ground.drawCell(exterior,this,gp.maxWorldCol-1,gp.maxWorldRow-1,0,0);exterior.dispose();
        for(var task:pending.values())task.cancel(false);pending.clear();baker.purge();
        // Static imagery is prepared around the camera, not across the whole world at startup.
    }
    private Visual prepare(int cx,int cy,Snapshot view){
        long at=System.nanoTime();BufferedImage image=bakeChunk(cx,cy,view);List<Edge> edges=new ArrayList<>();var w=view.plan;
        if(w!=null && w.heights()!=null)for(int y=cy*CHUNK;y<Math.min(view.height,(cy+1)*CHUNK);y++)for(int x=cx*CHUNK;x<Math.min(view.width,(cx+1)*CHUNK);x++)if(world.generation.CliffTileResolver.edge(w.heights(),x,y))edges.add(new Edge(x,y,world.CliffLayer.bakeImage(w,ground,view,x,y,gp.tileSize)));
        return new Visual(image,edges,System.nanoTime()-at);
    }
    private void install(int x,int y,Visual visual){
        long key=chunkKey(x,y);chunks.put(key,visual.ground);List<world.CliffLayer> layers=new ArrayList<>();
        for(Edge edge:visual.edges)layers.add(new world.CliffLayer(gp,gp.generatedWorld,edge.col,edge.row,edge.image));cliffs.put(key,layers);
        if(gp.performance!=null)gp.performance.end(main.PerformanceMetrics.Section.CHUNK,System.nanoTime()-visual.nanos);
    }
    /** Called on the game thread before rendering. Visible misses wait; preloads use one bounded worker. */
    public void prepareView(){
        var camera=gp.localPlayer();if(camera==null || camera.inWell)return;int px=CHUNK*gp.tileSize;
        int left=Math.max(0,Math.floorDiv(camera.worldX-camera.screenX-gp.tileSize,px)),right=Math.min((gp.maxWorldCol-1)/CHUNK,Math.floorDiv(camera.worldX-camera.screenX+gp.screenWidth+gp.tileSize,px));
        int top=Math.max(0,Math.floorDiv(camera.worldY-camera.screenY-gp.tileSize,px)),bottom=Math.min((gp.maxWorldRow-1)/CHUNK,Math.floorDiv(camera.worldY-camera.screenY+gp.screenHeight+gp.tileSize*(gp.generatedWorld==null?2:gp.generatedWorld.elevationConfig.maxHeight()+1),px));
        java.util.Set<Long> visible=new java.util.HashSet<>();
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)visible.add(chunkKey(x,y));
        int limit=Math.max(world.chunk.ChunkConfig.IMAGE_LIMIT,visible.size());
        int look=gp.tileSize*8,fx=camera.direction.equals("right")?look:camera.direction.equals("left")?-look:0,fy=camera.direction.equals("down")?look:camera.direction.equals("up")?-look:0;
        double centerX=camera.worldX-camera.screenX+gp.screenWidth/2.0+fx,centerY=camera.worldY-camera.screenY+gp.screenHeight/2.0+fy;
        java.util.List<Point> neighbors=new ArrayList<>();
        for(int y=Math.max(0,top-1);y<=Math.min((gp.maxWorldRow-1)/CHUNK,bottom+1);y++)for(int x=Math.max(0,left-1);x<=Math.min((gp.maxWorldCol-1)/CHUNK,right+1);x++)if(!visible.contains(chunkKey(x,y)))neighbors.add(new Point(x,y));
        // Keep the useful part of the preload ring moving even when the image cache is full.
        // A small retention bias prevents nearest-neighbor ties churning at a chunk boundary.
        neighbors.sort(java.util.Comparator.comparingDouble(p->{double dx=(p.x+.5)*px-centerX,dy=(p.y+.5)*px-centerY;return dx*dx+dy*dy-(chunks.containsKey(chunkKey(p.x,p.y))?px*px/9.0:0);}));
        java.util.Set<Long> desired=new java.util.HashSet<>(visible);for(Point point:neighbors){if(desired.size()>=limit)break;desired.add(chunkKey(point.x,point.y));}
        var obsolete=pending.entrySet().iterator();while(obsolete.hasNext()){var entry=obsolete.next();if(!desired.contains(entry.getKey())){entry.getValue().cancel(false);obsolete.remove();}}baker.purge();
        var done=pending.entrySet().iterator();while(done.hasNext()){var entry=done.next();if(!entry.getValue().isDone())continue;try{long k=entry.getKey();install((int)k,(int)(k>>32),entry.getValue().get());}catch(Exception e){throw new IllegalStateException("Terrain preparation failed",e);}done.remove();}
        Snapshot view=snapshot();
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){long key=chunkKey(x,y);if(chunks.containsKey(key)){chunks.get(key);continue;}
            var task=pending.remove(key);try{install(x,y,task==null?prepare(x,y,view):task.get());}catch(Exception e){throw new IllegalStateException("Visible terrain failed",e);}
        }
        for(long key:new ArrayList<>(chunks.keySet()))if(!desired.contains(key)){chunks.remove(key);cliffs.remove(key);}
        // One worker and a two-entry queue; results only enter live caches at this boundary.
        for(Point point:neighbors){long key=chunkKey(point.x,point.y);if(pending.size()>=2)break;if(desired.contains(key) && !chunks.containsKey(key) && !pending.containsKey(key))pending.put(key,baker.submit(()->prepare(point.x,point.y,view)));}
    }


    private BufferedImage bakeChunk(int cx, int cy,Snapshot view){
        int ts = gp.tileSize;
        int tilesW = Math.min(CHUNK, view.width - cx * CHUNK);
        int tilesH = Math.min(CHUNK, view.height - cy * CHUNK);
        BufferedImage img = new BufferedImage(tilesW * ts, tilesH * ts, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for(int r = 0; r < tilesH; r++){
            for(int c = 0; c < tilesW; c++){
                ground.drawCell(g, view, cx * CHUNK + c, cy * CHUNK + r, c * ts, r * ts);
            }
        }
        if(view.plan!=null && view.plan.heights()!=null){
            var w=view.plan;int max=w.elevationConfig.maxHeight();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int left=Math.max(0,cx*CHUNK-1),right=Math.min(view.width-1,cx*CHUNK+tilesW);
            int top=Math.max(0,cy*CHUNK-1),bottom=Math.min(view.height-1,cy*CHUNK+tilesH+(max+1)/2+1);
            if(w.generatorVersion>=10){
                for(int row=top;row<=bottom;row++)for(int col=left;col<=right;col++)if(w.heights().at(col,row)>0 && !world.generation.CliffTileResolver.edge(w.heights(),col,row))
                    world.generation.CliffTileResolver.drawTop(g,ground,view,w,col,row,w.heights().at(col,row),ts,(col-cx*CHUNK)*ts,(row-cy*CHUNK)*ts);
            }else for(int step=1;step<=max;step++){
                for(int row=top;row<=bottom;row++)for(int col=left;col<=right;col++)if(w.heights().at(col,row)>=step && !world.generation.CliffTileResolver.edge(w.heights(),col,row))
                    world.generation.CliffTileResolver.drawFace(g,ground,w,col,row,step,ts,(col-cx*CHUNK)*ts,(row-cy*CHUNK)*ts);
                for(int row=top;row<=bottom;row++)for(int col=left;col<=right;col++)if(w.heights().at(col,row)>=step && !world.generation.CliffTileResolver.edge(w.heights(),col,row))
                    world.generation.CliffTileResolver.drawTop(g,ground,view,w,col,row,step,ts,(col-cx*CHUNK)*ts,(row-cy*CHUNK)*ts);
            }
        }
        g.dispose();
        return img;
    }

    public void addCliffLayers(java.util.List<entity.Entity> layers){
        if(!world.TerrainHeightResolver.active(gp))return;
        var p=gp.localPlayer();int px=CHUNK*gp.tileSize;
        int left=Math.max(0,Math.floorDiv(p.worldX-p.screenX-gp.tileSize,px));
        int right=Math.min((gp.maxWorldCol-1)/CHUNK,Math.floorDiv(p.worldX-p.screenX+gp.screenWidth+gp.tileSize,px));
        int top=Math.max(0,Math.floorDiv(p.worldY-p.screenY-gp.tileSize,px));
        int bottom=Math.min((gp.maxWorldRow-1)/CHUNK,Math.floorDiv(p.worldY-p.screenY+gp.screenHeight+gp.generatedWorld.elevationConfig.maxHeight()*gp.tileSize,px));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){var strip=cliffs.get(chunkKey(x,y));if(strip!=null)for(var layer:strip)if(layer.intersects(gp.chunks.camera(1)))layers.add(layer);}
    }

    public void draw(Graphics2D g2){
        if(gp.quests.inDungeon()){gp.quests.drawDungeon(g2);return;}
        Player camera = gp.localPlayer();
        if(InnInterior.contains(gp, camera)){InnInterior.draw(g2, gp, camera);return;}
        int viewLeft = camera.worldX - camera.screenX;
        int viewTop = camera.worldY - camera.screenY;
        int ts = gp.tileSize;

        int mapW = gp.maxWorldCol * ts, mapH = gp.maxWorldRow * ts;
        if(viewLeft - ts < 0 || viewTop - ts < 0 || viewLeft + gp.screenWidth + ts > mapW || viewTop + gp.screenHeight + ts > mapH){
            g2.setColor(Palette.GROUND[GroundTiles.LEVELS - 1]);
            g2.fillRect(-ts * 2, -ts * 2, gp.screenWidth + ts * 4, gp.screenHeight + ts * 4);
            if(world.TerrainHeightResolver.active(gp)){
                for(int y=Math.floorDiv(viewTop,ts)-1;y<=(viewTop+gp.screenHeight)/ts+1;y++)
                    for(int x=Math.floorDiv(viewLeft,ts)-1;x<=(viewLeft+gp.screenWidth)/ts+1;x++)
                        if(x<0 || y<0 || x>=gp.maxWorldCol || y>=gp.maxWorldRow)
                            g2.drawImage(exteriorTile,x*ts-viewLeft,y*ts-viewTop,null);
            }
        }

        gp.performance.visibleTiles=Math.max(0,Math.min(gp.maxWorldCol,(viewLeft+gp.screenWidth)/ts+1)-Math.max(0,Math.floorDiv(viewLeft,ts)))*Math.max(0,Math.min(gp.maxWorldRow,(viewTop+gp.screenHeight)/ts+1)-Math.max(0,Math.floorDiv(viewTop,ts)));
        int chunkPx = CHUNK * ts;
        int firstCx = Math.max(0, Math.floorDiv(viewLeft - ts, chunkPx));
        int lastCx = Math.min((gp.maxWorldCol - 1) / CHUNK, Math.floorDiv(viewLeft + gp.screenWidth + ts, chunkPx));
        int firstCy = Math.max(0, Math.floorDiv(viewTop - ts, chunkPx));
        int lastCy = Math.min((gp.maxWorldRow - 1) / CHUNK, Math.floorDiv(viewTop + gp.screenHeight + ts, chunkPx));

        for(int cy = firstCy; cy <= lastCy; cy++){
            for(int cx = firstCx; cx <= lastCx; cx++){
                BufferedImage img = chunks.get(chunkKey(cx, cy));
                if(img == null) continue;
                g2.drawImage(img, cx * chunkPx - camera.worldX + camera.screenX,
                        cy * chunkPx - camera.worldY + camera.screenY, null);
            }
        }
        world.generation.RegionDebug.draw(gp,g2);
    }
}
