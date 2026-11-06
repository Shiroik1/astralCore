package main;

import entity.Entity;
import entity.Monster;
import entity.Player;
import monster.MonsterType;
import object.ItemRegistry;
import object.ItemType;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CommandProcessor {
    private final Gamepanel gp;
    private static final Color INFO = new Color(150, 220, 255);
    private static final Color ERROR = new Color(255, 120, 120);

    public CommandProcessor(Gamepanel gp){
        this.gp = gp;
    }

    public void execute(String input){
        Player me = gp.localPlayer();
        if(me == null) return;

        if(gp.isNetworked && !gp.isHost){
            reply("Only the host can use commands.", ERROR);
            return;
        }

        String[] args = input.substring(1).trim().split("\\s+");
        String cmd = args[0].toLowerCase(Locale.ROOT);

        switch(cmd){
            case "help" -> help();
            case "list" -> list();
            case "pos" -> pos(me);
            case "heal" -> heal(me);
            case "level" -> level(me, args);
            case "tp" -> teleport(me, args);
            case "spawn" -> spawn(me, args);
            case "give" -> give(me, args);
            case "kill" -> killAll();
            case "clear" -> clearAll();
            default -> reply("Unknown command: /" + cmd + "  (try /help)", ERROR);
        }
    }

    private void help(){
        reply("/pos  /heal  /kill  /clear  /list", INFO);
        reply("/level [n]   /tp <col> <row>", INFO);
        reply("/spawn <monster> [n]", INFO);
        reply("/give <item> [n]", INFO);
    }

    private void list(){
        replyList("Monsters", Arrays.stream(MonsterType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList());
        replyList("Items", Arrays.stream(ItemType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList());
    }

    private void pos(Player me){
        int col = Math.floorDiv(me.worldX + me.solidArea.x, gp.tileSize); // same math as the T debug overlay
        int row = Math.floorDiv(me.worldY + me.solidArea.y, gp.tileSize);
        reply("You are at col " + col + ", row " + row + ".", INFO);
    }

    private void heal(Player me){
        if(me.isDead){
            reply("You are dead.", ERROR);
            return;
        }
        me.HP = me.maxHP;
        me.MP = me.maxMP;
        me.rage = me.maxRage;
        reply("Restored HP, MP and rage.", INFO);
    }

    private void level(Player me, String[] args){
        int n = parseInt(args, 1, 1);
        if(n < 1 || n > 10){
            reply("Usage: /level [1-10]", ERROR);
            return;
        }
        me.debugGrantLevels(n);
        reply("You are level " + me.level + ".", INFO);
    }

    private void teleport(Player me, String[] args){
        int col = parseInt(args, 1, Integer.MIN_VALUE);
        int row = parseInt(args, 2, Integer.MIN_VALUE);
        if(col == Integer.MIN_VALUE || row == Integer.MIN_VALUE){
            reply("Usage: /tp <col> <row>", ERROR);
            return;
        }
        if(!isWalkable(col, row)){
            reply("Can't go there: outside the map or solid.", ERROR);
            return;
        }
        me.worldX = col * gp.tileSize;
        me.worldY = row * gp.tileSize;
        reply("Teleported to " + col + ", " + row + ".", INFO);
    }

    private void spawn(Player me, String[] args){
        if(args.length < 2){
            reply("Usage: /spawn <monster> [n]   (see /list)", ERROR);
            return;
        }
        MonsterType type = MonsterType.fromName(args[1]);
        if(type == null){
            reply("Unknown monster: " + args[1], ERROR);
            return;
        }
        int count = parseInt(args, 2, 1);
        if(count < 1 || count > 10){
            reply("Count must be 1 to 10.", ERROR);
            return;
        }

        int[] base = spawnPoint(me);
        int spawned = 0;
        for(int i = 0; i < gp.monster.length && spawned < count; i++){
            if(gp.monster[i] != null) continue;

            int x = base[0] + (spawned % 3 - 1) * gp.tileSize;
            int y = base[1] + (spawned / 3) * gp.tileSize;
            if(!isWalkable(Math.floorDiv(x, gp.tileSize), Math.floorDiv(y, gp.tileSize))){
                x = base[0];
                y = base[1];
            }

            Monster m = type.create(gp);
            m.worldX = x;
            m.worldY = y;
            gp.monster[i] = m;
            spawned++;
        }
        reply("Spawned " + spawned + " " + args[1].toLowerCase(Locale.ROOT) + ".", spawned > 0 ? INFO : ERROR);
    }

    private void give(Player me, String[] args){
        if(args.length < 2){
            reply("Usage: /give <item> [n]   (see /list)", ERROR);
            return;
        }
        ItemType type = parseItem(args[1]);
        if(type == null){
            reply("Unknown item: " + args[1], ERROR);
            return;
        }
        int count = parseInt(args, 2, 1);
        if(count < 1 || count > 20){
            reply("Count must be 1 to 20.", ERROR);
            return;
        }

        Entity first = ItemRegistry.create(type, gp);
        if(first.type == Entity.type_pickuponly){
            for(int i = 0; i < count; i++){
                first.use(me); // hearts, crystals and coins apply straight away
            }
            return;
        }
        if(first.stackable){
            first.stackCount = Math.min(count, first.maxStackSize);
            reply(me.addToInventory(first) ? "Gave " + first.stackCount + " " + first.name + "." : "Inventory full.",
                    INFO);
            return;
        }

        int given = 0;
        for(int i = 0; i < count; i++){
            Entity item = (i == 0) ? first : ItemRegistry.create(type, gp);
            if(!me.addToInventory(item)){
                reply("Inventory full.", ERROR);
                break;
            }
            given++;
        }
        if(given > 0){
            reply("Gave " + given + " " + first.name + ".", INFO);
        }
    }

    private void killAll(){
        int n = 0;
        for(Entity m : gp.monster){
            if(m != null && !m.dying){
                m.HP = 0;
                m.dying = true;
                n++;
            }
        }
        reply("Killed " + n + ". They drop loot and count toward the boss.", INFO);
    }

    private void clearAll(){
        int n = 0;
        for(int i = 0; i < gp.monster.length; i++){
            if(gp.monster[i] != null){
                gp.monster[i] = null;
                n++;
            }
        }
        reply("Removed " + n + " monster(s).", INFO);
    }

    //HELPERS
    private boolean isWalkable(int col, int row){
        if(col < 0 || row < 0 || col >= gp.maxWorldCol || row >= gp.maxWorldRow) return false;
        return !gp.tileM.tiles[gp.tileM.mapTileNum[col][row]].collision;
    }

    // up to 3 tiles in front of the player, backing off to the player's own tile if walls are in the way
    private int[] spawnPoint(Player me){
        int baseCol = Math.floorDiv(me.worldX + me.solidArea.x + me.solidArea.width / 2, gp.tileSize);
        int baseRow = Math.floorDiv(me.worldY + me.solidArea.y + me.solidArea.height / 2, gp.tileSize);
        int dc = 0, dr = 0;
        switch(me.direction){
            case "up" -> dr = -1;
            case "down" -> dr = 1;
            case "left" -> dc = -1;
            case "right" -> dc = 1;
        }
        for(int dist = 3; dist >= 1; dist--){
            int col = baseCol + dc * dist;
            int row = baseRow + dr * dist;
            if(isWalkable(col, row)){
                return new int[]{col * gp.tileSize, row * gp.tileSize};
            }
        }
        return new int[]{baseCol * gp.tileSize, baseRow * gp.tileSize};
    }

    private int parseInt(String[] args, int index, int fallback){
        if(args.length <= index) return fallback;
        try{
            return Integer.parseInt(args[index]);
        } catch(NumberFormatException e){
            return Integer.MIN_VALUE;
        }
    }

    private ItemType parseItem(String name){
        try{
            return ItemType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch(IllegalArgumentException e){
            return null;
        }
    }

    private void reply(String text, Color color){
        gp.ui.addMessage(text, color);
    }

    private void replyList(String label, List<String> names){
        StringBuilder line = new StringBuilder(label + ": ");
        for(int i = 0; i < names.size(); i++){
            String piece = names.get(i) + (i < names.size() - 1 ? ", " : "");
            if(line.length() + piece.length() > 44){ // the log box fits about 48 characters
                reply(line.toString(), INFO);
                line = new StringBuilder("  ");
            }
            line.append(piece);
        }
        reply(line.toString(), INFO);
    }
}