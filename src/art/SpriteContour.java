package art;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Shared thin creature contour, baked only when cached animation frames are constructed. */
public final class SpriteContour {
    /** Prevents instantiation of the frame-processing utility. */
    private SpriteContour() {}

    /** Bakes a thin slime-palette contour into each cached frame, never during rendering. */
    public static BufferedImage apply(BufferedImage source) {
        int w = source.getWidth(), h = source.getHeight();
        int[] pixels = source.getRGB(0, 0, w, h, null, 0, w);
        int[] contour = new int[pixels.length];
        int ink = SlimeArt.INK.getRGB() & 0xFFFFFF;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = 0;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int nx = x + ox, ny = y + oy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int a = pixels[ny * w + nx] >>> 24;
                        // Softer diagonal coverage keeps the roughly one-pixel edge rounded.
                        if (ox != 0 && oy != 0) a = a * 3 / 5;
                        alpha = Math.max(alpha, a);
                    }
                }
                contour[y * w + x] = alpha << 24 | ink;
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, contour, 0, w);
        Graphics2D g = out.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }
}
