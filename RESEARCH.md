# Research — SwiftFloris

## Executive Summary
SwiftFloris is a privacy-first Android IME fork with a strong trust posture: no `INTERNET` permission, signed release flow, backup/data-extraction exclusions, encrypted local dictionaries, addon enrollment, Espanso snippets, Tasker/MCP integrations, Compose settings, and CI checks that already cover release metadata, no-network drift, layout JSON, Roborazzi, lint, unit tests, OSV, SBOM, SLSA, reproducibility, and 16 KB native alignment. The highest-value direction is to keep tightening release trust, addon developer ergonomics, coroutine safety, and visual/regression proof instead of adding cloud or heavyweight ML features to the base app. Top opportunities, in priority order: release `v1.9.53`/issue #9 fixes through the human-gated release process; triage active dependency PRs with CI evidence; add a host-verifiable addon fixture APK; put production `runBlocking` behind a no-growth allowlist; use Roborazzi 1.64 preview filtering to widen visual baselines; add synthetic glide cap/latency regression tests before the external FUTO corpus; reconcile cleared blocked items such as Compose BOM 2026.06.00 and Emoji 17/CLDR 49; resolve the `ImeWindowMode.Fixed.THUMBS` placeholder.

## Product Map
- Core workflows: enable the IME; type with tap/glide/hardware/voice handoff; manage dictionaries, clipboard, snippets, profiles, themes, layouts, and local privacy posture; import/export local data; enroll and audit addons.
- User personas: privacy-conscious Gboard/SwiftKey refugees, multilingual offline typists, power users using terminal keys/snippets/hardware keyboards, accessibility users, addon authors, and maintainers who need release provenance.
- Platforms and distribution: Android app/IME, minSdk 26, targetSdk 36, GitHub Releases and Obtainium as canonical distribution, F-Droid metadata prepared, no Google Play dependency by design.
- Key integrations and data flows: local Room/SQLCipher stores, Jetpack DataStore, Android backup/data-transfer excludes, Tasker intents, MCP daemon bridge, addon APK descriptors, FUTO Voice Input handoff, Espanso YAML import, SwiftKey/Gboard/FlorisBoard dictionary migration, GitHub release provenance.

## Competitive Landscape
- FUTO Keyboard / FUTO Swipe: strong offline voice and public swipe-data work, including a 1M-row swipe dataset and benchmark framing. SwiftFloris should learn from its public evaluation discipline and keep glide quality measurable; it should avoid bundling large voice/RIME/neural engines into the base app.
- HeliBoard: strongest FOSS Gboard-style peer with active user-request pressure around floating keyboards, image clipboard, split/landscape modes, CJK, and toolbar customization. SwiftFloris should keep prioritizing compatibility polish and user-visible controls; it should avoid inheriting closed gesture-library dependency risk.
- FlorisBoard upstream: remains the architectural source for Compose/Snygg theming and custom layouts, while long-running NLP and layout-editor requests show the cost of promises without shipping loops. SwiftFloris should keep delivering narrow, test-backed slices and avoid broad "coming soon" NLP commitments.
- AnySoftKeyboard, fcitx5-android, and Trime: show mature language-pack/plugin separation and CJK schema ecosystems. SwiftFloris should strengthen addon fixtures and verification; it should avoid absorbing CJK/Rime complexity into the base keyboard until data licensing, native runtime, and test-device issues are solved.
- Unexpected Keyboard, Thumb-Key, and 8VIM: prove persistent demand for terminal modifiers, compact/one-hand layouts, and power-user key surfaces. SwiftFloris already covers terminal presets; it should either finish or remove unused thumb-mode placeholders instead of accumulating dead modes.
- Keyman and CLDR/LDML keyboards: provide the strongest standards-oriented model for layout authoring and portable keyboard definitions. SwiftFloris should keep layout JSON and LDML import/export round-trippable; it should avoid inventing incompatible layout semantics where standards cover the case.
- Gboard, Samsung Keyboard, and Grammarly: commercial leaders are moving writing assistance and dictation into AI layers, often cloud/account backed or OS-overlay based. SwiftFloris should interoperate with OS/vendor writing overlays where they work with any IME; it should not add networked grammar, GIF, or cloud-sync features that weaken the no-network proof.

