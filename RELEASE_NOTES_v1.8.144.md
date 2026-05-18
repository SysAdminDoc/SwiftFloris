# SwiftFloris v1.8.144

Date: 2026-05-18

## Backup Flow Trust States

- Added `BackupFlowNotice` to make backup-screen status precedence explicit for progress, cancellation, failure, share-sheet handoff, success, and clipboard privacy warning states.
- Settings -> Advanced -> Back up data now shows inline cards while a backup is preparing, after document-picker cancellation, after share-sheet handoff, after export failure, and when clipboard history is selected.
- Added copy explaining that app-marked sensitive clipboard entries are skipped from backup archives even when clipboard history is selected.

## Tests

- Extended `BackupRestorePolicyTest` with notice mapping and precedence coverage.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.advanced.BackupRestorePolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
