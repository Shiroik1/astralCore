package art;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;

/** Offline import of the owner's 6x4 sprite sheet. No recoloring, palette thresholds or redraws. */
public final class GenerateNatureTextures {
    private static final Path SOURCE=Path.of("assets/reference/environment/user-supplied-atlas.png");
    private GenerateNatureTextures(){}
    public static void main(String[] args)throws IOException{
        Path source=args.length==0?SOURCE:Path.of(args[0]);
        BufferedImage atlas=ImageIO.read(source.toFile());
        if(atlas==null || atlas.getWidth()%6!=0 || atlas.getHeight()%4!=0)
            throw new IOException("Expected the supplied 6-column, 4-row sprite sheet: "+source);
        Path nature=Path.of("res/scenery/nature"),meadow=Path.of("res/scenery/meadow");
        Files.createDirectories(nature);Files.createDirectories(meadow);
        BufferedImage[] sprites=new BufferedImage[24];
        for(int i=0;i<sprites.length;i++)sprites[i]=cell(atlas,i);
        for(NatureProp prop:NatureProp.values()){
            ArtKit.Surface surface=new ArtKit.Surface(prop.width*NatureProp.EXPORT_SCALE,prop.height*NatureProp.EXPORT_SCALE);
            Graphics2D g=surface.g;quality(g);g.scale(NatureProp.EXPORT_SCALE,NatureProp.EXPORT_SCALE);
            fit(g,sprites[prop.ordinal()],prop.width/2.0,prop.footY,prop.width-6,prop.footY-3);
            ImageIO.write(surface.finish(),"png",nature.resolve(prop.assetName+".png").toFile());
        }
        for(int type=3;type<GrassArt.TYPES;type++){
            int width=GrassArt.width(type),height=GrassArt.height(type),variants=GrassArt.VARIANTS[type];
            BufferedImage sheet=new BufferedImage(width*GrassArt.BEND_FRAMES,
                    height*variants*Palette.GROUND.length,BufferedImage.TYPE_INT_ARGB);
            Graphics2D target=sheet.createGraphics();
            for(int variant=0;variant<variants;variant++)for(int frame=0;frame<GrassArt.BEND_FRAMES;frame++){
                int index=type==3?(variant==0?19:22):type==4?20:variant==0?21:23;
                double bend=frame*2.0/(GrassArt.BEND_FRAMES-1)-1;
                ArtKit.Surface surface=new ArtKit.Surface(width,height);Graphics2D g=surface.g;quality(g);
                g.translate(width/2.0,GrassArt.rootY(type));g.shear(-bend*.16,0);
                g.translate(-width/2.0,-GrassArt.rootY(type));
                fit(g,sprites[index],width/2.0,GrassArt.rootY(type),66,variant==0?82:69);
                BufferedImage image=surface.finish();
                for(int level=0;level<Palette.GROUND.length;level++)
                    target.drawImage(image,frame*width,(level*variants+variant)*height,null);
            }
            target.dispose();ImageIO.write(sheet,"png",meadow.resolve(GrassArt.name(type)+".png").toFile());
        }
    }
    private static BufferedImage cell(BufferedImage atlas,int index)throws IOException{
        int width=atlas.getWidth()/6,height=atlas.getHeight()/4;
        BufferedImage cell=atlas.getSubimage(index%6*width,index/6*height,width,height);
        int left=width,top=height,right=-1,bottom=-1;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((cell.getRGB(x,y)>>>24)>24){
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
        }
        if(right<left)throw new IOException("Empty sprite cell "+index);
        // Bounds only: alpha and RGB values inside the sprite are copied unchanged.
        return cell.getSubimage(left,top,right-left+1,bottom-top+1);
    }
    private static void fit(Graphics2D g,BufferedImage image,double center,double root,int maxWidth,int maxHeight){
        double scale=Math.min(maxWidth/(double)image.getWidth(),maxHeight/(double)image.getHeight());
        java.awt.geom.AffineTransform transform=java.awt.geom.AffineTransform.getTranslateInstance(
                center-image.getWidth()*scale/2,root-image.getHeight()*scale);
        transform.scale(scale,scale);g.drawImage(image,transform,null);
    }
    private static void quality(Graphics2D g){
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
    }
}
