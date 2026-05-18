# SwiftFloris v1.8.126

Released: 2026-05-18

## Addons dictionary-pack catalog polish

- Added a shared `DictionaryPackCatalogReader` so Settings and the runtime loader read dictionary-pack descriptor resources through the same PackageManager path.
- Extended Settings -> Addons with a Dictionary packs group that lists mounted pack language, word count, dataset license, and source, plus descriptor-level rejection reasons.
- Updated install guidance now that descriptor validation, trust controls, and no-extraction asset mounting have shipped.

## Verification

- `./gradlew.bat :app:recordRoborazziDebug --tests dev.patrickgold.florisboard.screenshot.ThemeAndAddonsScreenshotTest.addonsSettingsRegistrySurface`
- `./gradlew.bat :app:verifyRoborazziDebug`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

