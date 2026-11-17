package progression;

import entity.Player;

import java.util.*;

/** First playable talent tier. Later tiers are intentionally not exposed as working abilities. */
public final class Talents {
    public record Definition(
            String id,
            String path,
            String name,
            String effect,
            int maxRank,
            int characterLevel,
            int masteryLevel) {}

    public static final List<Definition> ALL =
            List.of(
                    new Definition(
                            "sword_power",
                            "Sword",
                            "Sword Power",
                            "+3% Sword damage/rank",
                            3,
                            2,
                            2),
                    new Definition(
                            "quick_recovery",
                            "Sword",
                            "Quick Recovery",
                            "-1 recovery tick/rank",
                            3,
                            2,
                            2),
                    new Definition(
                            "guard_discipline",
                            "Sword",
                            "Guard Discipline",
                            "+4% guard reduction/rank",
                            3,
                            2,
                            2),
                    new Definition(
                            "strong_counter",
                            "Sword",
                            "Strong Counter",
                            "+12% counter damage/rank",
                            2,
                            7,
                            7),
                    new Definition(
                            "heavy_edge",
                            "Sword",
                            "Heavy Edge",
                            "+12% Heavy Slash damage/rank",
                            2,
                            7,
                            7),
                    new Definition(
                            "wide_swing", "Sword", "Wide Swing", "+8px sweep width/rank", 2, 7, 7),
                    new Definition(
                            "defensive_rhythm",
                            "Sword",
                            "Defensive Rhythm",
                            "Guard primes +6% next swing/rank",
                            2,
                            13,
                            13),
                    new Definition(
                            "blade_precision",
                            "Sword",
                            "Blade Precision",
                            "Third landed combo swing +4%/rank",
                            2,
                            13,
                            13),
                    new Definition(
                            "pursuing_strike",
                            "Sword",
                            "Pursuing Strike",
                            "Rush primes next slash +12%/rank for 2s",
                            2,
                            13,
                            13),
                    new Definition(
                            "perfect_swordsman",
                            "Sword",
                            "Perfect Swordsman",
                            "+6% basics, +8% counters/rank",
                            2,
                            20,
                            20),
                    new Definition(
                            "unbroken_guard",
                            "Sword",
                            "Unbroken Guard",
                            "+2.5% guard, +1 timing tick/rank",
                            2,
                            20,
                            20),
                    new Definition(
                            "masterful_blade",
                            "Sword",
                            "Masterful Blade",
                            "+8% signature damage/rank",
                            2,
                            20,
                            20),
                    new Definition("keen_eye", "Bow", "Keen Eye", "+3% Bow damage/rank", 3, 2, 2),
                    new Definition(
                            "keen_intellect",
                            "Staff",
                            "Keen Intellect",
                            "+3% Staff damage/rank",
                            3,
                            2,
                            2),
                    new Definition(
                            "swift_draw", "Bow", "Swift Draw", "-2 charge ticks/rank", 3, 2, 2),
                    new Definition(
                            "focus_efficiency",
                            "Bow",
                            "Focus Efficiency",
                            "+1 hit Focus/rank",
                            3,
                            2,
                            2),
                    new Definition(
                            "pinning_precision",
                            "Bow",
                            "Pinning Precision",
                            "+8% Pin damage/rank",
                            2,
                            7,
                            7),
                    new Definition(
                            "improved_piercing",
                            "Bow",
                            "Improved Piercing",
                            "+8% pierce damage/rank",
                            2,
                            7,
                            7),
                    new Definition(
                            "steady_hands", "Bow", "Steady Hands", "+3% Bow damage/rank", 2, 7, 7),
                    new Definition(
                            "suppressive_rain",
                            "Bow",
                            "Suppressive Rain",
                            "+4% baseline per rain wave/rank",
                            2,
                            13,
                            13),
                    new Definition(
                            "precise_release",
                            "Bow",
                            "Precise Release",
                            "+6% Clean damage, +2 timing ticks/rank",
                            2,
                            13,
                            13),
                    new Definition(
                            "hunters_rhythm",
                            "Bow",
                            "Hunter's Rhythm",
                            "+1 hit Focus/rank",
                            2,
                            13,
                            13),
                    new Definition(
                            "perfect_marksman",
                            "Bow",
                            "Perfect Marksman",
                            "+5% Bow damage/rank",
                            2,
                            20,
                            20),
                    new Definition(
                            "deadeye_mastery",
                            "Bow",
                            "Deadeye Mastery",
                            "+8% Deadeye damage/rank",
                            2,
                            20,
                            20),
                    new Definition(
                            "unerring_focus",
                            "Bow",
                            "Unerring Focus",
                            "+1 hit Focus/rank",
                            2,
                            20,
                            20),
                    new Definition(
                            "mana_flow", "Staff", "Mana Flow", "-1 MP per spell/rank", 3, 2, 2),
                    new Definition(
                            "precise_bolt",
                            "Staff",
                            "Precise Bolt",
                            "+4% free bolt damage/rank",
                            3,
                            2,
                            2),
                    new Definition(
                            "lingering_embers",
                            "Staff",
                            "Lingering Embers",
                            "+45 ticks Immolate/rank",
                            2,
                            7,
                            7),
                    new Definition(
                            "winters_grasp",
                            "Staff",
                            "Winter's Grasp",
                            "+5% Blizzard slow/rank",
                            2,
                            7,
                            7),
                    new Definition(
                            "wardcraft",
                            "Staff",
                            "Wardcraft",
                            "+5 shield absorption/rank",
                            2,
                            7,
                            7),
                    new Definition(
                            "elemental_reaction",
                            "Staff",
                            "Elemental Reaction",
                            "+8% Staff damage to slowed targets/rank",
                            2,
                            13,
                            13),
                    new Definition(
                            "arcane_weaving",
                            "Staff",
                            "Arcane Weaving",
                            "+3% spell damage, +3% Magic Buff/rank",
                            2,
                            13,
                            13),
                    new Definition(
                            "protective_flow",
                            "Staff",
                            "Protective Flow",
                            "Shield returns 1 MP on meaningful absorb/rank",
                            2,
                            13,
                            13),
                    new Definition(
                            "runic_overload",
                            "Staff",
                            "Runic Overload",
                            "+8% enhanced spell damage/rank",
                            2,
                            20,
                            20),
                    new Definition(
                            "elemental_echo",
                            "Staff",
                            "Elemental Echo",
                            "+4% Staff damage/rank",
                            2,
                            20,
                            20),
                    new Definition(
                            "grand_convergence",
                            "Staff",
                            "Grand Convergence",
                            "+12% baseline Convergence damage/rank",
                            2,
                            20,
                            20));
    public static final Map<String, List<Definition>> PATHS =
            Map.of(
                    "Sword",
                    ALL.stream().filter(t -> t.path().equals("Sword")).toList(),
                    "Bow",
                    ALL.stream().filter(t -> t.path().equals("Bow")).toList(),
                    "Staff",
                    ALL.stream().filter(t -> t.path().equals("Staff")).toList());

