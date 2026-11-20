package world.memorial;

import art.InteractionOutline;

import com.esotericsoftware.jsonbeans.*;

import entity.*;

import interaction.InteractionTargetResolver;

import main.Gamepanel;

import monster.SlimeMotion;

import net.*;

import object.*;

import ui.UITheme;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Persistent authoritative records; only camera/chunk-visible static actors are materialized. */
public final class DeathMemorials {
    public static final class Record {
        public String id, owner, name;
        public int x, y, originalX, originalY, chunkX, chunkY, day;
        public long timestamp;
        public boolean dungeon, recovered;
        public ArrayList<StoredItem> items = new ArrayList<>();
    }

    public static final class Data {
        public int version = 1;
        public ArrayList<Record> records = new ArrayList<>();
    }

    public static final class Carried {
        public StoredItem[] bag = new StoredItem[20], equipment = new StoredItem[9];
    }

    private final Gamepanel gp;
    private Data data = new Data();
    private final Map<String, Record> byId = new HashMap<>();
    private final Map<Long, ArrayList<Record>> chunks = new HashMap<>();
    private final Map<String, Actor> actors = new HashMap<>();
    private final Set<Player> processed = Collections.newSetFromMap(new IdentityHashMap<>());
    private String cached, openId, netCache, netKey, lastRestored;
    private int page;
    private BufferedImage bagged, bare;

    public DeathMemorials(Gamepanel gp) {
        this.gp = gp;
    }

    /** Concrete collection element types avoid jsonbeans' generic collection serialization trap. */
    private Json json() {
        Json j = new Json(OutputType.json);
        j.setTypeName(null);
        j.setUsePrototypes(false);
        j.setIgnoreUnknownFields(true);
        j.setElementType(Data.class, "records", Record.class);
        j.setElementType(Record.class, "items", StoredItem.class);
        j.setElementType(StoredItem.class, "values", String.class);
        return j;
    }

    private long key(int cx, int cy, boolean area) {
        return ((long) cx << 33) ^ ((long) cy << 1) ^ (area ? 1 : 0);
    }

    private void index(Record r) {
        byId.put(r.id, r);
        chunks.computeIfAbsent(key(r.chunkX, r.chunkY, r.dungeon), k -> new ArrayList<>()).add(r);
    }

    public void reset() {
        data = new Data();
        byId.clear();
        chunks.clear();
        actors.clear();
        processed.clear();
        cached = null;
        lastRestored = null;
        netCache = null;
        close();
    }

    /** Cached only until an inventory/death transaction changes records. */
    public String snapshot() {
        if (cached == null) cached = json().toJson(data);
        return cached;
    }

