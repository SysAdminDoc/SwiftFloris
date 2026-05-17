# SwiftFloris v1.8.50 — 2026-05-17

N17.1 — Emoji-picker crash triage hardening.

## Why ship this now

[GH-SWIFTF-ISSUE-1] reported "tap an emoji from the palette → IME
process death". The v1.8.46 FlorisEmojiCompat audit (matrix #5) ruled
out the EmojiCompat code path itself — every call site is null-safe.
The remaining triage pointed at the `Paint.hasGlyph(...)` glyph-probe
that `EmojiPaletteView` runs during the initial filter pass.
`Paint.hasGlyph("")` throws `IllegalArgumentException("hasGlyph called
with empty string")` and aborts the palette render. The two paths
that can leak an empty-value `Emoji` into the pipeline are the
history-deserialisation round-trip and a malformed bundled-asset
line.

## What changed

### Palette filter defends against empty values

- `EmojiPaletteView.emojiMappings` now skips any `emoji.value.isEmpty()`
  before calling either `EmojiCompat.getEmojiMatch` or
  `Paint.hasGlyph(...)`. Both functions reject empty input — the first
  via a documented invariant, the second with the historical
  `IllegalArgumentException`.
- `EmojiPaletteView` history mapping (lines that wrap
  `prefs.emoji.historyData` pinned / recent lists into `EmojiSet`s)
  now filters out empty-value entries before constructing the grid
  so the recently-used tab cannot render invisible / commit-empty
  tap targets.

### Asset loader rejects blank-value rows

- `EmojiData.loadEmojiDataMap` now skips a data line whose first
  column (the codepoint value) trims to empty. Bundled assets
  shouldn't ever carry such a line, but a future contributor or
  third-party addon-supplied asset can no longer crash the IME this
  way.

### Tests

- New `EmojiHistoryEmptyValueTest` pins the four contract layers:
  the value-only serializer's permissive round-trip, the
  EmojiHistory deserialiser tolerating a stored empty-value entry,
  the EmojiSet constructor still accepting a wrapped empty-value
  Emoji (the responsibility lives on the consumer side), and the
  palette's empty-value filter snippet replicated in pure Kotlin
  so the regression can be caught without Robolectric.

## Versioning

- `gradle.properties`: `projectVersionCode=1850`,
  `projectVersionName=1.8.50`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK on
the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The targeted runtime repro (touch-paste an emoji from a
hand-corrupted `emoji.historyData` pref) needs an emulator or
device session; the unit test covers the deterministic boundary
contracts the palette pipeline relies on.

## What's next

Continuing through the §6 NOW queue: N14.3 (Compose BOM refresh
audit), N14.4 (Gradle wrapper bump audit).
