package art;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Runtime hair loads exported PNGs. Geometry authoring lives in HairArtGenerator. */
public final class HairArt {
    private HairArt(){}
    public static BufferedImage thumbnail(CharacterOutfit outfit){
        return thumbnail(outfit,105);
    }
    public static BufferedImage thumbnail(CharacterOutfit outfit,int width){
        if(width<1)throw new IllegalArgumentException("Thumbnail width must be positive");
        ArtKit.Surface surface=new ArtKit.Surface(width,(int)Math.ceil(width*64/80.0));Graphics2D d=surface.g;
        d.scale(width/80.0,width/80.0);d.translate(8,10);
        CharacterArt.Look look=new CharacterArt.Look();look.outfit=outfit;
        rear(d,outfit,false,false,0);CharacterClothing.collar(d,outfit,false);
        CharacterArt.drawHead(d,false,false,look);crown(d,outfit,false,false);
        return surface.finish();
    }
    public static void rear(Graphics2D g,CharacterOutfit outfit,boolean side,boolean back,float swing){
        HairTextures.draw(g,outfit,side,back,true,swing);
    }
    public static void crown(Graphics2D g,CharacterOutfit outfit,boolean side,boolean back){
        HairTextures.draw(g,outfit,side,back,false,0);
    }
}
