# Research - SwiftFloris

## Executive Summary

SwiftFloris is a privacy-first Android IME fork focused on SwiftKey-class local typing without accounts, telemetry, cloud learning, or an `INTERNET` permission. Its strongest current shape is trust plus local capability: SQLCipher-backed learning, offline import/export/sync, addon signing pins, first-run privacy disclosure, local release gates, benchmark/scorecard scripts, and a broad settings surface. Highest-value direction, in order: fix the live-doc integrity self-failure; make public docs reproducible from tracked files only; clear Kotlin/compileSdk 37 stale blockers already on the roadmap; harden inline suggestion inflation before it copies upstream crashes; wire parser diagnostics into import UI; treat the existing scrollable candidate renderer as an enablement/test gap, not a renderer rewrite; add glide out-of-bounds regression fixtures; add context-scoped next-word rejection; expand Roborazzi coverage for new settings surfaces; finish row-count-aware custom layout sizing.

## Product Map

- Core workflows: enable/setup IME; configure typing, languages, layouts, themes, Smartbar, privacy posture, backup/restore, sync, snippets, voice handoff, stickers, addons; type with candidates, autocorrect, glide, snippets, clipboard, and rich content.
- User personas: privacy-conscious Android keyboard users, SwiftKey/Gboard migrators, multilingual/RTL/CJK users, physical-keyboard/tablet/foldable users, power users creating snippets/layouts/themes, addon authors, release/security reviewers.
- Platforms and distribution: Android 8+ APK, Gradle/AGP/Kotlin/Compose build, GitHub/Obtainium/F-Droid-prepared distribution, local builds only, no Play-services dependency required by core IME.
- Key integrations and data flows: Room + SQLCipher personal dictionary, Tink-wrapped local secrets, local backup/sync archives, SAF sticker folders, signature-protected addon and Tasker/MCP contracts, FUTO/external voice IME handoff, local release evidence and doc-integrity gates.

## Competitive Landscape

- FUTO Keyboard and FUTO Swipe: FUTO is the most active proprietary-adjacent comparator and publishes a strong swipe research/demo stack. Learn from its measured glide focus, context-aware prediction, and suggestion-bar teaching requests. Avoid importing GPLv3/proprietary-model assets into the Apache-2.0 base APK; any experiment belongs in an isolated addon.
- HeliBoard: Strong privacy/open-source keyboard with active beta releases and high-signal issue traffic. Learn from demand for scrollable candidates, image clipboard support, Unicode 17 emoji, touchpad mode, folded-state scaling, and zip-slip hardening. Avoid proprietary Google glide dependencies.
- FlorisBoard upstream: Useful cautionary baseline because the alpha branch still sees crashes around inline suggestions, UI-thread work, clipboard masking, rich-editor detection, Teams/desktop/physical-keyboard edge cases, and stale docs. SwiftFloris should continue shipping bounded fixes rather than following rewrite churn.
- AnySoftKeyboard/OpenBoard/Unexpected Keyboard: Strong "no internet" lineage and broad language support, but issue traffic shows dictionary crashes, settings loss, foldable split requests, emoji bugs, and lower modernization velocity. Learn from language/plugin breadth; avoid accumulating unowned native/dictionary crash paths.
- Fcitx5 Android, Trime/Rime, and Keyman: Best references for serious CJK/input-method frameworks, plugin engines, LDML-style keyboard data, and cross-platform keyboard infrastructure. Learn addon boundaries and data-format discipline; avoid pulling large CJK engines into the base APK without license/data decisions.
- Gboard, Microsoft SwiftKey, Samsung Keyboard, Grammarly: Commercial table stakes are multilingual setup, glide quality, clipboard/sticker affordances, prediction teachability, writing assistance, and polished onboarding. SwiftFloris should match local UX affordances where possible; avoid cloud sync, account coupling, telemetry, and remote model dependencies.
- F-Droid/Obtainium distribution trust: Reproducible build evidence and clear source-to-artifact traceability matter for keyboard adoption. SwiftFloris already has local gates; the next step is making docs and release claims impossible to drift.

## Security, Privacy, and Reliability

- Verified: `python scripts/check-live-doc-integrity.py` currently fails on `ROADMAP.md:60` because the active roadmap contains the forbidden `.github/workflows/` literal while the checker bans deleted workflow references. This is a release-evidence blocker, not a cosmetic doc issue.
- Verified: `CONTRIBUTING.md` links to `docs/LOCAL_VERIFICATION.md`, `docs/REPO_HYGIENE.md`, `docs/QA_CHECKLISTS.md`, and `docs/AUTOCORRECT_LIFECYCLE.md`; these files exist locally but are not tracked, so public/fresh-clone onboarding silently depends on ignored files.
- Verified: `FlorisImeService.kt` builds inline suggestion specs with `Size(0, 0)` and `Size(Int.MAX_VALUE, Int.MAX_VALUE)` and passes responses into `NlpInlineAutofill.showInlineSuggestions`; FlorisBoard issue #3294 shows the same platform path can crash with `InlineSuggestion.inflate` size validation. SwiftFloris should clamp/catch and test malformed inline presentations.
- Verified: Kotlin is pinned to `2.4.0`; CVE-2026-53914 is fixed in the 2.4.20 line. This is already in ROADMAP and remains a high-priority supply-chain hygiene item.
- Verified: `Roadmap_Blocked.md` still treats AGP 9.3.0 as required for compileSdk 37, but AGP 9.2.0+ supports API 37. Existing roadmap items cover moving the false blocker and bumping compileSdk.
- Verified: `docs/THREAT_MODEL.md` still lists `allowMainThreadQueries()` as a gap even though commit `765295b9` moved personal dictionary Room access to IO; existing roadmap covers this correction.
- Verified: `scripts/osv-release-gate.py` is a parser/gate only and exits 2 when `osv-result.json` is missing. Release verification must continue to include the scan producer, not just the parser.
- Likely: The no-network invariant is strong because the manifest has no `INTERNET`, README documents the invariant, and local release gates check merged permissions.

