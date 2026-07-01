# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

## Research-Driven Additions

### P3

## Research-Driven Additions (2026-06-29)

### P1

### P3

## Research-Driven Additions

### P1

### P2

## Research-Driven Additions (2026-06-29 refresh)

### P1

### P2

- [ ] P2 — Add a targetSdk 37 shadow build/replay preflight
  Why: compileSdk is 37 while release target remains 36; a local shadow target gate catches source/test drift before a future target bump or device-only validation pass.
  Evidence: `gradle.properties`; Android 17 behavior docs; existing `ImeVisibilityConfigurationPolicyTest` and `AndroidAdaptiveImeWindowTest`.
  Touches: Gradle project properties/build scripts, release evidence or a dedicated local preflight script, API 37 behavior tests, README verification notes.
  Acceptance: A documented local command temporarily builds/tests with `projectTargetSdk=37` without changing the release target, covers the existing API 37 behavior replay tests, and reports clear pass/fail evidence.
  Complexity: M

- [ ] P2 — Add save/share export paths for the local privacy audit log
  Why: The audit bundle is stable JSON but `PrivacyAuditScreen` only copies it to the clipboard; reviewers need an explicit file/share route that avoids clipboard mediation.
  Evidence: `AddonAuditExport.kt`; `PrivacyAuditScreen.kt`; `strings.xml`; commercial keyboard trust surfaces; local-only privacy posture.
  Touches: `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/privacy/PrivacyAuditScreen.kt`, `strings.xml`, settings policy/tests, Roborazzi baseline if visual layout changes.
  Acceptance: Privacy audit offers copy plus save/share actions, saves `application/json` via `ActivityResultContracts.CreateDocument`, never uploads automatically, shows success/cancel/failure feedback, and tests verify exported JSON excludes typed text/content.
  Complexity: M

### P3

- [ ] P3 — Add an Emoji 17 parser dry-run fixture without changing shipped CLDR assets
  Why: Unicode Emoji 17.0 keyboard test data is available, but full CLDR 49 assets are not; parser readiness can be tested now without changing production emoji ordering.
  Evidence: Unicode Emoji 17.0 `emoji-test.txt`; `EmojiData.kt`; `EmojiDataVersionTest.kt`; blocked CLDR 49 emoji refresh item.
  Touches: Emoji asset-generation/parsing tests, `EmojiDataVersionTest`, minimal test fixtures, `Roadmap_Blocked.md` note when implemented.
  Acceptance: A small fixture containing at least one E17.0 sequence parses through the existing emoji data path, records `emoji=17.0` metadata in tests, keeps shipped assets unchanged, and leaves the full CLDR 49 refresh blocked until CLDR artifacts are available.
  Complexity: S

## Research-Driven Additions

### P1

### P2

- [ ] P2 — Make candidate trailing-space policy provider-owned
  Why: Soft and hardware spacebar paths duplicate a TODO that candidate spacing should come from `SuggestionProvider`, and non-Latin/CJK/media candidates should not inherit Latin autocorrect spacing by accident.
  Evidence: `KeyboardManager.kt:808`; `KeyboardManager.kt:858`; `NlpProviders.kt`; `CjkInputProvider.kt`; Fcitx5 Android and Trime candidate-engine separation.
  Touches: `SuggestionCandidate`, `SuggestionProvider`, `EditorInputBehaviorPolicy`, `KeyboardManager`, CJK candidate tests, hostile editor replay tests, hardware-keyboard space tests.
  Acceptance: Candidate/provider metadata defines whether accepting a candidate commits a trailing space, soft and hardware spacebar paths share the same policy, current Latin autocorrect behavior is preserved, and tests cover Latin, CJK, emoji/media, and snippet candidates.
  Complexity: M

- [ ] P2 — Add hot-path latency budgets to the production `runBlocking` allowlist
  Why: The allowlist catches drift by count/string, but it does not classify main-thread per-keystroke bridges or prove their CPU-only latency budget.
  Evidence: `scripts/check-runblocking-allowlist.py`; `scripts/runblocking-allowlist.txt`; `NlpProviders.kt:208`; `docs/BENCHMARKS.md`.
  Touches: `scripts/check-runblocking-allowlist.py`, `scripts/runblocking-allowlist.txt`, `scripts/test-check-runblocking-allowlist.py` or equivalent, `docs/BENCHMARKS.md`, focused JVM/perf tests for `determineLocalComposing`.
  Acceptance: Every production `runBlocking` entry declares a category such as `main-thread-keystroke`, `sync-api`, or `cache-fill`, the gate rejects new hot-path entries without a budget/rationale, and a local test or benchmark fixture proves the per-keystroke composing bridge stays CPU-only within the documented threshold.
  Complexity: M

