# SwiftFloris Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions

### P0

- [ ] P0: Fail closed across every Android backup transport
  Why: Android 16 QPR2 treats a missing backup-mode section as fully enabled for eligible app data, while SwiftFloris defines cloud and device transfer only and enables cloud backup without an encryption-capability requirement.
  Evidence: https://developer.android.com/identity/data/autobackup; https://developer.android.com/privacy-and-security/risks/backup-best-practices; app/src/main/AndroidManifest.xml:111-113; app/src/main/res/xml/data_extraction_rules.xml; app/src/main/res/xml/backup_rules.xml; app/src/main/res/xml-v31/backup_rules.xml.
  Touches: AndroidManifest.xml, all backup-rule resource variants, BackupDataInventory.kt, backup parity gates, privacy copy, API 26, 28, 31, and 36.1 transport tests.
  Acceptance: Cloud, device-to-device, and cross-platform transports each export only inventory entries explicitly classified as portable, or export nothing; no password-derived data, learned text, clipboard data, authentication material, encryption key, or adaptive-touch state crosses a transport unless its classification explicitly changes; cloud backup is disabled when client-side encryption is unavailable on supported APIs, while API 26 and 27 either disable cloud export or limit it to a documented non-sensitive subset; no placeholder iOS identity is declared; tests parse every selected resource variant and fail for a missing mode or unclassified persisted store; Settings and README distinguish no app-owned cloud service from any retained Android-managed backup.
  Complexity: L

### P1

- [ ] P1: Route users and Android 16 directly to keyboard language setup
  Why: Discussion #21 shows real confusion even though the requested Portuguese data is already bundled.
  Evidence: https://github.com/SysAdminDoc/SwiftFloris/discussions/21; app/src/main/assets/ime/dict/pt.fldic; app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/LocalizationScreen.kt:85-100; app/src/main/res/values/strings.xml:375-378; https://developer.android.com/reference/android/view/inputmethod/InputMethodInfo.
  Touches: app/src/main/res/xml/method.xml, LocalizationScreen.kt, LanguagePackManagerScreen.kt, Routes.kt, strings.xml, navigation and Roborazzi tests.
  Acceptance: Android 16's IME language-settings action opens the subtype list; the language-pack screen offers Add keyboard language and distinguishes built-in resources from imported extensions; a user can enable Portuguese without importing a file; navigation, RTL, and 200 percent font-scale captures pass.
  Complexity: M

- [ ] P1: Respect the Android 16 writing-tools opt-out before rewrite dispatch
  Why: Editors can explicitly forbid generative text replacement, but the rewrite router cannot observe that decision.
  Evidence: https://developer.android.com/reference/android/view/inputmethod/EditorInfo; app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/RewriteRouter.kt:60-79; RewriteProvider.kt.
  Touches: EditorInfo wrapper or snapshot, RewriteRequest, RewriteRouter, rewrite route wiring, RewriteRouter tests.
  Acceptance: On API 36 and newer, isWritingToolsEnabled false suppresses rewrite before cache lookup or provider invocation; API 35 and older retain current behavior; ordinary prediction and spell correction are unchanged; tests assert provider call counts.
  Complexity: S

- [ ] P1: Apply Advanced Protection state changes without an IME restart
  Why: The policy reads snapshots even though Android can notify the process when the user toggles Advanced Protection.
  Evidence: https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager; https://developer.android.com/privacy-and-security/advanced-protection-mode; app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/AdvancedProtectionPolicy.kt:75-90.
  Touches: AdvancedProtectionPolicy.kt, FlorisApplication.kt or FlorisImeService.kt, ClipboardManager.kt, AddonEnumerator.kt, lifecycle and policy tests.
  Acceptance: API 36 registers one callback at process or service initialization and unregisters it on teardown; enabling protection immediately forces private typing, stops new clipboard persistence, and blocks new addon enrollment; disabling it re-evaluates saved preferences without process restart; lifecycle tests prove no duplicate registration.
  Complexity: M