    /** Network replicas receive nearby records only; the host retains every persistent record. */
    public String networkSnapshot() {
        StringBuilder key = new StringBuilder();
        int width = gp.tileSize * world.chunk.ChunkConfig.SIZE;
        for (Player p : gp.players)
            if (p != null)
                key.append(p.worldX / width)
                        .append(',')
                        .append(p.worldY / width)
                        .append(':')
                        .append(p.inWell)
                        .append(';');
        String at = key.toString();
        if (netCache != null && at.equals(netKey)) return netCache;
        Data view = new Data();
        Set<String> seen = new HashSet<>();
        for (Player p : gp.players)
            if (p != null)
                for (Record r :
                        near(
                                new Rectangle(
                                        p.worldX - width, p.worldY - width, width * 3, width * 3),
                                p.inWell)) if (seen.add(r.id)) view.records.add(r);
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(bytes)) {
                zip.write(json().toJson(view).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            netCache = "Z:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
            netKey = at;
            return netCache;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void restore(String text) {
        if (text == null || text.isBlank()) {
            reset();
            return;
        }
        if (text.equals(cached) || text.equals(lastRestored)) return;
        String decoded = text;
        if (text.startsWith("Z:"))
            try (var zip =
                    new java.util.zip.GZIPInputStream(
                            new java.io.ByteArrayInputStream(
                                    Base64.getDecoder().decode(text.substring(2))))) {
                decoded = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Invalid memorial snapshot", e);
            }
        Data next = json().fromJson(Data.class, decoded);
        if (next == null || next.version != 1)
            throw new IllegalArgumentException("Invalid memorial data");
        HashSet<String> ids = new HashSet<>();
        for (Record r : next.records) {
            if (r.id == null || r.owner == null || !ids.add(r.id))
                throw new IllegalArgumentException("Invalid memorial id");
            for (StoredItem i : r.items) i.restore(gp);
            r.recovered = r.items.isEmpty();
        }
        data = next;
        byId.clear();
        chunks.clear();
        actors.clear();
        for (Record r : data.records) index(r);
        cached = decoded;
        lastRestored = text;
    }

    /**
     * Full rich carried state also protects recovered item metadata in subsequent character saves.
     */
    public String carried(Player p) {
        Carried c = new Carried();
        for (int i = 0; i < c.bag.length; i++)
            if (p.inventorySlots[i] != null) c.bag[i] = StoredItem.capture(p.inventorySlots[i]);
        for (int i = 0; i < 9; i++) {
            Entity e = p.equippedItem(i + 1);
            if (e != null && !e.isPlaceholder) c.equipment[i] = StoredItem.capture(e);
        }
        return json().toJson(c);
    }

    public void restoreCarried(Player p, String text) {
        if (text == null) return;
        Carried c = json().fromJson(Carried.class, text);
        if (c.bag.length != 20 || c.equipment.length != 9)
            throw new IllegalArgumentException("Invalid carried data");
        for (int i = 0; i < 20; i++)
            p.inventorySlots[i] = c.bag[i] == null ? null : c.bag[i].restore(gp);
        p.currentWeapon = c.equipment[0] == null ? new OBJ_fists(gp) : c.equipment[0].restore(gp);
        p.currentShield =
                c.equipment[1] == null ? new OBJ_no_shield(gp) : c.equipment[1].restore(gp);
        for (int i = 0; i < 7; i++)
            p.apparelSlots[i] = c.equipment[i + 2] == null ? null : c.equipment[i + 2].restore(gp);
        p.rightHandItem = p.currentWeapon;
    }

    /**
     * Exactly-once game-thread transfer: validate/capture everything before publishing and
     * clearing.
     */
    public void onDeath(Player p) {
        if (!p.canResolveWorldActions()
                || p.HP > 0
                || processed.contains(p)
                || gp.arena.training
                || p.deathMemorialId != null && byId.containsKey(p.deathMemorialId)) return;
        Record r = new Record();
        r.id = UUID.randomUUID().toString();
        r.owner = p.deathOwnerId;
        r.name = p.playerName;
        r.day = gp.lighting.cycle.day();
        r.timestamp = System.currentTimeMillis();
        r.dungeon = p.inWell;
        r.originalX = SlimeMotion.centerX(p);
        r.originalY = p.sortY() - 1;
        Point at = safePosition(r.originalX, r.originalY, p);
        r.x = at.x;
        r.y = at.y;
        int ts = gp.tileSize, cs = world.chunk.ChunkConfig.SIZE;
        r.chunkX = Math.floorDiv(r.x / ts, cs);
        r.chunkY = Math.floorDiv(r.y / ts, cs);
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity e : p.inventorySlots)
            if (e != null && seen.add(e)) r.items.add(StoredItem.capture(e));
        for (int slot = 1; slot <= 9; slot++) {
            Entity e = p.equippedItem(slot);
            if (e != null && !e.isPlaceholder && seen.add(e)) r.items.add(StoredItem.capture(e));
        }
        r.recovered = r.items.isEmpty();
        data.records.add(r);
        index(r);
        processed.add(p);
        p.deathMemorialId = r.id;
        cached = null;
        lastRestored = null;
        netCache = null;
        Arrays.fill(p.inventorySlots, null);
        Arrays.fill(p.apparelSlots, null);
        p.currentWeapon = new OBJ_fists(gp);
        p.currentShield = new OBJ_no_shield(gp);
        p.rightHandItem = p.currentWeapon;
        for (int i = 0; i < 10; i++) p.hotbar.clear(i);
        p.sword.cancel();
        p.syncWeaponProfile();
        p.refreshWeaponAnimation();
        p.attack = p.getAttack();
        p.defense = p.getDefense();
        if (p.isLocal) close();
    }

    public void respawned(Player p) {
        processed.remove(p);
        p.deathMemorialId = null;
    }

    /** Nearby collision-safe recovery location, retaining original coordinates for the memorial. */
    private Point safePosition(int x, int y, Player player) {
        int ts = gp.tileSize,
                c = Math.clamp(x / ts, 1, gp.maxWorldCol - 2),
                r = Math.clamp(y / ts, 1, gp.maxWorldRow - 2);
        int elevation = gp.tileM.buildingElevation(c, r);
        if (safeCell(c, r, elevation, player)
                && x >= 0
                && y >= 0
                && x < gp.maxWorldCol * ts
                && y < gp.maxWorldRow * ts
                && SlimeMotion.clear(gp, new Rectangle(x - 8, y - 8, 16, 16)))
            return new Point(x, y);
        // Search in increasing tile radius on the same logical elevation; never choose a cliff
        // face.
        for (int radius = 0; radius <= 12; radius++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    if (safeCell(c + dx, r + dy, elevation, player))
                        return new Point((c + dx) * ts + ts / 2, (r + dy) * ts + ts / 2);
                }
        for (int radius = 0; radius < Math.max(gp.maxWorldCol, gp.maxWorldRow); radius++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dx = -radius; dx <= radius; dx++)
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == radius
                            && safeCell(c + dx, r + dy, 0, player))
                        return new Point((c + dx) * ts + ts / 2, (r + dy) * ts + ts / 2);
        throw new IllegalStateException("No safe death recovery location exists");
    }

