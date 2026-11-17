package progression;

import entity.Player;

import java.util.*;

/** Single-player progression authority. Legacy mastery counters remain cumulative XP. */
public final class Progression {
    public static final int CAP = 25;
    public static final double ATTRIBUTE_RATE =
            Double.parseDouble(System.getProperty("astral.progression.attributeRate", "0.012"));
    public static final double SOFT_RATE =
            Double.parseDouble(System.getProperty("astral.progression.softRate", "0.5"));
    public static final double ENERGY_PER_POINT =
            Double.parseDouble(System.getProperty("astral.progression.energyPerPoint", "3"));
    public int staffPage, mealTicks, drinkTicks;
    public int[] staffCooldowns = new int[13];
    public int version = 1, attributePoints, talentPoints, stamina = 10, energy = 100;
    public int[] lifeXP =
            new int[4]; // Mining, Gathering, Cooking, Fishing; Crafting keeps its existing owner.
    public final Map<String, Integer> talents = new LinkedHashMap<>();
    public final StarterQuests journal = new StarterQuests();
    public final Set<String> milestones = new HashSet<>();
    private final Map<Integer, Long> hitCooldown = new HashMap<>();

    /** Resolves the required result used by Progression. */
    public static int required(int level) {
        int n = Math.clamp(level, 1, CAP) - 1;
        return 100 + 35 * n + 15 * n * n;
    }

    /** Resolves the mastery required result used by Progression. */
    public static int masteryRequired(int level) {
        return 25 + 10 * (level - 1) + 5 * (level - 1) * (level - 1);
    }

    /** Resolves the skill level result used by Progression. */
    public static int skillLevel(int total) {
        int level = 1;
        while (level < CAP && total >= masteryRequired(level)) {
            total -= masteryRequired(level++);
        }
        return level;
    }

    /** Resolves the skill remainder result used by Progression. */
    public static int skillRemainder(int total) {
        int level = 1;
        while (level < CAP && total >= masteryRequired(level)) {
            total -= masteryRequired(level++);
        }
        return level == CAP ? 0 : Math.max(0, total);
    }

    /** Resolves the effective result used by Progression. */
    public static double effective(int stat) {
        int extra = Math.max(0, stat - 10);
        return Math.min(extra, 30) + Math.max(0, extra - 30) * SOFT_RATE;
    }

    /** Resolves the multiplier result used by Progression. */
    public static double multiplier(int stat) {
        return 1 + effective(stat) * ATTRIBUTE_RATE;
    }

    /** Resolves the max energy result used by Progression. */
    public int maxEnergy() {
        return 100
                + (mealTicks > 0 ? 20 : 0)
                + (int) Math.round(ENERGY_PER_POINT * effective(stamina));
    }

    /**
     * Validates the complete pending allocation before committing attributes and deducting unspent
     * points.
     */
    public boolean allocate(Player p, int[] pending) {
        if (p.gp.isNetworked || pending == null || pending.length != 4) return false;
        int[] base = {p.strength, p.dexterity, p.intelligence, stamina};
        long sum = 0;
        for (int i = 0; i < 4; i++) {
            if (pending[i] < 0 || pending[i] > 60 - base[i]) return false;
            sum += pending[i];
        }
        if (sum > attributePoints) return false;
        p.strength += pending[0];
        p.dexterity += pending[1];
        p.intelligence += pending[2];
        stamina += pending[3];
        attributePoints -= (int) sum;
        p.attack = p.getAttack();
        return true;
    }

    /** Performs the level reward operation for Progression. */
    public void levelReward(int level) {
        attributePoints += 3;
        if (level % 2 == 0) talentPoints++;
    }

    /**
     * Awards weapon mastery only for accepted damage, throttling rapid multi-hit rewards per weapon
     * type.
     */
    public void hit(Player p, int weapon, int damage) {
        if (damage <= 0 || p.gp.isNetworked) return;
        long now = p.gp.currentTick;
        if (now < hitCooldown.getOrDefault(weapon, Long.MIN_VALUE)) return;
        hitCooldown.put(weapon, now + 20);
        if (weapon == entity.Entity.type_sword)
            p.meleeMasteryXP = Math.min(1000000, p.meleeMasteryXP + 3);
        else if (weapon == entity.Entity.type_bow)
            p.bowMasteryXP = Math.min(1000000, p.bowMasteryXP + 3);
        else if (weapon == entity.Entity.type_staff)
            p.magicMasteryXP = Math.min(1000000, p.magicMasteryXP + 3);
    }

    /** Performs the life operation for Progression. */
    public void life(int index, int amount) {
        if (index >= 0 && index < 4 && amount > 0)
            lifeXP[index] = (int) Math.min(1000000, (long) lifeXP[index] + amount);
    }

    /** Evaluates the spend energy condition for Progression. */
    public boolean spendEnergy(int cost) {
        if (energy < cost) return false;
        energy -= cost;
        return true;
    }

    /**
     * Converts legacy automatic stats into refundable attribute points while retaining mastery XP.
     */
    public void migrate(Player p) {
        attributePoints = 3 * (Math.clamp(p.level, 1, 25) - 1);
        talentPoints = Math.clamp(p.level, 1, 25) / 2;
        p.strength = p.dexterity = p.intelligence = 10;
        stamina = 10;
        p.level = Math.clamp(p.level, 1, 25);
        p.maxHP = 100 + 4 * (p.level - 1);
        p.HP = Math.min(p.HP, p.maxHP);
        p.maxMP = 100;
        p.MP = Math.min(p.MP, 100);
        p.nextLevelExp = required(p.level);
        if (p.exp >= p.nextLevelExp) p.grantExperience(0);
    }

