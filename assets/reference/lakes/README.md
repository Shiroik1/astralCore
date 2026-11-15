# Lake tile design

A tile study matching AstralCore's existing vector-drawn terrain. These exported
PNGs remain a design reference; the game now draws the same lake artwork directly
for `WATER` terrain at the forest healing pond west of town.

## Art direction

- Muted blue water mixed from the existing `Palette.WATER`.
- Rounded shore shapes built with the same shape system as grass and paths.
- Thin earthy banks, pale mint shallows, and a small white top-left edge highlight.
- Low-contrast water sheen and sparse rounded ripples.
- Optional lily pads, including a soft pink flower.

## Files

- `lake_scene.png`: an assembled 18 × 12 tile lake over the game's grass, with a
  character for scale. The island demonstrates inward shoreline corners.
- `tile_study.png`: labeled connecting shapes and material/decor samples at 2×.
- `lake_atlas_v0.png`: calm water, `v1`: one ripple, `v2`: two small reflections.
  Each atlas is 384 × 288 with eight columns and six rows. The final cell is empty.
- `tiles_48/`: 141 individual transparent 48 × 48 PNGs (47 shapes × 3 variants).
  Draw these over existing grass; transparent areas are land.
- `decor/`: two separate transparent lily-pad overlays.
- `manifest.json`: atlas positions and neighbour-mask bits.
- `GenerateLakeAssets.java`: reproducible export and preview generator.
- `../../../src/art/LakeTiles.java`: editable code-native vector geometry and palette.

The three variants are static alternatives, **not animation frames**. Select them
sparsely to avoid covering the lake with repeated ripples. The shoreline has 47
canonical masks: diagonal water connections only apply when both adjacent
cardinal connections are also present. Mask 255 is continuous interior water.

## Rebuild

From the repository root (Java 21+), with compiled game classes on the classpath:

```sh
javac --release 21 -cp '/tmp/astral-compile:lib/*' -d /tmp/astral-compile assets/reference/lakes/GenerateLakeAssets.java
java -Djava.awt.headless=true -cp /tmp/astral-compile GenerateLakeAssets assets/reference/lakes
```

The exporter checks the 47-shape mask count, reusable tile cache, and exact
opposite-edge equality for calm interior water. In the game, `~` marks impassable
water in `maps/terrain.txt`; a `healing_pond` scenery object on the bank provides
the E interaction to restore HP.
