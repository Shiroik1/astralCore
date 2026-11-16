package crafting;

import entity.*;
import main.*;
import net.InputState;
import object.*;
import world.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Offline overworld prototype, matching the existing building subsystem's authority boundary. */
public final class CraftingManager {
    private final Gamepanel gp;
    public final CraftingUI ui;
    public boolean open;
    private final Map<String,Node> nodes=new LinkedHashMap<>();
    private final List<Station> stations=new ArrayList<>();
    private final Map<String,Integer> savedHits=new LinkedHashMap<>();
    private long seed=Long.MIN_VALUE;
    private boolean initialized;
    private int cooldown;
    private String placing;
    public String feedback="K: craft · E: gather/use station · LMB: gather with tool";
    public CraftingManager(Gamepanel gp){this.gp=gp;ui=new CraftingUI(this,gp);}
    public boolean available(){Player p=gp.localPlayer();return !gp.isNetworked && p!=null && !p.inWell && !p.isDead && !p.inDialogue && !world.InnInterior.contains(gp,p);}
    public void message(String text){feedback=text;gp.ui.addMessage(text);}
    public boolean key(int code){
        if(placing!=null && code==KeyEvent.VK_ESCAPE){placing=null;return true;}
        if(code==KeyEvent.VK_K){if(available()){open=!open;placing=null;gp.inventoryOpen=false;gp.buildings.stop();}else message("Crafting currently requires the single-player overworld.");return true;}
        if(!open)return false;
        switch(code){case KeyEvent.VK_ESCAPE->open=false;case KeyEvent.VK_UP,KeyEvent.VK_W->ui.select(-1);case KeyEvent.VK_DOWN,KeyEvent.VK_S->ui.select(1);
            case KeyEvent.VK_ENTER->craft(ui.recipe(),gp.localPlayer().craftingProgress.level>=9?ui.quantity:1);case KeyEvent.VK_P->beginPlace(ui.recipe().output());
            case KeyEvent.VK_EQUALS,KeyEvent.VK_ADD->{if(gp.localPlayer().craftingProgress.level>=9)ui.quantity=Math.min(99,ui.quantity+1);}case KeyEvent.VK_MINUS,KeyEvent.VK_SUBTRACT->ui.quantity=Math.max(1,ui.quantity-1);default->{}}
        return true;
    }
    public void update(){if(cooldown>0)cooldown--;if(!available()){open=false;placing=null;return;}ensureWorld();}
    public String craftError(RecipeRegistry.Recipe r,int quantity){
        Player p=gp.localPlayer();if(!available())return "Craft in the single-player overworld.";
        if(quantity<1 || quantity>99 || quantity>1 && p.craftingProgress.level<9)return "Batch crafting unlocks at Level 9.";
        if(p.craftingProgress.level<r.level())return "Requires Crafting Level "+r.level()+".";
        if(!stationAvailable(r.station(),p))return "Move within two tiles of a "+r.station()+".";
        Map<ItemType,Integer> costs=costs(r,quantity);
        for(var cost:costs.entrySet())if(InventoryTransaction.count(p,cost.getKey())<cost.getValue())return "Not enough "+RecipeRegistry.item(cost.getKey()).name()+".";
        if(InventoryTransaction.plan(gp,p,costs,Map.of(r.output(),r.count()*quantity))==null)return "Inventory full: make room for the output.";
        return null;
    }
    private Map<ItemType,Integer> costs(RecipeRegistry.Recipe r,int n){Map<ItemType,Integer> costs=new LinkedHashMap<>();r.ingredients().forEach((id,q)->costs.put(id,Math.multiplyExact(q,n)));return costs;}
    public boolean craft(RecipeRegistry.Recipe r,int quantity){
        String error=craftError(r,quantity);if(error!=null){message(error);return false;}
        Player p=gp.localPlayer();if(!InventoryTransaction.commit(gp,p,costs(r,quantity),Map.of(r.output(),r.count()*quantity)))return false;
        int level=p.craftingProgress.level,gained=p.craftingProgress.award(r,quantity);
        message("Crafted "+r.count()*quantity+" "+RecipeRegistry.item(r.output()).name()+" · +"+gained+" Crafting XP");
        if(p.craftingProgress.level>level)message("Crafting Level "+p.craftingProgress.level+"! New recipes unlocked." );return true;
    }
    public boolean stationAvailable(String type,Player p){return type.equals("hand") || stations.stream().anyMatch(s->s.type.equals(type) && s.bounds().distance(p.worldX,p.worldY)<gp.tileSize*2.5);}
    public void beginPlace(ItemType item){
        var def=RecipeRegistry.item(item);if(def==null || def.place().isEmpty()){message("Select a placeable recipe first.");return;}
        if(InventoryTransaction.count(gp.localPlayer(),item)<1){message("Craft or retrieve this item first.");return;}
        open=false;
        if(Set.of("floor","wall","door","bed_blue").contains(def.place())){gp.buildings.start(def.place());return;}
        placing=def.id();message("LMB: place "+def.name()+" within reach · Escape: cancel");
    }
    public boolean handleInput(Player p,InputState in){
        if(!available())return false;ensureWorld();
        if(open){in.suppressGameplay();return true;}
        if(placing!=null){
            if(in.escapePressed){placing=null;in.escapePressed=false;}
            else if(in.leftClicked){placeStation(p,in.aimWorldX,in.aimWorldY);}
            in.suppressGameplay();return true;
        }
        if(in.ePressed){
            Node near=nearest(p,p.worldX,p.worldY+gp.tileSize/2,true);
            if(near!=null){gather(p,near);in.ePressed=false;in.leftClicked=false;return true;}
            Station station=stations.stream().filter(s->s.bounds().distance(p.worldX,p.worldY)<gp.tileSize*2).min(Comparator.comparingDouble(s->s.bounds().distance(p.worldX,p.worldY))).orElse(null);
            if(station!=null){open=true;feedback="Nearby "+station.type+" ready.";in.suppressGameplay();return true;}
        }
        if(in.leftClicked && !gp.buildings.active && !gp.inventoryOpen){
            Node near=nearest(p,in.aimWorldX,in.aimWorldY,false);
            if(near!=null){gather(p,near);in.leftClicked=false;in.leftHeld=false;return true;}
            if(p.currentWeapon instanceof OBJ_crafted item && (item.type==Entity.type_staff || item.type==Entity.type_bow)){
                if(cooldown==0){
                    if(item.type==Entity.type_bow && !InventoryTransaction.commit(gp,p,Map.of(ItemType.WOODEN_ARROW,1),Map.of()))message("Craft Wooden Arrows first.");
                    else{gp.projectileList.add(new CraftedBolt(gp,p,in.aimWorldX,in.aimWorldY,item.type==Entity.type_staff));cooldown=32;}
                }
                in.leftClicked=false;in.leftHeld=false;return true;
            }
        }
        return false;
    }
    private Node nearest(Player p,double x,double y,boolean pickupFirst){
        Node best=null;double distance=Double.MAX_VALUE;
        for(Node n:nodes.values()){
            if(n.exhausted() || n.source!=null && !gp.worldObjects.contains(n.source))continue;
            if(Math.hypot(n.x-p.worldX,n.y-(p.worldY+gp.tileSize*.5))>gp.tileSize*2.1)continue;
            double d=Math.hypot(n.x-x,n.y-y);if(d>gp.tileSize*1.1)continue;
            if(pickupFirst && (n.kind.equals("twig") || n.kind.equals("stone")))d-=gp.tileSize;
            if(d<distance){distance=d;best=n;}
        }
        return best;
    }
    private void gather(Player p,Node n){
        if(cooldown>0)return;
        RecipeRegistry.Item tool=p.currentWeapon instanceof OBJ_crafted c?c.definition:null;
        String required=switch(n.kind){case "tree"->"axe";case "rock","iron"->"pickaxe";case "grass"->"cut";default->"";};
        String category=tool!=null?tool.tool():p.currentWeapon==null || p.currentWeapon.isPlaceholder?"":p.currentWeapon.type==Entity.type_axe?"axe":p.currentWeapon.type==Entity.type_sword?"cut":"";
        if(!required.isEmpty() && !(category.equals(required) || required.equals("cut") && category.equals("axe"))){message("Requires "+(required.equals("cut")?"a cutting weapon or axe":required)+".");cooldown=15;return;}
        int power=required.isEmpty()?n.hits:tool==null?1:tool.gather();
        if(n.hits<=power){
            ItemType drop=switch(n.kind){case "tree"->ItemType.LOG;case "rock","stone"->ItemType.STONE;case "grass"->ItemType.FIBER;case "iron"->ItemType.IRON_ORE;default->ItemType.TWIG;};
            int amount=switch(n.kind){case "tree"->4;case "rock"->4;case "grass"->3;case "iron"->3;default->1;};
            if(!InventoryTransaction.commit(gp,p,Map.of(),Map.of(drop,amount))){message("Inventory full. Resource remains here.");return;}
            n.hits=0;if(n.source!=null)n.source.resourceDepleted=true;message("Collected "+amount+" "+RecipeRegistry.item(drop).name());
        }else n.hits-=power;
        savedHits.put(n.id,n.hits);cooldown=required.isEmpty()?8:24;
        if(!required.isEmpty()){
            int dx=n.x-p.worldX,dy=n.y-p.worldY;p.direction=Math.abs(dx)>Math.abs(dy)?dx>0?"right":"left":dy>0?"down":"up";
            p.animState="attack";p.spriteNum=1;p.attacking=true;
        }
    }
    public void drawPreview(Graphics2D original){
        if(placing==null || !available())return;
        Player p=gp.localPlayer();var def=RecipeRegistry.item(placing);BufferedImage art=CraftingArt.icon(def);
        int x=gp.mouseH.getScaledX(),y=gp.mouseH.getScaledY(),w=(def.place().equals("workbench")?108:def.place().equals("forge")?72:48)*gp.tileSize/48,h=art.getHeight()*w/art.getWidth();
        Graphics2D g=(Graphics2D)original.create();g.setComposite(AlphaComposite.SrcOver.derive(.55f));g.drawImage(art,x-w/2,y-h,w,h,null);g.dispose();
    }
    private void placeStation(Player p,int x,int y){
        if(placing==null)return;var def=RecipeRegistry.item(placing);
        Station station=new Station(UUID.randomUUID().toString(),def.place(),placing,x,y);
        Rectangle box=station.bounds().rectangle();
        if(Math.hypot(x-p.worldX,y-p.worldY)>gp.tileSize*3){message("Place within three tiles.");return;}
        if(gp.tileM.blocksMovement(box.x,box.y,box.x+box.width-1,box.y+box.height-1) || blocks(box)
                || gp.buildings.blocksArea(box.x,box.y,box.width,box.height)) {message("Station needs clear, level ground.");return;}
        int level=gp.tileM.buildingElevation(Math.floorDiv(x,gp.tileSize),Math.floorDiv(y,gp.tileSize));
        for(int r=Math.floorDiv(box.y,gp.tileSize);r<=Math.floorDiv(box.y+box.height-1,gp.tileSize);r++)for(int c=Math.floorDiv(box.x,gp.tileSize);c<=Math.floorDiv(box.x+box.width-1,gp.tileSize);c++)
            if(c<0 || r<0 || c>=gp.maxWorldCol || r>=gp.maxWorldRow || gp.tileM.buildingElevation(c,r)!=level){message("Station needs level ground.");return;}
        for(WorldObject o:gp.worldObjects)if(o.blocksArea(box.x,box.y,box.width,box.height)){message("Blocked by scenery.");return;}
        for(Player actor:gp.players)if(actor!=null && box.intersects(actor.worldX+actor.solidArea.x,actor.worldY+actor.solidArea.y,actor.solidArea.width,actor.solidArea.height)){message("An actor is standing here.");return;}
        for(Entity actor:gp.monster)if(actor!=null && box.intersects(actor.worldX+actor.solidArea.x,actor.worldY+actor.solidArea.y,actor.solidArea.width,actor.solidArea.height)){message("An actor is standing here.");return;}
        for(Entity actor:gp.npc)if(actor!=null && box.intersects(actor.worldX+actor.solidArea.x,actor.worldY+actor.solidArea.y,actor.solidArea.width,actor.solidArea.height)){message("An actor is standing here.");return;}
        if(InventoryTransaction.commit(gp,p,Map.of(def.type(),1),Map.of())){
            for(Node n:nodes.values())if(box.contains(n.x,n.y)){n.hits=0;savedHits.put(n.id,0);if(n.source!=null)n.source.resourceDepleted=true;}
            stations.add(station);placing=null;message("Placed "+def.name()+". E to use; F to recover.");}
    }
    public boolean recoverStation(Player p){
        Station s=stations.stream().filter(v->v.bounds().distance(p.worldX,p.worldY)<gp.tileSize*2).findFirst().orElse(null);
        if(s==null)return false;
        if(InventoryTransaction.commit(gp,p,Map.of(),Map.of(RecipeRegistry.item(s.item).type(),1))){stations.remove(s);message("Recovered station.");}else message("Inventory full.");return true;
    }
    public boolean blocks(Rectangle box){if(!availableWorld())return false;return stations.stream().anyMatch(s->s.bounds().rectangle().intersects(box));}
    private boolean availableWorld(){return !gp.isNetworked && gp.localPlayer()!=null && !gp.localPlayer().inWell;}
    public void addDrawLayers(List<Entity> layers){
        if(!availableWorld())return;ensureWorld();
        for(Node n:nodes.values())if(n.source==null && !n.exhausted())layers.add(n);
        layers.addAll(stations);
    }
    public void reset(){for(WorldObject o:gp.worldObjects)o.resourceDepleted=false;initialized=false;nodes.clear();savedHits.clear();stations.clear();open=false;placing=null;seed=Long.MIN_VALUE;}
    public void ensureWorld(){
        if(gp.localPlayer()==null || gp.localPlayer().inWell || gp.quests.inDungeon())return;
        if(initialized && seed==gp.worldConfig.seed())return;
        if(initialized && seed!=gp.worldConfig.seed())reset();
        initialized=true;seed=gp.worldConfig.seed();nodes.clear();
        for(WorldObject o:gp.worldObjects){
            String kind=o.kind.swaysInWind()?"tree":o.kind==WorldObjectType.ROCK_LARGE?"rock":o.kind==WorldObjectType.TWIG?"twig":null;
            if(kind!=null)addNode("scenery:"+o.kind+":"+o.col+":"+o.row,kind,o.worldX,o.worldY,o);
        }
        // Traverse walkable ground from spawn: guaranteed starter pickups cannot be on inaccessible islands.
        Player p=gp.localPlayer();int sx=gp.generatedWorld==null?p.worldX/gp.tileSize:gp.generatedWorld.spawnCol,sy=gp.generatedWorld==null?p.worldY/gp.tileSize:gp.generatedWorld.spawnRow;
        ArrayDeque<Point> queue=new ArrayDeque<>();Set<Long> seen=new HashSet<>();List<Point> clear=new ArrayList<>();queue.add(new Point(sx,sy));
        while(!queue.isEmpty() && clear.size()<gp.maxWorldCol*gp.maxWorldRow){
            Point at=queue.remove();long key=((long)at.x<<32)|(at.y&0xffffffffL);if(!seen.add(key))continue;
            if(!clearGround(at.x,at.y))continue;clear.add(at);
            for(int[] d:new int[][]{{0,1},{1,0},{0,-1},{-1,0}})queue.add(new Point(at.x+d[0],at.y+d[1]));
        }
        SplittableRandom random=new SplittableRandom(seed^0x4352414654L);int starter=0;
        for(Point at:clear){
            double distance=Math.hypot(at.x-sx,at.y-sy);String kind=null;
            if(distance>0 && distance<5 && starter<12){kind=starter++<7?"twig":"stone";}
            else if(random.nextDouble()<.09)kind=switch(random.nextInt(10)){case 0,1,2->"twig";case 3,4->"stone";case 5->"iron";default->"grass";};
            if(kind!=null)addNode("generated:"+at.x+":"+at.y,kind,at.x*gp.tileSize+gp.tileSize/2,at.y*gp.tileSize+gp.tileSize/2,null);
        }
    }
    private boolean clearGround(int c,int r){
        if(c<1 || r<1 || c>=gp.maxWorldCol-1 || r>=gp.maxWorldRow-1)return false;
        int t=gp.tileSize,x=c*t,y=r*t;if(gp.tileM.blocksMovement(x+t/4,y+t/4,x+t*3/4,y+t*3/4))return false;
        for(WorldObject o:gp.worldObjects)if(o.blocksArea(x+t/4,y+t/4,t/2,t/2))return false;return true;
    }
    private void addNode(String id,String kind,int x,int y,WorldObject source){Node n=new Node(id,kind,x,y,source);n.hits=savedHits.getOrDefault(id,n.hits);if(gp.buildings.coversGround(Math.floorDiv(x,gp.tileSize),Math.floorDiv(y,gp.tileSize)))n.hits=0;nodes.put(id,n);if(source!=null)source.resourceDepleted=n.exhausted();}
    public ItemType buildingItem(String element){
        return switch(element){case "floor"->ItemType.WOODEN_FLOOR;case "wall"->ItemType.WOODEN_WALL;case "door"->ItemType.WOODEN_DOOR;case "bed_blue"->ItemType.SIMPLE_BED;default->null;};
    }
    public boolean buildingTransaction(Map<String,Integer> costs,Map<String,Integer> refunds){
        if(Boolean.getBoolean("astral.dev"))return true;
        Map<ItemType,Integer> take=new LinkedHashMap<>(),give=new LinkedHashMap<>();
        for(var entry:costs.entrySet()){
            if(entry.getValue()<=0)continue;ItemType type=buildingItem(entry.getKey());
            if(type==null){message("This building element has no survival recipe yet.");return false;}
            take.merge(type,entry.getValue(),Integer::sum);
        }
        for(var entry:refunds.entrySet()){ItemType type=buildingItem(entry.getKey());if(type!=null && entry.getValue()>0)give.merge(type,entry.getValue(),Integer::sum);}
        if(!InventoryTransaction.commit(gp,gp.localPlayer(),take,give)){message("Missing crafted components or inventory space for recovered parts.");return false;}return true;
    }
    public String worldJson(){
        StringBuilder out=new StringBuilder("{\"seed\":").append(gp.worldConfig.seed()).append(",\"hits\":{");
        int i=0;for(var e:savedHits.entrySet()){if(i++>0)out.append(',');out.append('"').append(e.getKey()).append("\":").append(e.getValue());}
        out.append("},\"stations\":[");i=0;for(Station s:stations){if(i++>0)out.append(',');out.append("{\"id\":\"").append(s.id).append("\",\"item\":\"").append(s.item).append("\",\"x\":").append(s.worldX).append(",\"y\":").append(s.worldY).append('}');}
        return out.append("]}").toString();
    }
    public static void validateSavedWorld(String json,int width,int height,int tileSize,long expectedSeed){
        if(json==null || json.isBlank())return;
        Object parsed=MiniJson.parse(json);
        if(!(parsed instanceof Map<?,?> root) || !(root.get("seed") instanceof Number seed)
                || seed.longValue()!=expectedSeed || !(root.get("hits") instanceof Map<?,?> hits)
                || !(root.get("stations") instanceof List<?> stations))throw new IllegalArgumentException("Invalid crafting state or different world seed");
        for(var entry:hits.entrySet()){
            if(!(entry.getKey() instanceof String id) || !id.matches("(?:scenery:[A-Z0-9_]+|generated):[0-9]+:[0-9]+")
                    || !(entry.getValue() instanceof Number hp) || hp.doubleValue()!=hp.intValue() || hp.intValue()<0 || hp.intValue()>100000)throw new IllegalArgumentException("Invalid resource state");
        }
        Set<String> ids=new HashSet<>();for(Object value:stations){
            if(!(value instanceof Map<?,?> station) || !(station.get("id") instanceof String id) || !id.matches("[a-zA-Z0-9_-]{1,100}") || !ids.add(id)
                    || !(station.get("item") instanceof String item) || !(station.get("x") instanceof Number x) || !(station.get("y") instanceof Number y)
                    || x.doubleValue()!=x.intValue() || y.doubleValue()!=y.intValue() || x.intValue()<0 || y.intValue()<0 || x.intValue()>=(long)width*tileSize || y.intValue()>=(long)height*tileSize
                    || !Set.of("workbench","campfire","forge","chest").contains(RecipeRegistry.item(item).place()))throw new IllegalArgumentException("Invalid saved crafting station");
        }
    }
    public void restoreWorld(String json){
        validateSavedWorld(json,gp.maxWorldCol,gp.maxWorldRow,gp.tileSize,gp.worldConfig.seed());
        reset();if(json==null || json.isBlank()){ensureWorld();return;}
        Map<?,?> root=(Map<?,?>)MiniJson.parse(json);if(((Number)root.get("seed")).longValue()!=gp.worldConfig.seed())throw new IllegalArgumentException("Saved crafting world seed differs from current world");
        ((Map<?,?>)root.get("hits")).forEach((k,v)->{int hp=((Number)v).intValue();if(hp<0 || hp>100000)throw new IllegalArgumentException("Invalid resource state");savedHits.put((String)k,hp);});
        Set<String> ids=new HashSet<>();for(Object value:(List<?>)root.get("stations")){
            Map<?,?> s=(Map<?,?>)value;String id=(String)s.get("id"),item=(String)s.get("item");var def=RecipeRegistry.item(item);
            int x=((Number)s.get("x")).intValue(),y=((Number)s.get("y")).intValue();
            if(!Set.of("workbench","campfire","forge","chest").contains(def.place()) || x<0 || y<0 || x>=gp.maxWorldCol*gp.tileSize || y>=gp.maxWorldRow*gp.tileSize || id==null || !id.matches("[a-zA-Z0-9_-]{1,100}") || !ids.add(id))throw new IllegalArgumentException("Invalid saved station");
            stations.add(new Station(id,def.place(),item,x,y));
        }
        ensureWorld();
    }
    private record Box(int x,int y,int width,int height){Rectangle rectangle(){return new Rectangle(x,y,width,height);}double distance(int px,int py){return Math.hypot(Math.max(x-px,Math.max(0,px-x-width)),Math.max(y-py,Math.max(0,py-y-height)));}}
    private final class Station extends Entity {
        final String id,type,item;
        Station(String id,String type,String item,int x,int y){super(CraftingManager.this.gp);this.id=id;this.type=type;this.item=item;worldX=x;worldY=y;}
        Box bounds(){int w=(type.equals("workbench")?96:60)*gp.tileSize/48;return new Box(worldX-w/2,worldY-gp.tileSize/3,w,gp.tileSize/3);}
        @Override public int sortY(){return worldY;}
        @Override public void draw(Graphics2D g){Player p=gp.localPlayer();BufferedImage art=CraftingArt.icon(RecipeRegistry.item(item));int w=(type.equals("workbench")?108:type.equals("forge")?72:48)*gp.tileSize/48,h=art.getHeight()*w/art.getWidth();g.drawImage(art,worldX-p.worldX+p.screenX-w/2,worldY-p.worldY+p.screenY-h,w,h,null);}
    }
    private final class Node extends Entity {
        final String id,kind;final int x,y;final WorldObject source;int hits;
        Node(String id,String kind,int x,int y,WorldObject source){super(CraftingManager.this.gp);this.id=id;this.kind=kind;this.x=x;this.y=y;this.source=source;worldX=x;worldY=y;hits=RecipeRegistry.HARDNESS.getOrDefault(kind,1);}
        boolean exhausted(){return hits<=0;}
        @Override public int sortY(){return y;}
        @Override public void draw(Graphics2D g){
            Player p=gp.localPlayer();int px=x-p.worldX+p.screenX,py=y-p.worldY+p.screenY,t=gp.tileSize;
            if(px<-t || py<-t || px>gp.screenWidth+t || py>gp.screenHeight+t)return;
            if(kind.equals("grass")){
                g.setColor(art.Palette.FOLIAGE_DARK);for(int i=0;i<4;i++)g.fillPolygon(new int[]{px-14+i*7,px-10+i*7,px-5+i*7},new int[]{py,py-20-i%2*9,py},3);
            }else{
                BufferedImage icon=CraftingArt.icon(RecipeRegistry.item(kind.equals("iron")?"iron_ore":kind));int size=kind.equals("iron")?t*3/4:t/2;
                g.drawImage(icon,px-size/2,py-size/2,size,size,null);
            }
        }
    }
}
