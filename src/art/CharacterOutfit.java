package art;

import java.awt.Color;

/** Immutable equipment snapshot; colours and cuts belong to each removable item. */
public record CharacterOutfit(Color hair, Color torso, Color pants, Color boots, Color gloves,
                              Color cloak, Details details, Color headpiece) {
    public enum Slot { HAIR, TORSO, PANTS, BOOTS, GLOVES, CLOAK, HEADPIECE }
    public enum Theme { LEATHER, CASTER, ARMOR }
    public record Details(Theme hair, Theme torso, Theme pants, Theme boots, Theme gloves,
                          Theme cloak, boolean longHair, int hairStyle) {
        public Details(Theme hair,Theme torso,Theme pants,Theme boots,Theme gloves,Theme cloak,boolean longHair){
            this(hair,torso,pants,boots,gloves,cloak,longHair,-1);
        }
        public static Details uniform(Theme theme, boolean longHair) {
            return new Details(theme, theme, theme, theme, theme, theme, longHair);
        }
    }
    public CharacterOutfit(Color hair, Color torso, Color pants, Color boots, Color gloves, Color cloak, Details details) {
        this(hair,torso,pants,boots,gloves,cloak,details,null);
    }
    public CharacterOutfit(Color hair, Color torso, Color pants, Color boots, Color gloves, Color cloak) {
        this(hair, torso, pants, boots, gloves, cloak, Details.uniform(Theme.LEATHER, false));
    }
    public static final CharacterOutfit BARE = new CharacterOutfit(null, null, null, null, null, null);
    public static final CharacterOutfit ADVENTURER = themed(Theme.LEATHER, false);
    public static CharacterOutfit themed(Theme theme, boolean longHair) {
        int[] colors = switch(theme) {
            case LEATHER -> new int[]{0x86574E,0xF4E6CC,0x51474B,0x574046,0x704A49,0x427782};
            case CASTER -> new int[]{0xB1A0BF,0xF4E6CC,0x62576F,0x4B424F,0x514653,0x655A73};
            case ARMOR -> new int[]{0xC9AA73,0xC5C2B9,0x51494A,0xC5C2B9,0xB8B6B0,0x864F55};
        };
        return new CharacterOutfit(new Color(colors[0]),new Color(colors[1]),new Color(colors[2]),
                new Color(colors[3]),new Color(colors[4]),new Color(colors[5]),Details.uniform(theme,longHair),
                theme==Theme.CASTER?new Color(0x655A73):null);
    }
    public Color color(Slot slot) {
        return switch(slot) {
            case HAIR -> hair; case TORSO -> torso; case PANTS -> pants;
            case BOOTS -> boots; case GLOVES -> gloves; case CLOAK -> cloak; case HEADPIECE -> headpiece;
        };
    }
    public Theme theme(Slot slot) {
        return switch(slot) {
            case HAIR -> details.hair(); case TORSO -> details.torso(); case PANTS -> details.pants();
            case BOOTS -> details.boots(); case GLOVES -> details.gloves(); case CLOAK -> details.cloak();
            case HEADPIECE -> Theme.CASTER;
        };
    }
    public boolean longHair() { return details.longHair(); }
}
