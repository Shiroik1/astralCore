package world;

import art.ArtKit;
import art.GrassArt;
import art.InteractionOutline;
import art.Terrain;

import entity.Entity;
import entity.Player;

import main.Gamepanel;
import main.Log;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.IdentityHashMap;
import java.util.Map;

public class GrassLayer {
    public static final int NONE = 0, FLOWERS = 3; // retired short/tall ids 1 and 2 are ignored
    public static final int FIELD_GREEN = 4, FIELD_DARK = 5, FIELD_GOLDEN = 6;
    public static final String PATH = "maps/grass.txt";
    private static final String RESOURCE = "/maps/grass.txt";
    private static final int MAX_PER_TILE = 5;
    private static final int[] COUNT = {0, 1, 1, 2, 2}; // tufts per tile for density 0..4

    private final Gamepanel gp;
    private final GrassArt art;
    private int cols, rows;
    private byte[][] cells; // 0 = empty, otherwise (type - 1) * 4 + density
    private float[][] energy; // wobble left behind by something that moved through a tile
    private short[] tuftX, tuftY; // tuft positions inside their tile, in pixels
    private int[] tuftHash;
    private byte[][] decorativeDensity;
    private Entity[] depthTufts;
    private final java.util.Set<Integer> stirred = new java.util.HashSet<>();
    private long time = 0;

    // creatures pushing the grass this tick
    private final float[] px = new float[64], py = new float[64], pr = new float[64];
    private int pushers = 0;
    private Map<Entity, int[]> lastPos = new IdentityHashMap<>();

    // short-lived radial pushes (attacks, slams): x, y, radius, strength, ticks left
    private final float[][] bursts = new float[16][5];
    private int burstCount = 0;

    /** Creates a GrassLayer instance and initializes the state required by its collaborators. */
    public GrassLayer(Gamepanel gp) {
        this.gp = gp;
        this.art = new GrassArt(gp.tileSize);
    }

    // call once the terrain and scenery are loaded
    /** Performs the load operation for GrassLayer. */
    public void load() {
        cols = gp.maxWorldCol;
        rows = gp.maxWorldRow;
        cells = new byte[cols][rows];
        energy = new float[cols][rows];
        buildTufts();

        time = 0;
        burstCount = pushers = 0;
        lastPos.clear();
        stirred.clear();
        if (gp.generatedWorld != null) {
            for (int c = 0; c < cols; c++)
                for (int r = 0; r < rows; r++) cells[c][r] = gp.generatedWorld.meadow(c, r);
            rebuildDepthTufts();
            return;
        }

        if (gp.arena.training) {
            rebuildDepthTufts();
            return;
        }
        String text = readText();
        if (text == null) {
            // Ordinary grass is now part of the terrain textures, including authored worlds.
            save();
            Log.warn("[GRASS] no grass.txt found: saved empty field layer to " + PATH);
        } else {
            parse(text);
        }
        rebuildDepthTufts();
    }

    // EDITING
    /** Performs the paint operation for GrassLayer. */
    public void paint(int col, int row, int type, int density) {
        if (cells == null || col < 0 || row < 0 || col >= cols || row >= rows) return;
        if (type == NONE) {
            cells[col][row] = 0;
            return;
        }
        if (type < FLOWERS || type > FIELD_GOLDEN || density < 1 || density > 4) return;
        if (!supportsGrass(col, row)) return;
        cells[col][row] = (byte) ((type - 1) * 4 + density);
        refreshDepthCell(col, row);
    }

    /** Existing green, dark and golden field grass supplies Fiber; flowers remain decorative. */
    public boolean isHarvestable(int col, int row) {
        return cells != null
                && col >= 0
                && row >= 0
                && col < cols
                && row < rows
                && cells[col][row] > 12
                && !gp.buildings.coversGround(col, row);
    }

