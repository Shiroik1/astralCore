# Tile building (first version)

Build in the single-player overworld with `/build floor`, `/build wall`, or `/build door`. Chat accepts `/` commands. Placement is free; inventory costs, ownership, roofs, and multiplayer construction are not part of this version. Built elements are hidden and have no collision during network sessions.

| Input | Action |
| --- | --- |
| 1 / 2 / 3 | Select floor / wall / door while building |
| Hold left mouse | Place on the pointed grid cell |
| Hold right mouse | Remove from the selected layer |
| R | Toggle vertical preference; rotate an isolated wall or a doorway |
| E near a door | Open or close; closing refuses an occupied passage |
| Escape or `/build off` | Leave build mode |
| `/savebuild` | Save this world's complete building layout |

Walls and doors occupy one structure layer; floors occupy a separate ground layer. A floor can remain underneath a wall or doorway. To replace a wall with a door, select wall or door, remove the old structure, then place the door. Right-click with floor selected removes only the floor.

Neighbor masks use north=1, east=2, south=4, west=8. Walls and doors share a connection group, including open doors. Walls automatically form horizontal runs, full vertical runs, L corners, T junctions and crosses. R chooses a vertical isolated wall; once neighbors are present their connections determine the wall shape. Doorways automatically face along a north/south run when there are no east/west neighbors, and R can prefer the side-facing orientation. Placing again on an existing piece of the same kind applies the selected orientation. Saved layouts retain that preference; existing saves without it use automatic orientation.

Diagonal turns can fill their missing corner automatically. For example, after horizontal walls at `(0,0)`, `(1,0)` and `(2,0)`, place the next wall at `(3,1)`: placement also adds the corner wall at `(3,0)`. You can continue vertically in column 3. The same rule works at mirrored horizontal endpoints and at vertical endpoints turning horizontally. A single isolated wall uses the R orientation preference to resolve the intended turn; a run supplies that direction automatically. Unrelated diagonal isolated walls do not join. The target, generated corner and all affected footprints are validated together, including terrain, scenery and actors. A blocked corner rejects the whole placement. The preview shows both cells. Generated corners clear foliage and save as normal wall elements; remove one using the existing right-click control.

Floors also join the ground beneath adjacent wall/door cells. A floor next to a structure automatically supplies its wooden foundation, including diagonal room corners. This derived foundation is not a separate saved placement. Floors and foundations share border masks, so their common seams are open instead of showing grass gaps or separate full borders. Beneath boundary walls, cached coverage shapes clip the floor to the timber footprint and the adjacent interior floor. This prevents an extra strip of flooring outside the wall; standalone floor tiles retain their complete texture and border. Removing the nearby floor or structure recalculates these joins. Isolated floors retain all four borders. Concave corners receive a small border cap when the diagonal ground cell is missing.

All wall faces are wooden boards with thick timber caps; there is no plaster. Top surfaces use a 16-pixel depth on the 48-pixel grid, and wall/door height is 72 design pixels. An isolated door keeps its original 38-pixel clear opening. When connected to walls, its frame borrows 8 design pixels from each adjoining wall along the door axis. With walls on both sides the frame is 64 pixels wide and the clear opening is about 51 pixels, giving the player's 32-pixel collision box more room. The neighboring wall art and collision give up the same strips; open-door jambs, floor coverage, contact shadows and closing checks use the wider doorway. The same rule applies to side-facing doors. Neighbor changes update the width automatically, and isolated doors remain complete. Exposed ends remain complete, while connected caps and wall faces suppress shared vertical end borders. The wall cap is a plain timber strip with a consistent dark rim and a continuous border on its exposed edges. Borders are drawn along exposed boundary segments and run uninterrupted across tile seams, including the matching door header. There are no per-tile wood-grain stripes across the cap.

