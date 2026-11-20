# Persistent death memorials and recovery

Every confirmed non-training death creates one persistent skeleton pile. Actual carried item state moves into that record before the player inventory is cleared. Returning and recovering the final item removes only the bag: the empty skeleton record has no expiry, decay counter or automatic cleanup limit.

## Player-facing behavior

Face the adjacent memorial tile and press **F**, the existing interaction key. A bag with belongings has the normal silhouette outline and a recovery hint. The Lost Belongings panel supports individual retrieval, Take All, paging and closing with Escape. The header shows its saved death day and shortened stable ID; clicking that header cycles recoverable bags occupying the same tile. Only the owner can recover the bag. Recovered bones stay quiet and non-lootable.

The inventory rule includes all 20 bag slots plus separately equipped real weapons, shields and apparel. Placeholder fists/no-shield objects are excluded. Hotbar entries are type shortcuts, not extra items, and are cleared without being dropped twice. Biological hair customization remains player appearance. Coins are a separate currency balance and are retained; chests and unrelated storage are untouched. Training-arena practice characters are excluded from real-world memorial creation.

When inventory space is limited, compatible stacks fill available stack space first, then free slots. Incompatible metadata is never merged. Anything that does not fit remains in the record indefinitely. Individual requests carry stable stored-item IDs, preventing a repeated request from inadvertently taking the next item after a row disappears.

## State and inventory safety

`DeathMemorials.Record` stores UUID, stable owner identity, player name, exact recovery and original death coordinates, chunk coordinates, area, world day, timestamp, recoverable item packets and recovery state. Dungeon and overworld records coexist in the same persistent owner.

`StoredItem` captures the registered item ID, exact quantity, mutable item name/description, power, armor, value, use cost and stacking settings, plus scalar subclass-specific properties. It restores that state through the existing item registry, not through recipes. Immutable asset data and apparel slot/color definitions continue to come from the registered factory/database. Strings in the property payload are encoded safely, including multiline descriptions. The current engine has no separate durability/enchantment-object system; a future nested metadata system will need an explicit serializer extension rather than being silently flattened.

The transfer validates/captures every item first. Then one game-thread operation publishes the record and clears the player's bag/equipment/physical hotbar references. Identity deduplication avoids dropping one object twice if a live reference is shared. The saved character retains the associated memorial UUID, so repeated death updates and loading a dead character cannot create another pile for the same event. Respawn clears that event association while retaining all previous records.

The character save also includes an optional rich carried-item payload so recovered modified equipment does not lose its instance stats on the next save. Older saves without these fields continue using their existing inventory representation. Existing save version 3 remains readable; memorial payloads have their own version 1. Saving captures both the player's cleared inventory and the memorial contents through the existing atomic write/backup procedure.

## Position, chunks and rendering

Normally the pile stays at the exact logical death position. Invalid ground is relocated to a nearby clear point with a legal same-elevation adjacent interaction approach; the original death position remains recorded. The fallback searches nearby same-height ground, then safe lowland if necessary. Checks reuse terrain/elevation, structure, scenery and directional interaction validation. Memorials are non-solid and never destroy existing scenery/buildings.

The manager indexes records by chunk and area. Camera-visible records in active chunks contribute lightweight static actors to the existing entity depth sorter. Leaving the view releases actor wrappers; it does not delete records, contents or empty memorials. No memorial has an AI update. Both building-component placement and station placement reject footprints covering memorial cells. Current deterministic world planning and saved scenery state remain authoritative; no terrain-generation rewrite was introduced.

The palette-adjusted two-state atlas is `res/scenery/memorial/death-piles.png`. Its two 144×144 cells share scale and the same bottom anchor. The import step removes disconnected export speckles and excess padding; the PNG has real alpha, with no checkerboard, ground, animated glow or shadow. Art is preloaded, and existing ambient lighting/depth rendering applies. The bagged cell is used while items remain; the empty cell is used forever afterward.

## Multiplayer

Recovery uses the existing authoritative inventory-action channel, with owner, area, live contents and directional adjacency checked on the host on every transfer. Clients cannot independently remove or create items. Host saves retain every record. Network replicas receive a compressed, cached view of nearby chunk records, so distant permanent history is not repeatedly sent. Network buffers are raised to 2 MiB to accommodate container snapshots. All clients must use the updated build; a live network session was not tested.

## Files changed for this feature

- `src/world/memorial/DeathMemorials.java`: records, spatial index, transfer/recovery transactions, recovery UI, rendering and compressed replica view.
- `src/world/memorial/StoredItem.java`: item-instance persistence.
- `src/art/GenerateMemorialTextures.java`, `res/scenery/memorial/death-piles.png`: palette-adjusted, aligned two-state art and import processing.
- `src/entity/Player.java`: death/respawn event association and inventory transfer hook.
- `src/main/Gamepanel.java`: manager ownership, static draw layers, preloading, local interaction and authoritative inventory actions.
- `src/interaction/InteractionTargetResolver.java`, `src/ui/GameUI.java`: shared directional targeting, outline/hint and recovery-window input/render integration.
- `src/world/building/BuildSystem.java`, `src/crafting/CraftingManager.java`: memorial footprint protection during placement.
- `src/save/CharacterState.java`, `src/save/SaveStore.java`: rich carried state and full persistent-record save/load.
- `src/net/PlayerState.java`, `src/net/WorldSnapshot.java`, `src/net/SnapshotBuilder.java`, `src/net/GameClient.java`: persistent owner/event identity and nearby record replication.
- `src/net/InventoryAction.java`, `src/net/InventoryActionType.java`: stable memorial/item recovery requests.
- `src/net/NetworkRegistration.java`, `src/net/GameServer.java`: container snapshot transport capacity.

## Verification and limits

Compilation passed. A focused headless harness passed 26 checks, including:

- Exactly-once death processing, bag and separate equipment transfer, cleared carried slots and placeholder equipment.
- Full-inventory rejection, partial recovery, complete recovery and repeat-request idempotence.
- Modified item power and multiline description preservation, including after disk load.
- Multiple independent deaths and empty-inventory permanent skeletons.
- Memorial JSON and compressed network-view round trips.
- Real atomic disk save/load with unclaimed inventory and cleared player slots.
- Reloading a dead character without duplicating the memorial.
- Saving/reloading recovered bones without regenerating the bag.
- Retaining records after leaving the visible chunk area.
- Matched atlas dimensions and actual transparent pixels.

The sprite atlas was visually inspected. No live FPS benchmark, extended multi-seed/cliff playtest or live multiplayer session was performed. The records intentionally grow with death history; chunk indexing and cached nearby visual instances reduce active work, but this is not a measured large-history performance result. Nested metadata types added to future items require serializer support. Existing dropped-enemy loot, XP and death animations were not redesigned.
