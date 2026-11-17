package entity;

import java.awt.image.BufferedImage;

public class Skill {
    public String name;
    public String description = "";
    public int resourceCost;
    public int cooldownDuration; // ticks
    public int cooldownRemaining = 0;
    public BufferedImage icon;
    public Runnable onUse;

    /** Creates a Skill instance and initializes the state required by its collaborators. */
    public Skill(
            String name,
            int resourceCost,
            int cooldownDuration,
            Runnable onUse,
            BufferedImage icon) {
        this.name = name;
        this.resourceCost = resourceCost;
        this.cooldownDuration = cooldownDuration;
        this.icon = icon;
        this.onUse = onUse;
    }

    /** Reports whether ready for Skill. */
    public boolean isReady() {
        return cooldownRemaining <= 0;
    }

    /** Advances this component for one simulation tick. */
    public void update() {
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
        }
    }

    /** Performs the trigger operation for Skill. */
    public void trigger() {
        cooldownRemaining = cooldownDuration;
        onUse.run();
    }
}
