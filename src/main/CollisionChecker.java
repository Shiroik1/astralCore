package main;

import entity.Entity;
import entity.Player;

import java.awt.*;

public class CollisionChecker {
    Gamepanel gp;

    public CollisionChecker(Gamepanel gp){
        this.gp = gp;
    }

    private boolean isSolidTile(int col, int row){
        if(col < 0 || row < 0 || col >= gp.maxWorldCol || row >= gp.maxWorldRow){
            return true; // outside the map counts as a wall
        }
        return gp.tileM.tiles[gp.tileM.mapTileNum[col][row]].collision;
    }

    public void checkTile(Entity entity){
        int entityLeftWorldX = entity.worldX + entity.solidArea.x;
        int entityRightWorldX = entity.worldX + entity.solidArea.x + entity.solidArea.width;
        int entityTopWorldY = entity.worldY + entity.solidArea.y;
        int entityBotWorldY = entity.worldY + entity.solidArea.y + entity.solidArea.height;

        int entityLeftCol = Math.floorDiv(entityLeftWorldX, gp.tileSize);
        int entityRightCol = Math.floorDiv(entityRightWorldX, gp.tileSize);
        int entityTopRow = Math.floorDiv(entityTopWorldY, gp.tileSize);
        int entityBotRow = Math.floorDiv(entityBotWorldY, gp.tileSize);

        switch (entity.direction){
            case "up" -> {
                entityTopRow = Math.floorDiv(entityTopWorldY - entity.speed, gp.tileSize);
                if(isSolidTile(entityLeftCol, entityTopRow) || isSolidTile(entityRightCol, entityTopRow)){
                    entity.collisionOn = true;
                }
            }
            case "down" -> {
                entityBotRow = Math.floorDiv(entityBotWorldY + entity.speed, gp.tileSize);
                if(isSolidTile(entityLeftCol, entityBotRow) || isSolidTile(entityRightCol, entityBotRow)){
                    entity.collisionOn = true;
                }
            }
            case "left" -> {
                entityLeftCol = Math.floorDiv(entityLeftWorldX - entity.speed, gp.tileSize);
                if(isSolidTile(entityLeftCol, entityTopRow) || isSolidTile(entityLeftCol, entityBotRow)){
                    entity.collisionOn = true;
                }
            }
            case "right" -> {
                entityRightCol = Math.floorDiv(entityRightWorldX + entity.speed, gp.tileSize);
                if(isSolidTile(entityRightCol, entityTopRow) || isSolidTile(entityRightCol, entityBotRow)){
                    entity.collisionOn = true;
                }
            }
        }
    }

    public int checkObject(Entity entity, boolean player){
        int index = 999;

        for(int i = 0; i < gp.obj.length; i++){
            if(gp.obj[i] != null){
                //Get entity's solid area position
                entity.solidArea.x += entity.worldX;
                entity.solidArea.y += entity.worldY;

                //Get object's solid area position
                gp.obj[i].solidArea.x += gp.obj[i].worldX;
                gp.obj[i].solidArea.y += gp.obj[i].worldY;

                switch (entity.direction){
                    case "up" -> {
                        entity.solidArea.y -= entity.speed;
                    }
                    case "down" -> {
                        entity.solidArea.y += entity.speed;
                    }
                    case "left" -> {
                        entity.solidArea.x -= entity.speed;
                    }
                    case "right" -> {
                        entity.solidArea.x += entity.speed;
                    }
                }

                if(entity.solidArea.intersects(gp.obj[i].solidArea)){
                    if(gp.obj[i].collision){
                        entity.collisionOn = true;
                    }
                    if(player){
                        index = i;
                    }
                }

                entity.solidArea.x = entity.solidAreaDefaultX;
                entity.solidArea.y = entity.solidAreaDefaultY;
                gp.obj[i].solidArea.x = gp.obj[i].solidAreaDefaultX;
                gp.obj[i].solidArea.y = gp.obj[i].solidAreaDefaultY;
            }

        }

        return index;
    }

