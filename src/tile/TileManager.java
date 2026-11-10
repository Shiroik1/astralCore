package tile;

import art.GroundTiles;
import art.Palette;
import art.Terrain;
import entity.Player;
import main.Gamepanel;
import world.InnInterior;
import world.TownMapGenerator;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileManager implements GroundTiles.World {
    private static final String TERRAIN_PATH = "maps/terrain.txt";
    private static final String LEVELS_PATH = "maps/levels.txt";
    private static final int CHUNK = 16; // tiles per chunk side

    private final Gamepanel gp;
    private final GroundTiles ground;
    private Terrain[][] terrainGrid;   // [col][row]
    private int[][] levelGrid;
    private final Map<Integer, BufferedImage> chunks = new HashMap<>();

    public TileManager(Gamepanel gp) throws IOException {
        this.gp = gp;
        this.ground = new GroundTiles(gp.tileSize);
        loadMap();
        bakeAll();
    }

    private void loadMap() throws IOException {
        if(!main.AppPaths.mapFile(TERRAIN_PATH).isFile() && getClass().getResource("/maps/terrain.txt") == null){
            System.out.println("[MAP] no terrain.txt found, generating the starter town");
            TownMapGenerator.writeFiles();
        }

        List<String> terrainLines = readLines(TERRAIN_PATH, "/maps/terrain.txt");
        List<String> levelLines = readLines(LEVELS_PATH, "/maps/levels.txt");

        int rows = terrainLines.size();
        int cols = terrainLines.get(0).length();
        gp.maxWorldCol = cols;
        gp.maxWorldRow = rows;

        terrainGrid = new Terrain[cols][rows];
        levelGrid = new int[cols][rows];
        for(int r = 0; r < rows; r++){
            String line = terrainLines.get(r);
            String levels = (r < levelLines.size()) ? levelLines.get(r) : "";
            for(int c = 0; c < cols; c++){
                terrainGrid[c][r] = parse(c < line.length() ? line.charAt(c) : '.');
                char lv = c < levels.length() ? levels.charAt(c) : '0';
                levelGrid[c][r] = Math.max(0, Math.min(GroundTiles.LEVELS - 1, lv - '0'));
            }
        }
    }

    private static Terrain parse(char ch){
        return switch(ch){
            case '=' -> Terrain.PATH;
            case '#' -> Terrain.COBBLE;
            case 'X' -> Terrain.DEEP;
            case '~' -> Terrain.WATER;
            case 'T' -> Terrain.TERRACE;
            case 'R' -> Terrain.RIM;
            case '^' -> Terrain.LEDGE;
            case '+' -> Terrain.STEPS;
            default -> Terrain.GRASS;
        };
    }

    // a file next to the game wins over a bundled copy, so hand edits take effect on the next launch
    private List<String> readLines(String externalPath, String resourcePath) throws IOException {
        String text;
        File f = main.AppPaths.mapFile(externalPath);
        if(f.isFile()){
            text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } else {
            try(InputStream is = getClass().getResourceAsStream(resourcePath)){
                if(is == null) throw new IOException("Missing map file: " + externalPath);
                text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        List<String> lines = new ArrayList<>();
        for(String line : text.split("\\R")){
            if(!line.isBlank()) lines.add(line.stripTrailing());
        }
        return lines;
    }

    //WORLD INTERFACE (read by GroundTiles; out-of-range cells return the nearest valid cell)
    @Override
    public Terrain terrain(int col, int row){
        return terrainGrid[Math.max(0, Math.min(gp.maxWorldCol - 1, col))][Math.max(0, Math.min(gp.maxWorldRow - 1, row))];
    }

    @Override
    public int level(int col, int row){
        return levelGrid[Math.max(0, Math.min(gp.maxWorldCol - 1, col))][Math.max(0, Math.min(gp.maxWorldRow - 1, row))];
    }

    // Coarse tile query for spawn placement. Movement uses the partial terrace boundaries below.
    public boolean isSolid(int col, int row){
        if(gp.quests!=null && gp.quests.inDungeon())return gp.quests.isDungeonSolid(col,row);
        if(InnInterior.isTile(col, row)){
            int x = col * gp.tileSize + gp.tileSize / 2;
            int y = row * gp.tileSize + gp.tileSize / 2;
            return InnInterior.blocksMovement(gp, x, y, x, y);
        }
        if(col < 0 || row < 0 || col >= gp.maxWorldCol || row >= gp.maxWorldRow) return true;
        return switch (terrainGrid[col][row]) {
            case DEEP, WATER, RIM, LEDGE -> true;
            default -> false;
        };
    }

    public boolean blocksMovement(int left, int top, int right, int bottom){
        int ts = gp.tileSize;
        boolean dungeon = gp.quests != null && gp.quests.inDungeon();
        if(!dungeon && InnInterior.containsPosition(gp, left, top)){
            return InnInterior.blocksMovement(gp, left, top, right, bottom);
        }
        int inset = ts * GroundTiles.GRASS_INSET / GroundTiles.ART_TILE_SIZE;
        for (int row = Math.floorDiv(top, ts); row <= Math.floorDiv(bottom, ts); row++) {
            for (int col = Math.floorDiv(left, ts); col <= Math.floorDiv(right, ts); col++) {
                if (!isSolid(col, row)) continue;
                if (dungeon || col < 0 || row < 0 || col >= gp.maxWorldCol || row >= gp.maxWorldRow) return true;

                int x = col * ts;
                int y = row * ts;
                if (terrainGrid[col][row] == Terrain.LEDGE) {
                    // The grassy upper half is walkable. The face and exposed ends stop movement.
                    if (bottom >= y + ts / 2) return true;
                    int joins = GroundTiles.ledgeConnections(this, col, row);
                    if (left < x + inset && (joins & GroundTiles.LEDGE_LEFT) == 0
                            || right >= x + ts - inset && (joins & GroundTiles.LEDGE_RIGHT) == 0) return true;
                } else if (terrainGrid[col][row] == Terrain.RIM) {
                    int level = levelGrid[col][row];
                    if (left < x + inset && !GroundTiles.joinsRaised(this, col - 1, row, level)
                            || right >= x + ts - inset && !GroundTiles.joinsRaised(this, col + 1, row, level)
                            || top < y + inset && !GroundTiles.joinsRaised(this, col, row - 1, level)
                            || bottom >= y + ts - inset && !GroundTiles.joinsRaised(this, col, row + 1, level)) return true;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    //BAKING: the ground never changes, so draw it once into 16x16-tile images
    private void bakeAll(){
        long start = System.currentTimeMillis();
        int chunkCols = (gp.maxWorldCol + CHUNK - 1) / CHUNK;
        int chunkRows = (gp.maxWorldRow + CHUNK - 1) / CHUNK;
        for(int cy = 0; cy < chunkRows; cy++){
            for(int cx = 0; cx < chunkCols; cx++){
                chunks.put(cy * 100 + cx, bakeChunk(cx, cy));
            }
        }
        System.out.println("[MAP] baked " + chunks.size() + " ground chunks in " + (System.currentTimeMillis() - start) + " ms");
    }

    private BufferedImage bakeChunk(int cx, int cy){
        int ts = gp.tileSize;
        int tilesW = Math.min(CHUNK, gp.maxWorldCol - cx * CHUNK);
        int tilesH = Math.min(CHUNK, gp.maxWorldRow - cy * CHUNK);
        BufferedImage img = new BufferedImage(tilesW * ts, tilesH * ts, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for(int r = 0; r < tilesH; r++){
            for(int c = 0; c < tilesW; c++){
                ground.drawCell(g, this, cx * CHUNK + c, cy * CHUNK + r, c * ts, r * ts);
            }
        }
        g.dispose();
        return img;
    }

    public void draw(Graphics2D g2){
        if(gp.quests.inDungeon()){gp.quests.drawDungeon(g2);return;}
        Player camera = gp.localPlayer();
        if(InnInterior.contains(gp, camera)){InnInterior.draw(g2, gp, camera);return;}
        int viewLeft = camera.worldX - camera.screenX;
        int viewTop = camera.worldY - camera.screenY;
        int ts = gp.tileSize;

        // beyond the map edge: the deepest forest tone, so the screen never shows a black void
        g2.setColor(Palette.GROUND[GroundTiles.LEVELS - 1]);
        g2.fillRect(-ts * 2, -ts * 2, gp.screenWidth + ts * 4, gp.screenHeight + ts * 4);

        int chunkPx = CHUNK * ts;
        int firstCx = Math.max(0, Math.floorDiv(viewLeft - ts, chunkPx));
        int lastCx = Math.min((gp.maxWorldCol - 1) / CHUNK, Math.floorDiv(viewLeft + gp.screenWidth + ts, chunkPx));
        int firstCy = Math.max(0, Math.floorDiv(viewTop - ts, chunkPx));
        int lastCy = Math.min((gp.maxWorldRow - 1) / CHUNK, Math.floorDiv(viewTop + gp.screenHeight + ts, chunkPx));

        for(int cy = firstCy; cy <= lastCy; cy++){
            for(int cx = firstCx; cx <= lastCx; cx++){
                BufferedImage img = chunks.get(cy * 100 + cx);
                if(img == null) continue;
                g2.drawImage(img, cx * chunkPx - camera.worldX + camera.screenX,
                        cy * chunkPx - camera.worldY + camera.screenY, null);
            }
        }
    }
}
