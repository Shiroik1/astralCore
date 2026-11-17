package art;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class ArtPreview {
    static final int T = 48;

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws Exception {
        new File("art_preview").mkdirs();
        GroundTiles tiles = new GroundTiles(T);
        ImageIO.write(scene(tiles), "png", new File("art_preview/ground_scene.png"));
        ImageIO.write(swatches(tiles), "png", new File("art_preview/ground_swatches.png"));
        System.out.println(
                "Wrote art_preview/ground_scene.png and art_preview/ground_swatches.png");
    }

    // SAMPLE TOWN: a winding trail from the left edge into a cobbled plaza
    static class SampleWorld implements GroundTiles.World {
        final GroundTiles tiles;

        /**
         * Creates a SampleWorld instance and initializes the state required by its collaborators.
         */
        SampleWorld(GroundTiles tiles) {
            this.tiles = tiles;
        }

        /** Resolves the path row result used by SampleWorld. */
        static int pathRow(int col) {
            return 12 + (int) Math.round(2.5 * Math.sin(col * 0.28));
        }

        /** Resolves the terrain result used by SampleWorld. */
        public Terrain terrain(int col, int row) {
            if (col >= 28 && col <= 34 && row >= 9 && row <= 15) return Terrain.COBBLE;
            if (col < 28) {
                int r0 = pathRow(col);
                int r1 = pathRow(col - 1);
                if (row >= Math.min(r0, r1) && row <= Math.max(r0, r1)) return Terrain.PATH;
            }
            return Terrain.GRASS;
        }

        /** Resolves the level result used by SampleWorld. */
        public int level(int col, int row) {
            return tiles.groundLevel(col, row);
        }
    }

    /** Resolves the scene result used by ArtPreview. */
    static BufferedImage scene(GroundTiles tiles) {
        int cols = 40, rows = 24;
        BufferedImage img = new BufferedImage(cols * T, rows * T, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        GroundTiles.World world = new SampleWorld(tiles);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles.drawCell(g, world, c, r, c * T, r * T);
            }
        }
        g.dispose();
        return img;
    }

    // a cardinal-only mask, with the corner bits set wherever both adjacent sides are present
    /** Resolves the plain result used by ArtPreview. */
    static int plain(int m) {
        int out = m;
        if ((m & GroundTiles.UP) != 0 && (m & GroundTiles.RIGHT) != 0) out |= GroundTiles.UP_RIGHT;
        if ((m & GroundTiles.UP) != 0 && (m & GroundTiles.LEFT) != 0) out |= GroundTiles.UP_LEFT;
        if ((m & GroundTiles.DOWN) != 0 && (m & GroundTiles.RIGHT) != 0)
            out |= GroundTiles.DOWN_RIGHT;
        if ((m & GroundTiles.DOWN) != 0 && (m & GroundTiles.LEFT) != 0)
            out |= GroundTiles.DOWN_LEFT;
        return out;
    }

    // SWATCHES at 2x. Row 0: the 4 ground tones, then 3 cobble tiles.
    // Rows 1-2: the 16 path shapes. Rows 3-4: the 16 patch shapes (tone 2 over tone 1).
    /** Resolves the swatches result used by ArtPreview. */
    static BufferedImage swatches(GroundTiles t) {
        int pad = 6;
        int cell = T * 2 + pad;
        BufferedImage img =
                new BufferedImage(8 * cell + pad, 5 * cell + pad, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x2B2B2B));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        for (int i = 0; i < GroundTiles.LEVELS; i++) {
            g.setColor(Palette.GROUND[i]);
            g.fillRect(pad + i * cell, pad, T * 2, T * 2);
        }
        for (int v = 0; v < GroundTiles.COBBLE_VARIANTS; v++) {
            put(g, null, t.cobble(v), 4 + v, 0, cell, pad);
        }
        for (int m = 0; m < 16; m++) {
            put(g, Palette.GROUND[1], t.pathOverlay(plain(m), 0), m % 8, 1 + m / 8, cell, pad);
            put(g, Palette.GROUND[1], t.levelOverlay(2, plain(m)), m % 8, 3 + m / 8, cell, pad);
        }
        g.dispose();
        return img;
    }

    /** Performs the put operation for ArtPreview. */
    private static void put(
            Graphics2D g, Color under, BufferedImage img, int cx, int cy, int cell, int pad) {
        int x = pad + cx * cell;
        int y = pad + cy * cell;
        if (under != null) {
            g.setColor(under);
            g.fillRect(x, y, T * 2, T * 2);
        }
        g.drawImage(img, x, y, T * 2, T * 2, null);
    }
}
