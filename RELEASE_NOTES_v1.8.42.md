# SwiftFloris v1.8.42 — 2026-05-16

N14.2 — Kotlin `2.3.20` → `2.3.21` bug-fix bump.

## What changed (user-visible)

Nothing — pure toolchain bump.

## What changed (internal)

### N14.2 — Kotlin 2.3.21

`gradle/libs.versions.toml`:

```toml
[versions]
-kotlin = "2.3.20"
+kotlin = "2.3.21"
```

That's the only source change. Every Kotlin Gradle plugin
(`kotlin-android` / `kotlin-jvm` / `kotlin-plugin-compose` /
`kotlin-serialization`) plus `kotlin-reflect` + `kotlin-test-junit5`
all declare `version.ref = "kotlin"`, so they pick up `2.3.21`
automatically.

Per the [Kotlin 2.3.21 release notes][KOTLIN-2321] (Apr 23 2026,
bug-fix line), this release closes:

- Wasm IC (incremental compilation) cache invalidation bug.
- Kotlin/Native ObjC protocol metaclass cast regression.
- AGP 9.1 R8 artifact-clear regression that surfaced as `Task
  :app:assembleRelease FAILED` for downstream projects pinning
  Kotlin 2.3.20 + AGP 9.1.
- KGP composite-build state mismatch under Wasm IC.

None of these regressions affected SwiftFloris's CI gates today
(no Wasm, no Native, no composite-build, AGP 9.0 not 9.1), but
the bump is free and keeps the toolchain on the current
bug-fix tip per the roadmap §6 N14.2 line.

### Docs

`docs/REPRODUCIBLE_BUILDS.md` "Pinned toolchain inputs" table
bumped from `2.3.20` → `2.3.21`; ROADMAP §2 "Stack" line bumped
likewise so the `git grep 2.3.20` audit is clean.

[KOTLIN-2321]: https://kotlinlang.org/docs/releases.html

## Versioning

- `gradle.properties`: `projectVersionCode=1842`,
  `projectVersionName=1.8.42`.

## What's next

- **N14.1** — Uncomment `alias(libs.plugins.roborazzi)` and bump
  `roborazzi = "1.39.0"` → `"1.55.0"` now that the AGP 9 plugin
  is live.
- **N13.2** — Audit IME-visibility-on-config-change for the
  Android 17 (API 37) behavior change.
- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual
  trackpad on long-press space).
