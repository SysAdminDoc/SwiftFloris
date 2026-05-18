# Bundled fonts

SwiftFloris ships a small set of script-specific fonts so the IME
renders correctly for languages whose system fallback font isn't
optimised for input-method rendering. All bundled fonts are
**open-licensed** (OFL-1.1 or equivalent) and attributed below.

## Current bundle

| Font | Path | License | Used for |
|---|---|---|---|
| **Noto Nastaliq Urdu** Regular | `app/src/main/assets/fonts/NotoNastaliqUrdu-Regular.ttf` | OFL-1.1 | Urdu Nastaliq positional shaping (ROADMAP §7 L4.2) |

## Source and routing

The Noto Nastaliq Urdu binary is committed to the repository as an APK
asset. It was sourced from the official Noto Nastaliq hinted TTF build:

- <https://notofonts.github.io/nastaliq/fonts/NotoNastaliqUrdu/hinted/ttf/NotoNastaliqUrdu-Regular.ttf>

`NastaliqFontProvider.bundledTypeface` loads the asset lazily with
`Typeface.createFromAsset`; if a custom build removes the binary, it
falls back to `Typeface.DEFAULT` so the IME still renders. The keyboard
renderer only applies the Compose `FontFamily` override when the active
subtype language is Urdu (`ur`) and the key label/hint contains
Arabic-script code points. Latin labels and non-Urdu Arabic/Persian
subtypes continue to use the active Snygg theme font.

## OFL-1.1 attribution

```
Copyright 2022 The Noto Project Authors
(https://github.com/notofonts/nastaliq)
Licensed under the SIL Open Font License, Version 1.1.
```

Full OFL text: <https://scripts.sil.org/cms/scripts/page.php?item_id=OFL_web>.

## Adding a new bundled font

1. Confirm the font is **OFL-1.1 / Apache-2.0 / CC0** (anything
   copyleft on the font itself disqualifies).
2. Add the file path + license to the table above.
3. Provide a `*FontProvider` Kotlin singleton in the same style as
   `NastaliqFontProvider`.
4. Wire it into the renderer or Snygg stylesheet selectors that need it.
5. Add an asset-presence/license test.
