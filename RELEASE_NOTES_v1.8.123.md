# SwiftFloris v1.8.123

Released: 2026-05-18

## Roborazzi baseline hard gate

- Closed carried-forward **F11** by recording committed Roborazzi baselines for the current screenshot suite under `app/src/test/snapshots/`.
- Added `ThemeAndAddonsScreenshotTest`, covering:
  - `swiftkey_high_contrast` keyboard-style surface.
  - `aurora_animated` keyboard-style surface.
  - Settings -> Addons accepted/rejected registry surface with deterministic seeded addon data.
- Kept the existing `ExtensionMaintainerChipScreenshotTest` baselines for name-only, email, and URL maintainer-chip variants.
- Removed `continue-on-error: true` from the `Roborazzi visual-regression verify (N14.1)` CI step, so `:app:verifyRoborazziDebug` is now a hard PR/push gate.

## Verification

- `./gradlew.bat :app:recordRoborazziDebug --tests dev.patrickgold.florisboard.screenshot.ThemeAndAddonsScreenshotTest`
- `./gradlew.bat :app:verifyRoborazziDebug`

Known unchanged warnings: AGP `android.newDsl=false` deprecation/configuration-time resolution warnings and the existing JUnit4 Compose-rule migration warning in screenshot tests.
