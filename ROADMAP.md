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

- [ ] P1 — Upgrade AboutLibraries to 15.0.3 on compileSdk 37
  Why: OSS license disclosure is a trust surface, compileSdk 37 is now active, and the current dependency remains on 14.2.0 while 15.0.3 is published.
  Evidence: `gradle.properties`; `gradle/libs.versions.toml`; Gradle Plugin Portal AboutLibraries metadata; stale blocked entry in `Roadmap_Blocked.md`.
  Touches: `gradle/libs.versions.toml`, About/third-party license settings screens, license-generation outputs/tests, `Roadmap_Blocked.md`.
  Acceptance: AboutLibraries plugin/runtime resolve at 15.0.3, third-party license UI still renders, relevant unit/Roborazzi/license checks pass, and the stale blocked duplicate is removed or marked moved during implementation.
  Complexity: M

- [ ] P1 — Add Android 17 CJKV selected-candidate accessibility signaling
  Why: API 37 exposes `TextAttribute.Builder.setTextSuggestionSelected`, and SwiftFloris now compiles against 37 but does not mark selected CJK conversion candidates for accessibility-aware editors.
  Evidence: Android `TextAttribute.Builder` API docs; `CjkInputProvider.kt`; `CjkBridgePrototype.kt`; `EditorInputConnectionBatch.kt`; `HostileEditorCandidateReplayTest.kt`.
  Touches: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/`, `app/src/main/kotlin/dev/patrickgold/florisboard/ime/cjk/`, candidate commit paths, editor replay tests, `Roadmap_Blocked.md`.
  Acceptance: On API 37+ CJK candidate commit/composition paths attach `TextAttribute` with selected-candidate metadata, older APIs keep identical calls, JVM replay tests cover both paths, and the stale blocked duplicate is removed or moved during implementation.
  Complexity: M

- [ ] P1 — Add addon sample APK validation to release evidence
  Why: The repo documents a buildable dictionary-pack sample and validator, but the release evidence bundle does not prove addon packaging still passes the contract.
  Evidence: `settings.gradle.kts`; `docs/addons/apk-validation.md`; `scripts/verify-addon-apk.sh`; `scripts/release-evidence.ps1`.
  Touches: `scripts/release-evidence.ps1`, `scripts/verify-addon-apk.sh`, `addons/dictionary-pack-sample/`, README/release verification docs.
  Acceptance: Release evidence builds `:addons:dictionary-pack-sample:assembleRelease`, runs `scripts/verify-addon-apk.sh` on the produced APK, stores the output in the evidence directory, and fails if the sample violates banned-permission, receiver, size, signing, or alignment checks.
  Complexity: M

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
