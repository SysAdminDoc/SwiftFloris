# SwiftFloris Dependency Triage Playbook

**ROADMAP matrix #41 + #42 + #43.** This document is a short triage checklist for the maintainer when a visual / insets
/ test / a11y / SDK bug appears, or when a dependency-bump PR opens. The point is to bias investigation toward the
common case — "this is upstream's bug" — before re-architecting app code.

## When to suspect Compose BOM / Material first

Symptoms that are usually upstream Compose / Material patches:

- Padding / inset jumps after a Compose BOM bump.
- Theme animation regressions (springs, motion).
- Focus behavior changes on `TextField`, `BasicTextField`, or any popup.
- `Scaffold` / `BottomSheet` / `ModalBottomSheet` keyboard-overlap regression on Android 14+.
- `LazyList` / `LazyGrid` scroll glitches.
- `Snackbar` / `Tooltip` placement off after rotation.
- Material3 color-role drift between BOM minor versions.

Check first:

1. `gradle/libs.versions.toml` — note the current `[versions] compose-bom` and `material3` references.
2. Compose BOM changelog at https://developer.android.com/jetpack/androidx/releases/compose — diff between the
   version we're on and the next two patch releases.
3. Material3 changelog at https://developer.android.com/jetpack/androidx/releases/compose-material3 — same diff.
4. If the symptom matches a fixed bug in the next patch, **prefer the BOM bump** over an app-code workaround.
   The bump is reversible, the workaround often is not (it leaves a stale conditional that nobody touches).

If the BOM has no relevant fix, then suspect app code — and even then, bisect by reverting recent merges in
`app/src/main/kotlin/dev/patrickgold/florisboard/ime/{keyboard,theme,smartbar}` rather than chasing the symptom.

## When to suspect Room / WindowManager

Less common but high-cost when they regress. Check the changelog when:

- Room migrations fail at app upgrade on a real device.
- `WindowMetrics` reports unexpected values after rotation on Android 15+ split-screen.
- `WindowInsetsCompat` flips visibility for `Type.ime()` mid-frame.
- Database query plans degrade after a Room minor bump.

See also https://developer.android.com/jetpack/androidx/releases/room and
https://developer.android.com/jetpack/androidx/releases/window.

## Bump cadence policy

- **Patch bumps (`x.y.Z` → `x.y.Z+1`).** Land on the same day as the upstream release if `osv-scanner` is clean and
  unit tests pass. No discussion needed — it is strictly safer than staying behind on a known bug-fix release.
- **Minor bumps (`x.Y.0` → `x.Y+1.0`).** Land after reading the upstream changelog and running the full
  Roborazzi + macrobenchmark suite. Note any new lint warnings; address before merging.
- **Major bumps (`X.0.0` → `X+1.0.0`).** A separate slice with its own PR + release note line. Pin the rollback
  path in the PR description.
- **Pre-release / alpha / beta / RC.** Avoid by default. Use only when the previous stable carries a CVE or a
  known crash regression the team is hitting locally.

## AGP 9.x → API 37 plan (matrix #43)

`AGP 9.1+` and `Kotlin 2.x` are the load-bearing path to `targetSdk 37`. Recommended sequencing:

1. Stay on AGP 9.0.0 + Kotlin 2.3.21 until upstream tags AGP 9.1.0 stable. Track the milestone in
   https://issuetracker.google.com/issues?q=AGP%209.1.
2. Bump AGP 9.0.0 → 9.1.0 in a dedicated PR. Re-run macrobenchmark + Roborazzi.
3. After the AGP bump, raise `compileSdk` to 37 in `gradle/tools.versions.toml`. The Android 17 behavior change set
   (already partially closed by v1.8.44 password popup guard + v1.8.45 IME visibility restore) defines the
   `compileSdk = 37` task list.
4. Raise `targetSdk` to 37 only after the full behavior-change set is closed. Bumping `targetSdk` exposes the app
   to Android 17's new defaults — only do it once every behavior gate is wired.

## Quick reference

| Upstream | Changelog URL |
|----------|---------------|
| Kotlin | https://kotlinlang.org/docs/releases.html |
| Android Gradle Plugin | https://developer.android.com/build/releases/gradle-plugin |
| Compose BOM | https://developer.android.com/jetpack/androidx/releases/compose |
| Material3 | https://developer.android.com/jetpack/androidx/releases/compose-material3 |
| Room | https://developer.android.com/jetpack/androidx/releases/room |
| WindowManager | https://developer.android.com/jetpack/androidx/releases/window |
| Roborazzi | https://github.com/takahirom/roborazzi/releases |
| SQLCipher (AndroidX bridge) | https://developer.android.com/jetpack/androidx/releases/sqlite |
| OSV-Scanner | https://github.com/google/osv-scanner/releases |

## Audit log

| Date | Pin audited | Conclusion |
|------|-------------|------------|
| 2026-05-17 (v1.8.69) | Bump-batch A: coroutines `1.11.0`, KSP `2.3.8`, ZXing `3.5.4`, AboutLibraries `14.2.0` | Applied after re-checking Maven Central / Gradle Plugin Portal metadata. AboutLibraries `15.0.0-b01` exists but is beta, so the stable line remains `14.2.0`. No app code, permissions, or runtime behavior changed. Full Gradle verification is still delegated to the maintainer build host because this VM has no Java on PATH. |
| 2026-05-17 (v1.8.51) | `androidx-compose-bom = "2026.03.01"` (ROADMAP §6 N14.3) | Audit performed against the [Compose BOM release notes](https://developer.android.com/jetpack/androidx/releases/compose). Current pin is the published March 2026 patch-01 line; no later patch is announced as of this audit. No known crash / a11y / inset regression in the Roborazzi sample suite forces an out-of-band bump. The next scheduled audit window is the day a `2026.04.xx` patch publishes; the bump itself is a separate slice gated on the macrobenchmark + Roborazzi evidence run per the cadence policy above. |
| 2026-05-17 (v1.8.51) | `gradle-wrapper.properties distributionUrl=gradle-9.4.1-bin.zip` + `distributionSha256Sum=2ab2958f...` (ROADMAP §6 N14.4) | Audit performed against the [Gradle release notes](https://gradle.org/releases/). Current pin is on the 9.4.x line; the rule from `docs/REPRODUCIBLE_BUILDS.md` is that any Gradle bump must update `distributionSha256Sum` in lockstep so the wrapper continues to verify. Bumping pure-tooling Gradle (no AGP change) carries low risk but still touches every build host — the bump itself is a separate slice gated on a clean run of `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` on the maintainer's local box per the cadence policy. |
