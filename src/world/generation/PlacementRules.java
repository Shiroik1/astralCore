package world.generation;

import art.Terrain;

import world.WorldObjectType;

import java.awt.Rectangle;
import java.util.*;

/** One footprint validator for every new regional prop. Never loads artwork itself. */
final class PlacementRules {
    record Entry(
            GeneratedWorld.Placement placement,
            Rectangle image,
            Rectangle body,
            Rectangle spaced) {}

    final GeneratedWorld world;
    final int ts, width, height;
    private final WorldPalette palette;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<Integer, List<Entry>> buckets = new HashMap<>();
    private final boolean[][] visual, anchors;
    private boolean[][] reachable;
    private float[][] treeInfluence;
    private final float[][] bushShade;

    /**
     * Creates a PlacementRules instance and initializes the state required by its collaborators.
     */
    PlacementRules(GeneratedWorld world, WorldPalette palette, int ts) {
        this.world = world;
        this.palette = palette;
        this.ts = ts;
        width = world.config.width();
        height = world.config.height();
        visual = new boolean[width][height];
        anchors = new boolean[width][height];
        bushShade = new float[width][height];
    }

    /** Evaluates the tree condition for PlacementRules. */
    static boolean tree(WorldObjectType t) {
        return t == WorldObjectType.OAK_1
                || t == WorldObjectType.OAK_2
                || t == WorldObjectType.PINE_1
                || t == WorldObjectType.PINE_2;
    }

    /** Evaluates the rock condition for PlacementRules. */
    static boolean rock(WorldObjectType t) {
        return t == WorldObjectType.ROCK_LARGE || t == WorldObjectType.ROCK_LARGE_MOSS;
    }

    /** Evaluates the inside condition for PlacementRules. */
    boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Evaluates the grass condition for PlacementRules. */
    boolean grass(int x, int y) {
        return inside(x, y)
                && world.terrain(x, y) == Terrain.GRASS
                && (world.heights() == null || world.heights().playable(x, y));
    }

    /** Evaluates the try place condition for PlacementRules. */
    boolean tryPlace(WorldObjectType type, int col, int row) {
        return tryPlace(type, col, row, false);
    }

