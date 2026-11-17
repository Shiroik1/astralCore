package art;

/** Sprite names and foot/collision anchors for the meadow expansion, in 48-pixel tile units. */
public enum NatureProp {
    FLOWERS_WHITE("flowers_white", 64, 40, 32, 35),
    FLOWERS_PINK("flowers_pink", 64, 40, 32, 35),
    FLOWERS_PURPLE("flowers_purple", 64, 40, 32, 35),
    FLOWERS_YELLOW("flowers_yellow", 64, 40, 32, 35),
    FLOWERS_BLUE("flowers_blue", 64, 40, 32, 35),
    FLOWERS_MIXED("flowers_mixed", 64, 40, 32, 35),
    MUSHROOM_RED("mushroom_red", 32, 32, 16, 28),
    MUSHROOM_BROWN("mushroom_brown", 32, 32, 16, 28),
    MUSHROOM_IVORY("mushroom_ivory", 32, 32, 16, 28),
    MUSHROOMS_RED("mushrooms_red", 48, 40, 24, 36),
    MUSHROOMS_BROWN("mushrooms_brown", 48, 40, 24, 36),
    MUSHROOMS_IVORY("mushrooms_ivory", 48, 40, 24, 36),
    STUMP_OAK("stump_oak", 80, 64, 40, 58, 32, 16),
    STUMP_OAK_MOSS("stump_oak_moss", 80, 64, 40, 58, 32, 16),
    STUMP_OAK_AGED("stump_oak_aged", 80, 64, 40, 58, 32, 16),
    STUMP_PINE("stump_pine", 64, 64, 32, 58, 22, 14),
    STUMP_PINE_MOSS("stump_pine_moss", 64, 64, 32, 58, 22, 14),
    STUMP_PINE_AGED("stump_pine_aged", 64, 64, 32, 58, 22, 14),
    TWIG("twig", 40, 24, 20, 20);

    public static final int EXPORT_SCALE = 2; // same high-resolution export convention as TownProp
    public final String assetName;
    public final int width, height, footX, footY, solidWidth, solidHeight;

    /** Creates a NatureProp instance and initializes the state required by its collaborators. */
    NatureProp(String name, int width, int height, int footX, int footY) {
        this(name, width, height, footX, footY, 0, 0);
    }

    /** Creates a NatureProp instance and initializes the state required by its collaborators. */
    NatureProp(
            String name,
            int width,
            int height,
            int footX,
            int footY,
            int solidWidth,
            int solidHeight) {
        this.assetName = name;
        this.width = width;
        this.height = height;
        this.footX = footX;
        this.footY = footY;
        this.solidWidth = solidWidth;
        this.solidHeight = solidHeight;
    }
}
