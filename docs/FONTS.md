# Bundled fonts

SwiftFloris ships a small set of script-specific fonts so the IME
renders correctly for languages whose system fallback font isn't
optimised for input-method rendering. All bundled fonts are
**open-licensed** (OFL-1.1 or equivalent) and attributed below.

## Current bundle

| Font | Path | License | Used for |
|---|---|---|---|
| **Noto Nastaliq Urdu** Regular | `app/src/main/assets/fonts/NotoNastaliqUrdu-Regular.ttf` | OFL-1.1 | Urdu Nastaliq positional shaping (ROADMAP §7 L4.2) |

## Download instructions (for fresh contributor checkouts)

The Noto Nastaliq Urdu binary is **not** committed to the repository
(~480 KB binary diff isn't an ideal git citizen). Download from
Google's Noto site before building:

```bash
mkdir -p app/src/main/assets/fonts
curl -L \
  -o app/src/main/assets/fonts/NotoNastaliqUrdu-Regular.ttf \
  "https://fonts.google.com/download?family=Noto+Nastaliq+Urdu"
# Or via Github mirror:
curl -L \
  -o app/src/main/assets/fonts/NotoNastaliqUrdu-Regular.ttf \
  "https://raw.githubusercontent.com/googlefonts/noto-fonts/main/hinted/ttf/NotoNastaliqUrdu/NotoNastaliqUrdu-Regular.ttf"
```

**Without the binary**, `NastaliqFontProvider.bundledTypeface` falls
back to `Typeface.DEFAULT` and Urdu renders in Naskh — the IME still
works, the user just sees the wrong glyph shape. The
`NastaliqFontProvider.isAvailable(context)` predicate lets the Snygg
theme selectors skip the `font-family` override when the file is
missing.

## OFL-1.1 attribution

```
Copyright 2013-2024 The Noto Project Authors
(https://github.com/notofonts/nastaliq)
Licensed under the SIL Open Font License, Version 1.1.
```

Full OFL text: <https://scripts.sil.org/cms/scripts/page.php?item_id=OFL_web>.

## CI handling

The Github Actions release workflow runs the download step inline
before `./gradlew :app:assembleRelease` so the bundled APK always
ships with the binary present. The build's `app/src/main/assets/`
directory is otherwise un-managed (no symlinks, no submodules) so
the OFL binary lives as a regular file in the workspace once
downloaded.

## Adding a new bundled font

1. Confirm the font is **OFL-1.1 / Apache-2.0 / CC0** (anything
   copyleft on the font itself disqualifies).
2. Add the file path + license to the table above.
3. Provide a `*FontProvider` Kotlin singleton in the same style as
   `NastaliqFontProvider`.
4. Wire it into the Snygg stylesheet selectors that need it.
5. Add to the CI download step.
