# AstralCore performance and chunk streaming report

Date: 17 November 2026

## Result and measurement limits

The verified Phase 3 targeting and collision bottlenecks were removed before introducing streaming. AstralCore now uses configurable 32×32 finite-world chunks, player/camera-centered residency, bounded terrain imagery, local resource/scenery queries, and nearby enemy simulation. The JPanel/Java2D engine, fixed 60 Hz simulation, terrain generator and art assets remain the existing systems.

The reported live 6 FPS could not be reproduced in the isolated headless fixture. The measurements below are **CPU software work rates, not measured monitor FPS**. Actual window/GPU presentation still requires a live display run; no stable 60 FPS claim is made. The in-game overlay measures actual painted-frame FPS and separates rendering, presentation and window painting.

## Investigation before chunks

A preserved compiled baseline was measured with Java Flight Recorder (10 ms execution samples, allocation samples and GC events), using the real `Gamepanel.update()` and Java2D scene/UI rendering. Probe saves use temporary directories. Tests use the existing finite world, seed 123456789 and generator version 11.

Verified findings:

1. `CraftingManager.collectTargets()` visited every resource on every resolution. `canHarvest()` then performed `ArrayList.contains(source)` over all scenery **before** spatial rejection. That nested lookup made interaction work grow roughly quadratically with world population. The 256×256 baseline had 23,056 resource nodes and 14,512 scenery objects. JFR's largest sampled stack was `ArrayList.indexOfRange → CraftingManager.canHarvest` (206 samples).
2. Interaction ray checks, slime movement and encounter reachability repeatedly scanned the entire scenery list. JFR recorded 73 samples at `Rectangle2D.intersects → WorldObject.blocksArea`; a forced spawn check took 141.757 ms in the first baseline and 127.958 ms in the later control.
3. Render collection added globally generated loose-resource actors to the depth sort, despite drawing only the camera area afterward. Scenery update/collection and grass-energy decay also visited global populations.
4. The old terrain cache eagerly baked every 16×16 ground chunk and all cliff actors. At 256×256, ground pixels alone represent approximately 576 MiB with 32-bit image storage. Grass also eagerly created depth actors throughout the world.
5. Empty building-ground queries allocated cells repeatedly; resource footprint rectangles were recreated during queries. Allocation sampling identified these paths. The existing silhouette-outline cache already reused images and was preserved.
6. Building edits constructed a complete `BuildingRenderer`, including global silhouette unions and shadow images. This was replaced with local visual snapshots rather than changing building geometry or artwork.

The first spatial-index fixes were measured **before the chunk classes were added**:

| 256×256 idle scenario | Original baseline | Spatial fixes, before streaming | Final isolated streaming run |
|---|---:|---:|---:|
| Update mean | 20.520 ms | 0.284 ms | 0.264 ms |
| Isolated targeting mean | 9.274 ms | 0.024 ms | 0.027 ms |
| Render including UI mean | 11.560 ms | 8.383 ms | 9.324 ms |
| Resident resource-node instances at fixture start | 23,056 | 23,056 | 8,182 |

The original 100×100 fixture measured 1.051 ms update and 8.617 ms render means. This shows why the population-sensitive regression could be much worse in a larger/denser world; it does not establish the sole cause of the user's exact live-display 6 FPS result.

## Isolated before/after comparison

The original compiled baseline was run again immediately before the current build, with identical 256×256 seed/configuration, Java 26.0.2, `-Xmx2048m`, 12 warmup frames and 60 measured frames per primary scenario. Concurrent stress/probe runs were excluded because contention inflated their wall-clock results. JIT, GC, asynchronous preparation and shared-host load still introduce variation.

| Scenario | Update mean: before → after | Scene/UI render mean: before → after | Combined work rate: before → after |
|---|---:|---:|---:|
| Idle | 21.205 → 0.264 ms | 12.369 → 9.324 ms | 29.8 → 104.3 work frames/s |
| Walking with axe | 14.064 → 0.167 ms | 8.585 → 7.833 ms | 44.2 → 125.0 work frames/s |
| Inventory open | 0.345 → 0.098 ms | 10.867 → 9.186 ms | 89.2 → 107.7 work frames/s |

Idle update p95 fell from 22.846 to 0.380 ms; idle update maximum fell from 23.870 to 0.440 ms. Isolated targeting mean fell from 9.840 to 0.027 ms. The final forced spawn command took 10.372 ms; the instrumented encounter check itself took 9.421 ms. The command includes additional debug/residency work, so it is not identical to the director-only scope.

