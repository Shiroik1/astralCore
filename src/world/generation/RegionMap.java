package world.generation;

/** Smooth influences drive placement. A dominant label never hard-switches the vegetation rules. */
public final class RegionMap {
    public final int width, height;
    final float[][] forest, flowers, moisture, shore, density;
    final boolean[][] goldenMargin;
    boolean[][] landReachable, accessible;
    final short[][] waterDistance;
    final int[][] golden;

    /** Creates a RegionMap instance and initializes the state required by its collaborators. */
    RegionMap(int width, int height) {
        this.width = width;
        this.height = height;
        forest = new float[width][height];
        flowers = new float[width][height];
        moisture = new float[width][height];
        shore = new float[width][height];
        density = new float[width][height];
        goldenMargin = new boolean[width][height];
        waterDistance = new short[width][height];
        golden = new int[width][height];
    }

    /** Resolves the forest result used by RegionMap. */
    public float forest(int x, int y) {
        return forest[x][y];
    }

    /** Resolves the flowers result used by RegionMap. */
    public float flowers(int x, int y) {
        return flowers[x][y];
    }

    /** Resolves the moisture result used by RegionMap. */
    public float moisture(int x, int y) {
        return moisture[x][y];
    }

    /** Resolves the shore result used by RegionMap. */
    public float shore(int x, int y) {
        return shore[x][y];
    }

    /** Resolves the vegetation density result used by RegionMap. */
    public float vegetationDensity(int x, int y) {
        return density[x][y];
    }

    /** Resolves the water distance result used by RegionMap. */
    public int waterDistance(int x, int y) {
        return waterDistance[x][y];
    }

    /** Resolves the golden result used by RegionMap. */
    public int golden(int x, int y) {
        return golden[x][y];
    }

    /** Resolves the dominant result used by RegionMap. */
    public EnvironmentRegion dominant(int x, int y) {
        if (golden[x][y] > 0) return EnvironmentRegion.GOLDEN_MEADOW;
        if (shore[x][y] > .35) return EnvironmentRegion.POND_SHORE;
        if (forest[x][y] > .42) return EnvironmentRegion.FOREST_GROVE;
        if (flowers[x][y] > .4) return EnvironmentRegion.FLOWER_PLAIN;
        return EnvironmentRegion.GRASSLAND;
    }
}
