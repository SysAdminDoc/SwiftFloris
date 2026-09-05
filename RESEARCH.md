# Research: SwiftFloris

Date: 2026-09-04. Replaces the 2026-08-23 report after a fresh repository, tracker, ecosystem, platform, dependency, security, community, and literature review.

## Executive Summary

Two things changed since the last pass, and both change the priorities. First, **v1.9.66 was published on 2026-08-30 with a signed APK** (94 downloads by 2026-09-04), which ends the distribution drought and clears the blocker under three items that had been parked on "no release keystore on this host". Second, **the project has real users filing real bugs for the first time**: issues #22 and #23, both opened 2026-09-02 against the shipped build on a Samsung S25 Ultra running Android 16.

Issue #22 traces to the most serious product defect this pass found. `FlorisPreferenceModel.migrate()` does not migrate — it **permanently pins eight shipped preferences to fork-preferred values on every datastore load and on every backup import**. A user who turns the number row off, enables symbol hints, picks Follow-system theming, or chooses a scrollable candidate row has that choice silently reverted the next time the process starts. The reporter described exactly this and also noticed that restoring a backup does not bring the settings back, which is the same code path. The migration test asserts the broken behaviour, so the suite is currently defending the bug.

Everything else in the 2026-08-23 report still holds. The strategic direction is unchanged: local quality, understandable recovery, verifiable distribution. Nothing found this pass argues for a cloud service, an account, or a second language engine in `:app`.

Top findings, priority order:

1. **Verified: eight preferences cannot be kept.** `AppPrefs.kt:297-354` rewrites `keyboard__number_row` false→true, `keyboard__hinted_number_row_enabled` and `keyboard__hinted_symbols_enabled` true→false, `keyboard__utility_key_action` DYNAMIC_SWITCH_LANGUAGE_EMOJIS→SWITCH_TO_EMOJIS, `keyboard__space_bar_display_mode` CURRENT_LANGUAGE→NOTHING, `suggestion__display_mode` DYNAMIC_SCROLLABLE→CLASSIC, `theme__mode` FOLLOW_SYSTEM→ALWAYS_NIGHT, and both `theme__*_theme_id` floris_*→swiftkey_*. Every value being forced away is still a selectable, shipping option (`ThemeMode.kt:27`, `SpaceBarMode.kt:21`, `CandidatesDisplayMode.kt:25`, `UtilityKeyAction.kt:26`, and `floris_day`/`floris_night` in the bundled theme `extension.json`). Introduced by commit `722fe491e` "Match default keyboard to SwiftKey layout", which implemented a change of *defaults* as a *migration rule*. Reported as issue #22.
2. **Verified: `migrate()` runs on every load, not once.** Decompiling `jetpref-datastore-model` 0.3.0 shows `PreferenceModel.migrate()` is invoked from the private `DataStore.loadAndUpdate(...)`, which `handleEvent` calls for both `Event.Init` (every datastore load, so every process start) and `Event.Import`. There is no version gate. `RestoreRollbackSnapshot.kt:260` and `RestoreScreen.kt` both reach it through `FlorisPreferenceStore.import(ImportStrategy...)`, which is why backup restore cannot recover the pinned values either.
3. **Verified: the migration test blesses the defect.** `AppPrefsMigrationTest.kt:137-151` asserts each forced rewrite as expected behaviour and `:211-213` asserts the forced values are then kept. Any fix must invert those cases, or the suite fails on the correct code.
4. **Likely: the resize gesture amplifies its own movement.** `ImeWindowEditorHandles.kt:444-448` accumulates `dragAmount` from `detectDragGestures`, whose deltas are measured in the pointer-input node's own coordinate space. The node is the resize handle, which is repositioned by the resize it is driving, so a stationary finger still produces non-zero deltas in the direction of growth. The in-file TODO at `:433` already suspects the approach. This matches issue #23's "quite janky and makes the keyboard bigger than intended".
5. **Verified: the resize gesture reads stale layout state.** The same block is `pointerInput(Unit)`, so the `rowCount` and `smartbarRowCount` values read at `:447`, and all four gesture callbacks, are captured from the first composition and never refreshed (`ImeWindowEditorHandles.kt:427-455`).
6. **Verified: inline autofill chips are sized from the display, not the keyboard.** `FlorisImeService.kt:898-901` builds the `InlinePresentationSpec` max size from `resources.displayMetrics.widthPixels`, and `NlpInlineAutofill.kt:75-79` then passes that same full-display width to `InlineSuggestion.inflate()` as the exact inflate size. A keyboard that is narrower than the display — floating, one-handed, split, or resized — gets chips wider than itself. This is issue #23's second half, with the attached screenshot.
7. **Verified: the F-Droid recipe cannot complete a binary comparison.** `fdroid/io.github.sysadmindoc.swiftfloris.yml` declares `binary: .../releases/download/v%v/app-release.apk`, which returns **HTTP 404**; the published asset is `SwiftFloris-v1.9.66-release.apk` (HTTP 200). `AllowedAPKSigningKeys` is still `[]`. Neither `scripts/check-fdroid-recipe.py` nor `scripts/check-release-front-door.sh` validates the binary URL against a real release asset.
8. **Verified: the release signing certificate now exists and can be pinned.** `apksigner verify --print-certs` on the published v1.9.66 APK (SHA-256 `d8ac3114…b644b`, matching the release asset digest) reports one signer, `CN=SysAdminDoc Sideload, O=SysAdminDoc, C=US`, RSA 4096, certificate SHA-256 **`dba1aa88e37b90155fca3135ca3b781de92c225107e47c9806e75bf88055fdd8`**, signed with v2 and v3 schemes. That value is exactly what `AllowedAPKSigningKeys` and the fork-provenance page need, so both blocked items can move back to active work.
9. **Verified: AGP 9.3.1 has a lint crash on JDK 17, and F-Droid builds on JDK 17.** AGP 9.3.2 (2026-08-24) fixes lint dying with `NoSuchMethodError: java.util.List.removeLast()` in the bundled intellij-core. The recipe installs `openjdk-17-jdk-headless`, so this is a live risk to the F-Droid build even though the host build on JDK 21 is green.
10. **Verified: the eight resize handles have no accessibility semantics at all.** `ImeWindowEditorHandles.kt` contains no `contentDescription`, `semantics`, `stateDescription`, or `Role`. A drag-only control with no announced identity and no non-gesture alternative is unreachable for TalkBack and switch access, in a repo that otherwise enforces a 48 dp touch-target floor and a theme-contrast gate.

