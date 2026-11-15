package entity;

/** Cast cues shared by local play and co-op. */
public final class SkillParticles {
    private SkillParticles() {}

    public static void spawn(Player owner,String skill){
        if(skill!=null && owner.playerClass.equals("warrior"))WarriorSkillEffect.spawn(owner.gp,owner,skill);
    }
}
