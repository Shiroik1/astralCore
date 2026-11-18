package entity;

import art.Palette;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Spell targeting, cast poses and hit-earned runes for the Mage. */
public final class MageCombat {
    public static final String INFUSION = "runeInfusion";
    private static final Map<String, BufferedImage> ICONS = new HashMap<>();
    private final Player player;
    private int castTicks, castLength;
    private double heading;

    /** Creates a MageCombat instance and initializes the state required by its collaborators. */
    MageCombat(Player player) {
        this.player = player;
    }

    public static final String[] SPELLS = {
        "Arcane Missile",
        "Immolate",
        "Conjure Flowerfield",
        "Magic Buff",
        "Blizzard",
        "Magic Protection",
        "Rainfall",
        "Arcane Crochet",
        "Morph",
        "Arcane Strike",
        "Minor Enhancement",
        "Strong Enhancement",
        "Runic Convergence"
    };
    public static final int[] UNLOCK = {1, 3, 4, 5, 7, 9, 11, 12, 15, 18, 1, 1, 25};
    private static final int[] COST = {8, 12, 5, 10, 18, 14, 6, 12, 18, 42, 0, 0, 30};
    private int boltHits, runeCooldown, shield, shieldTicks, enhancement;

    private static final String[] ICON_NAMES = {
        "arcane_missile",
        "immolate",
        "flowerfield",
        "magic_buff",
        "blizzard",
        "magic_protection",
        "rainfall",
        "arcane_crochet",
        "morph",
        "arcane_strike",
        "arcane_bolt",
        "arcane_missile",
        "arcane_strike"
    };
    private Runnable pendingCast;
    private boolean crochetReady;
    private int castingId = -1;

    /** Gives the HUD the spell being prepared so it can label the wind-up. */
    public String castingName() {
        return castingId >= 0 && castTicks > 0 ? SPELLS[castingId] : "";
    }

    /** Shows the armed next-shot modifier until the next free bolt is released. */
    public boolean crochetReady() {
        return crochetReady;
    }

    /** Displays remaining shield capacity, including on a replicated caster. */
    public int shieldRemaining() {
        return shield;
    }

    /** Applies only the host's visible preparation and shield state to a guest. */
    public void syncPreparation(int id, int capacity, boolean crochet) {
        castingId = id >= 0 && id < SPELLS.length ? id : -1;
        shield = capacity;
        crochetReady = crochet;
    }

    /** Returns the stable spell index for the network snapshot. */
    public int castingId() {
        return castingId;
    }

    /** Strike has the longest recovery. Shared page slots retain their own saved cooldown. */
    private static int cooldown(int id) {
        return id == 9 ? 1200 : id == 0 ? 45 : id >= 10 ? 90 : 300;
    }

    /** Returns the current player.progression.staff cooldowns value. */
    private int[] cooldowns() {
        return player.progression.staffCooldowns;
    }

    /** Derives Staff mastery level from its saved cumulative combat XP. */
    public int level() {
        return progression.Progression.skillLevel(player.magicMasteryXP);
    }

    /** Returns the invested rank of the requested Staff talent, defaulting to zero. */
    public int rank(String id) {
        return player.progression.talents.getOrDefault(id, 0);
    }

    /**
     * Selects the next saved spell page and rebuilds visible slots without resetting spell
     * cooldowns.
     */
    public void page(int delta) {
        player.progression.staffPage = Math.floorMod(player.progression.staffPage + delta, 3);
        if (player.isMage()) setup(player.skills);
    }

    /** Resolves the index result used by MageCombat. */
    private int index(int slot) {
        return player.progression.staffPage * 5 + slot;
    }

    /**
     * Populates the five active spell slots from the selected page, including costs and saved
     * cooldowns.
     */
    public void setup(Skill[] slots) {
        icon("arcane_bolt");
        for (int slot = 0; slot < slots.length; slot++) {
            int index = index(slot);
            if (index >= SPELLS.length) {
                slots[slot] = null;
                continue;
            }
            final int id = index;
            int cost = Math.max(0, COST[id] - rank("mana_flow"));
            slots[slot] =
                    new Skill(SPELLS[id], cost, cooldown(id), () -> cast(id), icon(ICON_NAMES[id]));
            slots[slot].cooldownRemaining = cooldowns()[id];
            slots[slot].description =
                    "Staff Lv "
                            + UNLOCK[id]
                            + " · "
                            + cost
                            + " MP · Page "
                            + (player.progression.staffPage + 1);
        }
    }