## Product Map

- **Verified: product and stack.** Kotlin and Jetpack Compose input method for Android API 26+, `projectTargetSdk=36`, `projectCompileSdk=37`, versionName 1.9.66 / versionCode 2115 (`gradle.properties`). Modules `:app`, `:benchmark`, `:addons:dictionary-pack-sample`, plus `lib/*` (`settings.gradle.kts`). 1,896 tracked files; 564 main Kotlin sources, 352 JVM/Robolectric test sources, 10 instrumented.
- **Verified: core workflows.** Tap and glide typing, local suggestion and correction, multilingual Latin subtypes, emoji and stickers, encrypted clipboard history, personal dictionaries, themes, per-app profiles, explicit backup and restore, migration importers, Settings search, local voice handoff (`app/src/main/kotlin/dev/patrickgold/florisboard/ime`, README).
- **Verified: distribution is now current.** GitHub Release v1.9.66 published 2026-08-30 with a signed APK and a `SHA256SUMS` file. The prior report's "source far ahead of the public install path" finding is resolved. The F-Droid recipe is written but not submitted, and its binary URL is wrong.
- **Verified: the app-owned data path is unchanged.** No `INTERNET`, `ACCESS_NETWORK_STATE`, or `ACCESS_WIFI_STATE` in the base manifest; `verifyNoInternetPermission` runs as part of `preBuild`. README's posture section correctly separates the app's own network surface from Android-managed backup.
- **Verified: personas.** Privacy-conscious Android users, people leaving SwiftKey who need user-owned migration, multilingual users, and layout customizers. Both 2026-09-02 issues came from a flagship Samsung user customizing layout and using a password manager, which is the second and fourth of those at once.
- **Verified: extensibility is designed but unproven in production.** Typed addon metadata, certificate enrollment, package verification, dictionary mounting, and one sample Esperanto pack exist. No third-party pack ships.
- **Verified: the tree is unusually clean.** 18 TODO/FIXME/HACK/XXX markers across all of `app/src/main` and `lib/`. No file over 1,800 lines. Every long-lived `ime/` scope carries `SupervisorJob`; every `runBlocking` is covered by `scripts/runblocking-allowlist.txt`. Prior audit passes did their job, which is why this pass concentrates on behaviour rather than hygiene.

