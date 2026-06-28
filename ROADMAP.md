# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

## Research-Driven Additions

### P0

- [ ] P0 — Replace workflow-era release proof with local release evidence
  Why: live public docs still promise GitHub Actions, Dependabot, workflow-backed scans, release attestations, SBOMs, and CI smoke gates even though `.github/workflows` is absent after local-build-only cleanup.
  Evidence: `.github/` contains no workflows; `README.md:59`, `README.md:69`, `README.md:320`, `docs/SECURITY.md:84`, `docs/REPRODUCIBLE_BUILDS.md:32`, `docs/PRIVACY_AND_AI.md:305`, `docs/BENCHMARKS.md:51`, `.github/PULL_REQUEST_TEMPLATE.md:7`, recent commit `73dc7d15`.
  Touches: `README.md`, `docs/SECURITY.md`, `docs/REPRODUCIBLE_BUILDS.md`, `docs/PRIVACY_AND_AI.md`, `docs/BENCHMARKS.md`, `docs/addons/apk-validation.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `scripts/`.
  Acceptance: one local command produces release evidence for no-network, backup/data-extraction, Fastlane/release freshness, OSV severity gate, repo hygiene, lint/test/build inputs, and reproducible APK verification; live docs no longer claim GitHub Actions/Dependabot/SLSA/SBOM automation except archived historical notes; `rg "GitHub Actions|workflow|Dependabot|SLSA|SBOM|CI" README.md docs .github -g "*.md" -g "*.yml"` has only current local-build wording or archive/history hits.
  Complexity: L

### P1

- [ ] P1 — Add SQLCipher and Room runtime compatibility sentinel
  Why: SwiftFloris uses Room 2.8.4 plus SQLCipher 4.16.0 for the encrypted personal dictionary, and SQLCipher issue #81 reports a Room 2.8.4 runtime compatibility gap around read-only transactions.
  Evidence: `gradle/libs.versions.toml`; `DictionaryManager.kt`; `PersonalDictionaryEncryptionTest.kt`; https://github.com/sqlcipher/sqlcipher-android/issues/81.
  Touches: `app/src/test/kotlin/dev/patrickgold/florisboard/ime/dictionary/`, `DictionaryManager.kt`, `gradle/libs.versions.toml` if a pin/rollback is required.
  Acceptance: a JVM/Robolectric or instrumented sentinel opens the encrypted dictionary through Room + SQLCipher, performs read/write and read-only DAO transactions, covers migration/open-helper setup, and fails with a clear remediation if the current pair is incompatible.
  Complexity: M

- [ ] P1 — Move personal dictionary Room access off the main thread
  Why: `allowMainThreadQueries()` remains on the personal dictionary path, and typing/dictionary work should not risk UI stalls during IME use.
  Evidence: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt:397`; `DictionaryManager.kt:410`; `docs/THREAT_MODEL.md` known-gap table.
  Touches: `DictionaryManager.kt`, dictionary DAOs/repositories, dictionary tests, typing latency benchmarks.
  Acceptance: no production `allowMainThreadQueries()` remains; dictionary reads/writes use an IO-safe boundary; existing dictionary/import/suggestion tests pass; cold dictionary and first-suggestion benchmarks stay inside documented regression windows.
  Complexity: L

- [ ] P1 — Add public-doc dependency truth drift check
  Why: public docs cite stale dependency versions, which weakens SwiftFloris's auditability claim.
  Evidence: `README.md` cites Roborazzi 1.63.0 and Tink Android 1.21.0; `docs/SECURITY.md` cites Tink Android 1.21.0; `gradle/libs.versions.toml` has Roborazzi 1.64.0 and Tink Android 1.22.0.
  Touches: `README.md`, `docs/SECURITY.md`, `docs/REPRODUCIBLE_BUILDS.md`, `scripts/`.
  Acceptance: a local checker extracts public-facing versions from `gradle/libs.versions.toml`, `gradle-wrapper.properties`, and `gradle.properties` and fails when README/security/reproducibility docs drift; stale version text is corrected.
  Complexity: S

