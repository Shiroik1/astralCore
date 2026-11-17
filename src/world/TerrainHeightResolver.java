package world;

import entity.Entity;
import entity.Player;
import main.Gamepanel;

/** Rendering projection and bounded camera; no entity or collision coordinate is rewritten. */
public final class TerrainHeightResolver {
    private TerrainHeightResolver(){}
    public static boolean active(Gamepanel gp){return gp.generatedWorld!=null && gp.generatedWorld.heights()!=null && (gp.quests==null || !gp.quests.inDungeon());}
    public static int lift(Gamepanel gp,Entity e){
        if(!active(gp) || e instanceof CliffLayer)return 0;
        if(e.groundElevation>=0)return e.groundElevation*(gp.tileSize/2);
        int x=e instanceof WorldObject o?o.col:Math.floorDiv(e.worldX+e.solidArea.x+e.solidArea.width/2,gp.tileSize);
        int y=e instanceof WorldObject o?o.row:Math.floorDiv(e.worldY+e.solidArea.y+e.solidArea.height-1,gp.tileSize);
        return gp.generatedWorld.heights().at(x,y)*(gp.tileSize/2);
    }
    public static int cameraOrigin(int position,int center,int worldPixels,int viewportPixels){
        return Math.max(0,Math.min(Math.max(0,worldPixels-viewportPixels),position-center));
    }
    public static void updateCameras(Gamepanel gp){
        for(Player p:gp.players)if(p!=null){
            int centerX=gp.screenWidth/2-gp.tileSize/2,centerY=gp.screenHeight/2-gp.tileSize/2;
            if(gp.generatedWorld==null || p.inWell || gp.quests!=null && gp.quests.inDungeon()){p.screenX=centerX;p.screenY=centerY;continue;}
            int width=gp.maxWorldCol*gp.tileSize,height=gp.maxWorldRow*gp.tileSize;
            int left=cameraOrigin(p.worldX,centerX,width,gp.screenWidth);
            int top=cameraOrigin(p.worldY,centerY,height,gp.screenHeight);
            p.screenX=p.worldX-left;p.screenY=p.worldY-top;
        }
    }
}
