package entity;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class Animator {
    private Map<String, SpriteAnimation> animations = new HashMap<>();
    private String state = "idle";
    private int frameIndex = 0;
    private int frameCounter = 0;
    private int frameDelay = 12;

    public void addAnimation(String state, SpriteAnimation anim){
        animations.put(state,anim);
    }

    public boolean hasAnimation(String state){
        return animations.containsKey(state);
    }

    public void setState(String newState){
        if(!newState.equals(state) && animations.containsKey(newState)){
            state = newState;
            frameIndex = 0;
            frameCounter = 0;
        }
    }

    public String getState(){
        return state;
    }

    public void setFrame(int index){
        frameIndex = index;
    }

    public void setFrameDelay(int delay){
        frameDelay = delay;
    }

    public void update(){
        SpriteAnimation anim = animations.get(state);
        if(anim == null) return;

        frameCounter++;
        if(frameCounter > frameDelay){
            frameIndex++;
            if(frameIndex >= anim.frames.length){
                frameIndex = 0;
            }
            frameCounter = 0;
        }
    }

    public BufferedImage getCurrentFrame(boolean flipped){
        SpriteAnimation anim = animations.get(state);
        if(anim == null) return null;
        return anim.getFrame(frameIndex, flipped);
    }
}