    /** Resolves the prior result used by Talents. */
    public static int prior(Definition d) {
        return d.characterLevel() >= 20
                ? 8
                : d.characterLevel() >= 13 ? 5 : d.characterLevel() >= 7 ? 2 : 0;
    }

    /** Resolves the lower investment result used by Talents. */
    public static int lowerInvestment(Player p, Definition d) {
        return ALL.stream()
                .filter(t -> t.path().equals(d.path()) && t.characterLevel() < d.characterLevel())
                .mapToInt(t -> p.progression.talents.getOrDefault(t.id(), 0))
                .sum();
    }

    /** Evaluates the available condition for Talents. */
    public static boolean available(Player p, Definition d) {
        return !p.gp.isNetworked
                && p.level >= d.characterLevel()
                && mastery(p, d.path()) >= d.masteryLevel()
                && lowerInvestment(p, d) >= prior(d)
                && p.progression.talentPoints > 0
                && p.progression.talents.getOrDefault(d.id(), 0) < d.maxRank();
    }

    /** Prevents instantiation of this utility class. */
    private Talents() {}

    /** Resolves the mastery result used by Talents. */
    public static int mastery(Player p, String path) {
        return Progression.skillLevel(
                path.equals("Sword")
                        ? p.meleeMasteryXP
                        : path.equals("Bow") ? p.bowMasteryXP : p.magicMasteryXP);
    }

    /**
     * Checks rank, mastery, tier investment and point requirements before committing one talent
     * rank.
     */
    public static boolean buy(Player p, Definition d) {
        if (!ALL.contains(d) || !available(p, d)) return false;
        int rank = p.progression.talents.getOrDefault(d.id(), 0);
        if (rank >= d.maxRank()) return false;
        p.progression.talents.put(d.id(), rank + 1);
        p.progression.talentPoints--;
        p.attack = p.getAttack();
        if (p.isMage()) p.mage.setup(p.skills);
        return true;
    }

    /** Resolves the damage result used by Talents. */
    public static double damage(Player p, int type) {
        String id =
                type == entity.Entity.type_sword
                        ? "sword_power"
                        : type == entity.Entity.type_bow
                                ? "keen_eye"
                                : type == entity.Entity.type_staff ? "keen_intellect" : "";
        return 1 + .03 * p.progression.talents.getOrDefault(id, 0);
    }
}
