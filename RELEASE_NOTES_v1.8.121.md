# Release v1.8.121 — remove dead clipboard history store

Date: 2026-05-18

Seventh-pass follow-up roster item G9.

## What changed

The unused Tink-backed `ClipboardHistoryManager` path has been deleted.

The live IME clipboard path is `ClipboardManager` backed by Room, provider metadata, media cleanup, backup/restore handling, sensitive-item gates, and the v1.8.119 serialized history-maintenance path. The older `ClipboardHistoryManager` stored only text entries in a separate Tink-encrypted preference payload and was referenced only by an unused `ClipboardHistoryPanel` plus a source-inspection test. Keeping both stores made the codebase imply two clipboard-history backends that could drift.

This release removes the dead manager and unused panel, and reframes the encryption regression test to pin the intended invariant: clipboard history must stay on the Room-backed `ClipboardManager` path and must not reintroduce the parallel Tink preference store.

## Files touched

- Removed `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryManager.kt`
- Removed `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ui/ClipboardHistoryPanel.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/dictionary/PersonalDictionaryEncryptionTest.kt`
- `docs/SECURITY.md`
- `gradle.properties` — versionCode 1921 / versionName 1.8.121

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryEncryptionTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.
