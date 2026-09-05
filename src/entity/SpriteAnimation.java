package entity;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

public class SpriteAnimation {
    public BufferedImage[] frames;
    public BufferedImage[] mirroredFrames;
    public int width;
    public int height;

    public SpriteAnimation(BufferedImage[] frames, int width, int height){
        this(frames, width, height, false);
    }

    public SpriteAnimation(BufferedImage[] frames, int width, int height, boolean generateMirror){
        this.frames = frames;
        this.width = width;
        this.height = height;
        if(generateMirror){
            mirroredFrames = new BufferedImage[frames.length];
            for(int i = 0; i < frames.length; i++){
                mirroredFrames[i] = flipHorizontal(frames[i]);
            }
        }
    }

    public BufferedImage getFrame(int index, boolean flipped){
        BufferedImage[] set = (flipped && mirroredFrames != null) ? mirroredFrames : frames;
        index = Math.min(index, set.length - 1);
        return set[index];
    }

    public static BufferedImage flipHorizontal(BufferedImage src){
        BufferedImage flipped = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = flipped.createGraphics();
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-src.getWidth(), 0);
        g2.drawImage(src,tx,null);
        g2.dispose();
        return flipped;
    }

    public static BufferedImage darken(BufferedImage src, float brightnessFactor){
        BufferedImage darkened = new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB);
        RescaleOp op = new RescaleOp(
                new float[]{brightnessFactor, brightnessFactor,brightnessFactor, 1f},
                new float[]{0f,0f,0f,0f},
                null
        );
        op.filter(src,darkened);
        return darkened;
    }
}
