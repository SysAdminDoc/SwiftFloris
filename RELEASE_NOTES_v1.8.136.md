# SwiftFloris v1.8.136

Released: 2026-05-18

## Changed

- Extracted subtype editor required-field validation into `SubtypeEditorValidationPolicy`.
- Updated the subtype editor save-state path to serialize an explicit draft model and delegate draft-to-subtype building to the policy.
- Added focused JVM coverage for default add-state missing fields, complete draft building, select-placeholder rejection, and edit-state preservation.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.localization.SubtypeEditorValidationPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
