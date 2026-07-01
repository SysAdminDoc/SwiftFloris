# Research — SwiftFloris

## Executive Summary

SwiftFloris is a privacy-first Android input method that aims for SwiftKey/Gboard-class typing while keeping the base app offline, auditable, account-free, telemetry-free, and without `INTERNET`. Its strongest current shape is the trust stack around no-network manifests, encrypted local stores, release evidence, addon validation, local typing features, and broad settings/QA coverage. The highest-value direction is now root-cause reliability and provability: keep the active Android 17 CJK accessibility, addon validation, targetSdk 37 preflight, privacy-audit export, and Emoji 17 dry-run roadmap items, then add five net-new gaps in priority order: fail release evidence on ignored root crash/replay logs; make candidate trailing-space policy provider-owned; add explicit latency budgets to main-thread `runBlocking` bridges; improve crash reports with environment and redaction fields; remove stale `RESEARCH_FEATURE_PLAN.md` source references.

## Product Map

- Core workflows: enable the keyboard, configure typing/languages/layouts/themes/privacy/addons/backup, type with candidates/autocorrect/glide/snippets/emoji/clipboard/voice handoff, export local evidence, and verify releases.
- User personas: privacy-focused Android users, SwiftKey/Gboard migrators, multilingual and CJK users, TalkBack/physical-keyboard/tablet users, addon authors, release/security reviewers, and maintainers triaging device-only IME failures.
- Platforms and distribution: Android 8+ APK, Kotlin/Compose/Gradle Android app, GitHub Releases/Obtainium/F-Droid-prep distribution, no Play-services dependency as a product requirement.
- Key integrations and data flows: Room/SQLCipher personal data, Tink-protected secrets, local audit JSON, SAF imports/exports, signature-pinned addons, FUTO/external voice handoff, no-network manifest gates, local release-evidence bundles.

## Competitive Landscape

- HeliBoard and OpenBoard: strong no-network trust and customization; SwiftFloris should keep learning from their theme/layout/dictionary sharing, but avoid HeliBoard's closed-source glide dependency path by keeping glide evidence local and open-compatible.
- FUTO Keyboard and FUTO Swipe: strongest modern OSS typing-quality comparator; SwiftFloris should keep benchmark/replay gates and transparent local AI affordances, while avoiding direct model/library import until licensing is cleared.
- AnySoftKeyboard: mature addon/language-pack ecosystem and physical-keyboard support; SwiftFloris should continue moving extension packaging into automated release evidence instead of relying on documentation alone.
- Fcitx5 Android, Trime/Rime, and Keyman: best analogues for CJK engines, custom schemas, and language-package ecosystems; SwiftFloris should keep heavy language runtimes behind provider/addon contracts and prioritize candidate semantics before data breadth.
- Unexpected Keyboard and Thumb-Key: show that narrow, privacy-conscious keyboards can win with focused ergonomics, Weblate/i18n discipline, and F-Droid presence; SwiftFloris should copy their clarity, not their reduced feature surface.
- Gboard, Microsoft SwiftKey, Samsung Keyboard, and Grammarly: set expectations for glide quality, multilingual UX, clipboard/media, voice, and writing assistance; SwiftFloris should copy only local, inspectable interactions and reject cloud/account/telemetry parity.
- F-Droid and Obtainium: distribution trust is part of keyboard UX because users type secrets; SwiftFloris should make source-to-artifact and addon evidence harder to skip.

## Security, Privacy, and Reliability

- Verified: the repo root currently contains ignored local `hs_err_pid24404.log`, `hs_err_pid24424.log`, and `replay_pid24404.log`; `.gitignore` ignores `*.log`, while `scripts/check-no-root-crash-logs.sh` checks only `git ls-files`, so release evidence can pass with noisy local crash/replay artifacts still present.
- Verified: `scripts/release-evidence.ps1` runs the root-crash-log and repo-hygiene checks, but those checks are committed-file focused; local artifact contamination is a release-trust blind spot rather than a shipped-source bug.
- Verified: `KeyboardManager.kt` has duplicated soft/hardware spacebar TODOs saying candidate spacing should be determined by `SuggestionProvider`; `CjkInputProvider.kt` already models non-Latin candidates, making a provider-owned commit policy the right root-cause fix.
- Verified: `scripts/check-runblocking-allowlist.py` gates production `runBlocking` drift, and `NlpProviders.kt` documents a per-keystroke main-thread path; the current allowlist records rationales but not hot-path category, budget, or measurable CPU-only assertions.
- Verified: `.github/ISSUE_TEMPLATE/bug_report.yml` asks for SwiftFloris version, install source, device, and Android version, but `.github/ISSUE_TEMPLATE/crash_report.yml` does not; crash reports also need explicit redaction prompts because keyboard logs can contain private text.
- Verified: multiple source/test comments still reference `RESEARCH_FEATURE_PLAN.md`, while live research now belongs in `RESEARCH.md` and historical feature plans are under `docs/research-feature-plan-*`; this is maintainability drift, not a product feature.
- Verified: existing active roadmap items remain valid and should not be duplicated: Android 17 `TextAttribute` selected-candidate signaling, addon sample APK validation in release evidence, targetSdk 37 shadow preflight, local privacy audit save/share, and Emoji 17 parser dry-run.
- Verified: dependency metadata does not justify a broad upgrade item today: KSP is current at `2.3.9`, Tink Android is current at `1.22.0`, and Kotlin's latest Maven metadata is `2.4.20-Beta1`, which is not a safe production bump.

