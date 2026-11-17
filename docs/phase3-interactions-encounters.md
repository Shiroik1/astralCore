# Phase 3 — Directional interactions and wilderness encounters

This update reuses the existing JPanel/Java2D loop, collision, terrain/elevation data, resource transactions, outline cache and slime AI. It does not change terrain generation, cliff art, recipes, resource yields, combat hitboxes or boss attacks.

## Directional interactions

Press **F** while facing the immediately adjacent tile. **G** recovers a station through the same target. Left-click gathering uses the same resolved resource; clicks without a valid resource continue through existing combat/projectile handling. Interaction targeting does not shorten sword hitboxes, arrows, spells or slime attacks.

`InteractionValidator` determines the player tile from the collision rectangle's horizontal center and bottom/feet pixel. It adds one cardinal direction offset. Candidate footprints must intersect that one tile. The validator checks playable logical terrain, equal accessible elevation, and a pixel-sampled contact path for terrain, building, station and scenery obstacles. The target's own collision can be touched; unrelated obstructions remain blocking. Sprite projection and canopy pixels never determine range.

`InteractionTargetResolver` supplies both the white outline and the actual action. Resource callbacks still check the existing tool category/power, depletion and cooldown. Drops and loose pickups have priority 4, stations/doors/NPCs priority 3, scenery interactions priority 2, and harvestable resources priority 1. Equal-priority candidates retain the current valid target, with stable IDs breaking other ties. That target remains fixed through authoritative pickup validation, so overlapping items cannot cause an outline/action mismatch.

Automatic collision pickup was removed. Walking across an item no longer collects it; face its adjacent tile and press F. Already-open dialogue retains its existing advance/close controls. Host-side remote-player interactions resolve from that player's own feet and direction rather than the local camera player.

### Footprints

- Trees and large rocks use their actual logical collision/trunk footprint, not their canopy or object center. Any portion touching the adjacent tile can be used.
- Generated loose resources and tall-grass fields use their authored logical tile.
- Placed stations use their existing 60-pixel-wide collision/interaction rectangle, including overhang into a neighboring tile.
- Constructed doors use their logical building cell; existing opening/blocked-closing safety and widened connected-door geometry are retained.
- World scenery buildings use their existing defined door area; other interactive scenery uses its logical collision footprint or anchored tile.
- Dropped entities and NPCs use their existing logical collision rectangle.

**Crafting recipe proximity was deliberately preserved.** Facing a station is required for its F/G world interaction; the combined inventory can still open with E, and recipe availability still uses the existing nearby-station radius and inventory validation. Placement reach is unchanged.

Decorative bushes and furniture without an existing harvest/use callback remain decorative. The update does not invent bush drops, storage, sleeping, or other furniture mechanics. Craftable chests retain their existing interaction behavior; world scenery chests retain their existing loot/state behavior.

## Why wilderness encounters were rare

The inspected Phase 2 director had several interacting constraints:

1. New slimes appeared beyond the visible camera plus a margin. Their normal detection radius is six tiles, but off-screen spawn positions commonly lie 11–14 or more tiles away. They only wandered around their spawn home, so they could remain unseen indefinitely.
2. The player roll gate used `GeneratedWorld.reserved`, which also includes generation-only access corridors. Standing on a safe crossing/route could suppress an otherwise legitimate wilderness roll outside the actual starting safe area.
3. The ordinary global count included far/inactive entities. These could consume the global budget even when the nearby population was empty.
4. A default 15-second base was randomized to 15–30 seconds, followed by a 45% roll and another regional random rejection per candidate. This compounded the first three issues.
5. The local reachability search checked body endpoints only. A thin station obstacle between grid anchors could be missed even though actual substepped movement would stop the slime.

There is no enemy chunk-unload callback removing new arrivals: terrain drawing chunks and entity storage are separate. The previous simulation margin generally covered the default spawn ring. Those systems were retained rather than replaced.

## Encounter corrections

The existing `SlimeSpawner` remains the only wilderness director. It rolls every **10–15 active-play seconds** under the default 15-second world setting. One successful group contains **1–2** ordinary slimes, subject to a nearby cap of **8** and a total bounded ordinary storage budget of 20. A roll has at most **28** candidate attempts for the whole group.

