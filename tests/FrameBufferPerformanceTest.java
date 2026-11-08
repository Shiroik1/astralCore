import main.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Slow display consumers cannot block the producer or expose a partially updated buffer. */
public class FrameBufferPerformanceTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void fill(BufferedImage image,Color color){Graphics2D g=image.createGraphics();g.setColor(color);g.fillRect(0,0,image.getWidth(),image.getHeight());g.dispose();}
    public static void main(String[] args)throws Exception{
        FrameBuffer buffers=new FrameBuffer();BufferedImage source=new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB);
        fill(source,Color.red);require(buffers.publish(source,320,180),"initial frame");
        try(var slow=buffers.acquire()){
            long sequence=slow.sequence();
            for(int i=0;i<100;i++){
                fill(source,i%2==0?Color.green:Color.blue);
                require(buffers.publish(source,320,180),"producer keeps publishing while old frame is held");
                require(slow.image().getRGB(160,90)==Color.red.getRGB(),"leased old frame never changes");
            }
            try(var current=buffers.acquire()){require(current.sequence()>sequence,"latest frame advances independently");}
        }
        try(var one=buffers.acquire()){
            buffers.publish(source,320,180);
            try(var two=buffers.acquire()){
                buffers.publish(source,320,180);
                try(var three=buffers.acquire()){
                    require(!buffers.publish(source,320,180),"busy buffers skip rather than block");
                }
            }
        }
        require(buffers.publish(source,320,180),"producer resumes after consumer releases frames");
        var game=new AppearanceEquipmentTest.QuietGame();
        var selected=DisplaySettings.class.getDeclaredField("selected");selected.setAccessible(true);
        selected.set(game.display,new DisplaySettings.Resolution(3840,2160));
        var size=game.display.frameResolution();
        require(size.width()==game.screenWidth && size.height()==game.screenHeight,"4K output does not require a 4K CPU staging buffer");
        BufferedImage scene=new BufferedImage(game.screenWidth,game.screenHeight,BufferedImage.TYPE_INT_RGB);
        require(game.display.renderFrame(scene).getWidth()==game.screenWidth,"preparation stays at scene resolution");
        selected.set(game.display,new DisplaySettings.Resolution(640,480));size=game.display.frameResolution();
        require(size.width()<=640 && size.height()<=480,"lower output resolutions remain supported");
        System.out.println("PASS: nonblocking triple buffering, frame leases, exhausted-buffer recovery and bounded high-resolution preparation.");
    }
}
