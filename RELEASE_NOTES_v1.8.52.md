# SwiftFloris v1.8.52 — 2026-05-17

Phase A1 — SwiftKey migration outreach push.

## Why ship this now

The SwiftKey-account cutoff is **2026-05-31**, 14 days from this release.
The on-device JSON importer landed in v1.8.46 and was hardened in v1.8.48;
the visibility step — making the migration funnel impossible to miss for
a user landing on the README — is what `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`
calls out as Phase A1 with the hardest deadline. Pure doc / marketing
slice; zero code changes.

## What changed

### README

- New `SwiftKey migration` Shields badge in the top badge row, captioned
  "window closes 2026-05-31" in red so a casual GitHub landing immediately
  surfaces the deadline.
- New banner block above the Highlights table walks the visitor through
  the two no-cloud migration paths in three sentences each:
  1. **Right now** — export `swiftkey-cloud.json` from
     `data.swiftkey.com`, install SwiftFloris via the Obtainium one-tap
     link below, then run **Settings → Personal dictionary → Import**.
  2. **If you missed the cutoff** — your learned words are gone from the
     cloud but the instant-remember overlay (v1.8.26) climbs anything you
     re-type back to the top of the prediction strip after a single use.
- Highlights table version-row bumped `v1.8.46` → `v1.8.52`.
- Existing "Migrating from SwiftKey" section consolidated and pointed at
  the new `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` for full context.
- "Recent releases" list now covers v1.8.46 → v1.8.52 (was stuck at
  v1.8.46).
- "Keyboard crashes on emoji insertion?" troubleshooting section updated
  to reference the v1.8.50 N17.1 root-cause fix instead of marking the
  issue as open triage.
- Status line bumped to v1.8.52 with the 14-days-remaining countdown.

### Versioning

- `gradle.properties`: `projectVersionCode=1852`,
  `projectVersionName=1.8.52`.

## Verification

Doc-only. The Shields badge URL pattern is the same one used by every
other badge on the README; Obtainium one-tap URL unchanged from v1.7.0.

## What's next

Phase A2 (v1.8.53) — `PersonalDictionaryImportSummary` Compose screen
that shows "Imported N words from your SwiftKey export" after a
successful import + a rollback action so a botched import is undoable.

Phase A3 (v1.8.54) — Encrypted-blob export option on the personal
dictionary so users can carry their learned vocabulary off the device
through any user-chosen channel (Syncthing, USB, etc.) without a
plain-text CSV intermediate.
