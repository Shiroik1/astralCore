package art;

import building.BuildElements;
import building.PropCatalog;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/** Shared saved PNG atlas slices, loaded lazily once per element and tile size. */
public final class BuildTextures {
    private final int tileSize;
    private final Map<String,BufferedImage[]> sprites=new HashMap<>();
    public record StructureSprite(BufferedImage image,int offsetX,int offsetY) {}
    private record DoorKey(String element,int mask,boolean open,boolean vertical,int before,int after) {}
    private final Map<DoorKey,StructureSprite> doors=new HashMap<>();
    public BuildTextures(int tileSize){
        if(tileSize<16)throw new IllegalArgumentException("Tile size must be at least 16");
        this.tileSize=tileSize;
    }
    private BufferedImage[] load(String resource,boolean floor){
        int count=floor?16:32,height=floor?48:BuildGeometry.IMAGE_HEIGHT;
        try(InputStream stream=BuildTextures.class.getResourceAsStream(resource)){
            if(stream==null)throw new IOException("Missing building atlas: "+resource);
            BufferedImage sheet=ImageIO.read(stream);
            if(sheet==null || sheet.getWidth()!=96*count || sheet.getHeight()!=height*2)
                throw new IOException("Invalid building atlas: "+resource);
            BufferedImage[] result=new BufferedImage[count];
            for(int variant=0;variant<count;variant++){
                BufferedImage slice=sheet.getSubimage(variant*96,0,96,height*2);
                BufferedImage scaled=new BufferedImage(tileSize,height*tileSize/48,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=scaled.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(slice,0,0,scaled.getWidth(),scaled.getHeight(),null);g.dispose();result[variant]=scaled;
            }
            return result;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public BufferedImage get(String element,int mask,boolean open,boolean vertical){
        BuildElements.Element e=BuildElements.get(element);
        boolean floor=e.shape().ground();
        String resource=e.shape()==BuildElements.Shape.DOOR && open?e.openAtlas():e.atlas();
        return sprites.computeIfAbsent((floor?"floor:":"structure:")+resource,key->load(resource,floor))[(mask&15)+(!floor && vertical?16:0)];
    }
    public StructureSprite structure(String element,int mask,boolean open,boolean preferred,int before,int after){
        BufferedImage source=get(element,mask,open,preferred);
        if(before==0 && after==0)return new StructureSprite(source,0,0);
        return doors.computeIfAbsent(new DoorKey(element,mask,open,preferred,before,after),key->{
            boolean vertical=BuildGeometry.vertical(mask,preferred);
            int leading=before*tileSize/48,trailing=after*tileSize/48;
            BufferedImage wider=new BufferedImage(source.getWidth()+(vertical?0:leading+trailing),
                    source.getHeight()+(vertical?leading+trailing:0),BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=wider.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if(vertical){
                // Extend the ground axis while retaining the original upright wall/door height.
                int split=tileSize,stretched=tileSize+leading+trailing;
                g.drawImage(source,0,0,tileSize,stretched,0,0,tileSize,split,null);
                g.drawImage(source,0,stretched,tileSize,wider.getHeight(),0,split,tileSize,source.getHeight(),null);
            }else g.drawImage(source,0,0,wider.getWidth(),wider.getHeight(),null);
            g.dispose();return new StructureSprite(wider,vertical?0:-leading,vertical?trailing:0);
        });
    }
    public record PropSprite(BufferedImage image,int anchorX,int anchorY) {}
    private final Map<String,PropSprite> propSprites=new HashMap<>();
    public PropSprite prop(String id,boolean flipped){return prop(id,flipped,0);}
    public PropSprite prop(String id,boolean flipped,int rotation){
        int turn=PropCatalog.get(id).rotatable()?Math.floorMod(rotation,4):0;
        return propSprites.computeIfAbsent(id+":"+flipped+":"+turn,key->{
            PropCatalog.Prop base=PropCatalog.get(id);
            boolean horizontal=base.horizontalBed(turn);
            PropCatalog.Prop p=base.oriented(turn);
            int artTurn=horizontal?0:turn;
            boolean artFlip=flipped^(horizontal && turn==3);
            try(InputStream stream=BuildTextures.class.getResourceAsStream(p.image())){
                if(stream==null)throw new IOException("Missing furnishing: "+p.image());
                BufferedImage source=ImageIO.read(stream);if(source==null)throw new IOException("Invalid furnishing: "+id);
                double scale=tileSize/48.0;
                var box=p.visualBounds(0,0,artFlip,artTurn);
                int left=(int)Math.floor(box.getMinX()*scale),top=(int)Math.floor(box.getMinY()*scale);
                int w=Math.max(1,(int)Math.ceil(box.getMaxX()*scale)-left),h=Math.max(1,(int)Math.ceil(box.getMaxY()*scale)-top);
                BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.translate(-left,-top);g.scale(scale,scale);g.transform(p.transform(artFlip,artTurn));
                g.translate(-p.anchorX(),-p.anchorY());g.scale((double)p.width()/source.getWidth(),(double)p.height()/source.getHeight());
                g.drawImage(source,0,0,null);g.dispose();
                return new PropSprite(image,-left,-top);
            }catch(IOException e){throw new UncheckedIOException(e);}
        });
    }
    /** Compatibility for existing art callers. */
    public BufferedImage get(int kind,int mask,boolean open,boolean vertical){
        return get(List.of("floor","wall","door").get(kind),mask,open,vertical);
    }
}