- [ ] P1: Produce and verify a genuinely unsigned F-Droid build
  Why: The recipe names an unsigned output while Gradle always applies release or debug signing, so reproducible binary comparison cannot start from the declared artifact.
  Evidence: fdroid/io.github.sysadmindoc.swiftfloris.yml:24-41; app/build.gradle.kts:116-133 and :233-243; https://f-droid.org/en/docs/Reproducible_Builds/; https://f-droid.org/en/docs/Build_Metadata_Reference/.
  Touches: app/build.gradle.kts, fdroid/io.github.sysadmindoc.swiftfloris.yml, utils/repr_build, scripts/check-public-doc-version-pins.py, metadata self-tests.
  Acceptance: A dedicated F-Droid task or variant emits the exact YAML output path; apksigner confirms it is unsigned; local fdroid build and binary comparison pass; AllowedAPKSigningKeys contains the verified upstream release certificate; the false KnownVuln entry is absent; a fixture signed with the debug key fails.
  Complexity: M

- [ ] P1: Gate the final app APK for 16 KB native compatibility
  Why: SQLCipher ships native libraries, while only addon APKs currently receive alignment checks.
  Evidence: https://developer.android.com/guide/practices/page-sizes; scripts/verify-addon-apk.sh:210-237; gradle/libs.versions.toml; scripts/release-evidence.ps1.
  Touches: new or shared APK-alignment verifier, release-evidence.ps1, app release tasks, verifier fixtures, 16 KB emulator smoke harness.
  Acceptance: Every release ABI passes ZIP page alignment and ELF LOAD-segment alignment checks against the final APK; deliberately misaligned fixtures fail; the release APK installs, launches Settings, and starts the IME on a 16 KB emulator.
  Complexity: M

- [ ] P1: Apply installed locale hints with explicit user choices first
  Why: Host apps can provide a conversation locale and Android 13 per-app locale, but SwiftFloris ignores both outside debug output.
  Evidence: https://developer.android.com/reference/android/view/inputmethod/EditorInfo; https://developer.android.com/reference/android/app/LocaleManager; https://github.com/futo-org/android-keyboard/pull/1892; app/src/main/kotlin/dev/patrickgold/florisboard/lib/util/DebugSummarizeUtils.kt:47-49; app/src/main/kotlin/dev/patrickgold/florisboard/ime/core/SubtypeManager.kt:191-238.
  Touches: EditorInfo snapshot, SubtypeManager.kt, PerAppSubtypeMemory, localization preferences and UI, locale-resolution tests.
  Acceptance: A current manual choice and saved per-app subtype take precedence; otherwise one unambiguous installed match from hintLocales, then LocaleManager application locales on API 33 and newer, may select a subtype; ambiguous or unavailable hints do nothing; no asset is downloaded; users can disable automatic hint matching; precedence and fallback tests pass.
  Complexity: M

- [ ] P1: Bring adaptive-touch state under the persisted-data contract
  Why: AdaptiveTouchModel writes `adaptive_touch_model.xml`, but the canonical inventory, backup omission UI, parity tests, and public privacy description do not classify or accurately describe that store.
  Evidence: app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/AdaptiveTouchModel.kt:36-47 and :83-92; app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupDataInventory.kt:89-101; docs/PRIVACY_AND_AI.md:97-106.
  Touches: AdaptiveTouchModel.kt, BackupDataInventory.kt, Android backup rules, manual archive sections or omission UI, TypingStatsScreen.kt, privacy documentation, persisted-store discovery and parity tests.
  Acceptance: The inventory classifies `adaptive_touch_model.xml` as portable or sensitive-excluded; Android and manual backup behavior matches that decision; an excluded store is named in omission UI, while an included store has an archive version, migration, and reset path; restored state cannot contain samples from password, incognito, or host-declared no-learning sessions; public text states that only ordinary learning sessions update the model; a fixture introducing an unregistered SharedPreferences file fails the persisted-store gate.
  Complexity: M

