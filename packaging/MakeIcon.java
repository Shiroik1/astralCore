import art.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
public final class MakeIcon {
    public static void main(String[] args)throws Exception{
        Path directory=Path.of(args[0]);Files.createDirectories(directory);
        var look=new CharacterArt.Look();look.outfit=CharacterOutfit.themed(CharacterOutfit.Theme.ARMOR,false);
        look.weapon=WeaponArt.Kind.SWORD;look.shieldStyle=1;look.shadow=false;
        var master=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);var g=master.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x171D28));g.fillRoundRect(10,10,1004,1004,210,210);
        g.setColor(new Color(0xE5ECF3));g.setStroke(new BasicStroke(12));g.drawRoundRect(30,30,964,964,185,185);
        g.drawImage(CharacterArt.render(CharacterArt.Dir.DOWN,new CharacterArt.Pose(),look,960),32,35,null);g.dispose();
        for(int size:new int[]{16,32,48,64,128,256,512,1024}){
            var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(master,0,0,size,size,null);graphics.dispose();ImageIO.write(image,"png",directory.resolve("icon-"+size+".png").toFile());
        }
    }
}
