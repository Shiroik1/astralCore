package art;

import entity.Particle;
import main.MiniJson;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** Shared building art and attachments. Palette work happens once per distinct design and color set. */
public final class BuildingCatalog {
    public record Light(double x, double y, double radius, float intensity) {}
    public record DepthStrip(int left, int right, int depth) {}
    public record Emitter(float x, float y, Particle.Style style, Color color,
                          int minInterval, int maxInterval, int minLife, int maxLife,
                          float minSize, float maxSize, float vx, float vy) {}
    public record Building(String id, Map<String, Integer> palette, ObjectArt.Sprite sprite,
                           List<Rectangle> extraFootprint, List<DepthStrip> depthStrips, Rectangle door, int cueOffset,
                           List<Light> lights, List<Emitter> emitters,
                           BufferedImage emissive, int emissiveX, int emissiveY) {}
    private record Key(String id, Map<String, Integer> palette, int tileSize) {}
    private record Overlay(BufferedImage image, int x, int y) {}

    private static final Map<?, ?> DEFINITIONS = definitions();
    private static final Map<Key, Building> CACHE = new HashMap<>();

    private BuildingCatalog(){}

    public static boolean has(String id){
        return DEFINITIONS.containsKey(id);
    }

    public static synchronized Building get(String id, Map<String, Integer> overrides, int tileSize){
        if(!(DEFINITIONS.get(id) instanceof Map<?, ?> data)) throw new IllegalArgumentException("Unknown building " + id);
        Map<?, ?> materials = object(data.get("materials"));
        Map<String, Integer> palette = new HashMap<>();
        for(var entry : materials.entrySet()){
            Map<?, ?> material = object(entry.getValue());
            if(material.get("color") instanceof String color) palette.put((String) entry.getKey(), parseColor(color));
        }
        for(var entry : overrides.entrySet()){
            if(!materials.containsKey(entry.getKey())) throw new IllegalArgumentException("Unknown " + id + " material " + entry.getKey());
            palette.put(entry.getKey(), entry.getValue());
        }
        Key key = new Key(id, Map.copyOf(palette), tileSize);
        return CACHE.computeIfAbsent(key, ignored -> bake(key, data, materials));
    }

    public static Map<String, Integer> parsePalette(Object value){
        Map<String, Integer> colors = new HashMap<>();
        for(var entry : object(value).entrySet()){
            colors.put((String) entry.getKey(), parseColor(entry.getValue()));
        }
        return Map.copyOf(colors);
    }