The base probability is **0.60**. The player's current dominant environmental region scales the probability once, never the group size:

| Region | Weight | Default roll probability |
|---|---:|---:|
| Grassland | 1.0 | 60% |
| Forest Grove | 1.3 | 78% |
| Flower Plain | 0.5 | 30% |
| Pond Shore | 1.1 | 66% |
| Golden Meadow | 0.7 | 42% |
| Protected starting area | 0 | No roll |

Candidates use each active player's actual world camera rectangle. The complete cached slime animation canvas, including terrain elevation projection, must remain outside every camera plus a two-tile margin. Physical window scaling does not change the game's logical camera coverage.

One bounded local flood fill is shared by all candidates in a roll. It uses the existing full slime body, collision rules, equal-elevation links, and half-tile swept-body checks between grid anchors. No A* search runs for individual spawn candidates. Water, cliff faces, boundaries, floors/buildings, stations, solid scenery, the starting safe area, nest arena, occupied bodies and unreachable components are rejected with separate reasons.

Generation-only access reservations no longer suppress encounters. The actual starting reservation and eight-tile starting safety radius remain protected. Nest bosses, guardians and summoned minions remain outside the ordinary population cap.

### Approach and lifecycle

A new slime captures one reachable destination near the player's position at spawn time. It investigates for up to 45 active seconds through existing pathfinding and collision-aware non-attacking hops. This target does not move with the player. Within normal sight/detection range it enters the existing Chase behavior, with the same leap windup, attack distance, damage and cooldowns.

Investigation temporarily bypasses the home-distance leash; it never bypasses collision. When the investigation ends or the slime detects the player, its current ground position becomes its normal home, avoiding an immediate return to its distant spawn point. Interrupted non-attacking movement can resume the saved destination.

Investigating arrivals remain simulated even outside the ordinary camera activation margin. A stalled investigation ends after 12 seconds without movement. A failed enemy that remains off-screen can be retired after 30 seconds; ordinary distant retirement remains 60 seconds beyond the expanded distance threshold. Retirement does not award loot, XP or kill credit. Under global storage pressure, inactive off-screen ordinary enemies can release space instead of permanently blocking new nearby encounters. Nest entities are not retired by this logic.

A successful group requires four tiles of travel before another group around that player. Standing still therefore does not fill the area. Kills/boss completion retain a 25-second quiet period. Respawn adds a 20-second grace period; active boss fights block random spawning entirely.

Investigation destination, remaining time and failure state are optional primitive fields in the existing monster snapshot. Existing monsters are restored once through normal save loading; restoration does not invoke a new director spawn. Older payloads without these fields remain ordinary non-investigating slimes. Peers must run the same build because snapshot fields have changed.

## Development tools

Launch with `-Dastral.dev=true`, then enter commands in game chat:

| Command | Effect |
|---|---|
| `/encounters on` | Show the development overlay and temporary spawn markers |
| `/encounters off` | Hide the overlay/markers |
| `/encounters fast` | Three-second checks, 100% probability, normal nearby cap and safety checks |
| `/encounters normal` | Restore normal timers/probability; overlay remains available |
| `/encounters now` | Run an immediate 100% roll; preserve terrain, visibility, safe areas, boss exclusions, grace periods, travel gate and caps |
| `/encounters` | Toggle overlay |

The overlay reports the current region/nearby population, next roll, active boss, last result, attempts, valid candidates, last position/distance and rejection counters. A forced check can legitimately report `RESPAWN_GRACE`, `IDLE_TRAVEL`, `PROTECTED_AREA` or other rejections. It does not bypass validation to claim success. Before any successful spawn it shows “Last spawn: none.”

Optional startup flags `-Dastral.encounters.debug=true` and `-Dastral.encounters.fast=true` require developer mode. Normal release gameplay has no diagnostic overlay or markers.

### Runtime configuration

Defaults live in `src/world/EncounterSpawnConfig.java`. Set JVM properties before startup, for example `-Dastral.encounters.chance=0.5 -Dastral.encounters.nearbyCap=8`. These do not alter deterministic world-generation settings.

