package art;

import art.ArtKit.OutlineStyle;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

// Flat, outlined world objects drawn in code. All art is designed on a 48-unit tile and scaled to `size`.
// Every sprite carries a foot point (its base-centre) and a collision box relative to it, for the entity phase.
public class ObjectArt {

    public static final class Sprite {
        public final String name;
        public final BufferedImage image;
        public final int footX, footY;      // base-centre of the object, in pixels inside the image
        public final Rectangle collision;   // blocking box relative to the foot point, in pixels (width 0 = walk-through)
        public final boolean interactive;

        Sprite(String name, BufferedImage image, int footX, int footY, Rectangle collision, boolean interactive){
            this.name = name;
            this.image = image;
            this.footX = footX;
            this.footY = footY;
            this.collision = collision;
            this.interactive = interactive;
        }
    }

    private static final Color GROUND_SHADOW = new Color(46, 84, 70, 64);

    private final int size;
    private final double k; // pixels per design unit

    public ObjectArt(int size){
        this.size = size;
        this.k = size / 48.0;
    }

    //HELPERS
    private ArtKit.Surface begin(int tilesW, int tilesH){
        ArtKit.Surface s = new ArtKit.Surface(tilesW * size, tilesH * size);
        s.g.scale(k, k);
        return s;
    }

    private Sprite done(String name, ArtKit.Surface s, double footX, double footY,
                        double colLeft, double colTop, double colW, double colH, boolean interactive){
        Rectangle col = new Rectangle((int) Math.round(colLeft * k), (int) Math.round(colTop * k),
                (int) Math.round(colW * k), (int) Math.round(colH * k));
        return new Sprite(name, s.finish(), (int) Math.round(footX * k), (int) Math.round(footY * k), col, interactive);
    }

