# SwiftFloris v1.8.140

Date: 2026-05-18

## Candidate Auto-Commit Policy Extraction

- Added `CandidateAutoCommitPolicy` as the pure decision point for auto-commit candidate ordering, quick-prediction spacebar insertion, plain-space prediction suppression, and rejected-correction gating.
- Thinned `NlpManager` so it gathers Android-bound state, preference values, dictionary shortcut expansions, and immediate autocorrect candidates before delegating deterministic selection to the policy.
- Kept existing priority semantics: user-dictionary shortcuts, phrase repairs, active-strip autocorrects, and immediate contraction fallbacks are still ordered ahead of generic guesses as before.

## Tests

- Added `CandidateAutoCommitPolicyTest` covering disabled states, shortcut / phrase / active / immediate priority, auto-commit eligibility, language-confidence gating, rejected-correction suppression, quick-prediction spacebar insertion, and plain-space prediction suppression.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.CandidateAutoCommitPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
