package monster;

import entity.*;

import main.Gamepanel;

import net.MonsterState;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * One recurring undead, three authored attacks. All simulation uses the existing fixed 60 Hz tick.
 */
public final class MON_Skeleton extends Monster {
    public enum State {
        BURIED,
        EMERGING,
        IDLE,
        WANDERING,
        INVESTIGATING,
        CHASING,
        ATTACKING,
        RECOVERING,
        HURT,
        DYING,
        RETREATING
    }

    public enum Attack {
        NONE,
        BONE_SWIPE,
        RATTLING_LUNGE,
        DOUBLE_RAKE
    }

    public State state = State.BURIED;
    public Attack currentAttack = Attack.NONE;
    private Attack recoveryAttack = Attack.NONE;
    public int spawnDay, baseHealth, baseSwipe, baseLunge, baseRake;
    public boolean enraged, departing;
    private static boolean effectsWarmed;
    private int timer,
            cooldown,
            lungeCooldown,
            rakeCooldown,
            hitMask,
            secondMask,
            goalX,
            goalY,
            startX,
            startY,
            daylightTicks,
            shadeTimer = 120,
            wanderTimer,
            investigationTicks;
    private double walkRemainder;
    private double gaitFrame, idleFrame;
    private int gaitTicks;
    private final double paceScale, stridePixels, pacePhase, paceFrequency, idleRate;
    private long combatUntil;
    private final ai.Pathfinder route;
    private final Map<String, SpriteAnimation> normal = new HashMap<>(), moon = new HashMap<>();
    private final SplittableRandom random;

    /** Creates a MON_Skeleton instance and initializes the state required by its collaborators. */
    public MON_Skeleton(Gamepanel gp) {
        super(gp);
        route = new ai.Pathfinder(gp);
        random = new SplittableRandom(combatId);
        name = "Skeleton · The Restless Dead";
        exp = 6;
        paceScale = random.nextDouble(.88, 1.12);
        stridePixels = gp.tileSize * random.nextDouble(.9, 1.15);
        gaitFrame = random.nextDouble(12);
        idleFrame = random.nextDouble(12);
        idleRate = random.nextDouble(.09, .15);
        pacePhase = random.nextDouble(Math.PI * 2);
        paceFrequency = random.nextDouble(.025, .045);
        solidArea.setBounds(10, 28, 28, 18);
        solidAreaDefaultX = 10;
        solidAreaDefaultY = 28;
        incomingHitImmunity = 36;
        incomingHitKnockback = gp.tileSize / 3;
        art.SkeletonArt.build(normal, false);
        art.SkeletonArt.build(moon, true);
        synchronized (MON_Skeleton.class) {
            if (!effectsWarmed) {
                Entity.preloadSpriteEffects(normal);
                Entity.preloadSpriteEffects(moon);
                effectsWarmed = true;
            }
        }
        sprites.putAll(normal);
        down1 = normal.get("idle_down").frames[0];
        scaleForDay(gp.lighting.cycle.day());
        invincible = true;
        invincibleCounter = 0;
    }

    /** Performs the scale for day operation for MON_Skeleton. */
    public void scaleForDay(int day) {
        spawnDay = Math.max(1, day);
        baseHealth =
                (int) Math.round(SkeletonConfig.HEALTH * SkeletonConfig.hpMultiplier(spawnDay));
        baseSwipe =
                (int) Math.round(SkeletonConfig.SWIPE * SkeletonConfig.damageMultiplier(spawnDay));
        baseLunge =
                (int) Math.round(SkeletonConfig.LUNGE * SkeletonConfig.damageMultiplier(spawnDay));
        baseRake =
                (int) Math.round(SkeletonConfig.RAKE * SkeletonConfig.damageMultiplier(spawnDay));
        maxHP = baseHealth;
        HP = maxHP;
        attack = baseSwipe;
        aggroRange = SkeletonConfig.DETECTION;
    }

    /** Evaluates the committed condition for MON_Skeleton. */
    public boolean committed() {
        return state == State.ATTACKING;
    }

    /** Evaluates the emerging condition for MON_Skeleton. */
    public boolean emerging() {
        return state == State.BURIED || state == State.EMERGING;
    }

    /** Performs the investigate operation for MON_Skeleton. */
    public void investigate(int x, int y) {
        goalX = x;
        goalY = y;
        investigationTicks = SkeletonConfig.HUNT_TIME;
    }

