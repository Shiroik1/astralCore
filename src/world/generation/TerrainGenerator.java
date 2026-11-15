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
        }
    }
}
