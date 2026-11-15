package world;

import art.ObjectArt;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

final class SwayCache {
    static final int STEPS = 12;       // frames each side of upright (25 total)
    static final double MAX = 0.024;   // just above AmbientFX.treeSway's peak (~0.022)
    private static final Map<ObjectArt.Sprite, BufferedImage[]> CACHE = new IdentityHashMap<>();
    private SwayCache(){}

    static int pad(ObjectArt.Sprite s){ return (int)Math.ceil(MAX * s.footY) + 2; }

    static int stepFor(double sway){
        return (int)Math.round(Math.max(-1, Math.min(1, sway / MAX)) * STEPS);
    }

    static synchronized BufferedImage frame(ObjectArt.Sprite s, int step){
        BufferedImage[] set = CACHE.computeIfAbsent(s, k -> new BufferedImage[STEPS * 2 + 1]);
        int i = step + STEPS;
        if(set[i] == null) set[i] = bake(s, step * MAX / STEPS);
        return set[i];
    }

    /** Bake every angle up front so nothing is generated during play. */
    static synchronized void warm(ObjectArt.Sprite s){
        BufferedImage[] set = CACHE.computeIfAbsent(s, k -> new BufferedImage[STEPS * 2 + 1]);
        if(set[0] != null && set[set.length - 1] != null) return; // already warmed
        for(int step = -STEPS; step <= STEPS; step++) frame(s, step);
    }

    private static BufferedImage bake(ObjectArt.Sprite s, double sway){
        int pad = pad(s);
        BufferedImage out = new BufferedImage(s.image.getWidth() + pad * 2, s.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(s.image, new AffineTransform(1, 0, -sway, 1, pad + sway * s.footY, 0), null);
        g.dispose();
        return out;
    }
}