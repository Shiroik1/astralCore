# Bullthrust animation and tuning

Bullthrust now has a dedicated sequence using the existing generated character rig, equipped appearance cache, Java2D render pass, and fixed 60 Hz simulation. The supplied textual sequence was used; no hand-drawn sketch was attached to this request.

## Sequence

| Stage | Default | Movement / pose |
|---|---|---|
| Backstep | 0.18 s | Smooth 0.75-tile hop backward; drawn-back sword, bent legs, weight shift, two dust puffs |
| Charging | 1.5 s to full | Planted feet, low torso, balanced off-hand, sword behind; subtle breathing and increasing tension |
| Full charge hold | Up to 4 additional seconds | Holds the full pose and capped meter; one restrained full-charge cue |
| Release | 0.12 s | One continuous 10-tile dash: launch, sweeping arms/trails, extended finishing thrust |
| Recovery | 0.25 s | Low landing stance, extended sword settles back to rest |

Release during backstep is latched and launches after the hop with zero charge. Release after the hop uses the current continuous charge. Maximum hold timeout releases automatically. Input is locked during the committed sequence; key repeat cannot restart it while active or on cooldown. Ordinary game pause/hit-stop pauses simulation time.

## Configuration

Set these JVM options in IntelliJ's **Run Configuration → VM options**, then restart:

```text
-Dastral.bullthrust.backstepSeconds=0.18
-Dastral.bullthrust.backstepTiles=0.75
-Dastral.bullthrust.chargeSeconds=1.5
-Dastral.bullthrust.holdSeconds=4.0
-Dastral.bullthrust.releaseSeconds=0.12
-Dastral.bullthrust.rangeTiles=10.0
-Dastral.bullthrust.recoverySeconds=0.25
```

Shorter release duration produces a faster dash over the same range. Shorter charge duration reaches maximum damage sooner. Hold duration starts *after* full charge. Duration/range settings are bounded against invalid or extreme values. Range defaults to ten tiles and is bounded to 1–16 tiles.

Existing skill settings remain `astral.warrior.cost.6` (50 Rage) and `astral.warrior.cooldown.6` (1080 simulation ticks / 18 s). Sword Lv 25 remains required; no selected enemy is needed. Rhythm is neither required nor consumed. Rage and cooldown are committed at activation, with no cancellation refund.

Damage remains `1.5 + 1.5 × charge` times the existing Sword attack calculation, including existing talents. Each target's Exposed adds the existing 20% Bullthrust multiplier independently, and its Defense reduction remains in the shared damage path. Visual arcs do not apply damage.

## Implementation

- `src/entity/BullthrustAnimation.java`: second-based phases, input release latch, bounded motion, charge meter, six reusable world-space trails and up to two cached-frame afterimages.
- `src/entity/SwordCombat.java`: starts the sequence after existing validation/payment; supplies one shared `CombatHit` and the unchanged damage/status formulas.
- `src/art/CharacterArt.java`: dedicated sword grip, off-hand balance, planted/bent legs, torso lean, hop and recovery transforms.
- `src/entity/CharacterSprites.java`: prebuilds 64 pose samples per direction when an equipped Sword appearance is built. No new images are built during update/render.
- `src/entity/Player.java`: selects cached poses, renders effects behind the body and the meter nearby, and cancels on appropriate interruptions.
- `src/net/PlayerState.java`, `src/net/SnapshotBuilder.java`, `src/net/GameClient.java`: mirror host phase/time/charge for client presentation. Clients never resolve Bullthrust damage or travel independently.

Bullthrust captures the cursor world position at activation and travels toward it after the backstep, always traveling the full ten-tile release range in that direction unless terrain blocks it. Arbitrary diagonal movement uses a normalized vector, so diagonals do not move faster. Body posing uses the closest of the existing four directional rigs; trails and afterimages follow the actual movement heading. Charge requires a living enemy under the cursor within its eight-tile range and captures its position at activation. Both skills turn toward the captured aim immediately, regardless of prior facing. Charge now has its own anticipation, dash, finishing slash and recovery; see [Charge animation](charge-animation.md). Its finishing slash applies Slow only to valid nearby targets. No new painted sprite assets are required. Each appearance adds approximately 8 MiB of cached Bullthrust frames at the normal 90px canvas; the existing bounded appearance cache remains authoritative.

Curved, tapered Java2D paths are constructed once. Arcs are emitted at actual dash positions, alternate across the trajectory, and fade over 0.18 s. Higher charge increases size and overlap; full charge adds two fading references to the actual cached appearance frames. A narrow finishing shape ends the sweep. Muted ivory, warm timber highlights and a few shared-pool dust/spark particles keep the effect physical.

## Movement and cleanup

Travel interpolates from each phase's actual start position and checks one-pixel steps through `SlimeMotion.clear`, retaining building, water, world-border and elevation collision. Each simulation step gathers nearby monster candidates once; the same bounded candidate set is used for swept hits and solid contact. The release passes through living enemies and damages each eligible enemy in the forward sweep once. Enemy bodies still block the preparatory backstep. Terrain and solid world objects stop both phases. One `CombatHit` identity covers the entire release, independent of trail count.

Death, knockback, stun/frozen status, weapon change, dialogue, inventory, gameplay suppression and scene changes clear the sequence and animation lock. A blocked backstep settles into charging; a blocked release lands into recovery. Ordinary chunk crossings do not cancel. The cursor destination is captured at activation; moving the cursor afterward does not redirect the committed attack. The meter hides immediately on release/cancellation. Trails and afterimages are cleared on cancellation or completion.

## Validation

Before the enemy-piercing update, focused headless arena checks passed 113 assertions: all four directions, exact unobstructed backstep/dash distances, hold/release/timeout, one-time Rage/Rhythm payment, unlock and stack requirements, cooldown retention, multiple target damage without duplicate hits, per-target Exposed benefit, solid enemy and thin obstacle collision, knockback/equipment/death/inventory cleanup, and full-sequence update/render. The generated directional pose sheet was visually inspected. Full project compilation and whitespace validation passed.

After removing the Rhythm requirement and enabling enemy pass-through, 29 focused assertions passed: zero-stack activation, preservation of existing stacks, one-time Rage payment, full-distance traversal through three surviving enemies in all four directions, one hit per target, and continued wall/behind-wall damage protection. Full compilation and whitespace validation passed.

Not yet manually playtested in a live window or across a network connection. No new FPS benchmark was run. Procedural cliff/chunk traversal is covered by the reused collision code, but was not separately gameplay-tested in this pass.

Facing-independent casting checks passed for Bullthrust in all four diagonal quadrants and Charge facing away without a selected enemy; cursor endpoints and normalized travel were verified.

Bullthrust uses the cursor for direction only. Cursor distance does not shorten the release; configured range is measured from the release position after the backstep. Terrain collision still stops travel. Combat square/spark/mote/ring particles now use cached radial glow paints; dust, smoke and material chips retain their physical appearance.
