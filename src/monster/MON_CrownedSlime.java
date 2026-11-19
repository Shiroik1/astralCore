package monster;

import entity.*;

import main.Gamepanel;

import net.MonsterState;

import object.OBJ_slimebolt;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Optional overworld encounter. The existing Slime King and Hell Slime keep their dungeon AI. */
public final class MON_CrownedSlime extends MON_SlimeBoss implements SlimeActor {
    public enum State {
        DORMANT,
        AWAKEN,
        REPOSITION,
        SLAM_WINDUP,
        SLAM_JUMP,
        SPIT_WINDUP,
        RADIAL_WINDUP,
        RECOVERY,
        PHASE_TWO
    }

    public static final int SLAM_WARNING = 54, SLAM_FLIGHT = 30, SPIT_WARNING = 36, RECOVERY = 60;
    public static final int LEASH_TILES = 10, SPIT_DAMAGE = SlimeCombatConfig.SPIT_DAMAGE;
    public String nestId = "";
    public State state = State.DORMANT;
    private int timer, homeX, homeY, startX, startY, goalX, goalY, adds;
    private boolean homeSet, phaseTwo, nextSlam = true, radialRecovery, deathCleanup;
    private final ai.Pathfinder route;

    /**
     * Creates a MON_CrownedSlime instance and initializes the state required by its collaborators.
     */
    public MON_CrownedSlime(Gamepanel gp) {
        super(gp);
        route = new ai.Pathfinder(gp);
        name = "Crowned Slime";
        maxHP = SlimeCombatConfig.BOSS_HP;
        clearMasteryCredit();
        HP = maxHP;
        attack = SlimeCombatConfig.SLAM_DAMAGE;
        defense = SlimeCombatConfig.BOSS_DEFENSE;
        exp = 35;
        aggroRange = 5;
        incomingHitImmunity = 36;
        incomingHitKnockback = gp.tileSize / 2;
    }

    /** Updates the home for MON_CrownedSlime. */
    public void setHome() {
        homeX = worldX;
        homeY = worldY;
        homeSet = true;
    }

    /** Reports whether this component can deal contact damage for MON_CrownedSlime. */
    @Override
    public boolean canDealContactDamage() {
        return false;
    }

    /** Evaluates the show boss health condition for MON_CrownedSlime. */
    @Override
    public boolean showBossHealth() {
        return super.showBossHealth() && state != State.DORMANT;
    }

    /** Performs the enter operation for MON_CrownedSlime. */
    private void enter(State next) {
        state = next;
        timer = 0;
        spriteNum = 1;
        spriteCounter = 0;
    }

    /** Resets the encounter for MON_CrownedSlime. */
    @Override
    public void resetEncounter() {
        if (!homeSet) setHome();
        gp.slimeNest.removeSummons(nestId);
        gp.projectileList.removeIf(e -> e instanceof Projectile p && p.firedBy(this));
        worldX = homeX;
        worldY = homeY;
        clearMasteryCredit();
        HP = maxHP;
        phaseTwo = false;
        adds = 0;
        nextSlam = true;
        radialRecovery = false;
        deathCleanup = false;
        enter(State.DORMANT);
        statusEffects.clear();
        clearAssassinPairs();
        invincible = false;
        invincibleCounter = 0;
        flashing = false;
        knockbackActive = false;
        attackState = false;
        attacking = false;
        moving = false;
        speed = 0;
    }

    /** Performs the damage reaction operation for MON_CrownedSlime. */
    @Override
    public void damageReaction() {
        if (state == State.DORMANT) enter(State.AWAKEN);
    }

