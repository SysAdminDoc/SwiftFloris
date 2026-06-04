# SwiftFloris Roadmap

> Single source of truth for all planned work. Items above the --- are existing plans; items below are research conducted 2026-06-03.

**Current release:** v1.8.220 (versionCode 2020). **Baseline green:** `:app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

Hard rules still apply (see `AGENTS.md`): no `INTERNET` permission in `:app`; Apache-2.0 ceiling on `:app`; no closed-source blobs; one logical change per commit; every shipped release bumps `gradle.properties` version, writes a `CHANGELOG.md` section, and adds a `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (draft <=480 chars for headroom).

Item IDs trace to their origin research: `F#`/`EI#` from the archived 2026-05-25 research feature plan; `R#`/`O#` from the 2026-05-25 second-pass findings; `WS#` from the archived improvement-plan workstreams; `N#`/`Next-#`/`L#` from the archived roadmap tiers. Shipped items and reframed/rejected items live in `COMPLETED.md`; full release detail in `CHANGELOG.md`. Historical strategy (tiered NOW/NEXT/LATER, sourced appendix) is preserved at `docs/archive/ROADMAP_v5.67_2026-05-18.md`.

> Last researched: Cycle 2 - 2026-06-04.

## ▶ Implementer Instructions (for the build machine)

This roadmap is fed continuously by an automated research machine. On every
pass, the implementing machine should:

1. `git pull --rebase` to get the latest researched items before starting.
2. Work the open 🤖 items top-down by priority (P0 -> P3). Build them properly:
   multi-file structure, real error handling, no runtime auto-install hacks,
   version strings synced, docs/CHANGELOG updated in the same commit.
3. In addition to building items, run a full UX audit each pass. Walk every
   screen / page / dialog / form / table / empty-loading-error-disabled state
   across light/dark/high-contrast themes. Check onboarding, navigation clarity,
   spacing/contrast/alignment, clipping/overflow, hierarchy, microcopy,
   destructive-action guards, keyboard + screen-reader accessibility, and trust
   signals. Fix what you find, or file it back as a new 🤖 roadmap item if it
   is larger than a pass.
4. Check off ✅ each item you complete (leave it in place with the checkmark),
   commit per logical change with a "why" message, and push.
5. Never edit this Implementer Instructions block or the 🔬 Researcher Queue
   headings. Never force-push.

Keep the `:app` invariant strict: no internet/network permissions, Apache-2.0
ceiling, no closed-source blobs, and network or incompatible features only in
isolated addon APKs. Shipped work belongs in `CHANGELOG.md`; completed roadmap
items belong in `COMPLETED.md`.

## Existing Planned Work

### Keyboard surface & visual polish (device-gated)

- [ ] P1 — Keyboard surface polish + manual-override verification (WS11)
  - Why: Candidate-row, smartbar, and software-key states plus the full layout matrix need real-field verification; cannot fully close without a device.
  - Touches: candidate-row selection/pressed/disabled/correction states; smartbar ordering + overflow + long-label resilience; software-key pressed/held/disabled/gesture states; one-handed/floating/split/compact/landscape/tablet layouts.
  - Acceptance: each state and layout verified in real input fields on a device.
  - Source: docs/archive/TODO_2026-06-03.md B / improvement-plan WS11.
- [ ] P1 — Glide-trail theme baselines + low-end perf evidence (F9)
  - Why: Glide-trail themes lack Roborazzi baselines and low-end (<=4 GB) performance evidence.
  - Touches: Roborazzi baselines (device/emulator); trace `swiftfloris.glide.trailDrawMs` on Pixel 4a / Galaxy A12-class.
  - Acceptance: baselines recorded; trail-draw timing captured on low-end hardware.
  - Source: docs/archive/TODO_2026-06-03.md B / research feature plan F9.
- [ ] P2 — F40 Roborazzi capture phase (F40 capture)
  - Why: Screen-level Roborazzi test classes ship baseline-pending (v1.8.201); the baseline PNGs still need on-device capture.
  - Touches: `:app:recordRoborazziDebug` for the A1 test classes, then remove the class-level `@Ignore` from the pending F40 screenshot classes.
  - Acceptance: baseline PNGs captured; `@Ignore` removed; gate green.
  - Source: docs/archive/TODO_2026-06-03.md A1/B / research feature plan F40.
- [ ] P2 — Glide-trail reduced-animation + tooltip verification (EI4 residual)
  - Why: Confirm Rainbow/Aurora/Neon glide trails honour `ANIMATOR_DURATION_SCALE == 0f` on-device; the doc disclosure already shipped (v1.8.182).
  - Touches: GesturesScreen "i" tooltip + on-device animation-scale check.
  - Acceptance: trails respect zero animation scale; tooltip present.
  - Source: docs/archive/TODO_2026-06-03.md B / second-pass EI4.

