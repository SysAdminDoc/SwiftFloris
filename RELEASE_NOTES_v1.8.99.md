# Release v1.8.99 — HardwareKeyboardLayout equality fast-path

Date: 2026-05-17

Follow-up F6 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt)
— overrides the auto-generated `equals` / `hashCode` so `scancodeMap` is
no longer walked entry-by-entry in the common case.

The data class previously inherited the generated equality, which calls
`Map.equals` on `scancodeMap`. A real LDML layout has ~300 keys and
each `HardwareKeyEntry` is itself a data class with eight fields —
O(n*m) per comparison. The mapper / settings paths compare layouts often
(device attach, pruning, refresh after rescan), so the cost
compounds.

The override has three fast-paths:

1. **`this === other`** — O(1) when the same layout reference is held
   by both sides (the dominant case: the mapper hands the same reference
   around).
2. **Different metadata** (`name`, `locale`, or `scancodeMap.size`) —
   O(1) reject without touching the map.
3. **Same metadata but `scancodeMap !== other.scancodeMap`** — fall
   through to the structural walk to preserve correctness for the rare
   cross-instance comparison. Two layouts produced by separate parse
   calls always have distinct map references, so this fallback runs only
   when the caller explicitly compares two parser outputs.

`hashCode` is now `name + locale + scancodeMap.size`, invariant across
all cases the new `equals` treats as equal.

`componentN()` / `copy()` keep their data-class semantics unchanged.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt`
- `gradle.properties` — versionCode 1899 / versionName 1.8.99

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

The existing
[`KeymanLdmlParserTest`](app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt)
exercises layout equality through `shouldBe` comparisons and should
continue to pass unchanged — the override preserves "two structurally
identical layouts compare equal" semantics for the same-reference and
size-mismatch cases, and falls through to the structural walk for the
remaining tail case.
