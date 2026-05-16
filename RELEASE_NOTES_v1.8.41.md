# SwiftFloris v1.8.41 — 2026-05-16

N15.2 — Gboard parity: auto-return to the letter keyboard after the apostrophe
in the symbols panel so contractions ("don't", "I'm") finish without a manual
mode switch.

## What changed (user-visible)

When the user is in the symbols panel (`SYMBOLS` or `SYMBOLS2`) and taps the
apostrophe key, the IME now flips back to the letter keyboard (`CHARACTERS`)
right after the apostrophe commits. This matches the Gboard 16.6 beta behavior
documented at `[GBOARD-AUTOSWITCH-2026]` in the roadmap.

The behavior is gated on a new **Settings → Keyboard → Return to letters after
apostrophe** switch, default **on**. Users who deliberately stay in the symbols
panel after an apostrophe can flip it off.

The space-bar variant of this behavior (`spaceBarSwitchesToCharacters`) is
unaffected — both switches are independent so each can be tuned to taste.

## What changed (internal)

### N15.2 — `ApostropheReturnGate`

New pure helper in `ime/keyboard/`:

```kotlin
object ApostropheReturnGate {
    fun shouldReturnToCharacters(
        committedText: String,
        currentMode: KeyboardMode,
        autoReturnEnabled: Boolean,
    ): Boolean
}
```

Returns `true` iff the pref is on, the committed text is exactly `"'"`
(U+0027 ASCII apostrophe — the curly U+2019 typographic quote is **not**
auto-returned to keep the gate conservative), and the current mode is
`SYMBOLS` or `SYMBOLS2`. `NUMERIC`, `NUMERIC_ADVANCED`, `PHONE`, and `PHONE2`
panels never trigger because the shipped layouts don't carry the apostrophe
on those panels.

### N15.2 — `KeyboardManager` wire-up

`KeyboardManager.onInputKeyUp` now calls `ApostropheReturnGate` immediately
after `editorInstance.commitChar(text)` in the CHARACTER/NUMERIC default
branch. When the gate returns `true`, `activeState.keyboardMode` flips to
`CHARACTERS` so the next keystroke sees the letter view.

### N15.2 — `prefs.keyboard.autoReturnAfterApostrophe`

New JetPref-backed preference in `AppPrefs.Keyboard`:

```kotlin
val autoReturnAfterApostrophe = boolean(
    key = "keyboard__auto_return_after_apostrophe",
    default = true,
)
```

Surfaced under **Settings → Keyboard** with the two new strings
`pref__keyboard__auto_return_after_apostrophe__label` +
`pref__keyboard__auto_return_after_apostrophe__summary`.

### Tests

- 8 new `ApostropheReturnGateTest` cases covering: SYMBOLS trigger, SYMBOLS2
  trigger, disabled-pref no-op, non-apostrophe symbol no-op, CHARACTERS
  no-op, NUMERIC/NUMERIC_ADVANCED/PHONE/PHONE2 no-op, curly-quote no-op,
  empty-string no-op.

## Versioning

- `gradle.properties`: `projectVersionCode=1841`,
  `projectVersionName=1.8.41`.

## What's next

- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual trackpad on
  long-press space — promotes the existing space-swipe path to a full
  `Box` overlay with `MotionEvent` deltas).
- **N14.1** — Uncomment `alias(libs.plugins.roborazzi)` and bump
  `roborazzi = "1.39.0"` → `"1.55.0"` now that the AGP 9 plugin is live.
