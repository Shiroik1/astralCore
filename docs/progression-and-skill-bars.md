# Progression balance and assignable skills

## Using the action bar

Open **C → Skills**, choose Sword, Staff or Bow, and click an ability in the
catalog. Press **1–5**, or click one of the five slots above the catalog, to
assign it. Locked abilities show their mastery requirement and cannot be
assigned. Select **Empty slot** to clear a position. Assigning a skill already
on that weapon's bar swaps it with the destination's previous skill.

Each weapon has a separate saved bar. Switching equipment restores its bar.
Gameplay uses 1–5 for skills; Shift+1–5 selects the corresponding item shortcut.
The old fixed skill pages are replaced by the full catalog. Assignment is
allowed while idle in solo play, consistent with existing progression controls.
Multiplayer assignment is deliberately rejected because the existing network
protocol does not replicate authored loadouts. No network progression authority
was added in this change.

Cooldowns belong to abilities, so moving a skill does not reset it. Sword and
Staff use identity-indexed cooldowns; Bow retains its canonical Skill instances.
Three arrays of stable ability IDs are stored in the progression save DTO,
cloned on snapshot/restore, and validated for length, IDs and duplicates.
Older saves default to the original five actions and keep all earned XP.

## XP model

Character XP per level: `80 + 20*n + 4*n*n`, where `n = level - 1`.
Character kill reward: `enemy.exp * 8`. Quest/milestone rewards remain intact.

Weapon mastery thresholds remain `25 + 10*n + 5*n*n`, preserving their existing
cumulative XP and all life-skill curves. Weapon XP is still awarded only after
a validated defeat to the main contributing weapon. Reward is
`min(300, (boss ? 80 : 20) + 4*enemy.exp)`.

| Enemy | Character XP | Weapon XP |
|---|---:|---:|
| Ordinary Slime | 24 | 32 |
| Skeleton (base) | 48 | 44 |
| Slime King | 160 | 160 |
| Crowned Slime | 280 | 220 |

| Reached level | Total character XP | Total mastery XP |
|---|---:|---:|
| 5 | 496 | 230 |
| 10 | 2,256 | 1,605 |
| 15 | 6,216 | 5,355 |
| 20 | 13,376 | 12,730 |
| 25 | 24,736 | 24,980 |

A roughly equal Slime/Skeleton mix needs around 687 kills for character 25 and
658 for one mastery 25, excluding quests and bosses. At 15–30 seconds per kill,
that is approximately 3–6 hours of combat, plus travel, building and gathering.
This is a balancing model, not a measured playthrough. Early unlocks arrive
quickly; later levels take progressively longer. Existing character levels are
retained; saved partial XP is evaluated against the new next-level requirement.
No hit-based XP, boss-reset XP or duplicate kill rewards were restored.

Balance functions live in `src/progression/Progression.java`: `required`,
`characterReward`, `combatReward`. Life skills and crafting XP were not retuned.

## Icons and validation

Warrior action icons were recut with explicit pixel rectangles from the supplied
square, label-free sheet. Each icon is mapped by ability identity, not slot.
The seven actions now show their own complete artwork with no adjacent tiles,
labels or reference-sheet borders. HUD slots have explicit 1–5 labels.

Validation performed: source compilation; assignment, duplicate swapping,
clearing, mastery locks, cooldown retention, cross-weapon switching, three-bar
snapshot/restore, malformed saved IDs and XP-total checks. An existing-save
copy passed actual save/load with customized Sword/Staff/Bow bars; original
save bytes were unchanged. The Skills UI and icon strip were rendered and
visually inspected. Live gameplay pacing and multiplayer sessions were not run.
