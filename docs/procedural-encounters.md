# Procedural encounters — phase two

For generator-nine physical height, boundary and reservation integration, see [Phase 4 elevation](elevation-boundaries.md). The phase-two combat and encounter settings below remain in effect.

Implements `Pasted text(5).txt` within the existing Java2D/JPanel engine. No new artwork, biomes, class kits, inventory UI or building systems were introduced. Source review is complete; compilation, automated checks, gameplay, save/load and multiplayer verification have **not** been run. `AGENTS.md` requires owner approval before those checks.

## Repeated damage and knockback

The source-level repeat-hit path was in `Player.attacking()`: melee resolution could run every tick of an active animation frame, with temporary target immunity as its only repeat suppression. Hit-stop pauses the player's attack animation while monster combat-effect ticking can continue advancing immunity. A stationary boss remains in that hitbox. An attack can therefore outlive immunity and damage the same target again. This is a source diagnosis; the reported runtime reproduction still needs approval to verify. No second boss-specific HP subtraction was found.

`CombatHit` now supplies a unique event ID and an identity-based target ledger for each swing, arrow and Arcane Bolt. Accepted damage records the target before subtracting HP; another attempt by that event is rejected even after immunity expires. Assassin cuts have distinct events, scheduled Ranger rain waves have one event per wave, and intentional spell pulses/bleed retain their existing timing. Crafting ranged attacks use their own projectile channel instead of also starting a class attack. Damage formulas and boss HP were not halved/doubled to mask the issue.

Basic displacement is tile-relative and does not stack onto existing knockback or truncate explicit skill displacement. Boss knockback immunity remains intact. Hit flashes, hit-stop, stagger and authored ability pushes remain available.

| VM property | Default tiles |
| --- | ---: |
| `astral.combat.knockback.sword` | 0.15 |
| `astral.combat.knockback.arrow` | 0.05 |
| `astral.combat.knockback.bolt` | 0.10 |
| `astral.combat.knockback.heavy` | 0.35 |

With `-Dastral.dev=true -Dastral.combat.hitLog=true`, hit resolution logs attack event/kind, attacker ID, runtime target ID, damage, simulation tick and accepted/duplicate/immunity result. Runtime IDs are diagnostic identifiers, not persistent entity identities.

## Projectile motion and boss phases

`Projectile.setVector()` normalizes an arbitrary direction and retains double-precision position and velocity. Each existing 60 Hz simulation update advances that position, then rounds only for the engine's integer entity coordinates. Pixel collision samples prevent tunnelling. Cardinal, diagonal and arbitrary directions therefore use equal requested speed. The existing angled and cardinal APIs remain available. Network replicas move visually while authoritative damage remains on the host.

The Crowned Slime's first-phase spit samples the player's body center when it fires, then travels without homing. Phase two alternates its existing slam with a radial burst: eight directions at 45-degree intervals, a 0.9-second warning, speed 3 pixels/tick, three-second lifetime and raw damage 10. The burst has a shared per-player hit ledger in addition to normal immunity: a volley cannot damage one player twice even if immunity expires before its last projectile arrives. A collision consumes the contacting projectile even when the player is immune. Shots stop at terrain/scenery/buildings/stations and are cleaned up on expiry, area change, boss death or retreat.

At 50% HP, one phase transition warns and issues **five successful normal-slime summons per fight**. Clear, nonoverlapping arena positions are tried around the boss and arena center, excluding player proximity. Each add gets 1.25 seconds before attacking. Successful insertions are counted persistently; killing an add never replenishes it. Temporarily blocked insertions receive bounded retries until the remaining issued count reaches five; impossible occupied space is never forced. With at least three owned adds alive, the boss waits an additional second between attack decisions. Retreat resets the fight and removes its adds/projectiles; death removes them and retains existing once-only rewards.

The global array has 28 slots: an ordinary cap of 20 and room for the boss, two guardians and five phase adds. Legacy 20-slot save arrays expand in place without moving or recreating monsters. Both network peers must use this build.

| `astral.slime.` property suffix | Default |
| --- | ---: |
| `bossHP` / `slamDamage` | 220 / 18 |
| `radialWarningSeconds` / `radialRecoverySeconds` | 0.9 / 1.4 |
| `projectileSpeed` / `projectileSeconds` | 3 / 3 |
| `radialDamage` / `spitDamage` | 10 / 12 |
| `spitWarningSeconds` | 0.6 |
| `summonWarningSeconds` / `summonGraceSeconds` | 0.75 / 1.25 |

These are JVM options, for example `-Dastral.slime.radialDamage=10`. Five summons and eight radial directions are explicit design constants, not randomized counts.

## Reliable nest clearing and persistence

