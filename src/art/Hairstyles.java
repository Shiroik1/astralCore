package art;

import java.awt.Color;

/** Stable IDs follow the supplied reference: male 01–20, then female 01–20. */
public final class Hairstyles {
    /** Prevents instantiation of this utility class. */
    private Hairstyles() {}

    public static final int COUNT = 40;
    public static final String[] NAMES = {
        "Messy", "Straight", "Short Spiky", "Side Part", "Curtain", "Bowl Cut", "Shaggy", "Wavy",
                "Undercut", "Slicked Back",
        "Low Ponytail", "Mid Ponytail", "Top Knot", "Braided", "Wolf Cut", "Long Straight",
                "Long Wavy", "Half-Up", "High Ponytail", "Layered Long",
        "Short Bob", "Hime Cut", "Layered Bob", "Side Bangs", "Twin Short", "Wavy Bob", "Curly",
                "Braided Crown", "Headband", "Hair Clips",
        "Low Ponytail", "Side Ponytail", "Twin Tails", "Braided Tail", "Side Braid", "Half-Up",
                "High Ponytail", "Long Straight", "Long Wavy", "Twin Braids"
    };
    public static final String[] COLOR_NAMES = {
        "Espresso",
        "Chestnut",
        "Cocoa",
        "Ash Brown",
        "Sand",
        "Honey",
        "Cream",
        "Copper",
        "Rosewood",
        "Dusty Rose",
        "Mauve",
        "Lilac",
        "Plum",
        "Slate",
        "Mist Blue",
        "Sage",
        "Sea Glass",
        "Silver",
        "Pearl",
        "Charcoal"
    };
    public static final Color[] COLORS = {
        new Color(0x66504B), new Color(0x8B655A), new Color(0xA17B6B), new Color(0x9C8A7E),
                new Color(0xC6AD91),
        new Color(0xC4A16D), new Color(0xD8C6A5), new Color(0xB58268), new Color(0x9C737B),
                new Color(0xC39BA6),
        new Color(0xAA95A7), new Color(0xB1A0BF), new Color(0x82718D), new Color(0x7C8399),
                new Color(0xA1B6C4),
        new Color(0x96AB99), new Color(0x88AAA6), new Color(0xB8B4BC), new Color(0xDDD4CD),
                new Color(0x68636E)
    };

    /** Evaluates the valid condition for Hairstyles. */
    public static boolean valid(int id) {
        return id >= 0 && id < COUNT;
    }

    /** Evaluates the valid color condition for Hairstyles. */
    public static boolean validColor(int id) {
        return id >= 0 && id < COLORS.length;
    }

    /** Evaluates the long hair condition for Hairstyles. */
    public static boolean longHair(int id) {
        return valid(id)
                && switch (id) {
                    case 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 20, 22, 23, 24, 25, 28, 29 -> false;
                    default -> true;
                };
    }

    /** Resolves the outfit result used by Hairstyles. */
    public static CharacterOutfit outfit(CharacterOutfit o, int style, int color) {
        var d = o.details();
        return new CharacterOutfit(
                o.hair() == null ? null : COLORS[color],
                o.torso(),
                o.pants(),
                o.boots(),
                o.gloves(),
                o.cloak(),
                new CharacterOutfit.Details(
                        d.hair(),
                        d.torso(),
                        d.pants(),
                        d.boots(),
                        d.gloves(),
                        d.cloak(),
                        longHair(style),
                        style),
                o.headpiece());
    }
}