    private static Building bake(Key key, Map<?, ?> data, Map<?, ?> materials){
        double artScale = number(data.get("scale"));
        if(artScale <= 0) throw new IllegalArgumentException("Building scale must be positive");
        double tileScale = key.tileSize / 48.0;
        double scale = artScale * tileScale;
        Rectangle crop = rect(data.get("crop"), 1);
        double[] size = numbers(data.get("size"), 2);
        double[] anchor = numbers(data.get("anchor"), 2);
        int w = px(size[0], scale), h = px(size[1], scale);
        int footX = px(anchor[0], scale), footY = px(anchor[1], scale);
        List<Rectangle> footprint = list(data.get("footprint")).stream().map(box -> rect(box, scale)).toList();
        Rectangle collision = footprint.isEmpty() ? new Rectangle() : footprint.getFirst();
        List<Rectangle> extra = footprint.isEmpty() ? List.of() : footprint.subList(1, footprint.size());

        double[] door = data.get("door") == null ? null : numbers(data.get("door"), 5);
        Rectangle doorRect = door == null ? null : rect(door, scale);
        int cueOffset = door == null ? 0 : px(door[4], scale);
        List<Light> lights = list(data.get("lights")).stream().map(light -> light(object(light), artScale)).toList();
        List<Emitter> emitters = list(data.get("emitters")).stream().map(emitter -> emitter(object(emitter), scale, tileScale)).toList();

        BufferedImage source = image(data.get("image"));
        if(crop.isEmpty() || !new Rectangle(source.getWidth(), source.getHeight()).contains(crop)){
            throw new IllegalArgumentException("Building crop must be inside the source PNG");
        }
        if(data.get("silhouette") != null){
            BufferedImage silhouette = mask(data.get("silhouette"), source);
            Graphics2D g = source.createGraphics();
            try{
                g.setComposite(AlphaComposite.DstIn);
                g.drawImage(silhouette, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        Overlay emissive = bakeEmission(source, crop, w, h, object(data.get("emissive")));
        for(var entry : key.palette.entrySet()){
            Map<?, ?> material = object(materials.get(entry.getKey()));
            tint(source, mask(material, source), crop, parseColor(material.get("reference")), entry.getValue(), false);
        }

        BufferedImage base = new BufferedImage(w, Math.max(h, footY + px(10, scale)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(46, 84, 70, 64));
            for(Object shadow : list(data.get("shadows"))){
                double[] oval = numbers(shadow, 4);
                g.fill(new Ellipse2D.Double((oval[0] - oval[2]) * scale, (oval[1] - oval[3]) * scale,
                        oval[2] * 2 * scale, oval[3] * 2 * scale));
            }
            blit(g, source, crop, w, h);
        } finally {
            g.dispose();
        }
        var sprite = new ObjectArt.Sprite(key.id, base, footX, footY, collision, door != null);
        return new Building(key.id, key.palette, sprite, extra, depthStrips(footprint, w, footX), doorRect, cueOffset, lights, emitters,
                emissive.image, emissive.x, emissive.y);
    }

    // The facade and side wings can meet the ground at different depths.
    private static List<DepthStrip> depthStrips(List<Rectangle> footprint, int width, int footX){
        if(footprint.isEmpty()) return List.of(new DepthStrip(0, width, 0));
        var boundaries = new TreeSet<>(List.of(0, width));
        int footprintLeft = width, footprintRight = 0;
        for(Rectangle box : footprint){
            int left = Math.clamp(box.x + footX, 0, width);
            int right = Math.clamp(box.x + box.width + footX, 0, width);
            boundaries.add(left);
            boundaries.add(right);
            footprintLeft = Math.min(footprintLeft, left);
            footprintRight = Math.max(footprintRight, right);
        }
        if(footprintRight <= footprintLeft) return List.of(new DepthStrip(0, width, 0));

        var strips = new ArrayList<DepthStrip>();
        int left = 0;
        for(int right : boundaries.tailSet(1)){
            int x = Math.clamp((left + right) / 2, footprintLeft, footprintRight - 1) - footX;
            int depth = Integer.MIN_VALUE;
            for(Rectangle box : footprint){
                if(x >= box.x && x < box.x + box.width) depth = Math.max(depth, box.y + box.height);
            }
            if(depth == Integer.MIN_VALUE) depth = 0;
            if(!strips.isEmpty() && strips.getLast().depth() == depth) left = strips.removeLast().left();
            strips.add(new DepthStrip(left, right, depth));
            left = right;
        }
        return List.copyOf(strips);
    }

    private static Light light(Map<?, ?> data, double scale){
        double[] p = numbers(data.get("position"), 2);
        double radius = number(data.get("radius")), intensity = number(data.get("intensity"));
        if(radius <= 0 || intensity < 0 || intensity > 1) throw new IllegalArgumentException("Invalid building light");
        return new Light(p[0] * scale, p[1] * scale, radius, (float) intensity);
    }

    private static Emitter emitter(Map<?, ?> data, double scale, double tileScale){
        double[] p = numbers(data.get("position"), 2), v = numbers(data.get("velocity"), 2);
        double[] interval = range(data.get("interval")), life = range(data.get("lifetime")), size = range(data.get("size"));
        if(!(data.get("style") instanceof String styleName)) throw new IllegalArgumentException("Missing building emitter style");
        var style = Particle.Style.valueOf(styleName.toUpperCase(Locale.ROOT));
        return new Emitter((float) (p[0] * scale), (float) (p[1] * scale), style,
                new Color(parseColor(data.get("color"))),
                (int) interval[0], (int) interval[1], (int) life[0], (int) life[1],
                (float) (size[0] * tileScale), (float) (size[1] * tileScale),
                (float) (v[0] * tileScale), (float) (v[1] * tileScale));
    }

    private static Overlay bakeEmission(BufferedImage source, Rectangle crop, int w, int h, Map<?, ?> data){
        if(data.isEmpty()) return new Overlay(null, 0, 0);
        BufferedImage tinted = mask(data, source);
        tint(source, tinted, crop, parseColor(data.get("reference")), parseColor(data.get("color")), true);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        blit(g, tinted, crop, w, h);
        g.dispose();

        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for(int y = 0; y < h; y++) for(int x = 0; x < w; x++){
            if(scaled.getRGB(x, y) >>> 24 == 0) continue;
            x0 = Math.min(x0, x); y0 = Math.min(y0, y);
            x1 = Math.max(x1, x); y1 = Math.max(y1, y);
        }
        if(x1 < x0) return new Overlay(null, 0, 0);
        BufferedImage trimmed = new BufferedImage(x1 - x0 + 1, y1 - y0 + 1, BufferedImage.TYPE_INT_ARGB);
        trimmed.setData(scaled.getSubimage(x0, y0, trimmed.getWidth(), trimmed.getHeight()).getData());
        return new Overlay(trimmed, x0, y0);
    }

    // The mask alpha marks coverage. Keep the original shade variation and the base sprite's transparency.
    private static void tint(BufferedImage source, BufferedImage mask, Rectangle crop, int reference, int color, boolean emission){
        int baseShade = shade(reference);
        for(int y = crop.y; y < crop.y + crop.height; y++) for(int x = crop.x; x < crop.x + crop.width; x++){
            int coverage = mask.getRGB(x, y) >>> 24;
            if(coverage == 0) continue;
            int pixel = source.getRGB(x, y);
            int alpha = pixel >>> 24;
            int delta = shade(pixel) - baseShade;
            if(emission) delta /= 2;
            int target = (shift(color >>> 16, delta) << 16) | (shift(color >>> 8, delta) << 8) | shift(color, delta);
            if(emission){
                mask.setRGB(x, y, (alpha * coverage / 255 << 24) | target);
            } else {
                int r = mix(pixel >>> 16, target >>> 16, coverage);
                int g = mix(pixel >>> 8, target >>> 8, coverage);
                int b = mix(pixel, target, coverage);
                source.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static int shade(int rgb){
        return (3 * (rgb >>> 16 & 255) + 6 * (rgb >>> 8 & 255) + (rgb & 255)) / 10;
    }

    private static int shift(int channel, int delta){
        return Math.clamp((channel & 255) + delta, 0, 255);
    }

    private static int mix(int from, int to, int coverage){
        return ((from & 255) * (255 - coverage) + (to & 255) * coverage + 127) / 255;
    }

    private static void blit(Graphics2D g, BufferedImage img, Rectangle crop, int w, int h){
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, w, h, crop.x, crop.y, crop.x + crop.width, crop.y + crop.height, null);
    }

    private static BufferedImage mask(Map<?, ?> material, BufferedImage source){
        return mask(material.get("mask"), source);
    }

    private static BufferedImage mask(Object path, BufferedImage source){
        BufferedImage mask = image(path);
        if(mask.getWidth() != source.getWidth() || mask.getHeight() != source.getHeight()){
            throw new IllegalArgumentException("Building mask must match the source PNG size");
        }
        return mask;
    }

    private static BufferedImage image(Object path){
        if(!(path instanceof String resource)) throw new IllegalArgumentException("Missing building image path");
        try(InputStream stream = BuildingCatalog.class.getResourceAsStream(resource)){
            if(stream == null) throw new IOException("Missing building resource " + path);
            BufferedImage img = ImageIO.read(stream);
            if(img == null) throw new IOException("Invalid building image " + path);
            if(img.getType() == BufferedImage.TYPE_INT_ARGB) return img;
            // Some PNGs decode into indexed formats that cannot represent the tinted colors.
            BufferedImage rgba = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rgba.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(img, 0, 0, null);
            g.dispose();
            return rgba;
        } catch(IOException e){
            throw new UncheckedIOException(e);
        }
    }

    private static Map<?, ?> definitions(){
        try(InputStream stream = BuildingCatalog.class.getResourceAsStream("/scenery/buildings.json")){
            if(stream == null) throw new IOException("Missing building catalog");
            return object(MiniJson.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        } catch(IOException e){
            throw new UncheckedIOException(e);
        }
    }

    private static int parseColor(Object value){
        if(!(value instanceof String hex) || !hex.matches("#[0-9a-fA-F]{6}")){
            throw new IllegalArgumentException("Expected #RRGGBB color");
        }
        return Integer.parseInt(hex.substring(1), 16);
    }

    private static Map<?, ?> object(Object value){
        if(value == null) return Map.of();
        if(value instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("Expected a building data object");
    }

    private static List<?> list(Object value){
        if(value == null) return List.of();
        if(value instanceof List<?> values) return values;
        throw new IllegalArgumentException("Expected a building data array");
    }

    private static double number(Object value){
        if(value instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue();
        throw new IllegalArgumentException("Expected a finite building data number");
    }

    private static double[] numbers(Object value, int count){
        List<?> values = list(value);
        if(values.size() != count) throw new IllegalArgumentException("Expected " + count + " building data numbers");
        return values.stream().mapToDouble(BuildingCatalog::number).toArray();
    }

    private static double[] range(Object value){
        double[] range = numbers(value, 2);
        if(range[0] < 1 || range[0] > range[1]) throw new IllegalArgumentException("Invalid building emitter range");
        return range;
    }

    private static Rectangle rect(Object value, double scale){
        return rect(numbers(value, 4), scale);
    }

    private static Rectangle rect(double[] values, double scale){
        return new Rectangle(px(values[0], scale), px(values[1], scale), px(values[2], scale), px(values[3], scale));
    }

    private static int px(double value, double scale){
        return (int) Math.round(value * scale);
    }
}
