# SwiftFloris Research Report

This report summarizes current research conclusions. The full 2026-05-25 research plan is archived at `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`. Deep-research pass refreshed **2026-06-03** (post-v1.8.204), with 2026-06-04 freshness notes through Cycle 3.

2026-06-04 Cycle 3 note: the branch is clean but five commits ahead of
`origin/master`, with `HEAD` at `8142536` and no tag pointing at it. The latest
three code-fix commits after the v1.8.225 docs marker cover n-gram data loss,
thread safety, SealedBoxCrypto KDF/scrubbing, private-session trace suppression,
Arabic combining-mark shaping, and Snygg selector/contentScale handling, but
the release ledger still tops out at v1.8.225 / versionCode 2025. R3-1 was
added as a P0 release/source-of-truth reconciliation item. External checks found
FUTO Keyboard v0.1.29 (2026-06-01) as the main new competitor signal: FUTO Swipe
publishes a 1M-swipe public dataset, top-1/top-4 benchmark framing, accepted+3
alternative glide behavior, and clipboard-history search. F21 was sharpened
with this glide evidence, and R3-2 was added to finish SwiftFloris' already
tested clipboard-query helper by wiring it into the keyboard clipboard palette.
R3-3 freezes the sealed-box envelope/KDF contract with deterministic vectors
before sync transport persists envelopes, using libsodium sealed-box docs and
RFC 5869 as primary references. R3-4 backfills regression tests for the newest
hotfix surfaces.

2026-06-04 freshness note: the live dirty tree has already moved EI7 out of active work into v1.8.207 release docs, with `VoiceInputEmptyStateCopyTest.kt` pinning the FUTO explanation and F-Droid install action. This pass did not run Gradle because repo instructions say not to run Android gates from this VM unless asked; the changelog's green Gradle evidence remains unverified here. Current external checks support the copy: FUTO's Voice Input page describes it as working entirely on-device with no stored data, latest F-Droid/standalone version v1.3.6 (28), and the source mirror says FUTO Voice Input remains available for third-party keyboards even though FUTO development has shifted toward FUTO Keyboard. Android-platform sources also moved: Android 17 API 37 setup docs are current, but SwiftFloris already keeps API 37 as a future behavior-gate decision. Maven metadata shows low-priority freshness drift rather than a security issue: Kotlin 2.4.0, Compose BOM 2026.05.01, AndroidX Core 1.19.0, and Roborazzi 1.63.0 are newer than the pinned versions, while Room 2.8.4, SQLCipher 4.16.0, Tink 1.21.0, and Robolectric 4.16.1 still match current metadata. A P3 dependency-refresh row was added to `ROADMAP.md`.

2026-06-04 delivery note: v1.8.215 closed RA-5 / RA-6 / RA-7. Settings search now folds combining diacritics during normalization, opens the field focused on first entry, exposes a clear action while text is present, and advertises the Search IME action. v1.8.221 closed RA-1 with a real-resource and typed-route drift guard, v1.8.222 closed RA-2 with a Browse all settings fallback for zero-result searches, v1.8.223 closed RA-3 with high-traffic synonym coverage for dark theme, haptic, trace, punctuation, and privacy queries, and v1.8.224 closed RA-10 with a populated-query result-scroll reset. The remaining settings-search queue is RA-4 plus RA-9 highlight-lifecycle follow-up.

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

2026-06-04 search-scroll note: local source inspection added RA-10. Settings search recomputes ranked results from `searchQuery`, but the `LazyColumn` keeps a single `rememberLazyListState()` across query changes and only has a first-open focus `LaunchedEffect`. The implementation target is a query-keyed scroll reset so a previous query's scroll offset does not hide the top hit for the next query.

## Executive Summary

SwiftFloris is a mature, heavily-audited privacy-first Android IME (FlorisBoard fork, `dev.patrickgold.florisboard`, `:app` permission-clean with no `INTERNET`). At v1.8.225 plus the three untagged local post-release fixes, the feature surface is broad (autocorrect/prediction, glide typing, clipboard, addons, voice handoff, sync, MCP bridge, hardware-keyboard import) and the compatible dependency stack is current for the applied pins (Compose BOM 2026.05.01, Kotlin 2.3.21, AGP 9.2.1, targetSdk 36). Three deep engineering audits (2026-05-28/29 and 2026-06-02) plus the existing roadmap already cover correctness, crypto, resource, and device-gated visual work, so the **net-new** opportunity space is narrow and concentrated on source-of-truth release hygiene, the already-partial clipboard search surface, and sync-crypto contract tests. The **settings search** feature shipped in v1.8.204 (commit `1966c69`) now has drift/no-results/synonym/scroll polish, while accessibility/highlight-lifecycle gaps remain. [Verified]

