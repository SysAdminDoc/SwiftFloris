# Release v1.8.90 — Surface lost SAF grant for imported sticker folder

Date: 2026-05-17

Follow-up #5 from the v1.8.85 audit pass.

## What changed

The v1.8.77 user-imported sticker folder takes a persistable SAF URI
grant at folder-pick time and stores the URI in `prefs.sticker.userFolderUri`.
If Android revokes the grant later (uninstall + reinstall of the file
manager that issued the grant, system-wide grant cleanup on storage
reset, factory restore patterns), the URI stays in prefs but the IME
can no longer read from it. Previous behaviour: the next `loadPack`
call silently returned an empty pack via `runCatching.getOrDefault`,
the Imported tab vanished, and the user had no signal as to why.

This release:

- Adds [`UserStickerRepository.hasPersistableReadPermission(context, folderUriRaw)`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt)
  which scans `contentResolver.persistedUriPermissions` for a still-valid
  read grant on the stored URI.
- Makes [`loadPack`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt#L45-L66)
  check the grant first; when missing, logs a warning naming the URI so
  the cause shows up in logcat, then returns null (no behaviour change
  on the keyboard side — the pack is still absent).
- Updates [`MediaScreen`](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/media/MediaScreen.kt#L200-L228)
  Settings preference summary: when the URI is set but the grant is
  lost, shows "Folder access lost. Tap to select again." instead of the
  normal "Using {folder}" summary. Tap re-enters the folder picker.

The IME-side recovery (in-keyboard banner / re-pick button inside the
Imported tab) is intentionally deferred — that requires adding a new
Snygg element to the sticker palette, which is bigger scope and worth
its own per-feature release.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/media/MediaScreen.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties` — versionCode 1890 / versionName 1.8.90

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA reproduction:
- Open Settings → Emoji & stickers → Imported sticker folder, pick a
  folder via a known file manager (e.g. Files by Google).
- Verify the summary reads "Using {folder name}" and the Imported tab
  appears in the sticker palette.
- Uninstall the file manager (or use `adb shell pm clear <package>` on
  the file manager).
- Re-open Settings → Emoji & stickers → Imported sticker folder.
  Pre-fix: summary still reads "Using {folder name}" but the Imported
  tab is silently absent in the palette.
  Post-fix: summary reads "Folder access lost. Tap to select again."
  Tap → folder picker reopens; pick again; back to working state.
