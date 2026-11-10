package ai;

import entity.Monster;
import entity.Player;
import main.Gamepanel;
import world.WorldObject;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/** Small bounded A* search. Waypoints are reused; footprint checks include walls and scenery. */
public final class Pathfinder {
    private final Gamepanel gp;
    private final ArrayDeque<Point> route=new ArrayDeque<>();
    private int scene=-1,goalX=Integer.MIN_VALUE,goalY,ticks;
    public Pathfinder(Gamepanel gp){this.gp=gp;}

    public void chase(Monster m,Player target){
        int ts=gp.tileSize;ticks++;
        int tx=target.worldX+target.solidArea.x+target.solidArea.width/2-m.solidArea.x-m.solidArea.width/2;
        int ty=target.worldY+target.solidArea.y+target.solidArea.height/2-m.solidArea.y-m.solidArea.height/2;
        if(scene!=gp.quests.areaVersion()){route.clear();scene=gp.quests.areaVersion();ticks=30;}
        if(clearLine(m,m.worldX,m.worldY,tx,m.worldY) && clearLine(m,tx,m.worldY,tx,ty)){
            route.clear();steer(m,tx,m.worldX!=tx?m.worldY:ty);if(!m.collisionOn)return;
        }
        while(!route.isEmpty() && route.peek().x==m.worldX && route.peek().y==m.worldY)route.remove();
        boolean atGrid=m.worldX%ts==0 && m.worldY%ts==0;
        if((route.isEmpty() || (atGrid && (goalX!=tx/ts || goalY!=ty/ts))) && ticks>=20){
            route.clear();route.addAll(search(m,tx,ty));goalX=tx/ts;goalY=ty/ts;ticks=0;
        }
        if(!route.isEmpty()){
            Point p=route.peek();steer(m,p.x,p.y);
            if(m.collisionOn){route.clear();ticks=20;m.speed=0;}
        }else m.speed=0;
    }
    private void steer(Monster m,int x,int y){
        int dx=x-m.worldX,dy=y-m.worldY;
        if(dx==0 && dy==0){m.speed=0;return;}
        boolean horizontal=Math.abs(dx)>Math.abs(dy);
        m.direction=horizontal?(dx>0?"right":"left"):(dy>0?"down":"up");
        m.speed=Math.min(m.speed,Math.abs(horizontal?dx:dy));
        m.collisionOn=false;gp.collisionChecker.checkTile(m);
    }
    private boolean clearLine(Monster m,int x,int y,int tx,int ty){
        int steps=Math.max(1,Math.max(Math.abs(tx-x),Math.abs(ty-y))/(gp.tileSize/3));
        for(int i=1;i<=steps;i++)if(!walkable(m,x+(tx-x)*i/steps,y+(ty-y)*i/steps,null))return false;
        return true;
    }
    private boolean walkable(Monster m,int x,int y,List<WorldObject> scenery){
        int left=x+m.solidArea.x,top=y+m.solidArea.y,right=left+m.solidArea.width,bottom=top+m.solidArea.height;
        if(gp.tileM.blocksMovement(left,top,right,bottom))return false;
        for(WorldObject o:scenery!=null?scenery:gp.worldObjects)if(o.blocksArea(left,top,right-left,bottom-top))return false;
        for(var tile:gp.interactable)if(tile!=null && tile.collision && new Rectangle(tile.worldX+tile.solidArea.x,tile.worldY+tile.solidArea.y,tile.solidArea.width,tile.solidArea.height).intersects(left,top,right-left,bottom-top))return false;
        return true;
    }
    private record Node(int id,int score) {}
    private List<Point> search(Monster m,int tx,int ty){
        int ts=gp.tileSize,sx=Math.round(m.worldX/(float)ts),sy=Math.round(m.worldY/(float)ts);
        int gx=Math.round(tx/(float)ts),gy=Math.round(ty/(float)ts);
        int minX=Math.max(0,Math.min(sx,gx)-5),minY=Math.max(0,Math.min(sy,gy)-5);
        int maxX=Math.min(gp.maxWorldCol-1,Math.max(sx,gx)+5),maxY=Math.min(gp.maxWorldRow-1,Math.max(sy,gy)+5);
        int width=maxX-minX+1,height=maxY-minY+1;if(width<=0 || height<=0)return List.of();
        var nearby=new ArrayList<WorldObject>();
        for(WorldObject o:gp.worldObjects)if(o.blocksArea(minX*ts,minY*ts,(width+3)*ts,(height+3)*ts))nearby.add(o);
        int[] distance=new int[width*height],parent=new int[width*height];Arrays.fill(distance,Integer.MAX_VALUE);Arrays.fill(parent,-1);
        byte[] valid=new byte[width*height];boolean[] closed=new boolean[width*height];
        int start=(sy-minY)*width+sx-minX;if(start<0 || start>=distance.length)return List.of();
        distance[start]=0;var open=new PriorityQueue<Node>(Comparator.comparingInt(Node::score));open.add(new Node(start,0));
        int found=-1,best=start,bestDist=Integer.MAX_VALUE,visits=0;
        int[] dx={1,0,-1,0},dy={0,1,0,-1};
        while(!open.isEmpty() && visits++<2048){
            int id=open.remove().id;if(closed[id])continue;closed[id]=true;
            int x=id%width+minX,y=id/width+minY,remaining=Math.abs(x-gx)+Math.abs(y-gy);
            if(remaining<bestDist){bestDist=remaining;best=id;}
            if(remaining==0){found=id;break;}
            for(int i=0;i<4;i++){
                int nx=x+dx[i],ny=y+dy[i];if(nx<minX || nx>maxX || ny<minY || ny>maxY)continue;
                int next=(ny-minY)*width+nx-minX;if(closed[next])continue;
                if(valid[next]==0)valid[next]=(byte)(walkable(m,nx*ts,ny*ts,nearby)?1:-1);
                if(valid[next]<0 || distance[next]<=distance[id]+1)continue;
                distance[next]=distance[id]+1;parent[next]=id;
                open.add(new Node(next,distance[next]+Math.abs(nx-gx)+Math.abs(ny-gy)));
            }
        }
        if(found<0)found=best;
        var points=new ArrayDeque<Point>();
        for(int id=found;id!=start && id>=0;id=parent[id])points.addFirst(new Point((id%width+minX)*ts,(id/width+minY)*ts));
        if((m.worldX!=sx*ts || m.worldY!=sy*ts) && walkable(m,sx*ts,sy*ts,nearby))points.addFirst(new Point(sx*ts,sy*ts));
        return List.copyOf(points);
    }
}
