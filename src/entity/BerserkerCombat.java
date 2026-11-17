package entity;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Committed greatsword attacks. Guests replay poses and effects while the host resolves hits and
 * rush movement.
 */
public final class BerserkerCombat {
    private enum Move {
        IDLE(0, 0),
        CLEAVE(6, 18),
        RUSH(12, 18),
        SUNDER(11, 25),
        WHIRLWIND(6, 33),
        WORLDBREAKER(20, 43);
        final int impact, end;

        /** Creates a Move instance and initializes the state required by its collaborators. */
        Move(int impact, int end) {
            this.impact = impact;
            this.end = end;
        }
    }

    static final String SUNDERED = "berserkerSunder";
    private static final Move[] MOVES = Move.values();
    private static final EnumMap<Move, BufferedImage> ICONS = new EnumMap<>(Move.class);
    private static final Color IVORY = new Color(0xFFF1D7),
            RUST = new Color(0xD17A58),
            GOLD = new Color(0xF6BE66);
    private static final Color AFTERIMAGE = new Color(0x995C55), INK = new Color(0x70443A);
    private static final Stroke WARNING_EDGE =
            new BasicStroke(
                    3.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[] {9, 5}, 0);
    private static final Stroke WARNING_LINE =
            new BasicStroke(
                    1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[] {9, 5}, 0);
    private static final int WHIRL_SECOND = 21, WAVE_TICKS = 14;
    private final Player player;
    private final Set<Monster> previousHits = new HashSet<>();
    private Move move = Move.IDLE;
    private int age, dashDistance, castSerial, lastCueAge;
    private double heading;
    private boolean dashStopped, waveStopped;

    /**
     * Creates a BerserkerCombat instance and initializes the state required by its collaborators.
     */
    BerserkerCombat(Player player) {
        this.player = player;
    }

    /** Performs the setup operation for BerserkerCombat. */
    void setup(Skill[] slots) {
        slots[0] =
                skill(Move.CLEAVE, "Cleave", 12, 5, "Sweep a wide arc in the direction you face.");
        slots[1] =
                skill(
                        Move.RUSH,
                        "Shoulder Rush",
                        0,
                        8,
                        "Rush forward. The first enemy stops you and takes a staggering hit.");
        slots[2] =
                skill(
                        Move.SUNDER,
                        "Sunder",
                        20,
                        8,
                        "Land a heavy overhead cut and reduce enemy armor for 4s.");
        slots[3] =
                skill(
                        Move.WHIRLWIND,
                        "Whirlwind",
                        25,
                        10,
                        "Move at half speed and sweep around you twice.");
        slots[4] =
                skill(
                        Move.WORLDBREAKER,
                        "Worldbreaker",
                        45,
                        15,
                        "Brace and slam. A forward shockwave is strongest up close.");
    }

    /** Resolves the skill result used by BerserkerCombat. */
    private Skill skill(Move action, String name, int rage, int seconds, String description) {
        var skill =
                new Skill(
                        name,
                        rage,
                        seconds * 60,
                        () -> {
                            start(action);
                            player.announceSkill(name);
                        },
                        icon(action));
        skill.description = description;
        return skill;
    }

