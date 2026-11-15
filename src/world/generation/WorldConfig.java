package world.generation;

/** Phase-one settings. JVM properties are read once, before the world is built. */
public record WorldConfig(boolean procedural, int width, int height, long seed, Biome biome,
                          double vegetationDensity, double treeDensity, double flowerDensity,
                          double rockDensity, double vegetationScale, double terrainScale,
                          double dirtThreshold, double toneContrast) {
    public enum Biome { TEMPERATE_GRASSLAND }
    public static final int GENERATOR_VERSION = 1;

    public WorldConfig {
        if(width < 16 || height < 16 || (long) width * height > 1_000_000)
            throw new IllegalArgumentException("World dimensions must be >=16, with at most 1,000,000 tiles");
        if(biome == null) throw new IllegalArgumentException("A biome is required");
        for(double value : new double[]{vegetationDensity, treeDensity, flowerDensity, rockDensity, dirtThreshold})
            if(!Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("Densities and dirt threshold must be in [0,1]");
        for(double value : new double[]{vegetationScale, terrainScale, toneContrast})
            if(!Double.isFinite(value) || value <= 0)
                throw new IllegalArgumentException("Noise scales and tone contrast must be positive");
    }

    public static WorldConfig developmentDefaults(){
        String mode = System.getProperty("astral.world.mode", "procedural");
        if(!mode.equals("procedural") && !mode.equals("authored"))
            throw new IllegalArgumentException("astral.world.mode must be procedural or authored");
        return new WorldConfig(mode.equals("procedural"), integer("width",100), integer("height",100),
                Long.parseLong(System.getProperty("astral.world.seed", "123456789")), Biome.TEMPERATE_GRASSLAND,
                number("vegetation",.65), number("trees",.18), number("flowers",.65), number("rocks",.008),
                number("vegetationScale",.09), number("terrainScale",.055), number("dirtThreshold",.77),
                number("toneContrast",1));
    }

    private static int integer(String name,int fallback){
        return Integer.parseInt(System.getProperty("astral.world."+name,Integer.toString(fallback)));
    }
    private static double number(String name,double fallback){
        return Double.parseDouble(System.getProperty("astral.world."+name,Double.toString(fallback)));
    }
    public WorldConfig withSeed(long value){
        return new WorldConfig(procedural,width,height,value,biome,vegetationDensity,treeDensity,flowerDensity,
                rockDensity,vegetationScale,terrainScale,dirtThreshold,toneContrast);
    }
}
