package entity;

import main.Gamepanel;
import monster.SlimeMotion;
import java.awt.Rectangle;
import java.util.Set;

/** Fixed-step projectile motion with fractional positions and swept collision samples. */
public class Projectile extends Entity {
    Entity user;
    public boolean networkReplica,collidesWithWorld,clearOnAreaExit;
    public Set<Player> burstHits;
    public float replicaVelocityX,replicaVelocityY;
    private double x,y,vx,vy;
    private boolean positionReady,angled;
    private int sceneVersion;
    private CombatHit hit;
    public Entity target;
    public boolean empowered;
    public Projectile(Gamepanel gp){super(gp);}
    public boolean firedBy(Entity source){return user==source;}
    public float velocityX(){return (float)(angled?vx:direction.equals("right")?speed:direction.equals("left")?-speed:0);}
    public float velocityY(){return (float)(angled?vy:direction.equals("down")?speed:direction.equals("up")?-speed:0);}
    private void launch(int px,int py,boolean live,Entity source){
        worldX=px;worldY=py;x=px;y=py;positionReady=true;alive=live;user=source;HP=maxHP;sceneVersion=gp.quests.areaVersion();
        hit=new CombatHit("projectile");if(source instanceof Player p && p.currentWeapon!=null && !p.currentWeapon.isPlaceholder)hit.mastery(p.currentWeapon.type);
    }
    public void set(int px,int py,String heading,boolean live,Entity source){set(px,py,heading,live,source,null);}
    public void set(int px,int py,String heading,boolean live,Entity source,Entity target){
        launch(px,py,live,source);direction=heading;this.target=target;angled=false;
    }
    public void setAngled(int px,int py,double angle,boolean live,Entity source){setVector(px,py,Math.cos(angle),Math.sin(angle),live,source);}
    public void setVector(int px,int py,double dx,double dy,boolean live,Entity source){
        launch(px,py,live,source);target=null;angled=true;
        double length=Math.hypot(dx,dy);if(length<1e-9){dx=0;dy=1;length=1;}
        vx=dx/length*speed;vy=dy/length*speed;direction=SlimeMotion.facing(vx,vy);
    }
    @Override public void update(){
        if(!alive)return;
        if(!positionReady || worldX!=(int)Math.round(x) || worldY!=(int)Math.round(y)){x=worldX;y=worldY;positionReady=true;}
        if(networkReplica){x+=replicaVelocityX;y+=replicaVelocityY;worldX=(int)Math.round(x);worldY=(int)Math.round(y);updateAnimation();return;}
        if(clearOnAreaExit && sceneVersion!=gp.quests.areaVersion()){alive=false;return;}
        if(user==null){alive=false;return;}
        double dx=velocityX(),dy=velocityY();
        if(target!=null && target.alive && !target.dying){
            double tx=target.worldX+gp.tileSize/2.0-x-gp.tileSize/2.0,ty=target.worldY+gp.tileSize/2.0-y-gp.tileSize/2.0;
            double length=Math.hypot(tx,ty),travel=Math.min(speed,length);
            if(length>0){dx=tx/length*travel;dy=ty/length*travel;direction=SlimeMotion.facing(dx,dy);}else{dx=0;dy=0;}
        }
        double startX=x,startY=y;int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dy))/2));
        for(int i=1;i<=steps && alive;i++){
            double nx=startX+dx*i/steps,ny=startY+dy*i/steps;int px=(int)Math.round(nx),py=(int)Math.round(ny);
            Rectangle body=SlimeMotion.body(this,px,py);
            if(collidesWithWorld && !SlimeMotion.clear(gp,body)){alive=false;break;}
            if(user instanceof Player owner){
                for(int j=0;j<gp.monster.length;j++){
                    Monster monster=gp.monster[j];if(monster==null || !monster.alive || monster.dying || !body.intersects(SlimeMotion.body(monster,monster.worldX,monster.worldY)))continue;
                    double push=owner.currentWeapon!=null && owner.currentWeapon.type==type_staff?CombatTuning.BOLT:CombatTuning.ARROW;
                    owner.damageMonsterOnce(j,attack,empowered,direction,hit,push);generateParticle(this,monster);alive=false;break;
                }
            }else{
                Player contacted=null;
                for(Player p:gp.players)if(SlimeMotion.activePlayer(gp,p) && body.intersects(SlimeMotion.body(p,p.worldX,p.worldY))){contacted=p;break;}
                if(PaladinCombat.interceptProjectile(this,px-worldX,py-worldY,contacted)){alive=false;break;}
                if(contacted!=null){
                    if((!gp.isNetworked || gp.isHost) && !contacted.invincible && (burstHits==null || !burstHits.contains(contacted))){
                        if(burstHits!=null)burstHits.add(contacted);damagePlayer(contacted,attack);generateParticle(this,contacted);
                    }
                    alive=false;
                }
            }
            x=nx;y=ny;worldX=px;worldY=py;
        }
        if(collidesWithWorld && (worldX+solidArea.x+solidArea.width<0 || worldY+solidArea.y+solidArea.height<0
                || worldX+solidArea.x>=gp.maxWorldCol*gp.tileSize || worldY+solidArea.y>=gp.maxWorldRow*gp.tileSize))alive=false;
        if(--HP<=0)alive=false;
        spriteCounter++;SpriteAnimation a=getCurrentAnimation();int delay=a==null?12:a.frameDelay,count=a==null?2:a.frames.length;
        if(spriteCounter>delay){spriteNum=spriteNum%count+1;spriteCounter=0;}
    }
    public boolean hasResource(Entity user){return false;}
    public void subtractResource(Entity user){}
}
