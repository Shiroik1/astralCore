package crafting;

import art.Palette;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** Approved atlas icons where available; explicit simple placeholders for missing artwork. */
public final class CraftingArt {
    private static final Map<String,BufferedImage> CACHE=new HashMap<>();
    private CraftingArt(){}
    public static BufferedImage icon(RecipeRegistry.Item item){return CACHE.computeIfAbsent(item.id(),k->load(item));}
    private static BufferedImage load(RecipeRegistry.Item item){
        if(!item.image().isEmpty())try(InputStream in=CraftingArt.class.getResourceAsStream(item.image())){
            if(in==null)throw new IOException("Missing declared image "+item.image());
            BufferedImage source=ImageIO.read(in);if(source==null)throw new IOException("Invalid image");
            // Building material icons use the isolated first atlas cell.
            if(item.place().equals("floor") || item.place().equals("wall") || item.place().equals("door"))source=source.getSubimage(0,0,96,source.getHeight());
            return source;
        }catch(IOException e){throw new UncheckedIOException(e);}
        BufferedImage out=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(item.id().contains("iron")?Palette.IRON:item.id().equals("fiber")?Palette.FOLIAGE:item.id().equals("charcoal")?new Color(0x554B50):Palette.WOOD);
        if(item.id().equals("twig") || item.id().equals("wooden_arrow")){g.setStroke(new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawLine(15,50,47,12);g.drawLine(30,32,18,19);}
        else if(item.id().equals("fiber")){for(int i=0;i<4;i++)g.fillRoundRect(18+i*6,10+i*2,5,43,5,5);}
        else if(item.equipment()){
            g.setStroke(new BasicStroke(7,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawLine(24,53,37,12);
            if(item.kind().equals("axe"))g.fillRoundRect(14,12,37,12,5,5);
            else g.fillPolygon(new int[]{29,37,44,34},new int[]{39,5,15,41},4);
        }else if(item.place().equals("campfire")){
            g.setColor(Palette.MORTAR);g.fillOval(7,39,50,18);g.setColor(Palette.GOLD);g.fillPolygon(new int[]{20,32,43},new int[]{46,9,46},3);g.setColor(Palette.GLOW);g.fillOval(27,29,12,19);
        }else if(item.place().equals("chest")){
            g.fillRoundRect(7,18,50,36,8,8);g.setColor(Palette.WOOD_LIGHT);g.fillRect(8,30,48,5);g.setColor(Palette.GOLD);g.fillRect(28,30,8,12);
        }else g.fillPolygon(new int[]{13,29,49,54,39,12},new int[]{26,12,19,40,53,44},6);
        g.dispose();return out;
    }
}