    private static void shadow(Graphics2D g, double cx, double cy, double rx, double ry){
        g.setColor(GROUND_SHADOW);
        g.fill(new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2));
    }

    private static Ellipse2D.Double circle(double cx, double cy, double r){
        return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }

    private static Area shifted(Area a, double dx, double dy){
        return a.createTransformedArea(AffineTransform.getTranslateInstance(dx, dy));
    }

    // flat fill, one shade crescent along the bottom-right (the light is top-left), optional highlight crescent
    private static void paint(Graphics2D g, Shape shape, Color base, double shadeK, boolean highlight){
        Area a = new Area(shape);
        g.setColor(base);
        g.fill(a);
        Area shade = new Area(a);
        shade.subtract(shifted(a, -shadeK, -shadeK));
        g.setColor(ArtKit.shade(base));
        g.fill(shade);
        if(highlight){
            Area lit = new Area(a);
            lit.subtract(shifted(a, shadeK * 0.8, shadeK * 0.8));
            g.setColor(ArtKit.light(base));
            g.fill(lit);
        }
    }

    private static void stroke(Graphics2D g, Shape shape, Color base, OutlineStyle style){
        if(style == OutlineStyle.NONE) return;
        g.setColor(ArtKit.outline(base, style));
        g.setStroke(new BasicStroke(style == OutlineStyle.INTERACTIVE ? 2.0f : 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    private static void part(Graphics2D g, Shape shape, Color base, OutlineStyle style, double shadeK, boolean highlight){
        paint(g, shape, base, shadeK, highlight);
        stroke(g, shape, base, style);
    }

    // a smooth blobby outline through jittered points around an ellipse
    private static Path2D rockShape(double cx, double cy, double rx, double ry, long seed, int points){
        Random rnd = new Random(seed);
        double[][] p = new double[points][2];
        for(int i = 0; i < points; i++){
            double a = i * Math.PI * 2 / points + rnd.nextDouble() * 0.25;
            double j = 0.85 + rnd.nextDouble() * 0.3;
            p[i][0] = cx + Math.cos(a) * rx * j;
            p[i][1] = cy + Math.sin(a) * ry * j;
        }
        Path2D.Double path = new Path2D.Double();
        path.moveTo((p[points - 1][0] + p[0][0]) / 2, (p[points - 1][1] + p[0][1]) / 2);
        for(int i = 0; i < points; i++){
            double[] c = p[i];
            double[] n = p[(i + 1) % points];
            path.quadTo(c[0], c[1], (c[0] + n[0]) / 2, (c[1] + n[1]) / 2);
        }
        path.closePath();
        return path;
    }

    //TREES AND PLANTS (non-interactive: soft outline)
    public Sprite oak(long seed){
        Random rnd = new Random(seed);
        ArtKit.Surface s = begin(3, 4);
        Graphics2D g = s.g;
        double cx = 72;

        shadow(g, cx + 8, 176, 42, 9);

        Path2D.Double trunk = new Path2D.Double();
        trunk.moveTo(cx - 9, 96);
        trunk.lineTo(cx + 9, 96);
        trunk.lineTo(cx + 13, 178);
        trunk.lineTo(cx - 13, 178);
        trunk.closePath();
        part(g, trunk, Palette.TRUNK, OutlineStyle.SOFT, 4, false);

        double[][] blobs = {{0, 66, 40}, {-30, 84, 28}, {30, 84, 28}, {-4, 38, 30}, {-26, 54, 24}, {26, 56, 24}};
        Area canopy = new Area();
        for(double[] b : blobs){
            canopy.add(new Area(circle(cx + b[0] + (rnd.nextDouble() - 0.5) * 6,
                    b[1] + (rnd.nextDouble() - 0.5) * 6, b[2] + (rnd.nextDouble() - 0.5) * 5)));
        }
        paint(g, canopy, Palette.FOLIAGE, 7, true);

        // flat leaf clumps: lighter on the sunny side, darker low on the shaded side
        Shape oldClip = g.getClip();
        g.setClip(canopy);
        g.setColor(Palette.FOLIAGE_LIGHT);
        for(int i = 0; i < 4; i++){
            g.fill(circle(cx - 34 + rnd.nextDouble() * 40, 22 + rnd.nextDouble() * 46, 7 + rnd.nextDouble() * 5));
        }
        g.setColor(Palette.FOLIAGE_DARK);
        for(int i = 0; i < 3; i++){
            g.fill(circle(cx + 2 + rnd.nextDouble() * 36, 66 + rnd.nextDouble() * 38, 8 + rnd.nextDouble() * 5));
        }
        g.setClip(oldClip);
        stroke(g, canopy, Palette.FOLIAGE, OutlineStyle.SOFT);

        return done("oak", s, cx, 178, -11, -10, 22, 10, false);
    }

    public Sprite pine(long seed){
        Random rnd = new Random(seed);
        ArtKit.Surface s = begin(2, 4);
        Graphics2D g = s.g;

        shadow(g, 54, 180, 30, 8);
        part(g, new RoundRectangle2D.Double(42, 138, 12, 42, 4, 4), Palette.TRUNK, OutlineStyle.SOFT, 3, false);

        Color[] tierColors = {
                Palette.FOLIAGE_DARK,
                ArtKit.mix(Palette.FOLIAGE_DARK, Palette.FOLIAGE, 0.3f),
                ArtKit.mix(Palette.FOLIAGE_DARK, Palette.FOLIAGE, 0.6f)
        };
        double[][] tiers = {{70, 150, 42}, {40, 112, 34}, {10, 72, 26}}; // apex y, base y, half width
        for(int t = 0; t < 3; t++){
            double apex = tiers[t][0], base = tiers[t][1], hw = tiers[t][2] + (rnd.nextDouble() - 0.5) * 4;
            Path2D.Double p = new Path2D.Double();
            p.moveTo(48, apex);
            p.lineTo(48 + hw, base - 4);
            p.quadTo(48, base + 8, 48 - hw, base - 4);
            p.closePath();
            part(g, p, tierColors[t], OutlineStyle.SOFT, 5, true);
        }
        return done("pine", s, 48, 180, -7, -10, 14, 10, false);
    }

    public Sprite bush(long seed, boolean flowers){
        Random rnd = new Random(seed);
        ArtKit.Surface s = begin(2, 1);
        Graphics2D g = s.g;

        shadow(g, 52, 44, 34, 5);
        Area a = new Area();
        double[][] blobs = {{28, 30, 16}, {48, 27, 19}, {68, 31, 15}};
        for(double[] b : blobs){
            a.add(new Area(circle(b[0] + (rnd.nextDouble() - 0.5) * 4, b[1] + (rnd.nextDouble() - 0.5) * 3,
                    b[2] + (rnd.nextDouble() - 0.5) * 3)));
        }
        paint(g, a, Palette.FOLIAGE, 4, true);
        if(flowers){
            Shape oldClip = g.getClip();
            g.setClip(a);
            for(int i = 0; i < 9; i++){
                g.setColor(Palette.FLOWERS[rnd.nextInt(Palette.FLOWERS.length)]);
                g.fill(circle(18 + rnd.nextDouble() * 60, 12 + rnd.nextDouble() * 28, 2.4));
            }
            g.setClip(oldClip);
        }
        stroke(g, a, Palette.FOLIAGE, OutlineStyle.SOFT);
        return done(flowers ? "flower_bush" : "bush", s, 48, 44, -20, -12, 40, 12, false);
    }

    //ROCKS AND STUMP
    public Sprite rockSmall(long seed){
        ArtKit.Surface s = begin(1, 1);
        Graphics2D g = s.g;
        shadow(g, 27, 40, 16, 4);
        part(g, rockShape(24, 29, 16, 12, seed, 8), Palette.ROCK, OutlineStyle.SOFT, 4, true);
        return done("rock_small", s, 24, 41, -14, -10, 28, 10, false);
    }

    public Sprite rockLarge(long seed){
        ArtKit.Surface s = begin(2, 2);
        Graphics2D g = s.g;
        shadow(g, 54, 86, 38, 7);
        part(g, rockShape(80, 70, 14, 11, seed + 7, 7), Palette.ROCK, OutlineStyle.SOFT, 3, true); // companion behind
        part(g, rockShape(44, 58, 34, 26, seed, 9), Palette.ROCK, OutlineStyle.SOFT, 6, true);
        return done("rock_large", s, 48, 88, -34, -16, 68, 16, false);
    }

    public Sprite stump(){
        ArtKit.Surface s = begin(1, 1);
        Graphics2D g = s.g;
        shadow(g, 28, 41, 16, 4);
        part(g, new RoundRectangle2D.Double(10, 18, 28, 22, 10, 10), Palette.TRUNK, OutlineStyle.SOFT, 3, false);
        part(g, new Ellipse2D.Double(10, 12, 28, 13), Palette.WOOD_LIGHT, OutlineStyle.SOFT, 2, false);
        g.setColor(ArtKit.shade(Palette.WOOD_LIGHT));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(16, 15, 16, 7));
        return done("stump", s, 24, 41, -12, -8, 24, 8, false);
    }

    //FENCES: rails run past the tile edge so neighbouring pieces join with no seam
    public Sprite fenceH(){
        ArtKit.Surface s = begin(1, 1);
        Graphics2D g = s.g;
        part(g, new RoundRectangle2D.Double(-6, 16, 60, 6, 3, 3), Palette.WOOD, OutlineStyle.SOFT, 2, false);
        part(g, new RoundRectangle2D.Double(-6, 28, 60, 6, 3, 3), Palette.WOOD, OutlineStyle.SOFT, 2, false);
        part(g, new RoundRectangle2D.Double(19, 10, 10, 34, 4, 4), Palette.WOOD_LIGHT, OutlineStyle.SOFT, 2.5, true);
        return done("fence_h", s, 24, 44, -24, -10, 48, 10, false);
    }

    public Sprite fenceV(){
        ArtKit.Surface s = begin(1, 1);
        Graphics2D g = s.g;
        part(g, new RoundRectangle2D.Double(22, -6, 4, 60, 2, 2), Palette.WOOD, OutlineStyle.SOFT, 1.5, false);
        part(g, new RoundRectangle2D.Double(19, 12, 10, 30, 4, 4), Palette.WOOD_LIGHT, OutlineStyle.SOFT, 2.5, true);
        return done("fence_v", s, 24, 42, -5, -42, 10, 48, false);
    }

    //HOUSE: plaster walls, timber frame, hip roof, chimney, warm windows with flower boxes
    public Sprite house(String name, Color roof, long seed){
        Random rnd = new Random(seed);
        ArtKit.Surface s = begin(5, 5);
        Graphics2D g = s.g;

        shadow(g, 128, 230, 104, 11);

        // chimney sits behind the roof
        part(g, new RoundRectangle2D.Double(144, 26, 22, 52, 4, 4), Palette.BRICK, OutlineStyle.SOFT, 4, true);
        part(g, new RoundRectangle2D.Double(140, 18, 30, 10, 3, 3),
                ArtKit.mix(Palette.BRICK, Palette.PLASTER, 0.35f), OutlineStyle.SOFT, 2, false);

        // walls
        part(g, new RoundRectangle2D.Double(44, 116, 152, 114, 4, 4), Palette.PLASTER, OutlineStyle.SOFT, 6, false);

        // timber frame: flat beams, no outline
        g.setColor(Palette.TIMBER);
        for(double bx : new double[]{47, 94, 142, 193}){
            g.fill(new Rectangle2D.Double(bx - 3, 118, 6, 112));
        }
        g.fill(new Rectangle2D.Double(44, 147, 152, 6));
        g.fill(new Rectangle2D.Double(44, 222, 152, 8));

        // shadow the roof eave throws on the wall
        g.setColor(ArtKit.alpha(ArtKit.shade(Palette.PLASTER), 150));
        g.fill(new Rectangle2D.Double(44, 126, 152, 12));

        // windows with flower boxes
        for(double wx : new double[]{54, 152}){
            g.setColor(Palette.TIMBER);
            g.fill(new Rectangle2D.Double(wx - 1, 158, 34, 30));
            g.setColor(Palette.GLOW);
            g.fill(new Rectangle2D.Double(wx + 3, 162, 26, 22));
            g.setColor(Palette.TIMBER);
            g.fill(new Rectangle2D.Double(wx + 14, 162, 4, 22));
            g.fill(new Rectangle2D.Double(wx + 3, 171, 26, 4));
            g.setColor(Palette.WOOD);
            g.fill(new RoundRectangle2D.Double(wx - 3, 188, 38, 9, 3, 3));
            for(int f = 0; f < 3; f++){
                g.setColor(Palette.FLOWERS[rnd.nextInt(Palette.FLOWERS.length)]);
                g.fill(circle(wx + 6 + f * 10, 187, 3.6));
            }
        }

        // arched door
        Path2D.Double door = new Path2D.Double();
        door.moveTo(106, 230);
        door.lineTo(106, 192);
        door.quadTo(106, 176, 120, 176);
        door.quadTo(134, 176, 134, 192);
        door.lineTo(134, 230);
        door.closePath();
        part(g, door, Palette.WOOD, OutlineStyle.SOFT, 4, true);
        g.setColor(Palette.GLOW);
        g.fill(circle(128, 208, 2.2));

        // roof, with flat shingle lines clipped to it
        Path2D.Double roofShape = new Path2D.Double();
        roofShape.moveTo(20, 128);
        roofShape.lineTo(62, 42);
        roofShape.lineTo(178, 42);
        roofShape.lineTo(220, 128);
        roofShape.closePath();
        paint(g, roofShape, roof, 8, true);
        Shape oldClip = g.getClip();
        g.setClip(roofShape);
        g.setColor(ArtKit.mix(roof, ArtKit.shade(roof), 0.55f));
        g.setStroke(new BasicStroke(2f));
        for(int row = 1; row <= 5; row++){
            double y = 42 + row * 17;
            g.draw(new Line2D.Double(0, y, 240, y));
            for(double x = (row % 2 == 0 ? 11 : 0); x < 240; x += 22){ // staggered shingle ticks
                g.draw(new Line2D.Double(x, y - 17, x, y));
            }
        }
        g.setClip(oldClip);
        stroke(g, roofShape, roof, OutlineStyle.SOFT);
        part(g, new RoundRectangle2D.Double(56, 38, 128, 10, 5, 5),
                ArtKit.mix(roof, ArtKit.shade(roof), 0.5f), OutlineStyle.SOFT, 2, false); // ridge cap

        return done(name, s, 120, 230, -76, -100, 152, 100, false);
    }

    //LIGHT (non-interactive for now)
    public Sprite lantern(){
        ArtKit.Surface s = begin(1, 2);
        Graphics2D g = s.g;
        shadow(g, 27, 90, 10, 3);
        part(g, new RoundRectangle2D.Double(21, 30, 6, 60, 2, 2), Palette.IRON, OutlineStyle.SOFT, 2, true);
        g.setColor(ArtKit.alpha(Palette.GLOW, 60));
        g.fill(circle(24, 24, 22));
        g.setColor(ArtKit.alpha(Palette.GLOW, 70));
        g.fill(circle(24, 24, 14));
        part(g, new RoundRectangle2D.Double(16, 15, 16, 20, 5, 5), Palette.GLOW, OutlineStyle.SOFT, 2, true);
        Path2D.Double cap = new Path2D.Double();
        cap.moveTo(13, 17);
        cap.lineTo(24, 6);
        cap.lineTo(35, 17);
        cap.closePath();
        part(g, cap, Palette.IRON, OutlineStyle.SOFT, 2, true);
        return done("lantern", s, 24, 90, -4, -6, 8, 6, false);
    }

    //INTERACTIVE OBJECTS (near-black outline)
    public Sprite well(){
        ArtKit.Surface s = begin(2, 2);
        Graphics2D g = s.g;
        OutlineStyle o = OutlineStyle.INTERACTIVE;
        shadow(g, 54, 84, 34, 7);

        part(g, new RoundRectangle2D.Double(18, 52, 60, 30, 14, 14), Palette.ROCK, o, 4, true);
        part(g, new Ellipse2D.Double(18, 42, 60, 28), ArtKit.light(Palette.ROCK), o, 3, true);
        paint(g, new Ellipse2D.Double(26, 47, 44, 18), Palette.WATER, 3, false);
        part(g, new RoundRectangle2D.Double(23, 12, 7, 46, 3, 3), Palette.WOOD, o, 2, true);
        part(g, new RoundRectangle2D.Double(66, 12, 7, 46, 3, 3), Palette.WOOD, o, 2, true);

        Path2D.Double roof = new Path2D.Double();
        roof.moveTo(12, 24);
        roof.lineTo(48, 3);
        roof.lineTo(84, 24);
        roof.closePath();
        part(g, roof, Palette.ROOF_CORAL, o, 4, true);

        g.setColor(ArtKit.shade(Palette.WOOD));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Line2D.Double(48, 24, 48, 38));
        part(g, new RoundRectangle2D.Double(43, 36, 10, 9, 3, 3), Palette.WOOD_LIGHT, o, 2, false);
        return done("well", s, 48, 82, -30, -24, 60, 24, true);
    }

    public Sprite sign(){
        ArtKit.Surface s = begin(1, 2);
        Graphics2D g = s.g;
        OutlineStyle o = OutlineStyle.INTERACTIVE;
        shadow(g, 27, 90, 11, 3);
        part(g, new RoundRectangle2D.Double(21, 24, 7, 66, 3, 3), Palette.WOOD, o, 2, true);
        part(g, new RoundRectangle2D.Double(5, 10, 38, 26, 6, 6), Palette.WOOD_LIGHT, o, 3, true);
        g.setColor(ArtKit.shade(Palette.WOOD_LIGHT));
        g.fill(new RoundRectangle2D.Double(11, 17, 26, 3, 1.5, 1.5));
        g.fill(new RoundRectangle2D.Double(11, 25, 18, 3, 1.5, 1.5));
        return done("sign", s, 24, 90, -5, -8, 10, 8, true);
    }

    public Sprite barrel(){
        ArtKit.Surface s = begin(1, 1);
        Graphics2D g = s.g;
        OutlineStyle o = OutlineStyle.INTERACTIVE;
        shadow(g, 27, 42, 15, 4);
        part(g, new RoundRectangle2D.Double(10, 10, 28, 32, 12, 12), Palette.BARREL, o, 3, true);
        g.setColor(ArtKit.shade(Palette.BARREL));
        g.fill(new Rectangle2D.Double(10, 18, 28, 3));
        g.fill(new Rectangle2D.Double(10, 32, 28, 3));
        part(g, new Ellipse2D.Double(10, 6, 28, 11), Palette.WOOD_LIGHT, o, 2, false);
        return done("barrel", s, 24, 42, -14, -10, 28, 10, true);
    }

    public Sprite crate(){
        ArtKit.Surface s = begin(1, 1);
        Graphics2D g = s.g;
        OutlineStyle o = OutlineStyle.INTERACTIVE;
        shadow(g, 27, 42, 17, 4);
        part(g, new RoundRectangle2D.Double(8, 12, 32, 30, 4, 4), Palette.WOOD, o, 3, true);
        g.setColor(ArtKit.shade(Palette.WOOD));
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new Rectangle2D.Double(13, 17, 22, 20));
        g.draw(new Line2D.Double(13, 17, 35, 37));
        return done("crate", s, 24, 42, -16, -12, 32, 12, true);
    }

    public Sprite chest(){
        ArtKit.Surface s = begin(1, 1);
        Graphics2D g = s.g;
        OutlineStyle o = OutlineStyle.INTERACTIVE;
        shadow(g, 27, 42, 17, 4);
        part(g, new RoundRectangle2D.Double(8, 24, 32, 17, 4, 4), Palette.WOOD, o, 3, true);
        Path2D.Double lid = new Path2D.Double();
        lid.moveTo(8, 26);
        lid.lineTo(8, 22);
        lid.quadTo(24, 4, 40, 22);
        lid.lineTo(40, 26);
        lid.closePath();
        part(g, lid, ArtKit.mix(Palette.WOOD, Palette.WOOD_LIGHT, 0.4f), o, 3, true);
        g.setColor(Palette.GOLD);
        g.fill(new Rectangle2D.Double(21, 13, 6, 27));
        g.setColor(ArtKit.shade(Palette.GOLD));
        g.fill(new RoundRectangle2D.Double(21, 24, 6, 7, 2, 2));
        return done("chest", s, 24, 41, -16, -12, 32, 12, true);
    }
}