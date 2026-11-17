package world.generation;

import art.Terrain;

/** Broad correlated plateaus; major reservations and pond basins stay on the starting elevation. */
final class ElevationGenerator {
    void generate(GeneratedWorld w,NoiseGenerator n){
        var h=w.heights();var cfg=w.elevationConfig;double scale=cfg.frequency();
        for(int y=0;y<h.height;y++)for(int x=0;x<h.width;x++){
            if(!h.playable(x,y)){w.ground(x,y,Terrain.GRASS,w.tone(x,y));h.set(x,y,Math.max(1,cfg.maxHeight()-(n.value(x*.06,y*.06,1310)<.3?1:0)));continue;}
            double wx=x+(n.value(x*scale*.6,y*scale*.6,1311)-.5)*8,wy=y+(n.value(x*scale*.6,y*scale*.6,1312)-.5)*8;
            double value=n.value(wx*scale,wy*scale,1313)+.07*w.regions().forest(x,y)-.13*w.regions().flowers(x,y);
            int level=value<cfg.raisedThreshold()?0:1+(int)((value-cfg.raisedThreshold())/.13);
            h.set(x,y,Math.min(cfg.maxHeight(),level));
        }
        // Majority filter nested plateau masks together, preserving broad contours rather than speckle.
        for(int pass=0;pass<2;pass++){
            int[][] next=new int[h.width][h.height];
            int[] counts=new int[cfg.maxHeight()+1];
            for(int y=1;y<h.height-1;y++)for(int x=1;x<h.width-1;x++){
                java.util.Arrays.fill(counts,0);for(int r=y-1;r<=y+1;r++)for(int c=x-1;c<=x+1;c++)counts[h.at(c,r)]++;
                int total=0;for(int k=0;k<counts.length;k++){total+=counts[k];if(total>=5){next[x][y]=k;break;}}
            }
            for(int y=1;y<h.height-1;y++)for(int x=1;x<h.width-1;x++)if(h.playable(x,y))h.set(x,y,next[x][y]);
        }
        // A boundary must be visibly higher even beside an already raised interior plateau.
        for(int y=0;y<h.height;y++)for(int x=0;x<h.width;x++){
            boolean otherSide=false;
            for(int r=y-1;r<=y+1;r++)for(int c=x-1;c<=x+1;c++)if(h.playable(c,r)!=h.playable(x,y))otherSide=true;
            if(otherSide)h.set(x,y,h.playable(x,y)?Math.min(h.at(x,y),cfg.maxHeight()-1):cfg.maxHeight());
        }
        for(int y=0;y<h.height;y++)for(int x=0;x<h.width;x++){
            if(w.reserved(x,y) || w.terrain(x,y)==Terrain.WATER){
                int margin=Math.max(w.terrain(x,y)==Terrain.WATER?3:2,(cfg.maxHeight()+1)/2);
                for(int r=Math.max(1,y-margin);r<=Math.min(h.height-2,y+margin);r++)for(int c=Math.max(1,x-margin);c<=Math.min(h.width-2,x+margin);c++)if(h.playable(c,r))h.set(c,r,0);
            }
        }
        new TerrainAccessibilityValidator().prepare(w);
        // A weathered trail must end at a cliff, not continue as paving over a scenic plateau face.
        for(int y=0;y<h.height;y++)for(int x=0;x<h.width;x++)if(h.at(x,y)>0 && w.terrain(x,y)!=Terrain.GRASS)w.ground(x,y,Terrain.GRASS,w.tone(x,y));
    }
}
