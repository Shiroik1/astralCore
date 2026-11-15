# Procedural grassland — Phase 1

The default launch builds a finite 100 × 100 temperate grassland with seed `123456789L`, using the existing 48-pixel game tile size. It uses the JPanel/Java2D renderer and threaded game loop. Generation runs at startup, before gameplay, and only repeats when explicitly requested through the development command.

## Configuration

In IntelliJ, open **Run → Edit Configurations → Modify options → Add VM options**, then enter, for example:

```text
-Dastral.world.seed=987654321 -Dastral.world.width=100 -Dastral.world.height=100 -Dastral.world.vegetation=0.65
```

For a packaged JAR, put these options before `-jar`. Seeds are signed decimal Java longs, without an `L` suffix in VM options. Defaults also live in `src/world/generation/WorldConfig.java`; programmatic configuration uses its record constructor.

| JVM property | Default | Meaning |
| --- | --- | --- |
| `astral.world.mode` | `procedural` | Use `authored` to load the existing town, NPCs, inn and well entrance from the original map files. |
| `astral.world.seed` | `123456789` | Signed 64-bit world seed. |
| `astral.world.width` | `100` | Width in tiles. |
| `astral.world.height` | `100` | Height in tiles. |
| `astral.world.vegetation` | `0.65` | 0–1 meadow coverage and bush placement. Higher values cover more ground. |
| `astral.world.trees` | `0.18` | 0–1 tree candidate probability within dense noise regions; spacing rules still apply. |
| `astral.world.flowers` | `0.65` | 0–1 flower coverage within small noise patches; flowering bushes also follow those patches. |
| `astral.world.rocks` | `0.008` | 0–1 rock candidate probability within rock noise regions. |
| `astral.world.vegetationScale` | `0.09` | Vegetation noise frequency. Lower values make larger clusters and clearings. |
| `astral.world.terrainScale` | `0.055` | Ground-tone noise frequency. Lower values make broader tone regions. |
| `astral.world.dirtThreshold` | `0.77` | 0–1 dirt noise cutoff. Lower values create more dirt; 1 disables dirt. |
| `astral.world.toneContrast` | `1.0` | Positive grass-tone contrast. Smaller values favor the middle tones. |
| `astral.dev` | `false` | Enables `/regenworld <seed>`. |

Dimensions must each be at least 16, with at most 1,000,000 tiles total. This is an allocation guard, not a performance promise: terrain images and meadow state are still allocated for the whole finite world. Start with 100 × 100. Width/height and other settings take effect on restart.

Enable `-Dastral.dev=true`, open the existing chat command input, and enter:

```text
/regenworld 42
```

This runs on the game thread, rebuilds terrain caches and scenery once, resets meadow animation, and moves players to the clear spawn. It is restricted to the offline procedural overworld. A short pause during rebuilding is expected. It does not change inventory, character stats, quest progress or the character save format. Use a development character: existing dropped items or effects keep their positions when the landscape changes.

## How generation works

`NoiseGenerator` hashes the full long seed, global tile/lattice coordinates and independent channels. Smooth quintic interpolation blends value noise, with three octaves for regional detail. There is no shared mutable random sequence. Terrain tones, dirt, vegetation, flower patches, rocks and coordinate selection use distinct channels.

`TerrainGenerator` assigns grass and existing dirt-path terrain, plus four existing ground tones. The tile renderer already blends adjacent tones and path borders. These tones are color variation, not elevation.

`ObjectGenerator` places tree candidates only in high-density vegetation, bushes in medium/high density, flowers in smaller correlated patches, and rocks in sparse rock regions. Coordinate hashes select candidates and variants within those fields. It then checks the actual sprite dimensions and collision footprints from the existing asset catalog. A conservative occupancy grid reserves half a tile around solid footprints to prevent overlap and leave walking gaps. Crowns may overlap other crowns for natural clusters; solid footprints cannot overlap. Meadow placement avoids occupied cells.

A gently wandering, five-tile-wide grassy corridor connects the left and right boundaries. Consecutive column bands overlap, including with unusual configured dimensions. A seven-by-seven clear spawn area joins that corridor at one quarter of the world width. Scenery image bounds and collision clearance cannot enter these reserved cells. Meadow has an extra one-tile buffer so its bend frames also leave the route clear. Sprite bounds stay within the world. All generated terrain is walkable; the existing tile collision checker still stops players at world boundaries.

