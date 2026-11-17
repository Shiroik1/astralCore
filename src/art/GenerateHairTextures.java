package art;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

import javax.imageio.ImageIO;

/** Explicit offline asset build, never called by startup or packaging. */
public final class GenerateHairTextures {
    /** Prevents instantiation of this utility class. */
    private GenerateHairTextures() {}

    /** Resolves the render atlas result used by GenerateHairTextures. */
    public static BufferedImage renderAtlas(CharacterOutfit outfit) {
        int n = HairTextures.FRAME;
        BufferedImage atlas = new BufferedImage(n * 4, n * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D all = atlas.createGraphics();
        for (int view = 0; view < 4; view++)
            for (int layer = 0; layer < 2; layer++) {
                Graphics2D g = (Graphics2D) all.create();
                g.translate(view * n, layer * n);
                g.clipRect(0, 0, n, n);
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(
                        RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.scale(n / 80.0, n / 80.0);
                g.translate(8, 16);
                if (view == 2) {
                    g.translate(64, 0);
                    g.scale(-1, 1);
                }
                boolean side = view >= 2, back = view == 1;
                if (outfit.details().hairStyle() < 0) {
                    if (layer == 1) CharacterClothing.legacyLongHair(g, outfit, side, back, 0);
                    else CharacterClothing.legacyHair(g, outfit, side, back);
                } else if (layer == 1) HairArtGenerator.rear(g, outfit, side, back, 0);
                else HairArtGenerator.crown(g, outfit, side, back);
                g.dispose();
            }
        all.dispose();
        return atlas;
    }

    /** Performs the export operation for GenerateHairTextures. */
    private static void export(Path root, CharacterOutfit o) throws Exception {
        Path file = root.resolve(HairTextures.resource(o).substring("/hair/".length()));
        Files.createDirectories(file.getParent());
        ImageIO.write(renderAtlas(o), "png", file.toFile());
    }

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "res/hair" : args[0]);
        Files.createDirectories(root);
        for (int style = 0; style < 40; style++) {
            for (int color = 0; color < Hairstyles.COLORS.length; color++)
                export(root, Hairstyles.outfit(CharacterOutfit.ADVENTURER, style, color));
            System.out.println("Exported hairstyle " + (style + 1) + "/40");
        }
        for (CharacterOutfit.Theme theme : CharacterOutfit.Theme.values())
            for (boolean longHair : new boolean[] {false, true})
                export(root, CharacterOutfit.themed(theme, longHair));
        export(
                root,
                new CharacterOutfit(new Color(0x704A38), null, null, null, null, null)); // merchant
        Files.writeString(
                root.resolve("manifest.properties"),
                "version=1\n"
                        + "frameSize=320\n"
                        + "columns=4\n"
                        + "rows=2\n"
                        + "views=down,up,left,right\n"
                        + "layers=crown,rear\n"
                        + "styles=40\n"
                        + "colors=20\n"
                        + "legacyAtlases=7\n"
                        + "originX=8\n"
                        + "originY=16\n"
                        + "viewUnits=80\n");
        System.out.println("Exported 807 PNG atlases (6,456 layers) to " + root.toAbsolutePath());
    }
}
