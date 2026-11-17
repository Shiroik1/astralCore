package art;

import java.awt.*;
import java.awt.geom.Path2D;

/**
 * The reference's eight poses, traced as single white silhouettes. The final pose leaves the
 * particles on their own.
 */
public final class MeleeSlash {
    /** Prevents instantiation of this utility class. */
    private MeleeSlash() {}

    private static final Shape[] FRAMES = {
        trace(4, 34, 30, 30, 63, 15, 82, 0, 75, 17, 45, 36, 4, 34),
        trace(
                40, -12, 65, -31, 91, -25, 91, -9, 89, 11, 55, 34, 0, 38, 34, 27, 69, 4, 68, -7, 67,
                -16, 51, -16, 40, -12),
        trace(
                18, -23, 42, -43, 81, -45, 95, -27, 113, -4, 75, 28, 37, 36, 23, 39, 12, 40, 1, 40,
                34, 29, 67, 5, 71, -10, 75, -24, 53, -30, 18, -23),
        trace(
                13, -28, 40, -48, 79, -51, 96, -31, 108, -18, 106, 1, 87, 18, 69, 34, 32, 42, 0, 41,
                37, 31, 66, 9, 70, -7, 75, -24, 51, -35, 13, -28),
        trace(
                24, -30, 47, -44, 83, -46, 96, -27, 111, -7, 94, 16, 70, 27, 52, 35, 29, 40, 9, 40,
                36, 29, 71, 8, 74, -9, 78, -24, 56, -34, 24, -30),
        trace(
                43, -27, 65, -38, 91, -36, 100, -20, 111, -4, 99, 12, 79, 22, 70, 27, 58, 30, 46,
                33, 63, 21, 79, 7, 81, -6, 82, -18, 64, -25, 43, -27),
        trace(
                64, -24, 80, -31, 98, -26, 103, -14, 110, -1, 98, 14, 78, 20, 88, 9, 91, -1, 86,
                -10, 82, -16, 75, -21, 64, -24)
    };
    private static final float[] OPACITY = {0.47f, 0.75f, 0.92f, 1f, 0.89f, 0.63f, 0.34f};

    /** Renders this component into the supplied Java2D graphics context. */
    public static void draw(
            Graphics2D graphics,
            float x,
            float y,
            double heading,
            float reach,
            float width,
            int ticksFromHit,
            boolean reverse) {
        int frame = ticksFromHit + 3;
        if (frame < 0 || frame >= FRAMES.length) return;
        var g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.rotate(heading);
        boolean mirror = reverse ^ (Math.cos(heading) < -0.5);
        g.scale(reach / 100f, width / 84f * (mirror ? -1 : 1));
        g.translate(9, 0);
        g.setComposite(AlphaComposite.SrcOver.derive(OPACITY[frame]));
        g.setColor(Color.WHITE);
        g.fill(FRAMES[frame]);
        g.dispose();
    }

    /** Resolves the heading result used by MeleeSlash. */
    public static double heading(String direction) {
        return switch (direction) {
            case "up" -> -Math.PI / 2;
            case "down" -> Math.PI / 2;
            case "left" -> Math.PI;
            default -> 0;
        };
    }

    /** Resolves the trace result used by MeleeSlash. */
    private static Shape trace(float x, float y, float... curves) {
        var path = new Path2D.Float();
        path.moveTo(x, y);
        for (int i = 0; i < curves.length; i += 6)
            path.curveTo(
                    curves[i],
                    curves[i + 1],
                    curves[i + 2],
                    curves[i + 3],
                    curves[i + 4],
                    curves[i + 5]);
        path.closePath();
        return path;
    }
}