## Security, Privacy, and Reliability
- [Verified] Public release freshness is the largest trust gap, but it is human-gated: GitHub Releases latest observed release is `v1.9.48` while source metadata and README claim `v1.9.53` in the current worktree; issue #9 crash reporters do not benefit until a release is cut. Evidence: `gradle.properties`, `README.md`, GitHub Releases, GitHub issue #9, `Roadmap_Blocked.md`.
- [Verified] The merged manifest keeps the no-network promise; `app/src/main/AndroidManifest.xml` declares IME, spellchecker, file import, Tasker, addon, and MCP surfaces but no `INTERNET` permission.
- [Verified] Backup and transfer excludes are materially stronger than many keyboard peers: `app/src/main/res/xml/data_extraction_rules.xml` excludes dictionary DBs, key prefs, clipboard history/files, personal n-grams/trigrams, trace logs, sync, and diagnostics; `backup_rules.xml` is narrow allowlist-style.
- [Verified] Destructive clipboard Room migrations are fixed: no `fallbackToDestructiveMigration` usage remains, and `ClipboardDatabase.kt` contains explicit migrations. This should not be re-added as roadmap work.
- [Verified] Current security-critical local crypto dependencies are fresh: `gradle/libs.versions.toml` pins Tink Android 1.22.0 and SQLCipher Android 4.16.0, both current by Maven metadata checked on 2026-06-25.
- [Verified] Active dependency PRs still need evidence-based triage rather than blanket merging: PRs cover Gradle wrapper 9.6.0, Kotest/Roborazzi, Compose/Core/Coil/Tink grouping, AboutLibraries 15.0.0, and GitHub Actions/OSV updates. Some are likely safe; compileSdk 37-related updates need holding or explicit blocker routing.
- [Verified] Production `runBlocking` remains broad across editor, NLP, spellchecker, cache, and UI-support code paths, including `AbstractEditorInstance.kt`, `EditorInstance.kt`, `FlorisSpellCheckerService.kt`, `NlpProviderRegistry.kt`, `NlpManager.kt`, `CacheManager.kt`, `QuickActionsEditorPanel.kt`, `HeuristicSmartComposeProvider.kt`, and `TextKeyboardCache.kt`. The right next step is an allowlist/no-growth gate, not a risky mass rewrite.
- [Verified] Addon trust docs and validation scripts exist, but there is no host-verifiable sample addon APK project in-tree. Evidence: `docs/addons/apk-validation.md`, `docs/addons/dictionary-pack-spec.md`, `scripts/verify-addon-apk.sh`.
- [Verified] Glide quality has a bounded local gap independent of the blocked public corpus: `StatisticalGlideTypingClassifier.kt` hard-codes `Gesture.MAX_SIZE = 500` with a TODO, and `docs/BENCHMARKS.md` says the public glide trace benchmark is pending first corpus run.
- [Likely] Compose BOM 2026.06.00 and Emoji 17/CLDR 49 blockers have changed since older blocked notes; both should be rechecked in `Roadmap_Blocked.md` during the next maintenance pass before creating duplicate active work.

## Architecture Assessment
- Module/boundary improvements needed: keep base-app features local and deterministic; move ecosystem growth through verifiable addon fixtures; make dependency updates pass through release/security gates; keep cloud AI, CJK engines, and voice runtimes out of the base app unless they ship as isolated addons.
- Refactor candidates: `ImeWindowMode.kt` has `Fixed.THUMBS` as a TODO placeholder; `FlorisImeSizing.kt` still notes a provider move into `ImeWindow`; `StatisticalGlideTypingClassifier.kt` needs trace-size cap proof; `SnyggUriValue.kt` has a default path resolver that throws `NotImplementedError`.
- Testing gaps: visual coverage exists but is hand-curated; Roborazzi 1.64 preview annotation filtering can broaden Settings/theme/addon coverage without capturing every preview. Glide has harness pieces but needs synthetic cap tests before external corpus work.
- Documentation gaps: addon docs are present, but addon authors need a buildable reference APK plus CI verifier output. Release/version truth is split between source metadata, README, release workflow, and GitHub Releases.
- Distribution gaps: F-Droid submission and Android developer verification remain human/operator-gated; they belong in blocked planning, not active coding roadmap, until account, identity, and store decisions are made.
- Accessibility and device-validation gaps: host-side accessibility patterns are represented in tests, while TalkBack, Switch Access, Credential Manager, and hardware-device verification stay in blocked planning until real-device coverage exists.
- Observability and multi-user scope: crash/diagnostic evidence should remain local/exportable because network telemetry conflicts with the product posture; multi-account/team features are not a fit for a single-user Android IME.
- Upgrade strategy gaps: active dependency PRs need a written merge/hold decision trail in code, workflow, or blocked-roadmap state so the next coding agent does not re-research the same version constraints.

