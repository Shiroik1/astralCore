# Building assets

Add a named entry to `res/scenery/buildings.json` and put its transparent sprite and masks under `res/scenery/`. The Astravale inn is the working example. Its catalog applies a terracotta roof at load time.

The shop, inn, and both houses share the editable vector source in `src/art/GenerateTownBuildingTextures.java`. Run `art.GenerateTownBuildingTextures` from the compiled game classes to export their PNGs and masks. An optional output directory lets you export a preview set first. The game loads the saved PNGs from `res/scenery/buildings/`.

## Catalog fields

- `image` is the source PNG resource path. `crop` is `[left, top, width, height]` in source pixels. `size` is the cropped sprite's design width and height before applying `scale`. An optional `silhouette` alpha mask clips an opaque source to the building outline.
- `anchor` is the ground point measured from the sprite's top left in design units. `footprint` holds collision rectangles relative to that point. Their front edges also determine when each section draws in front of the player. `door`, when present, holds the interaction rectangle and the horizontal offset of the prompt. The first four values are `[x, y, width, height]`. Optional `shadows` use `[centerX, centerY, radiusX, radiusY]` from the sprite's top left.
- Each `materials` entry has an alpha mask the same size as the source PNG and a `reference` color sampled from the material. Set `color` to give it a default tint, or omit it to keep the source color until a placement overrides it. Use separate, non-overlapping masks for materials such as roof tiles and ivy. Opaque pixels select the material, transparent pixels leave it alone, and partial alpha softens the edge.
- `emissive` takes a mask, a sampled `reference` color, and a glow `color`. The game fades this layer in at dusk. Add a light under `lights` when the feature should also cast a halo onto the world.
- Lights and particle emitters use `position` offsets relative to the anchor, with positive X to the right and positive Y down. The building scale affects those offsets. Light radius, particle size, and particle velocity use the game's 48-pixel tile as their reference. Particle `interval` and `lifetime` ranges are in game ticks. The `smoke` preset uses the running trail's cloud shape with slower rise and fade.

## Place and save

Use `/place building <catalog-id>` followed by `/saveobjects`, or add an entry to `maps/scenery.json`. A placement can override one or more named colors. For example, this scenery entry reuses the inn art as a decorative building with a slate-blue roof.

```json
{"type": "building", "building": "inn", "col": 11, "row": 28, "palette": {"roof": "#7C90CC"}}
```

The stock `inn` placement binds to the same catalog entry and retains its doorway behavior. Generic `building` placements use the catalog art, footprint, lights, and emitters. A new enterable building also needs a destination and interaction in the game.

Each unique building and palette shares a cached sprite. Lights use the existing lightmap. Emitters only create particles near the camera and share a separate capped pool for local scenery effects.

## Town props

`res/scenery/props/` holds the transparent well, lantern, storage, fence, woodpile, and rock PNGs. Run `art.GenerateTownPropTextures` from the compiled game classes to re-export them. The generator accepts an optional output directory. Sizes, ground anchors, and collision footprints are in `src/art/TownProp.java`. The game loads and shares the saved sprite for each prop type.

Place props by their scenery type, for example `/place lantern_hanging`, `/place logs_covered`, or `/place rock_large_moss`.
