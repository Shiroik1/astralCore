package world.generation;

/**
 * Physical heights, boundary and cached starting-component membership; never palette-tone indices.
 */
public final class HeightMap {
    public final int width, height;
    private final byte[][] heights;
    final boolean[][] playable, reachable;
    final int[][] formation;
    private CliffStatistics cliffStatistics;
    private java.util.List<Integer> pondApproaches = java.util.List.of();

    /** Returns the current pond approaches value. */
    public java.util.List<Integer> pondApproaches() {
        return pondApproaches;
    }

    /** Performs the pond approaches operation for HeightMap. */
    void pondApproaches(java.util.List<Integer> cells) {
        pondApproaches = java.util.List.copyOf(cells);
    }

    /** Resolves the formation result used by HeightMap. */
    public int formation(int x, int y) {
        return inside(x, y) ? formation[x][y] : 0;
    }

    /** Returns the current cliff statistics value. */
    public CliffStatistics cliffStatistics() {
        return cliffStatistics;
    }

    /** Performs the cliff statistics operation for HeightMap. */
    void cliffStatistics(CliffStatistics value) {
        cliffStatistics = value;
    }

    private String diagnostic = "Elevation not finalized";
    private java.util.List<String> debugLines = java.util.List.of(diagnostic);

    /** Returns the current debug lines value. */
    public java.util.List<String> debugLines() {
        return debugLines;
    }

    /** Creates a HeightMap instance and initializes the state required by its collaborators. */
    HeightMap(int width, int height) {
        this.width = width;
        this.height = height;
        formation = new int[width][height];
        heights = new byte[width][height];
        playable = new boolean[width][height];
        reachable = new boolean[width][height];
    }

    /** Evaluates the inside condition for HeightMap. */
    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Resolves the at result used by HeightMap. */
    public int at(int x, int y) {
        return heights[Math.max(0, Math.min(width - 1, x))][Math.max(0, Math.min(height - 1, y))];
    }

    /** Performs the set operation for HeightMap. */
    void set(int x, int y, int height) {
        heights[x][y] = (byte) height;
    }

    /** Evaluates the playable condition for HeightMap. */
    public boolean playable(int x, int y) {
        return inside(x, y) && playable[x][y];
    }

    /** Evaluates the reachable condition for HeightMap. */
    public boolean reachable(int x, int y) {
        return inside(x, y) && reachable[x][y];
    }

    /** Evaluates the flat condition for HeightMap. */
    public boolean flat(int x, int y, int radius) {
        if (!playable(x, y)) return false;
        int h = at(x, y);
        for (int r = y - radius; r <= y + radius; r++)
            for (int c = x - radius; c <= x + radius; c++)
                if (!playable(c, r) || at(c, r) != h) return false;
        return true;
    }

    /** Returns the current diagnostic value. */
    public String diagnostic() {
        return diagnostic;
    }

    /** Performs the diagnostic operation for HeightMap. */
    void diagnostic(String text) {
        diagnostic = text;
        debugLines = java.util.List.of(text.split("\\R"));
    }
}
