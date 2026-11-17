package art;

import java.util.Locale;

/** Sizes and anchors in the game's 48-pixel tile coordinates. */
public enum TownProp {
    WELL(96, 96, 48, 82, -30, -24, 60, 24, true),
    LANTERN(48, 96, 24, 90, -4, -6, 8, 6, false),
    LANTERN_HANGING(64, 96, 21, 90, -5, -7, 10, 7, false),
    CRATE(48, 48, 24, 42, -16, -12, 32, 12, true),
    CRATE_STACK(56, 68, 28, 61, -19, -13, 38, 13, true),
    BARREL(48, 48, 24, 42, -14, -10, 28, 10, true),
    BARREL_STACK(72, 68, 36, 61, -30, -13, 60, 13, true),
    FENCE_SHORT(80, 48, 40, 43, -35, -10, 70, 10, false),
    FENCE_TALL(80, 48, 40, 43, -35, -10, 70, 10, false),
    FENCE_H(48, 48, 24, 44, -24, -10, 48, 10, false),
    FENCE_V(48, 48, 24, 42, -5, -42, 10, 48, false),
    LOGS_COVERED(84, 62, 42, 54, -33, -15, 66, 15, false),
    LOGS_STACKED(84, 62, 42, 54, -33, -15, 66, 15, false),
    ROCK_SMALL(48, 48, 24, 41, -14, -10, 28, 10, false),
    ROCK_SMALL_MOSS(48, 48, 24, 41, -14, -10, 28, 10, false),
    ROCK_LARGE(96, 96, 48, 88, -34, -16, 68, 16, false),
    ROCK_LARGE_MOSS(96, 96, 48, 88, -34, -16, 68, 16, false);

    static final int EXPORT_SCALE = 2;
    final int width, height, footX, footY, left, top, solidWidth, solidHeight;
    final boolean interactive;

    /** Creates a TownProp instance and initializes the state required by its collaborators. */
    TownProp(
            int width,
            int height,
            int footX,
            int footY,
            int left,
            int top,
            int solidWidth,
            int solidHeight,
            boolean interactive) {
        this.width = width;
        this.height = height;
        this.footX = footX;
        this.footY = footY;
        this.left = left;
        this.top = top;
        this.solidWidth = solidWidth;
        this.solidHeight = solidHeight;
        this.interactive = interactive;
    }

    /** Resolves the asset name result used by TownProp. */
    String assetName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
