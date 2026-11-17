package world;

import world.spatial.SpatialIndex;

import java.awt.Rectangle;
import java.util.*;
import java.util.function.Predicate;

/** Existing canonical scenery list with identity membership and local spatial queries. */
public final class IndexedScenery extends ArrayList<WorldObject> {
    private final SpatialIndex<WorldObject> index;
    private long revision;

    /**
     * Creates a IndexedScenery instance and initializes the state required by its collaborators.
     */
    public IndexedScenery(int tileSize) {
        index = new SpatialIndex<>(tileSize * 4);
    }

    /** Returns the current revision value. */
    public long revision() {
        return revision;
    }

    /** Resolves the query result used by IndexedScenery. */
    public List<WorldObject> query(Rectangle area) {
        return index.query(area);
    }

    /** Evaluates the contains condition for IndexedScenery. */
    @Override
    public boolean contains(Object value) {
        return value instanceof WorldObject o && index.contains(o);
    }

    /** Evaluates the add condition for IndexedScenery. */
    @Override
    public boolean add(WorldObject value) {
        if (index.contains(value)) return false;
        boolean added = super.add(value);
        index.add(value, value.spatialBounds());
        revision++;
        return added;
    }

    /** Performs the add operation for IndexedScenery. */
    @Override
    public void add(int at, WorldObject value) {
        if (index.contains(value)) return;
        super.add(at, value);
        index.add(value, value.spatialBounds());
        revision++;
    }

    /** Adds the all for IndexedScenery. */
    @Override
    public boolean addAll(Collection<? extends WorldObject> values) {
        boolean changed = false;
        for (WorldObject value : values) changed |= add(value);
        return changed;
    }

    /** Adds the all for IndexedScenery. */
    @Override
    public boolean addAll(int at, Collection<? extends WorldObject> values) {
        int before = size();
        for (WorldObject value : values) if (!contains(value)) add(at++, value);
        return before != size();
    }

    /** Resolves the remove result used by IndexedScenery. */
    @Override
    public WorldObject remove(int at) {
        WorldObject old = super.remove(at);
        index.remove(old);
        revision++;
        return old;
    }

    /** Evaluates the remove condition for IndexedScenery. */
    @Override
    public boolean remove(Object value) {
        int at = indexOf(value);
        if (at < 0) return false;
        remove(at);
        return true;
    }

    /** Resolves the set result used by IndexedScenery. */
    @Override
    public WorldObject set(int at, WorldObject value) {
        WorldObject old = super.set(at, value);
        index.remove(old);
        index.add(value, value.spatialBounds());
        revision++;
        return old;
    }

    /** Performs the clear operation for IndexedScenery. */
    @Override
    public void clear() {
        super.clear();
        index.clear();
        revision++;
    }

    /** Removes the if for IndexedScenery. */
    @Override
    public boolean removeIf(Predicate<? super WorldObject> predicate) {
        List<WorldObject> removed = new ArrayList<>();
        boolean changed =
                super.removeIf(value -> {
                    if (!predicate.test(value)) return false;
                    removed.add(value);
                    return true;
                });
        for (WorldObject value : removed) index.remove(value);
        if (changed) revision++;
        return changed;
    }

    /** Removes the all for IndexedScenery. */
    @Override
    public boolean removeAll(Collection<?> values) {
        Set<Object> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(values);
        return removeIf(set::contains);
    }

    /** Evaluates the retain all condition for IndexedScenery. */
    @Override
    public boolean retainAll(Collection<?> values) {
        return removeIf(v -> !values.contains(v));
    }
}
