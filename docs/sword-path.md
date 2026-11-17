# Sword path and save repair

## Save repair

`Progression.Data.milestones` now uses `ArrayList<String>` rather than the interface `Set<String>`. JsonBeans 0.5 cannot serialize the old Set/HashSet pairing with type names disabled, which prevented both character creation and saving. Runtime milestones remain a set; snapshots copy into a supported list and restoration copies back into the set. Save version remains 3; existing inventory/world data and backups are retained.

## Mastery unlocks

| Sword level | Behavior |
|---|---|
| 1 | Basic Slash: 33 simulation ticks, one accepted hit per target |
| 3 | Heavy Slash: Shift+1, 175% damage, 15-tick wind-up, 45-tick cycle, 150-tick cooldown |
| 5 | Guard: hold right mouse, frontal 40% damage reduction, reduced movement |
| 7 | Counter Slash: Shift+2 after guarding a hit within the first 8 guard ticks; 90-tick counter opportunity; 160% damage; 90-tick cooldown |
| 9 | Sword Discipline: -2 recovery ticks |
| 12 | Sweeping Slash: Shift+3, 125% damage, +36px frontal width; 240-tick cooldown |
| 15 | Steady Guard: +10 percentage points of guard reduction |
| 18 | Blade Rush: Shift+4, short collision-checked forward movement, 150% damage; 360-tick cooldown |
| 22 | Perfect Counter: counter damage becomes 210%; guard timing expands to 11 ticks |
| 25 | Sword Mastery: +10% basic damage and -2 recovery ticks |

All timings use the existing 60 Hz simulation. Sword skills have no resource cost. Guard needs no equipped shield, never grants immunity, and takes at least one damage. Rear/side attacks bypass guard. Releasing guard is required before using a counter. The existing Guard skill slot is a reminder: guard itself is held with RMB, not cast using Shift+5. Timed guard emits a small spark cue. A warm arc marks guarding and active slashes. Missing skill art uses simple generated numbered sword icons.

Standard attacks use one CombatHit for all active frames. Specials cannot start while attacking, guarding, knocked back, dead or input-suppressed. Recovery must finish before another attack. Targets require line of sight; a wider sweep cannot hit through solid terrain. Blade Rush checks ground collision at each pixel; no teleportation. Basic push is 0.15 tile; Heavy Slash uses 0.35 tile. Existing enemy resistance still applies.

## Sword talents

All twelve requested Sword talents are purchasable in Character → Skills, using paged talent buttons. Hover a talent for its effect. Foundation max rank 3; other tiers max rank 2. Both character and Sword mastery level must reach 2/7/13/20. Higher tiers require 2/5/8 points in lower tiers of Sword. Purchases spend the shared talent budget once and persist through the existing progression DTO.

| Talent | Effect per rank |
|---|---|
| Sword Power | +3% Sword damage |
| Quick Recovery | -1 recovery tick |
| Guard Discipline | +4 percentage points guard reduction |
| Strong Counter | +12% baseline attack damage added to counter |
| Heavy Edge | +12% baseline attack damage added to Heavy Slash |
| Wide Swing | +8px sweep width |
| Defensive Rhythm | Guard primes next landed swing for +6% baseline damage |
| Blade Precision | Third consecutive landed swing gains +4% baseline damage; combo expires after 3s |
| Pursuing Strike | Blade Rush primes next landed basic slash for +12% baseline damage for 2s |
| Perfect Swordsman | +6% basic and +8% counter baseline damage |
| Unbroken Guard | +2.5 percentage points guard reduction and +1 timed-guard tick |
| Masterful Blade | +8% baseline damage for Heavy Slash, Sweep and Rush |

Guard reduction caps at 75%. Recovery has a 7-tick floor. Damage talents stack additively within a signature attack, with Sword Power applied to weapon attack. Weapon swaps and interaction/scene resets clear pending combat state. Cooldowns reuse the existing weapon-kit/save arrays. Committed attacks are not persisted/replayed after loading. Multiplayer synchronization of the new Sword state is outside this progression update.

## Files

`src/progression/Progression.java`, `src/progression/Talents.java`, `src/entity/SwordCombat.java`, `src/entity/Player.java`, `src/ui/GameUI.java`, this report, and the progression report.

## Validation status

Source review only. No compilation, save round-trip, character-creation run or gameplay checks were executed; AGENTS.md requires explicit owner approval first. Required checks: empty-slot creation/save/reload, legacy saves, all mastery thresholds, talent prerequisites and spending, sword swapping/cooldowns, front/rear guard, timed counter, one-hit-per-swing, collision-limited rush, and input suppression/recovery.
