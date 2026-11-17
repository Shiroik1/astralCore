package world.generation;

import world.WorldObjectType;

import java.awt.Rectangle;
import java.util.*;

/**
 * Guarantees the actual existing resource sprites fit before water, paths or decoration are
 * planned.
 */
final class StarterResourcePlanner {
    /** Performs the reserve operation for StarterResourcePlanner. */
    void reserve(GeneratedWorld w, WorldPalette palette, int ts) {
        int width = w.config.width(), height = w.config.height();
        var candidates = new ArrayList<Integer>();
        for (int y = 1; y < height - 1; y++)
            for (int x = 1; x < width - 1; x++)
                if (Math.hypot(x - w.spawnCol, y - w.spawnRow) <= 13) candidates.add(y * width + x);
        candidates.sort(
                Comparator.comparingDouble((Integer p) ->
                                Math.hypot(p % width - w.spawnCol, p / width - w.spawnRow))
                        .thenComparingInt(Integer::intValue));
        var props = new ArrayList<GeneratedWorld.Placement>();
        var images = new ArrayList<Rectangle>();
        for (WorldObjectType type :
                new WorldObjectType[] {WorldObjectType.OAK_1, WorldObjectType.ROCK_LARGE}) {
            for (int id : candidates) {
                int x = id % width, y = id / width;
                Rectangle image = new Rectangle(palette.footprint(type).image());
                image.translate(x * ts + ts / 2, (y + 1) * ts - 4);
                if (!fits(w, image, ts)) continue;
                Rectangle margin = new Rectangle(image);
                margin.grow(ts, ts / 2);
                boolean overlap = false;
                for (Rectangle old : images) if (margin.intersects(old)) overlap = true;
                if (overlap) continue;
                props.add(new GeneratedWorld.Placement(type, x, y));
                images.add(image);
                reserve(w, image, ts);
                reserveApproach(w, x, Math.min(height - 1, y + 1));
                break;
            }
        }
        var fiber = new ArrayList<Integer>();
        for (int id : candidates) {
            int x = id % width, y = id / width;
            if (x < 1
                    || y < 2
                    || x >= width - 1
                    || y >= height - 1
                    || Math.hypot(x - w.spawnCol, y - w.spawnRow) < 4) continue;
            Rectangle patch = new Rectangle((x - 1) * ts, (y - 2) * ts, 3 * ts, 4 * ts);
            if (!fits(w, patch, ts)) continue;
            boolean overlap = false;
            for (Rectangle image : images) if (patch.intersects(image)) overlap = true;
            if (overlap) continue;
            fiber.add(id);
            reserve(w, patch, ts);
            reserveApproach(w, x, y);
            if (fiber.size() == 4) break;
        }
        if (props.size() != 2 || fiber.isEmpty())
            throw new IllegalArgumentException(
                    "World is too small for the existing starter resource artwork");
        w.starterPlan(props, fiber);
    }

    /** Evaluates the fits condition for StarterResourcePlanner. */
    private boolean fits(GeneratedWorld w, Rectangle area, int ts) {
        int margin = w.generatorVersion >= 9 ? ts : 0;
        if (!new Rectangle(
                        margin,
                        margin,
                        w.config.width() * ts - 2 * margin,
                        w.config.height() * ts - 2 * margin)
                .contains(area)) return false;
        for (int y = area.y / ts; y <= (area.y + area.height - 1) / ts; y++)
            for (int x = area.x / ts; x <= (area.x + area.width - 1) / ts; x++)
                if (w.coreReserved(x, y)) return false;
        return true;
    }

    /** Performs the reserve operation for StarterResourcePlanner. */
    private void reserve(GeneratedWorld w, Rectangle area, int ts) {
        // The wide rock needs walking room beside it as well as space for its image.
        Rectangle clearance = new Rectangle(area);
        clearance.grow(ts, ts);
        clearance =
                clearance.intersection(
                        new Rectangle(0, 0, w.config.width() * ts, w.config.height() * ts));
        for (int y = clearance.y / ts; y <= (clearance.y + clearance.height - 1) / ts; y++)
            for (int x = clearance.x / ts; x <= (clearance.x + clearance.width - 1) / ts; x++)
                w.reserveStarter(x, y);
    }

    /** Performs the reserve approach operation for StarterResourcePlanner. */
    private void reserveApproach(GeneratedWorld w, int x, int y) {
        for (int c = Math.min(x, w.spawnCol); c <= Math.max(x, w.spawnCol); c++) {
            if (w.generatorVersion >= 11) {
                if (!w.starterReserved(c, w.spawnRow)) w.reserveAccess(c, w.spawnRow);
            } else w.reserveStarter(c, w.spawnRow);
        }
        for (int r = Math.min(y, w.spawnRow); r <= Math.max(y, w.spawnRow); r++) {
            if (w.generatorVersion >= 11) {
                if (!w.starterReserved(x, r)) w.reserveAccess(x, r);
            } else w.reserveStarter(x, r);
        }
    }
}
