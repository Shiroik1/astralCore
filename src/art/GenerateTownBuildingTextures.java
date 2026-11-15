package art;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline vector source for the town buildings and their aligned material masks. */
public final class GenerateTownBuildingTextures {
    private static final int WIDTH = 248, EXPORT_SCALE = 2;
    private static final int TERRACOTTA = 0xC48578;
    private static final int PLASTER = Palette.PLASTER.getRGB(), PLASTER_SHADE = 0xDECBAE;
    private static final int TIMBER = Palette.TIMBER.getRGB(), WOOD = Palette.WOOD.getRGB(), WOOD_LIGHT = 0xC9A17A;
    private static final int STONE = 0xB5A99D, STONE_SHADE = 0x9B8C81;
    private static final int GLASS = 0x778A9C, GLASS_SHADE = 0x65798D;
    private static final int DOOR_RECESS = 0x544753, DOOR_FLOOR = 0x655358;

    private enum Design {
        SHOP("general_shop", 264), HOUSE_OPEN("house_open", 264),
        HOUSE_CLOSED("house_closed", 264), INN("inn", 280);
        final String id;
        final int height;
        Design(String id, int height){
            this.id = id;
            this.height = height;
        }
    }

    private enum Layer {
        // Keep the ivy mask slot so previously saved palette overrides still load.
        ART(""), ROOF("-roof"), IVY("-ivy"), WINDOWS("-windows");
        final String suffix;
        Layer(String suffix){ this.suffix = suffix; }
    }

