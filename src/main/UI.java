package main;

import entity.Entity;
import object.OBJ_crystal;
import object.OBJ_heart;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class UI {
    Gamepanel gp;
    Graphics2D g2;
    Font solomonKey;
    BufferedImage heart_full, heart_half, heart_blank, crystal_full, crystal_blank;
    public boolean messageOn = false;
    ArrayList<String> message = new ArrayList<>();
    ArrayList<Integer> messageCounter = new ArrayList<>();
    public boolean gameFinished = false;
    public String currentDialogue = "";
    public int commandNum = 0;

    public int slotCol = 0;
    public int slotRow = 0;

    public int subState = 0;

    double playTime;
    DecimalFormat decimalFormat = new DecimalFormat("#0.00");

    public UI(Gamepanel gp){
        this.gp = gp;

        try{
            InputStream is = getClass().getResourceAsStream("/font/SolomonsKey.ttf");
            solomonKey = Font.createFont(Font.TRUETYPE_FONT, is);
        }
        catch (FontFormatException e){
            e.printStackTrace();
        }
        catch (Exception e){
            e.printStackTrace();
        }

        //CREATE HUD OBJECTS
        Entity heart = new OBJ_heart(gp);
        heart_full = heart.image;
        heart_half = heart.image1;
        heart_blank = heart.image2;
        Entity crystal = new OBJ_crystal(gp);
        crystal_full = crystal.image;
        crystal_blank = crystal.image2;

    }

    public void addMessage(String text){
        message.add(text);
        messageCounter.add(0);
    }

    public void draw(Graphics2D g2) throws IOException {

        this.g2 = g2;

        g2.setFont(solomonKey);
        g2.setColor(Color.white);

        //TITLE STATE
        if(gp.gameState == gp.titleState){
            drawTitleScreen();
        }

        //PLAY STATE
        if(gp.gameState == gp.playState){
            //PLAYTIME
            playTime += (double) 1/60;

            //Toggle debug menu with [ T ] key
            if(gp.keyH.showDebug){
                drawDebug(g2, playTime);
            }

            drawPlayerHP();
            drawMessage();
        }

        //PAUSE STATE
        if(gp.gameState == gp.pauseState){
            drawPauseScreen();
            drawPlayerHP();
        }

        //DIALOGUE STATE
        if(gp.gameState == gp.dialogueState){
            drawDialogueScreen();
        }

        //CHARACTER STATE
        if(gp.gameState == gp.characterState){
            drawCharacterScreen();
            drawInventory();
        }

        //OPTION STATE
        if(gp.gameState == gp.optionState){
            drawOptionScreen();
        }

        //GAMEOVER STATE
        if(gp.gameState == gp.gameOverState){
            drawGameOverScreen();
        }

    }

    private void drawGameOverScreen() {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        int x;
        int y;
        String text;
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 80));
        text = "GAME OVER";
        g2.setColor(Color.black);
        x = getXforCenteredText(text);
        y = gp.tileSize * 5;
        g2.drawString(text, x, y);

        g2.setColor(Color.white);
        g2.drawString(text, x-4, y-4);

        //RETRY
        g2.setFont(g2.getFont().deriveFont(30f));
        text = "RETRY";
        x = getXforCenteredText(text);
        y = gp.tileSize * 10;
        g2.drawString(text, x, y);
        if(commandNum == 0){
            g2.drawString(">", x-40, y);
        }

        text = "QUIT";
        x = getXforCenteredText(text);
        y += 50;
        g2.drawString(text, x, y);
        if(commandNum == 1){
            g2.drawString(">", x-40, y);
        }

    }

    private void drawOptionScreen() throws IOException {
        g2.setColor(Color.white);
        g2.setFont(g2.getFont().deriveFont(15f));

        int frameX = gp.tileSize * 9;
        int frameY = gp.tileSize;
        int frameWidth = gp.tileSize * 8;
        int frameHeight = gp.tileSize * 10;
        drawSubWindow(frameX, frameY, frameWidth, frameHeight);

        switch (subState){
            case 0 -> option_top(frameX, frameY);
            case 1 -> option_fullScreenNotification(frameX, frameY);
            case 2 -> option_control(frameX, frameY);
            case 3 -> option_endgameConfirm(frameX, frameY);
        }

        gp.keyH.enterPressed = false;
    }

    private void option_endgameConfirm(int frameX, int frameY) {
        int textX = frameX + gp.tileSize;
        int textY = frameY + gp.tileSize*3;

        currentDialogue = "Quit the game and\nreturn to the title\nscreen?";

        for(String line: currentDialogue.split("\n")){
            g2.drawString(line, textX, textY);
            textY += 40;
        }

        //YES
        String text = "Yes";
        textX = getXforCenteredText(text);
        textY += gp.tileSize * 3;
        g2.drawString(text, textX,textY);
        if(commandNum == 0){
            g2.drawString(">", textX-25, textY);
            if(gp.keyH.enterPressed){
                subState = 0;
                gp.gameState = gp.titleState;
            }
        }

        //NO
        text = "No";
        textX = getXforCenteredText(text);
        textY += gp.tileSize;
        g2.drawString(text, textX,textY);
        if(commandNum == 1){
            g2.drawString(">", textX-25, textY);
            if(gp.keyH.enterPressed){
                subState = 0;
                commandNum = 4;
            }
        }
    }

    private void option_top(int frameX, int frameY) throws IOException {
        int textX;
        int textY;
        String text = "OPTIONS";

        //TITLE
        textX = getXforCenteredText(text);
        textY =frameY + gp.tileSize;
        g2.drawString(text, textX, textY);

        //FULLSCREEN ON/OFF
        textX = frameX + gp.tileSize;
        textY += gp.tileSize * 2;
        g2.drawString("Full Screen", textX, textY);
        if(commandNum == 0){
            g2.drawString(">", textX-25, textY);
            if(gp.keyH.enterPressed){
                if(!gp.fullScreenOn){
                    gp.fullScreenOn = true;
                }
                else if(gp.fullScreenOn){
                    gp.fullScreenOn = false;
                }
                subState = 1;
            }
        }

        //MUSIC
        textY += gp.tileSize;
        g2.drawString("Music", textX, textY);
        if(commandNum == 1){
            g2.drawString(">", textX-25, textY);
        }

        //SE
        textY += gp.tileSize;
        g2.drawString("SE", textX, textY);
        if(commandNum == 2){
            g2.drawString(">", textX-25, textY);
        }

        //CONTROL
        textY += gp.tileSize;
        g2.drawString("Control", textX, textY);
        if(commandNum == 3){
            g2.drawString(">", textX-25, textY);
            if(gp.keyH.enterPressed){
                subState = 2;
                commandNum = 0;
            }
        }

        //ENDGAME
        textY += gp.tileSize * 2;
        g2.drawString("END GAME", textX, textY);
        if(commandNum == 4){
            g2.drawString(">", textX-25, textY);
            if(gp.keyH.enterPressed){
                subState = 3;
                commandNum = 0;
            }
        }

        //BACK
        textY += gp.tileSize;
        g2.drawString("BACK", textX, textY);
        if(commandNum == 5){
            g2.drawString(">", textX-25, textY);
            if(gp.keyH.enterPressed){
                gp.gameState = gp.playState;
                commandNum = 0;
            }
        }

        //FULLSCREEN CHECKBOX
        textX = frameX + gp.tileSize*5;
        textY = frameY + gp.tileSize*2 + 24;
        g2.setStroke(new BasicStroke(3));
        g2.drawRect(textX, textY, 20, 20);
        if(gp.fullScreenOn){
            g2.fillRect(textX, textY, 20, 20);
        }

        //MUSIC VOLUMN
        textY += gp.tileSize;
        g2.drawRect(textX, textY, 120, 20);
        int volumeWidth = 24 * gp.music.volumeScale;
        g2.fillRect(textX, textY, volumeWidth, 20);

        //SOUND EFFECT VOLUMN
        textY += gp.tileSize;
        g2.drawRect(textX, textY, 120, 20);
        volumeWidth = 24 * gp.soundEffect.volumeScale;
        g2.fillRect(textX, textY, volumeWidth, 20);

        gp.config.saveConfig();
    }

    private void drawInventory() {
        int frameX = gp.tileSize * 18;
        int frameY = gp.tileSize;
        int frameWidth = gp.tileSize * 6;
        int frameHeight = gp.tileSize * 5;
        drawSubWindow(frameX, frameY, frameWidth, frameHeight);

        //SLOT
        final int slotStartX = frameX + 20;
        final int slotStartY = frameY + 20;
        int slotX = slotStartX;
        int slotY = slotStartY;
        int slotSize = gp.tileSize+3;

        //Draw Player's Items
        for(int i = 0; i < gp.player.inventory.size(); i++){

            //EQUIP CURSOR
            if(gp.player.inventory.get(i) == gp.player.currentWeapon || gp.player.inventory.get(i) == gp.player.currentShield){
                g2.setColor(Color.orange);
                g2.fillRoundRect(slotX, slotY, gp.tileSize, gp.tileSize, 10, 10);
            }

            g2.drawImage(gp.player.inventory.get(i).down1, slotX, slotY, null);
            slotX += slotSize;

            if(i == 4 || i == 9|| i == 14){
                slotX = slotStartX;
                slotY += slotSize;
            }
        }

        //CURSOR
        int cursorX = slotStartX + (slotSize * slotCol);
        int cursorY = slotStartY + (slotSize * slotRow);
        int cursorWidth = gp.tileSize;
        int cursorHeight = gp.tileSize;

        //DESCRIPTION FRAME
        int dFrameX = frameX;
        int dFrameY = frameY + frameHeight;
        int dFrameWidth = frameWidth;
        int dFrameHeight = gp.tileSize * 3;

        //DESCRIPTION TEXT
        int textX = dFrameX + 20;
        int textY = dFrameY + gp.tileSize;
        g2.setFont(g2.getFont().deriveFont(15f));

        //DRAW CURSOR
        g2.setColor(Color.white);
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(cursorX, cursorY, cursorWidth, cursorHeight, 10, 10);

        int itemIndex = getItemIndexOnSlot();

        if(itemIndex < gp.player.inventory.size()){
            drawSubWindow(dFrameX, dFrameY, dFrameWidth, dFrameHeight);
            for(String line: gp.player.inventory.get(itemIndex).description.split("\n")){
                g2.drawString(line, textX, textY );
                textY += 32;
            }
        }
    }

    public int getItemIndexOnSlot(){
        int itemIndex = slotCol + (slotRow * 5);
        return itemIndex;
    }

    private void drawMessage() {
        int messageX = gp.tileSize;
        int messageY = gp.tileSize * 4;
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 15f));
        for(int i = 0; i < message.size(); i++){
            if(message.get(i) != null){
                g2.setColor(Color.white);
                g2.drawString(message.get(i), messageX, messageY);

                int counter = messageCounter.get(i) + 1;
                messageCounter.set(i, counter);
                messageY += 20;

                if(messageCounter.get(i) > 180){
                    message.remove(i);
                    messageCounter.remove(i);
                }
            }
        }
    }

    private void drawCharacterScreen() {
        final int frameX = gp.tileSize * 2;
        final int frameY = gp.tileSize;
        final int frameWidth = gp.tileSize * 7;
        final int frameHeight = gp.tileSize * 10;
        drawSubWindow(frameX, frameY, frameWidth, frameHeight);

        g2.setColor(Color.white);
        g2.setFont(g2.getFont().deriveFont(15f));

        int textX = frameX + 20;
        int textY = frameY + gp.tileSize;
        final int lineHeight = 30;

        //TITLES
        g2.drawString("Level", textX, textY);
        textY += lineHeight;
        g2.drawString("HP", textX, textY);
        textY += lineHeight;
        g2.drawString("HP", textX, textY);
        textY += lineHeight;
        g2.drawString("Strength", textX, textY);
        textY += lineHeight;
        g2.drawString("Dexterity", textX, textY);
        textY += lineHeight;
        g2.drawString("Attack", textX, textY);
        textY += lineHeight;
        g2.drawString("Defense", textX, textY);
        textY += lineHeight;
        g2.drawString("EXP", textX, textY);
        textY += lineHeight;
        g2.drawString("Next Level", textX, textY);
        textY += lineHeight;
        g2.drawString("Coin", textX, textY);
        textY += lineHeight + 30;
        g2.drawString("Weapon", textX, textY);
        textY += lineHeight + 20;
        g2.drawString("Shield", textX, textY);
        textY += lineHeight;

        //VALUES
        int tailX = (frameX + frameWidth) - 30;
        textY = frameY + gp.tileSize;
        String value;

        value = String.valueOf(gp.player.level);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.HP + "/" + gp.player.maxHP);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.MP + "/" + gp.player.maxMP);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.strength);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.dexterity);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.attack);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.defense);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.exp);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.nextLevelExp);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.player.coin);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        g2.drawImage(gp.player.currentWeapon.down1, tailX - gp.tileSize, textY, null);
        textY += gp.tileSize;

        g2.drawImage(gp.player.currentShield.down1, tailX - gp.tileSize, textY, null);
    }

    private void drawPlayerHP() {

        int x = gp.tileSize/2;
        int y = gp.tileSize/2;
        int i = 0;

        //DISPLAY MAXHP
        while(i < gp.player.maxHP/2){
            g2.drawImage(heart_blank, x, y, null);
            i++;
            x += gp.tileSize;
        }

        //RESET
        x = gp.tileSize/2;
        y = gp.tileSize/2;
        i = 0;

        //DISPLAY CURRENT HP
        while(i < gp.player.HP){
            g2.drawImage(heart_half, x, y, null);
            i++;
            if(i < gp.player.HP){
                g2.drawImage(heart_full, x, y, null);
            }
            i++;
            x += gp.tileSize;
        }

        //DRAW MAXMP
        x = (gp.tileSize/2) - 5;
        y = (int)(gp.tileSize*1.5);
        i = 0;
        while(i < gp.player.maxMP){
            g2.drawImage(crystal_blank, x, y, null);
            i++;
            x += 35;
        }

        //DRAW MP
        x = (gp.tileSize/2) - 5;
        y = (int)(gp.tileSize*1.5);
        i = 0;
        while(i < gp.player.MP){
            g2.drawImage(crystal_full, x, y, null);
            i++;
            x += 35;
        }
    }

    private void drawTitleScreen() {
        //TITLE SCREEN
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 90F));
        String text = "AstralCore";
        int x = getXforCenteredText(text);
        int y = gp.tileSize * 5;

        //SHADOW
        g2.setColor(Color.gray);
        g2.drawString(text, x+10, y+10);

        //MAIN TEXT
        g2.setColor(Color.white);
        g2.drawString(text, x, y);

        //IMAGE
        x = gp.screenWidth/2 - (gp.tileSize*2)/2;
        y += gp.tileSize;
        g2.drawImage(gp.player.down1, x, y, gp.tileSize * 2, gp.tileSize * 2, null);

        //MENU
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 30F));

        text = "NEW GAME";
        x = getXforCenteredText(text);
        y += gp.tileSize * 5;
        g2.drawString(text, x, y);
        if(commandNum == 0){
            g2.drawString(">", x-gp.tileSize, y);
        }

        text = "CONTINUE";
        x = getXforCenteredText(text);
        y += gp.tileSize;
        g2.drawString(text, x, y);
        if(commandNum == 1){
            g2.drawString(">", x-gp.tileSize, y);
        }

        text = "QUIT";
        x = getXforCenteredText(text);
        y += gp.tileSize;
        g2.drawString(text, x, y);
        if(commandNum == 2){
            g2.drawString(">", x-gp.tileSize, y);
        }
    }

    private void drawDialogueScreen() {
        //WINDOW
        int x = gp.tileSize * 2;
        int y = gp.tileSize / 2;
        int width = gp.screenWidth - (gp.tileSize * 4);
        int height = gp.tileSize * 5;

        drawSubWindow(x, y, width, height);

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 20F));
        x += gp.tileSize;
        y += gp.tileSize;

        for(String line: currentDialogue.split("\n")){
            g2.drawString(line, x, y);
            y += 40;
        }

    }

    public void drawSubWindow(int x, int y, int width, int height){
        Color color = new Color(0,0,0, 200);
        g2.setColor(color);
        g2.fillRoundRect(x, y, width, height, 35, 35);

        color = new Color(255, 255, 255);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(5));
        g2.drawRoundRect(x+5, y+5, width-10, height-10, 25, 25);
    }

    public void drawPauseScreen(){
        String text = "PAUSE";

        int x = getXforCenteredText(text);
        int y = gp.screenHeight/2;

        g2.drawString(text, x, y);
    }

    public void option_fullScreenNotification(int frameX, int frameY){
        int textX = frameX + gp.tileSize;
        int textY = frameY + gp.tileSize * 3;

        currentDialogue = "The changes will take\neffect after\nrestarting the game!";

        for(String line: currentDialogue.split("\n")){
            g2.drawString(line, textX, textY);
            textY += 40;
        }

        //BACK
        textY = frameY + gp.tileSize * 9;
        g2.drawString("BACK", textX, textY);
        if(commandNum == 0){
            g2.drawString(">", textX-25, textY);
            if(gp.keyH.enterPressed){
                subState = 0;
            }
        }
    }

    public void option_control(int frameX, int frameY){
        int textX;
        int textY;

        //TITLE
        String text = "CONTROL";
        textX = getXforCenteredText(text);
        textY = frameY + gp.tileSize;
        g2.drawString(text, textX, textY);

        textX = frameX + gp.tileSize;
        textY += gp.tileSize;
        g2.drawString("Move",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Confirm/Pause",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Attack",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Shoot/Cast",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Character",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Option",textX, textY);

        textX = frameX + gp.tileSize*6;
        textY = frameY + gp.tileSize*2;
        g2.drawString("WASD", textX, textY);
        textY += gp.tileSize;
        g2.drawString("ENTER", textX, textY);
        textY += gp.tileSize;
        g2.drawString("E", textX, textY);
        textY += gp.tileSize;
        g2.drawString("F", textX, textY);
        textY += gp.tileSize;
        g2.drawString("C", textX, textY);
        textY += gp.tileSize;
        g2.drawString("ESC", textX, textY);

        //BACK
        textX = frameX + gp.tileSize;
        textY = frameY + gp.tileSize*9;
        g2.drawString("BACK", textX, textY);
        if(commandNum == 0){
            g2.drawString(">", textX-25,textY);
            if(gp.keyH.enterPressed){
                subState = 0;
                commandNum = 3;
            }
        }
    }

    public int getXforCenteredText(String text){
        int length = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int x = gp.screenWidth/2 - length/2;
        return x;
    }

    public int getXforRightAlignText(String text, int tailX){
        int length = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int x = tailX - length;
        return x;
    }

    public void drawDebug(Graphics2D g2, double playTime){
        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        g2.setColor(Color.white);

        int playerTileX = (gp.player.worldX + gp.player.solidArea.x) / gp.tileSize;
        int playerTileY = (gp.player.worldY + gp.player.solidArea.y) / gp.tileSize;

        int x = 24 * gp.tileSize;
        int y = 25;
        int lineHeight = 22;

        g2.drawString("FPS:" + gp.fpsCounter, x, y);
        y += lineHeight;
        g2.drawString("X:" + playerTileX + ",Y:" + playerTileY, x, y);
        y += lineHeight;
        g2.drawString("Time: " + decimalFormat.format(playTime), x, y);
    }
}
