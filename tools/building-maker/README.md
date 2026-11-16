# AstralCore Builder

A separate JavaFX building maker with standard JavaFX controls and no custom CSS. It does not start the game, a game panel, a game loop, or a network session. Its Maven project compiles only the editor and the small shared building/art/JSON classes.

## Start

Use **JDK 21** and Maven. From the AstralCore repository root:

```sh
mvn -f tools/building-maker/pom.xml compile javafx:run
```

Maven downloads JavaFX and build plugins on the first run, so that run needs internet access. No separate JavaFX SDK or VM module-path configuration is needed with this command. This command compiles and launches the editor; it does not invoke the Maven test lifecycle.

In IntelliJ, open `tools/building-maker/pom.xml` as a Maven project (a separate window is convenient), select JDK 21 for the project and Maven runner, and run the Maven goals `compile javafx:run`. Do not add JavaFX dependencies to the Swing game's module.

The editor finds the project by walking upward from its working directory. If launching from elsewhere, set the JVM property `astral.projectDir` to the absolute AstralCore repository path. Keep the tool in the repository so it can compile shared source files and read the current assets.

## Make your first preset

1. Click **Open…** and choose `maps/blueprints/sample_cottage.json`, or start a new building.
2. Enter an ID such as `small_inn`, a display name, and a category such as `house`, `inn`, `shop`, or `workshop`. Categories are free text for future generator selection.
3. Set the width and height, then click **Apply size**. The maximum is 64 × 64 tiles. Shrinking refuses to discard placed elements; erase those first. The grid is the entire plot that placement will reserve, including its empty margins.
4. Select **Wooden floor** and **Rectangle**. Drag between two grid cells to fill a floor area.
5. Select **Wooden wall** and **Rectangle**. Drag around the floor area to make a wall outline. A drag is one undo step. Leave space for the entrance or erase one wall cell afterward.
6. Select **Wooden door**, erase the wall at the entrance with right-click, and paint the door there. Use **Prefer vertical** for a side-facing door. Floors remain on their separate layer when you remove a wall or door.
7. Choose **Set anchor** and click the entrance/road connection tile. The blue cross marks it. The anchor may be an empty cell just outside the door, but must stay inside the plot.
8. Click **Save As…** and save in `maps/blueprints/` using the same filename as the ID, for example `small_inn.json`.

Existing game world saves in `maps/buildings/` are different from reusable blueprints. Opening them as blueprints is deliberately rejected.

## Controls

| Control | Action |
| --- | --- |
| Paint + left drag | Place the selected element; fast drags interpolate missing cells |
| Right drag / click | Erase the selected tile layer; click to remove a furnishing |
| Erase selected layer tool | Erase with left drag |
| Rectangle | Fill floors; outline walls/doors; reject the entire rectangle if a placement conflicts |
| R | Toggle vertical preference for tiles, or mirror a furnishing |
| 1 / 2 / 3 | Select the default floor / wall / door |
| Toggle door tool | Click an existing door to change its saved open/closed state |
| Door starts open | Choose the state for new door placements |
| Set anchor tool | Click the tile to align with a world coordinate later |
| Zoom slider / scrollbars | Zoom and navigate the plot |
| Grid / Floors / Walls and doors | View controls; visibility does not change saved elements |
| Ctrl/Command S | Save |
| Ctrl/Command Shift S | Save As |
| Ctrl/Command O / N | Open / New |
| Ctrl/Command Z | Undo one stroke, rectangle, resize, anchor move, or door toggle |
| Ctrl/Command Shift Z or Y | Redo |

Undo history retains up to 100 operations. Text fields retain their normal text undo behavior; blueprint metadata changes are not in the tile undo stack. New, Open, and window close offer Save / Discard / Cancel for unsaved edits. Saving uses temporary files and atomic replacement where supported. Keep filenames aligned with IDs to use the game command.

## Use a preset in the game

In the single-player overworld:

```text
/blueprint sample_cottage 40 40
/savebuild
```

The first command aligns the saved anchor with world tile `(40,40)`. It refuses water, elevation changes, blocked scenery, other buildings, reserved inn space, and actors in the plot or affected wall connections. The complete placement is checked before it edits the world. Successful placement clears vegetation across the plot. The second command saves the resulting world layout using the existing building persistence system.

A blueprint is read from `maps/blueprints/<id>.json` in the source checkout, with a bundled-resource fallback. Installed games use their writable map directory and then bundled resources, following existing `AppPaths` behavior. This editor defaults to source-checkout authoring, not installed-game data directories.

