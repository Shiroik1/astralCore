package net;

import main.KeyHandler;
import main.MouseHandler;

public class InputState {
    public long tick;
    public boolean up, down, left, right;
    public boolean ePressed;
    public boolean leftClicked;
    public boolean shotKeyPressed;
    public boolean[] skillKeyPressed = new boolean[5];

    public static InputState captureFrom(KeyHandler keyH, MouseHandler mouseH, long tick){
        InputState state = new InputState();
        state.tick = tick;
        state.up = keyH.upPressed;
        state.down = keyH.downPressed;
        state.left = keyH.leftPressed;
        state.right = keyH.rightPressed;
        state.ePressed = keyH.ePressed;
        state.leftClicked = mouseH.leftClicked;
        state.shotKeyPressed = keyH.shotKeyPressed;
        for(int i = 0; i < state.skillKeyPressed.length; i++){
            state.skillKeyPressed[i] = keyH.skillKeyPressed[i];
        }
        return state;
    }
}