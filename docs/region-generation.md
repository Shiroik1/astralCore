# Region-driven grassland generation — versions 6–11

This implements `Pasted text(4).txt` within the existing Java2D/JPanel engine. Current worlds contain Flower Plains, Forest Groves, Pond Shores, rare Golden Meadows and ordinary Grassland, all inside the same Temperate Grassland biome. No sprites, water renderer, combat behavior, interaction range, buildings or class systems were replaced.

**Verification status:** source and asset-reference review only. The game has not been compiled or launched, and the development checker has not been run. Tests, gameplay checks and benchmarks require explicit owner approval under `AGENTS.md`. The old removed test suite has not been restored.


## Forest routes and square borders in version 11

The [current cliff fixes](cliff-redesign.md) keep route collision clear while allowing canopy/undergrowth continuity, restore square borders for new worlds, and correct cap seams, wall-foot corners and camera limits. Older landscapes are not migrated.

## Rare cliff formations in version 10

[The modular cliff update](cliff-redesign.md) budgets connected inland formations at an 8% target and hard 10% cap, with independent boundary highlands and at least 95% lowland access. Older terrain versions remain available.

## Elevation integration in version 9

[Phase 4](elevation-boundaries.md) adds a separate physical heightmap, irregular playable mask and connected cliff rendering. It retains the existing ecological rules while constraining footprints, pond basins, golden meadows and essential access. Older versions keep their layouts.

## Nest clearing in version 8

Version eight retains the version-seven ecological balance and creates a deterministic nest clearing from safe generated grass, dirt or paving before vegetation. It reserves an access corridor and falls back to a smaller arena when needed. Nest layout settings are saved with the world; versions four through seven keep their previous planners and layouts. See [procedural encounters](procedural-encounters.md) for combat, spawn tuning and the development `/nest` diagnostic.

## Vegetation rebalance in version 7

Forests occupy more of the broad environment mask. Tree candidates are substantially more frequent inside groves, with retained noise-defined clearings and collision spacing. Bushes follow canopy placement; mushroom and twig passes use actual shade/trunk proximity. Forest grass uses a lower coherent-field threshold and three clumps per accepted cell. Grass may now grow underneath tree crowns where roots and the player collision footprint remain clear, instead of losing a large exclusion rectangle around every canopy.

Plains have a stronger baseline of connected-looking flower beds, including ordinary grassland between the strongest flower masks. Small stones are much rarer, and large rocks prefer a few correlated forest groups. Trees and bushes claim space before stones. Starter crafting materials remain guaranteed separately.

Pond shores support mushrooms directly through dampness, even with no nearby trees. The shore mask retains noise variation but no longer drops entire shoreline stretches to zero influence. Denser green/dark grass uses up to four clumps per cell at the wettest edges; mushrooms use separate clustered placement. Plant roots and solid collision remain on land, while leaf/canopy images can naturally overhang water or paths. Spawn/route reservations and golden margins still protect full sprite bounds.

These changes apply to newly created version-seven worlds. Existing version-six and older saves keep their layout and harvested/building state. The rebalance has only received source review; it has not been compiled, visually checked or benchmarked. The standalone checker now reports loose stones, flowers, mushrooms, twigs and grass cells for both regional versions when explicitly run after approval.

## Planning and ecological relationships

The finite world is planned once before the existing 16×16 ground-image caches are baked:

1. Global-coordinate low-frequency value noise, with a small domain warp, produces smoothly blended forest and flower influences. A separate moisture field adds local variation. Region labels are diagnostics; placement uses continuous weights.
2. A starter-resource reservation uses the actual existing oak/rock sprite bounds to protect enough grass and access space before terrain selection. The original protected spawn/crossing is retained.
3. The existing terrain, pond and moss-paving generators run with those reservations. The existing special-encounter reservation is retained unchanged in purpose.
4. A multi-source distance transform measures tile distance from water. Noise breaks the shore influence into wet clumps and open gaps.
5. Golden meadows grow from a few eligible open-land seeds using connected frontiers. Their silhouettes and a grass-bend margin are protected from prop placement.
6. Trees, rocks, bushes, mushrooms, flowers, loose twigs/stones and body-height grass are placed in ordered passes. All candidates pass shared footprint validation.
7. A flood fill from spawn removes inaccessible prop/resource pockets. Starter pickups are added separately. Golden connectivity and coverage are validated as generation invariants.

