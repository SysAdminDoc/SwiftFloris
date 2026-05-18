# Release v1.8.119 — clipboard history maintenance serialization

Date: 2026-05-18

Seventh-pass follow-up roster item G5.

## What changed

Clipboard history maintenance no longer runs sort/filter/eviction work on the main dispatcher, and size-limit / expiry eviction now share one serialized maintenance path.

Previously `initializeForContext(...)` collected the Room history flow inside `withContext(Dispatchers.Main)`, so every history emission sorted and rebuilt `ClipboardHistory` on Main. The same path called `enforceHistoryLimit(...)`, which launched deletion work that caused another Room emission and could repeatedly re-enter the same maintenance logic. The timed expiry job also read `currentHistory` outside any shared history-maintenance lock.

This release keeps Room collection on the existing IO scope, moves history sorting to `Dispatchers.Default`, serializes limit and expiry maintenance through one `Mutex`, and hides rows selected for eviction before the next Room emission arrives. Automatic expiry now uses the same serialized maintenance path as history-size enforcement.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryMaintenance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryEvictionTest.kt`
- `gradle.properties` — versionCode 1919 / versionName 1.8.119

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardHistoryEvictionTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.
