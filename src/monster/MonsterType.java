package monster;

import entity.Entity;
import entity.Monster;
import main.Gamepanel;

import java.util.function.Function;

public enum MonsterType {
    GREEN_SLIME(MON_GreenSlime.class, MON_GreenSlime::new),
    SLIME_BOSS(MON_SlimeBoss.class, MON_SlimeBoss::new),
    IMP(MON_Imp.class, MON_Imp::new),
    HELL_SLIME(MON_HellSlime.class,MON_HellSlime::new);

    private final Class<? extends Monster> monsterClass;
    private final Function<Gamepanel, Monster> factory;

    MonsterType(Class<? extends Monster> monsterClass, Function<Gamepanel, Monster> factory){
        this.monsterClass = monsterClass;
        this.factory = factory;
    }

    public Monster create(Gamepanel gp){
        return factory.apply(gp);
    }

    /** Generate each reusable atlas and hit effect once before the game loop starts. */
    public static void preload(Gamepanel gp){
        for(MonsterType type:values()){
            Monster template=type.create(gp);
            Entity.preloadSpriteEffects(template.sprites);
        }
    }

    public static MonsterType of(Entity m){
        if(m == null) return null;
        for(MonsterType t : values()){
            if(t.monsterClass == m.getClass()) return t;
        }
        return null;
    }

    public static MonsterType fromName(String name){
        for(MonsterType t : values()){
            if(t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}