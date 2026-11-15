# Reference chibi character base

An editable, reference-inspired 3D base and prerendered sprites for AstralCore.
Made from the supplied front/side character reference. The unseen back, depth,
and animation poses are artistic interpretations, not an exact reconstruction.

## Deliverables

- `character_base.blend`: continuous skin mesh, separate eye meshes, seven-bone
  deformation rig, three saved animation actions, orthographic camera, materials,
  and soft lighting. Open in Blender 5.2 or newer. Select `Character_Rig` and choose
  `idle`, `run`, or `attack` in the Action Editor to edit an animation.
- `character_base.glb`: portable skinned model with all three animations.
- `frames_90/`: 80 transparent PNG frames on fixed 90 × 90 canvases.
- `renders_180/`: the same frames at 180 × 180 for further finishing.
- `sheets_90/` and `sheets_180/`: one sprite sheet per animation. Columns are
  animation frames; rows are **down, up, left, right**.
- `manifest.json`: dimensions, ground anchor, paths, and suggested playback timing.
- `model_preview.jpg`, `asset_preview.png`, `animation_preview.gif`: visual previews.
- `validation.json`: frame-count, dimensions, transparency, and clipping checks.

## Animation and game fit

Each direction has 4 idle frames, 8 run frames, and 8 unarmed attack frames.
Idle and run loop; attack is a one-shot with its strike at index 4.
Suggested frame durations are 200 ms for idle and 83 ms for run/attack.
The Blender idle action spans 24 frames at 12 fps, sampled into four sprites;
adjust sprite timing to taste rather than treating it as identical playback.

The model faces -Y in Blender, with +Z up. The sprite camera looks slightly down
at 10 degrees. All exports keep the same framing; the projected ground origin
is (45, 85.5) in the 90-pixel canvas, near the current game's 95% foot baseline.
No floor shadow is baked into these images because the game supplies its own.

This is a simple stylized base rig: arms and legs each have one deform bone.
It does not include fingers, elbow/knee articulation, facial expressions,
clothing, weapons, textures, or modular equipment passes. Materials are editable
solid colors. Future clothing should be fitted and rendered from the same rig,
camera, poses, and canvases; current 2D outfit layers may require adjustment.

These assets are **not wired into the Java character loader**. The game currently
builds its base sprites procedurally. These exports follow its idle/run/attack
direction naming and frame counts for a later explicit integration.

## Rebuild

From this directory on this Mac:

```sh
/Applications/Blender.app/Contents/MacOS/Blender --background --python build.py -- --all
python3 package_assets.py
```

The packing step requires Pillow. Rendering needs no Blender add-ons, external
textures, or network access. Running `build.py` without `--all` regenerates just
the model files and large transparent previews.

The original reference is included as `reference.png` for design comparison.
Its inclusion does not grant rights beyond those attached to the supplied image.
