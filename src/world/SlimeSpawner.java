package world;

import entity.*;
import main.Gamepanel;
import monster.*;
import world.generation.EnvironmentRegion;
import java.awt.*;
import java.util.*;
import java.util.List;
import static world.SpawnDebugInfo.Reason;

/** Existing wilderness director: bounded reachable searches, snapshot investigation and separate nest budget. */
public final class SlimeSpawner {
    private final Gamepanel gp;
    private SplittableRandom random;
    private long clock,elapsed,quietUntil,nextCheck,respawnUntil;
    private final Map<Player,Point> lastSpawnPosition=new WeakHashMap<>();
    private final Map<Monster,Long> farSince=new WeakHashMap<>(),failedSince=new WeakHashMap<>();
    public final SpawnDebugInfo debug=new SpawnDebugInfo();
    private boolean overlay=Boolean.getBoolean("astral.encounters.debug"),fast=Boolean.getBoolean("astral.dev") && Boolean.getBoolean("astral.encounters.fast");
    private record Marker(int x,int y,long expires){}
    private final List<Marker> markers=new ArrayList<>();
    private final SkeletonHabitat skeletonHabitat;
    private long skeletonElapsed;
    private boolean skeletonOverlay=Boolean.getBoolean("astral.skeleton.debug");
    public String skeletonResult="Waiting",skeletonRegion="UNKNOWN";
    public double skeletonDensity;
    private final Map<String,Integer> skeletonRejected=new LinkedHashMap<>();
    public SlimeSpawner(Gamepanel gp){this.gp=gp;skeletonHabitat=new SkeletonHabitat(gp);}
    public boolean skeletonShade(int x,int y){return skeletonHabitat.shaded(x,y);}
    public int nearbySkeletons(Player p){int count=0;Rectangle area=cameraView(p,gp.tileSize*10);for(Monster m:gp.monster)if(m instanceof MON_Skeleton && living(m) && area.intersects(SlimeMotion.body(m,m.worldX,m.worldY)))count++;return count;}
    public int combined(Player p){int count=0;Rectangle area=cameraView(p,gp.tileSize*10);for(Monster m:gp.monster)if((ordinary(m)||m instanceof MON_Skeleton)&&living(m)&&area.intersects(SlimeMotion.body(m,m.worldX,m.worldY)))count++;return count;}
    private int skeletonCap(){
        if(!gp.lighting.cycle.night())return SkeletonConfig.CAP;
        return gp.lighting.cycle.fullMoon(SkeletonConfig.MOON_DAYS)?SkeletonConfig.NIGHT_MOON_CAP:SkeletonConfig.NIGHT_CAP;
    }
    private int combinedLimit(){return gp.lighting.cycle.night()?SkeletonConfig.NIGHT_COMBINED_CAP:SkeletonConfig.COMBINED_CAP;}

