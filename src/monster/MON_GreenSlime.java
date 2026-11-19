package monster;

import art.SlimeArt;

import entity.*;

import main.Gamepanel;

import net.MonsterState;

import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadLocalRandom;

/** Hops and attacks have separate clocks. Only the committed leap can hurt a player. */
public class MON_GreenSlime extends Monster implements SlimeActor {
    public enum State {
        IDLE,
        WANDER,
        CHASE,
        WINDUP,
        LEAP_ATTACK,
        RECOVERY,
        RETURN,
        INVESTIGATE
    }

    public static final int WINDUP_TICKS = 30,
            LEAP_TICKS = 15,
            RECOVERY_TICKS = 30,
            COOLDOWN_TICKS = 120;
    public static final int DETECTION_TILES = 6, LEASH_TILES = 10, MAX_NEARBY_ATTACKERS = 2;
    public static final double ATTACK_TILES = 1.5;
    public State state = State.IDLE;
    public String nestId = "";
    private int timer, cooldown, homeX, homeY, startX, startY, goalX, goalY, hitMask, wait = 45;
    private boolean homeSet, returningHop;
    private int arrivalGrace;
    private int investigationX, investigationY, investigationTicks, stalledTicks;
    public boolean investigationFailed, directorSpawn;

    /** Evaluates the investigating condition for MON_GreenSlime. */
    public boolean investigating() {
        return investigationTicks > 0 && !dying;
    }

    /** Captured once by the director; this is never retargeted to a moving player. */
    public void investigate(int x, int y, int ticks) {
        investigationX = x;
        investigationY = y;
        investigationTicks = Math.max(1, ticks);
        stalledTicks = 0;
        investigationFailed = false;
        directorSpawn = true;
        enter(State.INVESTIGATE);
    }

    /** Performs the end investigation operation for MON_GreenSlime. */
    private void endInvestigation(boolean failed) {
        investigationTicks = 0;
        stalledTicks = 0;
        investigationFailed = failed;
        setHome();
    }

    /** Performs the delay attacks operation for MON_GreenSlime. */
    public void delayAttacks(int ticks) {
        arrivalGrace = Math.max(arrivalGrace, ticks);
        cooldown = Math.max(cooldown, ticks);
    }

    private final ai.Pathfinder route;

    /**
     * Creates a MON_GreenSlime instance and initializes the state required by its collaborators.
     */
    public MON_GreenSlime(Gamepanel gp) {
        super(gp);
        route = new ai.Pathfinder(gp);
        name = "Green Slime";
        maxHP = 30;
        clearMasteryCredit();
        HP = maxHP;
        attack = 8;
        exp = 3;
        aggroRange = DETECTION_TILES;
        incomingHitImmunity = 36;
        incomingHitKnockback = gp.tileSize / 2;
        solidArea.setBounds(3, 18, 48, 30);
        solidAreaDefaultX = 3;
        solidAreaDefaultY = 18;
        SlimeArt.buildAnimations(sprites, SlimeArt.CANVAS_SIZE, 1.10f);
        down1 = sprites.get("idle_down").frames[0];
    }

    /** Updates the home for MON_GreenSlime. */
    public void setHome() {
        homeX = worldX;
        homeY = worldY;
        homeSet = true;
        wait = 30 + ThreadLocalRandom.current().nextInt(90);
    }

    /** Reports whether this component can deal contact damage for MON_GreenSlime. */
    @Override
    public boolean canDealContactDamage() {
        return false;
    }

    /** Evaluates the committed condition for MON_GreenSlime. */
    public boolean committed() {
        return state == State.WINDUP || state == State.LEAP_ATTACK;
    }

    /** Evaluates the resists basic impact condition for MON_GreenSlime. */
    @Override
    protected boolean resistsBasicImpact() {
        return committed();
    }

    /** Reports whether this component can be knocked back for MON_GreenSlime. */
    @Override
    public boolean canBeKnockedBack() {
        return super.canBeKnockedBack() && !committed();
    }

    /** Performs the enter operation for MON_GreenSlime. */
    private void enter(State next) {
        state = next;
        timer = 0;
        spriteNum = 1;
        spriteCounter = 0;
    }

    /** Performs the on stagger operation for MON_GreenSlime. */
    @Override
    protected void onStagger(int ticks) {
        enter(State.RECOVERY);
        cooldown = Math.max(cooldown, ticks);
    }