    private GenerateTownBuildingTextures(){}

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length == 0 ? "res/scenery/buildings" : args[0]);
        Files.createDirectories(output);
        for(Design design : Design.values()){
            for(Layer layer : Layer.values()){
                var surface = new ArtKit.Surface(WIDTH * EXPORT_SCALE, design.height * EXPORT_SCALE);
                surface.g.scale(EXPORT_SCALE, EXPORT_SCALE);
                new Building(surface.g, layer).paint(design);
                Path target = output.resolve(design.id + "-flat" + layer.suffix + ".png");
                ImageIO.write(surface.finish(), "png", target.toFile());
            }
        }
    }

    private static final class Building {
        private static final Color CLEAR = new Color(0, 0, 0, 0);
        private final Graphics2D g;
        private final Layer output;
        private Layer material = Layer.ART;

        Building(Graphics2D g, Layer output){
            this.g = g;
            this.output = output;
            if(output != Layer.ART) g.setComposite(AlphaComposite.Src);
        }

        void paint(Design design){
            if(design == Design.INN){
                inn();
                return;
            }

            facade();
            roof();
            boolean shop = design == Design.SHOP;
            chimney(shop ? 52 : 51, 4, 52);
            dormer(0);
            if(shop){
                shopEntrance();
                window(52, 212, true);
                window(196, 212, true);
                canopy();
                sign();
            } else {
                houseEntrance(design == Design.HOUSE_CLOSED);
                window(57, 208, true);
                window(191, 208, true);
            }
        }

        private void facade(){
            rounded(12, 133, 224, 128, 6, PLASTER);
            rect(12, 143, 224, 15, PLASTER_SHADE);
            rect(12, 177, 224, 8, PLASTER_SHADE);
            rect(12, 238, 224, 6, WOOD);

            for(int x : new int[]{12, 86, 154, 226}){
                rect(x, 145, 10, 98, WOOD);
                rect(x + 7, 145, 3, 98, TIMBER);
            }
            polygon(WOOD, 22, 173, 46, 149, 56, 149, 32, 173);
            polygon(WOOD, 58, 149, 67, 149, 86, 169, 86, 173, 81, 173);
            polygon(WOOD, 164, 169, 184, 149, 194, 149, 170, 173, 164, 173);
            polygon(WOOD, 197, 149, 206, 149, 226, 170, 226, 173, 220, 173);
            rounded(10, 171, 228, 9, 3, TIMBER);
            rounded(10, 171, 228, 6, 3, WOOD);
            foundation();
        }

        private void shopEntrance(){
            // The recess and posts hold the largest contrast on the facade.
            polygon(DOOR_RECESS, 92, 199, 104, 191, 145, 191, 157, 199, 157, 258, 92, 258);
            polygon(DOOR_FLOOR, 93, 244, 155, 244, 155, 258, 93, 258);
            rect(90, 199, 7, 56, WOOD);
            rect(151, 199, 7, 56, WOOD);
            rounded(87, 249, 12, 13, 3, STONE);
            rounded(149, 249, 12, 13, 3, STONE);
        }

        private void houseEntrance(boolean closed){
            polygon(DOOR_RECESS, 96, 194, 110, 181, 138, 181, 152, 194, 152, 256, 96, 256);
            if(closed){
                polygon(TIMBER, 97, 197, 111, 184, 137, 184, 151, 197, 151, 256, 97, 256);
                rounded(100, 195, 48, 60, 3, 0xA48166);
                line(0x97765E, 1.2f, 112, 197, 112, 254);
                line(0x97765E, 1.2f, 126, 197, 126, 254);
                line(0x97765E, 1.2f, 139, 197, 139, 254);
                fill(new Ellipse2D.Double(134, 220, 8, 8), 0x685860);
                fill(new Ellipse2D.Double(136, 221, 4, 4), 0xD0AF84);
            } else {
                rect(96, 242, 56, 14, DOOR_FLOOR);
            }
            polygon(WOOD, 94, 194, 107, 180, 115, 180, 94, 202);
            polygon(WOOD, 154, 194, 141, 180, 133, 180, 154, 202);
            rect(88, 180, 9, 72, WOOD);
            rect(151, 180, 9, 72, WOOD);
            rounded(85, 248, 14, 13, 3, STONE);
            rounded(149, 248, 14, 13, 3, STONE);
        }

        private void foundation(){
            rounded(11, 242, 226, 19, 5, STONE_SHADE);
            rounded(11, 242, 226, 7, 4, STONE);
            for(int x : new int[]{32, 59, 164, 191}){
                rounded(x, 250, 24, 10, 3, STONE);
            }
            rounded(11, 241, 19, 20, 4, 0xC0B4A6);
            rounded(218, 241, 19, 20, 4, 0xC0B4A6);
        }

        private void inn(){
            innWings();
            innFacade();
            innRoof();
        }

        private void innWings(){
            rounded(12, 128, 65, 99, 7, TIMBER);
            rect(18, 146, 54, 69, PLASTER);
            rect(18, 149, 54, 11, PLASTER_SHADE);
            rounded(12, 150, 8, 65, 4, WOOD);
            rect(66, 150, 8, 64, WOOD);
            polygon(WOOD, 21, 200, 41, 179, 47, 179, 21, 206);
            rounded(13, 175, 64, 6, 3, WOOD);
            rounded(12, 211, 65, 16, 6, STONE_SHADE);
            rounded(12, 211, 65, 5, 4, STONE);
            rounded(19, 218, 23, 7, 2, STONE);
            rounded(44, 218, 25, 7, 2, STONE);
            for(int y : new int[]{164, 191}){
                rounded(37, y - 7, 13, 16, 2, TIMBER);
                line(WOOD_LIGHT, 1.2f, 39, y - 5, 48, y + 7);
                line(WOOD_LIGHT, 1.2f, 48, y - 5, 39, y + 7);
            }

            rounded(179, 151, 57, 74, 4, TIMBER);
            rect(183, 157, 47, 64, 0x9E775B);
            polygon(WOOD, 181, 206, 224, 161, 231, 163, 181, 217);
            rounded(228, 171, 8, 54, 4, WOOD);
            for(int y : new int[]{184, 201}){
                rounded(203, y, 23, 19, 5, 0xDAB578);
                rounded(203, y, 23, 12, 4, 0xE9C88D);
                line(WOOD, 1.4f, 211, y + 2, 211, y + 17);
                line(WOOD, 1.4f, 220, y + 2, 220, y + 17);
            }
            innAwning();
        }

        private void innAwning(){
            var canvas = new Path2D.Double();
            canvas.moveTo(180, 105);
            canvas.curveTo(196, 116, 216, 121, 231, 121);
            canvas.quadTo(235, 121, 236, 126);
            canvas.lineTo(243, 181);
            canvas.quadTo(244, 187, 238, 187);
            canvas.curveTo(214, 187, 196, 180, 180, 171);
            canvas.closePath();
            fill(canvas, 0xE8D9C0);

            for(int stripe = 1; stripe < 6; stripe += 2){
                int y = 105 + stripe * 11;
                var cloth = new Path2D.Double();
                cloth.moveTo(180, y);
                cloth.curveTo(196, y + 11, 216, y + 16, 250, y + 16);
                cloth.lineTo(250, y + 27);
                cloth.curveTo(216, y + 27, 196, y + 22, 180, y + 11);
                cloth.closePath();
                var band = new Area(cloth);
                band.intersect(new Area(canvas));
                fill(band, 0x84969D);
            }
        }

        private void innFacade(){
            rounded(59, 144, 130, 132, 5, TIMBER);
            rect(65, 148, 118, 117, PLASTER);
            rect(65, 151, 118, 14, PLASTER_SHADE);
            rect(65, 208, 118, 13, PLASTER_SHADE);

            for(int x : new int[]{59, 119, 181}){
                rect(x, 153, 8, 112, WOOD);
                rect(x + 6, 153, 2, 112, TIMBER);
            }
            polygon(WOOD, 67, 204, 81, 191, 89, 191, 72, 206);
            polygon(WOOD, 176, 206, 159, 191, 167, 191, 182, 204);
            rounded(60, 205, 128, 8, 3, TIMBER);
            rounded(60, 205, 128, 5, 3, WOOD_LIGHT);
            window(95, 179, true);
            window(153, 179, true);
            rounded(59, 259, 130, 17, 6, STONE_SHADE);
            rounded(60, 259, 128, 6, 4, STONE);
            rounded(62, 267, 21, 7, 3, STONE);
            rounded(165, 267, 21, 7, 3, STONE);

            rect(104, 226, 40, 18, TIMBER);
            polygon(DOOR_RECESS, 106, 239, 114, 230, 134, 230, 142, 239, 142, 272, 106, 272);
            rect(106, 262, 36, 10, DOOR_FLOOR);
            rect(85, 215, 21, 55, WOOD);
            rect(142, 215, 21, 55, WOOD);
            polygon(TIMBER, 85, 227, 98, 214, 106, 214, 85, 235);
            polygon(TIMBER, 163, 227, 150, 214, 142, 214, 163, 235);
            rounded(103, 270, 42, 6, 4, STONE);
            rounded(84, 262, 24, 14, 6, 0xC5BAAC);
            rounded(140, 262, 24, 14, 6, 0xC5BAAC);
            innSign();
            lantern(95, 246);
            lantern(153, 246);
        }

        private void innRoof(){
            roofAt(8, 77, 106, 77);
            roofAt(43, 26, 162, 128);
            chimney(76, 4, 62);
            dormer(2);
        }

        private void roofAt(int x, int y, int width, int height){
            AffineTransform original = g.getTransform();
            g.translate(x, y);
            g.scale(width / (double) WIDTH, height / 128.0);
            g.translate(0, -26);
            roof();
            g.setTransform(original);
        }

        private void innSign(){
            rect(108, 199, 4, 11, TIMBER);
            rect(136, 199, 4, 11, TIMBER);
            rounded(104, 207, 40, 23, 7, 0xC6A07A);
            rounded(107, 210, 34, 17, 5, 0x8B6A59);
            int foam = 0xF4E4C8;
            var handle = new Area(new Ellipse2D.Double(125, 215.5, 9, 8));
            handle.subtract(new Area(new Ellipse2D.Double(127, 217, 5, 5)));
            fill(handle, 0xDBA96B);
            rounded(116, 214, 13, 11, 3, 0xDBA96B);
            rect(118, 216, 2, 7, 0xF0C17D);
            rounded(115, 213, 15, 4, 3, foam);
            fill(new Ellipse2D.Double(116, 211.5, 6, 5), foam);
            fill(new Ellipse2D.Double(121, 211, 7, 5), foam);
        }

        private void lantern(int x, int y){
            line(0x61515A, 1.8f, x, y - 11, x, y - 6);
            polygon(0x685860, x - 5, y - 5, x, y - 9, x + 5, y - 5);
            polygon(0xDCAF67, x - 4, y - 5, x + 4, y - 5, x + 3, y + 6, x - 3, y + 6);
            rounded(x - 2.4, y - 3, 4.8, 7, 2, 0xF9D791);
            rounded(x - 3.5, y + 4, 7, 2.5, 2, 0x685860);
        }

        private void roof(){
            material = Layer.ROOF;
            var plane = new Path2D.Double();
            plane.moveTo(13, 31);
            plane.lineTo(234, 31);
            plane.quadTo(239, 31, 240, 37);
            plane.lineTo(245, 138);
            plane.quadTo(246, 148, 236, 149);
            plane.lineTo(12, 149);
            plane.quadTo(2, 148, 3, 138);
            plane.lineTo(7, 37);
            plane.quadTo(8, 31, 13, 31);
            plane.closePath();
            fill(plane, TERRACOTTA);
            Shape original = g.getClip();
            g.clip(plane);
            rect(0, 28, WIDTH, 34, 0xCD8E81);
            polygon(0xBC7D70, 0, 124, WIDTH, 107, WIDTH, 149, 0, 149);
            for(int y : new int[]{60, 88, 116}){
                line(0xB27A6F, 1.25f, 0, y, WIDTH, y);
            }
            for(int x : new int[]{39, 94, 151, 206}) line(0xBA8174, 1.15f, x, 36, x - 2, 60);
            for(int x : new int[]{21, 71, 176, 230}) line(0xB97E72, 1.15f, x, 60, x - 1, 88);
            for(int x : new int[]{45, 93, 155, 203}) line(0xB97E72, 1.15f, x, 88, x, 116);
            for(int x : new int[]{24, 74, 178, 228}) line(0xAC7367, 1.15f, x, 116, x, 145);
            rect(2, 141, WIDTH - 4, 7, 0xAB6E63);
            g.setClip(original);
            rounded(5, 26, 238, 10, 8, 0xD09283);

            material = Layer.ART;
            rounded(6, 147, 236, 7, 5, WOOD_LIGHT);
        }

        private void chimney(int x, int top, int bottom){
            rounded(x - 11, top + 6, 22, bottom - top - 6, 3, STONE_SHADE);
            rect(x - 11, top + 10, 16, bottom - top - 11, STONE);
            int joint = (top + bottom) / 2 + 5;
            line(0x9D8F83, 1.2f, x - 11, joint, x + 10, joint);
            line(0x9D8F83, 1.2f, x - 2, joint, x - 2, bottom - 1);
            rounded(x - 13, top, 26, 15, 5, 0xC3B4A7);
            rounded(x - 13, top + 11, 26, 5, 2, 0xAA998B);
            rounded(x - 7, top + 3, 14, 6, 2, 0x685860);
        }

        private void dormer(int plasterInset){
            int left = 83 + plasterInset, right = 165 - plasterInset;
            polygon(PLASTER, left, 133, 124, 94, right, 133, right, 149, left, 149);
            polygon(PLASTER_SHADE, left, 133, 124, 94, right, 133, right, 141, 124, 105, left, 142);
            rect(84, 128, 7, 22, WOOD);
            rect(157, 128, 7, 22, WOOD);

            material = Layer.ROOF;
            polygon(0xD59688, 78, 128, 121, 73, 128, 73, 125, 98, 80, 136);
            polygon(0xB6776C, 124, 74, 171, 130, 169, 137, 124, 98);
            rounded(119, 73, 10, 23, 7, 0xD09182);

            material = Layer.ART;
            polygon(WOOD_LIGHT, 78, 132, 124, 93, 170, 132, 168, 138, 124, 101, 80, 138);
            rect(85, 147, 78, 5, WOOD);
            window(124, 128, false);
        }

        private void window(int x, int y, boolean shutters){
            if(shutters){
                rounded(x - 24, y - 10, 11, 23, 3, TIMBER);
                rounded(x + 13, y - 10, 11, 23, 3, TIMBER);
                rounded(x - 24, y - 10, 8, 20, 2, WOOD);
                rounded(x + 14, y - 10, 8, 20, 2, WOOD);
            }
            rounded(x - 15, y - 13, 30, 26, 3, TIMBER);
            material = Layer.WINDOWS;
            rect(x - 11, y - 10, 22, 20, GLASS_SHADE);
            polygon(GLASS, x - 11, y + 4, x + 4, y - 10, x + 11, y - 10, x - 8, y + 10, x - 11, y + 10);
            material = Layer.ART;
            rect(x - 1.3, y - 11, 2.6, 23, WOOD_LIGHT);
            rect(x - 12, y - 1.2, 24, 2.4, WOOD_LIGHT);
            rounded(x - 16, y + 11, 32, 4, 2, WOOD_LIGHT);
        }

        private void canopy(){
            polygon(TIMBER, 79, 178, 169, 178, 173, 202, 75, 202);
            for(int i = 0; i < 6; i++){
                double top = 81 + i * 14.3, bottom = 77 + i * 15.65;
                int cloth = i % 2 == 0 ? 0x84969D : 0xE8D9C0;
                polygon(cloth, top, 179, top + 14.3, 179, bottom + 15.65, 199, bottom, 199);
                var flap = new Path2D.Double();
                flap.moveTo(bottom, 198);
                flap.lineTo(bottom + 15.65, 198);
                flap.lineTo(bottom + 15.65, 200);
                flap.curveTo(bottom + 15, 208, bottom + 1, 208, bottom, 200);
                fill(flap, i % 2 == 0 ? 0x71848F : 0xD6C4AA);
            }
        }

        private void sign(){
            rect(108, 151, 4, 12, TIMBER);
            rect(136, 151, 4, 12, TIMBER);
            rounded(102, 158, 44, 24, 7, 0xC6A07A);
            rounded(105, 160, 38, 19, 5, 0x8B6A59);
            int ink = 0xF4E4C8;
            line(ink, 1.8f, 116, 165, 119, 165, 122, 172, 131, 172, 134, 167, 120, 167);
            fill(new Ellipse2D.Double(122, 173.5, 2.4, 2.4), ink);
            fill(new Ellipse2D.Double(129, 173.5, 2.4, 2.4), ink);
        }

        private void rect(double x, double y, double w, double h, int rgb){
            fill(new Rectangle2D.Double(x, y, w, h), rgb);
        }

        private void rounded(double x, double y, double w, double h, double arc, int rgb){
            fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc), rgb);
        }

        private void polygon(int rgb, double... points){
            Path2D path = path(points);
            path.closePath();
            fill(path, rgb);
        }

        private void line(int rgb, float width, double... points){
            g.setColor(color(rgb));
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path(points));
        }

        private void fill(Shape shape, int rgb){
            g.setColor(color(rgb));
            g.fill(shape);
        }

        private Color color(int rgb){
            return output == Layer.ART ? new Color(rgb) : output == material ? Color.WHITE : CLEAR;
        }

        private static Path2D path(double[] points){
            var path = new Path2D.Double();
            path.moveTo(points[0], points[1]);
            for(int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            return path;
        }
    }
}
