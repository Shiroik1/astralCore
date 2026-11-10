package world;

import art.ArtKit;
import art.Terrain;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Builds the 50x50 starter-town map and writes maps/terrain.txt, maps/levels.txt and maps/scenery.json.
// Running main() rebuilds everything from scratch, which OVERWRITES hand edits. The game also calls
// writeFiles() once, automatically, if no terrain file exists.
public class TownMapGenerator {
    public static final int SIZE = 50;

    private record Obj(String type, int col, int row, String text, String building){
        Obj(String type, int col, int row, String text){
            this(type, col, row, text, null);
        }
    }

    public static void main(String[] args) throws IOException {
        writeFiles();
        System.out.println("Wrote maps/terrain.txt, maps/levels.txt and maps/scenery.json");
    }

    public static void writeFiles() throws IOException {
        Terrain[][] terrain = new Terrain[SIZE][SIZE];
        int[][] level = new int[SIZE][SIZE];
        List<Obj> objects = new ArrayList<>();
        generate(terrain, level, objects);

        main.AppPaths.createParent(main.AppPaths.mapFile("maps/terrain.txt").toPath());
        Files.writeString(main.AppPaths.mapFile("maps/terrain.txt").toPath(), terrainText(terrain), StandardCharsets.UTF_8);
        Files.writeString(main.AppPaths.mapFile("maps/levels.txt").toPath(), levelText(level), StandardCharsets.UTF_8);
        Files.writeString(main.AppPaths.mapFile("maps/scenery.json").toPath(), sceneryJson(objects), StandardCharsets.UTF_8);
    }

