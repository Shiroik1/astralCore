# Skeleton: The Restless Dead

The skeleton reference is implemented as a single unarmed enemy using the existing Java2D renderer, Monster combat pipeline, day/night clock, Encounter Director and chunk activation. No new crafting items, recipes, biome or boss were added.

## Visuals and animation

`src/art/SkeletonArt.java` draws transparent, antialiased vector poses with the reference's rounded ivory skull, large muted eye sockets, narrow ribs and limbs, and ragged taupe cloth. The palette uses ivory `#F1DAC1`, bone shade `#C4ADA0`, sockets `#8A736C`, cloth `#A18D7B` and rag shade `#796C71`. Full-moon eyes use subdued crimson `#A65F68`.

Both normal and moon variants contain nine poses in four directions with twelve frames each: idle, walk, emergence, swipe, lunge, rake, hurt, death and retreat. They are generated once, shared between enemies and prewarmed through the existing sprite-effect cache. Rendering does not load images or generate animation frames. Right-facing frames mirror the side view. Side-view shoulders attach at the torso center rather than the front silhouette edge. Walk poses use articulated knees, alternating foot lift, counter-swinging arms, upper-body sway, delayed head motion and a trailing cloth hem. Looping poses omit a duplicate endpoint frame to avoid a visible pause each cycle.

The original contact sheet is `assets/reference/skeleton-implementation-preview.png`; it predates the articulated gait/centered side-arm update and has not been regenerated. Columns follow the pose order above; rows are front, left, back and right. These are procedural vector animations, not hand-painted sprite sheets. Further motion polish can be made in the same pose generator.

Each skeleton receives a persistent-within-its-lifetime pace factor of 0.88–1.12, its own stride length (1.4–1.9 tiles per cycle), initial gait phase and idle cadence. A gentle independent 6% pace drift prevents a swarm from remaining in lockstep. Walk frames advance with actual collision-resolved travel, including partial steps; blocked actors use idle motion. These settings are generated once per instance, share the same cached pose images, and do not randomize attack timings or hit windows. Cosmetic cadence/personality is regenerated on save reload; health and gameplay encounter state retain existing persistence.

## Combat

| Attack | Unlock | Windup | Damage | Recovery | Cooldown |
|---|---:|---:|---:|---:|---:|
| Bone Swipe | Day 1 | 0.30 s | 6 | 0.45 s | 1.4 s |
| Rattling Lunge | Day 4 | 0.60 s | 9 | 0.75 s | 5 s |
| Double Rake | Day 8 | 0.40 s | 5 per rake | 0.80 s | 6 s |

Values are rounded to the existing 60 Hz simulation. Swipe has approximately 0.12 s of active frames. Rake has two independently limited hit windows separated by 0.25 s. Player armor, damage immunity and knockback use the existing hit pipeline; a second rake does not bypass invulnerability. There is no contact damage.

Lunge captures its cardinal bearing at commitment, travels approximately 2.5 tiles over 0.30 s, and stops at terrain, elevation or object collision. It never retargets during travel. Windup timing is captured at commitment so sunrise cannot interrupt its timer incorrectly. One nearby skeleton may commit an attack at a time during daylight; at night, two can commit together, making swarms more threatening.

The AI covers emergence, idle/wandering, investigation, chase, committed attack, recovery, hurt, death and daylight retreat. Emergence lasts 1.6 s: disturbance, hand, pull-out and standing. It cannot move or attack during emergence, becomes damageable in the latter portion, and receives a one-second standing attack grace. Night skeletons pursue nearby players for up to 60 seconds after spawning, including routing around obstacles when line of sight is lost. Night detection is ten tiles (eleven during a full moon), with a two-tile pursuit margin. Attacks still require line of sight and keep their committed bearing. Daytime investigation retains the captured spawn destination.

Ordinary player hits still deal damage and show impact feedback, but may trigger physical knockback/hurt recovery only once per enemy per second. Skeleton and normal slime committed windups/attacks resist ordinary interruption and knockback. Explicit stagger abilities retain their separate behavior. `Monster.reactToHit` owns this gate, and `Player` routes accepted hits through it. Committed skeleton attack poses remain visible when hit.

Player running movement is 12.5% slower in both straight and diagonal movement. Integer steps use the input tick to distribute fractional movement consistently between live simulation and client replay, giving 3.5 pixels per tick on average for normal straight movement instead of 4. Skeleton attacks receive a separate 20% nighttime damage bonus before armor, multiplied with their existing full-moon bonus. Early-world ordinary night values round to 7 swipe, 11 lunge and 6 per rake; daylight base damage remains unchanged.

## World age and full moon

World age is saved with the existing clock. Day 15 adds modest movement/aggression increases; day 22 adds veteran pacing without a new attack. Encounter HP and damage scaling are captured when the skeleton spawns:

