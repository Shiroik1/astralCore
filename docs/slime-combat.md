# Slime combat and the Crowned Slime nest

**Historical phase-one guide.** The [phase-two encounter guide](procedural-encounters.md) supersedes the population, nest-generation, projectile, knockback and phase-two-add details below. Current defaults are eight-way radial fire, five phase summons, 20 ordinary slots plus eight encounter slots, and generator version eight. Normal-slime movement and rewards described here remain applicable.

Implementation status: source and asset-reference review only. Compilation, automated checks, save/load checks and gameplay verification have **not** been run. `AGENTS.md` requires explicit owner approval first. No removed tests have been restored.

## Normal slimes

`MON_GreenSlime` owns an explicit `IDLE → WANDER / CHASE → WINDUP → LEAP_ATTACK → RECOVERY` state machine, with `RETURN` routing back to its saved home. Idle wandering and pursuit use short hops with pauses. Detection requires a living player in the same area, within six tiles, and clear line of sight. Slimes disengage when sight/range is lost or they travel ten tiles from home.

Touching a slime does no damage. Only its committed attack hop can hit. It locks the target position at the start of the warning and does not steer during the leap. A per-player mask allows one hit attempt per leap. Shared player armor, shields, hit flash and knockback still apply. Slime hits give 36 ticks of immunity and half-tile knockback. Other damage sources keep their previous defaults.

At most two nearby normal slimes may commit attacks simultaneously. Random idle delays and cooldown jitter spread their attacks. Ground movement uses pixel substeps, existing terrain/building/station/scenery collision and monster separation. Existing slows, pin/stagger, bleeding, knockback and death animation remain in use.

| Setting | Default | Edit location |
| --- | --- | --- |
| Normal HP / attack | 30 / 8 | `src/monster/MON_GreenSlime.java`, constructor |
| Detection / leash / attack distance | 6 / 10 / 1.5 tiles | Same file, named constants |
| Windup / leap / recovery | 30 / 15 / 30 ticks | Same file, named constants |
| Attack cooldown | 120 ticks + 0–23 tick jitter | Same file, `COOLDOWN_TICKS` |
| Nearby committed attackers | 2 within five tiles | Same file, `attackSlot()` |
| Total monster capacity | 20 | `Gamepanel.monster` |
| Ordinary population target | `-Dastral.world.slimes=20` | `WorldConfig.developmentDefaults()` |
| Ordinary respawn interval | `-Dastral.world.slimeRespawnSeconds=15` | Same method |

All timing uses the existing 60 Hz simulation, independent of rendering. The default early player health is 80–120 depending on class; raw damage still passes through existing defense. Existing melee, bow and staff hit/mastery handling is unchanged. Normal slimes keep their coin/heart/crystal roll and additionally drop one Slime Gel through the queued-loot system.

## Nest generation and population

`SlimeNestPlanner` runs after ground, ponds and cobble generation, **before** trees, props and tall grass. It chooses one deterministic 14×14 all-grass clearing, 25–40 tiles from spawn, clear of the protected crossing, ponds and paving. A ground flood fill proves reachability; the reconstructed route and a margin are reserved against decoration. Noise-biased vegetation forms a soft rim outside the clearing using existing assets.

The plan stores global tile coordinates and a stable ID containing generator version, seed, dimensions and location. Maps smaller than 36 tiles on either axis, or maps with no suitable reachable clearing, skip the encounter safely. Arena size and distance are constants at the top of `SlimeNestPlanner`.

`SlimeNestEncounter` places the dormant boss and two guardians once. It respects existing player floors, walls, furnishings and crafting stations when introducing the encounter into an older save. Late insertion waits until the boss is offscreen; it does not replace player structures. Guardians use bounded alternative positions if preferred spots are occupied.

The existing `SlimeSpawner` remains the only ordinary population controller. While an uncleared nest exists, five of the twenty slots are reserved for the boss, two guardians and two phase adds, leaving a default target of fifteen wilderness slimes. With all five encounter monsters alive the global array cannot exceed twenty. Ordinary spawn checks still exclude reserved areas, player safety zones, camera views, solid scenery, stations and building floors. Older full populations make room by removing only excess offscreen wilderness slimes. Killed guardians are not recreated when the camera moves or a save reloads.

This engine currently loads a **finite world** and caches ground images in 16×16 tile rendering chunks. Monsters are not unloaded with those drawing chunks. Their existing scene arrays and snapshots are retained; there is no second chunk-spawn system to duplicate them. Future entity streaming should key encounter restoration by the same persistent nest ID.

## Crowned Slime

`MON_CrownedSlime` reuses the existing crowned Slime King animations. The existing dungeon Slime King and Hell Slime keep their separate behavior.

| Setting | Default | Edit location |
| --- | --- | --- |
| Boss HP / slam damage | 220 / 18 | `MON_CrownedSlime` constructor |
| Awakening radius | 5 tiles with sight, or taking damage | Constructor / `DORMANT` state |
| Retreat leash | 10 tiles from home | `LEASH_TILES` |
| Slam warning / flight / recovery | 54 / 30 / 60 ticks | Named constants |
| Slam radius | 1.6 tiles | `slamRadius()` |
| Spit warning / damage | 36 ticks / 12 | `SPIT_WARNING`, `SPIT_DAMAGE` |
| Projectile speed / life | 3 pixels per tick / 180 ticks | `spit()` |
| Phase two threshold | 50% HP | `update()` |
| Phase two adds | Exactly two successful summons per attempt | `PHASE_TWO`, saved `adds` count |

