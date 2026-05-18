# SwiftFloris v1.8.131

Released: 2026-05-18

## Spacing lifecycle state tests

- Added `EditorSpacingLifecycleStateTest` for the editor spacing state holders.
- Covered auto-space one-editor-update grace and immediate expiry paths.
- Covered phantom-space composing-region visibility, candidate-for-revert retention through the first editor update, and cleanup after explicit or unprotected update deactivation.
- Checked off the `IMPROVEMENT_PLAN.md` phantom-space and autospace lifecycle test item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.editor.EditorSpacingLifecycleStateTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