    /** Loads each cropped spell icon once, when equipping the staff or preparing a spell page. */
    public static BufferedImage icon(String name) {
        return ICONS.computeIfAbsent(
                name,
                key -> {
                    String path = "/skill_icon/mage/" + key + ".png";
                    try (var stream = MageCombat.class.getResourceAsStream(path)) {
                        if (stream == null) throw new java.io.IOException("Missing " + path);
                        return javax.imageio.ImageIO.read(stream);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
    }

    /** Consumes shield absorption and awards a throttled rune for meaningful prevented damage. */
    public int protect(int incoming) {
        if (!player.hasStaff() || shield <= 0 || incoming <= 0) return incoming;
        int absorbed = Math.min(shield, incoming);
        shield -= absorbed;
        if (absorbed >= 2) {
            boolean earned = runeEvent();
            if (earned && rank("protective_flow") > 0)
                player.MP = Math.min(player.maxMP, player.MP + rank("protective_flow"));
        }
        if (shield == 0) {
            Particle.emit(
                    player,
                    Particle.Style.RING,
                    MageEffect.FROST,
                    player.gp.tileSize,
                    18,
                    0,
                    0,
                    0,
                    -8);
            burst(player, null, power(.5f), true);
        }
        return incoming - absorbed;
    }

    /** Evaluates the rune event condition for MageCombat. */
    boolean runeEvent() {
        if (runeCooldown > 0 || player.arcaneCharge >= 5) return false;
        runeCooldown = level() >= 22 ? 30 : 60;
        player.arcaneCharge = Math.min(5, player.arcaneCharge + 1);
        return true;
    }

    /** Performs the cast operation for MageCombat. */
    private void cast(int id) {
        cooldowns()[id] = cooldown(id);
        int tx = player.currentInput.aimWorldX, ty = player.currentInput.aimWorldY;
        Monster target = target();
        int windup =
                switch (id) {
                    case 0 -> 24;
                    case 4 -> 42;
                    case 9 -> 60;
                    default -> 0;
                };
        poseFor(windup == 0 ? 8 : windup, aim(tx, ty));
        if (windup == 0) release(id, tx, ty, target);
        else {
            castingId = id;
            pendingCast = () -> release(id, tx, ty, target);
        }
    }

    /** Resolves a cast using its original aim and target. Runes are consumed at release. */
    private void release(int id, int tx, int ty, Monster target) {
        if (id == 9 && !reachable(target)) return;
        boolean damaging = id == 0 || id == 1 || id == 4 || id == 9 || id == 12;
        int boost = damaging ? enhancement : 0;
        if (damaging) enhancement = 0;
        float mult =
                1
                        + boost * .25f
                        + (level() >= 25 ? .1f : 0)
                        + rank("arcane_weaving") * .03f
                        + (boost > 0 ? rank("runic_overload") * .08f : 0);
        switch (id) {
            case 0 ->
                    player.gp.projectileList.add(
                            MageEffect.missile(
                                    player,
                                    x(),
                                    y(),
                                    aim(tx, ty),
                                    power((1.3f + .35f * spendRunes()) * mult)));
            case 1 ->
                    player.gp.projectileList.add(
                            new StaffSpell(
                                    player,
                                    "Immolate",
                                    targetCenter(target, true),
                                    targetCenter(target, false),
                                    power(.3f * mult),
                                    180 + rank("lingering_embers") * 45,
                                    target));
            case 2 ->
                    player.gp.projectileList.add(
                            new StaffSpell(player, "Flowerfield", tx, ty, 0, 300, null));
            case 3 -> {
                Player ally = aimedAlly(tx, ty);
                mark(ally == null ? player : ally, "staffBuff", 360, 4 + rank("arcane_weaving"));
            }
            case 6 -> {
                Entity subject = aimedAlly(tx, ty);
                if (subject == null) subject = reachable(target) ? target : player;
                player.gp.projectileList.add(
                        new StaffSpell(player, "Rainfall", tx, ty, 0, 240, subject));
            }
            case 4 ->
                    player.gp.projectileList.add(
                            new StaffSpell(
                                    player, "Blizzard", tx, ty, power(.32f * mult), 180, null));
            case 5 -> {
                shield = power(1.5f) + rank("wardcraft") * 5;
                shieldTicks = 360;
            }
            case 7 -> crochetReady = true;
            case 8 -> {
                target.addStatusEffect(new StatusEffect("frogMorph", 180, 1, 0));
                runeEvent();
            }
            case 9 -> {
                int damage = power((3.2f + .55f * spendRunes()) * mult);
                chain(player, target, damage, 4, true);
            }
            case 10, 11 -> {
                int runes = id == 10 ? 1 : 3;
                player.arcaneCharge -= runes;
                enhancement = runes;
            }
            case 12 -> {
                player.arcaneCharge = 0;
                player.gp.projectileList.add(
                        new StaffSpell(
                                player,
                                "Convergence",
                                tx,
                                ty,
                                power((1.1f + .12f * rank("grand_convergence")) * mult),
                                120,
                                null));
            }
        }
    }

    /** Buffs and elemental marks refresh in place so repeated casts cannot stack indefinitely. */
    static void mark(Entity target, String type, int duration, int strength) {
        target.statusEffects.removeIf(e -> e.type.equals(type));
        target.addStatusEffect(new StatusEffect(type, duration, strength, 0));
    }

    /** Chooses the closest visible ally under the cursor, within normal spell reach. */
    private Player aimedAlly(int tx, int ty) {
        Player best = null;
        double radius = player.gp.tileSize * .8;
        for (Player ally : player.gp.players) {
            if (ally == null
                    || ally.isDead
                    || ally.inWell != player.inWell
                    || monster.SlimeMotion.distance(player, ally) > player.gp.tileSize * 6
                    || !monster.SlimeMotion.sight(player.gp, player, ally)) continue;
            double d =
                    Math.hypot(
                            monster.SlimeMotion.centerX(ally) - tx,
                            monster.SlimeMotion.centerY(ally) - ty);
            if (d < radius) {
                radius = d;
                best = ally;
            }
        }
        return best;
    }

    /** Tests identity as well as range because a slot may be replaced during a cast. */
    private boolean reachable(Monster m) {
        return m != null
                && m.alive
                && !m.dying
                && slot(m) >= 0
                && monster.SlimeMotion.distance(player, m) <= player.gp.tileSize * 6
                && monster.SlimeMotion.sight(player.gp, player, m);
    }

    /** Returns the current registry slot, discarding despawned targets. */
    private int slot(Monster m) {
        for (int i = 0; i < player.gp.monster.length; i++) if (player.gp.monster[i] == m) return i;
        return -1;
    }

    /** Empties the five slots and returns the charge used by the spell. */
    private int spendRunes() {
        int spent = player.arcaneCharge;
        player.arcaneCharge = 0;
        return spent;
    }

    /** Links the next basic bolt to up to three fresh targets, charging only landed jumps. */
    void crochet(Monster primary, int damage) {
        chain(primary, null, Math.max(1, damage), Math.min(3, player.arcaneCharge), false);
    }

    /**
     * Jumps to the nearest unhit visible enemy from each impact. One event owns the entire chain.
     */
    private void chain(Entity from, Monster next, int damage, int limit, boolean lightning) {
        CombatHit hit =
                new CombatHit(lightning ? "arcane-strike" : "arcane-crochet")
                        .mastery(Entity.type_staff);
        if (from instanceof Monster m) hit.record(m);
        for (int hop = 0; hop < limit; hop++) {
            if (next == null) {
                double best = player.gp.tileSize * 2.5;
                for (Monster m : player.gp.monster) {
                    if (m == null || !m.alive || m.dying || hit.contains(m)) continue;
                    double distance = monster.SlimeMotion.distance(from, m);
                    if (distance <= best && monster.SlimeMotion.sight(player.gp, from, m)) {
                        next = m;
                        best = distance;
                    }
                }
            }
            if (next == null
                    || !player.mageAreaDamage(
                            slot(next), damage, player.direction, true, false, hit)) break;
            player.gp.projectileList.add(StaffSpell.link(player, from, next, lightning));
            if (lightning) mark(next, "shocked", 360, 1);
            else player.arcaneCharge--;
            from = next;
            next = null;
            damage = Math.max(1, Math.round(damage * .78f));
        }
    }

    /** Detonates each elemental mark once, after the missile's direct hit has been accepted. */
    void react(Monster primary, int damage) {
        if (primary.statusEffects.removeIf(e -> e.type.equals("immolated"))) {
            Particle.burst(primary, Particle.Style.SQUARE, MageEffect.EMBER, 14, 7, 22, 5);
            burst(primary, null, Math.max(1, damage / 2), false);
        }
        if (primary.hasStatusEffect(MageEffect.FROST_SLOW)) {
            for (Monster m : player.gp.monster) {
                if (m == null
                        || m.isBoss()
                        || !m.alive
                        || m.dying
                        || !near(primary, m)
                        || !m.statusEffects.removeIf(e -> e.type.equals(MageEffect.FROST_SLOW)))
                    continue;
                mark(m, "frozen", 90, 1);
                Particle.emit(m, Particle.Style.RING, MageEffect.FROST, 18, 18, 0, 0, 0, -6);
            }
        }
        if (primary.statusEffects.removeIf(e -> e.type.equals("shocked"))) {
            burst(primary, primary, Math.max(1, damage * 2 / 3), false);
            Particle.emit(
                    primary,
                    Particle.Style.RING,
                    MageEffect.LILAC,
                    player.gp.tileSize * 2,
                    20,
                    0,
                    0,
                    0,
                    -6);
        }
    }

    /** Checks the short impact radius and prevents secondary hits through obstacles. */
    private boolean near(Entity origin, Monster target) {
        return monster.SlimeMotion.distance(origin, target) <= player.gp.tileSize * 2
                && monster.SlimeMotion.sight(player.gp, origin, target);
    }

    /** Resolves an elemental burst, pushing enemies away from a broken shield. */
    private void burst(Entity origin, Monster excluded, int damage, boolean push) {
        CombatHit hit = new CombatHit("staff-burst").mastery(Entity.type_staff);
        for (int i = 0; i < player.gp.monster.length; i++) {
            Monster m = player.gp.monster[i];
            if (m == null || m == excluded || !m.alive || m.dying || !near(origin, m)) continue;
            String dir =
                    monster.SlimeMotion.facing(
                            monster.SlimeMotion.centerX(m) - monster.SlimeMotion.centerX(origin),
                            monster.SlimeMotion.centerY(m) - monster.SlimeMotion.centerY(origin));
            if (player.mageAreaDamage(i, damage, dir, true, false, hit) && push && !m.isBoss())
                m.startKnockback(dir, player.gp.tileSize / 2);
        }
    }

    /** Resolves the target center result used by MageCombat. */
    private int targetCenter(Monster m, boolean horizontal) {
        return horizontal ? monster.SlimeMotion.centerX(m) : monster.SlimeMotion.centerY(m);
    }

    /** Resolves the target result used by MageCombat. */
    private Monster target() {
        int i = player.currentInput.targetedMonsterIndex;
        return i >= 0 && i < player.gp.monster.length ? player.gp.monster[i] : null;
    }

    /** Resolves the runes required result used by MageCombat. */
    public int runesRequired(int slot) {
        int i = index(slot);
        return i == 7 || i == 10 ? 1 : i == 11 ? 3 : i == 12 ? 5 : 0;
    }

    /**
     * Checks mastery, MP-independent cast restrictions, target legality, rune requirements and
     * spell cooldown.
     */
    boolean canUse(int slot) {
        int id = index(slot);
        if (id >= SPELLS.length
                || level() < UNLOCK[id]
                || cooldowns()[id] > 0
                || !canStartCast()
                || player.arcaneCharge < runesRequired(slot)) return false;
        if (id == 1 || id == 8 || id == 9) {
            Monster m = target();
            if (m == null
                    || !m.alive
                    || m.dying
                    || id == 8 && m.hasStatusEffect("frogMorph")
                    || id == 8 && m.isBoss()
                    || !canAimGround(6)) return false;
            return monster.SlimeMotion.distance(player, m) <= player.gp.tileSize * 6
                    && monster.SlimeMotion.sight(player.gp, player, m);
        }
        if (id == 7) return !crochetReady;
        return id == 5 || id == 10 || id == 11 || canAimGround(6);
    }

    /** Reports whether this component can start cast for MageCombat. */
    boolean canStartCast() {
        return !active()
                && player.hasStaff()
                && !player.knockbackActive
                && !player.currentInput.gameplaySuppressed
                && !player.currentInput.ePressed;
    }

    /** Reports whether this component can aim ground for MageCombat. */
    boolean canAimGround(float range) {
        int tx = player.currentInput.aimWorldX, ty = player.currentInput.aimWorldY;
        return Math.hypot(tx - x(), ty - y()) <= player.gp.tileSize * range
                && !RangerEffect.blocked(player.gp, tx, ty, 1, 1)
                && RangerEffect.clearLine(player.gp, x(), y(), tx, ty);
    }

    /** Performs the infusion cue operation for MageCombat. */
    private void infusionCue() {
        Particle.emit(player, Particle.Style.RING, MageEffect.LILAC, 18, 22, 0, 0, 0, -10);
        Particle.burst(player, Particle.Style.SQUARE, MageEffect.LILAC, 9, 5.5f, 20, 3.8f);
    }

    /** Creates the free aimed Arcane Bolt through the existing authoritative projectile list. */
    void fireBolt(int aimX, int aimY) {
        if (!player.canResolveWorldActions() || !player.hasStaff()) return;
        var bolt =
                MageEffect.bolt(
                        player,
                        x(),
                        y(),
                        aim(aimX, aimY),
                        power(.35f * (1 + .04f * rank("precise_bolt"))));
        bolt.crochet = crochetReady;
        crochetReady = false;
        player.gp.projectileList.add(bolt);
    }

    /** Performs the missed bolt operation for MageCombat. */
    void missedBolt() {
        boltHits = 0;
    }

    /** Performs the gain rune operation for MageCombat. */
    void gainRune() {
        if (++boltHits >= 2) {
            boltHits = 0;
            runeEvent();
        }
    }

    /** Performs the pose for operation for MageCombat. */
    void poseFor(int ticks, double angle) {
        player.interruptForMageSpell();
        castTicks = castLength = ticks;
        heading = angle;
    }

    /** Evaluates the active condition for MageCombat. */
    boolean active() {
        return castTicks > 0;
    }

    /** Returns the current cast ticks value. */
    public int castTicks() {
        return castTicks;
    }

    /** Returns the current cast length value. */
    public int castLength() {
        return castLength;
    }

    /** Returns the current heading value. */
    public double heading() {
        return heading;
    }

    /** Cancels the active operation and clears its temporary state. */
    void cancel() {
        castingId = -1;
        pendingCast = null;
        crochetReady = false;
        castTicks = 0;
        shield = 0;
        enhancement = 0;
    }

    /** Advances this component for one simulation tick. */
    void update() {
        if (shieldTicks > 0 && --shieldTicks == 0) shield = 0;
        for (int i = 0; i < cooldowns().length; i++) if (cooldowns()[i] > 0) cooldowns()[i]--;
        if (runeCooldown > 0) runeCooldown--;
        if (!player.hasStaff()
                || player.knockbackActive
                || player.currentInput.gameplaySuppressed) {
            castingId = -1;
            pendingCast = null;
            castTicks = 0;
            return;
        }
        if (castTicks > 0 && --castTicks == 0 && pendingCast != null) {
            Runnable spell = pendingCast;
            pendingCast = null;
            castingId = -1;
            spell.run();
        }
    }

    /** Performs the pose operation for MageCombat. */
    void pose() {
        if (!active()) return;
        player.animState = "attack";
        player.direction = RangerEffect.direction(heading);
        float progress = 1 - castTicks / (float) castLength;
        player.spriteNum =
                1
                        + Math.min(
                                CharacterSprites.ATTACK_FRAMES - 1,
                                (int) (progress * CharacterSprites.ATTACK_FRAMES));
    }

    /** Synchronizes the visual for MageCombat. */
    public void syncVisual(int ticks, int length, double angle, int runes, boolean infused) {
        if (infused && !player.hasStatusEffect(INFUSION)) infusionCue();
        if (player.arcaneCharge < player.maxArcaneCharge && runes >= player.maxArcaneCharge) {
            Particle.emit(player, Particle.Style.RING, MageEffect.LILAC, 22, 18, 0, 0, 0, -22);
        }
        castTicks = ticks;
        castLength = length;
        heading = angle;
    }

    /** Renders this component into the supplied Java2D graphics context. */
    void draw(Graphics2D graphics) {
        if (player.isDead || !player.hasStaff()) return;
        int infusion = player.getStatusEffectValue(INFUSION);
        int runes = player.arcaneCharge;
        if (shield > 0) {
            Player cam = player.gp.localPlayer();
            int sx = x() - cam.worldX + cam.screenX, sy = y() - cam.worldY + cam.screenY;
            var ward = (Graphics2D) graphics.create();
            ward.setColor(new Color(164, 205, 243, 42));
            ward.fillOval(sx - 24, sy - 48, 48, 60);
            ward.setColor(MageEffect.FROST);
            ward.drawOval(sx - 24, sy - 48, 48, 60);
            ward.dispose();
        }
        if (runes <= 0 && infusion <= 0) return;
        Player camera = player.gp.localPlayer();
        float cx = x() - camera.worldX + camera.screenX;
        float cy = y() - camera.worldY + camera.screenY - 44;
        cx +=
                switch (player.direction) {
                    case "left" -> -14;
                    case "right" -> 17;
                    case "up" -> -14;
                    default -> 15;
                };
        var g = (Graphics2D) graphics.create();
        if (runes >= player.maxArcaneCharge) {
            g.setColor(new Color(213, 179, 240, 150));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Float(cx - 12, cy - 7, 24, 14));
        }
        for (int i = 0; i < runes; i++) {
            double angle = player.gp.currentTick * 0.085 + i * Math.PI * 2 / 5;
            float dx = (float) Math.cos(angle) * 12, dy = (float) Math.sin(angle) * 7;
            g.setColor(MageEffect.LILAC);
            g.fill(MageEffect.diamond(cx + dx, cy + dy, 3.3f, 4.2f));
            g.setColor(Palette.PLASTER);
            g.fill(MageEffect.diamond(cx + dx, cy + dy, 1.5f, 2.2f));
        }
        for (int i = 0; i < infusion; i++) {
            double angle = -player.gp.currentTick * 0.12 + i * Math.PI;
            g.setColor(MageEffect.FROST);
            g.fill(
                    MageEffect.diamond(
                            cx + (float) Math.cos(angle) * 18,
                            cy + (float) Math.sin(angle) * 11,
                            3,
                            4));
        }
        g.dispose();
    }

    /** Resolves the power result used by MageCombat. */
    private int power(float multiplier) {
        return Math.max(
                1,
                (int)
                        Math.round(
                                player.getEffectiveAttack()
                                        * progression.Progression.multiplier(
                                                player.intelligence
                                                        + player.getStatusEffectValue("staffBuff"))
                                        / progression.Progression.multiplier(player.intelligence)
                                        * multiplier
                                        * (1 + rank("elemental_echo") * .04)));
    }

    /** Resolves the x result used by MageCombat. */
    private int x() {
        return player.worldX + (int) player.solidArea.getCenterX();
    }

    /** Resolves the y result used by MageCombat. */
    private int y() {
        return player.worldY + (int) player.solidArea.getCenterY();
    }

    /** Resolves the aim direction result used by MageCombat. */
    String aimDirection(int tx, int ty) {
        return RangerEffect.direction(aim(tx, ty));
    }

    /** Resolves the aim result used by MageCombat. */
    double aim(int tx, int ty) {
        int dx = tx - x(), dy = ty - y();
        return (tx == 0 && ty == 0 || dx == 0 && dy == 0) ? facing() : Math.atan2(dy, dx);
    }

    /** Resolves the facing result used by MageCombat. */
    double facing() {
        return switch (player.direction) {
            case "up" -> -Math.PI / 2;
            case "down" -> Math.PI / 2;
            case "left" -> Math.PI;
            default -> 0;
        };
    }
}
