# SwiftFloris v1.8.51 — 2026-05-17

N14.3 + N14.4 dependency-pin audit.

## Why ship this now

Both roadmap items are doc-only "review whether X is still current"
tasks. The audit-log entry pins the analysis so a future contributor
doesn't redo it from scratch, and explicitly separates the audit
deliverable from the version-bump deliverable (which is gated on the
CI evidence run per the cadence policy).

## What changed

### Compose BOM audit (N14.3)

Current pin: `androidx-compose-bom = "2026.03.01"`. Audit against
the upstream release notes confirms this is the published
March-2026 patch-01 line. No later patch is announced as of this
audit. No Roborazzi visual-regression or macrobenchmark surface
forces an out-of-band bump.

### Gradle wrapper audit (N14.4)

Current pin: `gradle-wrapper.properties` distributionUrl
`gradle-9.4.1-bin.zip` with `distributionSha256Sum=2ab2958f...`.
The wrapper still verifies. Per the `docs/REPRODUCIBLE_BUILDS.md`
contract, any bump must update the SHA-256 in lockstep so the
verify path stays correct.

### Documentation

- `docs/DEPENDENCY_TRIAGE.md` gains a new **Audit log** table at
  the bottom. Each entry pins the date, the pin audited, the
  conclusion, and the next-action gate. Future audits append a
  row instead of editing the body, so the historical analysis
  stays inspectable.

## Versioning

- `gradle.properties`: `projectVersionCode=1851`,
  `projectVersionName=1.8.51`.

## Verification

No code changes — doc-only. The audit deliverables are the
roadmap checkmarks + the audit-log entry.

## What's next

The Compose BOM and Gradle wrapper version-bumps themselves are
separate slices gated on the maintainer running
`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
(and the Roborazzi suite for BOM bumps) per the cadence policy in
`docs/DEPENDENCY_TRIAGE.md`. Continuing through the §6 NOW queue:
N15.1 (free-movement spacebar trackpad — already partially
shipped via matrix #14 + #15; verify and close), then on to the
remaining NEXT items that can land without external blockers.
