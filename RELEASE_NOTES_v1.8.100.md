# Release v1.8.100 — sticker bitmap LRU + folder-enumeration cap

Date: 2026-05-17

Follow-up F5 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## What changed

### Shared `LruCache<String, ImageBitmap>` for sticker tiles

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt) —
each `StickerPreview` composable previously held a private
`ImageBitmap?` state via `remember(sourceUri)`. When the user scrolled
the grid, tiles entered and exited composition; every entry re-fired the
`LaunchedEffect`, re-opened the SAF input stream, and re-ran the
two-pass decode. For a 240-sticker pack scrolled aggressively the IME
allocated and released hundreds of bitmaps per minute — visible jank on
mid-tier devices and material wakeup pressure on the SAF
content-resolver.

This release adds a module-scoped
`androidx.collection.LruCache<String, ImageBitmap>` keyed by `sourceUri`
with a 64-entry budget (~13 MB at typical sticker sizes; ~32 MB worst
case at the 512 px target edge). The composable seeds its
`remember` state from the cache so the cache-hit path renders the first
frame post-recomposition with no SAF round-trip. Cache-miss path still
runs the two-pass decode, then stores the result.

Object-count-based eviction is good enough; actual heap pressure on the
IME process is dominated by the keyboard atlas, the Compose tree, and
the active dictionary.

### Cursor-time enumeration cap in `UserStickerRepository`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt) —
`queryStickerDocuments` previously materialised every row in the SAF
cursor into a `UserStickerDocument`, sorted them, then dropped all but
the first `MaxStickers` (240). On a 50_000-file Downloads folder this
meant 50_000 allocations (plus their string fields) per pack reload, on
the IME-startup cold path.

Now the cursor walk caps at `MaxStickers * 4 = 960` entries — wider
than the final displayed count so `sortedBy { it.label }` /
`distinctBy { it.sourceUri }` still pick from a richer set, but bounded
hard. A `flogWarning` documents the cap so the user / maintainer can
see why files past the threshold are missing.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt`
- `gradle.properties` — versionCode 1900 / versionName 1.8.100

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA:
- Pick a folder with 240+ PNG stickers. Scroll the Imported tab rapidly
  for 30 seconds; the IME should not show frame drops or memory growth
  on the second pass through the grid (cache-hit). Pre-fix: every
  re-enter re-decoded.
- Pick a folder with 5000+ files. Verify `loadPack` returns within
  ~100 ms and the Imported tab populates. Pre-fix: the IME was tied up
  materialising every row in the cursor.
- Watch logcat for the
  "capped folder enumeration at … entries" warning when the chosen
  folder exceeds `MaxStickers * 4`.
