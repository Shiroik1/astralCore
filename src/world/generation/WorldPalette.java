package world.generation;

import art.ObjectArt;
import main.Gamepanel;
import world.WorldObjectType;
import java.awt.Rectangle;
import java.util.EnumMap;

/** Adapter to the existing cached asset catalog. Footprints come from the actual loaded sprites. */
public final class WorldPalette {
    public record Footprint(Rectangle image,Rectangle solid) {}
    private final EnumMap<WorldObjectType,Footprint> footprints=new EnumMap<>(WorldObjectType.class);
    public WorldPalette(Gamepanel gp){
        for(WorldObjectType type : new WorldObjectType[]{WorldObjectType.OAK_1,WorldObjectType.OAK_2,
                WorldObjectType.PINE_1,WorldObjectType.PINE_2,WorldObjectType.BUSH,WorldObjectType.FLOWER_BUSH,
                WorldObjectType.ROCK_SMALL,WorldObjectType.ROCK_LARGE,
                WorldObjectType.ROCK_SMALL_MOSS,WorldObjectType.ROCK_LARGE_MOSS}){
            ObjectArt.Sprite sprite=type.sprite(gp);
            footprints.put(type,new Footprint(new Rectangle(-sprite.footX,-sprite.footY,
                    sprite.image.getWidth(),sprite.image.getHeight()),new Rectangle(sprite.collision)));
        }
    }
    public Footprint footprint(WorldObjectType type){return footprints.get(type);}
}
