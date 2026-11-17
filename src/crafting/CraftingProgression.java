package crafting;

import java.util.*;

/** Crafting XP is independent from combat levels; unlocks are derived from the saved level. */
public final class CraftingProgression {
    public int level = 1, xp;
    public final Set<String> claimed = new HashSet<>();

    /** Resolves the next result used by CraftingProgression. */
    public int next() {
        return level >= RecipeRegistry.MAX_LEVEL ? 0 : RecipeRegistry.XP[level - 1];
    }

    /** Resolves the award result used by CraftingProgression. */
    public int award(RecipeRegistry.Recipe recipe, int batches) {
        int ordinary = recipe.xp();
        if (level > recipe.level() + 2)
            ordinary = Math.max(1, ordinary / (1 + (level - recipe.level()) / 2));
        int gained = Math.multiplyExact(ordinary, batches);
        if (claimed.add(recipe.id())) gained += recipe.firstBonus();
        xp += gained;
        while (level < RecipeRegistry.MAX_LEVEL && xp >= next()) {
            xp -= next();
            level++;
        }
        if (level == RecipeRegistry.MAX_LEVEL) xp = 0;
        return gained;
    }

    /** Restores this component from its saved state. */
    public void restore(int level, int xp, String[] first) {
        if (level < 1
                || level > RecipeRegistry.MAX_LEVEL
                || xp < 0
                || (level < RecipeRegistry.MAX_LEVEL && xp >= RecipeRegistry.XP[level - 1]))
            throw new IllegalArgumentException("Invalid crafting progression");
        this.level = level;
        this.xp = xp;
        claimed.clear();
        if (first != null)
            for (String id : first) {
                if (RecipeRegistry.RECIPES.stream().noneMatch(r -> r.id().equals(id)))
                    throw new IllegalArgumentException("Unknown crafting bonus");
                claimed.add(id);
            }
    }
}
