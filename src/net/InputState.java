package net;

import main.KeyHandler;
import main.MouseHandler;

public class InputState {
    public long tick;
    public boolean up, down, left, right;
    public boolean ePressed;
    public boolean escapePressed;
    public boolean leftClicked;
    public boolean leftHeld, rightHeld, gameplaySuppressed, movementLocked, chorusSpeed, heavyMovement;
    public boolean[] skillKeyPressed = new boolean[5];
    public int targetedMonsterIndex = -1;
    public int aimWorldX, aimWorldY;


    public static InputState captureFrom(KeyHandler keyH, MouseHandler mouseH, long tick, int targetedMonsterIndex){
        InputState state = new InputState();
        state.tick = tick;
        state.up = keyH.upPressed;
        state.down = keyH.downPressed;
        state.left = keyH.leftPressed;
        state.right = keyH.rightPressed;
        state.ePressed = keyH.consumeEEdge();
        state.escapePressed = keyH.consumeEscapeEdge();
        state.leftClicked = mouseH.leftClicked;
        state.leftHeld = mouseH.leftPressed;
        state.rightHeld = mouseH.rightPressed;
        state.targetedMonsterIndex = targetedMonsterIndex;
        for(int i = 0; i < state.skillKeyPressed.length; i++){
            state.skillKeyPressed[i] = keyH.skillKeyPressed[i];
        }
        return state;
    }

    public void mergeOneShotFlags(InputState newer){
        // Keep the newest directional/held state, but latch any one-shot press seen in this batch
        up = newer.up;
        down = newer.down;
        left = newer.left;
        right = newer.right;
        aimWorldX = newer.aimWorldX;
        aimWorldY = newer.aimWorldY;
        targetedMonsterIndex = newer.targetedMonsterIndex;
        leftHeld = newer.leftHeld;
        rightHeld = newer.rightHeld;
        gameplaySuppressed = newer.gameplaySuppressed;
        ePressed = ePressed || newer.ePressed;
        escapePressed = escapePressed || newer.escapePressed;
        leftClicked = leftClicked || newer.leftClicked;
        for(int i = 0; i < skillKeyPressed.length; i++){
            skillKeyPressed[i] = skillKeyPressed[i] || newer.skillKeyPressed[i];
        }
        tick = newer.tick;
    }

    public void suppressGameplay(){
        gameplaySuppressed = true;
        leftHeld = false;
        rightHeld = false;
        up = false;
        down = false;
        left = false;
        right = false;
        ePressed = false;
        leftClicked = false;
        for(int i = 0; i < skillKeyPressed.length; i++){
            skillKeyPressed[i] = false;
        }
        targetedMonsterIndex = -1;
    }
}