- [ ] P2 — Add environment and privacy-redaction fields to crash reports
  Why: Crash reports need the same version/install/device context as bug reports, plus explicit redaction prompts because keyboard logs can contain private typed content.
  Evidence: `.github/ISSUE_TEMPLATE/bug_report.yml`; `.github/ISSUE_TEMPLATE/crash_report.yml`; `docs/SECURITY.md`; `docs/PRIVACY_AND_AI.md`.
  Touches: `.github/ISSUE_TEMPLATE/crash_report.yml`, `docs/SECURITY.md` only if reporting guidance changes.
  Acceptance: Crash reports require SwiftFloris version, install source, Android version, device model, reproducibility, crash-log source, and a checkbox confirming typed text, clipboard content, personal dictionaries, private APK paths, and unrelated device logs were removed.
  Complexity: S

### P3

- [ ] P3 — Replace stale `RESEARCH_FEATURE_PLAN.md` source references
  Why: Source comments still point at a retired root research-plan filename even though current research lives in `RESEARCH.md` and historical plans live under `docs/research-feature-plan-*`.
  Evidence: `FlorisApplication.kt`; `PrivacyAuditScreen.kt`; `TypingStatsScreen.kt`; `AboutScreen.kt`; `ShiftStateMachine.kt`; `HeuristicSmartComposeProvider.kt`; `FlorisEmojiCompatReflectionGuardTest.kt`; `scripts/check-live-doc-integrity.py`.
  Touches: Source/test comments only, `scripts/check-live-doc-integrity.py` if adding a stale-reference guard.
  Acceptance: `rg "RESEARCH_FEATURE_PLAN\\.md" app lib scripts docs README.md ROADMAP.md` returns no stale source references, comments either name the current feature contract or are removed, behavior and tests are unchanged, and no new markdown files are created.
  Complexity: S

## Research-Driven Additions

### P1

- [ ] P1 — Restore runtime addon bundle-size enforcement
  Why: The docs and addon contract promise a 64 MiB cap, but runtime enrollment currently reports `bundleSizeBytes = 0L`, allowing oversized third-party addons if they bypass `scripts/verify-addon-apk.sh`.
  Evidence: `AddonEnumerator.kt`; `AddonContract.kt`; `docs/addons/apk-validation.md`; `scripts/verify-addon-apk.sh`; AnySoftKeyboard/Fcitx5 addon ecosystems.
  Touches: `AddonEnumerator`, `AddonManifest`, `AddonProvenanceReport`, addon settings UI, enumerator tests, addon validation docs if semantics change.
  Acceptance: Installed addon packages or declared addon assets report a real bounded size, over-cap packages reject with a visible provenance reason, at-cap packages pass, sample addon validation remains green, and tests cover over-cap/at-cap/no-source cases.
  Complexity: M

- [ ] P1 — Add a Kotlin build-cache CVE guard until stable 2.4.20+ KSP upgrade
  Why: Kotlin `2.4.0` is affected by CVE-2026-53914; release builds disable Gradle caching today, but a future property or command change could re-enable the vulnerable cache path before a stable Kotlin/KSP upgrade exists.
  Evidence: NVD CVE-2026-53914; `gradle/libs.versions.toml`; `scripts/release-evidence.ps1`; `scripts/verify-reproducible-apk.sh`; `docs/REPRODUCIBLE_BUILDS.md`.
  Touches: release-evidence scripts, reproducible-build verifier, Gradle/version-catalog metadata, security/reproducible-build docs, script tests.
  Acceptance: A local gate fails when Kotlin is `< 2.4.20` and Gradle build cache is enabled for release/reproducible builds, records the mitigation in release evidence, and documents removal criteria after stable Kotlin `2.4.20+` plus compatible KSP pass the full local suite.
  Complexity: M

### P2

- [ ] P2 — Correct in-app crash dialog report identity and redaction copy
  Why: The generated crash report still starts with `FlorisBoard` even though public issue templates and docs use SwiftFloris; crash copy should also remind users to redact typed text before clipboard/GitHub handoff.
  Evidence: `CrashDialogActivity.kt`; `strings.xml`; `.github/ISSUE_TEMPLATE/crash_report.yml`; `README.md`.
  Touches: `CrashDialogActivity.kt`, crash dialog strings/layout if needed, crash utility tests or snapshot coverage.
  Acceptance: Generated crash reports use `SwiftFloris` plus version code, include install/build context where locally available, link/open the crash-report template directly, show a redaction reminder before copy, and have regression coverage preventing a `FlorisBoard` product-name relapse in generated report text.
  Complexity: S

### P3

- [ ] P3 — Fix PR template debug package ID drift
  Why: Contributor docs say debug APKs install as `dev.patrickgold.florisboard.debug`, but the current app ID is `io.github.sysadmindoc.swiftfloris.debug`.
  Evidence: `.github/PULL_REQUEST_TEMPLATE.md`; `app/build.gradle.kts`; `README.md`.
  Touches: `.github/PULL_REQUEST_TEMPLATE.md`, optional doc-integrity guard if package-ID checks are expanded.
  Acceptance: The PR template names the actual debug package ID, keeps the upstream namespace explanation separate from install identity, and any existing package-ID doc check continues to pass.
  Complexity: S
