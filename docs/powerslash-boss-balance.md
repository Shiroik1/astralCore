# Powerslash and boss balance

Powerslash shares Basic Slash's raise/hold/three-tick swing/end/recovery clock,
cached body poses, and facing-tile hit footprint. It keeps its existing Rage
cost, cooldown, mastery unlock and damage/status bonuses. Its white crescent
uses Palette.PLASTER with a thick Palette.BRICK border and Palette.ROOF_CORAL sparks; twelve sparks launch from the forward footprint
at swing start and ten more burst from each successfully hit target. Particle
creation happens on simulation events. The empowered visual is replicated in
player snapshots.

Weapon mastery remains kill-only. Rewards now scale with enemy strength;
see [progression-and-skill-bars.md](progression-and-skill-bars.md) for the current
balance, saved action bars and corrected icon crops. The previous fixed +3
kill reward and swordHitXP override have been superseded.

Normal Slime King and overworld Crowned Slime now use:

| Setting | Default |
|---|---:|
| HP | 1400 |
| Defense | 9 |
| Slam / base attack | 64 |
| Spit damage | 36 |
| Crowned radial damage | 28 |

The older King previously had 50 HP; Crowned Slime had 220 HP and zero defense.
Their existing warnings and attack patterns remain. Hell Slime retains its
own balance. Level 15+ is the intended preparation target, not an enforced
level lock or a guarantee of victory. Skilled lower-level players can still
attempt the fight. Real-time encounter balance has not been playtested.

Configure defaults in `monster/SlimeCombatConfig.java`, or override through
`astral.slime.bossHP`, `bossDefense`, `slamDamage`, `spitDamage`, `radialDamage`
JVM properties. On loading an old normal-boss snapshot with the original
50/220 maximum HP, health is migrated proportionally. Dead bosses are not
revived and defeated encounters are not regenerated.

Validation: full source compilation; a headless action check for Powerslash's
three smear ticks; Sword XP increase and duplicate-reward cooldown checks;
constructor checks for both normal-boss balance settings. No live difficulty,
multiplayer session, or frame-rate benchmark was performed.

Latest validation: full compilation and six headless integration checks covering
nonlethal hits, mixed-weapon kill attribution, duplicate death, boss reset,
duplicate hit rejection, and the three-XP Sword kill reward.