## Competitive Landscape

Covering only what changed or what the prior pass under-read. The 2026-08-23 landscape section remains accurate for everything not listed here.

- **HeliBoard** (now `HeliBorg/HeliBoard`, 6,034 stars) shipped **v4.1 on 2026-08-30**, on F-Droid 2026-08-31. v4.1-beta1 added a D-Pad cursor block, key-repeat as a long-press code, and a shift-state fix on arrow-key cursor moves. **Learn:** issue #2778, "No way to turn off annoying d-pad", collected 17 reactions in three days. Every new affordance ships with its off switch on day one. **Avoid:** the maintainer has now signed off two consecutive releases with "I'll still be barely active for a while" — do not take a dependency on that project's cadence.
- **FlorisBoard** has had no release since v0.6.0-alpha02 (2026-01-23); stable is still v0.5.2. All current work is on `feat/k3lp/first-integration`, a ground-up touch-keyboard rewrite that landed `minDeviceWidthMm`, a touch-model cache, and per-key hitbox extension between 2026-08-26 and 2026-09-03. **Learn:** those three are the physical-accuracy primitives a fork normally has to invent. **Avoid:** PR #3330, a 151-file +32,649-line pure-Rust NLP engine, was closed unmerged in two days, with the only review comment asking whether AI wrote it. Large unlabelled drops get rejected regardless of merit.
- **AnySoftKeyboard** merged five `[LLM]`-prefixed PRs in the window and is explicit about labelling AI-assisted work. **Learn:** PR #4871 fixed a Direct Boot failure where the IME starts before unlock, the preference store returns a no-op, history loads defaults, and the next write destroys the user's real data. SwiftFloris avoids the write half — `FlorisApplication.kt:141` returns before `initializePreferenceStoreForStartup` at `:174` when the user is locked — but `FlorisImeService` is `directBootAware="true"` (`AndroidManifest.xml:129`), so the keyboard does run pre-unlock on compiled-in defaults with nothing telling the user why.
- **FUTO Keyboard** published a measured swipe benchmark in the v0.1.29 notes (2026-06-01): top-1 / top-4 error of **FUTO 7.38% / 4.19%, Gboard 11.05% / 5.66%, iOS 10.82% / 7.14%, HeliBoard with the Google library 13.12% / 7.63%**. **Learn:** that is a published yardstick and SwiftFloris has no equivalent number for its own glide engine. **Avoid:** the weights are under the FUTO Model Weights License, not an OSI licence, so they cannot ship in an F-Droid build; the 1M+ swipe dataset is MIT and can be used for evaluation freely.
- **CleverKeys** (GPL-3.0, 435 stars) was the most active keyboard project in the window and replaced a neural swipe engine with a CTC/ONNX decoder plus a geometric fallback. **Learn:** their gating discipline is the pattern to copy — context rescoring shipped inert, default off, behind every privacy gate, with the geometric path taking over when the ONNX session dies rather than silently emptying the suggestion bar.
- **Fossify Keyboard** (GPL-3.0, 664 stars, release 1.9.1) is fully offline with no INTERNET permission and a working F-Droid reproducible-build setup. **Learn:** its F-Droid metadata is the closest reference for getting SwiftFloris verified-reproducible.
- **Urik** (GPL-3.0, 409 stars) is the closest existing analogue to SwiftFloris's whole thesis: swipe typing, custom layouts, password-manager support, an encrypted word-learning store, clipboard history, no network permission. **Avoid:** no push since 2026-06-24. Being first is not the same as being maintained.
- **Microsoft SwiftKey** completed a **mandatory Microsoft-account migration on 2026-05-31**; legacy sign-ins were retired, learned dictionaries moved to OneDrive, and unmigrated data was permanently deleted. Copilot Tone and Chat are cloud round-trips. **Learn:** this is the sharpest available contrast for SwiftFloris's positioning — the price of SwiftKey is custody of the typing corpus. **Avoid:** nothing about account-backed backup is worth copying.
- **Gboard** shipped AI Writing Tools in 2026, gated on Gemini Nano v2+ hardware rather than a subscription. **Learn:** ML Kit GenAI exposes proofreading and rewriting on top of Gemini Nano with input, inference, and output staying on-device inside AICore. A keyboard with no INTERNET permission can legitimately call it. That makes local rewrite a platform API rather than a moat, and a candidate for an optional addon rather than base-app work.
- **Fleksy is dead** — ThingThing took the site down on 2026-06-08. **Chrooma** is abandoned since 2023. One competitor class has simply exited.

