# Equipment definitions and starter appearance

`res/equipment/items.json` is the editable, versioned equipment database, loaded once by `object.EquipmentDatabase`. It contains 108 definitions keyed by existing `ItemType` IDs. It needs no SQL server.

Each entry defines `id`, `name`, `slot`, `minLevel`, `attack`, `armor`, `hp`, `mp`, and optional hexadecimal `color`. Values are base item statistics; existing character attributes, talents, equipment compatibility and damage formulas still apply. Edit JSON and restart. Adding an entirely new item also requires its normal ItemType factory and artwork/behavior implementation.

`ItemRegistry.create` applies these definitions to existing item factories. Player equipment checks read the catalog's level requirements; equipped torso HP, MP and armor use its definitions. Apparel uses the configured color when present.

New male characters start with Plain Brown Pants; new female characters also wear Plain White Top. Both garments grant zero attack, armor, HP and MP, and no attributes. Hair is cosmetic. Existing saves retain their equipped items; the new item IDs were appended without reordering old IDs.

The sword-equipping crash came from a saved empty skill slot (`-1`) retaining an old Skill object when the character was already in the Warrior profile. Weapon synchronization now rebuilds the action bar, and SwordCombat rejects invalid cooldown indices. A reproduction using the existing saved character passed equip/update/render without writing back to that save. Both new-character appearances, all 108 catalog stat mappings, and empty skill bindings passed focused checks.
