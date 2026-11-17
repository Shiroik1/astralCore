package world.generation;

/** Planning happens once for the finite world, before any cached rendering chunks are baked. */
public final class WorldGenerator {
    /** Resolves the generate result used by WorldGenerator. */
    public GeneratedWorld generate(WorldConfig config, WorldPalette palette, int tileSize) {
        return generate(
                config,
                palette,
                tileSize,
                WorldConfig.GENERATOR_VERSION,
                GenerationConfig.developmentDefaults());
    }

    /** Resolves the generate result used by WorldGenerator. */
    public GeneratedWorld generate(
            WorldConfig config,
            WorldPalette palette,
            int tileSize,
            int version,
            GenerationConfig generation) {
        return generate(
                config,
                palette,
                tileSize,
                version,
                generation,
                version >= 8 ? NestConfig.developmentDefaults() : NestConfig.defaults());
    }

    /** Resolves the generate result used by WorldGenerator. */
    public GeneratedWorld generate(
            WorldConfig config,
            WorldPalette palette,
            int tileSize,
            int version,
            GenerationConfig generation,
            NestConfig nest) {
        return generate(
                config,
                palette,
                tileSize,
                version,
                generation,
                nest,
                version >= 9 ? ElevationConfig.developmentDefaults() : ElevationConfig.defaults());
    }

    /** Resolves the generate result used by WorldGenerator. */
    public GeneratedWorld generate(
            WorldConfig config,
            WorldPalette palette,
            int tileSize,
            int version,
            GenerationConfig generation,
            NestConfig nest,
            ElevationConfig elevation) {
        return generate(
                config,
                palette,
                tileSize,
                version,
                generation,
                nest,
                elevation,
                version >= 10
                        ? CliffFormationConfig.developmentDefaults()
                        : CliffFormationConfig.defaults());
    }

    /** Resolves the generate result used by WorldGenerator. */
    public GeneratedWorld generate(
            WorldConfig config,
            WorldPalette palette,
            int tileSize,
            int version,
            GenerationConfig generation,
            NestConfig nest,
            ElevationConfig elevation,
            CliffFormationConfig cliffs) {
        return generate(
                config,
                palette,
                tileSize,
                version,
                generation,
                nest,
                elevation,
                cliffs,
                CliffFormationConfig.MAX_FORMATIONS);
    }

    /** Resolves the generate result used by WorldGenerator. */
    private GeneratedWorld generate(
            WorldConfig config,
            WorldPalette palette,
            int tileSize,
            int version,
            GenerationConfig generation,
            NestConfig nest,
            ElevationConfig elevation,
            CliffFormationConfig cliffs,
            int formationLimit) {
        if (tileSize < 16
                || (long) Math.max(config.width(), config.height()) * tileSize > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Unsupported tile size or world pixel dimensions");
        if (version < 4 || version > WorldConfig.GENERATOR_VERSION)
            throw new IllegalArgumentException("Unsupported world generator version: " + version);
        NoiseGenerator noise = new NoiseGenerator(config.seed());
        GeneratedWorld world =
                new GeneratedWorld(config, noise, version, generation, nest, elevation, cliffs);
        if (version >= 6) world.regions(new RegionGenerator().generate(world, noise));
        if (version >= 6) new StarterResourcePlanner().reserve(world, palette, tileSize);
        new TerrainGenerator().generate(world, noise);
        if (version >= 9) new WorldBoundaryGenerator().generate(world, noise);
        new PondGenerator().generate(world, noise);
        new CobblePatchGenerator().generate(world, noise);
        if (version >= 8) new SlimeNestPlanner().generate(world, noise);
        else if (version >= 5) new LegacySlimeNestPlanner().generate(world, noise);
        if (version >= 10) new InlandCliffPlanner().generate(world, noise, formationLimit);
        else if (version >= 9) new ElevationGenerator().generate(world, noise);
        if (version < 6) {
            new ObjectGenerator().generate(world, noise, palette, tileSize);
            return world;
        }
        new PondVegetationGenerator().generate(world, noise);
        PlacementRules rules = new PlacementRules(world, palette, tileSize);
        world.regions().landReachable = rules.reachable();
        new GoldenMeadowGenerator().generate(world, noise);
        if (version == 6) new Version6VegetationGenerator().generate(world, noise, rules);
        else new VegetationGenerator().generate(world, noise, rules);
        new ResourceGenerator().generate(world, noise, rules);
        if (version >= 8) new NestPerimeterGenerator().generate(world, noise);
        // An invariant of the generated plan, not a rendering-time or gameplay-time test.
        GoldenMeadowGenerator.validate(world);
        new RegionGenerator().finishDensity(world);
        if (version >= 10) {
            InlandCliffPlanner.measure(world, rules);
            var stats = world.heights().cliffStatistics();
            boolean pondsAccessible = true;
            for (int id : world.heights().pondApproaches())
                if (!world.regions().accessible[id % config.width()][id / config.width()])
                    pondsAccessible = false;
            if (stats.floraAccess() < cliffs.minimumLowlandAccess() || !pondsAccessible) {
                if (stats.formations() > 0 && formationLimit > 0)
                    return generate(
                            config,
                            palette,
                            tileSize,
                            version,
                            generation,
                            nest,
                            elevation,
                            cliffs,
                            Math.min(formationLimit - 1, stats.formations() - 1));
                throw new IllegalStateException(
                        "Insufficient terrain/scenery access (ponds="
                                + pondsAccessible
                                + "): "
                                + stats);
            }
        }
        if (version >= 9) new TerrainAccessibilityValidator().finish(world);
        RegionDebug.log(world);
        return world;
    }
}
