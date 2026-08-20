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

### P2

- [ ] P2 — Replace source-text assertions with behavioural tests on the security paths
  Why: 20 test files "verify" behaviour by reading production source and asserting it contains an identifier — 76 such sites. The dictionary-encryption test asserts that a source file contains the strings `"System.loadLibrary(SQLCIPHER_LIBRARY)"` and `"TinkStringPreferenceCrypto.readBytes"`, so it passes if the dictionary is written in plaintext at runtime and fails on a pure rename; one sync test asserts on source indentation. Separately, 31 Roborazzi tests assert nothing under `:app:test` — comparison happens only in the verify task — so a plain unit run reports 31 green tests that cannot fail.
  Evidence: `app/src/test/.../ime/dictionary/PersonalDictionaryEncryptionTest.kt:44-46,58-60,76-78,101-103`; `lib/io/AtomicFileWriterTest.kt`, `config/ReleaseEvidenceContractTest.kt`, `ime/media/MediaPaletteAccessibilityContractTest.kt`, `app/settings/advanced/PortableBackupScreenContractTest.kt`, `app/settings/sync/SyncSettingsScreenContractTest.kt:37`; the correct pattern at `app/src/androidTest/.../PersonalDictionaryRoomSqlCipherRuntimeTest.kt`; Roborazzi wiring at `app/build.gradle.kts:33-36,435-438`
  Touches: the 20 contract test files, `app/src/androidTest/`, `app/build.gradle.kts`
  Acceptance: every security-relevant claim currently asserted against source text is asserted against observable behaviour — on the attached device where a real store is required — and the source-grep assertions are deleted rather than kept alongside; the Roborazzi capture tests are excluded from, or clearly labelled in, the plain unit-test report so the suite's green count reflects assertions.
  Complexity: L

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
  Note (2026-08-20): targets moved — Gradle 9.6.1 → 9.7.1, AGP 9.3.0 → 9.3.1, Compose BOM 2026.06.00 → 2026.08.00, Roborazzi → 1.72.0, KSP → 2.3.11, SQLCipher 4.17.0 → 4.18.0 (adds Room 3 support; requires compileSdk 37 — already satisfied), buildTools → 37.0.0; androidx-core 1.19.0, androidx-sqlite 2.7.0, Coil 3.5.0, Kotest 6.2.4 unchanged as targets. Kotlin 2.4.20 is still RC (2026-08-12); Robolectric stable is still 4.16.1. androidx-core 1.19.0's `TextAttributeCompat` backports the API 37 suggestion-selected attribute the editor already writes.

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
  Note (2026-08-20): now schedulable — draft `emoji-test.txt` v18.0 (dated 2026-04-30) is published at unicode.org/Public/draft/emoji/; final data lands at unicode.org/Public/emoji/18.0/ on 2026-09-16. Localized names/annotations/search keywords arrive with CLDR 49 (49-alpha0 tagged 2026-08-14; final ~Oct 2026) — CLDR 48 will never carry Emoji 18. Codepoints and ordering are regenerable today; hold annotation regeneration for CLDR 49.

- [ ] P3 — Publish a fork-provenance proof
  Why: a paid Play app is reported to ship FlorisBoard's service and native library while recording microphone clips and logging keystrokes. Every Floris derivative inherits that suspicion, and SwiftFloris already produces the two artefacts that answer it — a reproducible build and a `SHA256SUMS` manifest — but presents them as release hygiene rather than as a provenance argument a reviewer can check in one page.
  Evidence: https://github.com/florisboard/florisboard/discussions/3235 ; https://github.com/Julow/Unexpected-Keyboard/issues/1358 ; `scripts/verify-reproducible-apk.sh`; `README.md` install-trust section; `docs/REPRODUCIBLE_BUILDS.md`
  Touches: `README.md`, `docs/REPRODUCIBLE_BUILDS.md`, `docs/SECURITY.md`
  Acceptance: one page states the package id, the signing-certificate SHA-256 (which the README currently does not carry despite `docs/THREAT_MODEL.md:207-209,266` telling users to compare against it), the exact permission set with a one-line justification each, and the commands a third party runs to reproduce the APK and diff the permissions — verified end to end by someone other than the maintainer.
  Complexity: S
  Note (2026-08-20): stronger case now — Urik Keyboard (F-Droid, 2026-06) leads its listing with SQLCipher-encrypted learning, and Gboard markets its writing tools as on-device, so "on-device" alone no longer differentiates; the provenance page should lead with the properties Google cannot match: no network permission, reproducible build, verifiable signing.

