# Charge animation and movement

Charge retains Sword level 10, zero Rage cost and the existing eight-second cooldown. The finishing slash deals the same physical attack power as Basic Slash through the shared damage/Defense/critical-hit pipeline, and applies 25% Slow for three seconds. Each valid finishing hit adds one Rhythm stack (capped at five), with gold impact sparks. It does not trigger Bleed or generate Rage; eligible kills credit Sword mastery through the existing contribution system. The existing Pursuing Strike follow-up is preserved after a connected slash.

## Sequence and tuning

| Phase | Default duration | Character motion |
| --- | --- | --- |
| Anticipation | 0.10 s | Compresses the body, bends and separates the legs, draws the sword back |
| Dash | 0.24 s at maximum range (eight tiles) | Strong forward lean, trailing leg, low rear sword and small limb motion |
| Impact | 0.15 s | Plants the foot, brings the sword across the body, follows through |
| Recovery | 0.15 s | Settles the torso and returns sword and feet to their ready positions |

Settings are read at startup. Add these to IntelliJ's **VM options**, adjusting as desired:

```text
-Dastral.charge.anticipation=0.10
-Dastral.charge.dash=0.24
-Dastral.charge.impact=0.15
-Dastral.charge.recovery=0.15
-Dastral.charge.range=8
-Dastral.charge.slowPercent=25
-Dastral.charge.slowSeconds=3
```

A smaller dash duration makes travel faster without changing maximum distance. Short destinations reduce dash duration proportionally. Settings are bounded; range is bounded to 1–12 tiles. Cooldown and unlock remain in `SwordCombat.CD` and `UNLOCK`. Charge shares the Basic Slash damage scaling in `SwordCombat.basicAttackDamage()`. The impact occurs at 35% of the impact phase. Local impact pause is 0.04 seconds, recovery input window is 0.12 seconds, afterimage lifetime is 0.18 seconds; these are named or literal values in `ChargeAnimation` and are independent of the dash speed.

## Aiming, collision and impact

Activation requires a living enemy under the cursor within eight tiles. Invalid or missing targets do not consume cooldown. Activation captures that enemy's collision center relative to the player's collision center. The resulting vector is normalized and locked for this activation. It does not home toward a moving enemy or depend on prior facing. The closest existing four-direction rig supplies the body pose; movement and the slash arc retain the arbitrary-angle vector.

Movement uses one-pixel substeps through the existing `SlimeMotion.clear` collision path, including buildings, solid objects, world borders and elevation. Nearby monster candidates are resolved once per movement tick, rather than once per pixel. Charge stops before their body with a small margin. A terrain block also transitions into the finishing slash at the actual legal stopping point.

Only the rotated finishing hitbox applies damage and Slow. It checks line of sight, living/usable targets and per-activation `CombatHit` membership. Crossing or displaying afterimages never applies a status; the finishing hit alone adds Slow and one Rhythm stack. A cursor over empty ground cannot activate Charge. Cooldown commits once on valid activation, including blocked casts; cancellation does not refund it.

## Rendering and responsiveness

`CharacterArt.Pose.charge` authors separate anticipation, dash, impact and recovery samples using the existing body, clothing and sword rig. There are 32 cached frames per direction. Character body transforms and limb/sword positioning change together, with the original collision/foot anchor retained. Bullthrust's pose data and movement are unchanged.

Three reusable afterimage records reference the actual cached appearance and equipped-sword frame at recorded world positions. They fade from 45% opacity over 0.18 seconds, with captures spaced about 0.045 seconds apart during movement. No image is created in the update or render loop. Short directional streaks, shared-pool dust and one prebuilt tapered ivory/gold arc provide physical effects.

A successful finishing hit holds Charge's pose for 0.04 seconds, without sleeping or freezing other entities. It uses the existing monster-hit sound and a restrained local camera shake. Basic Slash clicks are accepted in the last 0.12 seconds of recovery and retained through completion; recovery cannot be skipped. Movement and other skill activation remain locked during the committed action.

Death, stun, freeze, knockback, equipment changes, dialogue/UI suppression and area changes clear the state and locks. A normal chunk crossing does not cancel the skill. Host snapshots carry presentation state to clients; clients do not independently apply Charge movement or Slow.

## Files modified for this update

- `src/entity/ChargeAnimation.java`: dedicated phases, cursor movement, safe stopping, status hit, cached-frame afterimages, arc and local hit-stop.
- `src/entity/SwordCombat.java`: activation/update/render routing, cancellation and existing talent follow-up.
- `src/entity/Player.java`: pose selection, movement lock, interruption handling, buffered basic attack and afterimage rendering.
- `src/art/CharacterArt.java`: dedicated procedural body, leg and sword poses using the existing appearance rig.
- `src/entity/CharacterSprites.java`: Charge frame caching alongside existing animations.
- `src/net/PlayerState.java`, `src/net/SnapshotBuilder.java`, `src/net/GameClient.java`: presentation-state replication.
- `docs/charge-animation.md`, `docs/bullthrust-animation.md`: tuning and current behavior documentation.

No new painted sprite or sound assets are required. Existing procedural character art and the existing impact sound are reused.

## Validation and limits

Full Java compilation passed. A focused headless harness exercised cursor endpoints, range clamping, diagonal normalization, facing-independent activation, incremental travel, all phases, rendering, monster stopping, Slow without damage/Rage/Rhythm, thin-wall blocking, interruption cleanup, mastery gating, stunned activation rejection and Basic Slash buffering without skipping recovery. The final run passed 417 assertions across these scenarios, including all eight compass directions, a range-clamped diagonal and a stationary cast. A directional pose sheet was rendered and visually reviewed.

This is not a measured live-game FPS benchmark. The added cache is approximately 4 MiB per Sword appearance at the current 90 px canvas, within the existing bounded appearance cache. Effects retain only three image references and one immutable arc. Existing collision helpers still enumerate entity slots for nearby queries; no new world traversal or separate enemy update loop was introduced. Live multiplayer, a full save/load round trip and extended cliff/chunk/combat playtesting remain unverified by this focused harness.

Range/target update: Charge now requires a hovered living enemy and defaults to eight tiles. Bullthrust defaults to ten tiles (`astral.bullthrust.rangeTiles`, bounded 1–16), retaining cursor aiming and enemy pass-through. Prior cursor-only test results above describe the earlier implementation; the target-only update has not been gameplay-tested.
