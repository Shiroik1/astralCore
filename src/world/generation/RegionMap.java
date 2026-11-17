package world.generation;

/** Smooth influences drive placement. A dominant label never hard-switches the vegetation rules. */
public final class RegionMap {
    public final int width,height;
    final float[][] forest,flowers,moisture,shore,density;
    final boolean[][] goldenMargin;
    boolean[][] landReachable,accessible;
    final short[][] waterDistance;
    final int[][] golden;
    RegionMap(int width,int height){
        this.width=width;this.height=height;
        forest=new float[width][height];flowers=new float[width][height];moisture=new float[width][height];
        shore=new float[width][height];density=new float[width][height];goldenMargin=new boolean[width][height];waterDistance=new short[width][height];golden=new int[width][height];
    }
    public float forest(int x,int y){return forest[x][y];}
    public float flowers(int x,int y){return flowers[x][y];}
    public float moisture(int x,int y){return moisture[x][y];}
    public float shore(int x,int y){return shore[x][y];}
    public float vegetationDensity(int x,int y){return density[x][y];}
    public int waterDistance(int x,int y){return waterDistance[x][y];}
    public int golden(int x,int y){return golden[x][y];}
    public EnvironmentRegion dominant(int x,int y){
        if(golden[x][y]>0)return EnvironmentRegion.GOLDEN_MEADOW;
        if(shore[x][y]>.35)return EnvironmentRegion.POND_SHORE;
        if(forest[x][y]>.42)return EnvironmentRegion.FOREST_GROVE;
        if(flowers[x][y]>.4)return EnvironmentRegion.FLOWER_PLAIN;
        return EnvironmentRegion.GRASSLAND;
    }
}
