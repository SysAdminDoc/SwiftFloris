# SwiftFloris Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions (2026-08-10)

### P1

### P2

### P3

- [ ] P3 — Add a reveal affordance and manual marking for sensitive clipboard entries
  Why: sensitive clips render as a fixed asterisk string with no way to confirm what was captured, and classification is fully automatic (two regexes plus the API-33 flag) with no way for a user to mark or unmark an entry — so a false positive is unrecoverable and a false negative is unfixable.
  Evidence: `ime/clipboard/provider/ClipboardDatabase.kt:233-238`; `app/src/main/res/values/strings_dont_translate.xml:35`; classifier `ime/clipboard/ClipboardSensitiveTextClassifier.kt:19-30`; `ime/clipboard/ClipboardManager.kt:274-301`; no reveal/mask pref in `app/prefs/ClipboardPrefs.kt`; https://github.com/florisboard/florisboard/issues/3323
  Touches: `ime/clipboard/ClipboardInputLayout.kt`, `ime/clipboard/provider/ClipboardDatabase.kt`, `app/prefs/ClipboardPrefs.kt`, `app/src/main/res/values/strings.xml`, `docs/PRIVACY_AND_AI.md`
  Acceptance: a long-press action toggles an entry's sensitive flag, and a per-entry reveal shows the content transiently without persisting the unmasked form or changing the a11y label default; revealing is disabled while the incognito or lock-screen gates are active.
  Complexity: M

## Research-Driven Additions (2026-08-11)

### P1

- [ ] P1 — Give the merged manifest and addon APKs the same allowlist the enrolment gate uses
  Why: v1.9.59 inverted enrolment to a permitted-permission allowlist because `SEND_SMS`, Bluetooth, nearby-devices, storage and Android 17's `ACCESS_LOCAL_NETWORK` exfiltrate without `INTERNET`. The APK-level gates were not inverted: both the source and merged-manifest checks and the addon APK validator still test five permission names. A dependency AAR merging in any other exfil permission ships unnoticed, and a third-party addon author self-certifying with the script gets a PASS on an APK the IME rejects at enrolment.
  Evidence: `app/build.gradle.kts:309-315` (`bannedNetworkPermissions`, used by `verifyNoInternetPermission` at `:341` and `verifyNoInternetPermissionMerged<Variant>` at `:395`); `scripts/verify-addon-apk.sh:42-46`; contrast with `ime/security/NoNetworkPermissionPolicy.kt:73-81`; `scripts/check-trust-capabilities.py:98` reads only `app/src/main/AndroidManifest.xml`
  Touches: `app/build.gradle.kts`, `scripts/verify-addon-apk.sh`, `scripts/check-trust-capabilities.py`, `app/src/main/config/trust-capabilities.json`, `docs/PRIVACY_AND_AI.md`, `docs/THREAT_MODEL.md`, `docs/addons/apk-validation.md`
  Acceptance: the merged-manifest gate fails on any permission outside an explicit allowlist, not only the five network names; `verify-addon-apk.sh` applies the same allowlist as `NoNetworkPermissionPolicy` so script PASS and runtime enrolment agree; a fixture manifest declaring `android.permission.SEND_SMS` fails both.
  Complexity: M

- [ ] P1 — Widen the live-doc-integrity gate to the files that actually drift
  Why: the gate that exists to catch stale docs is configured to skip them. `EXCLUDED_FILES` holds the five planning docs, `EXCLUDED_PREFIXES` holds the two files containing every `.github/workflows/` reference so `FORBIDDEN_CANONICAL_REFS` can never fire, `collect_live_markdown()` iterates tracked paths so 23 of 27 `docs/*.md` are never scanned, and `check_blocked_roadmap_freshness()` looks for GitHub URLs and release-advance lines that `Roadmap_Blocked.md` does not contain — making it a no-op on the file it is named for. It currently reports `OK (11 files checked)` against 63 dangling `ROADMAP §N` references across 15 docs.
  Evidence: `scripts/check-live-doc-integrity.py:19-25`, `:27-30`, `:33-37`, `:169-175`, `:355-418`; `git ls-files docs/` returns 4 top-level docs of 27
  Touches: `scripts/check-live-doc-integrity.py`, `scripts/test-check-live-doc-integrity.py`, the `docs/*.md` files whose violations it then surfaces
  Acceptance: the gate scans every `docs/*.md` and the planning docs regardless of tracked status; a doc citing a nonexistent path, a `ROADMAP §N` ID that ROADMAP.md does not define, or a `.github/workflows/` file fails the run; `check_blocked_roadmap_freshness` asserts something `Roadmap_Blocked.md` actually contains and its self-test proves it can fail.
  Complexity: M

- [ ] P1 — Gate the F-Droid recipe against a resolvable ref
  Why: `fdroid/io.github.sysadmindoc.swiftfloris.yml:25` pins `commit: v1.9.59` and F-Droid resolves that ref literally. Nothing checks it, so the recipe treated as "prepared, awaiting a GitLab MR" would have failed on submission. Tagging is covered by the P0 runner item; this is the check that stops it recurring.
  Evidence: `fdroid/io.github.sysadmindoc.swiftfloris.yml:25`; `git tag | sort -V | tail` ends at v1.9.56; `Roadmap_Blocked.md:135-139`; `README.md:375-377`
  Touches: `scripts/check-release-front-door.sh`, `scripts/test-check-release-front-door.py`, `fdroid/io.github.sysadmindoc.swiftfloris.yml`
  Acceptance: a release gate fails when the F-Droid recipe's `commit:` value does not resolve to an existing tag, and its self-test proves the gate can fail.
  Complexity: S

