package world.generation;

/** Builds a finite world once. The renderer only consumes its resulting data. */
public final class WorldGenerator {
    public GeneratedWorld generate(WorldConfig config,WorldPalette palette,int tileSize){
        if(tileSize<16 || (long)Math.max(config.width(),config.height())*tileSize>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Unsupported tile size or world pixel dimensions");
        NoiseGenerator noise=new NoiseGenerator(config.seed());
        GeneratedWorld world=new GeneratedWorld(config,noise);
        switch(config.biome()){
            case TEMPERATE_GRASSLAND -> {
                new TerrainGenerator().generate(world,noise);
                new PondGenerator().generate(world,noise);
                new CobblePatchGenerator().generate(world,noise);
                new ObjectGenerator().generate(world,noise,palette,tileSize);
            }
        }
        return world;
    }
}