- [ ] P3 — Evaluate a bundled rule-based offline proofreader
  Why: Gboard's on-device writing tools are gated to Gemini-Nano-class hardware and the Grammarly keyboard is being discontinued, leaving grammar assistance unavailable to everyone on ordinary devices. A rule-and-dictionary proofreader is the one credible offline answer that fits `minSdk 26` and needs no model runtime — and SwiftFloris already has the surfaces (spell-checker service, smartbar candidates, `SensitiveFieldGuard`, the addon contract) to host it without touching the base APK's no-network posture.
  Evidence: `ime/nlp/SpellingResult.kt:52-58,116` already carries the Android 12+ grammar-error attribute but nothing produces one; `ime/smartcompose/SensitiveFieldGuard.kt`; `AddonContract` already defines `SMART_COMPOSE_RUNTIME`; https://github.com/futo-org/android-keyboard/issues/2217 ; https://support.google.com/gboard/answer/16515540 ; https://support.grammarly.com/hc/en-us/articles/25038364027661--The-Grammarly-Keyboard-for-Android-will-be-discontinued
  Touches: `ime/nlp/SpellingResult.kt`, `FlorisSpellCheckerService.kt`, `ime/smartcompose/`, `addons/`, `docs/PRIVACY_AND_AI.md`
  Acceptance: a written evaluation covering licence compatibility with Apache-2.0, per-language rule-data size, and APK-vs-addon packaging, plus a spike proving one English rule set produces `RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR` results through the existing spell-checker service on the attached device. Ship the decision, not the integration, in this item. Distinct from the blocked transformer-prediction addon: no model runtime, no GPU, no `INTERNET`.
  Complexity: L

## Research-Driven Additions (2026-08-20)

### P1