    /** Advances this component for one simulation tick. */
    @Override
    public void update() {
        if (dying) {
            if (!deathCleanup) {
                deathCleanup = true;
                gp.slimeNest.removeSummons(nestId);
                gp.projectileList.removeIf(e -> e instanceof Projectile shot && shot.firedBy(this));
            }
            super.update();
            return;
        }
        if (!homeSet) setHome();
        speed = 0;
        moving = false;
        attackState = false;
        attacking = false;
        knockbackActive = false;
        Player p = findNearestPlayer();
        int ts = gp.tileSize;
        boolean nearby =
                SlimeMotion.activePlayer(gp, p)
                        && Math.hypot(p.worldX - homeX, p.worldY - homeY) <= LEASH_TILES * ts;
        if (state != State.DORMANT && !nearby) {
            resetEncounter();
            tickCombatEffects();
            return;
        }
        if (!phaseTwo && HP <= maxHP / 2 && state != State.SLAM_JUMP && state != State.DORMANT) {
            phaseTwo = true;
            enter(State.PHASE_TWO);
        }
        if (phaseTwo
                && adds < SlimeCombatConfig.SUMMONS
                && state != State.PHASE_TWO
                && gp.currentTick % 60 == 0)
            adds += gp.slimeNest.summon(this, SlimeCombatConfig.SUMMONS - adds);
        switch (state) {
            case DORMANT -> {
                if (nearby
                        && (HP < maxHP
                                || SlimeMotion.distance(this, p) <= aggroRange * ts
                                        && SlimeMotion.sight(gp, this, p))) enter(State.AWAKEN);
            }
            case AWAKEN -> {
                if (++timer >= 48) enter(State.REPOSITION);
            }
            case REPOSITION -> {
                timer++;
                // Short, slow hops with a rest between them, never a continuous slide.
                int stride = phaseTwo ? 45 : 60, step = timer % stride;
                if (step == 1) {
                    speed = 1;
                    route.chase(this, p);
                    startX = worldX;
                    startY = worldY;
                    var point = route.waypoint();
                    boolean horizontal = direction.equals("right") || direction.equals("left");
                    int distance =
                            Math.min(
                                    ts, Math.abs(horizontal ? point.x - worldX : point.y - worldY));
                    goalX =
                            worldX
                                    + (direction.equals("right")
                                            ? distance
                                            : direction.equals("left") ? -distance : 0);
                    goalY =
                            worldY
                                    + (direction.equals("down")
                                            ? distance
                                            : direction.equals("up") ? -distance : 0);
                    if (speed == 0) {
                        goalX = worldX;
                        goalY = worldY;
                    }
                }
                if (step > 0 && step <= 30) moveHop(step / 30f);
                int decision =
                        (phaseTwo ? 75 : 100) + (gp.slimeNest.livingSummons(nestId) >= 3 ? 60 : 0);
                if (timer >= decision && SlimeMotion.sight(gp, this, p)) {
                    lockTarget(p, nextSlam ? 4 * ts : 0);
                    radialRecovery = false;
                    enter(
                            nextSlam
                                    ? State.SLAM_WINDUP
                                    : phaseTwo ? State.RADIAL_WINDUP : State.SPIT_WINDUP);
                    nextSlam = !nextSlam;
                }
            }
            case SLAM_WINDUP -> {
                if (++timer >= SLAM_WARNING) {
                    startX = worldX;
                    startY = worldY;
                    enter(State.SLAM_JUMP);
                }
            }
            case SLAM_JUMP -> {
                moveHop(Math.min(1, ++timer / (float) SLAM_FLIGHT));
                if (timer >= SLAM_FLIGHT) {
                    // A newly placed obstacle can interrupt the jump; never impact outside the
                    // warning.
                    if (Math.hypot(worldX - goalX, worldY - goalY) <= 2) impact();
                    enter(State.RECOVERY);
                }
            }
            case SPIT_WINDUP -> {
                if (++timer >= SlimeCombatConfig.SPIT_WARNING) {
                    Player aimed = findNearestPlayer();
                    if (aimed != null) {
                        goalX = SlimeMotion.centerX(aimed);
                        goalY = SlimeMotion.centerY(aimed);
                    }
                    spit(false);
                    enter(State.RECOVERY);
                }
            }
            case RADIAL_WINDUP -> {
                if (++timer >= SlimeCombatConfig.RADIAL_WARNING) {
                    spit(true);
                    radialRecovery = true;
                    enter(State.RECOVERY);
                }
            }
            case RECOVERY -> {
                if (++timer
                        >= (radialRecovery
                                ? SlimeCombatConfig.RADIAL_RECOVERY
                                : phaseTwo ? 60 : RECOVERY)) enter(State.REPOSITION);
            }
            case PHASE_TWO -> {
                // Count successful insertions, not living adds; defeating one never summons a
                // replacement.
                if (++timer >= SlimeCombatConfig.SUMMON_WARNING
                        && timer % 15 == 0
                        && adds < SlimeCombatConfig.SUMMONS)
                    adds += gp.slimeNest.summon(this, SlimeCombatConfig.SUMMONS - adds);
                if (timer >= SlimeCombatConfig.SUMMON_WARNING + 30
                        && adds == SlimeCombatConfig.SUMMONS) enter(State.REPOSITION);
                else if (timer >= 180) enter(State.REPOSITION);
            }
        }
        bossState = "overworld_" + state.name();
        bossStateTimer = timer;
        animState = moving ? "walk" : "idle";
        speed = 0;
        tickCombatEffects();
    }