## Reported Issues

The tracker has 2 open issues, 2 closed, 0 open pull requests, and 3 discussions. Both open issues are new since the last research pass and both are actionable.

- **#22 "Customisation resetting issue" (open, 2026-09-02, v1.9.66, S25 Ultra / Android 16).** Number row returns after being set to hint; symbol hints turn off; neither is restored from a backup. **Traced** to `AppPrefs.kt:297-354` as described in findings 1-3. The report is precise and complete, and it understates the defect — six further preferences are affected that the reporter did not name.
- **#23 "UI And Resizing issues" (open, 2026-09-02, same build and device, screenshot attached).** Resize is janky and overshoots; autofill extends off screen. **Traced** to two independent causes: the self-referential drag deltas plus stale `pointerInput(Unit)` captures at `ImeWindowEditorHandles.kt:427-455`, and the display-width inline autofill sizing at `FlorisImeService.kt:900` and `NlpInlineAutofill.kt:76`. Two separate fixes; do not treat as one item.
- **#9 "crash while typing" (closed).** SymSpell OOM on a 256 MB-heap TECNO device, fixed in v1.9.53 with budget scaling, word-length caps, periodic heap checks, in-place freeze, and an OOM catch. Do not re-open.
- **#1 "Choosing an emoji crashes the board" (closed).** Fixed. Do not re-open.
- **Discussion #21** (how to install Portuguese) remains answered by the active language-setup work. Discussions #19 and #20 carry no demand. Unchanged from the last pass.
- **Not acted on:** upstream FlorisBoard's clipboard cluster (#3289 over-masking, #3323 reveal toggle) does not apply — `ClipboardInputLayout.kt:214-243` and `:786-826` already implement a time-limited per-item reveal through `ClipboardSensitiveRevealPolicy`. FlorisBoard #3222 and #3201 (settings lost after a bootloader change, imported settings reverting) describe symptoms close to #22 but in a different codebase; the local reproduction is stronger evidence and no separate item is needed.

## Security, Privacy, and Reliability

