# SwiftFloris v1.8.139

Released: 2026-05-18

## Changed

- Reviewed dependency-version lint warnings as a dedicated dependency slice.
- Bumped the Gradle wrapper distribution from 9.4.1 to checksum-pinned 9.5.1.
- Bumped `androidx.navigation:navigation-compose` from 2.9.7 to 2.9.8.
- Moved JUnit Vintage to the version catalog and bumped the test-runtime bridge from 5.13.1 to 6.0.3.
- Updated dependency and reproducible-build docs with the reviewed pins.

## Verification

- `./gradlew.bat --version`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
