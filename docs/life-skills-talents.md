# Life skill activities, profession perks and talent navigation

## Playable additions

- Craft a Fishing Rod at Crafting 2: 3 Twigs + 2 Fiber. Keep it in the bag, face adjacent pond water at the same elevation and press **V**. Wait for the Bite cue, then press V within the reaction window. Pressing early cancels. Movement, attacking, knockback, dialogue or opening a gameplay-blocking UI cancels the cast. A visible line/bobber marks the actual water tile.
- A cast costs 3 Gathering Energy. Successful catches award 12 Fishing XP and commit fish to inventory transactionally; a full bag awards neither fish nor XP. Ordinary catches produce Raw Fish. Fishing 15 adds a 15% River Fish chance; Fishing 18 raises it to 25%. Fishing 3 increases the reaction window, Fishing 10 reduces waiting, and Fishing 20 widens the bite window further.
- At a campfire, cook Grilled Fish (1 Raw Fish, Cooking 1), Hearty Fish Meal (1 River Fish + 1 Fiber, Cooking 5), or Herbal Drink (3 Fiber, Cooking 7). These recipes award Cooking XP, not Crafting or Character XP. Base recipe XP is 10/16/8, diminished by profession level. Fiber is the current simple placeholder ingredient for herbs.
- Meals restore 20/40 HP and grant +20 Gathering Energy capacity for 30 seconds. Herbal Drink restores 15 MP and regenerates 1 MP/second for 30 seconds. Only one meal timer and one drink timer exist: refreshing replaces duration and cannot stack bonuses. Full-health/full-mana refreshes are refused while the corresponding effect remains active. Consumption uses existing bag/hotbar actions.
- Mining now gains XP from existing forge recipes as well as depleted mining nodes. Iron smelting requires Mining 3; existing metal equipment recipes require Mining 5, alongside existing Crafting/station gates. Picking up loose stones still earns Gathering XP, not Mining XP.

## Automatic profession perks

Perks derive from the saved skill level at 5/10/15/20/25. They require no Combat Talent Points and cannot be claimed twice. Character → Skills lists each unlocked and future perk.

| Skill | Five milestone effects |
|---|---|
| Crafting | Material savings increase to 4/8/12/16/20%; rounded down, at least one of every ingredient retained |
| Mining | Hit energy cost 2→1; +1 node yield; 4 fewer swing cooldown ticks; +2 total yield; +1 mining power and another 2 fewer cooldown ticks |
| Gathering | Hit energy cost 2→1; +1 plant/wood yield; 4 fewer cooldown ticks; +2 total yield; +1 tool power and another 2 fewer cooldown ticks |
| Cooking | Meal/drink duration +20/40/60/80/100% |
| Fishing | +1 common/rare catch; shorter wait; rare catches unlocked; longer reaction window; +2 total catches |

Gathering additionally improves Fiber yield at 3 and logs at 5. Tool requirements remain enforced. Existing early recipe ingredient definitions, first-craft bonuses and the Wooden Axe progression are retained. Discounts use actual displayed transactional costs. Profession activities grant no Character XP. Crafting continues using its existing level curve; other life skills use the existing cumulative profession curve to 25.

## Talents

All 36 existing Sword/Bow/Staff talents are retained, with the shared 12-point budget and numerical effects from the weapon reports. The Skills panel now filters talents by selected weapon path and resets paging when changing the selected skill. Cached path lists avoid rebuilding trees per render. Buttons retain character/mastery level and lower-tier investment requirements; hover shows the effect. Life skills show profession perks instead of combat talent buttons. No extra spendable point pool or duplicate talent state was introduced.

## Persistence, art and limits

Food/drink timers are added to the existing version-3 progression DTO with zero defaults for old saves. Perks derive from saved levels; fish, rod and food use stable ItemType names and normal inventory saves. Active fishing is transient and cancels on gameplay interruption; loading never produces a pending free catch. Placeholder fish, rod, meal and drink icons are generated in the existing muted flat icon renderer.

This is a compact first activity set, not all proposed high-level recipes. Equipment reinforcement/repair, ore detection, advanced stations, bait/tackle items, regional fish tables and broader cooking recipes remain future content. Fishing and profession transactions currently support single-player, matching the progression update's multiplayer exclusion. Fishing key V is presently fixed rather than a settings binding. No runtime tests, compilation or gameplay checks were run: AGENTS.md requires owner approval first.

Files: `src/progression/LifeSkills.java`, `src/progression/Progression.java`, `src/progression/Talents.java`, `src/crafting/CraftingManager.java`, `src/crafting/CraftingArt.java`, `src/object/ItemType.java`, `src/object/OBJ_crafted.java`, `src/entity/Player.java`, `src/ui/GameUI.java`, `res/crafting/recipes.json`.
