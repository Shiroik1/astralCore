package object;

import art.SlimeArt;
import entity.Projectile;
import entity.SpriteAnimation;
import main.Gamepanel;
import java.awt.Color;
import java.awt.image.BufferedImage;

/** Same flight, lifetime and damage as the King's former rocks; jelly artwork only. */
public final class OBJ_slimebolt extends Projectile {
    private static final java.util.Map<String,BufferedImage[]> FRAMES=new java.util.HashMap<>();
    public final boolean hell;
    public OBJ_slimebolt(Gamepanel gp){this(gp,false);}
    public OBJ_slimebolt(Gamepanel gp,boolean hell){
        super(gp);this.hell=hell;name="Slime Droplet";speed=5;maxHP=80;HP=maxHP;attack=2;useCost=1;alive=false;
        for(String dir:new String[]{"down","up","left","right"}){
            BufferedImage[] frames;
            synchronized(FRAMES){
                frames=FRAMES.computeIfAbsent(dir+"|"+gp.tileSize+"|"+hell,key -> {
                    BufferedImage[] images=new BufferedImage[6];
                    for(int i=0;i<images.length;i++)images[i]=SlimeArt.projectileFrame(dir,i,gp.tileSize);
                    if(hell)for(int i=0;i<images.length;i++)images[i]=SlimeArt.hellFrame(images[i]);
                    return images;
                });
            }
            SpriteAnimation animation=new SpriteAnimation(frames,gp.tileSize,gp.tileSize);animation.frameDelay=3;
            sprites.put("idle_"+dir,animation);
        }
        down1=sprites.get("idle_down").frames[0];down2=sprites.get("idle_down").frames[1];
        up1=sprites.get("idle_up").frames[0];up2=sprites.get("idle_up").frames[1];
        left1=sprites.get("idle_left").frames[0];left2=sprites.get("idle_left").frames[1];
        right1=sprites.get("idle_right").frames[0];right2=sprites.get("idle_right").frames[1];
    }
    @Override public Color getParticleColor(){return SlimeArt.SAGE;}
    @Override public int getParticleSize(){return 8;}
    @Override public int getParticleSpeed(){return 1;}
    @Override public int getParticleMaxHP(){return 20;}
}
