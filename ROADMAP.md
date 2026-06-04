# SwiftFloris Roadmap

> Single source of truth for all planned work. Items above the --- are existing plans; items below are research conducted 2026-06-03.

**Current release:** v1.8.211 (versionCode 2011). **Baseline green:** `:app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

Hard rules still apply (see `AGENTS.md`): no `INTERNET` permission in `:app`; Apache-2.0 ceiling on `:app`; no closed-source blobs; one logical change per commit; every shipped release bumps `gradle.properties` version, writes a `CHANGELOG.md` section, and adds a `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (draft <=480 chars for headroom).

Item IDs trace to their origin research: `F#`/`EI#` from the archived 2026-05-25 research feature plan; `R#`/`O#` from the 2026-05-25 second-pass findings; `WS#` from the archived improvement-plan workstreams; `N#`/`Next-#`/`L#` from the archived roadmap tiers. Shipped items and reframed/rejected items live in `COMPLETED.md`; full release detail in `CHANGELOG.md`. Historical strategy (tiered NOW/NEXT/LATER, sourced appendix) is preserved at `docs/archive/ROADMAP_v5.67_2026-05-18.md`.

## Existing Planned Work

### Keyboard surface & visual polish (device-gated)

- [ ] P1 — Keyboard surface polish + manual-override verification (WS11)
  - Why: Candidate-row, smartbar, and software-key states plus the full layout matrix need real-field verification; cannot fully close without a device.
  - Touches: candidate-row selection/pressed/disabled/correction states; smartbar ordering + overflow + long-label resilience; software-key pressed/held/disabled/gesture states; one-handed/floating/split/compact/landscape/tablet layouts.
  - Acceptance: each state and layout verified in real input fields on a device.
  - Source: TODO.md B / improvement-plan WS11.
- [ ] P1 — Glide-trail theme baselines + low-end perf evidence (F9)
  - Why: Glide-trail themes lack Roborazzi baselines and low-end (<=4 GB) performance evidence.
  - Touches: Roborazzi baselines (device/emulator); trace `swiftfloris.glide.trailDrawMs` on Pixel 4a / Galaxy A12-class.
  - Acceptance: baselines recorded; trail-draw timing captured on low-end hardware.
  - Source: TODO.md B / research feature plan F9.
- [ ] P2 — F40 Roborazzi capture phase (F40 capture)
  - Why: Screen-level Roborazzi test classes ship baseline-pending (v1.8.201); the baseline PNGs still need on-device capture.
  - Touches: `:app:recordRoborazziDebug` for the A1 test classes, then remove the class-level `@Ignore` from the pending F40 screenshot classes.
  - Acceptance: baseline PNGs captured; `@Ignore` removed; gate green.
  - Source: TODO.md A1/B / research feature plan F40.
- [ ] P2 — Glide-trail reduced-animation + tooltip verification (EI4 residual)
  - Why: Confirm Rainbow/Aurora/Neon glide trails honour `ANIMATOR_DURATION_SCALE == 0f` on-device; the doc disclosure already shipped (v1.8.182).
  - Touches: GesturesScreen "i" tooltip + on-device animation-scale check.
  - Acceptance: trails respect zero animation scale; tooltip present.
  - Source: TODO.md B / second-pass EI4.

### Data safety, backup/restore & import (device-gated portions)

- [ ] P1 — Backup/restore + import path-safety device confirmation (WS13 device portions)
  - Why: Unit tests for these paths are Tier A and done; the on-device confirmation is still required.
  - Touches: backup/restore overwrite-vs-merge; clipboard media missing-file/path-safety; extension-import path-traversal.
  - Acceptance: overwrite/merge, missing-media, and traversal behaviors confirmed on-device.
  - Source: TODO.md B / improvement-plan WS13.

### CI, build & release hardening

