# Bow and Staff implementation

This pass connects crafted Wooden Bow/Staff to the full ranged combat controllers. It removes the separate CraftedBolt input shortcut. Existing projectile collision and hit acceptance remain in use. Compilation, tests, save round-trips and gameplay verification have **not** been run; project instructions require explicit testing approval.

## Bow

Hold/release left mouse for Full Draw; a short press releases Quick Shot. Quick range is 4 tiles, full range 6. Full Draw takes 33 ticks, reduced by Swift Draw. Clean Release unlocks at mastery 3, with an 8-tick window after charging; mastery 22 adds 4 ticks. The charge indicator uses the same threshold. Successful Quick/Full/Clean shots grant 8/12/16 Focus once per projectile. One Wooden Arrow is consumed on release, including skill projectiles; Arrow Rain consumes one arrow for its three waves. Empty releases grant no Focus/mastery. Direction uses normalized heading, including diagonals.

| Mastery | Unlock |
|---|---|
| 1 | Quick Shot, Full Draw |
| 3 | Clean Release: 130% baseline shot damage |
| 5 | Shift+1 Pinning Shot: 12 Focus |
| 7 | Shift+2 Piercing Arrow: 18 Focus, up to three targets |
| 9 | Shift+3 Steady Aim: 10 Focus, +15% damage for 5s |
| 12 | Shift+4 Arrow Rain: 25 Focus |
| 15 | Hunter's Instinct: +2 Focus on successful basic shots |
| 18 | Shift+5 Deadeye: 45 Focus |
| 22 | Perfect Release: expanded Clean window |
| 25 | Master Marksman: +10% Bow damage |

All twelve Bow talents are registered and purchase through the existing paged talent UI. Numerical effects: Keen Eye +3% damage/rank; Swift Draw -2 charge ticks/rank; Focus Efficiency +1 hit Focus/rank; Pinning Precision +8% baseline Pin damage/rank; Improved Piercing +8% baseline piercing damage/rank; Steady Hands +3% damage/rank; Suppressive Rain +4% baseline per wave/rank; Precise Release +6% baseline Clean damage and +2 timing ticks/rank; Hunter's Rhythm +1 hit Focus/rank; Perfect Marksman +5% damage/rank; Deadeye Mastery +8% baseline piercing damage/rank; Unerring Focus +1 hit Focus/rank. Foundation ranks cap at 3, others 2; character/mastery tiers 2/7/13/20 and lower-tier investment 2/5/8.

## Staff

Left-click performs free Arcane Bolt at 55% baseline damage, no MP cost. Successful pairs of bolt hits generate one Rune; misses reset the pair. Slowed-target arcane hits, first Immolate application, crowd control and meaningful shield absorption can generate Runes, sharing a 60-tick cooldown (30 at mastery 22). Rune capacity is five. Casting empty spells grants no mastery or Runes. Staff keeps the base 100 MP and existing delayed regeneration.

In Character → Skills, select Staff and use **[ / ]** to choose one of three saved pages. Use Shift+1–5 to cast the page slots. Each spell has its own cooldown shared across page changes; spell cooldowns are persisted. A weapon swap clears shield and pending rune enhancement. Non-damaging spells preserve a pending enhancement.

| Page/slot | Spell | Mastery | MP | Behavior |
|---|---|---:|---:|---|
| 1/1 | Arcane Missile | 1 | 8 | 130% ranged bolt |
| 1/2 | Immolate | 3 | 12 | Targeted burn; 30% damage every 45 ticks for 3s |
| 1/3 | Conjure Flowerfield | 4 | 5 | Temporary flowers and grass movement |
| 1/4 | Magic Buff | 5 | 10 | One nearby aimed ally, otherwise self; +15% Staff magic for 6s |
| 1/5 | Blizzard | 7 | 18 | Area pulses and 32% slow for 3s |
| 2/1 | Magic Protection | 9 | 14 | Self shield, 150% attack absorption for up to 6s |
| 2/2 | Rainfall | 11 | 6 | Temporary rainfall and grass movement |
| 2/3 | Arcane Crochet | 12 | 12 | Pull one regular enemy toward caster; terrain collision retained |
| 2/4 | Morph | 15 | 18 | Regular enemy becomes an immobile frog for 3s; damage breaks it |
| 2/5 | Arcane Strike | 18 | 24 | 280% focused bolt |
| 3/1 | Minor Enhancement | 1 | 0 | Spend 1 Rune: next damaging spell +25% |
| 3/2 | Strong Enhancement | 1 | 0 | Spend 3 Runes: next damaging spell +75% |
| 3/3 | Runic Convergence | 25 | 30 | Spend 5 Runes: 2-tile area pulses over 2s |

Arcane Missile cooldown is 45 ticks; rune actions 90; other spells 300. Morph does not affect bosses or reapply to a currently morphed target. Arcane Crochet rejects bosses. Utility spells are temporary and do not grant resources, grow harvestable plants, alter terrain or bypass construction rules. Arcane Crochet currently pulls enemies only; object manipulation is not implemented. Magic Protection currently targets self only. Spell art uses simple numbered muted icons; Morph uses a shared flat frog placeholder. These are functional simplifications, not final art or balance.

All twelve Staff talents are registered. Keen Intellect +3% damage/rank; Mana Flow -1 MP cost/rank; Precise Bolt +4% free bolt damage/rank; Lingering Embers +45 burn ticks/rank; Winter's Grasp +5% slow/rank; Wardcraft +5 shield absorption/rank; Elemental Reaction +8% arcane bolt damage against slowed enemies/rank; Arcane Weaving +3% spell damage and +3% Magic Buff/rank; Protective Flow returns 1 MP/rank on rune-eligible shield absorption; Runic Overload +8% enhanced spell damage/rank; Elemental Echo +4% magic damage/rank; Grand Convergence +12% baseline pulse damage/rank. Same rank/tier prerequisites as Sword/Bow.

## Persistence and limits

Progression DTO adds `staffPage` and thirteen spell cooldowns, with default values for existing version-3 saves. Mastery and talent storage reuse existing fields. Runtime spell areas, shield absorption remaining and rune-hit streaks are transient; saving/reloading does not resume those effects. New StaffSpell effects and Morph presentation have no new multiplayer synchronization (excluded by progression scope). Combat behavior and tuning still need approved runtime checks.

Files: `src/entity/RangerCombat.java`, `src/entity/RangerEffect.java`, `src/entity/MageCombat.java`, `src/entity/MageEffect.java`, `src/entity/StaffSpell.java`, `src/entity/Player.java`, `src/entity/Entity.java`, `src/monster/MON_Skeleton.java`, `src/main/Gamepanel.java`, `src/crafting/CraftingManager.java`, `src/progression/Progression.java`, `src/progression/Talents.java`, `src/ui/GameUI.java`, `src/save/SaveStore.java`, `src/art/MorphArt.java`.