The boss awakens briefly, repositions using slower hops, then alternates a locked-position slam and a single spit. A pale outlined amber circle marks the slam landing zone throughout its warning and jump. The destination is clipped to a clear ground path and its damage fires only on landing. Wall checks also prevent the area hit from reaching a player through an obstacle.

The spit uses the established jelly projectile art, follows one fixed direction, and expires or disappears on obstacle/player impact. New world collision is opt-in for this projectile, preserving other projectiles' behavior. Neither the boss nor its guardians deal contact damage.

At half health the boss pauses for a bounce/charge cue and summons two ordinary slimes at clear, separated positions. It shortens reposition/recovery pauses. If placements are temporarily blocked, bounded retries continue later until two insertions have succeeded; deaths do not replenish that count. It never exceeds the shared monster array capacity.

Walking away or dying disengages the boss: it returns home, restores health, clears statuses, removes its phase adds and cancels its remaining spit projectiles. No arena wall is created. Its health bar appears only for an awakened visible encounter, uses a sage fill and eased health display, and hides on retreat/death or while inventory/options are open.

## Saves, rewards and compatibility

`SlimeActor` writes primitive fields into the existing `MonsterState`: home, state, timers, committed destination, phase/add count and nest ID. `SnapshotBuilder`, `CharacterState` and `GameClient` share that representation. On disk reload, an in-progress attack becomes recovery so loading cannot immediately replay a hit. Network replicas receive the current visual state while the host alone simulates combat. Both peers must use the updated source build.

`CharacterData.slimeNestWorld` is an optional additive field in the existing version-one character format. It stores initialized and cleared IDs, including when the character saves from the dungeon. A cleared ID prevents boss respawn and duplicate reward delivery. Boss death and its queued loot are captured in the same existing save operation: a saved dying boss finishes its death once after loading, while a cleared boss has already delivered its loot. The normal backup/atomic-write behavior is unchanged.

On defeat the boss uses its existing death animation, grants 35 character EXP through the existing kill handler, and drops **6 Slime Gel + 1 Royal Slime Core** through `QuestManager.dropLoot`, including its full-world-loot queue. Weapon mastery continues through the existing successful-hit system. The new material identities are registered with `ItemType`, stack and save normally, and reuse the existing essence/crystal icons. Neither item is added as a requirement for early crafting recipes; the well's Slime Essence remains a separate item.

World generator version is now five because clearing reservations change decoration placement. Building loading falls back to existing version-four layout files if no current layout exists; character-embedded building data remains supported. No runtime art files were replaced or deleted.

## File map

Created:

- `src/monster/MON_CrownedSlime.java`: overworld boss states, attacks, telegraph and snapshot fields.
- `src/monster/SlimeMotion.java`: reusable ground collision, sight, swept movement and hit helpers.
- `src/monster/SlimeActor.java`: save/network state contract.
- `src/world/SlimeNestEncounter.java`: authoritative population, summons, clear flags and reward delivery.
- `src/world/generation/SlimeNestPlan.java`, `SlimeNestPlanner.java`: deterministic clearing and access reservation.
- `src/object/OBJ_slimegel.java`, `OBJ_royalslimecore.java`: inventory material identities with existing icons.
- `docs/slime-combat.md`: this guide.

Modified:

- `MON_GreenSlime`, `Monster`, `Entity`, `Player`, `Projectile`, `Pathfinder`: hop AI, shared effect ticking, source-specific hit feedback, optional projectile collisions and reusable home routing.
- `SlimeSpawner`, `GeneratedWorld`, `WorldGenerator`, `ObjectGenerator`, `WorldConfig`: finite population integration and pre-decoration clearing reservation.
- `Gamepanel`, `UI`: encounter lifetime/reset, asset warming and boss HUD.
- `MonsterType`, `ItemType`, `MonsterState`, `SnapshotBuilder`, `GameClient`: factories and authoritative snapshots.
- `CharacterData`, `CharacterState`, `SaveStore`: additive encounter persistence and safe reload recovery.
- `BuildSystem`: version-four building-file fallback only, alongside previously existing placement changes.
- `README.md`: guide link.

## Verification pending approval

Recommended focused checks: compile the game with its existing Java/jar setup; observe idle/chase/return hops; dodge a committed leap; verify no touch damage and one hit per leap; check walls/stations and two-attacker coordination; locate the seeded nest; dodge slam and spit; cross the phase threshold and count two adds; retreat and reengage; save/load during warning, phase transition, death and after looting; reload from the dungeon; confirm no boss/guardian/reward duplication; check small maps and older full-population saves. Multiplayer authority and visual snapshot handling also need a two-client check. No tests, smoke runs, benchmarks, gameplay sessions or package verification were executed as part of implementation.
