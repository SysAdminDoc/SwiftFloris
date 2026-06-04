# SwiftFloris Cycle 7 Findings - 2026-06-04

## Scope

Cycle 7 ran from the clean detached worktree at pushed `master` commit
`7c066d5`. `git pull --rebase origin master` reported the worktree was already
up to date. This pass did not edit feature code, tests, build files, or assets.

## Anti-Duplication Check

- Password-field `FLAG_SECURE` behavior is historical coverage and was not
  duplicated.
- The v1.8.225 incognito-on-field-start fix exists in `FlorisImeService`; this
  cycle targets only the mid-session smartbar toggle callback noted in code and
  in `docs/AUDIT_2026-06-02.md`.
- No clipboard search row was added because v1.8.228 closes R3-2.
- No addon trust row was added because R5-1 already covers the first-run addon
  enrollment trust boundary.

## Local Evidence

- `FlorisImeService.kt:599` calls `applyFlagSecureForCurrentField(editorInfo)`
  from `onStartInputView`.
- `FlorisImeService.kt:617-636` applies `FLAG_SECURE` when the active field is a
  password variant or `activeState.isIncognitoMode`; the code comment states
  that a mid-session `TOGGLE_INCOGNITO_MODE` does not re-run the policy until
  the next field start.
- `KeyboardManager.kt:738-755` handles `KeyCode.TOGGLE_INCOGNITO_MODE` by
  flipping `prefs.suggestion.forceIncognitoModeFromDynamic`, mutating
  `activeState.isIncognitoMode`, and showing the on/off toast. It does not call
  back into the service/window policy.
- `docs/AUDIT_2026-06-02.md:89-92` records the same follow-up after the
  field-start incognito `FLAG_SECURE` fix.
- Existing tests cover incognito resolution and toggle availability through
  `SuggestionPrivacyPolicyTest`, but no focused test pins the secure-window
  decision after a dynamic toggle.

## External Evidence

- Android `WindowManager.LayoutParams.FLAG_SECURE` docs define the flag as
  treating window content as secure so it does not appear in screenshots or on
  non-secure displays:
  https://developer.android.com/reference/android/view/WindowManager.LayoutParams

## Roadmap Changes Fed

1. R7-1, P2: re-apply `FLAG_SECURE` when incognito mode toggles mid-session.

## Non-Adds

- No row was added for password-field `FLAG_SECURE`; that coverage already
  exists.
- No broader incognito-mode rewrite was proposed; the item only asks to re-run
  the existing secure-window policy when the dynamic toggle changes.
- No source-code fix was attempted in this cycle; all changes are roadmap and
  research documentation only.