New worlds use generator **8**. The planner no longer requires a preexisting empty all-grass 14×14 square. Before vegetation/resources, it considers reachable safe grass, dirt and cobblestone, converts the selected footprint to grass, and reserves the clearing and an access corridor. It never converts water or protected starter/crossing ground. A 14-tile arena is preferred, then an 8-tile fallback; distance preference relaxes if no preferred candidate fits. Patchy existing green/dark grass softens the rim without blocking the central fight. If no safe footprint fits, a diagnostic reports dimensions, seed and rejected constraints rather than silently failing.

Existing version-four through version-seven worlds retain their old terrain and nest planner, including their earlier limitations. No occupied saved world is automatically regenerated. During runtime insertion, saved/player floors, walls and crafting stations prevent replacement rather than being erased. Late insertion waits until offscreen. Initial new-world generation may install the dormant boss before play begins.

The nest has a stable version/seed/dimensions/location ID. Saved encounter text includes explicit coordinates/size plus initialized and cleared IDs. Restoration deduplicates matching bosses and refuses replacement if its saved plan differs from the loaded plan. Terrain settings, region settings and new nest layout settings are pinned in `WorldSettings`; version-eight building sidecar identity includes the nest settings. Old sidecar identities remain unchanged. Combat state saves include issued summons, arrival grace and radial recovery. Interrupted disk-loaded attacks resume safely through recovery or the pending summon warning. Encounter state remains independent of drawing-chunk caching.

Enable `-Dastral.dev=true` and enter `/nest` to print the actual ID, center tile, arena size and any blocked-placement reason. `/regenworld <seed>` retains the loaded world's generator version/settings; use a new development character to preview version eight without changing an older landscape.

| `astral.nest.` suffix | Default |
| --- | ---: |
| `arenaSize` / `smallArenaSize` | 14 / 8 |
| `minDistance` / `maxDistance` | 25 / 40 tiles (preferred center distance) |
| `spawnSafety` | 8 tiles (minimum footprint distance) |

Nest properties affect layout, apply to new worlds and are persisted. Combat/director properties affect runtime behavior and do not change generated terrain identity.

## Ordinary encounter director

The current [Phase 3 director](phase3-interactions-encounters.md) rolls every 10–15 active-play seconds by default, with a 0.60 base chance modified once by the player's environmental region. Successful groups contain one or two normal slimes, up to eight nearby ordinary slimes. Nest entities retain their separate budget.

New arrivals receive a captured, temporary investigation destination near the player's area. They use existing collision-aware hops/pathfinding, then enter normal Chase within existing detection range. Complete sprites remain outside every active camera plus a two-tile margin. One local flood fill checks endpoints and swept bodies, and rejects inaccessible terrain, water, cliffs, construction, scenery, protected starting areas and nest arenas. Generation-only access routes no longer suppress valid wilderness rolls.

Four tiles of travel gate repeated successful groups. Kill/boss quiet time remains 25 seconds; respawn grace is 20 seconds. Active boss fights suppress random groups. Inactive distant ordinary enemies cannot monopolize storage, and failed off-screen investigations release their slots without loot or kill credit. Investigation state persists through existing monster snapshots; loading does not create an extra population.

See the Phase 3 guide for JVM settings, `/encounters on|fast|normal|now`, rejection counters, files changed, acceptance results and limitations.

## Files

Created in this phase: `entity/CombatHit`, `entity/CombatTuning`, `monster/SlimeCombatConfig`, `world/EncounterSpawnConfig`, `world/generation/NestConfig`, `LegacySlimeNestPlanner`, `NestPerimeterGenerator` and this guide.

Modified: `Player`, `Monster`, `Entity`, `Projectile`, `MageEffect`, `RangerEffect`, `CraftedBolt`, `OBJ_slimebolt`, `MON_CrownedSlime`, `MON_GreenSlime`, `SlimeActor`, `MonsterState`, `Gamepanel`, `SaveStore`, `SlimeSpawner`, `SlimeNestEncounter`, `GeneratedWorld`, `WorldGenerator`, `WorldConfig`, `WorldSettings`, `SlimeNestPlanner`, `BuildSystem`, `CommandProcessor`, `RegionGenerationCheck` and the existing README/world/region/slime guides. Other preexisting workspace changes were retained.

## Verification awaiting approval

The existing standalone generation checker was extended for version eight, repeated nest identity, clearing reservation and nest-settings round trips; it was not executed. No removed test suite was restored.

After approval, compile first. Then compare individual sword/arrow/Arcane Bolt HP deltas and logged IDs across ordinary/crowned targets; confirm intentional multi-hits; compare cardinal/diagonal projectile distance and obstacle collisions; count eight warned rays and at most one volley hit/player; count exactly five successful adds, no replacements after kills, and cleanup on retreat/death. Check several positive/negative seeds and small maps, `/nest`, corridor access, old/new saves, interrupted combat reload, cleared rewards and retained buildings. Finally observe encounter intervals, all viewport sizes, idle/kill/boss throttling, reachable offscreen spawn points, nearby/global limits, distant retirement and host/client authority. Runtime behavior, performance and compatibility remain unverified until those checks run.
