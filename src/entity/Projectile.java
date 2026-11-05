package entity;

import main.Gamepanel;

public class Projectile extends Entity{

    Entity user;
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

        if(user.type == type_player){
            int monsterIndex = gp.collisionChecker.checkEntity(this, gp.monster);
            if(monsterIndex != 999){
                if(((Player) user).canResolveWorldActions()){
                    ((Player) user).damageMonster(monsterIndex, attack, empowered);
                }
                generateParticle(user.projectile, gp.monster[monsterIndex]);
                alive = false;
            }
        }
        else {
            Player contactedPlayer = gp.collisionChecker.checkPlayer(this);
            if(contactedPlayer != null && !contactedPlayer.invincible){
                if(!gp.isNetworked || gp.isHost){
                    damagePlayer(contactedPlayer, attack);
                }
                generateParticle(user.projectile, contactedPlayer);
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
        if(spriteCounter > 12){
            if(spriteNum == 1){
                spriteNum = 2;
            }
            else if(spriteNum == 2){
                spriteNum = 1;
            }
            spriteCounter = 0;
        }
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