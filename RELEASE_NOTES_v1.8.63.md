# SwiftFloris v1.8.63 — 2026-05-17

Phase C3 — bundled High Contrast and Aurora Animated themes.

## Why ship this now

`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` tracks SwiftKey theme parity as the
remaining visual-polish batch after the split renderer landed. SwiftFloris
already had SwiftKey Pure and M3 Expressive static stylesheets, but no
dedicated AAA high-contrast theme and no animated bundled theme.

No permissions, dependencies, network surfaces, background jobs, or remote
assets are added.

## What changed

### SwiftKey High Contrast (AAA)

Added the bundled `swiftkey_high_contrast` stylesheet and registered it in
`org.florisboard.themes/extension.json`. The palette uses white-on-black key
surfaces and black-on-yellow action keys, with explicit key, popup, and inline
chip borders so key boundaries and focused alt-glyph popups remain visible.

`ThemeContrastTest` now parses the Snygg stylesheet defines and pins the
High Contrast text/background pairs at the WCAG AAA 7.0:1 floor.

### Aurora Animated

Added the bundled `aurora_animated` stylesheet and a small runtime background
layer gated by the active theme id. `AuroraAnimatedThemeBackground` draws
three translucent aurora bands using Compose `GenericShape` morphs; it respects
Android's animator-duration-scale reduced-motion setting by freezing to the
static first frame when system animations are disabled.

The active theme identity is exposed to IME Compose surfaces through
`LocalActiveThemeName`; normal colors, typography, and Snygg resolution remain
unchanged.

### Theme generator

`scripts/gen_m3e_themes.py` now regenerates the seven M3 Expressive stylesheets
plus the two new C3 stylesheets from the same `swift_slate.json` baseline.
The theme extension manifest moved from `0.3.0` to `0.4.0`, and the bundled
theme count is now 21.

## Tests

Added / updated unit coverage for:

- High Contrast WCAG AAA text/background token pairs;
- bundled manifest registration for High Contrast and Aurora Animated;
- active-theme gating for the Aurora background renderer.

## Versioning

- `gradle.properties`: `projectVersionCode=1863`,
  `projectVersionName=1.8.63`.

## Verification

Local non-Java checks:

```powershell
python scripts/gen_m3e_themes.py
git diff --check
```

Additional local sanity checks parsed the bundled theme manifest as JSON
(`version=0.4.0`, 21 registered stylesheets, no missing stylesheet files) and
computed the High Contrast token-pair contrast floor at 11.82:1 or higher.

This VM still has no JDK / Android SDK on the path, so run before merge on
the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.theme.ThemeContrastTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.window.AuroraAnimatedThemeBackgroundTest
```

Manual device follow-up: verify Aurora Animated on a Pixel 6-class device with
`adb shell settings get global animator_duration_scale` at `1.0` and `0.0`,
recording frame timing if the release gate requires the roadmap's 30 fps note.

## What's next

The remaining unblocked local-code SwiftKey-parity item is D1 calendar
quick-insert. B5 still needs human-captured local trace fixtures before
decoder constants should move.