Walls use the full horizontal/vertical/corner footprint of their timber caps for collision. Closed doors block their doorway footprint; open doors retain collision only on their jambs, leaving the passage clear. New or rotated pieces and changed neighboring joins cannot add collision on an actor. They join the existing entity depth sort so characters can walk behind them. Open doors retain a frame and a visible opening; side-facing open doors use a cutaway leaf. Movement, pathfinding, terrain-aware projectile checks and spawn checks share the building collision query through `TileManager`. Placing any floor, wall or door clears vegetation on its tile: meadow grass, trees, bushes, flowers, mushrooms, stumps, twigs and legacy interactive trees/trunks. Low plants overlapping the occupied ground tile are cleared too; neighboring trees are judged by their roots/trunks rather than their canopy. Non-vegetation obstacles such as rocks remain placement blockers. The reusable art and original authoring files are retained. Cleared tile records are saved with the building layout and remain cleared even after removing the building. Loading an older layout also clears vegetation from its occupied cells. Random slimes do not spawn on building cells.

A soft contact shadow extends about 12 design pixels from the room-facing wall base onto adjacent flooring and the clipped foundation. It is clipped to the floor coverage and excludes the wall footprint, so it does not create dark strips on the exterior terrain. Closed doors contribute their leaf footprint; open doors retain only jamb shadows. Footprints are combined before shading so junctions do not get overlapping dark seams. Shadow tiles rebuild only after layout edits, door changes or loading, and draw beneath entities.

Placement refuses map boundaries, water/solid terrain, real cliff or stair edges, existing non-vegetation blocking scenery, reserved authored inn space and actors in a new collision footprint. Moving, menus, chat, dialogue and death retain their existing controls; build clicks do not attack and build number keys do not cast skills. A green/red outline and ghost show the selected placement. Buildings are not placed inside the inn or well dungeon.

## Saving

Use `/savebuild` explicitly to keep both the layout and cleared vegetation; F5 remains character saving. Saves are world-wide and shared between local characters using that world. Files live under `maps/buildings/`, with an authored-world file or a procedural filename containing the seed and a stable configuration fingerprint. Different procedural settings use separate building files. Saves use a temporary file and atomic replacement where supported, and load on startup. Source checkout files use the project's normal map path; installed applications use their existing writable application data path. Bundled map resources provide a fallback.

`/regenworld` loads the target world's saved layout. Save first if you want to keep edits in the current world. Existing character saves do not embed buildings. The first version does not automatically save or warn when leaving with unsaved edits.

## Artwork

`res/building/{floor,wall,door,door-open}.png` are the runtime atlases. Artwork exports at double resolution after 4× supersampling and is scaled once at load time. Floors have sixteen 48×48 design cells. Each structure atlas has 32 cells of 48×120 design pixels: sixteen neighbor masks for each orientation preference. Runtime sprites are anchored at the bottom of their 48×48 world cell; the extra 72 pixels keep their full raised surfaces inside the image.

The runtime vector redraw uses AstralCore's muted timber and iron palette with simple cel shading. `assets/reference/building/astrified-wooden-interior.png` preserves the earlier study; `all-wood-interior-study.png` preserves the revised all-wood direction. Exact wall footprints, height and thickness live in `BuildGeometry`, shared by the artwork exporter and collision code. `GenerateBuildTextures` exports the PNGs, and `BuildTextures` loads and caches their scaled variants.

Artwork export only (does not launch the game):

```sh
mkdir -p /tmp/astral-building-art
javac -d /tmp/astral-building-art src/art/BuildGeometry.java src/art/PaletteOutline.java src/art/GenerateBuildTextures.java
java -Djava.awt.headless=true -cp /tmp/astral-building-art art.GenerateBuildTextures
```

No tests, gameplay checks, smoke checks or package verification were run for this change, following the project's approval requirement. Runtime placement, collision, persistence and appearance still require approved gameplay verification.

## Standalone Builder and blueprints

[The JavaFX Builder](../tools/building-maker/README.md) authors reusable local-coordinate presets independently of the running game. Run `mvn -f tools/building-maker/pom.xml compile javafx:run` with JDK 21. It uses standard JavaFX controls and the shared building element catalog, connection planner, geometry, atlases, foundation clipping, and indoor shadows. The game continues to render through Swing; JavaFX is only a tool dependency.

