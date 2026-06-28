# Research - SwiftFloris

## Executive Summary
SwiftFloris is a privacy-first Android IME fork whose strongest shape is verifiable local trust: no `INTERNET` permission, encrypted local learning, local backup/restore, addon signature pins, Tasker/MCP automation boundaries, migration helpers, settings search, and repeatable local verification scripts. The highest-value direction is to make the trust story impossible to misstate after workflow removal, then harden the database/import/UI paths that carry day-to-day typing reliability. Top opportunities: 1) replace workflow-era release proof with one local evidence command and corrected public docs; 2) add a SQLCipher + Room runtime compatibility sentinel; 3) move personal-dictionary Room work off the main thread; 4) add public-doc dependency truth checks; 5) add live-doc canonical-source/release-state checks; 6) unblock Compose BOM 2026.06.00; 7) update Gradle 9.6.1 with checksum; 8) surface skipped-record diagnostics for snippets/layout imports; 9) replace the Snygg URI resolver stub with typed failures; 10) expand Roborazzi baselines for the new settings surfaces.

## Product Map
- Core workflows: enable/setup IME, configure keyboard/theme/localization/typing, type with suggestions/glide/snippets/voice handoff, import/export dictionaries/extensions/backups, review privacy/trust evidence.
- User personas: privacy-conscious Android keyboard users, SwiftKey and proprietary-keyboard migrators, power users with snippets/layouts/themes, accessibility users, contributors validating release/security claims, addon authors.
- Platforms and distribution: Android 8+ base APK, GitHub/Obtainium/F-Droid-prepared channels, Gradle/AGP/Kotlin/Compose build, no Google Play dependency and no network permission by design.
- Key integrations and data flows: local Room/SQLCipher personal dictionary, Tink AndroidKeystore wrapping, local backup/restore archives, FUTO/WhisperInput/Bibi external voice IME handoff, addon APK discovery/signing pins, signature-protected Tasker-class broadcasts, MCP daemon bridge.

## Competitive Landscape
- FlorisBoard / HeliBoard: broad Android IME customization, themes, clipboard, language/layout asks, and current user pressure around backup, CJK, suggestion controls, contrast, and layout ergonomics. Learn from active issue demand; avoid GPL code intake into `:app` and avoid reopening closed privacy gaps.
- FUTO Keyboard: strong commercial-grade local prediction and swipe narrative, open-source issue traffic around glide edge cases, action-bar polish, RTL/CJK language support, and desktop/tablet behavior. Learn from public benchmarking and offline-first positioning; avoid incompatible licensing or base-app model blobs.
- AnySoftKeyboard / fcitx5-android: no-network multilingual keyboards show enduring demand for language packs, clipboard controls, mixed-script behavior, and CJK input engines. Learn from modular language/plugin patterns; avoid turning SwiftFloris into a full fcitx/Rime engine inside the base APK.
- Keyman / Unicode LDML / Trime: mature layout and script ecosystems prove that keyboard data formats, diagnostics, and import feedback matter as much as renderer polish. Learn from explicit keyboard specs; avoid accepting malformed or ambiguous layout records silently.
- SwiftKey / Samsung Keyboard / Grammarly-class products: users expect polished writing assist, sync, voice, and migration flows. Learn from onboarding clarity and writing UX; reject account/cloud/network parity inside the base app.
- Espanso and text-expansion tools: snippet import is a power-user capability only if parse failures are explicit and recoverable. Learn from predictable text-expansion syntax and import diagnostics; avoid silent partial imports.
- F-Droid / Obtainium / Android developer verification: distribution trust is now a product feature. Learn from reproducible-build and developer-verification expectations; avoid claiming CI/SBOM/SLSA automation that no longer exists.

