# Research: SwiftFloris

Date: 2026-11-19. Replaces all prior research (the 2026-09-25 pass and everything before it).

Confidence labels: **Verified** (read in the source or the code during this pass), **Likely** (code reading points one way, not run), **Needs live validation** (only a phone or emulator can settle it).

## Executive Summary

SwiftFloris is an offline Android keyboard forked from FlorisBoard. It holds no `INTERNET` permission, binds no account and is installed from GitHub Releases or Obtainium. v1.9.69 (2026-11-19) is its strongest shape so far: SymSpell autocorrect with slip fixes, personal word and phrase memory, glide typing, bilingual presets, encrypted clipboard and dictionary stores, and import paths for SwiftKey, Gboard and FlorisBoard data. Since the 2026-09-25 pass, 126 commits landed and 90 of them are fixes, mostly in editor tracking for hardware keys and in word learning. The tracker is small (7 issues in the project's life) and every open report is fixed or has its close queued.

The community signal is loud and consistent: people want a keyboard that sends nothing anywhere and has no AI, and they complain that open-source keyboards trail Gboard on autocorrect and glide. SwiftFloris already answers the first and is closing the second. The highest-value direction is to keep converting SwiftKey and Fleksy users (both vendors cut features or shut down in 2026) while hardening what's already shipped.

Top opportunities, in priority order:

1. Move SQLCipher from 4.18.0 to 4.20.0. Its bundled SQLite 3.53.4 is in range for CVE-2026-119375 and CVE-2026-119377 (fixed in SQLite 3.54.0). Verified. Effort S.
2. Import the learned-words file SwiftKey now keeps in each user's own OneDrive. Microsoft moved typing data there when accounts retired and calls it human-readable. No importer reads it today. Verified gap, format unknown. Effort M.
3. Make new emoji visible on phones with no emoji font provider (de-Googled ROMs), and keep EmojiCompat working through the emoji2 1.7.0 upgrade, which turns it off by default on Android 15+. Verified. Effort S to M.
4. Finish the #26 follow-through: name the phone's touch switch in the feedback mode labels, and check key sounds under Android 17's background audio hardening. Verified and Needs live validation. Effort S.
5. Let personal phrase counts add to the language model instead of competing with it, keep a strip slot for a phrase you've typed twice, and keep the typed case of learned names. Verified (blend) and Likely (case). Effort S to M.
6. Refresh the dependency catalog (AGP 9.5.1, KSP 2.3.14, Gradle 9.9.1, Robolectric 4.18.1 and the rest), avoiding two known traps. Verified. Effort M.
7. Give Fleksy users the gestures they're looking for (swipe to cycle suggestions) and pick up small parity items: phrase glide through space, a password-only number row, removing one emoji from Recent and email domain suggestions. Verified gaps. Effort S to M each.

## Product Map

- **Core workflows**
  - Tap typing with on-device autocorrect, completions and next-word prediction (`ime/nlp/`, `CandidateSignalAssembler.kt`, `LatinLanguageProvider.kt`).
  - Glide typing through `StatisticalGlideTypingClassifier` with an adaptive touch model.
  - Multilingual typing: bilingual presets, per-token language identification, per-app language memory.
  - Clipboard history, emoji and stickers, all kept in app-private storage.
  - Migration and recovery: dictionary imports (SwiftKey JSON, Gboard, FlorisBoard, Keyman), encrypted exports and backups.
- **Personas**
  - People leaving SwiftKey after its 2026-05-31 account retirement (README migration section).
  - Privacy-focused users on de-Googled or hardened phones.
  - Multilingual typists who switch languages mid-sentence.
  - Physical keyboard, tablet and foldable users.
- **Platforms and distribution:** Android 8.0+ (`projectMinSdk=26`), target 36, compile 37 (`gradle.properties`). Signed APKs on GitHub Releases with an Obtainium channel. Apache-2.0.
- **Integrations and data flows**
  - No network permission. Merged manifest permissions: notifications, calendar read (opt-in quick insert), Advanced Protection query, vibrate, wake lock, boot and foreground service, and a signature-protected MCP bind.
  - Storage: Room plus SQLCipher for clipboard and dictionaries, Tink for keys, SAF for imports and exports.
  - Outside apps: voice input hands off to an external voice keyboard such as FUTO Voice Input (`ime/voice/ExternalVoiceInputProvider.kt`). Addon APKs mount dictionary packs after a trust check. Tasker actions run only when the user turns on external automation, and every bundle is authenticated (`ime/tasker/TaskerActionReceiver.kt:55-77`).

## Competitive Landscape

**FlorisBoard (parent).**
- Does well: Keyboard3 landed. PR #3341 ("Keyboard3 integration, part 2", 239 files) merged 2026-09-30, and #3358 (M3 Expressive and an extension UI rewrite, 154 files) merged 2026-11-02. It added a share-selected-text quick action (#3357, 2026-11-18) and a setting to show sensitive clipboard items (#3375, 2026-11-18). Word suggestions are still an open PR (#2970). Top open requests: emoji search (#45, 82 reactions), two-finger swipe (#116, 35), Ctrl/Alt/Meta/Esc/F1 keys (#229, 24).
- Learn: nothing new from the share action. SwiftFloris's task quick action already sends the selection through the system share sheet (`QuickAction.kt:152-173`).
- Avoid: a wholesale Keyboard3 merge. The fork's prediction stack is the reason it exists, and the parent still has no word suggestions.

**HeliBoard.**
- Does well: steady releases with small, useful fixes.
  - v4.2-beta1 (2026-10-04): long-press an emoji in Recent to delete it.
  - v4.2 (2026-10-12): `.combined` wordlists.
  - v4.3-beta1 (2026-10-18): delete-swipe can select whole words.
  - v4.3-beta2 (2026-11-15): less learning of wrongly uppercased words.
  - "Phrase gesture" glides through the space key to enter several words. It holds each learn for 25 seconds so a correction can cancel it (`DictionaryFacilitatorImpl.kt:69-81`).
- Learn: phrase glide and the per-emoji Recent removal.
- Avoid: copying its delayed learning outright. SwiftFloris already keeps a pending list for corrections the user takes back (`DictionaryManager.kt:462-500`).

**FUTO Keyboard.**
- Does well:
  - 0.1.28 (2026-05-04) remembers email addresses and suggests common domains.
  - 0.1.29 (2026-06-01) shipped FUTO Swipe, "26% fewer errors relative to Gboard", plus clipboard search and a trimmed 1 MB emoji compatibility font.
  - 0.1.30 (2026-08-04) added the KASROZ swipe layout.
  - A user-history word scoring over 100 is put on the strip next to the neural pick (`LanguageModelFacilitator.kt:349-350`).
- Learn: the forced user-history slot and the small bundled emoji font.
- Avoid: its model weights, which are under FUTO's own license and can't be vendored (see `Roadmap_Blocked.md`), and its screenshot-in-clipboard toggle, which needs media access.

**Thumb-Key.**
- Does well: sketch mode (PR #2023, 2026-10-06) draws a character with your finger and works with no network. 5.1.18 (2026-10-06) added search and pinning to the layouts dialog.
- Learn: offline character recognition is possible without a big model.
- Avoid: making this a near-term goal. SwiftFloris has no recognizer runtime (README voice/handwriting rows).

**AnySoftKeyboard, Unexpected Keyboard, LeanType.**
- Does well: AnySoftKeyboard 1.13-r2 (2026-09-07) improved gesture typing. Unexpected Keyboard 2.1.0 (2026-09-06) and 2.2.0 (2026-11-01) added tablet split and better dictionary selection. LeanType (a HeliBoard fork) embeds llama.cpp GGUF models and OCR.
- Learn: Unexpected Keyboard's corner swipes show how much a power-user audience values gestures.
- Avoid: LeanType's direction. An embedded LLM and camera OCR break the small, permission-light base app.

**Gboard.**
- Does well:
  - Writing tools that run "on your device or in the cloud".
  - Rambler voice cleanup, which removes filler words but needs a Pixel 11 and an internet connection.
  - A clipboard that auto-deletes after one hour by default.
  - A number row that can show only in password fields, and an emoji size slider.
- Learn: the password-only number row is offline and small.
- Avoid: cloud features. They're why people leave.

**Microsoft SwiftKey.**
- Does well: kept Editor proofreading after removing Copilot and Compose. Testing an AI voice mode that runs on the device.
- Weakness: since the account retirement, learned words live "directly in your own OneDrive folder" in "a human-readable format", and users complain about OneDrive backup nags.
- Learn: that OneDrive file is the next import source.
- Avoid: account-bound data of any kind.

**Fleksy and Typewise.**
- What happened: Fleksy "stopped developing their SDK" (KeyboardKit, 2026-06-08), and r/androidapps has four threads in the past year asking for a Fleksy replacement. Typewise runs everything locally, and the company now builds customer-service AI agents for businesses (Wikipedia).
- Learn: Fleksy users miss swipe gestures for suggestions and deletion. SwiftFloris already has the hex layout Typewise users know.
- Avoid: nothing in particular.

**Samsung Keyboard (One UI 8.5).**
- What changed: lost its toolbar resize shortcut (SamMobile).
- Learn: SwiftFloris's in-keyboard resize, fixed in v1.9.68 (#23), is an advantage worth keeping visible.

**Curated lists.**
- pluja/awesome-privacy's "Android Keyboard" section recommends AnySoftKeyboard, FlorisBoard, FUTO, HeliBoard, Indic Keyboard and Simple Keyboard. It warns that Gboard's "learning updates it sends Google can reveal the words you type". SwiftFloris isn't listed there or in offa/android-foss.
- A Privacy Guides forum thread says "people mostly use FUTO and Heliboard".
- An iTechGuides checklist asks whether "typed words, diagnostics, crash reports, or voice input leave the phone".

## Reported Issues

The tracker has 7 issues in total and no outside pull requests (all 16 PRs were dependency bots, now closed).

- **#26, no key sound or vibration on a Galaxy S24, Android 17** (opened 2026-11-17). Fixed on master in 361092218, unreleased.
  - Cause: the default mode follows the phone's touch sounds and touch vibration switches (Samsung's "Touch interactions"). With them off, the keyboard made no feedback call. And "Always vibrate" was dropped as touch feedback on Android 13+.
  - Fix: a one-tap row under each group while the phone's switch mutes it, and `USAGE_PHYSICAL_EMULATION` for the "ignore system settings" mode.
  - Checked on the API 37 emulator only. A real Samsung check is still owed.
  - The reporter's note that upstream FlorisBoard works with the same settings isn't explained by code, since upstream v0.5.2 has the same gate.
  - Follow-up: the mode labels still say "Dynamically play sounds for input events, depending on system settings" (`strings.xml:2504-2507`), which doesn't name the switch.
- **#23 (resize overshoot, autofill off screen), #24 (`:pensive` shortcode and empty emoji tags), #25 (OnePlus crash).** Fixed in v1.9.68 and v1.9.67. Closes after 3 silent days are queued.
- **#22 (customizations reset after switching keyboards)** and earlier reports: closed.
- **Discussions #19, #20 and #21:** ours is the last word on #21, and #19 and #20 have no replies yet. Nothing waiting.
- **Stale or not acted on:** none. Every report so far traced to code.

## Security, Privacy, and Reliability

- **Bundled SQLite in range for two CVEs.** Verified. SQLCipher 4.18.0 ships SQLite 3.53.4. CVE-2026-119375 (uninitialized page-cache read through a WAL file with an undersized page size) and CVE-2026-119377 (`lookupName()` authorizer bypass) affect "SQLite before 3.54.0". SQLCipher Android 4.20.0 moves to SQLite 3.54.1.
  - Exposure is low. SQLite says most CVEs "probably do not apply", and `DictionaryImporter.kt:183` refuses imported `.db` and `.sqlite` files.
  - Still, every encrypted store depends on this library.
- **No other advisories.** Verified. An OSV batch query for 18 catalog coordinates at their exact versions (SQLCipher, Tink, Kotlin, Coil, ZXing, Room, SQLite, WorkManager, Navigation, emoji2 and others) returned none.
- **Emoji on phones without a font provider.** Verified. `DefaultEmojiCompatConfig.create()` returns null without a downloadable-font provider (LineageOS without microG, GrapheneOS without sandboxed Play, /e/OS without GMS).
  - The palette then falls back to `Paint.hasGlyph` (`FlorisEmojiCompat.kt:53-65`). Emoji newer than the system font disappear without a placeholder.
  - The Unicode 18 refresh says the same: new emoji show "once your phone's emoji font can draw them" (`CHANGELOG.md:39`).
  - This hits the privacy audience hardest.
- **emoji2 1.7.0 upgrade trap.** Verified. "On Android 15 and above, EmojiCompat is disabled by default." `FlorisEmojiCompat.kt:139` would need `setUseAfterUpdatableSystemFonts(true)` to keep today's behavior.
- **Key sounds under Android 17 audio hardening.** Needs live validation. Android 17 requires "a visible activity or ... a foreground service" for audio. The keyboard plays custom sounds through `SoundPool` with `USAGE_ASSISTANCE_SONIFICATION` (`InputFeedbackController.kt:57-61`), and the docs don't say whether an IME window counts as visible.
- **Password display on Android 17.** Verified API, Needs live validation. `ShowSecretsSetting.shouldShowPhysicalInput()` exposes the new `show_passwords_physical` choice. Nothing reads it (0 hits), and the existing P3 item asks exactly this.
- **Learned words lose their case.** Likely. `LearnedWordText.normalize()` and `UserDictionaryOverlay.normalise()` lowercase every learned word (`LearnedWordText.kt:37`, `UserDictionaryOverlay.kt:180`). No case form is stored, so a learned "NASA" or "Paris" probably comes back lowercase unless the base dictionary has it.
- **Exported components.** Verified.
  - `TaskerActionReceiver` is exported without a permission. It's gated by the opt-in automation preference and bundle authentication, so it's not a finding.
  - Every other exported component is the launcher activity or a share and settings entry point, or is guarded by a system permission (`BIND_INPUT_METHOD`, `BIND_TEXT_SERVICE`, `DUMP`).
- **Signing key custody.** Verified. A 2026 study found "5,673 compromised keystores on GitHub" (arXiv 2606.21487). `keystore/` isn't tracked here. For a sideload-only app, the key is the whole update chain.
- **Recovery and rollback.** Encrypted backup, restore reconciliation and quarantine paths exist, and the open roadmap already carries their edge cases (sticker import rollback, dictionary quarantine undo, backup prune reporting, clipboard media cleanup). No new gap found.

## Architecture Assessment

- **Context blending.** `CandidateSignalAssembler.kt:226-231` takes `maxOf(bigram * 0.75, trigram, coldStart, model)`. A personal phrase can replace the model's score but never add to it. LatinIME's dynamic model, still in HeliBoard, scales a personal n-gram's probability by how often its context was seen (`dynamic_language_model_probability_utils.cpp`). Count-based interpolation of personal and model probabilities is the usual fix, and Adhikary and Vertanen (Interspeech 2023) found an adapted PPM model the best single personalizer.
- **Learning pipeline.** Lowercasing in two places (`LearnedWordText`, `UserDictionaryOverlay`) means the case fix touches the overlay, the DAO row and the suggestion path together. Keep one normalizer.
- **Hot spots.** Since 2026-09-25, 25 commit subjects mention the editor (pass-through and hardware key tracking) and 14 mention learning. Both have heavy JVM coverage. Both still owe real-device checks (ROADMAP P1 editor smoke and P2 WebView items).
- **Docs.**
  - `README.md` is 92 KB. Lines 498-679 repeat the release log that `CHANGELOG.md` already holds.
  - The README has no screenshots. The only image set dates from the 2026-05-03 fork commit.
  - The feedback mode labels are inherited FlorisBoard copy ("Dynamically play sounds for input events").
- **Tests.**
  - 49 committed Roborazzi baselines (`app/src/test/snapshots/`) cover settings, themes and addons. Roborazzi 1.79.0 adds an experimental accessibility legend capture, which could put TalkBack labels under the same visual check that the open a11y roadmap items now verify by hand.
  - Two 2025-2026 papers give recipes for synthetic typing errors and suggestion use (arXiv 2502.03560 Typoist, 2602.06489 WSTypist). Both fit the existing autocorrect harness if the slip fixes need more data.
- **Observability.** Process exits are already classified, and Android 17 memory-limiter kills are named rather than reported as crashes (`app/src/main/kotlin/dev/patrickgold/florisboard/lib/crashutility/ProcessExitHistory.kt:36-94`). No new item.
- **Not pursued this pass, on purpose.**
  - Multi-user and work profiles: no report, no competitor signal.
  - Plugin ecosystem: the addon and MCP contracts are unchanged, and MCP binding stays paused (README "MCP daemon bridge").
  - Distribution: GitHub Releases and Obtainium are unchanged.
  - Accessibility and i18n: beyond the Roborazzi legend and the emoji items, these are already carried by open roadmap items (TalkBack key echo, Switch Access, 48 dp floor, RTL motion, CLDR regional names).

## Rejected Ideas

- Recent screenshots in clipboard history (FUTO 0.1.28): needs media-read permission, which works against the permission-light base app.
- On-device LLM proofreading and OCR (LeanType): a bundled general model was rejected for the base app on 2026-09-25 and nothing has changed. A transformer addon stays in `Roadmap_Blocked.md`.
- KASROZ and ClearFlow swipe layouts (FUTO 0.1.29/0.1.30): FUTO tuned them for neural decoders. Measure the glide engine first (existing P2 items).
- Two-finger glide typing (FlorisBoard #116, 35 reactions): Nintype-style simultaneous traces are a niche and a very large build.
- A global "show sensitive clipboard items" switch (FlorisBoard #3375): weaker than SwiftFloris's 4-second per-item reveal.
- In-keyboard end-to-end encryption (KryptEY): dormant since 2023, and key management doesn't belong in a keyboard.
- Hybrid post-quantum APK signing (Android 17 v3.2 scheme): needs a new key and rotation, which risks breaking updates for every sideload user. Revisit if rotation tooling matures.
- Multi-tap and T9 layouts (FlorisBoard Keyboard3, one Reddit thread): depends on Keyboard3 contracts. Belongs in the existing compatibility ledger item.
- HeliBoard `.dict` and `.combined` import: no user has asked for it, and HeliBoard's personal words aren't in those files.
- Smart reply, rephrase and "personal style" (Gboard): cloud only.
- A "Share" quick action for selected text (FlorisBoard #3357): already covered. The task quick action hands the selection to any app through the system share sheet (`QuickAction.kt:152-173`).
- Proofreading in the style of SwiftKey Editor or Gboard writing tools: grammar beyond the slip and real-word fixes needs a model or a large rule set. The rewrite surface is already an open P2 ("Wire the rewrite surface to production, or say it is a contract").
- A rule-based cleanup of dictated text: voice goes through a separate voice keyboard, so SwiftFloris never sees that text before it's committed (`ExternalVoiceInputProvider.kt`).
- Missed-space splitting beyond the fixed list: `ImmediateAutocorrect` already handles common run-togethers ("inthe" to "in the"), and no report asks for more.

## Sources

**Project and parent**
- https://github.com/SysAdminDoc/SwiftFloris/issues/26
- https://github.com/florisboard/florisboard/pull/3341
- https://github.com/florisboard/florisboard/pull/3358
- https://github.com/florisboard/florisboard/pull/3357
- https://github.com/florisboard/florisboard/pull/3375
- https://github.com/florisboard/florisboard/pull/2970
- https://github.com/florisboard/florisboard/issues/45
- https://github.com/florisboard/florisboard/issues/116
- https://github.com/florisboard/florisboard/issues/229

**Open-source keyboards**
- https://github.com/HeliBorg/HeliBoard/releases/tag/v4.2
- https://github.com/HeliBorg/HeliBoard/releases/tag/v4.2-beta1
- https://github.com/HeliBorg/HeliBoard/releases/tag/v4.3-beta1
- https://github.com/HeliBorg/HeliBoard/releases/tag/v4.3-beta2
- https://github.com/HeliBorg/HeliBoard/issues/780
- https://github.com/Helium314/HeliBoard/blob/main/app/src/main/java/helium314/keyboard/latin/DictionaryFacilitatorImpl.kt
- https://github.com/Helium314/HeliBoard/blob/main/app/src/main/res/values/strings.xml
- https://github.com/Helium314/HeliBoard/blob/main/app/src/main/jni/src/dictionary/structure/v4/content/dynamic_language_model_probability_utils.cpp
- https://github.com/futo-org/android-keyboard/releases/tag/0.1.28
- https://github.com/futo-org/android-keyboard/releases/tag/0.1.29
- https://github.com/futo-org/android-keyboard/releases/tag/0.1.30
- https://github.com/futo-org/android-keyboard/blob/master/java/src/org/futo/inputmethod/latin/xlm/LanguageModelFacilitator.kt
- https://github.com/dessalines/thumb-key/pull/2023
- https://github.com/dessalines/thumb-key/releases/tag/5.1.18
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/releases
- https://github.com/Julow/Unexpected-Keyboard/releases
- https://github.com/LeanBitLab/LeanType
- https://github.com/pluja/awesome-privacy

**Commercial keyboards**
- https://support.microsoft.com/en-us/swiftkey-keyboard/swiftkey-backup-and-sync-with-onedrive
- https://support.microsoft.com/en-us/swiftkey-keyboard/faqs-for-copilot-changes-in-swiftkey
- https://www.androidauthority.com/swiftkey-ai-voice-mode-3709656/
- https://support.google.com/gboard/answer/17468539?hl=en
- https://support.google.com/gboard/answer/16515540?hl=en
- https://www.androidauthority.com/gboard-emoji-slider-rollout-3700850/
- https://www.androidauthority.com/gboard-clipboard-duration-slider-apk-teardown-3698099/
- https://www.androidauthority.com/gboard-settings-i-always-change-3723138/
- https://www.sammobile.com/news/you-can-still-resize-samsung-keyboard-one-ui-8-5-just-not-as-quickly/
- https://keyboardkit.com/blog/2026/06/08/fleksy-shuts-down-their-website
- https://www.howtogeek.com/ditched-gboard-for-a-keyboard-with-hexagons-and-it-cut-my-typos-in-half/
- https://en.wikipedia.org/wiki/Typewise
- https://support.apple.com/en-us/123075

**Community**
- https://www.reddit.com/r/androidapps/comments/1x6z9d3/
- https://www.reddit.com/r/androidapps/comments/1xp87f4/
- https://www.reddit.com/r/androidapps/comments/1vga5w8/
- https://www.reddit.com/r/androidapps/comments/1vx6192/
- https://www.reddit.com/r/androidapps/comments/1w2hmjv/
- https://www.reddit.com/r/androidapps/comments/1wwrawl/
- https://news.ycombinator.com/item?id=49591956
- https://hn.algolia.com/api/v1/items/48092999
- https://hn.algolia.com/api/v1/items/46090679
- https://www.androidpolice.com/switched-default-keyboard-to-open-source-alternative-and-eliminated-typos/
- https://lemmy.world/api/v3/comment/list?post_id=44660012&sort=Top&limit=50
- https://lemmy.world/api/v3/comment/list?post_id=47761033&sort=Top&limit=50&type_=All
- https://lemmy.world/api/v3/post?id=48207814
- https://lemmy.ca/api/v3/comment/list?post_id=65129652&sort=Top&limit=50&type_=All
- https://learn.microsoft.com/en-us/answers/questions/5914519/stop-onedrive-from-nagging-to-backup
- https://discuss.privacyguides.net/t/best-android-keyboard/41733
- https://www.itechguides.com/choosing-a-private-android-keyboard-a-practical-privacy-checklist/

**Android platform and standards**
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/reference/android/text/ShowSecretsSetting
- https://developer.android.com/reference/android/view/inputmethod/InputMethodSubtype
- https://developer.android.com/reference/android/os/VibrationAttributes
- http://blog.unicode.org/2026/09/announcing-unicode-standard-version-180.html
- https://cldr.unicode.org/downloads/cldr-49

**Dependencies and security**
- https://github.com/sqlcipher/sqlcipher-android/releases/tag/v4.20.0
- https://github.com/sqlcipher/sqlcipher/releases/tag/v4.18.0
- https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=CVE-2026-119375
- https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=CVE-2026-119377
- https://www.sqlite.org/cves.html
- https://api.osv.dev/v1/querybatch
- https://developer.android.com/jetpack/androidx/releases/emoji2
- https://developer.android.com/jetpack/androidx/releases/compose-ui
- https://developer.android.com/jetpack/androidx/releases/core
- https://developer.android.com/jetpack/androidx/releases/navigation
- https://developer.android.com/jetpack/androidx/releases/sqlite
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/build/releases/agp-9-5-0-release-notes
- https://github.com/google/ksp/releases/tag/2.3.14
- https://github.com/robolectric/robolectric/releases/tag/robolectric-4.18
- https://github.com/takahirom/roborazzi/releases/tag/1.79.0
- https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.13.0
- https://github.com/tink-crypto/tink-java/releases/tag/v1.24.0
- https://github.com/coil-kt/coil/blob/main/CHANGELOG.md
- https://services.gradle.org/versions/current

**Research papers**
- https://arxiv.org/abs/2606.25247
- https://arxiv.org/abs/2502.03560
- https://arxiv.org/abs/2602.06489
- https://arxiv.org/abs/2606.21487
- https://arxiv.org/abs/1811.03604
- https://www.keithv.com/pub/personalization/adhikary23_interspeech.pdf

## Open Questions

1. What is the exact format of SwiftKey's OneDrive typing-data file? Microsoft says it's human-readable but doesn't publish the format. The importer needs one real file from a SwiftKey install with OneDrive backup turned on.
2. Is a bigger APK acceptable for a bundled emoji font? The stock compatibility font adds several megabytes, and FUTO cut theirs to 1 MB. If not, a trimmed font has to be built and maintained.
3. Should SwiftFloris be proposed for pluja/awesome-privacy's Android Keyboard section? It's a public post on someone else's repo, so it needs the maintainer's yes.
