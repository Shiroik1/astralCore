# Crafting and gathering, levels 1–10

The first survival crafting implementation uses the existing JPanel loop, Java2D UI, Entity inventory, equipment slot, projectiles, procedural scenery and tile building system. New characters start with an empty bag, Crafting Level 1 and placeholder fists/no shield. Existing character inventory is preserved when loaded. Clothing/appearance remains unchanged.

## Controls and progression

- **E** (also I / K) opens/closes Inventory & Crafting; **Escape** closes it or cancels station placement.
- **Up/Down** selects a recipe, including locked recipes below the visible list.
- **Enter** or **Craft** completes the selected recipe. Ingredient counts, station, output count, level requirement and XP bar are shown. Unavailable crafts are rejected without mutation.
- **+ / −** changes batch count after Crafting Level 9 (maximum 99 batches).
- **P / Place** begins placing a crafted station, furniture or component. Click within three tiles for stations. Face the adjacent station footprint and press **F** to reopen the common crafting menu, or **G** to recover it if the bag has space. Existing recipe-proximity validation is unchanged.
- **F** collects Twigs/Stones or works a resource only on the adjacent tile the player faces. **LMB** gathering uses that same directional resource target. Gathering uses an attack animation and cooldown. Equip tools by dragging them into the existing weapon slot.
- Crafted bows consume Wooden Arrows; crafted bows and staffs fire through the existing projectile list and respect terrain, building, scenery and station collision. Crafted melee weapons use existing damage resolution. Each hit advances a separate Melee/Magic/Bow mastery XP counter; complete mastery trees are intentionally absent.
- Placing wooden floor, wall, door or simple bed consumes the corresponding crafted item. Removing it returns that item if inventory space permits. Automatically inserted corner walls cost additional wall items; changing connectivity/orientation does not charge again. Blueprint placements reserve all ingredients before applying anything.

Collect three Twigs and two Stones and make the first Wooden Axe: its 5 base XP plus 20 first-craft bonus reach Level 2 exactly. Chop trees for Logs and cut the existing green, dark or golden tall-grass fields for Fiber. Make weapons and a Workbench; reach Level 3 for Planks/components, Level 4 for Campfire/Charcoal, Level 5 for a Pickaxe, then progress through Chest, Bed, Improved Axe and batch crafting to the Forge and iron recipes at Level 10. All 21 requested recipes are present. No roofs, armor recipes, other professions, durability or survival meters were added.

The [Phase 3 interaction and encounter guide](phase3-interactions-encounters.md) documents footprint/elevation validation and development diagnostics.

## Configuration

Edit [`res/crafting/recipes.json`](../res/crafting/recipes.json):

- `items` defines stable IDs, display names, equipment kinds, image paths, gathering tool category/power and optional placeable type.
- `recipes` contains stable recipe ID, required level, station, ingredient ID/count pairs, output ID/count, ordinary XP and first-time bonus. Existing IDs must not be renamed in saves. New item IDs also need corresponding `ItemType` enum entries with their crafting ID constructor. Existing `ItemType` ordinals were preserved by appending the new entries.
- `xpToNext[level-1]` defines the XP needed to leave that level. The first nine values match the specification. The curve already extends to Level 50; later recipes can be added without changing transaction or UI logic.
- `nodeHardness` configures tree/rock/iron/grass hit points. Gathering power on tools reduces required hits; tool categories enforce the axe/pickaxe/cutting restrictions.

Only completed recipes award crafting XP. First bonuses are claimed once per recipe. Repeated recipes more than two levels below the player award reduced ordinary XP; batch crafting awards the first-time bonus once, not once per batch. Placing/recovering objects awards no crafting XP.

## Resource and station state

Existing scenery trees, large rocks, small loose rocks and twigs become resource sources. Additional loose stones, twigs and iron deposits are deterministically scattered across walkable ground using the world seed. Loose stones render with the existing world rock sprite; the new stone texture is used only for inventory/UI icons. A traversal from spawn chooses a nearby guaranteed starter cache of seven Twigs and five Stones. Background terrain and flowers are untouched. Existing green, dark and golden tall-grass cells supply Fiber; no separate crafting grass texture is spawned. Harvested cells are tracked with stable `tallgrass:col:row` IDs and hidden in the normal grass renderer, so depletion survives reloading without changing the field art. Existing procedural slime spawn protection keeps hostiles away from the initial area; new stations are excluded from later slime spawn locations.

Resource identities use kind/coordinates or generated tile coordinates. Remaining hits and exhaustion are saved, so reinitializing the scenery does not regenerate harvested resources. The current engine does not stream chunks; these same stable identities support scene reinitialization and future chunk reloads. Nodes deliver resources atomically to the existing bag. If the bag cannot hold the reward, the final hit does not exhaust the node: its resources remain available, avoiding untracked/despawning loot or duplication. Loose and mined Stone both use `STONE`.

Workbench, Campfire and Forge share one station proximity check (about 2.5 tiles from their footprint) and recipe UI. Stations consume one inventory item when placed, have collision footprints, render in the usual depth-sorted entity list, and are recoverable. The Wooden Chest is currently a placeable furnishing; a storage UI is not implemented. The Simple Bed uses the existing bed furnishing and rotation. Sleeping is not implemented.

## Inventory, building and persistence

`InventoryTransaction` clones detached stacks, removes all costs, checks all output space/stack limits and commits the whole slot array only on success. Costs, outputs, building refunds, node drops and station recovery use the same path. Item identity uses enum/registry IDs, not translated names. Crafted stacks only combine when their IDs match.

