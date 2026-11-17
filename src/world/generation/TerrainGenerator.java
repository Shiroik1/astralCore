package world.generation;

import art.GroundTiles;
import art.Terrain;

final class TerrainGenerator {
    void generate(GeneratedWorld world,NoiseGenerator noise){
        WorldConfig config=world.config;
        for(int r=0;r<config.height();r++) for(int c=0;c<config.width();c++){
            double tone=noise.sample(c*config.terrainScale(),r*config.terrainScale(),10);
            int level=Math.max(0,Math.min(GroundTiles.LEVELS-1,(int)Math.floor(2+(tone-.5)*8*config.toneContrast())));
            double dirt=noise.sample(c*.14,r*.14,20);
            Terrain type=!world.reserved(c,r) && dirt>config.dirtThreshold()?Terrain.PATH:Terrain.GRASS;
            world.ground(c,r,type,level);
            double vegetation=noise.sample(c*config.vegetationScale(),r*config.vegetationScale(),30);
            if(world.regions()!=null){
                var region=world.regions();
                vegetation=Math.max(0,Math.min(1,vegetation*.65+region.moisture(c,r)*.25+region.forest(c,r)*.25-region.flowers(c,r)*.15));
            }
            int detail=world.decorationReserved(c,r) || config.vegetationDensity()==0 || noise.random(c,r,718)>config.vegetationDensity()?0:
                    1+Math.min(GroundTiles.GRASS_VARIANTS-2,(int)(vegetation*(GroundTiles.GRASS_VARIANTS-1)));
            world.groundDetail(c,r,detail);
        }
        // A gently bending, weathered trail independent of the guaranteed grassy crossing.
        if(config.mossPathDensity()>0) for(int r=1;r<config.height()-1;r++){
            int center=config.width()*2/3+(int)Math.round((noise.value(0,r*.045,120)-.5)*14);
            double wear=noise.sample(center*.15,r*.15,121);
            if(wear>config.mossPathDensity())continue;
            for(int c=Math.max(1,center-1);c<=Math.min(config.width()-2,center+1);c++)
                if(!world.reserved(c,r))world.ground(c,r,Terrain.MOSS_PATH,world.tone(c,r));
        }
    }
}
