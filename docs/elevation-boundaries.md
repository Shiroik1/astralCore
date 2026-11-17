# Phase 4 — elevation and natural boundaries (generator 9)

**Current generator 10:** [modular cliff redesign](cliff-redesign.md) supersedes the inland noise planner, cliff material/cap composition and coverage/access thresholds below. This guide retains the original foundation and version-nine behavior for older saves.

Implementation is integrated into the existing Java2D/JPanel generator and renderer. **Source and asset review only: compilation, staged checks, gameplay, save/load, screenshots and performance checks have not run.** The project requires explicit owner approval before verification. The stages below are implemented in source and await their corresponding checks; they should not be treated as verified milestones.

## A. Logical elevation foundation

Generator **9** adds `GeneratedWorld.heights()`, a `HeightMap` indexed **[column][row]**, matching this engine. Physical height is separate from `tone()` / the authored `levels.txt` palette grid. Each cell stores a height, playable-boundary membership and cached starting-component access. Heights default to 0–3; the configured maximum can be 1–8.

`ElevationGenerator` samples globally addressed, low-frequency warped value noise. Forest influence modestly favors hills; Flower Plains suppress them. Thresholds create broad plateaus, followed by two median-filter passes to remove fine noise. Higher thresholds are spaced 0.13 apart. No per-cell independent random heights or per-chunk random streams are used.

Before vegetation, spawn/crossing, starter artwork/approaches, the planned nest and its access corridor are flattened with a margin. Existing ponds get level-zero basins and at least three tiles of shore clearance. A pre-decoration dry-ground approach is lowered/reserved to each pond without converting water. The initial lowland flood fill can relax raised terrain if the starting component contains less than 45% of the playable mask. Inaccessible lowland pockets become scenic raised terrain; the enclosing nonplayable highlands remain impassable. Coverage is a tuning outcome rather than a quota, especially on small maps or difficult seeds.

`ElevationCollisionHandler` rejects nonplayable/out-of-range rectangles, bodies straddling different heights, and the exposed edge bands alongside different-height neighbors. This handles corners and diagonal approaches. `CollisionChecker` also checks the full prospective body. Normal movement inside one plateau height is valid, but no walking connection crosses a cliff. The existing pathfinder requires adjacent route nodes to have equal height; slimes, projectiles and skill motion retain their existing collision calls. No ramps, climbing or jumping were added.

`/elevation` toggles the development overlay with `-Dastral.dev=true`. Green/yellow/orange/purple show increasing height, dark cells mark the nonplayable rim, orange outlines mark edge cells, crossed cells show generated inaccessibility, cyan shows the nest and white marks spawn. It reads cached arrays after normal world drawing; it never flood-fills during a frame. `-Dastral.elevation.debug=true` enables it initially. Logs report seed, maximum, plateau components, cliff cells, height coverage, accessible size, border safety and nest/spawn access.

## B. Existing cliff artwork and depth

Inspected runtime art: `res/ground/ledge.png`, `steps.png` and the `grass-edge-*.png` connector family, plus their source in `GenerateGroundTextures` / `GroundTiles`. The ledge sheet has four tone rows and four left/right endpoint combinations. Its upper half is grass, the first face pixels contain hanging grass, and its lower wall body is the existing timber/brown material. The stairs are an authored, wooden, one-step strip rather than a general procedural multi-level traversal system; they are retained for existing maps and are not introduced into procedural plateaus.

`CliffTileResolver` computes an **eight-neighbor mask for each height threshold**. It uses the existing `GroundTiles.blobShape` connection geometry for straight runs, outer and inner corners, diagonal joins and ends. Raised surfaces use current ground textures clipped to that connected shape. Each height step lifts rendering by half a tile (24 pixels at the current 48-pixel tile size). The wall is an extrusion of the connected silhouette, with narrow exposed side/back bands, filled with repeated **unscaled wall-body crops** from the ledge sheet. The grassy lip is omitted from repeated strips so it does not create horizontal grass seams in a tall face. No runtime texture exports, recoloring, stretched cliff faces or placeholder art were introduced.

