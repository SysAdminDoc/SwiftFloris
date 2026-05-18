# SwiftFloris v1.8.124

Released: 2026-05-18

## Addon signing-pin trust controls

- Closed the local signing-pin revoke/reset half of the Next-10.4 Addons follow-up.
- Settings -> Addons now exposes a confirmed "Reset addon trust decisions" action that clears saved signing-certificate pins without silently re-enrolling currently installed addon APKs.
- Rejected changed-certificate addons now get a confirmed "Trust current certificate" action that clears the old package pin, rescans installed addons, and records the currently installed certificate only if the addon still passes normal validation.
- Added `AddonSigningPinSet.withoutPackage(...)` so trust updates stay in the pure pin codec before the Settings UI writes `prefs.addon.signingCertPins`.
- Refreshed the Settings -> Addons Roborazzi baseline so the hard visual gate covers the new trust-management row.

Verification:

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.addon.AddonSigningPinSetTest`
- `./gradlew.bat :app:recordRoborazziDebug --tests dev.patrickgold.florisboard.screenshot.ThemeAndAddonsScreenshotTest.addonsSettingsRegistrySurface`
- `./gradlew.bat :app:verifyRoborazziDebug`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
