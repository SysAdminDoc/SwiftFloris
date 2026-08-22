# Research: SwiftFloris

Date: 2026-08-22. Refreshes the 2026-08-21 research after v1.9.63 implementation.

## Executive Summary

SwiftFloris v1.9.63 is a Kotlin and Jetpack Compose Android input method for Android 8 and newer (gradle.properties, settings.gradle.kts, app/build.gradle.kts). Its strongest shape is already clear: the base APK has no INTERNET permission, typing and personalization stay local, clipboard and dictionary stores are encrypted, language and layout assets can arrive through signed extensions, and release evidence is generated locally (app/src/main/AndroidManifest.xml, app/src/main/config/trust-capabilities.json, app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard, app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary, scripts/release-evidence.ps1). The adaptive-touch privacy boundary is now closed in v1.9.63. The remaining highest-value direction is to remove the language and distribution dead ends users can hit. New cloud services or another large input engine would weaken the product's clearest advantage.

Top opportunities:

1. **Verified: keep raw-content developer overlays out of release builds.** Release Settings exposes Devtools, while overlays render the primary clipboard, surrounding text, selected text, composing text, and spelling words (app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/OtherScreen.kt:131-135, app/src/main/kotlin/dev/patrickgold/florisboard/app/devtools/DevtoolsOverlay.kt:70-143, :177-193).
2. **Verified: close the language-installation dead end reported in discussion #21.** Portuguese is bundled, but the language-pack screen only explains extension import and does not route users to Add keyboard language (app/src/main/assets/ime/dict/pt.fldic, app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/LocalizationScreen.kt:85-100, app/src/main/res/values/strings.xml:375-378, [discussion #21](https://github.com/SysAdminDoc/SwiftFloris/discussions/21)).
3. **Verified: honor the Android 16 writing-tools opt-out.** RewriteRouter checks consent and sensitive fields but never receives EditorInfo.isWritingToolsEnabled() ([Android EditorInfo](https://developer.android.com/reference/android/view/inputmethod/EditorInfo), app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/RewriteRouter.kt:60-79).
4. **Verified: subscribe to Advanced Protection changes.** The current policy queries state on demand, while Android instructs apps to register a runtime callback ([Android Advanced Protection](https://developer.android.com/privacy-and-security/advanced-protection-mode), app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/AdvancedProtectionPolicy.kt:75-90).
5. **Verified: make the F-Droid recipe build the artifact it declares.** The recipe expects app-release-unsigned.apk, but the release build always uses either release or debug signing. The same recipe declares a KnownVuln anti-feature with "None known" and leaves the upstream binary signing-key allowlist empty (fdroid/io.github.sysadmindoc.swiftfloris.yml:24-41, app/build.gradle.kts:116-133, :233-243).
6. **Verified: add a base-APK 16 KB page-size gate.** Addon verification checks ZIP alignment, but no equivalent release gate inspects the SQLCipher native libraries in the final app APK ([Android page-size guidance](https://developer.android.com/guide/practices/page-sizes), scripts/verify-addon-apk.sh:210-237, app/build.gradle.kts).
7. **Verified: use host locale signals without overriding explicit choices.** EditorInfo.hintLocales is only printed in a debug summary, and the IME does not query the foreground app's Android 13 per-app locale even though current IMEs may do so without the cross-app locale permission ([Android EditorInfo](https://developer.android.com/reference/android/view/inputmethod/EditorInfo), [Android LocaleManager](https://developer.android.com/reference/android/app/LocaleManager), app/src/main/kotlin/dev/patrickgold/florisboard/lib/util/DebugSummarizeUtils.kt:47-49).
8. **Verified: turn existing verification tools into enforceable gates.** Kover is enabled without coverage rules, failed reproducible builds discard their container output, and one candidate-row trace can remain open after a composition failure (app/build.gradle.kts:331-333, utils/repr_build/run.sh:90-108, app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/CandidatesRow.kt:97-109).
9. **Verified: remove small sources of i18n and repository drift.** Provider labels are hard-coded in English, backup retention uses a non-plural resource, build-tool documentation disagrees with machine-readable pins, and two Python bytecode files are tracked (app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/SubtypeEditorScreen.kt:403-415, app/src/main/res/values/strings.xml:1838, gradle/tools.versions.toml, CONTRIBUTING.md, docs/REPRODUCIBLE_BUILDS.md, scripts/__pycache__).

## Product Map

- **Core workflows, verified:** tap and glide typing, local autocorrection and prediction, bilingual Latin subtypes, emoji and sticker input, encrypted clipboard history, personal dictionaries, themes, per-app profiles, local backup and restore, settings search, and external voice-IME handoff (README.md:40-64, app/src/main/kotlin/dev/patrickgold/florisboard/ime).
- **User personas, likely:** privacy-conscious Android users, former SwiftKey users who need local migration and multilingual behavior, and users who customize layouts, themes, dictionaries, or automation. The product copy and migration tools target these groups directly (README.md:30-64, README.md:184-263, app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/migration).
- **Platforms and distribution, verified:** Android API 26 minimum, API 36 target, API 37 compile platform, GitHub Releases and Obtainium as active channels, and an F-Droid recipe that is not yet publishable (gradle.properties, fastlane/obtainium, fdroid/io.github.sysadmindoc.swiftfloris.yml, Roadmap_Blocked.md).
- **Integrations and data flows, verified:** base-app data stays on device; explicit exports use the Storage Access Framework; Tasker, calendar, voice IMEs, addons, and the parked MCP bridge are permission-gated local integrations (app/src/main/AndroidManifest.xml, docs/PRIVACY_AND_AI.md, docs/THREAT_MODEL.md, app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon).
- **Partial capabilities, verified:** local voice recognition, translation, model-backed rewrite, production handwriting recognition, and broad CJK data remain provider or dataset slots rather than complete shipping paths (app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice, ime/nlp/translation, ime/smartcompose, ime/handwriting, Roadmap_Blocked.md).

## Competitive Landscape

- **FlorisBoard and HeliBoard:** Both expose current editor-compatibility and language-switching demand. Duplicate commits, stale cursor state, and configurable subtype cycles have open reports ([FlorisBoard #3313](https://github.com/florisboard/florisboard/issues/3313), [FlorisBoard #3328](https://github.com/florisboard/florisboard/issues/3328), [HeliBoard #2702](https://github.com/HeliBorg/HeliBoard/issues/2702), [HeliBoard #2744](https://github.com/HeliBorg/HeliBoard/issues/2744)). SwiftFloris should keep invariant-based editor tests and add a configurable cycle subset. It should not copy app-specific patches without a reproducible contract.
- **FUTO Keyboard:** FUTO publishes active releases, a large MIT swipe dataset, locale-hint work, and an explicit clipboard tracking-parameter feature ([release 0.1.30](https://github.com/futo-org/android-keyboard/releases/tag/0.1.30), [locale hints PR](https://github.com/futo-org/android-keyboard/pull/1892), [clean-link PR](https://github.com/futo-org/android-keyboard/pull/1833), [dataset](https://huggingface.co/datasets/futo-org/swipe.futo.org)). SwiftFloris should learn from its measurable input work and explicit privacy actions. It should not vendor FUTO's separately licensed model weights.
- **AnySoftKeyboard:** Its language-pack architecture remains a useful example of keeping layouts and dictionaries outside the base application, while its editor tracker shows the cost of long-lived compatibility debt ([LanguagePack](https://github.com/AnySoftKeyboard/LanguagePack), [duplicate-text issue](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4812)). SwiftFloris should preserve typed addon contracts and avoid an unbounded legacy surface.
- **fcitx5-android, Trime, Keyman, Indic Keyboard, and Traditional T9:** These projects do complex script engines, schemas, candidate interfaces, and device-specific layouts well ([fcitx5-android](https://github.com/fcitx5-android/fcitx5-android), [Trime](https://github.com/osfans/trime), [Keyman Android](https://help.keyman.com/products/android/version-history/), [Indic Keyboard](https://github.com/smc/Indic-Keyboard), [Traditional T9](https://github.com/sspanak/tt9)). SwiftFloris should keep those engines behind packages or addons. Pulling a Rime-style or full CJK runtime into :app would add data, native-code, and maintenance costs that conflict with the small auditable base.
- **Thumb-Key, Unexpected Keyboard, FlickBoard, and 8VIM:** These projects show that alternative layouts need clear gesture feedback and a complete non-gesture path ([Thumb-Key](https://github.com/dessalines/thumb-key), [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard), [FlickBoard](https://codeberg.org/natkr/flickboard), [8VIM](https://github.com/8VIM/8VIM)). SwiftFloris already ships split, one-handed, honeycomb, terminal, and imported layouts. New gesture ideas should follow correctness and accessibility work.
- **Gboard:** Writing Tools, advanced voice typing, and federated personalization show the feature ceiling, but some operations require network processing or transmit model learnings ([Writing Tools](https://support.google.com/gboard/answer/16515540), [advanced voice typing](https://support.google.com/gboard/answer/11197787), [learning and audio donation](https://support.google.com/gboard/answer/12373137)). SwiftFloris should copy the explicit editor opt-out and quality discipline, not the data path.
- **Microsoft SwiftKey:** Multilingual prediction, Flow, themes, clipboard tools, and local use without an account remain table stakes. Backup and Sync uses OneDrive, while standalone SwiftKey Accounts ended on 2026-05-31 ([SwiftKey help](https://support.microsoft.com/en-us/swiftkey), [privacy](https://support.microsoft.com/en-us/swiftkey-keyboard/microsoft-swiftkey-keyboard-privacy-questions-and-your-data), [account changes](https://support.microsoft.com/en-us/swiftkey-keyboard/account)). SwiftFloris should keep user-owned import, export, and rollback. It should not recreate account-bound sync.
- **Samsung Keyboard:** Downloadable languages and Writing Assist make language management and rewriting visible in one settings path ([keyboard settings](https://www.samsung.com/us/support/answer/ANS10001592/), [Writing Assist](https://www.samsung.com/us/support/answer/ANS10000943/)). SwiftFloris should match the discoverability while keeping one no-network artifact instead of splitting behavior between local and cloud modes.
- **Grammarly, QuillBot, and LanguageTool:** These products confirm demand for proofreading and rewrite assistance, but Android offerings depend on accounts, cloud services, Accessibility overlays, or floating assistants ([Grammarly Android](https://support.grammarly.com/hc/en-us/articles/15606282682637-Grammarly-for-Android-user-guide), [QuillBot Android](https://help.quillbot.com/hc/en-us/articles/39335519701143-How-does-the-Quillbot-Keyboard-and-Writing-Assistant-work-on-Android), [LanguageTool platforms](https://help.languagetool.org/hc/en-us/articles/39254499343383-Where-can-I-access-the-LanguageTool-Writing-Assistant)). SwiftFloris should keep deterministic proofreading and optional local providers inside the IME. It should avoid overlay permissions and quotas.
- **Typewise, Fleksy, and Yandex Keyboard:** Their offline tiers, gesture systems, themes, connected mini-apps, ads, search, and AI content show both willingness to pay for typing features and the trust cost of connected surfaces ([Typewise support](https://www.typewise.app/support), [Fleksy Play listing](https://play.google.com/store/apps/details?id=com.syntellia.fleksy.keyboard), [Yandex help](https://yandex.com/support/keyboard-android/en/)). SwiftFloris should keep core typing and accessibility features available to every user and reject ads, embedded search, and content marketplaces.

## Reported Issues

- **Verified: no open GitHub issue or pull request was present on 2026-08-21.** The [open issue list](https://github.com/SysAdminDoc/SwiftFloris/issues?q=is%3Aissue+is%3Aopen) and [open pull request list](https://github.com/SysAdminDoc/SwiftFloris/pulls?q=is%3Apr+is%3Aopen) were empty.
- **Verified: discussion #21 is actionable user demand.** “How do I download new languages?” was opened on 2026-08-10 and had no reply or accepted answer on 2026-08-21 ([discussion #21](https://github.com/SysAdminDoc/SwiftFloris/discussions/21)). Portuguese is already bundled in app/src/main/assets/ime/dict/pt.fldic; the missing piece is a clear route to create a subtype and a clear distinction between built-in resources and extension packs.
- **Verified: discussions #19 and #20 do not justify work.** #19 is an announcement and #20 asks which layouts and dictionaries users want; both had zero replies on 2026-08-21 ([#19](https://github.com/SysAdminDoc/SwiftFloris/discussions/19), [#20](https://github.com/SysAdminDoc/SwiftFloris/discussions/20)).
- **Verified: closed issues #1 and #9 stay excluded.** The empty-emoji crash and SymSpell memory or latency report are fixed and documented, so neither belongs in the incomplete roadmap ([issue #1](https://github.com/SysAdminDoc/SwiftFloris/issues/1), [issue #9](https://github.com/SysAdminDoc/SwiftFloris/issues/9), CHANGELOG.md).
- **Likely: broad editor compatibility remains the main ecosystem risk, not a confirmed local regression.** Eight researched keyboards carry current duplicate-text, Backspace, Enter, cursor, or autocorrect reports. SwiftFloris already has editor invariant tests and a blocked foreground InputConnection smoke test, so a generic duplicate roadmap item would add no new action (app/src/test/kotlin/dev/patrickgold/florisboard/ime/editor, Roadmap_Blocked.md, [HeliBoard #2702](https://github.com/HeliBorg/HeliBoard/issues/2702), [FUTO #2246](https://github.com/futo-org/android-keyboard/issues/2246)).

## Security, Privacy, and Reliability

- **Resolved in v1.9.63: adaptive-touch recording no longer crosses the private-session boundary.** TextKeyboardLayout now applies SuggestionPrivacyPolicy before refinement and persistence, covering password variation, manual incognito, and app-declared IME_FLAG_NO_PERSONALIZED_LEARNING. Focused behavior tests cover normal, password, incognito, and host-declared no-learning paths (app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt, app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SuggestionPrivacyPolicy.kt, app/src/test/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInfoSensitiveFieldReplayTest.kt).
- **Verified: release devtools can display typed and copied secrets.** Navigation and preference visibility are not gated by BuildConfig.DEBUG, and three overlays interpolate raw user content (app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/OtherScreen.kt:131-135, app/src/main/kotlin/dev/patrickgold/florisboard/app/Routes.kt:297-312, :441-452, app/src/main/kotlin/dev/patrickgold/florisboard/app/devtools/DevtoolsOverlay.kt:108-143, :177-193). Debugging value does not justify keeping these raw surfaces in a production APK.
- **Verified: writing-tool consent is incomplete.** Android 16 lets an editor disable generative text replacement through EditorInfo.isWritingToolsEnabled(), but RewriteRequest contains only input type and IME options ([Android EditorInfo](https://developer.android.com/reference/android/view/inputmethod/EditorInfo), app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/RewriteProvider.kt, RewriteRouter.kt:60-79). Ordinary prediction and spell correction are not writing tools and should remain unaffected.
- **Verified: Advanced Protection can remain stale until the next policy query.** AdvancedProtectionPolicy.decide() reads a snapshot, but no code registers AdvancedProtectionManager.Callback. Android calls the callback once on registration and on every state change ([Advanced Protection API](https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager), app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/AdvancedProtectionPolicy.kt).
- **Verified: the release package lacks a 16 KB native compatibility proof.** scripts/verify-addon-apk.sh checks addon ZIP alignment, while the app release flow does not inspect the final APK's ZIP alignment or ELF load segments. SQLCipher 4.18.0 contributes native libraries, so dependency version alone is not proof ([Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes), gradle/libs.versions.toml, scripts/release-evidence.ps1).
- **Verified: the F-Droid binary recipe cannot match its declared output.** release always receives a signing config, including debug signing when the maintainer key is absent, while the YAML expects an unsigned APK. Reproducible binary comparison also needs the upstream certificate allowlist ([F-Droid reproducible builds](https://f-droid.org/en/docs/Reproducible_Builds/), [metadata reference](https://f-droid.org/en/docs/Build_Metadata_Reference/), app/build.gradle.kts:116-133, :233-243, fdroid/io.github.sysadmindoc.swiftfloris.yml).
- **Verified: Kotlin 2.4.10 matches CVE-2026-53914, with limited applicability here.** The advisory concerns unsafe build-cache metadata deserialization; the repository does not configure an untrusted shared or remote cache. The patched line is still prerelease, so the existing blocker to wait for Kotlin 2.4.20 final remains correct ([GitHub advisory](https://github.com/advisories/GHSA-r937-wjx7-w2jp), [Kotlin releases](https://kotlinlang.org/docs/releases.html), Roadmap_Blocked.md).
- **Verified: Tink 1.23.0 is already past the ChunkedMac fix.** Upstream identifies 1.21.0 and earlier as affected, and the constant-time comparison fix predates 1.22.0. SwiftFloris also has no ChunkedMac call site ([upstream issue](https://github.com/tink-crypto/tink-java/issues/75), [Tink 1.22.0](https://github.com/tink-crypto/tink-java/releases/tag/v1.22.0), gradle/libs.versions.toml). Prior “patched version unknown” notes are stale.
- **Verified: failed reproducible builds lose the evidence needed to diagnose them.** docker cp runs only after a successful assembly, then the container is removed unconditionally (utils/repr_build/run.sh:90-108).
- **Verified: candidate-row tracing accepts an unclosed section by design.** The comment states that a composition failure can leave the Perfetto section open (app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/CandidatesRow.kt:97-109). A benchmark marker must not corrupt the trace it is supposed to explain.

## Architecture Assessment

- **Verified: the module boundary is appropriate.** :app owns the IME and Settings, :benchmark owns device measurements, :addons:dictionary-pack-sample demonstrates a package boundary, and reusable Android, Compose, color, Kotlin, and Snygg code lives under :lib (settings.gradle.kts). Large language or model runtimes should continue to enter through typed provider or addon contracts.
- **Verified: provider metadata has no central owner.** SubtypeEditorScreen builds an English-only map for Latin and Han providers and carries a TODO asking where it belongs (app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/SubtypeEditorScreen.kt:403-415). A provider registry should own stable IDs, localized labels, capabilities, and availability.
- **Verified: test quantity is not a coverage contract.** Kover 0.9.9 is enabled with JaCoCo, but no verification rule protects privacy policy, backup, migration, addon enrollment, editor, or dictionary packages (app/build.gradle.kts:331-333, gradle/libs.versions.toml). Package and branch floors should target critical code, not a repository-wide vanity percentage.
- **Verified: repository hygiene misses generated Python artifacts.** Two .pyc files are tracked and .gitignore has no __pycache__ or *.pyc rule (scripts/__pycache__/check-locale-coverage.cpython-313.pyc, scripts/__pycache__/verify-targetsdk37-shadow.cpython-313.pyc, .gitignore).
- **Verified: build documentation conflates two valid toolchains.** Host release work uses JDK 21, while the reproducible and F-Droid container intentionally uses JDK 17. CONTRIBUTING.md still names Build Tools 36.0.0 even though gradle/tools.versions.toml pins 37.0.0 (README.md:268-305, CONTRIBUTING.md, docs/REPRODUCIBLE_BUILDS.md, gradle/tools.versions.toml).
- **Verified: the Room 3 blocker is stale, but the migration is not a direct swap.** Room 3.0.0 became stable on 2026-07-01 and 3.0.1 followed on 2026-07-29; SQLCipher 4.18.0 now supports it ([Room 3 releases](https://developer.android.com/jetpack/androidx/releases/room3), [migration guide](https://developer.android.com/training/data-storage/room/migration-2-to-3), [SQLCipher 4.18.0](https://github.com/sqlcipher/sqlcipher-android/releases/tag/v4.18.0)). Roadmap_Blocked.md:176-185 still says alpha. The existing item takes precedence, and its next step is an encrypted continuity and rollback spike.
- **Verified: UI coverage is broad, with one standards-documentation error.** Committed and generated baselines cover light, AMOLED, compact, wide, RTL, 200 percent font scale, high contrast, setup, backup, voice, and empty states (app/src/test/snapshots, docs/ACCESSIBILITY.md). The same document conflates Android's 48 dp guidance with WCAG 2.5.5, which specifies 44 CSS pixels at AAA ([WCAG target size](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html)).
- **Category coverage:** Security and privacy are represented by the P0 items and Advanced Protection. Accessibility keeps its existing device blockers and gains a standards-doc guard. I18n covers language discovery, locale hints, provider labels, cycle control, and plurals. Observability and testing cover trace balance, failed-build evidence, Kover, and repository hygiene. Documentation and distribution cover toolchain facts, 16 KB packaging, and F-Droid. Plugin, mobile, offline, migration, and upgrade strategy are represented by provider boundaries, Android APIs, reproducible builds, the existing migration tools, and the corrected Room 3 and Kotlin dispositions (Roadmap_Blocked.md, README.md, settings.gradle.kts). Multi-user expansion is consciously excluded because no repository report requests it and correct work-profile behavior still requires the device evidence already tracked in Roadmap_Blocked.md.

## Rejected Ideas

- **Cloud rewriting, translation, search, GIF stores, telemetry, and federated learning:** These require a network or server-side data path and conflict with the base APK's enforced no-network contract ([Gboard Writing Tools](https://support.google.com/gboard/answer/16515540), [federated Gboard language models](https://research.google/pubs/federated-learning-of-gboard-language-models-with-differential-privacy/), app/src/main/AndroidManifest.xml).
- **Vendoring FUTO Swipe model weights:** The dataset is MIT, but the weights use the FUTO Model Weights License with attribution and redistribution terms. Use the dataset for local evaluation; do not place the weights in the Apache-2.0 base APK ([dataset](https://huggingface.co/datasets/futo-org/swipe.futo.org), [model license](https://huggingface.co/futo-org/futo-swipe/blob/main/LICENSE.md)).
- **A direct Credential Manager integration:** Android already routes credentials through standard inline autofill and hides credential contents until selection. The correct work is the existing device compatibility check, not another dependency ([IME autofill](https://developer.android.com/identity/autofill/ime-autofill), [Credential Manager autofill](https://developer.android.com/identity/autofill/credential-manager-autofill), Roadmap_Blocked.md).
- **Connectionless handwriting before a recognizer ships:** method.xml advertises handwriting and the service has a facade, but production recognition remains blocked. Adding another callback surface first would deepen a nonfunctional path ([stylus handwriting](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/stylus-input-in-text-fields), app/src/main/res/xml/method.xml, Roadmap_Blocked.md).
- **A full Room 3 migration as an immediate dependency bump:** Stable availability clears the planning blocker, not the encrypted database continuity, historical schema, synchronous DAO, or rollback work ([migration guide](https://developer.android.com/training/data-storage/room/migration-2-to-3), app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider, ime/dictionary).
- **Changing the Obtainium regular expression without a failing import:** The outer JSON correctly escapes an inner JSON string, and the checker fixture expects the decoded app-release.*\.apk pattern (fastlane/obtainium/stable.json, fastlane/obtainium/preview.json, scripts/test-check-public-doc-version-pins.py:129-163). No verified defect remains.
- **A new generic editor-compatibility item:** The ecosystem signal is real, but SwiftFloris already has editor invariant tests and a blocked real-app smoke item. A second roadmap entry would duplicate that work (app/src/test/kotlin/dev/patrickgold/florisboard/ime/editor, Roadmap_Blocked.md).
- **Embedding Rime, a full CJK engine, or another large model in :app:** Current data and licensing blockers remain, while the addon boundary already exists ([fcitx5-android](https://github.com/fcitx5-android/fcitx5-android), [Trime](https://github.com/osfans/trime), app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon, Roadmap_Blocked.md).
- **Accessibility-overlay writing assistance:** Grammarly documents battery and permission failures for its floating assistant. A native IME already has the correct editor boundary ([Grammarly permission persistence](https://support.grammarly.com/hc/en-us/articles/46607321843981-I-keep-having-to-re-enable-permissions-for-Grammarly-they-don-t-stay-on)).

## Sources

Repository and tracker:

- https://github.com/SysAdminDoc/SwiftFloris/discussions/21
- https://github.com/SysAdminDoc/SwiftFloris/discussions/19
- https://github.com/SysAdminDoc/SwiftFloris/discussions/20
- https://github.com/SysAdminDoc/SwiftFloris/issues/1
- https://github.com/SysAdminDoc/SwiftFloris/issues/9

Open-source keyboards:

- https://github.com/florisboard/florisboard/releases/tag/v0.5.2
- https://github.com/florisboard/florisboard/issues/3313
- https://github.com/florisboard/florisboard/issues/3328
- https://github.com/HeliBorg/HeliBoard/releases/tag/v4.0
- https://github.com/HeliBorg/HeliBoard/issues/2702
- https://github.com/HeliBorg/HeliBoard/issues/2744
- https://github.com/HeliBorg/HeliBoard/issues/2736
- https://github.com/futo-org/android-keyboard/releases/tag/0.1.30
- https://github.com/futo-org/android-keyboard/pull/1833
- https://github.com/futo-org/android-keyboard/pull/1892
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/releases/tag/1.13-r1
- https://github.com/AnySoftKeyboard/LanguagePack
- https://github.com/fcitx5-android/fcitx5-android/releases/tag/0.1.3
- https://github.com/osfans/trime/releases/tag/v3.3.11
- https://github.com/dessalines/thumb-key/releases/tag/v5.1.16
- https://github.com/Julow/Unexpected-Keyboard/releases/tag/v2.0.4
- https://github.com/sspanak/tt9/releases/tag/v64.0
- https://github.com/tribixbite/CleverKeys/releases/tag/v1.5.0
- https://github.com/FossifyOrg/Keyboard/releases/tag/v1.9.1
- https://github.com/8VIM/8VIM/releases/tag/v0.17.5
- https://help.keyman.com/products/android/version-history/

Commercial products:

- https://support.google.com/gboard/answer/16515540
- https://support.google.com/gboard/answer/11197787
- https://support.google.com/gboard/answer/12373137
- https://support.microsoft.com/en-us/swiftkey
- https://support.microsoft.com/en-us/swiftkey-keyboard/microsoft-swiftkey-keyboard-privacy-questions-and-your-data
- https://support.microsoft.com/en-us/swiftkey-keyboard/account
- https://www.samsung.com/us/support/answer/ANS10001592/
- https://www.samsung.com/us/support/answer/ANS10000943/
- https://support.grammarly.com/hc/en-us/articles/15606282682637-Grammarly-for-Android-user-guide
- https://support.grammarly.com/hc/en-us/articles/46607321843981-I-keep-having-to-re-enable-permissions-for-Grammarly-they-don-t-stay-on
- https://help.quillbot.com/hc/en-us/articles/39335519701143-How-does-the-Quillbot-Keyboard-and-Writing-Assistant-work-on-Android
- https://help.languagetool.org/hc/en-us/articles/39254499343383-Where-can-I-access-the-LanguageTool-Writing-Assistant
- https://www.typewise.app/support
- https://play.google.com/store/apps/details?id=com.syntellia.fleksy.keyboard
- https://yandex.com/support/keyboard-android/en/

Platform, standards, and distribution:

- https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- https://developer.android.com/reference/android/view/inputmethod/InputMethodInfo
- https://developer.android.com/reference/android/app/LocaleManager
- https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager
- https://developer.android.com/privacy-and-security/advanced-protection-mode
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/stylus-input-in-text-fields
- https://developer.android.com/identity/autofill/ime-autofill
- https://developer.android.com/identity/autofill/credential-manager-autofill
- https://developer.android.com/build/releases/gradle-plugin-roadmap
- https://developer.android.com/jetpack/androidx/releases/room3
- https://developer.android.com/training/data-storage/room/migration-2-to-3
- https://developer.android.com/guide/topics/resources/string-resource#Plurals
- https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html
- https://f-droid.org/en/docs/Reproducible_Builds/
- https://f-droid.org/en/docs/Build_Metadata_Reference/

Research, dependencies, and security:

- https://arxiv.org/abs/2606.25247
- https://huggingface.co/datasets/futo-org/swipe.futo.org
- https://huggingface.co/futo-org/futo-swipe/blob/main/LICENSE.md
- https://research.google/pubs/spatial-model-personalization-in-gboard/
- https://research.google/pubs/federated-learning-of-gboard-language-models-with-differential-privacy/
- https://research.google/pubs/synthesizing-and-adapting-error-correction-data-for-mobile-large-language-model-applications/
- https://citizenlab.ca/research/vulnerabilities-across-keyboard-apps-reveal-keystrokes-to-network-eavesdroppers/
- https://github.com/advisories/GHSA-r937-wjx7-w2jp
- https://github.com/tink-crypto/tink-java/issues/75
- https://github.com/tink-crypto/tink-java/releases/tag/v1.22.0
- https://github.com/sqlcipher/sqlcipher-android/releases/tag/v4.18.0
- https://kotlin.github.io/kotlinx-kover/gradle-plugin/

Community and curated lists:

- https://github.com/ideas-no996/awesome-android-keyboards
- https://github.com/pluja/awesome-privacy
- https://discuss.privacyguides.net/t/what-keyboard-are-you-using-on-android/15973
- https://discuss.privacyguides.net/t/heliboard-offline-keyboard-for-android/28093
- https://news.ycombinator.com/item?id=40831489
- https://forum.languagetool.org/t/languagetool-on-android/11647
- https://www.reddit.com/r/Swiftkey/comments/1ugza0e/swiftkey_users_post_every_bug_problem_suggestion/

## Open Questions

None. Public evidence and repository inspection are sufficient to prioritize every addition below; device-only validation and external-account work remain explicitly blocked in Roadmap_Blocked.md.