    private static void generate(Terrain[][] t, int[][] level, List<Obj> out){
        Random rnd = new Random(20261107L);
        boolean[][] reserved = new boolean[SIZE][SIZE];

        //1. TERRAIN: grass everywhere, a solid deep-forest band along the border, and the ground tone levels
        for(int c = 0; c < SIZE; c++){
            for(int r = 0; r < SIZE; r++){
                int d = edgeDist(c, r);
                int deepLimit = 3 + (int) Math.round(ArtKit.valueNoise(c * 0.35, r * 0.35, 91) * 2); // 3 to 5, so the edge isn't a perfect square
                t[c][r] = (d < deepLimit) ? Terrain.DEEP : Terrain.GRASS;

                int base = noiseLevel(c, r);
                double dx = c - 24.5, dy = r - 24.5;
                double fromCenter = Math.sqrt(dx * dx + dy * dy);
                int lv;
                if(d < 9){
                    lv = 3;                                  // the forest ring is the deepest tone
                } else if(d < 12){
                    lv = Math.max(base, 2);                  // a transition band
                } else {
                    lv = Math.min(base, 2);
                    if(fromCenter < 10) lv = Math.min(lv, 1); // brighter ground around the plaza
                }
                level[c][r] = lv;
            }
        }

        //2. PLAZA AND ROADS
        for(int c = 21; c <= 29; c++){
            for(int r = 21; r <= 29; r++){
                t[c][r] = Terrain.COBBLE;
            }
        }
        roadNS(t, 20, 7, 25, 1.2, 0.45);   // north
        roadNS(t, 30, 42, 25, 1.2, 0.45);  // south
        roadEW(t, 20, 7, 25, 1.2, 0.45);   // west
        roadEW(t, 30, 42, 25, 1.2, 0.45);  // east

        // a short trail from the house door down to the west road
        int spurEnd = 25 + (int) Math.round(1.2 * Math.sin(17 * 0.45));
        for(int r = 20; r <= spurEnd; r++){
            if(t[17][r] != Terrain.COBBLE) t[17][r] = Terrain.PATH;
        }

        // keep trees off the roads and the plaza, with a one-tile margin
        for(int c = 0; c < SIZE; c++){
            for(int r = 0; r < SIZE; r++){
                if(t[c][r] == Terrain.PATH || t[c][r] == Terrain.COBBLE){
                    for(int dc = -1; dc <= 1; dc++){
                        for(int dr = -1; dr <= 1; dr++){
                            reserve(reserved, c + dc, r + dr);
                        }
                    }
                }
            }
        }
        for(int c = 14; c <= 20; c++){          // the house and its roof
            for(int r = 14; r <= 21; r++){
                reserve(reserved, c, r);
            }
        }

        //3. HAND-PLACED TOWN OBJECTS
        add(out, reserved, "inn", InnInterior.ENTRANCE_COL, InnInterior.ENTRANCE_ROW, null);
        add(out, reserved, "well", 25, 25, null);
        add(out, reserved, "lantern", 22, 22, null);
        add(out, reserved, "lantern_hanging", 28, 22, null);
        add(out, reserved, "lantern_hanging", 22, 28, null);
        add(out, reserved, "lantern", 28, 28, null);
        add(out, reserved, "sign", 23, 31, "OAKVALE\nA quiet village.");
        add(out, reserved, "sign", 9, 27, "The old forest path.\nTurn back, traveler.");
        add(out, reserved, "barrel", 14, 19, null);
        add(out, reserved, "crate", 14, 20, null);
        add(out, reserved, "bush", 19, 20, null);
        add(out, reserved, "flower_bush", 15, 21, null);
        add(out, reserved, "chest", 33, 23, null);
        for(int r = 33; r <= 38; r++){          // fences along the south lane
            add(out, reserved, "fence_v", 22, r, null);
            add(out, reserved, "fence_v", 28, r, null);
        }
        add(out, reserved, "oak_1", 12, 17, null);
        add(out, reserved, "oak_2", 36, 18, null);
        add(out, reserved, "oak_1", 35, 34, null);
        add(out, reserved, "oak_2", 14, 31, null);
        add(out, reserved, "pine_1", 31, 15, null);
        add(out, reserved, "pine_2", 38, 28, null);

        //4. RANDOM MEADOW SCATTER (inside the forest band)
        scatter(out, reserved, t, rnd, new String[]{"bush"}, 16, 12);
        scatter(out, reserved, t, rnd, new String[]{"flower_bush"}, 10, 12);
        scatter(out, reserved, t, rnd, new String[]{"rock_small"}, 10, 12);
        scatter(out, reserved, t, rnd, new String[]{"rock_large"}, 3, 12);
        scatter(out, reserved, t, rnd, new String[]{"stump"}, 5, 12);
        scatter(out, reserved, t, rnd, new String[]{"oak_1", "oak_2"}, 6, 12);
        scatter(out, reserved, t, rnd, new String[]{"pine_1", "pine_2"}, 6, 12);

        //5. DENSE FOREST BORDER: thickest at the edge, thinning inward
        for(int r = 0; r < SIZE; r++){
            for(int c = 0; c < SIZE; c++){
                int d = edgeDist(c, r);
                if(d > 11 || reserved[c][r]) continue;
                if(t[c][r] == Terrain.PATH || t[c][r] == Terrain.COBBLE) continue;

                double p = (d <= 4) ? 0.5 : 0.40 - (d - 5) * 0.045;
                if(rnd.nextDouble() >= p) continue;

                String kind = rnd.nextDouble() < 0.55 ? "pine_" : "oak_";
                out.add(new Obj(kind + (1 + rnd.nextInt(2)), c, r, null));
            }
        }
        forestPond(t, level, out);
        eastTerraces(t, level, out);
        townBuildings(t, out);
        townProps(out);
    }

    private static void townProps(List<Obj> objects){
        objects.add(new Obj("crate_stack", 31, 21, null));
        objects.add(new Obj("barrel_stack", 32, 22, null));
        objects.add(new Obj("logs_covered", 20, 20, null));
        objects.add(new Obj("logs_stacked", 39, 31, null));
        objects.add(new Obj("fence_short", 19, 34, null));
        objects.add(new Obj("fence_tall", 32, 34, null));
        // Keep the pine canopy clear of the shop's storage props.
        objects.removeIf(o -> o.type().equals("pine_1") && o.col() == 31 && o.row() == 23);
        objects.add(new Obj("pine_1", 34, 22, null));
        objects.replaceAll(o -> o.type().equals("rock_large") && o.col() == 37 && o.row() == 22
                ? new Obj("rock_large_moss", o.col(), o.row(), o.text()) : o);
    }

