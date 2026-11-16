package building;

import main.MiniJson;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Freely positioned interior objects. Design units use the same 48-pixel scale as building tiles. */
public final class PropCatalog {
    public record Prop(String id,String name,String image,int width,int height,int anchorX,int anchorY,
                       int collisionX,int collisionY,int collisionWidth,int collisionHeight,
                       boolean ground,boolean mounted,int lightRadius){
        @Override public String toString(){return name;}
        public boolean rotatable(){return id.startsWith("bed_") || id.startsWith("rug_");}
        public boolean horizontalBed(int rotation){return id.startsWith("bed_") && (rotation&1)==1;}
        /** Horizontal beds have their own upright artwork and grounded footprint. */
        public Prop oriented(int rotation){
            if(!horizontalBed(rotation))return this;
            return new Prop(id,name,"/building/props/"+id+"_horizontal.png",96,72,48,70,
                    -43,-42,86,42,ground,mounted,lightRadius);
        }
        public java.awt.geom.AffineTransform transform(boolean flipped,int rotation){
            var t=java.awt.geom.AffineTransform.getQuadrantRotateInstance(rotation);
            if(flipped)t.scale(-1,1);
            return t;
        }
        private Rectangle2D transformed(double x,double y,boolean flipped,int rotation,Rectangle2D rect){
            var t=java.awt.geom.AffineTransform.getTranslateInstance(x,y);t.concatenate(transform(flipped,rotation));
            return t.createTransformedShape(rect).getBounds2D();
        }
        public Rectangle2D bounds(double x,double y,boolean flipped){return bounds(x,y,flipped,0);}
        public Rectangle2D bounds(double x,double y,boolean flipped,int rotation){
            if(horizontalBed(rotation))return oriented(rotation).bounds(x,y,flipped^(rotation==3),0);
            return transformed(x,y,flipped,rotation,new Rectangle2D.Double(collisionX,collisionY,collisionWidth,collisionHeight));
        }
        public Rectangle2D visualBounds(double x,double y,boolean flipped,int rotation){
            if(horizontalBed(rotation))return oriented(rotation).visualBounds(x,y,flipped^(rotation==3),0);
            return transformed(x,y,flipped,rotation,new Rectangle2D.Double(-anchorX,-anchorY,width,height));
        }
        public Rectangle2D placementBounds(double x,double y){return placementBounds(x,y,false,0);}
        public Rectangle2D placementBounds(double x,double y,boolean flipped,int rotation){
            return ground?visualBounds(x,y,flipped,rotation)
                    :collisionWidth>0?bounds(x,y,flipped,rotation):new Rectangle2D.Double(x-.5,y-.5,1,1);
        }
    }
    private static final Map<String,Prop> PROPS=load();
    private PropCatalog(){}
    public static Collection<Prop> all(){return PROPS.values();}
    public static boolean has(String id){return PROPS.containsKey(id);}
    public static Prop get(String id){Prop p=PROPS.get(id);if(p==null)throw new IllegalArgumentException("Unknown furnishing: "+id);return p;}
    private static Map<String,Prop> load(){
        try(InputStream in=PropCatalog.class.getResourceAsStream("/building/props.json")){
            if(in==null)throw new IOException("Missing interior catalog");
            if(!(MiniJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8)) instanceof Map<?,?> root)
                    || !(root.get("props") instanceof List<?> list))throw new IOException("Invalid interior catalog");
            Map<String,Prop> result=new LinkedHashMap<>();
            for(Object item:list){
                if(!(item instanceof Map<?,?> e))throw new IOException("Invalid furnishing entry");
                String id=(String)e.get("id"),image=(String)e.get("image");
                if(id==null || !id.matches("[a-z][a-z0-9_-]*") || image==null || !image.startsWith("/building/props/") || image.contains(".."))throw new IOException("Invalid furnishing resource");
                Prop p=new Prop(id,(String)e.get("name"),image,n(e,"width"),n(e,"height"),n(e,"anchorX"),n(e,"anchorY"),
                        n(e,"collisionX"),n(e,"collisionY"),n(e,"collisionWidth"),n(e,"collisionHeight"),Boolean.TRUE.equals(e.get("ground")),Boolean.TRUE.equals(e.get("mounted")),n(e,"lightRadius"));
                if(p.width<1 || p.height<1 || p.collisionWidth<0 || p.collisionHeight<0 || result.putIfAbsent(id,p)!=null)throw new IOException("Invalid furnishing dimensions or duplicate id");
            }
            return Collections.unmodifiableMap(result);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static int n(Map<?,?> e,String key){return e.get(key) instanceof Number n?n.intValue():0;}
}
