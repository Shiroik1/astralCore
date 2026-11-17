package world.generation;

import art.Terrain;

import world.WorldObjectType;

import java.util.List;

/**
 * Generation data only: no live entities, images, camera, or gameplay state. Arrays are [col][row].
 */
public final class GeneratedWorld {
    public record Placement(WorldObjectType type, int col, int row) {}

    public final WorldConfig config;
    public final int generatorVersion;
    public final GenerationConfig generation;
    public final NestConfig nestConfig;
    public final ElevationConfig elevationConfig;
    public final CliffFormationConfig cliffConfig;
    private final HeightMap heights;

    /** Returns the current heights value. */
    public HeightMap heights() {
        return heights;
    }

    /** Evaluates the lowland condition for GeneratedWorld. */
    public boolean lowland(int x, int y) {
        return heights == null || heights.playable(x, y) && heights.at(x, y) == 0;
    }

    private String nestDiagnostic = "Not planned";

    /** Returns the current nest diagnostic value. */
    public String nestDiagnostic() {
        return nestDiagnostic;
    }

    /** Performs the nest diagnostic operation for GeneratedWorld. */
    void nestDiagnostic(String text) {
        nestDiagnostic = text;
    }

    private RegionMap regions;
    private List<Resource> resources = List.of();

    public record Resource(String kind, int col, int row) {}

    /** Returns the current regions value. */
    public RegionMap regions() {
        return regions;
    }

    /** Performs the regions operation for GeneratedWorld. */
    void regions(RegionMap map) {
        regions = map;
    }

    /** Returns the current resources value. */
    public List<Resource> resources() {
        return resources;
    }

    /** Performs the resources operation for GeneratedWorld. */
    void resources(List<Resource> values) {
        resources = List.copyOf(values);
    }

    private final Terrain[][] terrain;
    private final int[][] tones;
    private final byte[][] groundDetails;
    private final byte[][] meadow;
    private final boolean[][] reserved;
    private final boolean[][] accessGround;
    private final boolean[][] starterGround;
    private List<Placement> starterProps = List.of();
    private List<Integer> starterFiber = List.of();

    /** Returns the current starter props value. */
    List<Placement> starterProps() {
        return starterProps;
    }

    /** Returns the current starter fiber value. */
    List<Integer> starterFiber() {
        return starterFiber;
    }

    /** Performs the starter plan operation for GeneratedWorld. */
    void starterPlan(List<Placement> props, List<Integer> fiber) {
        starterProps = List.copyOf(props);
        starterFiber = List.copyOf(fiber);
    }

    /** Evaluates the core reserved condition for GeneratedWorld. */
    boolean coreReserved(int col, int row) {
        return reserved[col][row] || accessGround[col][row];
    }

    /** Evaluates the access reserved condition for GeneratedWorld. */
    public boolean accessReserved(int col, int row) {
        return col >= 0
                && row >= 0
                && col < config.width()
                && row < config.height()
                && accessGround[col][row];
    }

    /**
     * Solid-free routes may have canopy and nonblocking vegetation; arenas/spawn still need full
     * clearings.
     */
    public boolean decorationReserved(int col, int row) {
        return generatorVersion >= 11
                ? reserved[col][row] || starterGround[col][row]
                : reserved(col, row);
    }

    /** Evaluates the starter reserved condition for GeneratedWorld. */
    boolean starterReserved(int col, int row) {
        return starterGround[col][row];
    }

    /** Performs the reserve starter operation for GeneratedWorld. */
    void reserveStarter(int col, int row) {
        starterGround[col][row] = true;
    }

    private List<Placement> objects = List.of();
    private SlimeNestPlan slimeNest;

    /** Returns the current slime nest value. */
    public SlimeNestPlan slimeNest() {
        return slimeNest;
    }

    /** Performs the reserve access operation for GeneratedWorld. */
    void reserveAccess(int col, int row) {
        if (generatorVersion >= 11) accessGround[col][row] = true;
        else reserved[col][row] = true;
    }

    /** Performs the reserve nest operation for GeneratedWorld. */
    void reserveNest(SlimeNestPlan plan) {
        slimeNest = plan;
        for (int r = plan.row(); r < plan.row() + plan.size(); r++)
            for (int c = plan.col(); c < plan.col() + plan.size(); c++) reserved[c][r] = true;
    }

