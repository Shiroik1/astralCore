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
    public WorldPalette(Gamepanel gp){this(gp.tileSize);}
    public WorldPalette(int tileSize){
        for(WorldObjectType type : new WorldObjectType[]{WorldObjectType.OAK_1,WorldObjectType.OAK_2,
                WorldObjectType.PINE_1,WorldObjectType.PINE_2,WorldObjectType.BUSH,WorldObjectType.FLOWER_BUSH,
                WorldObjectType.ROCK_SMALL,WorldObjectType.ROCK_LARGE,
                WorldObjectType.ROCK_SMALL_MOSS,WorldObjectType.ROCK_LARGE_MOSS,
                WorldObjectType.FLOWERS_WHITE,
                WorldObjectType.FLOWERS_PINK,
                WorldObjectType.FLOWERS_PURPLE,
                WorldObjectType.FLOWERS_YELLOW,
                WorldObjectType.FLOWERS_BLUE,
                WorldObjectType.FLOWERS_MIXED,
                WorldObjectType.MUSHROOM_RED,
                WorldObjectType.MUSHROOM_BROWN,
                WorldObjectType.MUSHROOM_IVORY,
                WorldObjectType.MUSHROOMS_RED,
                WorldObjectType.MUSHROOMS_BROWN,
                WorldObjectType.MUSHROOMS_IVORY,
                WorldObjectType.STUMP_OAK,
                WorldObjectType.STUMP_OAK_MOSS,
                WorldObjectType.STUMP_OAK_AGED,
                WorldObjectType.STUMP_PINE,
                WorldObjectType.STUMP_PINE_MOSS,
                WorldObjectType.STUMP_PINE_AGED,
                WorldObjectType.TWIG}){
            ObjectArt.Sprite sprite=type.sprite(tileSize);
            footprints.put(type,new Footprint(new Rectangle(-sprite.footX,-sprite.footY,
                    sprite.image.getWidth(),sprite.image.getHeight()),new Rectangle(sprite.collision)));
        }
    }
    public Footprint footprint(WorldObjectType type){return footprints.get(type);}
}