End-of-run used heap was 1,208 MiB in the control and 679 MiB in the final run, with 13 versus 0 observed GC events. These are allocation-sensitive heap samples, **not retained-heap or leak measurements**. The dedicated retention test is described below.

Final timing scopes expose update, world/grass/building work, NPC/enemy/projectile work, enemy AI, collision queries, interaction resolution, director checks, terrain collection/draw, entity rendering, UI, particles, lighting, terrain-cache preparation, streaming integration, saving, buffer presentation and window painting. Scopes overlap: AI is part of entity work; UI is part of presentation in the live pipeline. They should not be summed indiscriminately. A zero scope may indicate no calls (for example, no autosave during a short probe), rather than zero-cost I/O. Mean/peak values are per invocation since startup or `/performance reset`; spawn-check means cover scheduled checks rather than every tick. `CHUNK` includes worker preparation duration and `STREAMING` measures game-thread integration, including required cold misses. The initial baseline had aggregate update/render/interaction timings and sampled CPU/allocation evidence; all detailed scope counters were added during implementation.

## Chunk ownership, loading and rendering

`WorldChunks` partitions the original global coordinates with `floorDiv` and `floorMod` conventions. Chunk records contain coordinates, lifecycle state, descriptor references and resident scenery references. They do not copy authoritative terrain arrays, inventories, enemy arrays or building records.

The existing deterministic `GeneratedWorld` remains the shared world plan: environmental region masks, connected golden meadows, ponds, heights, boundaries and nest reservations are planned once. Chunk order never advances a separate terrain random generator. A chunk materializes its original placement/resource descriptors from this plan; terrain and cliff renderers read neighboring **global** cells, including unloaded neighbors. The finite world dimensions, seed and pinned generator version are preserved.

Logical data passes through `UNLOADED → REQUESTED → LOADING → READY`, then becomes `ACTIVE` or remains ready for preloading. Chunks outside the required area become `DORMANT` before expiry or cache-pressure eviction. Request/loading transitions for live scenery integration occur synchronously on the game thread; image preparation has a separate worker pipeline. No incomplete image is published to the renderer.

The active set combines the player's minimum 3×3 neighborhood with the actual logical camera rectangle expanded by 12 tiles. The wanted set combines a default 5×5 neighborhood, the active set and a 16-tile camera preload margin. All connected overworld player positions contribute to metadata coverage. Physical window resolutions scale the existing fixed logical camera rather than changing its field of view. Camera/world boundaries are clamped using existing engine rules.

Recently needed chunks retain their resident objects for 10 seconds by default, subject to a 49-chunk cache target. The required camera/active set takes precedence over that target, preventing missing terrain if a larger viewport or activation setting requires more chunks. Current visible chunks are always pinned. Metadata and image residency are separate.

Ground/cliff visual caches default to 12 chunks. One worker with a bounded two-entry queue prepares immutable images against captured world-plan/grid references. Completion is integrated on the game thread. Obsolete requests are cancelled/discarded on movement or generation changes. Required visible misses are prepared/waited for before drawing; normal preloads run ahead of movement. This correctness fallback can stall on cold teleports and is reported below.

The image preload selection moves continuously with the camera and an eight-tile facing-direction lookahead, even when the cache is full. A retention bias reduces churn when two candidates have similar priority. Only the useful visible/preload set is retained. Ground images are drawn only where they intersect the camera; individual cliff images and scenery/resource actors are culled before sorting/drawing.

Grass keeps compact deterministic placement/energy arrays, constructs depth actors only around the camera during updates, releases their references when chunks unload, and decays only cells that were stirred. Sprite art and the existing silhouette-outline images are shared caches.

## Buildings, interactions and enemies

The adjacent interaction tile still comes from the player's collision/feet position and facing direction. Resource nodes and stations are queried through local buckets; scenery queries include actual collision and interaction footprints. Large resources remain targetable through their logical footprint. The existing collision/elevation ray validation runs when resolving an actionable target. Rendering consumes the cached target with position/direction/tool/depletion guards; it does not repeat the ray for each outlined sprite. Input actions resolve through the same resolver. Existing tool requirements, yields, recipes and combat reach remain unchanged.

