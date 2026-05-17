# SwiftFloris v1.8.53 — 2026-05-17

Phase A2 — Post-import confirmation + rollback for the personal
dictionary, plus the long-standing wiring of `DictionaryImporter`
(the SwiftKey JSON / Gboard XML / CSV / zip parser shipped v1.8.46)
into the Settings UI.

## Why ship this now

`DictionaryImporter.parseSwiftKeyJson` has existed since v1.8.46 but
the Settings → Personal dictionary → Import button still routed
through the legacy `importCombinedList` (FlorisBoard semicolon-
key=value format) — so users who picked their `swiftkey-cloud.json`
file from the picker hit "Could not import user dictionary: ...".
With 14 days remaining until the SwiftKey-account cutoff (2026-05-31),
**A2 also has to close that wiring gap**, not just add the
confirmation sheet the parity roadmap called out.

## What changed

### Wire `DictionaryImporter` into Settings

- The personal-dictionary import flow now tries the modular
  `DictionaryImporter` first (byte-sniff routing to JSON / XML / CSV /
  zip).
- On any `DictionaryImportException` — including "unknown format" —
  the flow falls through to the legacy `importCombinedList` so
  existing FlorisBoard `.combined` backups keep importing unchanged.
- Any other thrown error surfaces as the existing failure toast.

### `PersonalDictionaryImportBatch` orchestrator (new)

Pure-Kotlin bridge between the parser's `List<PersonalDictionaryEntry>`
output and the `UserDictionaryDao`. Implements a snapshot-and-diff
pattern so the rollback target list is the exact set of rows that
were newly inserted, not a guess.

- `import(parsedEntries, dao, format) → PersonalDictionaryImportResult`
  - Snapshots the DAO's known ids before the insert pass.
  - For each entry: if `(word, locale)` already exists, calls
    `dao.update(...)` with the new freq / shortcut (NOT
    rollback-eligible); otherwise calls `dao.insert(...)`.
  - Re-reads after the pass and diffs against the before-set to
    identify the new ids.
  - Frequencies clamped to `[FREQUENCY_MIN, FREQUENCY_MAX]` instead
    of skipped.
  - Blank `word` entries skipped and counted separately.
  - Malformed locale tags do NOT abort the batch — `FlorisLocale.fromTag`
    is wrapped in `runCatching`.
- `rollback(result, dao) → Int` deletes only the rows whose ids are
  still present, returns the deleted count. Idempotent (tolerates a
  manual delete between import and rollback) and bounded (in-place
  updates are intentionally NOT rolled back because the previous
  freq / shortcut is no longer available).

### `PersonalDictionaryImportResult` (new data class)

Carries the rollback-eligible id list, the in-place-updated count,
the skipped count, the total parsed count, and the detected source
format. Helper flags: `noChanges`, `isRollbackable`.

### `PersonalDictionaryImportSummaryDialog` (new Compose UI)

Surfaces after a successful modular import with:

- Title: **Import complete**.
- Up to four summary lines (inserted / updated / skipped / no-changes).
- Source-format line ("Imported from: SwiftKey JSON export", etc.).
- **Keep imported words** (primary, dismisses).
- **Undo import** (secondary; hidden when `result.isRollbackable` is
  false so users don't tap it and see "Removed 0 imported words").

Wires straight into `UserDictionaryScreen` via a new
`importSummary: PersonalDictionaryImportResult?` state slot.

### New string resources

`settings__udm__import_summary__title` and 13 sibling strings cover
the dialog text and format labels.

## New tests

`PersonalDictionaryImportBatchTest` (10 cases):
- empty input → no-op result;
- new entries → all inserted, all rollback-eligible;
- entries already present at `(word, locale)` → updated in place, NOT
  rollback-eligible;
- blank words skipped;
- out-of-range frequencies clamped;
- rollback deletes only newly-inserted ids;
- rollback idempotent (tolerates manual delete between import + undo);
- rollback no-op when nothing was inserted;
- shortcut + locale round-trip preserved;
- malformed locale tag falls back to null-locale insert (no crash).

Uses an in-memory `FakeUserDictionaryDao` that throws on the DAO
methods the batch shouldn't call, so a future regression that starts
using `query(word)` or `queryShortcut` gets flagged.

## Versioning

- `gradle.properties`: `projectVersionCode=1853`,
  `projectVersionName=1.8.53`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The new dialog should be hand-tested by picking a real
`swiftkey-cloud.json` from the document picker on a device.

## What's next

Phase A3 (v1.8.54) — Encrypted-blob export option on the personal
dictionary so users can carry their learned vocabulary off the device
through any user-chosen channel without a plain-text CSV intermediate.