    /** Requires clear memorial ground plus a same-height adjacent interaction approach. */
    private boolean safeCell(int c, int r, int elevation, Player player) {
        int ts = gp.tileSize;
        if (c < 1
                || r < 1
                || c >= gp.maxWorldCol - 1
                || r >= gp.maxWorldRow - 1
                || gp.tileM.buildingElevation(c, r) != elevation) return false;
        Rectangle body = new Rectangle(c * ts + ts / 2 - 8, r * ts + ts / 2 - 8, 16, 16);
        if (!SlimeMotion.clear(gp, body)) return false;
        int[] dx = {0, 1, 0, -1}, dy = {-1, 0, 1, 0};
        String[] face = {"down", "left", "up", "right"};
        int oldX = player.worldX, oldY = player.worldY;
        String oldDirection = player.direction;
        try {
            Rectangle footprint = new Rectangle(c * ts, r * ts, ts, ts);
            for (int i = 0; i < 4; i++) {
                player.worldX =
                        (c + dx[i]) * ts + ts / 2 - player.solidArea.x - player.solidArea.width / 2;
                player.worldY =
                        (r + dy[i]) * ts
                                + ts / 2
                                - player.solidArea.y
                                - player.solidArea.height
                                + 1;
                player.direction = face[i];
                if (SlimeMotion.clear(gp, SlimeMotion.body(player, player.worldX, player.worldY))
                        && gp.interactions.validator.valid(player, footprint)) return true;
            }
        } finally {
            player.worldX = oldX;
            player.worldY = oldY;
            player.direction = oldDirection;
        }

        return false;
    }

    public boolean covers(Rectangle box) {
        for (Record r : near(box, gp.quests.inDungeon()))
            if (footprint(r).intersects(box)) return true;
        return false;
    }

    private Rectangle footprint(Record r) {
        int t = gp.tileSize;
        return new Rectangle(Math.floorDiv(r.x, t) * t, Math.floorDiv(r.y, t) * t, t, t);
    }

    private List<Record> near(Rectangle box, boolean area) {
        ArrayList<Record> found = new ArrayList<>();
        int size = gp.tileSize * world.chunk.ChunkConfig.SIZE;
        for (int y = Math.floorDiv(box.y, size); y <= Math.floorDiv(box.y + box.height, size); y++)
            for (int x = Math.floorDiv(box.x, size);
                    x <= Math.floorDiv(box.x + box.width, size);
                    x++) {
                var list = chunks.get(key(x, y, area));
                if (list != null) found.addAll(list);
            }
        return found;
    }

    public void collectTargets(InteractionTargetResolver resolver) {
        Player p = resolver.player();
        for (Record r : near(resolver.validator.frontBounds(p), p.inWell))
            if (!r.recovered && r.owner.equals(p.deathOwnerId))
                resolver.offer(
                        r,
                        "memorial:" + r.id,
                        footprint(r),
                        5,
                        () -> {
                            if (p.isLocal) {
                                openId = r.id;
                                page = 0;
                            }
                        });
    }

    /**
     * Opens the local recovery window in either solo or network play; transfers remain
     * server-owned.
     */
    public boolean openTarget(Player p) {
        var target = gp.interactions.resolve(p);
        if (target == null || !(target.token() instanceof Record record)) return false;
        openId = record.id;
        page = 0;
        return true;
    }

