# Release v1.8.92 — LDML parser honours shift= over longPress=

Date: 2026-05-17

Follow-up #7 (final) from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt#L113-L140)
— LDML defines:

- `shift=` — the shift-modifier mapping for the key.
- `longPress=` — a space-separated list of alternates surfaced on long-press
  (NOT a shift mapping).

The previous parser was inverted: it tried `longPress` *first*, falling
back to `shift`. Two real bugs followed:

1. **Whenever both `shift=` and `longPress=` were declared on the same
   key, `shift` was silently ignored.** Real Keyman / LDML keyboards
   author shift= for the shift mapping and longPress= for alternates;
   the parser picked the wrong slot.
2. **When only `longPress=` was set with multiple alternates,** only the
   first alternate's first codepoint became the shift value, masking
   the rest of the alternates list with no UX to recover them.

This release reorders:

```
1. If shift= is set                                → use it.
2. Else if longPress= is a single value (no space) → use it
   (preserves Amharic-SERA-style legacy authors who used longPress
   as a shift workaround before this parser learned the right
   semantics).
3. Else                                             → leave shift = null
   (multi-alternate longPress is correctly a list, not a shift slot —
   wait for a future release to add `longPressAlternates: List<Int>` to
   HardwareKeyEntry and route through the long-press UI).
```

The pre-existing fixture (`output="ሀ" longPress="ሁ"`, single-value
longPress) still produces the same result through path #2, so no
already-imported keyboard regresses.

## Tests

[app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt](app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt)
gains three new tests:

- `shift attribute is preferred over longPress for the shift slot` —
  proves the bug fix on the both-attributes case.
- `multi-alternate longPress with no shift leaves shift slot null` —
  proves multi-alternate lists no longer poison the shift slot.
- `single-alternate longPress with no shift remains usable as shift fallback` —
  proves the Amharic-SERA backward-compat case still works.

The pre-existing `normal + shift output round-trip through the
scancode map` test (single-alternate longPress fixture) continues to
pass unchanged.

## Why not also implement longPressAlternates now

Adding `longPressAlternates: List<Int>` to
[HardwareKeyEntry](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt)
and routing the alternates through the long-press popup is a larger
slice that crosses the LDML parser, the data class, the popup
controller, and the popup-UI snygg surface. It's worth its own
per-feature release once the existing long-press popup is audited for
hardware-keyboard-source events. Tracked in the v1.8.85
[follow-up roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt`
- `gradle.properties` — versionCode 1892 / versionName 1.8.92

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

The new tests should pass; the pre-existing Amharic-fixture test should
continue to pass.