    /** Refreshes the enrage for MON_Skeleton. */
    public void refreshEnrage() {
        boolean next = gp.lighting.cycle.fullMoon(SkeletonConfig.MOON_DAYS);
        if (next == enraged) return;
        int old = maxHP;
        enraged = next;
        maxHP = (int) Math.round(baseHealth * (next ? 1 + SkeletonConfig.MOON_HP : 1));
        if (HP > 0)
            HP = Math.clamp((int) Math.round(HP * (double) maxHP / Math.max(1, old)), 1, maxHP);
        sprites.clear();
        sprites.putAll(next ? moon : normal);
        aggroRange = SkeletonConfig.DETECTION + (next ? 1 : 0);
    }

    /** Resolves the duration result used by MON_Skeleton. */
    private int duration(int ticks) {
        return Math.max(
                1, (int) Math.round(ticks * (enraged ? 1 - SkeletonConfig.MOON_COOLDOWN : 1)));
    }

    /** Resolves the decision cooldown result used by MON_Skeleton. */
    private int decisionCooldown(int ticks) {
        return duration(
                (int)
                        Math.round(
                                ticks
                                        * (spawnDay >= SkeletonConfig.AGGRESSIVE_DAY ? .9 : 1)
                                        * (spawnDay >= SkeletonConfig.VETERAN_DAY ? .95 : 1)));
    }

    /** Performs the enter operation for MON_Skeleton. */
    private void enter(State next) {
        state = next;
        timer = 0;
        spriteNum = 1;
        spriteCounter = 0;
        moving = false;
        if (next != State.RECOVERING) recoveryAttack = Attack.NONE;
    }

    /** Reports whether this component can deal contact damage for MON_Skeleton. */
    @Override
    public boolean canDealContactDamage() {
        return false;
    }

    /** Evaluates the resists basic impact condition for MON_Skeleton. */
    @Override
    protected boolean resistsBasicImpact() {
        return committed() || emerging() || departing;
    }

    /** Reports whether this component can be knocked back for MON_Skeleton. */
    @Override
    public boolean canBeKnockedBack() {
        return super.canBeKnockedBack() && !resistsBasicImpact();
    }

    /** Performs the on damaging hit operation for MON_Skeleton. */
    @Override
    protected void onDamagingHit() {
        combatUntil = gp.currentTick + 10 * 60;
    }

    /** Performs the on stagger operation for MON_Skeleton. */
    @Override
    protected void onStagger(int ticks) {
        if (emerging() || departing) return;
        currentAttack = Attack.NONE;
        enter(State.HURT);
        cooldown = Math.max(cooldown, ticks);
    }

    /** Performs the damage reaction operation for MON_Skeleton. */
    @Override
    public void damageReaction() {
        onDamagingHit();
        if (!resistsBasicImpact()) {
            currentAttack = Attack.NONE;
            recoverFor = 18;
            enter(State.HURT);
        }
    }

    /** Evaluates the attack slot condition for MON_Skeleton. */
    private boolean attackSlot() {
        int attackers = 0;
        for (Monster m : gp.monster)
            if (m instanceof MON_Skeleton other
                    && other != this
                    && other.committed()
                    && SlimeMotion.distance(this, other) < gp.tileSize * 5) attackers++;
        int limit =
                gp.lighting.cycle.night()
                        ? SkeletonConfig.NIGHT_ATTACKERS
                        : SkeletonConfig.ATTACKERS;
        if (attackers >= limit) return false;
        return true;
    }

    /** Begins the attack for MON_Skeleton. */
    public boolean beginAttack(Attack next, Player target) {
        if (emerging()
                || departing
                || dying
                || committed()
                || state == State.RECOVERING
                || state == State.HURT
                || cooldown > 0
                || !SlimeMotion.activePlayer(gp, target)
                || !attackSlot()) return false;
        if (next == Attack.RATTLING_LUNGE
                && (spawnDay < SkeletonConfig.LUNGE_DAY || lungeCooldown > 0)) return false;
        if (next == Attack.DOUBLE_RAKE && (spawnDay < SkeletonConfig.RAKE_DAY || rakeCooldown > 0))
            return false;
        if (next == Attack.NONE) return false;
        direction =
                SlimeMotion.facing(
                        SlimeMotion.centerX(target) - SlimeMotion.centerX(this),
                        SlimeMotion.centerY(target) - SlimeMotion.centerY(this));
        // Snapshot the lunge bearing at commitment; later player motion never retargets it.
        int distance = gp.tileSize * 5 / 2;
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
        currentAttack = next;
        attackWind =
                duration(
                        next == Attack.BONE_SWIPE
                                ? SkeletonConfig.SWIPE_WIND
                                : next == Attack.DOUBLE_RAKE
                                        ? SkeletonConfig.RAKE_WIND
                                        : SkeletonConfig.LUNGE_WIND);
        attackGap = duration(SkeletonConfig.RAKE_GAP);
        hitMask = secondMask = 0;
        enter(State.ATTACKING);
        return true;
    }

