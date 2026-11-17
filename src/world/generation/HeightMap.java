package world.generation;

/** Physical heights, boundary and cached starting-component membership; never palette-tone indices. */
public final class HeightMap {
    public final int width,height;
    private final byte[][] heights;
    final boolean[][] playable,reachable;
    final int[][] formation;
    private CliffStatistics cliffStatistics;
    private java.util.List<Integer> pondApproaches=java.util.List.of();
    public java.util.List<Integer> pondApproaches(){return pondApproaches;}
    void pondApproaches(java.util.List<Integer> cells){pondApproaches=java.util.List.copyOf(cells);}
    public int formation(int x,int y){return inside(x,y)?formation[x][y]:0;}
    public CliffStatistics cliffStatistics(){return cliffStatistics;}
    void cliffStatistics(CliffStatistics value){cliffStatistics=value;}
    private String diagnostic="Elevation not finalized";
    private java.util.List<String> debugLines=java.util.List.of(diagnostic);
    public java.util.List<String> debugLines(){return debugLines;}
    HeightMap(int width,int height){this.width=width;this.height=height;formation=new int[width][height];heights=new byte[width][height];playable=new boolean[width][height];reachable=new boolean[width][height];}
    public boolean inside(int x,int y){return x>=0 && y>=0 && x<width && y<height;}
    public int at(int x,int y){return heights[Math.max(0,Math.min(width-1,x))][Math.max(0,Math.min(height-1,y))];}
    void set(int x,int y,int height){heights[x][y]=(byte)height;}
    public boolean playable(int x,int y){return inside(x,y) && playable[x][y];}
    public boolean reachable(int x,int y){return inside(x,y) && reachable[x][y];}
    public boolean flat(int x,int y,int radius){
        if(!playable(x,y))return false;int h=at(x,y);
        for(int r=y-radius;r<=y+radius;r++)for(int c=x-radius;c<=x+radius;c++)if(!playable(c,r) || at(c,r)!=h)return false;return true;
    }
    public String diagnostic(){return diagnostic;}
    void diagnostic(String text){diagnostic=text;debugLines=java.util.List.of(text.split("\\R"));}
}
