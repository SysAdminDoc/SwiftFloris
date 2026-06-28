# Research - SwiftFloris

## Executive Summary
SwiftFloris is a privacy-first Android IME fork that is strongest where it turns trust into verifiable local behavior: no `INTERNET` permission, encrypted local dictionaries/clipboard data, rich migration tooling, addon trust pins, search/settings surfaces, Roborazzi baselines, and local benchmark scripts. The highest-value direction is not another cloud-style feature; it is restoring public truth after workflow removal, then hardening the local release/dependency/database paths that now carry the trust burden. Top opportunities: 1) replace stale GitHub Actions/Dependabot/SLSA/SBOM claims with a local release-evidence command and doc rewrite; 2) add a SQLCipher + Room 2.8.4 runtime sentinel for the exact encrypted dictionary stack; 3) remove personal-dictionary `allowMainThreadQueries()`; 4) add a public-doc version drift checker; 5) unblock Compose BOM 2026.06.00; 6) update Gradle 9.6.1 with checksum; 7) surface skipped/malformed import diagnostics; 8) replace the Snygg URI `NotImplementedError` failure path; 9) expand visual baselines for newly added settings screens.

## Product Map
- Core workflows: enable/setup IME, configure keyboard/theme/localization/typing, type with suggestions/glide/snippets/voice handoff, import/export dictionaries/extensions/backups, review privacy/trust evidence.
- User personas: privacy-conscious Android keyboard users, SwiftKey/Gboard migrators, power users with snippets/layouts/themes, contributors validating release/security claims, addon authors.
- Platforms and distribution: Android 8+ base APK, GitHub/Obtainium/F-Droid-prepared channels, Gradle/AGP/Kotlin/Compose build, no Google Play channel by design.
- Key integrations and data flows: local Room/SQLCipher personal dictionary, Tink AndroidKeystore wrapping, local backup/restore archives, FUTO/WhisperInput/Bibi external voice IME handoff, addon APK discovery/signing pins, Tasker-class signature-protected broadcasts, MCP daemon bridge.

## Competitive Landscape
- FlorisBoard / HeliBoard: broad Android IME customization, themes, clipboard, language/layout asks, and active user pressure around backup, CJK, suggestion controls, contrast, and layout ergonomics. Learn from active issue demand; avoid GPL code intake into `:app` and avoid reopening closed privacy gaps.
- FUTO Keyboard / FUTO Swipe: strongest current open benchmark signal for swipe typing and local-model positioning; open issues show real demand around glide correctness, action-bar ergonomics, RTL, CJK, and prediction controls. Learn from public benchmark framing; keep FUTO Swipe itself blocked until dataset/license review clears.
- AnySoftKeyboard: long-running no-internet, language-pack ecosystem model with Apache-2.0 base; issue history validates clipboard, voice provider, language-pack, and settings-search demand. Learn from modular language packs; avoid stale compatibility debt and old unmaintained request queues.
- fcitx5-android / Trime / Keyman: strongest analogous systems for CJK, Rime/Fcitx/LDML package ecosystems, input-method engines, and script-specific behavior. Learn from engine/package architecture and diagnostics; avoid linking incompatible GPL/LGPL/native engines directly into the base APK.
- Gboard / SwiftKey / Samsung Keyboard / Grammarly: commercial table stakes are strong prediction, rewrite/tone, translation, voice, stickers, sync, backup, and polished recovery states. Learn which features users expect; avoid account/cloud/telemetry implementation in the base APK.
- Espanso and text-expansion tools: validate snippet import and variable expansion as a power-user feature. Learn from explicit import diagnostics and predictable parse failures; avoid silent partial imports.

