package entity;

import art.Palette;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.imageio.ImageIO;

/** Timed dagger casts and their visual state. The host owns damage and dash movement. */
public final class AssassinCombat {
    private enum Move {
        IDLE,
        CROSSCUT,
        SLIPSTEP,
        OPEN_VEIN,
        KNIFE_FAN,
        NIGHTFALL
    }

    static final String BLEED = "assassinBleed";
    static final Color CORAL = new Color(0xE68880);
    private static final Color PLUM = new Color(0x986581);
    private static final Color AFTERIMAGE = new Color(91, 54, 83, 220);
    private static final Move[] MOVES = Move.values();
    private static final Map<String, BufferedImage> ICONS = new HashMap<>();
    private static final int DASH_TICKS = 10;
    private final Player player;
    private Move move = Move.IDLE;
    private int age, critTicks, dashX, dashY, dashDistance, dashRange;
    private double heading, critHeading;
    private Monster locked;
    private boolean firstLanded, connected, dashBlocked;

    /**
     * Creates a AssassinCombat instance and initializes the state required by its collaborators.
     */
    AssassinCombat(Player player) {
        this.player = player;
    }

    /** Performs the setup operation for AssassinCombat. */
    void setup(Skill[] slots) {
        slots[0] =
                skill(
                        "Crosscut",
                        "crosscut",
                        12,
                        5,
                        "Two cuts on an enemy in front. A landed pair advances the combo.",
                        Move.CROSSCUT);
        slots[1] =
                skill(
                        "Slipstep",
                        "slipstep",
                        0,
                        7,
                        "Dash a short distance in the direction you face.",
                        Move.SLIPSTEP);
        slots[2] =
                skill(
                        "Open Vein",
                        "open_vein",
                        16,
                        8,
                        "Bleed for 4s. Your fourth-cut crit cashes out the remaining damage.",
                        Move.OPEN_VEIN);
        slots[3] =
                skill(
                        "Knife Fan",
                        "knife_fan",
                        20,
                        9,
                        "Throw three knives forward. Each enemy can be hit once.",
                        Move.KNIFE_FAN);
        slots[4] =
                skill(
                        "Nightfall",
                        "nightfall",
                        45,
                        14,
                        "Lunge forward and commit to two rapid pairs on one enemy.",
                        Move.NIGHTFALL);
    }

    /** Resolves the skill result used by AssassinCombat. */
    private Skill skill(
            String name, String icon, int focus, int seconds, String description, Move action) {
        Skill skill =
                new Skill(
                        name,
                        focus,
                        seconds * 60,
                        () -> {
                            start(action);
                            player.announceSkill(name);
                        },
                        icon(icon));
        skill.description = description;
        return skill;
    }