    /** Performs the damage reaction operation for MON_GreenSlime. */
    @Override
    public void damageReaction() {
        if (!committed()) enter(State.RECOVERY);
    }

    /** Advances this component for one simulation tick. */
    @Override
    public void update() {
        if (dying) {
            super.update();
            return;
        }
        if (!homeSet) setHome();
        speed = 0;
        moving = false;
        attackState = false;
        attacking = false;
        if (arrivalGrace > 0) {
            arrivalGrace--;
            moving = false;
            animState = "idle";
            tickCombatEffects();
            return;
        }
        if (cooldown > 0) cooldown--;
        if (knockbackActive) {
            updateKnockback();
            enter(State.RECOVERY);
            tickCombatEffects();
            return;
        }
        if (investigating() && --investigationTicks == 0) {
            endInvestigation(true);
            if (state == State.INVESTIGATE) enter(State.IDLE);
        }
        Player target = findNearestPlayer();
        int ts = gp.tileSize;
        boolean visible =
                SlimeMotion.activePlayer(gp, target)
                        && SlimeMotion.distance(this, target) <= aggroRange * ts
                        && SlimeMotion.sight(gp, this, target);
        if (visible) investigationFailed = false;
        boolean outside =
                !investigating() && Math.hypot(worldX - homeX, worldY - homeY) > LEASH_TILES * ts;
        if (visible && state == State.INVESTIGATE) {
            endInvestigation(false);
            enter(State.CHASE);
        }
        int previousX = worldX, previousY = worldY;
        if (!committed() && state != State.RECOVERY && state != State.WANDER) {
            if (outside || !visible && (state == State.CHASE || state == State.RETURN))
                state = State.RETURN;
            else if (visible && state == State.IDLE) enter(State.CHASE);
        }
        switch (state) {
            case INVESTIGATE -> {
                if (Math.hypot(worldX - investigationX, worldY - investigationY) < ts * .75) {
                    endInvestigation(false);
                    enter(State.IDLE);
                } else if (++timer >= 18) {
                    speed = 2;
                    route.toward(this, investigationX, investigationY);
                    hopInDirection(State.WANDER);
                }
            }
            case IDLE -> {
                if (++timer >= wait) {
                    returningHop = false;
                    startHop(
                            homeX + ThreadLocalRandom.current().nextInt(-2, 3) * ts,
                            homeY + ThreadLocalRandom.current().nextInt(-2, 3) * ts,
                            State.WANDER);
                }
            }
            case CHASE -> {
                if (!visible || outside) {
                    enter(State.RETURN);
                    break;
                }
                if (cooldown == 0
                        && SlimeMotion.distance(this, target) <= ts * ATTACK_TILES
                        && attackSlot()) {
                    goalX =
                            target.worldX
                                    + target.solidArea.x
                                    + target.solidArea.width / 2
                                    - solidArea.x
                                    - solidArea.width / 2;
                    goalY =
                            target.worldY
                                    + target.solidArea.y
                                    + target.solidArea.height / 2
                                    - solidArea.y
                                    - solidArea.height / 2;
                    direction = SlimeMotion.facing(goalX - worldX, goalY - worldY);
                    enter(State.WINDUP);
                } else if (++timer >= 18) {
                    speed = 2;
                    route.chase(this, target);
                    hopInDirection(State.WANDER);
                }
            }
            case RETURN -> {
                if (Math.hypot(worldX - homeX, worldY - homeY) < ts / 2.0) {
                    enter(State.IDLE);
                    wait = 60;
                } else if (++timer >= 22) {
                    speed = 2;
                    route.toward(this, homeX, homeY);
                    hopInDirection(State.RETURN);
                }
            }
            case WANDER -> {
                hop(false);
                if (timer >= LEAP_TICKS) {
                    if (visible && investigating()) endInvestigation(false);
                    enter(
                            returningHop || outside
                                    ? State.RETURN
                                    : visible
                                            ? State.CHASE
                                            : investigating() ? State.INVESTIGATE : State.IDLE);
                    wait = 45 + ThreadLocalRandom.current().nextInt(100);
                }
            }
            case WINDUP -> {
                if (++timer >= WINDUP_TICKS) {
                    startX = worldX;
                    startY = worldY;
                    hitMask = 0;
                    enter(State.LEAP_ATTACK);
                }
            }
            case LEAP_ATTACK -> {
                hop(true);
                if (timer >= LEAP_TICKS) {
                    cooldown = COOLDOWN_TICKS + ThreadLocalRandom.current().nextInt(24);
                    enter(State.RECOVERY);
                }
            }
            case RECOVERY -> {
                if (++timer >= RECOVERY_TICKS) {
                    if (visible && investigating()) endInvestigation(false);
                    enter(
                            visible && !outside
                                    ? State.CHASE
                                    : investigating() ? State.INVESTIGATE : State.RETURN);
                }
            }
        }
        if (investigating()) {
            stalledTicks = worldX == previousX && worldY == previousY ? stalledTicks + 1 : 0;
            if (stalledTicks > 12 * 60) {
                endInvestigation(true);
                enter(State.IDLE);
            }
        }
        animState =
                state == State.WINDUP || state == State.LEAP_ATTACK
                        ? "attack"
                        : moving ? "walk" : "idle";
        speed = 0;
        tickCombatEffects();
    }