## Security, Privacy, and Reliability
- Verified: `.github/workflows` is absent, but live docs still promise CI/workflow-backed no-network scans, dependency scans, release workflows, SLSA attestations, SBOMs, emulator smoke, benchmark workflows, and Dependabot (`README.md`, `docs/SECURITY.md`, `docs/REPRODUCIBLE_BUILDS.md`, `docs/PRIVACY_AND_AI.md`, `docs/BENCHMARKS.md`, `docs/addons/apk-validation.md`, `.github/PULL_REQUEST_TEMPLATE.md`). This is the top trust bug.
- Verified: live docs still route contributors to missing canonical files. `AGENTS.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md`, and `.github/workflows` are absent, while `CLAUDE.md`, `CONTRIBUTING.md`, `docs/REPO_HYGIENE.md`, `docs/QA_CHECKLISTS.md`, `.github/PULL_REQUEST_TEMPLATE.md`, and README live sections still reference them. The tracked release-note source is fastlane changelogs plus README release bullets.
- Verified: public release/dependency copy drifted. `README.md` still says "What's in v1.9.52" while the badge/current release are v1.9.53; live docs cite Tink Android 1.21.0 and Roborazzi 1.63.0 while `gradle/libs.versions.toml` has Tink 1.22.0 and Roborazzi 1.64.0.
- Verified: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt` opens personal dictionary Room databases with `allowMainThreadQueries()`. `docs/THREAT_MODEL.md` calls this a known UI-lag risk; the exact Room 2.8.4 + SQLCipher 4.16.0 stack also intersects SQLCipher issue #81.
- Verified: OSV queries for `net.zetetic:sqlcipher-android@4.16.0`, `androidx.room:room-runtime@2.8.4`, `com.google.crypto.tink:tink-android@1.22.0`, and `io.github.takahirom.roborazzi:roborazzi@1.64.0` returned zero current advisories. Treat SQLCipher issue #81 as reliability, not a CVE.
- Verified: `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggUriValue.kt` still returns `Result.failure(NotImplementedError(...))` for default asset path resolution. Theme import/rendering should fail with typed user-actionable errors, not an implementation stub.
- Verified: snippet, hardware-layout, and benchmark import paths tolerate malformed lines, but some parsers silently skip records (`EspansoMatchParser`, `KlcLayoutParser`, `SwipeTraceImporter`). Dictionary import has better skipped-count UI; snippets/layouts need similar diagnostics.
- Missing guardrails: one local release-evidence entry point, live-doc link/canonical-source integrity, README release-state truth checks, Room/SQLCipher runtime sentinel, and Roborazzi coverage for newly added settings surfaces.
- Recovery and rollback needs: release evidence should state exact local commands and artifacts; database/SQLCipher regressions need a pin/rollback path; import diagnostics should preserve valid records while exposing rejected rows.

## Architecture Assessment
- Boundary improvement: after workflow removal, release/security proof must be local-first: a single script or Gradle task should compose existing checks (`verifyNoInternetPermission`, data extraction rules, fastlane/release-front-door, repo hygiene, OSV gate, reproducible verifier, lint/test/build inputs) and emit a human-readable evidence bundle.
- Refactor candidate: move personal dictionary Room operations behind an IO-bound repository boundary and remove `allowMainThreadQueries()` in `DictionaryManager.kt`.
- Refactor candidate: make public docs derive or verify dependency/tool versions from `gradle/libs.versions.toml`, `gradle-wrapper.properties`, and `gradle.properties`.
- Refactor candidate: add a live-doc integrity checker for non-archive Markdown so missing-file links and stale canonical-source references cannot reappear after local-only doc cleanup.
- Refactor candidate: replace `SnyggUriValue` default resolver stub with a deterministic asset-resolution contract and tests.
- Test gap: current `PersonalDictionaryEncryptionTest` statically checks SQLCipher wiring; it does not prove the Room + SQLCipher runtime path survives read-only transactions after Room 2.8.4.
- Test gap: Roborazzi baselines cover Addons, maintainer chips, selected pending settings, and keyboard themes, but not the new custom layout editor, snippets screen, privacy audit log, sync settings, backup, or restore surfaces.
- Documentation gap: docs are strong but stale around CI, release provenance, dependency versions, canonical source files, and release-state labels. This directly undermines the project's trust proposition.

## Rejected Ideas
- Base-APK cloud AI rewrite/translation/sync: rejected because SwiftKey/Samsung/Grammarly-style parity would require network/account paths that contradict `CONTRIBUTING.md`, `README.md`, and `docs/PRIVACY_AND_AI.md`.
- Re-adding GitHub Actions, Dependabot, or Renovate: rejected because repo/global rules explicitly removed workflows and automated dependency bots; solve with local commands and manual checks instead.
- Pulling GPL keyboard code from HeliBoard/fcitx5/Trime into `:app`: rejected because SwiftFloris keeps the base APK Apache-2.0-compatible.
- Promoting Room 3.0 or AGP 10 work to active roadmap: rejected because public metadata still shows Room 3.0 alpha / AGP 10 unavailable; keep them in blocked/planning state.
- Direct SwiftKey account import: rejected because Microsoft does not publish a stable export/import schema and the repo already documents supported migration paths in `docs/MIGRATE_FROM_SWIFTKEY.md`.
- Device-only accessibility and hardware-keyboard checks in active roadmap: rejected for this pass because they require TalkBack/Switch Access/API 37/tablet/foldable hardware; keep them in `Roadmap_Blocked.md` until a device lane exists.

## Sources
OSS keyboards:
- https://github.com/florisboard/florisboard
- https://github.com/HeliBorg/HeliBoard
- https://github.com/HeliBorg/HeliBoard/issues?q=is%3Aissue%20is%3Aopen%20label%3Aenhancement
- https://github.com/futo-org/android-keyboard
- https://keyboard.futo.org/
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/keymanapp/keyman
- https://github.com/osfans/trime

Commercial, adjacent, and community:
- https://support.microsoft.com/en-us/swiftkey-keyboard/
- https://www.samsung.com/us/support/answer/ANS10001617/
- https://support.grammarly.com/hc/en-us/categories/115000018611-Grammarly-for-Mobile
- https://espanso.org/docs/get-started/
- https://www.reddit.com/r/Android/search/?q=open%20source%20keyboard%20privacy&restrict_sr=1
- https://hn.algolia.com/?q=FUTO%20keyboard
- https://stackoverflow.com/questions/tagged/android-input-method

Platform, dependency, and security:
- https://developer.android.com/developer-verification
- https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/compose
- https://developer.android.com/build/releases/gradle-plugin
- https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
- https://services.gradle.org/versions/current
- https://repo1.maven.org/maven2/com/google/crypto/tink/tink-android/maven-metadata.xml
- https://repo1.maven.org/maven2/net/zetetic/sqlcipher-android/maven-metadata.xml
- https://repo1.maven.org/maven2/io/github/takahirom/roborazzi/roborazzi/maven-metadata.xml
- https://github.com/sqlcipher/sqlcipher-android/issues/81
- https://osv.dev/docs/
- https://f-droid.org/docs/Reproducible_Builds/
- https://unicode.org/reports/tr35/tr35-keyboards.html

## Open Questions
None. The remaining blocked decisions are already parked in `Roadmap_Blocked.md` because they require credentials, device hardware, external releases, or human policy choices.
