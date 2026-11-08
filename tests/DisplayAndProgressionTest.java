import art.*;
import entity.*;
import main.*;
import monster.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.util.*;

public class DisplayAndProgressionTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static long bosses(Gamepanel game){return Arrays.stream(game.monster).filter(m->m instanceof MON_SlimeBoss).count();}
    public static void main(String[] args)throws Exception{
        require(!CharacterSprites.pixelMode,"smooth characters by default");
        var game=new AppearanceEquipmentTest.QuietGame();
        require(!game.pixelBuffer,"whole-world pixel buffer off by default");
        require(game.display.selected().equals(game.display.nativeResolution()),"native display size selected by default");
        require(SlimeArt.SAGE.equals(Palette.FOLIAGE),"slimes use the tree foliage colour");
        var remove=Gamepanel.class.getDeclaredMethod("removeDefeatedMonster",int.class);remove.setAccessible(true);
        game.assetSetter.setMonster();require(bosses(game)==0 && Arrays.stream(game.monster).allMatch(Objects::isNull),"town has no enemies");
        for(int kill=1;kill<=15;kill++){
            var slime=new MON_GreenSlime(game);slime.alive=false;game.monster[0]=slime;remove.invoke(game,0);
            require(bosses(game)==0,"outdoor kills never summon a King");
        }
        var nativeSize=new DisplaySettings.Resolution(1920,1080);
        DisplayMode desktop=new DisplayMode(1920,1080,32,60);
        DisplayMode[] modes={desktop,new DisplayMode(1280,720,32,60),new DisplayMode(1920,1080,32,144),
                new DisplayMode(1280,720,16,60),new DisplayMode(2560,1440,32,60)};
        var supported=DisplaySettings.supportedResolutions(modes,nativeSize);
        require(supported.size()==3 && supported.contains(nativeSize),"device list deduplicates refresh/depth variants");
        require(DisplaySettings.chooseMode(modes,nativeSize,desktop).getRefreshRate()==144,"supported matching mode selected");
        require(DisplaySettings.chooseMode(modes,new DisplaySettings.Resolution(800,600),desktop)==desktop,"unsupported mode falls back safely");
        var fit=DisplaySettings.fit(1248,720,1920,1080);
        require(Math.abs(fit.width()/(double)fit.height()-1248/720.0)<.002,"letterbox preserves game aspect ratio");
        game.setSize(1920,1080);
        var viewport=game.display.gameViewport(1920,1080);
        game.mouseH.mouseX=viewport.x()+viewport.width()/2;game.mouseH.mouseY=viewport.y()+viewport.height()/2;
        require(Math.abs(game.mouseH.getScaledX()-game.screenWidth/2)<=1 && Math.abs(game.mouseH.getScaledY()-game.screenHeight/2)<=1,"mouse maps to canvas centre");
        game.mouseH.mouseX=0;game.mouseH.mouseY=0;
        require(game.mouseH.getScaledX()==-1 && game.mouseH.getScaledY()==-1,"letterbox bars excluded from input");
        BufferedImage canvas=new BufferedImage(game.screenWidth,game.screenHeight,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=canvas.createGraphics();graphics.setColor(Color.green);graphics.fillRect(0,0,canvas.getWidth(),canvas.getHeight());graphics.dispose();
        BufferedImage output=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);graphics=output.createGraphics();
        game.display.draw(graphics,canvas,1920,1080);graphics.dispose();
        require(output.getRGB(0,0)==Color.black.getRGB() && output.getRGB(960,540)==Color.green.getRGB(),"presentation rendering matches mouse viewport");
        Path configFile=Files.createTempFile("astral-display-",".txt");
        var config=new Config(game,configFile);
        Files.writeString(configFile,"Off\n0\n0\n");config.loadConfig();
        require(game.display.selected().equals(game.display.nativeResolution()),"legacy config adopts native resolution");
        Files.writeString(configFile,"On\n0\n0\n9999\n9999\n");config.loadConfig();
        require(game.display.selected().equals(game.display.nativeResolution()),"unsupported saved resolution falls back to device default");
        game.display.apply(false,game.display.nativeResolution());config.saveConfig();
        require(Files.readAllLines(configFile).size()==5,"display preference persisted with existing sound settings");
        var configField=Gamepanel.class.getDeclaredField("config");configField.setAccessible(true);configField.set(game,config);
        game.ui.openOptions();game.ui.commandNum=0;game.ui.confirmOptionSelection();
        require(game.ui.subState==1,"Display opens settings instead of restart notice");
        game.keyH.optionState(KeyEvent.VK_D); // windowed -> fullscreen draft
        require(!game.fullScreenOn,"draft does not apply until requested");
        game.ui.commandNum=2;game.ui.confirmOptionSelection();
        require(game.fullScreenOn && game.ui.subState==0,"apply updates display preferences live");
        game.ui.commandNum=0;game.ui.confirmOptionSelection();game.keyH.optionState(KeyEvent.VK_D);
        game.ui.commandNum=3;game.ui.confirmOptionSelection();require(game.fullScreenOn,"Back discards draft");
        game.ui.commandNum=0;game.ui.confirmOptionSelection();game.ui.commandNum=0;game.keyH.optionState(KeyEvent.VK_W);
        require(game.ui.commandNum==3,"display keyboard navigation wraps");
        Arrays.fill(game.monster,null);var king=new MON_SlimeBoss(game);king.worldX=game.localPlayer().worldX+50;king.worldY=game.localPlayer().worldY;king.HP=30;game.monster[0]=king;
        game.localPlayer().HP=5;game.localPlayer().maxHP=10;
        BufferedImage hud=new BufferedImage(game.screenWidth,game.screenHeight,BufferedImage.TYPE_INT_ARGB);graphics=hud.createGraphics();
        var g2=UI.class.getDeclaredField("g2");g2.setAccessible(true);g2.set(game.ui,graphics);
        var playerHp=UI.class.getDeclaredMethod("drawPlayerHP");playerHp.setAccessible(true);playerHp.invoke(game.ui);
        var bossHp=UI.class.getDeclaredMethod("drawBossHealth");bossHp.setAccessible(true);bossHp.invoke(game.ui);graphics.dispose();
        int bossX=(game.screenWidth-620)/2;
        require(hud.getRGB(bossX+8,63)==hud.getRGB(32,32),"boss HP uses player HP fill colour");
        System.out.println("PASS: no overworld slime encounters, shared foliage/HP style, pixel-off default, display mode list, native fallback, config, letterboxing, mouse mapping and settings controls.");
    }
}
