# Ground loot burst and automatic collection

New enemy drops and tool-harvested resources use the existing ground-item array and overflow queue. Trees release four separate logs; rocks, ore and tall grass retain their existing yields. Loose twigs and stones still use directional interaction.

`world.GroundLoot` gives each piece a random outward velocity and a visual hop, then attracts it to the nearest eligible player within 2.5 tiles. Collection occurs only on arrival. At the existing 60 Hz simulation rate, the landing delay is 24 ticks (0.4 seconds). Tune `LAND_TICKS` and `PICKUP_RADIUS_TILES` there. Motion pauses with simulation and inactive chunks.

Movement uses sampled terrain/object collision and logical elevation checks. Loot does not fly through walls or cliffs. Inventory capacity is checked before attraction and again before collection, so full bags leave the items on the ground. Coins/hearts/crystals retain their existing immediate-use behavior on arrival. Inventory-discarded items retain manual pickup rather than immediately returning to the player.

The host performs movement and collection; guests receive positions and visual lift through `ObjectState`. Both peers need the updated build. Loot velocity, landing age and lift are also included in save snapshots and queued-loot saves. Older saves default these optional fields to false/zero; their existing ground items retain manual pickup. Overflow uses the existing persistent queue when all 50 ground slots are occupied.

Integration points: `CraftingManager` creates harvested pieces, `QuestManager.dropLoot` launches and queues drops, `Entity.dropItem` routes legacy enemy drops into that queue, `Gamepanel` advances loot once per simulation tick, and `Player` performs authoritative collection. `Entity.draw` applies visual lift while preserving the grounded shadow. The directional interaction resolver excludes automatic loot.

Source changes have been reviewed without running compilation, automated checks or gameplay tests. Project instructions require owner approval before testing. Pending play checks: tree burst, enemy rewards, full bag/partial stack, wall and cliff obstruction, save/load during flight, overflow, and host/guest collection. Multiplayer area activation follows the existing shared active-area model.
