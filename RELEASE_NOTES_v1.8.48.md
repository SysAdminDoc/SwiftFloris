# SwiftFloris v1.8.48 — 2026-05-17

Defensive hardening pass across importers, MCP bridge, IME service
lifecycle, voice-model install, ZIP handling, and DB cursor
management. No user-visible behavior changes; every fix closes a
specific failure mode that an adversarial / malformed input or a
mis-ordered teardown could otherwise trigger.

## Why ship this now

The v1.8.43–v1.8.47 stack moved fast across MCP, glide replay, and
Roborazzi wiring. An end-to-end audit surfaced a cluster of latent
correctness and trust-boundary issues in code paths that recently
gained third-party-input surfaces (SwiftKey/Gboard importer, MCP
daemon bridge, Tasker dispatcher, voice-model installer). All of
them are small fixes individually and dangerous together, so the
batch lands as one slice.

## What changed

### Importer hardening (`DictionaryImporter`)

- UTF-8 BOM-bearing JSON / XML / CSV exports (Notepad, Excel) now
  route to the correct parser instead of falling through to
  `UNKNOWN`. `detectFormat` strips the BOM before pattern matching;
  `parseCsv` strips it from each row too.
- CSV header detection no longer drops the first row when the user's
  dictionary literally contains the word `word`. Header presence
  now requires column 2 to be `frequency` (case-insensitive), not
  just column 1 to start with `word`.
- `parseGboardXml` now decodes XML numeric character references
  (`&#233;`, `&#x42;`) that Android's own UserDictionary exporter
  emits for non-ASCII code points. Surrogate range validation is
  enforced.
- `parseZip` now bounds total bytes read across the whole archive,
  not just per-entry — a 256-entry × 16 MiB archive could previously
  push 4 GiB through the importer before the per-entry cap fired.
- Empty-result error messages distinguish "saw a candidate file but
  no entries recognised" from "no candidate files in archive".
- Removed dead `if (i > raw.length) break` in attribute parser;
  `var found` → `val found`.

### MCP hardening (`AndroidMcpClient`, `McpServiceConnectionManager`)

- `AndroidMcpClient` parameter-size cap now compares UTF-8 bytes
  instead of character length. A daemon-side proxy could previously
  smuggle a payload past the cap with 4-byte UTF-8 code points
  whose UTF-16 length is half the byte length.
- `AndroidMcpClient` now bounds the daemon's response size with the
  same `MAX_PAYLOAD_BYTES` cap. A malicious or buggy daemon can no
  longer force a multi-megabyte UTF-16 allocation just to throw it
  away on the decode line.
- `AndroidMcpClient` validates that the daemon echoed the correlation
  id the IME issued. A mismatched id (stale response, intentional
  spoofing) is rejected with `TOOL_INTERNAL_ERROR`.
- `McpServiceConnectionManager.onBindingDied` is now bounded to
  three rebind attempts per daemon; beyond that we log and stop
  trying until the next manual bind. Successful `onServiceConnected`
  resets the counter. The rebind itself runs through `runCatching`
  so a SecurityException from a freshly-uninstalled daemon does
  not escape into the system's binder dispatch.

### Tasker dispatcher (`TaskerActionDispatcher`)

- `INSERT_TEXT` and `INSERT_CLIP` now consult `SensitiveFieldGuard`
  before reaching the editor. A Tasker-class sender can no longer
  inject text or paste the clipboard into a password / numeric-PIN /
  `IME_FLAG_NO_PERSONALIZED_LEARNING` field — the same privacy
  guarantee the smart-compose, translation, and MCP surfaces already
  enforce. Sensitive-field suppression is logged.
- Hard `as String` casts on extras are now `as?` with explicit
  early-return on null, so a future contract change cannot crash
  the receiver.
- `TRIGGER_VOICE` now logs the requested mode for traceability
  (mode routing to dictation vs command grammar still lands with
  Next-2.4's voice-command split).

### IME service teardown (`FlorisImeService.onDestroy`)

- Resource cleanup (MCP bridge stop, voice-input manager destroy,
  input-feedback dispose, wallpaper receiver unregister) now runs
  BEFORE `super.onDestroy()`. The previous order cancelled the
  lifecycle scope first, so any callback scheduled by our cleanup
  steps was silently dropped.

### Voice-model install (`VoiceModelInstallStore`)

- On every install, sweep stale `.swiftfloris-staging-…` and
  `.swiftfloris-backup-…` directories left behind by an install
  that crashed between staging and rename. Without the sweep these
  accumulated indefinitely on disk.
- Staging/backup directories are now anchored to a dedicated
  prefix (`SafeModelIdPattern` requires the first char to be
  alphanumeric so a real model id can never collide).

### ZIP extraction (`ZipUtils.unzip`)

- Skip reasons (entry-name too long, destination path too long,
  zip-slip violation, oversize entry) are now logged via
  `flogWarning` instead of silently dropped, so malicious archives
  show up in audit / CI rather than masquerading as corrupt ones.
- `dstFile.delete()` on oversize-entry rejection now runs AFTER
  the output stream's `use` block closes. The previous order
  triggered "delete on open handle" failures on some filesystems
  and left a partial file behind.

### Personal n-gram store (`PersonalBigramStore`)

- `totalEntryCount` no longer treats leftover `.tsv.tmp` flushes
  from a crashed save as a phantom locale (strict `.tsv` suffix +
  explicit `.tsv.tmp` exclusion).

### User-dictionary cursor leak (`UserDictionary`)

- `queryResolver` and `queryLanguageList` now use `cursor.use { … }`
  so the cursor closes even when the row-parse throws. Robolectric
  and some OEM content-provider implementations do throw on
  malformed rows; the previous `.also { cursor.close() }` chain
  leaked the cursor in those cases.

## New tests

- `DictionaryImporterTest`: BOM-stripped detection across XML / JSON /
  CSV, the `word`-as-data regression, decimal + hex XML numeric
  entity decoding.
- `AndroidMcpClientTest`: correlation-id mismatch rejection, oversized
  daemon response rejection, UTF-8-bytes vs char-length payload cap
  bypass.
- `VoiceModelInstallStoreTest`: stale staging / backup dir sweep on
  next install.

## Versioning

- `gradle.properties`: `projectVersionCode=1848`,
  `projectVersionName=1.8.48`.

## Verification

Per-file syntactic verification — local Windows host has no JDK or
Android SDK on the path; recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

Continue working through the §6 NOW queue: N15.3 (Smart Edit voice
REMOVE_ITEM_FROM_LIST), N17.1 (emoji crash triage), N14.3 / N14.4
(Compose BOM + Gradle wrapper refresh).
