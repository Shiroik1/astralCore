package art;

import main.MiniJson;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Offline vector-atlas importer. Preserves design dimensions and exports complete, separate
 * furniture PNGs.
 */
public final class GenerateInteriorTextures {
    /** Prevents instantiation of this utility class. */
    private GenerateInteriorTextures() {}

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws Exception {
        Path source =
                Path.of(
                        args.length > 0
                                ? args[0]
                                : "assets/reference/interior/vector-interior-source.png");
        Path output = Path.of(args.length > 1 ? args[1] : "res/building/props");
        Files.createDirectories(output);
        BufferedImage sheet = ImageIO.read(source.toFile());
        var root = (Map<?, ?>) MiniJson.parse(Files.readString(Path.of("res/building/props.json")));
        List<?> props = (List<?>) root.get("props");
        int index = 0;
        BufferedImage board =
                new BufferedImage(
                        1000, ((props.size() + 4) / 5) * 210, BufferedImage.TYPE_INT_ARGB);
        Graphics2D preview = board.createGraphics();
        preview.setColor(new Color(0xE8E7DD));
        preview.fillRect(0, 0, board.getWidth(), board.getHeight());
        preview.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        preview.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        preview.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Object value : props) {
            var p = (Map<?, ?>) value;
            String id = (String) p.get("id");
            int w = ((Number) p.get("width")).intValue(), h = ((Number) p.get("height")).intValue();
            int col = index % 5,
                    row = index / 5,
                    x0 = col * sheet.getWidth() / 5,
                    x1 = (col + 1) * sheet.getWidth() / 5,
                    y0 = row * sheet.getHeight() / 4,
                    y1 = (row + 1) * sheet.getHeight() / 4;
            BufferedImage cell = sheet.getSubimage(x0, y0, x1 - x0, y1 - y0);
            BufferedImage art = trim(cell);
            if (id.equals("wall_lamp"))
                art =
                        trim(
                                ImageIO.read(
                                        Path.of(
                                                        "assets/reference/interior/wall-lamp-front-source.png")
                                                .toFile()));
            BufferedImage out = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(art, 0, 0, w * 2, h * 2, null);
            g.dispose();
            if (id.startsWith("bed_")
                    || id.startsWith("bookshelf_")
                    || id.startsWith("bar_shelf_")
                    || id.equals("wall_lamp"))
                PaletteOutline.apply(
                        out, new Color(id.equals("wall_lamp") ? 0x4A4657 : 0x735746), 3, 0);
            ImageIO.write(out, "png", output.resolve(id + ".png").toFile());
            double scale = Math.min(170.0 / out.getWidth(), 168.0 / out.getHeight());
            int pw = (int) (out.getWidth() * scale), ph = (int) (out.getHeight() * scale);
            preview.drawImage(
                    out, col * 200 + (200 - pw) / 2, row * 210 + 8 + (168 - ph), pw, ph, null);
            preview.setColor(new Color(0x594E45));
            preview.drawString(id, col * 200 + 12, row * 210 + 197);
            index++;
        }
        preview.dispose();
        ImageIO.write(
                board, "png", Path.of("assets/reference/interior/catalog-preview.png").toFile());
        exportHorizontalBeds(output);
    }

    /** Performs the export horizontal beds operation for GenerateInteriorTextures. */
    private static void exportHorizontalBeds(Path output) throws Exception {
        BufferedImage sheet =
                ImageIO.read(
                        Path.of("assets/reference/interior/horizontal-beds-source.png").toFile());
        String[] ids = {"bed_blue", "bed_coral", "bed_sage"};
        BufferedImage preview = new BufferedImage(720, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D board = preview.createGraphics();
        board.setColor(new Color(0xE8E7DD));
        board.fillRect(0, 0, 720, 180);
        for (int i = 0; i < ids.length; i++) {
            int y = i * sheet.getHeight() / 3, end = (i + 1) * sheet.getHeight() / 3;
            BufferedImage art = trim(sheet.getSubimage(0, y, sheet.getWidth(), end - y));
            BufferedImage out = new BufferedImage(192, 144, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(art, 0, 0, 192, 144, null);
            g.dispose();
            PaletteOutline.apply(out, new Color(0x735746), 3, 0);
            ImageIO.write(out, "png", output.resolve(ids[i] + "_horizontal.png").toFile());
            board.drawImage(out, i * 240 + 24, 8, null);
            board.setColor(new Color(0x594E45));
            board.drawString(ids[i] + " horizontal", i * 240 + 12, 170);
        }
        board.dispose();
        ImageIO.write(
                preview,
                "png",
                Path.of("assets/reference/interior/horizontal-beds-preview.png").toFile());
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
