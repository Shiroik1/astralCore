package art;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Pixel buffer effect: render small, scale up with nearest-neighbour. */
public final class PixelFilter {
    private PixelFilter() {}

    /** Downsample a hi-res image to w x h: box-average colour, hard (1-bit) alpha so silhouettes stay crisp. */
    public static BufferedImage toLowRes(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double fx = src.getWidth() / (double) w, fy = src.getHeight() / (double) h;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int x0 = (int) Math.floor(x * fx), x1 = (int) Math.ceil((x + 1) * fx);
            int y0 = (int) Math.floor(y * fy), y1 = (int) Math.ceil((y + 1) * fy);
            float a = 0, r = 0, g = 0, b = 0; int n = 0;
            for (int j = y0; j < y1; j++) for (int i = x0; i < x1; i++) {
                int p = src.getRGB(Math.min(i, src.getWidth() - 1), Math.min(j, src.getHeight() - 1));
                float pa = (p >>> 24) / 255f;
                a += pa; r += ((p >> 16) & 255) * pa; g += ((p >> 8) & 255) * pa; b += (p & 255) * pa; n++;
            }
            if (a <= 0) continue;
            r /= a; g /= a; b /= a; a /= n;
            if (a < 0.35f) continue;                     // hard edge (low threshold keeps thin blades and smears)
            out.setRGB(x, y, 0xFF000000 | (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b));
        }
        return out;
    }

    /** Nearest-neighbour upscale (the "pixel buffer" step). */
    public static BufferedImage upscale(BufferedImage low, int scale) {
        BufferedImage out = new BufferedImage(low.getWidth() * scale, low.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(low, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }
}