## Rejected Ideas
- Cloud sync, GIF search, networked grammar, and account-backed AI: rejected because the no-`INTERNET` proof is a core differentiator and Citizen Lab's keyboard research makes local-only input handling strategically important.
- Bundling FUTO Swipe, Whisper, RIME, or transformer prediction in the base app: rejected for runtime size, native complexity, licensing/test burden, and conflict with the addon-first architecture; use isolated addons or blocked evaluations.
- Full CJK/fcitx/Rime engine parity now: rejected because source data, native runtime, and device validation are already blocked outside active roadmap scope.
- Mass migration away from every `runBlocking`: rejected because it is high churn and high regression risk; an allowlist plus targeted migrations gives safer root-cause control.
- Duplicating active blocked items for F-Droid submission, developer verification, TalkBack/device coverage, Credential Manager inline autofill, public FUTO glide corpus, local voice runtime, or Emoji 17 refresh: rejected because `Roadmap_Blocked.md` already owns externally gated work.
- AboutLibraries 15.0.0 as an automatic merge: rejected until its compileSdk/targetSdk expectations are verified against SwiftFloris's current targetSdk 36 ceiling.
- In-app self-updater: rejected because Obtainium/GitHub Releases/F-Droid cover update distribution with less supply-chain surface.
- Formal third-party security audit as code roadmap: valuable but rejected for this active roadmap because it is procurement/operator work, not an implementable repository task.

## Sources

### Project and OSS Competitors
- https://github.com/SysAdminDoc/SwiftFloris/releases
- https://github.com/SysAdminDoc/SwiftFloris/issues/9
- https://github.com/SysAdminDoc/SwiftFloris/pulls
- https://github.com/florisboard/florisboard
- https://github.com/HeliBorg/HeliBoard
- https://github.com/futo-org/android-keyboard
- https://keyboard.futo.org/
- https://huggingface.co/datasets/futo-org/swipe.futo.org
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/Julow/Unexpected-Keyboard
- https://github.com/dessalines/thumb-key
- https://github.com/8VIM/8VIM
- https://github.com/tribixbite/CleverKeys
- https://github.com/keymanapp/keyman
- https://github.com/espanso/espanso

### Commercial, Community, and Privacy
- https://techcrunch.com/2026/05/12/google-adds-gemini-powered-dictation-to-gboard-which-could-be-bad-news-for-dictation-startups/
- https://support.grammarly.com/hc/en-us/articles/15606282682637-Grammarly-for-Android-user-guide
- https://www.sammobile.com/news/one-ui-7-0-galaxy-ai-writing-tools-any-keyboard/
- https://citizenlab.ca/research/vulnerabilities-across-keyboard-apps-reveal-keystrokes-to-network-eavesdroppers/chinese-keyboard-app-vulnerabilities-explained/

### Platform, Standards, and Dependencies
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html
- https://developer.android.com/developer-verification
- https://f-droid.org/en/2026/02/24/open-letter-opposing-developer-verification.html
- https://developer.android.com/develop/ui/compose/bom
- https://github.com/takahirom/roborazzi/releases
- https://github.com/google/osv-scanner-action/releases
- https://www.unicode.org/emoji/charts-17.0/emoji-released.html
- https://cldr.unicode.org/downloads/cldr-49

## Open Questions
- Was the `v1.9.53` source-versus-release gap intentional, or should the release workflow be re-run for issue #9 users before additional feature work?
- Should the next maintenance pass move cleared blocked items, especially Compose BOM 2026.06.00 and Emoji 17/CLDR 49, out of `Roadmap_Blocked.md` before implementation starts?
- Should the first canonical addon fixture be a dictionary-pack APK, an MCP daemon bridge addon, or both with one shared verifier harness?
