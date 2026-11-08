package main;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

/** Upload at scene resolution, then scale a GPU texture instead of scaling mutable CPU pixels. */
public final class GpuFramePresenter {
    private VolatileImage texture;
    private GraphicsConfiguration configuration;
    private BufferedImage uploadedSource;
    private long uploadedSequence=Long.MIN_VALUE;

    public void draw(Graphics2D target,BufferedImage source,long sequence,int x,int y,int width,int height){
        GraphicsConfiguration next=target.getDeviceConfiguration();
        if(next==null || next.getDevice().getType()!=GraphicsDevice.TYPE_RASTER_SCREEN){
            target.drawImage(source,x,y,width,height,null);return;
        }
        for(int attempt=0;attempt<3;attempt++){
            boolean recreate=texture==null || configuration!=next || texture.getWidth()!=source.getWidth()
                    || texture.getHeight()!=source.getHeight();
            if(recreate)createTexture(next,source);
            int validation=texture.validate(next);
            if(validation==VolatileImage.IMAGE_INCOMPATIBLE){createTexture(next,source);validation=VolatileImage.IMAGE_RESTORED;}
            if(recreate || validation==VolatileImage.IMAGE_RESTORED || uploadedSource!=source || uploadedSequence!=sequence || sequence<0){
                Graphics2D upload=texture.createGraphics();
                try{
                    upload.setComposite(AlphaComposite.Src);
                    upload.drawImage(source,0,0,null);
                }finally{upload.dispose();}
                uploadedSource=source;uploadedSequence=sequence;
            }
            target.drawImage(texture,x,y,width,height,null);
            if(!texture.contentsLost())return;
            uploadedSequence=Long.MIN_VALUE;
        }
        // A display-mode transition can invalidate surfaces repeatedly. Keep a complete frame visible.
        target.drawImage(source,x,y,width,height,null);
    }
    private void createTexture(GraphicsConfiguration next,BufferedImage source){
        if(texture!=null)texture.flush();
        configuration=next;
        texture=next.createCompatibleVolatileImage(source.getWidth(),source.getHeight(),Transparency.OPAQUE);
        uploadedSource=null;uploadedSequence=Long.MIN_VALUE;
    }
    public void dispose(){
        if(texture!=null)texture.flush();texture=null;configuration=null;uploadedSource=null;
    }
}