    /** Resolves the attack box result used by MON_Skeleton. */
    private Rectangle attackBox(boolean lunge) {
        Rectangle body = SlimeMotion.body(this, worldX, worldY);
        int reach = gp.tileSize * 2 / 3;
        return switch (direction) {
            case "up" ->
                    new Rectangle(
                            body.x - 5, body.y - reach, body.width + 10, reach + body.height / 2);
            case "left" ->
                    new Rectangle(
                            body.x - reach, body.y - 7, reach + body.width / 2, body.height + 14);
            case "right" ->
                    new Rectangle(
                            body.x + body.width / 2,
                            body.y - 7,
                            reach + body.width / 2,
                            body.height + 14);
            default ->
                    new Rectangle(
                            body.x - 5,
                            body.y + body.height / 2,
                            body.width + 10,
                            reach + body.height / 2);
        };
    }

    /** Performs the hit operation for MON_Skeleton. */
    private void hit(int base, boolean second, boolean lunge) {
        Rectangle box = attackBox(lunge);
        int mask = second ? secondMask : hitMask;
        for (int i = 0; i < gp.players.length; i++) {
            Player p = gp.players[i];
            if ((mask & (1 << i)) != 0 || !SlimeMotion.activePlayer(gp, p)) continue;
            if (box.intersects(SlimeMotion.body(p, p.worldX, p.worldY))) {
                mask |= 1 << i;
                String bearing = direction;
                double nightBonus = gp.lighting.cycle.night() ? 1 + SkeletonConfig.NIGHT_DAMAGE : 1;
                SlimeMotion.hit(
                        gp,
                        this,
                        p,
                        (int)
                                Math.round(
                                        base
                                                * nightBonus
                                                * (enraged ? 1 + SkeletonConfig.MOON_DAMAGE : 1)));
                direction = bearing;
            }
        }
        if (second) secondMask = mask;
        else hitMask = mask;
    }

    /** Performs the attacks operation for MON_Skeleton. */
    private void attacks() {
        int wind = attackWind;
        int active = SkeletonConfig.SWIPE_ACTIVE, end = wind + active;
        if (currentAttack == Attack.BONE_SWIPE) {
            if (timer >= wind && timer < end) hit(baseSwipe, false, false);
        } else if (currentAttack == Attack.DOUBLE_RAKE) {
            int second = wind + attackGap;
            if (timer >= wind && timer < wind + active) hit(baseRake, false, false);
            if (timer >= second && timer < second + active) hit(baseRake, true, false);
            end = second + active;
        } else {
            end = wind + SkeletonConfig.LUNGE_TIME;
            if (timer == wind) {
                startX = worldX;
                startY = worldY;
            }
            if (timer >= wind && timer < end) {
                double f = (timer - wind + 1.0) / SkeletonConfig.LUNGE_TIME;
                int x = startX + (int) Math.round((goalX - startX) * f),
                        y = startY + (int) Math.round((goalY - startY) * f);
                if (!SlimeMotion.move(gp, this, x, y, true)) end = timer;
                hit(baseLunge, false, true);
            }
        }
        if (timer >= end) {
            int recovery =
                    currentAttack == Attack.BONE_SWIPE
                            ? SkeletonConfig.SWIPE_RECOVERY
                            : currentAttack == Attack.DOUBLE_RAKE
                                    ? SkeletonConfig.RAKE_RECOVERY
                                    : SkeletonConfig.LUNGE_RECOVERY;
            cooldown = decisionCooldown(SkeletonConfig.SWIPE_COOLDOWN);
            if (currentAttack == Attack.RATTLING_LUNGE)
                lungeCooldown = decisionCooldown(SkeletonConfig.LUNGE_COOLDOWN);
            if (currentAttack == Attack.DOUBLE_RAKE)
                rakeCooldown = decisionCooldown(SkeletonConfig.RAKE_COOLDOWN);
            recoveryAttack = currentAttack;
            currentAttack = Attack.NONE;
            enter(State.RECOVERING);
            recoverFor = duration(recovery);
        }
    }

