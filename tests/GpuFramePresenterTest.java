import main.GpuFramePresenter;
import java.awt.*;
import java.awt.image.*;

/** Optional native cache check: run without headless mode and pass --native. */
public class GpuFramePresenterTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void fill(BufferedImage image,Color color){Graphics2D g=image.createGraphics();g.setColor(color);g.fillRect(0,0,image.getWidth(),image.getHeight());g.dispose();}
    public static void main(String[] args){
        BufferedImage source=new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB);
        BufferedImage output=new BufferedImage(320,240,BufferedImage.TYPE_INT_RGB);
        fill(source,Color.red);fill(output,Color.blue);
        GpuFramePresenter presenter=new GpuFramePresenter();Graphics2D target=output.createGraphics();
        presenter.draw(target,source,1,20,30,200,150);
        require(output.getRGB(100,100)==Color.red.getRGB(),"software fallback draws scaled frame");
        require(output.getRGB(0,0)==Color.blue.getRGB(),"presenter respects viewport bounds");
        fill(source,Color.green);presenter.draw(target,source,2,20,30,200,150);
        require(output.getRGB(100,100)==Color.green.getRGB(),"new frame becomes visible");target.dispose();presenter.dispose();
        if(args.length>0 && args[0].equals("--native")){
            require(!GraphicsEnvironment.isHeadless(),"native test needs a display");
            GraphicsConfiguration config=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            VolatileImage gpu=config.createCompatibleVolatileImage(800,600,Transparency.OPAQUE);gpu.validate(config);
            fill(source,Color.red);target=gpu.createGraphics();presenter.draw(target,source,1,0,0,800,600);target.dispose();
            require(gpu.getSnapshot().getRGB(400,300)==Color.red.getRGB(),"native texture displays frame");
            fill(source,Color.blue);target=gpu.createGraphics();presenter.draw(target,source,2,0,0,800,600);target.dispose();
            require(gpu.getSnapshot().getRGB(400,300)==Color.blue.getRGB(),"native texture refreshes on new sequence");
            fill(source,Color.green);target=gpu.createGraphics();presenter.draw(target,source,2,0,0,800,600);target.dispose();
            require(gpu.getSnapshot().getRGB(400,300)==Color.blue.getRGB(),"duplicate repaint keeps cached complete texture");
            presenter.dispose();target=gpu.createGraphics();presenter.draw(target,source,2,0,0,800,600);target.dispose();
            require(gpu.getSnapshot().getRGB(400,300)==Color.green.getRGB(),"texture recreated after disposal");
            presenter.dispose();gpu.flush();
        }
        System.out.println("PASS: GPU presenter fallback, viewport bounds, frame refresh and resource disposal.");
    }
}