Blueprints live in `maps/blueprints/`; per-world placed building saves remain in `maps/buildings/`. Use `/blueprint <id> <anchor-col> <anchor-row>` to place a complete preset in the single-player overworld and `/savebuild` to keep it. Placement reserves the full blueprint plot, rejects nonlevel/blocked/occupied plots before making changes, and clears vegetation from that plot. `sample_cottage.json` is an editable example. No automatic village placement is enabled yet.

`res/building/elements.json` supplies IDs and atlas references to both applications. New materials with current floor/wall/door behavior appear in the editor palette automatically and are selectable with `/build <element-id>`. Existing world files containing only `kind` remain readable; newer saves additionally preserve `element` IDs. New behavioral shapes require extending shared building code. See the Builder guide for the blueprint API and extension details.

## Ground tone transitions and connected doors

Grass shades and ordinary grass/path/cobblestone boundaries are decorative transitions. Their palette-level indices do not restrict construction. `TileManager.buildingElevation` uses height only for terrace/rim/ledge/stair terrain; stairs themselves remain unavailable. Blueprint placement uses this same distinction instead of requiring identical palette levels across the whole blueprint rectangle. Existing water, map limits, physical cliffs and scenery restrictions remain.

Connected door expansion is calculated from the current neighboring wall elements in `BuildingLayout`. `BuildTextures` caches extended door images, and `BuildingRenderer` trims adjoining wall pixels and draws the wider frame. Game movement and placement validation use the same expanded footprints. This is shared by the standalone JavaFX Builder and in-game presets; no blueprint format migration is required. The atlas cells and existing resource filenames remain unchanged.

## Materials, stairs and furnished blueprints

The catalog now includes the following additions. Mixed floor materials retain complete exposed borders and join through the same ground layer; wall materials retain horizontal, vertical, corner, T and cross variants.

| Category | Element IDs |
| --- | --- |
| Cobblestone | `cobblestone_floor`, `cobblestone_wall` |
| Reinforced timber | `reinforced_floor`, `reinforced_wall` |
| Concrete | `concrete_floor`, `concrete_wall` |
| Tiled stairs | `stairs_wood`, `stairs_stone`, `stairs_concrete` |
| Beds | `bed_blue`, `bed_coral`, `bed_sage` |
| Bookshelves | `bookshelf_full`, `bookshelf_sparse`, `bookshelf_empty` |
| Tables | `table_empty`, `table_feast`, `table_dining`, `table_map` |
| Rugs | `rug_coral`, `rug_blue`, `rug_sage` |
| Hearth and lighting | `fireplace`, `wall_lamp` |
| Other interiors | `potted_plant`, `bar_counter`, `bar_shelf_empty`, `bar_shelf_full` |

`/build <id>` selects any material or furnishing, and `/list` shows the IDs. Walls, floors and stairs snap to cells. Furniture is placed by a single left click at the cursor's exact world position. Hold/drag painting remains available for tiles. R mirrors furniture horizontally; right click removes a whole furnishing under the cursor while a furnishing is selected. Rugs render over the floor and beneath other objects, without collision. Beds, tables, shelves, plants, the counter and fireplace have ground footprints; wall lamps are nonblocking and require a supporting wall. Door closing and new wall connections respect furniture footprints. Original wood elements remain available.

Stairs use the ground tile layer, remove shared side rails when adjoining the same stair material, and remain walkable. They are visual stair modules in this single-level building system; they do not create a second floor, teleport destination or new terrain elevation.

Fireplaces and the direct-backplate wall lamp contribute warm lights to the existing game lightmap at dusk/night. The lamp has no hanging arm or hook. Fire art is a static frame; the light has a subtle flicker. The bar counter contains only its cloth runner.

The JavaFX Builder has **Tiles** and **Furnishings** tabs and keeps standard JavaFX controls. Furnishings use free placement with previews, footprint limits, wall support checks, undo/redo and horizontal mirroring. The Builder preview uses the same sprite resources and render ordering as the game; warm world lighting is supplied by the game rather than simulated by the editor.

