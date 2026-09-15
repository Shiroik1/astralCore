package entity;

import main.Gamepanel;

public class Projectile extends Entity{

    Entity user;

    public Projectile(Gamepanel gp) {
        super(gp);
    }

    public void set(int worldX, int worldY, String direction, boolean alive, Entity user){
        this.worldX = worldX;
        this.worldY = worldY;
        this.direction = direction;
        this.alive = alive;
        this.user = user;
        this.HP = this.maxHP;
    }

    public void update(){

        if(user.type == type_player){
            int monsterIndex = gp.collisionChecker.checkEntity(this, gp.monster);
            if(monsterIndex != 999){
                if(((Player) user).canResolveWorldActions()){
                    ((Player) user).damageMonster(monsterIndex, attack);
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

        switch (direction){
            case "up" -> worldY -= speed;
            case "down" -> worldY += speed;
            case "left" -> worldX -= speed;
            case "right" -> worldX += speed;
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

    public boolean hasResource(Entity user){
        boolean hasResource = false;
        return hasResource;
    }

    public void subtractResource(Entity user){

    }
}
