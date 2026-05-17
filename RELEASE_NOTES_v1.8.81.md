# SwiftFloris v1.8.81

Released: 2026-05-17

## Summary

This release ships the Next-10.3a addon-catalog foundation for dictionary
packs. It adds a process-local addon registry with signing-certificate pin
reconciliation and a pure dictionary-pack catalog builder that validates
descriptor JSON before a future Settings -> Addons screen mounts pack assets.

No network permission, telemetry path, or runtime dictionary asset mounting is
added in this slice.

## Changes

- Added `AddonRegistry`, the live-state companion to `AddonEnumerator`.
  - First-seen addon signing certificates are pinned by package name.
  - Packages whose signing certificate changes are rejected while the old pin is
    retained.
  - Runtime lookups are deterministic by type, display name, package name,
    stable id, and dictionary-pack type.
- Added `DictionaryPackCatalog`.
  - Builds a typed catalog from enrolled dictionary-pack manifests plus
    descriptor JSON.
  - Rejects missing, malformed, or forward-incompatible descriptors without
    crashing the IME.
  - Produces `AddonProvenanceReport` objects for accepted dictionary packs so
    the future Settings UI can surface dataset/source/license data.
- Added focused unit-test coverage for registry pinning, hijack rejection,
  stale-pin retention, duplicate-package collapse, descriptor rejection, and
  language lookup.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Focused Gradle command attempted for the new addon tests; this VM still has
  no Java toolchain (`JAVA_HOME` is not set and `java` is not on PATH), so
  maintainer-host Gradle verification remains required before publishing.
