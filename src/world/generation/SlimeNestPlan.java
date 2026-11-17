package world.generation;

/** Stable global tile coordinates; no runtime entities or rendering resources. */
public record SlimeNestPlan(String id,int col,int row,int size) {
    public boolean contains(int x,int y){return x>=col && y>=row && x<col+size && y<row+size;}
}
