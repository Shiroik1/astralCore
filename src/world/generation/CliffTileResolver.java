package world.generation;

import art.GroundTiles;
import java.awt.*;
import java.awt.geom.*;

/** Global eight-neighbour masks, shared by every rendering chunk. No chunk-local height decisions. */
public final class CliffTileResolver {
    private CliffTileResolver(){}
    // Clockwise cardinals first, then diagonals: N=1 E=2 S=4 W=8 NE=16 SE=32 SW=64 NW=128.
    // A bit means neighbour height >= the sampled threshold; height differences remain collision data.
    public static int mask(HeightMap h,int x,int y,int step){
        int result=0;int[] dx={0,1,0,-1,1,1,-1,-1},dy={-1,0,1,0,-1,1,1,-1};
        for(int i=0;i<8;i++)if(h.at(x+dx[i],y+dy[i])>=step)result|=1<<i;return result;
    }
    public static boolean edge(HeightMap h,int x,int y){
        int level=h.at(x,y);return level>0 && mask(h,x,y,level)!=GroundTiles.FULL;
    }
    private static Shape top(HeightMap h,int x,int y,int step,int ts,int px,int py){
        var shape=GroundTiles.blobShape(mask(h,x,y,step),GroundTiles.GRASS_INSET,7,5,-1);
        var transform=AffineTransform.getTranslateInstance(px,py-step*(ts/2));transform.scale(ts/48.0,ts/48.0);return transform.createTransformedShape(shape);
    }
    public static void drawFace(Graphics2D g,GroundTiles art,GeneratedWorld w,int x,int y,int step,int ts,int px,int py){
        if(mask(w.heights(),x,y,step)==GroundTiles.FULL)return; // No exposed face inside a connected surface.
        Shape cap=top(w.heights(),x,y,step,ts,px,py);Area wall=new Area();
        for(int shift=1;shift<=ts/2;shift++)wall.add(new Area(AffineTransform.getTranslateInstance(0,shift).createTransformedShape(cap)));
        // Side/back boundaries need a narrow face too, rather than an invisible same-color grass seam.
        wall.add(new Area(new BasicStroke(Math.max(2,ts/4f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(cap)));
        wall.subtract(new Area(cap));art.drawCliffFace(g,wall,w.tone(x,y));
    }
    private static Shape clippedTop(HeightMap h,int x,int y,int step,int ts,int px,int py){
        // Sprite cells clip the connector arms at their own 48px bounds; geometry must use the same domain.
        Area cap=new Area(top(h,x,y,step,ts,px,py));
        cap.intersect(new Area(new Rectangle(px,py-step*(ts/2),ts,ts)));return cap;
    }
    public static void drawWalls(Graphics2D g,GroundTiles art,GeneratedWorld w,int x,int y,int ts,int px,int py){
        int height=w.heights().at(x,y);Area wall=new Area();
        for(int step=1;step<=height;step++){
            if(mask(w.heights(),x,y,step)==GroundTiles.FULL)continue;
            Shape surface=clippedTop(w.heights(),x,y,step,ts,px,py);Area band=new Area();
            Area outline=new Area(surface);
            outline.add(new Area(new BasicStroke(Math.max(2,ts/4f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(surface)));
            // Carry the same rounded outer silhouette down to the foot. Adding a stroke only at
            // the top left the lower face inset and produced a square notch at convex corners.
            for(int shift=0;shift<=ts/2;shift++)band.add(new Area(AffineTransform.getTranslateInstance(0,shift).createTransformedShape(outline)));
            // Subtract the connected surface of this layer, including neighbouring cells. A joined edge
            // must not leave an internal face just because its neighbour's cap is baked in another chunk.
            Area connected=new Area(surface);
            for(int r=-1;r<=1;r++)for(int c=-1;c<=1;c++)if((c!=0 || r!=0) && w.heights().at(x+c,y+r)>=step)
                connected.add(new Area(clippedTop(w.heights(),x+c,y+r,step,ts,px+c*ts,py+r*ts)));
            band.subtract(connected);wall.add(band);
        }
        Shape cap=clippedTop(w.heights(),x,y,height,ts,px,py);wall.subtract(new Area(cap));
        int footTone=w.tone(x,y);int[] dx={0,-1,1,0},dy={1,0,0,-1};
        for(int i=0;i<4;i++)if(w.heights().inside(x+dx[i],y+dy[i]) && w.heights().at(x+dx[i],y+dy[i])<height){footTone=w.tone(x+dx[i],y+dy[i]);break;}
        art.drawCliffWall(g,wall,cap,w.tone(x,y),footTone,px,py,height*(ts/2));
    }
    public static void drawStack(Graphics2D g,GroundTiles art,GroundTiles.World tiles,GeneratedWorld w,int x,int y,int ts,int px,int py){
        drawWalls(g,art,w,x,y,ts,px,py);
        drawTop(g,art,tiles,w,x,y,w.heights().at(x,y),ts,px,py);
    }
    public static void drawTop(Graphics2D original,GroundTiles art,GroundTiles.World tiles,GeneratedWorld w,int x,int y,int step,int ts,int px,int py){
        if(w.generatorVersion>=10){art.drawCliffCap(original,tiles,x,y,mask(w.heights(),x,y,step),px,py-step*(ts/2));return;}
        var g=(Graphics2D)original.create();g.clip(top(w.heights(),x,y,step,ts,px,py));
        // Neighbouring cells complete the arms/corners. Sampling and clipping happen in world coordinates.
        art.drawCell(g,tiles,x,y,px,py-step*(ts/2));g.dispose();
    }
}
