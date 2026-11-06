package monster;

import entity.Entity;
import entity.Monster;
import main.Gamepanel;

import java.util.function.Function;

public enum MonsterType {
    GREEN_SLIME(MON_GreenSlime.class, MON_GreenSlime::new),
    SLIME_BOSS(MON_SlimeBoss.class, MON_SlimeBoss::new),
    IMP(MON_Imp.class, MON_Imp::new);

    private final Class<? extends Monster> monsterClass;
    private final Function<Gamepanel, Monster> factory;

    MonsterType(Class<? extends Monster> monsterClass, Function<Gamepanel, Monster> factory){
        this.monsterClass = monsterClass;
        this.factory = factory;
    }

    public Monster create(Gamepanel gp){
        return factory.apply(gp);
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