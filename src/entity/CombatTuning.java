package entity;

/** Tile-relative basic hit displacement. Explicit skill displacement remains owned by its skill. */
public final class CombatTuning {
    public static final double SWORD = n("sword", .15),
            ARROW = n("arrow", .05),
            BOLT = n("bolt", .10),
            HEAVY = n("heavy", .35);

    /** Prevents instantiation of this utility class. */
    private CombatTuning() {}

    /** Reads a typed configuration value and applies the local conversion or validation rules. */
    private static double n(String key, double value) {
        double v =
                Double.parseDouble(
                        System.getProperty(
                                "astral.combat.knockback." + key, Double.toString(value)));
        if (!Double.isFinite(v) || v < 0 || v > 2)
            throw new IllegalArgumentException("Invalid knockback: " + key);
        return v;
    }

    /** Performs the push operation for CombatTuning. */
    public static void push(Monster target, String direction, double tiles) {
        if (!target.canBeKnockedBack() || tiles <= 0) return;
        int pixels = Math.max(1, (int) Math.round(target.gp.tileSize * tiles));
        // Do not stack basic impacts or shorten displacement already owned by an explicit ability.
        if (target.knockbackActive) return;
        if (pixels > 0) target.startKnockback(direction, pixels);
    }
}