    /** Resolves the icon result used by BerserkerCombat. */
    private static synchronized BufferedImage icon(Move action) {
        return ICONS.computeIfAbsent(
                action,
                key -> {
                    String path =
                            switch (key) {
                                case RUSH -> "shoulder_rush";
                                default -> key.name().toLowerCase(java.util.Locale.ROOT);
                            };
                    try (var in =
                            BerserkerCombat.class.getResourceAsStream(
                                    "/skill_icon/berserker/" + path + ".png")) {
                        if (in == null) throw new IOException("Missing Berserker icon " + path);
                        return ImageIO.read(in);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    /** Reports whether this component can use for BerserkerCombat. */
    boolean canUse() {
        return !active()
                && !player.attacking
                && player.hasGreatsword()
                && !player.knockbackActive
                && !player.currentInput.gameplaySuppressed
                && !player.currentInput.ePressed;
    }

    /** Performs the start operation for BerserkerCombat. */
    private void start(Move action) {
        cancel();
        heading = headingFor(player.direction);
        move = action;
        castSerial++;
        player.beginBerserkerSkill();
        if (move == Move.RUSH) rushDust();
    }

    /** Evaluates the active condition for BerserkerCombat. */
    boolean active() {
        return move != Move.IDLE;
    }

    /** Reports whether this component can move for BerserkerCombat. */
    boolean canMove() {
        return move == Move.WHIRLWIND;
    }

    /** Resolves the action result used by BerserkerCombat. */
    public int action() {
        return move.ordinal();
    }

    /** Returns the current age value. */
    public int age() {
        return age;
    }

    /** Returns the current cast serial value. */
    public int serial() {
        return castSerial;
    }

    /** Returns the current heading value. */
    public double heading() {
        return heading;
    }

    /** Returns the current wave stopped value. */
    public boolean waveStopped() {
        return waveStopped;
    }

    /** Cancels the active operation and clears its temporary state. */
    void cancel() {
        move = Move.IDLE;
        age = 0;
        dashDistance = 0;
        dashStopped = false;
        waveStopped = false;
        previousHits.clear();
    }

    /** Performs the basic sweep operation for BerserkerCombat. */
    void basicSweep() {
        int hits = strike(1.0f, 1.83, 0.60, false, false);
        if (hits > 0) player.gainBerserkerRage(hits == 1 ? 10 : 15);
    }

    /** Advances this component for one simulation tick. */
    void update() {
        if (!active()) return;
        if (!player.hasGreatsword()
                || player.knockbackActive
                || player.currentInput.gameplaySuppressed) {
            cancel();
            return;
        }
        if (!player.canResolveWorldActions()) {
            advanceReplica();
            return;
        }
        if (player.gp.hitStopCounter > 0) return;
        age++;
        switch (move) {
            case CLEAVE -> {
                if (age == move.impact) {
                    strike(1.35f, 1.95, 0.40, false, false);
                    sweepCue(IVORY, 1);
                }
            }
            case RUSH -> {
                if (age <= move.impact && !dashStopped) rush();
            }
            case SUNDER -> {
                if (age == move.impact) {
                    strike(1.55f, 1.72, 0.90, true, false);
                    sweepCue(RUST, 1);
                }
            }
            case WHIRLWIND -> {
                if (age == move.impact || age == WHIRL_SECOND) {
                    strike(0.82f, 1.48, -1, false, true);
                    sweepCue(IVORY, age == WHIRL_SECOND ? -1 : 1);
                }
            }
            case WORLDBREAKER -> {
                if (age >= move.impact && age < move.impact + WAVE_TICKS && !waveStopped)
                    shockwave();
                if (age == move.impact) {
                    player.gp.startScreenShake(14, 14);
                    sweepCue(GOLD, 1);
                }
            }
            case IDLE -> {}
        }
        if (age >= move.end) cancel();
    }

    /** Resolves the strike result used by BerserkerCombat. */
    private int strike(float scale, double tiles, double arc, boolean sunder, boolean spin) {
        double facing = active() ? heading : headingFor(player.direction);
        double cos = Math.cos(facing), sin = Math.sin(facing);
        int landed = 0;
        for (int i = 0; i < player.gp.monster.length; i++) {
            Monster target = player.gp.monster[i];
            if (!live(target)) continue;
            double dx = targetX(target) - x(), dy = targetY(target) - y();
            double distance = Math.hypot(dx, dy);
            if (distance > tiles * player.gp.tileSize
                    || dx * cos + dy * sin < arc * distance
                    || !RangerEffect.clearLine(
                            player.gp, x(), y(), targetX(target), targetY(target))) continue;
            double angle = spin ? Math.atan2(dy, dx) : facing;
            boolean second = spin && age == WHIRL_SECOND && previousHits.contains(target);
            if (!player.berserkerDamage(
                    i, power(scale), RangerEffect.direction(angle), false, second)) continue;
            landed++;
            if (spin && age == move.impact) previousHits.add(target);
            if (sunder && !target.dying) {
                int penalty = Math.max(2, (target.defense + 3) / 4);
                target.statusEffects.removeIf(effect -> effect.type.equals(SUNDERED));
                target.addStatusEffect(new StatusEffect(SUNDERED, 240, penalty, 0));
                Particle.burst(target, Particle.Style.SQUARE, RUST, 7, 6, 20, 4.8f);
            }
        }
        if (!spin && !sunder && move == Move.IDLE) {
            for (int index :
                    player.gp.collisionChecker.checkEntityMultiple(
                            player.meleeAttackBounds(), player.gp.interactable))
                player.objectInteract(index);
        }
        return landed;
    }

    /** Performs the rush operation for BerserkerCombat. */
    private void rush() {
        int goal = Math.round(player.gp.tileSize * 1.65f * age / move.impact);
        int stepX = (int) Math.round(Math.cos(heading)),
                stepY = (int) Math.round(Math.sin(heading));
        Rectangle body = player.solidArea;
        while (dashDistance < goal) {
            int bx = player.worldX + body.x + stepX, by = player.worldY + body.y + stepY;
            int target = rushTarget(bx, by, body.width, body.height);
            if (target >= 0 || RangerEffect.blocksDash(player, bx, by, body.width, body.height)) {
                if (target >= 0
                        && !RangerEffect.blocked(player.gp, bx, by, body.width, body.height)
                        && player.berserkerDamage(
                                target, power(0.70f), player.direction, false, false)) {
                    Monster hit = player.gp.monster[target];
                    hit.stagger(24);
                    Particle.spray(
                            hit, Particle.Style.SQUARE, GOLD, 6, 6, 18, 4.9f, heading, 0.72, 0, -8);
                }
                dashStopped = true;
                break;
            }
            player.worldX += stepX;
            player.worldY += stepY;
            dashDistance++;
        }
        if (dashStopped || age == move.impact || age % 4 == 0) rushDust();
        if (dashStopped) age = move.impact;
    }

    /** Resolves the rush target result used by BerserkerCombat. */
    private int rushTarget(int x, int y, int w, int h) {
        for (int i = 0; i < player.gp.monster.length; i++) {
            Monster m = player.gp.monster[i];
            if (live(m) && m.solidArea.intersects(x - m.worldX, y - m.worldY, w, h)) return i;
        }
        return -1;
    }

    /** Performs the shockwave operation for BerserkerCombat. */
    private void shockwave() {
        double cos = Math.cos(heading), sin = Math.sin(heading);
        float reach = waveReach();
        float clear = RangerEffect.clearDistance(player.gp, x(), y(), heading, reach);
        waveStopped = clear < reach - 0.001;
        for (int i = 0; i < player.gp.monster.length; i++) {
            Monster target = player.gp.monster[i];
            if (!live(target) || previousHits.contains(target)) continue;
            int tx = targetX(target), ty = targetY(target), dx = tx - x(), dy = ty - y();
            double front = dx * cos + dy * sin, lateral = Math.abs(dx * sin - dy * cos);
            if (front < 0
                    || front > clear
                    || lateral > 18 + front * 0.32
                    || !RangerEffect.clearLine(player.gp, x(), y(), tx, ty)) continue;
            boolean close = front <= player.gp.tileSize * 1.25;
            previousHits.add(target);
            player.berserkerDamage(i, power(close ? 2.45f : 1.60f), player.direction, close, false);
        }
        if (age % 3 == 2) {
            Particle.spray(
                    player,
                    Particle.Style.SQUARE,
                    GOLD,
                    7,
                    7,
                    20,
                    6.5f,
                    heading,
                    0.9,
                    reach * (float) cos,
                    reach * (float) sin - 5);
        }
    }

    /** Resolves the wave reach result used by BerserkerCombat. */
    private float waveReach() {
        int phase = Math.max(0, Math.min(WAVE_TICKS - 1, age - Move.WORLDBREAKER.impact));
        return player.gp.tileSize * (0.95f + 1.95f * phase / (WAVE_TICKS - 1));
    }

    /** Performs the rush dust operation for BerserkerCombat. */
    private void rushDust() {
        Particle.spray(
                player,
                Particle.Style.DUST,
                Color.WHITE,
                3,
                7,
                17,
                2.8f,
                heading + Math.PI,
                0.62,
                0,
                5);
        Particle.spray(
                player, Particle.Style.SQUARE, RUST, 4, 5, 15, 3.3f, heading + Math.PI, 0.62, 0, 4);
    }

    /** Performs the sweep cue operation for BerserkerCombat. */
    private void sweepCue(Color color, int side) {
        double angle = heading + (side < 0 ? Math.PI : 0.22);
        Particle.spray(
                player,
                Particle.Style.SQUARE,
                color,
                8,
                6,
                17,
                4.8f,
                angle,
                1.0,
                (float) Math.cos(angle) * 21,
                (float) Math.sin(angle) * 21 - 7);
    }

    /** Evaluates the live condition for BerserkerCombat. */
    private static boolean live(Monster target) {
        return target != null && target.alive && !target.dying && target.HP > 0;
    }

    /** Resolves the target x result used by BerserkerCombat. */
    private static int targetX(Monster target) {
        return target.worldX + (int) target.solidArea.getCenterX();
    }

    /** Resolves the target y result used by BerserkerCombat. */
    private static int targetY(Monster target) {
        return target.worldY + (int) target.solidArea.getCenterY();
    }

    /** Resolves the x result used by BerserkerCombat. */
    private int x() {
        return player.worldX + (int) player.solidArea.getCenterX();
    }

    /** Resolves the y result used by BerserkerCombat. */
    private int y() {
        return player.worldY + (int) player.solidArea.getCenterY();
    }

    /** Resolves the power result used by BerserkerCombat. */
    private int power(float multiplier) {
        return Math.max(1, Math.round(multiplier * player.getEffectiveAttack()));
    }

    /** Resolves the heading for result used by BerserkerCombat. */
    private static double headingFor(String direction) {
        return switch (direction) {
            case "up" -> -Math.PI / 2;
            case "down" -> Math.PI / 2;
            case "left" -> Math.PI;
            default -> 0;
        };
    }

    /** Synchronizes the visual for BerserkerCombat. */
    public void syncVisual(int action, int ticks, int serial, double facing, boolean stopped) {
        Move next = action >= 0 && action < MOVES.length ? MOVES[action] : Move.IDLE;
        boolean fresh = next != move || serial != castSerial;
        if (fresh) {
            lastCueAge = -1;
            if (next != Move.IDLE) player.attacking = false;
        }
        move = next;
        age = ticks;
        castSerial = serial;
        heading = facing;
        waveStopped = stopped;
        if (move == Move.RUSH && fresh) rushDust();
        emitReplicaCues();
        pose();
    }

    /** Performs the advance replica operation for BerserkerCombat. */
    void advanceReplica() {
        if (!active() || player.canResolveWorldActions()) return;
        if (player.gp.hitStopCounter <= 0) age = Math.min(move.end, age + 1);
        emitReplicaCues();
        pose();
    }

    /** Emits the replica cues for BerserkerCombat. */
    private void emitReplicaCues() {
        if (lastCueAge < move.impact && age >= move.impact) replicaImpact();
        if (move == Move.WHIRLWIND && lastCueAge < WHIRL_SECOND && age >= WHIRL_SECOND)
            sweepCue(IVORY, -1);
        lastCueAge = Math.max(lastCueAge, age);
    }

    /** Performs the replica impact operation for BerserkerCombat. */
    private void replicaImpact() {
        switch (move) {
            case RUSH -> rushDust();
            case CLEAVE, WHIRLWIND -> sweepCue(IVORY, 1);
            case SUNDER -> sweepCue(RUST, 1);
            case WORLDBREAKER -> sweepCue(GOLD, 1);
            case IDLE -> {}
        }
    }

    /** Performs the pose operation for BerserkerCombat. */
    void pose() {
        if (!active()) return;
        if (move != Move.WHIRLWIND) player.direction = RangerEffect.direction(heading);
        if (move == Move.RUSH) {
            boolean traveling = age < move.impact;
            player.animState = traveling ? "run" : "idle";
            player.spriteNum = traveling ? 1 + age / 2 % 6 : 1;
        } else {
            int strike = move == Move.WHIRLWIND && age >= WHIRL_SECOND ? WHIRL_SECOND : move.impact;
            int beat = age - strike;
            if (beat < 0 && (move == Move.SUNDER || move == Move.WORLDBREAKER)) {
                player.animState = "greatsword_raise";
                player.spriteNum =
                        Math.min(
                                CharacterSprites.GREATSWORD_RAISE_FRAMES,
                                1 + Math.max(0, age - 1) / 2);
            } else if (beat >= 0) {
                int end = move == Move.WHIRLWIND && strike == move.impact ? WHIRL_SECOND : move.end;
                int poses = CharacterSprites.SWORD_ATTACK_FRAMES - CharacterSprites.SWORD_HIT_POSE;
                player.animState = "greatsword_impact";
                player.spriteNum =
                        Math.min(poses, 1 + beat * (poses - 1) / Math.min(13, end - strike - 1));
            } else {
                player.animState = "attack";
                player.spriteNum = 1 + windupPose() / 2;
            }
        }
    }

    /** Resolves the windup pose result used by BerserkerCombat. */
    int windupPose() {
        return !active()
                ? 0
                : Math.min(
                        CharacterSprites.SWORD_HIT_POSE - 1,
                        age * CharacterSprites.SWORD_HIT_POSE / move.impact);
    }

    /** Draws the afterimage for BerserkerCombat. */
    void drawAfterimage(Graphics2D graphics, BufferedImage body, int drawX, int drawY) {
        if (move != Move.RUSH || age < 2 || age >= move.impact) return;
        var ghost = player.getTintVersion(body, AFTERIMAGE);
        var g = (Graphics2D) graphics.create();
        for (int i = 2; i >= 1; i--) {
            int offset = Math.min(dashDistance > 0 ? dashDistance : age * 5, i * 13);
            g.setComposite(AlphaComposite.SrcOver.derive(i == 2 ? 0.16f : 0.30f));
            g.drawImage(
                    ghost,
                    drawX - Math.round((float) Math.cos(heading) * offset),
                    drawY - Math.round((float) Math.sin(heading) * offset),
                    null);
        }
        g.dispose();
    }

    /** Draws the ground for BerserkerCombat. */
    void drawGround(Graphics2D graphics) {
        if (move != Move.SUNDER && move != Move.WORLDBREAKER && move != Move.WHIRLWIND) return;
        var g = effectCanvas(graphics);
        int phase = age - move.impact;
        if (move == Move.WHIRLWIND) {
            boolean second = age >= WHIRL_SECOND;
            drawSpin(g, second ? age - WHIRL_SECOND : phase, second, true);
        } else {
            boolean heavy = move == Move.WORLDBREAKER;
            float forward = heavy ? 44 : 46;
            g.translate(Math.cos(heading) * forward, Math.sin(heading) * forward + 6);
            if (phase < 0) warningRing(g, heavy ? 35 : 27, heavy ? 14 : 11, phase);
            else if (phase < 14) {
                g.setComposite(AlphaComposite.SrcOver.derive((1 - phase / 14f) * 0.92f));
                drawCracks(g, heavy ? 28 : 22, heavy ? GOLD : RUST);
            }
        }
        g.dispose();
    }

    /** Draws the slash for BerserkerCombat. */
    void drawSlash(Graphics2D graphics) {
        if (move == Move.CLEAVE)
            player.drawSlash(graphics, heading, 88, 98, age - move.impact, false);
    }

    /** Renders this component into the supplied Java2D graphics context. */
    void draw(Graphics2D graphics) {
        if (player.isDead || move != Move.WHIRLWIND) return;
        var g = effectCanvas(graphics);
        boolean second = age >= WHIRL_SECOND;
        drawSpin(g, age - (second ? WHIRL_SECOND : move.impact), second, false);
        g.dispose();
    }

    /** Draws the impact for BerserkerCombat. */
    public void drawImpact(Graphics2D graphics) {
        if (player.isDead || move != Move.SUNDER && move != Move.WORLDBREAKER) return;
        int phase = age - move.impact;
        if (phase < 0 || phase >= WAVE_TICKS + 5) return;
        var g = effectCanvas(graphics);
        if (move == Move.WORLDBREAKER) drawWave(g, phase);
        else if (phase < 10) impactAt(g, 46, 23, 34, 1 - phase / 10f);
        g.dispose();
    }

    /** Resolves the effect canvas result used by BerserkerCombat. */
    private Graphics2D effectCanvas(Graphics2D graphics) {
        Player camera = player.gp.localPlayer();
        var g = (Graphics2D) graphics.create();
        g.translate(x() - camera.worldX + camera.screenX, y() - camera.worldY + camera.screenY);
        return g;
    }

    /** Draws the spin for BerserkerCombat. */
    private static void drawSpin(Graphics2D g, int phase, boolean second, boolean behind) {
        if (phase < 0 || phase >= 11) return;
        float fade = (1 - phase / 11f) * (behind ? 0.62f : 1);
        g.setComposite(AlphaComposite.SrcOver.derive(fade));
        g.translate(0, 6);
        double from = behind ? -174 + phase * 1.6 : 8 + phase * 1.5;
        double to = behind ? -11 + phase * 0.8 : 175 - phase * 1.4;
        float radius = second ? 68 : 61;
        g.setColor(Color.WHITE);
        g.fill(ringRibbon(radius, behind ? 15 : 21, behind ? 8 : 11, behind ? 6 : 8, from, to));
        g.setComposite(AlphaComposite.SrcOver.derive(fade * 0.48f));
        g.fill(ringRibbon(radius - 10, behind ? 11 : 15, 2, 2, from + 22, to - 8));
    }

    /** Resolves the ring ribbon result used by BerserkerCombat. */
    private static Shape ringRibbon(
            float rx, float ry, float wx, float wy, double from, double to) {
        var ribbon = new Path2D.Float();
        int steps = 20;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(from + (to - from) * i / steps);
            double x = rx * Math.cos(angle), y = ry * Math.sin(angle);
            if (i == 0) ribbon.moveTo(x, y);
            else ribbon.lineTo(x, y);
        }
        for (int i = steps; i >= 0; i--) {
            double angle = Math.toRadians(from + (to - from) * i / steps);
            double taper = Math.sin(Math.PI * i / steps);
            ribbon.lineTo((rx - wx * taper) * Math.cos(angle), (ry - wy * taper) * Math.sin(angle));
        }
        ribbon.closePath();
        return ribbon;
    }

    /** Performs the warning ring operation for BerserkerCombat. */
    private static void warningRing(Graphics2D g, float rx, float ry, int phase) {
        var ring = new Ellipse2D.Float(-rx, -ry, 2 * rx, 2 * ry);
        float pulse = 1 + phase / 24f;
        g.setComposite(AlphaComposite.SrcOver.derive(Math.clamp(pulse, 0.35f, 1f)));
        g.setColor(new Color(129, 72, 44, 58));
        g.fill(ring);
        g.setColor(INK);
        g.setStroke(WARNING_EDGE);
        g.draw(ring);
        g.setColor(GOLD);
        g.setStroke(WARNING_LINE);
        g.draw(ring);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int side : new int[] {-1, 1}) {
            g.draw(new Line2D.Float(side * (rx + 3), -ry * 0.56f, side * (rx + 6), -ry * 0.83f));
            g.draw(new Line2D.Float(side * (rx - 7), -ry - 4, side * (rx - 8), -ry - 8));
        }
    }

    /** Draws the wave for BerserkerCombat. */
    private void drawWave(Graphics2D g, int phase) {
        if (phase < 0 || phase >= WAVE_TICKS + 5) return;
        if (phase < 11) impactAt(g, 44, 34, 54, 1 - phase / 11f);
        if (phase < 4 || waveStopped) return;
        float fade = Math.min(1, (WAVE_TICKS + 5 - phase) / 7f);
        float front = waveReach(), width = 20 + front * 0.13f;
        impactAt(g, front, width, 24, fade * 0.88f);
    }

    /** Performs the impact at operation for BerserkerCombat. */
    private void impactAt(
            Graphics2D graphics, float forward, float width, float height, float alpha) {
        var g = (Graphics2D) graphics.create();
        g.translate(Math.cos(heading) * forward, Math.sin(heading) * forward + 5);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        drawCracks(g, width, GOLD);
        var burst = new Path2D.Float();
        burst.moveTo(-width, 3);
        burst.lineTo(-width * 0.73f, -height * 0.45f);
        burst.lineTo(-width * 0.36f, -height * 0.19f);
        burst.lineTo(-width * 0.21f, -height * 0.69f);
        burst.lineTo(-width * 0.05f, -height * 0.36f);
        burst.lineTo(width * 0.12f, -height);
        burst.lineTo(width * 0.24f, -height * 0.38f);
        burst.lineTo(width * 0.68f, -height * 0.59f);
        burst.lineTo(width * 0.49f, -height * 0.21f);
        burst.lineTo(width, 1);
        burst.quadTo(0, 11, -width, 3);
        burst.closePath();
        g.setPaint(new GradientPaint(0, 6, RUST, 0, -height * 0.7f, GOLD));
        g.fill(burst);
        g.scale(0.67, 0.75);
        g.setPaint(new GradientPaint(0, 7, GOLD, 0, -height * 0.65f, IVORY));
        g.fill(burst);
        g.dispose();
    }

    /** Draws the cracks for BerserkerCombat. */
    private static void drawCracks(Graphics2D g, float width, Color edge) {
        var cracks = new Path2D.Float();
        for (int side : new int[] {-1, 1}) {
            cracks.moveTo(0, 1);
            cracks.lineTo(side * width * 0.38f, 5);
            cracks.lineTo(side * width * 0.59f, 1);
            cracks.lineTo(side * width, 4);
            cracks.moveTo(side * width * 0.38f, 5);
            cracks.lineTo(side * width * 0.5f, 9);
            cracks.lineTo(side * width * 0.73f, 11);
        }
        cracks.moveTo(0, 1);
        cracks.lineTo(3, 8);
        cracks.lineTo(-4, 12);
        g.setStroke(new BasicStroke(3.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(INK);
        g.draw(cracks);
        g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(edge);
        g.draw(cracks);
    }
}
