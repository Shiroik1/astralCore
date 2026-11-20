# Retaliate: shared shout, charge glyph and reactive slash

Retaliate retains Sword level 20, 25 Rage, an 18-second cooldown, six seconds of protection, three charges and 25% reflected attack power. It is not an area attack.

## Activation

Ability 5 now invokes `WarriorShout.start(5)`. It uses exactly the same 24 character poses and 0.15 / 0.30 / 0.15-second preparation, release and recovery as Warcry and Taunt. No Retaliate activation frames were added.

Only its visual ring settings differ: 1.75-tile radius, 0.23-second expansion, 0.10-second linger and 0.18-second fade, with bronze-red `#B78076` and ivory `#F1E8DE`. The existing soft white outline, layered halo and smooth easing renderer are reused. Warcry and Taunt settings are unchanged.

At the one-time shout release, the existing status list receives `warriorRetaliation` with duration 360 ticks and value equal to the remaining charges. Reapplication replaces/refreshes this status. Cost and cooldown commit at skill activation; interruption before release prevents granting the buff and does not refund the committed cost. Interruption after release preserves the buff. Death or unequipping Sword removes it.

## Following glyph

`RetaliationVisual` draws a small ground ellipse centered on the player's current feet, with an inner circle and separated illuminated arcs. Each arc corresponds to one charge. The default three charges produce three arcs; `-Dastral.retaliate.charges=3` configures 1–8 charges and the same setting drives gameplay and arc count.

The glyph gently pulses and rotates. Consumption briefly highlights the spent segment, then dims it over 0.15 seconds. Expiration fades the glyph over 0.20 seconds. One glyph exists per player. It follows camera/player movement and the player's existing elevation rendering transform.

## Incoming hits and reflection

The existing `Player.receiveHit` / `applyHit` path rejects invulnerable, non-damaging and arena-invulnerable events before calling `SwordCombat.received`. Ordinary direct attackers are used as-is; projectile sources resolve their original `Projectile.user`. Only a registered, living hostile monster can consume a charge.

Accepted events decrement one charge, trigger the cosmetic reaction and immediately reflect `max(1, eligibleDamage / 4)` attack power through the existing `damageMonsterOnce` method, with zero extra knockback. Enemy Defense, immunity, defeat handling and kill-based mastery remain in that shared path. Thus the existing enemy immunity/Defense rules can reduce or reject reflected damage. No sword-range query or nearby area hitbox is used. The reaction itself never performs damage, generates Rage, applies Rhythm or triggers Bleed. The existing +5 Rage from taking valid damage remains.

Incoming invulnerability frames and existing enemy/projectile hit tracking prevent repeated contact events. A same-source/same-tick guard also rejects a duplicate reflection callback. Reflection uses a distinct `CombatHit("retaliation")` targeting only the original monster. Monster damage does not invoke player damage-received handling, so it cannot recursively activate Retaliate.

## Reactive character animation

Only confirmed hits trigger `RETALIATION_360_SLASH` presentation. Sixteen cached poses in each existing direction contain a short draw-in, circular hand/sword travel with light torso lean, planted feet and recovery. The reaction lasts 0.18 seconds; its rotating ivory/silver/bronze trail fades for another 0.12 seconds. Sword layering changes as it passes behind and in front of the body.

The reaction briefly takes visual priority over ordinary player poses. The underlying attack, movement and skill states continue advancing; the visual never changes velocity, direction, combat hitboxes or cooldowns. It restores whichever normal state remains legal afterward. The last reaction completes even after the final charge disappears.

Rapid accepted hits resolve reflection and charge consumption immediately. At most one extra visual swing can be pending. Further hits coalesce into that pending visual rather than creating an unbounded animation queue. Network snapshots carry a reaction serial and elapsed presentation time; client rendering does not execute reflection.

## Modified files

- `src/entity/SwordCombat.java`: release activation, charge status, original-attacker resolution and reflection event handling.
- `src/entity/WarriorShout.java`: third visual theme and smaller ring parameters using the shared renderer/poses.
- `src/entity/RetaliationVisual.java`: glyph, segment transitions, bounded reaction queue and cosmetic trail.
- `src/art/CharacterArt.java`: dedicated reactive circle-swing pose family and weapon layering.
- `src/entity/CharacterSprites.java`: cached reactive frames only; activation still reuses shout frames.
- `src/entity/Player.java`: reaction presentation priority, glyph drawing, cosmetic update and death cleanup.
- `src/net/PlayerState.java`, `src/net/SnapshotBuilder.java`, `src/net/GameClient.java`: cosmetic replication.

No additional painted sprite assets are required. The existing procedural body, clothing, Sword and shared impact feedback are reused. Buff/reaction state remains transient; no save format migration was added.

## Verification and performance limits

Java compilation passed. A focused headless harness passed 22 checks covering mastery/cost, delayed release, shared activation, smaller radius, three charges, damage fraction, original projectile ownership, no area damage, no movement/Rhythm, ordinary incoming Rage, duplicate rejection, last-charge completion, queue draining, duration expiry and death cleanup. The existing 17-check shout harness also passed after integration. Four-direction reactive poses were rendered and visually reviewed.

The reaction adds 64 cached 90px frames per Sword appearance (approximately 2 MiB); no per-frame images are generated. Glyph/trail geometry is reused. Only one spare reaction can be queued. The existing registered-enemy array is searched once per accepted retaliation event to resolve the original attacker, never every frame. No additional enemy update loop or world scan was introduced.

Live multiplayer, a save/load round trip and extended combat/chunk testing were not performed. No live FPS benchmark was taken, so these checks do not establish a measured performance change. Existing enemy immunity and damage-feedback behavior remain authoritative.
