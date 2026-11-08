package world;

import art.ArtKit;
import art.GrassArt;
import art.Terrain;
import entity.Entity;
import entity.Player;
import main.Gamepanel;

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
    public static final int NONE = 0, SHORT = 1, TALL = 2, FLOWERS = 3;
    public static final String PATH = "maps/grass.txt";
    private static final String RESOURCE = "/maps/grass.txt";
    private static final int MAX_PER_TILE = 5;
    private static final int[] COUNT = {0, 1, 2, 3, 5};   // tufts per tile for density 0..4

    private final Gamepanel gp;
    private final GrassArt art;
    private int cols, rows;
    private byte[][] cells;        // 0 = empty, otherwise (type - 1) * 4 + density
    private float[][] energy;      // wobble left behind by something that moved through a tile
    private short[] tuftX, tuftY;  // tuft positions inside their tile, in pixels
    private int[] tuftHash;
    private long time = 0;

    // creatures pushing the grass this tick
    private final float[] px = new float[64], py = new float[64], pr = new float[64];
    private int pushers = 0;
    private Map<Entity, int[]> lastPos = new IdentityHashMap<>();

    // short-lived radial pushes (attacks, slams): x, y, radius, strength, ticks left
    private final float[][] bursts = new float[16][5];
    private int burstCount = 0;

    public GrassLayer(Gamepanel gp){
        this.gp = gp;
        this.art = new GrassArt(gp.tileSize);
    }

    // call once the terrain and scenery are loaded
    public void load(){
        cols = gp.maxWorldCol;
        rows = gp.maxWorldRow;
        cells = new byte[cols][rows];
        energy = new float[cols][rows];
        buildTufts();

        String text = readText();
        if(text == null){
            paintDefault();
            save();
            System.out.println("[GRASS] no grass.txt found: painted the default and saved " + PATH);
        } else {
            parse(text);
        }
    }

    //EDITING
    public void paint(int col, int row, int type, int density){
        if(cells == null || col < 0 || row < 0 || col >= cols || row >= rows) return;
        if(type == NONE){
            cells[col][row] = 0;
            return;
        }
        if(gp.tileM.terrain(col, row) != Terrain.GRASS) return; // no grass on roads, plazas or the deep forest
        cells[col][row] = (byte) ((type - 1) * 4 + density);
    }

    public boolean save(){
        StringBuilder sb = new StringBuilder();
        for(int r = 0; r < rows; r++){
            for(int c = 0; c < cols; c++){
                sb.append(cells[c][r] == 0 ? '.' : (char) ('a' + cells[c][r] - 1));
            }
            sb.append('\n');
        }
        try{
            main.AppPaths.createParent(main.AppPaths.mapFile(PATH).toPath());
            Files.writeString(main.AppPaths.mapFile(PATH).toPath(), sb.toString(), StandardCharsets.UTF_8);
            return true;
        } catch(IOException e){
            e.printStackTrace();
            return false;
        }
    }

    //A burst of wind: attacks, slams
    public void burst(float x, float y, float radius, float strength){
        if(cells == null || burstCount >= bursts.length) return;
        float[] b = bursts[burstCount++];
        b[0] = x;
        b[1] = y;
        b[2] = radius;
        b[3] = strength;
        b[4] = 18;
        stir(x, y, radius + 20, 1f);
    }

    //UPDATE
    public void update(){
        if(cells == null) return;
        time++;
        collectPushers();

        for(int c = 0; c < cols; c++){
            for(int r = 0; r < rows; r++){
                if(energy[c][r] > 0){
                    energy[c][r] *= 0.93f;
                    if(energy[c][r] < 0.01f) energy[c][r] = 0;
                }
            }
        }

        for(int i = burstCount - 1; i >= 0; i--){
            bursts[i][4]--;
            if(bursts[i][4] <= 0){
                float[] t = bursts[i];
                bursts[i] = bursts[burstCount - 1];
                bursts[burstCount - 1] = t;
                burstCount--;
            }
        }
    }

    private void collectPushers(){
        Player cam = gp.localPlayer();
        pushers = 0;
        Map<Entity, int[]> now = new IdentityHashMap<>();
        if(cam != null){
            for(Player p : gp.players) addPusher(p, cam, now);
            for(Entity m : gp.monster) addPusher(m, cam, now);
            for(Entity n : gp.npc) addPusher(n, cam, now);
        }
        lastPos = now;
    }

    private void addPusher(Entity e, Player cam, Map<Entity, int[]> now){
        if(e == null || e.dying || pushers >= px.length) return;
        if(e instanceof Player p && p.isDead) return;

        float fx = e.worldX + e.solidArea.x + e.solidArea.width / 2f;
        float fy = e.worldY + e.solidArea.y + e.solidArea.height - 6;
        if(Math.abs(fx - cam.worldX) > 900 || Math.abs(fy - cam.worldY) > 600) return;

        int[] prev = lastPos.get(e);
        boolean moved = prev != null && (Math.abs(prev[0] - e.worldX) + Math.abs(prev[1] - e.worldY)) > 0;
        now.put(e, new int[]{e.worldX, e.worldY});

        float radius = e.solidArea.width / 2f + 16;
        px[pushers] = fx;
        py[pushers] = fy;
        pr[pushers] = radius;
        pushers++;

        if(moved) stir(fx, fy, radius + 10, 0.9f);
    }

    private void stir(float x, float y, float radius, float amount){
        int ts = gp.tileSize;
        int c0 = Math.max(0, Math.floorDiv((int) (x - radius), ts));
        int c1 = Math.min(cols - 1, Math.floorDiv((int) (x + radius), ts));
        int r0 = Math.max(0, Math.floorDiv((int) (y - radius), ts));
        int r1 = Math.min(rows - 1, Math.floorDiv((int) (y + radius), ts));
        for(int c = c0; c <= c1; c++){
            for(int r = r0; r <= r1; r++){
                energy[c][r] = Math.max(energy[c][r], amount);
            }
        }
    }

    //DRAWING
    // everything on the ground, before any entity
    public void drawBase(Graphics2D g2){
        if(cells == null) return;
        Player cam = gp.localPlayer();
        if(cam == null) return;

        int ts = gp.tileSize;
        int left = cam.worldX - cam.screenX, top = cam.worldY - cam.screenY;
        int c0 = Math.max(0, Math.floorDiv(left, ts) - 1);
        int c1 = Math.min(cols - 1, Math.floorDiv(left + gp.screenWidth, ts) + 1);
        int r0 = Math.max(0, Math.floorDiv(top, ts) - 1);
        int r1 = Math.min(rows - 1, Math.floorDiv(top + gp.screenHeight, ts) + 2);

        for(int r = r0; r <= r1; r++){
            for(int c = c0; c <= c1; c++){
                int v = cells[c][r];
                if(v == 0) continue;
                int type = (v - 1) / 4;
                int count = COUNT[(v - 1) % 4 + 1];
                for(int i = 0; i < count; i++){
                    drawTuft(g2, cam, c, r, i, type);
                }
            }
        }
    }

    // the tufts in front of a creature's feet, drawn again on top of it so it walks THROUGH the grass
    public void drawFront(Graphics2D g2, Entity e){
        if(cells == null) return;
        if(e.type != Entity.type_player && e.type != Entity.type_monster && e.type != Entity.type_npc) return;
        Player cam = gp.localPlayer();
        if(cam == null) return;

        int ts = gp.tileSize;
        int fx = e.worldX + e.solidArea.x + e.solidArea.width / 2;
        int fy = e.sortY();
        int c0 = Math.max(0, Math.floorDiv(fx - 36, ts)), c1 = Math.min(cols - 1, Math.floorDiv(fx + 36, ts));
        int r0 = Math.max(0, Math.floorDiv(fy, ts)), r1 = Math.min(rows - 1, Math.floorDiv(fy + 26, ts));

        for(int r = r0; r <= r1; r++){
            for(int c = c0; c <= c1; c++){
                int v = cells[c][r];
                if(v == 0) continue;
                int type = (v - 1) / 4;
                int count = COUNT[(v - 1) % 4 + 1];
                int base = (r * cols + c) * MAX_PER_TILE;
                for(int i = 0; i < count; i++){
                    int wx = c * ts + tuftX[base + i];
                    int wy = r * ts + tuftY[base + i];
                    if(wy <= fy || wy > fy + 26 || Math.abs(wx - fx) > 34) continue;
                    drawTuft(g2, cam, c, r, i, type);
                }
            }
        }
    }

    private void drawTuft(Graphics2D g2, Player cam, int c, int r, int i, int type){
        int idx = (r * cols + c) * MAX_PER_TILE + i;
        int ts = gp.tileSize;
        float wx = c * ts + tuftX[idx];
        float wy = r * ts + tuftY[idx];
        int hash = tuftHash[idx];
        int bend = bendIndex(c, r, wx, wy, hash);
        BufferedImage img = art.frame(type, (hash >>> 3) % GrassArt.VARIANTS[type], bend);
        g2.drawImage(img, (int) wx - art.footX - cam.worldX + cam.screenX,
                (int) wy - art.footY - cam.worldY + cam.screenY, null);
    }

    // wind sway + lingering wobble + creatures + bursts, quantised to one of the baked lean frames
    private int bendIndex(int c, int r, float wx, float wy, int hash){
        double phase = (hash & 255) * 0.0245;
        double wind = gp.ambient.wind;
        double lean = Math.sin(time * 0.045 + wx * 0.011 + wy * 0.007 + phase) * (0.10 + 0.14 * wind)
                + (wind - 0.4) * 0.25;

        float e = energy[c][r];
        if(e > 0) lean += Math.sin(time * 0.33 + phase * 3) * e * 0.5;

        for(int k = 0; k < pushers; k++){
            float dx = wx - px[k];
            if(dx > pr[k] || dx < -pr[k]) continue;
            float dy = wy - py[k];
            if(dy > pr[k] || dy < -pr[k]) continue;
            float d2 = dx * dx + dy * dy;
            if(d2 >= pr[k] * pr[k]) continue;
            double f = 1 - Math.sqrt(d2) / pr[k];
            double dir = dx > 0.5f ? 1 : (dx < -0.5f ? -1 : ((hash & 1) == 0 ? 1 : -1));
            lean += dir * f * 1.2;
        }

        for(int k = 0; k < burstCount; k++){
            float[] b = bursts[k];
            float dx = wx - b[0], dy = wy - b[1];
            float d2 = dx * dx + dy * dy;
            if(d2 >= b[2] * b[2]) continue;
            double f = (1 - Math.sqrt(d2) / b[2]) * b[3] * Math.min(1f, b[4] / 10f);
            lean += (dx >= 0 ? 1 : -1) * f * 1.6;
        }

        lean = Math.max(-1, Math.min(1, lean));
        return (int) Math.round((lean + 1) * 0.5 * (GrassArt.BEND_FRAMES - 1));
    }

    //SETUP
    // the tuft positions are a pure function of the tile, so painting only ever changes a tile's density
    private void buildTufts(){
        int n = cols * rows * MAX_PER_TILE;
        tuftX = new short[n];
        tuftY = new short[n];
        tuftHash = new int[n];
        int ts = gp.tileSize;
        for(int r = 0; r < rows; r++){
            for(int c = 0; c < cols; c++){
                int base = (r * cols + c) * MAX_PER_TILE;
                for(int i = 0; i < MAX_PER_TILE; i++){
                    tuftX[base + i] = (short) (4 + ArtKit.hash01(401 + i, c, r) * (ts - 8));
                    tuftY[base + i] = (short) (8 + ArtKit.hash01(433 + i, c, r) * (ts - 12));
                    tuftHash[base + i] = (int) (ArtKit.hash01(467 + i, c, r) * 1_000_000);
                }
                for(int a = 1; a < MAX_PER_TILE; a++){      // sort by y: lower tufts draw later, in front
                    for(int b = a; b > 0 && tuftY[base + b - 1] > tuftY[base + b]; b--){
                        swap(base + b, base + b - 1);
                    }
                }
            }
        }
    }

    private void swap(int a, int b){
        short sx = tuftX[a]; tuftX[a] = tuftX[b]; tuftX[b] = sx;
        short sy = tuftY[a]; tuftY[a] = tuftY[b]; tuftY[b] = sy;
        int h = tuftHash[a]; tuftHash[a] = tuftHash[b]; tuftHash[b] = h;
    }

    private String readText(){
        File f = main.AppPaths.mapFile(PATH);
        try{
            if(f.isFile()) return Files.readString(f.toPath(), StandardCharsets.UTF_8);
            try(InputStream is = GrassLayer.class.getResourceAsStream(RESOURCE)){
                if(is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch(IOException e){
            e.printStackTrace();
        }
        return null;
    }

    private void parse(String text){
        String[] lines = text.split("\\R");
        for(int r = 0; r < rows && r < lines.length; r++){
            String line = lines[r];
            for(int c = 0; c < cols && c < line.length(); c++){
                char ch = line.charAt(c);
                cells[c][r] = (byte) ((ch >= 'a' && ch <= 'l') ? ch - 'a' + 1 : 0);
            }
        }
    }

    // the starting meadow: patches from noise, tall grass toward the forest, flower clumps here and there
    private void paintDefault(){
        boolean[][] taken = new boolean[cols][rows];
        for(WorldObject o : gp.worldObjects){
            if(o.col >= 0 && o.row >= 0 && o.col < cols && o.row < rows) taken[o.col][o.row] = true;
        }
        for(int c = 0; c < cols; c++){
            for(int r = 0; r < rows; r++){
                if(gp.tileM.terrain(c, r) != Terrain.GRASS || taken[c][r]) continue;
                int edge = Math.min(Math.min(c, r), Math.min(cols - 1 - c, rows - 1 - r));
                double n = ArtKit.fbm(c * 0.16, r * 0.16, 211);
                if(edge < 14) n += 0.12;              // the forest edge is grassier
                if(n < 0.50) continue;

                int density = Math.max(1, Math.min(4, 1 + (int) ((n - 0.50) / 0.12 * 3)));
                int type = SHORT;
                if(edge < 10 || ArtKit.fbm(c * 0.13 + 40, r * 0.13, 222) > 0.60){
                    type = TALL;
                } else if(ArtKit.fbm(c * 0.2 + 90, r * 0.2 + 30, 233) > 0.64){
                    type = FLOWERS;
                }
                cells[c][r] = (byte) ((type - 1) * 4 + density);
            }
        }
    }
}