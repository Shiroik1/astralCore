package visual;

import entity.Particle;

import main.Gamepanel;

import java.awt.Graphics2D;
import java.util.*;

/**
 * Bounded visual events. Uses simulation seconds so rendering FPS never controls animation speed.
 */
public final class PlacementEffectManager {
    public static final int MAX_ANIMATIONS = 256, SMALL_PUFFS = 4, LARGE_PUFFS = 7;
    private final Gamepanel gp;
    private final Map<Long, PlacementAnimation> floors = new LinkedHashMap<>(),
            structures = new LinkedHashMap<>();
    private final Map<String, PlacementAnimation> objects = new LinkedHashMap<>();
    private double clock;

    /**
     * Creates a PlacementEffectManager instance and initializes the state required by its
     * collaborators.
     */
    public PlacementEffectManager(Gamepanel gp) {
        this.gp = gp;
    }

    /** Resolves the key result used by PlacementEffectManager. */
    private static long key(int col, int row) {
        return ((long) col << 32) | (row & 0xffffffffL);
    }

    /** Advances this component for one simulation tick. */
    public void update(double seconds) {
        clock += Math.max(0, seconds);
        floors.values().removeIf(a -> a.expired(clock));
        structures.values().removeIf(a -> a.expired(clock));
        objects.values().removeIf(a -> a.expired(clock));
    }

    /** Performs the add operation for PlacementEffectManager. */
    private <K> void add(Map<K, PlacementAnimation> map, K key) {
        if (activeAnimations() >= MAX_ANIMATIONS && !map.containsKey(key)) {
            Map<?, PlacementAnimation> oldest =
                    !floors.isEmpty() ? floors : !structures.isEmpty() ? structures : objects;
            oldest.remove(oldest.keySet().iterator().next());
        }
        map.put(key, new PlacementAnimation(clock));
    }

    /** Performs the tile operation for PlacementEffectManager. */
    public void tile(int col, int row, boolean floor) {
        add(floor ? floors : structures, key(col, row));
        int ts = gp.tileSize;
        Particle.placementSmoke(
                gp,
                col * ts + ts * .5f,
                row * ts + (floor ? ts * .5f : ts),
                ts,
                floor ? ts : 0,
                floor,
                SMALL_PUFFS);
    }

    /** Performs the object operation for PlacementEffectManager. */
    public void object(String id, double x, double y, double width) {
        add(objects, id);
        Particle.placementSmoke(gp, (float) x, (float) y, (float) width, 0, false, LARGE_PUFFS);
    }

    /** Performs the floor operation for PlacementEffectManager. */
    public void floor(Graphics2D g, int col, int row) {
        if (floors.isEmpty()) return;
        apply(g, floors.get(key(col, row)), gp.tileSize * .5, gp.tileSize * .5);
    }

    /** Performs the structure operation for PlacementEffectManager. */
    public void structure(Graphics2D g, int col, int row) {
        if (structures.isEmpty()) return;
        apply(g, structures.get(key(col, row)), gp.tileSize * .5, gp.tileSize);
    }

    /** Performs the object operation for PlacementEffectManager. */
    public void object(Graphics2D g, String id, double anchorX, double anchorY) {
        if (objects.isEmpty()) return;
        apply(g, objects.get(id), anchorX, anchorY);
    }

    /** Applies the supplied state or operation to this component. */
    private void apply(Graphics2D g, PlacementAnimation animation, double x, double y) {
        if (animation == null) return;
        double scale = animation.scale(clock);
        g.translate(x, y - animation.lift(clock) * gp.tileSize / 48.0);
        g.scale(scale, scale);
        g.translate(-x, -y);
    }

    /** Resolves the active animations result used by PlacementEffectManager. */
    public int activeAnimations() {
        return floors.size() + structures.size() + objects.size();
    }

    /** Performs the clear operation for PlacementEffectManager. */
    public void clear() {
        floors.clear();
        structures.clear();
        objects.clear();
        clock = 0;
        Particle.clearPlacementSmoke(gp);
    }
}
