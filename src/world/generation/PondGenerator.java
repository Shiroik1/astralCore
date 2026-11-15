package world.generation;

import art.Terrain;
import java.util.ArrayList;
import java.util.Comparator;

/** Small irregular ponds selected from a correlated wetness field, away from protected routes. */
final class PondGenerator {
    private record Candidate(int col,int row,double priority) {}
    void generate(GeneratedWorld world,NoiseGenerator noise){
        WorldConfig config=world.config;
        if(config.pondDensity()==0)return;
        var candidates=new ArrayList<Candidate>();
        for(int r=7;r<config.height()-7;r++) for(int c=7;c<config.width()-7;c++){
            if(noise.sample(c*.065,r*.065,110)<.56 || noise.random(c,r,113)>.04)continue;
            candidates.add(new Candidate(c,r,noise.random(c,r,114)));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::priority));
        int limit=Math.max(1,(int)Math.round(config.width()*(double)config.height()/2400*config.pondDensity()));
        var centers=new ArrayList<Candidate>();
        for(Candidate candidate:candidates){
            if(centers.size()>=limit)break;
            int x=candidate.col,y=candidate.row;
            double rx=2.5+noise.random(x,y,115)*1.8,ry=2+noise.random(x,y,116)*1.4;
            boolean fits=true;
            for(Candidate center:centers)if(Math.hypot(x-center.col,y-center.row)<15)fits=false;
            // Protect the entire pond bounding box and a two-tile shore margin.
            for(int r=y-6;r<=y+6 && fits;r++) for(int c=x-7;c<=x+7;c++)
                if(world.reserved(c,r)){fits=false;break;}
            if(!fits)continue;
            for(int r=y-4;r<=y+4;r++) for(int c=x-5;c<=x+5;c++){
                double dx=(c-x)/rx,dy=(r-y)/ry;
                double edge=1+(noise.value(c*.45,r*.45,117)-.5)*.3;
                if(dx*dx+dy*dy<edge)world.ground(c,r,Terrain.WATER,world.tone(c,r));
            }
            centers.add(candidate);
        }
    }
}
