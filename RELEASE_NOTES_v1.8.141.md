# SwiftFloris v1.8.141

Date: 2026-05-18

## Punctuation Flush Policy Extraction

- Added `KeyboardAutoCommitFlushPolicy` as the pure decision point for whether a software text commit should flush a pending autocorrect candidate first.
- Preserved the existing behavior: media-mode text commits flush, character-mode non-letter commits flush, alphabetic character commits do not flush, and numeric / phone layouts commit punctuation without flushing autocorrect.
- Thinned `KeyboardManager` so it asks the policy before invoking `getAutoCommitCandidate()` and then executes the chosen text commit.

## Tests

- Added `KeyboardAutoCommitFlushPolicyTest` covering media mode, alphabetic keys, punctuation keys, numeric keys, numeric / phone keyboard modes, non-text key types, and empty text.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.keyboard.KeyboardAutoCommitFlushPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
