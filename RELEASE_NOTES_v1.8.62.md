# SwiftFloris v1.8.62 — 2026-05-17

Phase C1 — split-keyboard renderer wire-up.

## Why ship this now

`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` identifies split-keyboard tablet
rendering as the remaining visual SwiftKey parity gap after the preference,
window mode, constraints, and row-boundary calculator were already present.
The prior scaffold could shift right-half keys, but it did not pre-shrink the
base layout width, so a shifted row could overflow the keyboard container.
It also allowed split mode on non-viable narrow roots and let adaptive-touch
nearest-key rescue grab taps near the gutter.

No new permissions, dependencies, assets, network surfaces, or background jobs
are added.

## What changed

### Renderer width accounting

Added `TextKeyboardSplitLayout` as the small render-policy helper for split
mode. When the active window spec is `Fixed.SPLIT`, the split constraints are
viable, and the keyboard mode is `CHARACTERS`, `TextKeyboardLayout` now:

1. resolves the active `Fixed.Split.defaultGutter`;
2. clamps the gutter to a bounded fraction of the keyboard width;
3. lays out the base `TextKeyboard` using `keyboardWidth - gutter`;
4. applies `SplitGutterPostPass.apply(keyboard, gutterPx)` so the right half
   moves back into the final container with a real mid-row gutter.

The result is a split layout that keeps the final right edge inside the same
container width as the non-split layout.

### Viability gate

`ImeWindowController` now checks `Fixed.Split.isViable` before promoting the
fixed window to `Fixed.SPLIT`. If a persisted config requests split mode on a
narrow root, `doComputeWindowSpec(...)` safely demotes it to `Fixed.NORMAL`
for that form factor.

### Gutter-aware touch behavior

`TextKeyboard.isPointInSplitGutter(...)` identifies the generated gap between
each row's left and right halves. `getNearestKeyForPos(...)` now refuses
adaptive-touch nearest-key rescue inside that gap, so tapping the gutter does
not land on the nearest key on either side.

### Documentation

Added `docs/SPLIT_KEYBOARD.md` with the activation, rendering, touch behavior,
and verification contract for future split-layout work.

## Tests

Added / updated unit coverage for:

- split gutter enabling only in viable character-mode split windows;
- gutter clamp + pre-pass layout-width reduction;
- pre-shrunk layout + post-pass preserving the final row width;
- gutter taps returning no primary key and no nearest-key rescue;
- persisted split configs falling back to fixed normal on narrow roots;
- split preference promotion only on viable roots;
- existing fixed-window property tests updated for the split fallback.

## Versioning

- `gradle.properties`: `projectVersionCode=1862`,
  `projectVersionName=1.8.62`.

## Verification

Local non-Java check:

```powershell
git diff --check
```

This VM still has no JDK / Android SDK on the path, so run before merge on
the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.SplitGutterPostPassTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardSplitLayoutTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.window.ImeWindowControllerTest
```

## What's next

The remaining unblocked SwiftKey-parity work is C3 High-Contrast / animated
themes and D1 calendar quick-insert. B5 still needs human-captured local trace
fixtures before decoder constants should move.
