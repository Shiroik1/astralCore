package art;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

import javax.imageio.ImageIO;

/** Offline sprite import: isolates the main alpha component and aligns equal-size foot canvases. */
public final class GenerateMemorialTextures {
    private GenerateMemorialTextures() {}

    private record Cut(BufferedImage image, Rectangle bounds) {}

    public static void main(String[] args) throws Exception {
        BufferedImage input = ImageIO.read(Path.of(args[0]).toFile());
        int half = input.getWidth() / 2;
        Cut left = extract(input.getSubimage(0, 0, half, input.getHeight()));
        Cut right = extract(input.getSubimage(half, 0, half, input.getHeight()));
        BufferedImage output = new BufferedImage(288, 144, BufferedImage.TYPE_INT_ARGB);
        double scale = 128.0 / Math.max(left.bounds.width, right.bounds.width);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        Cut[] cuts = {left, right};
        for (int i = 0; i < 2; i++) {
            Cut c = cuts[i];
            int w = (int) Math.round(c.bounds.width * scale),
                    h = (int) Math.round(c.bounds.height * scale);
            g.drawImage(
                    c.image,
                    i * 144 + (144 - w) / 2,
                    134 - h,
                    i * 144 + (144 + w) / 2,
                    134,
                    c.bounds.x,
                    c.bounds.y,
                    c.bounds.x + c.bounds.width,
                    c.bounds.y + c.bounds.height,
                    null);
        }
        g.dispose();
        Path path = Path.of(args[1]);
        Files.createDirectories(path.getParent());
        ImageIO.write(output, "png", path.toFile());
    }

    /** Discards detached export speckles while keeping the actual connected pile silhouette. */
    private static Cut extract(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] tags = new int[w * h], queue = new int[w * h];
        int label = 0, biggest = 0, best = 0;
        for (int p = 0; p < tags.length; p++) {
            if (tags[p] != 0 || (src.getRGB(p % w, p / w) >>> 24) < 96) continue;
            label++;
            int head = 0, tail = 1;
            queue[0] = p;
            tags[p] = label;
            while (head < tail) {
                int at = queue[head++], x = at % w, y = at / w;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int q = ny * w + nx;
                        if (tags[q] == 0 && (src.getRGB(nx, ny) >>> 24) >= 96) {
                            tags[q] = label;
                            queue[tail++] = q;
                        }
                    }
            }
            if (tail > biggest) {
                biggest = tail;
                best = label;
            }
        }
        BufferedImage clean = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int minX = w, minY = h, maxX = 0, maxY = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (tags[y * w + x] == best) {
                    clean.setRGB(x, y, src.getRGB(x, y));
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
        if (biggest == 0) throw new IllegalArgumentException("Empty memorial sprite");
        return new Cut(clean, new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1));
    }
}
