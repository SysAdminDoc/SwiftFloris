# SwiftFloris v1.8.130

Released: 2026-05-18

## Hardware keyboard input tests

- Extracted hardware keydown / keyup routing into `HardwareKeyboardInputPolicy`.
- Routed `KeyboardManager` through the policy while preserving mapped-layout priority before built-in space / enter / shift handling.
- Added focused JVM coverage for hardware space, enter, delete pass-through, shift down/up, mapped letters, mapped punctuation, and punctuation-triggered pending-autocorrect flushes.
- Checked off the `IMPROVEMENT_PLAN.md` hardware keyboard test item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardInputPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
