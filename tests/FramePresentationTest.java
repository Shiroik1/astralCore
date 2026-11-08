import main.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Complete frames are handed to Swing; no direct window drawing or half-painted frames. */
public class FramePresentationTest {
    static class Panel extends AppearanceEquipmentTest.QuietGame {
        Panel()throws Exception{super();}
        @Override public Graphics getGraphics(){throw new AssertionError("Live window graphics must never be used");}
    }
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void fill(BufferedImage image,Color color){
        Graphics2D g=image.createGraphics();g.setColor(color);g.fillRect(0,0,image.getWidth(),image.getHeight());g.dispose();
    }
    static int paintCentre(Panel panel,BufferedImage destination)throws Exception{
        int[] color={0};
        SwingUtilities.invokeAndWait(()->{
            Graphics2D g=destination.createGraphics();panel.paint(g);g.dispose();
            color[0]=destination.getRGB(panel.getWidth()/2,panel.getHeight()/2);
        });return color[0];
    }
    public static void main(String[] args)throws Exception{
        Panel panel=new Panel();SwingUtilities.invokeAndWait(()->panel.setSize(1280,720));
        BufferedImage source=new BufferedImage(panel.screenWidth,panel.screenHeight,BufferedImage.TYPE_INT_RGB);
        var screen=Gamepanel.class.getDeclaredField("tempScreen");screen.setAccessible(true);screen.set(panel,source);
        BufferedImage destination=new BufferedImage(1600,1000,BufferedImage.TYPE_INT_RGB);
        fill(source,Color.green);panel.drawToScreen();
        require(paintCentre(panel,destination)==Color.green.getRGB(),"Swing paints the completed frame");
        var counter=Gamepanel.class.getDeclaredField("paintedFrames");counter.setAccessible(true);
        var painted=(java.util.concurrent.atomic.AtomicInteger)counter.get(panel);
        require(painted.get()==1,"FPS measures presented frames");
        fill(source,Color.red);
        require(paintCentre(panel,destination)==Color.green.getRGB(),"painting retains previous frame while next scene is being drawn");
        require(painted.get()==1,"duplicate repaints do not inflate FPS");
        panel.drawToScreen();require(paintCentre(panel,destination)==Color.red.getRGB(),"next completed frame replaces previous frame");
        AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch start=new CountDownLatch(1);
        Thread producer=new Thread(()->{
            try{start.await();for(int i=0;i<100;i++){fill(source,i%2==0?Color.green:Color.magenta);panel.drawToScreen();Thread.yield();}}
            catch(Throwable t){failure.set(t);}
        });producer.start();start.countDown();
        for(int i=0;i<80;i++){
            final int width=i%2==0?1280:1000,height=i%2==0?720:800;
            SwingUtilities.invokeAndWait(()->{
                panel.setSize(width,height);Graphics2D g=destination.createGraphics();panel.paint(g);g.dispose();
                int left=destination.getRGB(width/2-200,height/2),right=destination.getRGB(width/2+200,height/2);
                require(left==right && (left==Color.red.getRGB() || left==Color.green.getRGB() || left==Color.magenta.getRGB()),
                        "no black or partially composed frame during concurrent painting/resizing");
            });
        }
        producer.join();if(failure.get()!=null)throw new AssertionError(failure.get());
        require(panel.isDoubleBuffered(),"Swing double buffering stays enabled");
        System.out.println("PASS: no live graphics drawing, complete-frame publication, stable previous frame, concurrent repainting and resize stress.");
    }
}
