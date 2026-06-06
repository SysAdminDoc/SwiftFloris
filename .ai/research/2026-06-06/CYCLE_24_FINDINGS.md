# Cycle 24 Findings - 2026-06-06

## Cycle

`sync-paired-device-corruption-recovery-recheck-2026-06-06`

## Scope

Resumed after Cycle 23. This pass focused on paired-device JSON parsing,
Settings -> Sync empty/error rendering, focused sync tests, existing
reset/corruption copy patterns, and Android DataStore corruption-handling
guidance.

## Files and sources reviewed

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairedSyncDevice.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/sync/SyncSettingsScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/sync/SyncPairingUiModelTest.kt`
- `docs/THREAT_MODEL.md`
- `docs/ACCESSIBILITY.md`
- Android DataStore corruption handling:
  https://developer.android.com/topic/libraries/architecture/datastore

## Findings

- `PairedSyncDevice.kt:60-64` returns `emptyList()` for blank input and for
  JSON decode/validation failure. The caller cannot distinguish intentional
  empty state from corrupt persisted state.
- `PairedSyncDevice.kt:71-76` calls `parse(rawJson)` before upserting a new
  device. If the raw JSON was corrupt, a valid new pairing overwrites the
  unreadable previous state with no reset/repair decision.
- `SyncSettingsScreen.kt:99-101` stores only `List<PairedSyncDevice>` from
  parsing. `SyncSettingsScreen.kt:283-290` renders the benign
  `settings__sync__no_paired_devices` copy whenever that list is empty.
- `SyncPairingUiModelTest.kt:83-84` currently pins corrupt JSON to
  `emptyList()`, so the silent fallback is intentional in tests but not
  recovery-ready.
- A Sync-specific string/code scan found no corrupt-state, repair, or reset copy
  for paired devices.

## External-source effect

Android DataStore documentation treats on-disk corruption as a distinct rare
state and offers a corruption-handler API for graceful recovery. SwiftFloris
does not need to use that exact API for a JSON string, but the same product rule
applies: recover deliberately rather than making corrupt state indistinguishable
from a clean default.

## Roadmap effect

Added R24-1 to `ROADMAP.md`: surface corrupt paired-device state instead of
treating it as "no devices."

## Acceptance shape

- Paired-device parsing exposes a typed result: empty, valid, or corrupt.
- Settings -> Sync shows a warning/error row for corrupt paired-device state.
- Reset/re-pair guidance is explicit and destructive reset is confirmed.
- `upsert` does not silently discard corrupt raw JSON without reset or a
  documented repair path.
- Diagnostics avoid including raw public-key payloads by default.
- Tests cover blank, valid, malformed JSON, schema-invalid rows, reset, and
  upsert-after-corruption behavior.

## Duplicate avoidance

- R23-1 is local Sync identity/private-key lifecycle.
- R24-1 is remote paired-device list recovery and user-facing state repair.
