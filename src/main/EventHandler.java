package main;

import java.awt.*;

public class EventHandler {
    Gamepanel gp;
    EventRect eventRect[][];

    int previousEventX, previousEventY;
    boolean canTouchEvent = true;

    public EventHandler(Gamepanel gp){
        this.gp = gp;

        eventRect = new EventRect[gp.maxWorldCol][gp.maxWorldRow];

        int col = 0;
        int row = 0;
        while(col < gp.maxWorldCol && row < gp.maxWorldRow){
            eventRect[col][row] = new EventRect();
            eventRect[col][row].x = 23;
            eventRect[col][row].y = 23;
            eventRect[col][row].width = 2;
            eventRect[col][row].height = 2;
            eventRect[col][row].eventRectDefaultX = eventRect[col][row].x;
            eventRect[col][row].eventRectDefaultY = eventRect[col][row].y;

            col++;
            if(col == gp.maxWorldCol){
                col = 0;
                row++;
            }
        }
    }

    public void checkEvent(){

        //Check if the player moves away 1 tile from the last event
        int xDistance = Math.abs(gp.localPlayer().worldX - previousEventX);
        int yDistance = Math.abs(gp.localPlayer().worldY - previousEventY);
        int distance = Math.max(xDistance, yDistance);
        if(distance > gp.tileSize){
            canTouchEvent = true;
        }

        if(canTouchEvent){
//            if(hit(54,79,"down")){
//                damagePit(54,79,gp.dialogueState);
//            }
            if(hit(32,18,"up")){
                healingPool(32,18, gp.dialogueState);
            }
        }
    }

    private void damagePit(int col, int row, int gameState) {
        gp.gameState = gameState;
        gp.playSE(6);
        gp.ui.currentDialogue = "You tripped!";
        gp.localPlayer().HP -= 2;
        canTouchEvent = false;
    }

    public void healingPool(int col, int row, int gameState){
        if(gp.keyH.ePressed){
            gp.gameState = gameState;
            gp.playSE(2);
            gp.localPlayer().attackCanceled = true;
            gp.ui.currentDialogue = "You drink the aqua of life.\nYour wounds has been fully healed!";
            gp.localPlayer().HP = gp.localPlayer().maxHP;
            gp.localPlayer().MP = gp.localPlayer().maxMP;
            gp.assetSetter.setMonster();
        }
    }

    public boolean hit(int col, int row, String reqDirection){
        boolean hit = false;

        gp.localPlayer().solidArea.x = gp.localPlayer().worldX + gp.localPlayer().solidArea.x;
        gp.localPlayer().solidArea.y = gp.localPlayer().worldY + gp.localPlayer().solidArea.y;
        eventRect[col][row].x = col*gp.tileSize + eventRect[col][row].x;
        eventRect[col][row].y = row*gp.tileSize + eventRect[col][row].y;

        if(gp.localPlayer().solidArea.intersects(eventRect[col][row]) && !eventRect[col][row].eventDone){
            if(gp.localPlayer().direction.contentEquals(reqDirection) || reqDirection.contentEquals("any")){
                hit = true;

                previousEventX = gp.localPlayer().worldX;
                previousEventY = gp.localPlayer().worldY;
            }
        }

        gp.localPlayer().solidArea.x = gp.localPlayer().solidAreaDefaultX;
        gp.localPlayer().solidArea.y = gp.localPlayer().solidAreaDefaultY;
        eventRect[col][row].x = eventRect[col][row].eventRectDefaultX;
        eventRect[col][row].y = eventRect[col][row].eventRectDefaultY;

        return hit;
    }
}
