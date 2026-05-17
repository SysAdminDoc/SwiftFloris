# SwiftFloris v1.8.82

Released: 2026-05-17

## Summary

This release ships the Next-10.3b persisted signing-pin layer for addon
enrolment. It adds a safe newline-string codec for addon package fingerprints
and a JetPref key that future startup/Settings code can use to persist
`AddonRegistry` first-enrolment trust across restarts.

No Settings UI, addon download flow, asset mounting, network permission, or
runtime dictionary loading changes are included in this slice.

## Changes

- Added `AddonSigningPinSet`.
  - Parses `packageName=SHA-256` newline strings into validated pins.
  - Ignores malformed/corrupt preference lines instead of crashing the IME.
  - Encodes sorted, validated pins for deterministic persistence.
  - Preserves first-seen pins when a later manifest for the same package carries
    a changed certificate.
- Added `prefs.addon.signingCertPins`.
  - Stores the raw newline-string pin set under `addon__signing_cert_pins`.
  - Keeps raw pin editing out of Settings; future Addons UI should expose
    provenance/revoke flows instead.
- Extended `AddonRegistry` with `fromPinnedSigningPinSet(...)` and
  `pinnedSigningPinSet()` helpers so the pure registry can round-trip through
  the persisted codec without taking a JetPref dependency.
- Added focused unit-test coverage for parsing, malformed-line tolerance,
  deterministic encoding, first-seen preservation, and registry codec
  round-trip.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Focused Gradle command attempted for the addon pin tests; this VM still has
  no Java toolchain (`JAVA_HOME` is not set and `java` is not on PATH), so
  maintainer-host Gradle verification remains required before publishing.
