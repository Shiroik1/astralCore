# Placement feedback and interaction targets

Directional interaction reach and tie-breaking now follow [Phase 3](phase3-interactions-encounters.md); its one-tile resolver supersedes the earlier proximity/alignment description below. The outline cache and placement effects remain the same.

Successful game placements now play a render-only pop and a small puff of the existing smoke. The world interaction highlight is one selected actionable target, rather than every nearby harvestable object. The Java2D engine, tile size, inventory transactions and saved building format are unchanged.

## Placement events

`BuildSystem` notifies `PlacementEffectManager` only after placement validation and component reservation succeed. Newly inserted/replaced floor and structure cells receive their own animation key; changing a neighboring wall's connection or orientation does not animate that neighbor. Furniture, ground furnishings/rugs and crafting stations use their stable object IDs. Blueprint placement emits events for its successfully placed components and decorations.

Drawing applies a temporary Graphics2D transform around the floor's visual center or the object's bottom anchor. Collision, saved coordinates, footprints, inventory and autotile selection are never scaled. Existing cached sprites and interpolation settings remain in use; no image loading or regenerated animation frames are needed for the bounce. Other tiles retain their normal drawing coordinates.

Timing uses simulation seconds, updated by the existing fixed 60 Hz game clock, independently of rendering FPS. Pausing the simulation pauses the effects. The default 240 ms smoothstep animation passes through 0.75×, 1.12×, 0.96× and 1× at 0/80/160/240 ms, with an optional 2-pixel lift. Expired animations are removed automatically. There are at most 256 active animations across all kinds.

The repository's existing smoke artwork is the resident vector silhouette in `Particle.Style.SMOKE` (shared with existing dust particles), rather than a separate smoke PNG. Placement reuses that shape, shading, fading, world coordinates, shared particle list and depth sorting. No replacement artwork was generated. Four puffs are emitted for tiles and seven for furniture/stations, with 250–450 ms lifetimes and subtle upward/outward motion. Floor puffs are distributed near its edges; wall/furniture puffs appear near the base. Placement smoke is capped at 96 particles, within the existing 180-particle overall limit.

Animations and placement smoke are transient. Character/world restoration clears them; neither is serialized into building or crafting-world data. Saving during a bounce stores the fully placed logical object normally.

## Target selection and outlines

`InteractionTargetResolver` collects candidates from crafting resources/stations, placed doors, existing interactable scenery, loose item entities and NPCs. Each collector retains its gameplay eligibility and range checks. Resources share the same tool-category validation with gathering: no axe means no actionable tree outline; a pickaxe cannot target grass or trees. Grass targets correspond to the existing harvestable field cell and use its animated tuft frames. Exhausted nodes are excluded.

Candidates must face the player (forward alignment at least 0.25). Alignment dominates selection, followed by distance, existing priority and stable ID tie-breaking. The previous target remains selected unless a challenger improves the score by more than 0.14. Interaction execution resolves through the same selected candidate. E opens the selected station/door or gathers the selected resource; aimed harvesting only acts on the selected resource. F recovers the selected station. Automatic item pickup and the existing combat hover system remain separate existing behaviors.

Outlines disappear when the target is removed, depleted, out of range, behind the player, no longer eligible for the current tool, or world interaction is disabled by a menu/dialogue/chat/pause state. Facing is checked again when drawing, so a turn or menu change does not leave a stale world outline. Blocked door closing remains a consumed interaction, preventing E from becoming a weapon swing.

`InteractionOutline` caches alpha-based warm-white silhouettes, ignores faint baked ground shadows and uses precomputed stroke offsets. Trees use their actual sway frame and padding; grass uses its current bend/variant frame; pickups and stations use their displayed sprite. Door outlines use the assembled cached alpha coverage, including open frames, borrowed edge strips and wall trims. No rectangular image-bound outlines are drawn. Normal sprites are drawn over the outline.

## Tuning

| Setting | File / symbol | Default |
|---|---|---|
| Bounce duration | `src/visual/PlacementAnimation.java`: `DURATION` | 0.240 seconds |
| Initial size / bounce strength / settling | `INITIAL_SCALE`, `OVERSHOOT`, `SETTLE` | 0.75, 1.12, 0.96 |
| Vertical lift | `MAX_LIFT` | 2 design pixels |
| Tile and large-object smoke quantities | `src/visual/PlacementEffectManager.java`: `SMALL_PUFFS`, `LARGE_PUFFS` | 4, 7 |
| Maximum active bounces | `MAX_ANIMATIONS` | 256 |
| Smoke lifetime | `src/entity/Particle.java`: `MIN_PLACEMENT_LIFETIME_TICKS`, `MAX_PLACEMENT_LIFETIME_TICKS` | 15–27 ticks at 60 Hz |
| Smoke cap | `MAX_PLACEMENT_PARTICLES` | 96 |
| Smoke size, drift, edge spread | `Particle.placementSmoke` | 9–14 size; subtle outward/upward velocity |
| Outline color / thickness | `src/art/InteractionOutline.java`: `COLOR`, `THICKNESS` | `#F5F4EC`, 2 logical screen pixels |
| Facing cone / target hysteresis | `src/interaction/InteractionTargetResolver.java`: `MIN_ALIGNMENT`, `SWITCH_ADVANTAGE` | 0.25, 0.14 |

Recompile after editing these constants. Thickness is in the game's logical screen coordinates and follows the existing display scaling, including pixel-buffer mode.

## Files

New classes:

- `src/visual/PlacementAnimation.java`: easing curve and expiry.
- `src/visual/PlacementEffectManager.java`: bounded event state and render transforms.
- `src/interaction/InteractionTargetResolver.java`: shared candidate selection and interaction dispatch.

Extended existing/current work:

- `src/art/InteractionOutline.java`: warm-white cached outlines and configurable stroke offsets.
- `src/entity/Particle.java`: placement emitters reusing existing smoke and caps.
- `src/main/Gamepanel.java`: simulation clock, target refresh and selected highlights.
- `src/world/building/BuildSystem.java`: successful placement events, per-piece rendering, selected doors and restore cleanup.
- `src/building/BuildingRenderer.java`: outlines from cached assembled door coverage.
- `src/crafting/CraftingManager.java`: shared tool validation/targets, station events and render transforms.
- `src/entity/Entity.java`, `src/world/WorldObject.java`, `src/main/CollisionChecker.java`: selected sprite outlines and shared scenery interaction target.
- `tools/building-maker/pom.xml`: includes the shared outline utility in the standalone tool build.
- `docs/crafting-system.md`, README and this guide document the revised behavior. Previous inventory sizing and tall-grass harvesting changes are preserved.

## Verification and limits

All Java sources compile. Temporary headless checks covered curve timing, one selected target, stable repeated selection, facing changes, tool gates, UI suppression, sprite/outline camera offsets, animated field-grass rendering, Fiber collection and depletion reload, unchanged neighbor transforms, bounded rapid placement events, particle expiry, real floor/wall/door/furniture/workbench placements, invalid placement with no VFX, inventory consumption, unchanged collision and coordinates, door interaction and restoration without transient effects. These checks do not restore the removed permanent test suite.

Manual live playtesting, subjective animation feel, pixel-buffer/display scaling and multiplayer sessions still need review. Actual construction/crafting continues to follow the existing single-player restriction; effects do not introduce a multiplayer placement protocol. Existing combat hover outlines are retained when no interaction target is selected. This change adds no new resource, recipe, UI system or gameplay mechanic.
