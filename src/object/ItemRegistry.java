package object;

import entity.Entity;
import main.Gamepanel;

import java.util.Map;
import java.util.function.Function;

public class ItemRegistry {
    private static final Map<String, Function<Gamepanel, Entity>> FACTORIES = Map.ofEntries(
            Map.entry("OBJ_potion_red", OBJ_potion_red::new),
            Map.entry("OBJ_sword_normal", OBJ_sword_normal::new),
            Map.entry("OBJ_axe", OBJ_axe::new),
            Map.entry("OBJ_shield_wood", OBJ_shield_wood::new),
            Map.entry("OBJ_blueshield", OBJ_blueshield::new),
            Map.entry("OBJ_key", OBJ_key::new),
            Map.entry("OBJ_door", OBJ_door::new),
            Map.entry("OBJ_chest", OBJ_chest::new),
            Map.entry("OBJ_boots", OBJ_boots::new),
            Map.entry("OBJ_heart", OBJ_heart::new),
            Map.entry("OBJ_crystal", OBJ_crystal::new),
            Map.entry("OBJ_bronzecoin", OBJ_bronzecoin::new),
            Map.entry("OBJ_fists", OBJ_fists::new),
            Map.entry("OBJ_no_shield", OBJ_no_shield::new)
    );

    public static Entity create(String typeId, Gamepanel gp){
        Function<Gamepanel, Entity> factory = FACTORIES.get(typeId);
        return (factory != null) ? factory.apply(gp) : null;
    }

    public static String idFor(Entity item){
        return item.getClass().getSimpleName();
    }
}