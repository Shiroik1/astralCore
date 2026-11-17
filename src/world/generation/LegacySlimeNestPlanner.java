package world.generation;

import art.Terrain;
import java.util.ArrayDeque;

/** Reserves a reachable grass clearing before vegetation placement. Small maps safely skip it. */
final class LegacySlimeNestPlanner {
    static final int ARENA_SIZE=14,MIN_DISTANCE=25,MAX_DISTANCE=40;
    private boolean starterBuffer(GeneratedWorld w,int x,int y){
        // Respect the earlier resource reservation, including the access lane's one-tile margin.
        for(int r=Math.max(0,y-1);r<=Math.min(w.config.height()-1,y+1);r++)
            for(int c=Math.max(0,x-1);c<=Math.min(w.config.width()-1,x+1);c++)
                if(w.starterReserved(c,r) && !w.coreReserved(c,r))return true;
        return false;
    }
    void generate(GeneratedWorld w,NoiseGenerator noise){
        int width=w.config.width(),height=w.config.height(),size=ARENA_SIZE;
        if(width<36 || height<36)return;
        int[][] parent=new int[width][height];
        boolean[][] reached=new boolean[width][height];var queue=new ArrayDeque<Integer>();
        queue.add(w.spawnRow*width+w.spawnCol);reached[w.spawnCol][w.spawnRow]=true;
        int[] dx={1,-1,0,0},dy={0,0,1,-1};
        while(!queue.isEmpty()){
            int p=queue.remove(),x=p%width,y=p/width;
            for(int i=0;i<4;i++){
                int nx=x+dx[i],ny=y+dy[i];if(nx<0 || ny<0 || nx>=width || ny>=height || reached[nx][ny])continue;
                if(w.generatorVersion>=6 && starterBuffer(w,nx,ny))continue;
                Terrain t=w.terrain(nx,ny);if(t!=Terrain.GRASS && t!=Terrain.PATH && t!=Terrain.COBBLE && t!=Terrain.MOSS_PATH)continue;
                reached[nx][ny]=true;parent[nx][ny]=p;queue.add(ny*width+nx);
            }
        }
        int bestX=-1,bestY=0;double best=-1;
        for(int y=2;y+size<height-2;y++)for(int x=2;x+size<width-2;x++){
            double distance=Math.hypot(x+size/2-w.spawnCol,y+size/2-w.spawnRow);
            if(distance<MIN_DISTANCE || distance>MAX_DISTANCE || !reached[x+size/2][y+size/2])continue;
            boolean valid=true;
            for(int r=y;r<y+size && valid;r++)for(int c=x;c<x+size;c++)
                if(w.reserved(c,r) || w.terrain(c,r)!=Terrain.GRASS){valid=false;break;}
            double score=noise.random(x,y,903);
            if(valid && score>best){best=score;bestX=x;bestY=y;}
        }
        if(bestX>=0){
            var plan=new SlimeNestPlan("slime-nest-v1:g"+w.generatorVersion+":"+w.config.seed()+":"+width+":"+height+":"+bestX+":"+bestY,bestX,bestY,size);
            w.reserveNest(plan);
            int at=(bestY+size/2)*width+bestX+size/2,spawn=w.spawnRow*width+w.spawnCol;
            while(at!=spawn){
                int x=at%width,y=at/width;
                for(int r=Math.max(0,y-1);r<=Math.min(height-1,y+1);r++)for(int c=Math.max(0,x-1);c<=Math.min(width-1,x+1);c++)w.reserveAccess(c,r);
                at=parent[x][y];
            }
        }
    }
}
