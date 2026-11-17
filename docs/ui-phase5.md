# Phase 5 — Java2D interface and classless creation

The runtime still uses JPanel/Java2D and the existing game thread, player inventory, equipment transactions, crafting registry, combat kits, terrain and building renderer. The standalone JavaFX Builder is unchanged.

## Controls

| Input | Action |
|---|---|
| E, I or K | Toggle Inventory & Crafting |
| C | Toggle Character window; switch from Inventory without stacking windows |
| Tab | Cycle Character / Equipment / Skills tabs |
| Esc or × | Close the active window |
| 1–9, 0 | Select item shortcuts 1–10 |
| Mouse wheel | Cycle shortcuts; scroll the recipe list when Inventory is open |
| Shift + 1–5 | Existing weapon combat abilities |
| F | Existing world interaction / gathering / station use |
| G | Recover a nearby crafting station |
| Inventory drag | Move/merge existing stacks or assign a hotbar shortcut |
| Hotbar drag | Rearrange shortcuts; release outside a shortcut to remove its assignment |
| Right-click shortcut | Remove the assignment, keeping its item |
| Right-click carried item | Equip eligible gear or use a consumable |
| Equipment drag | Equip from the bag, or return equipped gear to a bag slot |
| Right-click equipped item | Unequip into a free bag slot |
| Up / Down, Enter | Select a recipe / craft the selected recipe |
| + / − | Existing batch quantity control, unlocked at Crafting Level 9 |
| P inside Inventory | Place an owned output of the selected recipe |

E previously interacted with the world, and 1–5 previously activated abilities. F and Shift + 1–5 avoid conflicting with the requested inventory/item shortcuts. The game does not currently have configurable action bindings. The appearance editor retains Q/E for preview rotation.

Building rotation, reach, collision, refunds, foliage clearing and resource costs still belong to the building system. Number keys now consistently select item shortcuts, including while building; use the appropriate `/build` command for unrestricted authoring selections.

## Shared theme and scaling

`src/ui/UITheme.java` owns panel opacity, border colors/width, regular fonts, common slots, buttons, padded icons, clamped bars and centered window dimensions. Defaults are 1040 × 560 for major windows, 46-pixel item slots, an 8-pixel gap, a one-pixel logical border and a 150 ms transition. Modify its constants and colors to change the common presentation. `GameUI` lays out individual sections within that frame.

Panels use translucent #17191C, warm off-white borders, muted red HP, muted blue MP and restrained gold selections. The existing JetBrains Mono Regular font is loaded by `main.UI` once and shared through a small font cache. Existing inventory icons are centered with aspect-preserving padding; no item art is regenerated for the UI. Font sizes in component calls are logical viewport pixels.

The existing 1248 × 720 reference viewport and `DisplaySettings.gameViewport` handle window sizes, fullscreen/borderless fallback and high-DPI presentation. Mouse coordinates use the same inverse viewport mapping. Opening/closing windows fade and scale from 0.97 to 1; panel hit testing compensates for that transform. Gameplay input stays captured through the closing fade and until held mouse buttons are released.

The player status panel retains actual HP, MP and existing combat resources. Fill animation updates on game ticks; labels show current authoritative values. Enemy bars appear when damaged or targeted, follow sprite lift, and exclude bosses. Boss selection and encounter locking retain the previous behavior; their shared bar is centered at the top. Chat is raised above the permanent item hotbar, and existing weapon ability cooldowns remain visible beside it during normal gameplay.

## Hotbar references and persistence

`src/inventory/Hotbar.java` stores ten optional `ItemType` references and the selected index. It owns no item stacks. Item types are stable across bag moves, merges, equipment changes and crafting transactions. Selection resolves an existing owned item and calls the existing equip, consume or placement action. Quantities are read from the bag; equipped non-placeholder gear remains a valid reference.

The current item model has no instance affixes or durability, so matching identical item types intentionally share one shortcut and aggregate their bag quantities. If instance-specific properties are added later, replace this matching strategy with persistent item-instance IDs. Exhausted types are cleared. Removing a shortcut never destroys an item. Selection in a modal window changes the highlighted shortcut without starting placement or swapping equipment.

`PlayerState.hotbarTypes` and `selectedHotbar` are included in saves and network snapshots. Restoring happens after rebuilding the actual bag and equipment, which discards stale references safely. Missing hotbar data in older saves becomes an empty hotbar. Assignment, swap, clear and selection requests use the existing authoritative `InventoryAction` route and validate ownership/bounds; no new network inventory container is introduced. Host and guests must run the same build because packet fields have changed. Full live multiplayer sessions have not been verified as part of the headless checks.

## Combined inventory and crafting

`src/ui/GameUI.java` reads the existing 20-slot bag directly and displays all slots in a 5 × 4 grid. Inventory drag, equip, unequip, merge and consumption requests use existing player transactions. Equipment is managed in the Character window's Equipment tab. No inventory capacity was changed.