    /** Evaluates the attack slot condition for MON_GreenSlime. */
    private boolean attackSlot() {
        int n = 0;
        for (Monster m : gp.monster)
            if (m instanceof MON_GreenSlime s
                    && s != this
                    && s.committed()
                    && SlimeMotion.distance(this, s) < gp.tileSize * 5) n++;
        if (n >= MAX_NEARBY_ATTACKERS) {
            cooldown = 15 + ThreadLocalRandom.current().nextInt(20);
            return false;
        }
        return true;
    }

    /** Performs the hop in direction operation for MON_GreenSlime. */
    private void hopInDirection(State next) {
        if (speed == 0) {
            timer = 0;
            return;
        }
        returningHop = next == State.RETURN;
        var point = route.waypoint();
        boolean horizontal = direction.equals("right") || direction.equals("left");
        int distance =
                Math.min(
                        gp.tileSize * 3 / 4,
                        Math.abs(horizontal ? point.x - worldX : point.y - worldY));
        int x =
                worldX
                        + (direction.equals("right")
                                ? distance
                                : direction.equals("left") ? -distance : 0);
        int y =
                worldY
                        + (direction.equals("down")
                                ? distance
                                : direction.equals("up") ? -distance : 0);
        // Returning hops still use WANDER's movement, and reevaluate home/aggro when landing.
        startHop(x, y, State.WANDER);
    }

    /** Starts the hop for MON_GreenSlime. */
    private void startHop(int x, int y, State next) {
        startX = worldX;
        startY = worldY;
        double dx = x - worldX, dy = y - worldY, len = Math.max(1, Math.hypot(dx, dy));
        double max = gp.tileSize * .8;
        goalX = worldX + (int) (dx * Math.min(1, max / len));
        goalY = worldY + (int) (dy * Math.min(1, max / len));
        direction = SlimeMotion.facing(dx, dy);
        enter(next);
    }

    /** Performs the hop operation for MON_GreenSlime. */
    private void hop(boolean attackingHop) {
        timer++;
        float progress = Math.min(1, timer / (float) LEAP_TICKS);
        int slow =
                Math.max(
                        Math.max(
                                getStatusEffectValue("rangerSlow"),
                                getStatusEffectValue("warriorSlow")),
                        getStatusEffectValue(MageEffect.FROST_SLOW));
        int tx = startX + Math.round((goalX - startX) * progress),
                ty = startY + Math.round((goalY - startY) * progress);
        if (slow > 0) {
            tx = worldX + Math.round((tx - worldX) * (100 - slow) / 100f);
            ty = worldY + Math.round((ty - worldY) * (100 - slow) / 100f);
        }
        moving = SlimeMotion.move(gp, this, tx, ty, true);
        if (attackingHop)
            for (int i = 0; i < gp.players.length; i++) {
                Player p = gp.players[i];
                if ((hitMask & (1 << i)) != 0 || !SlimeMotion.activePlayer(gp, p)) continue;
                if (SlimeMotion.body(this, worldX, worldY)
                        .intersects(SlimeMotion.body(p, p.worldX, p.worldY))) {
                    hitMask |= 1 << i;
                    SlimeMotion.hit(gp, this, p, attack);
                }
            }
    }

    /** Resolves the sprite lift pixels result used by MON_GreenSlime. */
    @Override
    protected float spriteLiftPixels() {
        return state == State.WANDER || state == State.LEAP_ATTACK
                ? (float) Math.sin(Math.PI * Math.min(1, timer / (float) LEAP_TICKS)) * 14
                : 0;
    }

