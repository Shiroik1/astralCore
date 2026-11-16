# Asset organization

## Runtime resources

`res/` contains resources loaded by the game. Resource paths are case-sensitive. Keep filenames stable unless every loader and catalog reference is updated together.

| Folder | Contents |
| --- | --- |
| `res/ground/` | Terrain, water, paths, ledges, and shore sheets |
| `res/scenery/buildings/` | Building sprites and material/light masks |
| `res/scenery/buildings.json` | Building palette, anchor, collision, light, and smoke definitions |
| `res/scenery/props/` | Well, lamps, fences, crates, barrels, logs, and rocks |
| `res/scenery/foliage/` | Trees and bushes, including snowy/yellow variants saved for later |
| `res/scenery/meadow/` | Authored flowers and body-height green/dark/golden field bend atlases |
| `res/scenery/nature/` | Stemless flower patches, single/triple mushrooms, oak/pine stumps and one twig |
| `res/hair/` | Cached hairstyle/color sheets and their manifest |
| `res/skill_icon/<subclass>/` | Icons for the six current subclasses |
| `res/player/` | Referenced player fallback sprites and weapon images |
| `res/object/`, `res/projectile/`, `res/tiles_Interactive/` | Referenced items, projectiles, and interactive objects |
| `res/font/`, `res/sound/`, `res/dialogue/` | UI fonts, loaded audio, and dialogue content |

`maps/` is the authoring source for terrain, elevation, grass placement, and scenery. Packaging embeds these files at `/maps/` in the game JAR. The old numeric tile maps and obsolete NPC/monster PNGs have been removed. Current character, creature, and NPC artwork is primarily drawn by `src/art/`.

## References and reusable art

`assets/reference/character_base/` preserves the earlier 3D source, original reference, exports, and previews. It is not used by the current vector character renderer.

`assets/reference/lakes/` preserves the lake study, export source, and reusable tile variants. Current terrain loads from `res/ground/`.

These folders include unreviewed material and are committed for later reuse. They are excluded from game packages because packaging only embeds `res/` and `maps/`. Duplicate ZIP exports were removed, while their individual source files remain.

Screenshots used by project documentation stay in `docs/images/`. Build outputs remain ignored under `build/`, `dist/`, `out/`, and tool `target/` folders.

## Editable artwork

Offline generators live in `src/art/` as `GenerateGroundTextures`, `GenerateHairTextures`, `GenerateFoliageTextures`, `GenerateGrassTextures`, `GenerateTownBuildingTextures`, and `GenerateTownPropTextures`. They export PNGs into the matching runtime folders. Commit both generator changes and their exported PNGs.

Body-height grass and the retained authored flowers use 25 saved bend frames and four terrain-tone rows per variant. The game shares cached atlas slices and applies wind/player movement through frame selection. Flowering bushes use the same simple blossom shapes. No image export is needed at game startup.

See [building assets](building-assets.md) for palette, lighting, smoke, and placement metadata.

## Verification policy

The test suite has been removed at the owner's request. Ask before running tests or gameplay/package verification. CI package verification is disabled by default and can only be enabled through the manual `run_verification` input after approval.


## Procedural meadow expansion

The owner-supplied sheet is preserved verbatim at `assets/reference/environment/user-supplied-atlas.png`. It is the production source for `GenerateNatureTextures`. The importer crops the 6 × 4 cells, trims transparent margins, and scales them into the existing `NatureProp` slots. It does not recolor, quantize, redraw, or replace their materials. Nature sprites use the dimensions, foot anchors and collision boxes in `NatureProp`; flower patches, mushrooms and the twig are nonblocking, while stumps are solid.

Props retain `NatureProp.EXPORT_SCALE = 2` with supersampled bicubic scaling and alpha-aware downsampling. `ObjectArt` scales them once at load time, matching the existing town-prop convention. Grass bend atlases use the supplied green, dark-green and golden clumps with the existing offline bend transforms. There is no image generation or loading in the frame loop. Green/dark-green fields may mix; golden fields use only the golden source cells.

`GenerateGroundTextures --environment` exports the baked grass-variant/detail sheets, six connected moss-path sheets, and water sheets. Earlier art atlases and the superseded vector study are retained under `assets/reference/environment/` for reference, but are not used by the production nature importer. Retired short/medium grass atlases remain archived under `legacy-meadow/`.

See [procedural world configuration](procedural-world.md) for settings and the exported asset preview.


## Cobblestone / grass connectors

Both regular and mossy cobblestone now render over the existing blended grass base. `CobbleTransitions` supplies shared rounded neighbor-mask contours, shallow grass inlets, inner/outer corner joins and a short alpha transition along exposed borders. Interior/connected seams remain opaque. The fade reveals the real underlying grass tone rather than a baked-in green color.

The regular paving connector sheets are `res/ground/cobble-connect-0.png` through `cobble-connect-2.png`; mossy paving continues to use its six `moss-path-*.png` sheets. `cobble-fringe.png` adds sparse, translucent edge stones on neighboring grass, including diagonal-only corner neighbors. All atlases have 256 neighbor slots. The original regular cobble sheet remains available as the interior art source/preview.

Run `art.GenerateGroundTextures --cobble` to export just these paving/connector resources. They are decoded once and baked into the existing terrain cache, with no per-frame image creation. This is an artwork export operation; gameplay verification still requires owner approval.

## Tile building

`res/building/` contains saved floor, wall, closed-door and open-door neighbor atlases. Their editable source is `src/art/GenerateBuildTextures.java`; a generated style reference is retained under `assets/reference/building/`. Layout authoring data saves separately to `maps/buildings/`. See [tile building](building-system.md) for controls, mask layout and scope.

Building preset authoring lives in `maps/blueprints/`, separate from per-world placed layouts under `maps/buildings/`. `res/building/elements.json` is the shared runtime/editor element catalog. JavaFX Builder source and its standalone Maven configuration live under `tools/building-maker/`; no JavaFX classes or dependencies are added to the game's runtime source tree. See [the Builder guide](../tools/building-maker/README.md).

### Interior materials and furniture

`res/building/` also holds cobblestone, reinforced timber and concrete floor/wall atlases plus the three tiled stair materials. `res/building/props/` contains nineteen complete, transparent furnishing sprites; `props.json` stores their anchors, dimensions, collision footprints and light radii. The selected furniture atlas and contact sheet live in `assets/reference/interior/`. `src/art/GenerateInteriorTextures.java` imports `vector-interior-source.png` directly, slicing the nineteen objects, removing loose alpha flecks in the gutters and exporting smoothly scaled transparent PNGs. It preserves the supplied designs and colors without redrawing or palette remapping. The procedural vector exporter is retained only as a reference. Furniture placement data is included in version 2 building maps and blueprints as free-position decorations rather than tile cells. Reference art is retained; runtime code loads only the exported PNGs and catalogs.

### Crafting prototype

`assets/reference/crafting/crafting-source.png` retains the approved nine-sprite atlas. `src/art/GenerateCraftingTextures.java` slices complete silhouettes and exports the four wooden weapons, three resources, Workbench and Forge into `res/crafting/`. Recipe and item metadata lives in `res/crafting/recipes.json`. Missing dedicated art uses explicit palette-based geometric placeholders in `CraftingArt`; see [the crafting guide](crafting-system.md) for the list.