Buildings retain their global authoritative floor/structure/decoration records. Their rendering uses local eight-tile snapshots with a three-tile halo for joins, wall overhangs, clipped foundations, shadows and the whole-building silhouette. Changed cells invalidate only affected snapshots and neighboring halos. Floor-material changes beneath a wall also invalidate the cache. Furnishing visual/collision queries are spatially indexed and unchanged furnishing actors are reused. Building components crossing chunk borders are never instantiated twice or charged again by a load callback. Off-screen blueprint validation explicitly requests collision metadata for its footprint and halo, so an unloaded rock cannot disappear from placement checks.

The existing monster arrays remain the canonical owners. A positional identity index follows enemy movement across chunk boundaries; chunk loading does not spawn replacement enemies. Ordinary slimes run their existing AI near the player. Distant AI is dormant, with existing damage/status/death work retained where needed. An active Crowned Slime gets its normal disengagement/reset update before becoming dormant; this avoids leaving the encounter director blocked by a distant active boss.

The encounter director continues scheduled checks, existing probability/region weights, safe-area protections, population limits and captured investigation destinations. Candidate bodies must occupy loaded **active** terrain; failures report `UNLOADED_CHUNK`. The active margin exceeds the normal camera spawn ring, so new off-screen slimes can approach. Ordinary distant/unreachable slimes eventually release their slots even if their paused investigation has not completed. Crowned Slime and its summons retain separate encounter identities/budget. Projectile lifetimes, attack hitboxes, leap mechanics and boss attack patterns use their existing implementations.

## Persistence and deterministic settings

Generated terrain is reconstructible from the original saved descriptor. Player modifications outlive chunk instances in the existing authoritative owners:

- Resource damage/depletion stays in `CraftingManager.savedHits`, with the original stable resource IDs.
- Construction, cleared vegetation cells and decoration IDs stay in `BuildSystem` records.
- Stations remain single canonical persistent instances/records; their images and spatial indexes are shared/local views.
- Drops, NPCs and persistent bosses retain their existing entity/save owners.
- Slime Nest initialization/defeat records remain in the existing encounter snapshot.

Unloading removes disposable scenery/resource/tuft/image instances **after** their modifications are retained in these owners. It performs no disk write and does not reset a dirty flag or create an independent chunk save database. Existing atomic save/backup writes capture all modification records, including those outside resident chunks. Old saves use the same schema and resource/building IDs; no chunk-size migration or old-data deletion is needed.

A persistence check exposed a pre-existing identity bug, also reproducible against the preserved baseline: jsonbeans reflection serialized settings fields in different orders between instances. `installGeneration` compares descriptor strings, so identical settings could unnecessarily regenerate the world on load. `WorldSettings` now writes fields in a fixed order, retaining the same keys, numeric values and compatible decoder. Long seed and strict double-precision checks pass. Resident scene data is prepared before saved-position collision validation.

Legacy authored scenery and well/dungeon scenes retain their existing canonical area ownership. Procedural scenery/resources and terrain imagery are the streamed populations; this does not introduce a separate dungeon, chest or inventory implementation.

## Configuration and development tools

Set JVM properties before launching. Chunk settings are in `src/world/chunk/ChunkConfig.java`:

| Property | Default | Meaning |
|---|---:|---|
| `astral.chunks.size` | 32 | Chunk width/height in tiles; range 8–64 |
| `astral.chunks.activeRadius` | 1 | Minimum active neighborhood radius in chunks |
| `astral.chunks.preloadRadius` | 2 | Preloaded metadata neighborhood radius |
| `astral.chunks.cacheLimit` | 49 | Resident metadata cache target, subject to required coverage |
| `astral.chunks.imageLimit` | 12 | Ground/cliff image cache target, subject to visible coverage |
| `astral.chunks.keepSeconds` | 10 | Dormant retention before expiry |
| `astral.chunks.buildCacheSize` | 8 | Local building-render snapshot width/height |

The 12/16-tile camera activation/preload margins are in `WorldChunks.update`; image lookahead is in `TileManager.prepareView`. Larger chunk/image settings increase memory roughly with `chunkSize² × tileSize² × cachedImageCount × 4`, plus cliff images. Required camera/active coverage can exceed configured cache targets.

Start with `-Dastral.dev=true`, then use:

```text
/performance on
/performance reset
/performance grid
/performance off
/chunks grid
```

`/chunks` is an alias for the performance command. `grid` toggles lifecycle-colored chunk boundaries. `-Dastral.performance.debug=true` enables the overlay on startup when development mode is enabled. `-Dastral.performance.log=true` enables the older periodic render-scope console log; it is quiet by default.