- **Verified: no key leak.** `keystore/swiftfloris-selfhost.jks` exists on disk but is untracked; `.gitignore:59` excludes `*.jks` and `git ls-files keystore/` returns nothing.
- **Verified: the release key is now load-bearing and unpinned.** 94 people have installed an APK signed with certificate `dba1aa88…fdd8`. Android will refuse any future update signed by a different key, so this certificate is now permanent unless the maintainer accepts an uninstall-reinstall for every user. It should be pinned in the recipe and published on the fork-provenance page before another release goes out. The DN reads `SysAdminDoc Sideload`, which suggests the sideload key was used rather than a dedicated release key; that is a decision to record deliberately, not to discover later.
- **Verified: the CLAUDE.md and vault claim "no release keystore on this host" is stale.** A signed release shipped on 2026-08-30. Three items in `Roadmap_Blocked.md` rest on that premise and need re-checking, not just the two named above.
- **Verified: unescaped-delimiter corruption does not apply here.** `PersonalNgramPersistence.kt:24-33` rejects any token containing whitespace or a control character rather than escaping it, so the AnySoftKeyboard #4871 delimiter bug has no analogue in the n-gram stores.
- **Verified: sensitive clipboard entries are handled.** `ClipDescription.EXTRA_IS_SENSITIVE` is read in `ClipboardManager.kt:289` and `ClipboardDatabase.kt`, and the reveal path is time-boxed.
- **Verified: Android 17 native and reflection hardening do not apply.** The app uses `System.loadLibrary` only (`ClipboardHistoryEncryption.kt:47`, `FlorisUserDictionaryEncryption.kt:39`), never `System.load`, so "Safer Native DCL" is a non-issue. The single `isAccessible = true` (`FlorisEmojiCompat.kt:238`) is on a constructor, not a static final field. No `ContentCaptureManager.setContentCaptureEnabled` call exists. No manifest `screenOrientation`/`resizeableActivity`/`*AspectRatio` overrides exist, so the large-screen enforcement at targetSdk 37 is a no-op here.
- **Verified: the API 37 IME accessibility requirement is already met.** `EditorInputConnectionBatch.kt:198-222` implements `TextAttribute.Builder.setTextSuggestionSelected()` on both `commitText` and `setComposingText` behind an API 37 guard. The reader half of that feature belongs to host apps, not the IME. No item needed.
- **Verified: stylus handwriting is implemented.** `method.xml:8` declares `supportsStylusHandwriting`, and `FlorisImeService.kt:605-628` implements `onStartStylusHandwriting()` and `onStylusHandwritingMotionEvent()`. No item needed.
- **Needs live validation: Android 17 password visibility on physical keyboards.** Android 17 splits password display into `show_passwords_physical` (defaulting to hidden) and `show_passwords_touch`. The app has a physical-keyboard path and a `SensitiveFieldGuard`, but nothing reads either setting. Whether the platform handles this entirely below the IME needs a device check before code moves.
- **Likely: the keyboard runs on defaults before first unlock with no signal.** `FlorisImeService` is `directBootAware="true"` while the preference store is deliberately not initialized until after unlock. Nothing in the IME waits on `preferenceStoreLoaded`; only `FlorisAppActivity.kt:115` and `ScheduledBackupWorker.kt:98` do. The failure mode is a lock-screen keyboard that ignores every customization, which is confusing rather than dangerous, and it is one write away from the AnySoftKeyboard data-loss bug if a pre-unlock write is ever added.

## Architecture Assessment

- **Verified: `migrate()` is being used as a defaults mechanism and needs a different one.** The correct place to change a shipped default for new installs is the `PreferenceData` default plus a one-shot, version-stamped migration keyed on `internal.versionOnInstall` / `versionLastUse`, which already exist in the model (`FlorisPreferenceModelImpl.kt:154-156`). The current approach cannot distinguish "user never chose" from "user chose this and I disagree". Fixing the eight entries without fixing the mechanism invites the same commit again.
- **Verified: gesture code that mutates its own layout needs root-space coordinates.** `imeWindowEditorHandle` is the only place in the tree where a drag target repositions itself under the finger. The fix is the one its own TODO names: track `positionInRoot()` from `onGloballyPositioned` and compute `current - initial`, not accumulated node-local deltas.
- **Verified: `pointerInput(Unit)` is a latent staleness trap.** `ImeWindowEditorHandles.kt:427` and `:432` both use it. Any state or callback read inside those blocks is frozen at first composition. Worth a targeted sweep rather than a one-line fix.
- **Verified: IME window geometry has no single owner.** Inline autofill reads `resources.displayMetrics`, `FlorisImeSizing` derives its own heights (with a standing TODO at `FlorisImeSizing.kt:129-135` to fold that into `ImeWindow`), and `ImeWindowConstraints` owns the real window bounds. The autofill overflow is a symptom of three sources of truth for one number.
- **Verified: release truth still has hand-copied pins, and now a hand-copied URL.** The F-Droid `binary:` URL is the newest instance of the class the existing toolchain-drift item already covers. It should be folded into that item's checker rather than given a parallel gate.
- **Verified: coverage floors are still absent.** Kover is enabled without verification rules (`app/build.gradle.kts:331-333`). The preference-migration defect is precisely the kind a branch floor over `app/` migration code would have made visible.
- **Verified: fork-added strings remain untranslated.** 44 `values-*` directories, 2,607 source strings, `FORK_ADDED_SOURCE_CEILING = 297` in `scripts/check-locale-coverage.py:222`, and the `--json` report shows 0 fork-added strings translated in every locale. The gate stops the debt widening but the Crowdin ownership decision in `Roadmap_Blocked.md` is a maintainer choice, not an engineering gap. No new i18n item is added for that reason.
- **Verified: multi-user and work-profile handling has no found gap.** The only user-scoped code is `UserManagerCompat.isUserUnlocked` at `FlorisApplication.kt:141` and a `LauncherApps`/`UserHandle` reference in `FlorisAppActivity.kt:223`. An IME is installed and enabled per user by the platform, and the app keeps no cross-user state, so nothing here needs a roadmap item. Recorded so the next pass does not re-derive it.
- **Verified: observability needs nothing new.** Crash staging (`CrashUtility`), a crash-report dialog that survives recreation, environment reporting in About, and the `flog*` topic logger are all present and were audited in the 2026-08-20 and 2026-08-25 passes. Raw-content diagnostics remain governed by the existing release-devtools boundary. No item added.

