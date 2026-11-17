package main;

import java.awt.*;
import java.util.Locale;

/** Allocation-free timing counters; overlay and console output are explicitly development-only. */
public final class PerformanceMetrics {
    public enum Section {UPDATE,WORLD,ENTITY,AI,COLLISION,INTERACTION,ENCOUNTER,TERRAIN_RENDER,ENTITY_RENDER,UI,PARTICLES,LIGHTING,CHUNK,STREAMING,SAVE,PRESENT,PAINT,RENDER}
    private final long[] sum=new long[Section.values().length],max=new long[sum.length],count=new long[sum.length];
    private final java.util.concurrent.atomic.AtomicLong paintSum=new java.util.concurrent.atomic.AtomicLong(),paintMax=new java.util.concurrent.atomic.AtomicLong(),paintCount=new java.util.concurrent.atomic.AtomicLong();
    private boolean overlay=Boolean.getBoolean("astral.dev") && Boolean.getBoolean("astral.performance.debug"),grid;
    public int visibleTiles,visibleEntities;
    public long start(){return System.nanoTime();}
    public void end(Section s,long start){long elapsed=System.nanoTime()-start;if(s==Section.PAINT){paintSum.addAndGet(elapsed);paintMax.accumulateAndGet(elapsed,Math::max);paintCount.incrementAndGet();return;}int i=s.ordinal();sum[i]+=elapsed;max[i]=Math.max(max[i],elapsed);count[i]++;}
    public double mean(Section s){if(s==Section.PAINT)return paintSum.get()/Math.max(1.0,paintCount.get())/1e6;int i=s.ordinal();return count[i]==0?0:sum[i]/(double)count[i]/1e6;}
    public double peak(Section s){if(s==Section.PAINT)return paintMax.get()/1e6;return max[s.ordinal()]/1e6;}
    public void reset(){paintSum.set(0);paintMax.set(0);paintCount.set(0);java.util.Arrays.fill(sum,0);java.util.Arrays.fill(max,0);java.util.Arrays.fill(count,0);}
    public boolean grid(){return grid && Boolean.getBoolean("astral.dev");}
    public String command(String argument){if(!Boolean.getBoolean("astral.dev"))return "Enable -Dastral.dev=true first";switch(argument){case "on"->overlay=true;case "off"->{overlay=false;grid=false;}case "grid"->{overlay=true;grid=!grid;}case "reset"->reset();default->overlay=!overlay;}return "Performance overlay "+(overlay?"ON":"OFF")+"; chunk grid "+(grid?"ON":"OFF");}
    public void draw(Gamepanel gp,Graphics2D g){
        if(!overlay)return;var p=gp.localPlayer();int size=world.chunk.ChunkConfig.SIZE;Point feet=gp.interactions.validator.tile(p);
        int x=12,y=72;g.setColor(new Color(25,29,31,218));g.fillRoundRect(x,y,435,475,10,10);g.setColor(new Color(245,240,221));g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        java.util.List<String> lines=new java.util.ArrayList<>();lines.add("ASTRALCORE PERFORMANCE (mean / peak ms)");lines.add("Display FPS: "+gp.fpsCounter+"   period: "+(gp.fpsCounter==0?"unmeasured":String.format(Locale.ROOT,"~%.1f ms",1000.0/gp.fpsCounter)));
        lines.add("Chunk "+Math.floorDiv(feet.x,size)+","+Math.floorDiv(feet.y,size)+" size "+size+" loaded "+gp.chunks.loaded()+" active "+gp.chunks.active());
        lines.add("Dormant/ready: "+(gp.chunks.loaded()-gp.chunks.active())+" image cache: "+gp.tileM.cachedChunks()+" ("+gp.tileM.cacheBytes()/1048576+" MiB)");
        lines.add("Visible tiles/entities: "+visibleTiles+" / "+visibleEntities+"   active slimes: "+gp.slimeSpawner.nearby(p));
        lines.add("Active actors: "+gp.chunks.activeEntities()+"   terrain estimate: "+gp.chunks.estimatedTerrainBytes()/1048576+" MiB");
        for(Section s:Section.values())lines.add(String.format(Locale.ROOT,"%-15s %7.3f / %7.3f",s,mean(s),peak(s)));
        Runtime r=Runtime.getRuntime();lines.add("Heap: "+(r.totalMemory()-r.freeMemory())/1048576+" / "+r.maxMemory()/1048576+" MiB; global plan retained");
        for(String line:lines)g.drawString(line,x+10,y+=18);
    }
}
