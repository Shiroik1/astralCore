package art;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

/** Import the approved crafting atlas; no runtime image generation. */
public final class GenerateCraftingTextures {
    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws Exception {
        BufferedImage sheet =
                ImageIO.read(Path.of("assets/reference/crafting/crafting-source.png").toFile());
        String[] ids = {
            "wooden_axe",
            "wooden_sword",
            "wooden_bow",
            "wooden_staff",
            "plank",
            "log",
            "stone",
            "workbench",
            "forge"
        };
        Files.createDirectories(Path.of("res/crafting"));
        for (int i = 0; i < ids.length; i++) {
            int x = i % 3 * sheet.getWidth() / 3, y = i / 3 * sheet.getHeight() / 3;
            BufferedImage art =
                    trim(
                            sheet.getSubimage(
                                    x,
                                    y,
                                    (i % 3 + 1) * sheet.getWidth() / 3 - x,
                                    (i / 3 + 1) * sheet.getHeight() / 3 - y));
            int w = i == 7 ? 216 : i == 8 ? 144 : 128, h = i == 7 ? 176 : i == 8 ? 200 : 128;
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale = Math.min((w - 4.0) / art.getWidth(), (h - 4.0) / art.getHeight());
            int aw = (int) (art.getWidth() * scale), ah = (int) (art.getHeight() * scale);
            g.drawImage(art, (w - aw) / 2, h - ah, aw, ah, null);
            g.dispose();
            ImageIO.write(out, "png", Path.of("res/crafting/" + ids[i] + ".png").toFile());
        }
    }

    /**
     * Keeps the main connected silhouette, discarding unrelated alpha flecks in otherwise empty
     * gutters.
     */
    private static BufferedImage trim(BufferedImage cell) {
        int w = cell.getWidth(), h = cell.getHeight(), size = w * h;
        boolean[] seen = new boolean[size];
        BitSet best = new BitSet(size);
        int bestSize = 0;
        int[] queue = new int[size];
        for (int seed = 0; seed < size; seed++) {
            if (seen[seed] || (cell.getRGB(seed % w, seed / w) >>> 24) < 24) continue;
            BitSet component = new BitSet(size);
            int head = 0, tail = 0;
            queue[tail++] = seed;
            seen[seed] = true;
            while (head < tail) {
                int at = queue[head++];
                component.set(at);
                int x = at % w, y = at / w;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && (cell.getRGB(nx, ny) >>> 24) >= 24) {
                            seen[next] = true;
                            queue[tail++] = next;
                        }
                    }
            }
            if (tail > bestSize) {
                best = component;
                bestSize = tail;
            }
        }
        if (bestSize == 0) throw new IllegalArgumentException("Empty furniture cell");
        int left = w, top = h, right = 0, bottom = 0;
        BitSet soft = (BitSet) best.clone();
        for (int at = best.nextSetBit(0); at >= 0; at = best.nextSetBit(at + 1)) {
            int x = at % w, y = at / w;
            left = Math.min(left, x);
            right = Math.max(right, x);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    if (x + dx >= 0 && x + dx < w && y + dy >= 0 && y + dy < h)
                        soft.set((y + dy) * w + x + dx);
        }
        left = Math.max(0, left - 1);
        top = Math.max(0, top - 1);
        right = Math.min(w - 1, right + 1);
        bottom = Math.min(h - 1, bottom + 1);
        BufferedImage out =
                new BufferedImage(right - left + 1, bottom - top + 1, BufferedImage.TYPE_INT_ARGB);
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if (soft.get(y * w + x)) out.setRGB(x - left, y - top, cell.getRGB(x, y));
        return out;
    }
}
