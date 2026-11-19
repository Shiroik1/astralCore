# Basic Sword timing

Only Basic Slash uses the new cached pose sequence. The body raises the blade,
holds anticipation, snaps into a white crescent, holds the blade at the end,
then eases back. Left/right views cut overhead to low; up cuts left to right;
down cuts right to left. Body lean is visual and does not move collision bounds.

The constants at the top of `src/entity/SwordCombat.java` control timing.
One simulation tick is 1/60 second; render FPS does not change attack duration.

| Constant | Default ticks | Duration | Purpose |
|---|---:|---:|---|
| RAISE_TICKS | 7 | 117 ms | Ease into raised sword |
| HOLD_TICKS | 3 | 50 ms | Readable anticipation |
| SWING_TICKS | 3 | 50 ms | White smear and damage window |
| END_TICKS | 5 | 83 ms | Visible stopped sword |
| RECOVERY_TICKS | 15 | 250 ms | Return and next-attack lockout |

The level-one cycle is 33 ticks / 550 ms. Existing mastery and Quick Recovery
bonuses reduce recovery only; the maximum practice build takes 26 ticks.
Damage uses the same clock as the smear, with one accepted hit per target per
swing. A miss does not bypass recovery.

To tune without editing code, put these in IntelliJ **Run Configuration → VM
options**, then restart the game:

```
-Dastral.sword.raise=7 -Dastral.sword.hold=3 -Dastral.sword.swing=3 -Dastral.sword.end=5 -Dastral.sword.recovery=15
```

For a quicker response, lower raise to 5. For more anticipation, raise hold to
4. Keep swing around 2–4 for a crisp cut. Lower recovery to attack more often;
raising it makes each commitment heavier. Recovery has a seven-tick minimum
before the fixed end pose. Avoid making the slash long just to slow attack
frequency: increase recovery instead.

`WeaponArt.basicSword` controls smoothstep easing and forward reach.
`CharacterArt` draws the blade; `SwordCombat` draws the warm-white crescent in the forward hit footprint, independently of the body canvas. Basic reach is at least one tile. Up-facing sword/slash layers are behind the body; other directions draw their slash in front.
`CharacterSprites` prepares 41 poses in each direction once per cached outfit;
no images are generated in the render loop. Host snapshots carry the pose
index for other players. No new external animation artwork is required.

# Save decoding fix

The existing version-3 save failed because jsonbeans attempted to construct
`java.util.Map` for `Progression.Data.talents`. The save DTO now declares
concrete LinkedHashMaps, and SaveStore declares the Integer and quest-entry
value types. JSON field names and save version are unchanged. Existing save
files do not require manual conversion.

Validation: full compilation; reading the existing save; load/save/read on a
temporary copy with talents and quest keys retained and original bytes
unchanged; four-direction pose rendering; default and maximum-build cycle
checks. A real-time input/feel session and multiplayer playtest were not run.
