package world.generation;

import com.esotericsoftware.jsonbeans.Json;
import com.esotericsoftware.jsonbeans.OutputType;
import com.esotericsoftware.jsonbeans.JsonValue;
import main.Gamepanel;

/** Plain JSON DTO: records are reconstructed explicitly, never reflectively deserialized. */
public final class WorldSettings {
    public int generatorVersion,width,height,slimeCount,slimeRespawnSeconds;
    public boolean procedural;
    public long seed;
    public String biome;
    public double[] terrain,regions,elevation,cliffs;
    public int[] regionSizes,nestLayout;
    public WorldSettings(){}
    public static WorldSettings of(WorldConfig c,int version,GenerationConfig g){
        return of(c,version,g,version>=8?NestConfig.developmentDefaults():NestConfig.defaults());
    }
    public static WorldSettings of(WorldConfig c,int version,GenerationConfig g,NestConfig nest){
        return of(c,version,g,nest,version>=9?ElevationConfig.developmentDefaults():ElevationConfig.defaults());
    }
    public static WorldSettings of(WorldConfig c,int version,GenerationConfig g,NestConfig nest,ElevationConfig elevation){
        return of(c,version,g,nest,elevation,version>=10?CliffFormationConfig.developmentDefaults():CliffFormationConfig.defaults());
    }
    public static WorldSettings of(WorldConfig c,int version,GenerationConfig g,NestConfig nest,ElevationConfig elevation,CliffFormationConfig cliffs){
        var s=new WorldSettings();s.cliffs=new double[]{cliffs.targetCoverage(),cliffs.maximumCoverage(),cliffs.minimumTiles(),cliffs.formationAreaFraction(),cliffs.minimumLowlandAccess()};s.elevation=new double[]{elevation.maxHeight(),elevation.frequency(),elevation.raisedThreshold(),elevation.boundaryIrregularity(),elevation.boundaryRadius()};s.nestLayout=new int[]{nest.arenaSize(),nest.smallArenaSize(),nest.minDistance(),nest.preferredMaxDistance(),nest.spawnSafety()};s.generatorVersion=version;s.procedural=c.procedural();s.width=c.width();s.height=c.height();s.seed=c.seed();s.biome=c.biome().name();
        s.slimeCount=c.slimeCount();s.slimeRespawnSeconds=c.slimeRespawnSeconds();
        s.terrain=new double[]{c.vegetationDensity(),c.treeDensity(),c.flowerDensity(),c.rockDensity(),c.vegetationScale(),c.terrainScale(),c.dirtThreshold(),c.toneContrast(),c.fieldDensity(),c.pondDensity(),c.detailDensity(),c.mossPathDensity(),c.cobblePatchDensity()};
        s.regions=new double[]{g.regionScale(),g.warpTiles(),g.forestDensity(),g.flowerDensity(),g.grassDensity(),g.shoreDensity(),g.goldenCoverage(),g.treeSpacing(),g.rockSpacing()};
        s.regionSizes=new int[]{g.shoreNear(),g.shoreMiddle(),g.shoreFar(),g.goldenMinSize(),g.goldenMaxSize(),g.goldenPatchLimit()};return s;
    }
    public static WorldSettings capture(Gamepanel gp){
        return of(gp.worldConfig,gp.generatedWorld==null?WorldConfig.GENERATOR_VERSION:gp.generatedWorld.generatorVersion,
                gp.generatedWorld==null?GenerationConfig.defaults():gp.generatedWorld.generation,
                gp.generatedWorld==null?NestConfig.defaults():gp.generatedWorld.nestConfig,
                gp.generatedWorld==null?ElevationConfig.defaults():gp.generatedWorld.elevationConfig,
                gp.generatedWorld==null?CliffFormationConfig.defaults():gp.generatedWorld.cliffConfig);
    }
    public WorldConfig config(){
        if(generatorVersion<4 || generatorVersion>WorldConfig.GENERATOR_VERSION || terrain==null || terrain.length!=13)
            throw new IllegalArgumentException("Unsupported or incomplete world generation settings");
        return new WorldConfig(procedural,width,height,seed,WorldConfig.Biome.valueOf(biome),terrain[0],terrain[1],terrain[2],terrain[3],terrain[4],terrain[5],terrain[6],terrain[7],terrain[8],terrain[9],terrain[10],terrain[11],slimeCount,slimeRespawnSeconds,terrain[12]);
    }
    public GenerationConfig generation(){
        if(regions==null || regions.length!=9 || regionSizes==null || regionSizes.length!=6)throw new IllegalArgumentException("Incomplete regional settings");
        return new GenerationConfig(regions[0],regions[1],regions[2],regions[3],regions[4],regions[5],regions[6],regions[7],regions[8],regionSizes[0],regionSizes[1],regionSizes[2],regionSizes[3],regionSizes[4],regionSizes[5]);
    }
    public NestConfig nest(){
        if(nestLayout==null){if(generatorVersion>=8)throw new IllegalArgumentException("Missing saved nest layout settings");return NestConfig.defaults();}
        if(nestLayout.length!=5)throw new IllegalArgumentException("Invalid saved nest layout settings");
        return new NestConfig(nestLayout[0],nestLayout[1],nestLayout[2],nestLayout[3],nestLayout[4]);
    }
    public ElevationConfig elevation(){
        if(elevation==null){if(generatorVersion>=9)throw new IllegalArgumentException("Missing saved elevation settings");return ElevationConfig.defaults();}
        if(elevation.length!=5 || elevation[0]!=(int)elevation[0])throw new IllegalArgumentException("Invalid saved elevation settings");
        return new ElevationConfig((int)elevation[0],elevation[1],elevation[2],elevation[3],elevation[4]);
    }
    public CliffFormationConfig cliffs(){
        if(cliffs==null){if(generatorVersion>=10)throw new IllegalArgumentException("Missing cliff formation settings");return CliffFormationConfig.defaults();}
        if(cliffs.length!=5 || cliffs[2]!=(int)cliffs[2])throw new IllegalArgumentException("Invalid cliff formation settings");
        return new CliffFormationConfig(cliffs[0],cliffs[1],(int)cliffs[2],cliffs[3],cliffs[4]);
    }
    private static Json json(){
        var j=new Json(OutputType.json){
            // jsonbeans' reflection field map has unstable iteration order between Json instances.
            // The descriptor is also a generation identity key: a harmless reorder must not force regeneration.
            @Override public void writeFields(Object object){
                if(object instanceof WorldSettings){
                    for(String name:new String[]{"generatorVersion","width","height","slimeCount","slimeRespawnSeconds","procedural","seed","biome","terrain","regions","regionSizes","nestLayout","elevation","cliffs"})writeField(object,name);
                }else super.writeFields(object);
            }
            // jsonbeans 0.5 reads Double/primitive double through asFloat(). World descriptors must
            // retain their written precision: 0.10 and 0.95 are strict validated layout boundaries.
            @Override @SuppressWarnings({"unchecked","rawtypes"})
            public <T> T readValue(Class<T> type,Class elementType,JsonValue value){
                if((type==double.class || type==Double.class) && value!=null && value.isNumber())
                    return (T)Double.valueOf(value.asDouble());
                return super.readValue(type,elementType,value);
            }
        };
        j.setTypeName(null);j.setUsePrototypes(false);j.setIgnoreUnknownFields(true);return j;
    }
    public String encode(){return json().toJson(this,WorldSettings.class);}
    public static WorldSettings decode(String text){
        if(text==null || text.length()>16384)throw new IllegalArgumentException("Missing or oversized world settings");
        WorldSettings s=json().fromJson(WorldSettings.class,text);if(s==null)throw new IllegalArgumentException("Missing world settings");s.config();s.generation();s.nest();s.elevation();s.cliffs();return s;
    }
}
