# SwiftFloris v1.8.142

Date: 2026-05-18

## Theme Rule Edit Policy Extraction

- Added `ThemeRuleEditPolicy` as the pure decision point for add-rule selection validation, selector toggling, and key-code attribute parsing / replacement.
- Thinned `EditRuleDialog` so it keeps rendering dialog state and user feedback while delegating deterministic rule-edit decisions to the policy.
- Preserved existing behavior for empty add-rule selection errors, selector on/off toggles, invalid key-code input, duplicate-code rejection, unchanged-code dismissal, and add/replace code actions.

## Tests

- Added `ThemeRuleEditPolicyTest` covering empty add-rule selection, selector toggling, blank / non-numeric / out-of-range key-code input, unchanged values, duplicate codes, new-code adds, and old-code replacement.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.theme.ThemeRuleEditPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
