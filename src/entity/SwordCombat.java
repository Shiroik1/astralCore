package entity;

import monster.SlimeMotion;

import progression.*;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Sword attacks share one committed animation and one hit event per swing. */
public final class SwordCombat {
    // 60 simulation ticks = one second. Damage is active only during SWING_TICKS.
    public static final int RAISE_TICKS = Math.max(1, Integer.getInteger("astral.sword.raise", 7));
    public static final int HOLD_TICKS = Math.max(1, Integer.getInteger("astral.sword.hold", 3));
    public static final int SWING_TICKS = Math.max(1, Integer.getInteger("astral.sword.swing", 3));
    public static final int END_TICKS = Math.max(1, Integer.getInteger("astral.sword.end", 5));
    public static final int RECOVERY_TICKS =
            Math.max(7, Integer.getInteger("astral.sword.recovery", 15));
    private final Player p;
    private int age, total, wind, active, counterTicks, guardAge, combo, comboTicks, followupTicks;
    private String action = "Basic Slash", bearing = "down";
    private CombatHit hit;
    private boolean guarding, connected;
    private double rhythm;

    /** Creates a SwordCombat instance and initializes the state required by its collaborators. */
    SwordCombat(Player p) {
        this.p = p;
    }

    /** Evaluates the equipped condition for SwordCombat. */
    public boolean equipped() {
        return p.currentWeapon != null
                && !p.currentWeapon.isPlaceholder
                && p.currentWeapon.type == Entity.type_sword;
    }

    /** Resolves the level result used by SwordCombat. */
    public int level() {
        return Progression.skillLevel(p.meleeMasteryXP);
    }

    /** Resolves the rank result used by SwordCombat. */
    private int rank(String id) {
        return p.progression.talents.getOrDefault(id, 0);
    }

    public static final String[] NAMES = {
        "Powerslash", "Warcry", "Charge", "Taunt", "Sunder", "Retaliate", "Bullthrust"
    };
    public static final int[] UNLOCK = {5, 7, 10, 15, 17, 20, 25};
    private static final int[] COST = {20, 30, 0, 0, 20, 25, 50},
            CD = {300, 1500, 480, 900, 600, 1080, 1080};

    static {
        for (int i = 0; i < COST.length; i++) {
            COST[i] = Math.max(0, Integer.getInteger("astral.warrior.cost." + i, COST[i]));
            CD[i] = Math.max(1, Integer.getInteger("astral.warrior.cooldown." + i, CD[i]));
        }
    }

    private final int[] cooldowns = new int[7];
    private int page, retaliation, retaliationTicks;
    private Monster chargeTarget;
    private boolean bull;
    private int heldSlot;
    public final ChargeAnimation charge = new ChargeAnimation(this);
    public final BullthrustAnimation thrust = new BullthrustAnimation(this);

    /** Switches the five visible actions without discarding their cooldowns. */
    public void page() {
        setup(p.skills);
    }

    /** Maps a visible action slot to its stable Warrior ability. */
    private int ability(int slot) {
        return p.progression.swordSlots[slot];
    }

    /** Populates existing action slots from the selected Warrior page. */
    public void setup(Skill[] slots) {
        for (int i = 0; i < 5; i++) {
            final int slot = i;
            int id = ability(i);
            if (id < 0) {
                slots[i] = null;
                continue;
            }
            slots[i] = new Skill(NAMES[id], COST[id], CD[id], () -> start(slot), icon(id));
            slots[i].cooldownRemaining = cooldowns[id];
            slots[i].description = description(i);
        }
    }

    /** Describes mastery and resource requirements from the authoritative ability definition. */
    public String description(int slot) {
        int id = ability(slot);
        return "Sword Lv "
                + UNLOCK[id]
                + " · "
                + COST[id]
                + " Rage · "
                + CD[id] / 60
                + "s cooldown";
    }

    /** Resolves a living selected enemy without retaining an array slot across frames. */
    private Monster target() {
        int i = p.currentInput.targetedMonsterIndex;
        return i >= 0 && i < p.gp.monster.length ? p.gp.monster[i] : null;
    }