    private boolean active(){return gp.gameState==gp.playState && gp.worldConfig.procedural() && gp.generatedWorld!=null && (!gp.isNetworked || gp.isHost) && !gp.quests.inDungeon();}
    private void initialize(){if(random==null){random=new SplittableRandom(gp.worldConfig.seed() ^ 0x534C494D45534CL);schedule();}}
    public void resetPopulation(){
        skeletonElapsed=0;skeletonHabitat.reset();
        clock=elapsed=quietUntil=0;respawnUntil=EncounterSpawnConfig.RESPAWN_GRACE_SECONDS*60L;
        lastSpawnPosition.clear();lastSkeletonPosition.clear();farSince.clear();failedSince.clear();markers.clear();random=null;initialize();
        debug.checks=debug.totalSpawned=0;debug.begin();debug.checks=0;debug.result="Waiting for first check";debug.lastCol=debug.lastRow=-1;debug.lastDistance=0;debug.region="UNKNOWN";debug.nearby=0;debug.boss=false;
        if(gp.worldConfig.procedural() && (!gp.isNetworked || gp.isHost) && !gp.quests.inDungeon())gp.slimeNest.ensurePopulation(true);
    }
    private void schedule(){
        int high=EncounterSpawnConfig.intervalSeconds(gp.worldConfig.slimeRespawnSeconds())*60;
        int low=Math.max(5*60,high*2/3);nextCheck=fast?180:random.nextInt(low,Math.max(low,high)+1);
    }
    public void onRespawn(Player p){respawnUntil=Math.max(respawnUntil,clock+EncounterSpawnConfig.RESPAWN_GRACE_SECONDS*60L);lastSpawnPosition.remove(p);}
    public void onMonsterDefeated(Monster m){if(ordinary(m))onEncounterEnded();}
    public void onEncounterEnded(){quietUntil=Math.max(quietUntil,clock+EncounterSpawnConfig.KILL_GRACE_SECONDS*60L);}
    private boolean ordinary(Monster m){return m instanceof MON_GreenSlime slime && slime.nestId.isEmpty();}
    private boolean living(Monster m){return m!=null && m.alive && !m.dying && m.HP>0;}
    private int globalLimit(){return Math.min(gp.worldConfig.slimeCount(),Gamepanel.ORDINARY_MONSTER_CAP);}
    private int nearbyLimit(){return Math.min(EncounterSpawnConfig.NEARBY_CAP,gp.worldConfig.slimeCount());}
    public Rectangle cameraView(Player p,int margin){return new Rectangle(p.worldX-p.screenX-margin,p.worldY-p.screenY-margin,gp.screenWidth+2*margin,gp.screenHeight+2*margin);}
    private boolean inAnyView(Rectangle body,int margin){for(Player p:gp.players)if(SlimeMotion.activePlayer(gp,p) && cameraView(p,margin).intersects(body))return true;return false;}
    public Rectangle spriteBounds(Monster slime){
        SpriteAnimation animation=slime.sprites.get("idle_down");int w=animation.width,h=animation.height;
        return new Rectangle(slime.worldX+(gp.tileSize-w)/2,slime.worldY+(gp.tileSize-h)/2-TerrainHeightResolver.lift(gp,slime),w,h);
    }
    public boolean shouldSimulate(Monster m){
        // Allow one normal disengagement/reset tick before a distant active boss becomes dormant.
        if(m instanceof MON_CrownedSlime boss && boss.state!=MON_CrownedSlime.State.DORMANT)return true;
        if(!gp.quests.inDungeon() && !gp.chunks.activeAt(m.worldX,m.worldY) && !m.dying && !m.knockbackActive && m.statusEffects.isEmpty())return false;
        if(gp.quests.inDungeon() || !(m instanceof MON_GreenSlime slime) || slime.investigating() || m.dying || m.knockbackActive || !m.statusEffects.isEmpty())return true;
        return inAnyView(SlimeMotion.body(m,m.worldX,m.worldY),gp.tileSize*(EncounterSpawnConfig.MARGIN_TILES+EncounterSpawnConfig.RING_TILES+2));
    }
    public void update(){
        if(!active())return;clock++;initialize();
        if(gp.lighting.cycle.claimMoonWarning(SkeletonConfig.MOON_DAYS))gp.ui.addMessage("The moon shines strangely tonight. The dead grow restless.",new Color(190,160,175));
        int interval=skeletonInterval();
        if(++skeletonElapsed>=interval){skeletonElapsed=0;checkSkeleton(false);}

        if(clock%60==0)retireDistant();
        markers.removeIf(marker->marker.expires<=clock);
        if(gp.slimeNest.fighting()){elapsed=0;onEncounterEnded();debug.boss=true;debug.skip(Reason.BOSS_ENCOUNTER);return;}
        debug.boss=false;if(++elapsed<nextCheck)return;elapsed=0;check(false);schedule();
    }
    public int nearby(Player p){
        int n=0;Rectangle area=cameraView(p,gp.tileSize*(EncounterSpawnConfig.MARGIN_TILES+EncounterSpawnConfig.RING_TILES));
        for(Monster m:gp.monster)if(ordinary(m) && living(m) && area.intersects(SlimeMotion.body(m,m.worldX,m.worldY)))n++;return n;
    }
    private boolean protectedCell(int col,int row){return gp.generatedWorld.encounterProtected(col,row) || Math.hypot(col-gp.generatedWorld.spawnCol,row-gp.generatedWorld.spawnRow)<EncounterSpawnConfig.SAFE_TILES;}
    private void check(boolean certain){long performanceStart=gp.performance.start();try{
        initialize();debug.begin();
        if(!active()){debug.skip(Reason.INACTIVE);return;}
        gp.slimeNest.ensurePopulation();debug.boss=gp.slimeNest.fighting();
        if(debug.boss){debug.skip(Reason.BOSS_ENCOUNTER);return;}
        if(clock<respawnUntil){debug.skip(Reason.RESPAWN_GRACE);return;}
        if(clock<quietUntil){debug.skip(Reason.KILL_GRACE);return;}
        var players=new ArrayList<Player>();for(Player p:gp.players)if(SlimeMotion.activePlayer(gp,p))players.add(p);
        if(players.isEmpty()){debug.skip(Reason.INACTIVE);return;}
        Player p=players.get(random.nextInt(players.size()));int ts=gp.tileSize;
        int col=Math.floorDiv(SlimeMotion.centerX(p),ts),row=Math.floorDiv(SlimeMotion.centerY(p),ts);
        if(col<0 || row<0 || col>=gp.maxWorldCol || row>=gp.maxWorldRow){debug.skip(Reason.WORLD_BOUNDARY);return;}
        var regions=gp.generatedWorld.regions();EnvironmentRegion region=regions==null?EnvironmentRegion.GRASSLAND:regions.dominant(col,row);debug.region=region.name();debug.nearby=nearby(p);
        if(protectedCell(col,row)){debug.skip(Reason.PROTECTED_AREA);return;}
        var nest=gp.generatedWorld.slimeNest();if(nest!=null && nest.contains(col,row)){debug.skip(Reason.BOSS_ARENA);return;}
        Point previous=lastSpawnPosition.get(p);
        if(previous!=null && previous.distance(p.worldX,p.worldY)<ts*EncounterSpawnConfig.TRAVEL_TILES){debug.skip(Reason.IDLE_TRAVEL);return;}
        if(debug.nearby>=nearbyLimit() || combined(p)>=combinedLimit()){debug.skip(Reason.POPULATION_CAP);return;}
        if(!certain && !fast && random.nextDouble()>=Math.min(1,EncounterSpawnConfig.CHANCE*EncounterSpawnConfig.weight(region))){debug.skip(Reason.CHANCE_ROLL);return;}
        retireDistant();Reachable reach=reachable(p);
        int amount=Math.min(combinedLimit()-combined(p),Math.min(1+random.nextInt(2),nearbyLimit()-debug.nearby));
        Rectangle ring=cameraView(p,ts*(EncounterSpawnConfig.MARGIN_TILES+EncounterSpawnConfig.RING_TILES));
        int left=Math.max(1,Math.floorDiv(ring.x,ts)),right=Math.min(gp.maxWorldCol-2,Math.floorDiv(ring.x+ring.width-1,ts));
        int top=Math.max(1,Math.floorDiv(ring.y,ts)),bottom=Math.min(gp.maxWorldRow-2,Math.floorDiv(ring.y+ring.height-1,ts));
        if(left>right || top>bottom){debug.reject(Reason.WORLD_BOUNDARY);debug.finish();return;}
        MON_GreenSlime slime=null;
        for(int attempt=0;attempt<EncounterSpawnConfig.ATTEMPTS && debug.spawned<amount;attempt++){
            debug.attempts++;
            if(slime==null)slime=new MON_GreenSlime(gp);
            int c=random.nextInt(left,right+1),r=random.nextInt(top,bottom+1);slime.worldX=c*ts;slime.worldY=r*ts;
            Reason invalid=validateCandidate(slime,p,reach);
            if(invalid!=null){debug.reject(invalid);continue;}debug.valid++;
            int slot=availableSlot();if(slot<0){debug.reject(Reason.ENTITY_CAPACITY);break;}
            Point goal=investigationGoal(p,reach,c,r);slime.setHome();slime.investigate(goal.x*ts,goal.y*ts,EncounterSpawnConfig.INVESTIGATE_SECONDS*60);slime.delayAttacks(45);
            gp.monster[slot]=slime;markers.add(new Marker(slime.worldX,slime.worldY,clock+10*60));
            debug.lastCol=c;debug.lastRow=r;debug.lastDistance=SlimeMotion.distance(slime,p)/ts;debug.spawned++;slime=null;
        }
        if(debug.spawned>0){lastSpawnPosition.put(p,new Point(p.worldX,p.worldY));debug.nearby=nearby(p);}debug.finish();
    }finally{gp.performance.end(main.PerformanceMetrics.Section.ENCOUNTER,performanceStart);}
}
    private int availableSlot(){
        // Keep the existing global storage/boss reservation, but inactive distant enemies cannot monopolize it.
        int count=0;for(Monster m:gp.monster)if(ordinary(m))count++;
        if(count>=globalLimit()){
            for(int i=0;i<gp.monster.length;i++){Monster m=gp.monster[i];if(ordinary(m) && !shouldSimulate(m) && !inAnyView(spriteBounds((MON_GreenSlime)m),0)){retire(i);count--;break;}}
        }
        if(count>=globalLimit())return -1;
        int wilderness=0;for(Monster m:gp.monster)if(m!=null && !(m instanceof MON_CrownedSlime) && !(m instanceof MON_GreenSlime guard && !guard.nestId.isEmpty()))wilderness++;
        if(wilderness>=gp.monster.length-gp.slimeNest.reservedSlots())return -1;
        for(int i=0;i<gp.monster.length;i++)if(gp.monster[i]==null)return i;return -1;
    }
    private record Reachable(int left,int top,boolean[][] cells){boolean contains(int x,int y){int c=x-left,r=y-top;return c>=0 && r>=0 && c<cells.length && r<cells[0].length && cells[c][r];}}
    private Reachable reachable(Player p){
        int ts=gp.tileSize;Rectangle area=cameraView(p,ts*(EncounterSpawnConfig.MARGIN_TILES+EncounterSpawnConfig.RING_TILES+2));
        int left=Math.max(0,Math.floorDiv(area.x,ts)),top=Math.max(0,Math.floorDiv(area.y,ts));
        int right=Math.min(gp.maxWorldCol-1,Math.floorDiv(area.x+area.width-1,ts)),bottom=Math.min(gp.maxWorldRow-1,Math.floorDiv(area.y+area.height-1,ts));
        int width=Math.max(1,right-left+1),height=Math.max(1,bottom-top+1);boolean[][] seen=new boolean[width][height];byte[][] valid=new byte[width][height];var queue=new ArrayDeque<Integer>();
        int sx=Math.floorDiv(SlimeMotion.centerX(p),ts),sy=Math.floorDiv(SlimeMotion.centerY(p),ts);
        for(int y=Math.max(top,sy-1);y<=Math.min(bottom,sy+1);y++)for(int x=Math.max(left,sx-1);x<=Math.min(right,sx+1);x++)
            if(routeClear(x,y) && SlimeMotion.sight(gp,x*ts+27,y*ts+33,SlimeMotion.centerX(p),SlimeMotion.centerY(p))){seen[x-left][y-top]=true;queue.add((y-top)*width+x-left);}
        int[] dx={1,-1,0,0},dy={0,0,1,-1};
        while(!queue.isEmpty()){
            int at=queue.remove(),x=at%width,y=at/width;
            for(int i=0;i<4;i++){
                int c=x+dx[i],r=y+dy[i];if(c<0 || r<0 || c>=width || r>=height || seen[c][r])continue;
                if(valid[c][r]==0)valid[c][r]=(byte)(routeClear(c+left,r+top)?1:-1);
                if(valid[c][r]<0 || gp.tileM.buildingElevation(c+left,r+top)!=gp.tileM.buildingElevation(x+left,y+top))continue;
                // Body endpoints alone can miss a thin station/wall between two grid anchors.
                // Half-tile steps overlap the 30px-high body, covering the complete cardinal sweep.
                if(!SlimeMotion.clear(gp,new Rectangle((x+left)*ts+3+dx[i]*ts/2,(y+top)*ts+18+dy[i]*ts/2,48,30)))continue;
                seen[c][r]=true;queue.add(r*width+c);
            }
        }return new Reachable(left,top,seen);
    }
    private boolean routeClear(int col,int row){int ts=gp.tileSize;return SlimeMotion.clear(gp,new Rectangle(col*ts+3,row*ts+18,48,30));}
    private Point investigationGoal(Player p,Reachable reach,int col,int row){
        int ts=gp.tileSize,sx=Math.floorDiv(SlimeMotion.centerX(p),ts),sy=Math.floorDiv(SlimeMotion.centerY(p),ts);
        double length=Math.max(1,Math.hypot(col-sx,row-sy));double tx=sx+2*(col-sx)/length,ty=sy+2*(row-sy)/length;
        Point best=new Point(sx,sy);double distance=Double.MAX_VALUE;
        for(int y=sy-3;y<=sy+3;y++)for(int x=sx-3;x<=sx+3;x++)if(reach.contains(x,y)){double score=Math.hypot(x-tx,y-ty);if(score<distance){best=new Point(x,y);distance=score;}}return best;
    }
    private Reason validateCandidate(MON_GreenSlime slime,Player p,Reachable reach){return validateCandidate((Monster)slime,p,reach);}
    private Reason validateCandidate(Monster slime,Player p,Reachable reach){
        return validateCandidate(slime,p,reach,false);
    }
    private Reason validateCandidate(Monster slime,Player p,Reachable reach,boolean visibleEmergence){
        int ts=gp.tileSize;Rectangle body=SlimeMotion.body(slime,slime.worldX,slime.worldY);
        for(int row=Math.floorDiv(body.y,ts);row<=Math.floorDiv(body.y+body.height-1,ts);row++)for(int col=Math.floorDiv(body.x,ts);col<=Math.floorDiv(body.x+body.width-1,ts);col++){
            if(col<1 || row<1 || col>=gp.maxWorldCol-1 || row>=gp.maxWorldRow-1)return Reason.WORLD_BOUNDARY;
            if(!gp.chunks.loadedAt(col,row) || !gp.chunks.activeAt(col*ts,row*ts))return Reason.UNLOADED_CHUNK;
            var heights=gp.generatedWorld.heights();if(heights!=null && !heights.playable(col,row))return Reason.WORLD_BOUNDARY;
            var nest=gp.generatedWorld.slimeNest();if(nest!=null && nest.contains(col,row))return Reason.BOSS_ARENA;
            if(protectedCell(col,row))return Reason.PROTECTED_AREA;
            if(gp.buildings.coversGround(col,row))return Reason.BUILDING;
            var terrain=gp.tileM.terrain(col,row);if(terrain==art.Terrain.WATER || terrain==art.Terrain.DEEP)return Reason.WATER;
        }
        if(!visibleEmergence && inAnyView(spriteBounds(slime),ts*EncounterSpawnConfig.MARGIN_TILES))return Reason.INSIDE_CAMERA;
        if(gp.tileM.blocksTerrainMovement(body.x,body.y,body.x+body.width-1,body.y+body.height-1))return Reason.CLIFF_COLLISION;
        if(gp.buildings.blocksArea(body.x,body.y,body.width,body.height) || gp.crafting.blocks(body))return Reason.BUILDING;
        if(!SlimeMotion.clear(gp,body))return Reason.SOLID_OBJECT;
        if(!reach.contains(Math.floorDiv(slime.worldX,ts),Math.floorDiv(slime.worldY,ts)))return Reason.UNREACHABLE_LOCATION;
        Rectangle gap=new Rectangle(body);gap.grow(ts/2,ts/2);
        for(Player player:gp.players)if(SlimeMotion.activePlayer(gp,player) && SlimeMotion.distance(slime,player)<ts*3)return Reason.OCCUPIED;
        for(Monster other:gp.monster)if(living(other) && gap.intersects(SlimeMotion.body(other,other.worldX,other.worldY)))return Reason.OCCUPIED;
        return null;
    }
    private void retire(int index){Monster m=gp.monster[index];gp.monster[index]=null;farSince.remove(m);failedSince.remove(m);/* Retirement grants no loot/XP and does not signal a kill. */}
    private void retireDistant(){
        for(int i=0;i<gp.monster.length;i++){
            Monster m=gp.monster[i];
            if(m instanceof MON_Skeleton){if(!gp.chunks.activeAt(m.worldX,m.worldY)){long since=farSince.computeIfAbsent(m,key->clock);if(clock-since>=60*60)retire(i);}else farSince.remove(m);continue;}
            if(!ordinary(m) || m.dying || !m.statusEffects.isEmpty() || m.knockbackActive)continue;
            MON_GreenSlime slime=(MON_GreenSlime)m;
            boolean distant=!inAnyView(SlimeMotion.body(m,m.worldX,m.worldY),gp.tileSize*20);
            if(distant && (!slime.investigating() || !gp.chunks.activeAt(m.worldX,m.worldY))){
                long since=farSince.computeIfAbsent(m,key->clock);if(clock-since>=EncounterSpawnConfig.DESPAWN_SECONDS*60L){retire(i);continue;}
            }else farSince.remove(m);
            if(slime.investigationFailed && !inAnyView(spriteBounds(slime),0)){
                long since=failedSince.computeIfAbsent(m,key->clock);if(clock-since>=EncounterSpawnConfig.UNREACHABLE_SECONDS*60L)retire(i);
            }else failedSince.remove(m);
        }
    }
    private void rejectSkeleton(String reason){skeletonRejected.merge(reason,1,Integer::sum);skeletonResult="FAILED: "+reason;}
    private void checkSkeleton(boolean forced){
        long start=gp.performance.start();try{
            skeletonRejected.clear();skeletonResult="NO_VALID_TERRAIN";
            if(!active() || gp.isNetworked){rejectSkeleton("INACTIVE");return;}
            if(gp.slimeNest.fighting()){rejectSkeleton("BOSS_ENCOUNTER");return;}
            if(clock<respawnUntil || clock<quietUntil){rejectSkeleton("GRACE");return;}
            Player p=gp.localPlayer();if(!SlimeMotion.activePlayer(gp,p)){rejectSkeleton("INACTIVE");return;}
            int pc=Math.floorDiv(SlimeMotion.centerX(p),gp.tileSize),pr=Math.floorDiv(SlimeMotion.centerY(p),gp.tileSize);
            if(protectedCell(pc,pr)){rejectSkeleton("PROTECTED_AREA");return;}
            boolean night=gp.lighting.cycle.night();
            Point last=lastSkeletonPosition.get(p);if(!night && !forced && last!=null && last.distance(p.worldX,p.worldY)<gp.tileSize*4){rejectSkeleton("IDLE_TRAVEL");return;}
            int cap=skeletonCap();
            int budget=Math.min(cap-nearbySkeletons(p),combinedLimit()-combined(p));if(budget<=0){rejectSkeleton("POPULATION_CAP");return;}
            int count=0;for(Monster m:gp.monster)if(m!=null)count++;
            if(count>=gp.monster.length-gp.slimeNest.reservedSlots()){rejectSkeleton("ENTITY_CAPACITY");return;}
            if(!forced && random.nextDouble()>Math.min(1,(night?SkeletonConfig.NIGHT_CHANCE:SkeletonConfig.CHANCE)*EncounterSpawnConfig.weight(gp.generatedWorld.regions().dominant(pc,pr)))){rejectSkeleton("CHANCE_ROLL");return;}
            Reachable reach=reachable(p);Rectangle ring=cameraView(p,gp.tileSize*8);int ts=gp.tileSize,spawned=0;
            int wanted=Math.min(budget,night?random.nextInt(SkeletonConfig.NIGHT_GROUP_MIN,SkeletonConfig.NIGHT_GROUP_MAX+1):1+random.nextInt(2));MON_Skeleton skeleton=new MON_Skeleton(gp);
            for(int i=0;i<(night?56:28) && spawned<wanted;i++){
                int x,y;
                if(night){
                    int outer=Math.max(SkeletonConfig.EMERGE_MIN,SkeletonConfig.EMERGE_MAX);
                    x=pc+random.nextInt(-outer,outer+1);y=pr+random.nextInt(-outer,outer+1);
                    double distance=Math.hypot(x-pc,y-pr);
                    if(distance<SkeletonConfig.EMERGE_MIN || distance>outer){rejectSkeleton("EMERGENCE_DISTANCE");continue;}
                }else{x=Math.floorDiv(ring.x,ts)+random.nextInt(Math.max(1,ring.width/ts));y=Math.floorDiv(ring.y,ts)+random.nextInt(Math.max(1,ring.height/ts));}
                if(x<1||y<1||x>=gp.maxWorldCol-1||y>=gp.maxWorldRow-1){rejectSkeleton("WORLD_BOUNDARY");continue;}
                skeleton.worldX=x*ts;skeleton.worldY=y*ts;
                // Night skeletons visibly crawl out before becoming dangerous; slimes stay off-screen.
                Reason failure=validateCandidate(skeleton,p,reach,night);if(failure!=null){rejectSkeleton(failure.name());continue;}
                skeletonDensity=skeletonHabitat.coverage(x,y);skeletonRegion=gp.generatedWorld.regions().dominant(x,y).name();
                if(!gp.lighting.cycle.night() && skeletonDensity<SkeletonConfig.FOREST_COVER){rejectSkeleton("OPEN_DAYLIGHT");continue;}
                int slot=-1;int occupied=0;for(int n=0;n<gp.monster.length;n++){if(gp.monster[n]!=null)occupied++;else if(slot<0)slot=n;}
                if(slot<0 || occupied>=gp.monster.length-gp.slimeNest.reservedSlots()){rejectSkeleton("ENTITY_CAPACITY");break;}
                skeleton.investigate(p.worldX,p.worldY);skeleton.refreshEnrage();gp.monster[slot]=skeleton;spawned++;skeleton=new MON_Skeleton(gp);
            }
            if(spawned>0){skeletonResult="SUCCESS: "+spawned;lastSkeletonPosition.put(p,new Point(p.worldX,p.worldY));}
        }finally{gp.performance.end(main.PerformanceMetrics.Section.ENCOUNTER,start);}
    }
    private final Map<Player,Point> lastSkeletonPosition=new WeakHashMap<>();
    private int skeletonInterval(){int base=gp.lighting.cycle.night()?SkeletonConfig.NIGHT_INTERVAL:SkeletonConfig.INTERVAL;return Math.max(1,(int)(base/(gp.lighting.cycle.fullMoon(SkeletonConfig.MOON_DAYS)?SkeletonConfig.MOON_FREQUENCY:1)));}
    public String skeletonCommand(String argument){
        if(!Boolean.getBoolean("astral.dev"))return "Enable -Dastral.dev=true first";
        if(argument.equals("now")){initialize();gp.chunks.update();checkSkeleton(true);}else skeletonOverlay=!argument.equals("off");return skeletonResult;
    }
    public void drawSkeletonDebug(Graphics2D original){
        if(!Boolean.getBoolean("astral.dev")||!skeletonOverlay)return;Graphics2D g=(Graphics2D)original.create();try{
            Player p=gp.localPlayer();var cycle=gp.lighting.cycle;g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));g.setColor(new Color(30,29,39,225));g.fillRoundRect(gp.screenWidth-440,240,425,245,10,10);g.setColor(new Color(240,226,209));
            MON_Skeleton sample=null;for(Monster m:gp.monster)if(m instanceof MON_Skeleton s && gp.chunks.activeAt(m.worldX,m.worldY)){sample=s;break;}
            String[] lines={"SKELETON · RESTLESS DEAD","World Day "+cycle.day()+"   "+cycle.clock(),"Moon: "+(cycle.fullMoon(SkeletonConfig.MOON_DAYS)?"FULL / ENRAGED":"normal"),"Nearby: "+nearbySkeletons(p)+" / "+skeletonCap(),"Region: "+skeletonRegion+"   tree coverage: "+(int)(skeletonDensity*100)+"%","Next check: "+Math.max(0,(skeletonInterval()-skeletonElapsed)/60)+"s",skeletonResult,"Rejected: "+skeletonRejected,"Shade computations: "+skeletonHabitat.calculations,sample==null?"No active skeleton":"AI: "+sample.state+" / "+sample.currentAttack,sample==null?"":"Spawn day "+sample.spawnDay+" HP x"+String.format(java.util.Locale.ROOT,"%.2f",SkeletonConfig.hpMultiplier(sample.spawnDay))+" DMG x"+String.format(java.util.Locale.ROOT,"%.2f",SkeletonConfig.damageMultiplier(sample.spawnDay))};
            int y=260;for(String line:lines){g.drawString(line,gp.screenWidth-428,y);y+=19;}
        }finally{g.dispose();}
    }
    public String debugCommand(String argument){
        if(!Boolean.getBoolean("astral.dev"))return "Enable -Dastral.dev=true for encounter diagnostics.";
        if(argument.equals("now"))gp.chunks.update();
        initialize();switch(argument){
            case "fast"->{fast=true;overlay=true;elapsed=0;schedule();}
            case "normal"->{fast=false;elapsed=0;schedule();}
            case "now"->{check(true);elapsed=0;schedule();}
            case "on"->overlay=true;
            case "off"->overlay=false;
            default->overlay=!overlay;
        }
        return String.join(" | ",debugLines());
    }
    public List<String> debugLines(){
        Player p=gp.localPlayer();int count=p==null?0:nearby(p);String region=debug.region;
        if(p!=null && gp.generatedWorld!=null && gp.generatedWorld.regions()!=null){
            int col=Math.floorDiv(SlimeMotion.centerX(p),gp.tileSize),row=Math.floorDiv(SlimeMotion.centerY(p),gp.tileSize);
            if(col>=0 && row>=0 && col<gp.maxWorldCol && row<gp.maxWorldRow)region=gp.generatedWorld.regions().dominant(col,row).name();
        }
        return List.of("ENCOUNTERS"+(fast?" · FAST":""),"Region: "+region+"   Nearby: "+count+" / "+nearbyLimit(),String.format(Locale.ROOT,"Next roll: %.1fs   Boss: %s",Math.max(0,nextCheck-elapsed)/60.0,gp.slimeNest.fighting()?"ACTIVE":"inactive"),debug.result,"Candidates: "+debug.attempts+"   Valid: "+debug.valid,debug.lastCol<0?"Last spawn: none":String.format(Locale.ROOT,"Last: (%d, %d)  %.1f tiles",debug.lastCol,debug.lastRow,debug.lastDistance),"Rejected: "+debug.rejected,"Checks: "+debug.checks+"   Spawned: "+debug.totalSpawned);
    }
    public void drawMarkers(Graphics2D g){
        if(!Boolean.getBoolean("astral.dev") || !overlay || gp.localPlayer()==null)return;Player p=gp.localPlayer();
        Color color=g.getColor();g.setColor(new Color(215,194,155));
        for(Marker marker:markers){
            int lift=TerrainHeightResolver.active(gp)?gp.generatedWorld.heights().at(Math.floorDiv(marker.x,gp.tileSize),Math.floorDiv(marker.y,gp.tileSize))*gp.tileSize/2:0;
            int x=marker.x-p.worldX+p.screenX+gp.tileSize/2,y=marker.y-p.worldY+p.screenY+gp.tileSize/2-lift;
            g.drawOval(x-12,y-8,24,16);g.drawLine(x-16,y,x+16,y);
        }g.setColor(color);
    }
    public void drawDebug(Graphics2D original){
        if(!Boolean.getBoolean("astral.dev") || !overlay || gp.inventoryOpen || gp.optionsOpen)return;
        Graphics2D g=(Graphics2D)original.create();int x=gp.screenWidth-438,y=230;List<String> lines=new ArrayList<>();
        for(String text:debugLines()){
            while(text.length()>60){lines.add(text.substring(0,60));text=text.substring(60);}lines.add(text);
        }
        int height=Math.min(gp.screenHeight-y-60,lines.size()*17+24);ui.UITheme.panel(g,new Rectangle(x,y,418,height));
        for(int i=0;i<lines.size() && i*17+24<height;i++)ui.UITheme.text(g,lines.get(i),x+12,y+22+i*17,10,ui.UITheme.TEXT);
        g.dispose();
    }
}
