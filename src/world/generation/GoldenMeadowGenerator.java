package world.generation;

import art.Terrain;
import java.util.*;

/** Connected frontier growth: every accepted cell has a four-neighbour path to its meadow seed. */
public final class GoldenMeadowGenerator {
    private record Candidate(int id,double score){}
    void generate(GeneratedWorld w,NoiseGenerator n){
        var cfg=w.generation;var map=w.regions();int width=map.width,height=map.height,eligible=0;
        var seeds=new ArrayList<Candidate>();boolean[][] valid=new boolean[width][height];
        for(int y=2;y<height-2;y++)for(int x=1;x<width-1;x++){
            if(!eligible(w,x,y))continue;valid[x][y]=true;eligible++;
            if(map.forest(x,y)<.3 && map.waterDistance(x,y)>cfg.shoreMiddle() && Math.hypot(x-w.spawnCol,y-w.spawnRow)>10)
                seeds.add(new Candidate(y*width+x,n.random(x,y,850)));
        }
        int budget=(int)Math.floor(eligible*cfg.goldenCoverage());
        if(budget<cfg.goldenMinSize() || cfg.goldenPatchLimit()==0)return;
        seeds.sort(Comparator.comparingDouble(Candidate::score).reversed());
        int limit=Math.min(cfg.goldenPatchLimit(),Math.max(1,(int)Math.round(width*(double)height/3500))),patch=0;
        var centers=new ArrayList<Integer>();int[] dx={1,-1,0,0},dy={0,0,1,-1};
        for(Candidate seed:seeds){
            if(patch>=limit || budget<cfg.goldenMinSize())break;
            int sx=seed.id%width,sy=seed.id/width;if(map.golden(sx,sy)>0)continue;
            boolean near=false;for(int center:centers)if(Math.hypot(sx-center%width,sy-center/width)<12)near=true;if(near)continue;
            int target=Math.min(cfg.goldenMaxSize(),Math.max(cfg.goldenMinSize(),budget/(limit-patch)));
            var queue=new PriorityQueue<Candidate>(Comparator.comparingDouble(Candidate::score).thenComparingInt(Candidate::id));
            Set<Integer> seen=new HashSet<>();var footprint=new ArrayList<Integer>();queue.add(new Candidate(seed.id,0));seen.add(seed.id);
            while(!queue.isEmpty() && footprint.size()<target){
                int id=queue.remove().id,x=id%width,y=id/width;footprint.add(id);
                for(int i=0;i<4;i++){
                    int xx=x+dx[i],yy=y+dy[i],next=yy*width+xx;
                    if(xx<1 || yy<2 || xx>=width-1 || yy>=height-2 || !valid[xx][yy] || map.golden(xx,yy)>0 || !seen.add(next))continue;
                    if(map.forest(xx,yy)>.45 || Math.hypot(xx-w.spawnCol,yy-w.spawnRow)<9)continue;
                    double distance=Math.hypot(xx-sx,yy-sy);if(distance>Math.sqrt(target)*1.7)continue;
                    double cost=distance*distance/target+(1-n.value(xx*.21,yy*.21,851))*1.6;
                    queue.add(new Candidate(next,cost));
                }
            }
            if(footprint.size()<cfg.goldenMinSize())continue;
            patch++;for(int id:footprint)map.golden[id%width][id/width]=patch;
            budget-=footprint.size();centers.add(seed.id);
            for(int id:footprint){int x=id%width,y=id/width;
                for(int r=y-2;r<=y+1;r++)for(int c=x-1;c<=x+1;c++)map.goldenMargin[c][r]=true;
            }
        }
    }
    private static boolean eligible(GeneratedWorld w,int x,int y){
        if(!w.regions().landReachable[x][y] || w.heights()!=null && !w.heights().flat(x,y,2))return false;
        for(int r=y-2;r<=y+1;r++)for(int c=x-1;c<=x+1;c++)if(w.terrain(c,r)!=Terrain.GRASS || w.reserved(c,r))return false;
        return true;
    }
    /** Shared generation invariant and development checker. Throws on fragments or excess coverage. */
    public static int validate(GeneratedWorld w){
        var m=w.regions();if(m==null)return 0;
        boolean[][] seen=new boolean[m.width][m.height];Set<Integer> ids=new HashSet<>();int total=0,eligible=0,components=0;
        int[] dx={1,-1,0,0},dy={0,0,1,-1};
        for(int y=0;y<m.height;y++)for(int x=0;x<m.width;x++){
            if(x>=1 && y>=2 && x<m.width-1 && y<m.height-2 && eligible(w,x,y))eligible++;
            if(m.golden(x,y)==0 || seen[x][y])continue;
            int label=m.golden(x,y),size=0;if(!ids.add(label))throw new IllegalStateException("Disconnected golden meadow "+label);
            var q=new ArrayDeque<Integer>();q.add(y*m.width+x);seen[x][y]=true;
            while(!q.isEmpty()){
                int p=q.remove(),xx=p%m.width,yy=p/m.width;size++;
                if(w.terrain(xx,yy)!=Terrain.GRASS || w.reserved(xx,yy) || w.meadow(xx,yy)<=20 || !m.accessible[xx][yy])
                    throw new IllegalStateException("Invalid golden meadow tile");
                for(int i=0;i<4;i++){
                    int nx=xx+dx[i],ny=yy+dy[i];if(nx<0 || ny<0 || nx>=m.width || ny>=m.height || seen[nx][ny] || m.golden(nx,ny)!=label)continue;
                    seen[nx][ny]=true;q.add(ny*m.width+nx);
                }
            }
            if(size<w.generation.goldenMinSize())throw new IllegalStateException("Isolated golden fragment");total+=size;components++;
        }
        if(total>(int)Math.floor(eligible*w.generation.goldenCoverage()))throw new IllegalStateException("Golden meadow coverage exceeded");
        return components;
    }
}
