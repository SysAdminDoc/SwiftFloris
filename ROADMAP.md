# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

### P3

- [ ] P3 — **Minimal visual layout editor emitting existing layout JSON**
  Why: custom layout editing is a top upstream ask (florisboard #196, +22) no
  FOSS keyboard serves well; SwiftFloris already consumes layout JSON and
  imports KLC/keylayout/LDML, so a constrained row/key editor that round-trips
  the existing format is a leapfrog with bounded scope.
  Evidence: https://github.com/florisboard/florisboard/issues/196 ; existing
  import pipeline (`ime/hardware/`, layout assets).
  Touches: new Settings screen under keyboard/layout preferences; layout JSON
  serializer round-trip; preview via existing keyboard preview field.
  Acceptance: user can clone a bundled layout, swap/add/remove keys in rows,
  preview, save as a local layout selectable in subtype editor; invalid edits
  are rejected with visible validation.
  Complexity: L

## Research-Driven Additions

### P0

- [ ] P0 — **Make public release-channel freshness a blocking gate**
  Why: README/fastlane/`gradle.properties` currently claim `v1.9.52`, but the
  latest GitHub Release is `v1.9.48`; Obtainium and reviewers rely on GitHub
  Releases as the canonical install channel.
  Evidence: `gradle.properties`; `README.md`; `scripts/check-release-front-door.sh`;
  GitHub Releases latest `v1.9.48`.
  Touches: `scripts/check-release-front-door.sh`; `.github/workflows/android.yml`;
  `.github/workflows/release.yml`; README release wording if needed.
  Acceptance: normal CI fails when public README/fastlane surfaces claim a
  version that is not the latest GitHub Release, while release workflow preflight
  can still run before creating the tag.
  Complexity: S

- [ ] P0 — **Release and close the low-memory SymSpell crash follow-through**
  Why: issue #9 reports typing OOM on released `v1.9.48`; current source appears
  to contain the bounded SymSpell fix, but users cannot receive it until the
  public release channel advances.
  Evidence: https://github.com/SysAdminDoc/SwiftFloris/issues/9;
  `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/SymSpellIndex.kt`;
  `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/SymSpellIndexTest.kt`.
  Touches: release workflow artifacts; fastlane changelog; issue response;
  SymSpell regression tests if any gap remains.
  Acceptance: latest GitHub Release is newer than `v1.9.48`, issue #9 is
  answered with the fixed version, and `:app:testDebugUnitTest` covers partial
  index build without crashing.
  Complexity: S

### P1

- [ ] P1 — **Refresh stale trust docs and add a link/version drift gate**
  Why: public trust docs still reference `v1.8.231`, deleted
  `PROJECT_CONTEXT.md`, and old F-Droid/reproducible-build status; stale trust
  copy is worse than missing trust copy.
  Evidence: `docs/THREAT_MODEL.md`; `docs/PRIVACY_AND_AI.md`;
  `docs/outreach/2026-05-17-swiftkey-migration/alternativeto-entry.md`;
  F-Droid reproducible-build docs.
  Touches: `docs/THREAT_MODEL.md`; `docs/PRIVACY_AND_AI.md`; outreach docs;
  a repo-hygiene or release-front-door link/version check.
  Acceptance: trust docs match current release posture, no tracked Markdown link
  points to missing `PROJECT_CONTEXT.md`, and CI fails on future stale trust-doc
  version/link drift.
  Complexity: M

- [ ] P1 — **Expose addon provenance export in Settings → Addons**
  Why: `AddonProvenanceReport` already renders stable text/JSON, but Settings
  does not let users copy the complete no-network/signing/dataset proof before
  trusting or auditing an addon.
  Evidence: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonProvenanceReport.kt`;
  `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/addons/AddonsSettingsScreen.kt`;
  F-Droid reproducible-build trust model.
  Touches: `AddonsSettingsScreen.kt`; addon strings; Addons Roborazzi baseline;
  addon provenance tests.
  Acceptance: accepted and trust-pending addons show a copy/export action whose
  JSON includes package, type, signer SHA-256, no-network attestation, license,
  and dictionary dataset provenance when present.
  Complexity: M

- [ ] P1 — **Ship addon authoring docs and fixture APK project**
  Why: `scripts/verify-addon-apk.sh` references `docs/addons/apk-validation.md`,
  but that contract doc is absent; a plugin ecosystem cannot grow from source
  comments alone.
  Evidence: `scripts/verify-addon-apk.sh`; `AddonContract.kt`;
  `DictionaryPackDescriptor.kt`; AnySoftKeyboard language packs; fcitx5-android
  plugin architecture.
  Touches: addon docs; minimal fixture addon module or sample project;
  `scripts/verify-addon-apk.sh`; addon contract tests.
  Acceptance: a contributor can build a sample dictionary-pack APK, run
  `scripts/verify-addon-apk.sh`, install it, and see it enroll or reject with
  documented reasons in Settings → Addons.
  Complexity: M

### P2

- [ ] P2 — **Add a keyboard layout JSON validation and preview gate**
  Why: the active visual layout editor needs machine-checkable layout contracts
  before users can create local layouts; current layout assets/importers are
  broad but no standalone validator is visible.
  Evidence: active P3 visual layout editor item; `app/src/main/assets/ime/keyboard/`;
  `ime/hardware/KeymanPackageParser.kt`; FlorisBoard layout editor issue #196.
  Touches: layout parser/serializer utilities; new validation script or Gradle
  task; keyboard preview/Roborazzi fixtures.
  Acceptance: CI validates bundled and local-layout fixtures for duplicate IDs,
  missing refs, row width drift, invalid key actions, and at least one rendered
  preview per representative layout class.
  Complexity: M

- [ ] P2 — **Expand EditorInfo replay tests for sensitive fields and IME actions**
  Why: competitor issue trackers repeatedly show password-suggestion leaks,
  Bluetooth PIN enter-key failures, Flutter undo/redo shortcuts, Teams fields
  with no keyboard, and aggressive autocapitalization; SwiftFloris should catch
  these before device reports.
  Evidence: https://github.com/futo-org/android-keyboard/issues/2109;
  https://github.com/futo-org/android-keyboard/issues/2106;
  https://github.com/HeliBorg/HeliBoard/issues/2565;
  https://github.com/HeliBorg/HeliBoard/issues/2561;
  https://github.com/florisboard/florisboard/issues/3292; local
  `KeyboardManager.kt` and sensitive-field guards.
  Touches: `KeyboardManager.kt`; `FlorisImeService.kt`; `PasskeyInjector.kt`;
  focused JVM/editor replay fixtures.
  Acceptance: tests cover password/PIN/no-personalized-learning fields, IME
  action enter handling, hardware undo/redo passthrough, and autocapitalization
  opt-outs without leaking suggestions or corrupting editor state.
  Complexity: M

- [ ] P2 — **Add one-tap Privacy posture proof export**
  Why: Privacy posture checks no-network status and links source/release proof,
  but users and reviewers cannot export the current installed proof bundle from
  the app.
  Evidence: `PrivacyPostureScreen.kt`; `SigningFingerprint.kt`;
  Citizen Lab keyboard privacy research; F-Droid reproducible-build docs.
  Touches: `PrivacyPostureScreen.kt`; `SigningFingerprint.kt`; privacy strings;
  local JSON/plain-text export tests.
  Acceptance: Privacy posture can copy a stable proof bundle containing app
  version/code, package id, signer fingerprint, `INTERNET` permission absence,
  release/source URLs, enabled privacy toggles, and addon count/status.
  Complexity: S
