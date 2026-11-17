# Modular cliffs and rare inland formations — generators 10–11

This update uses the supplied reference for modular structure and connection behavior, with AstralCore's existing grass and earth palette. It preserves Java2D/JPanel, the logical grid, movement speed, environmental regions, boss behavior and existing persistence. It introduces no ramps, roofs, enemies, biomes or interaction-range changes.

**Status:** native runtime PNGs were exported with the offline Java2D artwork workflow and reviewed as assets. Only the artwork exporter and its art dependencies were compiled for production. Full-game compilation, automated checks, gameplay, multi-seed execution and performance verification have **not run**; `AGENTS.md` requires owner approval first. The checks described below are prepared, not claimed to have passed.

## Current fixes — generator 11

New version-eleven worlds restore a square/rectangular playable interior with a solid outer tile ring. The previous noisy boundary mask remains available only for versions nine and ten so existing saves and structures retain their layout. The inland 8% target / 10% maximum remains unchanged. Boundary radius/irregularity settings are retained for legacy reconstruction and do not reshape version-eleven borders.

The forest-strip cause was the shared reservation mask: both the five-tile crossing and pond/nest approach routes rejected whole tree images and suppressed grass/detail. Version eleven separates collision-only `accessGround` routes from full spawn, starter-resource and nest clearings. Crossing/approach lanes are one tile wide with connected turns. Trunks, bushes and solid rocks stay out of those cells, but canopies can overlap them and nonblocking mushrooms, twigs, flowers and natural grass can grow across them. Baked grass detail is no longer suppressed along navigation lanes. Terrain/pond/cliff planning still honors all routes, preserving access. Starter approach reservations avoid marking their own resource artwork as blocked.

The visual join fixes apply to version-ten and later cliff rendering without moving logical terrain: wall outlines are swept to the base with the same rounded profile, avoiding the recessed/square foot corner. Wall geometry is supersampled separately, then the already-antialiased cap is composed exactly once at native resolution. This avoids independently resampling connected cap rows into partial transparency and exposing bright horizontal seams. No new art or palette change is required.

Procedural camera origins are clamped to `[0, max(0, mapPixels - viewportPixels)]` on both axes. Small maps use origin zero instead of a negative centering offset. Normal-sized maps keep the viewport inside the full map dimensions; tiny maps retain the existing decorative exterior fill when the viewport itself is larger than the map. Authored interiors and dungeon camera behavior are preserved.

Create a new character/world for the square border and new forest layout. Existing versions four through ten retain their saved terrain/decorations; existing version-ten worlds receive the rendering and camera fixes. `/regenworld` retains its loaded generation version.

Modified for this fix: `GeneratedWorld`, `WorldConfig`, `WorldBoundaryGenerator`, `StarterResourcePlanner`, `SlimeNestPlanner`, `InlandCliffPlanner`, `PlacementRules`, `TerrainGenerator`, `VegetationGenerator`, `CliffTileResolver`, `CliffLayer`, and `TerrainHeightResolver`. The existing optional tools now include joined-cap opacity/color, rounded-foot, square-mask, soft-route canopy/grass/litter, solid-trunk exclusion and camera-limit regressions. Compilation, tests and gameplay verification have not run; they require owner approval.

## Why the previous landscape had too many cliffs

Generator nine quantized broad noise over the whole playable mask without a coverage budget. Its accessibility repair could also convert disconnected lowlands into additional elevated terrain. Reducing independent tile odds would not control the number or shape of the resulting landmasses.

Generator ten starts inland ground at zero and adds a small number of explicitly budgeted, connected formations. It never raises disconnected lowland merely to classify it as scenic terrain. Generator nine's original algorithm remains available for existing saves.

## Runtime artwork

The exported sprite cells are **48×48 RGBA**, matching the game's native tile size. Physical height steps remain **24 pixels**, matching the existing single-step ledge convention. Textures are repeated at native scale and clipped; they are not stretched into taller walls.