    private int recoverFor = 27, attackWind, attackGap;

    /** Advances this component for one simulation tick. */
    @Override
    public void update() {
        if (dying) {
            if (dyingCounter == 0)
                Particle.emit(this, Particle.Style.DUST, new Color(0xC4ADA0), 4, 24, 0, 0, 0, 0);
            state = State.DYING;
            currentAttack = Attack.NONE;
            super.update();
            return;
        }
        if (!gp.chunks.activeAt(worldX, worldY) || gp.quests.inDungeon()) return;
        gaitTicks++;
        idleFrame = (idleFrame + idleRate) % 12;
        refreshEnrage();
        moving = false;
        speed = 0;
        timer++;
        if (cooldown > 0) cooldown--;
        if (lungeCooldown > 0) lungeCooldown--;
        if (rakeCooldown > 0) rakeCooldown--;
        boolean night = gp.lighting.cycle.night();
        Player p = findNearestPlayer();
        boolean active = SlimeMotion.activePlayer(gp, p);
        double distance = active ? SlimeMotion.distance(this, p) : Double.POSITIVE_INFINITY;
        int detection = night ? SkeletonConfig.NIGHT_DETECTION + (enraged ? 1 : 0) : aggroRange;
        boolean visible =
                !emerging()
                        && active
                        && distance <= detection * gp.tileSize
                        && SlimeMotion.sight(gp, this, p);
        // Newly emerged night hunters pursue nearby players around obstacles, without homing
        // attacks.
        boolean hunting =
                night
                        && active
                        && investigationTicks > 0
                        && distance <= (detection + 2) * gp.tileSize;
        if (visible || hunting) combatUntil = gp.currentTick + 10 * 60;
        if (visible && !committed()) {
            // Keep the last seen destination through brief foliage/terrain occlusion.
            goalX = p.worldX;
            goalY = p.worldY;
            investigationTicks = Math.max(investigationTicks, 180);
        }
        if (night) {
            daylightTicks = 0;
            shadeTimer = 120;
        } else if (--shadeTimer <= 0) {
            shadeTimer = 120;
            daylightTicks =
                    gp.slimeSpawner.skeletonShade(worldX, worldY)
                            ? 0
                            : Math.min(SkeletonConfig.DAYLIGHT_GRACE, daylightTicks + 120);
        }
        if (state == State.RETREATING && (night || gp.currentTick < combatUntil)) {
            // Reverse the sinking pose if night falls or combat resumes during departure.
            double surfaced = 1 - Math.min(1, timer / 60.0);
            departing = false;
            enter(State.EMERGING);
            timer = (int) Math.round(surfaced * SkeletonConfig.EMERGE);
        }
        if (daylightTicks >= SkeletonConfig.DAYLIGHT_GRACE
                && gp.currentTick >= combatUntil
                && !committed()
                && !emerging()
                && state != State.RETREATING) {
            departing = true;
            enter(State.RETREATING);
        }
        if (state == State.RETREATING) {
            animState = "retreat";
            if (timer >= 60) {
                alive = false;
                return;
            }
            tickCombatEffects();
            return;
        }
        if (emerging()) {
            state = State.EMERGING;
            animState = "emerge";
            invincible = timer < SkeletonConfig.EMERGE * .44;
            invincibleCounter = 0;
            if (timer >= SkeletonConfig.EMERGE) {
                invincible = false;
                cooldown = 60;
                enter(investigationTicks > 0 ? State.INVESTIGATING : State.IDLE);
            }
            return;
        }
        if (investigationTicks > 0) investigationTicks--;
        if (knockbackActive) {
            currentAttack = Attack.NONE;
            updateKnockback();
            enter(State.HURT);
            animState = "hurt";
            tickCombatEffects();
            return;
        }
        if (state == State.HURT) {
            animState = "hurt";
            if (timer >= 18) enter(State.RECOVERING);
            tickCombatEffects();
            return;
        }
        if (committed()) {
            attacks();
            animState =
                    state == State.RECOVERING
                            ? "idle"
                            : currentAttack == Attack.RATTLING_LUNGE
                                    ? "lunge"
                                    : currentAttack == Attack.DOUBLE_RAKE ? "rake" : "swipe";
            tickCombatEffects();
            return;
        }
        if (state == State.RECOVERING) {
            animState = "idle";
            if (timer >= recoverFor) enter(State.IDLE);
            tickCombatEffects();
            return;
        }
        if (visible || hunting) {
            state = State.CHASING;
            double range = distance / gp.tileSize;
            if (visible && range <= 1.25 && cooldown == 0) {
                Attack a =
                        spawnDay >= SkeletonConfig.RAKE_DAY
                                        && rakeCooldown == 0
                                        && random.nextInt(3) == 0
                                ? Attack.DOUBLE_RAKE
                                : Attack.BONE_SWIPE;
                beginAttack(a, p);
            } else if (visible
                    && range > 1.5
                    && range <= 3
                    && cooldown == 0
                    && spawnDay >= SkeletonConfig.LUNGE_DAY
                    && lungeCooldown == 0) beginAttack(Attack.RATTLING_LUNGE, p);
            // A denied swarm attack slot must not leave the pursuer idle.
            if (!committed()) moveToward(p.worldX, p.worldY);
        } else if (investigationTicks > 0) {
            state = State.INVESTIGATING;
            if (Math.hypot(worldX - goalX, worldY - goalY) < gp.tileSize) investigationTicks = 0;
            else moveToward(goalX, goalY);
        } else {
            state = State.WANDERING;
            if (--wanderTimer <= 0) {
                wanderTimer = 180 + random.nextInt(180);
                goalX = worldX + random.nextInt(-2, 3) * gp.tileSize;
                goalY = worldY + random.nextInt(-2, 3) * gp.tileSize;
            }
            if (wanderTimer > 120) moveToward(goalX, goalY);
        }
        animState =
                committed()
                        ? currentAttack == Attack.RATTLING_LUNGE
                                ? "lunge"
                                : currentAttack == Attack.DOUBLE_RAKE ? "rake" : "swipe"
                        : moving ? "walk" : "idle";
        tickCombatEffects();
    }