The current character save captures Crafting Level/XP, first bonuses, inventory, equipped tools and the three mastery counters. It also captures a stable crafting-world JSON string (node hits and stations) and the existing building-format JSON. These participate in the existing atomic file/backup save transaction, including autosave and F5. Loading a layout restores it without charging ingredients. Legacy character files default to Crafting Level 1; older building files remain readable. Loading crafting state with a different world seed reports an error instead of silently regenerating depleted nodes; use the same world configuration for that character.

Survival placement is restricted to the requested craftable floor/wall/door/bed. Other existing materials and furnishings remain available in the standalone Builder and in explicit development mode (`-Dastral.dev=true`), which preserves creative placement without inventory costs.

This crafting/world prototype follows the building system's existing **single-player overworld** restriction. It is disabled during network sessions, dungeons, dialogue and inn interiors. Existing network combat/inventory remains in place; crafted equipment and player crafting/mastery values use shared item/player snapshots. Multiplayer world crafting and node/station synchronization need a host-authoritative protocol before enabling this subsystem online.

## Files

New files:

- `src/crafting/RecipeRegistry.java`, `CraftingProgression.java`, `InventoryTransaction.java`: definitions, unlocks/XP and atomic bag changes.
- `src/crafting/CraftingManager.java`: gathering, station placement/recovery, proximity, resource generation/state and component costs.
- `src/crafting/CraftingUI.java`, `CraftingArt.java`, `CraftedBolt.java`: overlay, icons/fallbacks and beginner ranged weapons.
- `src/object/OBJ_crafted.java`: items integrated into the existing Entity/equipment system.
- `src/art/GenerateCraftingTextures.java`, `assets/reference/crafting/crafting-source.png`, `res/crafting/recipes.json` and nine exported sprite PNGs.

Modified existing files:

- `src/main/Gamepanel.java`, `KeyHandler.java`, `UI.java`, `CollisionChecker.java`: input, drawing, updates and station collision.
- `src/entity/Player.java`: empty starting inventory/equipment, crafted equipment compatibility, melee integration and mastery counters.
- `src/object/ItemType.java`, `ItemRegistry.java`: stable item definitions and lookup.
- `src/net/PlayerState.java`, `SnapshotBuilder.java`, `GameClient.java`: shared player progression fields.
- `src/save/CharacterData.java`, `CharacterState.java`, `SaveStore.java`: character and crafting/building-world persistence.
- `src/world/WorldObject.java`, `SlimeSpawner.java`, `src/world/building/BuildSystem.java`: depletion/collision, station-safe spawning, material costs/refunds and snapshot restore.
- This guide, the README and asset guide document the implementation and limitations.

## Artwork

The approved nine-sprite atlas supplies the wooden weapons, Plank, Log, Stone, Workbench and Forge. Simple Bed and building-component icons reuse the existing building art. Missing dedicated artwork uses explicit, small palette-colored geometric placeholders: Twig, Fiber, Wooden Arrows, Charcoal, Iron Ore/Ingot, Pickaxes, Campfire, Chest and iron equipment. Improved Axe currently shares the Wooden Axe image. Equipped attack animations use existing weapon animation foundations rather than a new animation set. These are visual limitations, not broken resource filenames.

Export the approved atlas with:

```sh
javac -d /tmp/astral-craft-art src/art/GenerateCraftingTextures.java
java -Djava.awt.headless=true -cp /tmp/astral-craft-art art.GenerateCraftingTextures
```

## Verification and manual walkthrough

The requested checks were run as temporary headless integration harnesses rather than restoring the previously removed test suite. They covered empty start, initial gathering/tool gates, axe XP, grass/log drops, exhaustion, atomic insufficient/full-inventory handling, station range, Planks, mining, Charcoal, component consumption/refunds, auto-corner planning, blueprint placement/material charges, batch unlock, iron recipes, character snapshots, real character-file save/load and crafting UI rendering. All Java sources compiled. The headless environment lacks an audio output device; existing sound loading printed nonfatal warnings. Live manual playtesting and multiplayer sessions were not performed.

For a manual progression pass, create a **new** character, follow the controls and progression above and check each unlocked recipe at its listed level. Use each station from nearby, then move away and verify its recipes become unavailable. Fill the inventory and try collecting/crafting/recovering a station; no items or node rewards should disappear. Build and erase a room; verify automatic corner walls consume the correct count and removing one returns one component. At Level 9 craft two or more batches; at Level 10 mine Iron Ore and use a Forge to make Ingots and equipment. Save with F5, exit and reload: equipment, inventory, XP/bonus claims, harvested trees, stations and the room must remain. Finally check the existing class combat and dungeon content manually for regressions.

## Gathering and inventory polish

Crafted item images are fitted into a padded 48-pixel inventory canvas with their original aspect ratio; full-resolution station art remains separate. The Workbench is 72 design units wide with a 60×16 ground footprint. Station recipe access and E interaction share a 2.5-tile range measured from the player's body to the footprint, and stations take priority over nearby resource pickups when pressing E. The one selected actionable resource/station receives a warm-white silhouette outline, based on facing, range and tool requirements; grass and trees use their current animated frames. Flowers do not become harvestable. This follow-up was compiled and reviewed in code; gameplay checks were not run.

See [Placement feedback and interaction targets](placement-feedback.md) for bounce/smoke effects, target selection, tuning constants and verification.