Interior surfaces remain in the existing 16×16 ground cache. Edge tiles are cached transparent `CliffLayer` sprites and join the existing entity depth sort at their logical ground-base row. An actor in front draws after that wall; objects behind can be occluded. Masks sample the full world, including across cache boundaries. A vertical sampling halo covers surfaces projected up from neighboring chunks. Rendering caches are rebuilt only when the world changes.

`TerrainHeightResolver` handles visual lift separately from collision coordinates. The main renderer translates each entity draw, including its shadow; scenery uses its explicit ground anchor. Tall-grass draw entities now carry their real cell coordinates, and transient particles can retain their source ground elevation. Logical entity coordinates and normal sprite sizes remain unchanged. Scenic plateau trees/rocks require their full sprite footprint to fit one height, avoiding crowns or roots straddling cliff faces. Harvestable tall fields and essential loose resources remain in accessible lowlands; elevated surfaces still use baked grass details.

**Art limits:** the source set does not contain separately shaded east/west cliff-face artwork, north-facing back-wall artwork, or artist-authored multi-step concave return caps. Current silhouettes connect those directions using the existing wall material and rounded connector geometry. Dedicated side/back faces and multi-step corner/end caps would add directional detail later. No missing filename is assumed or silently substituted with newly generated art. Seam quality and depth ordering still require visual verification.

## C. Irregular boundary and camera

`WorldBoundaryGenerator` creates a noisy radial/elliptical contour using seeded three- and five-lobe harmonics plus low-frequency displacement. This changes the entire enclosing silhouette, rather than perturbing four straight sides. Protected starter artwork and approaches are retained inside it. A flood fill discards mask components detached from spawn. The literal rectangular storage edge is always nonplayable.

Outside the mask, terrain becomes highland grass and elevated cliff terrain. The cells immediately outside a playable border use the maximum height; adjacent interior heights are kept lower so the physical barrier also has a visible height transition. Distant highland heights vary. World data remains rectangular for storage and cached rendering; boundary collision is authoritative.

Camera coordinates use the engine's existing `world - camera.world + camera.screen` convention. For version-nine overworlds, screen anchors clamp the camera continuously to the rectangular map bounds. At small-world sizes, the map is centered and surrounding space is tiled with a cached existing highland ground texture. Thus the irregular edge is backed by initialized scenery, rather than black or empty space. Viewport/zoom behavior and existing mouse-coordinate conversion are retained. Entity culling now uses the actual viewport rectangle, which is necessary when the camera is no longer centered on the player near a border.

## D. Regions, essential locations, construction and saves

The integrated order is: existing regions → starter reservations → existing base ground → boundary mask → existing ponds/cobble → nest and corridor reservation → height planning/repair → pond influence → golden footprints → vegetation/resources → final accessibility diagnostics. Reserving the encounter before final elevation/vegetation prevents it from depending on a naturally empty post-decoration square. Its footprint must be inside a broad boundary-safe area; elevation is flattened around the reservation. The existing five summons, eight radial projectiles, encounter ID and cleared-state persistence remain intact.

Golden footprints grow only after elevation and only on reachable flat ground; cliffs never cut an already-planned golden field. Existing pond generation is preserved, with additional boundary clearance. Flora uses height-aware placement and full sprite bounds; essential pickups, starter trees/rock and Fiber retain their existing reservations/access checks. Scenic upper-level flora may be unreachable; progression resources and the boss are kept below. No enemy-director settings or interaction distances changed. Interaction selection additionally rejects targets across a height difference.

Building floors/walls/doors still use the same connections, collision and item transactions. Existing placement rules now read physical height and reject nonplayable/cliff neighbors. Since there is no traversal to upper plateaus, new construction/stations are restricted to the connected lowland terrain plan, with consistent height over their footprint. This also keeps the existing building ground-surface renderer on supported ground. No roofs or multi-story construction were added. Saved construction is restored through the existing system, not deleted to make an arena or cliff.

