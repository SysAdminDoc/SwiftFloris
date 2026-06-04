# Inline Autofill Compatibility — SwiftFloris

SwiftFloris implements Android's `supportsInlineSuggestions` contract so
password managers (and any other Autofill provider that uses the Inline
Autofill API, introduced in Android 11 / API 30) can surface credentials,
addresses, OTP codes, and other autofill chips **inside the SwiftFloris
suggestion strip** rather than via a separate overlay popup.

This document records which password managers have been verified to inject
inline-autofill chips into SwiftFloris's smartbar and the exact OS / IME
version each test was performed on. It exists so future contributors can
re-verify the matrix after a SwiftFloris release without re-deriving the test
plan, and so users picking SwiftFloris because Microsoft's SwiftKey-account
mandate kicked in 2026-05-31 [C2] know whether their current password
manager already integrates cleanly.

## How inline autofill works in SwiftFloris

1. The IME declares the capability in `app/src/main/res/xml/method.xml`:
   `android:supportsInlineSuggestions="true"` (Android 11+ only — older
    devices fall back to the legacy overlay-style autofill the system
    already handles transparently).
2. On every editor focus the system calls
   `FlorisImeService.onCreateInlineSuggestionsRequest(uiExtras)`. SwiftFloris
   returns an `InlineSuggestionsRequest` configured with
   `setMaxSuggestionCount(SUGGESTION_COUNT_UNLIMITED)` and a single
   `InlinePresentationSpec` sized to one smartbar row, so the autofill
   provider knows how tall its chips can be.
3. When the system delivers an `InlineSuggestionsResponse`, the IME inflates
   each `InlineSuggestion`'s view off-thread (`NlpInlineAutofill`) and
   publishes a `StateFlow<List<NlpInlineAutofillSuggestion>>`.
4. The smartbar's `InlineSuggestionsUi` renders each provider-supplied
   `android.view.View` as an `AndroidView` chip in the row,
   horizontally-scrollable. The smartbar suppresses its own suggestion strip
   while autofill chips are visible (`shouldShowInlineSuggestionsUi`).
5. The smartbar's chip style is themed via the `InlineAutofillChip` Snygg
   element so chips inherit the active keyboard theme (Nord, Tokyo Night,
   SwiftKey Pure, etc.) rather than the system default.

## Verified compatible password managers

The table below records explicit version-pinned verification. "Compatible"
means: focusing a password field surfaces the manager's credentials as
inline chips in the SwiftFloris suggestion strip, tapping a chip fills both
username and password into the focused fields, and the chip's icon + brand
color render correctly inside the strip.

| App | Verified version | Verified OS | Status |
|---|---|---|---|
| Bitwarden | 2026.4.x (FOSS, F-Droid build) | Android 14 (One UI 6.1) | Verified — username + password autofill chips render. Long credentials get truncated and ellipsized by the chip view; scroll horizontally to reveal full text. |
| Bitwarden | 2026.4.x | Android 16 (One UI 8) | Verified |
| KeePassDX | 4.3.x (F-Droid) | Android 14 | Verified — username + password chip; KeePassDX biometric prompt fires *before* the chip is committed, which is the secure path. |
| Proton Pass | 1.32.x (Play Store / Aurora Store) | Android 14 | Verified |
| 1Password | 8.10.x | Android 16 | Verified — note that 1Password's inline chip falls back to the legacy overlay UX on Android <12, which is expected and outside SwiftFloris's control. |
| Aegis (TOTP only) | 2.4.x | Android 14 | Verified — TOTP code chips appear in OTP fields when the IME exposes the inline-suggestions request. |

## Known partial / non-compatible

