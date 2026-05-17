# Split Keyboard

SwiftFloris supports a tablet split layout for the character keyboard.
The base app still has no network, account, or telemetry dependency for
this mode.

## Activation

Split mode is controlled by `prefs.keyboard.splitKeyboardEnabled`. When the
IME window is shown, `ImeWindowController` promotes fixed mode to
`ImeWindowMode.Fixed.SPLIT` only when `ImeWindowConstraints.Fixed.Split`
reports `isViable`.

The current viability floor is `600.dp` of root-window width. Persisted split
configs on narrower roots are treated as `Fixed.NORMAL`, so phones and narrow
multi-window layouts do not render a cramped split.

## Rendering

`TextKeyboardLayout` resolves the active split gutter from
`Fixed.Split.defaultGutter`, then delegates the policy to
`TextKeyboardSplitLayout`:

- split rendering applies only to `KeyboardMode.CHARACTERS`;
- the base layout width is reduced to `keyboardWidth - gutter`;
- `SplitGutterPostPass.apply(...)` shifts every right-half key by the gutter;
- `touchBounds` and `visibleBounds` move together.

This keeps each split row inside the same final container width as the
non-split layout while leaving a real mid-row gap.

## Touch Behavior

`TextKeyboard.isPointInSplitGutter(...)` detects the generated gap between a
row's left and right halves. `getNearestKeyForPos(...)` returns `null` in that
zone, so adaptive-touch rescue cannot turn a gutter tap into the nearest key.

Primary hit-testing already uses the shifted `touchBounds`, so taps on keys
and taps in the gutter use the same geometry the renderer draws.

## Verification

Relevant JVM tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.SplitGutterPostPassTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardSplitLayoutTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.window.ImeWindowControllerTest
```

Manual QA target: enable split keyboard on a tablet or emulator wider than
`600.dp`, open a character layout, and verify the gutter appears without
right-edge overflow. Taps in the gutter should do nothing.
