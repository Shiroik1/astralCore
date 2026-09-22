package entity;

import main.Gamepanel;

public class Projectile extends Entity{

    Entity user;
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
            switch (direction){
                case "up" -> worldY -= speed;
                case "down" -> worldY += speed;
                case "left" -> worldX -= speed;
                case "right" -> worldX += speed;
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