package entity;

import monster.SlimeMotion;

import java.awt.*;

/** Temporary spells use the existing effect list; damage always uses normal combat acceptance. */
public final class StaffSpell extends Entity {
    private final Player owner;
    private final String spell;
    private final int power, life;
    private int age;
    private final Entity target;
    private StatusEffect burn;
    private int fromX, fromY;
    private static final java.util.concurrent.atomic.AtomicInteger IDS =
            new java.util.concurrent.atomic.AtomicInteger();
    private int id = IDS.incrementAndGet();

    /** Creates a StaffSpell instance and initializes the state required by its collaborators. */
    StaffSpell(Player p, String spell, int x, int y, int power, int life, Entity target) {
        super(p.gp);
        owner = p;
        this.spell = spell;
        worldX = x;
        worldY = y;
        this.power = power;
        this.life = life;
        this.target = target;
        if ("Immolate".equals(spell)) {
            boolean fresh = !target.hasStatusEffect("immolated");
            burn = new StatusEffect("immolated", life + 1, 1, 0);
            target.statusEffects.removeIf(e -> e.type.equals("immolated"));
            target.addStatusEffect(burn);
            if (fresh) owner.mage.runeEvent();
        }
    }

    /** A short vector arc joins the actual source and impact positions of one chain hop. */
    static StaffSpell link(Player p, Entity from, Entity to, boolean lightning) {
        var link =
                new StaffSpell(
                        p,
                        lightning ? "Lightning" : "Thread",
                        SlimeMotion.centerX(to),
                        SlimeMotion.centerY(to),
                        0,
                        14,
                        null);
        link.fromX = SlimeMotion.centerX(from);
        link.fromY = SlimeMotion.centerY(from);
        return link;
    }

    /** Captures only the visual parameters. Guests never apply damage or generate runes. */
    public net.StaffVisual snapshot() {
        var state = new net.StaffVisual();
        state.id = id;
        state.spell = spell;
        state.x = worldX;
        state.y = worldY;
        state.fromX = fromX;
        state.fromY = fromY;
        state.age = age;
        state.life = life;
        return state;
    }

    /** Constructs an effect whose lifetime and position are synchronized by the host. */
    public StaffSpell(main.Gamepanel gp, net.StaffVisual state) {
        super(gp);
        owner = null;
        target = null;
        power = 0;
        life = state.life;
        spell = state.spell;
        id = state.id;
        sync(state);
    }

    /** Applies a host visual update without re-emitting its gameplay events. */
    public void sync(net.StaffVisual state) {
        age = state.age;
        worldX = state.x;
        worldY = state.y;
        fromX = state.fromX;
        fromY = state.fromY;
    }

    /** Stable network key, distinct from entity-array positions. */
    public int visualId() {
        return id;
    }

    /**
     * Advances a temporary spell and applies scheduled damage or utility pulses through existing
     * world rules.
     */
    @Override
    public void update() {
        if (owner == null || spell.equals("Lightning") || spell.equals("Thread")) {
            alive = ++age < life;
            return;
        }
        if (owner.isDead
                || owner.inWell != gp.quests.inDungeon()
                || target != null
                        && (!target.alive
                                || target.dying
                                || target instanceof Player ally
                                        && (ally.isDead || ally.inWell != owner.inWell))
                || burn != null && !target.statusEffects.contains(burn)) {
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
                    if (owner.mageAreaDamage(i, power, owner.direction, true, false, hit)
                            && spell.equals("Blizzard")) {
                        boolean fresh = !m.hasStatusEffect(MageEffect.FROST_SLOW);
                        MageCombat.mark(
                                m,
                                MageEffect.FROST_SLOW,
                                90,
                                32 + owner.mage.rank("winters_grasp") * 5);
                        if (fresh && !m.isBoss()) owner.mage.runeEvent();
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
        if (spell.equals("Lightning") || spell.equals("Thread")) {
            int dx = fromX - worldX, dy = fromY - worldY;
            v.setColor(new Color(213, 173, 247, 190));
            v.setStroke(new BasicStroke(spell.equals("Lightning") ? 5 : 2));
            var path = new java.awt.geom.Path2D.Float();
            path.moveTo(x + dx, y + dy - 14);
            path.lineTo(x + dx * .65 + 6, y + dy * .65 - 23);
            path.lineTo(x + dx * .42 - 6, y + dy * .42 - 8);
            path.lineTo(x, y - 12);
            v.draw(path);
            v.setColor(Color.WHITE);
            v.setStroke(new BasicStroke(1));
            v.draw(path);
            v.dispose();
            return;
        }
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
        if (spell.equals("Flowerfield")) {
            v.setComposite(AlphaComposite.SrcOver.derive(.9f));
            for (int i = 0; i < 19; i++) {
                double a = i * 2.4;
                int fx = x + (int) (Math.cos(a) * Math.sqrt(i) * 12),
                        fy = y + (int) (Math.sin(a) * Math.sqrt(i) * 7);
                v.setColor(art.Palette.FLOWERS[i % art.Palette.FLOWERS.length]);
                for (int k = 0; k < 5; k++)
                    v.fillOval(
                            fx + (int) (3 * Math.cos(k * 1.257)) - 2,
                            fy + (int) (3 * Math.sin(k * 1.257)) - 2,
                            5,
                            5);
                v.setColor(MageEffect.EMBER);
                v.fillOval(fx - 1, fy - 1, 3, 3);
            }
        }
        if (spell.equals("Rainfall")) {
            v.setComposite(AlphaComposite.SrcOver.derive(.85f));
            v.setColor(new Color(159, 174, 205));
            v.fillOval(x - 26, y - 62, 35, 18);
            v.fillOval(x - 12, y - 70, 32, 24);
            v.fillOval(x + 6, y - 63, 25, 17);
            v.setColor(MageEffect.FROST);
            for (int i = 0; i < 12; i++) {
                int rx = x + i * 17 % 50 - 25, ry = y + (age * 3 + i * 9) % 45 - 44;
                v.drawLine(rx, ry, rx - 2, ry + 5);
            }
        }
        v.dispose();
    }
}
