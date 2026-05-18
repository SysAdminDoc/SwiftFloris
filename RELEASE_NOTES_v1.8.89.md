# Release v1.8.89 — ZipUtils.unzip aborts atomically on security violations

Date: 2026-05-17

Follow-up #4 from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt](app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt#L154-L240)
— previously every guard (zip-slip, unsafe entry name, entry-count cap,
path-length cap, name-length cap, entry-size cap) followed a "warn and
continue" policy. A malicious archive containing one well-formed entry
plus one escape entry would leave the well-formed entry on disk and the
caller's `runCatching` would see a `Result.Success` — restore appears
to succeed, the user gets the toast for success, but the malicious
archive's intent was partially realised AND the user has no signal that
something was filtered.

This release splits guards by intent:

- **Abort-class (throw `SecurityException`):**
  - Pre-canonical unsafe entry name (path-traversal pattern).
  - Post-canonical-resolution path outside `dstDir` (zip-slip).
  - More than 10_000 entries in the archive (zip-bomb).
- **Continue-with-warning:**
  - Entry name > 255 chars.
  - Destination path > 1023 chars.
  - Per-entry / per-archive byte caps (unchanged — already cleanly
    handled by the `copy()` helper).

The abort path triggers the existing `try { ... } catch (error: Throwable)
{ workspace.close(); throw error }` block in
[`RestoreScreen.prepareRestoreWorkspace`](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt#L101-L132),
which deletes the partial workspace and re-throws. The launcher's
`onFailure` shows the user a toast with the security exception's
message, so the user sees *why* the archive was rejected rather than
silently getting a half-applied restore.

The split is deliberate: name-length / path-length anomalies are common
in legitimate archives produced by archivers that encode unusual paths,
and dropping those entries with a warning is the right behaviour. The
abort-class violations only fire on actively-malicious archive content
that no legitimate restore would carry.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt`
- `gradle.properties` — versionCode 1889 / versionName 1.8.89

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA:
- Construct a backup archive with one legitimate entry and one entry
  named `../../etc/foo`. Try to restore in Settings → Restore.
  Pre-fix: restore appears to succeed, only the legitimate entry lands.
  Post-fix: restore fails with a toast naming the rejected entry; no
  files are written to the workspace.
- Restore a legitimate backup archive (e.g. one produced by the
  Settings → Backup flow on the same install). Verify it still
  succeeds end-to-end — no change to the legitimate path.
- Construct a zip-bomb archive with 20_001 zero-byte entries. Verify
  the restore fails with the entry-count message rather than
  succeeding silently after truncation.
