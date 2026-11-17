# Source cleanup and formatting

## Scope

Formatted 275 game Java files and 12 Java tool files using google-java-format
2.0.0 with `--aosp` (four-space indentation). Added or retained function Javadoc
for all 2,980 explicit Java methods and constructors, including overrides and
interface declarations. Comments provide a baseline description of each function;
progression transactions, save operations, combat hit ownership and resource
collection include more specific explanations. This is not an exhaustive design
reference for every algorithm.

Added `.editorconfig` to retain UTF-8, four-space Java indentation and LF line
endings. `tools/format_java.py` reapplies the same format to owned Java files:

```sh
python3 tools/format_java.py /path/to/google-java-format-2.0.0-all-deps.jar --java /path/to/jdk/bin/java
```

The formatter is an external developer tool, not a runtime dependency. The older
1.24.0 formatter does not work with this project's JDK 26. No formatter binaries
or downloaded dependency caches were added to the repository.

## Removed code

- `src/crafting/CraftedBolt.java`: no remaining callers after crafted ranged
  weapons were connected to the Bow/Staff controllers.
- `src/art/SkillArt.java`: no remaining callers after old Warrior setup was retired.
- `Player.setupWarriorSkills`, `strikeFront`, and `loadSkillIcon`: private dead
  branch superseded by the Sword controller.
- `MageCombat.skill`, `load`, and `place`: private helpers left over from the old
  Mage loadout.
- `RangerCombat.backstep`: private entry point no longer used by the Bow loadout.
  Existing replica/backstep state handling was kept for compatibility.
- Commented-out attack-hitbox implementation in Player.

Renamed the ambiguous private Mage helper `Slime` to `targetCenter`. Formatter
processing also cleaned unused imports in the formatted files. Legacy class and
save identifiers, external entry points, public compatibility methods, authoring
utilities and verification tools were retained rather than treating lack of a
normal gameplay caller as proof that they were disposable.

## Assets

Moved five unused former Mage PNGs from `res/skill_icon/mage/` to
`assets/reference/legacy-skill-icons/mage/`. These are no longer runtime resources
but remain reusable artwork as required by the project agreements. No other
asset was proven safely removable: generated resource names, catalogs and
seasonal/reference variants must be accounted for before deletion. Existing
release artifacts, saves and user-authored maps were not deleted.

## Review and limitations

Ran the formatting transformation and read-only source/reference review. A
parser-based documentation inventory counted explicit declarations and their
preceding comments; it did not compile or execute game code. Formatting completed
successfully. No project compilation, automated tests, gameplay verification,
benchmarks or package checks were run because AGENTS.md requires owner approval.
The diff is intentionally broad for formatting/documentation and includes existing
uncommitted gameplay work; no unrelated changes were reverted or committed.
