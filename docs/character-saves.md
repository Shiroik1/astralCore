# Character saves

Current source builds support **five local character slots**, shared by Play,
Host Game, and Join Game. An empty slot opens the existing name/class/hair-length
creation flow, followed by 40 hairstyles and 20 muted hair colors with mouse controls
and front/side/back previews. An occupied slot continues that character. Character creation and
the versioned save format are separate so future customization can be added
without replacing the slot picker.

## Playing

- Choose a slot with the mouse or W/S / arrow keys. Enter creates or continues.
- Saves run every 30 seconds of game ticks while in play, including when paused.
- Press F5 to save immediately.
- Returning to the title saves before resetting the session.
- Closing the application requests a game-thread save before the window exits.
  If writing fails, the application stays open and reports the failure.
- Delete requires a second confirmation. Escape cancels it.

## What is kept

- Name, class, current appearance preset, level, experience, progression threshold,
  attributes, coins, HP, class resources, and death state.
- Chosen hairstyle and hair-color IDs. Older saves without these fields retain
  their original class hair until a custom choice is made.
- All 20 inventory positions and stack counts, weapon, shield, and seven apparel
  slots, including unequipped or mixed-class appearances.
- Skill cooldowns, status-effect timers, aura expiry, and prepared attack buffs.
- Character position and facing, town/dungeon location, and the position to return
  to when leaving the well.
- Quest stage, kill count, pending class reward, and boss introductions.
- Both the active and dormant area: enemies and HP, boss state/charge, ground loot,
  queued loot awaiting a free ground slot, NPC positions, and opened chests.

Dialogue typing, particles, and live projectiles are transient. A boss introduction
reopens when loading; its acknowledgment can still advance the quest. The existing
shared map files continue to define terrain and scenery placement.

## Co-op

A selected character carries their stats, inventory, and equipment when joining.
The host applies that character on the game thread before publishing the joined
player. Later duplicate join requests cannot reset their progress.

While playing as a guest, personal gains save to the selected local character.
The character's own world and return location stay separate from the host's quest
and map state. Hosting saves the host character's world; other party members are
not persisted as local character slots. A host disconnect saves the guest's latest
received state before resetting the network session.

## Local files and recovery

Saves live under the app's user-data directory, including source builds:

| Platform | Folder |
| --- | --- |
| macOS | `~/Library/Application Support/AstralCore/characters/` |
| Windows | `%LOCALAPPDATA%/AstralCore/characters/` |
| Linux | `~/.local/share/AstralCore/characters/` |

`character-1.json` through `character-5.json` are versioned JSON documents. Writes
flush to a temporary file before replacement. The previous readable generation
is kept in a `.json.bak` file. A corrupt primary can load from that backup, with a
recovery message; loading itself does not overwrite either file. An unreadable
slot remains occupied instead of being treated as empty. A newer format is never
silently loaded from an older backup or overwritten by this version.

The `astral.dataDir` Java property redirects user data, including saves, to a separate folder. Ask the project owner before running any verification or tests.