The overlay reports actual painted-frame FPS/display period, scope means/peaks, logical player chunk, loaded/active/cached counts, visible tiles/actors, active slimes, image-cache bytes, a rough terrain-array/image estimate and actual JVM heap usage. The terrain estimate excludes shared sprite assets/live entity objects and is not a retained-heap measurement. Window-paint counters use atomic values because painting runs on the EDT. A headless run displays an unmeasured display period.

Existing encounter diagnostics remain `/encounters on`, `/encounters fast`, `/encounters normal`, `/encounters now`. Interval/probability/cap values remain in `EncounterSpawnConfig` and `astral.encounters.*`; the generator's existing world settings remain responsible for world dimensions/seed. Forced checks retain collision, active-terrain, visibility and boss protections.

## Validation and remaining costs

The direct software pipeline probe includes scene composition, buffer publication, UI composition and a software `paintComponent` blit. These rates exclude display scheduling/GPU behavior. Results are in `performance-measurements.json`; the final isolated scenario table is below.

| Scenario | Mean ms | p95 ms | Max ms |
|---|---:|---:|---:|
| `open_idle_1280x720` | 8.846 | 9.442 | 11.659 |
| `forest_walk` | 11.324 | 19.291 | 27.090 |
| `pond_shore` | 8.373 | 8.557 | 8.955 |
| `multi_level_cliffs` | 8.596 | 8.745 | 8.841 |
| `normal_4_pixels_camera_traverse` | 10.011 | 11.335 | 18.115 |
| `rapid_5_tiles_per_frame` | 11.229 | 34.362 | 50.886 |
| `six_slime_combat` | 8.869 | 9.442 | 18.343 |
| `inventory_open` | 10.501 | 10.729 | 10.744 |
| `inventory_1920x1080` | 21.174 | 21.949 | 22.070 |
| `crowned_slime_phase_two` | 16.793 | 17.288 | 17.432 |

The six-slime fixture used six normal slimes; the Crowned Slime fixture reached phase two and retained all five summoned slimes. Normal-speed camera traversal used 4 pixels per simulated tick; the stress case jumped five tiles (240 pixels) per frame and deliberately exceeded ordinary movement speed.

Acceptance checks cover repeated unload/reload and 80 distant camera visits, bounded residency/images, global terrain/elevation/meadow hashes independent of visitation order, visible image coverage, unique scenery instances, partial resource damage/depletion retention, rotated furnishing collision/culling and adapter reuse, stations, real atomic disk save/load, boss dormancy/disengagement/defeat, and moving-enemy identity across a chunk border. A room crossing tile 31/32 matched the authoritative full-building renderer **pixel-for-pixel**, including joins, whole-building outlines, inner shadows and door geometry. The many per-object uniqueness assertions explain the large assertion count; it is not a claim of hundreds of thousands of distinct gameplay scenarios.

- Full Java source compilation: passed.
- Phase 3 gameplay acceptance: 147 checks passed, including directional/tool/footprint/outline rules, encounters across multiple seeds/sizes, and actual save/network-payload roundtrips.
- Existing UI/crafting/inventory acceptance: 76 checks passed.
- Chunk acceptance: 429,460 assertions passed in the latest run, including the repeated per-object uniqueness checks.
- Cliff topology/native sprite checks: passed.
- World settings precision/serialization checks: passed after the stable-order fix.
- Installation/resource and well-scene checks: passed in both procedural and authored modes, using the project map resources on the classpath.

The retained-heap stress run started at 327 MiB. At the same settled camera position after the first exploration pass it measured 367 MiB, and after the second it measured 367 MiB following explicit GC. Pending worker jobs were allowed to finish and distant residents to expire before each comparison. This supports a bounded residency/cache working set for that fixture, rather than proving an arbitrary-duration leak absence. Visible terrain-image residency stayed within the configured/required bound; the rapid-visit acceptance run peaked at 59 MiB of terrain-image cache because asynchronous preloads were frequently cancelled between teleports. Normal full-pipeline runs filled approximately 109–119 MiB of ground/cliff imagery. Global sprite assets and temporary allocation space are additional heap costs.

Remaining limits are explicit:

