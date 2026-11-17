package entity;

import art.PaladinGear;

import net.PlayerState;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;

import javax.imageio.ImageIO;

/**
 * Directional Guard, Resolve spenders and party defense. Only the host changes health, aggro and
 * projectiles.
 */
public final class PaladinCombat {
    private enum Move {
        IDLE(0, 0),
        BASH(5, 16),
        CHALLENGE(1, 16),
        INTERPOSE(1, 13),
        OATHSTRIKE(9, 23),
        BASTION(7, 19);
        final int impact, end;

        /** Creates a Move instance and initializes the state required by its collaborators. */
        Move(int impact, int end) {
            this.impact = impact;
            this.end = end;
        }
    }

    private static final Move[] MOVES = Move.values();
    private static final EnumMap<Move, BufferedImage> ICONS = new EnumMap<>(Move.class);
    private static final Color BLUE = new Color(0xB4DBE9),
            GOLD = new Color(0xF3CA85),
            IVORY = new Color(0xFFF1D8);
    private static final Color DARK = new Color(0x454C5F);
    private static final int LINK_DURATION = 240,
            CHALLENGE_DURATION = 240,
            WALL_DURATION = 240,
            WALL_CHARGES = 3;
    private static final float LINK_RANGE = 2.5f;
    private final Player player;

    private Move move = Move.IDLE;
    private Player linked, stepTarget;
    private int age, guardPose, blockTicks, linkTicks, replicaLink = -1;
    private int wallX, wallY, wallTicks, wallCharges, wallFlash, wallPanel;
    private double heading, wallHeading;
    private boolean guarding;

    /** Creates a PaladinCombat instance and initializes the state required by its collaborators. */
    PaladinCombat(Player player) {
        this.player = player;
    }

    /** Performs the setup operation for PaladinCombat. */
    void setup(Skill[] slots) {
        slots[0] =
                skill(
                        Move.BASH,
                        "Shield Bash",
                        0,
                        5,
                        "Bash forward. Stagger an ordinary enemy and reset your guard.");
        slots[1] =
                skill(
                        Move.CHALLENGE,
                        "Challenge",
                        0,
                        9,
                        "Draw nearby enemies to you for 4s. Bosses consider you when they next pick"
                                + " a target.");
        slots[2] =
                skill(
                        Move.INTERPOSE,
                        "Interpose",
                        2,
                        9,
                        "Step toward an ally under your cursor and intercept their next hit.");
        slots[3] =
                skill(
                        Move.OATHSTRIKE,
                        "Oathstrike",
                        5,
                        6,
                        "Deliver a heavy counter. Heal yourself if it lands.");
        slots[4] =
                skill(
                        Move.BASTION,
                        "Last Bastion",
                        3,
                        18,
                        "Raise a shield wall for 4s that catches up to three hostile projectiles.");
    }

    /** Resolves the skill result used by PaladinCombat. */
    private Skill skill(Move action, String name, int cost, int seconds, String description) {
        var skill =
                new Skill(
                        name,
                        cost,
                        seconds * 60,
                        () -> {
                            start(action);
                            player.announceSkill(name);
                        },
                        icon(action));
        skill.description = description;
        return skill;
    }

