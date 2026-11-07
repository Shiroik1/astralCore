package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;

public class GroundTiles {
    public static final int LEVELS = 4;            // ground tones 0..3
    public static final int COBBLE_VARIANTS = 3;

    // neighbour mask bits
    public static final int UP = 1, RIGHT = 2, DOWN = 4, LEFT = 8;
    public static final int UP_RIGHT = 16, DOWN_RIGHT = 32, DOWN_LEFT = 64, UP_LEFT = 128;
    public static final int FULL = 255;

    private static final int U = 48;            // art is designed on a 48-unit tile, then scaled to `size`
    private static final double INSET = 6;      // gap between a shape's edge and the tile edge
    private static final double RADIUS = 15;    // roundness of outer corners
    // the dirt trail uses tighter corners and a slightly wobbly edge, so it reads as a worn track, not a pipe
    private static final double PATH_INSET = 7;
    private static final double PATH_RADIUS = 5;
    private static final double PATH_FILLET = 4;
    public static final int PATH_VARIANTS = 3;
    private static final double[] LEVEL_THRESHOLDS = {0.42, 0.52, 0.60}; // noise cut-offs between tones

    // The world the renderer reads from. Out-of-range cells should return the nearest valid cell's value.
    public interface World {
        Terrain terrain(int col, int row);
        int level(int col, int row);
    }

    private final int size;
    private final BufferedImage[] cobble = new BufferedImage[COBBLE_VARIANTS];
    private final Map<Integer, BufferedImage> blobCache = new HashMap<>();

    public GroundTiles(int size){
        this.size = size;
        for(int v = 0; v < COBBLE_VARIANTS; v++){
            cobble[v] = cobbleTile(5000L + v);
        }
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

    public BufferedImage cobble(int variant){ return cobble[variant]; }
    public BufferedImage levelOverlay(int level, int mask){
        return blob(level, 0, Palette.GROUND[level], mask, INSET, RADIUS, INSET, false);
    }
    public BufferedImage pathOverlay(int mask, int variant){
        return blob(9, variant, Palette.PATH, mask, PATH_INSET, PATH_RADIUS, PATH_FILLET, true);
    }

    //DRAWING ONE CELL
    public void drawCell(Graphics2D g, World w, int col, int row, int px, int py){
        Terrain t = w.terrain(col, row);
        if(t == Terrain.COBBLE){
            g.drawImage(cobble[cobbleVariant(col, row)], px, py, null);
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
        g.setColor(Palette.GROUND[startK]);
        g.fillRect(px, py, size, size);
        for(int k = startK + 1; k <= level; k++){
            g.drawImage(levelOverlay(k, masks[k]), px, py, null);
        }

        if(t == Terrain.PATH){
            int m = mask((c, r) -> pathLike(w, c, r), col, row);
            g.drawImage(pathOverlay(m, pathVariant(col, row)), px, py, null);
        }
    }

    private static boolean pathLike(World w, int col, int row){
        Terrain t = w.terrain(col, row);
        return t == Terrain.PATH || t == Terrain.COBBLE; // paths run into the plaza
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

    //ROUNDED SHAPE AUTOTILE
    private BufferedImage blob(int kind, int variant, Color color, int mask, double inset, double radius, double fillet, boolean wobble){
        int key = (kind * 8 + variant) * 256 + (mask & 255);
        BufferedImage img = blobCache.get(key);
        if(img == null){
            ArtKit.Surface s = new ArtKit.Surface(size, size);
            s.g.scale(size / (double) U, size / (double) U);
            s.g.setColor(color);
            s.g.fill(blobShape(mask, inset, radius, fillet, wobble ? variant : -1));
            img = s.finish();
            blobCache.put(key, img);
        }
        return img;
    }

    // A rounded square, inset from the tile edge, with an arm toward every connected neighbour. Outer corners
    // are rounded with `radius`, inner corners with `fillet`. wobbleVariant >= 0 adds small bumps and dents
    // along the straight edges (never near the tile border, so neighbouring tiles still join).
    public static Area blobShape(int mask, double inset, double radius, double fillet, int wobbleVariant){
        double i = inset, r = radius, f = fillet, u = U, ext = 3;
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
                double along = margin + rnd.nextDouble() * (U - 2 * margin);
                double rad = 1.4 + rnd.nextDouble() * 1.4;
                boolean outward = rnd.nextBoolean();
                double cx, cy;
                switch(side){
                    case 0 -> { cx = along; cy = inset; }
                    case 1 -> { cx = U - inset; cy = along; }
                    case 2 -> { cx = along; cy = U - inset; }
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

    //COBBLESTONE: flat stones on flat mortar. Row widths add up to exactly one tile, so it repeats seamlessly.
    private BufferedImage cobbleTile(long seed){
        ArtKit.Surface s = new ArtKit.Surface(size, size);
        Graphics2D g = s.g;
        g.scale(size / (double) U, size / (double) U);
        Random rnd = new Random(seed);

        g.setColor(Palette.MORTAR);
        g.fill(new Rectangle2D.Double(-1, -1, U + 2, U + 2));

        int rows = 3;
        double rowH = U / (double) rows;
        double gap = 1.6;
        for(int r = 0; r < rows; r++){
            int stones = 2 + rnd.nextInt(2);
            double[] w = new double[stones];
            double total = 0;
            for(int i = 0; i < stones; i++){
                w[i] = 0.7 + rnd.nextDouble() * 0.6;
                total += w[i];
            }
            double x = 0;
            for(int i = 0; i < stones; i++){
                double sw = w[i] / total * U;
                g.setColor(Palette.STONES[rnd.nextInt(Palette.STONES.length)]);
                double arc = Math.min(sw, rowH) * 0.5;
                g.fill(new RoundRectangle2D.Double(x + gap / 2, r * rowH + gap / 2, sw - gap, rowH - gap, arc, arc));
                x += sw;
            }
        }
        return s.finish();
    }
}