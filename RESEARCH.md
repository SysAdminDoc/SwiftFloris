# Research - SwiftFloris

## Executive Summary

SwiftFloris is a privacy-first Android IME fork aimed at SwiftKey/Gboard-class local typing without accounts, telemetry, cloud learning, or an `INTERNET` permission. Verified current strength is no-network trust plus breadth: local dictionaries and n-grams, encrypted local stores, addon trust pins, privacy posture/audit surfaces, broad settings search, offline import/export, Roborazzi and release-evidence gates, and Android 17 compile-time readiness. Highest-value direction now is not another broad feature burst; it is converting cleared blockers into active platform work while keeping the trust story provable. Priority opportunities: harden release evidence against Kotlin build-cache CVE exposure; upgrade AboutLibraries now that compileSdk 37 is active; wire Android 17 CJKV selected-candidate accessibility metadata; make the in-tree addon sample part of release evidence; add a targetSdk 37 shadow preflight; add save/share paths for local audit exports; add an Emoji 17 parser dry-run while full CLDR 49 assets remain blocked.

## Product Map

- Core workflows: enable/setup the IME; configure typing, language, layout, theme, Smartbar, privacy, addon, backup/restore, sync, snippets, voice, emoji/stickers, clipboard, and physical-keyboard behavior; type with candidates, autocorrect, glide, snippets, media, and local auditability.
- User personas: privacy-focused Android keyboard users, SwiftKey/Gboard migrators, multilingual and CJK users, TalkBack/physical-keyboard/tablet/foldable users, power users managing local data, addon authors, and release/security reviewers.
- Platforms and distribution: Android 8+ APK, Gradle/AGP/Kotlin/Compose stack, GitHub Releases plus Obtainium, prepared F-Droid metadata, no Google Play dependency in the product strategy.
- Key integrations and data flows: Room/SQLCipher personal data, local backup/sync archives, signature-protected addon contracts, SAF sticker imports, FUTO/external voice handoff, local privacy/audit JSON, local release evidence, and no-network merged-manifest gates.

## Competitive Landscape

- HeliBoard, AnySoftKeyboard, OpenBoard, Unexpected Keyboard, Thumb-Key: these prove that no-network OSS keyboards win trust through small permission surfaces, language coverage, and low-friction install paths. SwiftFloris should keep learning from their privacy posture and language breadth while avoiding stale dependency stacks and fragile dictionary/runtime paths.
- FUTO Keyboard and FUTO Swipe: FUTO is the strongest typing-quality comparator because it publishes a swipe benchmark/demo and users actively request teach/demote behavior. SwiftFloris should keep using local replay/scorecard gates and addon boundaries; it should not import GPL/proprietary model assets into `:app`.
- FlorisBoard upstream: still useful as a compatibility and crash-warning source, especially around IME/platform edges. SwiftFloris should continue bounded cherry-pick-style hardening rather than following rewrite churn.
- Fcitx5 Android, Trime/Rime, and Keyman: strongest analogues for CJK engines, LDML keyboard formats, and plugin runtime boundaries. SwiftFloris should keep CJK and heavy runtime data behind explicit provider/addon contracts instead of growing the base APK.
- Gboard, Microsoft SwiftKey, Samsung Keyboard, Grammarly: commercial keyboards set user expectations for glide quality, clipboard/media affordances, multilingual onboarding, voice, writing assistance, and polished settings. SwiftFloris should copy only local, auditable affordances; cloud/account/telemetry features remain a deliberate non-goal.
- F-Droid and Obtainium: distribution trust is part of keyboard UX because users type secrets. SwiftFloris should keep source-to-artifact evidence first-class and make addon/release evidence harder to skip.

## Security, Privacy, and Reliability

- Verified: `gradle/libs.versions.toml` pins Kotlin `2.4.0`; CVE-2026-53914 is reported against Kotlin build-cache metadata and Maven currently exposes `2.4.20-Beta1`, while KSP metadata still tops out at `2.3.9`. Do not move production to beta; add release-build cache hardening until final Kotlin/KSP artifacts exist.
- Verified: `gradle.properties` now has `projectCompileSdk=37`, but `Roadmap_Blocked.md` still blocks AboutLibraries 15.x and Android 17 CJKV `TextAttribute` work on compileSdk 37. Those items are now actionable and should move through implementation.
- Verified: AboutLibraries is still `14.2.0`; Gradle Plugin Portal metadata lists `15.0.3` as the current release. License UI and OSS notices are user-facing trust surfaces, so this dependency should not lag behind the active compile SDK.
- Verified: `CjkInputProvider.kt`, `CjkBridgePrototype.kt`, `EditorInputConnectionBatch.kt`, and `HostileEditorCandidateReplayTest.kt` provide a clear headless seam for Android 17 CJKV selected-candidate metadata, but no `TextAttribute`/`setTextSuggestionSelected` usage exists yet.
- Verified: `docs/addons/apk-validation.md` documents `:addons:dictionary-pack-sample:assembleRelease` plus `scripts/verify-addon-apk.sh`, and `settings.gradle.kts` includes the sample module; `scripts/release-evidence.ps1` does not yet run that sample validation.
- Verified: `PrivacyAuditScreen.kt` only copies the local audit JSON to the clipboard even though `AddonAuditExport.kt` is format-stable and other settings screens already use `ActivityResultContracts.CreateDocument`. A keyboard audit log should support an explicit save/share path for reviewers who do not want clipboard mediation.
- Verified: `projectTargetSdk=36` while Android 17/API 37 behavior docs are live and compileSdk is already 37. A shadow targetSdk 37 local preflight can catch source/build/test drift before a release target bump or device-only validation.
- Verified: Unicode Emoji 17.0 keyboard test data is published under `Public/17.0.0/emoji`, but CLDR 49 artifact URLs probed during this pass returned 404. Keep the full emoji asset refresh blocked; add only parser/readiness coverage that does not change shipped CLDR-ordered assets.
- Verified: no-network invariants remain strong in README/docs/scripts/tests: the base manifest lacks `INTERNET`, addon validation bans network permissions, and release evidence includes the no-network gate.