    /** Resolves the roam pixels result used by MON_CrownedSlime. */
    private int roamPixels() {
        var plan = gp.generatedWorld == null ? null : gp.generatedWorld.slimeNest();
        return gp.tileSize * (plan == null ? 5 : Math.max(1, plan.size() / 2 - 2));
    }

    /** Performs the lock target operation for MON_CrownedSlime. */
    private void lockTarget(Player p, int maxDistance) {
        int tx = SlimeMotion.centerX(p) - solidArea.x - solidArea.width / 2,
                ty = SlimeMotion.centerY(p) - solidArea.y - solidArea.height / 2;
        direction = SlimeMotion.facing(tx - worldX, ty - worldY);
        if (maxDistance == 0) {
            goalX = SlimeMotion.centerX(p);
            goalY = SlimeMotion.centerY(p);
            return;
        }
        double dx = tx - worldX,
                dy = ty - worldY,
                scale = Math.min(1, maxDistance / Math.max(1, Math.hypot(dx, dy)));
        tx = worldX + (int) (dx * scale);
        ty = worldY + (int) (dy * scale);
        goalX = worldX;
        goalY = worldY;
        int n = Math.max(1, Math.max(Math.abs(tx - worldX), Math.abs(ty - worldY)));
        for (int i = 1; i <= n; i++) {
            int x = worldX + (tx - worldX) * i / n, y = worldY + (ty - worldY) * i / n;
            if (Math.hypot(x - homeX, y - homeY) > roamPixels()
                    || !SlimeMotion.clear(gp, SlimeMotion.body(this, x, y))) break;
            goalX = x;
            goalY = y;
        }
    }

    /** Performs the move hop operation for MON_CrownedSlime. */
    private void moveHop(float progress) {
        int x = startX + Math.round((goalX - startX) * progress),
                y = startY + Math.round((goalY - startY) * progress);
        if (Math.hypot(x - homeX, y - homeY) <= roamPixels())
            moving = SlimeMotion.move(gp, this, x, y, false);
    }

    /** Resolves the slam radius result used by MON_CrownedSlime. */
    @Override
    public int slamRadius() {
        return (int) (gp.tileSize * 1.6);
    }

    /** Performs the impact operation for MON_CrownedSlime. */
    private void impact() {
        for (Player p : gp.players)
            if (SlimeMotion.activePlayer(gp, p)) {
                Rectangle b = SlimeMotion.body(p, p.worldX, p.worldY);
                double x = Math.max(b.x, Math.min(SlimeMotion.centerX(this), b.x + b.width));
                double y = Math.max(b.y, Math.min(SlimeMotion.centerY(this), b.y + b.height));
                if (Math.hypot(x - SlimeMotion.centerX(this), y - SlimeMotion.centerY(this))
                        <= slamRadius()) SlimeMotion.hit(gp, this, p, attack);
            }
        gp.grass.burst(SlimeMotion.centerX(this), SlimeMotion.centerY(this), slamRadius(), 1f);
        gp.startScreenShake(8, 5);
        gp.playSE(6);
    }