- [ ] P1 — Chain reproducible-build verification to release-tag flow (F23)
  - Why: Reproducible-build verification is not gated on release.
  - Touches: `workflow_call` from `release.yml`, or block tag publish until repro is green.
  - Acceptance: a tag cannot publish unless reproducible-build verification passes.
  - Source: TODO.md A4 / research feature plan F23.
- [ ] P1 — `:app:verifyRoborazziRelease` gate (F24)
  - Why: R8/minify can rename Compose semantics nodes and nothing catches it today.
  - Touches: add a release-variant Roborazzi verification task.
  - Acceptance: release-variant screenshot gate runs and catches minify-induced semantics drift.
  - Source: TODO.md A4 / research feature plan F24.
- [ ] P2 — Macrobenchmark trend-regression job (EI9)
  - Why: No automated regression check against benchmark baselines.
  - Touches: `workflow_dispatch` job diffing against `docs/benchmark-results/baseline-*.json`; floor/target ranges documented in `docs/BENCHMARKS.md`.
  - Acceptance: job reports regressions against the baseline within documented ranges.
  - Source: TODO.md A4 / research feature plan EI9.

### Docs & hygiene

- [ ] P2 — Confirm absence of lint baseline / close EI10 (EI10)
  - Why: Research says no `app/lint-baseline.xml` exists; confirm and note.
  - Touches: one-line note; `bash scripts/run-lint-debug-with-baseline-check.sh` exits 0.
  - Acceptance: confirmed no baseline file; note added or item closed.
  - Source: TODO.md A5 / research feature plan EI10.
- [ ] P2 — Localization content-quality pass (WS12)
  - Why: Turkish repeated-word lint, vague/abrupt English source labels, and inconsistent failure/destructive copy need cleanup.
  - Touches: native-safe Turkish repeated-word review; tighten English source labels; standardize backup/restore/import/export failure + destructive-confirmation copy; document translation-safe cleanup rules.
  - Acceptance: lint warnings reviewed; copy standardized; rules documented.
  - Source: TODO.md A5 / improvement-plan WS12.
- [ ] P2 — Visual-QA + manual-QA + release-evidence checklists (WS10 / WS15)
  - Why: No standing checklists for the portrait/landscape/compact/floating/dark/high-font-scale matrix, manual QA, or release evidence.
  - Touches: docs for visual-QA matrix, manual-QA flow, and release-evidence capture.
  - Acceptance: three checklists exist and are referenced from the verification docs.
  - Source: TODO.md A5 / improvement-plan WS10/WS15.
- [ ] P3 — Fastlane changelog drafting guide (R5)
  - Why: No documented guidance on drafting the <=480-char fastlane changelog.
  - Touches: add the guide to `docs/LOCAL_VERIFICATION.md` / `docs/REPO_HYGIENE.md`.
  - Acceptance: guide present with the character-budget rule.
  - Source: TODO.md A5 / second-pass R5.
- [ ] P3 — Document module build-cache survival (O1)
  - Why: `lib/<module>/build/` cache survives `git rm --cached`; this surprises contributors.
  - Touches: note in `docs/REPO_HYGIENE.md`.
  - Acceptance: behavior documented.
  - Source: TODO.md A5 / second-pass O1.

### External-action-blocked / sibling-repo / XL (maintainer decision required)

These are genuine blockers — each needs an account, key, sibling repo, ML infra, or a product decision the code cannot make.

- [ ] P0 — Crowdin sync of v1.8.179 + v1.8.186 string drops (R1)
  - Why: 44 stale translated entries across 22 locales; Crowdin web console is source of truth (lint `UnusedResources` until done).
  - Touches: server-side Crowdin sync/pull.
  - Acceptance: translations synced; stale-entry lint clears.
  - Source: TODO.md C / second-pass R1.
