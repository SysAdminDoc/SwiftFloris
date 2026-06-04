# SwiftFloris Research Report

This report summarizes current research conclusions. The full 2026-05-25 research plan is archived at `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`. Deep-research pass refreshed **2026-06-03** (post-v1.8.204), with a 2026-06-04 freshness note after the v1.8.207 voice-copy slice.

2026-06-04 freshness note: the live dirty tree has already moved EI7 out of active work into v1.8.207 release docs, with `VoiceInputEmptyStateCopyTest.kt` pinning the FUTO explanation and F-Droid install action. This pass did not run Gradle because repo instructions say not to run Android gates from this VM unless asked; the changelog's green Gradle evidence remains unverified here. Current external checks support the copy: FUTO's Voice Input page describes it as working entirely on-device with no stored data, latest F-Droid/standalone version v1.3.6 (28), and the source mirror says FUTO Voice Input remains available for third-party keyboards even though FUTO development has shifted toward FUTO Keyboard. Android-platform sources also moved: Android 17 API 37 setup docs are current, but SwiftFloris already keeps API 37 as a future behavior-gate decision. Maven metadata shows low-priority freshness drift rather than a security issue: Kotlin 2.4.0, Compose BOM 2026.05.01, AndroidX Core 1.19.0, and Roborazzi 1.63.0 are newer than the pinned versions, while Room 2.8.4, SQLCipher 4.16.0, Tink 1.21.0, and Robolectric 4.16.1 still match current metadata. A P3 dependency-refresh row was added to `ROADMAP.md`.

## Executive Summary

SwiftFloris is a mature, heavily-audited privacy-first Android IME (FlorisBoard fork, `dev.patrickgold.florisboard`, `:app` permission-clean with no `INTERNET`). At v1.8.204 the feature surface is broad (autocorrect/prediction, glide typing, clipboard, addons, voice handoff, sync, MCP bridge, hardware-keyboard import) and the dependency stack is current (Compose BOM 2026.05.00, Kotlin 2.3.21, AGP 9.2.1, targetSdk 36). Three deep engineering audits (2026-05-28/29 and 2026-06-02) plus the existing roadmap already cover correctness, crypto, resource, and device-gated visual work, so the **net-new** opportunity space is narrow and concentrated on the newest code: the **settings search** feature shipped this release (v1.8.204, commit `1966c69`). Search is a hand-maintained static catalog (a 33-value `SettingsSearchDestination` enum plus ~100 entry rows) that mirrors the navigation graph and references string resIds directly, with **no drift guard** and several UX/accessibility gaps. [Verified]

Top opportunities (one line each):

1. **Drift guard for the search catalog** — pin destination→route navigability and resId resolution so a renamed/deleted pref or unmapped screen fails the build (RA-1, P1). [Verified]
2. **No-results dead-end** — add a browse-all / system-keyboard-settings escape hatch to the empty-result state (RA-2, P2). [Verified]
3. **Search UX polish** — clear button, `ImeAction.Search`, and auto-focus on the search field (RA-5/6/7, P3). [Verified]
4. **Keyword/synonym coverage** — most entries ship no `keywords`; capability terms like "dark mode"/"haptic" miss (RA-3, P2). [Verified]
5. **TalkBack pass over search** — no semantics/live-region on results or count; not in `ACCESSIBILITY.md` QA checklist (RA-4, P2). [Verified]
6. **Diacritic-insensitive matching** — `searchNormalize()` does not fold accents, hurting German/Turkish/French queries (RA-5, P3). [Verified]
7. **Search entry-point discoverability** from Settings home (RA-8, P3). [Likely]

No Critical or Major reliability/security defects were found that are not already on the roadmap or in the deferred audit lists. The remaining heavy work (glide model training, Vosk addon, F-Droid submission, device-only visual verification) stays maintainer-gated as the existing roadmap records.

## Evidence Reviewed