    public final int spawnCol, spawnRow;

    /**
     * Creates a GeneratedWorld instance and initializes the state required by its collaborators.
     */
    GeneratedWorld(
            WorldConfig config,
            NoiseGenerator noise,
            int version,
            GenerationConfig generation,
            NestConfig nestConfig,
            ElevationConfig elevationConfig,
            CliffFormationConfig cliffConfig) {
        this.config = config;
        this.generatorVersion = version;
        this.generation = generation;
        this.nestConfig = nestConfig;
        this.elevationConfig = elevationConfig;
        this.cliffConfig = cliffConfig;
        heights = version >= 9 ? new HeightMap(config.width(), config.height()) : null;
        terrain = new Terrain[config.width()][config.height()];
        tones = new int[config.width()][config.height()];
        groundDetails = new byte[config.width()][config.height()];
        meadow = new byte[config.width()][config.height()];
        reserved = new boolean[config.width()][config.height()];
        accessGround = new boolean[config.width()][config.height()];
        starterGround = new boolean[config.width()][config.height()];
        spawnCol = config.width() / 4;
        spawnRow = routeRow(config, noise, spawnCol);
        // Legacy worlds retain their five-tile clearing. New routes reserve collision space only;
        // overlapping canopies and nonblocking undergrowth keep the forest visually continuous.
        int previous = routeRow(config, noise, 0);
        for (int c = 0; c < config.width(); c++) {
            int row = routeRow(config, noise, c);
            int margin = version >= 11 ? 0 : 2;
            for (int r = Math.max(0, Math.min(previous, row) - margin);
                    r <= Math.min(config.height() - 1, Math.max(previous, row) + margin);
                    r++) reserveAccess(c, r);
            previous = row;
        }
        for (int c = Math.max(0, spawnCol - 3);
                c <= Math.min(config.width() - 1, spawnCol + 3);
                c++)
            for (int r = Math.max(0, spawnRow - 3);
                    r <= Math.min(config.height() - 1, spawnRow + 3);
                    r++) reserved[c][r] = true;
    }

    /** Resolves the route row result used by GeneratedWorld. */
    private static int routeRow(WorldConfig config, NoiseGenerator noise, int col) {
        return config.height() / 2
                + (int)
                        Math.round(
                                (noise.value(col * .035, 0, 100) - .5)
                                        * Math.min(12, config.height() / 4));
    }

    /** Resolves the terrain result used by GeneratedWorld. */
    public Terrain terrain(int col, int row) {
        return terrain[col][row];
    }

    /** Resolves the tone result used by GeneratedWorld. */
    public int tone(int col, int row) {
        return tones[col][row];
    }

    /** Resolves the ground detail result used by GeneratedWorld. */
    public int groundDetail(int col, int row) {
        return groundDetails[col][row];
    }

    /** Performs the ground detail operation for GeneratedWorld. */
    void groundDetail(int col, int row, int variant) {
        groundDetails[col][row] = (byte) variant;
    }

    /** Resolves the meadow result used by GeneratedWorld. */
    public byte meadow(int col, int row) {
        return meadow[col][row];
    }

    /**
     * Encounter protection excludes generation-only access corridors. Terrain generation is
     * unchanged.
     */
    public boolean encounterProtected(int col, int row) {
        return col < 0
                || row < 0
                || col >= config.width()
                || row >= config.height()
                || starterGround[col][row]
                || Math.abs(col - spawnCol) <= 3 && Math.abs(row - spawnRow) <= 3;
    }

    /** Evaluates the reserved condition for GeneratedWorld. */
    public boolean reserved(int col, int row) {
        return reserved[col][row] || starterGround[col][row] || accessGround[col][row];
    }

    /** Returns the current objects value. */
    public List<Placement> objects() {
        return objects;
    }

    /** Performs the ground operation for GeneratedWorld. */
    void ground(int col, int row, Terrain type, int tone) {
        terrain[col][row] = type;
        tones[col][row] = tone;
    }

    /** Performs the meadow operation for GeneratedWorld. */
    void meadow(int col, int row, int type, int density) {
        meadow[col][row] = (byte) ((type - 1) * 4 + density);
    }

    /** Performs the objects operation for GeneratedWorld. */
    void objects(List<Placement> values) {
        objects = List.copyOf(values);
    }
}
