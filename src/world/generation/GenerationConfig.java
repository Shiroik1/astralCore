package world.generation;

/** Region balance is independent of artwork and runtime gameplay. Saved with each world. */
public record GenerationConfig(double regionScale,double warpTiles,double forestDensity,double flowerDensity,
                               double grassDensity,double shoreDensity,double goldenCoverage,
                               double treeSpacing,double rockSpacing,int shoreNear,int shoreMiddle,int shoreFar,
                               int goldenMinSize,int goldenMaxSize,int goldenPatchLimit) {
    public GenerationConfig {
        if(!Double.isFinite(regionScale) || regionScale<=0 || regionScale>1 || !Double.isFinite(warpTiles) || warpTiles<0 || warpTiles>100)
            throw new IllegalArgumentException("Invalid region scale/warp");
        for(double v:new double[]{forestDensity,flowerDensity,grassDensity,shoreDensity,treeSpacing,rockSpacing})
            if(!Double.isFinite(v) || v<0 || v>8)throw new IllegalArgumentException("Invalid regional density or spacing");
        if(!Double.isFinite(goldenCoverage) || goldenCoverage<0 || goldenCoverage>.03)
            throw new IllegalArgumentException("Golden coverage must be between 0 and .03");
        if(shoreNear<1 || shoreMiddle<=shoreNear || shoreFar<=shoreMiddle || shoreFar>64 || goldenMinSize<2
                || goldenMaxSize<goldenMinSize || goldenMaxSize>4096 || goldenPatchLimit<0 || goldenPatchLimit>10000)
            throw new IllegalArgumentException("Invalid shore zones or meadow sizes");
    }
    public static GenerationConfig defaults(){return new GenerationConfig(.028,7,1,1,1,1,.02,.75,.5,2,5,8,12,90,3);}
    public static GenerationConfig developmentDefaults(){
        var d=defaults();return new GenerationConfig(n("scale",d.regionScale),n("warp",d.warpTiles),n("forest",1),n("flowers",1),
                n("grass",1),n("shore",1),n("goldenCoverage",.02),n("treeSpacing",.75),n("rockSpacing",.5),
                i("shoreNear",2),i("shoreMiddle",5),i("shoreFar",8),i("goldenMinSize",12),i("goldenMaxSize",90),i("goldenPatches",3));
    }
    private static double n(String key,double value){return Double.parseDouble(System.getProperty("astral.regions."+key,Double.toString(value)));}
    private static int i(String key,int value){return Integer.parseInt(System.getProperty("astral.regions."+key,Integer.toString(value)));}
}