## Security, Privacy, and Reliability
- Verified: `.github/workflows` is absent, but live docs still promise CI/workflow-backed no-network scans, dependency scans, release workflows, SLSA attestations, SBOMs, emulator smoke, benchmark workflows, and Dependabot (`README.md`, `docs/SECURITY.md`, `docs/REPRODUCIBLE_BUILDS.md`, `docs/PRIVACY_AND_AI.md`, `docs/BENCHMARKS.md`, `docs/addons/apk-validation.md`, `.github/PULL_REQUEST_TEMPLATE.md`). This is the top trust bug.
- Verified: public dependency/version copy drifted. `gradle/libs.versions.toml` has Tink Android 1.22.0 and Roborazzi 1.64.0, while `README.md` and `docs/SECURITY.md` still cite Tink 1.21.0 / Roborazzi 1.63.0 in live sections.
- Verified: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt` opens personal dictionary Room databases with `allowMainThreadQueries()`. `docs/THREAT_MODEL.md` already calls this a known low UI-lag risk; the exact dependency stack now also intersects SQLCipher issue #81 for Room 2.8.4 compatibility.
- Verified: OSV queries for `net.zetetic:sqlcipher-android@4.16.0`, `androidx.room:room-runtime@2.8.4`, `com.google.crypto.tink:tink-android@1.22.0`, `io.coil-kt.coil3:coil-compose@3.4.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-android@1.11.0`, and `androidx.compose:compose-bom@2026.05.01` returned no current advisories. Treat SQLCipher issue #81 as reliability, not CVE.
- Verified: `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggUriValue.kt` still returns `Result.failure(NotImplementedError(...))` for default asset path resolution. Theme import/rendering should fail with typed user-actionable errors, not an implementation stub.
- Verified: snippet, hardware-layout, and benchmark import paths deliberately tolerate malformed lines, but some parsers silently skip records (`EspansoMatchParser`, `KlcLayoutParser`, `SwipeTraceImporter`). Dictionary import has better skipped-count UI; snippets/layouts need similar diagnostics.

## Architecture Assessment
- Boundary improvement: after workflow removal, release/security proof must be local-first: a single script or Gradle task should compose existing checks (`verifyNoInternetPermission`, data extraction rules, fastlane/release-front-door, repo hygiene, OSV gate, reproducible verifier, lint/test/build inputs) and emit a human-readable evidence bundle.
- Refactor candidate: move personal dictionary Room operations behind an IO-bound repository boundary and remove `allowMainThreadQueries()` in `DictionaryManager.kt`.
- Refactor candidate: make public docs derive or verify dependency/tool versions from `gradle/libs.versions.toml`, `gradle-wrapper.properties`, and `gradle.properties`.
- Refactor candidate: replace `SnyggUriValue` default resolver stub with a deterministic asset-resolution contract and tests.
- Test gap: current `PersonalDictionaryEncryptionTest` statically checks SQLCipher wiring; it does not prove the Room + SQLCipher runtime path survives read-only transactions after Room 2.8.4.
- Test gap: Roborazzi baselines cover Addons, maintainer chips, selected pending settings, and keyboard themes, but not the new custom layout editor, snippets screen, privacy audit log, sync settings, backup, or restore surfaces.
- Documentation gap: docs are strong but stale around CI, release provenance, and dependency versions. This now directly undermines the project's trust proposition.

## Rejected Ideas
- Base-APK cloud AI rewrite/translation/sync: rejected because Gboard/SwiftKey/Samsung/Grammarly parity would require network/account paths that contradict `CONTRIBUTING.md`, `README.md`, and `docs/PRIVACY_AND_AI.md`.
- Re-adding GitHub Actions, Dependabot, or Renovate: rejected because repo/global rules explicitly removed workflows and automated dependency bots; solve with local commands and manual checks instead.
- Moving FUTO Swipe integration to active roadmap now: rejected for this pass because `Roadmap_Blocked.md` already tracks dataset download/license review as the blocker.
- Android 17 CJK `TextAttribute` / physical-keyboard password behavior: rejected for active roadmap because `Roadmap_Blocked.md` already gates it on compileSdk 37 and device/emulator validation.
- TalkBack key echo, Switch Access, Credential Manager inline autofill, foldable/tablet physical keyboard validation: rejected for active roadmap because `Roadmap_Blocked.md` already parks them behind device/hardware validation.
- Production CJK engine/data import from fcitx5/Rime/Trime: rejected for base APK because licensing/data-source review and native/runtime packaging are unresolved; keep addon or blocked research lane.
- Multi-user/cloud collaboration: rejected because SwiftFloris is a local Android IME with transport-neutral CRDT scaffolding; server-backed multi-user sync would violate the no-network base posture.

## Sources
OSS competitors and analogous projects:
- https://github.com/florisboard/florisboard
- https://github.com/HeliBorg/HeliBoard
- https://github.com/futo-org/android-keyboard
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/keymanapp/keyman
- https://github.com/8VIM/8VIM
- https://github.com/SimpleMobileTools/Simple-Keyboard
- https://github.com/topics/android-ime

Commercial, community, and research signals:
- https://keyboard.futo.org/
- https://swipe.futo.tech/
- https://arxiv.org/abs/2606.25247
- https://github.com/HeliBorg/HeliBoard/issues?q=is%3Aissue%20is%3Aopen%20label%3Aenhancement
- https://github.com/futo-org/android-keyboard/issues?q=is%3Aissue%20is%3Aopen
- https://github.com/fcitx5-android/fcitx5-android/issues
- https://support.google.com/gboard/
- https://www.microsoft.com/en-us/swiftkey
- https://www.grammarly.com/android

Platform, dependency, and security:
- https://developer.android.com/developer-verification
- https://developer.android.com/identity/autofill/ime-autofill
- https://developer.android.com/build/releases/gradle-plugin
- https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
- https://services.gradle.org/versions/current
- https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml
- https://repo1.maven.org/maven2/io/github/takahirom/roborazzi/io.github.takahirom.roborazzi.gradle.plugin/maven-metadata.xml
- https://repo1.maven.org/maven2/net/zetetic/sqlcipher-android/maven-metadata.xml
- https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml
- https://github.com/sqlcipher/sqlcipher-android/issues/81
- https://osv.dev/docs/

## Open Questions
- None block active prioritization. Human/device/signing/data-license questions are already parked in `Roadmap_Blocked.md`.
