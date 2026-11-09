package main;

import entity.Entity;
import entity.Monster;
import entity.Player;
import monster.MonsterType;
import object.ItemRegistry;
import object.ItemType;
import world.SceneryLoader;
import world.WorldObject;
import world.WorldObjectType;
import world.GrassLayer;
import world.Lighting;

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
            case "place" -> place(me, args);
            case "remove" -> remove(me);
            case "saveobjects" -> saveObjects();
            case "mood" -> mood(args);
            case "paint" -> paint(args);
            case "savegrass" -> saveGrass();
            case "pixel" -> pixel(args);
            default -> reply("Unknown command: /" + cmd + "  (try /help)", ERROR);
        }
    }

    private void help(){
        reply("/pos  /heal  /kill  /clear  /list", INFO);
        reply("/level [n]   /tp <col> <row>", INFO);
        reply("/spawn <monster> [n]", INFO);
        reply("/give <item> [n]", INFO);
        reply("/place <obj> [text]  /remove  /saveobjects", INFO);
        reply("/mood <day|golden|dusk|night|off>", INFO);
        reply("/paint <short|tall|flowers|erase|off>", INFO);
        reply("/savegrass", INFO);
    }

    private void list(){
        replyList("Monsters", Arrays.stream(MonsterType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList());
        replyList("Items", Arrays.stream(ItemType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList());
        replyList("Objects", Arrays.stream(WorldObjectType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList());
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

    private void pixel(String[] args){
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch(mode){
            case "on"   -> { entity.CharacterSprites.pixelMode = true;  gp.setPixelBuffer(true); }
            case "off"  -> { entity.CharacterSprites.pixelMode = false; gp.setPixelBuffer(false); }
            case "char" -> { entity.CharacterSprites.pixelMode = true;  gp.setPixelBuffer(false); }
            default -> { reply("Usage: /pixel <on|off|char>", ERROR); return; }
        }
        for(entity.Player p : gp.players){
            if(p == null) continue;
            if(p.usesMana()) p.getMagePlayerImage(); else p.getPlayerImage();
        }
        for(entity.Entity npc : gp.npc){
            if(npc instanceof entity.NPC_oldman oldman) oldman.getImage();
            if(npc instanceof entity.NPC_merchant merchant)merchant.getImage();
        }
        reply("Pixel mode: " + mode, INFO);
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

    private boolean isWalkable(int col, int row){
        return !gp.tileM.isSolid(col, row);
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

    private void place(Player me, String[] args){
        if(gp.isNetworked){
            reply("Placing objects only works in single-player.", ERROR);
            return;
        }
        if(args.length < 2){
            reply("Usage: /place <object> [text]   (see /list)", ERROR);
            return;
        }
        WorldObjectType type = WorldObjectType.fromName(args[1]);
        if(type == null){
            reply("Unknown object: " + args[1], ERROR);
            return;
        }
        int[] tile = facingTile(me);
        String text = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).replace("\\n", "\n")
                : null;
        gp.worldObjects.add(new WorldObject(gp, type, tile[0], tile[1], text));
        reply("Placed " + args[1].toLowerCase(Locale.ROOT) + " at " + tile[0] + ", " + tile[1] + ".", INFO);
    }

    private void remove(Player me){
        if(gp.isNetworked){
            reply("Removing objects only works in single-player.", ERROR);
            return;
        }
        int[] tile = facingTile(me);
        int fx = tile[0] * gp.tileSize + gp.tileSize / 2;
        int fy = (tile[1] + 1) * gp.tileSize - 4;

        WorldObject best = null;
        long bestDist = (long) (gp.tileSize * 2) * (gp.tileSize * 2); // within two tiles
        for(WorldObject o : gp.worldObjects){
            long dx = o.worldX - fx;
            long dy = o.worldY - fy;
            long d = dx * dx + dy * dy;
            if(d < bestDist){
                bestDist = d;
                best = o;
            }
        }
        if(best == null){
            reply("Nothing close enough to remove.", ERROR);
            return;
        }
        gp.worldObjects.remove(best);
        reply("Removed " + best.kind.name().toLowerCase(Locale.ROOT) + ".", INFO);
    }

    private void saveObjects(){
        reply(SceneryLoader.save(gp)
                ? "Saved " + gp.worldObjects.size() + " objects to " + SceneryLoader.EXTERNAL_PATH + "."
                : "Could not save (see the console).", gp.worldObjects.isEmpty() ? ERROR : INFO);
    }

    // the tile directly in front of the player
    private int[] facingTile(Player me){
        int col = Math.floorDiv(me.worldX + me.solidArea.x + me.solidArea.width / 2, gp.tileSize);
        int row = Math.floorDiv(me.worldY + me.solidArea.y + me.solidArea.height / 2, gp.tileSize);
        switch(me.direction){
            case "up" -> row--;
            case "down" -> row++;
            case "left" -> col--;
            case "right" -> col++;
        }
        return new int[]{col, row};
    }

    private void mood(String[] args){
        if(args.length < 2){
            reply("Usage: /mood <day|golden|dusk|night|off>", ERROR);
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if(name.equals("off")){
            gp.lighting.enabled = false;
            reply("Lighting off.", INFO);
            return;
        }
        Lighting.Mood m = Lighting.Mood.fromName(name);
        if(m == null){
            reply("Unknown mood: " + name, ERROR);
            return;
        }
        gp.lighting.enabled = true;
        gp.lighting.setMood(m, false);
        reply("Mood: " + name + ".", INFO);
    }

    private void paint(String[] args){
        if(gp.isNetworked){
            reply("Painting grass only works in single-player.", ERROR);
            return;
        }
        if(args.length < 2){
            reply("Usage: /paint <short|tall|flowers|erase|off> [size 0-4] [density 1-4]", ERROR);
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if(mode.equals("off")){
            gp.grassEditor.stop();
            reply("Paint mode off. Use /savegrass to keep your changes.", INFO);
            return;
        }
        int type = switch(mode){
            case "short" -> GrassLayer.SHORT;
            case "tall" -> GrassLayer.TALL;
            case "flowers" -> GrassLayer.FLOWERS;
            case "erase" -> GrassLayer.NONE;
            default -> -1;
        };
        if(type < 0){
            reply("Unknown brush: " + mode, ERROR);
            return;
        }
        int size = parseInt(args, 2, 1);
        int density = parseInt(args, 3, 3);
        if(size < 0 || size > 4 || density < 1 || density > 4){
            reply("Size is 0 to 4 and density is 1 to 4.", ERROR);
            return;
        }
        gp.grassEditor.start(type, size, density);
        reply("Paint mode: LMB paints, RMB erases. 1-4 brush, [ ] size, , . density.", INFO);
    }

    private void saveGrass(){
        boolean ok = gp.grass.save();
        reply(ok ? "Saved grass to " + GrassLayer.PATH + "." : "Could not save (see the console).", ok ? INFO : ERROR);
    }
}