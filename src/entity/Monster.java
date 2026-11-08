package entity;

import main.Gamepanel;

public class Monster extends Entity {

    protected int monsterAttackFrameDelay = 8;
    protected int attackDamageStartFrame = 5;
    protected int attackDamageEndFrame = 7;
    protected int monsterAttackCooldown = 40; // ticks of rest between attack cycles
    protected int deathAnimFrameDelay = 6;
    protected int deathDuration = 65;
    public String questId = "";
    private final ai.Pathfinder pathfinder;
    public int aggroRange = 0; // tiles; 0 = chase disabled, subclasses override

    private int attackCooldownCounter = 0;
    private int deathAnimFrameCounter = 0;
    private boolean attackDamageAppliedThisCycle = false;

    public Monster(Gamepanel gp){
        super(gp);
        type = type_monster;
        pathfinder=new ai.Pathfinder(gp);
    }

    public boolean isBoss(){ return false; }
    public boolean canDealContactDamage(){ return true; }

    @Override
    public boolean canBeKnockedBack(){ return !isBoss(); }

    @Override
    public void update(){
        if(dying){
            if(dyingCounter == 0){
                animState = "death";
                spriteNum = 1;
                attackState = false;
                attacking = false;
                flashing = false;
            }
            updateDeathAnimation();
            if(dyingCounter > deathDuration){
                alive = false;
            }
            return;
        }

        if(!attackState){
            if(attackCooldownCounter > 0){
                attackCooldownCounter--;
            } else {
                Player nearbyPlayer = gp.collisionChecker.checkPlayer(this);
                if(nearbyPlayer != null){
                    startMonsterAttack();
                }
            }
        }

        if(attackState){
            if(knockbackActive){
                updateKnockback();
            }
            Player nearbyPlayer = gp.collisionChecker.checkPlayer(this);
            updateMonsterAttack(nearbyPlayer);
            updateAnimation();

            if(invincible){
                invincibleCounter++;
                if(invincibleCounter > invincibilityDuration){
                    invincible = false;
                    invincibleCounter = 0;
                }
            }

            updateStatusEffects();
            updateAura();
            return;
        }

        super.update(); // shared path: setAction, knockback, collision, movement, animation, status effects
    }

    private void startMonsterAttack(){
        attackState = true;
        attacking = true;
        animState = "attack";
        spriteNum = 1;
        spriteCounter = 0;
        attackDamageAppliedThisCycle = false;
    }

    private void updateMonsterAttack(Player contactedPlayer){
        SpriteAnimation anim = getCurrentAnimation();
        int totalFrames = (anim != null) ? anim.frames.length : attackDamageEndFrame;

        spriteCounter++;
        if(spriteCounter >= monsterAttackFrameDelay){
            spriteCounter = 0;
            spriteNum++;
            if(spriteNum > totalFrames){
                attackState = false;
                attacking = false;
                spriteNum = 1;
                spriteCounter = 0;
                attackCooldownCounter = monsterAttackCooldown;
                animState = moving ? "walk" : "idle";
                return;
            }
        }

        if(spriteNum >= attackDamageStartFrame && spriteNum <= attackDamageEndFrame && !attackDamageAppliedThisCycle){
            if(contactedPlayer != null && !contactedPlayer.isDead){
                damagePlayer(contactedPlayer, attack);
            }
            attackDamageAppliedThisCycle = true;
        }
    }

    private void updateDeathAnimation(){
        dyingCounter++;

        SpriteAnimation anim = sprites.get("death_" + direction);
        if(anim != null){
            deathAnimFrameCounter++;
            if(deathAnimFrameCounter >= deathAnimFrameDelay){
                deathAnimFrameCounter = 0;
                if(spriteNum < anim.frames.length){
                    spriteNum++;
                }
            }
        }
    }

    protected Player findNearestPlayer(){
        Player nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        for(Player p : gp.players){
            if(p == null || p.isDead || p.inWell!=gp.quests.inDungeon()) continue;
            int dist = Math.abs(worldX - p.worldX) + Math.abs(worldY - p.worldY);
            if(dist < nearestDist){
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    protected boolean isPlayerInAggroRange(){
        if(aggroRange <= 0) return false;
        Player target = findNearestPlayer();
        if(target == null) return false;

        int xDistance = Math.abs(worldX - target.worldX);
        int yDistance = Math.abs(worldY - target.worldY);
        int tileDistance = Math.max(xDistance, yDistance) / gp.tileSize;
        return tileDistance < aggroRange;
    }

    protected void chasePlayer(){
        Player target = findNearestPlayer();
        if(target == null) return;

        pathfinder.chase(this,target);
    }
}
