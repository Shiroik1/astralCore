package world;

import art.GroundTiles;

import entity.Entity;

import main.Gamepanel;

import world.generation.CliffTileResolver;
import world.generation.GeneratedWorld;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Cached edge tile with a logical depth anchor. Foreground actors draw after the cliff base. */
public final class CliffLayer extends Entity {
    private final BufferedImage image;
    private final int lift;

    /** Creates a CliffLayer instance and initializes the state required by its collaborators. */
    public CliffLayer(
            Gamepanel gp,
            GeneratedWorld w,
            GroundTiles art,
            GroundTiles.World tiles,
            int col,
            int row) {
        super(gp);
        type = type_none;
        worldX = col * gp.tileSize;
        worldY = row * gp.tileSize;
        int ts = gp.tileSize;
        lift = w.heights().at(col, row) * (ts / 2) + ts / 8;
        image = bakeImage(w, art, tiles, col, row, ts);
    }

    /** Image prepared off-thread; entity registration and ownership remain on the game thread. */
    public CliffLayer(Gamepanel gp, GeneratedWorld w, int col, int row, BufferedImage cached) {
        super(gp);
        type = type_none;
        worldX = col * gp.tileSize;
        worldY = row * gp.tileSize;
        lift = w.heights().at(col, row) * (gp.tileSize / 2) + gp.tileSize / 8;
        image = cached;
    }

    /** Evaluates the intersects condition for CliffLayer. */
    public boolean intersects(Rectangle view) {
        return view.intersects(worldX, worldY - lift, image.getWidth(), image.getHeight());
    }

    /** Resolves the cache bytes result used by CliffLayer. */
    public long cacheBytes() {
        return (long) image.getWidth() * image.getHeight() * 4;
    }

    /**
     * Pure cache construction, also available to the offline renderer checks without creating a
     * game window.
     */
    public static BufferedImage bakeImage(
            GeneratedWorld w, GroundTiles art, GroundTiles.World tiles, int col, int row, int ts) {
        int lift = w.heights().at(col, row) * (ts / 2) + ts / 8;
        int cacheHeight = ts + lift + ts / 8, supersample = w.generatorVersion >= 10 ? 2 : 1;
        var canvas =
                new BufferedImage(
                        ts * supersample, cacheHeight * supersample, BufferedImage.TYPE_INT_ARGB);
        var g = canvas.createGraphics();
        g.scale(supersample, supersample);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (w.generatorVersion >= 10) CliffTileResolver.drawWalls(g, art, w, col, row, ts, 0, lift);
        else
            for (int step = 1; step <= w.heights().at(col, row); step++) {
                CliffTileResolver.drawFace(g, art, w, col, row, step, ts, 0, lift);
                CliffTileResolver.drawTop(g, art, tiles, w, col, row, step, ts, 0, lift);
            }
        g.dispose();
        BufferedImage image;
        if (supersample == 1) image = canvas;
        else {
            image = new BufferedImage(ts, cacheHeight, BufferedImage.TYPE_INT_ARGB);
            var down = image.createGraphics();
            down.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            down.drawImage(canvas, 0, 0, ts, cacheHeight, null);
            down.dispose();
        }
        if (w.generatorVersion >= 10) {
            // The cap's exported alpha is already antialiased. Resampling each cap in an isolated
            // cell faded joined rows into transparency and exposed bright ground-colored seams.
            var cap = image.createGraphics();
            CliffTileResolver.drawTop(
                    cap, art, tiles, w, col, row, w.heights().at(col, row), ts, 0, lift);
            cap.dispose();
        }
        return image;
    }

    /** Resolves the sort y result used by CliffLayer. */
    @Override
    public int sortY() {
        return worldY + gp.tileSize;
    }

    /** Renders this component into the supplied Java2D graphics context. */
    @Override
    public void draw(Graphics2D g) {
        var p = gp.localPlayer();
        g.drawImage(
                image, worldX - p.worldX + p.screenX, worldY - p.worldY + p.screenY - lift, null);
    }
}