Flower Plains favor correlated flower beds, few trees/bushes and little tall grass. Flower color comes from a broader noise field so each bed has a coherent palette while preserving all six existing variants. Forest Groves favor tree clusters with noise-defined clearings, nearby bushes, shaded mushrooms, twigs and small stone groups. Cached proximity fields connect understory placement to actual trees and bushes. Ordinary Grassland uses lighter baseline densities between these environments.

Pond influence modifies the surrounding forest/flower weights instead of replacing them. The first two shore tiles favor green/dark-green tall grass; tiles three through five taper to moderate growth; tiles six through eight blend back into the surrounding region. A correlated gap mask prevents a uniform ring. Trees and bushes avoid the immediate shore. Final resource accessibility checks and nonblocking grass leave approaches available. Accessible masks also exclude unreachable ordinary grass fields; a golden footprint that loses access is rejected as a whole.

Placement uses existing image bounds, collision rectangles, the current 32×32 player footprint, foot-position-aware gathering reach, boundary checks and configurable tree/rock clearance. Tree crowns may overlap naturally; collision footprints cannot. Ground decorations avoid occupied anchors, while mushrooms and twigs can use walkable shade beneath canopies. Body-height fields avoid solid roots, occupied prop anchors and protected routes; version seven allows understory grass beneath tree crowns. Base grass variations and tone/path/cobble blending still use the existing ground assets and renderer.

The density values in the request are design targets rather than exact coverage guarantees: actual sprite size, protected land, spacing and access checks reduce accepted placements. Appearance and balance still need multi-seed visual review.

## Golden-meadow connectivity

Golden grass is no longer selected independently by a per-tile low-noise threshold in new worlds.

Each meadow starts at a sunny, nonforest grass seed away from water, spawn and reservations. Every cell accepted from the frontier has a four-neighbor parent already in that footprint. Distance and smooth local noise shape an irregular border; growth cannot jump across water, paths or protected cells. Undersized footprints are rejected. Seeds must be reachable over the terrain; if final prop collision makes any part inaccessible, that entire footprint is rejected rather than split. Other props and green fields respect the golden footprint and its bend margin, so later placement cannot cut holes through the meadow.

A final flood fill verifies that each meadow ID has exactly one connected component, every cell uses the existing golden field atlas, there are no singleton fragments, and total coverage does not exceed the configured eligible-land budget. Default coverage is **2%**, with a validated maximum of **3%**. Small or unsuitable worlds may have no meadow. Maximum patch count and patch size can also make realized coverage lower than the target on large worlds.

All golden cells use golden clumps. Green and dark-green fields may mix elsewhere. The ordinary grass ground remains underneath, providing the existing natural transition without new artwork.

## Starter crafting and harvesting

The generation plan protects **7 loose Twigs and 5 loose Stones** within three tiles of spawn, enough for the current level-one Wooden Axe recipe (3 Twigs + 2 Stones) and some spare materials. It also reserves an accessible oak, a large rock deposit and up to four reachable green grass cells nearby. The Wooden Axe can gather both wood and Fiber through the existing tool rules. These small progression guarantees remain even if optional tree, rock or field density is set to zero.

Natural twigs, loose stones and rock deposits use the existing scenery and harvesting identities. Mossy small/large rocks are now recognized by the same gathering categories as their plain counterparts. New generated starter/ore pickups use deterministic `generated:col:row` identities, while scenery and grass retain their existing coordinate IDs. Harvested states are restored through the existing crafting save data. No recipe costs, inventory controls or interaction distances changed.

## Configuration