Terrain is baked into the existing 16 × 16 rendering caches. Those caches are rendering batches, not world streaming. Existing `WorldObject` instances retain their collision, transparency, wind sway and foot-point depth sorting. `GrassLayer` retains its cached bend atlases, movement response and drawing around player feet. No asset decode or world generation is added to frame rendering.

## Existing assets used

| Content | Existing resource/catalog |
| --- | --- |
| Ground and tone transitions | `res/ground/grass.png`, `grass-edge-0.png` through `grass-edge-3.png` |
| Dirt patches | `res/ground/path-0.png` through `path-2.png` via `Terrain.PATH` |
| Oak/pine clusters | `res/scenery/foliage/oak_1.png`, `oak_2.png`, `pine_1.png`, `pine_2.png` |
| Bushes | `res/scenery/foliage/bush.png`, `flower_bush.png` |
| Rocks | `res/scenery/props/rock_small.png`, `rock_large.png`, `rock_small_moss.png`, `rock_large_moss.png` |
| Meadow/flowers | `res/scenery/meadow/short.png`, `tall.png`, `flowers.png` |

Resource paths stay in the existing loaders/catalogs. `WorldPalette` maps existing `WorldObjectType` entries and reads their loaded image/solid dimensions; generation never constructs filenames or placeholder graphics.

## Files and integration

| Files | Responsibility |
| --- | --- |
| `src/world/generation/WorldConfig.java` | Validated dimensions, biome, seed and density/noise settings; generator version. |
| `NoiseGenerator.java` | Seeded smooth value noise and coordinate hashes. |
| `GeneratedWorld.java` | Terrain, tone, meadow, reservation and scenery placement data. |
| `WorldPalette.java` | Existing asset footprint adapter. |
| `TerrainGenerator.java`, `ObjectGenerator.java`, `WorldGenerator.java` | Separate terrain/object generation and orchestration. |
| `src/main/Gamepanel.java` | Startup configuration/data and development regeneration; loaded-position safety. |
| `src/tile/TileManager.java` | Generated terrain installation and cache rebuild; dimension-safe cache keys. |
| `src/world/SceneryLoader.java`, `GrassLayer.java` | Consume generated data instead of authored files in procedural mode. |
| `src/world/InnInterior.java` | Keep the authored inn's reserved coordinates from intercepting larger procedural maps. |
| `src/entity/Player.java` | Generated spawn for creation, default placement and respawn. |
| `src/main/AssetSetter.java` | Omit the authored town NPCs in procedural mode. |
| `src/save/SaveStore.java` | Move a loaded outdoor character to spawn if its old position is now blocked/outside the map. |
| `src/main/CommandProcessor.java` | Development regeneration command and clear feedback for disabled authored-map export. |

The authored `maps/` files are retained and never overwritten by procedural startup or regeneration. `/saveobjects` and `/savegrass` refuse procedural exports. Manual placement/painting remains temporary in this mode.

## Boundaries and compatibility

World identity is seed **plus all generation settings, generator version and the existing asset catalog**. `WorldConfig.GENERATOR_VERSION` is currently 1; increment it when changing generation rules. Animated wind and other transient effects are not part of deterministic world layout. Authored ground/path micro-variants already use fixed coordinate hashes; generated terrain regions, tones, meadow variants and scenery follow the configurable seed.

This phase adds no buildings, NPCs, enemies, dungeons, quests or multiplayer generation. Use `-Dastral.world.mode=authored` for the existing town/quest content and existing multiplayer play. No network protocol or seed synchronization is added. Existing combat, inventory, saves and authored content remain available; procedural mode intentionally supplies only the landscape.

Seeds/settings and generated objects are not persisted in character saves yet. Relaunch with the same settings to reconstruct the same landscape. Prefer a separate development character; old saves retain their quest and dungeon state. An outdoor position blocked by a different seed is relocated to the generated spawn. Future world persistence should store configuration/version alongside characters/worlds before supporting durable edits.

Noise uses global coordinates in preparation for chunks, but placement resolves occupancy in a stable row-major finite-world pass. Future independent chunk generation will need deterministic neighboring candidate resolution/halos; it is not implemented here. Extra biomes, water, elevation, structures, streaming and persistence remain future work.

## Verification status

Implementation received read-only code and asset-reference review. No compilation, tests, gameplay runs, smoke checks, benchmarks or package verification were run, per the project owner's approval requirement. Determinism, appearance and movement still need approved runtime verification.
