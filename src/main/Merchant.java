package main;

import entity.Entity;
import entity.Player;
import net.*;
import object.*;
import java.awt.*;

/** Fixed-price stock; the host validates every purchase and sale against range and inventory. */
public final class Merchant {
    public record Offer(ItemType type,int price) {}
    public static final Offer[] STOCK={new Offer(ItemType.POTION_RED,8),new Offer(ItemType.POTION_MANA,8),
            new Offer(ItemType.POTION_GREATER,18),new Offer(ItemType.SHIELD_WOOD,20),new Offer(ItemType.AXE,16)};
    private static final Offer[] RANGER_STOCK={STOCK[0],STOCK[1],STOCK[2],
            new Offer(ItemType.TRAILWOOD_BOW,8),new Offer(ItemType.TRAIL_SCOUT_OUTFIT,12),
            new Offer(ItemType.BRIAR_BOW,25),new Offer(ItemType.BRIAR_STALKER_OUTFIT,32),
            new Offer(ItemType.RIDGEWATCH_BOW,50),new Offer(ItemType.RIDGEWATCH_OUTFIT,62),
            new Offer(ItemType.MOONBOUGH_BOW,95),new Offer(ItemType.MOONBOUGH_OUTFIT,110),
            new Offer(ItemType.SUNCREST_BOW,160),new Offer(ItemType.SUNCREST_OUTFIT,185)};
    private static final Offer[] ASSASSIN_STOCK={STOCK[0],STOCK[1],STOCK[2],
            new Offer(ItemType.DUAL_DAGGERS,8),new Offer(ItemType.ALLEY_SCOUT_OUTFIT,12),
            new Offer(ItemType.BRIARHOOK_DAGGERS,25),new Offer(ItemType.BRIAR_SHADE_OUTFIT,32),
            new Offer(ItemType.ASHGLASS_DAGGERS,50),new Offer(ItemType.ASHVEIL_OUTFIT,62),
            new Offer(ItemType.MOONSPLIT_DAGGERS,95),new Offer(ItemType.MOONVEIL_OUTFIT,110),
            new Offer(ItemType.DUSKFANG_DAGGERS,160),new Offer(ItemType.GLOAM_REGENT_OUTFIT,185)};
    private static final Offer[] MAGE_STOCK={STOCK[0],STOCK[1],STOCK[2],
            new Offer(ItemType.STAFF,8),new Offer(ItemType.HEARTH_ROBE,12),
            new Offer(ItemType.RIMEGLASS_STAFF,25),new Offer(ItemType.FROST_SCRIBE_ROBE,32),
            new Offer(ItemType.PRISMWEAVE_STAFF,50),new Offer(ItemType.PRISM_ADEPT_ROBE,62),
            new Offer(ItemType.STARWARD_CROOK,95),new Offer(ItemType.STARWARD_ROBE,110),
            new Offer(ItemType.ASTRAL_CROWN_STAFF,160),new Offer(ItemType.ASTRAL_REGENT_ROBE,185)};
    private static final Offer[] PRIEST_STOCK={STOCK[0],STOCK[1],STOCK[2],
            new Offer(ItemType.WAYLIGHT_STAFF,8),new Offer(ItemType.WAYFARER_ROBE,12),
            new Offer(ItemType.GREENBOUGH_STAFF,25),new Offer(ItemType.GROVE_KEEPER_ROBE,32),
            new Offer(ItemType.VESPER_BELLSTAFF,50),new Offer(ItemType.BELLWARDEN_ROBE,62),
            new Offer(ItemType.DAWNRING_STAFF,95),new Offer(ItemType.DAWN_HERALD_ROBE,110),
            new Offer(ItemType.HEARTHCROWN_STAFF,160),new Offer(ItemType.SANCTUARY_KEEPER_ROBE,185)};
    private static final Offer[] BERSERKER_STOCK={STOCK[0],STOCK[1],STOCK[2],
            new Offer(ItemType.FIELDBREAKER,8),new Offer(ItemType.ROAD_REAVER_ARMOR,12),
            new Offer(ItemType.IRONWAKE_CLAYMORE,25),new Offer(ItemType.IRON_ROAMER_ARMOR,32),
            new Offer(ItemType.RIDGECLEAVER,50),new Offer(ItemType.RIDGE_VANGUARD_ARMOR,62),
            new Offer(ItemType.CINDERMAW,95),new Offer(ItemType.CINDER_REAVER_ARMOR,110),
            new Offer(ItemType.FAULTLINE_GREATSWORD,160),new Offer(ItemType.FAULTLINE_CHAMPION_ARMOR,185)};
    private static final Offer[] PALADIN_STOCK={STOCK[0],STOCK[1],STOCK[2],
            new Offer(ItemType.GATEKEEPER_SWORD,8),new Offer(ItemType.GATEKEEPER_ROUNDSHIELD,8),new Offer(ItemType.GATE_SENTINEL_ARMOR,12),
            new Offer(ItemType.WAYGUARD_SWORD,25),new Offer(ItemType.KITE_SHIELD,24),new Offer(ItemType.ROAD_WARDEN_ARMOR,32),
            new Offer(ItemType.VOWSTEEL_SWORD,50),new Offer(ItemType.HEATER_SHIELD,48),new Offer(ItemType.IRON_VOW_ARMOR,62),
            new Offer(ItemType.DAWNWARD_SWORD,95),new Offer(ItemType.SUNCREST_SHIELD,91),new Offer(ItemType.DAWN_BASTION_ARMOR,110),
            new Offer(ItemType.CITADEL_BLADE,160),new Offer(ItemType.BASTION_SHIELD,154),new Offer(ItemType.CITADEL_KEEPER_ARMOR,185)};
    private final Gamepanel gp;
    private final java.util.EnumMap<ItemType,Entity> stockItems=new java.util.EnumMap<>(ItemType.class);
    public Merchant(Gamepanel gp){this.gp=gp;}
    public void prepare(){
        for(Offer offer:STOCK)stockItem(offer);
    }