    /** Validates target, mastery, committed action and resource restrictions before payment. */
    public boolean canUse(int slot) {
        int id = ability(slot);
        if (id < 0) return false;
        return equipped()
                && level() >= UNLOCK[id]
                && cooldowns[id] == 0
                && !p.attacking
                && !p.knockbackActive
                && !p.isDead
                && !p.currentInput.gameplaySuppressed
                && (id != 2
                        || (!p.hasStatusEffect("stun")
                                && !p.hasStatusEffect("frozen")
                                && ChargeAnimation.target(p) != null));
    }

    /** Starts the requested action after the existing resource handler has paid once. */
    private void start(int slot) {
        heldSlot = slot;
        int id = ability(slot);
        cooldowns[id] = CD[id];
        if (id == 1) {
            for (Player ally : p.gp.players)
                if (ally != null
                        && !ally.isDead
                        && ally.inWell == p.inWell
                        && SlimeMotion.distance(p, ally) <= p.gp.tileSize * 5) {
                    MageCombat.mark(ally, "warcry", 600, 10);
                }
            pulse();
            return;
        }
        if (id == 3) {
            for (Monster m : p.gp.monster)
                if (m != null
                        && m.alive
                        && !m.dying
                        && !m.isBoss()
                        && SlimeMotion.distance(p, m) <= p.gp.tileSize * 4
                        && SlimeMotion.sight(p.gp, p, m)) m.challenge(p, p.gp.currentTick + 240);
            pulse();
            return;
        }
        if (id == 5) {
            retaliation = 3;
            retaliationTicks = 360;
            pulse();
            return;
        }
        action = NAMES[id];
        chargeTarget = id == 2 ? null : target();
        bull = id == 6;
        begin();
        p.attacking = true;
        p.animState = "attack";
        p.spriteNum = 1;
        if (id == 2) {
            charge.start(p);
            bearing = p.direction;
        }
        if (bull) {
            thrust.start(p, heldSlot);
            bearing = p.direction;
        } else if (id == 2) p.direction = bearing;
    }

    /** Emits restrained feedback using the shared particle pool. */
    private void pulse() {
        Particle.emit(p, Particle.Style.RING, art.Palette.PLASTER, 48, 24, 0, 0, 0, -8);
    }

    /** Applies one valid basic-hit reward independently to each enemy. */
    private void basic(Monster m) {
        p.rage = Math.min(100, p.rage + 8);
        int n = m.getStatusEffectValue("warriorRhythm");
        if (n == 5) {
            m.statusEffects.removeIf(e -> e.type.equals("warriorRhythm"));
            StatusEffect bleed =
                    new StatusEffect(
                            "warriorBleed", 301, Math.max(1, p.getEffectiveAttack() / 5), 60);
            bleed.source = p;
            m.statusEffects.removeIf(e -> e.type.equals("warriorBleed"));
            m.addStatusEffect(bleed);
        } else MageCombat.mark(m, "warriorRhythm", 180, Math.min(5, n + 1));
    }

    /** Reflects only accepted incoming damage through the existing combat damage path. */
    public void received(Entity source, int damage) {
        if (!equipped() || damage <= 0) return;
        p.rage = Math.min(100, p.rage + 5);
        if (retaliation > 0 && retaliationTicks > 0 && source instanceof Monster m) {
            retaliation--;
            for (int i = 0; i < p.gp.monster.length; i++)
                if (p.gp.monster[i] == m)
                    p.damageMonsterOnce(
                            i,
                            Math.max(1, damage / 4),
                            false,
                            p.direction,
                            new CombatHit("retaliation").mastery(Entity.type_sword),
                            0);
        }
    }

    /** Keeps the existing Pursuing Strike follow-up after a connected Charge. */
    void chargeConnected() {
        followupTicks = 120;
    }

