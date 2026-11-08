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
    CLOAK(art.CharacterOutfit.Slot.CLOAK, "Teal Cloak");

    final Class<? extends Entity> itemClass;
    final Function<Gamepanel, Entity> factory;

    ItemType(art.CharacterOutfit.Slot slot, String label){
        this.itemClass=OBJ_apparel.class;
        this.factory=gp -> new OBJ_apparel(gp,this,slot,label);
    }

    ItemType(Class<? extends Entity> itemClass, Function<Gamepanel, Entity> factory){
        this.itemClass = itemClass;
        this.factory = factory;
    }
}