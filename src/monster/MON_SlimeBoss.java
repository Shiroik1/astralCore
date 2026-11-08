package monster;

import art.SlimeArt;
import entity.Monster;
import entity.Particle;
import entity.Player;
import main.Gamepanel;
import object.OBJ_bronzecoin;
import object.OBJ_crystal;
import object.OBJ_heart;
import object.OBJ_slimebolt;
import entity.SpriteAnimation;
import java.awt.image.BufferedImage;

import java.awt.*;
import java.util.Random;

public class MON_SlimeBoss extends Monster {

    public String bossState = "idle"; // idle, telegraph_slam, telegraph_spray, cooldown
    public int bossStateTimer = 0;
    public boolean lastAttackWasSlam = false;

    private final int idleAttackDecisionInterval = 90; // ~1.5s of chasing before it decides to attack
    private final int telegraphSlamDuration = 60;       // 1s warning before slam lands
    private final int telegraphSprayDuration = 40;
    private final int cooldownDuration = 90;
    private final int slamRadius = 120;                 // px
    private final int sprayProjectileCount = 5;
    private final int sprayFanSpreadDegrees = 60;

    public MON_SlimeBoss(Gamepanel gp) {
        super(gp);

        name = "Slime King";
        speed = 2;
        maxHP = 60;
        HP = maxHP;
        attack = 8;
        defense = 0;
        exp = 30;
        aggroRange = 8;

        int size = gp.tileSize * 3;

        solidArea.x = 6;
        solidArea.y = 36;
        solidArea.width = 84;
        solidArea.height = 60;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        getImage(size);
    }

    public void getImage(int size){
        // Its collision box is centered 24px right of a normal tile center.
        // Convert that correction into the 64-unit slime design space.
        SlimeArt.buildBossAnimations(sprites, size, 1.25f, 10.67f);
        down1 = sprites.get("idle_down").frames[0];
    }

    @Override public boolean isBoss(){ return true; }
    @Override public boolean canDealContactDamage(){ return !bossState.equals("telegraph_slam"); }

    @Override
    public void update(){
        knockbackActive=false;knockbackRemaining=0;
        if(!dying && (bossState.equals("telegraph_slam") || bossState.equals("telegraph_spray") || landing())){
            // Special attacks own their clock and cannot be interrupted by contact attacks.
            attackState=false;attacking=false;moving=false;speed=0;
            setAction();
            animState="idle";
            updateAnimation();updateStatusEffects();updateAura();
            if(invincible && ++invincibleCounter>40){invincible=false;invincibleCounter=0;}
            return;
        }
        super.update();
    }

    /** Purely visual altitude: the ground position, telegraph and damage centre stay fixed. */
    public float slamHeight(){
        if(dying || !bossState.equals("telegraph_slam"))return 0;
        float t=bossStateTimer;
        if(t<12)return 0;
        if(t<28){float u=(t-12)/16;return 110*u*u*(3-2*u);}
        if(t<44)return 110;
        float u=Math.min(1,(t-44)/16);return 110*(1-u*u);
    }

    @Override protected float spriteLiftPixels(){ return slamHeight(); }
    @Override protected float shadowScale(){ return 1-0.45f*slamHeight()/110; }

    public void setAction(){
        speed=0;
        switch(bossState){
            case "idle" -> {
                if(isPlayerInAggroRange()){
                    speed=2;chasePlayer();
                    bossStateTimer++;
                    if(bossStateTimer >= idleAttackDecisionInterval){
                        bossStateTimer = 0;
                        startRandomAttack();
                    }
                } else {
                    bossStateTimer = 0;
                }
            }
            case "telegraph_slam" -> {
                bossStateTimer++;
                if(bossStateTimer >= telegraphSlamDuration){
                    executeSlam();
                    bossState = "cooldown";
                    bossStateTimer = 0;
                }
            }
            case "telegraph_spray" -> {
                bossStateTimer++;
                if(bossStateTimer % 8 == 0){
                    spawnChargeParticles();
                }
                if(bossStateTimer >= telegraphSprayDuration){
                    executeSpray();
                    bossState = "cooldown";
                    bossStateTimer = 0;
                }
            }
            case "cooldown" -> {
                if(!landing() && isPlayerInAggroRange()){speed=2;chasePlayer();}
                bossStateTimer++;
                if(bossStateTimer >= cooldownDuration){
                    bossState = "idle";
                    bossStateTimer = 0;
                }
            }
        }
    }

    private void startRandomAttack(){
        boolean useSlam = new Random().nextBoolean();
        lastAttackWasSlam=useSlam;
        bossState = useSlam ? "telegraph_slam" : "telegraph_spray";
        bossStateTimer = 0;
        speed=0;moving=false;
    }

    private void executeSlam(){
        lastAttackWasSlam=true;
        String savedDirection = direction;
        for(Player p : gp.players){
            if(p == null || p.isDead) continue;
            double dx = (p.worldX + p.solidArea.x + p.solidArea.width/2.0) - (worldX + solidArea.x + solidArea.width/2.0);
            double dy = (p.worldY + p.solidArea.y + p.solidArea.height/2.0) - (worldY + solidArea.y + solidArea.height/2.0);
            double dist = Math.sqrt(dx*dx + dy*dy);
            if(dist <= slamRadius){
                direction = radialDirection(dx, dy); // so knockback pushes away from the boss, not its facing
                damagePlayer(p, attack);
            }
        }
        direction = savedDirection;
        gp.grass.burst(worldX + solidArea.x + solidArea.width / 2f, worldY + solidArea.y + solidArea.height / 2f, slamRadius, 1f);
        gp.startScreenShake(15, 10);
        gp.startHitStop(10);
        gp.playSE(6);
    }

