package object;

import entity.Entity;
import main.Gamepanel;

import java.util.HashMap;
import java.util.Map;

public class ItemRegistry {
    private static final Map<Class<?>, ItemType> BY_CLASS = new HashMap<>();

    static {
        for(ItemType t : ItemType.values()){
            BY_CLASS.put(t.itemClass, t);
        }
    }

    public static Entity create(ItemType type, Gamepanel gp){
        return (type != null) ? type.factory.apply(gp) : null;
    }

    public static ItemType typeFor(Entity item){
        if(item instanceof OBJ_apparel apparel) return apparel.itemType;
        return (item != null) ? BY_CLASS.get(item.getClass()) : null;
    }
}