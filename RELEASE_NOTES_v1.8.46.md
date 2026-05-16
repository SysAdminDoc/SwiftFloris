# SwiftFloris v1.8.46 — 2026-05-16

N16.2 — SwiftKey `swiftkey-cloud.json` import support added to
`DictionaryImporter` ahead of Microsoft SwiftKey's 2026-05-31
account-retirement cutoff (15 days from this release) [SK-RETIRE].

## Why ship this now

The upstream `data.swiftkey.com` endpoint that hosts the
`swiftkey-cloud.json` export retires 2026-05-31. After that date the
input data evaporates entirely. SwiftFloris already had a `MIGRATE_FROM_SWIFTKEY.md`
guide describing three paths (Next-6.3, v1.7.9), but the actual
parser for SwiftKey's export shape was the gap. This release closes
it so any SwiftFloris user who has already downloaded their export
can run it through Settings → Personal dictionary → Import without
hand-converting to CSV.

## What changed (user-visible)

**Settings → Personal dictionary → Import** now accepts the JSON
file shape Microsoft SwiftKey hands users from its data export
flow. Two routes work:

1. **Standalone JSON file.** Pick a `swiftkey-cloud.json` (or any
   filename) directly through Android's document picker; the
   importer sniffs the first byte and routes JSON files to the
   SwiftKey parser automatically.
2. **Zip archive containing JSON.** The existing
   `parseZip(stream)` `.json` entry branch now feeds the SwiftKey
   parser instead of being a no-op for FlorisBoard manifest
   files. (FlorisBoard backup manifests don't carry `word`
   entries, so they fall through to an empty list, and the
   sibling CSV/XML in the zip still wins.)

## What changed (internal)

### N16.2 — `DictionaryImportFormat.JSON`

New enum case in `DictionaryImportFormat`. `detectFormat(sniffed)`
returns it when the first non-whitespace byte is `{` or `[`. The
check sits before the CSV branch so a JSON array of strings
containing commas doesn't accidentally route as CSV.

### N16.2 — `parseSwiftKeyJson`

```kotlin
internal fun parseSwiftKeyJson(json: String): List<PersonalDictionaryEntry>
```

Built on `kotlinx.serialization.json` (already in the toolchain).
Algorithm:

1. Parse JSON. On any parse error, return `emptyList()`. The
   importer intentionally doesn't throw here because a FlorisBoard
   backup-manifest JSON sitting in the same zip as a CSV must not
   abort the whole import.
2. Walk every nested `JsonArray` and `JsonObject` recursively.
3. At each `JsonObject`, check whether it carries a "word-class"
   field — any of `word`, `text`, `string`. If yes, lift the
   object into a `PersonalDictionaryEntry`:
   - `frequency`: pick from `frequency` / `count` / `rank`,
     clamped to [0, 255], default 128.
   - `shortcut`: pick from `shortcut` / `expansion`.
   - `locale`: pick from `locale` / `language` / `lang`.
4. If the object has no word-class field, recurse into its child
   arrays and objects (so envelope keys like `predictions`,
   `shortcuts`, `user_data`, `words` work without being hardcoded).

This tolerant walk covers the three envelope shapes most commonly
observed in user-supplied exports:

```json
{ "predictions": [...], "shortcuts": [...] }
{ "user_data": { "predictions": [...] } }
[ { "word": "..." }, ... ]
```

### Tests

10 new cases in `DictionaryImporterTest`:

1. `predictions+shortcuts` envelope (canonical).
2. `user_data` envelope wrapping.
3. Bare array of entries.
4. Missing `frequency` / `locale` defaults (`128` / `null`).
5. Frequency clamping for `-50` → `0` and `9999` → `255`.
6. Malformed JSON returns empty list (not throw).
7. Empty array / empty object / empty `predictions: []` → no entries.
8. Blank/missing word field filtering.
9. End-to-end `import(InputStream)` byte-sniff routing for JSON.
10. `detectFormat` returns `JSON` for `{` and `[` prefixes.

## Versioning

- `gradle.properties`: `projectVersionCode=1846`,
  `projectVersionName=1.8.46`.

## What's next

- **N16.1** — Pre-cutoff outreach: Reddit thread on
  r/SwiftKey + r/HeliBoard + r/FlorisBoard + r/PrivacyGuides
  with the Obtainium URL + `MIGRATE_FROM_SWIFTKEY.md` permalink.
  **Action by 2026-05-29 latest.**
- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual
  trackpad on long-press space).
- **Roborazzi baseline capture** — maintainer-side
  `:app:recordRoborazziDebug` run to commit the first batch of
  baseline PNGs.