    private static void townBuildings(Terrain[][] terrain, List<Obj> objects){
        // Keep the shop off the north road and connect the houses along the south lane.
        placeBuilding(objects, "general_shop", 30, 20);
        placeBuilding(objects, "house_open", 17, 30);
        placeBuilding(objects, "house_closed", 35, 30);
        terrain[30][21] = Terrain.PATH;
        for(int col = 17; col <= 35; col++) terrain[col][31] = Terrain.PATH;
        objects.removeIf(o -> o.row() == 31 && o.col() >= 17 && o.col() <= 35 && !o.type().equals("sign"));
        // Shift the oak west of the open house so its canopy leaves the doorway visible.
        objects.removeIf(o -> o.type().equals("oak_1") && o.col() == 16 && o.row() == 31);
        objects.add(new Obj("oak_1", 15, 33, null));
    }

    private static void placeBuilding(List<Obj> objects, String id, int col, int row){
        objects.removeIf(o -> Math.abs(o.col() - col) <= 3 && o.row() >= row - 1 && o.row() <= row);
        objects.add(new Obj("building", col, row, null, id));
    }

    private static void eastTerraces(Terrain[][] terrain, int[][] levels, List<Obj> objects) {
        terrace(terrain, levels, 34, 12, 43, 21, 40, 1);
        terrace(terrain, levels, 37, 13, 41, 17, 39, 2);
        terrain[40][22] = Terrain.PATH;
        terrain[40][23] = Terrain.PATH;
        objects.removeIf(o -> o.col() >= 34 && o.col() <= 43 && o.row() >= 12 && o.row() <= 21
                || o.col() == 40 && (o.row() == 22 || o.row() == 23));
        objects.add(new Obj("oak_1", 35, 15, null));
        objects.add(new Obj("flower_bush", 38, 14, null));
        objects.add(new Obj("rock_small", 40, 14, null));
        objects.add(new Obj("bush", 42, 19, null));
    }

    private static void terrace(Terrain[][] terrain, int[][] levels, int left, int top,
                                int right, int bottom, int stairCol, int level) {
        for (int col = left; col <= right; col++) {
            for (int row = top; row <= bottom; row++) {
                levels[col][row] = level;
                if (row == bottom) terrain[col][row] = col == stairCol ? Terrain.STEPS : Terrain.LEDGE;
                else if (row == top || col == left || col == right) terrain[col][row] = Terrain.RIM;
                else terrain[col][row] = Terrain.TERRACE;
            }
        }
    }

    /** Local addition after scatter, preserving the rest of the town's seeded layout. */
    private static void forestPond(Terrain[][] t, int[][] level, List<Obj> out){
        for(int c=4;c<=11;c++)for(int r=14;r<=23;r++){
            t[c][r]=Terrain.GRASS;level[c][r]=3;
        }
        for(int c=5;c<=9;c++)for(int r=16;r<=20;r++){
            if((c==5 || c==9) && (r==16 || r==20))continue;
            t[c][r]=Terrain.WATER;
        }
        for(int r=22;r<=25;r++)t[7][r]=Terrain.PATH;
        // Clear the lake, banks and approach, then frame them with a deliberate tree line.
        out.removeIf(o -> o.col()>=3 && o.col()<=12 && o.row()>=13 && o.row()<=25);
        int[][] trees={{4,13},{7,13},{10,13},{12,14},{3,16},{12,17},{3,19},{12,20},{3,22},{12,23},{4,25}};
        for(int i=0;i<trees.length;i++)out.add(new Obj(i%2==0?"pine_1":"oak_2",trees[i][0],trees[i][1],null));
        out.add(new Obj("healing_pond",7,21,null));
        out.add(new Obj("flower_bush",10,21,null));
        out.add(new Obj("rock_small_moss",4,21,null));
        out.add(new Obj("sign",9,23,"FOREST POND\nRest at the pale stone by the water to restore HP."));
        out.removeIf(o -> o.col()==9 && o.row()==27 && o.type().equals("sign"));
        out.add(new Obj("sign",9,27,"FOREST POND\nFollow the west path, then north into the trees."));
    }

    //HELPERS
    private static int edgeDist(int c, int r){
        return Math.min(Math.min(c, r), Math.min(SIZE - 1 - c, SIZE - 1 - r));
    }

    // same noise and thresholds as GroundTiles.groundLevel, repeated here so the generator needs no renderer
    private static int noiseLevel(int col, int row){
        double n = ArtKit.fbm(col * 0.08, row * 0.08, 7);
        int level = 0;
        if(n >= 0.42) level++;
        if(n >= 0.52) level++;
        if(n >= 0.60) level++;
        return level;
    }

