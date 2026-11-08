package art;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import entity.SpriteAnimation;

/** Clean paper-cut slime shapes. The Gamepanel global pixel buffer controls pixelation. */
public final class SlimeArt {
    private SlimeArt() {}

    private static final int HI = 256;
    public static final int CANVAS_SIZE = 72;

    /** Builds all runtime animation states at the requested canvas and body scale. */
    public static void buildAnimations(Map<String, SpriteAnimation> sprites, int canvasSize, float bodyScale) {
        buildAnimations(sprites, canvasSize, bodyScale, 0f);
    }

    /** artOffsetX is in the 64-unit design space and aligns oversized collision boxes. */
    public static void buildAnimations(Map<String, SpriteAnimation> sprites, int canvasSize,
                                       float bodyScale, float artOffsetX) {
        buildAnimations(sprites, canvasSize, bodyScale, artOffsetX, false);
    }

    /** Same cutout rig, with a permanent angry expression for the Slime King. */
    public static void buildBossAnimations(Map<String, SpriteAnimation> sprites, int canvasSize,
                                           float bodyScale, float artOffsetX) {
        buildAnimations(sprites, canvasSize, bodyScale, artOffsetX, true);
    }

    private static void buildAnimations(Map<String, SpriteAnimation> sprites, int canvasSize,
                                        float bodyScale, float artOffsetX, boolean angry) {
        String[] directions = {"down", "up", "left", "right"};
        for (String direction : directions) {
            BufferedImage[] idle = new BufferedImage[6];
            for (int i = 0; i < idle.length; i++) idle[i] = idleFrame(i, direction, canvasSize, bodyScale, artOffsetX, angry);
            sprites.put("idle_" + direction, new SpriteAnimation(idle, canvasSize, canvasSize));

            BufferedImage[] walk = new BufferedImage[8];
            for (int i = 0; i < walk.length; i++) walk[i] = walkFrame(direction, i, canvasSize, bodyScale, artOffsetX, angry);
            SpriteAnimation walkAnimation = new SpriteAnimation(walk, canvasSize, canvasSize);
            walkAnimation.frameDelay = 5;
            sprites.put("walk_" + direction, walkAnimation);

            BufferedImage[] attack = new BufferedImage[10];
            for (int i = 0; i < attack.length; i++) attack[i] = attackFrame(direction, i, canvasSize, bodyScale, artOffsetX, angry);
            sprites.put("attack_" + direction, new SpriteAnimation(attack, canvasSize, canvasSize));

            BufferedImage[] death = new BufferedImage[10];
            for (int i = 0; i < death.length; i++) death[i] = deathFrame(direction, i, canvasSize, bodyScale, artOffsetX, angry);
            sprites.put("death_" + direction, new SpriteAnimation(death, canvasSize, canvasSize));
        }
    }

    public static BufferedImage idleFrame(int frame) {
        return idleFrame(frame, "down", CANVAS_SIZE, 1f, 0f, false);
    }

    private static BufferedImage idleFrame(int frame, String direction, int canvasSize, float bodyScale, float artOffsetX, boolean angry) {
        float breathe = (float) Math.sin(frame / 6f * Math.PI * 2);
        return render(Dir.from(direction), breathe * 0.45f, 0f, 0f, 0f, 0f, canvasSize, bodyScale, artOffsetX, angry);
    }

    public static BufferedImage walkFrame(String direction, int frame) {
        return walkFrame(direction, frame, CANVAS_SIZE, 1f, 0f, false);
    }

    private static BufferedImage walkFrame(String direction, int frame, int canvasSize, float bodyScale, float artOffsetX, boolean angry) {
        float wave = (float) Math.sin(frame / 8f * Math.PI * 2);
        return render(Dir.from(direction), -Math.abs(wave) * 1.4f, -Math.abs(wave) * 0.9f, 0f, wave * 1.2f, 0f, canvasSize, bodyScale, artOffsetX, angry);
    }

    public static BufferedImage attackFrame(String direction, int frame) {
        return attackFrame(direction, frame, CANVAS_SIZE, 1f, 0f, false);
    }

    private static BufferedImage attackFrame(String direction, int frame, int canvasSize, float bodyScale, float artOffsetX, boolean angry) {
        // The game applies damage on frames 6-8; those frames are the lunge.
        float[] curve = {0f, .18f, .36f, .54f, .72f, .88f, 1f, .82f, .42f, 0f};
        float attack = curve[frame % curve.length];
        return render(Dir.from(direction), 0f, -attack * .9f, attack, 0f, 0f, canvasSize, bodyScale, artOffsetX, angry);
    }

