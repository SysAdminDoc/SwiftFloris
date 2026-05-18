# SwiftFloris v1.8.134

Released: 2026-05-18

## Backup/restore policy tests

- Extracted backup and restore validation / operation-state decisions into `BackupRestorePolicy`.
- Wired backup cancellation, backup start enablement, restore archive metadata/content validation, and restore action enablement through the policy.
- Restore archive inspection now rejects archives that contain metadata but no restorable data sections.
- Added JVM coverage for backup success/cancellation/failure, restore invalid archives, warning paths, action enablement, and partial-failure classification.
- Checked off the `IMPROVEMENT_PLAN.md` backup/restore test item and the backup/restore validation-policy extraction item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.advanced.BackupRestorePolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
