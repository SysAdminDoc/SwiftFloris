# SwiftFloris v1.8.79

Released: 2026-05-17

## Honeycomb hex layout wire-up

This release turns the earlier honeycomb renderer scaffolding into a selectable
production character layout path.

### What changed

- Registered `layouts/characters/honeycomb.json` in the bundled layout
  extension manifest so it can appear in subtype layout selection.
- Added `TextKeyboardLayoutStyle.Honeycomb` and taught `LayoutManager` to mark
  the bundled `honeycomb` character layout with that style.
- Added honeycomb-specific `TextKeyboard.layoutHoneycomb(...)` geometry that
  positions real `TextKey` instances in the existing tessellated hex grid.
- Routed honeycomb hit testing through the actual hex shape instead of the
  rectangular key bounding boxes; touches in bounding-box corners and inter-key
  gaps no longer activate a neighboring key.
- Reused the production `TextKeyboardLayout` and `TextKeyButton` surfaces while
  clipping Snygg-rendered key backdrops to `HoneycombHexShape`, preserving the
  normal popup, accessibility, input-dispatch, and feedback paths.
- Added `TextKeyboardHoneycombLayoutTest` coverage for odd-row offsets, center
  hits, corner rejection, and unchanged rectangular gap rescue for standard
  layouts.

### Privacy and security

- No network permission, telemetry, account, or cloud dependency was added.
- The honeycomb path is a local layout/rendering change only.

### Verification

- `git diff --check`
- Manifest permission scan for banned network permissions
- Root JVM crash/replay tracked-file guard
- Focused Gradle test attempted with
  `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardHoneycombLayoutTest`;
  this VM still cannot run Gradle because `JAVA_HOME` is unset and `java` is
  not on `PATH`.