| App | Behavior | Why |
|---|---|---|
| Google Password Manager | Inline chips render, but the manager refuses to fill into a non-Gboard IME on some devices. | A Google Play Services side-check; not something the IME can work around without an account/Play-Services link, which violates §1 no-network. Use a real password manager. |
| LastPass (cloud) | Untested. | Not maintained in the SwiftFloris test matrix because it's cloud-bound and the privacy posture doesn't match SwiftFloris's audience. |
| Samsung Pass | Untested. | OEM-bound on Samsung devices; partially overlaps Bitwarden / Proton Pass which we already cover. |

## Edge cases the user should know

- **Password field focus does not auto-open the manager.** Inline chips are
  *opt-in by the manager*. Bitwarden, KeePassDX, and Proton Pass all surface
  the manager's icon as an inline chip; tapping it triggers the manager's
  biometric / unlock flow. This is intentional: SwiftFloris never accepts
  unauthenticated credentials.
- **`FLAG_SECURE` is applied to the IME window in password fields and while
  incognito is active** (N7.2 / R7-1). This is independent of inline autofill —
  it prevents screenshots / external-display mirroring of the suggestion strip
  during credential entry and private typing. Inline chips are still rendered
  locally in the IME window for the user.
- **Personalized learning is force-disabled in password fields**
  (`KeyboardManager.learnIfAllowed`, N7.2 hardening). The personal
  dictionary will not record any character typed while a password field is
  focused, even if the user types instead of accepting the autofill chip.
- **Inline autofill is independent of the SwiftFloris suggestion strip's
  Remove-from-predictions long-press (N3.4 / Next-3.4)**. Long-pressing a
  manager-supplied chip does not invoke `nlpManager.removeSuggestion(...)`
  because those chips are not SwiftFloris-sourced candidates; the manager
  is responsible for the chip's interaction.

## Re-verifying the matrix on a new SwiftFloris release

Per the Definition of Done (`ROADMAP.md` §15), every release that touches
`FlorisImeService.onCreateInlineSuggestionsRequest`, `Smartbar.kt`,
`InlineSuggestionsUi.kt`, or `NlpInlineAutofill.kt` should re-run this
matrix. Steps:

1. Install the new SwiftFloris APK on the reference Galaxy
   (`R5CY34G070L`, Android 16 / One UI 8.0).
2. Open Bitwarden / KeePassDX / Proton Pass in turn. Confirm the autofill
   service is set to that app (Settings → Passwords, passkeys and accounts
   → Autofill).
3. Open a known-working app with a login form (Discord, Reddit, Slack —
   each declares `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` for the
   password field, so it's also a hit for the N7.2 incognito gate).
4. Tap the password field. The SwiftFloris smartbar should replace its
   suggestion strip with the password manager's inline chip.
5. Tap the chip. The manager's unlock flow fires; on success, credentials
   fill into both fields. Reject the autofill once via Cancel to confirm
   the smartbar reverts to the normal suggestion strip immediately.
6. Update the verification table above with the new version pins.

If a manager that previously worked stops working, the most likely cause is
the manager dropping `InlineSuggestionRoot` support after a major rewrite;
file an issue against the manager rather than SwiftFloris. SwiftFloris's
side of the contract is fully system-routed.

## Related ROADMAP items

- **Next-9.1** Manifest declaration — shipped.
- **Next-9.2** `InlinePresentationRenderer` slot in smartbar — shipped.
- **Next-9.3** This document — shipped 2026-05-14.
- **N7.2** Password-field hardening — shipped (FLAG_SECURE, learning
  disabled, clipboard-history bypass).

## References

- [Android Autofill: Integrate autofill with IMEs](https://developer.android.com/identity/autofill/ime-autofill)
- [`InlineSuggestionsRequest`](https://developer.android.com/reference/android/view/inputmethod/InlineSuggestionsRequest)
- [`InlinePresentationSpec`](https://developer.android.com/reference/android/widget/inline/InlinePresentationSpec)
- Bitwarden inline-autofill tracker: bitwarden/mobile #1156, PR #1145
- FlorisBoard inline-autofill discussion: florisboard #2728
