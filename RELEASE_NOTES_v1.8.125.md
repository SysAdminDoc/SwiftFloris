# SwiftFloris v1.8.125

Released: 2026-05-18

## Addons dictionary asset mounting

- Added `AddonDictionaryAssetMounts`, which reads enrolled dictionary-pack descriptor resources and mounts addon APK `assets/` through `PackageManager#getResourcesForApplication(...)` without extraction or temp-file copies.
- Enforced the addon asset byte cap while streaming addon asset text so an oversized pack is skipped before materializing an unbounded string.
- Wired `LatinDictionaryStore` to prefer addon dictionary and Zipf asset paths, merge readable addon dictionaries with bundled language baselines, and invalidate cached dictionaries when `AddonRegistryStore` publishes a new generation.
- Added tests for addon asset-path routing, generation-based dictionary reloads, merged addon/bundled dictionaries, and registry-store generation changes.

## Verification

- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
