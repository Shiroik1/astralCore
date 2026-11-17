package world.generation;

import java.util.*;

/** Explicitly invoked development check, not an automatically run or restored test suite. */
public final class RegionGenerationCheck {
    private static final int TILE_SIZE = 48;

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) {
        var palette = new WorldPalette(TILE_SIZE);
        var generator = new WorldGenerator();
        var regions = GenerationConfig.defaults();
        verifySoftRoutes(palette, regions);
        require(
                world.TerrainHeightResolver.cameraOrigin(0, 624, 4800, 1248) == 0,
                "Camera crossed the first tile");
        require(
                world.TerrainHeightResolver.cameraOrigin(4800, 624, 4800, 1248) == 3552,
                "Camera crossed the last tile");
        require(
                world.TerrainHeightResolver.cameraOrigin(0, 624, 768, 1248) == 0,
                "Small-map camera became negative");
        long[] seeds = {0, 1, -1, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE};
        int[][] sizes = {{16, 16}, {32, 48}, {100, 100}, {256, 192}};
        for (int version : new int[] {6, 7, 8, 9, 10, 11})
            for (int[] size : sizes)
                for (long seed : seeds) {
                    var base = WorldConfig.developmentDefaults();
                    var config =
                            new WorldConfig(
                                    true,
                                    size[0],
                                    size[1],
                                    seed,
                                    base.biome(),
                                    base.vegetationDensity(),
                                    base.treeDensity(),
                                    base.flowerDensity(),
                                    base.rockDensity(),
                                    base.vegetationScale(),
                                    base.terrainScale(),
                                    base.dirtThreshold(),
                                    base.toneContrast(),
                                    base.fieldDensity(),
                                    base.pondDensity(),
                                    base.detailDensity(),
                                    base.mossPathDensity(),
                                    base.slimeCount(),
                                    base.slimeRespawnSeconds(),
                                    base.cobblePatchDensity());
                    var a = generator.generate(config, palette, TILE_SIZE, version, regions);
                    var b = generator.generate(config, palette, TILE_SIZE, version, regions);
                    require(
                            Arrays.equals(tiles(a, false), tiles(b, false)),
                            "Different region/terrain plan for the same seed");
                    require(
                            Arrays.equals(tiles(a, false), tiles(a, true)),
                            "Chunk visitation order changed the world plan");
                    require(
                            a.objects().equals(b.objects()) && a.resources().equals(b.resources()),
                            "Unstable prop/resource coordinates");
                    var settings =
                            WorldSettings.decode(
                                    WorldSettings.of(config, version, regions).encode());
                    require(
                            settings.config().equals(config)
                                    && settings.generation().equals(regions)
                                    && settings.nest().equals(a.nestConfig)
                                    && settings.elevation().equals(a.elevationConfig)
                                    && settings.cliffs().equals(a.cliffConfig),
                            "World settings round trip changed configuration");
                    if (a.heights() != null) {
                        var h = a.heights();
                        for (int y = 0; y < size[1]; y++)
                            for (int x = 0; x < size[0]; x++) {
                                require(
                                        h.at(x, y) >= 0
                                                && h.at(x, y) <= a.elevationConfig.maxHeight(),
                                        "Height outside configured range");
                                if (x == 0 || y == 0 || x == size[0] - 1 || y == size[1] - 1)
                                    require(!h.playable(x, y), "Boundary touches storage edge");
                                if (a.terrain(x, y) == art.Terrain.WATER)
                                    require(
                                            h.at(x, y) == 0 && h.flat(x, y, 2),
                                            "Pond overlaps a cliff");
                            }
                        require(h.reachable(a.spawnCol, a.spawnRow), "Elevation blocks spawn");
                        var nest = a.slimeNest();
                        if (nest != null)
                            require(
                                    h.reachable(
                                            nest.col() + nest.size() / 2,
                                            nest.row() + nest.size() / 2),
                                    "Elevation blocks nest");
                    }
                    if (version >= 10) verifyCliffs(a);
                    if (version >= 11) {
                        var h = a.heights();
                        for (int y = 1; y < size[1] - 1; y++)
                            for (int x = 1; x < size[0] - 1; x++)
                                require(
                                        h.playable(x, y),
                                        "Square interior has an irregular cutout");
                        for (var prop : a.objects()) {
                            var fp = palette.footprint(prop.type());
                            var body = new java.awt.Rectangle(fp.solid());
                            if (body.width <= 0 || body.height <= 0) continue;
                            body.translate(
                                    prop.col() * TILE_SIZE + TILE_SIZE / 2,
                                    (prop.row() + 1) * TILE_SIZE - 4);
                            for (int y = Math.floorDiv(body.y, TILE_SIZE);
                                    y <= Math.floorDiv(body.y + body.height - 1, TILE_SIZE);
                                    y++)
                                for (int x = Math.floorDiv(body.x, TILE_SIZE);
                                        x <= Math.floorDiv(body.x + body.width - 1, TILE_SIZE);
                                        x++)
                                    require(
                                            !a.accessReserved(x, y),
                                            "A solid root blocks a reserved path");
                        }
                    }
                    int gold = GoldenMeadowGenerator.validate(a);
                    require(
                            java.util.Objects.equals(a.slimeNest(), b.slimeNest()),
                            "Nest location changed for the same seed");
                    if (version >= 8 && size[0] >= 100 && size[1] >= 100)
                        require(
                                a.slimeNest() != null,
                                "No nest in a valid test world: " + a.nestDiagnostic());
                    if (a.slimeNest() != null) {
                        var nest = a.slimeNest();
                        for (int row = nest.row(); row < nest.row() + nest.size(); row++)
                            for (int col = nest.col(); col < nest.col() + nest.size(); col++) {
                                require(a.reserved(col, row), "Nest reservation missing");
                                if (version >= 8)
                                    require(
                                            a.terrain(col, row) == art.Terrain.GRASS,
                                            "Nest ground not prepared");
                            }
                    }
                    var review = new PlacementRules(a, palette, TILE_SIZE);
                    for (var prop : a.objects())
                        require(
                                review.tryPlace(
                                        prop.type(),
                                        prop.col(),
                                        prop.row(),
                                        a.starterProps().contains(prop)),
                                "Invalid/overlapping prop " + prop);
                    review.finish();
                    require(
                            review.world.objects().equals(b.objects()),
                            "Inaccessible prop remained after validation");
                    var access = review.reachable();
                    int twigs = 0,
                            stones = 0,
                            fiber = 0,
                            trees = 0,
                            rocks = 0,
                            looseStones = 0,
                            flowers = 0,
                            mushrooms = 0,
                            litter = 0,
                            grass = 0;
                    for (var resource : a.resources()) {
                        require(
                                access[resource.col()][resource.row()],
                                "Unreachable loose resource");
                        if (Math.hypot(resource.col() - a.spawnCol, resource.row() - a.spawnRow)
                                <= 3) {
                            if (resource.kind().equals("twig")) twigs++;
                            if (resource.kind().equals("stone")) stones++;
                        }
                    }
                    for (var prop : a.objects()) {
                        if (PlacementRules.tree(prop.type())) trees++;
                        if (PlacementRules.rock(prop.type())) rocks++;
                        String type = prop.type().name();
                        if (type.startsWith("ROCK_SMALL")) looseStones++;
                        if (type.startsWith("FLOWERS_")) flowers++;
                        if (type.startsWith("MUSHROOM")) mushrooms++;
                        if (prop.type() == world.WorldObjectType.TWIG) litter++;
                    }
                    for (int y = 0; y < size[1]; y++)
                        for (int x = 0; x < size[0]; x++) if (a.meadow(x, y) > 12) grass++;
                    for (int id : a.starterFiber())
                        if (a.meadow(id % size[0], id / size[0]) > 12
                                && access[id % size[0]][id / size[0]]) fiber++;
                    require(
                            twigs >= 7 && stones >= 5 && fiber >= 1 && trees >= 1 && rocks >= 1,
                            "Starter progression guarantee failed");
                    System.out.println(
                            "v"
                                    + version
                                    + " "
                                    + size[0]
                                    + "x"
                                    + size[1]
                                    + " seed="
                                    + seed
                                    + " props="
                                    + a.objects().size()
                                    + " trees="
                                    + trees
                                    + " rocks="
                                    + rocks
                                    + " looseStones="
                                    + looseStones
                                    + " flowers="
                                    + flowers
                                    + " mushrooms="
                                    + mushrooms
                                    + " twigProps="
                                    + litter
                                    + " grassCells="
                                    + grass
                                    + " connectedGoldenMeadows="
                                    + gold);
                }
        // Zero inland budget must leave the independent, impassable boundary highlands intact.
        var base = WorldConfig.developmentDefaults();
        var zero = new CliffFormationConfig(0, 0, 24, .025, .95);
        var flat =
                generator.generate(
                        base,
                        palette,
                        TILE_SIZE,
                        10,
                        regions,
                        NestConfig.defaults(),
                        ElevationConfig.defaults(),
                        zero);
        verifyCliffs(flat);
        require(
                flat.heights().cliffStatistics().footprint() == 0,
                "Zero budget generated inland cliffs");
        boolean boundary = false;
        for (int y = 0; y < base.height(); y++)
            for (int x = 0; x < base.width(); x++)
                if (!flat.heights().playable(x, y) && flat.heights().at(x, y) > 0) boundary = true;
        require(boundary, "Inland limit removed boundary highlands");
        require(
                WorldSettings.decode(
                                WorldSettings.of(
                                                base,
                                                10,
                                                regions,
                                                NestConfig.defaults(),
                                                ElevationConfig.defaults(),
                                                zero)
                                        .encode())
                        .cliffs()
                        .equals(zero),
                "Cliff settings were not pinned");
        System.out.println("Region generation checks passed.");
    }

    /** Performs the verify soft routes operation for RegionGenerationCheck. */
    private static void verifySoftRoutes(WorldPalette palette, GenerationConfig regions) {
        var b = WorldConfig.developmentDefaults();
        var config =
                new WorldConfig(
                        true,
                        64,
                        64,
                        0,
                        b.biome(),
                        1,
                        b.treeDensity(),
                        b.flowerDensity(),
                        b.rockDensity(),
                        b.vegetationScale(),
                        b.terrainScale(),
                        b.dirtThreshold(),
                        b.toneContrast(),
                        b.fieldDensity(),
                        b.pondDensity(),
                        b.detailDensity(),
                        b.mossPathDensity(),
                        b.slimeCount(),
                        b.slimeRespawnSeconds(),
                        b.cobblePatchDensity());
        var w = routeFixture(config, regions);
        new TerrainGenerator().generate(w, new NoiseGenerator(0));
        boolean grassOnRoute = false;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                if (w.accessReserved(x, y) && !w.decorationReserved(x, y)) {
                    require(
                            w.groundDetail(x, y) > 0,
                            "Navigation reservation mowed a grass stripe");
                    grassOnRoute = true;
                }
                w.ground(x, y, art.Terrain.GRASS, 1);
            }
        require(grassOnRoute, "No soft crossing route");
        int col = 48, row = 16;
        var fp = palette.footprint(world.WorldObjectType.OAK_1);
        var image = new java.awt.Rectangle(fp.image());
        var body = new java.awt.Rectangle(fp.solid());
        image.translate(col * 48 + 24, (row + 1) * 48 - 4);
        body.translate(col * 48 + 24, (row + 1) * 48 - 4);
        int cx = -1, cy = -1;
        for (int y = image.y / 48; y <= (image.y + image.height - 1) / 48 && cx < 0; y++)
            for (int x = image.x / 48; x <= (image.x + image.width - 1) / 48; x++)
                if (!body.intersects(new java.awt.Rectangle(x * 48, y * 48, 48, 48))) {
                    cx = x;
                    cy = y;
                    break;
                }
        require(cx >= 0, "No canopy-only sample in existing oak asset");
        w.reserveAccess(cx, cy);
        var rules = new PlacementRules(w, palette, 48);
        require(
                rules.tryPlace(world.WorldObjectType.OAK_1, col, row),
                "Canopy cannot overlap a soft path");
        require(rules.meadowFits(cx, cy, false), "Soft route suppresses nonblocking grass");
        require(
                rules.tryPlace(world.WorldObjectType.TWIG, cx, cy),
                "Soft route suppresses forest litter");
        var blocked = routeFixture(config, regions);
        blocked.reserveAccess(
                Math.floorDiv((int) body.getCenterX(), 48),
                Math.floorDiv((int) body.getCenterY(), 48));
        require(
                !new PlacementRules(blocked, palette, 48)
                        .tryPlace(world.WorldObjectType.OAK_1, col, row),
                "Tree trunk blocks a reserved route");
    }

    /** Resolves the route fixture result used by RegionGenerationCheck. */
    private static GeneratedWorld routeFixture(WorldConfig config, GenerationConfig regions) {
        var w =
                new GeneratedWorld(
                        config,
                        new NoiseGenerator(config.seed()),
                        11,
                        regions,
                        NestConfig.defaults(),
                        ElevationConfig.defaults(),
                        CliffFormationConfig.defaults());
        w.regions(new RegionMap(64, 64));
        new WorldBoundaryGenerator().generate(w, new NoiseGenerator(config.seed()));
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) w.ground(x, y, art.Terrain.GRASS, 1);
        return w;
    }

    /**
     * Independent census/component traversal, not a check of the planner's reported numbers alone.
     */
    private static void verifyCliffs(GeneratedWorld w) {
        var h = w.heights();
        int land = 0, footprint = 0, components = 0;
        var labels = new HashSet<Integer>();
        boolean[][] seen = new boolean[h.width][h.height];
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        for (int y = 0; y < h.height; y++)
            for (int x = 0; x < h.width; x++) {
                if (h.playable(x, y) && PlacementRules.walkableTerrain(w.terrain(x, y))) {
                    land++;
                    if (h.at(x, y) > 0) footprint++;
                }
                if (!h.playable(x, y) || h.at(x, y) == 0 || seen[x][y]) continue;
                components++;
                int label = h.formation(x, y), size = 0;
                require(label > 0 && labels.add(label), "Disconnected or unlabeled formation");
                var queue = new ArrayDeque<Integer>();
                queue.add(y * h.width + x);
                seen[x][y] = true;
                while (!queue.isEmpty()) {
                    int id = queue.remove(), c = id % h.width, r = id / h.width;
                    size++;
                    require(h.formation(c, r) == label, "Touching formation labels");
                    require(!w.reserved(c, r), "Cliff intersects a protected reservation");
                    for (int i = 0; i < 4; i++) {
                        int xx = c + dx[i], yy = r + dy[i];
                        if (!h.playable(xx, yy) || seen[xx][yy] || h.at(xx, yy) == 0) continue;
                        seen[xx][yy] = true;
                        queue.add(yy * h.width + xx);
                    }
                }
                require(
                        size >= w.cliffConfig.minimumTiles(),
                        "Tiny elevated fragment survived cleanup");
            }
        require(
                footprint <= (int) Math.floor(land * w.cliffConfig.maximumCoverage()),
                "Inland coverage exceeded hard maximum");
        for (int id : h.pondApproaches())
            require(h.reachable(id % h.width, id / h.width), "Pond approach inaccessible");
        var stats = h.cliffStatistics();
        require(
                stats.interiorLand() == land
                        && stats.footprint() == footprint
                        && stats.formations() == components,
                "Incorrect cliff statistics");
        require(
                stats.disconnectedFragments() == 0
                        && stats.access() >= w.cliffConfig.minimumLowlandAccess()
                        && stats.floraAccess() >= w.cliffConfig.minimumLowlandAccess(),
                "Cliff/accessibility contract failed");
    }

    /** Resolves the tiles result used by RegionGenerationCheck. */
    private static long[] tiles(GeneratedWorld w, boolean reverse) {
        int width = w.config.width(),
                height = w.config.height(),
                nx = (width + 15) / 16,
                ny = (height + 15) / 16;
        long[] cells = new long[width * height];
        var chunks = new ArrayList<Integer>();
        for (int c = 0; c < nx * ny; c++) chunks.add(c);
        if (reverse) Collections.reverse(chunks);
        for (int chunk : chunks)
            for (int y = chunk / nx * 16; y < Math.min(height, (chunk / nx + 1) * 16); y++)
                for (int x = chunk % nx * 16; x < Math.min(width, (chunk % nx + 1) * 16); x++) {
                    long v = w.terrain(x, y).ordinal();
                    if (w.heights() != null) {
                        v = v * 31 + w.heights().at(x, y);
                        v = v * 31 + (w.heights().playable(x, y) ? 1 : 0);
                        v = v * 31 + (w.heights().reachable(x, y) ? 1 : 0);
                        v = v * 31 + w.heights().formation(x, y);
                    }
                    v = v * 31 + w.tone(x, y);
                    v = v * 31 + w.groundDetail(x, y);
                    v = v * 31 + w.meadow(x, y);
                    v = v * 31 + (w.reserved(x, y) ? 1 : 0);
                    var m = w.regions();
                    v = v * 31 + Float.floatToIntBits(m.forest(x, y));
                    v = v * 31 + Float.floatToIntBits(m.flowers(x, y));
                    v = v * 31 + Float.floatToIntBits(m.moisture(x, y));
                    v = v * 31 + Float.floatToIntBits(m.shore(x, y));
                    v = v * 31 + m.golden(x, y);
                    cells[y * width + x] = v;
                }
        return cells;
    }

    /** Performs the require operation for RegionGenerationCheck. */
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
