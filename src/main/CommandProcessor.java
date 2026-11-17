package main;

import entity.Entity;
import entity.Monster;
import entity.Player;

import monster.MonsterType;

import object.ItemRegistry;
import object.ItemType;

import world.GrassLayer;
import world.Lighting;
import world.SceneryLoader;
import world.WorldObject;
import world.WorldObjectType;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CommandProcessor {
    private final Gamepanel gp;
    private static final Color INFO = new Color(150, 220, 255);
    private static final Color ERROR = new Color(255, 120, 120);

    /**
     * Creates a CommandProcessor instance and initializes the state required by its collaborators.
     */
    public CommandProcessor(Gamepanel gp) {
        this.gp = gp;
    }

    /** Performs the execute operation for CommandProcessor. */
    public void execute(String input) {
        Player me = gp.localPlayer();
        if (me == null) return;

        if (gp.isNetworked && !gp.isHost) {
            reply("Only the host can use commands.", ERROR);
            return;
        }

        String[] args = input.substring(1).trim().split("\\s+");
        String cmd = args[0].toLowerCase(Locale.ROOT);

        switch (cmd) {
            case "help" -> help();
            case "list" -> list();
            case "pos" -> pos(me);
            case "heal" -> heal(me);
            case "level" -> level(me, args);
            case "stats" -> reply(me.statSummary(), INFO);
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
            case "build" -> build(args);
            case "savebuild" -> saveBuild();
            case "blueprint" -> blueprint(args);
            case "savegrass" -> saveGrass();
            case "pixel" -> pixel(args);
            case "time" -> time(args);
            case "regenworld" -> regenerateWorld(args);
            case "elevation" -> {
                if (!Boolean.getBoolean("astral.dev")) {
                    reply("Enable -Dastral.dev=true", ERROR);
                    break;
                }
                try {
                    if (args.length > 1) {
                        world.generation.ElevationDebugOverlay.mode(args[1]);
                        reply("Elevation overlay: " + args[1], INFO);
                    } else {
                        boolean shown = world.generation.ElevationDebugOverlay.toggle();
                        reply("Elevation overlay " + (shown ? "on" : "off"), INFO);
                    }
                    if (gp.generatedWorld != null && gp.generatedWorld.heights() != null)
                        for (String line : gp.generatedWorld.heights().debugLines())
                            reply(line, INFO);
                } catch (IllegalArgumentException e) {
                    reply(e.getMessage(), ERROR);
                }
            }
            case "performance", "chunks" ->
                    reply(gp.performance.command(args.length > 1 ? args[1] : "toggle"), INFO);
            case "skeletons" -> {
                if (args.length == 3 && args[1].equals("day") && Boolean.getBoolean("astral.dev")) {
                    try {
                        int day = Integer.parseInt(args[2]);
                        if (day < 1 || day > 1000000) throw new IllegalArgumentException();
                        gp.lighting.cycle.restore(day, gp.lighting.cycle.time(), 0);
                        reply("World day: " + day, INFO);
                    } catch (IllegalArgumentException invalid) {
                        reply("Use /skeletons day <1-1000000>", ERROR);
                    }
                } else
                    reply(gp.slimeSpawner.skeletonCommand(args.length > 1 ? args[1] : "on"), INFO);
            }
            case "encounters" ->
                    reply(gp.slimeSpawner.debugCommand(args.length > 1 ? args[1] : "toggle"), INFO);
            case "nest" -> {
                if (Boolean.getBoolean("astral.dev")) reply(gp.slimeNest.diagnostic(), INFO);
                else reply("Enable -Dastral.dev=true to inspect the nest", ERROR);
            }
            default -> reply("Unknown command: /" + cmd + "  (try /help)", ERROR);
        }
    }

    /** Performs the help operation for CommandProcessor. */
    private void help() {
        reply("/pos  /heal  /kill  /clear  /list", INFO);
        reply("/level <1-25>|+<n>  /stats  /tp <col> <row>", INFO);
        reply("/spawn <monster> [n]", INFO);
        reply("/give <item> [n]", INFO);
        reply("/place <obj> [text]  /remove  /saveobjects", INFO);
        reply("/mood <day|golden|dusk|night|off>", INFO);
        reply("/paint <green|dark|golden|flowers|erase|off>", INFO);
        reply("/savegrass", INFO);
        reply("/build <element-id|off>  /savebuild", INFO);
        reply("/blueprint <id> <anchor-col> <anchor-row>", INFO);
        reply("/time [hour]", INFO);
        if (Boolean.getBoolean("astral.dev"))
            reply(
                    "/regenworld <long seed> (offline procedural world), /nest (planned encounter"
                        + " coordinates), /elevation [height|formations|edges|boundary|access],"
                        + " /encounters [on|off|fast|normal|now], /performance [on|off|reset|grid],"
                        + " /skeletons [on|off|now|day <n>]",
                    INFO);
    }

    /** Performs the regenerate world operation for CommandProcessor. */
    private void regenerateWorld(String[] args) {
        if (args.length != 2) {
            reply("Usage: /regenworld <long seed>", ERROR);
            return;
        }
        try {
            gp.regenerateWorld(Long.parseLong(args[1]));
            reply("Generated grassland with seed " + gp.worldConfig.seed() + ".", INFO);
        } catch (IllegalArgumentException | IllegalStateException e) {
            reply(e.getMessage(), ERROR);
        }
    }

    /** Performs the list operation for CommandProcessor. */
    private void list() {
        replyList(
                "Monsters",
                Arrays.stream(MonsterType.values())
                        .map(t -> t.name().toLowerCase(Locale.ROOT))
                        .toList());
        replyList(
                "Items",
                Arrays.stream(ItemType.values())
                        .map(t -> t.name().toLowerCase(Locale.ROOT))
                        .toList());
        replyList(
                "Building elements",
                building.BuildElements.all().stream()
                        .map(building.BuildElements.Element::id)
                        .toList());
        replyList(
                "Objects",
                Arrays.stream(WorldObjectType.values())
                        .map(t -> t.name().toLowerCase(Locale.ROOT))
                        .toList());
        replyList(
                "Furnishings",
                building.PropCatalog.all().stream().map(building.PropCatalog.Prop::id).toList());
    }

    /** Performs the pos operation for CommandProcessor. */
    private void pos(Player me) {
        int col =
                Math.floorDiv(
                        me.worldX + me.solidArea.x,
                        gp.tileSize); // same math as the T debug overlay
        int row = Math.floorDiv(me.worldY + me.solidArea.y, gp.tileSize);
        reply("You are at col " + col + ", row " + row + ".", INFO);
    }

    /** Performs the heal operation for CommandProcessor. */
    private void heal(Player me) {
        if (me.isDead) {
            reply("You are dead.", ERROR);
            return;
        }
        me.HP = me.maxHP;
        me.MP = me.maxMP;
        me.rage = me.maxRage;
        reply("Restored HP, MP and rage.", INFO);
    }

    /** Performs the level operation for CommandProcessor. */
    private void level(Player me, String[] args) {
        String a = args.length > 1 ? args[1] : "";
        int n;
        try {
            n = a.startsWith("+") ? Integer.parseInt(a.substring(1)) : Integer.parseInt(a);
        } catch (NumberFormatException e) {
            reply("Usage: /level <1-25>  (set)   /level +<n>  (add)", ERROR);
            return;
        }
        int target = a.startsWith("+") ? me.level + n : n;
        if (target < 1 || target > 25) {
            reply("Level must be between 1 and 25.", ERROR);
            return;
        }
        me.debugSetLevel(target);
        reply(me.statSummary(), INFO);
    }

    /** Performs the pixel operation for CommandProcessor. */
    private void pixel(String[] args) {
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (mode) {
            case "on" -> {
                entity.CharacterSprites.pixelMode = true;
                gp.setPixelBuffer(true);
            }
            case "off" -> {
                entity.CharacterSprites.pixelMode = false;
                gp.setPixelBuffer(false);
            }
            case "char" -> {
                entity.CharacterSprites.pixelMode = true;
                gp.setPixelBuffer(false);
            }
            default -> {
                reply("Usage: /pixel <on|off|char>", ERROR);
                return;
            }
        }
        for (entity.Player p : gp.players) {
            if (p == null) continue;
            if (p.usesMana()) p.getMagePlayerImage();
            else p.getPlayerImage();
        }
        for (entity.Entity npc : gp.npc) {
            if (npc instanceof entity.NPC_oldman oldman) oldman.getImage();
            if (npc instanceof entity.NPC_merchant merchant) merchant.getImage();
        }
        reply("Pixel mode: " + mode, INFO);
    }

    /** Performs the teleport operation for CommandProcessor. */
    private void teleport(Player me, String[] args) {
        int col = parseInt(args, 1, Integer.MIN_VALUE);
        int row = parseInt(args, 2, Integer.MIN_VALUE);
        if (col == Integer.MIN_VALUE || row == Integer.MIN_VALUE) {
            reply("Usage: /tp <col> <row>", ERROR);
            return;
        }
        if (!isWalkable(col, row)) {
            reply("Can't go there: outside the map or solid.", ERROR);
            return;
        }
        me.worldX = col * gp.tileSize;
        me.worldY = row * gp.tileSize;
        reply("Teleported to " + col + ", " + row + ".", INFO);
    }

    /** Performs the spawn operation for CommandProcessor. */
    private void spawn(Player me, String[] args) {
        if (args.length < 2) {
            reply("Usage: /spawn <monster> [n]   (see /list)", ERROR);
            return;
        }
        MonsterType type = MonsterType.fromName(args[1]);
        if (type == null) {
            reply("Unknown monster: " + args[1], ERROR);
            return;
        }
        int count = parseInt(args, 2, 1);
        if (count < 1 || count > 10) {
            reply("Count must be 1 to 10.", ERROR);
            return;
        }

        int[] base = spawnPoint(me);
        int spawned = 0;
        for (int i = 0; i < gp.monster.length && spawned < count; i++) {
            if (gp.monster[i] != null) continue;

            int x = base[0] + (spawned % 3 - 1) * gp.tileSize;
            int y = base[1] + (spawned / 3) * gp.tileSize;
            if (!isWalkable(Math.floorDiv(x, gp.tileSize), Math.floorDiv(y, gp.tileSize))) {
                x = base[0];
                y = base[1];
            }

            Monster m = type.create(gp);
            m.worldX = x;
            m.worldY = y;
            gp.monster[i] = m;
            spawned++;
        }
        reply(
                "Spawned " + spawned + " " + args[1].toLowerCase(Locale.ROOT) + ".",
                spawned > 0 ? INFO : ERROR);
    }

    /** Performs the give operation for CommandProcessor. */
    private void give(Player me, String[] args) {
        if (args.length < 2) {
            reply("Usage: /give <item> [n]   (see /list)", ERROR);
            return;
        }
        ItemType type = parseItem(args[1]);
        if (type == null) {
            reply("Unknown item: " + args[1], ERROR);
            return;
        }
        int count = parseInt(args, 2, 1);
        if (count < 1 || count > 20) {
            reply("Count must be 1 to 20.", ERROR);
            return;
        }

        Entity first = ItemRegistry.create(type, gp);
        if (first.type == Entity.type_pickuponly) {
            for (int i = 0; i < count; i++) {
                first.use(me); // hearts, crystals and coins apply straight away
            }
            return;
        }
        if (first.stackable) {
            first.stackCount = Math.min(count, first.maxStackSize);
            reply(
                    me.addToInventory(first)
                            ? "Gave " + first.stackCount + " " + first.name + "."
                            : "Inventory full.",
                    INFO);
            return;
        }

        int given = 0;
        for (int i = 0; i < count; i++) {
            Entity item = (i == 0) ? first : ItemRegistry.create(type, gp);
            if (!me.addToInventory(item)) {
                reply("Inventory full.", ERROR);
                break;
            }
            given++;
        }
        if (given > 0) {
            reply("Gave " + given + " " + first.name + ".", INFO);
        }
    }

    /** Performs the kill all operation for CommandProcessor. */
    private void killAll() {
        int n = 0;
        for (Entity m : gp.monster) {
            if (m != null && !m.dying) {
                m.HP = 0;
                m.dying = true;
                n++;
            }
        }
        reply("Killed " + n + ". They drop loot and count toward the boss.", INFO);
    }

    /** Clears the all for CommandProcessor. */
    private void clearAll() {
        int n = 0;
        for (int i = 0; i < gp.monster.length; i++) {
            if (gp.monster[i] != null) {
                gp.monster[i] = null;
                n++;
            }
        }
        reply("Removed " + n + " monster(s).", INFO);
    }

    /** Reports whether walkable for CommandProcessor. */
    private boolean isWalkable(int col, int row) {
        return !gp.tileM.isSolid(col, row);
    }

    // up to 3 tiles in front of the player, backing off to the player's own tile if walls are in
    // the way
    /** Spawns the point for CommandProcessor. */
    private int[] spawnPoint(Player me) {
        int baseCol =
                Math.floorDiv(me.worldX + me.solidArea.x + me.solidArea.width / 2, gp.tileSize);
        int baseRow =
                Math.floorDiv(me.worldY + me.solidArea.y + me.solidArea.height / 2, gp.tileSize);
        int dc = 0, dr = 0;
        switch (me.direction) {
            case "up" -> dr = -1;
            case "down" -> dr = 1;
            case "left" -> dc = -1;
            case "right" -> dc = 1;
        }
        for (int dist = 3; dist >= 1; dist--) {
            int col = baseCol + dc * dist;
            int row = baseRow + dr * dist;
            if (isWalkable(col, row)) {
                return new int[] {col * gp.tileSize, row * gp.tileSize};
            }
        }
        return new int[] {baseCol * gp.tileSize, baseRow * gp.tileSize};
    }

    /** Parses the int for CommandProcessor. */
    private int parseInt(String[] args, int index, int fallback) {
        if (args.length <= index) return fallback;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    /** Parses the item for CommandProcessor. */
    private ItemType parseItem(String name) {
        try {
            return ItemType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Performs the reply operation for CommandProcessor. */
    private void reply(String text, Color color) {
        gp.ui.addMessage(text, color);
    }

    /** Performs the reply list operation for CommandProcessor. */
    private void replyList(String label, List<String> names) {
        StringBuilder line = new StringBuilder(label + ": ");
        for (int i = 0; i < names.size(); i++) {
            String piece = names.get(i) + (i < names.size() - 1 ? ", " : "");
            if (line.length() + piece.length() > 44) { // the log box fits about 48 characters
                reply(line.toString(), INFO);
                line = new StringBuilder("  ");
            }
            line.append(piece);
        }
        reply(line.toString(), INFO);
    }

    /** Performs the place operation for CommandProcessor. */
    private void place(Player me, String[] args) {
        if (gp.isNetworked) {
            reply("Placing objects only works in single-player.", ERROR);
            return;
        }
        if (args.length < 2) {
            reply("Usage: /place <object> [text]   (see /list)", ERROR);
            return;
        }
        WorldObjectType type = WorldObjectType.fromName(args[1]);
        if (type == null) {
            reply("Unknown object: " + args[1], ERROR);
            return;
        }
        int[] tile = facingTile(me);
        if (type == WorldObjectType.BUILDING) {
            if (args.length != 3 || !art.BuildingCatalog.has(args[2])) {
                reply("Use /place building <catalog-id>.", ERROR);
                return;
            }
            gp.worldObjects.add(
                    new WorldObject(gp, type, tile[0], tile[1], null, args[2], java.util.Map.of()));
            reply("Placed " + args[2] + ".", INFO);
            return;
        }
        String text =
                args.length > 2
                        ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                                .replace("\\n", "\n")
                        : null;
        gp.worldObjects.add(new WorldObject(gp, type, tile[0], tile[1], text));
        reply(
                "Placed "
                        + args[1].toLowerCase(Locale.ROOT)
                        + " at "
                        + tile[0]
                        + ", "
                        + tile[1]
                        + ".",
                INFO);
    }

    /** Performs the remove operation for CommandProcessor. */
    private void remove(Player me) {
        if (gp.isNetworked) {
            reply("Removing objects only works in single-player.", ERROR);
            return;
        }
        int[] tile = facingTile(me);
        int fx = tile[0] * gp.tileSize + gp.tileSize / 2;
        int fy = (tile[1] + 1) * gp.tileSize - 4;

        WorldObject best = null;
        long bestDist = (long) (gp.tileSize * 2) * (gp.tileSize * 2); // within two tiles
        for (WorldObject o : gp.worldObjects) {
            long dx = o.worldX - fx;
            long dy = o.worldY - fy;
            long d = dx * dx + dy * dy;
            if (d < bestDist) {
                bestDist = d;
                best = o;
            }
        }
        if (best == null) {
            reply("Nothing close enough to remove.", ERROR);
            return;
        }
        gp.worldObjects.remove(best);
        reply("Removed " + best.kind.name().toLowerCase(Locale.ROOT) + ".", INFO);
    }

    /** Saves the objects for CommandProcessor. */
    private void saveObjects() {
        if (gp.worldConfig.procedural()) {
            reply(
                    "Procedural scenery is seed-generated; authored scenery.json was not changed.",
                    ERROR);
            return;
        }
        reply(
                SceneryLoader.save(gp)
                        ? "Saved "
                                + gp.worldObjects.size()
                                + " objects to "
                                + SceneryLoader.EXTERNAL_PATH
                                + "."
                        : "Could not save (see the console).",
                gp.worldObjects.isEmpty() ? ERROR : INFO);
    }

    // the tile directly in front of the player
    /** Resolves the facing tile result used by CommandProcessor. */
    private int[] facingTile(Player me) {
        int col = Math.floorDiv(me.worldX + me.solidArea.x + me.solidArea.width / 2, gp.tileSize);
        int row = Math.floorDiv(me.worldY + me.solidArea.y + me.solidArea.height / 2, gp.tileSize);
        switch (me.direction) {
            case "up" -> row--;
            case "down" -> row++;
            case "left" -> col--;
            case "right" -> col++;
        }
        return new int[] {col, row};
    }

    /** Performs the mood operation for CommandProcessor. */
    private void mood(String[] args) {
        if (args.length < 2) {
            reply("Usage: /mood <auto|day|golden|dusk|night|off>", ERROR);
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (name.equals("auto")) {
            gp.lighting.enabled = true;
            gp.lighting.cycleOn = true;
            reply("Day/night cycle on.", INFO);
            return;
        }
        if (name.equals("off")) {
            gp.lighting.enabled = false;
            reply("Lighting off.", INFO);
            return;
        }
        Lighting.Mood m = Lighting.Mood.fromName(name);
        if (m == null) {
            reply("Unknown mood: " + name, ERROR);
            return;
        }
        gp.lighting.enabled = true;
        gp.lighting.cycleOn = false;
        gp.lighting.setMood(m, false);
        reply("Mood: " + name + ".", INFO);
    }

    /** Performs the paint operation for CommandProcessor. */
    private void paint(String[] args) {
        if (gp.isNetworked) {
            reply("Painting grass only works in single-player.", ERROR);
            return;
        }
        if (args.length < 2) {
            reply(
                    "Usage: /paint <green|dark|golden|flowers|erase|off> [size 0-4] [density 1-4]",
                    ERROR);
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("off")) {
            gp.grassEditor.stop();
            reply("Paint mode off. Use /savegrass to keep your changes.", INFO);
            return;
        }
        int type =
                switch (mode) {
                    case "green" -> GrassLayer.FIELD_GREEN;
                    case "dark" -> GrassLayer.FIELD_DARK;
                    case "golden" -> GrassLayer.FIELD_GOLDEN;
                    case "flowers" -> GrassLayer.FLOWERS;
                    case "erase" -> GrassLayer.NONE;
                    default -> -1;
                };
        if (type < 0) {
            reply("Unknown brush: " + mode, ERROR);
            return;
        }
        int size = parseInt(args, 2, 1);
        int density = parseInt(args, 3, 3);
        if (size < 0 || size > 4 || density < 1 || density > 4) {
            reply("Size is 0 to 4 and density is 1 to 4.", ERROR);
            return;
        }
        gp.buildings.stop();
        gp.grassEditor.start(type, size, density);
        reply("Paint mode: LMB paints, RMB erases. 1-4 brush, [ ] size, , . density.", INFO);
    }

    /** Performs the build operation for CommandProcessor. */
    private void build(String[] args) {
        if (args.length != 2) {
            reply("Usage: /build <element-id|off>", ERROR);
            return;
        }
        if (args[1].equalsIgnoreCase("off")) {
            gp.buildings.stop();
            reply("Build mode off. /savebuild keeps your changes.", INFO);
            return;
        }
        try {
            gp.buildings.start(args[1].toLowerCase(Locale.ROOT));
            reply(
                    "Build: 1 floor, 2 wall, 3 door. LMB place, RMB erase layer. /savebuild to"
                            + " save.",
                    INFO);
        } catch (IllegalArgumentException e) {
            reply("Use a building element ID or off. /list shows element IDs.", ERROR);
        } catch (IllegalStateException e) {
            reply(e.getMessage(), ERROR);
        }
    }

    /** Performs the blueprint operation for CommandProcessor. */
    private void blueprint(String[] args) {
        if (args.length != 4 || !args[1].matches("[a-z][a-z0-9_-]*")) {
            reply("Usage: /blueprint <id> <anchor-col> <anchor-row>", ERROR);
            return;
        }
        if (!gp.buildings.available()) {
            reply("Blueprints require a single-player overworld.", ERROR);
            return;
        }
        try {
            int col = Integer.parseInt(args[2]), row = Integer.parseInt(args[3]);
            String relative = "maps/blueprints/" + args[1] + ".json";
            java.nio.file.Path file = AppPaths.mapFile(relative).toPath();
            building.BuildingBlueprint preset =
                    java.nio.file.Files.isRegularFile(file)
                            ? building.BuildingBlueprint.read(file)
                            : building.BuildingBlueprint.readResource("/" + relative);
            String error = gp.buildings.placeBlueprint(preset, col, row);
            reply(
                    error == null
                            ? "Placed " + preset.name() + ". Use /savebuild to keep it."
                            : error,
                    error == null ? INFO : ERROR);
        } catch (java.io.IOException | IllegalArgumentException e) {
            reply("Could not place blueprint: " + e.getMessage(), ERROR);
        } catch (java.io.UncheckedIOException e) {
            reply("Could not load blueprint artwork: " + e.getMessage(), ERROR);
        }
    }

    /** Saves the build for CommandProcessor. */
    private void saveBuild() {
        boolean ok = gp.buildings.save();
        reply(
                ok
                        ? "Saved buildings for this world."
                        : "Could not save buildings. Use the single-player overworld; see console"
                                + " for file errors.",
                ok ? INFO : ERROR);
    }

    /** Saves the grass for CommandProcessor. */
    private void saveGrass() {
        if (gp.worldConfig.procedural()) {
            reply(
                    "Procedural meadow is seed-generated; authored grass.txt was not changed.",
                    ERROR);
            return;
        }
        boolean ok = gp.grass.save();
        reply(
                ok
                        ? "Saved grass to " + GrassLayer.PATH + "."
                        : "Could not save (see the console).",
                ok ? INFO : ERROR);
    }

    /** Performs the time operation for CommandProcessor. */
    private void time(String[] args) {
        var cycle = gp.lighting.cycle;
        if (args.length < 2) {
            reply(
                    "It is "
                            + cycle.clock()
                            + (gp.lighting.cycleOn ? "." : " (cycle off, use /mood auto)."),
                    INFO);
            return;
        }
        float hour;
        try {
            String[] parts = args[1].split(":");
            hour =
                    Float.parseFloat(parts[0])
                            + (parts.length > 1 ? Float.parseFloat(parts[1]) / 60f : 0f);
        } catch (NumberFormatException e) {
            hour = -1;
        }
        if (hour < 0 || hour >= 24) {
            reply("Usage: /time [hour 0-24, e.g. 18 or 6:30]", ERROR);
            return;
        }
        cycle.setHour(hour);
        gp.lighting.cycleOn = true;
        reply("It is now " + cycle.clock() + ".", INFO);
    }
}
