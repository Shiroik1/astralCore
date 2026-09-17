package main;

import entity.Player;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyHandler implements KeyListener {
    Gamepanel gp;
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean[] skillKeyPressed = new boolean[5];
    public boolean ePressed;
    private boolean ePressedPrevCapture = false;
    public boolean showDebug;
    public boolean shotKeyPressed;
    public boolean enterPressed;
    public boolean escapePressed;
    private boolean escapePressedPrevCapture = false;


    public boolean enteringNetworkAddress = false;
    public StringBuilder networkAddressInput = new StringBuilder();

    public KeyHandler(Gamepanel gp){
        this.gp = gp;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if(!enteringNetworkAddress) return;

        char c = e.getKeyChar();
        if(c >= 32 && c < 127 && networkAddressInput.length() < 64){
            networkAddressInput.append(c);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        if(enteringNetworkAddress){
            handleNetworkAddressKeyPress(code);
            return;
        }

        //TITLE STATE
        if(gp.gameState == gp.titleState){
            titleState(code);
        }
        //PLAY STATE
        else if(gp.gameState == gp.playState){
            playState(code);
        }
        //PAUSE STATE
        else if(gp.gameState == gp.pauseState){
            pauseState(code);
        }
        //DIALOGUE STATE
        else if(gp.gameState == gp.dialogueState){
            dialogueState(code);
        }
        //OPTION STATE
        else if(gp.gameState == gp.optionState){
            optionState(code);
        }
        //GAMEOVER STATE
        else if(gp.gameState == gp.gameOverState){
            gameOverState(code);
        }
    }

    public void gameOverState(int code) {
        if(code == KeyEvent.VK_W){
            gp.ui.commandNum--;
            if(gp.ui.commandNum < 0){
                gp.ui.commandNum = 1;
            }
            gp.playSE(8);
        }
        if(code == KeyEvent.VK_S){
            gp.ui.commandNum++;
            if(gp.ui.commandNum > 1){
                gp.ui.commandNum = 0;
            }
            gp.playSE(8);
        }
        if(code == KeyEvent.VK_ENTER){
            if(gp.ui.commandNum == 0){
                gp.gameState = gp.playState;
                gp.playMusic(0);
                gp.retry();
            }
            else if(gp.ui.commandNum == 1){
                gp.gameState = gp.titleState;
                gp.restart();
            }
        }
    }

    public void titleState(int code){
        if(code == KeyEvent.VK_W){
            gp.ui.commandNum--;
            if(gp.ui.commandNum < 0){
                gp.ui.commandNum = 3;
            }
        }
        if(code == KeyEvent.VK_S){
            gp.ui.commandNum++;
            if(gp.ui.commandNum > 3){
                gp.ui.commandNum = 0;
            }
        }
        if(code == KeyEvent.VK_ENTER){
            if(gp.ui.commandNum == 0){
                gp.gameState = gp.playState;
                gp.playMusic(0);
            }
            if(gp.ui.commandNum == 1){
                gp.ui.startHostFlow();
            }
            if(gp.ui.commandNum == 2){
                gp.ui.startJoinFlow();
            }
            if(gp.ui.commandNum == 3){
                System.exit(0);
            }
        }
    }

    public void playState(int code){
        if(code == KeyEvent.VK_T){
            showDebug = !showDebug;
        }
        if(code == KeyEvent.VK_W){
            upPressed = true;
        }
        if(code == KeyEvent.VK_S){
            downPressed = true;
        }
        if(code == KeyEvent.VK_A){
            leftPressed = true;
        }
        if(code == KeyEvent.VK_D){
            rightPressed = true;
        }
        if(code == KeyEvent.VK_ENTER){
            gp.gameState = gp.pauseState;
        }
        if(code == KeyEvent.VK_C){
            gp.inventoryOpen = !gp.inventoryOpen;
        }
        if(code == KeyEvent.VK_E){
            ePressed = true;
        }
        if(code == KeyEvent.VK_F){
            shotKeyPressed = true;
        }
        if(code == KeyEvent.VK_ESCAPE){
            escapePressed = true;
            Player local = gp.localPlayer();
            boolean inDialogueNow = (local != null && local.inDialogue);
            if(!inDialogueNow){
                gp.gameState = gp.optionState;
            }
        }

        //SKILL KEYS
        if(code == KeyEvent.VK_1){ skillKeyPressed[0] = true; }
        if(code == KeyEvent.VK_2){ skillKeyPressed[1] = true; }
        if(code == KeyEvent.VK_3){ skillKeyPressed[2] = true; }
        if(code == KeyEvent.VK_4){ skillKeyPressed[3] = true; }
        if(code == KeyEvent.VK_5){ skillKeyPressed[4] = true; }

    }

    public void pauseState(int code){
        if(code == KeyEvent.VK_ENTER){
            gp.gameState = gp.playState;
        }
    }

    public void optionState(int code){
        if(code == KeyEvent.VK_ESCAPE){
            gp.gameState = gp.playState;
        }
        if(code == KeyEvent.VK_ENTER){
            enterPressed = true;
        }

        int maxCommandNum = 0;
        switch (gp.ui.subState){
            case 0 -> maxCommandNum = 5;
            case 3 -> maxCommandNum = 1;
        }

        if(code == KeyEvent.VK_W){
            gp.ui.commandNum--;
            gp.playSE(8);
            if(gp.ui.commandNum < 0){
                gp.ui.commandNum = maxCommandNum;
            }
        }
        if(code == KeyEvent.VK_S){
            gp.ui.commandNum++;
            gp.playSE(8);
            if(gp.ui.commandNum > maxCommandNum){
                gp.ui.commandNum = 0;
            }
        }
        if(code == KeyEvent.VK_A){
            if(gp.ui.subState == 0){
                if(gp.ui.commandNum == 1 && gp.music.volumeScale > 0){
                    gp.music.volumeScale--;
                    gp.music.checkVolume();
                    gp.playSE(8);
                }
                if(gp.ui.commandNum == 2 && gp.soundEffect.volumeScale > 0){
                    gp.soundEffect.volumeScale--;
                    gp.playSE(8);
                }
            }
        }
        if(code == KeyEvent.VK_D){
            if(gp.ui.subState == 0){
                if(gp.ui.commandNum == 1 && gp.music.volumeScale < 5){
                    gp.music.volumeScale++;
                    gp.music.checkVolume();
                    gp.playSE(8);
                }
                if(gp.ui.commandNum == 2 && gp.soundEffect.volumeScale < 5){
                    gp.soundEffect.volumeScale++;
                    gp.playSE(8);
                }
            }
        }
    }

    public void dialogueState(int code){
        if(code == KeyEvent.VK_SPACE || code == KeyEvent.VK_E){
            gp.gameState = gp.playState;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();

        if(code == KeyEvent.VK_W){
            upPressed = false;
        }
        if(code == KeyEvent.VK_S){
            downPressed = false;
        }
        if(code == KeyEvent.VK_A){
            leftPressed = false;
        }
        if(code == KeyEvent.VK_D){
            rightPressed = false;
        }
        if(code == KeyEvent.VK_F){
            shotKeyPressed = false;
        }
        if(code == KeyEvent.VK_E){
            ePressed = false;
        }
        if(code == KeyEvent.VK_ESCAPE){
            escapePressed = false;
        }
    }

    private void handleNetworkAddressKeyPress(int code){
        if(code == KeyEvent.VK_ENTER){
            String address = networkAddressInput.toString().trim();
            System.out.println("[UI] Enter pressed in address box, raw text='" + address + "'");
            enteringNetworkAddress = false;
            networkAddressInput.setLength(0);
            gp.ui.submitJoinAddress(address);
        }
        else if(code == KeyEvent.VK_ESCAPE){
            enteringNetworkAddress = false;
            networkAddressInput.setLength(0);
            gp.ui.cancelJoinAddress();
        }
        else if(code == KeyEvent.VK_BACK_SPACE){
            if(networkAddressInput.length() > 0){
                networkAddressInput.deleteCharAt(networkAddressInput.length() - 1);
            }
        }
    }

    public void beginEnteringNetworkAddress(){
        System.out.println("[UI] beginEnteringNetworkAddress() called, opening text input");
        enteringNetworkAddress = true;
        networkAddressInput.setLength(0);
    }

    public boolean consumeEEdge(){
        boolean edge = ePressed && !ePressedPrevCapture;
        ePressedPrevCapture = ePressed;
        return edge;
    }

    public boolean consumeEscapeEdge(){
        boolean edge = escapePressed && !escapePressedPrevCapture;
        escapePressedPrevCapture = escapePressed;
        return edge;
    }


}