    /** Returns the current animation for MON_GreenSlime. */
    @Override
    protected SpriteAnimation getCurrentAnimation() {
        return sprites.get((flashing && !dying ? "hurt" : animState) + "_" + direction);
    }

    /** Returns the current frame for MON_GreenSlime. */
    @Override
    protected BufferedImage getCurrentFrame() {
        if (!dying && state == State.WINDUP) {
            var a = sprites.get("attack_" + direction);
            return getPose(a.frames[Math.min(4, 2 + timer * 3 / WINDUP_TICKS)]);
        }
        if (!dying && state == State.LEAP_ATTACK) {
            var a = sprites.get("attack_" + direction);
            return getPose(a.frames[Math.min(a.frames.length - 1, 5 + timer * 4 / LEAP_TICKS)]);
        }
        return super.getCurrentFrame();
    }

    /** Returns the pose for MON_GreenSlime. */
    private BufferedImage getPose(BufferedImage f) {
        if (flashing && flashCounter < 2) return getFlashVersion(f);
        var c = getActiveTintColor();
        return c == null ? f : getTintVersion(f, c);
    }

    /** Performs the check drop operation for MON_GreenSlime. */
    @Override
    public void checkDrop() {
        gp.quests.dropLoot(
                object.ItemRegistry.create(object.ItemType.SLIME_GEL, gp), worldX, worldY);
        int roll = ThreadLocalRandom.current().nextInt(100);
        gp.quests.dropLoot(
                roll < 50
                        ? new object.OBJ_bronzecoin(gp)
                        : roll < 75 ? new object.OBJ_heart(gp) : new object.OBJ_crystal(gp),
                worldX,
                worldY);
    }

    /** Captures the slime for MON_GreenSlime. */
    @Override
    public void captureSlime(MonsterState s) {
        s.slimeDirectorSpawn = directorSpawn;
        s.slimeInvestigationFailed = investigationFailed;
        s.slimeInvestigationX = investigationX;
        s.slimeInvestigationY = investigationY;
        s.slimeInvestigationTicks = investigationTicks;
        s.slimeStalledTicks = stalledTicks;
        s.slimeState = state.name();
        s.slimeTimer = timer;
        s.slimeCooldown = cooldown;
        s.slimeArrivalGrace = arrivalGrace;
        s.homeX = homeSet ? homeX : worldX;
        s.homeY = homeSet ? homeY : worldY;
        s.slimeStartX = startX;
        s.slimeStartY = startY;
        s.slimeGoalX = goalX;
        s.slimeGoalY = goalY;
        s.slimeHitMask = hitMask;
        s.slimeReturning = returningHop;
        s.nestId = nestId;
    }

    /** Restores the slime for MON_GreenSlime. */
    @Override
    public void restoreSlime(MonsterState s, boolean disk) {
        directorSpawn = s.slimeDirectorSpawn;
        investigationFailed = s.slimeInvestigationFailed;
        investigationX = s.slimeInvestigationX;
        investigationY = s.slimeInvestigationY;
        investigationTicks =
                Math.max(
                        0,
                        Math.min(
                                world.EncounterSpawnConfig.INVESTIGATE_SECONDS * 60,
                                s.slimeInvestigationTicks));
        stalledTicks = Math.max(0, s.slimeStalledTicks);
        if (s.slimeState == null) {
            setHome();
            return;
        }
        state = State.valueOf(s.slimeState);
        timer = s.slimeTimer;
        cooldown = s.slimeCooldown;
        arrivalGrace = s.slimeArrivalGrace;
        homeX = s.homeX;
        homeY = s.homeY;
        homeSet = true;
        startX = s.slimeStartX;
        startY = s.slimeStartY;
        goalX = s.slimeGoalX;
        goalY = s.slimeGoalY;
        hitMask = s.slimeHitMask;
        returningHop = s.slimeReturning;
        nestId = s.nestId == null ? "" : s.nestId;
        if (disk && (committed() || state == State.WANDER)) {
            enter(investigating() ? State.INVESTIGATE : State.RECOVERY);
            cooldown = Math.max(cooldown, 60);
        }
        animState =
                dying ? "death" : committed() ? "attack" : state == State.WANDER ? "walk" : "idle";
    }
}
