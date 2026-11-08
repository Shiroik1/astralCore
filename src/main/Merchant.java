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
    private final Gamepanel gp;
    private final Entity[] stockItems=new Entity[STOCK.length];
    public Merchant(Gamepanel gp){this.gp=gp;}
    public void prepare(){for(int i=0;i<STOCK.length;i++)if(stockItems[i]==null)stockItems[i]=ItemRegistry.create(STOCK[i].type,gp);}
    public void open(Player player){
        player.resetForSceneTransition();player.shopChangedInputTick=player.currentInput!=null?player.currentInput.tick:gp.currentTick;player.merchantOpen=true;player.shopMessage="Buy supplies, or click a weapon in your bag to sell it.";
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
            int index=action.sourceIndex;if(index<0 || index>=STOCK.length)return;Offer offer=STOCK[index];
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
        return switch(type){case SWORD_NORMAL,DUAL_DAGGERS->4;case AXE->6;case STAFF->5;default->0;};
    }
    private int left(){return (gp.screenWidth-860)/2;}
    private int top(){return (gp.screenHeight-460)/2;}
    private Rectangle closeBounds(){return new Rectangle(left()+740,top()+18,96,30);}
    private Rectangle buyBounds(int i){return new Rectangle(left()+28+(i%3)*72,top()+104+(i/3)*72,64,64);}
    private Rectangle sellBounds(int i){return new Rectangle(left()+350+(i%5)*72,top()+104+(i/5)*72,64,64);}
    public void update(int x,int y,boolean clicked){
        if(!clicked)return;if(closeBounds().contains(x,y)){close();return;}
        InventoryAction action=new InventoryAction();action.playerId=gp.localPlayer().playerId;
        for(int i=0;i<STOCK.length;i++)if(buyBounds(i).contains(x,y)){action.actionType=InventoryActionType.BUY;action.sourceIndex=i;gp.requestInventoryAction(action);return;}
        for(int i=0;i<20;i++)if(sellBounds(i).contains(x,y)){action.actionType=InventoryActionType.SELL;action.sourceIndex=i;gp.requestInventoryAction(action);return;}
    }
    public void draw(Graphics2D g){
        prepare();Player p=gp.localPlayer();int x=left(),y=top();WindowStyle.draw(g,x,y,860,460);
        Font font=gp.ui.jetbrainsMono;g.setFont(font.deriveFont(Font.BOLD,21f));g.setColor(Color.WHITE);g.drawString("Mira's Trading Post",x+25,y+39);
        g.setFont(font.deriveFont(14f));g.drawString("Coins: "+p.coin,x+570,y+37);box(g,closeBounds());g.drawString("Close [Esc]",x+747,y+38);
        g.setFont(font.deriveFont(Font.BOLD,15f));g.drawString("Buy",x+28,y+85);g.drawString("Sell weapons",x+350,y+85);
        Entity hovered=null;String price="";
        for(int i=0;i<STOCK.length;i++){
            Rectangle r=buyBounds(i);box(g,r);g.drawImage(stockItems[i].down1,r.x+8,r.y+8,48,48,null);
            if(r.contains(gp.mouseH.getScaledX(),gp.mouseH.getScaledY())){hovered=stockItems[i];price="Buy: "+STOCK[i].price+" coins";}
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
