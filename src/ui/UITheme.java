package ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Shared Java2D theme in the game's logical viewport; DisplaySettings handles physical/DPI scaling.
 */
public final class UITheme {
    public static final Color PANEL = new Color(23, 25, 28, 225),
            SECONDARY = new Color(36, 39, 42, 200),
            BORDER = new Color(0xF0EEE8),
            INACTIVE = new Color(0x8E9190),
            TEXT = new Color(0xF2F0E9),
            MUTED = new Color(0xB8BAB7),
            ACCENT = new Color(0xD7C29B),
            HP = new Color(0xAD7478),
            MP = new Color(0x759CB5),
            STAMINA = new Color(0x8DAD87),
            DISABLED = new Color(0x747774);
    public static final int WINDOW_WIDTH = 1040,
            WINDOW_HEIGHT = 560,
            SLOT = 46,
            GAP = 8,
            PADDING = 18;
    public static final float BORDER_WIDTH = 1f;
    public static final double TRANSITION = .15;
    private static Font base = new Font(Font.DIALOG, Font.PLAIN, 14);
    private static final Map<Integer, Font> FONTS = new HashMap<>();

    /** Prevents instantiation of this utility class. */
    private UITheme() {}

    /** Performs the base font operation for UITheme. */
    public static void baseFont(Font font) {
        if (font != null && !base.equals(font)) {
            base = font.deriveFont(Font.PLAIN);
            FONTS.clear();
        }
    }

    /** Resolves the font result used by UITheme. */
    public static Font font(int size) {
        return FONTS.computeIfAbsent(size, n -> base.deriveFont(Font.PLAIN, n.floatValue()));
    }

    /** Resolves the window result used by UITheme. */
    public static Rectangle window(int width, int height) {
        int w = Math.min(WINDOW_WIDTH, width - 40), h = Math.min(WINDOW_HEIGHT, height - 140);
        return new Rectangle((width - w) / 2, (height - h) / 2 - 10, w, h);
    }

    /** Performs the panel operation for UITheme. */
    public static void panel(Graphics2D g, Rectangle r) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(r.x + 3, r.y + 4, r.width, r.height, 8, 8);
        g.setColor(PANEL);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        g.setStroke(new BasicStroke(BORDER_WIDTH));
        g.setColor(BORDER);
        g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
    }

    /** Performs the text operation for UITheme. */
    public static void text(Graphics2D g, String s, int x, int y, int size, Color c) {
        g.setFont(font(size));
        g.setColor(c);
        g.drawString(s == null ? "" : s, x, y);
    }

    /** Performs the button operation for UITheme. */
    public static void button(
            Graphics2D g,
            Rectangle r,
            String label,
            boolean enabled,
            boolean hover,
            boolean active) {
        g.setColor(
                active
                        ? new Color(67, 65, 56, 230)
                        : hover && enabled ? new Color(64, 67, 69, 230) : SECONDARY);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 4, 4);
        g.setStroke(new BasicStroke(1));
        g.setColor(enabled ? (hover ? BORDER : active ? ACCENT : INACTIVE) : DISABLED);
        g.drawRoundRect(r.x, r.y, r.width, r.height, 4, 4);
        g.setFont(font(12));
        g.setColor(enabled ? TEXT : DISABLED);
        g.drawString(
                label,
                r.x + (r.width - g.getFontMetrics().stringWidth(label)) / 2,
                r.y + (r.height + g.getFontMetrics().getAscent()) / 2 - 3);
    }

    /** Performs the icon operation for UITheme. */
    public static void icon(Graphics2D g, BufferedImage image, Rectangle r) {
        icon(g, image, r, false);
    }

    /** Performs the item icon operation for UITheme. */
    public static void itemIcon(Graphics2D g, BufferedImage image, Rectangle r) {
        icon(g, image, r, true);
    }

    /** Performs the icon operation for UITheme. */
    private static void icon(Graphics2D g, BufferedImage image, Rectangle r, boolean outlined) {
        if (image == null) return;
        double scale =
                Math.min((r.width - 8.0) / image.getWidth(), (r.height - 8.0) / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale)),
                h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        if (outlined)
            art.ItemOutline.draw(g, image, r.x + (r.width - w) / 2, r.y + (r.height - h) / 2, w, h);
        g.drawImage(image, r.x + (r.width - w) / 2, r.y + (r.height - h) / 2, w, h, null);
    }

    /** Performs the slot operation for UITheme. */
    public static void slot(Graphics2D g, Rectangle r, boolean selected, BufferedImage image) {
        g.setColor(selected ? new Color(94, 84, 62, 155) : SECONDARY);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 4, 4);
        g.setStroke(new BasicStroke(selected ? 1.7f : 1f));
        g.setColor(selected ? BORDER : INACTIVE);
        g.drawRoundRect(r.x, r.y, r.width, r.height, 4, 4);
        itemIcon(g, image, r);
    }

    /** Performs the bar operation for UITheme. */
    public static void bar(
            Graphics2D g, Rectangle r, double value, double max, Color fill, String label) {
        double ratio =
                max > 0 && Double.isFinite(value) ? Math.max(0, Math.min(1, value / max)) : 0;
        g.setColor(SECONDARY);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 3, 3);
        g.setColor(fill);
        g.fillRoundRect(
                r.x + 2,
                r.y + 2,
                Math.max(0, (int) ((r.width - 4) * ratio)),
                Math.max(0, r.height - 4),
                2,
                2);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 3, 3);
        if (label != null) {
            g.setFont(font(11));
            g.setColor(TEXT);
            g.drawString(label, r.x + 7, r.y + (r.height + g.getFontMetrics().getAscent()) / 2 - 2);
        }
    }
}
