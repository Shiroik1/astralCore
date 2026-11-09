package main;

import entity.Entity;
import entity.Player;
import world.WorldObject;

import java.awt.*;

public class CollisionChecker {
    Gamepanel gp;

    public CollisionChecker(Gamepanel gp){
        this.gp = gp;
    }

    public void checkTile(Entity entity){checkTile(entity,entity.speed);}

    public void checkTile(Entity entity,int distance){
        int left = entity.worldX + entity.solidArea.x;
        int right = left + entity.solidArea.width;
        int top = entity.worldY + entity.solidArea.y;
        int bottom = top + entity.solidArea.height;
        boolean blocked = switch (entity.direction) {
            case "up" -> gp.tileM.blocksMovement(left, top - distance, right, top);
            case "down" -> gp.tileM.blocksMovement(left, bottom, right, bottom + distance);
            case "left" -> gp.tileM.blocksMovement(left - distance, top, left, bottom);
            case "right" -> gp.tileM.blocksMovement(right, top, right + distance, bottom);
            default -> false;
        };
        if (blocked) entity.collisionOn = true;

        checkScenery(entity,distance);
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
            if(p == null || p.isDead || p.inWell!=gp.quests.inDungeon()) continue;

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

    public java.util.List<Integer> checkEntityMultiple(Rectangle bounds,Entity[] targets){
        var hits=new java.util.ArrayList<Integer>();
        for(int i=0;i<targets.length;i++){
            Entity target=targets[i];
            if(target!=null && target.alive && !target.dying && bounds.intersects(target.worldX+target.solidArea.x,target.worldY+target.solidArea.y,target.solidArea.width,target.solidArea.height))hits.add(i);
        }
        return hits;
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

    // Blocks movement into a scenery object's collision box. An entity already overlapping a box is allowed
    // to move, so nothing can get permanently stuck (spawned inside one, knocked into one, and so on).
    private void checkScenery(Entity entity,int distance){
        int dx = 0, dy = 0;
        switch (entity.direction){
            case "up" -> dy = -distance;
            case "down" -> dy = distance;
            case "left" -> dx = -distance;
            case "right" -> dx = distance;
        }

        int x = entity.worldX + entity.solidArea.x;
        int y = entity.worldY + entity.solidArea.y;
        Rectangle now = new Rectangle(x, y, entity.solidArea.width, entity.solidArea.height);
        Rectangle moved = new Rectangle(x + dx, y + dy, entity.solidArea.width, entity.solidArea.height);

        for(WorldObject o : gp.worldObjects){
            if(!o.blocks) continue;
            if(Math.abs(o.worldX - entity.worldX) > 300 || Math.abs(o.worldY - entity.worldY) > 300) continue; // cheap reject
            if(moved.intersects(o.blockRect) && !now.intersects(o.blockRect)){
                entity.collisionOn = true;
                return;
            }
        }
    }

    // the nearest interactive object within reach of `source`, or null
    public WorldObject findInteractable(Entity source){
        Rectangle body = new Rectangle(source.worldX + source.solidArea.x, source.worldY + source.solidArea.y,
                source.solidArea.width, source.solidArea.height);
        body.grow(10, 10);
        int cx = body.x + body.width / 2;
        int cy = body.y + body.height / 2;

        WorldObject best = null;
        long bestDist = Long.MAX_VALUE;
        for(WorldObject o : gp.worldObjects){
            if(!o.isInteractive()) continue;
            Rectangle reach = o.reachRect();
            if(!body.intersects(reach)) continue;
            long dx = reach.x + reach.width / 2 - cx;
            long dy = reach.y + reach.height / 2 - cy;
            long dist = dx * dx + dy * dy;
            if(dist < bestDist){
                bestDist = dist;
                best = o;
            }
        }
        return best;
    }

}
