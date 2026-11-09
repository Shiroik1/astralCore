package entity;

import main.Gamepanel;

public class Projectile extends Entity{

    Entity user;
    public boolean networkReplica;
    public float replicaVelocityX,replicaVelocityY;
    public float velocityX(){return useAngledMovement?vx:direction.equals("right")?speed:direction.equals("left")?-speed:0;}
    public float velocityY(){return useAngledMovement?vy:direction.equals("down")?speed:direction.equals("up")?-speed:0;}
    private float vx, vy;
    private boolean useAngledMovement = false;
    public Entity target;
    public boolean empowered = false;

    public Projectile(Gamepanel gp) {
        super(gp);
    }

    public void set(int worldX, int worldY, String direction, boolean alive, Entity user){
        set(worldX, worldY, direction, alive, user, null);
    }

    public void set(int worldX, int worldY, String direction, boolean alive, Entity user, Entity target){
        this.worldX = worldX;
        this.worldY = worldY;
        this.direction = direction;
        this.alive = alive;
        this.user = user;
        this.target = target;
        this.HP = this.maxHP;
        this.useAngledMovement = false;
    }

    public void setAngled(int worldX, int worldY, double angleRadians, boolean alive, Entity user){
        this.worldX = worldX;
        this.worldY = worldY;
        this.vx = (float)(Math.cos(angleRadians) * speed);
        this.vy = (float)(Math.sin(angleRadians) * speed);
        this.direction = angleToDirection(angleRadians);
        this.alive = alive;
        this.user = user;
        this.HP = this.maxHP;
        this.useAngledMovement = true;
    }

    private String angleToDirection(double angleRadians){
        double deg = Math.toDegrees(angleRadians);
        deg = ((deg % 360) + 360) % 360;
        if(deg >= 45 && deg < 135) return "down";
        if(deg >= 135 && deg < 225) return "left";
        if(deg >= 225 && deg < 315) return "up";
        return "right";
    }

    public void update(){
        if(!alive)return;
        if(networkReplica){worldX+=replicaVelocityX;worldY+=replicaVelocityY;updateAnimation();return;}

        if(user.type == type_player){
            int monsterIndex = gp.collisionChecker.checkEntity(this, gp.monster);
            if(monsterIndex != 999){
                if(((Player) user).canResolveWorldActions()){
                    ((Player)user).damageMonster(monsterIndex,attack,empowered,direction);
                }
                generateParticle(user.projectile==null?this:user.projectile, gp.monster[monsterIndex]);
                alive = false;
            }
        }
        else {
            int dx=(int)(worldX+velocityX())-worldX,dy=(int)(worldY+velocityY())-worldY;
            Player contactedPlayer=hitPlayerAlongFlight(dx,dy);
            if(PaladinCombat.interceptProjectile(this,dx,dy,contactedPlayer)){
                alive=false;
                return;
            }
            if(contactedPlayer != null && !contactedPlayer.invincible){
                if(!gp.isNetworked || gp.isHost){
                    damagePlayer(contactedPlayer, attack);
                }
                generateParticle(this, contactedPlayer);
                alive = false;
            }
        }

        if(target != null && target.alive && !target.dying){
            moveToward(target.worldX + gp.tileSize/2, target.worldY + gp.tileSize/2);
        } else {
            if(useAngledMovement){
                worldX += vx;
                worldY += vy;
            } else {
                switch (direction) {
                    case "up" -> worldY -= speed;
                    case "down" -> worldY += speed;
                    case "left" -> worldX -= speed;
                    case "right" -> worldX += speed;
                }
            }
        }

        HP--;
        if(HP <= 0){
            alive = false;
        }

        spriteCounter++;
        SpriteAnimation animation=getCurrentAnimation();
        int frameDelay=animation!=null?animation.frameDelay:12;
        int frameCount=animation!=null?animation.frames.length:2;
        if(spriteCounter>frameDelay){
            spriteNum=spriteNum%frameCount+1;
            spriteCounter=0;
        }
    }

    private Player hitPlayerAlongFlight(int dx,int dy){
        java.awt.Rectangle swept=new java.awt.Rectangle(worldX+solidArea.x+Math.min(0,dx),worldY+solidArea.y+Math.min(0,dy),solidArea.width+Math.abs(dx),solidArea.height+Math.abs(dy));
        for(Player p:gp.players)if(p!=null && p.inWell==gp.quests.inDungeon() && !p.isDead && !p.invincible && swept.intersects(p.worldX+p.solidArea.x,p.worldY+p.solidArea.y,p.solidArea.width,p.solidArea.height))return p;
        return null;
    }

    private void moveToward(int targetX, int targetY){
        int centerX = worldX + gp.tileSize/2;
        int centerY = worldY + gp.tileSize/2;
        double dx = targetX - centerX;
        double dy = targetY - centerY;
        double distance = Math.sqrt(dx*dx + dy*dy);

        if(distance < speed){
            worldX = targetX - gp.tileSize/2;
            worldY = targetY - gp.tileSize/2;
            return;
        }

        worldX += (int) Math.round((dx / distance) * speed);
        worldY += (int) Math.round((dy / distance) * speed);

        direction = Math.abs(dx) > Math.abs(dy)
                ? (dx > 0 ? "right" : "left")
                : (dy > 0 ? "down" : "up");
    }

    public boolean hasResource(Entity user){
        boolean hasResource = false;
        return hasResource;
    }

    public void subtractResource(Entity user){

    }
}