| Asset | Layout | Role |
| --- | --- | --- |
| `res/ground/cliff-top-0.png` … `cliff-top-3.png` | 16×16 cells each | Four existing grass tones × every eight-bit connection mask; transparent outer/inner corners, ends, T and cross joins |
| `cliff-middle.png` | One cell | Vertically/horizontally repeatable exposed earth with a few broad, low-contrast facets |
| `cliff-side-left.png`, `cliff-side-right.png` | One cell each | Soft directional side shading, clipped to exposed side faces |
| `cliff-lip.png` | Four tone columns | Upper grass/earth lip, applied once along the highest exposed surface |
| `cliff-foot.png` | Four tone columns | Earth-to-lower-grass transition, applied at the bottom of the exposed wall union |
| `cliff-tiles.json` | Mapping metadata | Native size, step, bit convention, atlas indices and material filenames |

`GenerateCliffTextures` samples **actual PNG pixels** from `grass.png`, `ledge.png`, `path-0.png` and `cobble.png`. This export's earth base is `#987A63` and its shadow is the existing ledge brown `#8A6B58`; grass retains the four existing green tones. Source shapes are drawn at 4× resolution and downsampled once for smooth transparent edges. Material motifs wrap periodically at sprite edges. There are no black outlines, bright imported colors or dense crack textures.

Existing `ledge.png`, `steps.png`, grass, path, water and seasonal/reference assets were retained. The old ledge provides the earth palette; its actual texture remains available for authored and older generated worlds. The redesigned top sprites use the existing rounded ground-connector geometry. At cache-bake time, their alpha masks reveal the game's original ground rendering, including baked grass variation and blended neighboring tones; they do not replace ordinary grass textures.

The editable source is `src/art/GenerateCliffTextures.java` with shared geometry in `CliffTiles`. The existing exporter also accepts `GenerateGroundTextures --cliffs` to export only this new family. All production images live in `res/ground/`; no reference mockup is substituted for a runtime asset.

## Autotiling and continuous walls

The code and metadata use this explicit convention, independent of the inconsistent labels in the supplied diagram:

| Neighbor | N | E | S | W | NE | SE | SW | NW |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Bit | 1 | 2 | 4 | 8 | 16 | 32 | 64 | 128 |

At threshold `k`, a bit is set when the neighboring **world** tile has height `>= k`. Atlas index is `mask`, column `mask % 16`, row `mask / 16`. This supports all 256 input masks, with equivalent diagonal-only cases sharing the same effective shape. Building masks remain independent: structures join by building elements, while cliffs join by height.

For each height threshold, `CliffTileResolver` creates the exposed band and subtracts the connected surfaces of adjacent tiles. This prevents a phantom internal face when a neighbor is equal/higher or its cap is cached in a different chunk. Wall bands are united, textured once, and topped by **only the actual highest cap**. A three-high tile beside a one-high neighbor therefore exposes two height steps, rather than three full walls or repeated intermediate grass caps.

The grass lip is limited to the upper exposed cap boundary. The foot is computed from the lower boundary of the united wall, not from each repeated material strip. Side materials follow the same clipped topology; corner/end shapes come from the cap mask and connected wall geometry rather than unrelated manually placed sprites. Texture sampling is anchored to the logical world tile base, so different stack heights do not shift the material phase across a join.

Interior surfaces remain in `TileManager`'s existing 16×16 ground cache. Exposed cells remain cached transparent `CliffLayer` depth objects. Only cache construction composes sprites; exposed-wall geometry is rasterized at 2× and downsampled once to smooth its clipped outline, while frames draw the resulting native-size images. Masks and clipping sample neighboring global heights across chunk boundaries. Logical collision and the existing camera/projection remain separate from visual selection.

The mapping covers the requested square-grid straight edges, endpoints, outer/inner corners, T/cross junctions, isolated platforms, L shapes and nested heights 1–3. Visual seam/depth quality still needs gameplay verification. No climbing, ramps, overhangs or free-angle slopes are introduced. Extra flower/color themes are left to existing environmental decorations rather than adding bright cliff variants.

## Budgeted formation generation

`CliffFormationConfig` is saved with the world's generation descriptor. Defaults:

- Target inland footprint: **8%**.
- Hard maximum inland footprint: **10%**.
- Minimum accepted formation: **24 tiles**.
- Typical formation area: **2.5% of interior land**, varied deterministically per formation.
- Minimum terrain and clear-lowland accessibility: **95%**.

The stable denominator is all **playable, walkable-terrain land cells before vegetation**, including protected lowland but excluding water and the nonplayable world boundary. Objects and buildings never affect it. Changing a dirt/cobble cell to grass does not change this denominator. The numerator counts inland cells with height above zero exactly once, irrespective of rendered wall height or the number of face sprites. Boundary highlands are generated separately and never spend this budget.

The planner ranks deterministic coordinate-addressed centers, favoring groves and avoiding Flower Plains where possible. Centers are separated relative to formation size. Connected priority-frontier growth combines broad elliptical distance with smooth noise to create coherent irregular landmasses. At most eight formations are accepted; ordinary test maps generally have room for only a few. Small/crowded maps may have fewer or none.

Cleanup erodes tips, fills small concavities, performs a 3×3 opening to remove thin strips, and keeps the largest four-connected component. Components below the configured minimum are rejected. Coverage is recalculated **after** cleanup; an over-budget candidate is rejected as a whole. No cleanup pass is allowed to add unbudgeted cliff cells. The final plan independently measures coverage again and refuses a contract violation.

Most formation cells are height one. Sufficiently broad formations may contain a connected, distance-inset level-two core. A level-three core requires a large formation, a rare seed condition and a substantial connected footprint. These cores stay inside the already-counted formation; they do not increase its inland footprint.

## Access, regions and existing gameplay

Before a formation can be accepted, candidates exclude protected spawn/starter artwork and approaches, the nest/corridor, a visual-clearance margin, water, and a four-tile pond-shore band. Each pond also receives a reserved dry approach before formation growth, validated again after vegetation. Golden Meadows are planned afterward on accessible flat terrain, so new cliffs cannot cut existing generated golden components.

Each candidate receives a lowland flood fill. It is rejected if the starting component falls below 95% of otherwise ordinary lowland or loses the nest/starter reservations. No invisible walkable links are created. A second metric after scenery measures reachable **clear** lowland, excluding cells blocked by actual scenery bodies. A bounded bottleneck repair can remove a few nonstarter scenery obstacles to open grove paths. If the final result is still below 95%, generation deterministically retries with fewer cliff formations; it does not change the boundary or force an inaccessible formation to satisfy the target percentage. If even a zero-formation landscape cannot meet the contract, generation reports that failure rather than pretending success.

Tree/root/canopy placement still requires valid same-height sprite footprints. Loose starting Twigs/Stones, Fiber and mining pickups remain generated through the existing reachable-resource systems. Ordinary regions, dense pond vegetation and rare connected golden fields retain their existing ecological passes. Boss arena reservations precede cliff obstacles; Crowned Slime summons, projectiles and combat code are unchanged.

Collision remains height-based, including exposed edge bands and diagonal-corner protection. The sprite chosen does not determine solidity. Existing player/slime motion and projectile collision calls remain intact. Construction retains its lowland/flat-footprint restrictions, wall/floor/door connections, collision and material transactions.

## Diagnostics and controls

With `-Dastral.dev=true`, use:

- `/elevation` — toggle the current overlay.
- `/elevation height` — physical heights and exposed edges.
- `/elevation formations` — connected formation labels/footprints.
- `/elevation edges` — exposed edge lines.
- `/elevation boundary` — separate nonplayable border mask.
- `/elevation access` — generated reachable and inaccessible cells.
- `/nest` — existing encounter ID/coordinates.

The overlay includes cached seed/version, interior land count, cliff footprint, coverage/maximum, formation/disconnected-component counts, inland/total exposed edge counts, terrain and clear-lowland access ratios, border exclusion, and spawn/nest access. The generation log prints the same diagnostics. Connectivity is not recalculated while drawing.

Add these JVM options for a **new world**:

| Property | Default | Effect |
| --- | --- | --- |
| `astral.cliffs.target` | `0.08` | Desired inland footprint share; may undershoot when safe space is limited |
| `astral.cliffs.maximum` | `0.10` | Hard cap; configurable downward, never above 10% in this implementation |
| `astral.cliffs.minimumTiles` | `24` | Reject smaller components; allowed 9–512 |
| `astral.cliffs.formationAreaFraction` | `0.025` | Typical footprint relative to interior land; larger gives fewer, broader formations |
| `astral.cliffs.minimumAccess` | `0.95` | Minimum connected terrain/clear-lowland ratio; allowed 0.95–1 |

For example, `-Dastral.cliffs.target=0.06 -Dastral.cliffs.maximum=0.08` makes an even flatter world. A zero target/maximum disables inland formations while retaining the enclosing cliffs.

`astral.elevation.maxHeight`, `boundaryIrregularity` and `boundaryRadius` retain their existing roles. The old `frequency` and `raisedThreshold` fields remain pinned for generator-nine compatibility; they no longer control generator-ten inland formations. Formation growth noise and nested-height thresholds are centralized in `InlandCliffPlanner`.

## Saves and versioning

New worlds use **generator 11** (see current fixes above). The complete cliff configuration is saved and used during deterministic reconstruction. New building-sidecar identity includes these settings. Versions **4–10 preserve their existing algorithms and layout keys**, including generator nine's denser elevations. No saved structures, terrain edits or nest defeat flags are moved or overwritten to install this update.

Use a **new character/world** to get the rare-formation landscape. `/regenworld <seed>` intentionally retains the loaded version and settings; it does not silently convert an occupied version-nine world. No user save was rewritten during this work.

## Files

Created: `CliffFormationConfig`, `CliffStatistics`, `InlandCliffPlanner`, `art/CliffTiles`, `GenerateCliffTextures`, `tools/CliffTilesCheck.java`, the nine runtime PNGs/mapping JSON above, and this guide.

Modified: `GeneratedWorld`, `HeightMap`, `WorldGenerator`, `WorldConfig`, `WorldSettings`, `TerrainAccessibilityValidator`, `PlacementRules`, `CliffTileResolver`, `ElevationDebugOverlay`, `GroundTiles`, `GenerateGroundTextures`, `CliffLayer`, `TileManager`, `Gamepanel`, `BuildSystem`, `CommandProcessor`, the existing `RegionGenerationCheck` and related README/world/asset guides. Existing unrelated workspace changes were retained.

## Prepared checks, awaiting approval

`RegionGenerationCheck` now includes generator ten across positive/negative/extreme long seeds and 16×16, 32×48, 100×100 and 256×192 maps. It independently counts eligible land and elevated cells, flood-fills formation components, rejects tiny/disconnected labels, checks the configured maximum and accessibility, compares repeated/forward/reverse reads, verifies starter/nest/golden invariants and round-trips saved cliff settings. A zero-budget case verifies that boundary highlands remain present and excluded.

`CliffTilesCheck` independently checks all cardinal joins across the 256 cap masks, inner/outer corner alpha, native sprite sizes/transparency, the explicit neighbor bits across a 16-tile cache boundary and taller stacks without intermediate green lips. These are explicit development tools, not a restored framework or automatic build hook.

Neither tool nor the full game has been run. After approval, compile and run these checks, then inspect straight/concave/L/T/cross connections, nested height differences, camera/chunk seams, gathering, pond/nest access, construction, old/new save loading and performance in gameplay.


## Character-creation settings precision fix

The bundled jsonbeans 0.5 `Json.readValue` implementation narrows numeric doubles through `JsonValue.asFloat()`. That turns the saved 0.10 maximum into a value slightly above the limit and the 0.95 access minimum into a value slightly below it, rejecting character creation during save validation. `WorldSettings` now overrides only numeric double decoding to use `asDouble()` for all layout arrays, preserving both strict limits and deterministic settings. `SaveStore` reports generation-descriptor failures separately from crafting-data failures. No saves are deleted or reset. The opt-in `WorldSettingsCheck` covers defaults, exact long seeds, repeated round trips and rejection of an actually excessive maximum; it has not been run pending owner approval.
