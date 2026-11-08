package object;

import entity.Entity;
import entity.Player;
import main.Gamepanel;
import net.GameEventType;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Stackable merchant supplies, using the same bag/consume flow as red potions. */
public final class OBJ_supply extends Entity {
    public final ItemType itemType;
    private static final java.util.Map<ItemType,BufferedImage> ICONS=new java.util.EnumMap<>(ItemType.class);
    public OBJ_supply(Gamepanel gp,ItemType itemType){
        super(gp);this.itemType=itemType;boolean mana=itemType==ItemType.POTION_MANA;
        name=mana?"Mana Potion":"Greater Health Potion";value=mana?8:12;type=type_consumable;stackable=true;maxStackSize=5;
        description="("+name+")\nRestores "+value+(mana?" MP.":" HP.");
        synchronized(ICONS){down1=ICONS.computeIfAbsent(itemType,key->{
            BufferedImage icon=new BufferedImage(gp.tileSize,gp.tileSize,BufferedImage.TYPE_INT_ARGB);var g=icon.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x453D4A));g.fillRoundRect(10,15,28,30,12,12);g.fillRect(18,5,12,15);
            g.setColor(new Color(0xDBD9D2));g.fillRoundRect(13,17,22,25,9,9);g.setColor(mana?new Color(0x559AE3):new Color(0xDE6375));g.fillRoundRect(15,24,18,16,7,7);
            g.setColor(new Color(0xB28A58));g.fillRect(19,4,10,9);g.setColor(Color.WHITE);g.fillRoundRect(17,25,3,9,2,2);g.dispose();return icon;
        });}
    }
    @Override public boolean canStackWith(Entity other){return other instanceof OBJ_supply supply && supply.itemType==itemType;}
    @Override public void use(Entity entity){
        boolean mana=itemType==ItemType.POTION_MANA;int before=mana?entity.MP:entity.HP;
        if(mana)entity.MP=Math.min(entity.maxMP,entity.MP+value);else entity.HP=Math.min(entity.maxHP,entity.HP+value);
        int restored=(mana?entity.MP:entity.HP)-before;gp.playSE(2);
        if(entity instanceof Player player){
            gp.broadcastPlayerEvent(player.playerId,GameEventType.HEAL,null,null);
            gp.spawnFloatingText(entity.worldX+gp.tileSize/2,entity.worldY,"+"+restored+(mana?" MP":" HP"),mana?Color.CYAN:new Color(120,220,120));
        }
    }
}
