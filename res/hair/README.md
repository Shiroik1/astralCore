# Pre-exported hair PNGs

The game loads these resources rather than generating hair geometry during play.
`style_00` through `style_39` contain `color_00.png` through `color_19.png`.
`legacy/` contains six older class/preset appearances and the merchant's hair.

Each transparent PNG is **1280 × 640**, with eight **320 × 320** frames:

| Row | Column 0 | Column 1 | Column 2 | Column 3 |
| --- | --- | --- | --- | --- |
| 0 | Front crown | Back crown | Left crown | Right crown |
| 1 | Front rear layer | Back rear layer | Left rear layer | Right rear layer |

Frames cover an 80 × 80 design-unit canvas with origin offset (8,16).
Left/right PNGs have their own baked lighting. The rig scales and positions these
layers, including the small custom tail-sway offset. Decoded PNGs are cached in
memory with a fixed limit.

## Rebuild after editing shapes, colors, or lighting

From the repository root with Java 21+:

```sh
mkdir -p /tmp/astral-hair-build
find src -name '*.java' > /tmp/astral-hair-build/sources.txt
javac --release 21 -encoding UTF-8 -cp 'lib/*' -d /tmp/astral-hair-build @/tmp/astral-hair-build/sources.txt
java -Djava.awt.headless=true -cp '/tmp/astral-hair-build:res:lib/*' art.GenerateHairTextures
```

The exporter accepts an alternate output directory as its first argument and writes
807 PNG sheets. Normal startup, character creation, save loading, and release
packaging never run it. The packager simply copies `res/` into the game JAR.

Authoring lives in `src/art/HairArtGenerator.java` and the legacy helpers in
`src/art/CharacterClothing.java`. Runtime loading uses `HairArt` and `HairTextures`.
