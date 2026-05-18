# Release v1.8.116 — clipboard startup storage reconciliation

Date: 2026-05-18

Seventh-pass follow-up roster item G3.

## What changed

Clipboard startup now reconciles provider-backed media history with the private clipboard file store.

Previously, a destructive Room migration or any drift between `clipboard_history`, `clipboard_files`, and `noBackupFilesDir/clipboard_files` could leave orphaned media files behind forever, or leave history rows pointing at provider files that no longer existed. This release adds `ClipboardStorageReconciliation`, runs it before collecting clipboard history, deletes provider-backed history rows whose stored file is missing, removes stale `ClipboardFileInfo` rows, and deletes stored provider files that no history row references.

Restored media files that still exist but lack `ClipboardFileInfo` metadata are intentionally preserved here; rebuilding those metadata rows is the separate G4 restore-path slice.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardStorageReconciliation.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardStorageReconciliationTest.kt`
- `gradle.properties` — versionCode 1916 / versionName 1.8.116

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardStorageReconciliationTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.
