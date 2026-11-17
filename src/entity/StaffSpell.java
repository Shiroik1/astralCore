package entity;

import monster.SlimeMotion;

import java.awt.*;

/** Temporary spells use the existing effect list; damage always uses normal combat acceptance. */
final class StaffSpell extends Entity {
    private final Player owner;
    private final String spell;
    private final int power, life;
    private int age;
    private final Monster target;

    /** Creates a StaffSpell instance and initializes the state required by its collaborators. */
    StaffSpell(Player p, String spell, int x, int y, int power, int life, Monster target) {
        super(p.gp);
        owner = p;
        this.spell = spell;
        worldX = x;
        worldY = y;
        this.power = power;
        this.life = life;
        this.target = target;
    }

    /**
     * Advances a temporary spell and applies scheduled damage or utility pulses through existing
     * world rules.
     */
    @Override
    public void update() {
        if (owner.isDead
                || owner.inWell != gp.quests.inDungeon()
                || target != null && (!target.alive || target.dying)) {
            alive = false;
            return;
        }
        age++;
        if (target != null) {
            worldX = SlimeMotion.centerX(target);
            worldY = SlimeMotion.centerY(target);
        }
        if (age == 1 || age % 45 == 0) {
            if (spell.equals("Flowerfield") || spell.equals("Rainfall")) {
                gp.grass.burst(worldX, worldY, gp.tileSize * 1.5f, .25f);
                Particle.emit(
                        this,
                        Particle.Style.MOTE,
                        spell.equals("Rainfall") ? art.Palette.WATER : art.Palette.FLOWERS[0],
                        6,
                        28,
                        0,
                        -.3f,
                        0,
                        0);
            } else {
                CombatHit hit = new CombatHit("staff:" + spell).mastery(type_staff);
                for (int i = 0; i < gp.monster.length; i++) {
                    Monster m = gp.monster[i];
                    if (m == null || !m.alive || m.dying || target != null && m != target) continue;
                    if (Math.hypot(SlimeMotion.centerX(m) - worldX, SlimeMotion.centerY(m) - worldY)
                                    > gp.tileSize * (spell.equals("Convergence") ? 2 : 1.5)
                            || !SlimeMotion.sight(
                                    gp,
                                    worldX,
                                    worldY,
                                    SlimeMotion.centerX(m),
                                    SlimeMotion.centerY(m))) continue;
                    if (owner.damageMonsterOnce(
                            i, power, false, owner.direction, hit, CombatTuning.BOLT)) {
                        if (spell.equals("Immolate")
                                && age == 1
                                && !m.hasStatusEffect("immolated")) {
                            m.addStatusEffect(new StatusEffect("immolated", life, 1, 0));
                            owner.mage.runeEvent();
                        }
                        if (spell.equals("Blizzard"))
                            m.addStatusEffect(
                                    new StatusEffect(
                                            MageEffect.FROST_SLOW,
                                            60,
                                            32 + owner.mage.rank("winters_grasp") * 5,
                                            0));
                    }
                }
            }
        }
        if (age >= life) alive = false;
    }

    /** Renders this component into the supplied Java2D graphics context. */
    @Override
    public void draw(Graphics2D g) {
        Player cam = gp.localPlayer();
        int x = worldX - cam.worldX + cam.screenX, y = worldY - cam.worldY + cam.screenY;
        Graphics2D v = (Graphics2D) g.create();
        v.setColor(
                new Color(
                        spell.equals("Immolate")
                                ? 0xCE7D69
                                : spell.equals("Blizzard") || spell.equals("Rainfall")
                                        ? 0x9BCDE8
                                        : 0xD5B3D0));
        v.setComposite(AlphaComposite.SrcOver.derive(.35f));
        v.setStroke(new BasicStroke(3));
        int r = spell.equals("Convergence") ? gp.tileSize * 2 : gp.tileSize;
        v.drawOval(x - r, y - r / 2, r * 2, r);
        v.drawArc(x - r / 2, y - r / 2, r, r, age * 5, 230);
        if (spell.equals("Flowerfield"))
            for (int i = 0; i < 7; i++) {
                v.setColor(art.Palette.FLOWERS[i % art.Palette.FLOWERS.length]);
                v.fillOval(x + (i % 4) * 15 - 25, y + (i / 4) * 17 - 10, 9, 9);
            }
        if (spell.equals("Rainfall"))
            for (int i = 0; i < 8; i++)
                v.drawLine(
                        x + (i * 17) % 80 - 40,
                        y + (age * 3 + i * 9) % 45 - 35,
                        x + (i * 17) % 80 - 42,
                        y + (age * 3 + i * 9) % 45 - 27);
        v.dispose();
    }
}
