# Release v1.8.102 — longPressAlternates parsing for hardware-keyboard imports

Date: 2026-05-17

Follow-up F8 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).
Closes the LDML-side half; popup-UI routing remains a separate slice.

## What changed

### `HardwareKeyEntry.longPressAlternates`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt) —
adds `longPressAlternates: List<Int>` to `HardwareKeyEntry`. LDML
defines `longPress="a b c"` as a space-separated list of alternates
surfaced on long-press. v1.8.92 closed the shift-slot misuse but
silently dropped the alternates list; this release stores it.

Empty list when no `longPress=` was declared, so behaviour is unchanged
for layouts that already worked.

### `KeymanLdmlParser` populates the field

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt) —
tokenises the `longPress=` attribute on whitespace, decodes each
token's LDML escapes (`\u{xxxx}`, `\uxxxx`), and keeps the first
codepoint of each. Order is preserved to match the LDML author's
intent (alternates are usually authored in popularity / display
order).

The empty-output guard now allows a key with no `output=` and no
`shift=` but a populated `longPressAlternates` — that's a key that
exists only as a long-press source, which is unusual but legitimate.

### Tests

[`KeymanLdmlParserTest`](app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt)
gains two new tests and two existing tests are extended:

- `longPress alternates are populated alongside shift when both attributes are present` —
  proves the alternates list is captured independently of the shift
  slot when both attributes are declared.
- `empty longPressAlternates when longPress attribute is absent` —
  proves the field defaults cleanly.
- The pre-existing `multi-alternate longPress with no shift leaves shift slot null`
  test now also asserts `longPressAlternates == listOf('ä'.code, 'á'.code, 'à'.code)`.
- The pre-existing `single-alternate longPress with no shift remains usable as shift fallback`
  test now also asserts the alternate codepoint lands in
  `longPressAlternates`.

## What this release does NOT do

**Popup-routing.** The on-screen keyboard's long-press popup currently
reads alternates from `KeyData.popup`, which is a software-keyboard
data model unrelated to `HardwareKeyEntry`. Wiring
`HardwareKeyEntry.longPressAlternates` into the popup would require:

- A new bridge between the hardware-keyboard runtime mapper and the
  popup controller.
- A way to identify which on-screen key is "the long-press source" when
  a hardware keystroke is the trigger.
- Snygg styling for the hardware-source popup variant.

That's a multi-file feature slice worth its own scoped release; for
now `longPressAlternates` is populated and exposed but not consumed at
input time. This release lands the parser side so the future popup
slice doesn't need a second parser change.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt`
- `gradle.properties` — versionCode 1902 / versionName 1.8.102

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

The four updated / new tests in `KeymanLdmlParserTest` should pass;
existing tests for the parser should continue to pass with the
extended assertions.
