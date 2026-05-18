# SwiftFloris v1.8.137

Released: 2026-05-18

## Changed

- Extracted theme component metadata validation into `ThemeComponentMetaValidationPolicy`.
- Updated the theme component metadata dialog confirm path to use the policy for field validity, duplicate-ID detection, and normalized apply data.
- Added focused JVM coverage for valid metadata normalization, invalid fields, duplicate IDs, and blank stylesheet fallback.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.theme.ThemeComponentMetaValidationPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
