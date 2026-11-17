package world.spatial;

import java.awt.Rectangle;
import java.util.*;

/**
 * Game-thread uniform spatial index. Values retain one authoritative owner; buckets hold references
 * only.
 */
public final class SpatialIndex<T> {
    private final int cell;
    private final Map<Long, List<T>> buckets = new HashMap<>();
    private final IdentityHashMap<T, Rectangle> bounds = new IdentityHashMap<>();

    /** Creates a SpatialIndex instance and initializes the state required by its collaborators. */
    public SpatialIndex(int cell) {
        this.cell = Math.max(1, cell);
    }

    /** Resolves the key result used by SpatialIndex. */
    public static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /** Performs the clear operation for SpatialIndex. */
    public void clear() {
        buckets.clear();
        bounds.clear();
    }

    /** Evaluates the contains condition for SpatialIndex. */
    public boolean contains(T value) {
        return bounds.containsKey(value);
    }

    /** Resolves the size result used by SpatialIndex. */
    public int size() {
        return bounds.size();
    }

    /** Performs the add operation for SpatialIndex. */
    public void add(T value, Rectangle box) {
        remove(value);
        Rectangle r = new Rectangle(box);
        if (r.isEmpty()) r.setSize(1, 1);
        bounds.put(value, r);
        for (int y = Math.floorDiv(r.y, cell); y <= Math.floorDiv(r.y + r.height - 1, cell); y++)
            for (int x = Math.floorDiv(r.x, cell); x <= Math.floorDiv(r.x + r.width - 1, cell); x++)
                buckets.computeIfAbsent(key(x, y), k -> new ArrayList<>()).add(value);
    }

    /** Performs the remove operation for SpatialIndex. */
    public void remove(T value) {
        Rectangle r = bounds.remove(value);
        if (r == null) return;
        for (int y = Math.floorDiv(r.y, cell); y <= Math.floorDiv(r.y + r.height - 1, cell); y++)
            for (int x = Math.floorDiv(r.x, cell);
                    x <= Math.floorDiv(r.x + r.width - 1, cell);
                    x++) {
                long k = key(x, y);
                List<T> list = buckets.get(k);
                if (list != null) {
                    list.remove(value);
                    if (list.isEmpty()) buckets.remove(k);
                }
            }
    }

    /** Resolves the query result used by SpatialIndex. */
    public List<T> query(Rectangle area) {
        if (area.isEmpty() || buckets.isEmpty()) return List.of();
        int left = Math.floorDiv(area.x, cell),
                right = Math.floorDiv(area.x + area.width - 1, cell),
                top = Math.floorDiv(area.y, cell),
                bottom = Math.floorDiv(area.y + area.height - 1, cell);
        if (left == right && top == bottom) {
            List<T> values = buckets.get(key(left, top));
            if (values == null) return List.of();
            List<T> result = new ArrayList<>();
            for (T value : values) if (bounds.get(value).intersects(area)) result.add(value);
            return result;
        }
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<T> result = new ArrayList<>();
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                List<T> values = buckets.get(key(x, y));
                if (values != null)
                    for (T value : values)
                        if (seen.add(value) && bounds.get(value).intersects(area))
                            result.add(value);
            }
        return result;
    }
}