The crafting section reads the existing `RecipeRegistry.RECIPES`. All / Available / Locked filters and the scrollable list use real levels and authoritative `CraftingManager.craftError`. Selected recipe details show actual output quantities, levels, stations, ingredient requirements and owned amounts. Craft calls the existing transaction and XP/first-time-bonus logic. Failed/full-bag crafts do not consume materials. A short UI click guard avoids repeated activation on a single rapidly repeated press. Crafting retains the existing single-player overworld limitation and station reach rules; opening the combined window elsewhere still allows inventory/equipment management while crafting reports its actual limitation.

Nearby station interaction opens this same combined window. It does not open a second crafting overlay. Recipe search is optional in the request and is not included.

## Character data and classless creation

Name entry now opens appearance customization directly. Gender, hairstyle, hair color and preview rotation remain available. Back returns to naming. The class-choice renderer was removed; previews no longer show a free weapon. New character creation uses the existing default Warrior stat/appearance baseline internally, starts with fists and no bag weapons/tools, and requires no class choice. This UI phase does not invent a new starting-stat or resource table.

The Character tab reads actual name, level, XP, attributes, attack, defense and coins. Equipment uses the existing nine supported slots and their real eligibility/minimum-level rules. Basic swords, bows, staffs and gathering tools no longer have fixed-character-class restrictions. Weapon types choose the existing combat kit through `Player.syncWeaponProfile`; switching never resets character attributes, maximum HP/MP or mastery XP. Existing ability cooldowns are retained while changing weapon kits within a session. Legacy casters switching to a non-mana kit receive its existing 100-point Rage/Focus capacity. Existing equipment bonuses still apply through the normal equip/unequip path.

The Skills tab reads independent Melee, Magic and Bow mastery XP counters plus the existing Level 1–10 Crafting progression, next-level XP and upcoming recipe unlock. Delayed projectiles retain their source mastery when a different weapon is equipped before impact. Existing combat damage, resource and growth formulas are retained.

The legacy serialized `playerClass` field remains an internal combat-kit adapter for save/network compatibility. Older valid characters retain items, appearance, attributes and mastery data. On load the equipped weapon chooses the current kit. Existing legacy kit migrations are retained. No character file is deleted or rewritten just to hide its former class label.

### Underlying features not implemented

- Attribute allocation and available attribute points.
- Combat mastery level curves and combat skill points; actual saved mastery XP is displayed without invented levels.
- Mining, Gathering, Cooking and Fishing progression; their rows are explicitly marked unimplemented.
- A separate gathering stamina mechanic; no survival resource was added.
- New ability unlock trees, recipes or character art.

Advanced spells continue to require the existing mana/resource conditions. Switching to a staff does not grant free MP or replace the saved stat table. Combat and crafting balancing remain separate work.

## Files

| File(s) | Responsibility |
|---|---|
| `src/ui/UITheme.java` | Shared visual configuration and reusable primitives |
| `src/ui/GameUI.java` | HUD, ten slots, combined window, Character tabs, modal input and transitions |
| `src/inventory/Hotbar.java` | Non-owning item shortcuts and existing-action dispatch |
| `src/main/UI.java`, `WindowStyle.java` | Runtime UI integration, common chrome, health bars, class screen removal |
| `src/main/KeyHandler.java`, `MouseHandler.java`, `Gamepanel.java` | Shortcuts, wheel, input capture, authoritative hotbar actions |
| `src/main/CharacterCustomization.java`, `CharacterSlots.java` | Appearance-only creation and class-free save slot labels |
| `src/entity/Player.java` | Weapon-kit selection, classless eligibility, actual preview and retained mastery/cooldowns |
| `src/entity/Entity.java` | Matching compact normal-enemy bars |
| `src/quest/QuestManager.java`, `src/entity/NPC_merchant.java` | Correct F interaction hints |
| `src/entity/CombatHit.java`, `Projectile.java`, `MageEffect.java`, `RangerEffect.java`, `src/crafting/CraftedBolt.java` | Source mastery attribution for in-flight attacks |
| `src/net/InventoryAction.java`, `InventoryActionType.java`, `PlayerState.java`, `SnapshotBuilder.java`, `GameClient.java` | Host-authoritative actions and synchronized shortcut state |
| `src/save/CharacterState.java`, `SaveStore.java` | Optional hotbar restoration, save validation and classless new characters |
| `src/crafting/CraftingManager.java`, `src/interaction/InteractionTargetResolver.java` | Combined station UI and modal input boundaries |
| `src/world/building/BuildSystem.java`, `src/world/GrassEditor.java`, `src/world/WorldObject.java` | Prevent painting through the UI, updated interaction hint |
| `tools/UIPhase5Check.java` | Explicitly requested, standalone headless regression and render checks |

## Validation

Full source compilation and 76 standalone checks passed, including the two requested viewport renders.

The standalone check compiles against the existing `lib/*` jars and runs with `res` on the classpath. Its generated character data and screenshots go to temporary directories, not the player's real saves. It exercises inventory references, drag operations, filters, recipes, capacity rejection, independent mastery, legacy class payloads, equipment, disk saves, packet serialization and viewport renders at 1366 × 768 and 1920 × 1080. It uses actual game entities and rendering, with no replacement inventory or crafting implementation.

The renders can be inspected without a display server. They do not establish live fullscreen switching, high-DPI hardware behavior, audio output or a complete interactive multiplayer gameplay pass; those still require checking on target devices.
