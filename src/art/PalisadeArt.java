package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;

/** Cached pointed-log geometry; vertical runs retain upright logs and ground-plane depth. */
public final class PalisadeArt {
    private static final ConcurrentHashMap<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private PalisadeArt() {}

    public static boolean accepts(String id) {
        return id.startsWith("palisade");
    }

    public static boolean gate(String id) {
        return id.startsWith("palisade_gate");
    }

    public static BufferedImage frame(
            String id, int mask, boolean vertical, boolean open, int size) {
        return CACHE.computeIfAbsent(
                id + ":" + mask + ":" + vertical + ":" + open + ":" + size,
                key -> render(id, mask, vertical, open, size));
    }

    private static BufferedImage render(
            String id, int mask, boolean vertical, boolean open, int size) {
        BufferedImage out = new BufferedImage(size, size * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.scale(size / 48.0, size / 48.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean gate = gate(id), tail = id.endsWith("_tail");
        boolean v = gate ? vertical : BuildGeometry.vertical(mask, vertical);
        if (v || (mask & 5) != 0) {
            int first = gate || mask == 0 || (mask & 1) != 0 ? 100 : 121;
            int last = gate || mask == 0 || (mask & 4) != 0 ? 142 : 121;
            for (int y = first; y <= last; y += 7) {
                if (gate && open && (tail ? y < 135 : y > 107)) continue;
                log(g, 16, y, 16, gate ? 78 : 94, y % 3);
            }
        }
        if (!v || (!gate && (mask & 10) != 0)) {
            int first = gate || mask == 0 || (mask & 8) != 0 ? 0 : 12;
            int last = gate || mask == 0 || (mask & 2) != 0 ? 48 : 36;
            for (int x = first; x < last; x += 12) {
                if (gate && open && (tail ? x < 36 : x > 0)) continue;
                log(g, x, 144, 12, gate ? (tail ? x == 36 : x == 0) ? 102 : 87 : 102, x / 12 % 3);
            }
        }
        g.dispose();
        return out;
    }

    /** Two continuous rope bands and lighter hewn tips follow each upright log. */
    private static void log(
            Graphics2D g, double x, double base, double w, double h, int variation) {
        Color wood = new Color(0x96705C + variation * 0x030202);
        Path2D p = new Path2D.Double();
        p.moveTo(x, base);
        p.lineTo(x, base - h + 19);
        p.lineTo(x + w / 2, base - h);
        p.lineTo(x + w, base - h + 19);
        p.lineTo(x + w, base);
        p.closePath();
        g.setColor(wood);
        g.fill(p);
        g.setColor(new Color(0xCDA582));
        g.fill(
                new Polygon(
                        new int[] {(int) x, (int) (x + w / 2), (int) (x + w)},
                        new int[] {(int) (base - h + 19), (int) (base - h), (int) (base - h + 19)},
                        3));
        g.setColor(new Color(91, 64, 53, 45));
        g.fill(new Rectangle2D.Double(x + w * .72, base - h + 20, w * .24, h - 20));
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (double y : new double[] {base - 16, base - 59}) {
            g.setColor(new Color(0xCEAD85));
            g.draw(new Line2D.Double(x, y, x + w, y));
            g.setStroke(new BasicStroke(1));
            g.setColor(new Color(0xE1C49D));
            g.draw(new Line2D.Double(x, y - 1, x + w, y - 1));
            g.setStroke(new BasicStroke(3));
        }
    }
}