- Actual live 60 FPS has not been verified. The supplied live overlay can distinguish simulation, software lighting/UI and actual window-paint costs on the target machine.
- Cold startup, explicit world regeneration and visible misses after abrupt teleports can stall. The final 256×256 scope run included a 172.928 ms cold streaming integration peak; that is not the warmed idle update time. Image preparation is bounded/background where possible, but visible correctness takes precedence over presenting holes.
- The existing software cloud/light pass remains the largest steady CPU render component: 5.531 ms mean in the isolated 256×256 scope run. Its appearance and movement were preserved.
- Software painting/upscaling at 1920×1080 is materially more expensive than the logical scene and misses a 60 Hz budget in some fixtures. GPU/HiDPI display-driver behavior is outside this headless measurement. Occasional warmed forest/UI/GC spikes remain; stable frame pacing is not claimed.
- The finite global region/height/grass plan still occupies memory proportional to map area and is planned at world creation. Streaming bounds heavy live scenery/resource instances and images; it is not an infinite-world or on-demand global-generator rewrite.
- Persistent modification/entity records grow with actual player-created content. Image caches and disposable residents are bounded; persistent gameplay state is retained for correctness.
- Existing atomic disk saves still run through the existing save path. Unloading never triggers a disk write; slow storage can still cause an autosave spike, now visible in the `SAVE` scope.
- Headless audio-device exceptions were caught by the existing sound loader during tests; live audio and perceptual display smoothness were not tested.

## Files changed by this update

| File | Performance/streaming change |
|---|---|
| [src/world/spatial/SpatialIndex.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/spatial/SpatialIndex.java) | Uniform cell buckets and identity bounds for local interaction/collision/render queries. |
| [src/world/IndexedScenery.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/IndexedScenery.java) | Canonical scenery collection with constant-time identity membership and indexed local queries; bulk removals maintain the index. |
| [src/interaction/InteractionTargetResolver.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/interaction/InteractionTargetResolver.java) | Adjacent-cell queries and cached outline selection; current position/direction/tool validity guards. |
| [src/interaction/InteractionValidator.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/interaction/InteractionValidator.java) | One local scenery query per interaction ray, retaining terrain/elevation/obstacle validation. |
| [src/crafting/CraftingManager.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/crafting/CraftingManager.java) | Indexed resident resource nodes and stations, chunk attach/detach, cached footprints and shared prepared station images. |
| [src/crafting/CraftedBolt.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/crafting/CraftedBolt.java) | Local scenery collision query; projectile hitbox and range remain existing values. |
| [src/monster/SlimeMotion.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/monster/SlimeMotion.java) | Local scenery collision queries and collision timings. |
| [src/ai/Pathfinder.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/ai/Pathfinder.java) | Local scenery prefilter for the existing bounded path search. |
| [src/main/CollisionChecker.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/main/CollisionChecker.java) | Spatial scenery queries and collision timings. |
| [src/world/WorldObject.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/WorldObject.java) | Index bounds covering visible art, collision and interaction footprints; tree animation cache warmup. |
| [src/world/chunk/ChunkConfig.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/chunk/ChunkConfig.java) | Configurable chunk, active/preload/cache and building visual-cache sizes. |
| [src/world/chunk/WorldChunks.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/chunk/WorldChunks.java) | Finite-world residency, lifecycle, hysteresis, persistent-owner integration, moving-enemy index and debug grid. |
| [src/tile/TileManager.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/tile/TileManager.java) | Lazy bounded 32-tile terrain/cliff caches, camera coverage, rolling directional preloads and bounded worker preparation. |
| [src/world/CliffLayer.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/CliffLayer.java) | Accept immutable prepared cliff images and cull by projected sprite bounds. |
| [src/world/SceneryLoader.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/SceneryLoader.java) | Bin procedural placement descriptors instead of constructing all procedural scenery at startup. |
| [src/world/GrassLayer.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/GrassLayer.java) | Lazy visible tuft actors prepared during updates, chunk release and decay of only stirred cells. |
| [src/world/building/BuildSystem.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/building/BuildSystem.java) | Local building snapshots with halos, neighbor invalidation, indexed furnishings, local render queries and stable furnishing adapters. |
| [src/world/SlimeSpawner.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/SlimeSpawner.java) | Loaded/active spawn validation, dormant AI gates, ordinary retirement and boss disengagement compatibility. |
| [src/world/SpawnDebugInfo.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/SpawnDebugInfo.java) | UNLOADED_CHUNK rejection diagnostics. |
| [src/world/generation/WorldSettings.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/world/generation/WorldSettings.java) | Stable serialization field order for generation identity comparisons; existing values and decoder stay compatible. |
| [src/save/SaveStore.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/save/SaveStore.java) | Prepare resident data before validating a restored player position; save timing instrumentation. |
| [src/main/PerformanceMetrics.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/main/PerformanceMetrics.java) | Per-scope mean/peak timings, display FPS, cache/actor/heap counters and development overlay. |
| [src/main/Gamepanel.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/main/Gamepanel.java) | Update-boundary streaming, local scenery/particle/entity work, timing scopes, shared asset preparation and presentation/paint measurements. |
| [src/main/UI.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/main/UI.java) | Development performance overlay integration. |
| [src/main/CommandProcessor.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/main/CommandProcessor.java) | /performance and /chunks commands and help. |
| [src/main/Prof.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/main/Prof.java) | Gate legacy profiling logs behind explicit development settings. |
| [src/main/InstallationCheck.java](sandbox:/Users/shiro/IdeaProjects/astralCore/src/main/InstallationCheck.java) | Prepare the streamed scene; validate authored-town NPCs only in authored mode. |
| [tools/PerformanceProbe.java](sandbox:/Users/shiro/IdeaProjects/astralCore/tools/PerformanceProbe.java) | Reproducible timing/JFR probe with isolated temporary saves. |
| [tools/PerformanceScenarios.java](sandbox:/Users/shiro/IdeaProjects/astralCore/tools/PerformanceScenarios.java) | Software pipeline, terrain traversal, inventory and slime/boss scenarios. |
| [tools/ChunkStreamingCheck.java](sandbox:/Users/shiro/IdeaProjects/astralCore/tools/ChunkStreamingCheck.java) | Residency, bounded memory, cross-chunk visual/collision, persistence and boss checks. |
| [docs/performance-measurements.json](sandbox:/Users/shiro/IdeaProjects/astralCore/docs/performance-measurements.json) | Machine-readable measurement evidence and methodology. |

