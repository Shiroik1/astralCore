# AstralCore v0.1.0-alpha packages

`python3 packaging/build_release.py --target all` creates two self-contained portable downloads:

- `dist/AstralCore_v0.1.0-alpha-macos-arm64.zip`: native Apple Silicon `.app`, bundled Temurin Java 21 runtime, game JAR/libraries, player instructions and notices.
- `dist/AstralCore_v0.1.0-alpha-windows-x64.zip`: native Windows GUI `.exe`, bundled Windows x64 Temurin runtime, the same game JAR/libraries, instructions and notices.

Players extract the complete ZIP and open the app/executable. No Java installation is required.
These are portable ZIPs, not installers. Neither has a public publisher signing identity; the Mac build is not notarized.

## Build requirements

Build both packages on Apple Silicon macOS using Python 3.12+ and JDK 21+ with `javac`, `jar` and `jpackage` on PATH.
The build uses the system `curl`, `iconutil` and `ditto`. It downloads the exact Temurin runtimes and Zig compiler
listed in `toolchain-lock.json` and verifies SHA-256 checksums before extracting or executing them.
Zig cross-compiles the small Windows GUI launcher, so Windows is not required to produce that ZIP.
Native Windows execution is separately verified by the included GitHub Actions workflow.

Only the game and its runtimes are shipped; the downloaded compiler and JDK are build-time tools.
The builder preserves Java's bundled legal notices and extracts notices present inside dependency JARs.

## Assets and writable files

The builder compiles for Java 21 and embeds `res/` in the JAR, then overlays the current `maps/` directory.
The live town and grass therefore ship with the release instead of depending on the developer's working directory.
The JAR manifest supplies the dependency classpath. The native launchers always set `astral.packaged=true`.

Installed settings, edited maps and logs live in:

- macOS: `~/Library/Application Support/AstralCore/`
- Windows: `%LOCALAPPDATA%/AstralCore/`

A developer run without the packaged flag keeps the existing working-directory behavior.
`-Dastral.dataDir=/path/to/folder` overrides the packaged data location for isolated tests.

## Checks

Run the packaged launcher with `--verify-install` from an empty folder. This checks runtime loading,
embedded fonts/audio/maps, item artwork, town NPCs and the tutorial dungeon without opening a window.
The flag exits nonzero if required content is missing. `tests/PackagedPathsTest.java` additionally checks
that settings and edited maps remain outside the install directory.

The GitHub workflow builds the archives on macOS and runs the Windows executable on a Windows runner.
It uploads workflow artifacts only; it does not publish a GitHub Release or Pages site automatically.
A real Windows playtest is still appropriate before calling the release verified on Windows.

## Versioning

Update `version.txt` before a new release. The JAR embeds that complete version string; the Mac bundle uses
its numeric prefix for the display version, with a positive-major internal bundle build number. Update the Windows VERSIONINFO numeric fields in the builder
if the numeric version changes. `SHA256SUMS.txt` lists checksums of the produced ZIPs.
