package world.generation;

import art.Terrain;
import world.WorldObjectType;
import java.util.List;

/** Generation data only: no live entities, images, camera, or gameplay state. Arrays are [col][row]. */
public final class GeneratedWorld {
    public record Placement(WorldObjectType type,int col,int row) {}
    public final WorldConfig config;
    private final Terrain[][] terrain;
    private final int[][] tones;
    private final byte[][] groundDetails;
    private final byte[][] meadow;
    private final boolean[][] reserved;
    private List<Placement> objects=List.of();
    public final int spawnCol,spawnRow;

    GeneratedWorld(WorldConfig config,NoiseGenerator noise){
        this.config=config;
        terrain=new Terrain[config.width()][config.height()];
        tones=new int[config.width()][config.height()];
        groundDetails=new byte[config.width()][config.height()];
        meadow=new byte[config.width()][config.height()];
        reserved=new boolean[config.width()][config.height()];
        spawnCol=config.width()/4;
        spawnRow=routeRow(config,noise,spawnCol);
        // Five-tile-wide connected corridor, with <=1 row change per column at default settings.
        // Connecting each column to the preceding one also keeps extreme custom dimensions safe.
        int previous=routeRow(config,noise,0);
        for(int c=0;c<config.width();c++){
            int row=routeRow(config,noise,c);
            for(int r=Math.max(0,Math.min(previous,row)-2);r<=Math.min(config.height()-1,Math.max(previous,row)+2);r++)
                reserved[c][r]=true;
            previous=row;
        }
        for(int c=Math.max(0,spawnCol-3);c<=Math.min(config.width()-1,spawnCol+3);c++)
            for(int r=Math.max(0,spawnRow-3);r<=Math.min(config.height()-1,spawnRow+3);r++) reserved[c][r]=true;
    }
    private static int routeRow(WorldConfig config,NoiseGenerator noise,int col){
        return config.height()/2+(int)Math.round((noise.value(col*.035,0,100)-.5)*Math.min(12,config.height()/4));
    }
    public Terrain terrain(int col,int row){return terrain[col][row];}
    public int tone(int col,int row){return tones[col][row];}
    public int groundDetail(int col,int row){return groundDetails[col][row];}
    void groundDetail(int col,int row,int variant){groundDetails[col][row]=(byte)variant;}
    public byte meadow(int col,int row){return meadow[col][row];}
    public boolean reserved(int col,int row){return reserved[col][row];}
    public List<Placement> objects(){return objects;}
    void ground(int col,int row,Terrain type,int tone){terrain[col][row]=type;tones[col][row]=tone;}
    void meadow(int col,int row,int type,int density){meadow[col][row]=(byte)((type-1)*4+density);}
    void objects(List<Placement> values){objects=List.copyOf(values);}
}
