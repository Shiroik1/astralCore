import entity.*;
import main.*;
import monster.*;
import net.*;
import object.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

public class BalancePolishTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void design(Player p,String name){p.setCharacterDesign(name,"male");p.setDefaultValues();p.setItems();p.refreshWeaponAnimation();}
    static void save(BufferedImage image,String name)throws Exception{new File("/tmp/astral-balance-polish").mkdirs();ImageIO.write(image,"png",new File("/tmp/astral-balance-polish/"+name+".png"));}
    public static void main(String[] args)throws Exception{
        var g=new AppearanceEquipmentTest.QuietGame();Player p=g.localPlayer();
        var hell=new MON_HellSlime(g);require(hell.maxHP==600 && hell.HP==600,"Hell Slime HP doubled from 300 to 600");
        hell.HP=25;hell.resetEncounter();require(hell.HP==600,"wipe reset restores doubled health");
        require(new MON_SlimeBoss(g).maxHP==50,"normal King health preserved");
        design(p,"warrior");require(p.maxHP==12 && p.HP==12,"Warrior has modest health increase");
        design(p,"hunter");require(p.maxHP==10 && p.resourceName().equals("Focus"),"Hunter keeps health and uses Focus");p.rage=50;
        var image=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        var field=UI.class.getDeclaredField("g2");field.setAccessible(true);field.set(g.ui,graphics);
        var draw=UI.class.getDeclaredMethod("drawPlayerHP");draw.setAccessible(true);draw.invoke(g.ui);
        require(image.getRGB(32,60)==p.resourceBarColor().getRGB() && p.resourceBarColor().getGreen()>150 && p.resourceBarColor().getBlue()<100,"Focus bar renders yellow");
        graphics.dispose();save(image.getSubimage(0,0,270,110),"focus-hud");
        design(p,"warrior");p.worldX=300;p.worldY=300;
        var gallery=new BufferedImage(950,180,BufferedImage.TYPE_INT_ARGB);var art=gallery.createGraphics();art.setColor(Color.BLACK);art.fillRect(0,0,950,180);
        var signatures=new HashSet<Integer>();
        for(int skill=0;skill<5;skill++){
            g.particleList.clear();p.skills[skill].onUse.run();
            var effects=g.particleList.stream().filter(e->e instanceof WarriorSkillEffect).toList();
            require(effects.size()==1 && g.particleList.size()==13,"skill creates one bounded effect plus twelve sparks");
            var effect=(WarriorSkillEffect)effects.getFirst();require(effect.kind==WarriorSkillEffect.Kind.values()[skill],"each Warrior ability has its own effect");
            for(int tick=0;tick<8;tick++)effect.update();
            var frame=new BufferedImage(g.screenWidth,g.screenHeight,BufferedImage.TYPE_INT_ARGB);var fg=frame.createGraphics();effect.draw(fg);fg.dispose();
            signatures.add(AppearanceEquipmentTest.pixels(frame));
            AffineTransform original=art.getTransform();art.translate(skill*190+95-(p.screenX+24),85-(p.screenY+36));effect.draw(art);art.setTransform(original);
            art.setColor(Color.WHITE);art.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));art.drawString(p.skills[skill].name,skill*190+24,158);
            for(Entity particle:g.particleList)for(int tick=0;tick<45;tick++)particle.update();
            require(g.particleList.stream().noneMatch(e->e.alive),"all skill particles expire promptly");
        }
        require(signatures.size()==5,"five distinct visible Warrior skill effects");art.dispose();save(gallery,"warrior-effects");
        // Network effects are cosmetic, and render in the caster's scene even when the viewer is elsewhere.
        var endpoint=new com.esotericsoftware.kryonet.Client();NetworkRegistration.register(endpoint);GameEvent event=new GameEvent();event.playerId=0;event.type=GameEventType.SKILL_EFFECT;event.personalText="Rally";
        var output=new com.esotericsoftware.kryo.io.Output(2048,-1);endpoint.getKryo().writeObject(output,event);GameEvent decoded=endpoint.getKryo().readObject(new com.esotericsoftware.kryo.io.Input(output.toBytes()),GameEvent.class);
        g.particleList.clear();p.HP=1;g.quests.activateArea(true);g.applyGameEventLocally(decoded.playerId,decoded.type,decoded.message,decoded.personalText);
        require(g.particleList.isEmpty() && g.quests.inDungeon(),"town cast does not leak effects into dungeon");g.quests.activateArea(false);
        require(g.particleList.stream().anyMatch(e->e instanceof WarriorSkillEffect) && p.HP==1,"replicated Rally renders in town without healing twice");
        System.out.println("PASS: doubled Hell HP and wipe reset, Warrior health increase, yellow Hunter Focus, five bounded animated Warrior skill effects, network replay and scene isolation.");
    }
}
