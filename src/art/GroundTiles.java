package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.function.BiPredicate;

public class GroundTiles {
    public static final int LEVELS = 4;            // ground tones 0..3
    public static final int COBBLE_VARIANTS = 3;

    // neighbour mask bits
    public static final int UP = 1, RIGHT = 2, DOWN = 4, LEFT = 8;
    public static final int UP_RIGHT = 16, DOWN_RIGHT = 32, DOWN_LEFT = 64, UP_LEFT = 128;
    public static final int FULL = 255;

    public static final int ART_TILE_SIZE = 48;
    public static final int GRASS_INSET = 6;
    public static final int LEDGE_LEFT = 1, LEDGE_RIGHT = 2;
    public static final int PATH_VARIANTS = 3;
    private static final double[] LEVEL_THRESHOLDS = {0.42, 0.52, 0.60}; // noise cut-offs between tones

    // The world the renderer reads from. Out-of-range cells should return the nearest valid cell's value.
    public interface World {
        Terrain terrain(int col, int row);
        int level(int col, int row);
    }

    private final int size;
    private final GroundTextures textures = new GroundTextures();

    public GroundTiles(int size){
        this.size = size;
    }

    // large smooth regions of lighter and darker tone
    public int groundLevel(int col, int row){
        double n = ArtKit.fbm(col * 0.08, row * 0.08, 7);
        int level = 0;
        for(double t : LEVEL_THRESHOLDS){
            if(n >= t) level++;
        }
        return level;
    }

    public BufferedImage cobble(int variant){ return textures.tile("cobble", variant); }
    public BufferedImage levelOverlay(int level, int mask){
        return textures.tile("grass-edge-" + level, mask & 255);
    }
    public BufferedImage pathOverlay(int mask, int variant){
        return textures.tile("path-" + variant, mask & 255);
    }

    //DRAWING ONE CELL
    public void drawCell(Graphics2D g, World w, int col, int row, int px, int py){
        Terrain t = w.terrain(col, row);
        if(t == Terrain.COBBLE){
            draw(g, cobble(cobbleVariant(col, row)), px, py);
            return;
        }

        if (t == Terrain.LEDGE || t == Terrain.STEPS) {
            draw(g, textures.tile("grass", Math.max(0, w.level(col, row) - 1)), px, py);
            if (t == Terrain.STEPS) {
                draw(g, textures.tile("steps", w.level(col, row)), px, py);
            } else {
                int connections = ledgeConnections(w, col, row);
                draw(g, textures.tile("ledge", w.level(col, row) * 4 + connections), px, py);
            }
            return;
        }
        if (t == Terrain.TERRACE || t == Terrain.RIM) {
            int level = w.level(col, row);
            int neighbors = mask((c, r) -> joinsRaised(w, c, r, level), col, row);
            draw(g, textures.tile("grass", Math.max(0, level - 1)), px, py);
            draw(g, level == 0 ? textures.tile("grass", 0) : levelOverlay(level, neighbors), px, py);
            return;
        }

        // Tones are nested: a tile at tone 3 sits inside tone 2, which sits inside tone 1. Start from the highest
        // tone that fully covers the tile and only draw the overlays above it.
        int level = w.level(col, row);
        int[] masks = new int[LEVELS];
        int startK = 0;
        for(int k = level; k >= 1; k--){
            final int tone = k;
            masks[k] = mask((c, r) -> w.level(c, r) >= tone, col, row);
            if(masks[k] == FULL){
                startK = k;
                break;
            }
        }
        draw(g, textures.tile("grass", startK), px, py);
        for(int k = startK + 1; k <= level; k++){
            draw(g, levelOverlay(k, masks[k]), px, py);
        }

        if(t == Terrain.PATH){
            int m = mask((c, r) -> pathLike(w, c, r), col, row);
            draw(g, pathOverlay(m, pathVariant(col, row)), px, py);
        } else if(t == Terrain.WATER) {
            int m = mask((c, r) -> w.terrain(c, r) == Terrain.WATER, col, row);
            double variation = ArtKit.hash01(32, col, row);
            int variant = variation < 0.64 ? 0 : variation < 0.86 ? 1 : 2;
            draw(g, textures.tile("water-" + variant, m), px, py);
            if(m == FULL && ArtKit.hash01(57, col, row) < 0.18)
                draw(g, textures.tile("lilies", ArtKit.hash01(58, col, row) < 0.4 ? 1 : 0), px, py);
        }
    }

    private static boolean pathLike(World w, int col, int row){
        Terrain t = w.terrain(col, row);
        return t == Terrain.PATH || t == Terrain.COBBLE || t == Terrain.STEPS; // paths run into the plaza
    }

    private static int mask(BiPredicate<Integer, Integer> same, int col, int row){
        int m = 0;
        if(same.test(col, row - 1)) m |= UP;
        if(same.test(col + 1, row)) m |= RIGHT;
        if(same.test(col, row + 1)) m |= DOWN;
        if(same.test(col - 1, row)) m |= LEFT;
        if(same.test(col + 1, row - 1)) m |= UP_RIGHT;
        if(same.test(col + 1, row + 1)) m |= DOWN_RIGHT;
        if(same.test(col - 1, row + 1)) m |= DOWN_LEFT;
        if(same.test(col - 1, row - 1)) m |= UP_LEFT;
        return m;
    }

    private int cobbleVariant(int col, int row){
        return (int) (ArtKit.hash01(3, col, row) * COBBLE_VARIANTS) % COBBLE_VARIANTS;
    }

    private int pathVariant(int col, int row){
        return (int) (ArtKit.hash01(11, col, row) * PATH_VARIANTS) % PATH_VARIANTS;
    }