VM options apply to **new worlds**. Existing versioned saves use their stored configuration. In IntelliJ, add them under Run → Edit Configurations → VM options, for example:

```text
-Dastral.world.seed=987654321 -Dastral.world.width=100 -Dastral.world.height=100
-Dastral.regions.scale=0.024 -Dastral.regions.goldenCoverage=0.02
```

Existing `astral.world.vegetation`, `trees`, `flowers`, `rocks`, `fields`, `ponds` and `details` remain overall density controls. The additional settings live in `GenerationConfig`:

| Property prefix `astral.regions.` | Default | Effect |
| --- | --- | --- |
| `scale` | `0.028` | Lower values create broader forest/flower environments. |
| `warp` | `7` | Boundary warp in global tiles; zero removes the warp. |
| `forest` | `1` | Multiplier for tree and bush candidates. |
| `flowers` | `1` | Multiplier for flower beds. |
| `grass` | `1` | Green/dark body-height field multiplier. |
| `shore` | `1` | Pondside vegetation influence multiplier. |
| `treeSpacing` | `0.75` | Approximate minimum clearance between tree collision footprints, in tiles. |
| `rockSpacing` | `0.5` | Corresponding clearance for large rocks. |
| `shoreNear` / `shoreMiddle` / `shoreFar` | `2 / 5 / 8` | Increasing distance-band endpoints in tiles. |
| `goldenCoverage` | `0.02` | Golden eligible-land budget; allowed range `0`–`0.03`. |
| `goldenMinSize` | `12` | Minimum connected footprint size; at least two cells. |
| `goldenMaxSize` | `90` | Maximum cells per golden footprint. |
| `goldenPatches` | `3` | Maximum golden footprints per world. |

The existing development `/regenworld <seed>` command retains the loaded world's generator version and settings. Use a **new character slot** to preview version 11 while keeping an older character's landscape. No automatic conversion of an occupied legacy world to regional generation is performed.

## Debug visualization

Enable the overlay with:

```text
-Dastral.dev=true -Dastral.regions.debug=true
```

It tints walkable ground beneath existing props: light green is ordinary grassland, pink is Flower Plain influence, dark green is Forest Grove influence, cyan is Pond Shore influence and gold marks actual golden footprints. The development log prints region cell counts and seed/version.

Add `-Dastral.regions.debugDensity=true` for a purple local vegetation-density overlay instead. Density is precomputed from accepted props and field cells using a summed-area neighborhood, not recalculated while drawing. Omitting the debug flag disables the overlay and its logging completely; the overlay is also gated behind development mode.

## Save and multiplayer compatibility

Character format **2** adds a world-settings descriptor: signed 64-bit seed, dimensions, generator version, original terrain controls and all region settings. Configuration is restored **before** scenery, buildings, harvested resources and encounter state. The existing atomic writes and backups remain. Older game builds reject the newer character format rather than silently ignoring its terrain identity.

Versions **4, 5, 6 and 7** of terrain/decorative generation remain available. Version-six regional placement is retained in `Version6VegetationGenerator`, including its original mask and spacing behavior. Unversioned saves from before the slime encounter use version 4; saves containing the earlier nest-state field use version 5. Their recorded crafting seed is recovered exactly, including long values beyond floating-point integer precision. First loading an unversioned save still requires the previous mode, dimensions and nonseed generation options: that information was not stored by the earlier build and cannot be recovered automatically. After saving with this build, its complete settings are pinned. Do not change those legacy launch options before the first load/save.

Building sidecar lookup follows the loaded generator version. The version-five fallback to version-four building files is retained; new version-six/seven worlds do not load older sidecars onto their changed landscape. Character-embedded building maps, cleared vegetation cells, placed stations, depleted scenery and harvested grass remain in their existing persistence systems. Restoring those changes temporarily selects the overworld, including when the character saved inside the well. Generated ground has no new runtime terrain-editing system.