Blueprint version 2 adds a separate `decorations` array. Each record saves a stable ID, catalog element, pixel-precise local x/y coordinates in 48-unit design space, and `flipped`. The entrance anchor translates both the tiles and furniture into the world. World building saves also use version 2; readers continue accepting version 1 files. Reusing a blueprint creates new decoration IDs, so repeated settlement placements do not share identities.

Open `maps/blueprints/interior_cottage_example.json` or `interior_tavern_example.json` in the Builder for furnished examples. Place them in-game using `/blueprint interior_cottage_example <col> <row>` or `/blueprint interior_tavern_example <col> <row>`, then `/savebuild` to persist the complete interior. Layout validation and required image loads happen before the placement mutates the world.

Runtime materials live in `res/building/` and individual furniture PNGs in `res/building/props/`. `props.json` defines display sizes, foot anchors, collision and lighting. The selected atlas and contact sheet are retained under `assets/reference/interior/`. `GenerateInteriorTextures` imports all nineteen furnishings directly from `vector-interior-source.png` in its five-column, four-row order. It trims loose alpha flecks outside each silhouette and smoothly scales the complete sprites to twice their catalog design dimensions. The supplied designs and colors are preserved, without palette remapping or added geometry. `GenerateBuildTextures` exports tile materials separately. Runtime code loads the exported PNGs; artwork export is offline.

Artwork export:

```sh
javac -d /tmp/astral-building-art src/art/BuildGeometry.java src/art/PaletteOutline.java src/art/GenerateBuildTextures.java src/main/MiniJson.java src/art/GenerateInteriorTextures.java
java -Djava.awt.headless=true -cp /tmp/astral-building-art art.GenerateBuildTextures
java -Djava.awt.headless=true -cp /tmp/astral-building-art art.GenerateInteriorTextures
```

The game and Builder have not been compiled or launched, and no tests, smoke checks, benchmarks or package verification have been run for this change. The artwork exporters were run to produce the runtime PNGs, and the exported art was reviewed directly. Runtime behavior and blueprint round-tripping still require approved verification.

### Furniture atlas source

All nineteen furnishings use the selected minimalist furniture atlas directly. IDs, PNG filenames, dimensions, anchors, collision footprints and blueprint data remain compatible. Both applications load these same sprites; restart the game or Builder to reload cached artwork.

### Furniture rotation and revised props

Press R before placing a bed or rug to turn it clockwise in 90-degree steps in either application. The Builder also has a Rotate furnishing button; its mirror checkbox remains independent. Other furnishings retain mirroring. Decoration JSON saves `rotation` as quarter turns (0–3); older records default to zero. Rendering, collision, placement limits, erase selection and blueprint translation use the same transform.

The bar counter is 216 design units wide (50% longer), with its collision footprint expanded to match. The lamp imports `assets/reference/interior/wall-lamp-front-source.png`: a symmetric front-facing lantern without its wooden mounting plate. Other sprites continue using the selected atlas.

The updated game code and Builder were compiled. Gameplay and automated tests were not run.

Tables no longer rotate; existing table records with rotation load in their standard orientation. Horizontal bed orientations use dedicated `bed_*_horizontal.png` artwork (96×72 design units), with upright posts, legs and side rails, rather than turning the original sprite sideways. They use a matching horizontal collision footprint; the opposite horizontal direction mirrors this artwork. `GenerateInteriorTextures` exports the three variants from `assets/reference/interior/horizontal-beds-source.png`.

### Palette outlines

Building tile exports do not have added outlines. The shared renderer unions the visible floors and structures, then draws one thin inward outline around that combined silhouette in dark timber or muted stone shades. Individual tile boundaries receive no outline. Beds (both orientations), bookshelves, bar shelves and the wall lamp have matching palette outlines. `PaletteOutline` runs during offline export, preserving image dimensions, alpha, anchors and collision footprints. Re-exporting starts from the original artwork so strokes do not accumulate.