- [ ] P1 — Unblock Compose BOM 2026.06.00 upgrade
  Why: the blocked roadmap still says Compose BOM 2026.06.x is unpublished, but Google Maven now publishes `androidx.compose:compose-bom:2026.06.00`.
  Evidence: Google Maven metadata `https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml`; `gradle/libs.versions.toml`; `Roadmap_Blocked.md` Compose BOM item.
  Touches: `gradle/libs.versions.toml`, Compose UI tests, Roborazzi baselines, relevant docs/blocked-roadmap cleanup.
  Acceptance: Compose BOM bumps from 2026.05.01 to 2026.06.00; unit/lint/assemble and relevant Roborazzi checks pass; any visual/accessibility diffs are accepted with updated baselines; the stale blocked entry is resolved during implementation cleanup.
  Complexity: M

### P2

- [ ] P2 — Bump Gradle wrapper to 9.6.1 with checksum verification
  Why: Gradle 9.6.1 is current and provides the latest build-tool fixes while the repo remains on 9.5.1.
  Evidence: `gradle/wrapper/gradle-wrapper.properties`; `https://services.gradle.org/versions/current`.
  Touches: `gradle/wrapper/gradle-wrapper.properties`, `docs/REPRODUCIBLE_BUILDS.md`, local verification docs.
  Acceptance: wrapper distribution URL and SHA-256 update together; wrapper validation/build/test/lint/assemble pass with JDK 21; reproducible-build documentation reflects the new pin.
  Complexity: S

- [ ] P2 — Surface skipped-record diagnostics for snippet and layout imports
  Why: competitor/community issue traffic shows import/migration/layout quality matters, and SwiftFloris dictionary import already reports skipped counts while Espanso snippets and hardware layout parsers can silently tolerate malformed lines.
  Evidence: `EspansoMatchParser.kt`; `KlcLayoutParser.kt`; `SwipeTraceImporter.kt`; `PersonalDictionaryImportSummaryDialog.kt`; HeliBoard issue traffic around imports/customization.
  Touches: `ime/snippet/`, `app/settings/typing/SnippetSettingsScreen.kt`, `ime/hardware/`, relevant parser tests and string resources.
  Acceptance: snippet/layout imports return parsed entries plus skipped/malformed diagnostics; UI shows a calm summary with a copy/exportable diagnostic detail; malformed fixtures prove partial imports remain safe and transparent.
  Complexity: M

- [ ] P2 — Replace Snygg URI resolver stub with typed failure handling
  Why: theme asset resolution should fail predictably; returning `NotImplementedError` from the default resolver is a user-facing reliability footgun if a theme path reaches it.
  Evidence: `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggUriValue.kt:97`.
  Touches: `lib/snygg/`, theme import/rendering tests, theme editor error copy.
  Acceptance: no `NotImplementedError` is used for normal resolver failure; default resolver returns a typed unsupported-path result with tests; theme import/rendering surfaces actionable copy instead of an implementation-stub error.
  Complexity: S

- [ ] P2 — Expand Roborazzi baselines for new settings surfaces
  Why: recent user-facing screens landed without matching committed visual baselines, while existing baselines cover only selected pending settings and addon/theme surfaces.
  Evidence: `Routes.kt` includes CustomLayoutEditor, SnippetSettings, PrivacyAuditLog, Sync, Backup, Restore; `app/src/test/snapshots/` lacks those screen baselines.
  Touches: `app/src/test/kotlin/dev/patrickgold/florisboard/screenshot/`, `app/src/test/snapshots/`, new `@RoboPreviewInclude` previews where appropriate.
  Acceptance: Roborazzi baselines cover custom layout editor, snippets, privacy audit, sync, backup, and restore in dark and high-contrast-relevant states; `:app:verifyRoborazziDebug` passes.
  Complexity: M
