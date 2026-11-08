package main;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Three reusable scene-sized buffers: updating never waits for a slow window repaint. */
public final class FrameBuffer {
    private static final class Slot {
        BufferedImage image;
        int readers;
        boolean writing;
        long sequence;
    }
    public final class Frame implements AutoCloseable {
        private Slot slot;
        private Frame(Slot slot){this.slot=slot;}
        public BufferedImage image(){return slot.image;}
        public long sequence(){return slot.sequence;}
        @Override public void close(){
            synchronized(FrameBuffer.this){
                if(slot!=null){slot.readers--;slot=null;}
            }
        }
    }
    private final Slot[] slots={new Slot(),new Slot(),new Slot()};
    private Slot front;
    private long sequence;

    public boolean publish(BufferedImage source,int width,int height){
        Slot next=null;
        synchronized(this){
            for(Slot slot:slots)if(slot!=front && slot.readers==0 && !slot.writing){
                next=slot;next.writing=true;break;
            }
        }
        if(next==null)return false; // Keep the latest complete frame, never stall simulation.
        try{
            if(next.image==null || next.image.getWidth()!=width || next.image.getHeight()!=height)
                next.image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=next.image.createGraphics();
            try{
                g.setComposite(AlphaComposite.Src);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,entity.CharacterSprites.pixelMode?RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        :RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(source,0,0,width,height,null);
            }finally{g.dispose();}
            synchronized(this){next.sequence=++sequence;front=next;}
            return true;
        }finally{synchronized(this){next.writing=false;}}
    }
    public synchronized Frame acquire(){
        if(front==null)return null;
        front.readers++;return new Frame(front);
    }
}
