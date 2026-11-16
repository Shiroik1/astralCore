package building;

import art.BuildGeometry;
import art.BuildTextures;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import static building.BuildingLayout.*;

/** Shared foundation clipping, border joins and indoor wall shadows for game and Builder. */
public final class BuildingRenderer {
    private final BuildingLayout layout;
    private final int ts;
    private final BuildTextures textures;
    private final Map<Cell,Tile> ground=new LinkedHashMap<>();
    private final Map<Cell,Area> clips=new HashMap<>();
    private final Map<Cell,BufferedImage> shadows=new HashMap<>();
    private final Map<Cell,Area> structureCoverage=new HashMap<>();
    private Area exteriorOutline=new Area();
    public BuildingRenderer(BuildingLayout source,int tileSize,BuildTextures textures){
        this.layout=new BuildingLayout(source);this.ts=tileSize;this.textures=textures;
        ground.putAll(layout.floors());
        for(Cell c:layout.structures().keySet())if(!ground.containsKey(c)){
            search:for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){
                Tile floor=layout.floors().get(c.offset(dc,dr));
                if(floor!=null){ground.put(c,floor);break search;}
            }
        }
        for(Cell c:ground.keySet())if(layout.structures().containsKey(c))clips.put(c,floorCoverage(c));
        refreshShadows();
        refreshExteriorOutline();
    }
    public Set<Cell> groundCells(){return Collections.unmodifiableSet(ground.keySet());}
    private boolean interiorFloor(Cell c){return layout.floors().containsKey(c) && !layout.structures().containsKey(c);}
    private Area floorCoverage(Cell c){
        Tile p=layout.structures().get(c);Area coverage=new Area();
        for(Rectangle r:BuildGeometry.footprint(layout.structureMask(c),p.vertical(),p.shape()==BuildElements.Shape.DOOR,false))coverage.add(new Area(r));
        Rectangle span=coverage.getBounds();
        for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){
            if(dc==0 && dr==0 || !interiorFloor(c.offset(dc,dr)))continue;
            Rectangle base=dc!=0 && dr!=0?new Rectangle(16,32,16,16):span;
            int left=dc<0?0:base.x,right=dc>0?48:base.x+base.width;
            int top=dr<0?0:base.y,bottom=dr>0?48:base.y+base.height;
            coverage.add(new Area(new Rectangle(left,top,right-left,bottom-top)));
        }
        coverage.transform(AffineTransform.getScaleInstance(ts/48.0,ts/48.0));return coverage;
    }
    private Area shape(Cell c,Tile tile){
        Area result=new Area();
        for(Rectangle r:BuildingLayout.footprint(c,tile,layout.structures()::get)){
            Rectangle scaled=BuildGeometry.scale(r,ts);scaled.translate(c.col()*ts,c.row()*ts);result.add(new Area(scaled));
        }
        return result;
    }
    private void refreshShadows(){
        if(ground.isEmpty() || layout.structures().isEmpty())return;
        Area walls=new Area(),floor=new Area();
        layout.structures().forEach((cell,tile)->walls.add(shape(cell,tile)));
        for(Cell c:ground.keySet()){
            Area tile=clips.containsKey(c)?new Area(clips.get(c)):new Area(new Rectangle(0,0,ts,ts));
            tile.transform(AffineTransform.getTranslateInstance(c.col()*ts,c.row()*ts));floor.add(tile);
        }
        floor.subtract(walls);List<Area> bands=new ArrayList<>();
        for(int radius=12;radius>=1;radius--){
            Area band=new Area(new BasicStroke(radius*2f*ts/48f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(walls));
            band.intersect(floor);bands.add(band);
        }
        for(Cell c:ground.keySet()){
            Rectangle bounds=new Rectangle(c.col()*ts,c.row()*ts,ts,ts);
            if(!bands.get(0).intersects(bounds))continue;
            BufferedImage image=new BufferedImage(ts,ts,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(-bounds.x,-bounds.y);g.setColor(new Color(48,36,29,5));for(Area band:bands)g.fill(band);
            g.dispose();shadows.put(c,image);
        }
    }
    /** Union the visible building first, so shared element edges never acquire a stroke. */
    private void refreshExteriorOutline(){
        Area silhouette=new Area();
        for(Cell c:ground.keySet()){
            Area part=clips.containsKey(c)?new Area(clips.get(c)):new Area(new Rectangle(0,0,ts,ts));
            part.transform(AffineTransform.getTranslateInstance(c.col()*ts,c.row()*ts));silhouette.add(part);
        }
        int head=BuildGeometry.HEIGHT*ts/48,pad=ts;
        for(Cell c:layout.structures().keySet()){
            BufferedImage mask=new BufferedImage(ts+pad*2,ts+head,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=mask.createGraphics();g.translate(pad,head);
            drawStructure(g,c,layout.structures().get(c),layout.structures()::get,ts,textures);g.dispose();
            Area part=new Area();
            // Coalesce equal opaque spans vertically before constructing the Area.
            Map<String,Rectangle> active=new HashMap<>();
            for(int y=0;y<=mask.getHeight();y++){
                Map<String,Rectangle> next=new HashMap<>();
                if(y<mask.getHeight())for(int x=0;x<mask.getWidth();){
                    if((mask.getRGB(x,y)>>>24)<128){x++;continue;}
                    int start=x++;while(x<mask.getWidth() && (mask.getRGB(x,y)>>>24)>=128)x++;
                    String key=start+":"+x;Rectangle run=active.remove(key);
                    if(run==null)run=new Rectangle(start,y,x-start,1);else run.height++;
                    next.put(key,run);
                }
                for(Rectangle run:active.values())part.add(new Area(run));active=next;
            }
            part.transform(AffineTransform.getTranslateInstance(c.col()*ts-pad,c.row()*ts-head));
            structureCoverage.put(c,part);silhouette.add(part);
        }
        exteriorOutline=new Area(new BasicStroke(2f*ts/48f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(silhouette));
        exteriorOutline.intersect(silhouette);
    }
    private void drawExteriorOutline(Graphics2D original,Cell cell,Area coverage,Tile tile){
        if(exteriorOutline.isEmpty())return;
        Graphics2D g=(Graphics2D)original.create();
        g.translate(-cell.col()*ts,-cell.row()*ts);
        if(coverage!=null)g.clip(coverage);
        String id=tile.element();g.setColor(new Color(id.startsWith("cobblestone") || id.startsWith("concrete") || id.equals("stairs_stone")?0x71696D:0x735746));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(exteriorOutline);g.dispose();
    }
    /** Draw at the top-left of the cell, in the caller's translated coordinate space. */
    public void drawFloor(Graphics2D original,Cell c){
        Tile floor=ground.get(c);if(floor==null)return;
        Graphics2D g=(Graphics2D)original.create();
        g.clipRect(0,0,ts,ts);
        Area clip=clips.get(c);if(clip!=null)g.clip(clip);
        int connections=mask(c,ground.keySet());
        if(floor.shape()==BuildElements.Shape.STAIRS){
            connections=0;for(int i=0;i<4;i++){
                Tile neighbor=ground.get(c.offset(DC[i],DR[i]));
                if(neighbor!=null && neighbor.element().equals(floor.element()))connections|=1<<i;
            }
        }
        g.drawImage(textures.get(floor.element(),connections,false,false),0,0,null);
        BufferedImage shadow=shadows.get(c);if(shadow!=null)g.drawImage(shadow,0,0,null);
        int[] a={0,0,2,2},b={3,1,3,1},dx={-1,1,-1,1},dy={-1,-1,1,1};
        for(int i=0;i<4;i++)if(ground.containsKey(c.offset(DC[a[i]],DR[a[i]])) && ground.containsKey(c.offset(DC[b[i]],DR[b[i]])) && !ground.containsKey(c.offset(dx[i],dy[i]))){
            g.setColor(new Color(floor.element().startsWith("cobblestone") || floor.element().startsWith("concrete")?0x948D90:0x8A6B58));int edge=Math.max(2,ts/16);
            g.fillRect(dx[i]<0?0:ts-edge,dy[i]<0?0:ts-edge,edge,edge);
        }
        drawExteriorOutline(g,c,null,floor);
        g.dispose();
    }
    public void drawStructure(Graphics2D g,Cell cell){
        Tile tile=layout.structures().get(cell);
        if(tile!=null){
            drawStructure(g,cell,tile,layout.structures()::get,ts,textures);
            drawExteriorOutline(g,cell,structureCoverage.get(cell),tile);
        }
    }
    /** Caller translates to the cell origin; neighbor walls yield their borrowed frame strips. */
    public static void drawStructure(Graphics2D original,Cell cell,Tile tile,
            java.util.function.Function<Cell,Tile> structure,int tileSize,BuildTextures textures){
        int mask=BuildingLayout.structureMask(cell,structure);
        int[] extension=tile.shape()==BuildElements.Shape.DOOR?BuildingLayout.doorExtensions(cell,tile,structure):new int[2];
        BuildTextures.StructureSprite sprite=textures.structure(tile.element(),mask,tile.open(),tile.vertical(),extension[0],extension[1]);
        Graphics2D g=(Graphics2D)original.create();
        if(tile.shape()==BuildElements.Shape.WALL){
            int[] trims=BuildingLayout.wallTrims(cell,tile,structure);
            Area allowed=new Area(new Rectangle2D.Double(0,-BuildGeometry.HEIGHT,48,BuildGeometry.IMAGE_HEIGHT));
            if(trims[3]>0)allowed.subtract(new Area(new Rectangle2D.Double(0,-BuildGeometry.HEIGHT,trims[3],BuildGeometry.IMAGE_HEIGHT)));
            if(trims[1]>0)allowed.subtract(new Area(new Rectangle2D.Double(48-trims[1],-BuildGeometry.HEIGHT,trims[1],BuildGeometry.IMAGE_HEIGHT)));
            if(trims[0]>0)allowed.subtract(new Area(new Rectangle2D.Double(16,-BuildGeometry.HEIGHT,16,trims[0])));
            if(trims[2]>0)allowed.subtract(new Area(new Rectangle2D.Double(16,48-trims[2]-BuildGeometry.HEIGHT,16,trims[2])));
            allowed.transform(AffineTransform.getScaleInstance(tileSize/48.0,tileSize/48.0));g.clip(allowed);
        }
        g.drawImage(sprite.image(),sprite.offsetX(),tileSize-sprite.image().getHeight()+sprite.offsetY(),null);g.dispose();
    }
    public static double decorationDepth(Decoration d,int tileSize){
        var p=PropCatalog.get(d.element());
        double foot=p.mounted()?d.y()+BuildGeometry.HEIGHT:p.collisionWidth()>0?p.bounds(d.x(),d.y(),d.flipped(),d.rotation()).getMaxY():d.y();
        return foot*tileSize/48+1;
    }
    public static void drawDecoration(Graphics2D g,Decoration d,int tileSize,BuildTextures textures){
        var sprite=textures.prop(d.element(),d.flipped(),d.rotation());
        var prop=PropCatalog.get(d.element());
        if(!prop.ground() && !prop.mounted()){
            Color old=g.getColor();g.setColor(new Color(49,43,37,28));
            var box=prop.bounds(d.x(),d.y(),d.flipped(),d.rotation());
            g.fill(new Ellipse2D.Double(box.getX()*tileSize/48,(box.getMaxY()-8)*tileSize/48,
                    box.getWidth()*tileSize/48,12.0*tileSize/48));g.setColor(old);
        }
        g.drawImage(sprite.image(),(int)Math.round(d.x()*tileSize/48)-sprite.anchorX(),(int)Math.round(d.y()*tileSize/48)-sprite.anchorY(),null);
    }
    public void drawGroundDecorations(Graphics2D g){for(Decoration d:layout.decorations())if(PropCatalog.get(d.element()).ground())drawDecoration(g,d,ts,textures);}
    /** Transparent image with enough headroom above row zero for raised walls. */
    public BufferedImage image(int width,int height){return image(width,height,true,true);}
    public BufferedImage image(int width,int height,boolean showFloors,boolean showStructures){
        int head=BuildGeometry.HEIGHT*ts/48;
        BufferedImage image=new BufferedImage(width*ts,height*ts+head,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.translate(0,head);
        if(showFloors)for(Cell c:ground.keySet()){
            Graphics2D tile=(Graphics2D)g.create();tile.translate(c.col()*ts,c.row()*ts);drawFloor(tile,c);tile.dispose();
        }
        if(showFloors)drawGroundDecorations(g);
        if(showStructures){
            record Layer(double depth,Cell cell,Decoration object) {}
            List<Layer> layers=new ArrayList<>();
            for(Cell c:layout.structures().keySet())layers.add(new Layer((c.row()+1)*ts,c,null));
            for(Decoration d:layout.decorations())if(!PropCatalog.get(d.element()).ground())layers.add(new Layer(decorationDepth(d,ts),null,d));
            layers.sort(Comparator.comparingDouble(Layer::depth));
            for(Layer layer:layers){
                if(layer.object!=null)drawDecoration(g,layer.object,ts,textures);
                else{Cell c=layer.cell;Graphics2D tile=(Graphics2D)g.create();tile.translate(c.col()*ts,c.row()*ts);drawStructure(tile,c);tile.dispose();}
            }
        }
        g.dispose();return image;
    }
}
