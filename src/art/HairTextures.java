package art;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;

/** Loads exported PNGs only; never generates geometry or writes resources during play. */
public final class HairTextures {
    public static final int FRAME = 320, VERSION = 1;
    private static final Map<String, BufferedImage> CACHE =
            new LinkedHashMap<>(32, .75f, true) {
                /** Removes the eldest entry for . */
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) {
                    return size() > 24;
                }
            };
    private static boolean manifestChecked;
    private static int decodes;

    /** Prevents instantiation of this utility class. */
    private HairTextures() {}

    /** Resolves the resource result used by HairTextures. */
    static String resource(CharacterOutfit o) {
        if (o.details().hairStyle() < 0)
            return "/hair/legacy/"
                    + String.format("%06x", o.hair().getRGB() & 0xffffff)
                    + (o.longHair() ? "_long" : "_short")
                    + ".png";
        int color = -1;
        for (int i = 0; i < Hairstyles.COLORS.length; i++)
            if (Hairstyles.COLORS[i].equals(o.hair())) {
                color = i;
                break;
            }
        if (color < 0)
            throw new IllegalArgumentException("Hair color is not in the exported palette");
        return String.format("/hair/style_%02d/color_%02d.png", o.details().hairStyle(), color);
    }

    /** Resolves the atlas result used by HairTextures. */
    private static synchronized BufferedImage atlas(String resource) {
        if (!manifestChecked) {
            try (InputStream in =
                    HairTextures.class.getResourceAsStream("/hair/manifest.properties")) {
                if (in == null) throw new IOException("Missing hair texture manifest");
                Properties p = new Properties();
                p.load(in);
                if (!Integer.toString(VERSION).equals(p.getProperty("version"))
                        || !Integer.toString(FRAME).equals(p.getProperty("frameSize")))
                    throw new IOException("Incompatible hair textures");
                manifestChecked = true;
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Rebuild hair textures with art.GenerateHairTextures.", e);
            }
        }
        BufferedImage image = CACHE.get(resource);
        if (image != null) return image;
        try (InputStream in = HairTextures.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Missing " + resource);
            image = ImageIO.read(in);
            if (image == null || image.getWidth() != FRAME * 4 || image.getHeight() != FRAME * 2)
                throw new IOException("Invalid hair texture dimensions: " + resource);
            CACHE.put(resource, image);
            decodes++;
            return image;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot load hair PNG. Rebuild with art.GenerateHairTextures: " + resource, e);
        }
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public static void draw(
            Graphics2D g,
            CharacterOutfit o,
            boolean side,
            boolean back,
            boolean rear,
            float swing) {
        if (o.hair() == null) return;
        boolean left = side && g.getTransform().getDeterminant() < 0;
        int view = side ? (left ? 2 : 3) : (back ? 1 : 0), layer = rear ? 1 : 0;
        BufferedImage image = atlas(resource(o));
        Graphics2D d = (Graphics2D) g.create();
        int id = o.details().hairStyle();
        if (rear
                && (id == 10 || id == 13 || id == 23 || id == 30 || id == 33 || id == 34
                        || id == 39)) d.translate(Math.abs(swing) * .45, 0);
        // Left PNGs already have mirrored geometry and correct top-left lighting.
        if (left) {
            d.translate(64, 0);
            d.scale(-1, 1);
        }
        d.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        d.translate(-8, -16);
        d.scale(80.0 / FRAME, 80.0 / FRAME);
        d.clipRect(0, 0, FRAME, FRAME);
        d.drawImage(image, -view * FRAME, -layer * FRAME, null);
        d.dispose();
    }

    /** Returns the current decodes value. */
    static synchronized int decodedAtlases() {
        return decodes;
    }
}