- **Key files/dirs:** `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/search/` (`SettingsSearchIndex.kt`, `SettingsSearchScreen.kt`), `app/src/test/.../settings/search/SettingsSearchIndexTest.kt`, `gradle/libs.versions.toml`, `gradle.properties`, `app/src/main/AndroidManifest.xml`, `docs/ACCESSIBILITY.md`, `.github/workflows/*`, the three `docs/AUDIT_2026-*.md` reports (read-only).
- **Git range:** `git log -30 --oneline` (shallow history, 50 commits total; HEAD `1966c69 feat: add settings search`). `git show --stat 1966c69` confirmed the search feature's file footprint.
- **External sources / standards:** Android predictive-back (`enableOnBackInvokedCallback="true"` already set, manifest:67); Material 3 search patterns; WCAG 2.2 AA (touch target 24×24 min / `TouchTargetWcagTest` already present); Android IME settings deep-link (`Settings.ACTION_INPUT_METHOD_SETTINGS`). No competitor app could be byte-inspected (closed-source), so competitive notes are capability-level. [Assumption]
- **Unverifiable here:** No Android SDK on this host (the audit docs note the same), so gradle gates, on-device focus/IME-raise behavior, and TalkBack output could not be executed — those acceptance criteria are marked "manual on-device" in the roadmap. [Needs validation]

## Current Product Map

Privacy-first multilingual IME. `:app` is Apache-2.0-ceiling, no network permission, no telemetry, no account. Networked/native capability (voice recognizer runtime, FunctionGemma/MCP, glide ML model) is pushed to optional **signed addon APKs** rather than linked in. Persistence: JetPref datastore (partitioned per feature area as of v1.8.202), Room + SQLCipher for clipboard/dictionary, Tink for string-pref crypto. Release stream v1.8.x with one `## vX.Y.Z` `CHANGELOG.md` section and a fastlane changelog per versionCode. CI: no-network manifest gate, Roborazzi visual gate, OSV/dependency scan, reproducible-build tooling, string-no-translations validation, emulator smoke.

## Feature Inventory (delta focus)

- **Settings search (v1.8.204, NEW):** accessed via Settings → Search route (`Routes.Settings` arm added in `1966c69`); implemented as a static catalog in `SettingsSearchIndex` (entries with title/summary/screen-title/keyword haystacks, weighted `score()` ranking) rendered by `SettingsSearchScreen` (TextField + `LazyColumn` of `JetPrefListItem`). Highlight handoff via `SettingsSearchHighlightStore`. Maturity: shipped but thin — 1 test class with a fake string resolver, no drift/integrity guard, no diacritic folding, no clear/auto-focus/IME-action, dead-end no-results, not in the a11y QA checklist. [Verified]
- Established surfaces (autocorrect/SymSpell, glide classifier, clipboard, addons, voice handoff, sync, MCP, hardware-keyboard import) are covered by `COMPLETED.md` and the audits; no net-new gap surfaced beyond what the roadmap already tracks.

## Competitive Landscape

- **Gboard (Google), SwiftKey (Microsoft):** both expose an in-settings search with auto-focused field, clear button, synonym matching, and a no-results state that still offers related sections. Lesson: settings search is expected to behave like the platform search box (auto-focus + clear + IME Search). What to avoid: their cloud sync / telemetry — explicitly a non-goal here.
- **AnySoftKeyboard, OpenBoard, HeliBoard (FLOSS analogues):** simpler settings, typically no dedicated search; SwiftFloris is already ahead by shipping one. Lesson: the differentiator is *correctness/maintainability* of the catalog, hence the drift-guard priority. What to avoid: their sparse accessibility coverage.
- **Android platform Settings search:** indexes via a `SearchIndexablesProvider` content provider so entries can't silently drift from the screens they point to. SwiftFloris's hand-maintained enum is the lighter-weight choice but needs the test-level guard (RA-1) to get the same integrity property. [Likely]

## Quality & Friction Findings

- **[Major] Search catalog drift** → RA-1. Enum + ~100 entries mirror the nav graph with resIds inlined; only test uses a fake resolver, so a deleted/renamed string or unmapped destination passes CI. (`SettingsSearchIndex.kt:24-205`, `SettingsSearchScreen.kt:148-188`, test `:79`.)
- **[Minor] No-results dead-end** → RA-2. (`SettingsSearchScreen.kt:106-115`.)
- **[Minor] Missing clear button / IME Search action** → RA-6. (`SettingsSearchScreen.kt:77-95`.)
- **[Minor] No auto-focus on open** → RA-7. (`SettingsSearchScreen.kt:70-95`.)
- **[Minor] Sparse keyword coverage** → RA-3. (`SettingsSearchIndex.kt:103-204`.)
- **[Minor] Search a11y gap** → RA-4. (`SettingsSearchScreen.kt:82-143`; `docs/ACCESSIBILITY.md` checklist.)
- **[Cosmetic] No diacritic folding** → RA-5. (`SettingsSearchIndex.kt:271-279`.)
- **[Cosmetic] Entry-point discoverability** → RA-8. (`HomeScreen.kt` per `1966c69`.)

