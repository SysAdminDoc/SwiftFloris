# Release v1.8.112 — clipboard automatic eviction cleanup

Date: 2026-05-18

Seventh-pass follow-up roster item G6.

## What changed

Clipboard history size-limit rotation and old/sensitive auto-expiry now close provider-backed clipboard items before deleting their Room rows.

The previous manual clear paths called `ClipboardItem.close(context)`, which deletes the app-owned clipboard provider URI and lets `ClipboardMediaProvider` revoke outstanding read grants. The automatic paths in `ClipboardManager.enforceHistoryLimit(...)` and `ClipboardManager.enforceExpiryDate(...)` deleted rows directly, so image/video entries removed by rotation or expiry could leave private files and receiver grants behind.

This release adds `ClipboardHistoryEviction` and routes both automatic deletion paths through `closeThenDelete(...)`. The helper keeps the selection policy testable while guaranteeing that provider-backed media is closed before its history row disappears.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryEviction.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryEvictionTest.kt`
- `gradle.properties` — versionCode 1912 / versionName 1.8.112

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardHistoryEvictionTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog (290 warnings) and a stale lint baseline note, but no errors.
