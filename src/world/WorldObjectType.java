package world;

import art.BuildingCatalog.Light;
import art.ObjectArt;
import art.Palette;
import art.TownProp;
import main.Gamepanel;

import java.util.List;
import java.util.function.Function;

public enum WorldObjectType {
    OAK_1(a -> a.oak(1), null, true, Behavior.NONE, null),
    OAK_2(a -> a.oak(2), null, true, Behavior.NONE, null),
    PINE_1(a -> a.pine(1), null, true, Behavior.NONE, null),
    PINE_2(a -> a.pine(2), null, true, Behavior.NONE, null),
    BUSH(a -> a.bush(false), null, false, Behavior.NONE, null),
    FLOWER_BUSH(a -> a.bush(true), null, false, Behavior.NONE, null),
    ROCK_SMALL(a -> a.rockSmall(), null, false, Behavior.NONE, null),
    ROCK_LARGE(a -> a.rockLarge(), null, false, Behavior.NONE, null),
    STUMP(a -> a.stump(), null, false, Behavior.NONE, null),
    FENCE_H(a -> a.fenceH(), null, false, Behavior.NONE, null),
    FENCE_V(a -> a.fenceV(), null, false, Behavior.NONE, null),
    HOUSE_CORAL(a -> a.house("house_coral", Palette.ROOF_CORAL, 1), null, true, Behavior.NONE, null),
    HOUSE_THATCH(a -> a.house("house_thatch", Palette.ROOF_THATCH, 2), null, true, Behavior.NONE, null),
    HOUSE_SLATE(a -> a.house("house_slate", Palette.ROOF_SLATE, 3), null, true, Behavior.NONE, null),
    LANTERN(a -> a.lantern(), null, false, Behavior.NONE, null),
    WELL(a -> a.well(), null, false, Behavior.ENTER_WELL, null),
    SIGN(a -> a.sign(), null, false, Behavior.TEXT, "Welcome to the village."),
    BARREL(a -> a.barrel(), null, false, Behavior.TEXT, "An old barrel.\nNothing useful inside."),
    CRATE(a -> a.crate(), null, false, Behavior.TEXT, "A wooden crate.\nIt is empty."),
    CHEST(a -> a.chest(), a -> a.chestOpen(), false, Behavior.CHEST, null),
    WELL_LADDER(a -> a.wellLadder(), null, false, Behavior.EXIT_WELL, null),
    WELL_ALTAR(a -> a.wellAltar(),null,false,Behavior.SUMMON_HELL,null),
    HEALING_POND(a -> a.pondStone(),null,false,Behavior.HEAL,null),
    INN(null, null, true, Behavior.ENTER_INN, null),
    INN_EXIT(ObjectArt::innExit, null, false, Behavior.EXIT_INN, null),
    BUILDING(null, null, true, Behavior.NONE, null),
    LANTERN_HANGING(a -> a.townProp(TownProp.LANTERN_HANGING), null, false, Behavior.NONE, null),
    CRATE_STACK(a -> a.townProp(TownProp.CRATE_STACK), null, false, Behavior.TEXT, "Crates stacked for storage."),
    BARREL_STACK(a -> a.townProp(TownProp.BARREL_STACK), null, false, Behavior.TEXT, "Barrels stacked for storage."),
    FENCE_SHORT(a -> a.townProp(TownProp.FENCE_SHORT), null, false, Behavior.NONE, null),
    FENCE_TALL(a -> a.townProp(TownProp.FENCE_TALL), null, false, Behavior.NONE, null),
    LOGS_COVERED(a -> a.townProp(TownProp.LOGS_COVERED), null, false, Behavior.NONE, null),
    LOGS_STACKED(a -> a.townProp(TownProp.LOGS_STACKED), null, false, Behavior.NONE, null),
    ROCK_SMALL_MOSS(a -> a.townProp(TownProp.ROCK_SMALL_MOSS), null, false, Behavior.NONE, null),
    ROCK_LARGE_MOSS(a -> a.townProp(TownProp.ROCK_LARGE_MOSS), null, false, Behavior.NONE, null);


    public enum Behavior { NONE, TEXT, CHEST, HEAL, ENTER_WELL, EXIT_WELL, SUMMON_HELL, ENTER_INN, EXIT_INN }

    private final Function<ObjectArt, ObjectArt.Sprite> factory;
    private final Function<ObjectArt, ObjectArt.Sprite> altFactory;
    public final boolean fadesWhenBehind;
    public final Behavior behavior;
    public final String defaultText;

    private ObjectArt.Sprite sprite, altSprite;
    private static ObjectArt art;

    WorldObjectType(Function<ObjectArt, ObjectArt.Sprite> factory, Function<ObjectArt, ObjectArt.Sprite> altFactory,
                    boolean fadesWhenBehind, Behavior behavior, String defaultText){
        this.factory = factory;
        this.altFactory = altFactory;
        this.fadesWhenBehind = fadesWhenBehind;
        this.behavior = behavior;
        this.defaultText = defaultText;
    }

    // each sprite is baked once and shared by every object of that type
    public synchronized ObjectArt.Sprite sprite(Gamepanel gp){
        if(sprite == null) sprite = factory.apply(art(gp));
        return sprite;
    }

    public synchronized ObjectArt.Sprite altSprite(Gamepanel gp){
        if(altFactory == null) return sprite(gp);
        if(altSprite == null) altSprite = altFactory.apply(art(gp));
        return altSprite;
    }

    private static synchronized ObjectArt art(Gamepanel gp){
        if(art == null) art = new ObjectArt(gp.tileSize);
        return art;
    }

    public static WorldObjectType fromName(String name){
        for(WorldObjectType t : values()){
            if(t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    public boolean swaysInWind(){
        return switch(this){
            case OAK_1, OAK_2, PINE_1, PINE_2 -> true;
            default -> false;
        };
    }

    private static final List<Light> LANTERN_LIGHTS = List.of(new Light(0, -66, 150, 1f));
    private static final List<Light> HANGING_LANTERN_LIGHTS = List.of(new Light(25, -53, 142, 0.9f));
    private static final List<Light> HOUSE_LIGHTS = List.of(new Light(-51, -57, 80, 0.8f), new Light(47, -57, 80, 0.8f));

    // light sources relative to the object's foot point: {dx, dy, radius, intensity}, in 48-unit art coordinates
    public List<Light> lights(){
        return switch(this){
            case LANTERN -> LANTERN_LIGHTS;
            case LANTERN_HANGING -> HANGING_LANTERN_LIGHTS;
            case HOUSE_CORAL, HOUSE_THATCH, HOUSE_SLATE -> HOUSE_LIGHTS;
            default -> List.of();
        };
    }
}
