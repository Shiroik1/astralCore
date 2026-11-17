package interaction;

import entity.*;

import main.Gamepanel;

import java.awt.*;

/** Logical feet and cardinal adjacency only; sprite projection/canopies never determine reach. */
public final class InteractionValidator {
    private final Gamepanel gp;

    /**
     * Creates a InteractionValidator instance and initializes the state required by its
     * collaborators.
     */
    public InteractionValidator(Gamepanel gp) {
        this.gp = gp;
    }

    /** Resolves the tile result used by InteractionValidator. */
    public Point tile(Player p) {
        return new Point(
                Math.floorDiv(p.worldX + p.solidArea.x + p.solidArea.width / 2, gp.tileSize),
                Math.floorDiv(p.worldY + p.solidArea.y + p.solidArea.height - 1, gp.tileSize));
    }

    /** Resolves the front result used by InteractionValidator. */
    public Point front(Player p) {
        Point cell = tile(p);
        switch (p.direction) {
            case "up" -> cell.y--;
            case "left" -> cell.x--;
            case "right" -> cell.x++;
            default -> cell.y++;
        }
        return cell;
    }

    /** Resolves the front bounds result used by InteractionValidator. */
    public Rectangle frontBounds(Player p) {
        Point at = front(p);
        return new Rectangle(at.x * gp.tileSize, at.y * gp.tileSize, gp.tileSize, gp.tileSize);
    }

    /** Evaluates the valid condition for InteractionValidator. */
    public boolean valid(Player p, Rectangle footprint) {
        return valid(p, footprint, null);
    }

    /** Evaluates the valid condition for InteractionValidator. */
    public boolean valid(Player p, Rectangle footprint, Object token) {
        Object owner = gp.crafting.collisionOwner(token);
        if (p == null || footprint == null || footprint.isEmpty()) return false;
        Point from = tile(p), to = front(p);
        int ts = gp.tileSize;
        if (to.x < 0
                || to.y < 0
                || to.x >= gp.maxWorldCol
                || to.y >= gp.maxWorldRow
                || !frontBounds(p).intersects(footprint)) return false;
        if (world.TerrainHeightResolver.active(gp)) {
            var heights = gp.generatedWorld.heights();
            if (!heights.playable(from.x, from.y)
                    || !heights.playable(to.x, to.y)
                    || heights.at(from.x, from.y) != heights.at(to.x, to.y)) return false;
        } else if (gp.tileM.buildingElevation(from.x, from.y)
                != gp.tileM.buildingElevation(to.x, to.y)) return false;
        Rectangle hit = frontBounds(p).intersection(footprint);
        int x = p.worldX + p.solidArea.x + p.solidArea.width / 2,
                y = p.worldY + p.solidArea.y + p.solidArea.height - 1;
        int tx = (int) hit.getCenterX(), ty = (int) hit.getCenterY();
        var nearby =
                gp.worldObjects.query(
                        new Rectangle(
                                Math.min(x, tx),
                                Math.min(y, ty),
                                Math.abs(tx - x) + 1,
                                Math.abs(ty - y) + 1));
        int steps = Math.max(1, Math.max(Math.abs(tx - x), Math.abs(ty - y)));
        for (int i = 0; i <= steps; i++) {
            int px = x + (tx - x) * i / steps, py = y + (ty - y) * i / steps;
            // Terrain remains authoritative even inside the interactable's own collision footprint.
            if (gp.tileM.blocksTerrainMovement(px, py, px, py)) return false;
            if (gp.buildings.blocksAreaExcept(px, py, 1, 1, token)
                    || gp.crafting.blocksExcept(new Rectangle(px, py, 1, 1), owner)) return false;
            for (var o : nearby)
                if ((o != owner || !footprint.contains(px, py)) && o.blocksArea(px, py, 1, 1))
                    return false;
            for (var o : gp.obj)
                if (o != null && o != owner && o.collision && bounds(o).contains(px, py))
                    return false;
            for (var o : gp.npc)
                if (o != null && o != owner && bounds(o).contains(px, py)) return false;
            for (var o : gp.interactable)
                if (o != null && o != owner && o.collision && bounds(o).contains(px, py))
                    return false;
        }
        return true;
    }

    /** Resolves the bounds result used by InteractionValidator. */
    public static Rectangle bounds(Entity e) {
        return new Rectangle(
                e.worldX + e.solidArea.x,
                e.worldY + e.solidArea.y,
                e.solidArea.width,
                e.solidArea.height);
    }
}