    /** Performs the move toward operation for MON_Skeleton. */
    private void moveToward(int x, int y) {
        double pace =
                SkeletonConfig.SPEED
                        * paceScale
                        * (1 + .06 * Math.sin(gaitTicks * paceFrequency + pacePhase))
                        * (enraged ? 1 + SkeletonConfig.MOON_SPEED : 1)
                        * (spawnDay >= SkeletonConfig.AGGRESSIVE_DAY ? 1.1 : 1)
                        * (spawnDay >= SkeletonConfig.VETERAN_DAY ? 1.05 : 1);
        if (daylightTicks > 0 && gp.currentTick >= combatUntil) pace *= .55;
        int slow =
                Math.max(
                        Math.max(
                                getStatusEffectValue("rangerSlow"),
                                getStatusEffectValue("warriorSlow")),
                        getStatusEffectValue(MageEffect.FROST_SLOW));
        pace *= Math.max(.1, (100 - slow) / 100.0);
        walkRemainder += pace;
        speed = (int) walkRemainder;
        walkRemainder -= speed;
        route.toward(this, x, y);
        int amount = speed;
        int beforeX = worldX, beforeY = worldY;
        if (amount > 0)
            SlimeMotion.move(
                    gp,
                    this,
                    worldX
                            + (direction.equals("right")
                                    ? amount
                                    : direction.equals("left") ? -amount : 0),
                    worldY
                            + (direction.equals("down")
                                    ? amount
                                    : direction.equals("up") ? -amount : 0),
                    true);
        if (amount > 0 && worldX == beforeX && worldY == beforeY) {
            // Terrain routing does not include moving swarm bodies. Slip around a blocked
            // neighbor rather than repeatedly pushing the same point; all steps still collide.
            boolean horizontal = direction.equals("left") || direction.equals("right");
            int side = (combatId & 1) == 0 ? 1 : -1;
            for (int attempt = 0; attempt < 2; attempt++, side = -side) {
                int sx = horizontal ? worldX : worldX + side * amount;
                int sy = horizontal ? worldY + side * amount : worldY;
                if (SlimeMotion.move(gp, this, sx, sy, true)) {
                    direction =
                            horizontal ? (side > 0 ? "down" : "up") : (side > 0 ? "right" : "left");
                    break;
                }
                if (worldX != beforeX || worldY != beforeY) break;
            }
        }
        double traveled = Math.hypot(worldX - beforeX, worldY - beforeY);
        moving = traveled > 0;
        // Footfalls follow actual displacement, so blocked skeletons do not march in place.
        gaitFrame = (gaitFrame + traveled * 12 / stridePixels) % 12;
        speed = 0;
    }