- [ ] P1 — Wire or delete the smart-compose / MCP router stack
  Why: roughly 900 LOC of router, cache, context window, result filter and opt-in dispatcher is constructed only in tests. The shipping path calls the provider registry directly, bypassing the router's truncation, cache, filter **and `AddonInvocationAudit.record(...)`** — so the privacy audit log has readers and no writer, and a user reads an empty log as "no AI invocation occurred". Meanwhile the IME still binds every discovered third-party MCP daemon at startup for a dispatch path whose only caller is the uncalled `NlpAddonHub`. Whichever dispatcher gets wired first also decides whether the `McpPrefs` daemon/tool toggles ever gate anything: `OptInAddonDispatcher` calls `mcpClient.callTool` directly while its own KDoc calls itself the load-bearing privacy seam.
  Evidence: `ime/smartcompose/{SmartComposeRouter,SmartComposeCache,SmartComposeContextWindow,SmartComposeResultFilter,OptInAddonDispatcher,RewriteRouter,NlpAddonHub}.kt` — no `app/src/main` construction; shipping call at `ime/nlp/NlpManager.kt:383-390`; audit readers at `app/settings/privacy/PrivacyAuditDisplay.kt:35` and `PrivacyAuditExportPolicy.kt:37`; binding at `FlorisImeService.kt:428-437` → `ime/mcp/McpServiceLifecycle.kt:56-71`; `ime/smartcompose/OptInAddonDispatcher.kt:41-45,80-99`; `ime/mcp/McpDispatchRouter.kt:51-53` (permissive defaults); `README.md:337`
  Touches: `ime/nlp/NlpManager.kt`, `ime/smartcompose/*`, `ime/mcp/McpServiceLifecycle.kt`, `FlorisImeService.kt`, `app/settings/privacy/PrivacyAuditScreen.kt`, `README.md`
  Acceptance: either the shipping suggestion path goes through the router so every addon invocation is audited and every consent/disable toggle is consulted, or the stack is deleted, daemon binding stops until a real dispatch path exists, and the privacy-audit surface says plainly that no auditable invocation can occur. Either way a test asserts that an audited surface cannot be invoked without producing a record, and `McpDispatchRouter`'s permissive default lambdas are removed so a caller cannot silently get full consent.
  Complexity: L

- [ ] P1 — Stop substituting empty state for unreadable persisted data
  Why: a failed sticker-manifest read yields an empty manifest that is then written back, orphaning the user's entire imported sticker library and bypassing the quota — the same failure is handled correctly 90 lines earlier in the same file. Three sibling sites use `runCatching` with no terminal operator, so both the exception and `atomicReplace`'s `false` return are discarded; a mid-file read error installs a partially-loaded n-gram table which is then flushed back over the file, silently truncating learned data. And the clipboard store reduces every read failure to "unreadable", then quarantines the database and clears the passphrase — irreversible, on evidence that cannot tell a transient IO error from real corruption.
  Evidence: `ime/media/sticker/LocalStickerPackRepository.kt:205-206`, `:354-355`, write-back at `:275-281`/`:386-392`, correct handling at `:113`; `ime/nlp/CorrectionOutcomePriors.kt:170,264-291`; `ime/dictionary/PersonalBigramStore.kt:125,160,688` and `PersonalTrigramStore.kt:133,170,720` (correct check at `PersonalBigramStore.kt:549-552`); `ime/clipboard/provider/ClipboardHistoryStore.kt:94,100-110`
  Touches: `ime/media/sticker/LocalStickerPackRepository.kt`, `ime/nlp/CorrectionOutcomePriors.kt`, `ime/dictionary/PersonalBigramStore.kt`, `PersonalTrigramStore.kt`, `ime/clipboard/provider/ClipboardHistoryStore.kt`
  Acceptance: an unreadable persisted store fails the operation and surfaces the failure rather than substituting an empty value and overwriting the source; a partially-parsed n-gram file is discarded rather than installed; clipboard quarantine requires an exception class that actually indicates corruption; tests cover the transient-IO-error path for each.
  Complexity: M

- [ ] P1 — Fix the test that catches its own failure signal, and the hole it hides
  Why: the "lifecycle is single-shot" test calls `error("expected IllegalStateException")` inside a `try` whose `catch (e: IllegalStateException)` swallows it — `kotlin.error()` throws exactly that type, so the test passes unconditionally. The invariant it claims to prove does not hold: `startWithDaemons` returns early without setting `started = true` when the bridge is disabled. The same class also has real concurrency exposure — `started` is not `@Volatile`, and `replaceDaemons` is `@Synchronized` while `startWithDaemons`, `retryDaemon` and `stop` are not, so a Settings-thread rescan can repopulate the daemon registry after the IME thread has torn it down.
  Evidence: `app/src/test/.../ime/mcp/McpServiceLifecycleTest.kt:148-157`; `ime/mcp/McpServiceLifecycle.kt:50`, `:56-62`, `:79`, `:102`, `:109`; `app/settings/mcp/McpSettingsScreen.kt:159`; correct idiom at `app/src/test/.../AddonProvenanceReportTest.kt:90-95`
  Touches: `app/src/test/.../McpServiceLifecycleTest.kt`, `ime/mcp/McpServiceLifecycle.kt`
  Acceptance: the test uses `shouldThrow`/`throw AssertionError` and fails when production does not throw; `started` is set on every return path from `startWithDaemons` and the lifecycle's mutable state is guarded consistently; a test covers rescan-during-teardown.
  Complexity: S

- [ ] P1 — Remove the maintainer's device serial from the tracked benchmark baselines
  Why: all six committed baseline files embed `"serial": "R5CY34G070L"` alongside manufacturer, model and device. They are `git ls-files`-tracked and therefore public. A privacy-first keyboard publishing a hardware identifier in its own repo is an avoidable contradiction, and nothing in the benchmark pipeline needs the serial.
  Evidence: `docs/benchmark-results/baseline-2026-05-18-*.json` (6 files); `git ls-files docs/benchmark-results/`
  Touches: `docs/benchmark-results/*.json`, `tools/benchmark-*.ps1`, `scripts/check-benchmark-trends.py`, `scripts/check-repo-hygiene.sh`
  Acceptance: no tracked file contains a device serial; the capture scripts record a stable anonymised device key (manufacturer/model/SDK) instead; repo hygiene fails if a serial-shaped value reappears in `docs/`.
  Complexity: S