    /** Evaluates the save condition for GrassLayer. */
    public boolean save() {
        if (gp.worldConfig.procedural()) return false;
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                sb.append(cells[c][r] == 0 ? '.' : (char) ('a' + cells[c][r] - 1));
            }
            sb.append('\n');
        }
        try {
            main.AppPaths.createParent(main.AppPaths.mapFile(PATH).toPath());
            Files.writeString(
                    main.AppPaths.mapFile(PATH).toPath(), sb.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // A burst of wind: attacks, slams
    /** Performs the burst operation for GrassLayer. */
    public void burst(float x, float y, float radius, float strength) {
        if (cells == null || burstCount >= bursts.length) return;
        float[] b = bursts[burstCount++];
        b[0] = x;
        b[1] = y;
        b[2] = radius;
        b[3] = strength;
        b[4] = 18;
        stir(x, y, radius + 20, 1f);
    }

    // UPDATE
    /** Advances this component for one simulation tick. */
    public void update() {
        if (cells == null) return;
        time++;
        collectPushers();

        var iterator = stirred.iterator();
        while (iterator.hasNext()) {
            int key = iterator.next(), c = key % cols, r = key / cols;
            energy[c][r] *= .93f;
            if (energy[c][r] < .01f) {
                energy[c][r] = 0;
                iterator.remove();
            }
        }

        for (int i = burstCount - 1; i >= 0; i--) {
            bursts[i][4]--;
            if (bursts[i][4] <= 0) {
                float[] t = bursts[i];
                bursts[i] = bursts[burstCount - 1];
                bursts[burstCount - 1] = t;
                burstCount--;
            }
        }
    }

    /** Collects the pushers for GrassLayer. */
    private void collectPushers() {
        Player cam = gp.localPlayer();
        pushers = 0;
        Map<Entity, int[]> now = new IdentityHashMap<>();
        if (cam != null) {
            for (Player p : gp.players) addPusher(p, cam, now);
            for (Entity m : gp.monster) addPusher(m, cam, now);
            for (Entity n : gp.npc) addPusher(n, cam, now);
        }
        lastPos = now;
    }

    /** Adds the pusher for GrassLayer. */
    private void addPusher(Entity e, Player cam, Map<Entity, int[]> now) {
        if (e == null || e.dying || pushers >= px.length) return;
        if (e instanceof Player p && p.isDead) return;

        float fx = e.worldX + e.solidArea.x + e.solidArea.width / 2f;
        float fy = e.worldY + e.solidArea.y + e.solidArea.height - 6;
        if (Math.abs(fx - cam.worldX) > 900 || Math.abs(fy - cam.worldY) > 600) return;

        int[] prev = lastPos.get(e);
        boolean moved =
                prev != null && (Math.abs(prev[0] - e.worldX) + Math.abs(prev[1] - e.worldY)) > 0;
        now.put(e, new int[] {e.worldX, e.worldY});

        float radius = e.solidArea.width / 2f + 16;
        px[pushers] = fx;
        py[pushers] = fy;
        pr[pushers] = radius;
        pushers++;

        if (moved) stir(fx, fy, radius + 10, 0.9f);
    }

    /** Performs the stir operation for GrassLayer. */
    private void stir(float x, float y, float radius, float amount) {
        int ts = gp.tileSize;
        int c0 = Math.max(0, Math.floorDiv((int) (x - radius), ts));
        int c1 = Math.min(cols - 1, Math.floorDiv((int) (x + radius), ts));
        int r0 = Math.max(0, Math.floorDiv((int) (y - radius), ts));
        int r1 = Math.min(rows - 1, Math.floorDiv((int) (y + radius), ts));
        for (int c = c0; c <= c1; c++) {
            for (int r = r0; r <= r1; r++) {
                energy[c][r] = Math.max(energy[c][r], amount);
                stirred.add(r * cols + c);
            }
        }
    }

    // DRAWING
    // everything on the ground, before any entity
    /** Draws the base for GrassLayer. */
    public void drawBase(Graphics2D g2) {
        if (cells == null) return;
        Player cam = gp.localPlayer();
        if (cam == null) return;

        int ts = gp.tileSize;
        int left = cam.worldX - cam.screenX, top = cam.worldY - cam.screenY;
        int c0 = Math.max(0, Math.floorDiv(left, ts) - 1);
        int c1 = Math.min(cols - 1, Math.floorDiv(left + gp.screenWidth, ts) + 1);
        int r0 = Math.max(0, Math.floorDiv(top, ts) - 1);
        int r1 = Math.min(rows - 1, Math.floorDiv(top + gp.screenHeight, ts) + 2);

        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                if (gp.buildings.coversGround(c, r)) continue;
                drawShortGrass(g2, cam, c, r);
                int v = cells[c][r];
                if (v <= 8) continue;
                int type = (v - 1) / 4;
                if (type >= 3) continue; // body-height grass participates in the shared depth sort
                int level = gp.tileM.level(c, r);
                int count = COUNT[(v - 1) % 4 + 1];
                for (int i = 0; i < count; i++) {
                    drawTuft(g2, cam, c, r, i, type, level);
                }
            }
        }
    }

    // the tufts in front of a creature's feet, drawn again on top of it so it walks THROUGH the
    // grass
    /** Draws the front for GrassLayer. */
    public void drawFront(Graphics2D g2, Entity e) {
        if (cells == null) return;
        if (e.type != Entity.type_player
                && e.type != Entity.type_monster
                && e.type != Entity.type_npc) return;
        Player cam = gp.localPlayer();
        if (cam == null) return;

        int ts = gp.tileSize;
        int fx = e.worldX + e.solidArea.x + e.solidArea.width / 2;
        int fy = e.sortY();
        int c0 = Math.max(0, Math.floorDiv(fx - 36, ts)),
                c1 = Math.min(cols - 1, Math.floorDiv(fx + 36, ts));
        int r0 = Math.max(0, Math.floorDiv(fy, ts)),
                r1 = Math.min(rows - 1, Math.floorDiv(fy + 26, ts));

        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                if (gp.buildings.coversGround(c, r)) continue;
                if (e.type == Entity.type_player) drawShortGrass(g2, cam, c, r, e);
                int v = cells[c][r];
                if (v <= 8) continue;
                int type = (v - 1) / 4;
                if (type >= 3) continue;
                int level = gp.tileM.level(c, r);
                int count = COUNT[(v - 1) % 4 + 1];
                int base = (r * cols + c) * MAX_PER_TILE;
                for (int i = 0; i < count; i++) {
                    int wx = c * ts + tuftX[base + i];
                    int wy = r * ts + tuftY[base + i];
                    if (wy <= fy || wy > fy + 26 || Math.abs(wx - fx) > 34) continue;
                    drawTuft(g2, cam, c, r, i, type, level);
                }
            }
        }
    }

    /** Seeded ground decoration is drawn only in view and never participates in harvesting. */
    private void drawShortGrass(Graphics2D g, Player camera, int c, int r) {
        drawShortGrass(g, camera, c, r, null);
    }

    /** Replays only tufts rooted immediately in front of feet after the player is drawn. */
    private void drawShortGrass(Graphics2D g, Player camera, int c, int r, Entity front) {
        if (!supportsGrass(c, r) || cells[c][r] > 12) return;
        int base = (r * cols + c) * MAX_PER_TILE;
        int count = decorativeDensity[c][r];
        if (count == 0) return;
        boolean golden = false;
        for (int y = Math.max(0, r - 3); y <= Math.min(rows - 1, r + 3) && !golden; y++)
            for (int x = Math.max(0, c - 3); x <= Math.min(cols - 1, c + 3); x++)
                if (cells[x][y] >= 21 && cells[x][y] <= 24) {
                    golden = true;
                    break;
                }
        for (int i = 0; i < count; i++) {
            int index = base + i % MAX_PER_TILE, hash = tuftHash[index];
            // Jitter stays inside each planting pocket: dense coverage without visible rows.
            float jitterX = (((hash + i * 179) % 101) / 100f - .5f) * .65f;
            float jitterY = (((hash / 101 + i * 53) % 101) / 100f - .5f) * .65f;
            float wx = c * gp.tileSize + ((i % 3) + .5f + jitterX) * gp.tileSize / 3;
            float wy = r * gp.tileSize + ((i / 3) + .5f + jitterY) * gp.tileSize / 3;
            if (front != null) {
                int feetX = front.worldX + front.solidArea.x + front.solidArea.width / 2;
                int feetY = front.sortY();
                if (wy <= feetY
                        || wy > feetY + gp.tileSize * .28f
                        || Math.abs(wx - feetX) > front.solidArea.width * .65f) continue;
            }
            int size = Math.round(gp.tileSize * (.80f + ((hash + i * 31) % 97) / 96f * .30f));
            int bend = bendIndex(c, r, wx, wy, hash);
            g.drawImage(
                    art.shortGrass(golden ? 2 : (hash / 100) % 2, gp.tileM.level(c, r), bend),
                    (int) wx - size / 2 - camera.worldX + camera.screenX,
                    (int) wy - Math.round(size * 44f / 48) - camera.worldY + camera.screenY,
                    size,
                    size,
                    null);
        }
    }

    /** Draws the tuft for GrassLayer. */
    private void drawTuft(Graphics2D g2, Player cam, int c, int r, int i, int type, int level) {
        if (type >= 3 && gp.crafting.grassHarvested(c, r)) return;
        int idx = (r * cols + c) * MAX_PER_TILE + i;
        int ts = gp.tileSize;
        float wx = c * ts + tuftX[idx];
        float wy = r * ts + tuftY[idx];
        int hash = tuftHash[idx];
        int bend = bendIndex(c, r, wx, wy, hash);
        BufferedImage img = art.frame(type, level, (hash >>> 3) % GrassArt.VARIANTS[type], bend);
        int sx = (int) wx - art.footX(type) - cam.worldX + cam.screenX,
                sy = (int) wy - art.footY(type) - cam.worldY + cam.screenY;
        if (type >= 3 && gp.crafting.nearTallGrass(c, r)) InteractionOutline.draw(g2, img, sx, sy);
        g2.drawImage(img, sx, sy, null);
    }

    /** Performs the rebuild depth tufts operation for GrassLayer. */
    private void rebuildDepthTufts() {
        depthTufts = new Entity[cols * rows * MAX_PER_TILE];
        // Tuft entities are materialized only for visible cells.
    }

    /** Performs the release chunk operation for GrassLayer. */
    public void releaseChunk(int x, int y) {
        if (depthTufts == null) return;
        int size = world.chunk.ChunkConfig.SIZE;
        for (int r = y * size; r < Math.min(rows, (y + 1) * size); r++)
            for (int c = x * size; c < Math.min(cols, (x + 1) * size); c++)
                java.util.Arrays.fill(
                        depthTufts,
                        (r * cols + c) * MAX_PER_TILE,
                        (r * cols + c + 1) * MAX_PER_TILE,
                        null);
    }

    /** Refreshes the depth cell for GrassLayer. */
    private void refreshDepthCell(int c, int r) {
        if (depthTufts == null || cells[c][r] <= 12) return;
        int base = (r * cols + c) * MAX_PER_TILE;
        for (int i = 0; i < COUNT[(cells[c][r] - 1) % 4 + 1]; i++)
            if (depthTufts[base + i] == null) depthTufts[base + i] = new FieldTuft(c, r, i);
    }

    /**
     * Allocate newly visible depth actors at the game-thread update boundary, not during rendering.
     */
    public void prepareView() {
        Player camera = gp.localPlayer();
        if (camera == null || depthTufts == null) return;
        int ts = gp.tileSize,
                left = camera.worldX - camera.screenX,
                top = camera.worldY - camera.screenY;
        int c0 = Math.max(0, Math.floorDiv(left, ts) - 2),
                c1 = Math.min(cols - 1, Math.floorDiv(left + gp.screenWidth, ts) + 2);
        int r0 = Math.max(0, Math.floorDiv(top, ts) - 1),
                r1 = Math.min(rows - 1, Math.floorDiv(top + gp.screenHeight, ts) + 3);
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (cells[c][r] > 12
                        && !gp.buildings.coversGround(c, r)
                        && !gp.crafting.grassHarvested(c, r)) refreshDepthCell(c, r);
    }

    /** Existing shared entity depth sort puts tall grass in front of bodies, behind nearer feet. */
    public void addDrawLayers(java.util.List<Entity> layers) {
        Player camera = gp.localPlayer();
        if (camera == null || depthTufts == null) return;
        int ts = gp.tileSize,
                left = camera.worldX - camera.screenX,
                top = camera.worldY - camera.screenY;
        int c0 = Math.max(0, Math.floorDiv(left, ts) - 2),
                c1 = Math.min(cols - 1, Math.floorDiv(left + gp.screenWidth, ts) + 2);
        int r0 = Math.max(0, Math.floorDiv(top, ts) - 1),
                r1 = Math.min(rows - 1, Math.floorDiv(top + gp.screenHeight, ts) + 3);
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++) {
                if (gp.buildings.coversGround(c, r)) continue;
                int value = cells[c][r];
                if (value <= 12 || gp.crafting.grassHarvested(c, r)) continue;
                int base = (r * cols + c) * MAX_PER_TILE, count = COUNT[(value - 1) % 4 + 1];
                for (int i = 0; i < count; i++)
                    if (depthTufts[base + i] != null) layers.add(depthTufts[base + i]);
            }
    }

    private final class FieldTuft extends Entity {
        private final int col, row, index, base;

        /** Creates a FieldTuft instance and initializes the state required by its collaborators. */
        FieldTuft(int col, int row, int index) {
            super(GrassLayer.this.gp);
            this.col = col;
            this.row = row;
            this.index = index;
            worldX = col * gp.tileSize;
            worldY = row * gp.tileSize;
            type = type_none;
            base = (row * cols + col) * MAX_PER_TILE + index;
        }

        /** Resolves the sort y result used by FieldTuft. */
        @Override
        public int sortY() {
            return row * gp.tileSize + tuftY[base];
        }

        /** Renders this component into the supplied Java2D graphics context. */
        @Override
        public void draw(Graphics2D g) {
            Player camera = gp.localPlayer();
            if (camera == null) return;
            drawTuft(
                    g,
                    camera,
                    col,
                    row,
                    index,
                    (cells[col][row] - 1) / 4,
                    gp.tileM.level(col, row));
        }
    }

    // wind sway + lingering wobble + creatures + bursts, quantised to one of the baked lean frames
    /** Resolves the bend index result used by GrassLayer. */
    private int bendIndex(int c, int r, float wx, float wy, int hash) {
        double phase = (hash & 255) * 0.012;
        double wind = gp.ambient.wind;
        double wave = Math.sin(time * 0.047 - wx * 0.013 - wy * 0.008);
        double flutter = Math.sin(time * 0.081 + phase - wx * 0.020);
        double lean = wave * (0.15 + 0.20 * wind) + flutter * 0.07 + (wind - 0.35) * 0.25;

        float e = energy[c][r];
        if (e > 0) lean += Math.sin(time * 0.22 + phase * 3) * e * 0.34;

        for (int k = 0; k < pushers; k++) {
            float dx = wx - px[k];
            if (dx > pr[k] || dx < -pr[k]) continue;
            float dy = wy - py[k];
            if (dy > pr[k] || dy < -pr[k]) continue;
            float d2 = dx * dx + dy * dy;
            if (d2 >= pr[k] * pr[k]) continue;
            double f = 1 - Math.sqrt(d2) / pr[k];
            double dir = Math.clamp(dx / (pr[k] * 0.25), -1, 1);
            lean += dir * f * f * (3 - 2 * f) * 1.1;
        }

        for (int k = 0; k < burstCount; k++) {
            float[] b = bursts[k];
            float dx = wx - b[0], dy = wy - b[1];
            float d2 = dx * dx + dy * dy;
            if (d2 >= b[2] * b[2]) continue;
            double f = (1 - Math.sqrt(d2) / b[2]) * b[3] * Math.min(1f, b[4] / 10f);
            lean += Math.clamp(dx / (b[2] * 0.2), -1, 1) * f * 1.6;
        }

        lean = Math.max(-1, Math.min(1, lean));
        return (int) Math.round((lean + 1) * 0.5 * (GrassArt.BEND_FRAMES - 1));
    }

    // SETUP
    // the tuft positions are a pure function of the tile, so painting only ever changes a tile's
    // density
    /** Builds the tufts for GrassLayer. */
    private void buildTufts() {
        int n = cols * rows * MAX_PER_TILE;
        tuftX = new short[n];
        tuftY = new short[n];
        tuftHash = new int[n];
        decorativeDensity = new byte[cols][rows];
        int ts = gp.tileSize;
        world.generation.NoiseGenerator seeded =
                gp.generatedWorld == null
                        ? null
                        : new world.generation.NoiseGenerator(gp.worldConfig.seed());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int base = (r * cols + c) * MAX_PER_TILE;
                // A filled radial patch has broad irregular lobes, with no isolated outside tufts.
                int regionX = c / 40, regionY = r / 40;
                double centerX =
                        regionX * 40 + 20 + (tuftRandom(seeded, 941, regionX, regionY) - .5) * 4;
                double centerY =
                        regionY * 40 + 20 + (tuftRandom(seeded, 942, regionX, regionY) - .5) * 4;
                double radiusX = 11 + tuftRandom(seeded, 943, regionX, regionY) * 3;
                double radiusY = 11 + tuftRandom(seeded, 944, regionX, regionY) * 3;
                double nx = (c + .5 - centerX) / radiusX, ny = (r + .5 - centerY) / radiusY;
                double angle = Math.atan2(ny, nx);
                double phase = tuftRandom(seeded, 945, regionX, regionY) * Math.PI * 2;
                double edge =
                        1
                                + .15 * Math.sin(angle * 3 + phase)
                                + .08 * Math.sin(angle * 5 - phase * .7)
                                + .035 * Math.sin(angle * 9 + phase);
                // Taper towards irregular edges while retaining a solid, dense heart.
                double radius = Math.hypot(nx, ny) / edge;
                decorativeDensity[c][r] = (byte) (radius > 1 ? 0 : radius > .88 ? 7 : 9);
                for (int i = 0; i < MAX_PER_TILE; i++) {
                    tuftX[base + i] = (short) (4 + tuftRandom(seeded, 401 + i, c, r) * (ts - 8));
                    tuftY[base + i] = (short) (8 + tuftRandom(seeded, 433 + i, c, r) * (ts - 12));
                    tuftHash[base + i] = (int) (tuftRandom(seeded, 467 + i, c, r) * 1_000_000);
                }
                for (int a = 1;
                        a < MAX_PER_TILE;
                        a++) { // sort by y: lower tufts draw later, in front
                    for (int b = a; b > 0 && tuftY[base + b - 1] > tuftY[base + b]; b--) {
                        swap(base + b, base + b - 1);
                    }
                }
            }
        }
    }

    /** Resolves the tuft random result used by GrassLayer. */
    private static double tuftRandom(
            world.generation.NoiseGenerator noise, int channel, int col, int row) {
        return noise == null ? ArtKit.hash01(channel, col, row) : noise.random(col, row, channel);
    }

    /** Performs the swap operation for GrassLayer. */
    private void swap(int a, int b) {
        short sx = tuftX[a];
        tuftX[a] = tuftX[b];
        tuftX[b] = sx;
        short sy = tuftY[a];
        tuftY[a] = tuftY[b];
        tuftY[b] = sy;
        int h = tuftHash[a];
        tuftHash[a] = tuftHash[b];
        tuftHash[b] = h;
    }

    /** Reads the text for GrassLayer. */
    private String readText() {
        File f = main.AppPaths.mapFile(PATH);
        try {
            if (f.isFile()) return Files.readString(f.toPath(), StandardCharsets.UTF_8);
            try (InputStream is = GrassLayer.class.getResourceAsStream(RESOURCE)) {
                if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Performs the parse operation for GrassLayer. */
    private void parse(String text) {
        String[] lines = text.split("\\R");
        for (int r = 0; r < rows && r < lines.length; r++) {
            String line = lines[r];
            for (int c = 0; c < cols && c < line.length(); c++) {
                char ch = line.charAt(c);
                cells[c][r] =
                        (byte) ((supportsGrass(c, r) && ch >= 'i' && ch <= 'x') ? ch - 'a' + 1 : 0);
            }
        }
    }

    /** Evaluates the supports grass condition for GrassLayer. */
    private boolean supportsGrass(int col, int row) {
        Terrain terrain = gp.tileM.terrain(col, row);
        return terrain == Terrain.GRASS || terrain == Terrain.TERRACE;
    }
}