- [ ] P1 — FlorisBoard `0.6.0-alpha02` cherry-picks (F22)
  - Why: Upstream CLDR 48, Emoji 17, number-field fix, and floating-window foundation are worth picking up; conflict resolution needs iterative on-device builds and risks regressing shipped features.
  - Touches: cherry-pick + conflict resolution across input/emoji/layout.
  - Acceptance: picks merged without regressing shipped features; on-device verified.
  - Source: TODO.md C / research feature plan F22.
- [ ] P1 — Apache-2.0 glide model trained on the MIT FUTO swipe dataset (F21)
  - Why: A licensed in-tree glide model needs off-device ML training infra (XL, out-of-tree).
  - Touches: external training pipeline + model integration.
  - Acceptance: Apache-2.0-clean model trained and integrated.
  - Source: TODO.md C / research feature plan F21.
- [ ] P2 — Bundled Vosk small-en-us recognizer addon (F8)
  - Why: Needs a sibling addon repo + JNI; `RECORD_AUDIO` only in the addon, never `:app`.
  - Touches: sibling addon repo, JNI binding.
  - Acceptance: recognizer ships as a signed addon; `:app` stays permission-clean.
  - Source: TODO.md C / research feature plan F8.
- [ ] P2 — CycloneDX SBOM + SLSA provenance on release (F10)
  - Why: Needs GitHub Attestations onboarding + release-tag dispatch.
  - Touches: release workflow attestation step.
  - Acceptance: SBOM + provenance attached to releases.
  - Source: TODO.md C / research feature plan F10.
- [ ] P2 — GPG-signed release tags (F11)
  - Why: Needs a maintainer GPG key.
  - Touches: release-tag signing.
  - Acceptance: tags are GPG-signed and verifiable.
  - Source: TODO.md C / research feature plan F11.
- [ ] P2 — F-Droid `fdroiddata` submission (F12)
  - Why: `dev.patrickgold.florisboard(.beta)` package-id collides with upstream; needs a rename/coexistence decision plus a multi-month review queue.
  - Touches: fdroiddata metadata + package-id decision.
  - Acceptance: submission accepted into the F-Droid queue.
  - Source: TODO.md C / research feature plan F12.
- [ ] P2 — FunctionGemma 270M MCP-bridge addon (F30)
  - Why: Needs a sibling addon repo.
  - Touches: sibling addon repo + MCP bridge.
  - Acceptance: addon bridges FunctionGemma over MCP without linking into `:app`.
  - Source: TODO.md C / research feature plan F30.
- [ ] P3 — Cross-platform desktop dictionary-export CLI (F13)
  - Why: Needs a sibling repo.
  - Touches: standalone CLI project.
  - Acceptance: CLI exports the dictionary format cross-platform.
  - Source: TODO.md C / research feature plan F13.

#### Open questions blocking the external-action items (maintainer decisions)

1. F-Droid package-id: coexist with upstream FlorisBoard `.beta` or rename? (blocks F12)
2. Vosk 40 MB addon in 2026, or voice stays FUTO-handoff-only? (affects F8 + EI7 copy)
3. Maintainer GPG key (Yubikey-backed?) for signed tags? (affects F11)
4. F-Droid submission timing — during the migration spike or a quiet week? (affects F12)

---

## Research-Driven Additions

*Research conducted 2026-06-03. Items below are new — not duplicates of Existing Planned Work.*

This pass focused on the v1.8.204 **settings search** drop (the newest feature, shipped this release) and a few cross-cutting gaps the three deep audits (`docs/AUDIT_2026-05-28/29` + `2026-06-02`) and the existing roadmap do not already cover. The search subsystem is a hand-maintained static catalog that mirrors the navigation graph with no drift guard — the highest-leverage net-new work.

### Quick Wins

