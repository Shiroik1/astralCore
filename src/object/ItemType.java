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
    DUAL_DAGGERS(OBJ_dualdaggers.class,OBJ_dualdaggers::new),
    TRAILWOOD_BOW(art.RangerGear.TRAIL,true), TRAIL_SCOUT_OUTFIT(art.RangerGear.TRAIL,false),
    BRIAR_BOW(art.RangerGear.BRIAR,true), BRIAR_STALKER_OUTFIT(art.RangerGear.BRIAR,false),
    RIDGEWATCH_BOW(art.RangerGear.RIDGE,true), RIDGEWATCH_OUTFIT(art.RangerGear.RIDGE,false),
    MOONBOUGH_BOW(art.RangerGear.MOON,true), MOONBOUGH_OUTFIT(art.RangerGear.MOON,false),
    SUNCREST_BOW(art.RangerGear.SUN,true), SUNCREST_OUTFIT(art.RangerGear.SUN,false),
    ALLEY_SCOUT_OUTFIT(art.AssassinGear.ALLEY,false),
    BRIARHOOK_DAGGERS(art.AssassinGear.BRIAR,true), BRIAR_SHADE_OUTFIT(art.AssassinGear.BRIAR,false),
    ASHGLASS_DAGGERS(art.AssassinGear.ASH,true), ASHVEIL_OUTFIT(art.AssassinGear.ASH,false),
    MOONSPLIT_DAGGERS(art.AssassinGear.MOON,true), MOONVEIL_OUTFIT(art.AssassinGear.MOON,false),
    DUSKFANG_DAGGERS(art.AssassinGear.GLOAM,true), GLOAM_REGENT_OUTFIT(art.AssassinGear.GLOAM,false),
    HEARTH_ROBE(art.MageGear.HEARTH,false),
    RIMEGLASS_STAFF(art.MageGear.FROST,true), FROST_SCRIBE_ROBE(art.MageGear.FROST,false),
    PRISMWEAVE_STAFF(art.MageGear.PRISM,true), PRISM_ADEPT_ROBE(art.MageGear.PRISM,false),
    STARWARD_CROOK(art.MageGear.STAR,true), STARWARD_ROBE(art.MageGear.STAR,false),
    ASTRAL_CROWN_STAFF(art.MageGear.ASTRAL,true), ASTRAL_REGENT_ROBE(art.MageGear.ASTRAL,false),
    WAYLIGHT_STAFF(art.PriestGear.WAY,true), WAYFARER_ROBE(art.PriestGear.WAY,false),
    GREENBOUGH_STAFF(art.PriestGear.GROVE,true), GROVE_KEEPER_ROBE(art.PriestGear.GROVE,false),
    VESPER_BELLSTAFF(art.PriestGear.BELL,true), BELLWARDEN_ROBE(art.PriestGear.BELL,false),
    DAWNRING_STAFF(art.PriestGear.DAWN,true), DAWN_HERALD_ROBE(art.PriestGear.DAWN,false),
    HEARTHCROWN_STAFF(art.PriestGear.HEARTH,true), SANCTUARY_KEEPER_ROBE(art.PriestGear.HEARTH,false),
    FIELDBREAKER(art.BerserkerGear.ROAD,true), ROAD_REAVER_ARMOR(art.BerserkerGear.ROAD,false),
    IRONWAKE_CLAYMORE(art.BerserkerGear.IRON,true), IRON_ROAMER_ARMOR(art.BerserkerGear.IRON,false),
    RIDGECLEAVER(art.BerserkerGear.RIDGE,true), RIDGE_VANGUARD_ARMOR(art.BerserkerGear.RIDGE,false),
    CINDERMAW(art.BerserkerGear.CINDER,true), CINDER_REAVER_ARMOR(art.BerserkerGear.CINDER,false),
    FAULTLINE_GREATSWORD(art.BerserkerGear.FAULT,true), FAULTLINE_CHAMPION_ARMOR(art.BerserkerGear.FAULT,false),
    GATEKEEPER_SWORD(art.PaladinGear.GATE,art.PaladinGear.Piece.SWORD), GATEKEEPER_ROUNDSHIELD(art.PaladinGear.GATE,art.PaladinGear.Piece.SHIELD), GATE_SENTINEL_ARMOR(art.PaladinGear.GATE,art.PaladinGear.Piece.ARMOR),
    WAYGUARD_SWORD(art.PaladinGear.ROAD,art.PaladinGear.Piece.SWORD), KITE_SHIELD(art.PaladinGear.ROAD,art.PaladinGear.Piece.SHIELD), ROAD_WARDEN_ARMOR(art.PaladinGear.ROAD,art.PaladinGear.Piece.ARMOR),
    VOWSTEEL_SWORD(art.PaladinGear.VOW,art.PaladinGear.Piece.SWORD), HEATER_SHIELD(art.PaladinGear.VOW,art.PaladinGear.Piece.SHIELD), IRON_VOW_ARMOR(art.PaladinGear.VOW,art.PaladinGear.Piece.ARMOR),
    DAWNWARD_SWORD(art.PaladinGear.DAWN,art.PaladinGear.Piece.SWORD), SUNCREST_SHIELD(art.PaladinGear.DAWN,art.PaladinGear.Piece.SHIELD), DAWN_BASTION_ARMOR(art.PaladinGear.DAWN,art.PaladinGear.Piece.ARMOR),
    CITADEL_BLADE(art.PaladinGear.CITADEL,art.PaladinGear.Piece.SWORD), BASTION_SHIELD(art.PaladinGear.CITADEL,art.PaladinGear.Piece.SHIELD), CITADEL_KEEPER_ARMOR(art.PaladinGear.CITADEL,art.PaladinGear.Piece.ARMOR);

    final Class<? extends Entity> itemClass;
    final Function<Gamepanel, Entity> factory;

    ItemType(){
        this.itemClass=OBJ_supply.class;this.factory=gp -> new OBJ_supply(gp,this);
    }
    ItemType(entity.CharacterClass characterClass,boolean hell){
        this.itemClass=OBJ_rewardweapon.class;
        this.factory=gp -> new OBJ_rewardweapon(gp,this,characterClass,hell);
    }

    ItemType(art.RangerGear gear,boolean bow){
        itemClass=bow?OBJ_rangerbow.class:OBJ_apparel.class;
        factory=bow?gp->new OBJ_rangerbow(gp,this,gear)
                :gp->new OBJ_apparel(gp,this,art.CharacterOutfit.Slot.TORSO,gear.outfit+" Leathers",
                        art.CharacterOutfit.Theme.LEATHER,false,gear);
    }

    ItemType(art.AssassinGear gear,boolean weapon){
        itemClass=weapon?OBJ_dualdaggers.class:OBJ_apparel.class;
        factory=weapon?gp->new OBJ_dualdaggers(gp,this,gear)
                :gp->new OBJ_apparel(gp,this,art.CharacterOutfit.Slot.TORSO,gear.outfit+" Leathers",
                        art.CharacterOutfit.Theme.LEATHER,false,null,gear);
    }

    ItemType(art.MageGear gear,boolean weapon){
        itemClass=weapon?OBJ_staff.class:OBJ_apparel.class;
        factory=weapon?gp->new OBJ_staff(gp,this,gear)
                :gp->new OBJ_apparel(gp,this,art.CharacterOutfit.Slot.TORSO,gear.outfit+" Robe",
                        art.CharacterOutfit.Theme.CASTER,false,null,null,gear);
    }

    ItemType(art.PriestGear gear,boolean weapon){
        itemClass=weapon?OBJ_prieststaff.class:OBJ_apparel.class;
        factory=weapon?gp->new OBJ_prieststaff(gp,this,gear)
                :gp->new OBJ_apparel(gp,this,art.CharacterOutfit.Slot.TORSO,gear.outfit+" Robe",
                        art.CharacterOutfit.Theme.CASTER,false,null,null,null,gear);
    }

    ItemType(art.BerserkerGear gear,boolean weapon){
        itemClass=weapon?OBJ_greatsword.class:OBJ_apparel.class;
        factory=weapon?gp->new OBJ_greatsword(gp,this,gear)
                :gp->new OBJ_apparel(gp,this,art.CharacterOutfit.Slot.TORSO,gear.outfit+" Armor",
                        art.CharacterOutfit.Theme.ARMOR,false,null,null,null,null,gear);
    }

    ItemType(art.PaladinGear gear,art.PaladinGear.Piece piece){
        boolean armor=piece==art.PaladinGear.Piece.ARMOR;
        itemClass=armor?OBJ_apparel.class:OBJ_paladinGear.class;
        factory=armor?gp->new OBJ_apparel(gp,this,art.CharacterOutfit.Slot.TORSO,gear.outfit+" Armor",
                art.CharacterOutfit.Theme.ARMOR,false,null,null,null,null,null,gear)
                :gp->new OBJ_paladinGear(gp,this,gear,piece==art.PaladinGear.Piece.SHIELD);
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