package art;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Two logical pixels of muted ink, cached at the actual icon display size. */
public final class ItemOutline {
    private static final int PAD=2,INK=0x62545B;
    private static final Map<BufferedImage,Map<Long,BufferedImage>> CACHE=new WeakHashMap<>();
    private ItemOutline(){}
    public static void draw(Graphics2D g,BufferedImage image,int x,int y,int width,int height){
        if(image!=null)g.drawImage(prepare(image,width,height),x-PAD,y-PAD,null);
    }
    public static synchronized BufferedImage prepare(BufferedImage source,int width,int height){
        long key=((long)width<<32)|(height&0xffffffffL);
        Map<Long,BufferedImage> sizes=CACHE.computeIfAbsent(source,k->new LinkedHashMap<>());
        BufferedImage cached=sizes.get(key);if(cached!=null)return cached;
        BufferedImage scaled=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=scaled.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source,0,0,width,height,null);g.dispose();
        BufferedImage outline=new BufferedImage(width+PAD*2,height+PAD*2,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int alpha=scaled.getRGB(x,y)>>>24;if(alpha<100)continue;
            for(int dy=-PAD;dy<=PAD;dy++)for(int dx=-PAD;dx<=PAD;dx++){
                if(dx*dx+dy*dy>PAD*PAD)continue;
                int px=x+PAD+dx,py=y+PAD+dy;
                if((outline.getRGB(px,py)>>>24)<alpha)outline.setRGB(px,py,(alpha<<24)|INK);
            }
        }
        // Resizing windows cannot retain an unbounded set of raster sizes.
        if(sizes.size()>=6)sizes.remove(sizes.keySet().iterator().next());
        sizes.put(key,outline);return outline;
    }
}
