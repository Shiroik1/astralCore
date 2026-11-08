package entity;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

public class PlaceholderSprites {

    private record AtlasKey(int size,int color,String label) {}
    private static final java.util.concurrent.ConcurrentMap<AtlasKey,Map<String,SpriteAnimation>> ATLASES=
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final String[] DIRECTIONS = {"down", "up", "left", "right"};

    // Fills `sprites` with the same keys real art uses ("idle_down", "walk_left", ...), so swapping in
    // real sprites later means replacing this one call and nothing else.
    public static void fill(Map<String, SpriteAnimation> sprites, int size, Color body, String label){
        AtlasKey key=new AtlasKey(size,body.getRGB(),label);
        Map<String,SpriteAnimation> atlas=ATLASES.computeIfAbsent(key,k -> {
            Map<String,SpriteAnimation> generated=new java.util.HashMap<>();
            generate(generated,size,body,label);return Map.copyOf(generated);
        });
        SpriteAnimation.copyInto(sprites,atlas);
    }

    private static void generate(Map<String, SpriteAnimation> sprites,int size,Color body,String label){
        for(String dir : DIRECTIONS){
            sprites.put("idle_" + dir, build(size, body, label, dir, "idle", 6, 12));
            sprites.put("walk_" + dir, build(size, body, label, dir, "walk", 8, 5));
            sprites.put("attack_" + dir, build(size, body, label, dir, "attack", 6, 8));
            sprites.put("death_" + dir, build(size, body, label, dir, "death", 8, 6));
        }
    }

    private static SpriteAnimation build(int size, Color body, String label, String dir, String anim, int frames, int delay){
        boolean looping = anim.equals("idle") || anim.equals("walk");
        BufferedImage[] images = new BufferedImage[frames];
        for(int i = 0; i < frames; i++){
            float t = looping ? i / (float) frames : i / (float) (frames - 1);
            images[i] = drawFrame(size, body, label, dir, anim, t);
        }
        SpriteAnimation animation = new SpriteAnimation(images, size, size);
        animation.frameDelay = delay;
        return animation;
    }

    private static BufferedImage drawFrame(int size, Color body, String label, String dir, String anim, float t){
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double wave = Math.sin(t * Math.PI * 2);
        double sx = 1, sy = 1, lunge = 0, alpha = 1;
        switch(anim){
            case "idle" -> { sx = 1 - wave * 0.04; sy = 1 + wave * 0.04; }
            case "walk" -> { sx = 1 - wave * 0.10; sy = 1 + wave * 0.10; }
            case "attack" -> { lunge = Math.sin(t * Math.PI) * size * 0.12; sx = 1 + Math.sin(t * Math.PI) * 0.2; }
            case "death" -> { sx = 1 + t * 0.3; sy = 1 - t * 0.8; alpha = 1 - t * 0.9; }
        }

        int dx = dir.equals("left") ? -1 : dir.equals("right") ? 1 : 0;
        int dy = dir.equals("up") ? -1 : dir.equals("down") ? 1 : 0;

        int bw = (int) (size * 0.35 * sx);
        int bh = (int) (size * 0.30 * sy);
        int baseY = (int) (size * 0.65);                    // feet stay on one line while squashing
        int bx = size / 2 - bw / 2 + (int) (dx * lunge);
        int by = baseY - bh + (int) (dy * lunge);
        int a = (int) (alpha * 255);

        g.setColor(new Color(body.getRed(), body.getGreen(), body.getBlue(), a));
        g.fillRoundRect(bx, by, bw, bh, bw / 2, bh / 2);
        g.setColor(new Color(0, 0, 0, a));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(bx, by, bw, bh, bw / 2, bh / 2);

        if(!dir.equals("up") && !anim.equals("death")){      // eyes show which way it faces
            int ex = bx + bw / 2 + (int) (dx * bw * 0.22);
            int ey = by + bh / 2 + (int) (dy * bh * 0.10) - bh / 8;
            int eyeR = Math.max(2, size / 28);
            g.setColor(new Color(255, 255, 255, a));
            if(dir.equals("down")){
                g.fillOval(ex - (int) (bw * 0.18) - eyeR, ey - eyeR, eyeR * 2, eyeR * 2);
                g.fillOval(ex + (int) (bw * 0.18) - eyeR, ey - eyeR, eyeR * 2, eyeR * 2);
            } else {
                g.fillOval(ex - eyeR, ey - eyeR, eyeR * 2, eyeR * 2);
            }
        }

        if(label != null && !label.isEmpty() && !anim.equals("death")){
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, size / 9)));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(255, 255, 255, 200));
            g.drawString(label, size / 2 - fm.stringWidth(label) / 2, by - 4);
        }

        g.dispose();
        return img;
    }
}