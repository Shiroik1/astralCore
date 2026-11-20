# Wooden palisades and gates

Palisades use the existing construction layer, inventory transactions, directional interactions, spatial collision queries, chunk render caches and version-2 building persistence. They are a separate connection family from house walls.

## Recipes

| Item | Ingredients | Crafting level | Station | XP |
| --- | --- | --- | --- | --- |
| Rope ×1 | Fiber ×3 | 3 | Hand | 4 |
| Wooden Palisade ×1 | Logs ×3, Rope ×1 | 6 | Workbench | 12 |
| Wooden Gate ×1 | Logs ×6, Rope ×2 | 8 | Workbench | 24 |

Select the crafted item through the normal inventory/hotbar or crafting Place action. R changes orientation. Right-click in construction mode removes a section and refunds its component if inventory space is available. Development diagnostics (`astral.dev`) do not bypass item costs. One gate item places both halves; removing either half refunds one gate.

## Connections, artwork and collision

`BuildingLayout` uses N/E/S/W bits 1/2/4/8 and filters neighbors by palisade family. Existing global tile coordinates supply neighbors across chunk boundaries. Placement follows existing lowland, water, cliff, scenery, memorial and actor restrictions. The game currently limits construction to the single-player overworld.

`PalisadeArt` creates cached Java2D log sprites with pointed lighter tips, muted wood variations and two rope bands. Vertical variants place upright logs along ground depth rather than rotating horizontal artwork. The three-tile canvas accommodates upward overhang and bottom anchoring. The collision footprint is one tile per section, independent of artwork. Existing world-colliding projectiles use the same collision queries.

Gate IDs `palisade_gate` and `palisade_gate_tail` form one entrance, horizontally or vertically. They operate together from either adjacent interaction tile. Opening and closing take 0.22 seconds with a blend between cached door states. Collision remains closed during transitions; opening clears it at completion. Closing is rejected if either tile contains a player, enemy, NPC or solid furnishing. No combat effects or safe zones are created.

## Persistence and authoring

Stable element IDs, orientation and settled gate state use the existing building save format; old building saves remain readable. Saving during a transition stores its intended settled state. Restore rejects orphaned or contradictory gate pairs. Chunk cache eviction retains authoritative structure records.

BuildingMaker uses the shared palette, rendering, footprint and two-tile placement plan. It hides the internal tail from its palette, toggles both halves together, removes pairs together and rejects incomplete pairs on blueprint validation/export. Its Maven source allowlist includes the shared palisade art.

`PalisadePerimeter.plan(boundary, entrances)` accepts irregular or partial boundary cells and explicitly oriented entrances, returning a shared BuildingLayout. Entrances are mandatory and overlapping gate footprints are rejected. A settlement planner can wrap this in BuildingBlueprint and call the existing `BuildSystem.placeBlueprint`, which validates the complete site before applying it. Full settlement planning, road connectivity and essential-building reachability remain the future generator's responsibility; no new village generator or protection zone was introduced.

## Changed files

- `res/crafting/recipes.json`, `res/building/elements.json`
- `src/object/ItemType.java`
- `src/crafting/CraftingManager.java`, `src/crafting/CraftingArt.java`
- `src/art/BuildTextures.java`, `src/art/PalisadeArt.java`
- `src/building/BuildingLayout.java`, `src/building/BuildingRenderer.java`, `src/building/PalisadePerimeter.java`
- `src/world/building/BuildSystem.java`
- `tools/building-maker/src/main/java/builder/BuilderApp.java`, `tools/building-maker/pom.xml`

## Validation and limits

Game compilation passed. Focused checks covered all 16 neighbor masks, ordinary-wall separation, both gate orientations, paired state/removal and sprite cache reuse (46 checks). A headless game-state harness passed 10 checks for Rope crafting/level/material deduction, single gate-item consumption, both halves' collision, opening, blocked closing, successful closing and structure save round trips. Generated sprite previews were inspected.

No live multiplayer, gameplay traversal or frame-time benchmark was performed. No FPS improvement or stable-60-FPS claim is made. No new image files are required: artwork is generated once into cached sprites. Gate animation is a short crossfade, not articulated swinging leaves. Optional foreground fade is not implemented. Full village/road reachability testing awaits the settlement generator.