    /** Resolves the attack pose result used by MON_Skeleton. */
    private String attackPose(Attack attack, boolean second) {
        return attack == Attack.RATTLING_LUNGE
                ? "lunge"
                : attack == Attack.DOUBLE_RAKE && second ? "rake" : "swipe";
    }

    /** Returns the current animation for MON_Skeleton. */
    @Override
    protected SpriteAnimation getCurrentAnimation() {
        String pose = animState;
        if (committed())
            pose = attackPose(currentAttack, timer >= attackWind + SkeletonConfig.SWIPE_ACTIVE);
        else if (state == State.RECOVERING && recoveryAttack != Attack.NONE)
            pose = attackPose(recoveryAttack, true);
        else if (flashing && !dying && !emerging()) pose = "hurt";
        return sprites.get(pose + "_" + direction);
    }

    /** Resolves the strike frame result used by MON_Skeleton. */
    private int strikeFrame() {
        int age = timer,
                wind = attackWind,
                active =
                        currentAttack == Attack.RATTLING_LUNGE
                                ? SkeletonConfig.LUNGE_TIME
                                : SkeletonConfig.SWIPE_ACTIVE;
        if (currentAttack == Attack.DOUBLE_RAKE && age >= wind + active) {
            age -= wind + active;
            wind = Math.max(1, attackGap - active);
        }
        if (age < wind) return Math.min(4, age * 5 / Math.max(1, wind));
        return 5 + Math.min(3, (age - wind) * 4 / Math.max(1, active));
    }

    /** Resolves the animation frame result used by MON_Skeleton. */
    @Override
    protected BufferedImage animationFrame(SpriteAnimation a) {
        if (emerging()) return a.frames[Math.min(11, timer * 12 / SkeletonConfig.EMERGE)];
        if (state == State.RETREATING) return a.frames[Math.min(11, timer * 12 / 60)];
        if (committed()) return a.frames[strikeFrame()];
        if (state == State.RECOVERING && recoveryAttack != Attack.NONE)
            return a.frames[9 + Math.min(2, timer * 3 / Math.max(1, recoverFor))];
        if (a == sprites.get("walk_" + direction))
            return a.frames[
                    Math.min(a.frames.length - 1, (int) (gaitFrame * a.frames.length / 12))];
        if (a == sprites.get("idle_" + direction)) return a.frames[(int) idleFrame];
        return super.animationFrame(a);
    }

    /** Performs the check drop operation for MON_Skeleton. */
    @Override
    public void checkDrop() {
        if (!departing) gp.quests.dropLoot(new object.OBJ_bronzecoin(gp), worldX, worldY);
    }

    /** Renders this component into the supplied Java2D graphics context. */
    @Override
    public void draw(Graphics2D g) {
        super.draw(g);
        if (hasStatusEffect("frogMorph")) return;
        if (!committed() || currentAttack == Attack.RATTLING_LUNGE) return;
        int age = timer - attackWind;
        if (currentAttack == Attack.DOUBLE_RAKE && age >= attackGap) age -= attackGap;
        if (age < 0 || age >= SkeletonConfig.SWIPE_ACTIVE) return;
        Player camera = gp.localPlayer();
        if (camera == null) return;
        Graphics2D trail = (Graphics2D) g.create();
        trail.translate(
                SlimeMotion.centerX(this) - camera.worldX + camera.screenX,
                SlimeMotion.centerY(this) - camera.worldY + camera.screenY - 8);
        trail.rotate(
                switch (direction) {
                    case "up" -> Math.PI;
                    case "left" -> Math.PI / 2;
                    case "right" -> -Math.PI / 2;
                    default -> 0;
                });
        trail.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        trail.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        trail.setColor(
                new Color(
                        241,
                        218,
                        193,
                        Math.max(0, 155 - age * 130 / Math.max(1, SkeletonConfig.SWIPE_ACTIVE))));
        for (int claw = 0; claw < 3; claw++)
            trail.draw(
                    new java.awt.geom.QuadCurve2D.Double(
                            -15 + claw * 3, 12, 0, gp.tileSize * .8 - claw * 3, 15 + claw * 3, 14));
        trail.dispose();
    }

