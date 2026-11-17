package art;

import java.awt.*;
import java.awt.geom.*;

/** The five dagger silhouettes and muted leathers from the Assassin concept sheet. */
public enum AssassinGear {
    ALLEY(
            "Alley Scout",
            "Flintfang Pair",
            1,
            0,
            3,
            1,
            0x453839,
            0x674953,
            0xE6D3BF,
            0xA28873,
            15,
            1),
    BRIAR(
            "Briar Shade",
            "Briarhook Pair",
            5,
            1,
            5,
            3,
            0x554048,
            0x73515C,
            0xEDCDB5,
            0xB37B5D,
            16,
            4),
    ASH("Ashveil", "Ashglass Daggers", 10, 3, 9, 5, 0x4D484E, 0x655A68, 0xD9D1C9, 0xA7A3A3, 17, 2),
    MOON("Moonveil", "Moonsplit Pair", 17, 6, 13, 7, 0x44414D, 0x565464, 0xDCD8DE, 0xA4AABA, 17, 6),
    GLOAM(
            "Gloam Regent",
            "Duskfang Relics",
            25,
            8,
            19,
            9,
            0x3A303A,
            0x663D50,
            0xF2DDC2,
            0xD6A86D,
            19,
            5);
    public final String outfit, daggers;
    public final int level, health, power, armor;
    public final Color tunic, cloak, edge, trim;
    private final int length, hook;

    /** Creates a AssassinGear instance and initializes the state required by its collaborators. */
    AssassinGear(
            String outfit,
            String daggers,
            int level,
            int health,
            int power,
            int armor,
            int tunic,
            int cloak,
            int edge,
            int trim,
            int length,
            int hook) {
        this.outfit = outfit;
        this.daggers = daggers;
        this.level = level;
        this.health = health;
        this.power = power;
        this.armor = armor;
        this.tunic = new Color(tunic);
        this.cloak = new Color(cloak);
        this.edge = new Color(edge);
        this.trim = new Color(trim);
        this.length = length;
        this.hook = hook;
    }

    /** Resolves the pants result used by AssassinGear. */
    public Color pants() {
        return ArtKit.mix(tunic, new Color(0x30282C), 0.45f);
    }

    /** Resolves the boots result used by AssassinGear. */
    public Color boots() {
        return ArtKit.mix(tunic, new Color(0x6D4D40), 0.44f);
    }

    /** Resolves the gloves result used by AssassinGear. */
    public Color gloves() {
        return ArtKit.mix(tunic, new Color(0x846653), 0.43f);
    }

    /** Draws the dagger for AssassinGear. */
    void drawDagger(Graphics2D g, AffineTransform transform, boolean offHand) {
        float tip = offHand && this != ALLEY ? length - 1 : length;
        float curve = offHand ? hook * 0.65f : hook;
        Path2D blade = new Path2D.Float();
        blade.moveTo(-3, -2);
        blade.quadTo(-5, -tip * 0.52f, curve - 2, -tip + 2);
        blade.lineTo(curve, -tip);
        blade.quadTo(curve + 2, -tip * 0.45f, 2, -2);
        blade.closePath();
        CharacterArt.softPart(g, transform.createTransformedShape(blade), edge);
        Path2D bevel = new Path2D.Float();
        bevel.moveTo(0, -2);
        bevel.quadTo(-1, -tip * 0.6f, curve, -tip);
        bevel.quadTo(curve + 2, -tip * 0.45f, 2, -2);
        bevel.closePath();
        g.setColor(ArtKit.mix(edge, trim, 0.42f));
        g.fill(transform.createTransformedShape(bevel));
        CharacterArt.softPart(
                g,
                transform.createTransformedShape(
                        new RoundRectangle2D.Float(-1.4f, 0, 2.8f, 7, 2, 2)),
                boots());
        g.setColor(trim);
        g.fill(
                transform.createTransformedShape(
                        new RoundRectangle2D.Float(-4.5f, -2.6f, 9, 2.7f, 2, 2)));
        g.fill(transform.createTransformedShape(new Ellipse2D.Float(-2, 5.2f, 4, 4)));
    }
}