    /** Evaluates the try place condition for PlacementRules. */
    boolean tryPlace(WorldObjectType type, int col, int row, boolean starter) {
        if (!grass(col, row)
                || (starter ? world.coreReserved(col, row) : world.decorationReserved(col, row)))
            return false;
        var fp = palette.footprint(type);
        if (fp == null) return false;
        Rectangle image = new Rectangle(fp.image()), body = new Rectangle(fp.solid());
        image.translate(col * ts + ts / 2, (row + 1) * ts - 4);
        body.translate(col * ts + ts / 2, (row + 1) * ts - 4);
        if (!new Rectangle(0, 0, width * ts, height * ts).contains(image)) return false;
        int left = image.x / ts,
                right = (image.x + image.width - 1) / ts,
                top = image.y / ts,
                bottom = (image.y + image.height - 1) / ts;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if ((starter ? world.coreReserved(x, y) : world.decorationReserved(x, y))
                        || world.regions().goldenMargin[x][y]
                        || world.generatorVersion < 7 && !grass(x, y)) return false;
        if (world.heights() != null) {
            int elevation = world.heights().at(col, row);
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++)
                    if (!world.heights().playable(x, y) || world.heights().at(x, y) != elevation)
                        return false;
        }
        boolean solid = body.width > 0 && body.height > 0;
        boolean understory = type == WorldObjectType.TWIG || type.name().startsWith("MUSHROOM");
        if (anchors[col][row] || !solid && visual[col][row] && !understory) return false;
        Rectangle spaced = new Rectangle(body);
        int gap =
                (int)
                        Math.round(
                                ts
                                        * (tree(type)
                                                ? world.generation.treeSpacing()
                                                : rock(type) ? world.generation.rockSpacing() : .2)
                                        / 2);
        if (solid) {
            if (world.generatorVersion >= 11)
                for (int y = Math.floorDiv(body.y, ts);
                        y <= Math.floorDiv(body.y + body.height - 1, ts);
                        y++)
                    for (int x = Math.floorDiv(body.x, ts);
                            x <= Math.floorDiv(body.x + body.width - 1, ts);
                            x++) if (world.accessReserved(x, y)) return false;
            if (world.generatorVersion >= 7)
                for (int y = Math.floorDiv(body.y, ts);
                        y <= Math.floorDiv(body.y + body.height - 1, ts);
                        y++)
                    for (int x = Math.floorDiv(body.x, ts);
                            x <= Math.floorDiv(body.x + body.width - 1, ts);
                            x++)
                        if (!inside(x, y) || !walkableTerrain(world.terrain(x, y))) return false;
            spaced.grow(gap, gap);
            if (!new Rectangle(0, 0, width * ts, height * ts).contains(spaced)) return false;
            for (Entry e : nearby(spaced))
                if (e.body.width > 0 && spaced.intersects(e.spaced)) return false;
        } else if (!clearTile(col, row)) return false;
        var entry = new Entry(new GeneratedWorld.Placement(type, col, row), image, body, spaced);
        entries.add(entry);
        index(entry);
        reachable = null;
        return true;
    }

    /** Resolves the nearby result used by PlacementRules. */
    private List<Entry> nearby(Rectangle area) {
        var result = new ArrayList<Entry>();
        for (int y = Math.max(0, Math.floorDiv(area.y, ts));
                y <= Math.min(height - 1, Math.floorDiv(area.y + area.height - 1, ts));
                y++)
            for (int x = Math.max(0, Math.floorDiv(area.x, ts));
                    x <= Math.min(width - 1, Math.floorDiv(area.x + area.width - 1, ts));
                    x++) {
                var values = buckets.get(y * width + x);
                if (values != null) result.addAll(values);
            }
        return result;
    }

    /** Performs the index operation for PlacementRules. */
    private void index(Entry e) {
        int col = e.placement.col(), row = e.placement.row();
        anchors[col][row] = true;
        if (tree(e.placement.type())) treeInfluence = null;
        if (e.placement.type() == WorldObjectType.BUSH
                || e.placement.type() == WorldObjectType.FLOWER_BUSH)
            for (int y = Math.max(0, row - 2); y <= Math.min(height - 1, row + 2); y++)
                for (int x = Math.max(0, col - 2); x <= Math.min(width - 1, col + 2); x++)
                    bushShade[x][y] =
                            Math.max(
                                    bushShade[x][y],
                                    (float) Math.max(0, 1 - Math.hypot(x - col, y - row) / 2.5));
        Rectangle image = e.image, area = e.spaced;
        for (int y = image.y / ts; y <= (image.y + image.height - 1) / ts; y++)
            for (int x = image.x / ts; x <= (image.x + image.width - 1) / ts; x++)
                visual[x][y] = true;
        if (e.body.width > 0 && e.body.height > 0)
            for (int y = Math.max(0, area.y / ts);
                    y <= Math.min(height - 1, (area.y + area.height - 1) / ts);
                    y++)
                for (int x = Math.max(0, area.x / ts);
                        x <= Math.min(width - 1, (area.x + area.width - 1) / ts);
                        x++) buckets.computeIfAbsent(y * width + x, k -> new ArrayList<>()).add(e);
    }

    /** Clears the tile for PlacementRules. */
    boolean clearTile(int col, int row) {
        if (!inside(col, row)
                || !walkableTerrain(world.terrain(col, row))
                || world.heights() != null
                        && (world.generatorVersion < 10
                                ? !world.heights().flat(col, row, 1)
                                : !world.heights().playable(col, row))) return false;
        // Player currently uses a 32x32 collision body; sample its centre on the navigation grid.
        int side = Math.max(32, ts * 2 / 3), inset = (ts - side) / 2;
        Rectangle player = new Rectangle(col * ts + inset, row * ts + inset, side, side);
        for (Entry e : nearby(player))
            if (e.body.width > 0 && player.intersects(e.body)) return false;
        return true;
    }

    /** Evaluates the walkable terrain condition for PlacementRules. */
    static boolean walkableTerrain(Terrain t) {
        return t == Terrain.GRASS
                || t == Terrain.PATH
                || t == Terrain.COBBLE
                || t == Terrain.MOSS_PATH;
    }

    /** Evaluates the meadow fits condition for PlacementRules. */
    boolean meadowFits(int col, int row, boolean golden) {
        return meadowFits(col, row, golden, false);
    }

    /** Evaluates the meadow fits condition for PlacementRules. */
    boolean meadowFits(int col, int row, boolean golden, boolean starter) {
        if (!grass(col, row)
                || world.heights() != null && !world.heights().flat(col, row, 1)
                || col < 1
                || row < 2
                || col >= width - 1
                || row >= height - 1) return false;
        boolean understory = world.generatorVersion >= 7 && !golden;
        for (int y = row - 2; y <= row + 1; y++)
            for (int x = col - 1; x <= col + 1; x++)
                if (world.heights() != null
                                && (!world.heights().playable(x, y)
                                        || world.heights().at(x, y) != world.heights().at(col, row))
                        || (starter
                                ? world.coreReserved(x, y)
                                : golden ? world.reserved(x, y) : world.decorationReserved(x, y))
                        || !understory && visual[x][y]
                        || golden && !grass(x, y)) return false;
        // Leaves overhead are not ground obstacles. Keep tuft roots off trunks and prop anchors.
        if (understory && (anchors[col][row] || !clearTile(col, row))) return false;
        return golden || !world.regions().goldenMargin[col][row];
    }

    /** Resolves the near trees result used by PlacementRules. */
    double nearTrees(int col, int row) {
        if (treeInfluence == null) {
            treeInfluence = new float[width][height];
            for (Entry e : entries)
                if (tree(e.placement.type())) {
                    int c = e.placement.col(), r = e.placement.row();
                    for (int y = Math.max(0, r - 4); y <= Math.min(height - 1, r + 4); y++)
                        for (int x = Math.max(0, c - 4); x <= Math.min(width - 1, c + 4); x++)
                            treeInfluence[x][y] =
                                    Math.max(
                                            treeInfluence[x][y],
                                            (float) Math.max(0, 1 - Math.hypot(x - c, y - r) / 4));
                }
        }
        return treeInfluence[col][row];
    }

    /** Resolves the shade result used by PlacementRules. */
    double shade(int col, int row) {
        return Math.max(nearTrees(col, row), bushShade[col][row] * .65);
    }

    /** Reports whether this component has tree near spawn for PlacementRules. */
    boolean hasTreeNearSpawn() {
        for (Entry e : entries)
            if (tree(e.placement.type())
                    && Math.hypot(
                                    e.placement.col() - world.spawnCol,
                                    e.placement.row() - world.spawnRow)
                            <= 12) return true;
        return false;
    }

    /** Reports whether this component has rock near spawn for PlacementRules. */
    boolean hasRockNearSpawn() {
        for (Entry e : entries)
            if (rock(e.placement.type())
                    && Math.hypot(
                                    e.placement.col() - world.spawnCol,
                                    e.placement.row() - world.spawnRow)
                            <= 14) return true;
        return false;
    }

    /** Resolves the reachable result used by PlacementRules. */
    boolean[][] reachable() {
        if (reachable != null) return reachable;
        reachable = new boolean[width][height];
        var queue = new ArrayDeque<Integer>();
        queue.add(world.spawnRow * width + world.spawnCol);
        reachable[world.spawnCol][world.spawnRow] = true;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int p = queue.remove(), x = p % width, y = p / width;
            for (int i = 0; i < 4; i++) {
                int xx = x + dx[i], yy = y + dy[i];
                if (!inside(xx, yy)
                        || reachable[xx][yy]
                        || !clearTile(xx, yy)
                        || world.heights() != null
                                && world.heights().at(xx, yy) != world.heights().at(x, y)) continue;
                reachable[xx][yy] = true;
                queue.add(yy * width + xx);
            }
        }
        return reachable;
    }

    /** Performs the rebuild operation for PlacementRules. */
    private void rebuild() {
        buckets.clear();
        treeInfluence = null;
        for (float[] column : bushShade) Arrays.fill(column, 0);
        for (boolean[] column : visual) Arrays.fill(column, false);
        for (boolean[] column : anchors) Arrays.fill(column, false);
        for (Entry e : entries) index(e);
        reachable = null;
    }

    /** Performs the repair lowland access operation for PlacementRules. */
    private void repairLowlandAccess() {
        // Open a few scenery bottlenecks rather than leaving closed pockets between a grove and a
        // cliff.
        for (int attempt = 0; attempt < 16; attempt++) {
            var access = reachable();
            int available = 0, reached = 0;
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    if (world.lowland(x, y) && clearTile(x, y)) {
                        available++;
                        if (access[x][y]) reached++;
                    }
            boolean pondsAccessible = true;
            for (int id : world.heights().pondApproaches())
                if (!access[id % width][id / width]) pondsAccessible = false;
            if ((available == 0
                            || reached
                                    >= (int)
                                            Math.ceil(
                                                    available
                                                            * world.cliffConfig
                                                                    .minimumLowlandAccess()))
                    && pondsAccessible) return;
            Entry bridge = null;
            for (Entry e : entries) {
                int x = e.placement.col(), y = e.placement.row();
                if (e.body.width <= 0
                        || !world.lowland(x, y)
                        || world.starterProps().contains(e.placement)) continue;
                boolean nearMain = false, nearPocket = false;
                for (int r = Math.max(0, y - 2); r <= Math.min(height - 1, y + 2); r++)
                    for (int c = Math.max(0, x - 2); c <= Math.min(width - 1, x + 2); c++)
                        if (world.lowland(c, r) && clearTile(c, r)) {
                            if (access[c][r]) nearMain = true;
                            else nearPocket = true;
                        }
                if (nearMain && nearPocket) {
                    bridge = e;
                    break;
                }
            }
            if (bridge == null) return;
            entries.remove(bridge);
            rebuild();
        }
    }

    /** Performs the finish operation for PlacementRules. */
    void finish() {
        boolean[][] access = reachable();
        entries.removeIf(e -> {
            int x = e.placement.col(), y = e.placement.row();
            if (world.heights() != null && world.heights().at(x, y) > 0)
                return false; // Scenic, nonessential plateau flora.
            if (e.body.width <= 0 || e.body.height <= 0) return !access[x][y];
            for (int r = Math.max(0, y - 2); r <= Math.min(height - 1, y + 2); r++)
                for (int c = Math.max(0, x - 2); c <= Math.min(width - 1, x + 2); c++)
                    if (access[c][r]
                            && Math.hypot((c - x) * ts, (r - y) * ts - (ts / 2 - 4)) <= ts * 2.1)
                        return false;
            return true;
        });
        rebuild();
        if (world.generatorVersion >= 10) repairLowlandAccess();
        world.objects(entries.stream().map(Entry::placement).toList());
    }
}
