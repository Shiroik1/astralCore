package object;

import art.WeaponArt;
import entity.CharacterClass;
import entity.Entity;
import main.Gamepanel;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Class rewards share the existing equipment and combat rules, with their own held artwork. */
public final class OBJ_rewardweapon extends Entity {
    public final ItemType itemType;
    public final WeaponArt.Kind kind;
    private static final java.util.Map<ItemType,BufferedImage> ICONS=new java.util.EnumMap<>(ItemType.class);
    public OBJ_rewardweapon(Gamepanel gp,ItemType itemType,CharacterClass characterClass,boolean hell){
        super(gp);this.itemType=itemType;
        String weapon=switch(characterClass){case HUNTER->"Dagger";case RANGER,PRIEST,BERSERKER,PALADIN->throw new IllegalArgumentException("This class uses its own gear tiers");case MAGE->"Staff";case WARRIOR->"Sword";};
        name=(hell?"Hellfire ":"Royal Slime ")+weapon;
        kind=WeaponArt.Kind.valueOf((hell?"HELL_":"ROYAL_")+weapon.toUpperCase(java.util.Locale.ROOT));
        type=characterClass==CharacterClass.MAGE?type_staff:type_sword;
        attackValue=hell?7:4;
        attackArea.width=switch(characterClass){case HUNTER,RANGER->60;case WARRIOR,BERSERKER,PALADIN->68;case MAGE,PRIEST->40;};
        attackArea.height=characterClass==CharacterClass.WARRIOR?66:attackArea.width;
        if(characterClass==CharacterClass.HUNTER)name+="s";
        description="("+name+")\nAttack x"+attackValue+"\n"+(hell?"Forged in the well's hellfire.":"A reward for clearing the well.");
        synchronized(ICONS){down1=ICONS.computeIfAbsent(itemType,key->{
            BufferedImage image=new BufferedImage(gp.tileSize,gp.tileSize,BufferedImage.TYPE_INT_ARGB);
            var g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            WeaponArt.draw(g,kind,24,33,25,1,0,0,0,1.25f);g.dispose();return image;
        });}
    }
    public static ItemType forClass(String classId,boolean hell){
        return switch(CharacterClass.fromId(classId)){
            case RANGER -> hell?ItemType.SUNCREST_BOW:ItemType.MOONBOUGH_BOW;
            case HUNTER -> hell?ItemType.HELL_DAGGER:ItemType.ROYAL_DAGGER;
            case PALADIN -> hell?ItemType.CITADEL_BLADE:ItemType.DAWNWARD_SWORD;
            case BERSERKER -> hell?ItemType.FAULTLINE_GREATSWORD:ItemType.CINDERMAW;
            case PRIEST -> hell?ItemType.HEARTHCROWN_STAFF:ItemType.DAWNRING_STAFF;
            case MAGE -> hell?ItemType.HELL_STAFF:ItemType.ROYAL_STAFF;
            case WARRIOR -> hell?ItemType.HELL_SWORD:ItemType.ROYAL_SWORD;
        };
    }
}