- [ ] P1 — Cut v1.9.60 for the eight unreleased post-tag commits
  Why: eight commits of shipped fixes (incognito ghost text, read-only system dictionary, merged-manifest permission allowlist, MCP no-bind, data-preservation rewrites, loading states, method.xml capabilities, MCP lifecycle serialization) sit past the `v1.9.59` tag with `gradle.properties` still at 1.9.59/2108 — the exact drift the front-door gate exists to stop, re-accumulating from the other direction. The uncommitted contrast-gate WIP should be finished (see the 2026-08-11 P1 item's note) or explicitly shelved before the cut.
  Evidence: `git log v1.9.59..HEAD` (8 commits, b6f368f8a..89bc87d6a); `gradle.properties:14-15`; `scripts/check-release-front-door.sh` (green today only because HEAD is unreleased)
  Touches: `gradle.properties`, `README.md` (badge, table header, release log), `fastlane/metadata/android/en-US/changelogs/2109.txt`, `CHANGELOG.md`
  Acceptance: versionCode/versionName bumped, fastlane changelog written, full release-evidence run green, tag pushed, GitHub Release published with APK + SHA256SUMS, front-door gate green against the published release.
  Complexity: S

### P2

- [ ] P2 — Refresh the README developer-verification section with the 2026-08 facts
  Why: the section predates every material development: enforcement is precisely 2026-09-30 (Brazil, Indonesia, Singapore, Thailand), ADB installs are explicitly exempt, the "advanced flow" (Developer-options toggle → warnings → one-time 24-hour wait) is rolling out now, a free email-only tier covers up to 20 devices, and the September named-store list does not include F-Droid. The front-door gate requires this section to be reviewed each quarter, and users in pilot regions get materially wrong guidance six weeks before enforcement.
  Evidence: `README.md` "Google developer verification (Sept 2026)" section; https://android-developers.googleblog.com/2026/03/android-developer-verification.html ; https://9to5google.com/2026/08/18/ (advanced-flow rollout); https://support.google.com/android-developer-console/answer/16561738
  Touches: `README.md`, `Roadmap_Blocked.md` (registration item's blocker text)
  Acceptance: the section states the exact enforcement date, the ADB and advanced-flow fallbacks for pilot-region users, and the 20-device free tier's irrelevance to public distribution; the quarterly-review stamp is current; the registration decision itself stays in `Roadmap_Blocked.md` as human-gated.
  Complexity: S

- [ ] P2 — Make the MCP settings surface honest about the parked engine
  Why: `FlorisImeService` now pins the MCP lifecycle to null and empties both registries (correct — no binding can occur), but `McpSettingsScreen` still offers discovery review, trust actions, and per-daemon toggles that govern a no-op. A user who enables a daemon there reasonably believes something turned on. The 2026-08-11 audit-hub commit deleted the routing roadmap item as done, so no open item tracks this residue.
  Evidence: `FlorisImeService.kt:424-434` (`mcpLifecycle = null`, registries pinned empty, parked comment); `app/settings/mcp/McpSettingsScreen.kt`; commit 491d90d93
  Touches: `app/settings/mcp/McpSettingsScreen.kt`, `app/src/main/res/values/strings.xml`, `docs/PRIVACY_AND_AI.md`
  Acceptance: the screen carries a persistent parked-state banner stating that no daemon is bound or dispatchable in this build (or the screen is gated behind a developer toggle until a live action exists); trust and toggle state remain editable and persisted; README/PRIVACY_AND_AI wording matches.
  Complexity: M

- [ ] P2 — Persist or caption the in-memory addon invocation audit
  Why: `AddonInvocationAudit` is an in-memory object by design, so the privacy audit screen silently resets on process death — an empty log after a reboot reads as "no AI invocation ever occurred", which is the exact misreading the audit surface exists to prevent.
  Evidence: `ime/addons/AddonInvocationAudit.kt:43-49` ("nothing is persisted"); `PrivacyAuditDisplay.kt`; wired in production via `NlpAddonHub.kt:98-160` since commit 491d90d93
  Touches: `ime/addons/AddonInvocationAudit.kt`, `app/settings/privacy/PrivacyAuditScreen.kt`, `app/src/main/res/values/strings.xml`
  Acceptance: either a bounded, size-capped local persistence (covered by the backup exclude inventory) or an explicit "since keyboard start" caption with the process start time on the audit screen; a test pins whichever contract is chosen.
  Complexity: S

- [ ] P2 — Add Transcribro to the external voice-IME providers
  Why: the offline voice handoff supports exactly three voice IMEs (FUTO, WhisperInput, Whisper), and FUTO's standalone Voice Input is in maintenance mode. Transcribro (whisper.cpp + Silero VAD, on-device, actively developed, F-Droid) is the current best-maintained private voice IME and costs one list entry to support.
  Evidence: `ime/voice/ExternalVoiceInputProvider.kt:39-58` (`SupportedOfflineImeProviders`); https://github.com/soupslurpr/Transcribro ; https://github.com/futo-org/voice-input/releases (maintenance cadence)
  Touches: `ime/voice/ExternalVoiceInputProvider.kt`, `ime/voice/VoiceInputSetupActivity.kt` (provider-agnostic copy where it is FUTO-specific), `app/src/main/res/values/strings.xml`
  Acceptance: Transcribro's exact package id is verified from its F-Droid listing before coding; with it installed and enabled, the mic action hands off to it and returns; setup guidance lists it alongside the existing three; existing FUTO-first behavior is unchanged when multiple providers are installed.
  Complexity: S

### P3

- [ ] P3 — Share one `NlpAddonHub` between the smartbar action and the NLP manager
  Why: `QuickAction` constructs `NlpAddonHub.production()` per invocation while `NlpManager` holds a long-lived instance — harmless today because the audit sink is a global object, but silently wrong the day the hub gains any per-instance state (cache, cooldown, rate limit), and two construction sites already drifted once before the hub became load-bearing.
  Evidence: `ime/keyboard/QuickAction.kt:112` vs `ime/nlp/NlpManager.kt:69`; commit 491d90d93
  Touches: `ime/keyboard/QuickAction.kt`, `ime/nlp/NlpManager.kt`
  Acceptance: one production hub instance is shared (injected or resolved via the existing manager), construction-site count for `NlpAddonHub.production()` is one, and a test pins it.
  Complexity: S