## Architecture & Technical Findings

- **Module boundaries:** clean `:app` + `:lib:*` split; addon capability isolation is a deliberate, well-documented pattern. No new boundary issue surfaced.
- **Dependency health:** the security-sensitive pins checked here are still current for SQLCipher 4.16.0 and Tink 1.21.0, and Room/Robolectric also match metadata. Freshness drift now exists for Kotlin 2.4.0, Compose BOM 2026.05.01, AndroidX Core 1.19.0, and Roborazzi 1.63.0; AGP 9.2.1 appears to be the stable baseline while Google Maven's newest AGP metadata is 9.3 alpha. This is a P3 maintenance batch, not a security item. [Verified via Maven metadata]
- **Overgrown files:** `IndicTransliterator.kt` (~86 KB), `TextKeyboardLayout.kt` (~76 KB), `LatinLanguageProvider.kt` (~60 KB), `KeyboardManager.kt` (~60 KB) are large but the SHIFT state machine was already extracted (F27 shipped) and the audits already track `LatinLanguageProvider` heap risk (A1). Left as-is — no speculative refactor proposed.
- **Testability:** 217 JVM test files, 5 androidTest. The search subsystem is the thinnest-tested new code; RA-1/RA-3 close that.
- **Release automation:** mature (reproducible build, SBOM/provenance and signed-tags already roadmapped as maintainer-gated). No new item.

## Security / Privacy / Data Safety

No net-new finding. The settings-search additions are display/navigation only — no new permission, no data egress, no persistence change. The empty/no-results system-settings deep-link (RA-2) uses a standard `Settings.ACTION_INPUT_METHOD_SETTINGS` intent and does not weaken the no-network posture. The deferred audit lists (`docs/AUDIT_2026-06-02.md`) remain the authority for crypto/parsing/lifecycle hardening; this pass does not duplicate them.

## UX & Accessibility

The keyboard surface already has a strong a11y baseline (`ACCESSIBILITY.md`, `TouchTargetWcagTest`, RTL mirroring, candidate-row custom actions). The gap is that the **new** search screen wasn't brought under that umbrella: no field label/semantics, no live-region result-count announcement, and no entry in the manual-QA checklist (RA-4). UX polish (auto-focus, clear, IME Search, diacritic folding, no-results escape) brings search to parity with platform expectations without scope creep.

## Explicit Non-Goals (rejected + why)

- **Dynamic/reflective settings indexing** (auto-discover entries from the route graph) — rejected: heavier than the problem; a test-level drift guard (RA-1) gets the integrity benefit without runtime reflection cost on an IME process.
- **Fuzzy/typo-tolerant search (edit-distance)** — rejected for now: substring + synonym keywords (RA-3) covers the realistic miss cases; edit-distance adds index cost for marginal value on a ~100-entry catalog.
- **Cloud-synced search history / suggestions** — rejected: violates the no-network / no-telemetry posture.
- **Refactoring the 60-86 KB files** — rejected: no task requires it; the audits already track the only load-bearing one (`LatinLanguageProvider` heap).

## Open Questions (genuine blockers only)

1. Is the Settings-home search affordance already a top-of-screen bar or a buried row? (decides whether RA-8 is a no-op) — needs an on-device look at `HomeScreen.kt` rendering. [Needs validation]
2. Should the no-results state deep-link to Android system IME settings, or stay app-internal only? (product call for RA-2; affects copy and the no-network optics even though the intent itself sends no data). [Needs validation]

## Archived Evidence

- `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`
- `docs/AUDIT_2026-05-28.md`, `docs/AUDIT_2026-05-29.md`, `docs/AUDIT_2026-06-02.md` (deep engineering audits; deferred-item authority)
- `.ai/research/2026-05-17/`, `.ai/research/2026-05-25/`
