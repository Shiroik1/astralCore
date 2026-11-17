package entity;

import monster.SlimeMotion;

import progression.*;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Sword attacks share one committed animation and one hit event per swing. */
public final class SwordCombat {
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

    /** Performs the setup operation for SwordCombat. */
    public void setup(Skill[] slots) {
        String[] names = {"Heavy Slash", "Counter Slash", "Sweeping Slash", "Blade Rush", "Guard"};
        int[] cooldowns = {150, 90, 240, 360, 0};
        for (int i = 0; i < 5; i++) {
            final int slot = i;
            slots[i] = new Skill(names[i], 0, cooldowns[i], () -> start(slot), icon(i));
            slots[i].description = description(i);
        }
    }

    /** Resolves the description result used by SwordCombat. */
    public String description(int slot) {
        return switch (slot) {
            case 0 -> "Lv 3 · 175% damage, deliberate wind-up.";
            case 1 -> "Lv 7 · Timed guard opens a 1.5s counter: 160% damage.";
            case 2 -> "Lv 12 · 125% damage in a wider frontal sweep.";
            case 3 -> "Lv 18 · Collision-aware 0.8-tile rush, 150% damage.";
            default -> "Lv 5 · Hold right mouse. Frontal damage reduction; never immunity.";
        };
    }

    /** Reports whether this component can use for SwordCombat. */
    public boolean canUse(int slot) {
        int[] unlock = {3, 7, 12, 18, 5};
        return equipped()
                && level() >= unlock[slot]
                && !p.attacking
                && !p.knockbackActive
                && !p.isDead
                && !p.currentInput.gameplaySuppressed
                && !guarding
                && (slot != 1 || counterTicks > 0)
                && slot != 4;
    }

    /** Advances this component for one simulation tick. */
    public void tick() {
        if (!equipped() || p.isDead || p.inDialogue || p.currentInput.gameplaySuppressed) {
            cancel();
            return;
        }
        if (!p.attacking && hit != null) {
            hit = null;
            age = 0;
            action = "Basic Slash";
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

    /** Performs the start operation for SwordCombat. */
    private void start(int slot) {
        if (!canUse(slot)) return;
        action =
                switch (slot) {
                    case 0 -> "Heavy Slash";
                    case 1 -> "Counter Slash";
                    case 2 -> "Sweeping Slash";
                    default -> "Blade Rush";
                };
        begin();
        p.attacking = true;
        p.animState = "attack";
        p.spriteNum = 1;
        if (slot == 1) counterTicks = 0;
    }

    /** Performs the begin operation for SwordCombat. */
    private void begin() {
        bearing = p.direction;
        age = 0;
        connected = false;
        hit = new CombatHit("sword:" + action);
        hit.masteryType = Entity.type_sword;
        boolean heavy = action.equals("Heavy Slash");
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
        total = wind + active + recovery;
    }

    /**
     * Advances the committed sword wind-up, active frames and recovery without allowing a recovery
     * bypass.
     */
    public void animate() {
        if (hit == null) {
            action = "Basic Slash";
            begin();
        }
        p.direction = bearing;
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
        if (action.equals("Blade Rush") && age >= wind && age < wind + active) {
            int amount = Math.max(1, p.gp.tileSize * 4 / 5 / active),
                    dx = bearing.equals("right") ? amount : bearing.equals("left") ? -amount : 0,
                    dy = bearing.equals("down") ? amount : bearing.equals("up") ? -amount : 0;
            for (int step = 0; step < amount; step++) {
                int x = p.worldX + Integer.signum(dx), y = p.worldY + Integer.signum(dy);
                if (!SlimeMotion.clear(p.gp, SlimeMotion.body(p, x, y))) break;
                p.worldX = x;
                p.worldY = y;
            }
        }
        if (age >= wind && age < wind + active && p.canResolveWorldActions()) strike();
        if (age >= total) {
            p.attacking = false;
            p.animState = "idle";
            p.spriteNum = 1;
            hit = null;
            age = 0;
            action = "Basic Slash";
        }
    }

    /**
     * Applies the current sword attack to visible targets once each and updates combo and follow-up
     * bonuses.
     */
    private void strike() {
        Rectangle box = p.meleeAttackBounds();
        if (action.equals("Sweeping Slash")) {
            if (bearing.equals("up") || bearing.equals("down")) {
                box.x -= 18 + rank("wide_swing") * 4;
                box.width += 36 + rank("wide_swing") * 8;
            } else {
                box.y -= 18 + rank("wide_swing") * 4;
                box.height += 36 + rank("wide_swing") * 8;
            }
        }
        double scale =
                switch (action) {
                    case "Heavy Slash" -> 1.75 + .12 * rank("heavy_edge");
                    case "Counter Slash" ->
                            (level() >= 22 ? 2.1 : 1.6)
                                    + .12 * rank("strong_counter")
                                    + .08 * rank("perfect_swordsman");
                    case "Sweeping Slash" -> 1.25;
                    case "Blade Rush" -> 1.5;
                    default -> 1 + (level() >= 25 ? .1 : 0) + .06 * rank("perfect_swordsman");
                };
        if (!action.equals("Basic Slash") && !action.equals("Counter Slash"))
            scale += .08 * rank("masterful_blade");
        double bonus =
                rhythm
                        + (combo >= 2 ? .04 * rank("blade_precision") : 0)
                        + (action.equals("Basic Slash") && followupTicks > 0
                                ? .12 * rank("pursuing_strike")
                                : 0);
        for (int i : p.gp.collisionChecker.checkEntityMultiple(box, p.gp.monster)) {
            Monster m = p.gp.monster[i];
            if (m == null || !SlimeMotion.sight(p.gp, p, m)) continue;
            if (p.damageMonsterOnce(
                            i,
                            (int) Math.round(p.getEffectiveAttack() * (scale + bonus)),
                            false,
                            bearing,
                            hit,
                            action.equals("Heavy Slash") ? CombatTuning.HEAVY : CombatTuning.SWORD)
                    && !connected) {
                if (action.equals("Blade Rush")) followupTicks = 120;
                else if (action.equals("Basic Slash")) followupTicks = 0;
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

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(Graphics2D g) {
        if (!equipped()) return;
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
        if (hit != null && age >= wind && age < wind + active) {
            v.setColor(new Color(244, 233, 207, 180));
            v.drawArc(-26, 2, 52, 38, 190, 160);
        }
        v.dispose();
    }

    /** Resolves the icon result used by SwordCombat. */
    private static BufferedImage icon(int slot) {
        BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xD7C29B));
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(13, 35, 34, 12);
        g.drawLine(10, 27, 21, 38);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        g.drawString(Integer.toString(slot + 1), 33, 43);
        g.dispose();
        return image;
    }
}