### Data safety, backup/restore & import (device-gated portions)

- [ ] P1 — Backup/restore + import path-safety device confirmation (WS13 device portions)
  - Why: Unit tests for these paths are Tier A and done; the on-device confirmation is still required.
  - Touches: backup/restore overwrite-vs-merge; clipboard media missing-file/path-safety; extension-import path-traversal.
  - Acceptance: overwrite/merge, missing-media, and traversal behaviors confirmed on-device.
  - Source: docs/archive/TODO_2026-06-03.md B / improvement-plan WS13.

### CI, build & release hardening

- [ ] P3 — API 37 / Kotlin 2.4 dependency compatibility follow-up
  - Why: The v1.8.216 freshness pass verified Kotlin `2.4.0` and AndroidX Core `1.19.0` as current, but Kotlin has no matching KSP `2.4.0` plugin artifact yet and AndroidX Core `1.19.0` requires `compileSdk 37`.
  - Touches: `gradle/libs.versions.toml`, `gradle/tools.versions.toml`, API 37 behavior-gate docs.
  - Acceptance: bump Kotlin only after a compatible KSP plugin is published; bump AndroidX Core only with the compileSdk 37 behavior-gate plan and full Gradle/Roborazzi verification.
  - Source: v1.8.216 dependency freshness pass.

### Docs & hygiene

- [ ] P2 — Localization content-quality pass (WS12)
  - Why: Turkish repeated-word lint, vague/abrupt English source labels, and inconsistent failure/destructive copy need cleanup.
  - Touches: native-safe Turkish repeated-word review; tighten English source labels; standardize backup/restore/import/export failure + destructive-confirmation copy; document translation-safe cleanup rules.
  - Acceptance: lint warnings reviewed; copy standardized; rules documented.
  - Source: docs/archive/TODO_2026-06-03.md A5 / improvement-plan WS12.
- [ ] P2 — Visual-QA + manual-QA + release-evidence checklists (WS10 / WS15)
  - Why: No standing checklists for the portrait/landscape/compact/floating/dark/high-font-scale matrix, manual QA, or release evidence.
  - Touches: docs for visual-QA matrix, manual-QA flow, and release-evidence capture.
  - Acceptance: three checklists exist and are referenced from the verification docs.
  - Source: docs/archive/TODO_2026-06-03.md A5 / improvement-plan WS10/WS15.
- [ ] P3 — Fastlane changelog drafting guide (R5)
  - Why: No documented guidance on drafting the <=480-char fastlane changelog.
  - Touches: add the guide to `docs/LOCAL_VERIFICATION.md` / `docs/REPO_HYGIENE.md`.
  - Acceptance: guide present with the character-budget rule.
  - Source: docs/archive/TODO_2026-06-03.md A5 / second-pass R5.
- [ ] P3 — Document module build-cache survival (O1)
  - Why: `lib/<module>/build/` cache survives `git rm --cached`; this surprises contributors.
  - Touches: note in `docs/REPO_HYGIENE.md`.
  - Acceptance: behavior documented.
  - Source: docs/archive/TODO_2026-06-03.md A5 / second-pass O1.

### External-action-blocked / sibling-repo / XL (maintainer decision required)

These are genuine blockers — each needs an account, key, sibling repo, ML infra, or a product decision the code cannot make.

- [ ] P0 — Crowdin sync of v1.8.179 + v1.8.186 string drops (R1)
  - Why: 44 stale translated entries across 22 locales; Crowdin web console is source of truth (lint `UnusedResources` until done).
  - Touches: server-side Crowdin sync/pull.
  - Acceptance: translations synced; stale-entry lint clears.
  - Source: docs/archive/TODO_2026-06-03.md C / second-pass R1.
- [ ] P1 — FlorisBoard `0.6.0-alpha02` cherry-picks (F22)
  - Why: Upstream CLDR 48, Emoji 17, number-field fix, and floating-window foundation are worth picking up; conflict resolution needs iterative on-device builds and risks regressing shipped features.
  - Touches: cherry-pick + conflict resolution across input/emoji/layout.
  - Acceptance: picks merged without regressing shipped features; on-device verified.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F22.
- [ ] P1 — Apache-2.0 glide model trained on the MIT FUTO swipe dataset (F21)
  - Why: A licensed in-tree glide model needs off-device ML training infra (XL, out-of-tree).
  - Touches: external training pipeline + model integration.
  - Acceptance: Apache-2.0-clean model trained and integrated.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F21.
- [ ] P2 — Bundled Vosk small-en-us recognizer addon (F8)
  - Why: Needs a sibling addon repo + JNI; `RECORD_AUDIO` only in the addon, never `:app`.
  - Touches: sibling addon repo, JNI binding.
  - Acceptance: recognizer ships as a signed addon; `:app` stays permission-clean.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F8.