## Architecture Assessment

- `CandidatesDisplayMode.DYNAMIC_SCROLLABLE` and a horizontal-scroll code path already exist in `CandidatesRow.kt`; the existing scrollable-suggestion roadmap item should focus on unblocking the preference migration in `AppPrefs.kt`, user-facing copy, accessibility/Roborazzi coverage, and QA rather than building a renderer from scratch.
- `scripts/typing-quality-scorecard.py`, `scripts/glide-benchmark.py`, SwiftKey replay fixtures, and `SwiftKeyCandidateRankerTest` make FUTO issue #2120 actionable as local glide endpoint-plausibility fixtures instead of speculative model work.
- `CorrectionOutcomePriors`, `LearnedWordForgetSuggestionCandidate`, candidate-removal UI, and personal n-gram stores make FUTO issue #2117 actionable as context-scoped next-word rejection without globally blacklisting a word.
- `ImportDiagnostics` and parser-side `parseWithDiagnostics()` already landed for Espanso and KLC imports; remaining work is UI surfacing and malformed fixture coverage.
- Roborazzi coverage still trails new settings surfaces: custom layout editor, snippets, privacy audit, sync, backup, restore, and Smartbar-only mode.
- Row-count-aware custom layouts remain architecture work in `FlorisImeSizing`, `TextKeyboardLayout`, and popup anchoring; this is supported by HeliBoard 4-row layout issue traffic and current sizing comments.
- CompileSdk 37 unlocks Android 17 IME visibility, `TextAttribute`, physical-keyboard password behavior verification, and AboutLibraries/core 15.x/1.19.x upgrades.
- The untracked-doc problem shows `scripts/check-live-doc-integrity.py` should validate tracked-source truth, not just filesystem existence, for public markdown links.

## Rejected Ideas

- Put FUTO Swipe directly in `:app`: rejected because GPLv3/proprietary-model licensing conflicts with the Apache-2.0 base APK; only an isolated addon is viable.
- Re-add GitHub Actions/SLSA/SBOM workflows: rejected because project rules require local builds and existing docs already disclaim remote attestation.
- Add shell-command quick actions from FUTO issue #2129: rejected because an IME invoking arbitrary shell/stdin actions conflicts with SwiftFloris' security posture.
- Build a full CJK engine in the base APK: rejected because Fcitx5/Trime/Keyman show the correct model is an engine/addon boundary with explicit data licensing.
- Treat awesome-list discovery as a roadmap driver: rejected because current awesome-list hits were tiny/low-signal and did not outperform direct repo/issue/release research.
- Replace emoji2 with a maintained custom emoji font now: rejected because FUTO's size savings are attractive but the asset pipeline would distract from current reliability blockers.
- Duplicate the existing scrollable-suggestion roadmap item: rejected because the code already has `DYNAMIC_SCROLLABLE`; the active item should be narrowed during implementation.

## Sources

OSS keyboards:
- https://github.com/HeliBorg/HeliBoard
- https://github.com/HeliBorg/HeliBoard/issues/2584
- https://github.com/HeliBorg/HeliBoard/pull/2472
- https://github.com/futo-org/android-keyboard
- https://github.com/futo-org/android-keyboard/issues/2120
- https://github.com/futo-org/android-keyboard/issues/2117
- https://swipe.futo.tech/
- https://gitlab.futo.org/keyboard/swipe-library
- https://github.com/florisboard/florisboard/issues/3294
- https://github.com/florisboard/florisboard/issues/3300
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4771
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/keymanapp/keyman

Platform, standards, dependencies, security:
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/jetpack/androidx/releases/compose
- https://www.cve.org/CVERecord?id=CVE-2026-53914
- https://cvefeed.io/vuln/detail/CVE-2026-53914
- https://plugins.gradle.org/m2/com/mikepenz/aboutlibraries/plugin/com.mikepenz.aboutlibraries.plugin.gradle.plugin/maven-metadata.xml
- https://www.unicode.org/reports/tr35/tr35-keyboards.html
- https://unicode.org/reports/tr51/
- https://cldr.unicode.org/index/downloads/cldr-49
- https://developer.android.com/developer-verification
- https://support.google.com/android-developer-console/answer/16561738
- https://f-droid.org/en/2025/03/04/even-my-keyboard-is-built-reproducibly.html

Commercial and community:
- https://support.google.com/gboard/answer/7068494
- https://www.microsoft.com/en-us/swiftkey
- https://discuss.techlore.tech/t/what-keyboard-are-you-using-on-android/6588

## Open Questions

- None for prioritization. FUTO Swipe addon distribution remains blocked by license/model-weight and dataset policy decisions already tracked outside the active roadmap.
