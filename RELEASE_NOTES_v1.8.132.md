# SwiftFloris v1.8.132

Released: 2026-05-18

## Glide typing delete policy tests

- Extracted the committed-glide-word backspace escalation decision into `EditorInputBehaviorPolicy.shouldEscalateGlideBackspaceToWordDelete`.
- Kept `EditorInstance.deleteBackwards` behavior aligned with the existing `immediateBackspaceDeletesWord` path.
- Added JVM coverage for enabled word-delete escalation, disabled preference, inactive phantom-space, and explicit word-delete paths.
- Checked off the `IMPROVEMENT_PLAN.md` glide typing delete interaction test item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.editor.EditorInputBehaviorPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
