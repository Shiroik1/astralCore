package world.generation;

import world.GrassLayer;

/** Patchy, nonblocking grass softens the clearing border without obstructing the central fight. */
final class NestPerimeterGenerator {
    void generate(GeneratedWorld w,NoiseGenerator n){
        var p=w.slimeNest();if(p==null)return;
        for(int y=p.row();y<p.row()+p.size();y++)for(int x=p.col();x<p.col()+p.size();x++){
            int edge=Math.min(Math.min(x-p.col(),p.col()+p.size()-1-x),Math.min(y-p.row(),p.row()+p.size()-1-y));
            if(edge>1 || !w.regions().accessible[x][y] || n.sample(x*.24,y*.24,908)<.46 || n.random(x,y,909)>.26)continue;
            w.meadow(x,y,n.random(x,y,910)<.4?GrassLayer.FIELD_DARK:GrassLayer.FIELD_GREEN,1);
        }
    }
}
