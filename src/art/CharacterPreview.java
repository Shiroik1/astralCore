package art;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class CharacterPreview {
    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "preview";
        new File(out).mkdirs();
        CharacterArt.Look look = new CharacterArt.Look();
        CharacterArt.Dir[] dirs = {CharacterArt.Dir.DOWN, CharacterArt.Dir.UP, CharacterArt.Dir.RIGHT, CharacterArt.Dir.LEFT};

        // 1) big vector sheet: 4 dirs idle + walk frames
        int S = 256; int cols = 4;
        BufferedImage sheet = new BufferedImage(S * cols, S * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0xA3A5C3)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < 4; i++) {
            g.drawImage(CharacterArt.render(dirs[i], new CharacterArt.Pose(), look, S), i * S, 0, null);
            g.drawImage(CharacterArt.render(dirs[i], new CharacterArt.Pose().walk(0.25f), look, S), i * S, S, null);
            g.drawImage(CharacterArt.render(dirs[i], new CharacterArt.Pose().attack(0.5f), look, S), i * S, 2 * S, null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(out, "vector_sheet.png"));

        // 2) pixel buffer: render hi-res, shrink to LOW px, upscale
        for (int low : new int[]{32, 48}) {
            int sc = 6;
            BufferedImage ps = new BufferedImage(low * sc * 4, low * sc * 3, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = ps.createGraphics();
            pg.setColor(new Color(0xA3A5C3)); pg.fillRect(0, 0, ps.getWidth(), ps.getHeight());
            for (int i = 0; i < 4; i++) {
                CharacterArt.Pose[] poses = {new CharacterArt.Pose(), new CharacterArt.Pose().walk(0.25f), new CharacterArt.Pose().attack(0.5f)};
                for (int r = 0; r < 3; r++) {
                    BufferedImage hi = CharacterArt.render(dirs[i], poses[r], look, 256);
                    BufferedImage lo = PixelFilter.toLowRes(hi, low, low);
                    pg.drawImage(PixelFilter.upscale(lo, sc), i * low * sc, r * low * sc, null);
                }
            }
            pg.dispose();
            ImageIO.write(ps, "png", new File(out, "pixel_" + low + ".png"));
        }
        System.out.println("done");
    }
}