    /** The standard outline gains a concise recovery hint when the same target is selected. */
    public void drawHint(Graphics2D g) {
        if (open() || !(gp.interactions.token() instanceof Record record)) return;
        UITheme.text(
                g,
                "[F] Recover lost belongings · Day " + record.day,
                gp.screenWidth / 2 - 165,
                gp.screenHeight - 118,
                14,
                UITheme.TEXT);
    }

    /** Records persist forever; only visible actor wrappers are cached while chunks are active. */
    public void addDrawLayers(List<Entity> layers) {
        Rectangle camera = gp.chunks.camera(2);
        Set<String> visible = new HashSet<>();
        for (Record r : near(camera, gp.quests.inDungeon()))
            if (camera.intersects(footprint(r)) && gp.chunks.activeAt(r.x, r.y)) {
                visible.add(r.id);
                layers.add(actors.computeIfAbsent(r.id, k -> new Actor(r.id)));
            }
        actors.keySet().retainAll(visible);
    }

    private void loadArt() {
        if (bagged != null) return;
        try {
            BufferedImage atlas =
                    javax.imageio.ImageIO.read(
                            DeathMemorials.class.getResourceAsStream(
                                    "/scenery/memorial/death-piles.png"));
            int half = atlas.getWidth() / 2;
            bagged = atlas.getSubimage(0, 0, half, atlas.getHeight());
            bare = atlas.getSubimage(half, 0, half, atlas.getHeight());
        } catch (Exception e) {
            throw new IllegalStateException("Missing death memorial sprites", e);
        }
    }

    public void preload() {
        loadArt();
    }

    private final class Actor extends Entity {
        private final String id;

        Actor(String id) {
            super(DeathMemorials.this.gp);
            this.id = id;
            Record r = byId.get(id);
            worldX = r.x - gp.tileSize / 2;
            worldY = r.y - gp.tileSize;
            solidArea.setBounds(0, gp.tileSize, gp.tileSize, 1);
        }

        public void draw(Graphics2D g) {
            Record r = byId.get(id);
            if (r == null) return;
            Player cam = gp.localPlayer();
            int w = gp.tileSize * 3 / 2, h = w;
            int x = r.x - w / 2 - cam.worldX + cam.screenX,
                    y = r.y - Math.round(h * (134f / 144)) - cam.worldY + cam.screenY;
            BufferedImage img = r.recovered ? bare : bagged;
            if (!r.recovered && gp.interactions.selected(r)) {
                var old = g.getTransform();
                g.translate(x, y);
                g.scale(w / (double) img.getWidth(), h / (double) img.getHeight());
                InteractionOutline.draw(g, img, 0, 0);
                g.setTransform(old);
            }
            g.drawImage(img, x, y, w, h, null);
        }
    }

    public boolean open() {
        return openId != null;
    }

    public void close() {
        if (openId != null && gp.mouseH != null) {
            gp.mouseH.leftClicked = false;
            gp.mouseH.leftPressed = false;
        }
        openId = null;
        page = 0;
    }

    private Rectangle panel() {
        return new Rectangle((gp.screenWidth - 540) / 2, (gp.screenHeight - 470) / 2, 540, 470);
    }

    /**
     * Authoritative retrieval validates owner, adjacency and the live bag before every transfer.
     */
    public void recover(Player p, String id, int itemIndex) {
        Record r = byId.get(id);
        if (r == null
                || r.recovered
                || p.isDead
                || !r.owner.equals(p.deathOwnerId)
                || p.inWell != r.dungeon
                || !gp.interactions.accepts(p, footprint(r), r)) return;
        int first = itemIndex < 0 ? 0 : itemIndex,
                last = itemIndex < 0 ? r.items.size() : itemIndex + 1;
        if (first < 0 || last > r.items.size()) return;
        for (int i = last - 1; i >= first; i--) {
            StoredItem saved = r.items.get(i);
            Entity e = saved.restore(gp);
            if (e.stackable) {
                for (Entity owned : p.inventorySlots) {
                    if (owned == null
                            || ItemRegistry.typeFor(owned) != saved.type
                            || !owned.canStackWith(e)) continue;
                    StoredItem existing = StoredItem.capture(owned);
                    if (!existing.values.equals(saved.values)) continue;
                    int amount =
                            Math.min(
                                    saved.count,
                                    Math.max(0, owned.maxStackSize - owned.stackCount));
                    if (amount > 0) {
                        owned.stackCount += amount;
                        saved.count -= amount;
                        cached = null;
                        lastRestored = null;
                        netCache = null;
                    }
                    if (saved.count == 0) break;
                }
            }
            if (saved.count == 0) {
                r.items.remove(i);
                continue;
            }
            int free = -1;
            for (int slot = 0; slot < p.inventorySlots.length; slot++)
                if (p.inventorySlots[slot] == null) {
                    free = slot;
                    break;
                }
            if (free < 0) continue;
            e.stackCount = saved.count;
            p.inventorySlots[free] = e;
            r.items.remove(i);
            cached = null;
            lastRestored = null;
            netCache = null;
        }
        r.recovered = r.items.isEmpty();
    }

