# Character creation and hair

The creation flow is **slot → name → class/preset → hairstyle/color → create**.
All navigation and selection buttons work with the mouse; typing a name still uses
the keyboard. The slot picker supports create, continue, and confirmed deletion.

The appearance screen has two reference collections of 20 styles each. Every style
is available with either preset and every class. Click a tile, a color swatch, or
Front / Side / Back to inspect it. Back returns to class selection and preserves
the selected hair. Hair is previewed without headwear; Mage hats remain separate
equipment in the game.

Keyboard alternatives: A/D or left/right change hair, W/S or up/down change color,
Q/E rotate, Tab switches collections, Enter creates, and Escape returns to class.

## Reference mapping

IDs are stable: 0–19 follow male reference 01–20, and 20–39 follow female reference
01–20. Front silhouettes were individually authored in vector curves from the
supplied sheet. Side and rear views are interpretations of those designs, retaining
their lengths, ties, braids, and accessories. Authoring uses native Java2D shapes;
runtime uses exported PNG layers and the existing sprite cache, without the reference image.

| Reference | Male collection | Female collection |
| --- | --- | --- |
| 01 | Messy | Short Bob |
| 02 | Straight | Hime Cut |
| 03 | Short Spiky | Layered Bob |
| 04 | Side Part | Side Bangs |
| 05 | Curtain | Twin Short |
| 06 | Bowl Cut | Wavy Bob |
| 07 | Shaggy | Curly |
| 08 | Wavy | Braided Crown |
| 09 | Undercut | Headband |
| 10 | Slicked Back | Hair Clips |
| 11 | Low Ponytail | Low Ponytail |
| 12 | Mid Ponytail | Side Ponytail |
| 13 | Top Knot | Twin Tails |
| 14 | Braided | Braided Tail |
| 15 | Wolf Cut | Side Braid |
| 16 | Long Straight | Half-Up |
| 17 | Long Wavy | High Ponytail |
| 18 | Half-Up | Long Straight |
| 19 | High Ponytail | Long Wavy |
| 20 | Layered Long | Twin Braids |

## Color and persistence

The shared low-saturation palette contains Espresso, Chestnut, Cocoa, Ash Brown,
Sand, Honey, Cream, Copper, Rosewood, Dusty Rose, Mauve, Lilac, Plum, Slate, Mist
Blue, Sage, Sea Glass, Silver, Pearl, and Charcoal. Each uses the same subtle
top-left white highlight and darker bottom-right shading as the character art.

`Player.hairStyle` and `hairColor` are personal appearance choices. Existing hair
equipment still controls whether hair is visible: removing it exposes the bald
base, and re-equipping it restores the chosen cut/color. These choices are included
in save files, co-op join requests, and appearance snapshots. Legacy saves default
to -1 for both values and preserve their old appearance.

`art.Hairstyles` defines IDs, labels, palette, and outfit application.
`art.HairArtGenerator` contains crown, rear, profile, braid, bow, and accessory
geometry for the explicit offline `art.GenerateHairTextures` exporter. Runtime
`art.HairArt` loads PNG layers through `art.HairTextures`. The immutable
outfit snapshot includes the style ID, keeping different appearances isolated in
the existing bounded character cache. UI preview images also use a bounded cache.

`HairCustomizationTest` checks all 40 front designs, 6,400 animation frames in both
display modes against the existing frame bounds, color variants, equipment
removal, save migration, disk restart, and co-op serialization.
`CharacterCustomizationMouseTest` exercises the actual creation hit targets from
the title menu through continuing a customized save.

The 807 PNG sheets live in `res/hair`, including the older hair presets and merchant.
They are generated once and loaded in gameplay, character previews, and thumbnails.
See `res/hair/README.md` for their layout and rebuild command. The ordinary release
packager copies them into the game without generating them again.
`art.HairPngRuntimeTest` also runs with both offline generator classes removed from
the resource JAR, verifying that gameplay requires only the PNG assets.