The descriptor also travels through the existing world snapshot. Clients build a matching finite plan when the host descriptor changes, then consume ordinary entity/scenery snapshots. It is not regenerated on each snapshot or during rendering. Both peers need this source build. Building/crafting retain their existing single-player restriction; no new networking gameplay or streaming system was added.

The world still uses finite planning and cached drawing chunks. Visiting drawing chunks in another order cannot change the already-completed plan. Truly lazy independent chunk generation is not implemented; it would need stable neighbor conflict resolution and reuse of the world-level golden/reservation plan.

## Files in this phase

Created:

- `GenerationConfig`, `EnvironmentRegion`, `RegionMap`, `RegionGenerator`: tuning, blended masks and diagnostic density.
- `PondVegetationGenerator`: bounded distance-to-water planning and irregular shore influence.
- `GoldenMeadowGenerator`: connected growth, size/coverage limits and flood-fill validation.
- `PlacementRules`: shared actual-footprint, spacing, shade and accessibility checks.
- `VegetationGenerator`, `Version6VegetationGenerator`, `ResourceGenerator`, `StarterResourcePlanner`: ecological passes and starter guarantees.
- `RegionDebug`: development-only cached overlay and summary.
- `WorldSettings`: explicit versioned configuration serialization.
- `tools/RegionGenerationCheck.java`: manually invoked multi-seed generator checks.
- `docs/region-generation.md`: this guide.

Modified:

- `WorldGenerator`, `GeneratedWorld`, `WorldConfig`, `TerrainGenerator`: version-six pipeline, retained legacy paths and region-aware existing grass detail.
- `WorldPalette`, `WorldObjectType`: reuse actual cached artwork for generation tooling without constructing the game UI.
- `CraftingManager`: consume planned pickups in version six, retain legacy placement in older worlds, recognize mossy rock resources and read long seeds exactly.
- `Gamepanel`, `TileManager`: install the pinned world plan outside drawing and add the optional debug overlay.
- `CharacterData`, `SaveStore`: configuration persistence, legacy selection and overworld-only restoration of world changes.
- `WorldSnapshot`, `SnapshotBuilder`, `GameClient`: share the same terrain descriptor with clients.
- `BuildSystem`: select the correct version/settings-specific sidecar identity.
- `SlimeNestPlanner`: use the loaded plan's version in its existing ID and keep its access reservation outside the earlier starter reservation in version six; encounter behavior was not changed.
- `README.md`, `docs/procedural-world.md`: current guide and configuration links.

## Verification prepared, not executed

`GoldenMeadowGenerator.validate()` is part of generation correctness: it rejects a disconnected or invalid golden plan before the world becomes playable. No generation was executed during this implementation session.

The standalone development checker covers versions six through eleven across six seeds (including zero, negatives and both long extremes) at 16×16, 32×48, 100×100 and 256×192. It compares repeated plans and forward/reverse chunk reads, validates golden components and coverage, checks real sprite footprints and access, verifies starter resources and round-trips saved settings. It uses existing assets; it does not create placeholder graphics or launch a window.

After owner approval, from the project root with JDK 21+:

```sh
mkdir -p build/region-check
rg --files src -g '*.java' > build/region-check/sources.txt
javac --release 21 -encoding UTF-8 -cp 'lib/*' -d build/region-check @build/region-check/sources.txt tools/RegionGenerationCheck.java
java -Djava.awt.headless=true -cp 'build/region-check:res:.:lib/*' world.generation.RegionGenerationCheck
```

Also verify visually across several seeds: open flower plains, forest clearings and gathering, pond approaches, golden silhouettes, existing slime encounters, and saving/loading from both world areas. Test existing built/harvested worlds with their original launch options before the first migration save. Generation/rendering performance and multiplayer descriptor application have not been measured or exercised.

No separate reed asset exists in the current scenery catalog, so ponds use the existing green/dark tall grass and bushes. There is no new aquatic art or biome. Very small maps may skip both specialized golden footprints and the existing optional nest. Extreme custom dimensions/spacing may leave no room for the actual starter artwork; generation reports that rather than producing an unwinnable resource start.
