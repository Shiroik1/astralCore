# Warrior implementation status

Sword Mastery drives the Warrior unlocks: Basic Slash at 1; Powerslash 5;
Warcry 7; Charge 10; Taunt 15; Sunder 17; Retaliate 20; Bullthrust 25.

## Controls

In gameplay, 1–5 activate abilities. Shift+number selects the item hotbar.
Update: see `progression-and-skill-bars.md` for the saved, freely assignable
action bars that replace the fixed pages described below.
Brackets switch action pages. Page one: Powerslash, Warcry, Charge, Taunt,
Sunder. Page two: Retaliate, Bullthrust, Powerslash, Warcry, Charge.
Hold Bullthrust's key to charge for up to 90 simulation ticks; release to thrust.
RMB retains existing guarding for talent compatibility.

Arena entry is command-only: `/arena`. The existing isolated arena opens a
practice window while pausing the original adventure. `/arena level1` clears
mastery/talents for starter testing; `/arena max` restores the full practice
kit; `/arena boss` replaces the wave with Crowned Slime. `/arena wave`,
`/arena god`, `/arena refill`, `/arena leave` remain available. Use the item
hotbar to equip the Sword. Practice is unsaved.

## Combat implementation

Rage capacity is 100. Accepted basic hits grant 8 per enemy; accepted incoming
damage grants 5. Skills and reflection do not run the generic basic-hit Rage
reward. Existing damage acceptance prevents duplicate rewards.

Rhythm uses the enemy's existing status list (`warriorRhythm`), capped at 5.
Its 180-tick timer resets on each accepted basic hit, then drops one stack every
60 ticks. The sixth hit removes Rhythm and applies `warriorBleed` for five
one-second ticks. Bleed uses the existing kill/reward completion handler;
physical armor reduces its tick damage. Ordinary entity disposal clears state.

Exposed reduces armor in the shared physical-hit calculation by 15%, rounded
up for fractional armor. Sunder refreshes duration to `(3 + Rhythm) seconds`.
It never spends Rhythm. Powerslash is 180% attack with additive +25% for
Bleed and +20% for Exposed. Bullthrust scales 150–300% with +20% Exposed
per target. Talent damage modifiers are applied before shared damage handling.

Charge and Bullthrust move in one-pixel terrain checks. Charge stops on solid
entities; Bullthrust passes through enemies during release, hitting each once.
Bullthrust does not require or consume Rhythm. It checks its frontal hitbox during movement;
one CombatHit owns all hit tracking. Its qualifying target's five stacks are
consumed at activation. Taunt uses the existing challenger targeting mechanism.
Retaliate uses three charges over six seconds and the shared damage handler.

Warcry currently grants +10% effective Sword attack and 10% effective incoming
damage durability. Sword also receives 20% effective durability. These are
**temporary compatibility approximations**, not actual STR/VIT or maximum-HP
bonuses. This part does not yet satisfy the requested attribute/HP behavior.

## Configuration and compatibility

`SwordCombat.COST`, `CD`, `UNLOCK` centralize definitions. Costs and cooldowns
can be overridden with `astral.warrior.cost.<index>` and
`astral.warrior.cooldown.<index>` JVM properties (cooldowns in 60-Hz ticks).
Damage, radii and status durations currently remain in SwordCombat.
Existing Sword talent definitions and saved allocations are unchanged.
Heavy Edge applies to Powerslash; Strong Counter contributes during the old
counter window. Guard-related talents retain the existing RMB guard.
Wide Swing and several old signature-skill modifiers still need a complete
compatibility decision; their original unavailable actions are no longer shown.
No talent data was deleted or rewritten.

Provided first-sheet icons are cropped/cached under `res/skill_icon/warrior`.
Existing sword pose frames and particles are reused. Dedicated backstep,
charge and thrust animation frames were not supplied.

## Validation and remaining work

Full Java source compilation passed. A temporary headless harness passed ten
checks covering Rage capacity/cap, independent Rhythm, fifth/sixth-hit rules,
decay, mastery gating, action pages, invalid Bullthrust targeting and unsaved
practice. This was not a full live gameplay, network or FPS regression run.

Still requires completion/verification:

- Actual temporary STR/VIT and higher maximum HP, with safe save snapshots.
- Exhaustive skill collision, reflection, boss and multi-target gameplay tests.
- Stable saved custom action loadouts/pages and multiplayer page replication.
- Complete old talent effect compatibility without changing talent definitions.
- Retaliation attribution for projectile-owned attacks.
- Dedicated status icons for all Warrior states and Bullthrust damage preview.
- Long-session performance and chunk-boundary regression measurement.

Modified integration points: SwordCombat, Player, Monster, Entity,
MON_GreenSlime, MON_Skeleton, CombatArena, KeyHandler, InputState, GameUI.
SaveStore also contains practice-mode save protection carried over from the
preceding arena work.
