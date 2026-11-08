# Release screenshots

`CaptureScreenshots.java` captures reproducible scenes with the actual v0.1.0-alpha renderer and assets.
It runs against the built release JAR, sets up reachable gameplay states, and writes unretouched 1248×720 PNGs.
It does not change game source, package contents, or the player's saved settings.

From the repository root, after building the release:

```sh
mkdir -p build/docs-capture
javac --release 21 -cp 'build/release/input/AstralCore.jar:lib/*' -d build/docs-capture docs/tools/CaptureScreenshots.java
java -Djava.awt.headless=true -Dastral.dataDir=/tmp/astral-docs-screenshot-data \
  -cp 'build/docs-capture:build/release/input/AstralCore.jar:build/release/input/lib/*' \
  main.CaptureScreenshots docs/images
```

The commands above use the macOS classpath separator. The Windows separator is `;`.
The README icon is copied from the existing release icon at `build/release/icons/icon-128.png`.

## Publishing v0.1.0-alpha

Commit the README and `docs/images/` before creating the `v0.1.0-alpha` tag. The release notes use tag-pinned
screenshot URLs so the images continue to show the correct version as the game changes.

Suggested release title: **AstralCore v0.1.0-alpha — First playable alpha**.

Copy `docs/releases/v0.1.0-alpha.md` into the release description. Mark the release as a pre-release.
Upload these existing files from `dist/` as release assets:

- `AstralCore_v0.1.0-alpha-macos-arm64.zip`
- `AstralCore_v0.1.0-alpha-windows-x64.zip`
- `SHA256SUMS.txt`

The download links become live once those exact assets are attached to that tag's release.
