package main;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Device-supported presentation sizes; the fixed game canvas and hitboxes never change. */
public final class DisplaySettings {
    public record Resolution(int width,int height) {
        public Resolution { if(width<=0 || height<=0)throw new IllegalArgumentException("Invalid resolution"); }
        @Override public String toString(){ return width+" x "+height; }
    }
    public record Viewport(int x,int y,int width,int height) {
        public boolean contains(int px,int py){return px>=x && py>=y && px<x+width && py<y+height;}
    }
    private final Gamepanel panel;
    private GraphicsDevice device;
    private DisplayMode desktopMode;
    private volatile Resolution nativeResolution;
    private volatile Resolution selected;
    private volatile List<Resolution> resolutions;
    private static final int MAX_RENDER_FPS = 120;
    private volatile int renderFps = MAX_RENDER_FPS;
    private final AtomicInteger revision=new AtomicInteger();
    private BufferedImage presentation;
    private final GpuFramePresenter presenter = new GpuFramePresenter();
    private final GpuFramePresenter interfacePresenter = new GpuFramePresenter();

    public DisplaySettings(Gamepanel panel){
        this.panel=panel;
        if(GraphicsEnvironment.isHeadless()){
            nativeResolution=new Resolution(1280,720);resolutions=List.of(nativeResolution);
        }else{
            device=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            readDevice();
        }
        selected=nativeResolution;
    }
    private void readDevice(){
        desktopMode=device.getDisplayMode();
        nativeResolution=new Resolution(desktopMode.getWidth(),desktopMode.getHeight());
        resolutions=supportedResolutions(device.getDisplayModes(),nativeResolution);
        updateRenderRate(desktopMode);
    }
    private void updateRenderRate(DisplayMode mode){
        int refreshRate = mode.getRefreshRate();
        renderFps = refreshRate > 0 ? Math.min(MAX_RENDER_FPS, refreshRate) : MAX_RENDER_FPS;
    }
    int renderFps(){ return renderFps; }
    public static List<Resolution> supportedResolutions(DisplayMode[] modes,Resolution nativeResolution){
        TreeSet<Resolution> sorted=new TreeSet<>(Comparator.comparingLong((Resolution r)->(long)r.width*r.height)
                .thenComparingInt(Resolution::width).thenComparingInt(Resolution::height));
        sorted.add(nativeResolution);
        for(DisplayMode mode:modes)if(mode.getWidth()>0 && mode.getHeight()>0)
            sorted.add(new Resolution(mode.getWidth(),mode.getHeight()));
        return List.copyOf(sorted);
    }
    public List<Resolution> resolutions(){return resolutions;}
    public Resolution selected(){return selected;}
    public Resolution nativeResolution(){return nativeResolution;}
    public Resolution cycle(Resolution start,int delta){
        List<Resolution> list=resolutions;
        int index=list.indexOf(start);return list.get(Math.floorMod((index<0?0:index)+delta,list.size()));
    }
    public void selectSaved(int width,int height){
        Resolution saved=width>0 && height>0?new Resolution(width,height):nativeResolution;
        selected=resolutions.contains(saved)?saved:nativeResolution;
    }
    public void apply(boolean fullscreen,Resolution resolution){
        selected=resolutions.contains(resolution)?resolution:nativeResolution;
        panel.fullScreenOn=fullscreen;
        if(GraphicsEnvironment.isHeadless() || Main.window==null)return;
        int request=revision.incrementAndGet();
        Runnable apply=()->{if(revision.get()==request)applyWindow();};
        if(SwingUtilities.isEventDispatchThread())apply.run();else SwingUtilities.invokeLater(apply);
    }
    private void applyWindow(){
        JFrame window=Main.window;
        GraphicsDevice current=window.getGraphicsConfiguration().getDevice();
        restoreDesktop();
        if(current!=device){device=current;readDevice();if(!resolutions.contains(selected))selected=nativeResolution;}
        window.dispose();window.setUndecorated(panel.fullScreenOn);
        Rectangle bounds=device.getDefaultConfiguration().getBounds();
        if(panel.fullScreenOn){
            window.setBounds(bounds);window.setVisible(true);
            if(device.isFullScreenSupported()){
                device.setFullScreenWindow(window);
                if(device.isDisplayChangeSupported()){
                    DisplayMode target=chooseMode(device.getDisplayModes(),selected,desktopMode);
                    try{device.setDisplayMode(target);}catch(IllegalArgumentException | UnsupportedOperationException ignored){
                        // Borderless presentation still supports the chosen rendering resolution.
                    }
                }
            }
        }else{
            Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(device.getDefaultConfiguration());
            int availableWidth=Math.max(1,bounds.width-insets.left-insets.right-24);
            int availableHeight=Math.max(1,bounds.height-insets.top-insets.bottom-64);
            Viewport fit=fit(selected.width,selected.height,availableWidth,availableHeight);
            panel.setPreferredSize(new Dimension(fit.width,fit.height));window.pack();
            window.setLocation(bounds.x+(bounds.width-window.getWidth())/2,bounds.y+(bounds.height-window.getHeight())/2);
            window.setVisible(true);
        }
        updateRenderRate(device.getDisplayMode());
        window.validate();panel.requestFocusInWindow();
    }
    public static DisplayMode chooseMode(DisplayMode[] modes,Resolution resolution,DisplayMode desktop){
        return Arrays.stream(modes).filter(m->m.getWidth()==resolution.width && m.getHeight()==resolution.height)
                .max(Comparator.comparingInt((DisplayMode m)->m.getBitDepth()==desktop.getBitDepth()?1:0)
                        .thenComparingInt(DisplayMode::getRefreshRate)).orElse(desktop);
    }
    public void restoreDesktop(){
        if(device==null)return;
        if(device.getFullScreenWindow()!=null){
            try{if(device.isDisplayChangeSupported())device.setDisplayMode(desktopMode);}
            catch(IllegalArgumentException | UnsupportedOperationException ignored){}
            device.setFullScreenWindow(null);
        }
    }
    public static Viewport fit(int sourceWidth,int sourceHeight,int width,int height){
        width=Math.max(1,width);height=Math.max(1,height);
        double scale=Math.min(width/(double)sourceWidth,height/(double)sourceHeight);
        int w=Math.max(1,(int)Math.round(sourceWidth*scale)),h=Math.max(1,(int)Math.round(sourceHeight*scale));
        return new Viewport((width-w)/2,(height-h)/2,w,h);
    }
    public Viewport gameViewport(int width,int height){
        Resolution r=selected;
        Viewport outer=fit(r.width,r.height,width,height),inner=fit(panel.screenWidth,panel.screenHeight,r.width,r.height);
        return new Viewport(outer.x+(int)Math.round(inner.x*outer.width/(double)r.width),
                outer.y+(int)Math.round(inner.y*outer.height/(double)r.height),
                Math.max(1,(int)Math.round(inner.width*outer.width/(double)r.width)),
                Math.max(1,(int)Math.round(inner.height*outer.height/(double)r.height)));
    }
    /** Upscaling happens once during window painting, never in CPU frame preparation. */
    public Resolution frameResolution(){
        Resolution r=selected;
        Viewport content=fit(panel.screenWidth,panel.screenHeight,r.width,r.height);
        return content.width>=panel.screenWidth?new Resolution(panel.screenWidth,panel.screenHeight)
                :new Resolution(content.width,content.height);
    }
    Resolution interfaceResolution(){
        Viewport view=gameViewport(panel.getWidth(),panel.getHeight());
        GraphicsConfiguration configuration=panel.getGraphicsConfiguration();
        var transform=configuration==null?null:configuration.getDefaultTransform();
        double sx=transform==null?1:transform.getScaleX(),sy=transform==null?1:transform.getScaleY();
        // Keep the world staging buffer fixed. The separate UI layer can use up to twice its pixel density.
        double scale=Math.min(2,Math.min(view.width*sx/panel.screenWidth,view.height*sy/panel.screenHeight));
        if(panel.getWidth()<=0 || panel.getHeight()<=0)scale=1;
        return new Resolution(Math.max(1,(int)Math.round(panel.screenWidth*scale)),
                Math.max(1,(int)Math.round(panel.screenHeight*scale)));
    }

