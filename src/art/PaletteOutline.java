package art;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Offline inward silhouette stroke: preserves sprite dimensions, alpha and connected tile edges. */
public final class PaletteOutline {
    private PaletteOutline(){}
    public static void apply(BufferedImage image,Color color,int radius,int connections){
        int w=image.getWidth(),h=image.getHeight();
        int[] pixels=image.getRGB(0,0,w,h,null,0,w);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int pixel=pixels[y*w+x],alpha=pixel>>>24;if(alpha==0)continue;
            int lowest=alpha;
            for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){
                if(dx*dx+dy*dy>radius*radius)continue;
                int nx=x+dx,ny=y+dy;
                // Continue sampling the opaque neighboring tile across connected seams.
                if(nx<0 && (connections&8)!=0 || nx>=w && (connections&2)!=0
                        || ny<0 && (connections&1)!=0 || ny>=h && (connections&4)!=0)continue;
                int neighbor=nx<0 || nx>=w || ny<0 || ny>=h?0:pixels[ny*w+nx]>>>24;
                lowest=Math.min(lowest,neighbor);
            }
            double coverage=(alpha-lowest)/(double)alpha;
            int r=(int)Math.round(((pixel>>>16)&255)*(1-coverage)+color.getRed()*coverage);
            int g=(int)Math.round(((pixel>>>8)&255)*(1-coverage)+color.getGreen()*coverage);
            int b=(int)Math.round((pixel&255)*(1-coverage)+color.getBlue()*coverage);
            image.setRGB(x,y,(alpha<<24)|(r<<16)|(g<<8)|b);
        }
    }
}
