# SwiftFloris Research Report

This report summarizes current research conclusions. The full 2026-05-25 research plan is archived at `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`. Deep-research pass refreshed **2026-06-03** (post-v1.8.204), with a 2026-06-04 freshness note after the v1.8.207 voice-copy slice.

2026-06-04 freshness note: the live dirty tree has already moved EI7 out of active work into v1.8.207 release docs, with `VoiceInputEmptyStateCopyTest.kt` pinning the FUTO explanation and F-Droid install action. This pass did not run Gradle because repo instructions say not to run Android gates from this VM unless asked; the changelog's green Gradle evidence remains unverified here. Current external checks support the copy: FUTO's Voice Input page describes it as working entirely on-device with no stored data, latest F-Droid/standalone version v1.3.6 (28), and the source mirror says FUTO Voice Input remains available for third-party keyboards even though FUTO development has shifted toward FUTO Keyboard. Android-platform sources also moved: Android 17 API 37 setup docs are current, but SwiftFloris already keeps API 37 as a future behavior-gate decision. Maven metadata shows low-priority freshness drift rather than a security issue: Kotlin 2.4.0, Compose BOM 2026.05.01, AndroidX Core 1.19.0, and Roborazzi 1.63.0 are newer than the pinned versions, while Room 2.8.4, SQLCipher 4.16.0, Tink 1.21.0, and Robolectric 4.16.1 still match current metadata. A P3 dependency-refresh row was added to `ROADMAP.md`.

2026-06-04 delivery note: v1.8.215 closed RA-5 / RA-6 / RA-7. Settings search now folds combining diacritics during normalization, opens the field focused on first entry, exposes a clear action while text is present, and advertises the Search IME action. v1.8.221 closed RA-1 with a real-resource and typed-route drift guard. The remaining settings-search queue starts at RA-2 / RA-3 / RA-4 plus the new RA-9 highlight-lifecycle follow-up.

2026-06-04 dependency note: v1.8.216 closed the compatible portion of the P3 freshness row by bumping Compose BOM `2026.05.01`, KSP `2.3.9`, and Roborazzi `1.63.0`. Kotlin `2.4.0` remains deferred because the KSP Gradle plugin metadata currently tops out at `2.3.9`; AndroidX Core `1.19.0` remains deferred because `:app:checkDebugAarMetadata` reports a `compileSdk 37` requirement.

2026-06-04 Cycle 2 note: local reconciliation found three gaps that were not
already represented in `ROADMAP.md`: a staged startup-exception path that was
never drained (`R2-1`, closed v1.8.218), stale root onboarding/release docs
after the release run (`R2-3`, closed v1.8.220), and a smaller
diagnostic-consistency row (`R2-2`) for remaining `printStackTrace()` paths
without claiming release-build file logging. External checks did not create new upstream rows: FlorisBoard has
`v0.6.0-alpha02` in tags while the latest GitHub release page is `v0.5.2`,
HeliBoard `v3.9` and AnySoftKeyboard `1.13-r1` reinforce the existing gesture,
backup/restore, 16 KB, edge-to-edge, and emoji rows, and CLDR 48.2 / Unicode
Emoji 17.0 / F-Droid reproducible-build guidance remain covered by existing
F22/F10/F12/API 37 work.

2026-06-04 lint-baseline note: v1.8.217 closed EI10. No `app/lint-baseline.xml` exists in the checkout, `docs/LOCAL_VERIFICATION.md` now documents the baseline-free lint contract, and `bash scripts/run-lint-debug-with-baseline-check.sh` passed with the verified JDK 21 path.

2026-06-04 startup-diagnostics note: v1.8.218 closed R2-1 with a recoverable staged-init path. `CrashUtility.consumeStagedException(...)` persists the staged stacktrace without invoking the process-killing uncaught handler, and `FlorisAppActivity` redirects to `CrashDialogActivity` before the splash screen can wait on unloaded preferences.

2026-06-04 restore-diagnostics note: v1.8.219 closed R2-2. Restore failure diagnostics now use `flogError`, restore cards/toasts resolve null or blank throwable messages to stable fallback copy, and `CrashUtility.writeToFile(...)` logs stacktrace write failures through `LogTopic.CRASH_UTILITY` without claiming release-build persisted file logging.