    public BufferedImage renderFrame(BufferedImage canvas){
        Resolution r=frameResolution();
        if(presentation==null || presentation.getWidth()!=r.width || presentation.getHeight()!=r.height)
            presentation=new BufferedImage(r.width,r.height,BufferedImage.TYPE_INT_RGB);
        Graphics2D buffer=presentation.createGraphics();
        buffer.setComposite(AlphaComposite.Src);
        buffer.setRenderingHint(RenderingHints.KEY_INTERPOLATION,interpolation());
        buffer.drawImage(canvas,0,0,r.width,r.height,null);buffer.dispose();
        return presentation;
    }
    /** The opaque frame covers the content; only letterbox margins need clearing. */
    public void paintFrame(Graphics2D target,BufferedImage frame,int width,int height){
        paintFrame(target,frame,width,height,-1);
    }
    public void paintFrame(Graphics2D target,BufferedImage frame,int width,int height,long sequence){
        paintFrame(target,frame,null,width,height,sequence);
    }

    void paintFrame(Graphics2D target,BufferedImage frame,BufferedImage overlay,int width,int height,long sequence){
        Viewport view=gameViewport(width,height);
        target.setColor(Color.black);
        target.fillRect(0,0,width,view.y);
        target.fillRect(0,view.y+view.height,width,height-view.y-view.height);
        target.fillRect(0,view.y,view.x,view.height);
        target.fillRect(view.x+view.width,view.y,width-view.x-view.width,view.height);
        target.setRenderingHint(RenderingHints.KEY_INTERPOLATION,interpolation());
        presenter.draw(target,frame,sequence,view.x,view.y,view.width,view.height);
        if(overlay!=null){
            target.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            interfacePresenter.draw(target,overlay,sequence,view.x,view.y,view.width,view.height);
        }
    }
    private Object interpolation(){
        return entity.CharacterSprites.pixelMode?RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                :RenderingHints.VALUE_INTERPOLATION_BILINEAR;
    }
    public void draw(Graphics2D target,BufferedImage canvas,int width,int height){
        paintFrame(target,renderFrame(canvas),width,height);
    }
}
