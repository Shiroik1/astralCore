package crafting;

import main.Gamepanel;
import java.awt.*;
import object.ItemType;

/** Java2D overlay using the existing game's fonts and inventory; keyboard and mouse controls. */
public final class CraftingUI {
    private final CraftingManager manager;private final Gamepanel gp;
    private int selected,first;public int quantity=1;
    private Rectangle craft=new Rectangle(),place=new Rectangle(),minus=new Rectangle(),plus=new Rectangle();
    private final java.util.Map<Integer,Rectangle> rows=new java.util.LinkedHashMap<>();
    CraftingUI(CraftingManager manager,Gamepanel gp){this.manager=manager;this.gp=gp;}
    public RecipeRegistry.Recipe recipe(){return RecipeRegistry.RECIPES.get(selected);}
    public void select(int delta){selected=Math.floorMod(selected+delta,RecipeRegistry.RECIPES.size());}
    public void click(int x,int y){
        for(var row:rows.entrySet())if(row.getValue().contains(x,y)){selected=row.getKey();return;}
        if(craft.contains(x,y))manager.craft(recipe(),batches());
        else if(place.contains(x,y))manager.beginPlace(recipe().output());
        else if(plus.contains(x,y) && gp.localPlayer().craftingProgress.level>=9)quantity=Math.min(99,quantity+1);
        else if(minus.contains(x,y))quantity=Math.max(1,quantity-1);
    }
    private int batches(){return gp.localPlayer().craftingProgress.level>=9?quantity:1;}
    public void draw(Graphics2D original,Font font){
        if(!manager.open)return;
        Graphics2D g=(Graphics2D)original.create();g.setFont(font.deriveFont(13f));
        int w=Math.min(850,gp.screenWidth-32),h=Math.min(570,gp.screenHeight-36),x=(gp.screenWidth-w)/2,y=(gp.screenHeight-h)/2;
        g.setColor(new Color(24,29,34,245));g.fillRoundRect(x,y,w,h,16,16);g.setColor(new Color(0xB8A58B));g.drawRoundRect(x,y,w,h,16,16);
        var progress=gp.localPlayer().craftingProgress;
        g.setColor(new Color(0xF2E9D6));g.setFont(font.deriveFont(Font.BOLD,17f));g.drawString("Crafting · Level "+progress.level,x+18,y+28);
        g.setFont(font.deriveFont(12f));g.drawString("XP "+progress.xp+" / "+(progress.next()==0?"MAX":progress.next())+"    K / Esc close",x+18,y+48);
        int count=Math.max(3,(h-135)/26),left=w/2;
        g.setColor(new Color(0x414448));g.fillRoundRect(x+18,y+54,left-32,4,4,4);
        g.setColor(new Color(0x80B772));double fill=progress.next()==0?1:progress.xp/(double)progress.next();g.fillRoundRect(x+18,y+54,(int)((left-32)*fill),4,4,4);
        if(selected<first)first=selected;if(selected>=first+count)first=selected-count+1;rows.clear();
        for(int i=first;i<Math.min(first+count,RecipeRegistry.RECIPES.size());i++){
            var r=RecipeRegistry.RECIPES.get(i);int ry=y+67+(i-first)*26;Rectangle rect=new Rectangle(x+12,ry,left-20,25);rows.put(i,rect);
            if(i==selected){g.setColor(new Color(0x4E5C65));g.fillRoundRect(rect.x,rect.y,rect.width,rect.height,5,5);}
            g.setColor(progress.level>=r.level()?new Color(0xF2E9D6):new Color(0x948D90));
            g.drawString("L"+r.level()+" "+RecipeRegistry.item(r.output()).name(),rect.x+7,ry+17);
        }
        var r=recipe();var item=RecipeRegistry.item(r.output());int dx=x+left+10,dy=y+82;
        g.drawImage(CraftingArt.icon(item),dx,dy,48,48,null);g.setColor(new Color(0xF2E9D6));g.drawString(item.name()+" × "+r.count()*batches(),dx+57,dy+20);
        g.drawString("Station: "+r.station(),dx,dy+70);g.drawString("Requires Crafting L"+r.level(),dx,dy+92);
        int line=dy+123;for(var ingredient:r.ingredients().entrySet()){
            int owned=InventoryTransaction.count(gp.localPlayer(),ingredient.getKey()),need=ingredient.getValue()*batches();
            g.setColor(owned>=need?new Color(0xADD786):new Color(0xCE7D69));g.drawString(RecipeRegistry.item(ingredient.getKey()).name()+": "+owned+" / "+need,dx,line);line+=23;
        }
        int buttons=y+h-125;String error=manager.craftError(r,batches());
        minus=new Rectangle(dx,buttons,28,26);plus=new Rectangle(dx+100,buttons,28,26);
        button(g,minus,"−",true);button(g,plus,"+",progress.level>=9);g.setColor(Color.WHITE);g.drawString("× "+batches(),dx+42,buttons+18);
        craft=new Rectangle(dx,buttons+36,115,30);place=new Rectangle(dx+125,buttons+36,115,30);
        button(g,craft,"Enter: Craft",error==null);button(g,place,"P: Place",!item.place().isEmpty() && InventoryTransaction.count(gp.localPlayer(),item.type())>0);
        g.setColor(new Color(0xD9B88C));String info=error==null?"Ready · Up/Down select · batch at L9":error;
        g.drawString(info.length()>52?info.substring(0,52):info,dx,y+h-38);
        g.setColor(new Color(0xF2E9D6));String message=manager.feedback;g.drawString(message.length()>100?message.substring(0,100):message,x+18,y+h-15);g.dispose();
    }
    private void button(Graphics2D g,Rectangle r,String text,boolean enabled){g.setColor(new Color(enabled?0x52684F:0x414448));g.fillRoundRect(r.x,r.y,r.width,r.height,5,5);g.setColor(enabled?Color.WHITE:Color.GRAY);g.drawString(text,r.x+6,r.y+19);}
}
