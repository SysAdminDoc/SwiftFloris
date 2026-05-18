# SwiftFloris v1.8.135

Released: 2026-05-18

## Changed

- Extracted extension import readiness decisions into `ExtensionImportPolicy`.
- Updated the typed extension import screen path so the language-pack importer rejects non-language-pack `.flex` files before enabling import.
- Added focused JVM coverage for language-pack new installs, user-installed updates, bundled-core rejection, corrupted metadata, wrong extension type, unsupported files, missing parsed extensions, and import button enablement.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.ext.ExtensionImportPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
