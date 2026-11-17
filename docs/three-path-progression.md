# Three-path progression: implemented foundation and remaining work

This is an incremental foundation, **not the complete requested RPG update**. Existing engine, world, crafting recipes, inventory and encounter ownership are retained. No tests, compilation or gameplay verification have been run for these changes; project working agreements require owner approval first.

See `docs/sword-path.md` for the subsequent complete Sword implementation and save serialization repair. Sword-specific limitations below describe the original foundation and are superseded by that report. Bow, Staff, professions and dialogue remain unfinished.

See `docs/bow-staff-paths.md` for the subsequent Bow/Staff implementation, controls, numerical talents, save additions and explicitly limited utility behavior.

## Implemented in source

- Character levels 1–25: next-level XP `100 + 35*n + 15*n*n`, `n = level - 1`. XP is subtracted at each level; large rewards can cross multiple levels. Level 25 stops gaining XP.
- New characters start at 100 HP, 100 MP, STR/DEX/INT/STA 10. Levels grant 3 unspent attribute points, 4 HP, and one talent point on even levels. No automatic attribute growth.
- Character window (`C`) supports pending +/- allocations, Reset Pending, Confirm, available point balance and percentage damage preview. Confirm validates the whole transaction. Confirmed points cannot be removed. Manual allocation caps at 60.
- Effective points above 10: `min(extra,30) + max(0,extra-30)*0.5`. Weapon damage multiplies its existing baseline by `1 + effective*0.012`. Gathering Energy is `100 + effective(STA)*3`. JVM properties: `astral.progression.attributeRate`, `.softRate`, `.energyPerPoint`.
- Tool harvesting costs 2 energy per hit; idle recovery restores 2 every 30 ticks. No STA investment is needed to recover energy. Loose stones/twigs cost no energy.
- Sword, Staff and Bow display independent levels 1–25 derived from existing cumulative Melee/Magic/Bow counters. Threshold per mastery level is `25 + 10*n + 5*n*n`. Accepted damaging hits award 3 XP, at most once per weapon type per 20 ticks. Old counters retain every XP point. Axes and other unsupported types do not gain Sword XP.
- Three playable foundation talents: Sword Power, Keen Eye, Keen Intellect. Each grants +3% weapon damage per rank, max rank 3, requires character and relevant mastery level 2. Purchase through Skills buttons. Uses the shared point budget and persists ranks. Other requested talents are not implemented.
- Existing Crafting progression remains authoritative. Mining gains XP from rock/ore depletion; Gathering from plant/wood depletion or loose resource collection. Cooking/Fishing have persistent counters but no playable activities added.
- Weapon profiles now select existing Sword/Bow/Staff kits. Legacy class enums/assets remain for save and asset compatibility; this pass does not remove all old class-specific content. Staff has five rune slots. The requested replacement Sword/Bow/Staff ability tables are **not implemented**; existing abilities are still used.
- Event-driven starter journal (`J`): speaking to the Old Man accepts available quests; speaking again claims ready quests once. Rest: 4 newly collected Logs + placed Workbench, reward 100 XP/20 coins. Night: 3 Skeleton kills, reward 180 XP/35 coins. Crown: discover existing nest + defeat existing boss, reward 350 XP/80 coins. Persistent boss defeat/discovery facts count for Crown if already recorded. No duplicate boss is spawned.
- Journal uses actual state and progress, not UI-owned counters. Resource pickup, station placement and enemy defeat emit events. Nest discovery is checked during the existing encounter population check; first discovery grants 50 character XP. No new whole-world scan.

## Persistence

Character save format is version 3, with a separate progression DTO. Versions 1 and 2 remain readable. Their existing mastery counters are preserved; automatic legacy attributes are reset to 10 and their level-earned points are refunded for manual allocation. Character HP/MP are normalized to the new baselines (equipment is retained). Existing character XP is normalized through the new curve if it exceeds the new requirement. New saves validate attribute and talent budgets before applying world state. Pending allocations are transient and never saved. Inventory, equipment, construction, resource state and world snapshots keep their existing owners. Multiplayer progression is not implemented; allocation/talent purchases are single-player only.

## Remaining requested scope

- All specified mastery ability unlock tables, Sword guard/counters/rush, Bow starter full-draw/clean-release integration, and renamed/expanded elemental Staff spells and utility spells.
- Technique, Specialization and Mastery talent tiers, plus remaining Foundation talents and their individual mechanics.
- Profession perks, Mining forge recipe gates, Cooking meal/drink rules, Fishing state machine, and high-level recipe content. Existing recipes were preserved.
- General quest definitions from data; TALK/DELIVER/SURVIVE objectives; categories, journal filters and interactive tracking; configurable journal key binding.
- DialogueGraph choices/conditions/effects runtime integration. Starter quest interaction currently hooks the Old Man interaction directly, without authored choice nodes.
- Placeholder artwork for these future abilities/items has not been added because those items/abilities do not exist yet. Current UI uses the existing assets and text controls.
- All runtime validation and performance measurements remain outstanding.

## Files changed by this foundation

`src/progression/Progression.java`, `src/progression/StarterQuests.java`, `src/progression/Talents.java`, `src/entity/Player.java`, `src/crafting/CraftingManager.java`, `src/ui/GameUI.java`, `src/save/CharacterData.java`, `src/save/SaveStore.java`, `src/world/SlimeNestEncounter.java`.
