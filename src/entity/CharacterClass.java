package entity;

import art.CharacterOutfit;

/** Class choices and starter appearance presets. */
public enum CharacterClass {
    HUNTER("hunter", "Hunter · Assassin", "Leather", "Dual daggers · Close-range DPS", CharacterOutfit.Theme.LEATHER),
    MAGE("mage", "Caster · Mage", "Cloth", "Staff · Area DPS", CharacterOutfit.Theme.CASTER),
    WARRIOR("warrior", "Warrior", "Armor", "One-handed sword & shield", CharacterOutfit.Theme.ARMOR),
    RANGER("ranger", "Hunter · Ranger", "Leather", "Bow · Precision ranged DPS", CharacterOutfit.Theme.LEATHER),
    PRIEST("priest", "Caster · Priest", "Cloth", "Staff · Healer and buffer", CharacterOutfit.Theme.CASTER),
    BERSERKER("berserker", "Warrior · Berserker", "Armor", "Greatsword · Close-range DPS", CharacterOutfit.Theme.ARMOR),
    PALADIN("paladin", "Warrior · Paladin", "Armor", "Sword and shield · Tank", CharacterOutfit.Theme.ARMOR);

    public final String id, label, armorLabel, weaponLabel;
    public final CharacterOutfit.Theme theme;
    CharacterClass(String id, String label, String armorLabel, String weaponLabel, CharacterOutfit.Theme theme) {
        this.id=id;this.label=label;this.armorLabel=armorLabel;this.weaponLabel=weaponLabel;this.theme=theme;
    }
    public static CharacterClass fromId(String id) {
        for(CharacterClass choice:values()) if(choice.id.equalsIgnoreCase(id))return choice;
        return WARRIOR; // includes the former "swordsman" identifier
    }
    public static final java.util.List<CharacterClass> CREATION_CHOICES=java.util.List.of(HUNTER,MAGE,PALADIN,RANGER,PRIEST,BERSERKER);
    public static CharacterClass at(int index) { return CREATION_CHOICES.get(Math.floorMod(index,CREATION_CHOICES.size())); }
    public CharacterOutfit outfit(boolean female) {
        CharacterOutfit base=CharacterOutfit.themed(theme,female);
        if(this==HUNTER){
            var gear=art.AssassinGear.ALLEY;
            return new CharacterOutfit(base.hair(),gear.tunic,gear.pants(),gear.boots(),gear.gloves(),gear.cloak,base.details(),null);
        }
        if(this==MAGE){
            var gear=art.MageGear.HEARTH;
            return new CharacterOutfit(base.hair(),gear.robe,gear.pants(),gear.boots(),gear.gloves(),gear.mantle,base.details(),null);
        }
        if(this==PRIEST){
            var gear=art.PriestGear.WAY;
            return new CharacterOutfit(base.hair(),gear.robe,gear.pants(),gear.boots(),gear.gloves(),gear.mantle,base.details(),null);
        }
        if(this==BERSERKER){
            var gear=art.BerserkerGear.ROAD;
            return new CharacterOutfit(base.hair(),gear.armorColor,gear.pants(),gear.boots(),gear.gloves(),gear.scarf,base.details(),null);
        }
        if(this==PALADIN){
            var gear=art.PaladinGear.GATE;
            return new CharacterOutfit(base.hair(),gear.plate,gear.pants(),gear.boots(),gear.gloves(),gear.mantle(),base.details(),null);
        }
        if(this!=RANGER)return base;
        var gear=art.RangerGear.TRAIL;
        return new CharacterOutfit(base.hair(),gear.tunic,gear.pants(),gear.boots(),gear.gloves(),gear.cloak,base.details(),null);
    }
}