The sample is an authoring example; it is not automatically spawned in generated worlds. Automatic settlement layout, road routing, and preset selection are the next generation step.

## Shared code and future elements

The editor and game share:

- `src/building/BuildElements.java` and `res/building/elements.json`: element IDs, labels, shape behavior, and atlas references.
- `src/building/BuildingLayout.java`: independent layers, neighbor masks, and automatic diagonal corners.
- `src/building/BuildingRenderer.java`: foundations, clipped floor coverage, border corners, and indoor shadows.
- `src/art/BuildGeometry.java` and `BuildTextures.java`: footprints, dimensions, and atlas slicing.
- `src/building/BuildingBlueprint.java`: portable format, validation, atomic saving, and anchor-based placement coordinates.

To add another material using an existing behavior, export its atlases to `res/building/`, add a catalog entry with a unique stable ID and `shape` of `FLOOR`, `WALL`, `DOOR`, or `STAIRS`, then restart the editor/game. The palette automatically lists it; blueprints and game saves retain its ID. The game also accepts `/build <element-id>`; `/list` lists available IDs. Never rename a published ID without migrating saved files.

Floor atlases contain 16 cells at 96 × 96 pixels. Structure atlases contain 32 cells at 96 × 240 pixels (16 masks for each orientation). Doors need both `atlas` and `openAtlas`. Walls and doors share the current wall geometry/connection group, while stair tiles use the ground layer with their own same-material connections. Freely positioned furniture uses `PropCatalog` and `res/building/props.json`, with individual PNGs and per-object footprints. A new behavior such as roofs, windows or true multi-floor transitions needs a shared model/renderer extension so both applications can use it.

## Generation integration

Blueprint version 2 files store version, ID, display name, category, plot size, anchor, element entries using local tile coordinates, and a separate decorations array with exact local positions and mirror states. Version 1 files remain readable. Masks, generated foundations, and shadows are derived rather than serialized. Different game tile sizes use the same blueprint coordinates. Door state and orientation preferences are preserved.

For a data-only settlement planner:

```java
BuildingBlueprint preset = BuildingBlueprint.read(path);
List<BuildingBlueprint.Placed> placements = preset.placedAt(roadCol, roadRow);
```

Reserve the complete `width × height` plot, translated by `roadCol - anchor.col()` and `roadRow - anchor.row()`, then consume those elements after terrain and scenery generation. The metadata category can select houses versus shops; a seeded random choice can select variants. No Swing or JavaFX state is required to read blueprints.

For the current live world, call this on the game thread:

```java
String error = gp.buildings.placeBlueprint(preset, roadCol, roadRow);
if (error != null) {
    // Reject this plot and choose another candidate.
}
```

`null` means success. This API uses current terrain, scenery, actor, and neighboring-connection checks and marks the world building layout dirty. Call the existing building save flow when you want to persist it. It requires the single-player overworld with a local player, so generation before player creation should use the data-only API and integrate the placements at the appropriate initialization stage.

## Verification status

Code and asset-reference review only. Compilation, tests, editor launch, gameplay checks, and package verification have not been run; project instructions require owner approval before runtime/test verification.

Connected doors automatically borrow a small strip from adjacent walls to form a wider opening. The frame, neighboring wall cutouts, floor shadows and in-game collision use the shared building rules; isolated doors keep their complete original frame.

## Furnishings and material variants

Use **Tiles** for the wood, cobblestone, reinforced timber, concrete and stair materials. Use **Furnishings** for beds, bookcases, tables, rugs, the fireplace, direct-mounted wall lamp, plant, counter and bar shelves. Click once to place a furnishing at a free position; right click removes the object under the cursor, R turns beds and rugs, and the mirror checkbox flips furnishings. Furnishings are not stamped into the tile grid. Tile stairs remain on the ground layer and connect only to adjoining stairs of the same material.

The object preview checks its footprint, keeps the complete sprite within the editor canvas, and prevents solid furnishings overlapping walls/doors or each other. Lamps require a wall behind their mounting position. Rugs may sit under furniture. Undo, redo, resizing, save and open retain the decoration layer. Standard JavaFX tabs and controls are used without CSS styling.

Saved blueprints use version 2 with a `decorations` array and still read version 1 presets. The game translates those object positions together with the entrance anchor. Try `maps/blueprints/interior_cottage_example.json` and `interior_tavern_example.json`; see [building system](../../docs/building-system.md) for element IDs and in-game placement commands.

Beds and rugs: press **R** or click **Rotate furnishing (R)** before placement for 90° turns. Rotation is saved with the blueprint. The mirror checkbox works independently.