    /** Advances this component for one simulation tick. */
    public void tick() {
        for (int i = 0; i < 7; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        if (retaliationTicks > 0) --retaliationTicks;
        if (!equipped() || p.isDead || p.inDialogue || p.currentInput.gameplaySuppressed) {
            cancel();
            return;
        }
        if (!p.attacking && hit != null) {
            hit = null;
            age = 0;
            action = "Basic Slash";
        }
        for (int i = 0; i < p.skills.length; i++) {
            int id = ability(i);
            if (id < 0 || id >= cooldowns.length) {
                p.skills[i] = null;
                continue;
            }
            if (p.skills[i] != null) p.skills[i].cooldownRemaining = cooldowns[id];
        }
        if (followupTicks > 0) followupTicks--;
        if (counterTicks > 0) counterTicks--;
        if (comboTicks > 0) comboTicks--;
        else combo = 0;
        boolean next = p.currentInput.rightHeld && level() >= 5 && !p.attacking;
        if (next) {
            if (!guarding) guardAge = 0;
            guardAge++;
        } else guardAge = 0;
        guarding = next;
    }

    /** Returns the current guarding value. */
    public boolean guarding() {
        return guarding;
    }

    /** Evaluates the counter ready condition for SwordCombat. */
    public boolean counterReady() {
        return counterTicks > 0;
    }

    /** Cancels the active operation and clears its temporary state. */
    public void cancel() {
        if (thrust.active() || charge.active()) {
            p.attacking = false;
            p.animState = "idle";
            p.spriteNum = 1;
            p.currentInput.movementLocked = false;
        }
        thrust.clear();
        charge.clear();
        bull = false;
        age = 0;
        hit = null;
        action = "Basic Slash";
        guarding = false;
        guardAge = 0;
        counterTicks = 0;
        rhythm = 0;
        combo = 0;
        followupTicks = 0;
    }

    /** Performs the begin operation for SwordCombat. */
    private void begin() {
        bearing = p.direction;
        age = 0;
        connected = false;
        hit = new CombatHit("sword:" + action);
        hit.masteryType = Entity.type_sword;
        boolean heavy = action.equals("Powerslash");
        wind = heavy ? 15 : action.equals("Counter Slash") ? 4 : 9;
        active = 7;
        int recovery = heavy ? 23 : 17;
        recovery =
                Math.max(
                        7,
                        recovery
                                - (level() >= 9 ? 2 : 0)
                                - rank("quick_recovery")
                                - (level() >= 25 ? 2 : 0));
        if (usesSlashPose()) {
            wind = RAISE_TICKS + HOLD_TICKS;
            active = SWING_TICKS;
            recovery =
                    Math.max(
                                    7,
                                    RECOVERY_TICKS
                                            - rank("quick_recovery")
                                            - (level() >= 9 ? 2 : 0)
                                            - (level() >= 25 ? 2 : 0))
                            + END_TICKS;
        }
        total = wind + active + recovery;
    }

    /** Both slashes share anticipation, active frames, and the same facing-tile reach. */
    private boolean usesSlashPose() {
        return action.equals("Basic Slash")
                || action.equals("Powerslash")
                || action.equals("Sunder");
    }

    /** Replicates the empowered silhouette with the authoritative animation pose. */
    public boolean powerslash() {
        return p.gp.isNetworked && !p.gp.isHost ? replicatedPower : action.equals("Powerslash");
    }

    /** Mirrors Sunder's gold presentation without changing its damage or Exposed rules. */
    public boolean sundering() {
        return p.gp.isNetworked && !p.gp.isHost ? replicatedSunder : action.equals("Sunder");
    }

    private boolean replicatedSunder;

    /** Applies the authoritative gold slash presentation on network clients. */
    public void syncSunder(boolean sunder) {
        replicatedSunder = sunder;
    }

    private boolean replicatedPower;

    /** Applies the host's action presentation without executing client combat. */
    public void syncPower(boolean power) {
        replicatedPower = power;
    }

    /** Cached pose supplied by the authoritative host. */
    private int replicatedPose = -1;

    /** Applies the host's cached pose index without resolving client damage. */
    public void syncBasicPose(int pose) {
        replicatedPose = Math.clamp(pose, -1, 40);
    }

    /** Returns the cached basic-only pose; special skills keep their existing animation. */
    public int basicPose() {
        if (p.gp.isNetworked && !p.gp.isHost) return p.attacking ? replicatedPose : -1;
        if (thrust.active() || !p.attacking || !usesSlashPose() || hit == null) return -1;
        float t;
        if (age < RAISE_TICKS) t = .25f * age / RAISE_TICKS;
        else if (age < wind) t = .25f + .15f * (age - RAISE_TICKS) / HOLD_TICKS;
        else if (age < wind + active) t = .4f + .2f * (age - wind) / active;
        else if (age < wind + active + END_TICKS)
            t = .6f + .15f * (age - wind - active) / END_TICKS;
        else
            t =
                    .75f
                            + .25f
                                    * (age - wind - active - END_TICKS)
                                    / Math.max(1, total - wind - active - END_TICKS);
        return Math.clamp(Math.round(t * 40), 0, 40);
    }

    /**
     * Advances the committed sword wind-up, active frames and recovery without allowing a recovery
     * bypass.
     */
    public void animate() {
        if (p.gp.isNetworked && !p.gp.isHost && (thrust.active() || charge.active())) return;
        if (hit == null) {
            action = "Basic Slash";
            begin();
        }
        p.direction = bearing;
        if (charge.active()) {
            charge.update(1.0 / 60.0);
            return;
        }
        if (bull) {
            thrust.update(1.0 / 60.0);
            return;
        }
        age++;
        p.spriteNum =
                age < wind
                        ? 1 + Math.min(2, age * 3 / wind)
                        : age < wind + active
                                ? 4 + Math.min(2, (age - wind) * 3 / active)
                                : 7
                                        + Math.min(
                                                1,
                                                (age - wind - active)
                                                        * 2
                                                        / Math.max(
                                                                1,
                                                                total - wind - active)); // existing
        // sword pose
        // atlas
        if (age >= wind && age < wind + active && p.canResolveWorldActions()) strike();
        if (age >= total) {
            p.attacking = false;
            p.animState = "idle";
            p.spriteNum = 1;
            hit = null;
            age = 0;
            action = "Basic Slash";
            bull = false;
        }
    }

    /** Keeps the basic slash's visual and damage footprint together in the facing tile. */
    private Rectangle basicBounds() {
        Rectangle box = p.meleeAttackBounds();
        int extra = Math.max(0, p.gp.tileSize - p.attackArea.height);
        switch (p.direction) {
            case "up" -> {
                box.y -= extra;
                box.height += extra;
            }
            case "down" -> box.height += extra;
            case "left" -> {
                box.x -= extra;
                box.width += extra;
            }
            default -> box.width += extra;
        }
        return box;
    }

    /** Prebuilt tapered crescent in forward/across coordinates; independent of the body atlas. */
    private static final java.awt.Shape BASIC_SMEAR = basicSmear();

    /** Builds the silhouette once so rendering performs no path construction. */
    private static java.awt.Shape basicSmear() {
        var shape = new java.awt.geom.Path2D.Float();
        shape.moveTo(-.4, -.5);
        shape.curveTo(.85, -.42, .85, .42, -.4, .5);
        shape.curveTo(.10, .26, .10, -.26, -.4, -.5);
        shape.closePath();
        return shape;
    }

    /** Draws the same three-tick slash in the actual forward collision footprint. */
    private void drawBasicSmear(Graphics2D graphics) {
        int pose = basicPose();
        if (pose < 16 || pose >= 24) return;
        Rectangle box = basicBounds();
        Player camera = p.gp.localPlayer();
        var g = (Graphics2D) graphics.create();
        g.translate(
                box.getCenterX() - camera.worldX + camera.screenX,
                box.getCenterY() - camera.worldY + camera.screenY);
        g.rotate(art.MeleeSlash.heading(p.direction));
        boolean vertical = p.direction.equals("up") || p.direction.equals("down");
        g.scale(vertical ? box.height : box.width, vertical ? box.width : box.height);
        if (sundering()) {
            g.setColor(art.Palette.GLOW);
            g.setComposite(AlphaComposite.SrcOver.derive(.16f));
            g.setStroke(new BasicStroke(.28f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(BASIC_SMEAR);
            g.setComposite(AlphaComposite.SrcOver.derive(.32f));
            g.setStroke(new BasicStroke(.17f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(BASIC_SMEAR);
            g.setComposite(AlphaComposite.SrcOver);
        }
        if (powerslash() || sundering()) {
            g.setColor(sundering() ? art.Palette.GOLD : art.Palette.BRICK);
            g.setStroke(new BasicStroke(.095f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(BASIC_SMEAR);
        }
        g.setColor(art.Palette.PLASTER);
        g.fill(BASIC_SMEAR);
        g.dispose();
    }

    /**
     * Applies the current sword attack to visible targets once each and updates combo and follow-up
     * bonuses.
     */
    private void strike() {
        Rectangle box = usesSlashPose() ? basicBounds() : p.meleeAttackBounds();
        strike(box, p.gp.collisionChecker.checkEntityMultiple(box, p.gp.monster));
    }

    /** Shared Basic Slash power for ordinary swings and Charge's single finishing hit. */
    int basicAttackDamage() {
        return (int)
                Math.round(
                        p.getEffectiveAttack()
                                * (basicAttackScale()
                                        + rhythm
                                        + (combo >= 2 ? .04 * rank("blade_precision") : 0)
                                        + (followupTicks > 0 ? .12 * rank("pursuing_strike") : 0)));
    }

    /** Keeps the level capstone and existing basic-attack talent scaling authoritative. */
    private double basicAttackScale() {
        return 1 + (level() >= 25 ? .1 : 0) + .06 * rank("perfect_swordsman");
    }

    /** Sweeps only the bounded candidate set; all steps share the same CombatHit. */
    void strike(Rectangle box, java.util.List<Integer> candidates) {
        double scale =
                switch (action) {
                    case "Powerslash" ->
                            1.8
                                    + .12 * rank("heavy_edge")
                                    + (counterTicks > 0 ? .12 * rank("strong_counter") : 0);
                    case "Bullthrust" -> 1.5 + 1.5 * thrust.charge();
                    case "Sunder" -> 1;
                    case "Charge" -> 0;
                    case "Counter Slash" ->
                            (level() >= 22 ? 2.1 : 1.6)
                                    + .12 * rank("strong_counter")
                                    + .08 * rank("perfect_swordsman");
                    case "Sweeping Slash" -> 1.25;
                    case "Blade Rush" -> 1.5;
                    default -> basicAttackScale();
                };
        if (!action.equals("Basic Slash") && !action.equals("Counter Slash"))
            scale += .08 * rank("masterful_blade");
        double bonus =
                rhythm
                        + (combo >= 2 ? .04 * rank("blade_precision") : 0)
                        + (action.equals("Basic Slash") && followupTicks > 0
                                ? .12 * rank("pursuing_strike")
                                : 0);
        for (int i : candidates) {
            Monster m = p.gp.monster[i];
            if (m == null
                    || !m.alive
                    || m.dying
                    || hit == null
                    || hit.contains(m)
                    || !box.intersects(
                            m.worldX + m.solidArea.x,
                            m.worldY + m.solidArea.y,
                            m.solidArea.width,
                            m.solidArea.height)
                    || !SlimeMotion.sight(p.gp, p, m)
                    || action.equals("Charge")) continue;
            double statusBonus =
                    action.equals("Powerslash")
                            ? (m.hasStatusEffect("warriorBleed") ? .25 : 0)
                                    + (m.hasStatusEffect("exposed") ? .20 : 0)
                            : bull && m.hasStatusEffect("exposed") ? .20 : 0;
            boolean accepted =
                    p.damageMonsterOnce(
                            i,
                            (int)
                                    Math.round(
                                            p.getEffectiveAttack()
                                                    * (scale + bonus)
                                                    * (1 + statusBonus)),
                            false,
                            bearing,
                            hit,
                            CombatTuning.SWORD);
            if (accepted && powerslash())
                Particle.burst(m, Particle.Style.SPARK, art.Palette.ROOF_CORAL, 10, 5, 20, 4);
            if (accepted && sundering())
                Particle.burst(m, Particle.Style.SPARK, art.Palette.GLOW, 10, 5, 20, 4);
            if (accepted && bull) thrust.impact(m);
            if (accepted && action.equals("Basic Slash")) basic(m);
            if (accepted && action.equals("Sunder"))
                MageCombat.mark(
                        m, "exposed", 180 + 60 * m.getStatusEffectValue("warriorRhythm"), 15);
            if (!accepted) continue;
            if (!connected) {
                connected = true;
                rhythm = 0;
                combo = (combo + 1) % 3;
                comboTicks = 180;
            }
        }
    }

    /**
     * Reduces eligible frontal damage and opens the timed-counter opportunity without granting
     * immunity.
     */
    public int guard(Entity source, int incoming) {
        if (!equipped()
                || !guarding
                || p.attacking
                || p.currentInput.gameplaySuppressed
                || incoming <= 0) return incoming;
        int dx = SlimeMotion.centerX(source) - SlimeMotion.centerX(p),
                dy = SlimeMotion.centerY(source) - SlimeMotion.centerY(p);
        boolean front =
                switch (p.direction) {
                    case "up" -> dy < 0 && Math.abs(dx) <= -dy + 12;
                    case "down" -> dy > 0 && Math.abs(dx) <= dy + 12;
                    case "left" -> dx < 0 && Math.abs(dy) <= -dx + 12;
                    default -> dx > 0 && Math.abs(dy) <= dx + 12;
                };
        if (!front) return incoming;
        int window = (level() >= 22 ? 11 : 8) + rank("unbroken_guard");
        if (guardAge <= window && level() >= 7) counterTicks = 90;
        rhythm = .06 * rank("defensive_rhythm");
        if (guardAge <= window)
            Particle.burst(p, Particle.Style.SPARK, art.Palette.PLASTER, 4, 5, 12, 1.8f);
        double reduction =
                .4
                        + (level() >= 15 ? .1 : 0)
                        + .04 * rank("guard_discipline")
                        + .025 * rank("unbroken_guard");
        return Math.max(1, (int) Math.ceil(incoming * (1 - Math.min(.75, reduction))));
    }

    /** Reports preparation progress for the existing resource HUD. */
    public int chargePercent() {
        return thrust.charging() ? (int) Math.round(thrust.charge() * 100) : -1;
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(Graphics2D g) {
        if (!equipped()) return;
        if (charge.active()) {
            charge.drawImpact(g);
            return;
        }
        drawBasicSmear(g);
        int x = p.worldX - p.gp.localPlayer().worldX + p.gp.localPlayer().screenX + 24,
                y = p.worldY - p.gp.localPlayer().worldY + p.gp.localPlayer().screenY + 32;
        Graphics2D v = (Graphics2D) g.create();
        v.translate(x, y);
        v.rotate(
                switch (p.direction) {
                    case "up" -> Math.PI;
                    case "left" -> Math.PI / 2;
                    case "right" -> -Math.PI / 2;
                    default -> 0;
                });
        v.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (guarding) {
            v.setColor(counterTicks > 0 ? art.Palette.GLOW : new Color(240, 230, 208, 180));
            v.drawArc(-18, 0, 36, 30, 195, 150);
        }
        if (!bull && hit != null && !usesSlashPose() && age >= wind && age < wind + active) {
            v.setColor(new Color(244, 233, 207, 180));
            v.drawArc(-26, 2, 52, 38, 190, 160);
        }
        v.dispose();
    }

    /** Reuses the supplied Warrior icon crop, loaded once per action. */
    public static BufferedImage icon(int slot) {
        return Icons.IMAGES[slot];
    }

    private static final class Icons {
        static final BufferedImage[] IMAGES = new BufferedImage[7];

        static {
            for (int i = 0; i < 7; i++)
                try (var in =
                        SwordCombat.class.getResourceAsStream(
                                "/skill_icon/warrior/" + i + ".png")) {
                    IMAGES[i] = javax.imageio.ImageIO.read(in);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
        }
    }
}
