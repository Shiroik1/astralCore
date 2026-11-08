package object;

import entity.Entity;
import main.Gamepanel;

import java.util.function.Function;

public enum ItemType {
    POTION_RED(OBJ_potion_red.class, OBJ_potion_red::new),
    SWORD_NORMAL(OBJ_sword_normal.class, OBJ_sword_normal::new),
    AXE(OBJ_axe.class, OBJ_axe::new),
    SHIELD_WOOD(OBJ_shield_wood.class, OBJ_shield_wood::new),
    BLUE_SHIELD(OBJ_blueshield.class, OBJ_blueshield::new),
    KEY(OBJ_key.class, OBJ_key::new),
    DOOR(OBJ_door.class, OBJ_door::new),
    CHEST(OBJ_chest.class, OBJ_chest::new),
    BOOTS(OBJ_boots.class, OBJ_boots::new),
    HEART(OBJ_heart.class, OBJ_heart::new),
    CRYSTAL(OBJ_crystal.class, OBJ_crystal::new),
    BRONZE_COIN(OBJ_bronzecoin.class, OBJ_bronzecoin::new),
    FISTS(OBJ_fists.class, OBJ_fists::new),
    NO_SHIELD(OBJ_no_shield.class, OBJ_no_shield::new),
    STAFF(OBJ_staff.class, OBJ_staff::new),
    HAIR(art.CharacterOutfit.Slot.HAIR, "Tousled Hair"),
    TUNIC(art.CharacterOutfit.Slot.TORSO, "Adventurer Tunic"),
    PANTS(art.CharacterOutfit.Slot.PANTS, "Adventurer Pants"),
    GLOVES(art.CharacterOutfit.Slot.GLOVES, "Leather Gloves"),
    CLOAK(art.CharacterOutfit.Slot.CLOAK, "Teal Cloak"),
    LONG_HAIR(art.CharacterOutfit.Slot.HAIR, "Long Brown Hair"),
    MAGE_HAIR(art.CharacterOutfit.Slot.HAIR, "Short Lilac Hair"),
    MAGE_LONG_HAIR(art.CharacterOutfit.Slot.HAIR, "Long Lilac Hair"),
    MAGE_ROBE(art.CharacterOutfit.Slot.TORSO, "Ivory Mage Robe"),
    MAGE_PANTS(art.CharacterOutfit.Slot.PANTS, "Mage Trousers"),
    MAGE_BOOTS(art.CharacterOutfit.Slot.BOOTS, "Mage Boots"),
    MAGE_GLOVES(art.CharacterOutfit.Slot.GLOVES, "Mage Gloves"),
    MAGE_CLOAK(art.CharacterOutfit.Slot.CLOAK, "Violet Mantle"),
    WARRIOR_HAIR(art.CharacterOutfit.Slot.HAIR, "Short Golden Hair"),
    WARRIOR_LONG_HAIR(art.CharacterOutfit.Slot.HAIR, "Long Golden Hair"),
    WARRIOR_ARMOR(art.CharacterOutfit.Slot.TORSO, "Silver Cuirass"),
    WARRIOR_PANTS(art.CharacterOutfit.Slot.PANTS, "Warrior Trousers"),
    WARRIOR_BOOTS(art.CharacterOutfit.Slot.BOOTS, "Plate Greaves"),
    WARRIOR_GLOVES(art.CharacterOutfit.Slot.GLOVES, "Plate Gauntlets"),
    WARRIOR_CLOAK(art.CharacterOutfit.Slot.CLOAK, "Crimson Cape"),
    MAGE_HAT(art.CharacterOutfit.Slot.HEADPIECE, "Mage Hat"),
    SLIME_ESSENCE(OBJ_slimeessence.class,OBJ_slimeessence::new),
    ROYAL_SWORD(entity.CharacterClass.WARRIOR,false),
    ROYAL_DAGGER(entity.CharacterClass.HUNTER,false),
    ROYAL_STAFF(entity.CharacterClass.MAGE,false),
    HELL_SWORD(entity.CharacterClass.WARRIOR,true),
    HELL_DAGGER(entity.CharacterClass.HUNTER,true),
    HELL_STAFF(entity.CharacterClass.MAGE,true),
    POTION_MANA, POTION_GREATER,
    DUAL_DAGGERS(OBJ_dualdaggers.class,OBJ_dualdaggers::new);

    final Class<? extends Entity> itemClass;
    final Function<Gamepanel, Entity> factory;

    ItemType(){
        this.itemClass=OBJ_supply.class;this.factory=gp -> new OBJ_supply(gp,this);
    }
    ItemType(entity.CharacterClass characterClass,boolean hell){
        this.itemClass=OBJ_rewardweapon.class;
        this.factory=gp -> new OBJ_rewardweapon(gp,this,characterClass,hell);
    }

    ItemType(art.CharacterOutfit.Slot slot, String label){
        this.itemClass=OBJ_apparel.class;
        art.CharacterOutfit.Theme theme = name().startsWith("MAGE_") ? art.CharacterOutfit.Theme.CASTER
                : name().startsWith("WARRIOR_") ? art.CharacterOutfit.Theme.ARMOR : art.CharacterOutfit.Theme.LEATHER;
        boolean longHair=name().contains("LONG_HAIR");
        this.factory=gp -> new OBJ_apparel(gp,this,slot,label,theme,longHair);
    }

    ItemType(Class<? extends Entity> itemClass, Function<Gamepanel, Entity> factory){
        this.itemClass = itemClass;
        this.factory = factory;
    }
}