- [ ] P1 — Re-verify the blocked hardware section against the attached device
  Why: 11 items are gated on "`adb devices -l` reported no attached device or emulator". A device is attached (`R5CY34G070L`, SM-S938B, Android 16 / SDK 36) with the debug build installed, Chrome and a password manager present, plus API 26/35/36/36.1/37 system images and an API-37 AVD on disk. Nine of the eleven blockers have cleared; the remaining two need a Play install and an emulator profile, not hardware. Four items in the external-deliverables section are likewise blocked on premises the tree contradicts.
  Evidence: `Roadmap_Blocked.md:20`, `:169`, `:184`, `:197`, `:203`, `:210`, `:222`, `:229`, `:246`; `adb devices -l`; `app/src/androidTest/.../ImeEndToEndSmokeTest.kt:64` (the test "Expand instrumented coverage" asks for); `settings.gradle.kts:47` + `scripts/release-evidence.ps1:262-272` (the addon trust kit it says needs building); `ime/voice/VoiceModelCatalog.kt:31-32,46-68,150` (the size/licence review it says is outstanding); `ime/sync/SyncChannel.kt:22-44,51,76` (the transport it says is unselected); the Han pack ships 235,847 rows
  Touches: `Roadmap_Blocked.md`, `ROADMAP.md`
  Acceptance: every hardware item is re-tested against the attached device and either moved to `ROADMAP.md` or given a blocker that survives inspection; the addon-trust-kit and instrumented-coverage items are closed as already-shipped; the voice, sync and CJK blockers are rescoped to what is genuinely missing. Note: the VNI analysis at `Roadmap_Blocked.md:30-42` is exact and must not be redone. Never set the device's default IME without asking — it is the maintainer's personal phone.
  Complexity: M

- [ ] P1 — Declare the two missing `<input-method>` attributes
  Why: `onPrepareStylusHandwriting`/`onStartStylusHandwriting`/`onStylusHandwritingMotionEvent`/`onFinishStylusHandwriting` are fully implemented and a shipped Settings toggle gates them, but `android:supportsStylusHandwriting` defaults to `false` in AOSP, so the platform never starts a handwriting session and the whole path is unreachable. `android:supportsInlineSuggestionsWithTouchExploration` also defaults to `false`, which suppresses inline autofill suggestions for every TalkBack user — on a keyboard that ships inline autofill and advertises TalkBack support.
  Evidence: `app/src/main/res/xml/method.xml` (20 lines, declares neither); `FlorisImeService.kt:601-680`; `app/prefs/KeyboardPrefs.kt:145`; parsing and defaults in https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/view/inputmethod/InputMethodInfo.java
  Touches: `app/src/main/res/xml/method.xml`, `app/src/test/.../` (a manifest-contract test), `docs/ACCESSIBILITY.md`, `docs/INLINE_AUTOFILL.md`
  Acceptance: `method.xml` declares both attributes; a test asserts their presence so neither can be dropped; on the attached device, TalkBack + a password manager shows inline suggestion pills, and stylus handwriting reaches `onStartStylusHandwriting` (which still declines, correctly, with no recognizer bound).
  Complexity: S

- [ ] P1 — Stop rendering empty states while data is still loading
  Why: three screens show "there's nothing here" before their flow emits. The theme screen's copy tells a first-run user to reinstall the app; the addons screen renders its progress card and two "nothing installed" empty states simultaneously during the first scan because `scanInProgress` suppresses only the action button.
  Evidence: `app/settings/theme/ThemeManagerScreen.kt:111-118` (+ `strings.xml:664-665`); `app/ext/ExtensionListScreen.kt:164-178`; `app/settings/addons/AddonsSettingsScreen.kt:187`, `:232-239`, `:249-257`
  Touches: `app/settings/theme/ThemeManagerScreen.kt`, `app/ext/ExtensionListScreen.kt`, `app/settings/addons/AddonsSettingsScreen.kt`, `lib/compose/FlorisCards.kt`
  Acceptance: each screen distinguishes loading from empty and renders the progress card alone until its flow emits; the existing Roborazzi settings baselines gain a loading-state capture for at least the theme screen.
  Complexity: S

- [ ] P1 — Extend the contrast gate past its ten selectors and fix what it then finds
  Why: `ThemeContrastTest` enumerates 10 selectors; each shipped stylesheet defines 98. `key-hint` is not covered and fails WCAG AA in three shipped themes — `floris_night` `#a0a0a0` on `#424242` = 3.84:1, `swiftkey_pure_light` `#7A7E85` on `#FFFFFF` = 4.08:1, `floris_pure_night` `#ffffff73` over `#212121` = 4.37:1. The theme editor has no contrast validation at all, so a user can save a theme with identical foreground and background, and `docs/ACCESSIBILITY.md:151-155` claims both a 4.5:1 floor and a per-element editor warning.
  Evidence: `app/src/test/.../ime/theme/ThemeContrastTest.kt:328-343`; `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/{floris_night,swiftkey_pure_light,floris_pure_night}.json` (`--on-surface-variant` over `--surface`); `app/settings/theme/EditPropertyDialog.kt`, `ThemeEditorScreen.kt` (no ratio check); `docs/ACCESSIBILITY.md:151-155`
  Touches: `app/src/test/.../ThemeContrastTest.kt`, the shipped stylesheets, `app/settings/theme/EditPropertyDialog.kt`, `docs/ACCESSIBILITY.md`
  Acceptance: the test derives its cases from every text-bearing selector present in a stylesheet rather than a literal list, so a new selector is covered without editing the test; all shipped themes meet 4.5:1 on text and 3:1 on non-text UI, with any deliberate exemption named in the test rather than absent from it; the theme editor warns inline when a chosen pair falls below the floor.
  Complexity: M