    /** Performs the spit operation for MON_CrownedSlime. */
    private void spit(boolean radial) {
        var hits =
                radial
                        ? java.util.Collections.newSetFromMap(
                                new java.util.IdentityHashMap<Player, Boolean>())
                        : null;
        int count = radial ? SlimeCombatConfig.RADIAL_COUNT : 1;
        double aimed =
                Math.atan2(goalY - SlimeMotion.centerY(this), goalX - SlimeMotion.centerX(this));
        for (int i = 0; i < count; i++) {
            OBJ_slimebolt bolt = new OBJ_slimebolt(gp);
            bolt.speed = SlimeCombatConfig.PROJECTILE_SPEED;
            bolt.maxHP = SlimeCombatConfig.RADIAL_LIFE;
            bolt.attack = radial ? SlimeCombatConfig.RADIAL_DAMAGE : SPIT_DAMAGE;
            bolt.collidesWithWorld = true;
            bolt.clearOnAreaExit = true;
            bolt.burstHits = hits;
            bolt.incomingHitImmunity = 36;
            bolt.incomingHitKnockback = gp.tileSize / 3;
            bolt.solidArea.setBounds(
                    gp.tileSize / 3, gp.tileSize / 3, gp.tileSize / 3, gp.tileSize / 3);
            double angle = radial ? Math.PI * 2 * i / count : aimed;
            bolt.setVector(
                    SlimeMotion.centerX(this) - gp.tileSize / 2,
                    SlimeMotion.centerY(this) - gp.tileSize / 2,
                    Math.cos(angle),
                    Math.sin(angle),
                    true,
                    this);
            gp.projectileList.add(bolt);
        }
        gp.playSE(9);
    }

    /** Resolves the slam height result used by MON_CrownedSlime. */
    @Override
    public float slamHeight() {
        if (dying) return 0;
        if (state == State.SLAM_JUMP)
            return (float) Math.sin(Math.PI * Math.min(1, timer / (float) SLAM_FLIGHT)) * 100;
        if (state == State.REPOSITION && timer % (phaseTwo ? 45 : 60) <= 30)
            return (float) Math.sin(Math.PI * (timer % (phaseTwo ? 45 : 60)) / 30f) * 14;
        if (state == State.AWAKEN || state == State.PHASE_TWO)
            return (float) Math.abs(Math.sin(timer * .13)) * 9;
        return 0;
    }

    /** Returns the current animation for MON_CrownedSlime. */
    @Override
    protected SpriteAnimation getCurrentAnimation() {
        String pose =
                dying
                        ? "death"
                        : flashing
                                ? "hurt"
                                : state == State.SLAM_WINDUP || state == State.SLAM_JUMP
                                        ? "slam"
                                        : state == State.SPIT_WINDUP
                                                        || state == State.RADIAL_WINDUP
                                                        || state == State.PHASE_TWO
                                                ? "charge"
                                                : animState;
        return sprites.get(pose + "_" + direction);
    }

    /** Returns the current frame for MON_CrownedSlime. */
    @Override
    protected BufferedImage getCurrentFrame() {
        var a = getCurrentAnimation();
        if (a == null) return down1;
        int frame =
                dying
                        ? Math.max(0, Math.min(a.frames.length - 1, spriteNum - 1))
                        : state == State.SLAM_WINDUP
                                ? Math.min(3, timer * 4 / SLAM_WARNING)
                                : state == State.SLAM_JUMP
                                        ? Math.min(a.frames.length - 1, 4 + timer * 4 / SLAM_FLIGHT)
                                        : Math.max(0, Math.min(a.frames.length - 1, spriteNum - 1));
        BufferedImage f = a.frames[frame];
        if (flashing && flashCounter < 2) return getFlashVersion(f);
        var tint = getActiveTintColor();
        return tint == null ? f : getTintVersion(f, tint);
    }

