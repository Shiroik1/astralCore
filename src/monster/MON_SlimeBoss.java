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

    protected final boolean hell;
    private int hellAttackCycle;
    private double chargeX,chargeY;
    private final int idleAttackDecisionInterval = 75; // ~1.5s of chasing before it decides to attack
    private final int telegraphSlamDuration = 60;       // 1s warning before slam lands
    private final int telegraphSprayDuration = 40;
    private final int cooldownDuration = 70;
    private final int slamRadius;                 // px
    private final int sprayProjectileCount = 5;
    private final int sprayFanSpreadDegrees = 60;

    public MON_SlimeBoss(Gamepanel gp) {this(gp,false);}

    protected MON_SlimeBoss(Gamepanel gp,boolean hell) {
        super(gp);this.hell=hell;slamRadius=hell?180:156;

        name = hell?"Hell Slime":"Slime King";
        speed = 2;
        maxHP = hell?600:50;
        HP = maxHP;
        attack = hell?13:9;
        defense = hell?2:1;
        exp = hell?25:20;
        aggroRange = 24;

        int size = gp.tileSize * 7 / 2;

        solidArea.x = 0;
        solidArea.y = 30;
        solidArea.width = 96;
        solidArea.height = 66;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        getImage(size);
    }

    public void getImage(int size){
        // Its collision box is centered 24px right of a normal tile center.
        // Convert that correction into the 64-unit slime design space.
        if(hell)SlimeArt.buildHellBossAnimations(sprites,size,1.25f,64f*24/size);
        else SlimeArt.buildBossAnimations(sprites,size,1.25f,64f*24/size);
        down1 = sprites.get("idle_down").frames[0];
    }

    public void resetEncounter(){
        HP=maxHP;worldX=25*gp.tileSize;worldY=14*gp.tileSize;bossState="idle";bossStateTimer=0;
        lastAttackWasSlam=false;hellAttackCycle=0;attackState=false;attacking=false;moving=false;
        spriteNum=1;spriteCounter=0;invincible=false;invincibleCounter=0;flashing=false;knockbackActive=false;
        statusEffects.clear();comboStacks=0;auraActive=false;
    }

    public double chargeX(){return chargeX;}
    public double chargeY(){return chargeY;}
    public void syncCharge(double x,double y){chargeX=x;chargeY=y;}
    public int slamRadius(){return slamRadius;}
    public boolean isHell(){return hell;}
    @Override protected float spriteFootFraction(){return 62.3f/64;}
    @Override public boolean isBoss(){ return true; }
    @Override public boolean canDealContactDamage(){ return !bossState.equals("telegraph_slam"); }

    @Override
    public void update(){
        knockbackActive=false;knockbackRemaining=0;
        if(!dying && (bossState.equals("telegraph_slam") || bossState.equals("telegraph_spray") || bossState.equals("burst") || bossState.equals("telegraph_charge") || bossState.equals("charge") || landing())){
            // Special attacks own their clock and cannot be interrupted by contact attacks.
            attackState=false;attacking=false;moving=false;speed=0;
            setAction();
            animState="idle";
            updateAnimation();updateStatusEffects();updateAura();
            if(invincible && ++invincibleCounter>invincibilityDuration){invincible=false;invincibleCounter=0;}
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
                    speed=hell?3:2;chasePlayer();
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
                    if(hell)executeRadialWave(0);else executeSpray();
                    bossState = hell?"burst":"cooldown";
                    bossStateTimer = 0;
                }
            }
            case "burst" -> {
                bossStateTimer++;
                if(bossStateTimer==18 || bossStateTimer==36)executeRadialWave(bossStateTimer/18);
                if(bossStateTimer>=36){bossState="cooldown";bossStateTimer=0;}
            }
            case "telegraph_charge" -> {
                if(++bossStateTimer>=42){bossState="charge";bossStateTimer=0;}
            }
            case "charge" -> {
                moveCharge();
                if(++bossStateTimer>=22){bossState="cooldown";bossStateTimer=0;}
            }
            case "cooldown" -> {
                if(!landing() && isPlayerInAggroRange()){speed=hell?3:2;chasePlayer();}
                bossStateTimer++;
                if(bossStateTimer >= (hell?50:cooldownDuration)){
                    bossState = "idle";
                    bossStateTimer = 0;
                }
            }
        }
    }

    private void startRandomAttack(){
        if(hell){
            bossState=switch(hellAttackCycle++%3){case 0->"telegraph_slam";case 1->"telegraph_spray";default->"telegraph_charge";};
            if(bossState.equals("telegraph_charge")){
                Player target=findNearestPlayer();
                double dx=target==null?0:target.worldX+target.solidArea.x+target.solidArea.width/2.0-(worldX+48);
                double dy=target==null?1:target.worldY+target.solidArea.y+target.solidArea.height/2.0-(worldY+63);
                double length=Math.max(1,Math.hypot(dx,dy));chargeX=dx/length;chargeY=dy/length;direction=radialDirection(dx,dy);
            }
        }else bossState=new Random().nextBoolean()?"telegraph_slam":"telegraph_spray";
        lastAttackWasSlam=bossState.equals("telegraph_slam");bossStateTimer=0;speed=0;moving=false;
    }

    private void moveCharge(){
        // Move each axis through the normal wall/scenery collision checks; never dash through pillars.
        int oldX=worldX,oldY=worldY;
        int dx=(int)Math.round(chargeX*10),dy=(int)Math.round(chargeY*10);
        for(int axis=0;axis<2;axis++){
            int step=axis==0?dx:dy;if(step==0)continue;
            direction=axis==0?(step>0?"right":"left"):(step>0?"down":"up");
            speed=Math.abs(step);collisionOn=false;gp.collisionChecker.checkTile(this);
            if(!collisionOn){if(axis==0)worldX+=step;else worldY+=step;}
        }
        speed=0;moving=true;
        for(Player p:gp.players)if(p!=null && p.inWell==gp.quests.inDungeon() && !p.isDead){
            Rectangle body=new Rectangle(worldX+solidArea.x,worldY+solidArea.y,solidArea.width,solidArea.height);
            if(body.intersects(new Rectangle(p.worldX+p.solidArea.x,p.worldY+p.solidArea.y,p.solidArea.width,p.solidArea.height)))damagePlayer(p,attack);
        }
        if(worldX==oldX && worldY==oldY)bossStateTimer=22;
    }

    private void executeRadialWave(int wave){
        int cx=worldX+solidArea.x+solidArea.width/2,cy=worldY+solidArea.y+solidArea.height/2;
        for(int i=0;i<16;i++){
            OBJ_slimebolt bolt=new OBJ_slimebolt(gp,true);bolt.attack=8;
            bolt.setAngled(cx-gp.tileSize/2,cy-gp.tileSize/2,2*Math.PI*i/16+wave*Math.PI/16,true,this);
            gp.projectileList.add(bolt);
        }gp.playSE(9);
    }

    private void executeSlam(){
        lastAttackWasSlam=true;
        String savedDirection = direction;
        for(Player p : gp.players){
            if(p == null || p.isDead || p.inWell!=gp.quests.inDungeon()) continue;
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
            droplet.setAngled((int)centerX-gp.tileSize/2, (int)centerY-gp.tileSize/2, angle, true, this);
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
        gp.quests.dropLoot(new object.OBJ_slimeessence(gp),worldX,worldY);
        if(hell)for(Player member:gp.players)if(member!=null)
            gp.quests.dropLoot(object.ItemRegistry.create(object.OBJ_rewardweapon.forClass(member.playerClass,true),gp),worldX,worldY);
        dropItem(new OBJ_bronzecoin(gp));
        dropItem(new OBJ_heart(gp));
        dropItem(new OBJ_crystal(gp));
    }

    @Override
    protected SpriteAnimation getCurrentAnimation(){
        if(dying || attackState)return super.getCurrentAnimation();
        if(bossState.equals("telegraph_slam") || landing())return sprites.get("slam_"+direction);
        if(bossState.equals("telegraph_spray") || bossState.equals("telegraph_charge") || bossState.equals("burst"))return sprites.get("charge_"+direction);
        if(bossState.equals("charge"))return sprites.get("attack_"+direction);
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
        if(bossState.equals("telegraph_spray") || bossState.equals("telegraph_charge") || bossState.equals("burst"))return withPoseEffects(anim.frames[(bossStateTimer/5)%anim.frames.length]);
        if(bossState.equals("charge"))return withPoseEffects(anim.frames[Math.min(anim.frames.length-1,3+bossStateTimer/4)]);
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
        if(!dying && bossState.equals("telegraph_charge"))drawChargeTelegraph(g2);
        super.draw(g2);
    }

    private void drawChargeTelegraph(Graphics2D g){
        Player camera=gp.localPlayer();if(camera==null)return;
        int x=worldX+48-camera.worldX+camera.screenX,y=worldY+63-camera.worldY+camera.screenY;
        Stroke saved=g.getStroke();g.setStroke(new BasicStroke(54,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(244,67,40,65));g.drawLine(x,y,x+(int)(chargeX*220),y+(int)(chargeY*220));
        g.setStroke(new BasicStroke(3));g.setColor(new Color(255,166,91,210));g.drawLine(x,y,x+(int)(chargeX*220),y+(int)(chargeY*220));g.setStroke(saved);
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
