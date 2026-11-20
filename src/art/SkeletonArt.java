package art;

import entity.SpriteAnimation;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.Map;

/**
 * Reference-authored ivory skull, narrow ribs and ragged taupe cloth; shared transparent pose
 * atlases.
 */
public final class SkeletonArt {
    public static final int WIDTH = 80, HEIGHT = 96;
    public static final int WALK_FRAMES = 20;
    private static final Color BONE = new Color(0xF1DAC1),
            SHADE = new Color(0xC4ADA0),
            DARK = new Color(0x8A736C),
            CLOTH = new Color(0xA18D7B),
            RAG = new Color(0x796C71),
            GLOW = new Color(0xA65F68);
    private static final Map<Boolean, Map<String, SpriteAnimation>> ATLAS = new HashMap<>();

    /** Prevents instantiation of this utility class. */
    private SkeletonArt() {}

    /** Copies the shared directional skeleton atlas into an entity’s independent animation map. */
    public static synchronized void build(Map<String, SpriteAnimation> into, boolean moon) {
        SpriteAnimation.copyInto(into, ATLAS.computeIfAbsent(moon, SkeletonArt::atlas));
    }

    /** Builds and caches directional frames for idle, walking, emergence and combat poses. */
    private static Map<String, SpriteAnimation> atlas(boolean moon) {
        Map<String, SpriteAnimation> out = new HashMap<>();
        for (String pose :
                new String[] {
                    "idle", "walk", "emerge", "swipe", "lunge", "rake", "hurt", "death", "retreat"
                })
            for (String dir : new String[] {"down", "up", "left", "right"}) {
                boolean loop = pose.equals("walk") || pose.equals("idle");
                BufferedImage[] frames = new BufferedImage[pose.equals("walk") ? WALK_FRAMES : 12];
                for (int f = 0; f < frames.length; f++)
                    frames[f] =
                            frame(
                                    pose,
                                    dir,
                                    f / (loop ? (double) frames.length : frames.length - 1.0),
                                    moon);
                SpriteAnimation animation = new SpriteAnimation(frames, WIDTH, HEIGHT);
                animation.frameDelay = 6;
                out.put(pose + "_" + dir, animation);
            }
        return Map.copyOf(out);
    }

