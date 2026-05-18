# Release v1.8.122 — harden KenLM mmap reader offsets

Date: 2026-05-18

Eighth-pass NLP / autocorrect / suggestion audit closure for the concrete KenLM-reader finding found during the local re-audit.

## What changed

`KenLmTrieReader.readBytesAt(...)` now treats its `offset` argument strictly as an absolute file offset. Requests before `bodyStartOffset` return `null` instead of being coerced to mapped-body offset zero, so header/pre-body reads cannot accidentally alias to trie-body bytes.

The reader also guards offset arithmetic overflow, avoids `FileChannel.size().toInt()` overflow while reading the fixed 256-byte header probe, and rejects bodies too large for the single `MappedByteBuffer` path. This keeps malformed or very large KenLM-shaped files on the safe fallback path.

The README clipboard summary was corrected to describe the current Room-backed clipboard history path after v1.8.121 retired the legacy Tink preference store.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/kenlm/KenLmTrieReader.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/kenlm/KenLmTrieReaderTest.kt`
- `README.md`
- `gradle.properties` — versionCode 1922 / versionName 1.8.122

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.kenlm.KenLmTrieReaderTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.
