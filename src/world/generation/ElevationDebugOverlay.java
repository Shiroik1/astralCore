package world.generation;

import main.Gamepanel;

import java.awt.*;

/** Toggle with /elevation; reads cached arrays and never searches connectivity during drawing. */
public final class ElevationDebugOverlay {
    private static String mode = "height";
    private static boolean enabled = Boolean.getBoolean("astral.elevation.debug");
    private static final Color[] COLORS = {
        new Color(120, 175, 120, 85),
        new Color(215, 192, 98, 120),
        new Color(194, 136, 82, 135),
        new Color(148, 112, 175, 145)
    };

    /** Prevents instantiation of this utility class. */
    private ElevationDebugOverlay() {}

    /** Evaluates the toggle condition for ElevationDebugOverlay. */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    /** Performs the mode operation for ElevationDebugOverlay. */
    public static void mode(String value) {
        if (!java.util.Set.of("height", "formations", "edges", "boundary", "access")
                .contains(value))
            throw new IllegalArgumentException("Use height, formations, edges, boundary or access");
        mode = value;
        enabled = true;
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public static void draw(Gamepanel gp, Graphics2D original) {
        if (!Boolean.getBoolean("astral.dev")
                || !enabled
                || !world.TerrainHeightResolver.active(gp)) return;
        var w = gp.generatedWorld;
        var h = w.heights();
        var p = gp.localPlayer();
        int ts = gp.tileSize;
        int left = Math.max(0, Math.floorDiv(p.worldX - p.screenX, ts)),
                top = Math.max(0, Math.floorDiv(p.worldY - p.screenY, ts));
        int right = Math.min(h.width - 1, Math.floorDiv(p.worldX - p.screenX + gp.screenWidth, ts)),
                bottom =
                        Math.min(
                                h.height - 1,
                                Math.floorDiv(p.worldY - p.screenY + gp.screenHeight, ts));
        var g = (Graphics2D) original.create();
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                int px = x * ts - p.worldX + p.screenX, py = y * ts - p.worldY + p.screenY;
                Color color =
                        !h.playable(x, y)
                                ? new Color(65, 55, 75, 180)
                                : COLORS[Math.min(3, h.at(x, y))];
                if (mode.equals("formations") && h.playable(x, y)) {
                    int id = h.formation(x, y);
                    color =
                            id == 0
                                    ? new Color(120, 175, 120, 35)
                                    : Color.getHSBColor((id * .217f) % 1, .40f, .9f);
                } else if (mode.equals("boundary"))
                    color =
                            h.playable(x, y)
                                    ? new Color(120, 175, 120, 35)
                                    : new Color(125, 95, 150, 195);
                else if (mode.equals("access"))
                    color =
                            h.reachable(x, y)
                                    ? new Color(95, 195, 150, 125)
                                    : new Color(210, 95, 105, 150);
                g.setColor(color);
                g.fillRect(px, py, ts, ts);
                if (mode.equals("edges") || mode.equals("height")) {
                    int level = h.at(x, y), mask = CliffTileResolver.mask(h, x, y, level);
                    g.setColor(Color.ORANGE);
                    if (level > 0) {
                        if ((mask & 1) == 0) g.drawLine(px, py, px + ts, py);
                        if ((mask & 2) == 0) g.drawLine(px + ts - 1, py, px + ts - 1, py + ts);
                        if ((mask & 4) == 0) g.drawLine(px, py + ts - 1, px + ts, py + ts - 1);
                        if ((mask & 8) == 0) g.drawLine(px, py, px, py + ts);
                    }
                }
                if (h.playable(x, y) && h.at(x, y) == 0 && !h.reachable(x, y)) {
                    g.setColor(new Color(220, 85, 105, 150));
                    g.drawLine(px, py, px + ts, py + ts);
                }
            }
        var nest = w.slimeNest();
        g.setStroke(new BasicStroke(3));
        g.setColor(Color.CYAN);
        if (nest != null)
            g.drawRect(
                    nest.col() * ts - p.worldX + p.screenX,
                    nest.row() * ts - p.worldY + p.screenY,
                    nest.size() * ts,
                    nest.size() * ts);
        g.setColor(Color.WHITE);
        g.drawOval(
                w.spawnCol * ts - p.worldX + p.screenX,
                w.spawnRow * ts - p.worldY + p.screenY,
                ts,
                ts);
        var lines = h.debugLines();
        int boxHeight = (lines.size() + 1) * 16 + 12,
                boxY = Math.max(8, gp.screenHeight - boxHeight - 12);
        g.setColor(new Color(30, 37, 35, 220));
        g.fillRoundRect(10, boxY, 520, boxHeight, 10, 10);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setColor(new Color(238, 239, 222));
        int lineY = boxY + 18;
        for (String line : lines) {
            g.drawString(line, 20, lineY);
            lineY += 16;
        }
        g.drawString("Overlay: " + mode, 20, lineY);
        g.dispose();
    }
}