    /** Draws or constructs the ellipse shape used by this artwork. */
    private static void ellipse(Graphics2D g, Color c, double x, double y, double w, double h) {
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x, y, w, h));
    }

    /** Draws or constructs the bone shape used by this artwork. */
    private static void bone(Graphics2D g, double x, double y, double tx, double ty, float w) {
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(SHADE);
        g.draw(new Line2D.Double(x + 1, y + 1, tx + 1, ty + 1));
        g.setColor(BONE);
        g.draw(new Line2D.Double(x, y, tx, ty));
    }

    /** Resolves the ease result used by SkeletonArt. */
    private static double ease(double t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Draws or constructs the leg shape used by this artwork. */
    private static void leg(
            Graphics2D g, double hip, double phase, boolean walking, boolean side, boolean back) {
        double cycle = (phase / (Math.PI * 2)) % 1;
        // The stance foot travels back evenly; the bent knee brings the free foot forward.
        double swing =
                walking ? (cycle < .6 ? 1 - 2 * cycle / .6 : -1 + 2 * ease((cycle - .6) / .4)) : 0;
        double lift = walking && cycle >= .6 ? Math.sin((cycle - .6) / .4 * Math.PI) * 5 : 0;
        double footX = hip + (side ? -swing * 12 : swing * .8),
                footY = 82 - lift + (side ? 0 : swing * (back ? -3 : 3));
        double kneeX = hip + (footX - hip) * .4 - (side ? lift * .7 : 0), kneeY = 72 - lift * .55;
        bone(g, hip, 62, kneeX, kneeY, 4);
        bone(g, kneeX, kneeY, footX, footY, 3.5f);
        ellipse(g, BONE, footX - (side ? 6 : 4), footY - 1, side ? 10 : 8, 4);
    }

    /** Resolves the frame result used by SkeletonArt. */
    private static BufferedImage frame(String pose, String dir, double t, boolean moon) {
        BufferedImage hi = new BufferedImage(WIDTH * 3, HEIGHT * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hi.createGraphics();
        g.scale(3, 3);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(0, 9);
        boolean side = dir.equals("left") || dir.equals("right"), back = dir.equals("up");
        if (dir.equals("right")) {
            g.translate(WIDTH, 0);
            g.scale(-1, 1);
        }
        boolean walking = pose.equals("walk"), idle = pose.equals("idle");
        double phase = t * Math.PI * 2;
        double walk = walking ? Math.sin(phase) : 0, breath = idle ? Math.sin(phase) : 0;
        boolean emerging = pose.equals("emerge"), retreat = pose.equals("retreat");
        double progress = retreat ? 1 - t : t;
        double rise = (emerging || retreat) ? (1 - Math.min(1, (progress - .44) / .56)) * 68 : 0;
        if (emerging || retreat) {
            ellipse(g, new Color(0x8B7D70), 23, 82, 34, 4);
            g.setStroke(new BasicStroke(1));
            g.setColor(DARK);
            g.draw(new Line2D.Double(25, 86, 18, 89));
            g.draw(new Line2D.Double(53, 85, 60, 88));
            if (progress < .22) {
                g.dispose();
                return downsample(hi);
            }
            if (progress < .44) {
                bone(g, 28, 86, 29, 77, 4);
                for (int i = 0; i < 3; i++) bone(g, 27 + i * 2, 78, 26 + i * 2, 73, 1.5f);
                g.dispose();
                return downsample(hi);
            }
            g.clip(new Rectangle2D.Double(0, 0, 80, 86));
            g.translate(0, rise);
        }
        if (pose.equals("hurt")) g.translate(-Math.sin(t * Math.PI) * 3, Math.sin(t * Math.PI) * 2);
        if (pose.equals("death")) {
            g.translate(40, 86);
            g.rotate(t * 1.3);
            g.scale(1, 1 - t * .65);
            g.translate(-40, -86);
            g.setComposite(AlphaComposite.SrcOver.derive((float) (1 - t * .85)));
        }
        boolean striking = pose.equals("swipe") || pose.equals("rake") || pose.equals("lunge");
        // Atlas frames 0–4 anticipate, 5–8 strike, and 9–11 recover.
        double strike =
                t <= 4 / 11.0
                        ? -ease(t / (4 / 11.0))
                        : t <= 8 / 11.0
                                ? .65 + .35 * ease((t - 5 / 11.0) / (3 / 11.0))
                                : 1 - ease((t - 8 / 11.0) / (3 / 11.0));
        double armRaise =
                t <= 4 / 11.0
                        ? ease(t / (4 / 11.0))
                        : t <= 8 / 11.0 ? 1 : 1 - ease((t - 8 / 11.0) / (3 / 11.0));
        double arm = striking ? strike * (side ? 26 : 18) : walk * 7 + breath * .5;
        if (pose.equals("rake") && !side) arm = -arm;
        double crouch = pose.equals("lunge") ? armRaise * (2 + Math.max(0, strike) * 4) : 0;
        g.translate(0, crouch);
        leg(g, side ? 42 : 45, phase + Math.PI, walking, side, back);
        leg(g, side ? 38 : 35, phase, walking, side, back);
        double hem = walking ? Math.sin(phase - .6) * 1.8 : breath * .35;
        g.setColor(RAG);
        Path2D skirt = new Path2D.Double();
        skirt.moveTo(29, 53);
        skirt.lineTo(51, 53);
        skirt.lineTo(54 + hem, 67);
        skirt.lineTo(48 + hem, 63);
        skirt.lineTo(44 + hem, 70);
        skirt.lineTo(39 + hem, 65);
        skirt.lineTo(34 + hem, 69);
        skirt.lineTo(30 + hem, 63);
        skirt.lineTo(26 + hem, 66);
        skirt.closePath();
        g.fill(skirt);
        g.setColor(CLOTH);
        Path2D front = new Path2D.Double();
        front.moveTo(31, 54);
        front.lineTo(49, 54);
        front.lineTo(43 + hem * .7, 69);
        front.lineTo(40 + hem * .7, 65);
        front.lineTo(36 + hem * .7, 67);
        front.closePath();
        g.fill(front);
        g.fillRoundRect(28, 52, 24, 5, 4, 4);
        // Upper-body sway is separate from planted feet; the skull follows slightly later.
        g.translate(walk * .8, breath * .55 + (walking ? Math.cos(phase * 2) * .65 : 0));
        g.rotate(
                walk * .045 + breath * .008 + (striking ? strike * (side ? -.07 : .09) : 0),
                40,
                54);
        if (side) {
            // The far arm counter-swings behind the ribs instead of both arms moving together.
            double far = walking ? -walk * 6 : striking ? arm * .45 : 0;
            Composite opacity = g.getComposite();
            float alpha = opacity instanceof AlphaComposite a ? a.getAlpha() : 1;
            g.setComposite(AlphaComposite.SrcOver.derive(alpha * .7f));
            bone(g, 42, 35, 42 - far * .4, 45, 3);
            bone(g, 42 - far * .4, 45, 40 - far, 54 - (striking ? armRaise * 7 : 0), 2.8f);
            g.setComposite(opacity);
        }
        bone(g, 40, 31, 40, 51, 4);
        for (int rib = 0; rib < 3; rib++) {
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(BONE);
            double y = 36 + rib * 5;
            g.draw(new QuadCurve2D.Double(40, y, side ? 26 : 24, y + 5, side ? 32 : 29, y - 1));
            if (!side) g.draw(new QuadCurve2D.Double(40, y, 56, y + 5, 51, y - 1));
        }
        if (back) {
            ellipse(g, BONE, 29, 33, 10, 10);
            ellipse(g, BONE, 42, 33, 10, 10);
        }
        double
                handY =
                        emerging && progress < .8
                                ? 86 - rise
                                : striking
                                        ? 55
                                                + ((side ? 43 : back ? 35 : 57) - 55) * armRaise
                                                - Math.abs(strike) * 3
                                        : 55 + (walking ? Math.sin(phase + .6) : 0),
                elbowY = emerging && progress < .8 ? (34 + handY) / 2 : 43;
        double shoulderX = side ? 40 : 29,
                elbowX = shoulderX - (side ? 1 : 4) - arm * .45,
                handX = shoulderX - (side ? 3 : 6) - arm;
        bone(g, shoulderX, 34, elbowX, elbowY, 4);
        bone(g, elbowX, elbowY, handX, handY, 3);
        ellipse(g, BONE, handX - 3, handY - 2, 6, 7);
        if (!side) {
            double other = striking ? -arm : -walk * 7;
            double otherY = striking ? handY : 55 - (walking ? Math.sin(phase + .6) : 0);
            bone(g, 51, 34, 55 + other * .3, 43, 4);
            bone(g, 55 + other * .3, 43, 57 + other, otherY, 3);
            ellipse(g, BONE, 55 + other, otherY - 2, 6, 7);
        }
        g.rotate(walking ? -.022 * Math.sin(phase - .35) : breath * .008, 40, 24);
        ellipse(g, SHADE, 23, 8, 35, 28);
        ellipse(g, BONE, 22, 5, 36, 29);
        g.setColor(BONE);
        g.fillRoundRect(side ? 22 : 28, 23, side ? 20 : 24, 14, 6, 6);
        if (!back) {
            ellipse(g, DARK, side ? 24 : 28, 21, side ? 5 : 7, 9);
            if (!side) ellipse(g, DARK, 45, 21, 7, 9);
            g.setColor(DARK);
            Path2D nose = new Path2D.Double();
            nose.moveTo(side ? 24 : 40, 28);
            nose.lineTo(side ? 22 : 37, 32);
            nose.lineTo(side ? 26 : 43, 32);
            nose.closePath();
            g.fill(nose);
            g.setStroke(new BasicStroke(1));
            g.setColor(SHADE);
            for (int x = 31; x <= 48; x += 5) if (!side || x < 37) g.drawLine(x, 34, x, 37);
            if (moon) {
                ellipse(g, GLOW, side ? 25 : 30, 24, 3, 3);
                if (!side) ellipse(g, GLOW, 47, 24, 3, 3);
            }
        } else {
            ellipse(g, SHADE, 29, 27, 23, 9);
            bone(g, 40, 34, 40, 38, 4);
        }
        if (pose.equals("hurt")) {
            g.setComposite(AlphaComposite.SrcAtop.derive(.15f));
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 80, 96);
        }
        g.dispose();
        return downsample(hi);
    }

    /** Resolves the downsample result used by SkeletonArt. */
    private static BufferedImage downsample(BufferedImage hi) {
        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(hi, 0, 0, WIDTH, HEIGHT, null);
        g.dispose();
        return SpriteContour.apply(out);
    }
}