2026-06-04 docs-source note: v1.8.220 closed R2-3 and RA-8. Root onboarding now routes open work to `ROADMAP.md`, shipped state to `COMPLETED.md`, release notes to `CHANGELOG.md` plus fastlane metadata, and archived parity/improvement plans to historical context. Settings Home already exposes the search route through the top app-bar action, so entry-point discoverability required documentation only.

2026-06-04 search-highlight note: local source inspection added RA-9. Search result highlighting uses a process-wide `SettingsSearchHighlightStore` that is marked from `SettingsSearchScreen` and rendered by `FlorisScreen`, but production code never consumes or clears it after the destination screen displays. The implementation target is a one-shot consume/dismiss contract, not a new search feature.

## Executive Summary

SwiftFloris is a mature, heavily-audited privacy-first Android IME (FlorisBoard fork, `dev.patrickgold.florisboard`, `:app` permission-clean with no `INTERNET`). At v1.8.221 the feature surface is broad (autocorrect/prediction, glide typing, clipboard, addons, voice handoff, sync, MCP bridge, hardware-keyboard import) and the compatible dependency stack is current for the applied pins (Compose BOM 2026.05.01, Kotlin 2.3.21, AGP 9.2.1, targetSdk 36). Three deep engineering audits (2026-05-28/29 and 2026-06-02) plus the existing roadmap already cover correctness, crypto, resource, and device-gated visual work, so the **net-new** opportunity space is narrow and concentrated on the newest code: the **settings search** feature shipped in v1.8.204 (commit `1966c69`). Search is a hand-maintained static catalog (a 33-value `SettingsSearchDestination` enum plus ~100 entry rows) that mirrors the navigation graph and references string resIds directly; v1.8.221 adds the drift guard, while several UX/accessibility gaps remain. [Verified]

Top opportunities (one line each):

1. **Drift guard for the search catalog** — destination-route mapping, unique IDs, and real string resources are now pinned by `SettingsSearchIndexIntegrityTest` (RA-1). [Closed]
2. **No-results dead-end** — add a browse-all / system-keyboard-settings escape hatch to the empty-result state (RA-2, P2). [Verified]
3. **Search UX polish** — clear button, `ImeAction.Search`, auto-focus, and diacritic folding shipped in v1.8.215 (RA-5/6/7). [Closed]
4. **Keyword/synonym coverage** — most entries ship no `keywords`; capability terms like "dark mode"/"haptic" miss (RA-3, P2). [Verified]
5. **TalkBack pass over search** — no semantics/live-region on results or count; not in `ACCESSIBILITY.md` QA checklist (RA-4, P2). [Verified]
6. **Search entry-point discoverability** from Settings home was already satisfied by the app-bar search action (RA-8). [Closed]
7. **Restore/crash diagnostic consistency** — remaining `printStackTrace()` paths were replaced with project logging plus user-safe fallback copy in v1.8.219 (R2-2). [Closed]
8. **Root docs source-of-truth refresh** — onboarding docs now route open work, shipped state, release notes, and archived planning context consistently (R2-3). [Closed]
9. **Search highlight lifecycle** — the global search highlight target is never consumed by production code, so stale result cards can reappear after the original search flow (RA-9, P2). [Verified]

No Critical or Major reliability/security defects were found that are not already on the roadmap or in the deferred audit lists. The remaining heavy work (glide model training, Vosk addon, F-Droid submission, device-only visual verification) stays maintainer-gated as the existing roadmap records.

## Evidence Reviewed

