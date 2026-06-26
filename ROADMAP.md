# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

### P3

- [ ] P3 — **Minimal visual layout editor emitting existing layout JSON**
  Why: custom layout editing is a top upstream ask (florisboard #196, +22) no
  FOSS keyboard serves well; SwiftFloris already consumes layout JSON and
  imports KLC/keylayout/LDML, so a constrained row/key editor that round-trips
  the existing format is a leapfrog with bounded scope.
  Evidence: https://github.com/florisboard/florisboard/issues/196 ; existing
  import pipeline (`ime/hardware/`, layout assets).
  Touches: new Settings screen under keyboard/layout preferences; layout JSON
  serializer round-trip; preview via existing keyboard preview field.
  Acceptance: user can clone a bundled layout, swap/add/remove keys in rows,
  preview, save as a local layout selectable in subtype editor; invalid edits
  are rejected with visible validation.
  Complexity: L

## Research-Driven Additions

### P2

- [ ] P2 — **Use Roborazzi preview filtering to broaden visual baselines**
  Why: Existing screenshot tests are hand-curated, while Roborazzi 1.64 adds preview annotation filtering and fixes useful for Gradle 9-era snapshot validation.
  Evidence: GitHub PR #14; Roborazzi 1.64 release notes; `ThemeAndAddonsScreenshotTest`; `PendingSettingsScreensScreenshotTest`; `PendingKeyboardSurfacesScreenshotTest`; `ExtensionMaintainerChipScreenshotTest`.
  Touches: `gradle/libs.versions.toml`; Roborazzi test configuration; Compose preview annotations; snapshot baselines for Settings, addons, privacy posture, snippets, themes, and keyboard surfaces.
  Acceptance: Roborazzi is upgraded to 1.64.x, annotated previews for high-risk Settings/theme/addon surfaces are captured and verified in CI, and unrelated previews are excluded by annotation filter.
  Complexity: M

- [ ] P2 — **Add synthetic glide trace cap and latency regression tests**
  Why: The public FUTO corpus benchmark is externally gated, but SwiftFloris can still prove overlong/noisy trace handling and keep the hard-coded gesture cap from regressing.
  Evidence: `StatisticalGlideTypingClassifier.kt` `Gesture.MAX_SIZE = 500` TODO; `docs/BENCHMARKS.md` glide trace benchmark note; FUTO Swipe public dataset.
  Touches: glide classifier tests; synthetic trace fixtures; `scripts/glide-benchmark.py`; benchmark documentation.
  Acceptance: synthetic tests cover overlong, sparse, noisy, and high-speed glide traces; benchmark output reports p95 latency and failure counts; no external dataset is required for CI.
  Complexity: M

### P3

- [ ] P3 — **Resolve the `ImeWindowMode.Fixed.THUMBS` placeholder**
  Why: A placeholder thumb mode is dead architecture unless it is implemented, hidden, or removed; niche keyboard peers show thumb layouts are useful only when intentionally designed and tested.
  Evidence: `ImeWindowMode.kt`; Thumb-Key; 8VIM; Unexpected Keyboard.
  Touches: `ime/window/ImeWindowMode.kt`; keyboard window sizing/layout constraints; Settings exposure if any; migration/default handling; preview or Roborazzi coverage.
  Acceptance: `THUMBS` is either fully implemented with selectable behavior, previews, and tests, or removed/hidden with safe migration for any persisted values.
  Complexity: M
