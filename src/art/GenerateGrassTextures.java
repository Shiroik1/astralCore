package art;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Offline vector source for meadow textures and their bend frames. */
public final class GenerateGrassTextures {
    private static final Color FLOWER_CENTER = new Color(0xD6AB70);

    private GenerateGrassTextures(){}

    public static void main(String[] args) throws IOException{
        Path output = Path.of(args.length == 0 ? "res/scenery/meadow" : args[0]);
        Files.createDirectories(output);
        for(int type = 2; type < 3; type++){
            int variants = GrassArt.VARIANTS[type];
            var sheet = new BufferedImage(GrassArt.WIDTH * GrassArt.BEND_FRAMES,
                    GrassArt.HEIGHT * variants * Palette.GROUND.length, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            for(int level = 0; level < Palette.GROUND.length; level++){
                Color ground = Palette.GROUND[level];
                Color[] blades = {
                        ArtKit.mix(ground, Palette.FOLIAGE_DARK, 0.57f),
                        ArtKit.mix(ground, Palette.FOLIAGE_DARK, 0.39f),
                        ArtKit.mix(ground, Palette.FOLIAGE, 0.32f)
                };
                for(int variant = 0; variant < variants; variant++){
                    int y = (level * variants + variant) * GrassArt.HEIGHT;
                    for(int frame = 0; frame < GrassArt.BEND_FRAMES; frame++){
                        double bend = frame * 2.0 / (GrassArt.BEND_FRAMES - 1) - 1;
                        var surface = new ArtKit.Surface(GrassArt.WIDTH, GrassArt.HEIGHT);
                        tuft(surface.g, type, variant, bend, blades);
                        g.drawImage(surface.finish(), frame * GrassArt.WIDTH, y, null);
                    }
                }
            }
            g.dispose();
            ImageIO.write(sheet, "png", output.resolve(GrassArt.NAMES[type] + ".png").toFile());
        }
    }

    private static void tuft(Graphics2D g, int type, int variant, double bend, Color[] tones){
        // The same plant geometry is drawn for every bend and ground shade.
        Random random = new Random(type * 1000L + variant * 37L + 11);
        int count = type == 2 ? 3 : 4 + (type == 1 ? variant % 2 : 0);
        double height = type == 1 ? 21 : type == 0 ? 13 : 8;
        double direction = variant % 2 == 0 ? -1 : 1;
        for(int i = 0; i < count; i++){
            double spread = i / (double) (count - 1) - 0.5;
            double x = GrassArt.ROOT_X + spread * 8.0;
            double h = height * (0.77 + random.nextDouble() * 0.29);
            double lean = spread * 8.5 + direction * 1.35 + bend * h * 0.52;
            double width = 2.2 + random.nextDouble() * 1.4;
            blade(g, x, GrassArt.ROOT_Y, h * (1 - 0.12 * Math.abs(bend)), lean, width, tones[i % tones.length]);
        }
        if(type == 2){
            Color petals = Palette.FLOWERS[variant];
            stem(g, GrassArt.ROOT_X - 2, 12 + variant % 2, -4.5, bend, tones[0], petals, 2.3);
            stem(g, GrassArt.ROOT_X + 2, 15 - variant % 2, 2.5, bend, tones[0], petals, 2.8);
        }
    }

    private static void blade(Graphics2D g, double x, double y, double h, double lean, double width, Color color){
        var blade = new Path2D.Double();
        blade.moveTo(x - width * .45, y);
        blade.curveTo(x - width * .50, y - h * .48, x + lean * .48 - width * .42, y - h * .86,
                x + lean - .35, y - h);
        blade.quadTo(x + lean + .28, y - h - .3, x + lean + .24, y - h + .45);
        blade.curveTo(x + lean * .57 + width * .52, y - h * .78, x + width * .58, y - h * .40,
                x + width * .45, y);
        blade.closePath();
        g.setColor(color);
        g.fill(blade);
    }

    private static void stem(Graphics2D g, double x, double h, double natural, double bend,
                             Color color, Color petals, double radius){
        double lean = natural + bend * h * .55;
        double y = GrassArt.ROOT_Y;
        g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(color);
        g.draw(new QuadCurve2D.Double(x, y, x + lean * .32, y - h * .66, x + lean, y - h));
        blossom(g, x + lean, y - h, radius, petals);
    }

    static void blossom(Graphics2D g, double x, double y, double r, Color petals){
        double d = r * .54;
        g.setColor(petals);
        oval(g, x - d, y, r * .59);
        oval(g, x + d, y, r * .59);
        oval(g, x, y - d, r * .59);
        oval(g, x, y + d, r * .59);
        g.setColor(FLOWER_CENTER);
        oval(g, x, y, r * .32);
    }

    private static void oval(Graphics2D g, double x, double y, double r){
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
    }
}
