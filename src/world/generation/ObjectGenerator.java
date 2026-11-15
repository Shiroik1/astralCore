package world.generation;

import art.Terrain;
import world.GrassLayer;
import world.WorldObjectType;
import java.awt.Rectangle;
import java.util.ArrayList;

final class ObjectGenerator {
    void generate(GeneratedWorld world,NoiseGenerator noise,WorldPalette palette,int tileSize){
        WorldConfig config=world.config;
        boolean[][] occupied=new boolean[config.width()][config.height()];
        var placements=new ArrayList<GeneratedWorld.Placement>();
        for(int r=0;r<config.height();r++) for(int c=0;c<config.width();c++){
            if(world.reserved(c,r) || world.terrain(c,r)!=Terrain.GRASS) continue;
            double vegetation=noise.sample(c*config.vegetationScale(),r*config.vegetationScale(),30);
            double flowers=noise.sample(c*.23,r*.23,40);
            WorldObjectType type=null;
            double pick=noise.random(c,r,50);
            if(vegetation>.59 && pick<config.treeDensity()*(vegetation-.50)*3){
                double species=noise.random(c,r,51);
                type=species<.35?WorldObjectType.OAK_1:species<.70?WorldObjectType.OAK_2:
                        species<.85?WorldObjectType.PINE_1:WorldObjectType.PINE_2;
            } else if(vegetation>.55 && noise.random(c,r,52)<config.vegetationDensity()*.055){
                type=flowers>.6?WorldObjectType.FLOWER_BUSH:WorldObjectType.BUSH;
            } else if(noise.sample(c*.11,r*.11,60)>.53 && noise.random(c,r,53)<config.rockDensity()){
                double size=noise.random(c,r,54);
                type=size<.4?WorldObjectType.ROCK_SMALL:size<.8?WorldObjectType.ROCK_SMALL_MOSS:
                        size<.9?WorldObjectType.ROCK_LARGE:WorldObjectType.ROCK_LARGE_MOSS;
            }
            if(type!=null && place(world,palette.footprint(type),occupied,c,r,tileSize))
                placements.add(new GeneratedWorld.Placement(type,c,r));
        }
        // Meadow follows the same density field, with a separate small-scale flower-patch field.
        for(int r=0;r<config.height();r++) for(int c=0;c<config.width();c++){
            if(occupied[c][r] || !meadowFits(world,c,r) || world.terrain(c,r)!=Terrain.GRASS) continue;
            double vegetation=noise.sample(c*config.vegetationScale(),r*config.vegetationScale(),30);
            if(vegetation<1-config.vegetationDensity()*.85) continue;
            int density=Math.max(1,Math.min(4,(int)((vegetation-.35)*8)));
            int type=vegetation>.63?GrassLayer.TALL:GrassLayer.SHORT;
            if(noise.sample(c*.23,r*.23,40)>.60 && noise.random(c,r,55)<config.flowerDensity()) type=GrassLayer.FLOWERS;
            world.meadow(c,r,type,density);
        }
        world.objects(placements);
    }

    private boolean meadowFits(GeneratedWorld world,int c,int r){
        // Existing bend frames extend beyond a tuft's anchor: keep a one-tile buffer at edges/clearings.
        if(c<1 || r<1 || c>=world.config.width()-1 || r>=world.config.height()-1) return false;
        for(int dy=-1;dy<=1;dy++) for(int dx=-1;dx<=1;dx++)
            if(world.reserved(c+dx,r+dy)) return false;
        return true;
    }

    private boolean place(GeneratedWorld world,WorldPalette.Footprint footprint,boolean[][] occupied,int c,int r,int ts){
        int x=c*ts+ts/2,y=(r+1)*ts-4; // Matches WorldObject's foot anchor.
        Rectangle image=new Rectangle(footprint.image());image.translate(x,y);
        Rectangle solid=new Rectangle(footprint.solid());solid.translate(x,y);
        Rectangle boundary=new Rectangle(0,0,world.config.width()*ts,world.config.height()*ts);
        if(!boundary.contains(image) || !boundary.contains(solid)) return false;
        // Reserve actual sprite bounds around spawn/corridor so even tree crowns leave them visible.
        int left=Math.floorDiv(image.x,ts),right=Math.floorDiv(image.x+image.width-1,ts);
        int top=Math.floorDiv(image.y,ts),bottom=Math.floorDiv(image.y+image.height-1,ts);
        for(int row=top;row<=bottom;row++) for(int col=left;col<=right;col++)
            if(world.reserved(col,row)) return false;
        // Half a tile of clearance around every blocking box. A tile occupancy grid is conservative,
        // prevents all solid overlap, and leaves gaps without a quadratic scan of existing objects.
        solid.grow(ts/2,ts/2);
        left=Math.max(0,Math.floorDiv(solid.x,ts));right=Math.min(world.config.width()-1,Math.floorDiv(solid.x+solid.width-1,ts));
        top=Math.max(0,Math.floorDiv(solid.y,ts));bottom=Math.min(world.config.height()-1,Math.floorDiv(solid.y+solid.height-1,ts));
        for(int row=top;row<=bottom;row++) for(int col=left;col<=right;col++)
            if(occupied[col][row] || world.reserved(col,row) || world.terrain(col,row)!=Terrain.GRASS) return false;
        for(int row=top;row<=bottom;row++) for(int col=left;col<=right;col++) occupied[col][row]=true;
        return true;
    }
}
