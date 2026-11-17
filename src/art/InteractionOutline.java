package art;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.WeakHashMap;

/** Cached white silhouette behind the actual sprite, excluding faint baked ground shadows. */
public final class InteractionOutline {
    public static final int THICKNESS = 2;
    public static final int COLOR = 0xF5F4EC;
    private static final int[][] OFFSETS = offsets();
    private static final Map<BufferedImage, BufferedImage> MASKS = new WeakHashMap<>();

    /** Prevents instantiation of this utility class. */
    private InteractionOutline() {}

    /** Renders this component into the supplied Java2D graphics context. */
    public static void draw(Graphics2D g, BufferedImage image, int x, int y) {
        BufferedImage white;
        synchronized (MASKS) {
            white =
                    MASKS.computeIfAbsent(
                            image,
                            source -> {
                                BufferedImage mask =
                                        new BufferedImage(
                                                source.getWidth(),
                                                source.getHeight(),
                                                BufferedImage.TYPE_INT_ARGB);
                                for (int py = 0; py < source.getHeight(); py++)
                                    for (int px = 0; px < source.getWidth(); px++) {
                                        int a = source.getRGB(px, py) >>> 24;
                                        if (a >= 100) mask.setRGB(px, py, (a << 24) | COLOR);
                                    }
                                return mask;
                            });
        }
        for (int[] d : OFFSETS) g.drawImage(white, x + d[0], y + d[1], null);
    }

    /** Resolves the offsets result used by InteractionOutline. */
    private static int[][] offsets() {
        java.util.List<int[]> result = new java.util.ArrayList<>();
        for (int y = -THICKNESS; y <= THICKNESS; y++)
            for (int x = -THICKNESS; x <= THICKNESS; x++)
                if (x * x + y * y <= THICKNESS * THICKNESS && (x != 0 || y != 0))
                    result.add(new int[] {x, y});
        return result.toArray(int[][]::new);
    }
}