Top opportunities (one line each):

1. **Drift guard for the search catalog** — destination-route mapping, unique IDs, and real string resources are now pinned by `SettingsSearchIndexIntegrityTest` (RA-1). [Closed]
2. **No-results dead-end** — zero-result searches now include a Browse all settings action back to Settings Home (RA-2). [Closed]
3. **Search UX polish** — clear button, `ImeAction.Search`, auto-focus, and diacritic folding shipped in v1.8.215 (RA-5/6/7). [Closed]
4. **Keyword/synonym coverage** — high-traffic capability terms like "dark theme", "haptic", "trace", "punctuation", and "privacy" are now covered and pinned by search tests (RA-3). [Closed]
5. **TalkBack pass over search** — no semantics/live-region on results or count; not in `ACCESSIBILITY.md` QA checklist (RA-4, P2). [Verified]
6. **Search entry-point discoverability** from Settings home was already satisfied by the app-bar search action (RA-8). [Closed]
7. **Restore/crash diagnostic consistency** — remaining `printStackTrace()` paths were replaced with project logging plus user-safe fallback copy in v1.8.219 (R2-2). [Closed]
8. **Root docs source-of-truth refresh** — onboarding docs now route open work, shipped state, release notes, and archived planning context consistently (R2-3). [Closed]
9. **Release-ledger reconciliation** — local post-v1.8.225 fixes need a new version/changelog/fastlane/tag handoff before they are treated as shipped (R3-1, P0). [Verified]
10. **Clipboard history search UI** — pure filtering and a default-on pref exist, but the in-keyboard clipboard palette still only exposes type filters; FUTO v0.1.29 adds clipboard history search as current parity evidence (R3-2, P1). [Verified]
11. **Sealed-box contract vectors** — sync crypto tests need deterministic envelope/KDF vectors before CRDT transport persists or exchanges encrypted deltas (R3-3, P1). [Verified]
12. **Search highlight lifecycle** — the global search highlight target is never consumed by production code, so stale result cards can reappear after the original search flow (RA-9, P2). [Verified]
13. **Search result scroll reset** — populated non-blank queries now reset the result list to the top when the query changes (RA-10). [Closed]

No Critical or Major reliability/security defects were found that are not already on the roadmap or in the deferred audit lists. The remaining heavy work (glide model training, Vosk addon, F-Droid submission, device-only visual verification) stays maintainer-gated as the existing roadmap records.

## Evidence Reviewed

