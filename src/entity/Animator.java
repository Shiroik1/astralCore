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

    /** Adds the animation for Animator. */
    public void addAnimation(String state, SpriteAnimation anim) {
        animations.put(state, anim);
    }

    /** Reports whether this component has animation for Animator. */
    public boolean hasAnimation(String state) {
        return animations.containsKey(state);
    }

    /** Updates the state for Animator. */
    public void setState(String newState) {
        if (!newState.equals(state) && animations.containsKey(newState)) {
            state = newState;
            frameIndex = 0;
            frameCounter = 0;
        }
    }

    /** Returns the state for Animator. */
    public String getState() {
        return state;
    }

    /** Updates the frame for Animator. */
    public void setFrame(int index) {
        frameIndex = index;
    }

    /** Updates the frame delay for Animator. */
    public void setFrameDelay(int delay) {
        frameDelay = delay;
    }

    /** Advances this component for one simulation tick. */
    public void update() {
        SpriteAnimation anim = animations.get(state);
        if (anim == null) return;

        frameCounter++;
        if (frameCounter > frameDelay) {
            frameIndex++;
            if (frameIndex >= anim.frames.length) {
                frameIndex = 0;
            }
            frameCounter = 0;
        }
    }

    /** Returns the current frame for Animator. */
    public BufferedImage getCurrentFrame(boolean flipped) {
        SpriteAnimation anim = animations.get(state);
        if (anim == null) return null;
        return anim.getFrame(frameIndex, flipped);
    }
}
