package entity;

/** Cast cues shared by local play and co-op. */
public final class SkillParticles {
    /** Prevents instantiation of this utility class. */
    private SkillParticles() {}

    /** Performs the spawn operation for SkillParticles. */
    public static void spawn(Player owner, String skill) {
        if (skill != null && owner.playerClass.equals("warrior"))
            WarriorSkillEffect.spawn(owner.gp, owner, skill);
    }
}