- [ ] P2 — CycloneDX SBOM + SLSA provenance on release (F10)
  - Why: Needs GitHub Attestations onboarding + release-tag dispatch.
  - Touches: release workflow attestation step.
  - Acceptance: SBOM + provenance attached to releases.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F10.
- [ ] P2 — GPG-signed release tags (F11)
  - Why: Needs a maintainer GPG key.
  - Touches: release-tag signing.
  - Acceptance: tags are GPG-signed and verifiable.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F11.
- [ ] P2 — F-Droid `fdroiddata` submission (F12)
  - Why: `dev.patrickgold.florisboard(.beta)` package-id collides with upstream; needs a rename/coexistence decision plus a multi-month review queue.
  - Touches: fdroiddata metadata + package-id decision.
  - Acceptance: submission accepted into the F-Droid queue.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F12.
- [ ] P2 — FunctionGemma 270M MCP-bridge addon (F30)
  - Why: Needs a sibling addon repo.
  - Touches: sibling addon repo + MCP bridge.
  - Acceptance: addon bridges FunctionGemma over MCP without linking into `:app`.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F30.
- [ ] P3 — Cross-platform desktop dictionary-export CLI (F13)
  - Why: Needs a sibling repo.
  - Touches: standalone CLI project.
  - Acceptance: CLI exports the dictionary format cross-platform.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F13.

#### Open questions blocking the external-action items (maintainer decisions)

1. F-Droid package-id: coexist with upstream FlorisBoard `.beta` or rename? (blocks F12)
2. Vosk 40 MB addon in 2026, or voice stays FUTO-handoff-only? (affects F8 + EI7 copy)
3. Maintainer GPG key (Yubikey-backed?) for signed tags? (affects F11)
4. F-Droid submission timing — during the migration spike or a quiet week? (affects F12)

---

## Research-Driven Additions

### Researcher Queue (Cycle 2 - 2026-06-04)

- [x] 🔬 `startup-diagnostics-and-docs-refresh-2026-06-04` - re-read the
  current v1.8.218 repo state, the committed audit docs, the last 15 shipped
  releases, and current upstream/standards sources. Existing settings-search,
  dependency, upstream FlorisBoard, CLDR/Emoji, F-Droid, device-gated, and
  maintainer-gated rows remain correctly represented below; this cycle adds
  only the net-new startup diagnostics and source-of-truth documentation gaps.

#### Reliability & diagnostics

- [x] 🤖 P1 — Persist or surface staged startup exceptions before Settings opens (R2-1)
  - Shipped v1.8.218: `CrashUtility.consumeStagedException(...)` persists
    staged init exceptions without invoking the process-killing uncaught
    handler, and `FlorisAppActivity` opens `CrashDialogActivity` before the
    splash keep condition can hang on `preferenceStoreLoaded`.
  - Why: A synchronous `FlorisApplication.onCreate()` failure is staged and the
    application returns, but no production call drains the staged exception into
    the existing crash-file / notification path. On a privacy keyboard, a silent
    startup failure is a trust problem even if the failure is rare.
  - Evidence: `FlorisApplication.kt:100-156` installs Flog/CrashUtility, then
    catches `Exception` with `CrashUtility.stageException(e); return`;
    `CrashUtility.kt:159-170` stores and drains staged exceptions; `rg
    "handleStagedButUnhandledExceptions" app/src/main/kotlin app/src/test/kotlin`
    finds no production/test call site; `FlorisAppActivity.kt:100-170` opens the
    Settings activity without reading `CrashUtility`; `docs/AUDIT_2026-05-28.md:16-17`
    independently verified the same path.
  - Touches: `FlorisApplication.kt`, `CrashUtility.kt`, `FlorisAppActivity.kt`,
    and a focused JVM/Robolectric test around the chosen staging/drain policy.
  - Acceptance: an injected synchronous app-init failure creates a persisted
    stacktrace or visible crash/recovery surface; the splash screen does not
    hang silently; the implementation documents whether it intentionally calls
    the existing uncaught handler (process-killing) or writes a recoverable
    staged-init stacktrace without killing the Settings activity.
  - Verify: `:app:testDebugUnitTest`; manual debug build with a temporary
    injected pre-`init()` failure before removing the injection.
  - Complexity: M