- [ ] P1: Verify manual backup publication before reporting success
  Why: The interactive path treats a completed SAF write as a valid backup, while the scheduled path reads the destination back and compares its digest before publication.
  Evidence: app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt:392-405; app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/ScheduledBackupSaf.kt:74-80 and :164-213; https://bitwarden.com/help/encrypted-export/; https://www.reddit.com/r/Swiftkey/comments/1vp13la/restore_keyboard_layout_languages_settings_and/; https://www.reddit.com/r/Swiftkey/comments/1tylh00/migration_to_onedrive_failed_lost_predictions/.
  Touches: BackupScreen.kt, BackupArchiveBuilder.kt, ScheduledBackupSaf.kt, PortableBackupEnvelope.kt, restore inspection, backup receipt UI and strings, SAF provider fixtures.
  Acceptance: One shared verifier reads the destination document back, enforces size limits, compares SHA-256, authenticates and decrypts encrypted envelopes, and validates metadata plus selected archive sections through the same parser limits used by restore; interactive success appears only after verification; truncation, corruption, wrong passphrases, and provider readback failures produce actionable errors without discarding the source workspace or a prior verified archive; the receipt shows format version, encryption state, selected sections, named omissions, byte size, and checksum; fake-provider tests cover short writes and altered reads.
  Complexity: M

### P2

- [ ] P2: Preserve reproducible-build output when the container build fails
  Why: The current script copies output only after success and removes the failed container before its evidence is collected.
  Evidence: utils/repr_build/run.sh:90-108.
  Touches: utils/repr_build/run.sh, reproducible-build shell tests, output manifest and log handling.
  Acceptance: Success and failure both copy available logs, manifests, and partial outputs before container removal; the script returns the original assembly exit code; cleanup runs once; a fake failing container test proves evidence survives.
  Complexity: S

- [ ] P2: Balance the candidate-row benchmark trace on every exit
  Why: The current comment accepts an open Perfetto section if composition throws.
  Evidence: app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/CandidatesRow.kt:97-109 and :217-222.
  Touches: CandidatesRow.kt, trace helper or trace sink, candidate-row tests, benchmark parser if marker shape changes.
  Acceptance: No hand-started candidate-row section remains open after normal composition, recomposition cancellation, or a thrown test composable; the benchmark still records a bounded candidate-row duration; fake-sink tests assert begin and end counts.
  Complexity: S

- [ ] P2: Enforce coverage floors for trust-critical packages
  Why: Kover is enabled without verification rules, so a green suite does not guarantee execution of privacy, backup, migration, addon, editor, or dictionary branches.
  Evidence: app/build.gradle.kts:331-333; https://kotlin.github.io/kotlinx-kover/gradle-plugin/; recurring migration, privacy, and editor fixes in CHANGELOG.md.
  Touches: app/build.gradle.kts, Kover filters and verification rules, release-evidence.ps1, coverage documentation.
  Acceptance: Package or class-level line and branch floors cover privacy policy, backup and restore, migrations, addon enrollment, editor transactions, and dictionary persistence; generated and UI-only code is excluded with written reasons; a self-test removes coverage from a fixture and proves verification fails.
  Complexity: M

- [ ] P2: Reject generated Python bytecode from the repository
  Why: Two .pyc files are tracked and the ignore and hygiene rules do not prevent recurrence.
  Evidence: scripts/__pycache__/check-locale-coverage.cpython-313.pyc; scripts/__pycache__/verify-targetsdk37-shadow.cpython-313.pyc; .gitignore; scripts/check-repo-hygiene.sh.
  Touches: tracked bytecode files, .gitignore, scripts/check-repo-hygiene.sh, hygiene self-test.
  Acceptance: No tracked .pyc or __pycache__ path remains; standard ignore rules cover both; the hygiene gate rejects a staged fixture anywhere in the tree.
  Complexity: S

