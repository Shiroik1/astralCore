package crafting;

import entity.*;
import main.Gamepanel;
import java.awt.*;

/** Beginner weapons use the existing projectile list and damage resolution, independently of class kits. */
final class CraftedBolt extends Entity {
    private final Player owner;private final boolean magic;private double x,y,dx,dy;private int ticks=55;
    CraftedBolt(Gamepanel gp,Player owner,int targetX,int targetY,boolean magic){
        super(gp);this.owner=owner;this.magic=magic;x=owner.worldX+owner.solidArea.getCenterX();y=owner.worldY+owner.solidArea.getCenterY();
        double length=Math.hypot(targetX-x,targetY-y);if(length<1)length=1;dx=(targetX-x)/length*gp.tileSize*.16;dy=(targetY-y)/length*gp.tileSize*.16;worldX=(int)x;worldY=(int)y;alive=true;
    }
    @Override public void update(){
        for(int step=0;step<3;step++){
            x+=dx/3;y+=dy/3;worldX=(int)x;worldY=(int)y;
            if(gp.tileM.blocksMovement(worldX-2,worldY-2,worldX+2,worldY+2) || gp.buildings.blocksArea(worldX-2,worldY-2,4,4)){alive=false;return;}
            if(gp.crafting.blocks(new Rectangle(worldX-2,worldY-2,4,4))){alive=false;return;}
            for(var scenery:gp.worldObjects)if(scenery.blocksArea(worldX-2,worldY-2,4,4)){alive=false;return;}
            for(int i=0;i<gp.monster.length;i++){Monster m=gp.monster[i];if(m==null || !m.alive || m.dying)continue;
                if(new Rectangle(m.worldX+m.solidArea.x,m.worldY+m.solidArea.y,m.solidArea.width,m.solidArea.height).intersects(worldX-4,worldY-4,8,8)){
                    owner.damageMonster(i,owner.getAttack());alive=false;return;
                }
            }
        }
        if(--ticks<=0)alive=false;
    }
    @Override public void draw(Graphics2D g){Player camera=gp.localPlayer();int sx=worldX-camera.worldX+camera.screenX,sy=worldY-camera.worldY+camera.screenY;
        g.setColor(magic?new Color(0x7C90CC):new Color(0x8A6B58));g.setStroke(new BasicStroke(3));if(magic)g.fillOval(sx-5,sy-5,10,10);else g.drawLine(sx,sy,(int)(sx-dx*1.5),(int)(sy-dy*1.5));}
}
