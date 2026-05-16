# SwiftFloris v1.8.43 — 2026-05-16

N14.1 — Roborazzi plugin alias uncommented. Visual-regression
verify now wired into CI.

## What changed (user-visible)

Nothing — pure CI infrastructure bump.

## What changed (internal)

### N14.1 — Roborazzi 1.55.0 + plugin alias

`gradle/libs.versions.toml`:

```toml
[versions]
-roborazzi = "1.39.0"
+roborazzi = "1.55.0"
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.plugin.compose)
    ...
    alias(libs.plugins.kotlinx.kover)
-    // alias(libs.plugins.roborazzi)  // commented during AGP-9 gate
+    alias(libs.plugins.roborazzi)
}
```

Roborazzi 1.55.0 (Jan 2026 line) shipped AGP 9 support via
[PR #782][ROBORAZZI-782], so the plugin's `TestedExtension` API
churn that blocked the previous v1.43.x line is resolved. The
plugin alias being applied lights up two Gradle tasks:

- `:app:recordRoborazziDebug` — runs every Roborazzi-annotated
  JUnit test and writes the captured PNGs to
  `app/src/test/snapshots/images/` as the baseline. Maintainer
  task; not run in CI.
- `:app:verifyRoborazziDebug` — re-runs the same tests and
  diff-compares each capture against the baseline. Fails if any
  snapshot drifts beyond the default change threshold (0.01).

### N14.1 — CI step

`.github/workflows/android.yml`:

```yaml
- name: Roborazzi visual-regression verify (N14.1)
  run: ./gradlew :app:verifyRoborazziDebug
  continue-on-error: true
```

Inserted between the unit-tests step and the lint step.
`continue-on-error: true` is set for the bootstrap window because
no baseline PNGs are committed yet — without it, every PR would
red-flag with `verifyRoborazziDebug FAILED: snapshot baseline
missing`. Once a maintainer runs `:app:recordRoborazziDebug`
locally and commits the resulting `.png` files under
`app/src/test/snapshots/images/`, the flag can be removed and
the verify becomes a hard gate.

The existing `ExtensionMaintainerChipScreenshotTest` (Next-12.2
sample suite) is the first test the plugin lights up. Follow-up
batches will extend Roborazzi coverage to:

- The smartbar candidates row.
- The seven M3 Expressive theme keys (Nord light/dark, Tokyo
  Night, Dracula, Catppuccin Mocha, SwiftKey Pure M3E light/dark).
- The floating-window border.
- The stylus handwriting overlay (when Next-4.2 ML Kit Digital
  Ink addon lands).

[ROBORAZZI-782]: https://github.com/takahirom/roborazzi/pull/782

## Versioning

- `gradle.properties`: `projectVersionCode=1843`,
  `projectVersionName=1.8.43`.

## What's next

- **Roborazzi baseline capture** — maintainer-side `:app:recordRoborazziDebug`
  run to commit the first batch of baseline PNGs, then remove
  `continue-on-error: true`.
- **N13.2** — Audit IME-visibility-on-config-change for the
  Android 17 (API 37) behavior change.
- **N13.3** — Audit long-press popup rendering on password fields
  to skip when the active variation is `PASSWORD` / `VISIBLE_PASSWORD`
  / `WEB_PASSWORD` regardless of input source.
