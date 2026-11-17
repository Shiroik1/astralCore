package world.generation;

import art.Terrain;
import java.util.*;

/** A small number of connected, budgeted landmasses. Noise shapes whole formations, never independent tile odds. */
final class InlandCliffPlanner {
    private record Candidate(int id,double score){}
    private static final int[] DX={1,-1,0,0},DY={0,0,1,-1};
    private GeneratedWorld w;
    private HeightMap h;
    private NoiseGenerator noise;
    private boolean[][] eligible,candidates;
    private int width,height,denominator;

    void generate(GeneratedWorld world,NoiseGenerator n,int formationLimit){
        w=world;h=w.heights();noise=n;width=h.width;height=h.height;denominator=0;
        eligible=new boolean[width][height];candidates=new boolean[width][height];
        // Inland starts completely flat. Only nonplayable boundary highlands are generated outside the budget.
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            if(h.playable(x,y)){h.set(x,y,0);eligible[x][y]=PlacementRules.walkableTerrain(w.terrain(x,y));if(eligible[x][y])denominator++;}
            else {w.ground(x,y,Terrain.GRASS,w.tone(x,y));h.set(x,y,Math.max(1,w.elevationConfig.maxHeight()-(n.value(x*.06,y*.06,1310)<.3?1:0)));}
        }
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(!h.playable(x,y))
            for(int r=y-1;r<=y+1;r++)for(int c=x-1;c<=x+1;c++)if(h.playable(c,r))h.set(x,y,w.elevationConfig.maxHeight());
        reservePondApproaches();
        int margin=Math.max(2,(w.elevationConfig.maxHeight()+1)/2);
        for(int y=margin;y<height-margin;y++)for(int x=margin;x<width-margin;x++){
            boolean valid=true;
            for(int r=y-margin;r<=y+margin && valid;r++)for(int c=x-margin;c<=x+margin;c++)
                if(!eligible[c][r] || w.reserved(c,r)){valid=false;break;}
            // Keep entire pond shore bands, not only water pixels, clear of formations.
            for(int r=Math.max(0,y-4);r<=Math.min(height-1,y+4) && valid;r++)for(int c=Math.max(0,x-4);c<=Math.min(width-1,x+4);c++)
                if(w.terrain(c,r)==Terrain.WATER){valid=false;break;}
            candidates[x][y]=valid;
        }
        var cfg=w.cliffConfig;int maximum=(int)Math.floor(denominator*cfg.maximumCoverage());
        int budget=Math.min(maximum,(int)Math.floor(denominator*cfg.targetCoverage()));
        int preferred=Math.max(cfg.minimumTiles(),(int)Math.round(denominator*cfg.formationAreaFraction()));
        var centers=new ArrayList<Integer>();var seeds=new ArrayList<Candidate>();
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(candidates[x][y])
            seeds.add(new Candidate(y*width+x,n.random(x,y,1400)+.25*w.regions().forest(x,y)-.65*w.regions().flowers(x,y)));
        seeds.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparingInt(Candidate::id));
        int used=0,attempts=0,label=0;
        for(var seed:seeds){
            if(centers.size()>=Math.min(CliffFormationConfig.MAX_FORMATIONS,formationLimit) || budget-used<cfg.minimumTiles() || attempts>=96)break;
            int x=seed.id%width,y=seed.id/width;
            boolean near=false;for(int old:centers)if(Math.hypot(x-old%width,y-old/width)<Math.sqrt(preferred)*1.8)near=true;
            if(near || h.at(x,y)>0)continue;attempts++;
            int target=Math.min(budget-used,Math.max(cfg.minimumTiles(),(int)Math.round(preferred*(.75+n.random(x,y,1401)*.5))));
            var footprint=grow(seed.id,target);
            smooth(footprint); // Erosion / bounded hole filling precedes the authoritative budget check.
            footprint=mainComponent(footprint);
            if(footprint.size()<cfg.minimumTiles() || used+footprint.size()>budget || used+footprint.size()>maximum)continue;
            for(int id:footprint)h.set(id%width,id/width,1);
            var access=lowlandAccess(w);
            if(access[0]<Math.ceil(access[1]*cfg.minimumLowlandAccess()) || !essentialAccess(w)){
                for(int id:footprint)h.set(id%width,id/width,0);continue;
            }
            centers.add(seed.id);used+=footprint.size();label++;
            for(int id:footprint)h.formation[id%width][id/width]=label;
            nestedHeights(footprint,seed.id);
        }
        // All cleanup can only remove footprint cells, and accepted groups have already passed connectivity.
        measure(w,null);
        var stats=h.cliffStatistics();
        if(stats.footprint()>maximum || stats.access()<cfg.minimumLowlandAccess())
            throw new IllegalStateException("Cliff plan violates its coverage/access contract: "+stats);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(h.at(x,y)>0 && w.terrain(x,y)!=Terrain.GRASS)w.ground(x,y,Terrain.GRASS,w.tone(x,y));
    }
    private void reservePondApproaches(){
        boolean water=false;for(int y=0;y<height && !water;y++)for(int x=0;x<width;x++)if(w.terrain(x,y)==Terrain.WATER){water=true;break;}
        if(!water)return;
        int[][] parent=new int[width][height];for(int[] column:parent)Arrays.fill(column,-1);
        int spawn=w.spawnRow*width+w.spawnCol;var queue=new ArrayDeque<Integer>();queue.add(spawn);parent[w.spawnCol][w.spawnRow]=spawn;
        while(!queue.isEmpty()){
            int id=queue.remove(),x=id%width,y=id/width;
            for(int i=0;i<4;i++){int c=x+DX[i],r=y+DY[i];if(!h.playable(c,r) || parent[c][r]>=0 || !eligible[c][r])continue;
                boolean fits=true;for(int yy=r-1;yy<=r+1;yy++)for(int xx=c-1;xx<=c+1;xx++)
                    if(!h.playable(xx,yy) || w.starterReserved(xx,yy) && !w.coreReserved(xx,yy))fits=false;
                if(!fits)continue;parent[c][r]=id;queue.add(r*width+c);
            }
        }
        var approaches=new ArrayList<Integer>();boolean[][] seen=new boolean[width][height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(w.terrain(x,y)==Terrain.WATER && !seen[x][y]){
            int shore=-1;double nearest=Double.MAX_VALUE;queue.add(y*width+x);seen[x][y]=true;
            while(!queue.isEmpty()){
                int id=queue.remove(),c=id%width,r=id/width;
                for(int i=0;i<4;i++){int xx=c+DX[i],yy=r+DY[i];if(!h.inside(xx,yy))continue;
                    if(w.terrain(xx,yy)==Terrain.WATER){if(!seen[xx][yy]){seen[xx][yy]=true;queue.add(yy*width+xx);}}
                    else if(parent[xx][yy]>=0){double d=Math.hypot(xx-w.spawnCol,yy-w.spawnRow);int candidate=yy*width+xx;
                        if(d<nearest || d==nearest && candidate<shore){nearest=d;shore=candidate;}}
                }
            }
            if(shore<0)throw new IllegalStateException("No dry approach to pond near "+x+","+y);
            approaches.add(shore);
            while(shore!=spawn){int c=shore%width,r=shore/width;
                if(w.generatorVersion>=11){if(!w.starterReserved(c,r))w.reserveAccess(c,r);}
                else for(int yy=r-1;yy<=r+1;yy++)for(int xx=c-1;xx<=c+1;xx++)if(h.playable(xx,yy) && !w.starterReserved(xx,yy))w.reserveAccess(xx,yy);
                shore=parent[c][r];
            }
        }
        h.pondApproaches(approaches);
    }
    private Set<Integer> grow(int center,int target){
        int sx=center%width,sy=center/width;double stretch=.8+noise.random(sx,sy,1402)*.5,radius=Math.sqrt(target/Math.PI)*1.8;
        var frontier=new PriorityQueue<Candidate>(Comparator.comparingDouble(Candidate::score).thenComparingInt(Candidate::id));
        var seen=new HashSet<Integer>();var result=new HashSet<Integer>();frontier.add(new Candidate(center,0));seen.add(center);
        while(!frontier.isEmpty() && result.size()<target){
            int id=frontier.remove().id,x=id%width,y=id/width;
            if(!candidates[x][y] || h.at(x,y)>0)continue;
            boolean touches=false;
            for(int r=Math.max(0,y-2);r<=Math.min(height-1,y+2);r++)for(int c=Math.max(0,x-2);c<=Math.min(width-1,x+2);c++)if(h.formation[c][r]>0)touches=true;
            if(touches)continue;result.add(id);
            for(int i=0;i<4;i++){
                int c=x+DX[i],r=y+DY[i],next=r*width+c;
                if(c<0 || r<0 || c>=width || r>=height || !seen.add(next) || !candidates[c][r])continue;
                double dx=(c-sx)/stretch,dy=(r-sy)*stretch;if(Math.hypot(dx,dy)>radius)continue;
                double cost=(dx*dx+dy*dy)/target+(1-noise.value(c*.14,r*.14,1403))*.9;
                frontier.add(new Candidate(next,cost));
            }
        }return result;
    }
    private void smooth(Set<Integer> shape){
        for(int pass=0;pass<2;pass++){
            var next=new HashSet<Integer>(shape);var nearby=new HashSet<Integer>();
            for(int id:shape){int x=id%width,y=id/width,count=0;
                for(int i=0;i<4;i++){int c=x+DX[i],r=y+DY[i];if(c>=0 && r>=0 && c<width && r<height){int p=r*width+c;nearby.add(p);if(shape.contains(p))count++;}}
                if(count<2)next.remove(id);
            }
            for(int id:nearby)if(!shape.contains(id) && candidates[id%width][id/width] && h.at(id%width,id/width)==0){
                int count=0;for(int i=0;i<4;i++)if(shape.contains(id+DY[i]*width+DX[i]))count++;
                if(count>=3)next.add(id);
            }
            shape.clear();shape.addAll(next);
        }
        // Opening with a 3x3 kernel removes thin strips and spikes, then mainComponent removes any split remnants.
        Set<Integer> opened=new HashSet<>();
        for(int id:shape)if(broad(shape,id%width,id/width))
            for(int r=id/width-1;r<=id/width+1;r++)for(int c=id%width-1;c<=id%width+1;c++)opened.add(r*width+c);
        shape.retainAll(opened);
    }
    private boolean broad(Set<Integer> shape,int x,int y){
        for(int r=y-1;r<=y+1;r++)for(int c=x-1;c<=x+1;c++)if(!shape.contains(r*width+c))return false;return true;
    }
    private Set<Integer> mainComponent(Set<Integer> shape){
        var remaining=new TreeSet<Integer>(shape);Set<Integer> largest=new HashSet<>();
        while(!remaining.isEmpty()){
            var part=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();int first=remaining.pollFirst();queue.add(first);part.add(first);
            while(!queue.isEmpty()){int id=queue.remove();for(int i=0;i<4;i++){int next=id+DY[i]*width+DX[i];if(remaining.remove(next)){part.add(next);queue.add(next);}}}
            if(part.size()>largest.size())largest=part;
        }return largest;
    }
    private void nestedHeights(Set<Integer> shape,int seed){
        if(w.elevationConfig.maxHeight()<2 || shape.size()<w.cliffConfig.minimumTiles()*3)return;
        // An interior distance transform creates nested terraces, never detached high-altitude speckles.
        var distance=new HashMap<Integer,Integer>();var queue=new ArrayDeque<Integer>();
        for(int id:shape){boolean edge=false;for(int i=0;i<4;i++)if(!shape.contains(id+DY[i]*width+DX[i]))edge=true;
            if(edge){distance.put(id,0);queue.add(id);}}
        while(!queue.isEmpty()){int id=queue.remove();for(int i=0;i<4;i++){int next=id+DY[i]*width+DX[i];if(shape.contains(next) && !distance.containsKey(next)){distance.put(next,distance.get(id)+1);queue.add(next);}}}
        int maximum=distance.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int threshold=Math.max(3,(int)Math.ceil(maximum*.65));Set<Integer> inner=new HashSet<>();
        for(int id:shape)if(distance.get(id)>=threshold)inner.add(id);inner=mainComponent(inner);
        if(inner.size()<9)return;for(int id:inner)h.set(id%width,id/width,2);
        if(w.elevationConfig.maxHeight()>=3 && shape.size()>=220 && noise.random(seed%width,seed/width,1404)<.08){
            Set<Integer> peak=new HashSet<>();for(int id:inner)if(distance.get(id)>=threshold+3)peak.add(id);peak=mainComponent(peak);
            if(peak.size()>=9)for(int id:peak)h.set(id%width,id/width,3);
        }
    }
    static int[] lowlandAccess(GeneratedWorld w){
        var h=w.heights();int width=h.width,height=h.height,total=0,count=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){h.reachable[x][y]=false;if(h.playable(x,y) && h.at(x,y)==0 && PlacementRules.walkableTerrain(w.terrain(x,y)))total++;}
        var queue=new ArrayDeque<Integer>();queue.add(w.spawnRow*width+w.spawnCol);h.reachable[w.spawnCol][w.spawnRow]=true;
        while(!queue.isEmpty()){int id=queue.remove(),x=id%width,y=id/width;count++;
            for(int i=0;i<4;i++){int c=x+DX[i],r=y+DY[i];if(!h.playable(c,r) || h.reachable[c][r] || h.at(c,r)!=0 || !PlacementRules.walkableTerrain(w.terrain(c,r)))continue;h.reachable[c][r]=true;queue.add(r*width+c);}}
        return new int[]{count,total};
    }
    private static boolean essentialAccess(GeneratedWorld w){
        var h=w.heights();var nest=w.slimeNest();
        if(nest!=null && !h.reachable(nest.col()+nest.size()/2,nest.row()+nest.size()/2))return false;
        for(int id:w.starterFiber())if(!h.reachable(id%h.width,id/h.width))return false;
        for(int id:h.pondApproaches())if(!h.reachable(id%h.width,id/h.width))return false;
        for(var p:w.starterProps())if(!h.reachable(p.col(),p.row()))return false;return true;
    }
    static void measure(GeneratedWorld w,PlacementRules rules){
        var h=w.heights();int[] access=lowlandAccess(w);int land=0,footprint=0,edges=0,inlandEdges=0,clear=0,clearReachable=0;
        Set<Integer> groups=new HashSet<>();
        for(int y=0;y<h.height;y++)for(int x=0;x<h.width;x++){
            if(h.playable(x,y) && PlacementRules.walkableTerrain(w.terrain(x,y))){land++;if(h.at(x,y)>0){footprint++;groups.add(h.formation[x][y]);}}
            if(CliffTileResolver.edge(h,x,y)){edges++;if(h.playable(x,y))inlandEdges++;}
            if(rules!=null && h.playable(x,y) && h.at(x,y)==0 && rules.clearTile(x,y)){clear++;if(w.regions().accessible[x][y])clearReachable++;}
        }
        int components=0;boolean[][] seen=new boolean[h.width][h.height];
        for(int y=0;y<h.height;y++)for(int x=0;x<h.width;x++)if(h.playable(x,y) && h.at(x,y)>0 && !seen[x][y]){
            components++;var queue=new ArrayDeque<Integer>();queue.add(y*h.width+x);seen[x][y]=true;
            while(!queue.isEmpty()){int id=queue.remove(),c=id%h.width,r=id/h.width;for(int i=0;i<4;i++){
                int xx=c+DX[i],yy=r+DY[i];if(!h.playable(xx,yy) || seen[xx][yy] || h.at(xx,yy)==0)continue;
                seen[xx][yy]=true;queue.add(yy*h.width+xx);
            }}
        }
        h.cliffStatistics(new CliffStatistics(land,footprint,edges,inlandEdges,groups.size(),Math.max(0,components-groups.size()),access[1],access[0],clear,clearReachable));
        if(rules!=null)for(int y=0;y<h.height;y++)for(int x=0;x<h.width;x++)h.reachable[x][y]=w.regions().accessible[x][y];
    }
}