## Reproducing checks and profiles

From the project root, using the project's Java installation and existing `lib/` jars:

```sh
rg --files src -g '*.java' > /tmp/astral-stream-sources
javac -cp 'lib/*' -d /tmp/astral-stream-check @/tmp/astral-stream-sources
javac -cp '/tmp/astral-stream-check:lib/*' -d /tmp/astral-stream-check tools/PerformanceProbe.java tools/PerformanceScenarios.java tools/ChunkStreamingCheck.java tools/Phase3GameplayCheck.java tools/UIPhase5Check.java tools/CliffTilesCheck.java tools/WorldSettingsCheck.java
java -Djava.awt.headless=true -Dastral.dev=true -Dastral.world.width=256 -Dastral.world.height=256 -Xmx2048m -cp '/tmp/astral-stream-check:res:lib/*' main.PerformanceProbe local256 60
java -Djava.awt.headless=true -Xmx1536m -cp '/tmp/astral-stream-check:res:lib/*' main.PerformanceScenarios
java -Djava.awt.headless=true -Xmx1536m -cp '/tmp/astral-stream-check:res:lib/*' main.ChunkStreamingCheck
java -Djava.awt.headless=true -cp '/tmp/astral-stream-check:res:lib/*' Phase3GameplayCheck
java -Djava.awt.headless=true -cp '/tmp/astral-stream-check:res:lib/*' main.UIPhase5Check
java -Djava.awt.headless=true -cp '/tmp/astral-stream-check:res:lib/*' world.generation.CliffTilesCheck
java -Djava.awt.headless=true -cp '/tmp/astral-stream-check:res:lib/*' world.generation.WorldSettingsCheck
java -Djava.awt.headless=true -Dastral.dataDir=/tmp/astral-stream-install -cp '/tmp/astral-stream-check:res:.:lib/*' main.Main --verify-install
java -Djava.awt.headless=true -Dastral.world.mode=authored -Dastral.dataDir=/tmp/astral-stream-authored -cp '/tmp/astral-stream-check:res:.:lib/*' main.Main --verify-install
```

Run benchmarks one at a time, separately from acceptance suites. `PerformanceProbe` writes `/tmp/astral-<label>.jfr` and prints means/p95/maxima, sampled CPU stacks, weighted allocation samples, GC events and cache counters. Open that recording in a JFR viewer for detailed stacks/allocations. The preserved baseline was measured before gameplay edits; its results are retained in the JSON evidence, while the probe commands above measure the current source.
