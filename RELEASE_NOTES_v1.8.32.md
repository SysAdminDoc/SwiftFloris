# SwiftFloris v1.8.32 — 2026-05-15

L9.2 honeycomb renderer slice — `HoneycombHexButton` and
`HoneycombKeyboardRow` Compose building blocks now live alongside
the v1.8.31 `HoneycombHexShape`. **937 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing yet — these are renderer primitives. The honeycomb-tiled
keyboard layout still ships disabled in the layout selector. Wiring
them into `TextKeyboardLayout` (touch routing, theme + Snygg
integration, popup support) lands in a follow-up release.

## What changed (internal)

### Next-9.2a — `HoneycombHexButton`

New `ime/text/keyboard/HoneycombHexButton`:

- Single-cell Compose composable for a honeycomb-tiled key.
- Clips its modifier-supplied bounding box to `HoneycombHexShape`
  (six straight edges, six 60° vertices — no pill/oval/capsule
  backdrop, per the global GUI rule).
- Idle vs. pressed background colours (`pointerInput` +
  `detectTapGestures` + `tryAwaitRelease` flips a local `pressed`
  flag).
- `onTap` and `onLongPress` callbacks. Defers theming, popup
  rendering, and input-feedback hooks to the TextKeyboardLayout
  integration follow-up.
- Defaults pull from a dark Catppuccin-adjacent palette
  (`0xFF2A2D40` idle / `0xFF3D4159` pressed) until Snygg flows
  in.

### Next-9.2b — `HoneycombKeyboardRow`

New `ime/text/keyboard/HoneycombKeyboardRow`:

- Multi-row Compose composable that lays out a `List<List<String>>`
  of labels as a flat-top hex tessellation.
- Geometry comes from the existing `HoneycombTessellation` shipped
  in v1.8.4 — row stride `1.5·r`, column stride `√3·r`, odd-indexed
  rows offset by half a column-stride.
- Absolute positioning via Compose's `offset` modifier — the
  minimal renderer slice. Touch routing and Snygg theming wait
  for the TextKeyboardLayout call-site work.
- Sized by the caller via the outer modifier; `keyRadiusDp`
  defaults to 24 dp (sensible for a 6–7" phone).
- `onKeyTap` callback fires with `(row, col, label)` so the
  upstream `KeyboardManager` can map taps to the layout's key
  set without coupling this composable to `TextKeyboardLayout`.

## Test surface

- 937 unit tests pass (`./gradlew :app:testDebugUnitTest --offline`).
- Pure-Compose composables — no JVM-testable geometry surface
  beyond what `HoneycombHexShape`/`HoneycombTessellation` already
  cover. Visual regression / Compose-test coverage lands in a
  follow-up (Roborazzi or instrumentation).

## Versioning

- `gradle.properties`: `projectVersionCode=1832`,
  `projectVersionName=1.8.32`.
- README badge bumped to `v1.8.32`.

## What's next

- Compose-test (Roborazzi or instrumentation) for
  `HoneycombKeyboardRow` to lock the visual layout.
- L9.2 final step: wire `HoneycombKeyboardRow` into
  `TextKeyboardLayout` — touch routing through the existing
  pointer-event pipeline, Snygg theme integration, popup support.