    /** Stable item identity makes repeated individual retrieval requests idempotent. */
    public void recoverRequest(Player p, String memorial, String itemId, int index) {
        Record r = byId.get(memorial);
        if (r == null) return;
        if (index >= 0) {
            index = -2;
            for (int i = 0; i < r.items.size(); i++)
                if (r.items.get(i).id.equals(itemId)) {
                    index = i;
                    break;
                }
            if (index < 0) return;
        }
        recover(p, memorial, index);
    }

    private void request(int index) {
        net.InventoryAction action = new net.InventoryAction();
        action.playerId = gp.localPlayerIndex;
        action.actionType = net.InventoryActionType.RECOVER_MEMORIAL;
        action.memorialId = openId;
        action.sourceIndex = index;
        Record record = byId.get(openId);
        if (index >= 0 && record != null && index < record.items.size())
            action.memorialItemId = record.items.get(index).id;
        gp.requestInventoryAction(action);
    }

    /** Compact paged recovery UI; overlapping graves can be cycled without changing the target. */
    public boolean updateUI(int mx, int my, boolean click) {
        if (!open()) return false;
        Player p = gp.localPlayer();
        Record r = byId.get(openId);
        if (r == null || p.isDead || !gp.interactions.accepts(p, footprint(r), r)) {
            close();
            return false;
        }
        if (!click) return true;
        Rectangle b = panel();
        if (!b.contains(mx, my)) return true;
        int x = mx - b.x, y = my - b.y;
        if (y >= 425 && y < 460) {
            if (x < 180) request(-1);
            else if (x < 360) {
                page = (page + 1) % Math.max(1, (r.items.size() + 9) / 10);
            } else close();
        } else if (y >= 85 && y < 385) {
            int index = page * 10 + (y - 85) / 30;
            if (index < r.items.size()) request(index);
        } else if (y >= 40 && y < 75) {
            var candidates =
                    near(footprint(r), r.dungeon).stream()
                            .filter(v ->
                                    !v.recovered
                                            && v.owner.equals(p.deathOwnerId)
                                            && footprint(v).equals(footprint(r)))
                            .toList();
            if (candidates.size() > 1)
                openId = candidates.get((candidates.indexOf(r) + 1) % candidates.size()).id;
            page = 0;
        }
        return true;
    }

    public void drawUI(Graphics2D g) {
        Record r = byId.get(openId);
        if (r == null) return;
        Rectangle b = panel();
        UITheme.panel(g, b);
        UITheme.text(g, "LOST BELONGINGS", b.x + 20, b.y + 30, 19, UITheme.TEXT);
        UITheme.text(
                g,
                "Day " + r.day + " · " + r.id.substring(0, 8) + " · Click to cycle nearby bags",
                b.x + 20,
                b.y + 61,
                13,
                UITheme.MUTED);
        for (int row = 0; row < 10; row++) {
            int index = page * 10 + row;
            if (index >= r.items.size()) break;
            StoredItem s = r.items.get(index);
            UITheme.text(
                    g,
                    s.label + "   ×" + s.count + "    [Take]",
                    b.x + 22,
                    b.y + 108 + row * 30,
                    14,
                    UITheme.TEXT);
        }
        UITheme.text(
                g,
                r.recovered
                        ? "Belongings recovered. These bones remain here forever."
                        : "Click an item to retrieve it. Full inventory leaves items here.",
                b.x + 20,
                b.y + 405,
                12,
                UITheme.MUTED);
        UITheme.text(
                g,
                "[Take All]                 [Next Page]                 [Close]",
                b.x + 20,
                b.y + 450,
                15,
                UITheme.ACCENT);
    }
}
