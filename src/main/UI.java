package main;

import java.awt.*;
import java.io.InputStream;
import java.text.DecimalFormat;

public class UI {
    Gamepanel gp;
    Graphics2D g2;
    Font solomonKey;
    public boolean messageOn = false;
    public boolean gameFinished = false;
    public String message = "";
    int messageCounter = 0;
    public String currentDialogue = "";

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

    }

    public void showMessage(String text){
        message = text;
        messageOn = true;
    }

    public void draw(Graphics2D g2){

        this.g2 = g2;

        g2.setFont(solomonKey);
        g2.setColor(Color.white);

        //PLAY STATE
        if(gp.gameState == gp.playState){
            //PLAYTIME
            playTime += (double) 1/60;

            //MESSAGE
            if(messageOn){
                g2.setFont(g2.getFont().deriveFont(30F));
                g2.drawString(message, gp.tileSize/2, 15 * gp.tileSize);

                messageCounter++;

                if(messageCounter > 120){
                    messageCounter = 0;
                    messageOn = false;
                }
            }

            //Toggle debug menu with [ T ] key
            if(gp.keyH.showDebug){
                drawDebug(g2, playTime);
            }
        }

        //PAUSE STATE
        if(gp.gameState == gp.pauseState){
            drawPauseScreen();
        }

        //DIALOGUE STATE
        if(gp.gameState == gp.dialogueState){
            drawDialogueScreen();
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

    public int getXforCenteredText(String text){
        int length = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int x = gp.screenWidth/2 - length/2;
        return x;
    }

    public void drawDebug(Graphics2D g2, double playTime){
        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        g2.setColor(Color.white);

        int playerTileX = (gp.player.worldX + gp.player.solidArea.x) / gp.tileSize;
        int playerTileY = (gp.player.worldY + gp.player.solidArea.y) / gp.tileSize;

        int x = 10;
        int y = 25;
        int lineHeight = 22;

        g2.drawString("FPS:" + gp.fpsCounter, x, y);
        y += lineHeight;
        g2.drawString("X:" + playerTileX + ",Y:" + playerTileY, x, y);
        y += lineHeight;
        g2.drawString("Time: " + decimalFormat.format(playTime), x, y);
    }
}