- [ ] P2: Centralize and localize input-provider metadata
  Why: The subtype editor owns a two-entry English map and cannot describe future providers consistently.
  Evidence: app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/SubtypeEditorScreen.kt:403-415.
  Touches: NLP provider registry or extension metadata, SubtypeEditorScreen.kt, strings.xml and locale resources, provider registry tests.
  Acceptance: Every registered provider supplies a stable ID, localized label, capability summary, and availability state from one registry; the subtype editor contains no provider-name literals; unknown persisted IDs show an explicit unavailable state; an exhaustive test fails when a provider lacks metadata.
  Complexity: S

- [ ] P2: Let users choose the subtype cycle order and subset
  Why: FlorisBoard and HeliBoard users independently request quick switching that skips rarely used languages, while SwiftFloris cycles the full subtype list.
  Evidence: https://github.com/florisboard/florisboard/issues/3328; https://github.com/HeliBorg/HeliBoard/issues/2744; app/src/main/kotlin/dev/patrickgold/florisboard/ime/core/SubtypeManager.kt:386-413.
  Touches: localization preferences and UI, SubtypeManager.kt, backup schema, preference migration, switching tests.
  Acceptance: Users can reorder subtypes and exclude entries from previous or next cycling without deleting them; the subtype picker still shows all configured entries; deleting or disabling the active entry selects a deterministic fallback; backup, restore, and migration retain the configured cycle.
  Complexity: M

- [ ] P2: Add an explicit clean-link clipboard action
  Why: Tracking-parameter removal fits the offline privacy model, but silent clipboard rewriting would be unsafe for signed or authenticated URLs.
  Evidence: https://github.com/futo-org/android-keyboard/pull/1833; https://support.brave.com/hc/en-us/articles/9982188779405-What-does-Copy-clean-link-mean; app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard.
  Touches: clipboard text actions, URL sanitizer and pinned rules asset, preview and undo UI, strings, sanitizer and Roborazzi tests.
  Acceptance: A user-invoked action previews original and cleaned URLs before copy or paste; path, fragment, parameter order, and repeated non-tracking parameters are preserved; signed, expiring, authentication, and ambiguous URLs remain unchanged; no automatic rewrite occurs; undo restores the original.
  Complexity: M

- [ ] P2: Guard public toolchain and accessibility facts against drift
  Why: Build docs disagree on JDK and Build Tools versions, and accessibility docs attribute Android's 48 dp guidance to a WCAG criterion that specifies 44 CSS pixels.
  Evidence: gradle/tools.versions.toml; README.md:268-305; CONTRIBUTING.md; docs/REPRODUCIBLE_BUILDS.md; docs/ACCESSIBILITY.md; https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html.
  Touches: CONTRIBUTING.md, docs/REPRODUCIBLE_BUILDS.md, docs/ACCESSIBILITY.md, scripts/check-public-doc-version-pins.py, checker self-tests.
  Acceptance: Docs distinguish host JDK 21 from reproducible and F-Droid JDK 17, derive or verify Build Tools 37.0.0 from the machine-readable pin, cite Android 48 dp and WCAG 44 CSS pixels separately, and fail a fixture containing each stale value or attribution.
  Research note (2026-08-23): The same gate must derive SQLCipher version and native-library claims from the dependency catalog and built APK. docs/THREAT_MODEL.md still says 4.17.0 and zero native code while SQLCipher 4.18.0 contributes native libraries.
  Complexity: M

### P3

- [ ] P3: Make scheduled-backup retention quantity-aware
  Why: The current option formats every count through one English plural form.
  Evidence: app/src/main/res/values/strings.xml:1838; https://developer.android.com/guide/topics/resources/string-resource#Plurals.
  Touches: strings.xml and translated quantity resources, scheduled-backup settings UI, resource-format tests.
  Acceptance: Retention uses a plurals resource with the count passed to resource selection and formatting; one and many render correctly in English and pseudolocales; the locale coverage and resource-format gates pass.
  Complexity: S
