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
    public boolean escapePressed;
    private boolean escapePressedPrevCapture = false;

    public boolean enteringNetworkAddress = false;
    public StringBuilder networkAddressInput = new StringBuilder();
    public boolean enteringCharacterName = false;
    public StringBuilder characterNameInput = new StringBuilder();

    public int characterCreationStep = 0; // 0 = naming, 1 = class/gender select
    public int selectedClassIndex = 0; // 0 = swordsman, 1 = mage
    public int selectedGenderIndex = 0; // 0 = male, 1 = female

    public KeyHandler(Gamepanel gp){
        this.gp = gp;
    }

    //EDT: RECORD ONLY — nothing here touches game state
    @Override
    public void keyTyped(KeyEvent e) {
        gp.inputQueue.add(RawInput.keyTyped(e.getKeyChar()));
    }

    @Override
    public void keyPressed(KeyEvent e) {
        gp.inputQueue.add(RawInput.keyPressed(e.getKeyCode()));
    }

    @Override
    public void keyReleased(KeyEvent e) {
        gp.inputQueue.add(RawInput.keyReleased(e.getKeyCode()));
    }

    //GAME THREAD: INTERPRET — called from Gamepanel.processInput(), once per queued event
    void handleKeyTyped(char c){
        if(enteringNetworkAddress && c >= 32 && c < 127 && networkAddressInput.length() < 64){
            networkAddressInput.append(c);
        }
        if(enteringCharacterName && c >= 32 && c < 127 && characterNameInput.length() < 16){
            characterNameInput.append(c);
        }
    }

    void handleKeyPressed(int code){
        if(enteringCharacterName){
            handleCharacterNameKeyPress(code);
            return;
        }

        if(gp.gameState == gp.characterState && characterCreationStep == 1){
            characterCreationStep(code);
            return;
        }

        if(enteringNetworkAddress){
            handleNetworkAddressKeyPress(code);
            return;
        }

        if(gp.gameState == gp.titleState){
            titleState(code);
        }
        else if(gp.gameState == gp.playState){
            if(gp.optionsOpen){
                optionState(code);
            } else if(gp.paused){
                pauseState(code);
            } else {
                playState(code);
            }
        }
    }

    void handleKeyReleased(int code){
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
        if(code == KeyEvent.VK_E){
            ePressed = false;
        }
        if(code == KeyEvent.VK_ESCAPE){
            escapePressed = false;
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
            gp.ui.confirmTitleSelection(gp.ui.commandNum);
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
            if(!gp.isNetworked){
                gp.paused = true;
            }
        }
        if(code == KeyEvent.VK_C){
            gp.inventoryOpen = !gp.inventoryOpen;
        }
        if(code == KeyEvent.VK_E){
            ePressed = true;
        }
        if(code == KeyEvent.VK_ESCAPE){
            escapePressed = true;
            Player local = gp.localPlayer();
            boolean inDialogueNow = (local != null && local.inDialogue);
            if(!inDialogueNow){
                gp.ui.openOptions();
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
            gp.paused = false;
        }
    }

    public void optionState(int code){
        if(code == KeyEvent.VK_ESCAPE){
            gp.ui.closeOptions();
            return;
        }
        if(code == KeyEvent.VK_ENTER){
            gp.ui.confirmOptionSelection();
            return;
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
                    gp.config.saveConfigSafe();
                }
                if(gp.ui.commandNum == 2 && gp.soundEffect.volumeScale > 0){
                    gp.soundEffect.volumeScale--;
                    gp.playSE(8);
                    gp.config.saveConfigSafe();
                }
            }
        }
        if(code == KeyEvent.VK_D){
            if(gp.ui.subState == 0){
                if(gp.ui.commandNum == 1 && gp.music.volumeScale < 5){
                    gp.music.volumeScale++;
                    gp.music.checkVolume();
                    gp.playSE(8);
                    gp.config.saveConfigSafe();
                }
                if(gp.ui.commandNum == 2 && gp.soundEffect.volumeScale < 5){
                    gp.soundEffect.volumeScale++;
                    gp.playSE(8);
                    gp.config.saveConfigSafe();
                }
            }
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

    private void handleCharacterNameKeyPress(int code){
        if(code == KeyEvent.VK_ENTER){
            String name = characterNameInput.toString().trim();
            if(name.isEmpty()) name = "Adventurer";
            gp.ui.pendingCharacterName = name;
            enteringCharacterName = false;
            characterCreationStep = 1;
        }
        else if(code == KeyEvent.VK_BACK_SPACE){
            if(characterNameInput.length() > 0){
                characterNameInput.deleteCharAt(characterNameInput.length() - 1);
            }
        }
    }

    private void characterCreationStep(int code){
        if(code == KeyEvent.VK_A){
            selectedClassIndex = 1 - selectedClassIndex;
        }
        if(code == KeyEvent.VK_D){
            selectedClassIndex = 1 - selectedClassIndex;
        }
        if(code == KeyEvent.VK_W || code == KeyEvent.VK_S){
            selectedGenderIndex = 1 - selectedGenderIndex;
        }
        if(code == KeyEvent.VK_ENTER){
            characterCreationStep = 0;
            gp.ui.confirmCharacterCreation(selectedClassIndex, selectedGenderIndex);
        }
    }

    public void beginEnteringCharacterName(){
        enteringCharacterName = true;
        characterNameInput.setLength(0);
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