    private Entity stockItem(Offer offer){return stockItems.computeIfAbsent(offer.type,type->ItemRegistry.create(type,gp));}
    private static Offer[] stock(Player p){return p.isRanger()?RANGER_STOCK:p.isAssassin()?ASSASSIN_STOCK:p.isMage()?MAGE_STOCK:p.isPriest()?PRIEST_STOCK:p.isBerserker()?BERSERKER_STOCK:p.isPaladin()?PALADIN_STOCK:STOCK;}
    private static int requiredLevel(Entity item){
        if(item instanceof OBJ_rangerbow bow)return bow.gear.level;
        if(item instanceof OBJ_dualdaggers daggers)return daggers.gear.level;
        if(item instanceof OBJ_staff staff)return staff.gear.level;
        if(item instanceof OBJ_prieststaff staff)return staff.gear.level;
        if(item instanceof OBJ_greatsword sword)return sword.gear.level;
        if(item instanceof OBJ_paladinGear gear)return gear.gear.level;
        if(item instanceof OBJ_apparel apparel){
            if(apparel.rangerGear!=null)return apparel.rangerGear.level;
            if(apparel.assassinGear!=null)return apparel.assassinGear.level;
            if(apparel.mageGear!=null)return apparel.mageGear.level;
            if(apparel.priestGear!=null)return apparel.priestGear.level;
            if(apparel.berserkerGear!=null)return apparel.berserkerGear.level;
            if(apparel.paladinGear!=null)return apparel.paladinGear.level;
        }
        return 1;
    }
    public void open(Player player){
        player.resetForInteraction();player.shopChangedInputTick=player.currentInput!=null?player.currentInput.tick:gp.currentTick;player.merchantOpen=true;player.shopMessage="Buy supplies, or click a weapon in your bag to sell it.";
        if(player.isLocal){gp.inventoryOpen=false;gp.mouseH.leftClicked=false;gp.mouseH.leftPressed=false;}
    }
    public void close(){
        Player p=gp.localPlayer();if(p==null)return;
        InventoryAction action=new InventoryAction();action.playerId=p.playerId;action.actionType=InventoryActionType.SHOP_CLOSE;
        gp.requestInventoryAction(action);p.shopChangedInputTick=gp.currentTick;p.merchantOpen=false;
    }
    public boolean inRange(Player p){
        if(p.isDead || gp.quests.inDungeon())return false;
        for(Entity npc:gp.npc)if(npc!=null && "merchant".equals(npc.npcId) && Math.hypot(p.worldX-npc.worldX,p.worldY-npc.worldY)<=gp.tileSize*2)return true;
        return false;
    }
    private void say(Player p,String text){gp.broadcastPlayerEvent(p.playerId,GameEventType.SHOP,null,text);}
    public void transact(Player p,InventoryAction action){
        if(action.actionType==InventoryActionType.SHOP_CLOSE){p.merchantOpen=false;return;}
        if(!p.canResolveWorldActions() || !p.merchantOpen || !inRange(p)){say(p,"Stand beside the merchant to trade.");return;}
        if(action.actionType==InventoryActionType.BUY){
            Offer[] stock=stock(p);
            int index=action.sourceIndex;if(index<0 || index>=stock.length)return;Offer offer=stock[index];
            int requiredLevel=requiredLevel(stockItem(offer));
            if(p.level<requiredLevel){say(p,"This gear unlocks at level "+requiredLevel+".");return;}
            if(p.coin<offer.price){say(p,"Not enough coins.");return;}
            Entity item=ItemRegistry.create(offer.type,gp);
            if(!p.addToInventory(item)){say(p,"Your bag is full. Sell a weapon or make room first.");return;}
            p.coin-=offer.price;say(p,"Bought "+item.name+" for "+offer.price+" coins.");gp.playSE(1);
        }else if(action.actionType==InventoryActionType.SELL){
            int slot=action.sourceIndex;if(slot<0 || slot>=p.inventorySlots.length)return;Entity item=p.inventorySlots[slot];int price=sellPrice(item);
            if(price==0){say(p,"I only buy weapons. Equipped gear must be moved to your bag first.");return;}
            p.inventorySlots[slot]=null;p.coin+=price;say(p,"Sold "+item.name+" for "+price+" coins.");gp.playSE(1);
        }
    }
    public static int sellPrice(Entity item){
        if(item==null || item.isPlaceholder)return 0;
        ItemType type=ItemRegistry.typeFor(item);if(type==null)return 0;
        if(item instanceof OBJ_rewardweapon weapon)return weapon.kind.isHell()?40:18;
        if(item instanceof OBJ_rangerbow bow)return 4+bow.gear.ordinal()*8;
        if(item instanceof OBJ_dualdaggers daggers)return 4+daggers.gear.ordinal()*8;
        if(item instanceof OBJ_staff staff)return 5+staff.gear.ordinal()*8;
        if(item instanceof OBJ_prieststaff staff)return 5+staff.gear.ordinal()*8;
        if(item instanceof OBJ_greatsword sword)return 5+sword.gear.ordinal()*8;
        if(item instanceof OBJ_paladinGear gear && item.type==Entity.type_sword)return 5+gear.gear.ordinal()*8;
        return switch(type){case SWORD_NORMAL,DUAL_DAGGERS->4;case AXE->6;case STAFF->5;default->0;};
    }
    private int left(){return (gp.screenWidth-860)/2;}
    private int top(){return (gp.screenHeight-460)/2;}
    private Rectangle closeBounds(){return new Rectangle(left()+740,top()+18,96,30);}
    private Rectangle buyBounds(int i){
        if(gp.localPlayer().isPaladin())return new Rectangle(left()+28+(i%5)*58,top()+104+(i/5)*52,48,48);
        if(stock(gp.localPlayer()).length>STOCK.length)return new Rectangle(left()+28+(i%5)*61,top()+104+(i/5)*61,54,54);
        return new Rectangle(left()+28+(i%3)*72,top()+104+(i/3)*72,64,64);
    }
    private Rectangle sellBounds(int i){return new Rectangle(left()+350+(i%5)*72,top()+104+(i/5)*72,64,64);}
    public void update(int x,int y,boolean clicked){
        if(!clicked)return;if(closeBounds().contains(x,y)){close();return;}
        Player p=gp.localPlayer();
        InventoryAction action=new InventoryAction();action.playerId=p.playerId;
        for(int i=0;i<stock(p).length;i++)if(buyBounds(i).contains(x,y)){action.actionType=InventoryActionType.BUY;action.sourceIndex=i;gp.requestInventoryAction(action);return;}
        for(int i=0;i<20;i++)if(sellBounds(i).contains(x,y)){action.actionType=InventoryActionType.SELL;action.sourceIndex=i;gp.requestInventoryAction(action);return;}
    }
    public void draw(Graphics2D g){
        prepare();Player p=gp.localPlayer();int x=left(),y=top();WindowStyle.draw(g,x,y,860,460);
        Font font=gp.ui.jetbrainsMono;g.setFont(font.deriveFont(Font.BOLD,21f));g.setColor(Color.WHITE);g.drawString("Mira's Trading Post",x+25,y+39);
        g.setFont(font.deriveFont(14f));g.drawString("Coins: "+p.coin,x+570,y+37);box(g,closeBounds());g.drawString("Close [Esc]",x+747,y+38);
        g.setFont(font.deriveFont(Font.BOLD,15f));g.drawString("Buy",x+28,y+85);g.drawString("Sell weapons",x+350,y+85);
        Entity hovered=null;String price="";
        Offer[] stock=stock(p);
        for(int i=0;i<stock.length;i++){
            Rectangle r=buyBounds(i);Entity item=stockItem(stock[i]);box(g,r);
            g.drawImage(item.down1,r.x+8,r.y+8,r.width-16,r.height-16,null);
            int level=requiredLevel(item);
            if(level>p.level){
                g.setColor(new Color(17,23,34,170));g.fillRoundRect(r.x+4,r.y+r.height-22,r.width-8,18,6,6);
                g.setFont(font.deriveFont(11f));g.setColor(Color.WHITE);g.drawString("Lv "+level,r.x+9,r.y+r.height-9);
            }
            if(r.contains(gp.mouseH.getScaledX(),gp.mouseH.getScaledY())){hovered=item;price="Buy: "+stock[i].price+" coins";}
        }
        for(int i=0;i<20;i++){
            Rectangle r=sellBounds(i);box(g,r);Entity item=p.inventorySlots[i];
            if(item!=null){g.drawImage(item.down1,r.x+8,r.y+8,48,48,null);
                if(item.stackable && item.stackCount>1){g.setColor(Color.WHITE);g.setFont(font.deriveFont(12f));g.drawString(""+item.stackCount,r.x+49,r.y+58);}
                if(r.contains(gp.mouseH.getScaledX(),gp.mouseH.getScaledY())){hovered=item;int value=sellPrice(item);price=value>0?"Sell: "+value+" coins":"This item is not bought.";}
            }
        }
        g.setColor(Color.WHITE);g.setFont(font.deriveFont(12f));g.drawString("Hover for item details. Click to buy or sell.",x+28,y+330);
        g.drawString("Equipped weapons must be moved to your bag first.",x+28,y+410);
        g.drawString(p.shopMessage,x+28,y+435);
        if(hovered!=null){
            var lines=new java.util.ArrayList<String>();lines.add(hovered.name);
            if(hovered.description!=null)for(String line:hovered.description.split("\\n"))if(!line.startsWith("("))lines.add(line);
            lines.add(price);tooltip(g,lines,gp.mouseH.getScaledX()+18,gp.mouseH.getScaledY()+18);
        }
    }
    public void tooltip(Graphics2D g,java.util.List<String> lines,int x,int y){
        g.setFont(gp.ui.jetbrainsMono.deriveFont(13f));int width=220;for(String line:lines)width=Math.max(width,g.getFontMetrics().stringWidth(line)+28);
        int height=lines.size()*22+22;x=Math.min(x,gp.screenWidth-width-10);y=Math.min(y,gp.screenHeight-height-10);
        WindowStyle.draw(g,x,y,width,height);g.setColor(Color.WHITE);int baseline=y+25;for(String line:lines){g.drawString(line,x+14,baseline);baseline+=22;}
    }
    private void box(Graphics2D g,Rectangle r){WindowStyle.draw(g,r.x,r.y,r.width,r.height);}
}