    private void draw(Graphics2D g, BufferedImage image, int x, int y) {
        g.drawImage(image, x, y, size, size, null);
    }

    private static boolean ledgeLike(Terrain terrain) {
        return terrain == Terrain.LEDGE || terrain == Terrain.STEPS;
    }

    public static int ledgeConnections(World w, int col, int row) {
        int connections = ledgeLike(w.terrain(col - 1, row)) ? LEDGE_LEFT : 0;
        if (ledgeLike(w.terrain(col + 1, row))) connections |= LEDGE_RIGHT;
        return connections;
    }

    public static boolean joinsRaised(World w, int col, int row, int level) {
        Terrain terrain = w.terrain(col, row);
        return w.level(col, row) >= level
                && (ledgeLike(terrain) || terrain == Terrain.TERRACE || terrain == Terrain.RIM);
    }

    // A rounded square, inset from the tile edge, with an arm toward every connected neighbour. Outer corners
    // are rounded with `radius`, inner corners with `fillet`. wobbleVariant >= 0 adds small bumps and dents
    // along the straight edges (never near the tile border, so neighbouring tiles still join).
    public static Area blobShape(int mask, double inset, double radius, double fillet, int wobbleVariant){
        double i = inset, r = radius, f = fillet, u = ART_TILE_SIZE, ext = 3;
        boolean up = (mask & UP) != 0, right = (mask & RIGHT) != 0, down = (mask & DOWN) != 0, left = (mask & LEFT) != 0;

        Area shape = new Area(new Rectangle2D.Double(i, i, u - 2 * i, u - 2 * i));

        if(!up && !left) shape.subtract(roundCut(i, i, i, i, r));
        if(!up && !right) shape.subtract(roundCut(u - i - r, i, u - i - 2 * r, i, r));
        if(!down && !right) shape.subtract(roundCut(u - i - r, u - i - r, u - i - 2 * r, u - i - 2 * r, r));
        if(!down && !left) shape.subtract(roundCut(i, u - i - r, i, u - i - 2 * r, r));

        // arms run past the tile border so neighbouring tiles join without a hairline
        if(up) shape.add(new Area(new Rectangle2D.Double(i, -ext, u - 2 * i, i + ext)));
        if(down) shape.add(new Area(new Rectangle2D.Double(i, u - i, u - 2 * i, i + ext)));
        if(left) shape.add(new Area(new Rectangle2D.Double(-ext, i, i + ext, u - 2 * i)));
        if(right) shape.add(new Area(new Rectangle2D.Double(u - i, i, i + ext, u - 2 * i)));

        // inner corners
        if(up && left){
            if((mask & UP_LEFT) != 0) shape.add(new Area(new Rectangle2D.Double(-ext, -ext, i + ext, i + ext)));
            else shape.add(fillet(i, i, -1, -1, f));
        }
        if(up && right){
            if((mask & UP_RIGHT) != 0) shape.add(new Area(new Rectangle2D.Double(u - i, -ext, i + ext, i + ext)));
            else shape.add(fillet(u - i, i, 1, -1, f));
        }
        if(down && right){
            if((mask & DOWN_RIGHT) != 0) shape.add(new Area(new Rectangle2D.Double(u - i, u - i, i + ext, i + ext)));
            else shape.add(fillet(u - i, u - i, 1, 1, f));
        }
        if(down && left){
            if((mask & DOWN_LEFT) != 0) shape.add(new Area(new Rectangle2D.Double(-ext, u - i, i + ext, i + ext)));
            else shape.add(fillet(i, u - i, -1, 1, f));
        }

        if(wobbleVariant >= 0){
            addEdgeWobble(shape, mask, wobbleVariant, i, i + r + 3);
        }
        return shape;
    }

    private static void addEdgeWobble(Area shape, int mask, int variant, double inset, double margin){
        Random rnd = new Random(mask * 977L + variant * 131L + 5);
        int[] bits = {UP, RIGHT, DOWN, LEFT};
        for(int side = 0; side < 4; side++){
            if((mask & bits[side]) != 0) continue; // this side has an arm, so no straight edge here
            int count = 1 + rnd.nextInt(2);
            for(int n = 0; n < count; n++){
                double along = margin + rnd.nextDouble() * (ART_TILE_SIZE - 2 * margin);
                double rad = 1.4 + rnd.nextDouble() * 1.4;
                boolean outward = rnd.nextBoolean();
                double cx, cy;
                switch(side){
                    case 0 -> { cx = along; cy = inset; }
                    case 1 -> { cx = ART_TILE_SIZE - inset; cy = along; }
                    case 2 -> { cx = along; cy = ART_TILE_SIZE - inset; }
                    default -> { cx = inset; cy = along; }
                }
                Area bump = new Area(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
                if(outward) shape.add(bump); else shape.subtract(bump);
            }
        }
    }

    // the part of a corner square outside the quarter circle: subtract it to round a convex corner
    private static Area roundCut(double rectX, double rectY, double circX, double circY, double r){
        Area cut = new Area(new Rectangle2D.Double(rectX, rectY, r, r));
        cut.subtract(new Area(new Ellipse2D.Double(circX, circY, 2 * r, 2 * r)));
        return cut;
    }

    // the filler for a concave corner whose point is (px, py); (sx, sy) points into the empty quadrant
    private static Area fillet(double px, double py, int sx, int sy, double f){
        Area a = new Area(new Rectangle2D.Double(Math.min(px, px + sx * f), Math.min(py, py + sy * f), f, f));
        double cx = px + sx * f, cy = py + sy * f;
        a.subtract(new Area(new Ellipse2D.Double(cx - f, cy - f, 2 * f, 2 * f)));
        return a;
    }

}
