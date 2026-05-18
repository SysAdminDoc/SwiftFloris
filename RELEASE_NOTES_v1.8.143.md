# SwiftFloris v1.8.143

Date: 2026-05-18

## Autocorrect Lifecycle Contract

- Added `docs/AUTOCORRECT_LIFECYCLE.md` as the written contract for autocorrect, spacebar prediction insertion, punctuation/non-letter commits, hardware mapped commits, backspace rejection, glide-delete escalation, provider notifications, manual QA, and regression coverage.
- Added `CandidateCommitSideEffectPolicy` so accepted-provider notifications and personal-dictionary learning are explicitly gated on successful editor commits.
- Updated `KeyboardManager.commitCandidate(...)` to notify a candidate source provider only after the editor commit succeeds, while preserving non-clipboard learning through `learnIfAllowed(...)`.
- Clarified `SuggestionProvider` accepted/reverted callback semantics and linked the contract from contributor manual QA instructions.

## Tests

- Added `CandidateCommitSideEffectPolicyTest` covering accepted-provider notification and learning side-effect gates.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.CandidateCommitSideEffectPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