    private void executeSpray(){
        lastAttackWasSlam=false;
        Player target = findNearestPlayer();
        if(target == null) return;

        double centerX = worldX + solidArea.x + solidArea.width/2.0;
        double centerY = worldY + solidArea.y + solidArea.height/2.0;
        double targetX = target.worldX + target.solidArea.x + target.solidArea.width/2.0;
        double targetY = target.worldY + target.solidArea.y + target.solidArea.height/2.0;
        double baseAngle = Math.atan2(targetY - centerY, targetX - centerX);

        double spreadRad = Math.toRadians(sprayFanSpreadDegrees);
        double step = (sprayProjectileCount > 1) ? spreadRad / (sprayProjectileCount - 1) : 0;
        double startAngle = baseAngle - spreadRad/2;

        for(int i = 0; i < sprayProjectileCount; i++){
            double angle = (sprayProjectileCount > 1) ? startAngle + step * i : baseAngle;
            OBJ_slimebolt droplet = new OBJ_slimebolt(gp);
            droplet.setAngled((int)centerX, (int)centerY, angle, true, this);
            gp.projectileList.add(droplet);
        }
        gp.playSE(9);
    }

    private String radialDirection(double dx, double dy){
        if(Math.abs(dx) > Math.abs(dy)){
            return dx > 0 ? "right" : "left";
        } else {
            return dy > 0 ? "down" : "up";
        }
    }

    private void spawnChargeParticles(){
        for(int i = 0; i < 4; i++){
            double angle = Math.random() * 2 * Math.PI;
            int xd = (int) Math.round(Math.cos(angle) * 2);
            int yd = (int) Math.round(Math.sin(angle) * 2);
            Particle p = new Particle(gp, this, SlimeArt.SAGE, 5, 1, 15, xd, yd);
            gp.particleList.add(p);
        }
    }

    public void damageReaction(){
        // Intentionally empty — a boss shouldn't flinch/reset out of an attack it's mid-telegraph on
    }

    public void checkDrop(){
        dropItem(new OBJ_bronzecoin(gp));
        dropItem(new OBJ_heart(gp));
        dropItem(new OBJ_crystal(gp));
    }

    @Override
    protected SpriteAnimation getCurrentAnimation(){
        if(dying || attackState)return super.getCurrentAnimation();
        if(bossState.equals("telegraph_slam") || landing())return sprites.get("slam_"+direction);
        if(bossState.equals("telegraph_spray"))return sprites.get("charge_"+direction);
        if(flashing)return sprites.get("hurt_"+direction);
        return super.getCurrentAnimation();
    }

    private boolean landing(){return lastAttackWasSlam && bossState.equals("cooldown") && bossStateTimer<20;}

    @Override
    protected BufferedImage getCurrentFrame(){
        if(dying || attackState)return super.getCurrentFrame();
        SpriteAnimation anim=getCurrentAnimation();
        if(bossState.equals("telegraph_slam")){
            int frame=Math.min(3,bossStateTimer*4/telegraphSlamDuration);
            return withPoseEffects(anim.frames[frame]);
        }
        if(landing())return withPoseEffects(anim.frames[Math.min(7,4+bossStateTimer/5)]);
        if(bossState.equals("telegraph_spray"))return withPoseEffects(anim.frames[(bossStateTimer/5)%anim.frames.length]);
        if(flashing)return withPoseEffects(anim.frames[Math.min(anim.frames.length-1,flashCounter*anim.frames.length/Math.max(1,flashDuration))]);
        return super.getCurrentFrame();
    }

    private BufferedImage withPoseEffects(BufferedImage frame){
        if(flashing && flashCounter<2)return getFlashVersion(frame);
        Color tint=getActiveTintColor();
        return tint!=null?getTintVersion(frame,tint):frame;
    }

    @Override
    public void draw(Graphics2D g2){
        if(!dying && bossState.equals("telegraph_slam"))drawSlamTelegraph(g2);
        super.draw(g2);
    }

    private void drawSlamTelegraph(Graphics2D g2){
        Player camera = gp.localPlayer();
        if(camera == null) return;
        int centerScreenX = worldX + solidArea.x + solidArea.width/2 - camera.worldX + camera.screenX;
        int centerScreenY = worldY + solidArea.y + solidArea.height/2 - camera.worldY + camera.screenY;

        float progress = Math.min(1f, bossStateTimer / (float) telegraphSlamDuration);
        int alpha = (int)(120 + progress * 100);
        g2.setColor(new Color(255, 40, 40, Math.min(255, alpha)));
        g2.setStroke(new BasicStroke(3));
        g2.drawOval(centerScreenX - slamRadius, centerScreenY - slamRadius, slamRadius * 2, slamRadius * 2);
    }
}
