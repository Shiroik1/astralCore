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
    public boolean enteringChat = false;
    public StringBuilder chatInput = new StringBuilder();

    public int characterCreationStep = 0; // 0 = naming, 1 = class/preset, 2 = hair customization
    public int selectedClassIndex = 2; // Hunter, Mage, Warrior (default)
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
        if(enteringChat){
            if(c >= 32 && c < 127 && chatInput.length() < 120){
                chatInput.append(c);
            }
            return;
        }
        if(c == '/' && canOpenChat()){
            beginChat();
            chatInput.append('/');
            return;
        }
        if(enteringNetworkAddress && c >= 32 && c < 127 && networkAddressInput.length() < 64){
            networkAddressInput.append(c);
        }
        if(enteringCharacterName && c >= 32 && c < 127 && characterNameInput.length() < 16){
            characterNameInput.append(c);
        }
    }

    void handleKeyPressed(int code){
        if(gp.gameState==gp.characterSelectState){gp.ui.characterSlots.key(code);return;}
        if(enteringChat){
            handleChatKeyPress(code);
            return;
        }
        if(enteringCharacterName){
            handleCharacterNameKeyPress(code);
            return;
        }

        if(gp.gameState==gp.characterState && characterCreationStep==2){gp.ui.customization.key(code);return;}

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
        if(code==KeyEvent.VK_F5){
            if(gp.saves.saveActive())gp.ui.addMessage("Character saved.",new java.awt.Color(150,220,170));
            return;
        }
        if(gp.localPlayer()!=null && gp.localPlayer().merchantOpen){
            if(code==KeyEvent.VK_ESCAPE)gp.merchant.close();
            return;
        }
        if(gp.grassEditor.active && gp.grassEditor.handleKey(code)) return; // brush keys are not skill keys
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
            beginChat();
        }
        if(code == KeyEvent.VK_P){
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
        if(code == KeyEvent.VK_ENTER || code == KeyEvent.VK_P){
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
            case 1 -> maxCommandNum = 3;
            case 3 -> maxCommandNum = 1;
        }

        if(code == KeyEvent.VK_W || code == KeyEvent.VK_UP){
            gp.ui.commandNum--;
            gp.playSE(8);
            if(gp.ui.commandNum < 0){
                gp.ui.commandNum = maxCommandNum;
            }
        }
        if(code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN){
            gp.ui.commandNum++;
            gp.playSE(8);
            if(gp.ui.commandNum > maxCommandNum){
                gp.ui.commandNum = 0;
            }
        }
        if(code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT){
            gp.ui.adjustDisplaySelection(-1);
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
        if(code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT){
            gp.ui.adjustDisplaySelection(1);
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
        if(code==KeyEvent.VK_ESCAPE){enteringCharacterName=false;gp.ui.characterSlots.open();return;}
        if(code == KeyEvent.VK_ENTER){
            gp.ui.confirmCharacterName();
        }
        else if(code == KeyEvent.VK_BACK_SPACE){
            if(characterNameInput.length() > 0){
                characterNameInput.deleteCharAt(characterNameInput.length() - 1);
            }
        }
    }

    private void characterCreationStep(int code){
        if(code==KeyEvent.VK_ESCAPE){gp.ui.characterSlots.open();return;}
        if(code == KeyEvent.VK_A){
            selectedClassIndex = Math.floorMod(selectedClassIndex - 1, entity.CharacterClass.values().length);
        }
        if(code == KeyEvent.VK_D){
            selectedClassIndex = (selectedClassIndex + 1) % entity.CharacterClass.values().length;
        }
        if(code == KeyEvent.VK_W || code == KeyEvent.VK_S){
            selectedGenderIndex = 1 - selectedGenderIndex;
        }
        if(code == KeyEvent.VK_ENTER){
            gp.ui.customization.open();
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

    private boolean canOpenChat(){
        return gp.gameState == gp.playState && !gp.optionsOpen && !gp.paused
                && !enteringNetworkAddress && !enteringCharacterName && (gp.localPlayer()==null || !gp.localPlayer().merchantOpen);
    }

    public void beginChat(){
        if(!canOpenChat()) return;
        enteringChat = true;
        chatInput.setLength(0);
        upPressed = false;
        downPressed = false;
        leftPressed = false;
        rightPressed = false;
    }

    private void handleChatKeyPress(int code){
        if(code == KeyEvent.VK_ENTER){
            String text = chatInput.toString().trim();
            enteringChat = false;
            chatInput.setLength(0);
            if(!text.isEmpty()){
                gp.submitChat(text);
            }
        }
        else if(code == KeyEvent.VK_ESCAPE){
            enteringChat = false;
            chatInput.setLength(0);
        }
        else if(code == KeyEvent.VK_BACK_SPACE){
            if(chatInput.length() > 0){
                chatInput.deleteCharAt(chatInput.length() - 1);
            }
        }
    }
}
