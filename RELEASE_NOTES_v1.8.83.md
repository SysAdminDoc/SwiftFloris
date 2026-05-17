# SwiftFloris v1.8.83

Released: 2026-05-17

## Summary

This release ships the Next-10.3c addon registry startup wiring. The IME now
scans installed addon APK manifests on startup, reconciles them through the
persisted signing-certificate pin store, publishes a process-wide
`AddonRegistry`, and writes back canonical pins when first-seen addons or
corrupt stored lines change the trust set.

No Settings UI, addon download flow, dictionary asset mounting, network
permission, or runtime dictionary loading changes are included in this slice.

## Changes

- Added `AddonRegistryStartup`.
  - Reconciles `AddonEnumerator` snapshots with the raw
    `prefs.addon.signingCertPins` value.
  - Accepts new addons by first-seen signing certificate and emits the updated
    canonical pin string.
  - Rejects changed-certificate package-name hijacks by preserving the old pin.
  - Marks malformed stored pin lines dirty so startup can clean them out of the
    preference.
- Added `AddonRegistryStore`.
  - Provides the process-wide active registry for future Settings and runtime
    consumers.
  - Supports reset on startup failure without clearing persisted trust pins.
- Wired `FlorisImeService.onCreate()` to run addon startup reconciliation on
  `Dispatchers.Default`.
  - Publishes the active registry after scan/reconcile.
  - Persists updated signing pins only when the canonical encoded form changed.
  - Logs accepted/rejected counts and tolerates failures without aborting IME
    startup.
- Added focused unit-test coverage for new-addon enrolment, changed-certificate
  rejection, corrupt preference cleanup, and registry store publish/reset.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Focused Gradle command attempted for the addon startup tests; this VM still
  has no Java toolchain (`JAVA_HOME` is not set and `java` is not on PATH), so
  maintainer-host Gradle verification remains required before publishing.