```text
health = 35 × (1 + min(day - 1, 20) × 0.03)
damage = base damage × (1 + min(day - 1, 20) × 0.02)
```

Integer health and damage are rounded. Existing enemies do not heal or gain ordinary day scaling when another day begins.

A full moon occurs every eighth night. Night follows the existing clock, from 19:30 until 06:30. The early-morning hours belong to the preceding night, so the effect survives midnight and ends at sunrise. One warning is issued per moon night and its record is saved.

Full moon adds 25% maximum HP, 35% damage, 20% movement speed, one tile of detection, and a 15% cooldown/windup reduction. Spawn checks run 1.5 times as often and the nighttime skeleton cap rises from twelve to sixteen. The same three attacks remain. Entering and leaving enrage derives stats from captured base values and preserves the current HP proportion within integer rounding; bonuses do not compound.

## Habitat and encounters

Skeleton checks share `SlimeSpawner`. Night checks run every six seconds with a 90% base chance multiplied by the existing regional weights, attempting swarms of four to six up to twelve nearby skeletons (sixteen at full moon). Ordinary slimes and skeletons share a nighttime nearby cap of eighteen. Actual group size is limited by valid terrain, existing enemies and reserved boss capacity. Daytime retains sixteen-second checks, a 65% base chance, groups of one or two, a skeleton cap of four and combined cap of ten. Bosses, nest guards and summoned minions retain their reserved slots and do not consume this ordinary population budget.

Every candidate must pass existing world-boundary, water, elevation, solid-object, construction, active-chunk, protected-start and local reachability checks. At night, skeletons visibly crawl out of safe ground four to eight tiles around the player, including on-screen terrain. Their full emergence and standing grace happen before attacks. Daytime skeletons and all ordinary slime spawns retain camera exclusion. Random spawning pauses during the Crowned Slime encounter and existing respawn/kill grace periods. The four-tile travel gate applies only during daytime; night groups can replenish while stationary, within the caps.

At night, eligible wilderness terrain can spawn skeletons. During daylight, only Forest Grove candidates with at least 60% canopy coverage in a four-tile radius qualify. `SkeletonHabitat` queries the local scenery index and samples the 9×9 tile neighborhood against living tree canopy bounds. Coverage is an approximate canopy footprint, not an alpha-mask calculation. Results expire after two simulation seconds or a scenery-index revision, with a 1,024-entry bound. No whole-world forest scan occurs each frame.

Outside qualifying shade, twelve seconds of daylight exposure makes a skeleton eligible to retreat. Retreat and daylight sluggishness are deferred while it detects or pursues the player, or has fought within the last ten seconds. It never starts daylight retreat mid-fight. Night immediately clears accumulated sunlight exposure. Once safely disengaged, it crumbles/retreats without rewards. Distant inactive skeletons stop AI and retire after the director's distant timeout (60 seconds). Persistent chunk data and the canonical monster array remain authoritative; loading a chunk does not create another skeleton.

## Save compatibility

`WorldSnapshot` stores world day and the last moon warning. `MonsterState` stores skeleton birth day, base stats, HP, moon state, emergence/retreat progress, cooldowns, daylight exposure and investigation destination. Existing enum ordinals are preserved by appending `SKELETON` to `MonsterType`.

Loading resumes emergence and retreat. A saved committed attack becomes recovery, preventing a hit replay after loading. Old saves without a day counter begin at day one while retaining their saved time of day; historical world age cannot be recovered from those saves. Existing buildings, resources and inventory persistence stay in their current owners.

Skeleton wilderness spawning currently applies to offline procedural worlds. Multiplayer spawning/synchronization was not added in this update.

## Configuration and developer tools

Defaults and property validation live in `src/monster/SkeletonConfig.java`. Set JVM properties before launching, for example:

```text
-Dastral.skeleton.spawnSeconds=16
-Dastral.skeleton.spawnChance=0.65
-Dastral.skeleton.nearbyCap=4
-Dastral.skeleton.moonCap=16
-Dastral.skeleton.combinedCap=10
-Dastral.skeleton.forestRadius=4
-Dastral.skeleton.forestCoverage=0.60
-Dastral.skeleton.nightSpawnSeconds=6
-Dastral.skeleton.nightSpawnChance=0.90
-Dastral.skeleton.nightCap=12
-Dastral.skeleton.nightMoonCap=16
-Dastral.skeleton.nightCombinedCap=18
-Dastral.skeleton.nightDetectionTiles=10
-Dastral.skeleton.emergeMinTiles=4
-Dastral.skeleton.emergeMaxTiles=8
-Dastral.skeleton.huntSeconds=60
-Dastral.skeleton.nightGroupMin=4
-Dastral.skeleton.nightGroupMax=6
-Dastral.skeleton.nightAttackers=2
-Dastral.skeleton.nightDamageBonus=0.20
```