    /** Resolves the icon result used by PaladinCombat. */
    private static synchronized BufferedImage icon(Move action) {
        return ICONS.computeIfAbsent(
                action,
                move -> {
                    String file =
                            switch (move) {
                                case BASH -> "shield_bash";
                                case BASTION -> "last_bastion";
                                default -> move.name().toLowerCase(java.util.Locale.ROOT);
                            };
                    try (var in =
                            PaladinCombat.class.getResourceAsStream(
                                    "/skill_icon/paladin/" + file + ".png")) {
                        if (in == null) throw new IOException("Missing Paladin icon " + file);
                        return ImageIO.read(in);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    /** Updates the guard for PaladinCombat. */
    void setGuard(boolean held) {
        guarding =
                held
                        && player.hasShield()
                        && !player.attacking
                        && !active()
                        && !player.knockbackActive;
        guardPose = Math.clamp(guardPose + (guarding ? 1 : -1), 0, 6);
    }

    /** Reports whether this component can use for PaladinCombat. */
    boolean canUse(int slot) {
        return !active()
                && !player.attacking
                && !player.knockbackActive
                && !player.currentInput.gameplaySuppressed
                && !player.currentInput.ePressed
                && (!guarding || slot == 0)
                && (slot == 3 ? player.hasPaladinSword() : player.hasShield())
                && (slot != 4
                        || wallDistance(headingFor(player.direction))
                                >= player.gp.tileSize * 0.45f);
    }

    /** Performs the start operation for PaladinCombat. */
    private void start(Move action) {
        move = action;
        age = 0;
        guarding = false;
        guardPose = 0;
        heading = headingFor(player.direction);
        if (action == Move.INTERPOSE) {
            Player target = hoveredAlly();
            linked = target;
            linkTicks = LINK_DURATION;
            stepTarget = target == player ? null : target;
            if (stepTarget != null) {
                heading = Math.atan2(y(target) - y(player), x(target) - x(player));
                player.direction = RangerEffect.direction(heading);
            }
        }
    }

    /** Evaluates the active condition for PaladinCombat. */
    boolean active() {
        return move != Move.IDLE;
    }

    /** Returns the current guarding value. */
    public boolean guarding() {
        return guarding;
    }

    /** Resolves the guard frame result used by PaladinCombat. */
    int guardFrame() {
        if (move == Move.BASH || move == Move.BASTION) return 2;
        return (guardPose + 2) / 3;
    }

    /** Cancels the active operation and clears its temporary state. */
    void cancel() {
        move = Move.IDLE;
        age = 0;
        stepTarget = null;
        guarding = false;
        guardPose = 0;
    }

    /** Restores this component to its initial runtime state. */
    void reset() {
        cancel();
        linked = null;
        replicaLink = -1;
        linkTicks = 0;
        blockTicks = 0;
        wallTicks = 0;
        wallCharges = 0;
        wallFlash = 0;
    }

    /** Performs the tick timers operation for PaladinCombat. */
    void tickTimers() {
        if (!player.canResolveWorldActions()) return;
        if (blockTicks > 0) blockTicks--;
        if (wallFlash > 0) wallFlash--;
        if (wallTicks > 0 && --wallTicks == 0) wallCharges = 0;
        if (linkTicks > 0 && (--linkTicks == 0 || !validLink(linked))) {
            linked = null;
            linkTicks = 0;
        }
    }

    /** Advances this component for one simulation tick. */
    void update() {
        if (!active()) return;
        if (player.knockbackActive || player.currentInput.gameplaySuppressed) {
            cancel();
            return;
        }
        if (player.gp.hitStopCounter > 0) return;
        age++;
        if (player.canResolveWorldActions()) {
            if (move == Move.INTERPOSE && age <= 8 && stepTarget != null) step();
            if (age == move.impact) {
                switch (move) {
                    case BASH -> strike(false);
                    case CHALLENGE -> challenge();
                    case OATHSTRIKE -> strike(true);
                    case BASTION -> buildWall();
                    default -> {}
                }
            }
        }
        if (age >= move.end) cancel();
    }

    /** Performs the advance replica operation for PaladinCombat. */
    void advanceReplica() {
        if (player.canResolveWorldActions()) return;
        if (blockTicks > 0) blockTicks--;
        if (wallFlash > 0) wallFlash--;
        if (active() && player.gp.hitStopCounter <= 0) age = Math.min(move.end, age + 1);
        pose();
    }

    /** Resolves the sword pose result used by PaladinCombat. */
    int swordPose() {
        if (move != Move.OATHSTRIKE) return 0;
        int hit = CharacterSprites.SWORD_HIT_POSE, last = CharacterSprites.SWORD_ATTACK_FRAMES - 1;
        if (age < move.impact) return age * hit / move.impact;
        return Math.min(
                last, hit + (age - move.impact) * (last - hit) / (move.end - move.impact - 1));
    }

    /** Performs the pose operation for PaladinCombat. */
    void pose() {
        if (!active()) return;
        player.direction = RangerEffect.direction(heading);
        if (move == Move.OATHSTRIKE) {
            player.animState = "attack";
            player.spriteNum = 1 + swordPose() / 2;
        } else if (move == Move.INTERPOSE
                && age < 9
                && (player.canResolveWorldActions()
                        ? stepTarget != null
                        : replicaLink >= 0 && replicaLink != player.playerId)) {
            player.animState = "run";
            player.spriteNum = 1 + age / 2;
        } else {
            player.animState = "idle";
            player.spriteNum = 1;
        }
    }

    /** Performs the strike operation for PaladinCombat. */
    private void strike(boolean oath) {
        boolean landed = false;
        int power =
                oath
                        ? Math.round(player.getEffectiveAttack() * 1.7f)
                                + Math.max(1, player.getEffectiveDefense() / 2)
                        : Math.max(
                                1,
                                Math.round(player.getEffectiveAttack() * 0.7f)
                                        + player.getEffectiveDefense() / 3);
        for (int i = 0; i < player.gp.monster.length; i++) {
            Monster target = player.gp.monster[i];
            if (!withinArc(target, oath ? 1.5f : 1.05f, oath ? 0.58 : 0.75)) continue;
            if (!player.damageMonster(i, power, oath, player.direction)) continue;
            landed = true;
            if (!oath) target.stagger(27);
        }
        if (oath && landed) PriestCombat.heal(player, Math.max(2, player.maxHP / 12));
        Particle.spray(
                player,
                Particle.Style.SQUARE,
                oath ? GOLD : IVORY,
                oath ? 13 : 8,
                oath ? 7 : 5,
                18,
                oath ? 6 : 4,
                heading,
                0.62,
                (float) Math.cos(heading) * 23,
                (float) Math.sin(heading) * 23 - 12);
    }

    /** Evaluates the within arc condition for PaladinCombat. */
    private boolean withinArc(Monster target, float range, double arc) {
        if (target == null || !target.alive || target.dying) return false;
        double dx = x(target) - x(player), dy = y(target) - y(player), dist = Math.hypot(dx, dy);
        return dist <= player.gp.tileSize * range
                && dx * Math.cos(heading) + dy * Math.sin(heading) >= dist * arc
                && RangerEffect.clearLine(player.gp, x(player), y(player), x(target), y(target));
    }

    /** Performs the challenge operation for PaladinCombat. */
    private void challenge() {
        for (Monster m : player.gp.monster) {
            if (m != null
                    && m.alive
                    && !m.dying
                    && distance(player, m) <= player.gp.tileSize * 3
                    && RangerEffect.clearLine(player.gp, x(player), y(player), x(m), y(m))) {
                m.challenge(player, player.gp.currentTick + CHALLENGE_DURATION);
            }
        }
        Particle.emit(player, Particle.Style.RING, BLUE, 34, 19, 0, 0, 0, 0);
    }

    /** Resolves the hovered ally result used by PaladinCombat. */
    private Player hoveredAlly() {
        Player best = player;
        double bestDist = Double.POSITIVE_INFINITY;
        int mouseX = player.currentInput.aimWorldX, mouseY = player.currentInput.aimWorldY;
        for (Player ally : player.gp.players) {
            if (ally == player
                    || !validLink(ally)
                    || distance(player, ally) > player.gp.tileSize * 3.8f) continue;
            if (Math.abs(mouseX - x(ally)) > 24
                    || mouseY < ally.worldY - 24
                    || mouseY > y(ally) + 9
                    || !RangerEffect.clearLine(player.gp, x(player), y(player), x(ally), y(ally)))
                continue;
            double aim = Math.hypot(mouseX - x(ally), mouseY - y(ally));
            if (aim < bestDist) {
                best = ally;
                bestDist = aim;
            }
        }
        return best;
    }

    /** Performs the step operation for PaladinCombat. */
    private void step() {
        if (!validLink(stepTarget) || distance(player, stepTarget) <= player.gp.tileSize * 0.75) {
            stepTarget = null;
            return;
        }
        Rectangle body = player.solidArea;
        int px = player.worldX, py = player.worldY;
        for (int i = 1; i <= 7; i++) {
            int nx = px + (int) Math.round(i * Math.cos(heading)),
                    ny = py + (int) Math.round(i * Math.sin(heading));
            if (RangerEffect.blocksDash(
                    player, nx + body.x, ny + body.y, body.width, body.height)) {
                stepTarget = null;
                break;
            }
            player.worldX = nx;
            player.worldY = ny;
        }
        if (age % 3 == 1)
            Particle.spray(
                    player,
                    Particle.Style.DUST,
                    Color.WHITE,
                    2,
                    7,
                    14,
                    2.6f,
                    heading + Math.PI,
                    0.6,
                    0,
                    4);
    }

    /** Evaluates the valid link condition for PaladinCombat. */
    private boolean validLink(Player target) {
        return living(target)
                && target.inWell == player.inWell
                && target.playerId >= 0
                && target.playerId < player.gp.players.length
                && player.gp.players[target.playerId] == target;
    }

    /** Resolves the interceptor result used by PaladinCombat. */
    static Player interceptor(Player target) {
        for (Player candidate : target.gp.players) {
            if (candidate == target
                    || !living(candidate)
                    || !candidate.isPaladin()
                    || candidate.invincible
                    || !candidate.hasShield()) continue;
            var paladin = candidate.paladin;
            if (paladin.linkTicks > 0 && paladin.linked == target && paladin.inLinkRange(target)) {
                paladin.spendLink();
                return candidate;
            }
        }
        return target;
    }

    /** Evaluates the in link range condition for PaladinCombat. */
    private boolean inLinkRange(Player target) {
        return validLink(target)
                && distance(player, target) <= player.gp.tileSize * LINK_RANGE
                && RangerEffect.clearLine(player.gp, x(player), y(player), x(target), y(target));
    }

    /** Evaluates the self brace condition for PaladinCombat. */
    boolean selfBrace() {
        if (linkTicks <= 0 || linked != player || !player.hasShield()) return false;
        spendLink();
        return true;
    }

    /** Performs the spend link operation for PaladinCombat. */
    private void spendLink() {
        linked = null;
        linkTicks = 0;
        blockCue();
    }

    /** Resolves the guard result used by PaladinCombat. */
    int guard(Entity source, int damage) {
        if (!guarding || !player.hasShield() || !fromFront(source)) return damage;
        int reduced = Math.max(1, Math.round(damage * 0.55f));
        if (reduced < damage) {
            player.resolve = Math.min(player.maxResolve, player.resolve + 1);
            blockCue();
        }
        return reduced;
    }

    /** Evaluates the from front condition for PaladinCombat. */
    private boolean fromFront(Entity source) {
        double angle = headingFor(player.direction),
                dx = x(source) - x(player),
                dy = y(source) - y(player);
        if (source instanceof Projectile shot) {
            dx = -shot.velocityX();
            dy = -shot.velocityY();
        }
        double length = Math.hypot(dx, dy);
        if (length < 1) {
            dx = -Math.cos(headingFor(source.direction));
            dy = -Math.sin(headingFor(source.direction));
            length = 1;
        }
        return dx * Math.cos(angle) + dy * Math.sin(angle) >= length * 0.5;
    }

    /** Performs the block cue operation for PaladinCombat. */
    private void blockCue() {
        blockTicks = 9;
        double facing = headingFor(player.direction);
        Particle.spray(
                player,
                Particle.Style.SQUARE,
                BLUE,
                9,
                5.5f,
                16,
                4.5f,
                facing,
                0.9,
                (float) Math.cos(facing) * 17,
                (float) Math.sin(facing) * 17 - 12);
    }

    /** Resolves the wall distance result used by PaladinCombat. */
    private float wallDistance(double facing) {
        return RangerEffect.clearDistance(
                player.gp, x(player), y(player), facing, player.gp.tileSize * 0.95f);
    }

    /** Builds the wall for PaladinCombat. */
    private void buildWall() {
        wallHeading = heading;
        float distance = wallDistance(heading);
        wallX = x(player) + Math.round(distance * (float) Math.cos(heading));
        wallY = y(player) + Math.round(distance * (float) Math.sin(heading));
        wallTicks = WALL_DURATION;
        wallCharges = WALL_CHARGES;
        wallFlash = 0;
        Particle.emit(
                player,
                Particle.Style.RING,
                BLUE,
                22,
                14,
                0,
                0,
                wallX - x(player),
                wallY - y(player));
    }

    /** Evaluates the intercept projectile condition for PaladinCombat. */
    static boolean interceptProjectile(Projectile shot, int dx, int dy, Player contacted) {
        var gp = shot.gp;
        if (gp.isNetworked && !gp.isHost) return false;
        double px = shot.worldX + shot.solidArea.getCenterX(),
                py = shot.worldY + shot.solidArea.getCenterY();
        double halfWidth = shot.solidArea.width / 2.0, halfHeight = shot.solidArea.height / 2.0;
        double time =
                contacted == null
                        ? 1
                        : entryTime(
                                px,
                                py,
                                dx,
                                dy,
                                contacted.worldX + contacted.solidArea.x - halfWidth,
                                contacted.worldY + contacted.solidArea.y - halfHeight,
                                contacted.solidArea.width + 2 * halfWidth,
                                contacted.solidArea.height + 2 * halfHeight);
        PaladinCombat first = null;
        for (Player player : gp.players) {
            if (!living(player) || !player.isPaladin() || player.inWell != gp.quests.inDungeon())
                continue;
            var wall = player.paladin;
            if (wall.wallTicks <= 0 || wall.wallCharges <= 0) continue;
            boolean vertical = Math.abs(Math.cos(wall.wallHeading)) > 0.5;
            double width = vertical ? 10 : 112, height = vertical ? 112 : 10;
            double hit =
                    entryTime(
                            px,
                            py,
                            dx,
                            dy,
                            wall.wallX - width / 2 - halfWidth,
                            wall.wallY - height / 2 - halfHeight,
                            width + 2 * halfWidth,
                            height + 2 * halfHeight);
            if (hit <= Math.min(1, time)) {
                time = hit;
                first = wall;
            }
        }
        if (first == null) return false;
        first.hitWall(px + dx * time, py + dy * time);
        return true;
    }

    /** Resolves the entry time result used by PaladinCombat. */
    private static double entryTime(
            double x, double y, int dx, int dy, double rx, double ry, double w, double h) {
        double near = 0, far = 1;
        if (dx == 0) {
            if (x < rx || x > rx + w) return Double.POSITIVE_INFINITY;
        } else {
            double a = (rx - x) / dx, b = (rx + w - x) / dx;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }
        if (dy == 0) {
            if (y < ry || y > ry + h) return Double.POSITIVE_INFINITY;
        } else {
            double a = (ry - y) / dy, b = (ry + h - y) / dy;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }
        return near <= far ? near : Double.POSITIVE_INFINITY;
    }

    /** Performs the hit wall operation for PaladinCombat. */
    private void hitWall(double x, double y) {
        wallCharges--;
        wallFlash = 10;
        double lateral = -(x - wallX) * Math.sin(wallHeading) + (y - wallY) * Math.cos(wallHeading);
        wallPanel = Math.clamp((int) Math.round(lateral / 34) + 1, 0, 2);
        Particle.spray(
                player,
                Particle.Style.SQUARE,
                BLUE,
                11,
                6,
                19,
                5.2f,
                wallHeading,
                1.5,
                (float) (x - x(player)),
                (float) (y - y(player)) - 10);
    }

    /** Captures the current state in the representation used for persistence or replication. */
    public void snapshot(PlayerState state) {
        state.paladinMove = move.ordinal();
        state.paladinAge = age;
        state.paladinHeading = heading;
        state.paladinGuard = guardPose;
        state.paladinBlock = blockTicks;
        state.paladinLinkId = linkTicks > 0 && validLink(linked) ? linked.playerId : -1;
        state.paladinWallX = wallX;
        state.paladinWallY = wallY;
        state.paladinWallHeading = wallHeading;
        state.paladinWallTicks = wallTicks;
        state.paladinWallCharges = wallCharges;
        state.paladinWallFlash = wallFlash;
        state.paladinWallPanel = wallPanel;
    }

    /** Synchronizes the visual for PaladinCombat. */
    public void syncVisual(PlayerState state) {
        move = MOVES[Math.clamp(state.paladinMove, 0, MOVES.length - 1)];
        age = state.paladinAge;
        heading = state.paladinHeading;
        if (active()) player.attacking = false;
        guardPose = state.paladinGuard;
        blockTicks = state.paladinBlock;
        replicaLink = state.paladinLinkId;
        wallX = state.paladinWallX;
        wallY = state.paladinWallY;
        wallHeading = state.paladinWallHeading;
        wallTicks = state.paladinWallTicks;
        wallCharges = state.paladinWallCharges;
        wallFlash = state.paladinWallFlash;
        wallPanel = state.paladinWallPanel;
        pose();
    }

    /** Evaluates the living condition for PaladinCombat. */
    static boolean living(Player p) {
        return p != null && !p.isDead && p.HP > 0;
    }

    /** Resolves the x result used by PaladinCombat. */
    private static int x(Entity e) {
        return e.worldX + (int) e.solidArea.getCenterX();
    }

    /** Resolves the y result used by PaladinCombat. */
    private static int y(Entity e) {
        return e.worldY + (int) e.solidArea.getCenterY();
    }

    /** Resolves the distance result used by PaladinCombat. */
    private static double distance(Entity a, Entity b) {
        return Math.hypot(x(a) - x(b), y(a) - y(b));
    }

    /** Resolves the heading for result used by PaladinCombat. */
    private static double headingFor(String direction) {
        return switch (direction) {
            case "up" -> -Math.PI / 2;
            case "down" -> Math.PI / 2;
            case "left" -> Math.PI;
            default -> 0;
        };
    }

    /** Draws the slash for PaladinCombat. */
    void drawSlash(Graphics2D graphics) {
        if (move == Move.OATHSTRIKE)
            player.drawSlash(graphics, heading, 70, 66, age - move.impact, false);
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(Graphics2D graphics) {
        if (player.isDead) return;
        Player camera = player.gp.localPlayer();
        float sx = x(player) - camera.worldX + camera.screenX,
                sy = y(player) - camera.worldY + camera.screenY;
        var g = (Graphics2D) graphics.create();
        drawLink(g, camera, sx, sy);
        drawWall(g, camera);
        g.translate(sx, sy);
        if (blockTicks > 0) {
            double facing = headingFor(player.direction);
            g.setComposite(AlphaComposite.SrcOver.derive(blockTicks / 9f));
            g.setColor(GOLD);
            g.fill(
                    PaladinGear.star(
                            (float) Math.cos(facing) * 21,
                            (float) Math.sin(facing) * 15 - 17,
                            11,
                            13));
            g.setComposite(AlphaComposite.SrcOver);
        }
        if (!active()) {
            g.dispose();
            return;
        }
        int phase = age - move.impact;
        if (phase >= 0 && phase < 13) {
            g.setComposite(AlphaComposite.SrcOver.derive(1 - phase / 13f));
            if (move == Move.CHALLENGE) {
                float r = 19 + phase * 6;
                g.setStroke(new BasicStroke(4));
                g.setColor(DARK);
                g.draw(new Ellipse2D.Float(-r, -r, r * 2, r * 2));
                g.setStroke(new BasicStroke(2));
                g.setColor(BLUE);
                g.draw(new Ellipse2D.Float(-r, -r, r * 2, r * 2));
            } else if (move == Move.BASH) {
                g.translate(0, -10);
                g.rotate(heading);
                var smear = new Path2D.Float();
                smear.moveTo(14, -12);
                smear.lineTo(46, -8);
                smear.lineTo(52, 0);
                smear.lineTo(45, 8);
                smear.lineTo(11, 10);
                smear.lineTo(24, 0);
                smear.closePath();
                g.setColor(IVORY);
                g.fill(smear);
                g.setColor(GOLD);
                g.fill(PaladinGear.star(47, 0, 9, 13));
            }
        }
        g.dispose();
    }

    /** Draws the link for PaladinCombat. */
    private void drawLink(Graphics2D g, Player camera, float x, float y) {
        Player target =
                player.canResolveWorldActions()
                        ? linked
                        : replicaLink < 0 || replicaLink >= player.gp.players.length
                                ? null
                                : player.gp.players[replicaLink];
        if (!validLink(target)) return;
        float tx = x(target) - camera.worldX + camera.screenX,
                ty = y(target) - camera.worldY + camera.screenY;
        if (target != player) {
            if (!inLinkRange(target)) return;
            var line =
                    new QuadCurve2D.Float(
                            x, y - 10, (x + tx) / 2, Math.min(y, ty) - 36, tx, ty - 10);
            g.setStroke(new BasicStroke(4));
            g.setColor(DARK);
            g.draw(line);
            g.setStroke(new BasicStroke(2));
            g.setColor(GOLD);
            g.draw(line);
        }
        g.setColor(GOLD);
        g.fill(PaladinGear.star(tx - 23, ty - 29, 4.5f, 7));
    }

    /** Draws the wall for PaladinCombat. */
    private void drawWall(Graphics2D g, Player camera) {
        if (wallTicks <= 0 || wallCharges <= 0 && wallFlash <= 0) return;
        float sx = wallX - camera.worldX + camera.screenX,
                sy = wallY - camera.worldY + camera.screenY;
        for (int i = 0; i < 3; i++) {
            float offset = (i - 1) * 34;
            float x = sx - (float) Math.sin(wallHeading) * offset,
                    y = sy + (float) Math.cos(wallHeading) * offset;
            boolean lit = wallFlash > 0 && wallPanel == i;
            var panel = new Path2D.Float();
            panel.moveTo(x - 15, y - 26);
            panel.quadTo(x, y - 24, x + 15, y - 26);
            panel.quadTo(x + 13, y - 3, x, y + 4);
            panel.quadTo(x - 13, y - 3, x - 15, y - 26);
            panel.closePath();
            g.setColor(
                    lit
                            ? new Color(245, 225, 176, 196)
                            : new Color(115, 184, 206, wallCharges == 0 ? 63 : 120));
            g.fill(panel);
            g.setColor(DARK);
            g.setStroke(new BasicStroke(4));
            g.draw(panel);
            g.setColor(lit ? GOLD : BLUE);
            g.setStroke(new BasicStroke(2));
            g.draw(panel);
            g.fill(PaladinGear.star(x, y - 14, 3, 5));
        }
    }
}