    //NPC OR MONSTER
    public int checkEntity(Entity entity, Entity[] target){
        int index = 999;

        for(int i = 0; i < target.length; i++){
            if(target[i] != null){
                //Get entity's solid area position
                entity.solidArea.x += entity.worldX;
                entity.solidArea.y += entity.worldY;

                //Get object's solid area position
                target[i].solidArea.x += target[i].worldX;
                target[i].solidArea.y += target[i].worldY;

                switch (entity.direction){
                    case "up" -> {
                        entity.solidArea.y -= entity.speed;
                    }
                    case "down" -> {
                        entity.solidArea.y += entity.speed;
                    }
                    case "left" -> {
                        entity.solidArea.x -= entity.speed;
                    }
                    case "right" -> {
                        entity.solidArea.x += entity.speed;
                    }
                }

                if(entity.solidArea.intersects(target[i].solidArea)){
                    if(target[i] != entity){
                        entity.collisionOn = true;
                        index = i;
                    }
                }

                entity.solidArea.x = entity.solidAreaDefaultX;
                entity.solidArea.y = entity.solidAreaDefaultY;
                target[i].solidArea.x = target[i].solidAreaDefaultX;
                target[i].solidArea.y = target[i].solidAreaDefaultY;
            }

        }

        return index;
    }

    public Player checkPlayer(Entity entity){
        Player contactedPlayer = null;

        for(Player p : gp.players){
            if(p == null || p.isDead) continue;

            entity.solidArea.x += entity.worldX;
            entity.solidArea.y += entity.worldY;
            p.solidArea.x += p.worldX;
            p.solidArea.y += p.worldY;

            switch (entity.direction){
                case "up" -> entity.solidArea.y -= entity.speed;
                case "down" -> entity.solidArea.y += entity.speed;
                case "left" -> entity.solidArea.x -= entity.speed;
                case "right" -> entity.solidArea.x += entity.speed;
            }

            if(entity.solidArea.intersects(p.solidArea)){
                entity.collisionOn = true;
                contactedPlayer = p;
            }

            entity.solidArea.x = entity.solidAreaDefaultX;
            entity.solidArea.y = entity.solidAreaDefaultY;
            p.solidArea.x = p.solidAreaDefaultX;
            p.solidArea.y = p.solidAreaDefaultY;

            if(contactedPlayer != null) break; // a monster's single collision check only needs the first player it touches
        }

        return contactedPlayer;
    }

    public boolean isInInteractRange(Entity source, Entity target){
        Rectangle sourceBounds = new Rectangle(source.worldX + source.solidArea.x, source.worldY + source.solidArea.y,
                source.solidArea.width, source.solidArea.height);
        Rectangle targetBounds = new Rectangle(target.worldX + target.solidArea.x, target.worldY + target.solidArea.y,
                target.solidArea.width, target.solidArea.height);
        return sourceBounds.intersects(targetBounds);
    }

    public java.util.List<Integer> checkEntityMultiple(Entity entity, Entity[] target){
        java.util.List<Integer> hits = new java.util.ArrayList<>();

        for(int i = 0; i < target.length; i++){
            if(target[i] != null){
                entity.solidArea.x += entity.worldX;
                entity.solidArea.y += entity.worldY;
                target[i].solidArea.x += target[i].worldX;
                target[i].solidArea.y += target[i].worldY;

                switch (entity.direction){
                    case "up" -> entity.solidArea.y -= entity.speed;
                    case "down" -> entity.solidArea.y += entity.speed;
                    case "left" -> entity.solidArea.x -= entity.speed;
                    case "right" -> entity.solidArea.x += entity.speed;
                }

                if(entity.solidArea.intersects(target[i].solidArea)){
                    if(target[i] != entity){
                        entity.collisionOn = true;
                        hits.add(i);
                    }
                }

                entity.solidArea.x = entity.solidAreaDefaultX;
                entity.solidArea.y = entity.solidAreaDefaultY;
                target[i].solidArea.x = target[i].solidAreaDefaultX;
                target[i].solidArea.y = target[i].solidAreaDefaultY;
            }
        }

        return hits;
    }

}
