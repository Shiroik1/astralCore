# Existing gameplay polish

This pass improves existing controls and feedback without new gameplay assets.

- **Attack buffering:** a click during an attack queues one follow-up for up to 15 simulation ticks (0.25 seconds). Every melee class can consume it while stationary; previously the outer input branch only honored a stationary buffer for Berserker/Paladin. Hit pause does not consume the buffer. Suppressed UI input, death, dialogue and boss intros clear it. Old clicks expire instead of unexpectedly firing later.
- **Facing:** directional movement input no longer changes facing during an ongoing basic attack. Facing updates again when the attack finishes, making its animation and damage direction easier to read.
- **Movement:** diagonal normalization happens before integer quantization. Straight movement retains the existing 3.5 pixels/tick average; diagonal axes average approximately 2.475 each, for equal total travel speed. Both live movement and prediction use the input tick, with no new replay state.
- **Impact:** ordinary melee hit pause is three ticks instead of six; damage taken uses five instead of eight. A single CombatHit claims camera/pause feedback once, even when it damages multiple enemies. Per-enemy damage, particles and floating damage numbers still occur. Critical hits remain heavier.
- **Camera:** ordinary hit shake is smaller, and all shake decays quadratically toward rest. UI retains its existing separate rendering position.
- **Gathering:** successful tool hits emit a few existing CHIP particles in wood, leaf or stone colors at the logical resource footprint. Depletion emits a slightly larger burst. Successful collection shows a short quantity/item label at the resource. Existing particle caps apply. Inventory-full failures emit no success effect.

Files: `src/entity/Player.java`, `src/entity/CombatHit.java`, `src/main/Gamepanel.java`, `src/crafting/CraftingManager.java`.

The changes were reviewed in source only. Compilation, automated tests and gameplay verification have not been run for this pass because `AGENTS.md` requires explicit approval. Suggested authorized checks: stationary buffered melee swings, turning while swinging, expired/UI-canceled buffers, straight versus diagonal travel and collision, multi-target cleaves, and harvesting with both available and full inventory space.
