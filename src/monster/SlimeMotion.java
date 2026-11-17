package monster;

import entity.*;

import main.Gamepanel;

import java.awt.Rectangle;

/** Ground collision is authoritative even while the sprite is visually airborne. */
public final class SlimeMotion {
    /** Prevents instantiation of this utility class. */
    private SlimeMotion() {}

    /** Resolves the body result used by SlimeMotion. */
    public static Rectangle body(Entity e, int x, int y) {
        return new Rectangle(
                x + e.solidArea.x, y + e.solidArea.y, e.solidArea.width, e.solidArea.height);
    }

    /** Resolves the distance result used by SlimeMotion. */
    public static double distance(Entity a, Entity b) {
        return Math.hypot(centerX(a) - centerX(b), centerY(a) - centerY(b));
    }

    /** Resolves the center x result used by SlimeMotion. */
    public static int centerX(Entity e) {
        return e.worldX + e.solidArea.x + e.solidArea.width / 2;
    }

    /** Resolves the center y result used by SlimeMotion. */
    public static int centerY(Entity e) {
        return e.worldY + e.solidArea.y + e.solidArea.height / 2;
    }

    /** Resolves the facing result used by SlimeMotion. */
    public static String facing(double dx, double dy) {
        return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "right" : "left") : (dy > 0 ? "down" : "up");
    }

    /** Evaluates the clear condition for SlimeMotion. */
    public static boolean clear(Gamepanel g, Rectangle b) {
        long performanceStart = g.performance.start();
        try {
            if (g.tileM.blocksMovement(b.x, b.y, b.x + b.width - 1, b.y + b.height - 1))
                return false;
            if (!g.quests.inDungeon() && g.crafting.blocks(b)) return false;
            for (var o : g.worldObjects.query(b))
                if (o.blocksArea(b.x, b.y, b.width, b.height)) return false;
            for (var t : g.interactable)
                if (t != null && t.collision && b.intersects(body(t, t.worldX, t.worldY)))
                    return false;
            for (var n : g.npc)
                if (n != null && b.intersects(body(n, n.worldX, n.worldY))) return false;
            for (var o : g.obj)
                if (o != null && o.collision && b.intersects(body(o, o.worldX, o.worldY)))
                    return false;
            return true;
        } finally {
            g.performance.end(main.PerformanceMetrics.Section.COLLISION, performanceStart);
        }
    }

    /** Evaluates the sight condition for SlimeMotion. */
    public static boolean sight(Gamepanel g, int x, int y, int tx, int ty) {
        int n = Math.max(1, (int) Math.ceil(Math.hypot(tx - x, ty - y) / 6));
        for (int i = 0; i <= n; i++)
            if (!clear(g, new Rectangle(x + (tx - x) * i / n - 2, y + (ty - y) * i / n - 2, 4, 4)))
                return false;
        return true;
    }

    /** Evaluates the sight condition for SlimeMotion. */
    public static boolean sight(Gamepanel g, Entity a, Entity b) {
        return sight(g, centerX(a), centerY(a), centerX(b), centerY(b));
    }

    /** Pixel substeps prevent fast leaps/projectiles tunnelling through walls or stations. */
    public static boolean move(Gamepanel g, Monster m, int tx, int ty, boolean separate) {
        int x = m.worldX,
                y = m.worldY,
                dx = tx - x,
                dy = ty - y,
                n = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
        for (int i = 1; i <= n; i++) {
            int nx = x + dx * i / n, ny = y + dy * i / n;
            Rectangle b = body(m, nx, ny);
            if (!clear(g, b)) return false;
            if (separate)
                for (Monster other : g.monster)
                    if (other != null
                            && other != m
                            && other.alive
                            && !other.dying
                            && b.intersects(body(other, other.worldX, other.worldY))) return false;
            m.worldX = nx;
            m.worldY = ny;
        }
        return true;
    }

    /** Evaluates the active player condition for SlimeMotion. */
    public static boolean activePlayer(Gamepanel g, Player p) {
        return p != null && !p.isDead && p.inWell == g.quests.inDungeon();
    }

    /** Performs the hit operation for SlimeMotion. */
    public static void hit(Gamepanel g, Monster m, Player p, int damage) {
        if (!activePlayer(g, p) || p.invincible || !sight(g, m, p)) return;
        m.direction = facing(centerX(p) - centerX(m), centerY(p) - centerY(m));
        m.damagePlayer(p, damage);
    }
}