- **Key files/dirs:** `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/search/` (`SettingsSearchIndex.kt`, `SettingsSearchScreen.kt`), `app/src/test/.../settings/search/SettingsSearchIndexTest.kt`, `FlorisApplication.kt`, `FlorisAppActivity.kt`, `lib/crashutility/CrashUtility.kt`, `RestoreScreen.kt`, `BackupScreen.kt`, `Flog.kt`, `gradle/libs.versions.toml`, `gradle.properties`, `app/src/main/AndroidManifest.xml`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`, `README.md`, `docs/ACCESSIBILITY.md`, `.github/workflows/*`, and the three `docs/AUDIT_2026-*.md` reports (read-only).
- **Git range:** `git log --oneline -n 40`; `git show --stat --oneline v1.8.223..HEAD` confirmed v1.8.224 -> v1.8.225 docs/build/release movement plus the newest local n-gram/thread-safety/crypto/privacy, Arabic-shaping, and Snygg hotfixes.
- **External sources / standards:** Android Compose semantics and live-region guidance (`https://developer.android.com/develop/ui/compose/accessibility/semantics`); Android `Settings.ACTION_INPUT_METHOD_SETTINGS` reference (`https://developer.android.com/reference/android/provider/Settings.html#ACTION_INPUT_METHOD_SETTINGS`); AOSP Settings search-indexing / `SearchIndexablesProvider` pattern (`https://source.android.com/docs/automotive/hmi/car_settings/search_indexing`); F-Droid reproducible-build docs (`https://f-droid.org/docs/Reproducible_Builds/`); Unicode Emoji 17.0 / Unicode 17.0 (`https://unicode.org/reports/tr51/`, `https://www.unicode.org/versions/latest/`); CLDR 48.2 downloads (`https://cldr.unicode.org/index/downloads`); FlorisBoard v0.6.0-alpha02 (`https://github.com/florisboard/florisboard/releases/tag/v0.6.0-alpha02`); HeliBoard v3.9 (`https://github.com/HeliBorg/HeliBoard/releases/tag/v3.9`); AnySoftKeyboard v1.13-r1 (`https://github.com/AnySoftKeyboard/AnySoftKeyboard/releases/tag/1.13-r1`); FUTO Keyboard v0.1.29 / FUTO Swipe (`https://github.com/futo-org/android-keyboard/releases/tag/0.1.29`); libsodium sealed boxes (`https://doc.libsodium.org/public-key_cryptography/sealed_boxes`); RFC 5869 HKDF (`https://datatracker.ietf.org/doc/html/rfc5869`).
- **Unverifiable here:** This research-only pass did not run Gradle or device QA; on-device focus/IME-raise behavior, TalkBack output, and clipboard palette interaction remain manual acceptance criteria for the build machine. [Needs validation]

## Current Product Map

Privacy-first multilingual IME. `:app` is Apache-2.0-ceiling, no network permission, no telemetry, no account. Networked/native capability (voice recognizer runtime, FunctionGemma/MCP, glide ML model) is pushed to optional **signed addon APKs** rather than linked in. Persistence: JetPref datastore (partitioned per feature area as of v1.8.202), Room + SQLCipher for clipboard/dictionary, Tink for string-pref crypto. Release stream v1.8.x with one `## vX.Y.Z` `CHANGELOG.md` section and a fastlane changelog per versionCode. CI: no-network manifest gate, Roborazzi visual gate, OSV/dependency scan, reproducible-build tooling, string-no-translations validation, emulator smoke.

## Feature Inventory (delta focus)

- **Settings search (v1.8.204, NEW):** accessed via Settings → Search route (`Routes.Settings` arm added in `1966c69`) and exposed from Settings Home through the app-bar search action; implemented as a static catalog in `SettingsSearchIndex` (entries with title/summary/screen-title/keyword haystacks, weighted `score()` ranking) rendered by `SettingsSearchScreen` (TextField + `LazyColumn` of `JetPrefListItem`). Highlight handoff via `SettingsSearchHighlightStore`. Maturity: shipped with ranking tests, the v1.8.221 real-resource/typed-route drift guard, the v1.8.222 no-results Settings Home action, the v1.8.223 synonym-hit coverage, and the v1.8.224 query-change scroll reset; still thin on accessibility checklist coverage and stale highlight state after result navigation. [Verified]
- **Clipboard history search (partial):** `ClipboardHistoryFilter` and
  `ClipboardHistoryFilterTest` pin a privacy-neutral text-query contract, and
  `prefs.clipboard.historySearchEnabled` exists, but `ClipboardInputLayout`
  currently applies only item-type filters. R3-2 is the UI wire-up, not a new
  storage feature. [Verified]
- **Sync sealed-box scaffold (partial):** `SealedBoxCrypto` uses X25519 +
  AES-GCM and an HMAC-based KDF after the latest local fix, but it is still
  scaffold/test-surface rather than a full production transport. R3-3 asks for
  vectors/schema docs before transport lands. [Verified]
- Established surfaces (autocorrect/SymSpell, glide classifier, clipboard, addons, voice handoff, sync, MCP, hardware-keyboard import) are covered by `COMPLETED.md` and the audits; no net-new gap surfaced beyond what the roadmap already tracks.

## Competitive Landscape

- **FUTO Keyboard:** v0.1.29 is the strongest new 2026 signal for the
  offline-keyboard space: FUTO Swipe ships with a public swipe dataset,
  top-1/top-4 benchmark framing, accepted+3 alternative glide results, and
  clipboard-history search. Lesson: SwiftFloris' existing F21 glide-model item
  should evaluate against public test-set framing, and the already-tested
  clipboard query helper should graduate to UI. What to avoid: absorbing any
  incompatible runtime or network posture into `:app`.
- **Gboard (Google), SwiftKey (Microsoft):** both expose an in-settings search with auto-focused field, clear button, synonym matching, and a no-results state that still offers related sections. Lesson: settings search is expected to behave like the platform search box (auto-focus + clear + IME Search). What to avoid: their cloud sync / telemetry — explicitly a non-goal here.
- **AnySoftKeyboard, OpenBoard, HeliBoard (FLOSS analogues):** simpler settings, typically no dedicated search; SwiftFloris is already ahead by shipping one. Lesson: the differentiator is *correctness/maintainability* of the catalog, hence the drift-guard priority. What to avoid: their sparse accessibility coverage.
- **Android platform Settings search:** indexes via a `SearchIndexablesProvider` content provider so entries can't silently drift from the screens they point to. SwiftFloris's hand-maintained enum is the lighter-weight choice but needs the test-level guard (RA-1) to get the same integrity property. [Likely]

## Quality & Friction Findings

- **[Closed v1.8.221] Search catalog drift** → RA-1. `SettingsSearchIndexIntegrityTest` now checks duplicate entry IDs, real non-blank `R.string` resolution, fake-fallback leakage, and expected typed routes for every `SettingsSearchDestination`.
- **[Closed v1.8.222] No-results dead-end** → RA-2. The empty-results branch now shows the no-results message plus a `Browse all settings` text button that navigates to `Routes.Settings.Home`.
- **[Closed v1.8.215] Missing clear button / IME Search action** → RA-6. (`SettingsSearchScreen.kt`.)
- **[Closed v1.8.215] No auto-focus on open** → RA-7. (`SettingsSearchScreen.kt`.)
- **[Closed v1.8.223] Sparse keyword coverage** → RA-3. `SettingsSearchIndex` now has targeted synonyms for dark/light theme mode, haptic feedback, trace/shape-writing gestures, punctuation spacing, and privacy audit, with JVM query coverage.
- **[Minor] Search a11y gap** → RA-4. (`SettingsSearchScreen.kt:82-143`; `docs/ACCESSIBILITY.md` checklist.)
- **[Closed v1.8.215] No diacritic folding** → RA-5. (`SettingsSearchIndex.kt`.)
- **[Closed] Entry-point discoverability** → RA-8. `HomeScreen.kt` exposes `Routes.Settings.Search` through a top app-bar `FlorisIconButton`, so search is reachable from Settings Home without scrolling.
- **[Minor] Search highlight lifecycle** → RA-9. `SettingsSearchScreen.kt` marks `SettingsSearchHighlightStore.activeTarget`, `FlorisScreen.kt` renders the card whenever the target title matches, and production code has no `clear()` caller; add a one-shot consume/dismiss contract.
- **[Closed v1.8.224] Search result scroll reset** → RA-10. `SettingsSearchScreen` now scrolls populated non-blank result sets back to item 0 when the query changes, guarded by `SettingsSearchScreenStateTest`.
- **[Closed v1.8.218] Staged startup exception is never surfaced** → R2-1. `CrashUtility.consumeStagedException(...)` now persists the staged report without the process-killing handler, and `FlorisAppActivity` opens the crash dialog before installing the splash-screen keep condition.
- **[Closed v1.8.219] Remaining diagnostic `printStackTrace()` paths** → R2-2. `RestoreScreen` failure diagnostics now use `flogError`, restore UI copy falls back to the existing "Unknown error" string for null/blank throwable messages, and `CrashUtility.writeToFile` logs through `LogTopic.CRASH_UTILITY`.
- **[High] Local release ledger drift** → R3-1. Three code-fix commits after
  the v1.8.225 docs marker are untagged and absent from the release ledger.
- **[Medium] Clipboard query helper not surfaced in the IME palette** → R3-2.
  The pure helper and pref exist; the user-facing keyboard UI does not expose a
  search field yet.

## Architecture & Technical Findings

- **Module boundaries:** clean `:app` + `:lib:*` split; addon capability isolation is a deliberate, well-documented pattern. No new boundary issue surfaced.
- **Sync crypto contract:** the recent HMAC-KDF correction is the right
  direction, but deterministic vectors and envelope-schema docs are missing.
  Because the transport is still scaffold-level, this is a pre-release
  hardening item rather than a production decrypt-migration incident.
- **Dependency health:** the security-sensitive pins checked here are still current for SQLCipher 4.16.0 and Tink 1.21.0, and Room/Robolectric also match metadata. The compatible P3 maintenance batch shipped in v1.8.216 (Compose BOM `2026.05.01`, KSP `2.3.9`, Roborazzi `1.63.0`). Kotlin `2.4.0` and AndroidX Core `1.19.0` remain gated on KSP publication and compileSdk 37 respectively; AGP 9.2.1 appears to be the stable baseline while Google Maven's newest AGP metadata is 9.3 alpha. [Verified via Maven metadata]
- **Overgrown files:** `IndicTransliterator.kt` (~86 KB), `TextKeyboardLayout.kt` (~76 KB), `LatinLanguageProvider.kt` (~60 KB), `KeyboardManager.kt` (~60 KB) are large but the SHIFT state machine was already extracted (F27 shipped) and the audits already track `LatinLanguageProvider` heap risk (A1). Left as-is — no speculative refactor proposed.
- **Testability:** 218 JVM test files, 5 androidTest. The search catalog's integrity and synonym-hit coverage are now pinned by RA-1 and RA-3, and the RA-10 scroll-reset guard is covered; RA-4 remains the manual/accessibility coverage gap.
- **Release automation:** mature (reproducible build, SBOM/provenance and signed-tags already roadmapped as maintainer-gated). No new item.
- **Documentation routing:** root docs now align with the roadmap source-of-truth contract. `ROADMAP.md` owns active work, `COMPLETED.md` summarizes shipped state, `CHANGELOG.md` plus fastlane metadata owns release notes, and archived parity/improvement plans remain historical context. R2-3 closed this in v1.8.220 before future implementers pick stale instructions.

## Security / Privacy / Data Safety

No net-new permission or data-egress finding. The settings-search additions are display/navigation only; the no-results Browse all settings action (RA-2), synonym keyword coverage (RA-3), and query-change scroll reset (RA-10) do not weaken the no-network posture. R2-1 and R2-2 closed as local diagnostic-safety work without adding network, telemetry, or broad file export. R3-2 is also local-only clipboard filtering. R3-3 is sync-crypto contract hardening before transport activation, with no new permission or native dependency. The deferred audit lists (`docs/AUDIT_2026-06-02.md`) remain the authority for crypto/parsing/lifecycle hardening; this pass does not duplicate them.

## UX & Accessibility

The keyboard surface already has a strong a11y baseline (`ACCESSIBILITY.md`, `TouchTargetWcagTest`, RTL mirroring, candidate-row custom actions). The gap is that the **new** search screen wasn't brought under that umbrella: no field label/semantics, no live-region result-count announcement, and no entry in the manual-QA checklist (RA-4). UX polish (auto-focus, clear, IME Search, diacritic folding, no-results escape) brings search to parity with platform expectations without scope creep.

## Explicit Non-Goals (rejected + why)

- **Dynamic/reflective settings indexing** (auto-discover entries from the route graph) — rejected: heavier than the problem; a test-level drift guard (RA-1) gets the integrity benefit without runtime reflection cost on an IME process.
- **Fuzzy/typo-tolerant search (edit-distance)** — rejected for now: substring + shipped synonym keywords cover the realistic miss cases; edit-distance adds index cost for marginal value on a ~100-entry catalog.
- **Cloud-synced search history / suggestions** — rejected: violates the no-network / no-telemetry posture.
- **Refactoring the 60-86 KB files** — rejected: no task requires it; the audits already track the only load-bearing one (`LatinLanguageProvider` heap).

## Open Questions (genuine blockers only)

1. RA-4 still needs manual TalkBack verification on a device after the semantics pass.
2. RA-9 is a code-local follow-up and does not require a product decision before implementation.

## Archived Evidence

- `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`
- `docs/AUDIT_2026-05-28.md`, `docs/AUDIT_2026-05-29.md`, `docs/AUDIT_2026-06-02.md` (deep engineering audits; deferred-item authority)
- `.ai/research/2026-05-17/`, `.ai/research/2026-05-25/`
- Cycle 2 external source classes checked: Android platform/Compose docs, AOSP Settings indexing docs, Unicode/CLDR, F-Droid reproducible-build docs, FlorisBoard/HeliBoard/AnySoftKeyboard/OpenBoard release metadata.
- Cycle 3 companion: `.ai/research/2026-06-04/CYCLE_3_FINDINGS.md`.
- Cycle 3 external source classes checked: FUTO Keyboard v0.1.29 / FUTO Swipe,
  AnySoftKeyboard 1.13-r1, HeliBoard 3.9, FlorisBoard v0.6.0-alpha02,
  libsodium sealed boxes, RFC 5869 HKDF, Android Compose semantics/live-region,
  Unicode UAX #53 Arabic mark rendering.
