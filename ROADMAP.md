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

- [ ] P2 — Scroll a settings search result to its row instead of announcing it in a card
  Why: following a search result now lands on the right screen with every preference indexed, but the target row is only named in a card pinned at the top of the screen. On a long screen like Gestures the user still has to hunt for the row the card is talking about, which is the part of the interaction the card was standing in for.
  Evidence: `lib/compose/FlorisScreen.kt:250-272` renders `SettingsSearchHighlightCard` above `content()`; `SettingsSearchHighlightStore` already carries the resolved row title, and `SettingsSearchCoverageTest` now guarantees every row has an entry to aim at
  Touches: `app/src/main/kotlin/.../lib/compose/FlorisScreen.kt`, a new preference wrapper module, the 28 settings screen files
  Acceptance: following a search result scrolls the target row into view and highlights it for a few seconds, the card is removed or demoted, and a test asserts the scroll position changed for a row below the fold. Note the mechanism this needs: Compose gives a parent no way to locate an arbitrary descendant, so the row has to report its own position. The cheapest route is a thin SwiftFloris wrapper over jetpref's `Preference`/`SwitchPreference`/`ListPreference`/`DialogSliderPreference`/`ColorPickerPreference`/`TextFieldPreference` that compares its resolved `title` against the pending target and reports its offset — the screens then change only their import line, not their call sites.
  Complexity: M


- [ ] P2 — Add an in-app bug-report path that carries the evidence the templates ask for
  Why: the crash path is good, but the only in-app route to the issue tracker is via an actual crash. `AboutScreen` has no report link, and the build type, commit hash, device and Android version the issue templates demand are computed only inside the crash dialog. Neither the crash dialog nor the debug-log export offers a share intent — both are clipboard-copy only — so a user must paste manually and self-redact.
  Evidence: `.github/ISSUE_TEMPLATE/bug_report.yml:34-65`; `lib/crashutility/CrashDialogActivity.kt:76-101,113-122,137-146`; `app/settings/about/AboutScreen.kt:73,117-140`; `app/devtools/ExportDebugLogScreen.kt:85-107`
  Touches: `app/settings/about/AboutScreen.kt`, `app/devtools/ExportDebugLogScreen.kt`, `lib/crashutility/CrashDialogActivity.kt`, `app/src/main/res/xml/file_paths.xml`
  Acceptance: About offers "Report a problem" with a pre-filled block containing version, versionCode, build type, commit hash, install source, device and Android version; crash reports and debug logs can be shared via `ACTION_SEND` through the existing FileProvider, with the same redaction the crash template asks the user to perform.
  Complexity: M

- [ ] P2 — Stop transitive Compose Multiplatform dependencies from overriding the Compose BOM
  Why: `docs/REPRODUCIBLE_BUILDS.md` states that every Compose dependency resolves through `gradle/libs.versions.toml` version refs with no floating selectors, but four Compose Multiplatform artifacts (`aboutlibraries-compose-m3`, `jetpref-datastore-ui`, `jetpref-material-ui`, `material-kolor`) each depend on `org.jetbrains.compose.material3:material3`, which carries a constraint on `androidx.compose.material3:material3` at a **pre-release alpha**. Gradle conflict resolution picks the alpha over the BOM's stable pin, so the shipped app has been built against an alpha Material 3 that no file in the repo names. This is how the 2026-08-20 pin batch broke: material-kolor 5.0.0 escalated material3 to 1.5.0-alpha17 against the BOM's foundation 1.12.0 and three screenshot tests died with `AbstractMethodError` on `androidx.compose.foundation.style.CustomStyle.applyStyle`.
  Evidence: `./gradlew :app:dependencyInsight --configuration debugUnitTestRuntimeClasspath --dependency androidx.compose.material3:material3` reports `1.5.0-alpha08`, "By constraint / By conflict resolution: between versions 1.5.0-alpha08, 1.4.0 and 1.3.1"; Compose BOM 2026.08.00 declares `material3 = 1.4.0`, `foundation = 1.12.0`; `docs/REPRODUCIBLE_BUILDS.md:28`
  Touches: `app/build.gradle.kts`, `lib/compose/build.gradle.kts`, `lib/color/build.gradle.kts`, `lib/snygg/build.gradle.kts`, `gradle/libs.versions.toml`, `docs/REPRODUCIBLE_BUILDS.md`
  Acceptance: the resolved `androidx.compose.material3:material3` version is the one the BOM names, or the alpha is declared explicitly in the catalog with a written reason; a gate fails when a Compose artifact resolves to a version no repository file names; the reproducible-build doc's pinning claim matches what resolution actually does. Verify the Compose Multiplatform libraries still work against the chosen version — they are compiled against the alpha, so forcing the stable line risks the mirror-image `AbstractMethodError` in the jetpref settings UI.
  Complexity: M

### P3

- [ ] P3 — Decode a true dual-thumb alternating-hand glide
  Why: the detector now traces whichever finger is gliding and survives a second pointer, but it still traces exactly one at a time, so alternating-hand swiping produces one trace with a jump where the hands change over rather than two interleaved words. Two-finger swipe is the most-repeated must-have in the highest-traffic 2026 keyboard discussion and the stated reason people stay on HeliBoard; the pointer plumbing that blocked it is done, the decoding is not.
  Evidence: `ime/text/gestures/GlideTypingGesture.kt` traces a single `pointerId` and hands over only when the current one is unconfirmed; `ime/text/gestures/GlideTypingManager.kt` holds one in-flight gesture; https://news.ycombinator.com/item?id=48656610
  Touches: `ime/text/gestures/GlideTypingGesture.kt`, `ime/text/gestures/GlideTypingManager.kt`, `ime/text/keyboard/TextKeyboardLayout.kt`, `app/src/test/.../gestures/`
  Acceptance: two pointers can each carry a trace, the decoder resolves them into words in the order they completed rather than merging them, and the glide trail renders both; a replay test covers alternating-hand input where the second trace starts before the first ends. Single-pointer behaviour stays byte-identical, proved by the existing detector tests.
  Complexity: L

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

### P2

### P3

- [ ] P3 — Share one `NlpAddonHub` between the smartbar action and the NLP manager
  Why: `QuickAction` constructs `NlpAddonHub.production()` per invocation while `NlpManager` holds a long-lived instance — harmless today because the audit sink is a global object, but silently wrong the day the hub gains any per-instance state (cache, cooldown, rate limit), and two construction sites already drifted once before the hub became load-bearing.
  Evidence: `ime/keyboard/QuickAction.kt:112` vs `ime/nlp/NlpManager.kt:69`; commit 491d90d93
  Touches: `ime/keyboard/QuickAction.kt`, `ime/nlp/NlpManager.kt`
  Acceptance: one production hub instance is shared (injected or resolved via the existing manager), construction-site count for `NlpAddonHub.production()` is one, and a test pins it.
  Complexity: S