    public static final class Data {
        public int staffPage, mealTicks, drinkTicks;
        public int[] staffCooldowns = new int[13];
        public int schema = 1, attributePoints, talentPoints, stamina = 10, energy = 100;
        public int[] lifeXP = new int[4];
        public Map<String, Integer> talents = new LinkedHashMap<>();
        public ArrayList<String> milestones = new ArrayList<>();
        public Map<String, StarterQuests.Entry> quests = new LinkedHashMap<>();
        public String tracked = "rest";
    }

    /**
     * Copies persistent progression into an independent save DTO without retaining mutable runtime
     * collections.
     */
    public Data snapshot() {
        Data d = new Data();
        d.staffPage = staffPage;
        d.mealTicks = mealTicks;
        d.drinkTicks = drinkTicks;
        d.staffCooldowns = staffCooldowns.clone();
        d.attributePoints = attributePoints;
        d.talentPoints = talentPoints;
        d.stamina = stamina;
        d.energy = energy;
        d.lifeXP = lifeXP.clone();
        d.talents.putAll(talents);
        d.milestones.addAll(milestones);
        d.tracked = journal.tracked;
        journal.entries.forEach((id, e) -> {
            var copy = new StarterQuests.Entry();
            copy.active = e.active;
            copy.claimed = e.claimed;
            copy.counts = e.counts.clone();
            d.quests.put(id, copy);
        });
        return d;
    }

    /**
     * Restores versioned progression data and rejects invalid cooldowns, quest counters or point
     * values.
     */
    public void restore(Data d) {
        if (d == null
                || d.schema != 1
                || d.lifeXP == null
                || d.talents == null
                || d.milestones == null
                || d.quests == null)
            throw new IllegalArgumentException("Missing or unsupported progression");
        if (d.staffCooldowns == null || d.staffCooldowns.length != 13)
            throw new IllegalArgumentException("Invalid staff cooldowns");
        staffCooldowns = d.staffCooldowns.clone();
        for (int c : staffCooldowns)
            if (c < 0 || c > 36000) throw new IllegalArgumentException("Invalid spell cooldown");
        mealTicks = Math.clamp(d.mealTicks, 0, 7200);
        drinkTicks = Math.clamp(d.drinkTicks, 0, 7200);
        staffPage = Math.clamp(d.staffPage, 0, 2);
        attributePoints = d.attributePoints;
        talentPoints = d.talentPoints;
        stamina = d.stamina;
        energy = Math.clamp(d.energy, 0, maxEnergy());
        lifeXP = d.lifeXP.clone();
        talents.clear();
        talents.putAll(d.talents);
        milestones.clear();
        milestones.addAll(d.milestones);
        journal.tracked = d.tracked;
        for (var def : StarterQuests.DEFINITIONS) {
            var e = d.quests.get(def.id());
            if (e == null) continue;
            if (e.counts == null || e.counts.length != def.needed().length)
                throw new IllegalArgumentException("Invalid quest counts");
            for (int i = 0; i < e.counts.length; i++)
                if (e.counts[i] < 0 || e.counts[i] > def.needed()[i])
                    throw new IllegalArgumentException("Invalid quest progress");
            journal.entries.put(def.id(), e);
        }
        validate();
    }

    /**
     * Rejects allocations or talent investments that exceed the rewards earned at the saved
     * character level.
     */
    public void validateBudget(Player p) {
        validateBudget(p.level, p.strength, p.dexterity, p.intelligence);
    }

    /**
     * Rejects allocations or talent investments that exceed the rewards earned at the saved
     * character level.
     */
    public void validateBudget(int level, int strength, int dexterity, int intelligence) {
        long spent = (long) strength + dexterity + intelligence + stamina - 40;
        if (level < 1
                || level > 25
                || strength < 10
                || strength > 60
                || dexterity < 10
                || dexterity > 60
                || intelligence < 10
                || intelligence > 60
                || spent + attributePoints != 3L * (level - 1))
            throw new IllegalArgumentException("Invalid attribute budget");
        int invested = talents.values().stream().mapToInt(Integer::intValue).sum();
        if (invested + talentPoints != level / 2)
            throw new IllegalArgumentException("Invalid talent budget");
        for (var definition : Talents.ALL)
            if (talents.getOrDefault(definition.id(), 0) > 0) {
                int prior =
                        Talents.ALL.stream()
                                .filter(t ->
                                        t.path().equals(definition.path())
                                                && t.characterLevel() < definition.characterLevel())
                                .mapToInt(t -> talents.getOrDefault(t.id(), 0))
                                .sum();
                if (level < definition.characterLevel() || prior < Talents.prior(definition))
                    throw new IllegalArgumentException("Invalid talent prerequisites");
            }
    }

    /** Rejects input values that violate this component’s data constraints. */
    public void validate() {
        if (attributePoints < 0
                || attributePoints > 72
                || talentPoints < 0
                || talentPoints > 12
                || stamina < 10
                || stamina > 60
                || lifeXP == null
                || lifeXP.length != 4) throw new IllegalArgumentException("Invalid progression");
        for (var entry : talents.entrySet()) {
            var definition =
                    Talents.ALL.stream()
                            .filter(t -> t.id().equals(entry.getKey()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unknown talent"));
            if (entry.getValue() < 0 || entry.getValue() > definition.maxRank())
                throw new IllegalArgumentException("Invalid talent rank");
        }
        for (int xp : lifeXP)
            if (xp < 0 || xp > 1000000) throw new IllegalArgumentException("Invalid life XP");
    }
}