- [ ] P3 — Strip diacritics in settings-search normalization (RA-5)
  - Why: `searchNormalize()` lowercases and folds `& / - _` but does not fold accents, so a user typing `ä`/`ö`/`ç`/`é` (German, Turkish, French — all shipped subtypes) won't match ASCII labels like "Theme"/"Localization"; non-ASCII queries silently return fewer results.
  - Evidence: `app/.../app/settings/search/SettingsSearchIndex.kt:271-279` — `searchNormalize()` has no `Normalizer.normalize(..., NFD)` + combining-mark strip; `score()` and `search()` both run through it.
  - Touches: `SettingsSearchIndex.searchNormalize()` (add NFD decompose + `\p{Mn}` strip); add a JVM case to `SettingsSearchIndexTest.kt`.
  - Acceptance: `search("themé")` and `search("thèmе")` return the Theme entry; ASCII queries unchanged.
  - Verify: `:app:testDebugUnitTest` (new diacritic test green).
  - Complexity: S
- [ ] P3 — Clear ("X") button + Search IME action on the search field (RA-6)
  - Why: The search `TextField` has no trailing clear affordance and no `KeyboardOptions(imeAction = Search)`; users must select-all-delete to reset and get a newline-style Enter. Every mainstream keyboard's own settings search offers a one-tap clear.
  - Evidence: `app/.../search/SettingsSearchScreen.kt:77-95` — `TextField` sets `leadingIcon` only, `singleLine = true`, no `trailingIcon`, no `keyboardOptions`.
  - Touches: `SettingsSearchScreen` (`trailingIcon` when query non-blank → clears; `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)`).
  - Acceptance: a clear button appears while typing and resets the query; the on-screen Enter reads as a search action.
  - Verify: `:app:assembleDebug`; manual on-device tap.
  - Complexity: S