- [x] 🤖 P2 — Replace remaining restore/crash diagnostic `printStackTrace()` paths with project logging plus user-safe fallback copy (R2-2)
  - Shipped v1.8.219: restore archive-load, per-section restore, restore
    launcher, and top-level restore failures now route diagnostics through
    `flogError`, restore cards/toasts use `BackupRestorePolicy.restoreErrorMessage(...)`
    to avoid null/blank user copy, and crash stacktrace write failures use the
    `CRASH_UTILITY` logging topic instead of raw `printStackTrace()`.
  - Why: The restore flow and crash-file write helper still fall back to raw
    `printStackTrace()` on exceptional diagnostic paths, while adjacent code
    already uses `flogError`. The fix should improve consistency and user-facing
    failure text without overstating release-build file-log coverage, because
    `Flog` is debug-gated and `fileLog()` is still a stub.
  - Evidence: `RestoreScreen.kt` failure paths are called out in
    `docs/AUDIT_2026-05-28.md:19-22`; sibling `BackupScreen.kt:205` and
    `BackupScreen.kt:338` use `flogError`; `CrashUtility.kt:366-370` still
    catches crash-file write failures with `e.printStackTrace()`;
    `Flog.kt:326` tracks the file-logging TODO.
  - Touches: `RestoreScreen.kt`, `CrashUtility.kt`, possibly `Flog.kt` only if
    a minimal release-safe sink is added; add focused tests for non-null restore
    failure messages where practical.
  - Acceptance: restore failure cards/toasts use stable fallback text when
    `localizedMessage` is null; diagnostic exceptions route through the project
    logging idiom; docs/changelog do not claim persisted release logs unless a
    real persisted sink is implemented.
  - Verify: `:app:testDebugUnitTest`; manual restore-failure smoke with a bad
    archive on the Android SDK host.
  - Complexity: S-M

#### Docs & source-of-truth

- [x] 🤖 P2 — Refresh root onboarding docs to the v1.8.220 source of truth (R2-3)
  - Shipped v1.8.220: root onboarding docs now route open work to
    `ROADMAP.md`, shipped state to `COMPLETED.md`, release notes to
    `CHANGELOG.md` plus fastlane metadata, and archived parity/improvement
    plans are clearly historical context.
  - Why: The live roadmap is current, but the fast onboarding docs still mixed
    older stack facts, archived-plan routes, and retired release-note
    instructions. Future build passes use these docs first, so stale routing
    increases the chance of wrong release or planning edits.
  - Evidence: the pre-fix stale scan found outdated stack/version facts and
    root release-note/planning routes in `PROJECT_CONTEXT.md`,
    `ARCHITECTURE.md`, `CONTRIBUTING.md`, `README.md`, `docs/REPO_HYGIENE.md`,
    and `AGENTS.md`.
  - Touches: `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`,
    `README.md`, `docs/REPO_HYGIENE.md`, `AGENTS.md`, and release ledgers.
  - Acceptance: root docs agree that `ROADMAP.md` is the open-work source,
    `COMPLETED.md` is shipped-state summary, `CHANGELOG.md` is the only release
    note stream, and current stack/release facts match v1.8.220.
  - Verify: stale reference scan; `:app:verifyNoInternetPermission`;
    `:app:testDebugUnitTest`; `:app:lintDebug`; `:app:assembleDebug`;
    fastlane metadata check; repo hygiene check.
  - Complexity: M

### Researcher Queue (Cycle 1 - 2026-06-04)

- [x] 🔬 `voice-copy-dependency-refresh-2026-06-04` - rechecked the v1.8.207
  voice-copy slice and public dependency metadata without running Gradle on this
  VM. FUTO Voice Input remains the correct privacy-preserving handoff for voice
  copy, Android 17/API 37 remains future behavior-gate work, and dependency
  drift is low-risk maintenance rather than a security item. The new P3
  dependency freshness row is the build-lane handoff.

*Research conducted 2026-06-03. Items below are new — not duplicates of Existing Planned Work.*

This pass focused on the v1.8.204 **settings search** drop (the newest feature, shipped this release) and a few cross-cutting gaps the three deep audits (`docs/AUDIT_2026-05-28/29` + `2026-06-02`) and the existing roadmap do not already cover. The search subsystem is a hand-maintained static catalog that mirrors the navigation graph with no drift guard — the highest-leverage net-new work.

### Quick Wins

All current quick wins shipped through v1.8.215. Remaining settings-search work is listed under Larger Bets.

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
- [x] P3 — Surface settings search from Settings home (entry-point discoverability) (RA-8)
  - Confirmed 2026-06-04: Settings Home already exposes the search route as a
    top app-bar action with `settings__search__title` content description, so
    search is reachable from the first Settings screen without scrolling.
  - Why: Search is a registered route but reaching it depends on the home-screen wiring; a top-of-home search affordance (or app-bar icon) is the conventional discovery point and matches how Gboard/SwiftKey expose their settings search.
  - Evidence: `HomeScreen.kt:68-75` defines `actions { FlorisIconButton(...) }`,
    `onClick = { navController.navigate(Routes.Settings.Search) }`, icon
    `Icons.Default.Search`, and content description
    `R.string.settings__search__title`.
  - Touches: none; source already satisfies the row.
  - Acceptance: search is reachable from the first screen of Settings without scrolling.
  - Verify: source inspection; optional manual on-device smoke.
  - Complexity: S
