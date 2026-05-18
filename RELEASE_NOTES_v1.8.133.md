# SwiftFloris v1.8.133

Released: 2026-05-18

## Incognito suggestion privacy policy tests

- Extracted incognito suggestion privacy decisions into `SuggestionPrivacyPolicy`.
- Wired editor startup, incognito toggle availability, committed-word learning, and touch-decoder evidence recording through the shared policy.
- Added JVM coverage for app-declared no-personalized-learning override, fixed and dynamic incognito modes, toggle availability, learning suppression, and touch-decoder evidence suppression.
- Checked off the `IMPROVEMENT_PLAN.md` incognito suggestion behavior test item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.SuggestionPrivacyPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