    /** Resolves the icon result used by AssassinCombat. */
    private static synchronized BufferedImage icon(String id) {
        return ICONS.computeIfAbsent(
                id,
                key -> {
                    try (var input =
                            AssassinCombat.class.getResourceAsStream(
                                    "/skill_icon/assassin/" + key + ".png")) {
                        if (input == null) throw new IOException("Missing Assassin icon " + key);
                        return ImageIO.read(input);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    /** Reports whether this component can use for AssassinCombat. */
    boolean canUse(int slot) {
        if (active()
                || !player.hasDaggers()
                || player.knockbackActive
                || player.currentInput.gameplaySuppressed
                || player.currentInput.ePressed) return false;
        // Slipstep can cancel a basic attack to escape; the offensive skills need a ready stance.
        if (player.attacking && slot != 1) return false;
        return slot != 4 || nightfallTarget(headingFor(player.direction)) != null;
    }

    /** Performs the start operation for AssassinCombat. */
    private void start(Move action) {
        heading = headingFor(player.direction);
        move = action;
        age = 0;
        firstLanded = false;
        connected = false;
        locked = null;
        player.interruptForAssassinSkill();
        if (action == Move.NIGHTFALL) locked = nightfallTarget(heading);
        if (action == Move.SLIPSTEP || action == Move.NIGHTFALL) {
            dashX = player.worldX;
            dashY = player.worldY;
            dashDistance = 0;
            dashBlocked = false;
            int tile = player.gp.tileSize;
            dashRange =
                    action == Move.SLIPSTEP
                            ? Math.round(tile * 1.65f)
                            : locked == null
                                    ? 0
                                    : (int)
                                            Math.max(
                                                    0,
                                                    Math.min(
                                                            tile * 1.85,
                                                            forwardDistance(locked, heading)
                                                                    - tile * 0.83));
            dashCue(false);
        }
    }

    /** Evaluates the active condition for AssassinCombat. */
    boolean active() {
        return move != Move.IDLE;
    }

    /** Resolves the action result used by AssassinCombat. */
    public int action() {
        return move.ordinal();
    }

    /** Returns the current age value. */
    public int age() {
        return age;
    }

    /** Returns the current heading value. */
    public double heading() {
        return heading;
    }

    /** Returns the current crit ticks value. */
    public int critTicks() {
        return critTicks;
    }

    /** Returns the current crit heading value. */
    public double critHeading() {
        return critHeading;
    }

    /** Cancels the active operation and clears its temporary state. */
    void cancel() {
        move = Move.IDLE;
        age = 0;
        locked = null;
    }

    /** Restores this component to its initial runtime state. */
    void reset() {
        cancel();
        critTicks = 0;
    }

    /** Advances this component for one simulation tick. */
    void update() {
        if (critTicks > 0) critTicks--;
        if (!active()) return;
        if (!player.hasDaggers()
                || player.knockbackActive
                || player.currentInput.gameplaySuppressed) {
            cancel();
            return;
        }
        if (!player.canResolveWorldActions() || player.gp.hitStopCounter > 0) return;
        age++;
        switch (move) {
            case SLIPSTEP -> {
                if (age <= DASH_TICKS) dash();
                if (age == DASH_TICKS) dashCue(true);
                if (age >= 13) cancel();
            }
            case CROSSCUT -> {
                if (age == 5) {
                    locked = target(heading, player.gp.tileSize * 1.6, 0.86);
                    cut(false, 1.2f);
                }
                if (age == 9) cut(true, 1.2f);
                if (age >= 17) cancel();
            }
            case OPEN_VEIN -> {
                if (age == 6) {
                    locked = target(heading, player.gp.tileSize * 1.6, 0.82);
                    if (reachable(locked)
                            && player.assassinHit(index(locked), power(0.65f), direction())
                            && !locked.dying) {
                        applyWound(locked);
                    }
                    slashParticles(CORAL, 6);
                }
                if (age >= 15) cancel();
            }
            case KNIFE_FAN -> {
                if (age == 5) {
                    var targets = new HashSet<Monster>();
                    for (int i = -1; i <= 1; i++) {
                        player.gp.projectileList.add(
                                RangerEffect.knife(
                                        player,
                                        x(),
                                        y() - 7,
                                        heading + i * 0.27,
                                        power(0.72f),
                                        targets));
                    }
                    slashParticles(Palette.PLASTER, 7);
                }
                if (age >= 14) cancel();
            }
            case NIGHTFALL -> {
                if (age <= DASH_TICKS) dash();
                if (age == DASH_TICKS) {
                    dashCue(true);
                    cut(false, 0.95f);
                }
                if (age == 14) cut(true, 0.95f);
                if (age == 21) cut(false, 0.95f);
                if (age == 25) cut(true, 0.95f);
                if (age >= 34) cancel();
            }
            case IDLE -> {}
        }
    }

    /** Performs the dash operation for AssassinCombat. */
    private void dash() {
        int destination = Math.round(dashRange * age / (float) DASH_TICKS);
        double dx = Math.cos(heading), dy = Math.sin(heading);
        var area = player.solidArea;
        while (!dashBlocked && dashDistance < destination) {
            int distance = dashDistance + 1;
            int x = dashX + (int) Math.round(dx * distance),
                    y = dashY + (int) Math.round(dy * distance);
            if (RangerEffect.blocksDash(player, x + area.x, y + area.y, area.width, area.height)) {
                dashBlocked = true;
                break;
            }
            player.worldX = x;
            player.worldY = y;
            dashDistance = distance;
        }
        if (age % 3 == 0 && !dashBlocked) {
            Particle.spray(
                    player,
                    Particle.Style.SQUARE,
                    PLUM,
                    3,
                    5,
                    14,
                    2.8f,
                    heading + Math.PI,
                    0.45,
                    0,
                    7);
        }
    }

    /** Performs the dash cue operation for AssassinCombat. */
    private void dashCue(boolean landing) {
        Particle.spray(
                player,
                Particle.Style.DUST,
                Color.WHITE,
                landing ? 3 : 4,
                landing ? 8 : 10,
                17,
                2.7f,
                heading + Math.PI,
                0.8,
                0,
                -3);
        Particle.spray(
                player,
                Particle.Style.SQUARE,
                PLUM,
                landing ? 4 : 7,
                6,
                16,
                4,
                heading + Math.PI,
                0.62,
                0,
                4);
    }

    /** Performs the slash particles operation for AssassinCombat. */
    private void slashParticles(Color color, int count) {
        float dx = (float) Math.cos(heading), dy = (float) Math.sin(heading);
        Particle.spray(
                player,
                Particle.Style.SQUARE,
                color,
                count,
                5.5f,
                17,
                3.8f,
                heading,
                0.65,
                dx * 25,
                dy * 25 - 5);
    }

    /** Performs the cut operation for AssassinCombat. */
    private void cut(boolean second, float multiplier) {
        if (reachable(locked) && (!second || firstLanded)) {
            boolean landed =
                    player.assassinCut(
                            index(locked), power(multiplier), direction(), second, connected);
            if (!second) firstLanded = landed;
            connected |= landed;
        } else if (!second) {
            firstLanded = false;
        }
        slashParticles(Palette.PLASTER, second ? 7 : 4);
    }

    /** Applies the wound for AssassinCombat. */
    private void applyWound(Monster target) {
        target.statusEffects.removeIf(
                effect -> effect.type.equals(BLEED) && effect.source == player);
        StatusEffect wound = new StatusEffect(BLEED, 240, power(0.16f), 60);
        wound.source = player;
        target.addStatusEffect(wound);
        bleedCue(target, false);
    }

    /** Resolves the consume wound result used by AssassinCombat. */
    int consumeWound(Monster target) {
        for (var iterator = target.statusEffects.iterator(); iterator.hasNext(); ) {
            StatusEffect wound = iterator.next();
            if (!wound.type.equals(BLEED) || wound.source != player) continue;
            int damage = ((wound.duration + wound.tickCounter) / wound.tickInterval) * wound.value;
            iterator.remove();
            bleedCue(target, true);
            return damage;
        }
        return 0;
    }

    /** Performs the bleed cue operation for AssassinCombat. */
    static void bleedCue(Monster target, boolean burst) {
        Particle.burst(
                target,
                Particle.Style.SQUARE,
                CORAL,
                burst ? 13 : 5,
                burst ? 7 : 5,
                20,
                burst ? 5.8f : 3.2f);
        if (burst) Particle.emit(target, Particle.Style.RING, CORAL, 20, 15, 0, 0, 0, 0);
    }

    /** Performs the finisher operation for AssassinCombat. */
    void finisher(String direction) {
        critTicks = 9;
        critHeading = headingFor(direction);
    }

    /** Performs the pose operation for AssassinCombat. */
    void pose() {
        if (!active()) return;
        player.direction = direction();
        if (move == Move.SLIPSTEP || move == Move.NIGHTFALL && age < DASH_TICKS) {
            player.animState = "run";
            player.spriteNum = 1 + age % CharacterSprites.RUN_FRAMES;
        } else {
            player.animState = "attack";
            int phase = move == Move.NIGHTFALL ? (age >= 19 ? age - 16 : age - 5) : age;
            player.spriteNum =
                    switch (phase) {
                        case 5, 6, 7 -> art.WeaponArt.DAGGER_FIRST_HIT_FRAME;
                        case 8 -> 5;
                        case 9, 10, 11 -> art.WeaponArt.DAGGER_SECOND_HIT_FRAME;
                        default ->
                                phase >= 12
                                        ? Math.min(
                                                CharacterSprites.ATTACK_FRAMES,
                                                7 + (phase - 12) / 3)
                                        : Math.max(1, 1 + phase / 2);
                    };
        }
    }

    /** Synchronizes the visual for AssassinCombat. */
    public void syncVisual(int action, int age, double heading, int critTicks, double critHeading) {
        Move next = MOVES[action];
        double previousHeading = this.heading;
        this.heading = heading;
        if (next != move && (next == Move.SLIPSTEP || next == Move.NIGHTFALL)) dashCue(false);
        if (move == Move.SLIPSTEP && next == Move.IDLE) {
            this.heading = previousHeading;
            dashCue(true);
            this.heading = heading;
        }
        if (next == Move.NIGHTFALL && age >= DASH_TICKS && (move != next || this.age < DASH_TICKS))
            dashCue(true);
        if (next != Move.IDLE && next != Move.SLIPSTEP) {
            int cue = next == Move.NIGHTFALL ? DASH_TICKS : 5;
            if (age >= cue && (move != next || this.age < cue))
                slashParticles(next == Move.OPEN_VEIN ? CORAL : Palette.PLASTER, 6);
        }
        move = next;
        this.age = age;
        this.critTicks = critTicks;
        this.critHeading = critHeading;
    }

    /** Draws the afterimages for AssassinCombat. */
    void drawAfterimages(Graphics2D g, BufferedImage image, int x, int y) {
        if (!((move == Move.SLIPSTEP || move == Move.NIGHTFALL) && age > 1 && age <= DASH_TICKS))
            return;
        var ghost = player.getTintVersion(image, AFTERIMAGE);
        Graphics2D trail = (Graphics2D) g.create();
        for (int i = 2; i >= 1; i--) {
            double offset = Math.min(age * 5, i * 13);
            trail.setComposite(AlphaComposite.SrcOver.derive(i == 2 ? 0.18f : 0.30f));
            trail.drawImage(
                    ghost,
                    x - (int) Math.round(Math.cos(heading) * offset),
                    y - (int) Math.round(Math.sin(heading) * offset),
                    null);
        }
        trail.dispose();
    }

    /** Draws the slashes for AssassinCombat. */
    void drawSlashes(Graphics2D g) {
        switch (move) {
            case CROSSCUT -> {
                player.drawSlash(g, heading - 0.24, 58, 49, age - 5, false);
                finishingSlash(g, 9, 60, 52);
            }
            case OPEN_VEIN -> player.drawSlash(g, heading, 53, 45, age - 6, false);
            case NIGHTFALL -> {
                player.drawSlash(g, heading - 0.18, 59, 48, age - DASH_TICKS, false);
                finishingSlash(g, 14, 60, 50);
                player.drawSlash(g, heading - 0.18, 61, 51, age - 21, false);
                finishingSlash(g, 25, 65, 54);
            }
            default -> {}
        }
    }

    /** Performs the finishing slash operation for AssassinCombat. */
    private void finishingSlash(Graphics2D g, int hitAge, float reach, float width) {
        boolean critical = critTicks > 0 && age >= hitAge && age - hitAge <= 3;
        player.drawSlash(
                g,
                heading + 0.20,
                reach * (critical ? 1.18f : 1),
                width * (critical ? 1.16f : 1),
                age - hitAge,
                true);
    }

    /** Resolves the heading for result used by AssassinCombat. */
    private static double headingFor(String direction) {
        return switch (direction) {
            case "up" -> -Math.PI / 2;
            case "down" -> Math.PI / 2;
            case "left" -> Math.PI;
            default -> 0;
        };
    }

    /** Resolves the direction result used by AssassinCombat. */
    private String direction() {
        return RangerEffect.direction(heading);
    }

    /** Resolves the power result used by AssassinCombat. */
    private int power(float multiplier) {
        return Math.max(1, Math.round(player.getEffectiveAttack() * multiplier));
    }

    /** Resolves the x result used by AssassinCombat. */
    private int x() {
        return centerX(player);
    }

    /** Resolves the y result used by AssassinCombat. */
    private int y() {
        return centerY(player);
    }

    /** Resolves the center x result used by AssassinCombat. */
    private static int centerX(Entity e) {
        return e.worldX + (int) e.solidArea.getCenterX();
    }

    /** Resolves the center y result used by AssassinCombat. */
    private static int centerY(Entity e) {
        return e.worldY + (int) e.solidArea.getCenterY();
    }

    /** Resolves the index result used by AssassinCombat. */
    private int index(Monster monster) {
        if (monster != null)
            for (int i = 0; i < player.gp.monster.length; i++)
                if (player.gp.monster[i] == monster) return i;
        return -1;
    }

    /** Evaluates the reachable condition for AssassinCombat. */
    private boolean reachable(Monster m) {
        return m != null
                && m.alive
                && !m.dying
                && index(m) >= 0
                && within(m, player.gp.tileSize * 1.62);
    }

    /** Evaluates the within condition for AssassinCombat. */
    private boolean within(Monster m, double reach) {
        double dx = Math.max(0, Math.abs(centerX(m) - x()) - m.solidArea.width / 2.0);
        double dy = Math.max(0, Math.abs(centerY(m) - y()) - m.solidArea.height / 2.0);
        return Math.hypot(dx, dy) <= reach
                && RangerEffect.clearLine(player.gp, x(), y(), centerX(m), centerY(m));
    }

    /** Resolves the nightfall target result used by AssassinCombat. */
    private Monster nightfallTarget(double angle) {
        return target(angle, player.gp.tileSize * 2.7, 0.70);
    }

    /** Resolves the forward distance result used by AssassinCombat. */
    private double forwardDistance(Monster m, double angle) {
        return (centerX(m) - x()) * Math.cos(angle) + (centerY(m) - y()) * Math.sin(angle);
    }

    /** Resolves the target result used by AssassinCombat. */
    private Monster target(double angle, double reach, double cone) {
        Monster best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Monster m : player.gp.monster) {
            if (m == null || !m.alive || m.dying) continue;
            double dx = centerX(m) - x(), dy = centerY(m) - y(), distance = Math.hypot(dx, dy);
            if (distance >= bestDistance
                    || forwardDistance(m, angle) < distance * cone
                    || !within(m, reach)) continue;
            best = m;
            bestDistance = distance;
        }
        return best;
    }
}
