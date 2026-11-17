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
    private final world.spatial.SpatialIndex<Node> nodeIndex;
    private final Map<Long,Node> tallGrassNodes=new HashMap<>();
    private final world.spatial.SpatialIndex<Station> stationIndex;
    private final Map<String,BufferedImage> stationArt=new HashMap<>();
    private final List<Station> stations=new ArrayList<>(){
        @Override public boolean add(Station value){boolean added=super.add(value);stationIndex.add(value,value.bounds().rectangle());return added;}
        @Override public boolean remove(Object value){boolean removed=super.remove(value);if(removed && value instanceof Station station)stationIndex.remove(station);return removed;}
        @Override public void clear(){super.clear();stationIndex.clear();}
    };
    private final Map<String,Integer> savedHits=new LinkedHashMap<>();
    private long seed=Long.MIN_VALUE;
    private boolean initialized;
    private int cooldown;
    private art.ObjectArt.Sprite looseStoneSprite;
    private String placing;
    public String feedback="E: inventory/crafting · F: gather/use station · G: recover station";
    public CraftingManager(Gamepanel gp){this.gp=gp;nodeIndex=new world.spatial.SpatialIndex<>(gp.tileSize);stationIndex=new world.spatial.SpatialIndex<>(gp.tileSize*2);ui=new CraftingUI(this,gp);}
    public void cancelPlacement(){placing=null;}
    public boolean placingObject(){return placing!=null;}
    private static long grassKey(int col,int row){return ((long)col<<32)|(row&0xffffffffL);}
    public void collectTargets(interaction.InteractionTargetResolver resolver){
        if(!available())return;ensureWorld();Player p=resolver.player();
        for(Node node:nodeIndex.query(resolver.validator.frontBounds(p)))if(nodes.get(node.id)==node && canHarvest(p,node))
            resolver.offer(node,node.id,node.footprint(),node.kind.equals("twig") || node.kind.equals("stone")?4:1,()->gather(p,node));
        for(Station station:stationIndex.query(resolver.validator.frontBounds(p)))resolver.offer(station,"station:"+station.id,station.bounds().rectangle(),3,()->{open=true;feedback="Nearby "+station.type+" ready.";});
    }
    private String requiredTool(Node node){return switch(node.kind){case "tree"->"axe";case "rock","iron"->"pickaxe";case "grass"->"cut";default->"";};}
    public boolean usableTarget(Object token){return !(token instanceof Node node) || canHarvest(gp.localPlayer(),node);}
    private boolean canHarvest(Player p,Node node){
        if(node.exhausted() || node.source!=null && !gp.worldObjects.contains(node.source))return false;
        if(node.kind.equals("grass") && !gp.grass.isHarvestable(Math.floorDiv(node.x,gp.tileSize),Math.floorDiv(node.y,gp.tileSize)))return false;
        String required=requiredTool(node);if(required.isEmpty())return true;
        RecipeRegistry.Item tool=p.currentWeapon instanceof OBJ_crafted c?c.definition:null;
        String category=tool!=null?tool.tool():p.currentWeapon==null || p.currentWeapon.isPlaceholder?"":p.currentWeapon.type==Entity.type_axe?"axe":p.currentWeapon.type==Entity.type_sword?"cut":"";
        return category.equals(required) || required.equals("cut") && category.equals("axe");
    }
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
    public boolean stationAvailable(String type,Player p){return type.equals("hand") || stationIndex.query(new Rectangle(p.worldX-gp.tileSize*3,p.worldY-gp.tileSize*3,gp.tileSize*6,gp.tileSize*6)).stream().anyMatch(s->s.type.equals(type) && stationDistance(s,p)<=gp.tileSize*2.5);}
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
        if(in.ePressed && gp.interactions.interact()){in.ePressed=false;in.leftClicked=false;return true;}
        if(in.leftClicked && !gp.buildings.active && !gp.inventoryOpen){
            var target=gp.interactions.target();
            if(target!=null && target.token() instanceof Node node && canHarvest(p,node)){
                gp.interactions.interact();in.leftClicked=false;in.leftHeld=false;return true;
            }
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
    private void gather(Player p,Node n){
        if(cooldown>0 || !canHarvest(p,n) || !gp.interactions.accepts(p,n.footprint(),n))return;
        RecipeRegistry.Item tool=p.currentWeapon instanceof OBJ_crafted c?c.definition:null;
        String required=requiredTool(n);
        String category=tool!=null?tool.tool():p.currentWeapon==null || p.currentWeapon.isPlaceholder?"":p.currentWeapon.type==Entity.type_axe?"axe":p.currentWeapon.type==Entity.type_sword?"cut":"";
        if(!required.isEmpty() && !(category.equals(required) || required.equals("cut") && category.equals("axe"))){message("Requires "+(required.equals("cut")?"a cutting weapon or axe":required)+".");cooldown=15;return;}
        int power=required.isEmpty()?n.hits:tool==null?1:tool.gather();
        if(n.hits<=power){
            ItemType drop=switch(n.kind){case "tree"->ItemType.LOG;case "rock","stone"->ItemType.STONE;case "grass"->ItemType.FIBER;case "iron"->ItemType.IRON_ORE;default->ItemType.TWIG;};
            int amount=switch(n.kind){case "tree"->4;case "rock"->4;case "grass"->3;case "iron"->3;default->1;};
            Rectangle footprint=n.footprint();
            if(required.isEmpty()){
                if(!InventoryTransaction.commit(gp,p,Map.of(),Map.of(drop,amount))){message("Inventory full. Resource remains here.");return;}
                message("Collected "+amount+" "+RecipeRegistry.item(drop).name());
                gp.spawnFloatingText(footprint.x+footprint.width/2,footprint.y,"+"+amount+" "+RecipeRegistry.item(drop).name(),new Color(220,231,185));
            }
            n.hits=0;if(n.source!=null)n.source.resourceDepleted=true;
            if(!required.isEmpty()){
                // Separate pieces fan out from the depleted resource's ground footprint.
                for(int i=0;i<amount;i++)gp.quests.dropLoot(object.ItemRegistry.create(drop,gp),
                        footprint.x+footprint.width/2-gp.tileSize/2,footprint.y+footprint.height/2-gp.tileSize/2);
            }
        }else n.hits-=power;
        savedHits.put(n.id,n.hits);cooldown=required.isEmpty()?8:24;
        if(!required.isEmpty()){
            Rectangle footprint=n.footprint();
            if(n.kind.equals("tree")&&n.source!=null)n.source.chopped(p);
            Color material=switch(n.kind){case "tree"->new Color(181,144,102);case "grass"->new Color(142,165,111);default->new Color(171,163,155);};
            Particle.spray(n,Particle.Style.CHIP,material,n.exhausted()?12:6,5.5f,28,2.5f,-Math.PI/2,Math.PI*.55,
                    footprint.x+footprint.width/2f-n.worldX-n.solidArea.x-n.solidArea.width/2f,
                    footprint.y+footprint.height/2f-n.worldY-n.solidArea.y-n.solidArea.height/2f);
            p.animState="attack";p.spriteNum=1;p.attacking=true;
        }
    }
    public void drawPreview(Graphics2D original){
        if(placing==null || !available())return;
        Player p=gp.localPlayer();var def=RecipeRegistry.item(placing);BufferedImage art=CraftingArt.icon(def);
        int x=gp.mouseH.getScaledX(),y=gp.mouseH.getScaledY(),w=(def.place().equals("workbench")?72:def.place().equals("forge")?72:48)*gp.tileSize/48,h=art.getHeight()*w/art.getWidth();
        Graphics2D g=(Graphics2D)original.create();g.setComposite(AlphaComposite.SrcOver.derive(.55f));g.drawImage(art,x-w/2,y-h,w,h,null);g.dispose();
    }
    private void placeStation(Player p,int x,int y){
        if(placing==null)return;var def=RecipeRegistry.item(placing);
        Station station=new Station(UUID.randomUUID().toString(),def.place(),placing,x,y);
        Rectangle box=station.bounds().rectangle();
        if(world.TerrainHeightResolver.active(gp) && !gp.generatedWorld.lowland(Math.floorDiv(x,gp.tileSize),Math.floorDiv(y,gp.tileSize))){message("Station needs connected lowland ground.");return;}
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
            stations.add(station);gp.placementEffects.object(station.id,station.worldX,station.worldY,station.bounds().width());placing=null;message("Placed "+def.name()+". F to use; G to recover.");}
    }
    public boolean recoverStation(Player p){
        gp.interactions.refresh();Station s=gp.interactions.token() instanceof Station station?station:null;
        if(s==null)return false;
        if(InventoryTransaction.commit(gp,p,Map.of(),Map.of(RecipeRegistry.item(s.item).type(),1))){stations.remove(s);message("Recovered station.");}else message("Inventory full.");return true;
    }
    public Object collisionOwner(Object token){return token instanceof Node node && node.source!=null?node.source:token;}
    public boolean blocks(Rectangle box){return blocksExcept(box,null);}
    public boolean blocksExcept(Rectangle box,Object ignored){if(!availableWorld())return false;return stationIndex.query(box).stream().anyMatch(s->s!=ignored);}
    private boolean availableWorld(){return !gp.isNetworked && gp.localPlayer()!=null && !gp.localPlayer().inWell;}
    public void addDrawLayers(List<Entity> layers){
        if(!availableWorld())return;ensureWorld();
        for(Node n:nodeIndex.query(new Rectangle(gp.localPlayer().worldX-gp.localPlayer().screenX-gp.tileSize,gp.localPlayer().worldY-gp.localPlayer().screenY-gp.tileSize,gp.screenWidth+gp.tileSize*2,gp.screenHeight+gp.tileSize*2)))if(nodes.get(n.id)==n && n.source==null && !n.kind.equals("grass") && !n.exhausted())layers.add(n);
        layers.addAll(stationIndex.query(gp.chunks.camera(4)));
    }
    private void prepareResourceArt(){
        if(looseStoneSprite==null)looseStoneSprite=new art.ObjectArt(gp.tileSize).rockSmall();
        CraftingArt.inventoryIcon(RecipeRegistry.item("twig"),gp.tileSize/2);
        CraftingArt.inventoryIcon(RecipeRegistry.item("iron_ore"),gp.tileSize*3/4);
    }
    private BufferedImage stationImage(String type,String item){return stationArt.computeIfAbsent(type,key->{
        BufferedImage image=CraftingArt.icon(RecipeRegistry.item(item));int w=(type.equals("workbench") || type.equals("forge")?72:48)*gp.tileSize/48,h=image.getHeight()*w/image.getWidth();
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D scaled=out.createGraphics();try{scaled.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);scaled.drawImage(image,0,0,w,h,null);}finally{scaled.dispose();}return out;
    });}
    public void reset(){for(WorldObject o:gp.worldObjects)o.resourceDepleted=false;initialized=false;nodes.clear();nodeIndex.clear();tallGrassNodes.clear();savedHits.clear();stations.clear();open=false;placing=null;seed=Long.MIN_VALUE;}
    public void ensureWorld(){
        if(gp.localPlayer()==null || gp.localPlayer().inWell || gp.quests.inDungeon())return;
        if(initialized && seed==gp.worldConfig.seed())return;
        if(initialized && seed!=gp.worldConfig.seed())reset();
        prepareResourceArt();
        initialized=true;seed=gp.worldConfig.seed();nodes.clear();nodeIndex.clear();
        for(WorldObject o:gp.worldObjects){
            String kind=o.kind.swaysInWind()?"tree":(o.kind==WorldObjectType.ROCK_LARGE || o.kind==WorldObjectType.ROCK_LARGE_MOSS)?"rock":(o.kind==WorldObjectType.ROCK_SMALL || o.kind==WorldObjectType.ROCK_SMALL_MOSS)?"stone":o.kind==WorldObjectType.TWIG?"twig":null;
            if(kind!=null)addNode("scenery:"+o.kind+":"+o.col+":"+o.row,kind,o.worldX,o.worldY,o);
        }
        if(gp.chunks.enabled()){gp.chunks.reloadResources();return;}
        if(gp.generatedWorld!=null && gp.generatedWorld.generatorVersion>=6){
            for(var resource:gp.generatedWorld.resources())addNode("generated:"+resource.col()+":"+resource.row(),resource.kind(),
                    resource.col()*gp.tileSize+gp.tileSize/2,resource.row()*gp.tileSize+gp.tileSize/2,null);
        }else{
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
            else if(random.nextDouble()<.09)kind=switch(random.nextInt(10)){case 0,1,2->"twig";case 3,4->"stone";case 5->"iron";default->null;};
            if(kind!=null)addNode("generated:"+at.x+":"+at.y,kind,at.x*gp.tileSize+gp.tileSize/2,at.y*gp.tileSize+gp.tileSize/2,null);
        }
        }
        for(int row=0;row<gp.maxWorldRow;row++)for(int col=0;col<gp.maxWorldCol;col++)if(gp.grass.isHarvestable(col,row))
            addNode("tallgrass:"+col+":"+row,"grass",col*gp.tileSize+gp.tileSize/2,row*gp.tileSize+gp.tileSize/2,null);
    }
    /** Resident node objects are disposable; compact savedHits is the persistent authority. */
    public void loadChunk(world.chunk.WorldChunks.Chunk chunk){
        if(!initialized)return;
        int size=world.chunk.ChunkConfig.SIZE,ts=gp.tileSize;
        for(WorldObject o:gp.worldObjects.query(new Rectangle(chunk.x*size*ts,chunk.y*size*ts,size*ts,size*ts))){
            if(Math.floorDiv(o.col,size)!=chunk.x || Math.floorDiv(o.row,size)!=chunk.y)continue;
            String kind=o.kind.swaysInWind()?"tree":(o.kind==WorldObjectType.ROCK_LARGE || o.kind==WorldObjectType.ROCK_LARGE_MOSS)?"rock":(o.kind==WorldObjectType.ROCK_SMALL || o.kind==WorldObjectType.ROCK_SMALL_MOSS)?"stone":o.kind==WorldObjectType.TWIG?"twig":null;
            if(kind!=null)addNode("scenery:"+o.kind+":"+o.col+":"+o.row,kind,o.worldX,o.worldY,o);
        }
        for(var r:chunk.resources)addNode("generated:"+r.col()+":"+r.row(),r.kind(),r.col()*ts+ts/2,r.row()*ts+ts/2,null);
        for(int row=chunk.y*size;row<Math.min(gp.maxWorldRow,(chunk.y+1)*size);row++)for(int col=chunk.x*size;col<Math.min(gp.maxWorldCol,(chunk.x+1)*size);col++)if(gp.grass.isHarvestable(col,row))addNode("tallgrass:"+col+":"+row,"grass",col*ts+ts/2,row*ts+ts/2,null);
    }
    public void unloadChunk(world.chunk.WorldChunks.Chunk chunk){
        int size=world.chunk.ChunkConfig.SIZE;
        var iterator=nodes.values().iterator();while(iterator.hasNext()){
            Node n=iterator.next();int col=n.source==null?Math.floorDiv(n.x,gp.tileSize):n.source.col,row=n.source==null?Math.floorDiv(n.y,gp.tileSize):n.source.row;
            if(Math.floorDiv(col,size)!=chunk.x || Math.floorDiv(row,size)!=chunk.y)continue;
            nodeIndex.remove(n);if(n.kind.equals("grass"))tallGrassNodes.remove(grassKey(col,row));iterator.remove();
        }
    }
    private double stationDistance(Station s,Player p){return s.bounds().distance(p.worldX+(int)p.solidArea.getCenterX(),p.worldY+(int)p.solidArea.getCenterY());}
    public boolean grassHarvested(int col,int row){Node n=tallGrassNodes.get(grassKey(col,row));return availableWorld() && n!=null && n.exhausted();}
    public boolean nearTallGrass(int col,int row){Node node=tallGrassNodes.get(grassKey(col,row));return node!=null && !node.exhausted() && gp.interactions.selected(node);}
    public boolean nearHarvestable(WorldObject object){
        if(object.resourceDepleted)return false;
        Node node=nodes.get("scenery:"+object.kind+":"+object.col+":"+object.row);
        return node!=null && !node.exhausted() && gp.interactions.selected(node);
    }
    private boolean clearGround(int c,int r){
        if(c<1 || r<1 || c>=gp.maxWorldCol-1 || r>=gp.maxWorldRow-1)return false;
        int t=gp.tileSize,x=c*t,y=r*t;if(gp.tileM.blocksMovement(x+t/4,y+t/4,x+t*3/4,y+t*3/4))return false;
        for(WorldObject o:gp.worldObjects)if(o.blocksArea(x+t/4,y+t/4,t/2,t/2))return false;return true;
    }
    private void addNode(String id,String kind,int x,int y,WorldObject source){Node n=new Node(id,kind,x,y,source);n.hits=savedHits.getOrDefault(id,n.hits);if(gp.buildings.coversGround(Math.floorDiv(x,gp.tileSize),Math.floorDiv(y,gp.tileSize)))n.hits=0;Node old=nodes.put(id,n);if(old!=null)nodeIndex.remove(old);nodeIndex.add(n,n.footprint());if(kind.equals("grass"))tallGrassNodes.put(grassKey(Math.floorDiv(x,gp.tileSize),Math.floorDiv(y,gp.tileSize)),n);if(source!=null)source.resourceDepleted=n.exhausted();}
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
    public static long savedWorldSeed(String json){
        var matcher=java.util.regex.Pattern.compile("\\A\\s*\\{\\s*\"seed\"\\s*:\\s*(-?\\d+)\\s*[,}]").matcher(json);
        if(!matcher.find())throw new IllegalArgumentException("Missing exact saved world seed");
        return Long.parseLong(matcher.group(1));
    }
    public static void validateSavedWorld(String json,int width,int height,int tileSize,long expectedSeed){
        if(json==null || json.isBlank())return;
        Object parsed=MiniJson.parse(json);
        if(!(parsed instanceof Map<?,?> root) || !(root.get("seed") instanceof Number seed)
                || savedWorldSeed(json)!=expectedSeed || !(root.get("hits") instanceof Map<?,?> hits)
                || !(root.get("stations") instanceof List<?> stations))throw new IllegalArgumentException("Invalid crafting state or different world seed");
        for(var entry:hits.entrySet()){
            if(!(entry.getKey() instanceof String id) || !id.matches("(?:scenery:[A-Z0-9_]+|generated|tallgrass):[0-9]+:[0-9]+")
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
        Map<?,?> root=(Map<?,?>)MiniJson.parse(json);if(savedWorldSeed(json)!=gp.worldConfig.seed())throw new IllegalArgumentException("Saved crafting world seed differs from current world");
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
        final String id,type,item;private final BufferedImage display;
        Station(String id,String type,String item,int x,int y){super(CraftingManager.this.gp);this.id=id;this.type=type;this.item=item;worldX=x;worldY=y;display=stationImage(type,item);}
        Box bounds(){int w=60*gp.tileSize/48;return new Box(worldX-w/2,worldY-gp.tileSize/3,w,gp.tileSize/3);}
        @Override public int sortY(){return worldY;}
        @Override public void draw(Graphics2D g){
            Player p=gp.localPlayer();
            int x=worldX-p.worldX+p.screenX-display.getWidth()/2,y=worldY-p.worldY+p.screenY-display.getHeight();
            Graphics2D visual=(Graphics2D)g.create();gp.placementEffects.object(visual,id,worldX-p.worldX+p.screenX,worldY-p.worldY+p.screenY);
            if(gp.interactions.selected(this))art.InteractionOutline.draw(visual,display,x,y);
            visual.drawImage(display,x,y,null);visual.dispose();
        }
    }
    private final class Node extends Entity {
        final String id,kind;final int x,y;final WorldObject source;int hits;private Rectangle interactionBounds;
        Node(String id,String kind,int x,int y,WorldObject source){super(CraftingManager.this.gp);this.id=id;this.kind=kind;this.x=x;this.y=y;this.source=source;worldX=x;worldY=y;hits=RecipeRegistry.HARDNESS.getOrDefault(kind,1);}
        Rectangle footprint(){
            if(interactionBounds==null)interactionBounds=source!=null && !source.solidArea.isEmpty()?interaction.InteractionValidator.bounds(source):new Rectangle(Math.floorDiv(x,gp.tileSize)*gp.tileSize,Math.floorDiv(y,gp.tileSize)*gp.tileSize,gp.tileSize,gp.tileSize);
            return interactionBounds;
        }
        boolean exhausted(){return hits<=0;}
        @Override public int sortY(){return y;}
        @Override public void draw(Graphics2D g){
            Player p=gp.localPlayer();int px=x-p.worldX+p.screenX,py=y-p.worldY+p.screenY,t=gp.tileSize;
            if(px<-t || py<-t || px>gp.screenWidth+t || py>gp.screenHeight+t)return;
            if(kind.equals("grass"))return;
            if(kind.equals("stone")){
                BufferedImage image=looseStoneSprite.image;int sx=px-looseStoneSprite.footX,sy=py-looseStoneSprite.footY;
                if(gp.interactions.selected(this))art.InteractionOutline.draw(g,image,sx,sy);
                g.drawImage(image,sx,sy,null);
            }else{
                int size=kind.equals("iron")?t*3/4:t/2;
                BufferedImage image=CraftingArt.inventoryIcon(RecipeRegistry.item(kind.equals("iron")?"iron_ore":kind),size);
                if(gp.interactions.selected(this))art.InteractionOutline.draw(g,image,px-size/2,py-size/2);
                g.drawImage(image,px-size/2,py-size/2,null);
            }
        }
    }
}
