package world;

import world.generation.HeightMap;

/** Grid collision stays in logical coordinates; projection never changes collision Y. */
public final class ElevationCollisionHandler {
    private ElevationCollisionHandler(){}
    public static boolean blocks(HeightMap map,int ts,int left,int top,int right,int bottom){
        int firstX=Math.floorDiv(left,ts),lastX=Math.floorDiv(right,ts),firstY=Math.floorDiv(top,ts),lastY=Math.floorDiv(bottom,ts);
        int level=map.at(firstX,firstY),inset=Math.max(2,ts/8);
        for(int y=firstY;y<=lastY;y++)for(int x=firstX;x<=lastX;x++){
            if(!map.playable(x,y) || map.at(x,y)!=level)return true;
            int px=x*ts,py=y*ts;
            if((!map.playable(x-1,y) || map.at(x-1,y)!=level) && left<px+inset
                    || (!map.playable(x+1,y) || map.at(x+1,y)!=level) && right>=px+ts-inset
                    || (!map.playable(x,y-1) || map.at(x,y-1)!=level) && top<py+inset
                    || (!map.playable(x,y+1) || map.at(x,y+1)!=level) && bottom>=py+ts-inset)return true;
        }return false;
    }
}