    /** Performs the capture operation for MON_Skeleton. */
    public void capture(MonsterState s) {
        s.skeletonState = state.name();
        s.skeletonAttack = currentAttack.name();
        s.skeletonDay = spawnDay;
        s.skeletonBaseHealth = baseHealth;
        s.skeletonSwipe = baseSwipe;
        s.skeletonLunge = baseLunge;
        s.skeletonRake = baseRake;
        s.skeletonEnraged = enraged;
        s.skeletonTimer = timer;
        s.skeletonCooldown = cooldown;
        s.skeletonLungeCooldown = lungeCooldown;
        s.skeletonRakeCooldown = rakeCooldown;
        s.skeletonDeparting = departing;
        s.skeletonDaylight = daylightTicks;
        s.skeletonGoalX = goalX;
        s.skeletonGoalY = goalY;
        s.skeletonInvestigation = investigationTicks;
        s.skeletonPose = animState;
        s.skeletonRecoveryAttack = recoveryAttack.name();
        s.skeletonGait = gaitFrame;
        s.skeletonIdle = idleFrame;
        s.skeletonWind = attackWind;
        s.skeletonGap = attackGap;
        s.skeletonRecovery = recoverFor;
    }

    /** Snapshot-only presentation; never runs attacks or restarts them on the guest. */
    public void syncVisual(MonsterState s) {
        state = s.skeletonState == null ? State.IDLE : State.valueOf(s.skeletonState);
        currentAttack = s.skeletonAttack == null ? Attack.NONE : Attack.valueOf(s.skeletonAttack);
        recoveryAttack =
                s.skeletonRecoveryAttack == null
                        ? Attack.NONE
                        : Attack.valueOf(s.skeletonRecoveryAttack);
        timer = Math.max(0, s.skeletonTimer);
        attackWind = s.skeletonWind;
        attackGap = s.skeletonGap;
        recoverFor = s.skeletonRecovery;
        gaitFrame = Double.isFinite(s.skeletonGait) ? Math.clamp(s.skeletonGait, 0, 11.999) : 0;
        idleFrame = Double.isFinite(s.skeletonIdle) ? Math.clamp(s.skeletonIdle, 0, 11.999) : 0;
        animState = dying ? "death" : s.skeletonPose == null ? "idle" : s.skeletonPose;
        if (enraged != s.skeletonEnraged) {
            enraged = s.skeletonEnraged;
            sprites.clear();
            sprites.putAll(enraged ? moon : normal);
        }
    }

    /** Restores this component from its saved state. */
    public void restore(MonsterState s) {
        scaleForDay(Math.max(1, s.skeletonDay));
        if (s.skeletonBaseHealth > 0) {
            baseHealth = s.skeletonBaseHealth;
            baseSwipe = s.skeletonSwipe;
            baseLunge = s.skeletonLunge;
            baseRake = s.skeletonRake;
        }
        enraged = s.skeletonEnraged;
        maxHP = s.maxHP;
        HP = s.HP;
        sprites.clear();
        sprites.putAll(enraged ? moon : normal);
        aggroRange = SkeletonConfig.DETECTION + (enraged ? 1 : 0);
        departing = s.skeletonDeparting;
        daylightTicks = s.skeletonDaylight;
        goalX = s.skeletonGoalX;
        goalY = s.skeletonGoalY;
        investigationTicks = s.skeletonInvestigation;
        state = s.skeletonState == null ? State.EMERGING : State.valueOf(s.skeletonState);
        timer = Math.max(0, s.skeletonTimer);
        cooldown = Math.max(60, s.skeletonCooldown);
        lungeCooldown = s.skeletonLungeCooldown;
        rakeCooldown = s.skeletonRakeCooldown;
        currentAttack = Attack.NONE;
        // Never replay a committed hit after a load; emerging and daylight departure resume safely.
        if (state == State.ATTACKING || state == State.HURT) {
            enter(State.RECOVERING);
        }
        animState =
                dying
                        ? "death"
                        : emerging() ? "emerge" : state == State.RETREATING ? "retreat" : "idle";
        invincible = emerging() && timer < SkeletonConfig.EMERGE * .44;
    }
}