- [ ] P1 — Correct the four public docs that contradict the code
  Why: these are tracked, public files that assert security and accessibility properties the tree does not have. `PRIVACY_AND_AI.md` says the dictionary key is held in Android Keystore when it is a `SecureRandom` passphrase in SharedPreferences AEAD-wrapped by a Keystore key (`SECURITY.md` says this correctly — the two disagree). `TASKER_INTEGRATION.md` claims a signature permission on a receiver that has none, and its four `adb` examples are all rejected at runtime. `ACCESSIBILITY.md` claims a Compose API that does not exist, a settings key never read, an `announceForAccessibility` absence that is false, and an editor contrast warning that does not exist. `THREAT_MODEL.md` claims a merged-manifest guarantee its own checklist command does not verify.
  Evidence: `docs/PRIVACY_AND_AI.md:236-237` vs `ime/dictionary/FlorisUserDictionaryEncryption.kt:117-136` + `ime/security/TinkStringPreferenceCrypto.kt:186-195`; `docs/TASKER_INTEGRATION.md:6-8,11-23,39-45,49-77` vs `AndroidManifest.xml:265-272` + `ime/tasker/TaskerActionReceiver.kt:29-46`; `docs/ACCESSIBILITY.md:20-21,110-113,151-155` vs `ime/keyboard/KeyboardManager.kt:602-605` + `lib/compose/ReducedMotion.kt:31-42`; `docs/THREAT_MODEL.md:14-16,263` vs `app/build.gradle.kts:341-347` and `:395`
  Touches: `docs/PRIVACY_AND_AI.md`, `docs/TASKER_INTEGRATION.md`, `docs/ACCESSIBILITY.md`, `docs/THREAT_MODEL.md`, `docs/SECURITY.md`
  Acceptance: every corrected claim names the file and symbol that implements it; the Tasker doc documents the real gates (default-off preference plus HMAC payload signature) and replaces the non-working `adb` examples; no doc describes a Compose or platform API that does not exist. Depends on the doc-integrity gate widening so regressions are caught.
  Complexity: M

- [ ] P1 — Raise the shared touch-target minimum from 44 dp to 48 dp
  Why: the shared Settings widgets standardise on `44.dp` — the iOS figure — which is 4 dp under the Android/WCAG 2.5.5 floor, while two composables in the same file correctly use `minimumInteractiveComponentSize()`. Every text button, chip and card action in Settings is affected. `docs/ACCESSIBILITY.md:141` claims 48 dp on every interactive element, and `TouchTargetWcagTest` only measures keyboard row heights on one synthetic device, so nothing catches it.
  Evidence: `lib/compose/FlorisButtons.kt:69,106,143,206`, `FlorisButtonBar.kt:84,107`, `FlorisChip.kt:57`, `FlorisCards.kt:516`; correct usage at `FlorisButtons.kt:179,215`; `app/src/test/.../ime/window/TouchTargetWcagTest.kt:37-60`; `docs/ACCESSIBILITY.md:141`
  Touches: `lib/compose/FlorisButtons.kt`, `FlorisButtonBar.kt`, `FlorisChip.kt`, `FlorisCards.kt`, the Roborazzi settings baselines, `docs/ACCESSIBILITY.md`
  Acceptance: no shared interactive widget declares a minimum below 48 dp; the Roborazzi baselines are re-recorded; a test asserts the floor for the shared widgets, not only for keyboard rows.
  Complexity: S

- [ ] P1 — Give fork-added strings a translation route SwiftFloris owns
  Why: v1.9.59 fixed the locale *mapping* and added a coverage gate, but `crowdin.yml` still names upstream FlorisBoard's `FSEC_CROWDIN_PROJECT_ID`/`FSEC_CROWDIN_PERSONAL_TOKEN` and nothing in the tree consumes the file. 2,541 strings ship; locales carry 830–999. No fork-added string — MCP, sync, snippets, migration assistant, typing stats, per-app profiles, settings search — exists in any locale, so Arabic and Hebrew users get a mostly-English RTL settings app.
  Evidence: `crowdin.yml:1-2`; `app/src/main/res/values/strings.xml` (2,541) vs `values-de` 869 / `values-fr` 872 / `values-ar` 830 / `values-zh-rCN` 999; `settings__mcp__title`, `settings__sync__title`, `settings__migration_assistant__title`, `settings__search__placeholder` absent from `values-de/strings.xml`; no consumer of `crowdin.yml` anywhere in the tree
  Touches: `crowdin.yml`, `scripts/` (a local CLI wrapper if the pipeline is kept), `CONTRIBUTING.md`, `README.md`, `docs/`
  Acceptance: either a SwiftFloris-owned Crowdin project is configured and a local script can push source strings and pull translations, or `crowdin.yml` is deleted and `CONTRIBUTING.md` documents a PR-based path; either way `scripts/check-locale-coverage.py` gains a ratchet on fork-added string coverage so the gap cannot silently widen. Resolving the ownership question in RESEARCH.md's Open Questions comes first.
  Complexity: M

- [ ] P1 — Repair the two dead ends in the migration flow
  Why: the Migration Assistant's "SwiftFloris encrypted backup (.sfexp)" tile navigates to the archive Restore screen, which handles `.sfbak`/ZIP and has no dictionary path — `.sfexp` is produced and consumed only inside the user-dictionary screen. And the importer rejects `.db`/`.sqlite` entries with a message directing the user to an "Import .flbackup path" that exists nowhere in the app. Migration is the fork's headline on-ramp during the SwiftKey account retirement.
  Evidence: `app/settings/dictionary/MigrationAssistantScreen.kt:95-100` vs `app/settings/dictionary/UserDictionaryScreen.kt:113,1148-1165`; `ime/dictionary/DictionaryImporter.kt:183-192`; `docs/MIGRATE_FROM_SWIFTKEY.md:121,149`
  Touches: `app/settings/dictionary/MigrationAssistantScreen.kt`, `ime/dictionary/DictionaryImporter.kt`, `docs/MIGRATE_FROM_SWIFTKEY.md`, `app/src/main/res/values/strings.xml`
  Acceptance: the encrypted-backup tile opens the `.sfexp` import flow; the `.flbackup` SQLite path either works or the error names a route that exists; `docs/MIGRATE_FROM_SWIFTKEY.md` describes only actions the UI offers.
  Complexity: S

### P2