## Rejected Ideas

- **Rejected: a second Android 17 candidate-accessibility item.** `EditorInputConnectionBatch.kt:198-222` already implements the IME half. (Source: Android 17 behaviour changes.)
- **Rejected: a stylus handwriting item.** Implemented at `FlorisImeService.kt:605-628` and declared in `method.xml`. (Source: Android stylus input docs.)
- **Rejected: an n-gram delimiter-escaping item.** `PersonalNgramPersistence.kt:24-33` sanitizes rather than escapes, which closes the same hole. (Source: AnySoftKeyboard PR #4871.)
- **Rejected: a clipboard reveal-toggle item.** `ClipboardSensitiveRevealPolicy` already provides a time-limited per-item reveal. (Source: FlorisBoard issues #3289, #3323.)
- **Rejected: `System.load` / static-final-reflection hardening for Android 17.** Neither pattern exists in the tree. (Source: Android 17 behaviour changes.)
- **Rejected: large-screen resizability work for targetSdk 37.** The manifest sets no orientation, resizability, or aspect-ratio constraints, so the enforcement changes nothing. (Source: Android 17 behaviour changes.)
- **Rejected: vendoring FUTO swipe model weights.** The FUTO Model Weights License is not an OSI licence and would break the F-Droid build. Unchanged from 2026-08-23. (Source: FUTO model card.)
- **Rejected: training a swipe decoder in this repo.** arXiv:2606.25247 fully describes a 635K-parameter TCN at 2.5 MB and 1.54 ms, and the training data is MIT, but the work is a research project with its own hardware and evaluation needs, not a keyboard roadmap item. Measure first. (Source: arXiv:2606.25247, NLnet GestureTyping.)
- **Rejected: an on-device LLM rewrite runtime in `:app`.** ML Kit GenAI and AICore are Google Play Services surfaces and NNAPI is deprecated in favour of them; both are unusable in an F-Droid-shippable no-network base APK. If local rewrite is ever wanted it belongs behind the existing addon boundary with a self-contained runtime. (Source: Android NNAPI migration guide, ML Kit GenAI docs.)
- **Rejected: NPU/GPU acceleration for any future local model.** Published 2026 measurements show framework-induced NPU gaps up to 10×, NPU wake latency that alone exceeds a keystroke budget, and an OS-enforced GPU frequency floor that terminates inference on Galaxy S24. Short-prompt decode is memory-bound and belongs on a tuned CPU thread. (Source: arXiv:2607.05475, arXiv:2603.23640.)
- **Rejected: a generic "improve glide accuracy" item.** No local measurement exists, so any such item would be unfalsifiable. The measurement harness below is the prerequisite.
- **Rejected: an escape-hatch audit of existing features.** HeliBoard #2778 is a good warning for new work but there is no local report that any shipped SwiftFloris affordance lacks an off switch.
- **Rejected: post-quantum APK signing.** Available in Android 17, but the release key was only just put into service and multi-signer compatibility with F-Droid is unresolved. Pinning the current certificate is the higher-confidence work.
- **Deferred: an alternative-layout practice lab.** Unchanged from 2026-08-23 — still no local usability evidence.

## Sources

Repository and tracker:

- https://github.com/SysAdminDoc/SwiftFloris/issues/22
- https://github.com/SysAdminDoc/SwiftFloris/issues/23
- https://github.com/SysAdminDoc/SwiftFloris/releases/tag/v1.9.66
- https://github.com/SysAdminDoc/SwiftFloris/discussions/21

Open-source keyboards:

- https://github.com/HeliBorg/HeliBoard/releases/tag/v4.1
- https://github.com/HeliBorg/HeliBoard/releases/tag/v4.1-beta1
- https://github.com/HeliBorg/HeliBoard/issues/2778
- https://github.com/HeliBorg/HeliBoard/issues/2226
- https://github.com/florisboard/florisboard/commits/feat/k3lp/first-integration
- https://github.com/florisboard/florisboard/pull/3330
- https://raw.githubusercontent.com/florisboard/florisboard/main/ROADMAP.md
- https://github.com/florisboard/florisboard/issues/3289
- https://github.com/florisboard/florisboard/issues/3323
- https://github.com/florisboard/florisboard/issues/3201
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/pull/4871
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/pull/4879
- https://github.com/futo-org/android-keyboard/releases
- https://swipe.futo.tech/
- https://futo.tech/blog/swipe-keyboard
- https://huggingface.co/futo-org/futo-swipe/blob/main/LICENSE.md
- https://huggingface.co/datasets/futo-org/swipe.futo.org
- https://github.com/FossifyOrg/Keyboard
- https://f-droid.org/en/packages/org.fossify.keyboard/
- https://github.com/urikdev/Urik
- https://github.com/tribixbite/CleverKeys
- https://github.com/divvun/giellakbd-android
- https://github.com/onlyloveyd/LazyKeyboard

Commercial products:

- https://www.androidauthority.com/gboard-writing-tools-other-android-phones-3593589/
- https://www.windowscentral.com/software-apps/swiftkey-will-soon-require-a-microsoft-account-data-to-be-moved-to-onedrive
- https://www.heise.de/en/news/Microsoft-SwiftKey-Microsoft-account-mandatory-for-backups-from-end-of-May-11217783.html
- https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-tone-in-microsoft-swiftkey-keyboard
- https://www.sammobile.com/news/you-can-still-resize-samsung-keyboard-one-ui-8-5-just-not-as-quickly/
- https://keyboardkit.com/blog/2026/06/08/fleksy-shuts-down-their-website

Android platform and distribution:

- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://android-developers.googleblog.com/2026/06/Android-17.html
- https://developer.android.com/reference/android/view/inputmethod/TextAttribute
- https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/stylus-input-in-text-fields
- https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
- https://developers.google.com/ml-kit/genai
- https://developer.android.com/ai/gemini-nano
- https://android-developers.googleblog.com/2026/06/android-developer-verification.html
- https://support.google.com/android-developer-console/answer/16561738
- https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- https://f-droid.org/en/docs/Reproducible_Builds/

Dependencies:

- https://developer.android.com/build/releases/agp-9-4-0-release-notes
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/jetpack/androidx/releases/navigation
- https://developer.android.com/jetpack/androidx/releases/test-uiautomator
- https://developer.android.com/jetpack/androidx/releases/room3
- https://developer.android.com/jetpack/compose/bom/bom-mapping
- https://services.gradle.org/versions/current

Research:

- https://arxiv.org/abs/2606.25247
- https://arxiv.org/abs/2602.12432
- https://arxiv.org/abs/2602.06489
- https://arxiv.org/abs/2607.05475
- https://arxiv.org/abs/2603.23640
- https://arxiv.org/abs/2605.08195
- https://arxiv.org/abs/2604.19642
- https://aclanthology.org/2026.acl-industry.51/
- https://nlnet.nl/project/GestureTyping/
- https://dl.acm.org/doi/10.1145/3447526.3472059

## Open Questions

- **Does the platform already hide password characters from a physical keyboard below the IME on Android 17, or must the IME read `show_passwords_physical` itself?** This decides whether the physical-keyboard privacy item is code or documentation. It needs an Android 17 device or emulator, which this host does not have.
- **Was the published v1.9.66 APK signed with `keystore/swiftfloris-selfhost.jks` or a separate release key?** The certificate DN says "SysAdminDoc Sideload". Only the maintainer can answer, and the answer determines whether the fork-provenance page describes one key or two.

Neither blocks prioritization. Everything else in the roadmap additions has an acceptance test that is runnable on this host.
