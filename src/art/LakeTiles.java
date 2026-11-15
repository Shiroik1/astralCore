package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Offline water overlays with layered recessed banks. The runtime consumes exported tile sheets. */
public final class LakeTiles {
    public static final int VARIANTS = 3;
    public static final Color WATER = Palette.WATER;
    public static final Color SHALLOW = ArtKit.mix(Palette.WATER,Palette.FLOWERS[1],.28f);
    public static final Color BANK = ArtKit.mix(Palette.GROUND[1],Palette.FOLIAGE,.25f);
    private final int size;
    private final Map<Integer, BufferedImage> cache = new HashMap<>();

    public LakeTiles(int size) {
        if (size < 1) throw new IllegalArgumentException("Tile size must be positive");
        this.size = size;
    }

    /** Ignore a diagonal unless both adjoining cardinal neighbours contain water: 47 cases. */
    public static int canonicalMask(int mask) {
        int m = mask & 15;
        if ((m & 3) == 3) m |= mask & GroundTiles.UP_RIGHT;
        if ((m & 6) == 6) m |= mask & GroundTiles.DOWN_RIGHT;
        if ((m & 12) == 12) m |= mask & GroundTiles.DOWN_LEFT;
        if ((m & 9) == 9) m |= mask & GroundTiles.UP_LEFT;
        return m;
    }

    public static Area waterShape(int mask) {
        return GroundTiles.blobShape(canonicalMask(mask), 5, 13, 8, -1);
    }

    /** Variant 0 is calm, 1 has a short ripple, and 2 has two small reflections. */
    public BufferedImage tile(int mask, int variant) {
        if (variant < 0 || variant >= VARIANTS) throw new IllegalArgumentException("Unknown water variant");
        int key = canonicalMask(mask) * VARIANTS + variant;
        return cache.computeIfAbsent(key, ignored -> render(mask, variant));
    }

    private BufferedImage render(int mask, int variant) {
        ArtKit.Surface surface = new ArtKit.Surface(size, size);
        Graphics2D g = surface.g;
        g.scale(size / 48.0, size / 48.0);
        Area water = extendedWater(mask);
        // All boundaries run beyond connected tile edges; only actual shore gets a bank.
        g.setColor(BANK);
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(water);
        g.setColor(WATER);
        g.fill(water);
        g.clip(water);

        g.setColor(SHALLOW);
        g.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(water);

        // North/back-facing banks show earth strata above the unchanged turquoise water.
        // Extend connected borders before shifting so internal tile seams never acquire false walls.
        Color[] soil={ArtKit.shade(Palette.TIMBER),Palette.TIMBER,Palette.WOOD,Palette.GROUND[1]};
        double[] depths={11,8,5,2};
        Area wall=new Area();
        for(int i=0;i<depths.length;i++){
            Area band=new Area(water);
            band.subtract(new Area(AffineTransform.getTranslateInstance(0,depths[i]).createTransformedShape(water)));
            g.setColor(soil[i]);g.fill(band);
            if(i==0)wall=band;
        }
        Area surfaceWater=new Area(water);surfaceWater.subtract(wall);
        g.clip(surfaceWater);

        // A narrow white rim on top-left shores. Connected edges stay free of strokes.
        Area rim = new Area(water);
        rim.subtract(new Area(AffineTransform.getTranslateInstance(0.85, 0.85)
                .createTransformedShape(water)));
        g.setColor(new Color(255, 255, 255, 115));
        g.fill(rim);

        // Flat water fill; depth comes from discrete bank strata rather than a surface gradient.
        g.setColor(new Color(242, 252, 247, 155));
        g.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (variant == 1) {
            Path2D ripple = new Path2D.Float();
            ripple.moveTo(17, 24); ripple.quadTo(23, 25.6, 29, 24);
            g.draw(ripple);
        } else if (variant == 2) {
            Path2D ripple = new Path2D.Float();
            ripple.moveTo(13, 18); ripple.quadTo(17, 19.2, 21, 18);
            ripple.moveTo(27, 31); ripple.quadTo(30, 32, 33, 31);
            g.draw(ripple);
        }
        return surface.finish();
    }

    private static Area extendedWater(int mask){
        Area water=waterShape(mask),extended=new Area(water);
        for(int i=-2;i<50;i++){
            if(water.contains(i+.5,-1))extended.add(new Area(new Rectangle2D.Double(i,-64,1,64)));
            if(water.contains(i+.5,49))extended.add(new Area(new Rectangle2D.Double(i,48,1,64)));
            if(water.contains(-1,i+.5))extended.add(new Area(new Rectangle2D.Double(-64,i,64,1)));
            if(water.contains(49,i+.5))extended.add(new Area(new Rectangle2D.Double(48,i,64,1)));
        }
        // Diagonally connected corner arms must extend in both axes as well.
        for(int x:new int[]{-1,48}) for(int y:new int[]{-1,48})
            if(water.contains(x+.5,y+.5))extended.add(new Area(new Rectangle2D.Double(x<0?-64:48,y<0?-64:48,64,64)));
        return extended;
    }

    /** Separate decoration, so pads are optional rather than repeated in the terrain. */
    public BufferedImage lilyPad(boolean flower) {
        ArtKit.Surface surface = new ArtKit.Surface(size, size);
        Graphics2D g = surface.g;
        g.scale(size / 48.0, size / 48.0);
        g.setColor(new Color(63, 121, 119, 38));
        g.fill(new Ellipse2D.Float(11, 21, 27, 14));
        Area pad = new Area(new Ellipse2D.Float(10, 17, 27, 16));
        Path2D notch = new Path2D.Float();
        notch.moveTo(24,25); notch.lineTo(35,27); notch.lineTo(33,34); notch.closePath();
        pad.subtract(new Area(notch));
        g.setPaint(new GradientPaint(12,17,new Color(0xA4CF88),35,33,new Color(0x6BA67C)));
        g.fill(pad);
        g.setColor(new Color(0x609D78));
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(pad);
        if (flower) {
            g.setColor(new Color(0xF2CDCA));
            for (int i=0; i<5; i++) {
                double a=i*Math.PI*2/5;
                g.fill(new Ellipse2D.Double(22+Math.cos(a)*3-2.5,22+Math.sin(a)*2-3,5,6));
            }
            g.setColor(new Color(0xF3DB9B)); g.fill(new Ellipse2D.Float(20.8f,20,4,3));
        }
        return surface.finish();
    }
}
