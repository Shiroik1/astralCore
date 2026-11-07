package art;

import java.awt.Color;

// Between the reference painting and the pastel pass: softer than the painting, richer than the pastel.
public final class Palette {
    private Palette(){}

    // ground tones, lightest to deepest
    public static final Color[] GROUND = {
            new Color(0xADD786), new Color(0x96C879), new Color(0x80B772), new Color(0x659E6C)
    };
    public static final Color PATH = new Color(0xE0C89A);

    public static final Color MORTAR = new Color(0x948D90);
    public static final Color[] STONES = {
            new Color(0xCEC8C5), new Color(0xC8C0C0), new Color(0xD3CCC8)
    };

    // roofs, walls, timber
    public static final Color ROOF_CORAL = new Color(0xCE7D69);
    public static final Color ROOF_THATCH = new Color(0xDAB76B);
    public static final Color ROOF_SLATE = new Color(0x7C90CC);
    public static final Color PLASTER = new Color(0xF2E9D6);
    public static final Color TIMBER = new Color(0x8A6B58);
    public static final Color BRICK = new Color(0xC27560);

    // plants: foliage is deliberately darker than the ground so trees and bushes stand out
    public static final Color TRUNK = new Color(0x997458);
    public static final Color FOLIAGE_DARK = new Color(0x3F7D55);
    public static final Color FOLIAGE = new Color(0x5BA063);
    public static final Color FOLIAGE_LIGHT = new Color(0x8BC877);
    public static final Color[] FLOWERS = {
            new Color(0xED98B2), new Color(0xFAF4E9), new Color(0xF5DA74),
            new Color(0xAA94DD), new Color(0xF3AE6F)
    };

    // props
    public static final Color ROCK = new Color(0xB7B0B9);
    public static final Color WOOD = new Color(0xB48B62);
    public static final Color WOOD_LIGHT = new Color(0xD9B88C);
    public static final Color BARREL = new Color(0xB08058);
    public static final Color WATER = new Color(0x9BCDE8);
    public static final Color GLOW = new Color(0xF6DD9A);
    public static final Color GOLD = new Color(0xE2B84E);
    public static final Color IRON = new Color(0x6E6A78);
    public static final Color SKY = new Color(0xA7D6F2);
}