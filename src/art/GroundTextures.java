package art;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/** Shared classpath PNGs. Tiles are views into the sheets, so pixels are decoded once. */
final class GroundTextures {
    private static final int TILE = 48;
    private static final Map<String, BufferedImage[]> SHEETS = new HashMap<>();

    /** Resolves the tile result used by GroundTextures. */
    BufferedImage tile(String name, int index) {
        synchronized (SHEETS) {
            return SHEETS.computeIfAbsent(name, GroundTextures::load)[index];
        }
    }

    /** Resolves the load result used by GroundTextures. */
    private static BufferedImage[] load(String name) {
        String path = "/ground/" + name + ".png";
        try (InputStream stream = GroundTextures.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing terrain sheet " + path);
            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() % TILE != 0 || image.getHeight() % TILE != 0)
                throw new IOException("Invalid terrain sheet " + path);
            int columns = image.getWidth() / TILE;
            BufferedImage[] tiles = new BufferedImage[columns * (image.getHeight() / TILE)];
            for (int i = 0; i < tiles.length; i++) {
                tiles[i] = image.getSubimage(i % columns * TILE, i / columns * TILE, TILE, TILE);
            }
            return tiles;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