Other available suffixes under `astral.skeleton.` are `health`, `swipeDamage`, `lungeDamage`, `rakeDamage`, `speed`, `detectionTiles`, `emergenceSeconds`, `daylightGraceSeconds`, `swipeWindup`, `swipeActive`, `swipeRecovery`, `swipeCooldown`, `lungeWindup`, `lungeSeconds`, `lungeRecovery`, `lungeCooldown`, `rakeWindup`, `rakeGap`, `rakeRecovery`, `rakeCooldown`, `lungeDay`, `rakeDay`, `aggressiveDay`, `veteranDay`, `scalingDays`, `moonDays`, `attackers`, `dailyHealthGrowth`, `dailyDamageGrowth`, `moonHealthBonus`, `moonDamageBonus`, `moonSpeedBonus`, `moonSpawnMultiplier` and `moonCooldownReduction`.

Enable `-Dastral.dev=true`, then use:

```text
/skeletons on
/skeletons off
/skeletons now
/skeletons day 8
/time 22
```

`now` skips the chance roll and travel gate, but preserves terrain, emergence distance, applicable daytime camera exclusion, safety, capacity and grace checks. `day` changes world age for testing. `-Dastral.skeleton.debug=true` opens the overlay at startup when developer mode is enabled. It shows day/time, moon, local cap, last candidate region/coverage, timer, rejection counts, sampled AI/attack and birth-day multipliers. Coverage and region describe the most recently evaluated eligible candidate, not a continuous player scan.

## Files

| Responsibility | Files |
|---|---|
| Shared hit reactions | `src/entity/Monster.java`, `src/entity/Player.java`, `src/monster/MON_GreenSlime.java` |
| Enemy, tuning, cached art | `src/monster/MON_Skeleton.java`, `src/monster/SkeletonConfig.java`, `src/art/SkeletonArt.java`, `src/monster/MonsterType.java` |
| Spawn, habitat, world clock | `src/world/SlimeSpawner.java`, `src/world/SkeletonHabitat.java`, `src/world/DayCycle.java` |
| Snapshot and disk restoration | `src/net/MonsterState.java`, `src/net/WorldSnapshot.java`, `src/net/SnapshotBuilder.java`, `src/save/CharacterState.java`, `src/save/SaveStore.java` |
| New-world reset, retirement, debug UI | `src/main/Gamepanel.java`, `src/main/UI.java`, `src/main/CommandProcessor.java` |
| Acceptance tools and preview | `tools/SkeletonGameplayCheck.java`, `tools/SkeletonWorldProbe.java`, `assets/reference/skeleton-implementation-preview.png` |

## Previous validation and performance

The measurements and pass counts below predate the nighttime-emergence and anti-stunlock fixes. Those follow-up fixes and subsequent movement/damage/swarm tuning and articulated gait/side-arm changes have not been compiled or run: the project agreement requires explicit approval for tests, smoke checks and verification. The updated acceptance tools are ready for an authorized run.

Generated-world probes passed with seeds `123456789`, `0` and `-17`: off-screen night spawning, active terrain, real dense-forest daytime spawning, disk save/load of HP and age, saved moon warning, and no entity duplication. Qualifying sampled forest cells were respectively 93/152, 78/154 and 73/137; the terrain generator was not changed.

The warmed, headless Java2D probe measured 180 update/render pairs with actual spawned skeletons:

| Seed | Mean update | Update p95 | Mean render | Render p95 | Largest render |
|---|---:|---:|---:|---:|---:|
| 123456789 | 0.082 ms | 0.121 ms | 7.937 ms | 8.192 ms | 9.131 ms |
| 0 | 0.153 ms | 0.277 ms | 9.501 ms | 12.701 ms | 18.121 ms |
| -17 | 0.078 ms | 0.123 ms | 7.732 ms | 8.023 ms | 9.407 ms |

These are local steady-state measurements, not guaranteed live FPS or a before/after optimization claim. The short sample does not cover an extended play session or every combat situation. Initial atlas generation happens during preloading and is outside the warmed timing window. Headless audio warnings come from unavailable audio output; the checks use no player saves.

`SkeletonGameplayCheck` covers animation sharing, emergence, tool-independent enemy combat, one-hit/two-hit masks, invulnerability, fixed lunge bearing, water collision, moon transitions during windup, attack-slot limits, capped scaling, daylight retirement, habitat invalidation and population budgets. Final results: 80 skeleton checks, 147 existing Phase 3 checks, 76 inventory/UI checks and 429,460 chunk assertions passed. The project compiled with JDK 26, and `git diff --check` passed. Live visual/play-feel review remains useful, particularly for procedural attack poses.