    private static void reserve(boolean[][] reserved, int c, int r){
        if(c >= 0 && r >= 0 && c < SIZE && r < SIZE) reserved[c][r] = true;
    }

    private static void add(List<Obj> out, boolean[][] reserved, String type, int col, int row, String text){
        if(WorldObjectType.fromName(type) == null){
            throw new IllegalArgumentException("Unknown object type: " + type);
        }
        out.add(new Obj(type, col, row, text));
        for(int dc = -1; dc <= 1; dc++){
            for(int dr = -1; dr <= 1; dr++){
                reserve(reserved, col + dc, row + dr);
            }
        }
    }

    private static void scatter(List<Obj> out, boolean[][] reserved, Terrain[][] t, Random rnd,
                                String[] types, int count, int minEdge){
        int placed = 0, attempts = 0;
        while(placed < count && attempts < count * 60){
            attempts++;
            int c = minEdge + rnd.nextInt(SIZE - 2 * minEdge);
            int r = minEdge + rnd.nextInt(SIZE - 2 * minEdge);
            if(reserved[c][r] || t[c][r] != Terrain.GRASS) continue;

            boolean crowded = false;
            for(Obj o : out){
                if(Math.abs(o.col() - c) <= 1 && Math.abs(o.row() - r) <= 1){
                    crowded = true;
                    break;
                }
            }
            if(crowded) continue;

            out.add(new Obj(types[rnd.nextInt(types.length)], c, r, null));
            reserve(reserved, c, r);
            placed++;
        }
    }

    // a wiggling north-south road; every row fills the span between the previous and current column,
    // so the trail is always connected tile to tile
    private static void roadNS(Terrain[][] t, int fromRow, int toRow, int baseCol, double amp, double freq){
        int step = toRow >= fromRow ? 1 : -1;
        int prev = baseCol + (int) Math.round(amp * Math.sin(fromRow * freq));
        for(int r = fromRow; r != toRow + step; r += step){
            int c = baseCol + (int) Math.round(amp * Math.sin(r * freq));
            for(int x = Math.min(prev, c); x <= Math.max(prev, c); x++){
                if(t[x][r] != Terrain.COBBLE) t[x][r] = Terrain.PATH;
            }
            prev = c;
        }
    }

    private static void roadEW(Terrain[][] t, int fromCol, int toCol, int baseRow, double amp, double freq){
        int step = toCol >= fromCol ? 1 : -1;
        int prev = baseRow + (int) Math.round(amp * Math.sin(fromCol * freq));
        for(int c = fromCol; c != toCol + step; c += step){
            int r = baseRow + (int) Math.round(amp * Math.sin(c * freq));
            for(int y = Math.min(prev, r); y <= Math.max(prev, r); y++){
                if(t[c][y] != Terrain.COBBLE) t[c][y] = Terrain.PATH;
            }
            prev = r;
        }
    }

    //FILE WRITERS
    private static String terrainText(Terrain[][] t){
        StringBuilder sb = new StringBuilder();
        for(int r = 0; r < SIZE; r++){
            for(int c = 0; c < SIZE; c++){
                sb.append(switch(t[c][r]){
                    case PATH -> '=';
                    case COBBLE -> '#';
                    case DEEP -> 'X';
                    case WATER -> '~';
                    case TERRACE -> 'T';
                    case RIM -> 'R';
                    case LEDGE -> '^';
                    case STEPS -> '+';
                    default -> '.';
                });
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String levelText(int[][] level){
        StringBuilder sb = new StringBuilder();
        for(int r = 0; r < SIZE; r++){
            for(int c = 0; c < SIZE; c++){
                sb.append(level[c][r]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String sceneryJson(List<Obj> objects){
        List<Obj> sorted = new ArrayList<>(objects);
        sorted.sort(Comparator.comparingInt(Obj::row).thenComparingInt(Obj::col));

        StringBuilder sb = new StringBuilder("{\n  \"objects\": [\n");
        for(int i = 0; i < sorted.size(); i++){
            Obj o = sorted.get(i);
            sb.append("    {\"type\": \"").append(o.type()).append("\", \"col\": ").append(o.col())
                    .append(", \"row\": ").append(o.row());
            if(o.text() != null){
                sb.append(", \"text\": \"").append(o.text().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
            }
            if(o.building() != null) sb.append(", \"building\": \"").append(o.building()).append("\"");
            sb.append("}").append(i < sorted.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }
}
