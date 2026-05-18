# Release v1.8.101 — in-keyboard banner for SAF lost-grant

Date: 2026-05-17

Follow-up F7 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).
Mirror of the v1.8.90 Settings-side surface, now applied to the
in-keyboard sticker palette as well.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt) —
the Imported sticker tab previously vanished silently when Android
revoked the SAF persistable read grant for the user-picked folder
(e.g. the file manager that issued the grant was uninstalled, the
system cleaned up old grants, the user did a factory pattern restore).
The user had no in-keyboard signal that the pack was recoverable.

This release distinguishes three states inside the palette
`LaunchedEffect`:

| `userFolderUri` | `hasPersistableReadPermission` | Behaviour |
|---|---|---|
| blank | n/a | No Imported tab (unchanged) |
| non-blank | true | Pack rendered (unchanged) |
| non-blank | false | **Empty placeholder pack so the tab stays visible**, plus a Snygg-styled `MediaEmojiSubheader` row reading "Imported folder access lost. Open Settings → Emoji & stickers to re-pick." |

The tab stays present + selectable in the third state so the user gets
a clear actionable signal. Tapping the warning is intentionally NOT
wired to a deep-link to Settings: the IME view runs in a different
process surface and launching Settings activities from it has
historically been fragile across Android versions / OEMs; the message
instead points the user to the deterministic re-pick path that already
works (`Settings → Emoji & stickers → Imported sticker folder`, which
the v1.8.90 surface already surfaces with the matching "Folder access
lost" preference summary).

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties` — versionCode 1901 / versionName 1.8.101

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA reproduction:
- Pick an Imported sticker folder via the file manager. Verify the
  Imported tab appears with stickers (unchanged baseline).
- Uninstall the file manager (or `adb shell pm clear <file-manager-pkg>`).
- Open the keyboard → Stickers palette.
  - **Pre-fix:** Imported tab is absent; user has no signal.
  - **Post-fix:** Imported tab is present; tapping it shows "Imported
    folder access lost. Open Settings → Emoji & stickers to re-pick."
  - Walk through Settings → Emoji & stickers → re-pick. Tab repopulates
    with the new pack.
- Verify bundled sticker packs remain unaffected throughout.