- [ ] P3 — Auto-focus the search field on screen open (RA-7)
  - Why: Opening Settings → Search lands on a blank field that is not focused, so the keyboard does not raise — an extra tap before the user can type. Auto-focus is the platform expectation for a dedicated search screen.
  - Evidence: `SettingsSearchScreen.kt:70-95` — no `FocusRequester`/`LaunchedEffect{ requester.requestFocus() }`.
  - Touches: `SettingsSearchScreen` (add `FocusRequester` + `LaunchedEffect(Unit)` request; respect `rememberSaveable` so rotation doesn't re-raise unexpectedly).
  - Acceptance: the field is focused and the IME is shown on first composition; rotating does not steal focus from an in-progress edit.
  - Verify: manual on-device.
  - Complexity: S

### Larger Bets

- [ ] P1 — Drift guard test: every `SettingsSearchDestination` is navigable + every entry resId resolves (RA-1)
  - Why: The search catalog is a 33-value enum + ~100 hand-curated entries that mirror the navigation graph and reference real string resIds. Nothing fails the build when a Settings screen is added without a search entry, an entry points at a deleted/renamed pref label, or a `destination` loses its `Routes.*` arm. The only existing test (`SettingsSearchIndexTest.kt`) uses a fake `resolve` map and asserts ranking, not integrity. This is the same registry-drift failure mode the project already hit elsewhere (see the partitioned-prefs golden test).
  - Evidence: `SettingsSearchIndex.kt:24-58` (enum), `:102-205` (entries reference `R.string.*` directly), `SettingsSearchScreen.kt:148-188` (`when(destination)` mapping); `app/src/test/.../search/SettingsSearchIndexTest.kt:79` resolves via a fake map (`"res-$resId"`), so a dangling resId never surfaces in test.
  - Touches: new JVM/Robolectric test asserting (a) entry `id`s are unique, (b) every `SettingsSearchEntry.titleResId`/`summaryResId`/`screenTitleResId` resolves against real `R.string` (non-blank, not the missing-resource fallback), (c) the `SettingsSearchDestination` enum is exhaustively handled by `navigateSearchDestination` (the `when` is already exhaustive — pin it with a `forEach` over `entries()` that constructs each `Routes.*` without throwing).
  - Acceptance: deleting a referenced string res or adding an unmapped destination fails the test; passes today.
  - Verify: `:app:testDebugUnitTest` (or `:app:testDebugUnitTest` + Robolectric for real resId resolution).
  - Complexity: M
- [ ] P2 — No-results fallback action in settings search (RA-2)
  - Why: An empty result set renders only gray "no results for X" text — a dead-end. There's no escape hatch (browse-all / jump to Settings home) and, notably, no link into the Android **system** keyboard settings, which is where a missing pref often actually lives (the search index is app-internal only).
  - Evidence: `SettingsSearchScreen.kt:106-115` — `results.isEmpty()` branch is a single `Text`; no action row.
  - Touches: `SettingsSearchScreen` no-results branch — add a "Browse all settings" button (nav to `Routes.Settings.Home`) and optionally an "Open system keyboard settings" intent (`Settings.ACTION_INPUT_METHOD_SETTINGS`).
  - Acceptance: from a zero-result query the user can reach Settings home in one tap; copy is translation-safe.
  - Verify: `:app:assembleDebug`; manual.
  - Complexity: S
- [ ] P2 — Keyword/synonym coverage audit for high-traffic settings terms (RA-3)
  - Why: Search matches title/summary/screen-title/keywords substrings, but many discoverable prefs have sparse `keywords` (e.g. "haptic" only on input-feedback, "dark"/"light" not on theme.mode, "swipe" present on gestures but "shape writing"/"trace" partial). Users search by capability words, not the exact shipped label.
  - Evidence: `SettingsSearchIndex.kt:103-204` — most `entry(...)` rows pass no `keywords`; `theme.mode` (`:151`) has none, so "dark mode" misses unless the label literally contains it.
  - Touches: `SettingsSearchIndex.entries` keyword strings only (no code path change); extend `SettingsSearchIndexTest` with synonym-hit cases ("dark theme", "haptic", "trace", "punctuation", "privacy").
  - Acceptance: a documented set of capability synonyms each resolve to the right destination; test pins them.
  - Verify: `:app:testDebugUnitTest`.
  - Complexity: M
- [ ] P2 — Accessibility/TalkBack pass over the search screen + result list (RA-4)
  - Why: `ACCESSIBILITY.md` does not yet cover the new search surface. The result `JetPrefListItem`s are `clickable` with no `role`/merged-semantics announcement of "result N of M", the leading icon is correctly `contentDescription = null` (decorative) but the field itself has no labelled state, and the empty/no-results text isn't a live region — a TalkBack user won't hear result-count changes as they type.
  - Evidence: `SettingsSearchScreen.kt:82-143` — no `Modifier.semantics{}`/`liveRegion`/`role` on the field, results, or the count-changing branches; `docs/ACCESSIBILITY.md` "Manual QA checklist" has no search entry.
  - Touches: `SettingsSearchScreen` semantics (field label, results `role = Role.Button`/merged, `liveRegion = Polite` on the result-count container); add a search row to the `docs/ACCESSIBILITY.md` manual-QA checklist.
  - Acceptance: TalkBack announces a labelled search field, reads each result's screen + title, and reports result-count changes; checklist documents the flow.
  - Verify: manual TalkBack on-device; `:app:assembleDebug`.
  - Complexity: M
- [ ] P3 — Surface settings search from Settings home (entry-point discoverability) (RA-8)
  - Why: Search is a registered route but reaching it depends on the home-screen wiring; a top-of-home search affordance (or app-bar icon) is the conventional discovery point and matches how Gboard/SwiftKey expose their settings search.
  - Evidence: `git show --stat 1966c69` added `app/.../settings/HomeScreen.kt` (+10 lines) and `Routes.kt` (+6) for the route; confirm whether the entry is a persistent search bar at the top of home vs. a buried row, and align with platform convention.
  - Touches: `HomeScreen.kt` (promote the search entry to a top affordance if it isn't already); no index changes.
  - Acceptance: search is reachable from the first screen of Settings without scrolling.
  - Verify: manual on-device.
  - Complexity: S
