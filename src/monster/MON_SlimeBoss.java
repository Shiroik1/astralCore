package monster;

import entity.Monster;
import entity.Particle;
import entity.Player;
import main.Gamepanel;
import object.OBJ_bronzecoin;
import object.OBJ_crystal;
import object.OBJ_heart;
import object.OBJ_rock;

import java.awt.*;
import java.util.Random;

public class MON_SlimeBoss extends Monster {

    public String bossState = "idle"; // idle, telegraph_slam, telegraph_spray, cooldown
    public int bossStateTimer = 0;

    private final int idleAttackDecisionInterval = 90; // ~1.5s of chasing before it decides to attack
    private final int telegraphSlamDuration = 60;       // 1s warning before slam lands
    private final int telegraphSprayDuration = 40;
    private final int cooldownDuration = 90;
    private final int slamRadius = 120;                 // px
    private final int sprayRockCount = 5;
    private final int sprayFanSpreadDegrees = 60;

    public MON_SlimeBoss(Gamepanel gp) {
        super(gp);

        name = "Slime King";
        speed = 2;
        maxHP = 60;
        HP = maxHP;
        attack = 8;
        defense = 1;
        exp = 30;
        aggroRange = 8;

        int size = gp.tileSize * 2; // reusing slime art scaled up — swap for unique boss art later

        solidArea.x = 6;
        solidArea.y = 36;
        solidArea.width = 84;
        solidArea.height = 60;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        getImage(size);
    }

    public void getImage(int size){
        up1 = setup("/monster/greenslime_down_1", size, size);
        up2 = setup("/monster/greenslime_down_2", size, size);
        down1 = setup("/monster/greenslime_down_1", size, size);
        down2 = setup("/monster/greenslime_down_2", size, size);
        left1 = setup("/monster/greenslime_down_1", size, size);
        left2 = setup("/monster/greenslime_down_2", size, size);
        right1 = setup("/monster/greenslime_down_1", size, size);
        right2 = setup("/monster/greenslime_down_2", size, size);
    }

    public void setAction(){
        switch(bossState){
            case "idle" -> {
                if(isPlayerInAggroRange()){
                    chasePlayer();
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
                if(isPlayerInAggroRange()) chasePlayer();
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
        bossState = useSlam ? "telegraph_slam" : "telegraph_spray";
        bossStateTimer = 0;
    }

    private void executeSlam(){
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
        Player target = findNearestPlayer();
        if(target == null) return;

        double centerX = worldX + solidArea.x + solidArea.width/2.0;
        double centerY = worldY + solidArea.y + solidArea.height/2.0;
        double targetX = target.worldX + target.solidArea.x + target.solidArea.width/2.0;
        double targetY = target.worldY + target.solidArea.y + target.solidArea.height/2.0;
        double baseAngle = Math.atan2(targetY - centerY, targetX - centerX);

        double spreadRad = Math.toRadians(sprayFanSpreadDegrees);
        double step = (sprayRockCount > 1) ? spreadRad / (sprayRockCount - 1) : 0;
        double startAngle = baseAngle - spreadRad/2;

        for(int i = 0; i < sprayRockCount; i++){
            double angle = (sprayRockCount > 1) ? startAngle + step * i : baseAngle;
            OBJ_rock rock = new OBJ_rock(gp);
            rock.setAngled((int)centerX, (int)centerY, angle, true, this);
            gp.projectileList.add(rock);
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
            Particle p = new Particle(gp, this, new Color(150, 50, 200), 5, 1, 15, xd, yd);
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
    public void draw(Graphics2D g2){
        super.draw(g2);
        if(bossState.equals("telegraph_slam")){
            drawSlamTelegraph(g2);
        }
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