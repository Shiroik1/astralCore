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
| `res/scenery/meadow/` | Short grass, tall grass, and simple flower bend atlases |
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

Grass and flowers use 25 saved bend frames and four terrain-tone rows per variant. The game shares cached atlas slices and applies wind/player movement through frame selection. Flowering bushes use the same simple blossom shapes. No image export is needed at game startup.

See [building assets](building-assets.md) for palette, lighting, smoke, and placement metadata.

## Verification policy

The test suite has been removed at the owner's request. Ask before running tests or gameplay/package verification. CI package verification is disabled by default and can only be enabled through the manual `run_verification` input after approval.
