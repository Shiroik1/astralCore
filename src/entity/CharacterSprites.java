package entity;

import art.CharacterArt;
import art.CharacterArt.Dir;
import art.PixelFilter;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Builds the generated chibi body as the same "idle_/run_/attack_" + direction
 * SpriteAnimations the Player already uses, so nothing else in the game changes.
 */
public final class CharacterSprites {
    private CharacterSprites() {}

    /** toggled by /pixel. false = smooth vector look, true = pixel-buffer look. */
    public static boolean pixelMode = true;

    /** Hi-res size the vector art is drawn at before it is shrunk/stored. */
    private static final int HI = 144;
    /** Low-res canvas for pixel mode. Screen size / PIXEL_SCALE must equal this (see canvasSize). */
    public static final int LOW = 24;
    public static final int PIXEL_SCALE = 3;

    public static final int IDLE_FRAMES = 4, RUN_FRAMES = 8, ATTACK_FRAMES = 8;

    /** Screen size (px) of the square canvas every body frame is drawn into. */
    public static int canvasSize() { return LOW * PIXEL_SCALE; }

    /** Fraction of the canvas height where the feet sit (matches CharacterArt's FOOT_Y 60 / 64). */
    public static final float FOOT_FRAC = 60f / 64f;

    private static final Dir[] DIRS = {Dir.DOWN, Dir.UP, Dir.LEFT, Dir.RIGHT};
    private static final String[] NAMES = {"down", "up", "left", "right"};

    public static void build(Map<String, SpriteAnimation> sprites, CharacterArt.Look look) {
        look.shadow = false; // the game already draws its own ground shadow
        look.weaponScale = pixelMode ? 1.6f : 1f;
        int size = canvasSize();
        for (int d = 0; d < 4; d++) {
            BufferedImage[] idle = new BufferedImage[IDLE_FRAMES];
            for (int i = 0; i < IDLE_FRAMES; i++)
                idle[i] = frame(DIRS[d], new CharacterArt.Pose().idle(i / (float) IDLE_FRAMES), look);
            BufferedImage[] run = new BufferedImage[RUN_FRAMES];
            for (int i = 0; i < RUN_FRAMES; i++)
                run[i] = frame(DIRS[d], new CharacterArt.Pose().walk(i / (float) RUN_FRAMES), look);
            BufferedImage[] atk = new BufferedImage[ATTACK_FRAMES];
            for (int i = 0; i < ATTACK_FRAMES; i++)
                atk[i] = frame(DIRS[d], new CharacterArt.Pose().attack(i / (float) (ATTACK_FRAMES - 1)), look);

            sprites.put("idle_" + NAMES[d], new SpriteAnimation(idle, size, size));
            SpriteAnimation r = new SpriteAnimation(run, size, size);
            r.frameDelay = 4; // five ticks per frame: slightly quicker eight-frame run
            sprites.put("run_" + NAMES[d], r);
            sprites.put("attack_" + NAMES[d], new SpriteAnimation(atk, size, size));
        }
    }

    private static BufferedImage frame(Dir dir, CharacterArt.Pose pose, CharacterArt.Look look) {
        BufferedImage hi = CharacterArt.render(dir, pose, look, HI);
        if (!pixelMode) {
            // smooth mode: just scale the vector art to the same on-screen size
            BufferedImage out = new BufferedImage(canvasSize(), canvasSize(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(hi, 0, 0, canvasSize(), canvasSize(), null);
            g.dispose();
            return out;
        }
        BufferedImage low = PixelFilter.toLowRes(hi, LOW, LOW);
        return PixelFilter.upscale(low, PIXEL_SCALE);
    }
}