`WorldSettings` pins all elevation/boundary settings alongside seed, dimensions, regions, nest layout and generator version. Reconstructing the same version-nine descriptor reconstructs the heightmap and boundary before buildings, depleted resources and encounter state are restored. Version-nine building sidecar identity includes elevation settings. Versions **4–8 retain their original generation algorithms, terrain identity and building-sidecar keys**, with no automatic elevation conversion. Older saves therefore remain flat/legacy until a new character/world is deliberately created. Older executables cannot understand generator nine and should not be used to resave those worlds. No save files were opened, rewritten or migrated during this implementation.

Use a **new development character** to see the new landscape. `/regenworld <seed>` deliberately retains the loaded generator version and settings, so it does not convert an older built world into cliffs. `/nest` still prints the planned encounter coordinates.

## Tuning

Add JVM options in the game's existing IntelliJ run configuration. These layout settings affect **new worlds**, and then are saved with that world:

| Property | Default | Effect |
| --- | --- | --- |
| `astral.elevation.maxHeight` | `3` | Maximum logical height; allowed 1–8 |
| `astral.elevation.frequency` | `0.028` | Lower values produce larger, broader plateaus; allowed 0.005–0.15 |
| `astral.elevation.raisedThreshold` | `0.53` | Higher values leave more land at zero; allowed 0.35–0.85 |
| `astral.elevation.boundaryIrregularity` | `0.14` | Strength of broad boundary indentations/lobes; allowed 0.05–0.23 |
| `astral.elevation.boundaryRadius` | `0.86` | Normalized playable extent before protection/repair; allowed 0.72–0.94 |
| `astral.world.seed` | `123456789` | Existing signed long seed |
| `astral.world.width` / `height` | `100` / `100` | Existing dimensions |

Region-specific elevation biases and the 0.13 height-threshold spacing live in `ElevationGenerator`. Boundary harmonics live in `WorldBoundaryGenerator`. Height is independent of terrain tone; changing grass color thresholds is not an elevation control.

## Files created or modified

Created:

- `world/generation/ElevationConfig`, `HeightMap`, `ElevationGenerator`: settings and physical height data.
- `WorldBoundaryGenerator`, `TerrainAccessibilityValidator`: boundary and cached connectivity planning.
- `CliffTileResolver`, `ElevationDebugOverlay`: global connection masks and diagnostics.
- `world/ElevationCollisionHandler`, `TerrainHeightResolver`, `CliffLayer`: collision, projection/camera and cached depth layers.
- This guide.

Modified:

- `WorldGenerator`, `GeneratedWorld`, `WorldConfig`, `WorldSettings`: generator-nine pipeline and persisted descriptor.
- `PondGenerator`, `SlimeNestPlanner`, `StarterResourcePlanner`, `PlacementRules`, `GoldenMeadowGenerator`: boundary/height-aware existing placement and reservations.
- `TileManager`, `GroundTiles`, `Gamepanel`, `Entity`, `Player`, `Particle`, `GrassLayer`, `WorldObject`: cached rendering, depth, camera, sprite anchors and collision data.
- `CollisionChecker`, `Pathfinder`: full-body and equal-height movement checks.
- `BuildSystem`, `CraftingManager`, `InteractionTargetResolver`: valid lowland footprints and height-gated targets, retaining existing reach and costs.
- `CommandProcessor`: development toggle.
- `tools/RegionGenerationCheck.java`: additional version-nine data invariants, not executed.
- README and existing world/region guides: links/current generator version.

## Pending staged verification

No compilation, tests, smoke checks, gameplay or benchmarks were run. No removed suite was restored. The existing optional development checker now includes version nine, repeated height/boundary/access signatures, saved elevation-settings round trips, boundary-edge rejection and level-zero flat pond basins. These additions are prepared but unverified.

After owner approval, Stage A needs compilation, deterministic height/range/access checks and collision corner checks; Stage B needs visual cliff/corner/depth and cross-chunk checks; Stage C needs several boundary seeds, diagonal barrier and small-world/viewport/zoom checks; Stage D needs starter gathering, nest combat, construction and old/new save/load checks. Performance remains unmeasured. This finite renderer still caches the full world, so the existing one-million-tile allocation guard is not a performance guarantee or a streaming implementation.
