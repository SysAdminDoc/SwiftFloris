# SwiftFloris v1.8.44 — 2026-05-16

N13.3 — long-press popup guard on password fields.

## What changed (user-visible)

Long-pressing a key on a password field (Android `TYPE_TEXT_VARIATION_PASSWORD`,
`TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`,
or `TYPE_NUMBER_VARIATION_PASSWORD`) no longer renders a popup showing the
typed character — neither the small accent popup nor the extended
alt-glyph mini-keyboard. Tapping characters into the field continues to
work as before; only the visual popup is suppressed.

This closes the on-screen IME side of the Android 17 (API 37)
`show_passwords_physical` separation [STD-A17-BEHAVIOR]: Android 17
introduces separate "show physical-keyboard passwords" and "show
on-screen-keyboard passwords" toggles, and SwiftFloris already gates
suggestions / clipboard / FLAG_SECURE on password fields (N7.2). The
popup surface was the last visual leak of typed credential characters,
and is now closed.

## What changed (internal)

### N13.3 — `PasswordFieldPopupGate`

New pure helper in `ime/text/keyboard/`:

```kotlin
object PasswordFieldPopupGate {
    fun shouldSuppressPopups(activeVariation: KeyVariation): Boolean {
        return activeVariation == KeyVariation.PASSWORD
    }
}
```

Returns `true` only when the active variation is `KeyVariation.PASSWORD`.
The Android `KeyVariation` enum in SwiftFloris collapses all four
password input types into a single `PASSWORD` bucket inside
`EditorInstance.handleStartInputView`, so the IME-side gate only
needs one comparison.

### N13.3 — `TextKeyboardLayout` wire-up

`TextKeyboardLayout`'s `rememberPopupUiController(...)` call now
consults the gate in both predicates:

```kotlin
val evaluatorHack = rememberUpdatedState(evaluator)
val popupUiController = rememberPopupUiController(
    ...
    isSuitableForBasicPopup = { key ->
        if (PasswordFieldPopupGate.shouldSuppressPopups(evaluatorHack.value.state.keyVariation)) {
            false
        } else if (key is TextKey) { ...existing checks... } else true
    },
    isSuitableForExtendedPopup = { key ->
        if (PasswordFieldPopupGate.shouldSuppressPopups(evaluatorHack.value.state.keyVariation)) {
            false
        } else if (key is TextKey) { ...existing checks... } else true
    },
)
```

The new `rememberUpdatedState(evaluator)` ("evaluatorHack") capture
mirrors the existing `desiredKeyHack` pattern and makes the predicate
read the *live* evaluator on every long-press evaluation, not the one
snapshotted at the first recomposition. Without that, a navigation
from a normal field to a password field within the same IME session
could keep showing popups against a stale evaluator reference.

### Tests

6 new `PasswordFieldPopupGateTest` cases:

1. `PASSWORD` variation suppresses popups.
2. `NORMAL` variation does not suppress.
3. `ALL` variation does not suppress (default for unspecified fields).
4. `EMAIL_ADDRESS` variation does not suppress.
5. `URI` variation does not suppress.
6. Forward-compat exhaustive sweep — iterates every `KeyVariation.entries`
   value and asserts only `PASSWORD` trips. A future variation added to
   the enum that should also suppress popups (e.g. a hypothetical
   "PIN") will fail this test, forcing the author to confirm intent.

## Versioning

- `gradle.properties`: `projectVersionCode=1844`,
  `projectVersionName=1.8.44`.

## What's next

- **N13.2** — IME visibility on config change for Android 17.
- **Roborazzi baseline capture** — maintainer-side `:app:recordRoborazziDebug`
  run to commit the first batch of baseline PNGs, then remove
  `continue-on-error: true` from the CI verify step.
- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual
  trackpad on long-press space).