- [ ] P2 — Replace source-text assertions with behavioural tests on the security paths
  Why: 20 test files "verify" behaviour by reading production source and asserting it contains an identifier — 76 such sites. The dictionary-encryption test asserts that a source file contains the strings `"System.loadLibrary(SQLCIPHER_LIBRARY)"` and `"TinkStringPreferenceCrypto.readBytes"`, so it passes if the dictionary is written in plaintext at runtime and fails on a pure rename; one sync test asserts on source indentation. Separately, 31 Roborazzi tests assert nothing under `:app:test` — comparison happens only in the verify task — so a plain unit run reports 31 green tests that cannot fail.
  Evidence: `app/src/test/.../ime/dictionary/PersonalDictionaryEncryptionTest.kt:44-46,58-60,76-78,101-103`; `lib/io/AtomicFileWriterTest.kt`, `config/ReleaseEvidenceContractTest.kt`, `ime/media/MediaPaletteAccessibilityContractTest.kt`, `app/settings/advanced/PortableBackupScreenContractTest.kt`, `app/settings/sync/SyncSettingsScreenContractTest.kt:37`; the correct pattern at `app/src/androidTest/.../PersonalDictionaryRoomSqlCipherRuntimeTest.kt`; Roborazzi wiring at `app/build.gradle.kts:33-36,435-438`
  Touches: the 20 contract test files, `app/src/androidTest/`, `app/build.gradle.kts`
  Acceptance: every security-relevant claim currently asserted against source text is asserted against observable behaviour — on the attached device where a real store is required — and the source-grep assertions are deleted rather than kept alongside; the Roborazzi capture tests are excluded from, or clearly labelled in, the plain unit-test report so the suite's green count reflects assertions.
  Complexity: L

- [ ] P2 — Give every security-relevant dependency a freshness floor
  Why: `.github/security-dependency-freshness.json` lists exactly one dependency, so Tink (which wraps the dictionary, clipboard and Tasker secrets), Room, AGP, Kotlin and the Gradle wrapper have no floor at all. The gate prints `OK (1 checked dependency floor(s))`, which reads like a pass. The override matcher is also inverted — it requires both `catalogKey` **and** `module` to differ before skipping, so one override can suppress a second dependency's floor (latent; `overrides` is empty).
  Evidence: `.github/security-dependency-freshness.json:4-13`; `scripts/check-security-dependency-freshness.py:131-134`, `:165`, `:199-225`; `gradle/libs.versions.toml` is never enumerated
  Touches: `.github/security-dependency-freshness.json`, `scripts/check-security-dependency-freshness.py`, `scripts/test-check-security-dependency-freshness.py`
  Acceptance: every crypto, storage and build-toolchain pin in `gradle/libs.versions.toml` has a floor, the gate fails when the catalog contains a security-relevant coordinate with no entry, and the override matcher requires both fields to match before suppressing.
  Complexity: S

- [ ] P2 — Retire the drifted copy of the data-extraction exclude list
  Why: `verifyDataExtractionRules` pins 13 of the 22 paths in `data_extraction_rules.xml`. Unpinned: `swiftfloris_tasker_auth.xml` (the per-install Tasker HMAC secret), the whole `clipboard_history*` family, `clipboard_files`, `clipboard_history_key.xml`, `clipboard_media_key.xml`, `swiftfloris_scheduled_backup.xml`. Deleting any leaves the gate green. `BackupDataInventoryTest` already does a bidirectional exact match over all of them, so the Gradle task is a weaker, drifted second copy of a list that has a stronger owner.
  Evidence: `app/build.gradle.kts:451-474` (13 pairs) vs `app/src/main/res/xml/data_extraction_rules.xml` (22 unique paths, 44 `<exclude>` elements across both sections); `app/src/test/.../BackupDataInventoryTest.kt`; `ime/tasker/TaskerAuthentication.kt:23-26`
  Touches: `app/build.gradle.kts`, `app/src/test/.../BackupDataInventoryTest.kt`
  Acceptance: one owner for the list — either the Gradle task derives its required set from the same source as the inventory test, or it is deleted and the inventory test is wired into the release evidence run as the gate. Adding a new persisted secret without an exclude fails exactly one check, and that check names the file.
  Complexity: S

