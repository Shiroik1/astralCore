package entity;

import art.Palette;

import net.InputState;

import object.OBJ_rangerbow;

import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/** Bow input, Focus spends, and short-lived targeting state for one Ranger. */
public final class RangerCombat {
    private static final int FULL_DRAW = 33, CLEAN_END = 41;
    private static final int HOP_TICKS = 15, HOP_RELEASE = 6;
    private static final int QUICK_RANGE = 4, FULL_RANGE = 6, DEADEYE_RANGE = 8;
    private static final Color INK = new Color(45, 51, 44, 205);
    private static final Color SAGE = new Color(0xB7DBA3);
    private static final Color GOLD = new Color(0xF0C574);
    private final Player player;
    private int draw = -1, recovery, deadeye, markTicks;
    private int hopAge, hopStartX, hopStartY, hopDistance;
    private boolean hopBlocked;
    private Monster marked;
    private double heading;
    private int markIndex = -1;
    private boolean releaseLocked;

    private static final class Icons {
        static final java.util.Map<String, java.awt.image.BufferedImage> IMAGES =
                new java.util.HashMap<>();

        /** Resolves the load result used by Icons. */
        static synchronized java.awt.image.BufferedImage load(String id) {
            return IMAGES.computeIfAbsent(
                    id,
                    key -> {
                        String path = "/skill_icon/ranger/" + key + ".png";
                        try (var in = RangerCombat.class.getResourceAsStream(path)) {
                            if (in == null) throw new IOException("Missing " + path);
                            return ImageIO.read(in);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    /** Creates a RangerCombat instance and initializes the state required by its collaborators. */
    RangerCombat(Player player) {
        this.player = player;
    }

    /** Updates the skills for RangerCombat. */
    void setSkills(Skill[] slots) {
        slots[0] =
                skill(
                        "Pinning Shot",
                        "pinning_shot",
                        12,
                        300,
                        "Lv 5: slow a regular enemy by 25% for 2s.",
                        () -> {
                            heading = aim();
                            draw = -1;
                            recovery = 12;
                            launch(RangerEffect.Kind.PIN, heading, 6, 0.75f, 0);
                        });
        slots[1] =
                skill(
                        "Piercing Arrow",
                        "rangefinder",
                        18,
                        360,
                        "Lv 7: pierce up to three enemies.",
                        () -> {
                            heading = aim();
                            launch(RangerEffect.Kind.DEADEYE, heading, 6, 1.25f, 0);
                        });
        slots[2] =
                skill(
                        "Steady Aim",
                        "backstep_shot",
                        10,
                        480,
                        "Lv 9: +15% damage for 5s.",
                        () -> player.addStatusEffect(new StatusEffect("steadyAim", 300, 15, 0)));
        slots[3] =
                skill(
                        "Arrow Rain",
                        "arrow_rain",
                        25,
                        540,
                        "Lv 12: three volleys hit the aimed area.",
                        () -> {
                            if (!ammo(true)) return;
                            InputState in = player.currentInput;
                            player.gp.projectileList.add(
                                    RangerEffect.rain(
                                            player,
                                            in.aimWorldX,
                                            in.aimWorldY,
                                            damage(0.30f + .04f * rank("suppressive_rain"))));
                        });
        slots[4] =
                skill(
                        "Deadeye",
                        "deadeye",
                        45,
                        720,
                        "Lv 18: plant your feet; fire through up to three enemies.",
                        () -> {
                            draw = -1;
                            deadeye = FULL_DRAW;
                            heading = aim();
                            releaseLocked = true;
                            chargeCue(GOLD, 12);
                        });
    }

    /** Resolves the skill result used by RangerCombat. */
    private Skill skill(
            String name, String icon, int cost, int cooldown, String description, Runnable cast) {
        Skill result =
                new Skill(
                        name,
                        cost,
                        cooldown,
                        () -> {
                            cast.run();
                            player.announceSkill(name);
                        },
                        Icons.load(icon));
        result.description = description;
        return result;
    }

    /** Reports whether this component can use for RangerCombat. */
    boolean canUse(int slot) {
        if (!player.hasBow()
                || hopAge > 0
                || deadeye > 0
                || player.knockbackActive
                || player.currentInput.ePressed
                || player.currentInput.gameplaySuppressed) return false;
        int[] unlock = {5, 7, 9, 12, 18};
        if (level() < unlock[slot] || slot != 2 && !ammo(false)) return false;
        return slot != 3 || canTarget(player.currentInput.aimWorldX, player.currentInput.aimWorldY);
    }

    /** Reports whether this component can target for RangerCombat. */
    private boolean canTarget(int x, int y) {
        return Math.hypot(x - originX(), y - originY()) <= FULL_RANGE * player.gp.tileSize
                && RangerEffect.clearLine(player.gp, originX(), originY(), x, y);
    }

    /** Advances this component for one simulation tick. */
    void update() {
        if (player.canResolveWorldActions()
                && markTicks > 0
                && (--markTicks == 0
                        || marked == null
                        || !marked.alive
                        || marked.dying
                        || player.gp.monster[markIndex] != marked)) clearMark();
        if (recovery > 0) recovery--;
        InputState input = player.currentInput;
        if (!player.hasBow()
                || player.knockbackActive
                || input.gameplaySuppressed
                || input.ePressed) {
            cancelCast();
            return;
        }
        if (hopAge > 0) {
            if (player.canResolveWorldActions()) advanceBackstep();
            else advanceVisual();
            return;
        }
        if (deadeye > 0) {
            if (--deadeye == 0) {
                launch(RangerEffect.Kind.DEADEYE, heading, DEADEYE_RANGE, 2, 0);
                recovery = 15;
            } else if (deadeye % 8 == 0) chargeCue(GOLD, 3);
            return;
        }
        if (!input.leftHeld) releaseLocked = false;
        if (draw < 0 && recovery == 0 && input.leftClicked && !releaseLocked) draw = 0;
        if (draw < 0) return;
        heading = aim();
        if (input.leftHeld) {
            draw = Math.min(fullDraw() + 20, draw + 1);
            if (draw == fullDraw()) chargeCue(GOLD, 8);
        } else {
            boolean full = draw >= fullDraw(),
                    clean =
                            level() >= 3
                                    && full
                                    && draw
                                            <= fullDraw()
                                                    + 8
                                                    + (level() >= 22 ? 4 : 0)
                                                    + rank("precise_release") * 2;
            launch(
                    clean
                            ? RangerEffect.Kind.CLEAN
                            : full ? RangerEffect.Kind.FULL : RangerEffect.Kind.QUICK,
                    heading,
                    full ? FULL_RANGE : QUICK_RANGE,
                    clean ? 1.30f : full ? 1.05f : 0.60f,
                    clean ? 16 : full ? 12 : 8);
            recovery = full ? 12 : 24;
            draw = -1;
        }
    }

    /** Resolves the full draw result used by RangerCombat. */
    private int fullDraw() {
        return Math.max(22, FULL_DRAW - rank("swift_draw") * 2);
    }

    /** Evaluates the braced condition for RangerCombat. */
    boolean braced() {
        return hopAge > 0 || deadeye > 0 || draw >= fullDraw();
    }

    /** Resolves the hop lift result used by RangerCombat. */
    int hopLift() {
        return hopAge == 0
                ? 0
                : Math.round(16 * (float) Math.sin(Math.PI * (hopAge - 1) / HOP_TICKS));
    }

    /** Returns the current hop age value. */
    public int hopAge() {
        return hopAge;
    }

    /** Performs the advance visual operation for RangerCombat. */
    void advanceVisual() {
        if (hopAge > 0 && ++hopAge > HOP_TICKS) {
            hopAge = 0;
            hopDust(9, 3);
        }
    }

    /** Performs the pose operation for RangerCombat. */
    void pose() {
        if (!player.hasBow() || draw < 0 && deadeye == 0 && recovery == 0 && hopAge == 0) return;
        player.animState = "attack";
        player.direction = RangerEffect.direction(heading);
        player.spriteNum =
                hopAge > 0
                        ? (hopAge <= HOP_RELEASE ? 3 : 5)
                        : deadeye > 0
                                ? Math.min(4, 1 + (FULL_DRAW - deadeye) / 9)
                                : draw >= 0
                                        ? Math.min(4, 1 + draw / 9)
                                        : Math.max(5, 8 - recovery / 5);
    }

    /** Resolves the aim result used by RangerCombat. */
    private double aim() {
        InputState in = player.currentInput;
        int dx = in.aimWorldX - originX(), dy = in.aimWorldY - originY();
        if (dx != 0 || dy != 0) return Math.atan2(dy, dx);
        return switch (player.direction) {
            case "up" -> -Math.PI / 2;
            case "down" -> Math.PI / 2;
            case "left" -> Math.PI;
            default -> 0;
        };
    }

    /** Resolves the origin x result used by RangerCombat. */
    int originX() {
        return player.worldX + player.solidArea.x + player.solidArea.width / 2;
    }

    /** Resolves the origin y result used by RangerCombat. */
    int originY() {
        return player.worldY + player.solidArea.y + 6;
    }

    /** Resolves the level result used by RangerCombat. */
    public int level() {
        return progression.Progression.skillLevel(player.bowMasteryXP);
    }

    /** Resolves the rank result used by RangerCombat. */
    public int rank(String id) {
        return player.progression.talents.getOrDefault(id, 0);
    }

    /** Evaluates the ammo condition for RangerCombat. */
    private boolean ammo(boolean consume) {
        return !consume
                ? crafting.InventoryTransaction.count(player, object.ItemType.WOODEN_ARROW) > 0
                : crafting.InventoryTransaction.commit(
                        player.gp,
                        player,
                        java.util.Map.of(object.ItemType.WOODEN_ARROW, 1),
                        java.util.Map.of());
    }

    /** Resolves the damage result used by RangerCombat. */
    private int damage(float multiplier) {
        return Math.max(
                1,
                (int)
                        Math.round(
                                player.getEffectiveAttack()
                                        * multiplier
                                        * (1
                                                + (level() >= 25 ? .1 : 0)
                                                + player.getStatusEffectValue("steadyAim") * .01
                                                + .03 * rank("steady_hands")
                                                + .05 * rank("perfect_marksman"))));
    }

    /**
     * Consumes one arrow before creating the ranged attack with its damage and hit-earned Focus
     * reward.
     */
    private void launch(
            RangerEffect.Kind kind, double angle, int range, float multiplier, int focus) {
        if (!player.canResolveWorldActions() || !player.hasBow()) return;
        if (!ammo(true)) return;
        if (kind == RangerEffect.Kind.CLEAN) multiplier += .06f * rank("precise_release");
        if (kind == RangerEffect.Kind.PIN) multiplier += .08f * rank("pinning_precision");
        if (kind == RangerEffect.Kind.DEADEYE)
            multiplier += .08f * (rank("improved_piercing") + rank("deadeye_mastery"));
        player.gp.projectileList.add(
                RangerEffect.arrow(player, kind, angle, range, damage(multiplier), focus));
    }

    /** Performs the advance backstep operation for RangerCombat. */
    private void advanceBackstep() {
        int tick = hopAge++;
        double t = tick / (double) HOP_TICKS;
        int targetDistance = (int) Math.round(player.gp.tileSize * t * (1.45 - 0.45 * t));
        double dx = -Math.cos(heading), dy = -Math.sin(heading);
        var area = player.solidArea;
        while (!hopBlocked && hopDistance < targetDistance) {
            int distance = hopDistance + 1;
            int x = hopStartX + (int) Math.round(dx * distance),
                    y = hopStartY + (int) Math.round(dy * distance);
            if (RangerEffect.blocksDash(player, x + area.x, y + area.y, area.width, area.height)) {
                hopBlocked = true;
                break;
            }
            player.worldX = x;
            player.worldY = y;
            hopDistance = distance;
        }
        if (tick == HOP_RELEASE) launch(RangerEffect.Kind.BACKSTEP, heading, QUICK_RANGE, 0.45f, 0);
        if (tick == HOP_TICKS) {
            hopAge = 0;
            recovery = 6;
            hopDust(9, 3);
        }
    }

    /** Performs the hop dust operation for RangerCombat. */
    private void hopDust(float size, int count) {
        Particle.spray(
                player, Particle.Style.DUST, Color.WHITE, count, size, 18, 2.1f, heading, 1, 0, -3);
    }

    /** Resolves the bow reach result used by RangerCombat. */
    static float bowReach(double heading) {
        return 26 + 22 * Math.max(0, -(float) Math.sin(heading));
    }

    /** Performs the charge cue operation for RangerCombat. */
    private void chargeCue(Color color, int count) {
        float reach = bowReach(heading),
                dx = (float) Math.cos(heading) * reach,
                dy = (float) Math.sin(heading) * reach;
        Particle.spray(
                player,
                Particle.Style.SQUARE,
                color,
                count,
                5.5f,
                16,
                2.1f,
                heading,
                1.2,
                dx,
                dy - 10 - hopLift());
        if (count >= 8)
            Particle.emit(
                    player,
                    Particle.Style.SPARK,
                    Palette.PLASTER,
                    11,
                    12,
                    0,
                    0,
                    dx,
                    dy - 10 - hopLift());
    }

    /** Performs the mark burst operation for RangerCombat. */
    private static void markBurst(Monster target) {
        Particle.emit(target, Particle.Style.RING, GOLD, 23, 22, 0, 0, 0, -5);
        Particle.spray(
                target, Particle.Style.SQUARE, GOLD, 9, 5.5f, 20, 3.6f, -Math.PI / 2, 1.05, 0, -12);
    }

    /** Resolves the mark bonus result used by RangerCombat. */
    int markBonus(Monster target) {
        return markTicks > 0 && marked == target
                ? Math.max(1, Math.round(player.getEffectiveAttack() * 0.45f))
                : 0;
    }

    /** Performs the consume mark operation for RangerCombat. */
    void consumeMark(Monster target) {
        if (markTicks > 0 && marked == target) {
            Particle.burst(target, Particle.Style.SQUARE, GOLD, 7, 4.6f, 16, 3.5f);
            clearMark();
        }
    }

    /** Clears the mark for RangerCombat. */
    private void clearMark() {
        marked = null;
        markTicks = 0;
        markIndex = -1;
    }

    /** Cancels the cast for RangerCombat. */
    void cancelCast() {
        hopAge = 0;
        cancelAim();
    }

    /** Cancels the aim for RangerCombat. */
    void cancelAim() {
        draw = -1;
        deadeye = 0;
        recovery = 0;
        releaseLocked = true;
        if (hopAge == 0 && player.isRanger() && "attack".equals(player.animState)) {
            player.animState = "idle";
            player.spriteNum = 1;
        }
    }

    /** Restores this component to its initial runtime state. */
    void reset() {
        cancelCast();
        releaseLocked = false;
        clearMark();
    }

    /** Returns the current mark index value. */
    public int markIndex() {
        return markIndex;
    }

    /** Returns the current mark ticks value. */
    public int markTicks() {
        return markTicks;
    }

    /** Draws the ticks for RangerCombat. */
    public int drawTicks() {
        return draw;
    }

    /** Returns the current deadeye value. */
    public int deadeyeTicks() {
        return deadeye;
    }

    /** Returns the current heading value. */
    public double heading() {
        return heading;
    }

    /** Applies the visual for RangerCombat. */
    public void applyVisual(
            int markIndex,
            int markTicks,
            int draw,
            int deadeye,
            int hopAge,
            int recovery,
            double heading,
            boolean remote) {
        if (this.hopAge == 0 && hopAge > 0) {
            this.heading = heading;
            hopDust(11, 4);
        } else if (this.hopAge > 0 && hopAge == 0 && recovery > 0) {
            hopDust(9, 3);
        }
        if (markIndex >= 0
                && markIndex < player.gp.monster.length
                && markTicks > 0
                && (this.markIndex != markIndex || markTicks > this.markTicks)) {
            Monster target = player.gp.monster[markIndex];
            if (target != null && target.alive && !target.dying) markBurst(target);
        }
        if (this.deadeye == 0 && deadeye > 0) {
            this.heading = heading;
            chargeCue(GOLD, 12);
        }
        this.markIndex = markIndex;
        this.markTicks = markTicks;
        this.deadeye = deadeye;
        this.hopAge = hopAge;
        if (remote || deadeye > 0 || hopAge > 0 || this.draw < 0 && recovery > 0) {
            this.draw = draw;
            this.heading = heading;
            this.recovery = recovery;
        }
    }

    /** Returns the current recovery value. */
    public int recoveryTicks() {
        return recovery;
    }

    /** Draws the bow for RangerCombat. */
    void drawBow(Graphics2D g) {
        if (!"attack".equals(player.animState)
                || !(player.currentWeapon instanceof OBJ_rangerbow bow)
                || player.isDead) return;
        Player camera = player.gp.localPlayer();
        float x = originX() - camera.worldX + camera.screenX,
                y = originY() - camera.worldY + camera.screenY;
        float pull =
                deadeye > 0
                        ? 1 - deadeye / (float) FULL_DRAW
                        : Math.max(0, Math.min(1, draw / (float) fullDraw()));
        Graphics2D aim = (Graphics2D) g.create();
        switch (player.direction) {
            case "down" -> {
                aim.translate(x, y + 15 - hopLift());
                aim.rotate(Math.PI / 2);
            }
            case "up" -> {
                aim.translate(x, y - 22 - hopLift());
                aim.rotate(-Math.PI / 2);
            }
            default -> {
                float reach = bowReach(heading);
                aim.translate(
                        x + Math.cos(heading) * reach, y + Math.sin(heading) * reach - hopLift());
                aim.rotate(heading);
            }
        }
        bow.gear.drawBow(aim, 0, 0, 1.02f, pull);
        aim.dispose();
    }

    /** Renders this component into the supplied Java2D graphics context. */
    void draw(Graphics2D g) {
        if (player.isDead) return;
        Player camera = player.gp.localPlayer();
        float left = camera.worldX - camera.screenX, top = camera.worldY - camera.screenY;
        Color old = g.getColor();
        Stroke stroke = g.getStroke();
        if (deadeye > 0) {
            float x = originX() - left, y = originY() - top;
            float length =
                    RangerEffect.clearDistance(
                            player.gp,
                            originX(),
                            originY(),
                            heading,
                            DEADEYE_RANGE * player.gp.tileSize);
            float dx = (float) Math.cos(heading), dy = (float) Math.sin(heading);
            if (length > 28) {
                var line =
                        new Line2D.Float(
                                x + dx * 28, y + dy * 28, x + dx * length, y + dy * length);
                g.setColor(INK);
                g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(line);
                g.setColor(GOLD);
                g.setStroke(new BasicStroke(deadeye < 12 ? 4 : 2.5f));
                g.draw(line);
                g.setColor(Palette.PLASTER);
                g.setStroke(new BasicStroke(1.2f));
                g.draw(line);
            }
        } else if (level() >= 3
                && draw >= fullDraw()
                && draw <= fullDraw() + 8 + (level() >= 22 ? 4 : 0) + rank("precise_release") * 2) {
            float reach = bowReach(heading) + 14;
            float x = originX() - left + (float) Math.cos(heading) * reach,
                    y = originY() - top + (float) Math.sin(heading) * reach;
            g.setColor(GOLD);
            g.fill(new Ellipse2D.Float(x - 3.5f, y - 3.5f, 7, 7));
            g.setColor(Palette.PLASTER);
            g.fill(new Rectangle2D.Float(x - 1.5f, y - 1.5f, 3, 3));
            g.draw(new Line2D.Float(x - 6, y, x - 4, y));
            g.draw(new Line2D.Float(x + 4, y, x + 6, y));
        }
        if (markTicks > 0 && markIndex >= 0 && markIndex < player.gp.monster.length) {
            Monster m = player.gp.monster[markIndex];
            if (m != null && m.alive && !m.dying) {
                float x = m.worldX + m.solidArea.x + m.solidArea.width / 2f - left,
                        y = m.worldY - 20 - top;
                y -= 7 + (float) Math.sin(markTicks * 0.15) * 2;
                Path2D diamond = new Path2D.Float();
                diamond.moveTo(x, y - 11);
                diamond.lineTo(x + 10, y);
                diamond.lineTo(x, y + 11);
                diamond.lineTo(x - 10, y);
                diamond.closePath();
                g.setColor(INK);
                g.fill(diamond);
                g.setStroke(new BasicStroke(5));
                g.draw(diamond);
                g.setColor(GOLD);
                g.setStroke(new BasicStroke(2.6f));
                g.draw(diamond);
                g.setColor(Palette.PLASTER);
                g.fill(new Rectangle2D.Float(x - 2, y - 2, 4, 4));
            }
        }
        g.setColor(old);
        g.setStroke(stroke);
    }

    /** Performs the pin operation for RangerCombat. */
    static void pin(Monster target) {
        target.statusEffects.removeIf(effect -> effect.type.equals("rangerSlow"));
        target.addStatusEffect(new StatusEffect("rangerSlow", 120, 25, 0));
        pinParticles(target);
    }

    /** Performs the pin particles operation for RangerCombat. */
    static void pinParticles(Monster target) {
        Particle.emit(target, Particle.Style.RING, SAGE, 23, 20, 0, 0, 0, 9);
        Particle.burst(target, Particle.Style.SQUARE, SAGE, 11, 6, 22, 4.5f);
    }
}
