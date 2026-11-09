package entity;

import main.Gamepanel;

import java.awt.*;
import java.awt.geom.Arc2D;

public class Monster extends Entity {

    protected int monsterAttackFrameDelay = 8;
    protected int attackDamageStartFrame = 5;
    protected int attackDamageEndFrame = 7;
    protected int monsterAttackCooldown = 40; // ticks of rest between attack cycles
    protected int deathAnimFrameDelay = 6;
    protected int deathDuration = 65;
    public String questId = "";
    public int hitSequence, criticalHitRgb;
    private boolean rangerPinnedReplica,assassinBleedReplica;
    private final java.util.Set<Player> assassinPairs=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final Color PIN_RING=new Color(0xD8E9A6);
    public String hitDirection;
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

    void stagger(int ticks){
        if(isBoss() || dying)return;
        if(attackState){animState="idle";spriteNum=1;}
        attackState=false;
        attacking=false;
        attackCooldownCounter=Math.max(attackCooldownCounter,ticks);
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

    boolean completeAssassinPair(Player player){
        boolean critical=assassinPairs.remove(player);
        if(!critical)assassinPairs.add(player);
        comboStacks=assassinPairs.isEmpty()?0:1;
        return critical;
    }

    int visibleComboStacks(){
        Player local=gp.localPlayer();
        return local!=null && local.isAssassin() && local.canResolveWorldActions()
                ?(assassinPairs.contains(local)?1:0):comboStacks;
    }

    public int assassinPairMask(){
        int mask=0;
        for(Player player:assassinPairs){
            if(player.playerId>=0 && player.playerId<gp.players.length && gp.players[player.playerId]==player)mask|=1<<player.playerId;
        }
        return mask;
    }

    public void restoreAssassinPair(Player player){
        if(player!=null && player.isAssassin()){assassinPairs.add(player);comboStacks=1;}
    }

    public void clearAssassinPairs(){assassinPairs.clear();comboStacks=0;}

    public boolean hasAssassinBleed(){return hasStatusEffect(AssassinCombat.BLEED);}

    public void syncAssassinBleed(boolean bleeding){
        if(bleeding && !assassinBleedReplica && !dying)AssassinCombat.bleedCue(this,false);
        assassinBleedReplica=bleeding;
    }

    public void syncRangerPin(boolean pinned){
        if(pinned && !rangerPinnedReplica && !dying)RangerCombat.pinParticles(this);
        rangerPinnedReplica=pinned;
    }

    private Player challenger;
    private long challengeUntil;
    private boolean challengeReplica;

    void challenge(Player source,long until){challenger=source;challengeUntil=until;}
    public boolean isChallenged(){
        return PaladinCombat.living(challenger) && challenger.inWell==gp.quests.inDungeon()
                && challenger.playerId>=0 && challenger.playerId<gp.players.length
                && gp.players[challenger.playerId]==challenger && gp.currentTick<challengeUntil;
    }
    public void syncChallengeMark(boolean active){challengeReplica=active;}

    private boolean berserkerSunderReplica;
    public boolean hasBerserkerSunder(){return hasStatusEffect(BerserkerCombat.SUNDERED);}
    public void syncBerserkerSunder(boolean active){berserkerSunderReplica=active;}

    @Override public void draw(Graphics2D g){
        if(!dying && (rangerPinnedReplica || hasStatusEffect("rangerSlow"))){
            Player camera=gp.localPlayer();
            int x=worldX-camera.worldX+camera.screenX+solidArea.x;
            int y=sortY()-camera.worldY+camera.screenY-10;
            Color color=g.getColor();Stroke stroke=g.getStroke();
            var ring=new Arc2D.Float(x-5,y,solidArea.width+10,17,195,150,Arc2D.OPEN);
            g.setColor(new Color(37,56,37,225));g.setStroke(new BasicStroke(5));g.draw(ring);
            g.setColor(PIN_RING);g.setStroke(new BasicStroke(2.7f));g.draw(ring);
            g.setColor(color);g.setStroke(stroke);
        }
        super.draw(g);
        if(!dying && (challengeReplica || isChallenged())){
            var marker=(Graphics2D)g.create();
            Player camera=gp.localPlayer();
            int x=worldX+(int)solidArea.getCenterX()-camera.worldX+camera.screenX;
            int y=worldY-29-camera.worldY+camera.screenY;
            var tip=new java.awt.geom.Path2D.Float();
            for(int i=0;i<2;i++){
                tip.moveTo(x-7,y+i*6-5);tip.lineTo(x,y+i*6);tip.lineTo(x+7,y+i*6-5);
            }
            marker.setStroke(new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            marker.setColor(new Color(0x4B4658));marker.draw(tip);
            marker.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            marker.setColor(new Color(0xF5CB87));marker.draw(tip);marker.dispose();
        }
        if(!dying && (berserkerSunderReplica || hasBerserkerSunder())){
            var mark=(Graphics2D)g.create();
            Player camera=gp.localPlayer();
            int x=worldX+(int)solidArea.getCenterX()-camera.worldX+camera.screenX-12;
            int y=worldY-29-camera.worldY+camera.screenY;
            Shape diamond=MageEffect.diamond(x,y,7,7);
            mark.setColor(new Color(0x533735));mark.setStroke(new BasicStroke(4));mark.draw(diamond);mark.fill(diamond);
            mark.setColor(new Color(0xEAA787));mark.setStroke(new BasicStroke(2));mark.draw(diamond);
            mark.drawPolyline(new int[]{x+2,x-1,x+1,x-2},new int[]{y-5,y,y+1,y+5},4);
            mark.dispose();
        }
        if(!dying && (assassinBleedReplica || hasAssassinBleed())){
            Player camera=gp.localPlayer();
            int x=worldX+(int)solidArea.getCenterX()-camera.worldX+camera.screenX;
            int y=worldY-29-camera.worldY+camera.screenY;
            var marker=new Polygon(new int[]{x,x+7,x,x-7},new int[]{y-7,y,y+7,y},4);
            Stroke old=g.getStroke();
            g.setStroke(new BasicStroke(4));g.setColor(new Color(0x533542));g.draw(marker);g.fill(marker);
            g.setStroke(new BasicStroke(2));g.setColor(AssassinCombat.CORAL);g.draw(marker);
            g.setStroke(old);
        }
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
        if(isChallenged()){
            Player target=challenger;
            if(isBoss())challenger=null;
            return target;
        }
        challenger=null;
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
