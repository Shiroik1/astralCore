package entity;

import art.CharacterOutfit;

/** Class choices and starter appearance presets. */
public enum CharacterClass {
    HUNTER("hunter", "Hunter", "Leather", "Dual daggers", CharacterOutfit.Theme.LEATHER),
    MAGE("mage", "Mage", "Caster", "Staff / Grimoire", CharacterOutfit.Theme.CASTER),
    WARRIOR("warrior", "Warrior", "Armor", "One-handed sword & shield", CharacterOutfit.Theme.ARMOR);

    public final String id, label, armorLabel, weaponLabel;
    public final CharacterOutfit.Theme theme;
    CharacterClass(String id, String label, String armorLabel, String weaponLabel, CharacterOutfit.Theme theme) {
        this.id=id;this.label=label;this.armorLabel=armorLabel;this.weaponLabel=weaponLabel;this.theme=theme;
    }
    public static CharacterClass fromId(String id) {
        for(CharacterClass choice:values()) if(choice.id.equalsIgnoreCase(id))return choice;
        return WARRIOR; // includes the former "swordsman" identifier
    }
    public static CharacterClass at(int index) { return values()[Math.floorMod(index,values().length)]; }
    public CharacterOutfit outfit(boolean female) { return CharacterOutfit.themed(theme,female); }
}
