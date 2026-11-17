package main;

import entity.*;
import monster.*;
import world.*;
import world.generation.EnvironmentRegion;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;

/** CPU frame-pipeline scenarios: actual Java2D draw, publish, UI and software window blit. */
public final class PerformanceScenarios {
    private static Gamepanel g;
    private static Method publish;
    private static BufferedImage screen;
    private static void visit(int col,int row){g.localPlayer().worldX=col*48;g.localPlayer().worldY=row*48;TerrainHeightResolver.updateCameras(g);g.chunks.update();}
    private static void frame()throws Exception{g.localPlayer().HP=g.localPlayer().maxHP;g.update();g.drawToTempscreen();publish.invoke(g);Graphics2D target=screen.createGraphics();try{g.paintComponent(target);}finally{target.dispose();}}
    private static void sample(String name,int count)throws Exception{
        for(int i=0;i<20;i++)frame();g.performance.reset();long[] times=new long[count];for(int i=0;i<count;i++){long at=System.nanoTime();frame();times[i]=System.nanoTime()-at;}
        Arrays.sort(times);double mean=Arrays.stream(times).average().orElse(0)/1e6;System.out.printf(Locale.ROOT,"SCENARIO %s mean=%.3f p95=%.3f max=%.3f work_frames/s=%.1f%n",name,mean,times[(int)(count*.95)]/1e6,times[count-1]/1e6,1000/mean);
        System.out.printf(Locale.ROOT,"COMPONENT update=%.3f render=%.3f UI=%.3f AI=%.3f present=%.3f paint=%.3f streamingPeak=%.3f images=%d MiB=%d%n",g.performance.mean(PerformanceMetrics.Section.UPDATE),g.performance.mean(PerformanceMetrics.Section.RENDER),g.performance.mean(PerformanceMetrics.Section.UI),g.performance.mean(PerformanceMetrics.Section.AI),g.performance.mean(PerformanceMetrics.Section.PRESENT),g.performance.mean(PerformanceMetrics.Section.PAINT),g.performance.peak(PerformanceMetrics.Section.STREAMING),g.tileM.cachedChunks(),g.tileM.cacheBytes()/1048576);
    }
    private static void region(EnvironmentRegion region){for(int y=4;y<g.maxWorldRow-4;y++)for(int x=4;x<g.maxWorldCol-4;x++)if(g.generatedWorld.regions().dominant(x,y)==region && g.generatedWorld.lowland(x,y) && !g.tileM.isSolid(x,y)){visit(x,y);return;}}
    public static void main(String[] args)throws Exception{
        System.setProperty("astral.dataDir",Files.createTempDirectory("astral-scenarios-").toString());System.setProperty("astral.world.width","256");System.setProperty("astral.world.height","256");g=new Gamepanel();g.setUpGame();g.gameState=g.playState;g.setSize(1280,720);screen=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);
        Field thread=Gamepanel.class.getDeclaredField("gameThread");thread.setAccessible(true);thread.set(g,Thread.currentThread());publish=Gamepanel.class.getDeclaredMethod("presentGameFrame");publish.setAccessible(true);
        sample("open_idle_1280x720",90);region(EnvironmentRegion.FOREST_GROVE);g.keyH.rightPressed=true;sample("forest_walk",90);g.keyH.rightPressed=false;region(EnvironmentRegion.POND_SHORE);sample("pond_shore",90);
        outer:for(int y=6;y<g.maxWorldRow-6;y++)for(int x=6;x<g.maxWorldCol-6;x++)if(g.generatedWorld.heights().at(x,y)>0 && !g.tileM.isSolid(x,y)){visit(x,y);break outer;}sample("multi_level_cliffs",90);
        visit(31,128);long[] normal=new long[420];for(int i=0;i<normal.length;i++){g.localPlayer().worldX+=4;long at=System.nanoTime();frame();normal[i]=System.nanoTime()-at;}Arrays.sort(normal);System.out.printf(Locale.ROOT,"SCENARIO normal_4_pixels_camera_traverse mean=%.3f p95=%.3f max=%.3f ms%n",Arrays.stream(normal).average().orElse(0)/1e6,normal[399]/1e6,normal[419]/1e6);
        long[] crosses=new long[40];for(int i=0;i<40;i++){long at=System.nanoTime();visit(20+i*5,128);frame();crosses[i]=System.nanoTime()-at;}Arrays.sort(crosses);System.out.printf(Locale.ROOT,"SCENARIO rapid_5_tiles_per_frame mean=%.3f p95=%.3f max=%.3f ms%n",Arrays.stream(crosses).average().orElse(0)/1e6,crosses[38]/1e6,crosses[39]/1e6);
        visit(g.generatedWorld.spawnCol,g.generatedWorld.spawnRow);Arrays.fill(g.monster,null);int slimes=0;for(int y=-4;y<=4 && slimes<6;y++)for(int x=-5;x<=5 && slimes<6;x++){if(Math.hypot(x,y)<2)continue;var m=new MON_GreenSlime(g);m.worldX=g.localPlayer().worldX+x*48;m.worldY=g.localPlayer().worldY+y*48;if(SlimeMotion.clear(g,SlimeMotion.body(m,m.worldX,m.worldY))){m.setHome();g.monster[slimes++]=m;}}System.out.println("COMBAT_SLIMES "+slimes);sample("six_slime_combat",150);
        g.ui.game.open(ui.GameUI.Window.INVENTORY);sample("inventory_open",90);g.ui.game.reset();
        g.ui.game.open(ui.GameUI.Window.INVENTORY);g.setSize(1920,1080);screen=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);sample("inventory_1920x1080",60);
        g.ui.game.reset();Arrays.fill(g.monster,null);g.slimeNest.reset();g.slimeNest.ensurePopulation(true);var nest=g.generatedWorld.slimeNest();visit(nest.col()+nest.size()/2,nest.row()+nest.size()/2-3);var boss=Arrays.stream(g.monster).filter(m->m instanceof MON_CrownedSlime).map(m->(MON_CrownedSlime)m).findFirst().orElseThrow();boss.HP=boss.maxHP/2;boss.damageReaction();sample("crowned_slime_phase_two",180);System.out.println("BOSS_SUMMONS "+g.slimeNest.livingSummons(boss.nestId));
        thread.set(g,null);
    }
}
