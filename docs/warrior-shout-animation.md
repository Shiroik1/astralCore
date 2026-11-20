# Shared Warcry / Taunt shout

Both skills use one `WarriorShout` sequence: preparation (0.15 s), release/expansion (0.30 s), recovery (0.15 s). They use the same 24 cached character poses in each of the existing four directions. Facing is retained; cursor position is irrelevant.

## Character and effects

`CharacterArt.Pose.shout` uses the existing layered rig with its own raised-hand coordinates, upright sword grip, bent/planted legs and torso compression/extension. The sword stays attached to its hand and clears the head. The arms lower through the same pose family during recovery. Clothing, hair and equipment use the current appearance; no separate painted Warcry or Taunt sheets are required.

A small preparation circle precedes release. At release, one ellipse expands with cubic ease-out to the gameplay radius, with thinning stroke and fading opacity. The position is captured in world coordinates and remains fixed. It renders beneath the player's body. Three restrained motes and three brief head strokes use the same code for both skills. The ring has a warm-white outline and soft layered glow. It holds at full radius for 80 ms, then fades with smooth easing over 360 ms, continuing after movement is restored.

Warcry uses palette Thatch/Glow gold; Taunt uses muted crimson `#B96568` and accent `#D98985`. Both use identical geometry, timing and emission behavior. The ring itself has no collision, damage or gameplay update.

## Gameplay and cleanup

The existing skill handler commits Rage/cooldown once on activation. Warcry retains level 7, 30 Rage, 25-second cooldown and a ten-second buff. Taunt retains level 15, zero Rage, 15-second cooldown and four-second challenge, including the existing boss exclusion and line-of-sight rule. The release event queries allies/enemies once, rather than applying statuses on expanding-ring contact.

Warcry continues to affect existing allied players in the same area; there is no added NPC affiliation or multiplayer system. The current game implements Warcry as a 10% effective Sword attack bonus and divides eligible incoming damage by 1.1 for temporary frontline vitality. This update preserves that existing mapping: it does not add permanent VIT, substitute STA, or introduce a new maximum-HP buff.

Weapon changes, death, knockback, stun/freeze, suppressed UI/dialogue and area changes cancel the pose and controls. Unreleased effects never apply; already-released buffs/challenges continue through normal status handling. Pause follows the existing simulation pause. Normal chunk crossings do not change areas or cancel. Shout animation state is transient, with no save schema change. Existing animation snapshots now include shout presentation; clients do not replay gameplay release.

## Settings

Use IntelliJ VM options; settings are read once at startup:

```text
-Dastral.shout.preparation=0.15
-Dastral.shout.release=0.30
-Dastral.shout.recovery=0.15
-Dastral.shout.ringHold=0.08
-Dastral.shout.ringFade=0.36
-Dastral.shout.warcryRadius=5
-Dastral.shout.tauntRadius=4
```

Durations are bounded to 0.05–0.60 seconds; radii to 1–8 tiles. Each radius setting drives both visuals and the existing gameplay query. Skill cost, cooldown and unlock remain in `SwordCombat`.

## Modified files

- `src/entity/WarriorShout.java`: shared clock, once-only release, world-space ring, accents and snapshot presentation.
- `src/entity/SwordCombat.java`: shared activation, delayed existing effects and cancellation.
- `src/art/CharacterArt.java`: shared procedural body/arm/sword poses.
- `src/entity/CharacterSprites.java`: 24-frame, four-direction appearance cache.
- `src/entity/Player.java`: pose selection, movement lock, interruption gates and ground rendering.
- `src/net/PlayerState.java`, `src/net/SnapshotBuilder.java`, `src/net/GameClient.java`: authoritative presentation replication.

## Verification and performance limits

Compilation passed. A focused headless harness passed 17 checks covering costs, delayed and once-only effects, recovery locks, anchored release coordinates, Taunt's lack of damage/Rhythm, interruption before release and persistence of released statuses. Four-direction pose sheets were rendered and visually reviewed, with sword positioning adjusted to avoid the face.

No per-frame image generation or second entity loop was added. There are 96 cached 90px frames per Sword appearance (approximately 3 MiB), one reusable ellipse and three shared-pool particles per shout. Radius queries occur once per release using existing player/enemy collections. A live FPS benchmark, multiplayer session, save/load round trip and extended cliff/chunk playtest were not performed. These results do not establish a measured FPS change.
