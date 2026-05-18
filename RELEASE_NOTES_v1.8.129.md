# SwiftFloris v1.8.129

Released: 2026-05-18

## Editor input behavior tests

- Extracted autocorrect spacebar commits, punctuation auto-spacing, phantom spacing, double-space period, and sentence-capitalization decisions into `EditorInputBehaviorPolicy`.
- Routed `EditorInstance` and `KeyboardManager` through the extracted policy without changing the runtime commit behavior.
- Added focused JVM coverage for accepted autocorrect spacing, rejected-correction protection, suppressed plain-space predictions, punctuation spacing, phantom spacing, double-space period, and sentence-capitalization gates.
- Checked off the first `IMPROVEMENT_PLAN.md` editor/input behavior test item and the related phantom-space/autospace extraction item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.editor.EditorInputBehaviorPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