## Architecture Assessment

- `EditorInputConnectionBatch` is the right boundary for API-gated `TextAttribute` calls because it already centralizes composing/commit call sequences and has hostile editor replay coverage.
- `CjkInputProvider` is deliberately provider/addon-shaped; production CJK data remains a licensing/data-source blocker, but Android 17 candidate-selection metadata can be implemented against the current prototype/provider contract.
- `scripts/release-evidence.ps1` is the release trust aggregator; addon sample validation and build-cache mitigation belong there rather than as README-only manual commands.
- `PrivacyAuditScreen.kt` already has a stable JSON exporter; adding `CreateDocument("application/json")` and optional share intent is a small UX/reliability improvement, not a data-model redesign.
- `gradle.properties` target/compile split is intentional today; a targetSdk 37 shadow gate should validate build/test behavior without claiming device-level compatibility covered by blocked hardware items.
- Emoji data has version metadata gates in `EmojiDataVersion`, so parser readiness can be tested with a small Unicode 17 fixture while CLDR 49 asset regeneration waits for available release artifacts.
- Category coverage: security, accessibility, i18n/l10n, observability, testing, docs, distribution/packaging, plugin ecosystem, mobile, offline/resilience, migration, and upgrade strategy all have evidence-backed work. Multi-user collaboration is intentionally excluded because SwiftFloris is a single-user local IME and adding multi-user/cloud state would conflict with the project philosophy.

## Rejected Ideas

- Promote Kotlin `2.4.20-Beta1` to production now: rejected because Maven shows only a beta compiler and KSP still tops out at `2.3.9`; mitigate release builds until final artifacts exist.
- Move the full Emoji 17/CLDR 49 asset refresh into active work today: rejected because Emoji 17.0 data exists but CLDR 49 release artifacts were not fetchable; keep full regeneration blocked and add parser dry-run coverage only.
- Put FUTO Swipe directly in `:app`: rejected because the library/model/license review remains blocked; use benchmark/replay evidence and addon isolation instead.
- Add cloud AI writing, account sync, or telemetry-backed prediction: rejected because Gboard/SwiftKey/Samsung/Grammarly parity in those areas conflicts with SwiftFloris' no-network premise.
- Embed a full Fcitx5/Rime/Keyman CJK runtime in the base APK: rejected because data/runtime licensing and APK-size risk belong behind signed addons.
- Add GitHub Actions/SLSA workflows for release trust: rejected because repo policy requires local builds and direct local release evidence.
- Treat Android developer verification registration as active implementation work: rejected because it requires human identity/payment/legal policy decisions and remains correctly blocked.

## Sources

OSS keyboards and adjacent projects:
- https://github.com/HeliBorg/HeliBoard
- https://github.com/futo-org/android-keyboard
- https://swipe.futo.tech/
- https://github.com/florisboard/florisboard
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/openboard-team/openboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/keymanapp/keyman
- https://github.com/dessalines/thumb-key

Commercial and community:
- https://support.google.com/gboard/answer/7068494
- https://www.microsoft.com/en-us/swiftkey
- https://www.samsung.com/us/support/answer/ANS10000943/
- https://support.grammarly.com/hc/en-us/articles/25028519116429-Error-Grammarly-Assistant-is-not-enabled-right-now
- https://discuss.techlore.tech/t/what-keyboard-are-you-using-on-android/6588

Platform, standards, dependencies, security:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/reference/android/view/inputmethod/TextAttribute.Builder#setTextSuggestionSelected(boolean)
- https://developer.android.com/developer-verification
- https://www.cve.org/CVERecord?id=CVE-2026-53914
- https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml
- https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml
- https://plugins.gradle.org/m2/com/mikepenz/aboutlibraries/plugin/com.mikepenz.aboutlibraries.plugin.gradle.plugin/maven-metadata.xml
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/emoji2
- https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt
- https://cldr.unicode.org/index/downloads/cldr-49
- https://www.unicode.org/reports/tr35/tr35-keyboards.html
- https://f-droid.org/en/2025/03/04/even-my-keyboard-is-built-reproducibly.html
- https://github.com/ImranR98/Obtainium

## Open Questions

- None for active prioritization. Human identity/payment decisions, hardware-only validation, FUTO Swipe license/model review, production CJK data sourcing, Kotlin final 2.4.20/KSP compatibility, and CLDR 49 artifacts remain blocked in `Roadmap_Blocked.md`.
