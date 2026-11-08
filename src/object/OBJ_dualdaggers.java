package object;
import entity.Entity;
import main.Gamepanel;
import art.WeaponArt;
import java.awt.image.BufferedImage;
public final class OBJ_dualdaggers extends Entity {
    private static BufferedImage icon;
    public OBJ_dualdaggers(Gamepanel gp){
        super(gp);name="Twin Daggers";type=type_sword;attackValue=1;attackArea.width=28;attackArea.height=28;
        description="(Twin Daggers)\nFast, short-range paired blades.\nBuild combos and focus on hits.";
        synchronized(OBJ_dualdaggers.class){if(icon==null){icon=new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);var g=icon.createGraphics();
            WeaponArt.draw(g,WeaponArt.Kind.DAGGERS,17,32,-20,1,0,0,0,1.3f);WeaponArt.draw(g,WeaponArt.Kind.DAGGERS,31,32,20,1,0,0,0,1.3f);g.dispose();}down1=icon;}
    }
}
