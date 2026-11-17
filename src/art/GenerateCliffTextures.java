package art;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.IOException;

/** Offline native-vector asset production. Samples existing PNGs, writes only the new cliff resource family. */
public final class GenerateCliffTextures {
    private static final int U=48,SS=4;
    private static BufferedImage grass;
    private static Color earth,shade,light,stone;
    private interface Painter{void paint(Graphics2D g);}
    private static BufferedImage sprite(Painter painter){
        var large=new BufferedImage(U*SS,U*SS,BufferedImage.TYPE_INT_ARGB);var g=large.createGraphics();
        g.scale(SS,SS);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);painter.paint(g);g.dispose();
        var result=new BufferedImage(U,U,BufferedImage.TYPE_INT_ARGB);g=result.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(large,0,0,U,U,null);g.dispose();return result;
    }
    private static Color mix(Color a,Color b,double t){return new Color((int)Math.round(a.getRed()*(1-t)+b.getRed()*t),(int)Math.round(a.getGreen()*(1-t)+b.getGreen()*t),(int)Math.round(a.getBlue()*(1-t)+b.getBlue()*t));}
    private static BufferedImage read(Path directory,String file)throws IOException{var image=ImageIO.read(directory.resolve(file).toFile());if(image==null)throw new IOException("Missing source artwork: "+file);return image;}
    private static void write(Path directory,String name,int columns,int rows,java.util.function.IntFunction<BufferedImage> make)throws IOException{
        var image=new BufferedImage(columns*U,rows*U,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();for(int i=0;i<columns*rows;i++)g.drawImage(make.apply(i),i%columns*U,i/columns*U,null);g.dispose();ImageIO.write(image,"png",directory.resolve(name+".png").toFile());
    }
    private static void material(Graphics2D g){
        g.setColor(earth);g.fillRect(0,0,U,U);
        // Broad periodic facets. Copies crossing tile edges have identical pixels on neighbouring repeats.
        for(int oy=-48;oy<=48;oy+=48)for(int ox=-48;ox<=48;ox+=48){
            var facet=new Path2D.Double();facet.moveTo(ox-8,oy+10);facet.curveTo(ox+2,oy+3,ox+12,oy+4,ox+21,oy+15);facet.lineTo(ox+15,oy+32);facet.curveTo(ox+5,oy+33,ox-2,oy+24,ox-8,oy+10);facet.closePath();
            g.setColor(mix(earth,light,.48));g.fill(facet);
            var dark=new Path2D.Double();dark.moveTo(ox+31,oy-6);dark.lineTo(ox+43,oy+7);dark.curveTo(ox+40,oy+21,ox+35,oy+27,ox+28,oy+31);dark.lineTo(ox+24,oy+16);dark.closePath();g.setColor(mix(earth,shade,.34));g.fill(dark);
            g.setColor(mix(earth,stone,.12));g.fill(new Ellipse2D.Double(ox+17,oy+37,8,4));
        }
    }
    public static void main(String[] args)throws IOException{
        Path directory=Path.of(args.length==0?"res/ground":args[0]);
        grass=read(directory,"grass.png");var ledge=read(directory,"ledge.png");var path=read(directory,"path-0.png");var cobble=read(directory,"cobble.png");
        shade=new Color(ledge.getRGB(3*48+24,36));var dirt=new Color(path.getRGB(15*48+24,15*48+24));stone=new Color(cobble.getRGB(24,24));
        earth=mix(shade,dirt,.16);light=mix(earth,dirt,.24);
        write(directory,"cliff-middle",1,1,i->sprite(GenerateCliffTextures::material));
        write(directory,"cliff-side-left",1,1,i->sprite(g->{material(g);g.setColor(new Color(shade.getRed(),shade.getGreen(),shade.getBlue(),38));g.fill(new RoundRectangle2D.Double(-5,-4,14,56,8,8));}));
        write(directory,"cliff-side-right",1,1,i->sprite(g->{material(g);g.setColor(new Color(shade.getRed(),shade.getGreen(),shade.getBlue(),65));g.fill(new RoundRectangle2D.Double(38,-4,15,56,8,8));}));
        write(directory,"cliff-lip",4,1,i->sprite(g->{Color green=new Color(grass.getRGB(i*48+24,24));g.setColor(mix(green,shade,.12));g.fillRect(0,0,48,48);g.setColor(green);for(int y=-12;y<=48;y+=12)for(int x=-12;x<=48;x+=12)g.fill(new Ellipse2D.Double(x-4,y-3,14,11));}));
        write(directory,"cliff-foot",4,1,i->sprite(g->{material(g);Color green=new Color(grass.getRGB(i*48+24,24));g.setColor(new Color(green.getRed(),green.getGreen(),green.getBlue(),65));for(int y=-8;y<56;y+=16)for(int x=-6;x<54;x+=16)g.fill(new Ellipse2D.Double(x,y,20,10));}));
        for(int tone=0;tone<4;tone++){
            final int row=tone;
            write(directory,"cliff-top-"+tone,16,16,mask->sprite(g->{g.clip(CliffTiles.surface(mask));g.drawImage(grass,0,0,48,48,row*48,0,(row+1)*48,48,null);}));
        }
        Files.writeString(directory.resolve("cliff-tiles.json"),"""
                {"tileSize":48,"heightStep":24,"maskBits":{"N":1,"E":2,"S":4,"W":8,"NE":16,"SE":32,"SW":64,"NW":128},
                 "caps":{"pattern":"cliff-top-{tone}.png","tones":4,"columns":16,"rows":16,"index":"mask"},
                 "faces":{"middle":"cliff-middle.png","left":"cliff-side-left.png","right":"cliff-side-right.png","lip":"cliff-lip.png","foot":"cliff-foot.png"},
                 "faceRules":"Repeat native middle pixels; lip only along highest exposed cap; foot only at the lower union boundary. No intermediate caps."}
                """);
        System.out.println("Exported native 48px cliff sprites from existing PNG palette: earth="+Integer.toHexString(earth.getRGB())+" shadow="+Integer.toHexString(shade.getRGB()));
    }
}
