package art;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/** Pre-rendered meadow sheets, shared by every game view. */
public final class GrassArt {
    public static final int TYPES = 3; // short, tall, flowers
    public static final int[] VARIANTS = {4, 4, 5};
    public static final int BEND_FRAMES = 25;
    static final int WIDTH = 44, HEIGHT = 36;
    static final int ROOT_X = WIDTH / 2, ROOT_Y = HEIGHT - 3;
    static final String[] NAMES = {"short", "tall", "flowers"};

    private static final Map<Integer, BufferedImage[][]> CACHE = new HashMap<>();
    private final BufferedImage[][] frames;
    public final int footX, footY;

    public GrassArt(int tileSize){
        footX = scale(ROOT_X, tileSize);
        footY = scale(ROOT_Y, tileSize);
        synchronized(CACHE){
            frames = CACHE.computeIfAbsent(tileSize, GrassArt::load);
        }
    }

    public BufferedImage frame(int type, int level, int variant, int bend){
        return frames[type][(level * VARIANTS[type] + variant) * BEND_FRAMES + bend];
    }

    private static BufferedImage[][] load(int tileSize){
        BufferedImage[][] frames = new BufferedImage[TYPES][];
        for(int type = 0; type < TYPES; type++){
            String path = "/scenery/meadow/" + NAMES[type] + ".png";
            int rows = Palette.GROUND.length * VARIANTS[type];
            try(InputStream stream = GrassArt.class.getResourceAsStream(path)){
                if(stream == null) throw new IOException("Missing meadow sheet " + path);
                BufferedImage sheet = ImageIO.read(stream);
                if(sheet == null || sheet.getWidth() != WIDTH * BEND_FRAMES || sheet.getHeight() != HEIGHT * rows){
                    throw new IOException("Invalid meadow sheet " + path);
                }
                frames[type] = new BufferedImage[rows * BEND_FRAMES];
                for(int row = 0; row < rows; row++) for(int bend = 0; bend < BEND_FRAMES; bend++){
                    var frame = sheet.getSubimage(bend * WIDTH, row * HEIGHT, WIDTH, HEIGHT);
                    frames[type][row * BEND_FRAMES + bend] = tileSize == 48 ? frame : resize(frame, tileSize);
                }
            } catch(IOException e){
                throw new UncheckedIOException(e);
            }
        }
        return frames;
    }

    private static BufferedImage resize(BufferedImage frame, int tileSize){
        var scaled = new BufferedImage(scale(WIDTH, tileSize), scale(HEIGHT, tileSize), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(frame, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        g.dispose();
        return scaled;
    }

    private static int scale(int units, int tileSize){
        return (int) Math.round(units * (tileSize / 48.0));
    }
}
