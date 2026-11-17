package object;

import art.RangerGear;

import entity.Entity;

import main.Gamepanel;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

public final class OBJ_rangerbow extends Entity {
    private static final EnumMap<RangerGear, BufferedImage> ICONS = new EnumMap<>(RangerGear.class);
    public final ItemType itemType;
    public final RangerGear gear;

    /** Creates a OBJ_rangerbow instance and initializes the state required by its collaborators. */
    OBJ_rangerbow(Gamepanel gp, ItemType itemType, RangerGear gear) {
        super(gp);
        this.itemType = itemType;
        this.gear = gear;
        type = type_bow;
        name = gear.bow;
        attackValue = gear.power;
        description =
                "(" + name + ")\nRanger · Level " + gear.level + "\n+" + gear.power + " Power";
        synchronized (ICONS) {
            down1 =
                    ICONS.computeIfAbsent(
                            gear,
                            tier -> {
                                var icon = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
                                var g = icon.createGraphics();
                                g.setRenderingHint(
                                        RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                                g.rotate(-0.45, 24, 24);
                                tier.drawBow(g, 23, 24, 0.9f, 0);
                                g.dispose();
                                return icon;
                            });
        }
    }
}