| Property suffix after `astral.encounters.` | Default |
|---|---:|
| `intervalSeconds` | Existing world upper interval, normally 15; normal rolls use 2/3 to 1 times this value |
| `chance` | 0.60 |
| `nearbyCap` / `attempts` | 8 / 28 |
| `marginTiles` / `ringTiles` | 2 / 6 |
| `safeTiles` / `travelTiles` | 8 / 4 |
| `killGraceSeconds` / `respawnGraceSeconds` | 25 / 20 |
| `investigateSeconds` / `unreachableSeconds` / `distantSeconds` | 45 / 30 / 60 |
| `grasslandWeight` / `forestWeight` / `flowerWeight` | 1.0 / 1.3 / 0.5 |
| `shoreWeight` / `goldenWeight` | 1.1 / 0.7 |

The existing `astral.world.slimes` remains the ordinary storage-budget upper bound (at most 20) and also bounds nearby counts, including zero to disable ordinary encounters. Inactive far enemies can release their slots even when this budget is customized below 20. The entity array retains its existing 20 ordinary plus eight encounter storage slots. Configured larger physical camera margins automatically expand the ordinary simulation margin; investigating arrivals stay active until their temporary investigation ends.

## Files changed for Phase 3

| File(s) | Phase 3 change |
|---|---|
| `src/interaction/InteractionValidator.java` | New shared feet tile, contact footprint and obstacle/elevation validation |
| `src/interaction/InteractionTargetResolver.java` | Exact adjacent-cell target, stable priorities and authoritative shared action |
| `src/crafting/CraftingManager.java` | Resource/station target collection, harvesting callback validation, collision-owner query |
| `src/world/building/BuildSystem.java` | Door target integration and collision query excluding the target door only |
| `src/world/WorldObject.java` | Defined logical interaction footprint and callback guard |
| `src/entity/Player.java`, `src/main/CollisionChecker.java` | Directional pickup/NPC/scenery routing; remove automatic contact pickup |
| `src/tile/TileManager.java` | Expose existing terrain-only collision separately from building collision; calculations unchanged |
| `src/world/SlimeSpawner.java` | Diagnose, validate, schedule, investigate, cap and retire wilderness encounters; development overlay/markers |
| `src/world/EncounterSpawnConfig.java` | Runtime settings and regional weights |
| `src/world/SpawnDebugInfo.java` | New plain diagnostic results and rejection counters |
| `src/monster/MON_GreenSlime.java` | Temporary investigation using existing non-attacking hops/pathfinding; existing attacks retained |
| `src/net/MonsterState.java` | Optional persisted/synchronized investigation fields |
| `src/world/generation/GeneratedWorld.java` | Runtime encounter-protection query distinguishing starting ground from access corridors; generation unchanged |
| `src/quest/QuestManager.java` | Notify director on respawn |
| `src/main/CommandProcessor.java`, `Gamepanel.java`, `UI.java` | Developer commands and diagnostic drawing hooks |
| `tools/Phase3GameplayCheck.java` | Explicitly requested headless acceptance checks |

## Verification and remaining limits

Full Java source compilation passed. **147 Phase 3 checks passed**, plus **76 existing UI Phase 5 checks**. The Phase 3 checks cover cardinal/behind/diagonal/distance rules, tool checks, actual tree/large-rock footprints, grass yield, dropped-item ties, solid obstruction/elevation rejection, workbench/forge/campfire/chest/door callbacks, unchanged attack-area values, complete sprite visibility for all active cameras, investigation approach/chase, snapshot compatibility, terrain rejection reasons, thin station barriers, caps, idle/respawn/boss protections, retirement, and developer overlay rendering.

Actual generated worlds produced encounters with seeds **0**, **-17** and **987654321**, at 64×64 and 128×96. Physical viewport checks covered 1366×768 and 1920×1080. A 16×16 world entirely within the camera correctly produces no forced on-screen encounter. A real disk round-trip retained ordinary enemy counts and investigation state, and registered network serialization retained the same fields. Tests use temporary character directories.

These are headless checks, not a live multiplayer/fullscreen playthrough or a hardware performance benchmark. The existing crafting/construction single-player boundary is unchanged. The flood fill is intentionally local and bounded: a very long detour outside its search rectangle can reject an otherwise globally reachable location safely. Existing decorative bushes/furniture receive no invented gameplay callbacks.
