package art;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ObjectPreview {
    static final int T = 48;

    public static void main(String[] args) throws Exception {
        new File("art_preview").mkdirs();
        ObjectArt art = new ObjectArt(T);
        ImageIO.write(sheet(art), "png", new File("art_preview/objects_sheet.png"));
        ImageIO.write(scene(art), "png", new File("art_preview/town_scene.png"));
        ImageIO.write(ArtPreview.scene(new GroundTiles(T)), "png", new File("art_preview/ground_scene.png"));
        System.out.println("Wrote objects_sheet.png, town_scene.png and ground_scene.png in art_preview/");
    }

    static List<ObjectArt.Sprite> all(ObjectArt a){
        List<ObjectArt.Sprite> list = new ArrayList<>();
        list.add(a.oak(1));
        list.add(a.oak(2));
        list.add(a.pine(1));
        list.add(a.pine(2));
        list.add(a.bush(false));
        list.add(a.bush(true));
        list.add(a.rockSmall(1));
        list.add(a.rockLarge(1));
        list.add(a.stump());
        list.add(a.fenceH());
        list.add(a.fenceV());
        list.add(a.house("house_coral", Palette.ROOF_CORAL, 1));
        list.add(a.house("house_thatch", Palette.ROOF_THATCH, 2));
        list.add(a.house("house_slate", Palette.ROOF_SLATE, 3));
        list.add(a.lantern());
        list.add(a.well());
        list.add(a.sign());
        list.add(a.barrel());
        list.add(a.crate());
        list.add(a.chest());
        return list;
    }

    //SHEET: every object at 2x on the ground colour, with the collision box in red and the foot point marked
    static BufferedImage sheet(ObjectArt art){
        List<ObjectArt.Sprite> sprites = all(art);
        int pad = 24, maxW = 2000, scale = 2;

        int x = pad, y = pad, rowH = 0;
        int[][] pos = new int[sprites.size()][2];
        for(int i = 0; i < sprites.size(); i++){
            ObjectArt.Sprite sp = sprites.get(i);
            int w = sp.image.getWidth() * scale, h = sp.image.getHeight() * scale;
            if(x + w + pad > maxW){
                x = pad;
                y += rowH + pad + 18;
                rowH = 0;
            }
            pos[i][0] = x;
            pos[i][1] = y;
            x += w + pad;
            rowH = Math.max(rowH, h);
        }
        int totalH = y + rowH + pad + 24;

        BufferedImage img = new BufferedImage(maxW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Palette.GROUND[1]);
        g.fillRect(0, 0, maxW, totalH);

        for(int i = 0; i < sprites.size(); i++){
            ObjectArt.Sprite sp = sprites.get(i);
            int px = pos[i][0], py = pos[i][1];
            int w = sp.image.getWidth() * scale, h = sp.image.getHeight() * scale;
            g.drawImage(sp.image, px, py, w, h, null);

            if(sp.collision.width > 0){
                g.setColor(new Color(255, 60, 60, 220));
                g.setStroke(new BasicStroke(1f));
                g.drawRect(px + (sp.footX + sp.collision.x) * scale, py + (sp.footY + sp.collision.y) * scale,
                        sp.collision.width * scale, sp.collision.height * scale);
            }
            g.setColor(new Color(255, 60, 60));
            g.fillOval(px + sp.footX * scale - 3, py + sp.footY * scale - 3, 6, 6);

            g.setColor(Color.black);
            g.drawString(sp.name + (sp.interactive ? "  (interactive)" : ""), px, py + h + 14);
        }
        g.dispose();
        return img;
    }

    //TOWN SCENE: the ground from before, with objects placed by tile and drawn back-to-front
    record Placement(ObjectArt.Sprite sprite, int col, int row){}

    static BufferedImage scene(ObjectArt art){
        BufferedImage img = ArtPreview.scene(new GroundTiles(T));
        Graphics2D g = img.createGraphics();

        ObjectArt.Sprite oak1 = art.oak(1), oak2 = art.oak(2), pine1 = art.pine(1), pine2 = art.pine(2);
        ObjectArt.Sprite bush = art.bush(false), flowerBush = art.bush(true);
        ObjectArt.Sprite rock = art.rockSmall(1), bigRock = art.rockLarge(1), stump = art.stump();
        ObjectArt.Sprite fenceH = art.fenceH(), fenceV = art.fenceV();
        ObjectArt.Sprite coral = art.house("house_coral", Palette.ROOF_CORAL, 1);
        ObjectArt.Sprite thatch = art.house("house_thatch", Palette.ROOF_THATCH, 2);
        ObjectArt.Sprite slate = art.house("house_slate", Palette.ROOF_SLATE, 3);
        ObjectArt.Sprite lantern = art.lantern(), well = art.well(), sign = art.sign();
        ObjectArt.Sprite barrel = art.barrel(), crate = art.crate(), chest = art.chest();

        List<Placement> list = new ArrayList<>();
        list.add(new Placement(coral, 20, 7));
        list.add(new Placement(thatch, 31, 6));
        list.add(new Placement(slate, 37, 10));

        list.add(new Placement(oak1, 3, 7));
        list.add(new Placement(oak1, 9, 4));
        list.add(new Placement(oak2, 14, 4));
        list.add(new Placement(oak1, 13, 19));
        list.add(new Placement(oak2, 24, 20));
        list.add(new Placement(oak1, 38, 19));
        list.add(new Placement(pine1, 6, 6));
        list.add(new Placement(pine2, 11, 7));
        list.add(new Placement(pine1, 1, 19));
        list.add(new Placement(pine2, 18, 20));
        list.add(new Placement(pine1, 27, 21));
        list.add(new Placement(pine2, 33, 20));
        list.add(new Placement(pine1, 36, 3));

        list.add(new Placement(bush, 14, 9));
        list.add(new Placement(bush, 24, 11));
        list.add(new Placement(flowerBush, 16, 15));
        list.add(new Placement(flowerBush, 8, 16));
        list.add(new Placement(rock, 10, 10));
        list.add(new Placement(rock, 22, 16));
        list.add(new Placement(bigRock, 5, 11));
        list.add(new Placement(bigRock, 30, 19));
        list.add(new Placement(stump, 3, 15));

        for(int c = 4; c <= 9; c++) list.add(new Placement(fenceH, c, 9));
        for(int r = 5; r <= 8; r++) list.add(new Placement(fenceV, 12, r));

        list.add(new Placement(well, 31, 12));
        list.add(new Placement(lantern, 28, 9));
        list.add(new Placement(lantern, 34, 9));
        list.add(new Placement(lantern, 28, 15));
        list.add(new Placement(lantern, 34, 15));
        list.add(new Placement(sign, 27, 11));
        list.add(new Placement(barrel, 23, 8));
        list.add(new Placement(crate, 24, 8));
        list.add(new Placement(chest, 29, 8));

        list.sort(Comparator.comparingInt((Placement p) -> p.row()).thenComparingInt(p -> p.col()));
        for(Placement p : list){
            int x = p.col() * T + T / 2 - p.sprite().footX;
            int y = (p.row() + 1) * T - 4 - p.sprite().footY;
            g.drawImage(p.sprite().image, x, y, null);
        }
        g.dispose();
        return img;
    }
}