- **Key files/dirs:** `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/search/` (`SettingsSearchIndex.kt`, `SettingsSearchScreen.kt`), `app/src/test/.../settings/search/SettingsSearchIndexTest.kt`, `FlorisApplication.kt`, `FlorisAppActivity.kt`, `lib/crashutility/CrashUtility.kt`, `RestoreScreen.kt`, `BackupScreen.kt`, `Flog.kt`, `gradle/libs.versions.toml`, `gradle.properties`, `app/src/main/AndroidManifest.xml`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`, `README.md`, `docs/ACCESSIBILITY.md`, `.github/workflows/*`, and the three `docs/AUDIT_2026-*.md` reports (read-only).
- **Git range:** `git log --oneline -n 40`; `git show --stat --oneline HEAD~15..HEAD` confirmed v1.8.204 -> v1.8.216 shipped settings search, hardware-layout import, per-app language memory, dictionary import preview, per-app accent discovery, release/reproducible-build/benchmark gates, and the compatible dependency freshness batch.
- **External sources / standards:** Android Compose semantics and live-region guidance (`https://developer.android.com/develop/ui/compose/accessibility/semantics`); Android `Settings.ACTION_INPUT_METHOD_SETTINGS` reference (`https://developer.android.com/reference/android/provider/Settings.html#ACTION_INPUT_METHOD_SETTINGS`); AOSP Settings search-indexing / `SearchIndexablesProvider` pattern (`https://source.android.com/docs/automotive/hmi/car_settings/search_indexing`); F-Droid reproducible-build docs (`https://f-droid.org/docs/Reproducible_Builds/`); Unicode Emoji 17.0 / Unicode 17.0 (`https://unicode.org/reports/tr51/`, `https://www.unicode.org/versions/latest/`); CLDR 48.2 downloads (`https://cldr.unicode.org/index/downloads`); FlorisBoard tags/releases (`https://api.github.com/repos/florisboard/florisboard/tags?per_page=10`, `https://github.com/florisboard/florisboard/releases/tag/v0.5.2`); HeliBoard v3.9 and AnySoftKeyboard v1.13-r1 release pages.
- **Unverifiable here:** No Android SDK on this host (the audit docs note the same), so gradle gates, on-device focus/IME-raise behavior, and TalkBack output could not be executed — those acceptance criteria are marked "manual on-device" in the roadmap. [Needs validation]

## Current Product Map

Privacy-first multilingual IME. `:app` is Apache-2.0-ceiling, no network permission, no telemetry, no account. Networked/native capability (voice recognizer runtime, FunctionGemma/MCP, glide ML model) is pushed to optional **signed addon APKs** rather than linked in. Persistence: JetPref datastore (partitioned per feature area as of v1.8.202), Room + SQLCipher for clipboard/dictionary, Tink for string-pref crypto. Release stream v1.8.x with one `## vX.Y.Z` `CHANGELOG.md` section and a fastlane changelog per versionCode. CI: no-network manifest gate, Roborazzi visual gate, OSV/dependency scan, reproducible-build tooling, string-no-translations validation, emulator smoke.

## Feature Inventory (delta focus)

- **Settings search (v1.8.204, NEW):** accessed via Settings → Search route (`Routes.Settings` arm added in `1966c69`) and exposed from Settings Home through the app-bar search action; implemented as a static catalog in `SettingsSearchIndex` (entries with title/summary/screen-title/keyword haystacks, weighted `score()` ranking) rendered by `SettingsSearchScreen` (TextField + `LazyColumn` of `JetPrefListItem`). Highlight handoff via `SettingsSearchHighlightStore`. Maturity: shipped with ranking tests plus the v1.8.221 real-resource/typed-route drift guard; still thin on no-results escape, keyword coverage, accessibility checklist coverage, and stale highlight state after result navigation. [Verified]
- Established surfaces (autocorrect/SymSpell, glide classifier, clipboard, addons, voice handoff, sync, MCP, hardware-keyboard import) are covered by `COMPLETED.md` and the audits; no net-new gap surfaced beyond what the roadmap already tracks.

## Competitive Landscape

- **Gboard (Google), SwiftKey (Microsoft):** both expose an in-settings search with auto-focused field, clear button, synonym matching, and a no-results state that still offers related sections. Lesson: settings search is expected to behave like the platform search box (auto-focus + clear + IME Search). What to avoid: their cloud sync / telemetry — explicitly a non-goal here.
- **AnySoftKeyboard, OpenBoard, HeliBoard (FLOSS analogues):** simpler settings, typically no dedicated search; SwiftFloris is already ahead by shipping one. Lesson: the differentiator is *correctness/maintainability* of the catalog, hence the drift-guard priority. What to avoid: their sparse accessibility coverage.
- **Android platform Settings search:** indexes via a `SearchIndexablesProvider` content provider so entries can't silently drift from the screens they point to. SwiftFloris's hand-maintained enum is the lighter-weight choice but needs the test-level guard (RA-1) to get the same integrity property. [Likely]

## Quality & Friction Findings

- **[Closed v1.8.221] Search catalog drift** → RA-1. `SettingsSearchIndexIntegrityTest` now checks duplicate entry IDs, real non-blank `R.string` resolution, fake-fallback leakage, and expected typed routes for every `SettingsSearchDestination`.
- **[Minor] No-results dead-end** → RA-2. (`SettingsSearchScreen.kt:106-115`.)
- **[Closed v1.8.215] Missing clear button / IME Search action** → RA-6. (`SettingsSearchScreen.kt`.)
- **[Closed v1.8.215] No auto-focus on open** → RA-7. (`SettingsSearchScreen.kt`.)
- **[Minor] Sparse keyword coverage** → RA-3. (`SettingsSearchIndex.kt:103-204`.)
- **[Minor] Search a11y gap** → RA-4. (`SettingsSearchScreen.kt:82-143`; `docs/ACCESSIBILITY.md` checklist.)
- **[Closed v1.8.215] No diacritic folding** → RA-5. (`SettingsSearchIndex.kt`.)
- **[Closed] Entry-point discoverability** → RA-8. `HomeScreen.kt` exposes `Routes.Settings.Search` through a top app-bar `FlorisIconButton`, so search is reachable from Settings Home without scrolling.
- **[Minor] Search highlight lifecycle** → RA-9. `SettingsSearchScreen.kt` marks `SettingsSearchHighlightStore.activeTarget`, `FlorisScreen.kt` renders the card whenever the target title matches, and production code has no `clear()` caller; add a one-shot consume/dismiss contract.
- **[Closed v1.8.218] Staged startup exception is never surfaced** → R2-1. `CrashUtility.consumeStagedException(...)` now persists the staged report without the process-killing handler, and `FlorisAppActivity` opens the crash dialog before installing the splash-screen keep condition.
- **[Closed v1.8.219] Remaining diagnostic `printStackTrace()` paths** → R2-2. `RestoreScreen` failure diagnostics now use `flogError`, restore UI copy falls back to the existing "Unknown error" string for null/blank throwable messages, and `CrashUtility.writeToFile` logs through `LogTopic.CRASH_UTILITY`.

## Architecture & Technical Findings

- **Module boundaries:** clean `:app` + `:lib:*` split; addon capability isolation is a deliberate, well-documented pattern. No new boundary issue surfaced.
- **Dependency health:** the security-sensitive pins checked here are still current for SQLCipher 4.16.0 and Tink 1.21.0, and Room/Robolectric also match metadata. The compatible P3 maintenance batch shipped in v1.8.216 (Compose BOM `2026.05.01`, KSP `2.3.9`, Roborazzi `1.63.0`). Kotlin `2.4.0` and AndroidX Core `1.19.0` remain gated on KSP publication and compileSdk 37 respectively; AGP 9.2.1 appears to be the stable baseline while Google Maven's newest AGP metadata is 9.3 alpha. [Verified via Maven metadata]
- **Overgrown files:** `IndicTransliterator.kt` (~86 KB), `TextKeyboardLayout.kt` (~76 KB), `LatinLanguageProvider.kt` (~60 KB), `KeyboardManager.kt` (~60 KB) are large but the SHIFT state machine was already extracted (F27 shipped) and the audits already track `LatinLanguageProvider` heap risk (A1). Left as-is — no speculative refactor proposed.
- **Testability:** 217 JVM test files, 5 androidTest. The search subsystem is the thinnest-tested new code; RA-1/RA-3 close that.
- **Release automation:** mature (reproducible build, SBOM/provenance and signed-tags already roadmapped as maintainer-gated). No new item.
- **Documentation routing:** root docs now align with the roadmap source-of-truth contract. `ROADMAP.md` owns active work, `COMPLETED.md` summarizes shipped state, `CHANGELOG.md` plus fastlane metadata owns release notes, and archived parity/improvement plans remain historical context. R2-3 closed this in v1.8.220 before future implementers pick stale instructions.

## Security / Privacy / Data Safety

No net-new permission or data-egress finding. The settings-search additions are display/navigation only; the empty/no-results system-settings deep-link (RA-2) uses Android's documented `Settings.ACTION_INPUT_METHOD_SETTINGS` intent and does not weaken the no-network posture. R2-1 and R2-2 closed as local diagnostic-safety work without adding network, telemetry, or broad file export. The deferred audit lists (`docs/AUDIT_2026-06-02.md`) remain the authority for crypto/parsing/lifecycle hardening; this pass does not duplicate them.

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
- Cycle 2 external source classes checked: Android platform/Compose docs, AOSP Settings indexing docs, Unicode/CLDR, F-Droid reproducible-build docs, FlorisBoard/HeliBoard/AnySoftKeyboard/OpenBoard release metadata.
