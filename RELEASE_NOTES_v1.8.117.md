# Release v1.8.117 — clipboard restore media metadata

Date: 2026-05-18

Seventh-pass follow-up roster item G4.

## What changed

Clipboard media restore now recreates the provider metadata rows needed to serve restored images and videos.

The backup flow copied provider-backed media files and serialized the matching `ClipboardItem` rows, but restore only copied the files back into `clipboard_files`. It did not reinsert the corresponding `ClipboardFileInfo` rows, leaving restored item URIs pointed at IDs missing from the provider database. This release creates replacement metadata from the restored clipboard item and file size, writes it with conflict replacement, and lets `ClipboardMediaProvider` lazy-load metadata from Room on cache misses so restored clips work without an app restart.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardRestoredFileInfo.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardRestoredFileInfoTest.kt`
- `gradle.properties` — versionCode 1917 / versionName 1.8.117

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardRestoredFileInfoTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.
