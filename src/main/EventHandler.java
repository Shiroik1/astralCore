package main;

import entity.Player;

import java.awt.*;

public class EventHandler {
    Gamepanel gp;
    EventRect eventRect[][];

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

    public void checkEvent(Player player){
        int xDistance = Math.abs(player.worldX - player.previousEventX);
        int yDistance = Math.abs(player.worldY - player.previousEventY);
        int distance = Math.max(xDistance, yDistance);
        if(distance > gp.tileSize){
            player.canTouchEvent = true;
        }

        if(player.canTouchEvent){
            if(hit(32,18,"up", player)){
                healingPool(32,18, player);
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

    public void healingPool(int col, int row, Player player){
        if(player.currentInput != null && player.currentInput.ePressed && player.canResolveWorldActions()){
            player.attackCanceled = true;
            int healedAmount = player.maxHP - player.HP;
            player.HP = player.maxHP;
            player.MP = player.maxMP;
            gp.playSE(2);
            gp.broadcastPlayerEvent(player.playerId, "heal",
                    "Player " + (player.playerId + 1) + " drank the aqua of life and was fully healed!",
                    "You drink the aqua of life.\nYour wounds have been fully healed!");
            if(healedAmount > 0){
                gp.spawnFloatingText(player.worldX + gp.tileSize/2, player.worldY, "+" + healedAmount, new Color(120, 220, 120));
            }
            gp.assetSetter.setMonster();
            player.canTouchEvent = false;
        }
    }

    public boolean hit(int col, int row, String reqDirection, Player player){
        boolean hit = false;

        player.solidArea.x = player.worldX + player.solidArea.x;
        player.solidArea.y = player.worldY + player.solidArea.y;
        eventRect[col][row].x = col*gp.tileSize + eventRect[col][row].x;
        eventRect[col][row].y = row*gp.tileSize + eventRect[col][row].y;

        if(player.solidArea.intersects(eventRect[col][row]) && !eventRect[col][row].eventDone){
            if(player.direction.contentEquals(reqDirection) || reqDirection.contentEquals("any")){
                hit = true;
                player.previousEventX = player.worldX;
                player.previousEventY = player.worldY;
            }
        }

        player.solidArea.x = player.solidAreaDefaultX;
        player.solidArea.y = player.solidAreaDefaultY;
        eventRect[col][row].x = eventRect[col][row].eventRectDefaultX;
        eventRect[col][row].y = eventRect[col][row].eventRectDefaultY;

        return hit;
    }
}
