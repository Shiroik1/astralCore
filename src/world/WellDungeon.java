package world;

import main.Gamepanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** A cached, compact damp-stone chamber. Town map files remain untouched. */
public final class WellDungeon {
    public static final int LEFT=14,TOP=10,RIGHT=36,BOTTOM=30;
    public static final int ENTRANCE_COL=25,ENTRANCE_ROW=27;
    public static final int[][] SLIME_SPAWNS={{17,13},{23,13},{28,13},{33,13},{17,18},{25,18},{33,18},{18,24},{25,24},{33,24}};
    private static final int[][] PILLARS={{20,18},{30,18},{20,23},{30,23}};
    private final Gamepanel gp;
    private final BufferedImage floor;
    private final List<WorldObject> objects;

    public WellDungeon(Gamepanel gp){
        this.gp=gp;int ts=gp.tileSize;
        floor=new BufferedImage((RIGHT-LEFT+1)*ts,(BOTTOM-TOP+1)*ts,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=floor.createGraphics();g.setColor(new Color(24,31,37));g.fillRect(0,0,floor.getWidth(),floor.getHeight());
        for(int row=TOP;row<=BOTTOM;row++)for(int col=LEFT;col<=RIGHT;col++){
            int x=(col-LEFT)*ts,y=(row-TOP)*ts;
            if(isSolid(col,row)){
                g.setColor(new Color(42,48,55));g.fillRect(x,y,ts,ts);
                g.setColor(new Color(83,88,91));g.fillRoundRect(x+2,y+3,ts-4,ts-10,5,5);
                g.setColor(new Color(61,68,72));g.fillRect(x+2,y+ts-14,ts-4,8);
                g.setColor(new Color(114,119,116));g.drawLine(x+5,y+5,x+ts-6,y+5);
            }else{
                int variation=Math.floorMod(col*17+row*13,9);
                g.setColor(new Color(58+variation,70+variation,74+variation));g.fillRect(x,y,ts,ts);
                g.setColor(new Color(47,57,61));g.drawRect(x+1,y+1,ts-2,ts-2);
                if((col+row)%5==0){g.setColor(new Color(46,77,65));g.fillOval(x+5,y+ts-12,ts/2,7);}
                if((col*3+row)%7==0){g.setColor(new Color(43,53,58));g.drawLine(x+14,y+10,x+22,y+21);g.drawLine(x+22,y+21,x+17,y+28);}
            }
        }g.dispose();
        objects=new ArrayList<>();
        objects.add(new WorldObject(gp,WorldObjectType.WELL_LADDER,25,28,null));
        objects.add(new WorldObject(gp,WorldObjectType.WELL_ALTAR,25,12,null));
        for(int[] pos:new int[][]{{17,12},{33,12},{17,26},{33,26},{25,20}})
            objects.add(new WorldObject(gp,WorldObjectType.LANTERN,pos[0],pos[1],null));
    }
    public boolean isSolid(int col,int row){
        if(col<=LEFT || col>=RIGHT || row<=TOP || row>=BOTTOM)return true;
        for(int[] p:PILLARS)if(col==p[0] && (row==p[1] || row==p[1]+1))return true;
        return false;
    }
    public List<WorldObject> objects(){return objects;}
    public void draw(Graphics2D g){
        g.setColor(new Color(19,25,31));g.fillRect(0,0,gp.screenWidth,gp.screenHeight);
        var camera=gp.localPlayer();
        g.drawImage(floor,LEFT*gp.tileSize-camera.worldX+camera.screenX,TOP*gp.tileSize-camera.worldY+camera.screenY,null);
    }
}
