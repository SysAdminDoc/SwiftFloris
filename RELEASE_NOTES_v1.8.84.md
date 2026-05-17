# SwiftFloris v1.8.84

Released: 2026-05-17

## Summary

This release ships the Next-10.3d Settings -> Addons read-only status surface.
Users can now open Settings -> Addons, inspect the process-wide addon registry,
manually rescan installed addon APKs through the same startup reconciliation
path, see accepted/rejected counts, and review installed addon package,
license, version, size, and signing-certificate details.

No addon download flow, dictionary asset mounting, signing-pin revoke/reset UI,
network permission, or runtime dictionary loading changes are included in this
slice.

## Changes

- Added `AddonsSettingsScreen`.
  - Shows accepted, rejected, and pinned-certificate counts from the active
    addon registry and persisted pin set.
  - Lists accepted addons with package name, addon type, version, APK license,
    bundle size, and SHA-256 signing-certificate fingerprint.
  - Lists rejected addons from the latest registry snapshot with package name
    and rejection reason.
  - Adds local-only install guidance that restates the metadata, no-network,
    and first-seen signing certificate requirements.
- Added a manual rescan action.
  - Runs `AddonEnumerator` on `Dispatchers.Default`.
  - Reuses `AddonRegistryStartup.reconcile(...)` so Settings and IME startup
    share the same trust and package-hijack rules.
  - Publishes `AddonRegistryStore` and persists canonical signing pins only when
    the trust set changes.
- Wired `Routes.Settings.Addons`, deep link `ui://florisboard/settings/addons`,
  and the Home screen entry under Data & extensions.
- Added English source strings for the Addons screen.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Gradle compile command attempted for the Addons settings screen; this VM
  still has no Java toolchain (`JAVA_HOME` is not set and `java` is not on
  PATH), so maintainer-host Gradle verification remains required before
  publishing.