    private static BufferedImage deathFrame(String direction, int frame, int canvasSize, float bodyScale, float artOffsetX, boolean angry) {
        float progress = frame / 9f;
        return render(Dir.from(direction), progress * 3f, -progress * 6f, 0f, 0f, progress, canvasSize, bodyScale, artOffsetX, angry);
    }

    private static BufferedImage render(Dir dir, float bob, float squash, float attack, float sway,
                                        float deathProgress, int canvasSize, float bodyScale, float artOffsetX, boolean angry) {
        BufferedImage hi = new BufferedImage(HI, HI, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(HI / 64f, HI / 64f);

        AffineTransform base = g.getTransform();
        g.translate(artOffsetX + dir.dx * attack * 4.5f + sway, bob + dir.dy * attack * 3f);
        // Entity.draw centers every canvas inside one tile. Larger boss canvases
        // need a lower foot anchor so their art and collision shadow share ground.
        float footY = 52f + (canvasSize - CANVAS_SIZE) / 6f;
        g.translate(32, footY);
        g.scale(bodyScale * (1f + squash * .045f + attack * .10f + deathProgress * .35f),
                bodyScale * (1f - squash * .035f - attack * .08f - deathProgress * .72f));
        // The body artwork has its feet at y=52. Keep that point on the
        // selected ground anchor even when the boss body is scaled up.
        g.translate(-32, -52);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - deathProgress * .72f));
        drawBody(g, dir, attack, angry);
        g.setTransform(base);
        g.dispose();

        BufferedImage out = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D output = out.createGraphics();
        output.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        output.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        output.drawImage(hi, 0, 0, canvasSize, canvasSize, null);
        output.dispose();
        return out;
    }

    private static void drawBody(Graphics2D g, Dir dir, float attack, boolean angry) {
        // Small creature inside the shared 64-unit character canvas.
        Shape body = new RoundRectangle2D.Float(17, 32, 30, 20, 14, 14);
        Shape lowerShade = new RoundRectangle2D.Float(18.5f, 42, 27, 8.5f, 8, 8);

        Color outline = new Color(42, 76, 61);
        Color base = new Color(122, 195, 151);
        Color shade = new Color(86, 157, 119);
        Color highlight = new Color(202, 239, 208);

        g.setColor(base);
        g.fill(body);
        g.setColor(shade);
        g.fill(lowerShade);
        g.setColor(outline);
        g.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(body);

        g.setColor(new Color(highlight.getRed(), highlight.getGreen(), highlight.getBlue(), 180));
        g.fill(new Ellipse2D.Float(21, 35, 9, 3.6f));

        if (dir == Dir.UP) {
            g.setColor(new Color(67, 126, 98, 150));
            g.fill(new RoundRectangle2D.Float(24, 38, 16, 7, 6, 6));
            return;
        }

        float eyeShiftX = dir.dx * 1.2f;
        float eyeY = dir == Dir.DOWN ? 41.5f : 40.5f;
        drawEye(g, 27 + eyeShiftX, eyeY);
        drawEye(g, 36 + eyeShiftX, eyeY);

        if (angry) {
            g.setColor(new Color(56, 45, 56));
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(Math.round(24 + eyeShiftX), Math.round(36.5f), Math.round(29 + eyeShiftX), Math.round(38.5f));
            g.drawLine(Math.round(39 + eyeShiftX), Math.round(36.5f), Math.round(34 + eyeShiftX), Math.round(38.5f));
        }

        if (attack > .55f) {
            g.setColor(new Color(255, 247, 229));
            float fangX = dir == Dir.LEFT ? 24 : dir == Dir.RIGHT ? 39 : 31;
            float fangY = dir == Dir.UP ? 36 : 45;
            g.fill(new RoundRectangle2D.Float(fangX, fangY, 2.8f, 3.2f, 1.5f, 1.5f));
        }
    }

    private static void drawEye(Graphics2D g, float x, float y) {
        g.setColor(new Color(38, 62, 53));
        g.fill(new Ellipse2D.Float(x - 1.7f, y - 2.5f, 3.4f, 5f));
        g.setColor(new Color(255, 255, 255, 190));
        g.fill(new Ellipse2D.Float(x - .65f, y - 1.65f, .9f, 1.15f));
    }

    private enum Dir {
        DOWN(0, 1), UP(0, -1), LEFT(-1, 0), RIGHT(1, 0);
        final int dx, dy;
        Dir(int dx, int dy) { this.dx = dx; this.dy = dy; }
        static Dir from(String value) {
            return switch (value) {
                case "up" -> UP;
                case "left" -> LEFT;
                case "right" -> RIGHT;
                default -> DOWN;
            };
        }
    }
}
