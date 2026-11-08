package world;

import art.ObjectArt;
import art.Palette;
import main.Gamepanel;

import java.util.function.Function;

public enum WorldObjectType {
    OAK_1(a -> a.oak(1), null, true, Behavior.NONE, null),
    OAK_2(a -> a.oak(2), null, true, Behavior.NONE, null),
    PINE_1(a -> a.pine(1), null, true, Behavior.NONE, null),
    PINE_2(a -> a.pine(2), null, true, Behavior.NONE, null),
    BUSH(a -> a.bush(1, false), null, false, Behavior.NONE, null),
    FLOWER_BUSH(a -> a.bush(2, true), null, false, Behavior.NONE, null),
    ROCK_SMALL(a -> a.rockSmall(1), null, false, Behavior.NONE, null),
    ROCK_LARGE(a -> a.rockLarge(1), null, false, Behavior.NONE, null),
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
    HEALING_POND(a -> a.pondStone(),null,false,Behavior.HEAL,null);


    public enum Behavior { NONE, TEXT, CHEST, HEAL, ENTER_WELL, EXIT_WELL, SUMMON_HELL }

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

    private static final double[][] NO_LIGHTS = {};
    private static final double[][] LANTERN_LIGHTS = {{0, -66, 150, 1.0}};
    private static final double[][] HOUSE_LIGHTS = {{-51, -57, 80, 0.8}, {47, -57, 80, 0.8}};

    // light sources relative to the object's foot point: {dx, dy, radius, intensity}, in 48-unit art coordinates
    public double[][] lights(){
        return switch(this){
            case LANTERN -> LANTERN_LIGHTS;
            case HOUSE_CORAL, HOUSE_THATCH, HOUSE_SLATE -> HOUSE_LIGHTS;
            default -> NO_LIGHTS;
        };
    }
}