    /** Renders this component into the supplied Java2D graphics context. */
    @Override
    public void draw(Graphics2D g) {
        if (!dying && (state == State.SLAM_WINDUP || state == State.SLAM_JUMP)) {
            Player c = gp.localPlayer();
            if (c != null) {
                var pen = (Graphics2D) g.create();
                int x = goalX + solidArea.x + solidArea.width / 2 - c.worldX + c.screenX,
                        y = goalY + solidArea.y + solidArea.height / 2 - c.worldY + c.screenY,
                        r = slamRadius();
                pen.setColor(new Color(215, 151, 111, 72));
                pen.fillOval(x - r, y - r, 2 * r, 2 * r);
                pen.setColor(new Color(255, 237, 180, 230));
                pen.setStroke(new BasicStroke(3));
                pen.drawOval(x - r, y - r, 2 * r, 2 * r);
                pen.dispose();
            }
        }
        if (!dying && (state == State.RADIAL_WINDUP || state == State.PHASE_TWO)) {
            Player c = gp.localPlayer();
            if (c != null) {
                var pen = (Graphics2D) g.create();
                int cx = SlimeMotion.centerX(this) - c.worldX + c.screenX,
                        cy = SlimeMotion.centerY(this) - c.worldY + c.screenY;
                int count = state == State.RADIAL_WINDUP ? 8 : 5, r = gp.tileSize * 2;
                pen.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                pen.setColor(new Color(246, 220, 161, 210));
                for (int i = 0; i < count; i++) {
                    double a = Math.PI * 2 * i / count;
                    int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
                    if (state == State.RADIAL_WINDUP)
                        pen.drawLine(
                                cx + (int) (Math.cos(a) * (r - 14)),
                                cy + (int) (Math.sin(a) * (r - 14)),
                                x,
                                y);
                    else pen.drawOval(x - 5, y - 5, 10, 10);
                }
                pen.dispose();
            }
        }
        super.draw(g);
    }

    /** Performs the check drop operation for MON_CrownedSlime. */
    @Override
    public void checkDrop() {
        gp.slimeNest.defeated(this);
    }

    /** Captures the slime for MON_CrownedSlime. */
    @Override
    public void captureSlime(MonsterState s) {
        s.slimeState = state.name();
        s.slimeTimer = timer;
        s.homeX = homeSet ? homeX : worldX;
        s.homeY = homeSet ? homeY : worldY;
        s.slimeStartX = startX;
        s.slimeStartY = startY;
        s.slimeGoalX = goalX;
        s.slimeGoalY = goalY;
        s.slimePhaseTwo = phaseTwo;
        s.slimeAdds = adds;
        s.slimeNextSlam = nextSlam;
        s.slimeRadialRecovery = radialRecovery;
        s.nestId = nestId;
    }

    /** Restores the slime for MON_CrownedSlime. */
    @Override
    public void restoreSlime(MonsterState s, boolean disk) {
        if (s.slimeState == null) {
            setHome();
            return;
        }
        state = State.valueOf(s.slimeState);
        timer = s.slimeTimer;
        homeX = s.homeX;
        homeY = s.homeY;
        homeSet = true;
        startX = s.slimeStartX;
        startY = s.slimeStartY;
        goalX = s.slimeGoalX;
        goalY = s.slimeGoalY;
        phaseTwo = s.slimePhaseTwo;
        adds = s.slimeAdds;
        nextSlam = s.slimeNextSlam;
        radialRecovery = s.slimeRadialRecovery;
        nestId = s.nestId == null ? "" : s.nestId;
        if (disk && state != State.DORMANT) {
            enter(phaseTwo && adds < SlimeCombatConfig.SUMMONS ? State.PHASE_TWO : State.RECOVERY);
        }
        animState = dying ? "death" : "idle";
    }
}