## Architecture Assessment

- `EditorInputConnectionBatch` is the right boundary for API-gated `TextAttribute` work because it centralizes commit/composition calls and already has hostile editor replay tests.
- `SuggestionProvider`/`SuggestionCandidate` should own candidate commit semantics such as trailing spaces; keeping that logic in `KeyboardManager` couples Latin autocorrect behavior to CJK, emoji, media, and snippet candidates.
- `scripts/release-evidence.ps1` should remain the release-trust aggregator; local ignored crash/replay logs, addon sample validation, OSV, no-network, and repo hygiene belong in one evidence bundle.
- `scripts/check-runblocking-allowlist.py` is a good ratchet but needs risk classification; a counted allowlist alone cannot tell maintainers which main-thread IME bridges are safe under per-keystroke latency constraints.
- `.github/ISSUE_TEMPLATE/crash_report.yml` is a maintainer-facing observability surface; adding environment and redaction fields is cheaper than post-hoc triage across install channels.
- `docs/REPO_HYGIENE.md`, `docs/LOCAL_VERIFICATION.md`, and `scripts/check-live-doc-integrity.py` already establish doc hygiene expectations; replacing stale research-plan references should be a small code-comment cleanup, not a new documentation system.
- Category coverage: security, accessibility, i18n/l10n, observability, testing, docs, distribution/packaging, plugin ecosystem, mobile, offline/resilience, migration, and upgrade strategy were all reviewed. Multi-user collaboration remains intentionally excluded because it conflicts with a local single-user keyboard trust model.

## Rejected Ideas

- Promote Kotlin `2.4.20-Beta1`: rejected because Maven shows a beta compiler and the project needs release-stable Android/KSP alignment.
- Add broad commercial keyboard parity: rejected because account/cloud writing assistance, telemetry-backed prediction, and online GIF/search flows conflict with the no-network base app.
- Embed full Fcitx5/Rime/Keyman runtimes in `:app`: rejected because heavy runtime/data licensing and APK-size risk belong behind explicit addons.
- Add a new Weblate/i18n program as a roadmap item now: rejected because current evidence found privacy keyboard comparators using it, but no local translation pipeline gap was verified in this pass.
- Add a new GitHub Actions/SLSA release workflow: rejected because this repo's policy and current architecture require local builds and local evidence instead.
- Add a full Emoji 17/CLDR 49 asset refresh now: rejected as a duplicate/misfit; the existing roadmap correctly limits active work to a parser dry-run while full CLDR artifacts remain blocked.
- Add a dependency-upgrade sweep: rejected because current KSP/Tink are already latest and Kotlin's only newer artifact checked is beta.

## Sources

OSS keyboards and adjacent projects:
- https://github.com/HeliBorg/HeliBoard
- https://github.com/openboard-team/openboard
- https://github.com/futo-org/android-keyboard
- https://swipe.futo.tech/
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/keymanapp/keyman
- https://github.com/Julow/Unexpected-Keyboard
- https://github.com/dessalines/thumb-key

Commercial and community:
- https://support.google.com/gboard/answer/7068494
- https://www.microsoft.com/en-us/swiftkey
- https://www.samsung.com/us/support/answer/ANS10000943/
- https://support.grammarly.com/hc/en-us/articles/25028519116429-Error-Grammarly-Assistant-is-not-enabled-right-now
- https://discuss.techlore.tech/t/what-keyboard-are-you-using-on-android/6588

Platform, standards, dependencies, and distribution:
- https://developer.android.com/reference/android/view/inputmethod/TextAttribute.Builder#setTextSuggestionSelected(boolean)
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/developer-verification
- https://developer.android.com/guide/practices/page-sizes
- https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt
- https://cldr.unicode.org/index/downloads/cldr-49
- https://www.unicode.org/reports/tr35/tr35-keyboards.html
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/emoji2
- https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml
- https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml
- https://repo1.maven.org/maven2/com/google/crypto/tink/tink-android/maven-metadata.xml
- https://f-droid.org/en/2025/03/04/even-my-keyboard-is-built-reproducibly.html
- https://github.com/ImranR98/Obtainium

## Open Questions

- None for active prioritization. Human identity/payment decisions, hardware-only validation, FUTO Swipe licensing/model review, production CJK data sourcing, final Kotlin/KSP compatibility, and full CLDR 49 asset availability remain blocked external inputs rather than active roadmap work.
