# Release v1.8.86 — keyVariation honours TYPE_NUMBER_VARIATION_PASSWORD

Date: 2026-05-17

Follow-up #1 from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt#L75-L120)
— in `handleStartInputView`, when the focused field reports
`InputAttributes.Type.NUMBER`, `activeState.keyVariation` was hard-coded to
`KeyVariation.NORMAL`. That meant `TYPE_NUMBER_VARIATION_PASSWORD` fields
(numeric PIN / OTP entry — bank PINs, app-lock PINs, TOTP codes) bypassed
every privacy gate keyed on `keyVariation == KeyVariation.PASSWORD`,
including the clipboard-history exclusion in
`performClipboardCut` / `performClipboardCopy`.

Net effect before this fix: a user copying a numeric OTP out of the IME
selection (rare but possible) wrote the OTP into the IME-local clipboard
history, where it would surface on the next clipboard-palette open.
[FlorisImeService.applyFlagSecureForCurrentField](app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt#L562)
already covered the FLAG_SECURE side via `InputAttributes.variation ==
Variation.PASSWORD` (numeric PIN variation maps cleanly into that enum), so
the IME window was correctly opaque to screenshots; only the local
clipboard-history write was unguarded.

After this fix: numeric PIN fields propagate `keyVariation = PASSWORD`
while still selecting `KeyboardMode.NUMERIC` for the actual layout, so all
existing `keyVariation == PASSWORD` gates fire — clipboard history, glide
delete suppression (see TextKeyboardLayout.kt:151 / 646 / 788), long-press
popup suppression (TextKey.kt:88), and the `isComposingEnabled` /
suggestion-suppression branch.

## Why this is a separate release

Per [AGENTS.md §6](AGENTS.md) (one logical improvement per release).
v1.8.85 was an explicit cross-subsystem exception; subsequent follow-ups
return to per-feature commits.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `gradle.properties` — versionCode 1886 / versionName 1.8.86

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA:
- Focus a field declared `android:inputType="numberPassword"`. Verify the
  numeric keyboard appears (mode preserved). Long-press a digit key —
  popup should be suppressed (now-active PASSWORD gate).
- Type some digits, select them via the system selection handles, tap the
  IME's Cut/Copy quick action if shown. Open the clipboard palette and
  confirm the digits do NOT appear in the history pane. Pre-fix they did.
- Focus a regular numeric field (no `numberPassword` flag). Verify
  long-press popups still work and clipboard cut/copy still writes to
  history.