- [ ] P2 — Add a snackbar host and undo for destructive actions
  Why: the app has no undo infrastructure — zero `Snackbar`/`SnackbarHost`, 62 toasts — so the surface splits into 11 confirmation dialogs (against the project's stated preference for immediate action plus undo) and 8 deletions with neither confirmation nor undo. Deleting a single clipboard entry is completely silent; per-app profile delete is a destructive button in a dialog's dismiss slot. One piece of infrastructure fixes the whole category.
  Evidence: no `SnackbarHost` under `app/` or `lib/`; silent delete at `ime/clipboard/ClipboardInputLayout.kt:786-792`; dismiss-slot delete at `app/settings/privacy/PerAppKeyboardProfileScreen.kt:356-360`; dialogs at `app/ext/ExtensionViewScreen.kt:276`, `app/settings/typing/SnippetSettingsScreen.kt:220,238`, `app/settings/localization/LocalizationScreen.kt:196`, `app/settings/addons/AddonsSettingsScreen.kt:504`; the one existing undo at `app/settings/dictionary/PersonalDictionaryImportSummaryDialog.kt:36-41`
  Touches: `lib/compose/FlorisScreen.kt`, `lib/compose/FlorisCards.kt`, the 19 call sites above, `ime/clipboard/ClipboardInputLayout.kt`
  Acceptance: a shared snackbar surface exists for both Settings and the IME panels; every deletion either produces an undoable snackbar or keeps its dialog with a stated reason; no destructive action sits in a dialog's dismiss slot.
  Complexity: L

- [ ] P2 — Make the settings search index self-verifying
  Why: the index is a hand-written list of 106 entries (81 preferences) against roughly 300 rendered `Preference(...)` composables, and its integrity test iterates the index itself, so it validates what is already there and can never detect an omission. Input Feedback indexes 0 of 16, Addons 0 of 8, MCP 0 of 9, Gestures 7 of 28 — "vibration strength" and "utility key action" return nothing. The gap widens with every new preference.
  Evidence: `app/settings/search/SettingsSearchIndex.kt:119-238`; `app/src/test/.../search/SettingsSearchIndexIntegrityTest.kt:45,55,70`; deep-link landing renders a card at the top of the screen rather than scrolling to the row (`lib/compose/FlorisScreen.kt:250-271`)
  Touches: `app/settings/search/SettingsSearchIndex.kt`, `app/src/test/.../SettingsSearchIndexIntegrityTest.kt`, `lib/compose/FlorisScreen.kt`
  Acceptance: the index is generated from, or diffed against, the preference keys the screens actually render, and a test fails when a rendered preference has no entry; a deep link scrolls to and highlights the target row.
  Complexity: M

- [ ] P2 — Let glide typing survive a second pointer
  Why: the gesture detector tracks one pointer and returns `false` on `ACTION_POINTER_DOWN` whenever one is already tracked; its `ACTION_MOVE` branch compares the tracked id against `event.getPointerId(event.actionIndex)`, which is always index 0 for a move. Dual-thumb swipe is impossible, and a glide begun while another finger rests on the keyboard never registers movement. Two-finger swipe is the single most-repeated must-have in the highest-traffic 2026 keyboard discussion, and it is the stated reason people stay on HeliBoard.
  Evidence: `ime/text/gestures/GlideTypingGesture.kt:62-90`, `:134-136`, `:164`; https://news.ycombinator.com/item?id=48656610
  Touches: `ime/text/gestures/GlideTypingGesture.kt`, `ime/text/gestures/GlideTypingManager.kt`, `ime/text/keyboard/TextKeyboardLayout.kt`, `app/src/test/.../gestures/`
  Acceptance: a glide continues correctly while a second pointer is down, and `ACTION_MOVE` resolves the tracked pointer by index lookup rather than `actionIndex`; a replay test covers a two-pointer sequence in both orders (glide first, rest-finger first). Full dual-thumb alternating-hand decoding is out of this item's scope — this is the prerequisite that unblocks it.
  Complexity: M

- [ ] P2 — Add an in-app bug-report path that carries the evidence the templates ask for
  Why: the crash path is good, but the only in-app route to the issue tracker is via an actual crash. `AboutScreen` has no report link, and the build type, commit hash, device and Android version the issue templates demand are computed only inside the crash dialog. Neither the crash dialog nor the debug-log export offers a share intent — both are clipboard-copy only — so a user must paste manually and self-redact.
  Evidence: `.github/ISSUE_TEMPLATE/bug_report.yml:34-65`; `lib/crashutility/CrashDialogActivity.kt:76-101,113-122,137-146`; `app/settings/about/AboutScreen.kt:73,117-140`; `app/devtools/ExportDebugLogScreen.kt:85-107`
  Touches: `app/settings/about/AboutScreen.kt`, `app/devtools/ExportDebugLogScreen.kt`, `lib/crashutility/CrashDialogActivity.kt`, `app/src/main/res/xml/file_paths.xml`
  Acceptance: About offers "Report a problem" with a pre-filled block containing version, versionCode, build type, commit hash, install source, device and Android version; crash reports and debug logs can be shared via `ACTION_SEND` through the existing FileProvider, with the same redaction the crash template asks the user to perform.
  Complexity: M

- [ ] P2 — Tell users what backup does not cover, and what import overwrites
  Why: the personal dictionary — everything a SwiftKey/Gboard migrant brings — is `SensitiveExcluded` from backups, and the coverage notice mentions only "learned words", never the personal dictionary, Tasker auth or the typing-trace file. `BackupDataInventory.sensitiveExclusions()` exists but no UI calls it, so the notice is a hand-written string free to drift. Separately, importing over an existing `(word, locale)` destroys the prior `freq`/`shortcut` irrecoverably while the summary reports only "Updated N existing words".
  Evidence: `app/settings/advanced/BackupDataInventory.kt:201-214`, `:280-282`; `app/settings/advanced/BackupScreen.kt:598-602` + `strings.xml:1755`; `ime/dictionary/PersonalDictionaryImportBatch.kt:34-37,102-113,146-150` + `strings.xml:1406`
  Touches: `app/settings/advanced/BackupScreen.kt`, `app/settings/advanced/BackupDataInventory.kt`, `app/settings/dictionary/PersonalDictionaryImportSummaryDialog.kt`, `app/src/main/res/values/strings.xml`
  Acceptance: the coverage notice is rendered from `sensitiveExclusions()` so it cannot drift, and names every excluded store; the import summary states that overwritten rows lose their previous frequency and shortcut and are not covered by undo.
  Complexity: S

- [ ] P2 — Bring the lagging build and library pins current
  Why: nine pins are behind their latest stable as of 2026-08-11, and two of them matter beyond hygiene: `androidx-core` 1.19.0 adds `TextAttributeCompat`, the compat backport of the API 37 suggestion-selected attribute the editor already writes behind an API-37 guard; and `buildTools` is pinned to 36.0.0 against `compileSdk 37`.
  Evidence: verified against Maven metadata 2026-08-11 — Gradle 9.6.1 → 9.7.0, `androidx-core` 1.18.0 → 1.19.0, `androidx-sqlite` 2.6.2 → 2.7.0, Coil 3.4.0 → 3.5.0, Roborazzi 1.70.0 → 1.71.0, Kotest 6.2.3 → 6.2.4, KSP 2.3.9 → 2.3.11, `buildTools` 36.0.0 → 37.0.0; `gradle/libs.versions.toml`, `gradle/tools.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`
  Touches: `gradle/libs.versions.toml`, `gradle/tools.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `README.md`, `docs/REPRODUCIBLE_BUILDS.md`, `docs/DEPENDENCY_TRIAGE.md`
  Acceptance: pins are current, the full unit suite and Roborazzi verify stay green, `scripts/check-public-doc-version-pins.py` passes, and the reproducible-build image installs the same component set. Kotlin stays on 2.4.10 — see the CVE item in `Roadmap_Blocked.md`. Coil 3.5.0 raises minSdk to 23, which is below this project's 26; confirm no other bump raises it above 26.
  Complexity: S

- [ ] P2 — Fix the unreachable per-locale empty state in the user dictionary
  Why: `loadUiSnapshot` resets `currentLocale` to null whenever the selected locale returns zero words, so the per-locale empty state can never render. Deleting the last word for a language silently bounces the user back to the language list with no explanation, and two shipped strings are dead.
  Evidence: `app/settings/dictionary/UserDictionaryScreen.kt:251-273`, `:881-903`; `strings.xml:1373-1374`
  Touches: `app/settings/dictionary/UserDictionaryScreen.kt`
  Acceptance: deleting the last word for a locale keeps the user on that locale and shows the empty state with its add action; a test covers the zero-word snapshot.
  Complexity: S

- [ ] P2 — Prove the encrypted clipboard survives a very large clip
  Why: three keyboards report multi-second stalls or crashes on large clipboard payloads, one specifically at ~120 KiB of URLs. SwiftFloris bounds retention at 64 KiB UTF-8 and hands larger live clips to direct-paste only, but the history store is SQLCipher-encrypted and the search/filter path runs over decrypted rows — encryption makes this class worse, not better, and nothing replays it.
  Evidence: `ime/clipboard/ClipboardTextRetentionPolicy.kt:32`; `ime/clipboard/provider/ClipboardHistoryEncryption.kt:23,45-48`; `ime/clipboard/ClipboardHistoryFilter.kt:51-73`; https://github.com/HeliBorg/HeliBoard/issues/2697 ; https://github.com/florisboard/florisboard/issues/3117 ; https://github.com/florisboard/florisboard/pull/3303
  Touches: `app/src/test/.../ime/clipboard/`, `ime/clipboard/ClipboardInputLayout.kt`, `ime/clipboard/provider/ClipboardHistoryStore.kt`
  Acceptance: a test ingests a 120 KiB clip and a history full of near-limit entries, asserts the retention bound is applied off the main thread, and pins a search/filter budget over the encrypted store; the panel opens without a main-thread stall on the attached device.
  Complexity: M

- [ ] P2 — Add `android:localeConfig` and an in-app UI language picker
  Why: 43 locales ship but the manifest declares no `localeConfig` and nothing calls `LocaleManager`/`AppCompatDelegate.setApplicationLocales`, so on Android 13+ the app's UI language cannot be chosen in system settings or in-app — it always follows the system. Users typing in one language while wanting the settings UI in another have no route, and this is the standard axis privacy tool comparisons check.
  Evidence: no `localeConfig`, `locales_config`, `LocaleManager` or `setApplicationLocales` anywhere in `app/src` or `lib/`; `app/src/main/res/values-*` ships 43 locale directories; https://developer.android.com/guide/topics/resources/app-languages
  Touches: `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts` (AGP `generateLocaleConfig`), `app/settings/localization/LocalizationScreen.kt`, `README.md`
  Acceptance: the app appears under Android Settings → Apps → Language with the shipped locales listed, and a Settings entry sets the app locale independently of the typing subtype; the locale-coverage gate keeps the generated config and the shipped `values-*` directories in agreement.
  Complexity: S

### P3

- [ ] P3 — Offer scrambled digit layouts for PIN and numeric password fields
  Why: shoulder-surfing and touch-trace attacks on a fixed numeric grid are the one keyboard-layer threat a privacy IME can actually mitigate, and no FOSS Android keyboard ships it — upstream accepted the proposal and never implemented it. SwiftFloris already detects password variations for popup suppression, so the field-classification half exists.
  Evidence: no `scramble`/`randomiz` match anywhere in `app/src/main`; existing password detection at `ime/text/keyboard/PasswordFieldPopupGate.kt:25`, used at `ime/text/keyboard/TextKeyboardLayout.kt:350,364`; layouts at `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/{numeric,phone,phone2}`; https://github.com/florisboard/florisboard/issues/3225
  Touches: `ime/text/keyboard/TextKeyboardLayout.kt`, `ime/keyboard/KeyboardMode.kt`, `app/prefs/KeyboardPrefs.kt`, the numeric layout assets, `app/src/main/res/values/strings.xml`
  Acceptance: an opt-in preference scrambles digit positions on numeric password fields only, reshuffling per field focus rather than per keypress; TalkBack announces the actual digit under the finger; a test asserts the scramble never applies outside a password variation.
  Complexity: M

- [ ] P3 — Support user-supplied keypress sounds
  Why: key feedback is limited to the four `AudioManager.FX_KEYPRESS_*` system effects, so the sound is whatever the OEM ships and cannot be themed alongside a Snygg theme. This has been an accepted upstream proposal since 2021 and is a visible, low-risk differentiator that needs no network.
  Evidence: `ime/input/InputFeedbackController.kt:108-123`; `app/prefs/InputFeedbackPrefs.kt` has volume and effect toggles but no sound source; https://github.com/florisboard/florisboard/issues/1360
  Touches: `ime/input/InputFeedbackController.kt`, `app/prefs/InputFeedbackPrefs.kt`, `app/settings/keyboard/InputFeedbackScreen.kt`, the extension/backup paths
  Acceptance: a user can select a local audio file per key class through SAF, playback is pooled and does not allocate on the input path, the choice is covered by backup, and the system-effect default is unchanged when nothing is selected.
  Complexity: M

- [ ] P3 — Turn the spacebar into a continuous cursor trackpad
  Why: spacebar gestures currently dispatch discrete DPAD key events bound to four swipe directions; Gboard and HeliBoard both ship a continuous drag that moves the cursor proportionally, which is materially better for editing and is the most-cited text-editing improvement of 2026. The existing gesture pipeline and the clamped selection bounds are the hard parts and both already exist.
  Evidence: `app/prefs/GesturesPrefs.kt:90-117` (four discrete swipe actions plus sensitivity; the comment at `:95` already anticipates "continuous vertical trackpad"); `ime/keyboard/KeyboardManager.kt:648-663` dispatches DPAD events; clamping at `ime/editor/EditorInstance.kt:533,544`
  Touches: `ime/text/gestures/SwipeGesture.kt`, `ime/keyboard/KeyboardManager.kt`, `app/prefs/GesturesPrefs.kt`, `app/settings/gestures/GesturesScreen.kt`
  Acceptance: holding and dragging the spacebar moves the cursor continuously with a configurable ratio, releasing leaves the cursor where the finger stopped, the discrete swipe actions remain available for users who prefer them, and every computed index stays inside `safeEditorBounds`.
  Complexity: M

- [ ] P3 — Split the offensive-word filter and expose autocorrect aggressiveness
  Why: `blockPossiblyOffensive` is a single boolean, so a user who wants profanity suggested but slurs filtered has no option — FUTO split exactly this in v0.1.29.1. And autocorrect is on/off with a commit-mode enum but no confidence threshold, while HeliBoard shipped a confidence slider in v4.0; users repeatedly report over-correction of ordinals and punctuation.
  Evidence: `app/prefs/SuggestionPrefs.kt:86`; `app/prefs/CorrectionPrefs.kt:80,84`; `ime/nlp/ImmediateAutocorrect.kt`; https://github.com/futo-org/android-keyboard/releases/tag/v0.1.29.1 ; https://github.com/HeliBorg/HeliBoard/releases/tag/v4.0-alpha1 ; https://github.com/HeliBorg/HeliBoard/issues/2665 ; https://github.com/HeliBorg/HeliBoard/issues/2727
  Touches: `app/prefs/SuggestionPrefs.kt`, `app/prefs/CorrectionPrefs.kt`, `ime/nlp/ImmediateAutocorrect.kt`, `ime/nlp/SwiftKeyCandidateRanker.kt`, `app/settings/typing/`
  Acceptance: the offensive filter has at least a slurs-only tier alongside the existing all-or-nothing setting; autocorrect exposes a confidence threshold that feeds the ranker's accept bar; the typing-quality scorecard records the score at each threshold so the default is chosen from data.
  Complexity: M

- [ ] P3 — Align the split-keyboard gutter to the physical hinge
  Why: split geometry is derived from `WindowSizeClass` alone; only `androidx.window:window-core` is on the classpath, so there is no `WindowInfoTracker`/`FoldingFeature` consumer and the gutter never lands on the fold. Foldable-specific keyboard bugs are the most common hardware complaint against every competitor, and HeliBoard shipped separate foldable scaling in v4.0.
  Evidence: `ime/window/ImeFormFactor.kt:23-24` (only `WindowSizeClass`); `gradle/libs.versions.toml` pins `androidx-window-core`, not `androidx.window`; `ime/window/SplitKeyboardLayoutCalculator.kt`, `ime/text/keyboard/SplitGutterPostPass.kt`; https://github.com/HeliBorg/HeliBoard/issues/2708
  Touches: `gradle/libs.versions.toml`, `ime/window/ImeFormFactor.kt`, `ime/window/SplitKeyboardLayoutCalculator.kt`, `app/prefs/KeyboardPrefs.kt`
  Acceptance: on a device reporting a vertical `FoldingFeature`, the split gutter aligns to the hinge bounds and the halves size to the reported posture; behaviour on non-folding devices is byte-identical to the pre-change baseline, proved by the existing split tests and a Roborazzi capture. Verify on the emulator foldable profile — the attached device does not fold.
  Complexity: M

- [ ] P3 — Prepare for Unicode 18 emoji data
  Why: Unicode 18.0 ships 2026-09-16 with nine new emoji, and the bundled data is generated from CLDR 48 / Emoji 17.0. The regeneration path is now understood and the version header is honest as of v1.9.59, so this is a scheduled data refresh rather than an investigation — but `EmojiDataVersion` still has no production consumer, so nothing would notice stale data at runtime.
  Evidence: `app/src/main/assets/ime/media/emoji/*.txt` (`# EMOJI-VERSION: 17.0`); `ime/media/emoji/EmojiData.kt:30-33` parsed only by `EmojiDataVersionTest`; https://www.unicode.org/versions/beta-18.0.0.html ; https://emojipedia.org/unicode-18.0
  Touches: `app/src/main/assets/ime/media/emoji/*.txt`, `ime/media/emoji/EmojiData.kt`, `app/src/test/.../EmojiDataVersionTest.kt`, `CHANGELOG.md`
  Acceptance: after CLDR publishes its Unicode 18 update, the assets are regenerated, the declared version matches a probe set of Emoji 18.0 code points present in the data, and `EmojiDataVersion` gains a real consumer so a mismatch is observable at runtime rather than only in a test.
  Complexity: S

- [ ] P3 — Publish a fork-provenance proof
  Why: a paid Play app is reported to ship FlorisBoard's service and native library while recording microphone clips and logging keystrokes. Every Floris derivative inherits that suspicion, and SwiftFloris already produces the two artefacts that answer it — a reproducible build and a `SHA256SUMS` manifest — but presents them as release hygiene rather than as a provenance argument a reviewer can check in one page.
  Evidence: https://github.com/florisboard/florisboard/discussions/3235 ; https://github.com/Julow/Unexpected-Keyboard/issues/1358 ; `scripts/verify-reproducible-apk.sh`; `README.md` install-trust section; `docs/REPRODUCIBLE_BUILDS.md`
  Touches: `README.md`, `docs/REPRODUCIBLE_BUILDS.md`, `docs/SECURITY.md`
  Acceptance: one page states the package id, the signing-certificate SHA-256 (which the README currently does not carry despite `docs/THREAT_MODEL.md:207-209,266` telling users to compare against it), the exact permission set with a one-line justification each, and the commands a third party runs to reproduce the APK and diff the permissions — verified end to end by someone other than the maintainer.
  Complexity: S

- [ ] P3 — Evaluate a bundled rule-based offline proofreader
  Why: Gboard's on-device writing tools are gated to Gemini-Nano-class hardware and the Grammarly keyboard is being discontinued, leaving grammar assistance unavailable to everyone on ordinary devices. A rule-and-dictionary proofreader is the one credible offline answer that fits `minSdk 26` and needs no model runtime — and SwiftFloris already has the surfaces (spell-checker service, smartbar candidates, `SensitiveFieldGuard`, the addon contract) to host it without touching the base APK's no-network posture.
  Evidence: `ime/nlp/SpellingResult.kt:52-58,116` already carries the Android 12+ grammar-error attribute but nothing produces one; `ime/smartcompose/SensitiveFieldGuard.kt`; `AddonContract` already defines `SMART_COMPOSE_RUNTIME`; https://github.com/futo-org/android-keyboard/issues/2217 ; https://support.google.com/gboard/answer/16515540 ; https://support.grammarly.com/hc/en-us/articles/25038364027661--The-Grammarly-Keyboard-for-Android-will-be-discontinued
  Touches: `ime/nlp/SpellingResult.kt`, `FlorisSpellCheckerService.kt`, `ime/smartcompose/`, `addons/`, `docs/PRIVACY_AND_AI.md`
  Acceptance: a written evaluation covering licence compatibility with Apache-2.0, per-language rule-data size, and APK-vs-addon packaging, plus a spike proving one English rule set produces `RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR` results through the existing spell-checker service on the attached device. Ship the decision, not the integration, in this item. Distinct from the blocked transformer-prediction addon: no model runtime, no GPU, no `INTERNET`.
  Complexity: L
