# Research: SwiftFloris

Date: 2026-08-23. Replaces the 2026-08-22 report after a fresh repository, tracker, ecosystem, platform, dependency, security, community, and research review.

## Executive Summary

SwiftFloris v1.9.63 has a coherent product identity: a local-first Android keyboard with no `INTERNET` permission, encrypted sensitive stores, explicit import and export, signed addon boundaries, and unusually deep local release evidence. The current source tree is far ahead of the public install path, however. GitHub's latest release is v1.9.59, it has no downloadable assets, and master was 52 commits ahead at the start of this pass. The existing blocked release item remains the right owner for that distribution problem.

This pass found four net-new implementation gaps. Two are release or data-safety failures and belong at P0. Two weaken the backup and privacy contracts and belong at P1. Every other candidate was already represented, blocked by external evidence, fixed in v1.9.63, or too speculative to displace verified work.

Top findings:

1. **Verified: the clean-checkout trust gate fails against its own live pins.** `trust-capabilities.json` declares Build Tools 36.0.0 and SQLCipher 4.17.0, while the Gradle catalogs declare 37.0.0 and 4.18.0. Both `scripts/check-trust-capabilities.py` and its self-test fail before release evidence can complete (app/src/main/config/trust-capabilities.json:7 and :46, gradle/tools.versions.toml:2, gradle/libs.versions.toml, scripts/check-trust-capabilities.py:340 and :449-451, scripts/release-evidence.ps1:281).
2. **Verified: Android 16 QPR2 added a backup mode that the app does not govern.** The manifest enables backup and the active rules define cloud and device transfer only. Android's current documentation says a missing mode is fully enabled except for no-backup and cache locations. Exact transport behavior still needs API 36.1 validation, but an absent cross-platform policy is already a fail-open configuration defect (app/src/main/AndroidManifest.xml:111-113, app/src/main/res/xml/data_extraction_rules.xml:33-237).
3. **Verified: the advertised no-cloud posture is broader than the implementation.** Android Auto Backup can upload selected preferences and customization data to the user's Google Drive. SwiftFloris itself has no network permission, account, telemetry, or cloud learning, but README and Settings use an unqualified “No cloud” claim (README.md:364-384, app/src/main/res/values/strings.xml:276 and :290, app/src/main/res/xml/data_extraction_rules.xml).
4. **Verified: adaptive-touch state is missing from the canonical persisted-data inventory.** `AdaptiveTouchModel` stores per-subtype offsets in `adaptive_touch_model.xml`, but `BackupDataInventory` does not classify that store even though it claims every persisted store is listed. Current allowlists normally keep the file out of Android backup, but omission UI and parity tests cannot describe or protect an entry they do not know exists (AdaptiveTouchModel.kt:36-47 and :83-92, BackupDataInventory.kt:89-101).
5. **Verified: adaptive-touch runtime privacy was fixed, while the public description was not.** v1.9.63 prevents learning in password, incognito, and host-declared no-learning sessions. `docs/PRIVACY_AND_AI.md` still says the model updates after every key press and sees every tap coordinate (docs/PRIVACY_AND_AI.md:97-106, TextKeyboardLayout.kt, SuggestionPrivacyPolicy.kt).
6. **Verified: an interactive backup is reported as successful without reading the SAF document back.** The scheduled path compares the local archive and destination digest before publication and authenticates old encrypted archives before rotation. The interactive path writes once, closes its workspace, and shows success. It does not prove that a provider stored the complete document or that the archive can be parsed (BackupScreen.kt:392-405, ScheduledBackupSaf.kt:74-80 and :164-213).
7. **Verified: two threat-model facts are stale.** The document pins SQLCipher 4.17.0 and says the base APK ships zero native code. The app now uses SQLCipher 4.18.0, whose AAR contains native libraries. The existing documentation-drift roadmap item should absorb these new facts (docs/THREAT_MODEL.md:190-194 and :243-250, gradle/libs.versions.toml).
8. **Verified: the most important previously researched items remain valid.** Release-only devtools, language setup, the Android 16 writing-tools opt-out, Advanced Protection callbacks, a truly unsigned F-Droid artifact, final-APK 16 KB verification, locale hints, critical coverage floors, subtype-cycle control, and precise documentation gates are still incomplete (ROADMAP.md).

The best product direction remains local quality, understandable recovery, language reach, and verifiable distribution. A cloud account, a second monolithic language engine, or a broad assistant surface would spend trust and maintenance budget without fixing the current release and data contracts.

## Product Map

- **Verified: product and stack.** SwiftFloris is a Kotlin and Jetpack Compose input method for Android API 26 and newer. It targets API 36, compiles against API 37, and is organized into `:app`, `:benchmark`, `:addons:dictionary-pack-sample`, and reusable library modules (gradle.properties, settings.gradle.kts, app/build.gradle.kts).
- **Verified: core workflows.** The shipped path covers tap and glide typing, local suggestions and correction, multilingual Latin subtypes, emoji and stickers, encrypted clipboard history, personal dictionaries, themes, per-app profiles, explicit backup and restore, migration tools, Settings search, and local voice-IME handoff (README.md, app/src/main/kotlin/dev/patrickgold/florisboard/ime).
- **Likely: primary users.** The product is best matched to privacy-conscious Android users, people leaving SwiftKey who need user-owned migration, multilingual users, and people who customize keyboard layouts or dictionaries. README positioning, importers, and discussion #21 support this reading.
- **Verified: app-owned data path.** The base manifest declares no `INTERNET`, `ACCESS_NETWORK_STATE`, or `ACCESS_WIFI_STATE` permission. Typing, learning, rewrite routing, and addon enrollment stay local unless another explicitly chosen app handles an exported file or voice request (app/src/main/AndroidManifest.xml, docs/PRIVACY_AND_AI.md, docs/THREAT_MODEL.md).
- **Verified: operating-system backup is a separate data path.** `android:allowBackup="true"` lets Android move allowlisted app data through cloud backup or device transfer without the app opening a network connection. This distinction must be visible in policy and product copy (app/src/main/AndroidManifest.xml:111-113, app/src/main/res/xml/backup_rules.xml, app/src/main/res/xml/data_extraction_rules.xml).
- **Verified: manual recovery is mature but asymmetric.** Users can create plain or passphrase-encrypted archives, select sections, schedule verified SAF backups, and restore through versioned metadata. The interactive publication path does not yet share the scheduled path's destination verification (BackupArchiveBuilder.kt, BackupScreen.kt, ScheduledBackupSaf.kt, PortableBackupEnvelope.kt).
- **Verified: public distribution is not current.** Source identifies as v1.9.63. The latest GitHub release is v1.9.59, published on 2026-08-12, with no assets. The F-Droid recipe is not published and its unsigned-output contract is still wrong. The release keystore blocker already records the necessary next step (Roadmap_Blocked.md).
- **Verified: extensibility is designed, not yet proven by a production third-party pack.** Typed addon metadata, certificate enrollment, package verification, dictionary mounting, and a sample Esperanto pack exist. Production translation, local voice recognition, model-backed rewrite, handwriting recognition, and broad CJK engines remain blocked providers or external addon concepts (app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon, addons/dictionary-pack-sample, Roadmap_Blocked.md).
- **Verified: visual coverage is broad.** Existing Roborazzi baselines cover light, dark, AMOLED, high contrast, RTL, compact and wide layouts, 200 percent font scale, loading, error, empty, setup, backup, voice, and keyboard surfaces. Representative snapshots show consistent hierarchy and state treatment. This pass found no evidence for another generic polish item (app/src/test/snapshots, docs/ACCESSIBILITY.md).

## Competitive Landscape

- **FlorisBoard and HeliBoard:** Their trackers continue to surface editor-state failures, language-cycle control, and morphology gaps. SwiftFloris already owns editor invariant tests and has real-device editor work in `Roadmap_Blocked.md`, so another generic compatibility item would duplicate existing work. Configurable subtype cycling remains a valid active item. HeliBoard's Portuguese contraction report also supports the blocked compound-boundary work, not a new parallel engine.
- **FUTO Keyboard:** FUTO publishes active releases, locale-hint work, explicit clean-link actions, and an MIT swipe-trace dataset. Its reported top-1 swipe result is useful as a vendor benchmark target, not an independent quality result. SwiftFloris can use the dataset for local regression measurement without redistributing FUTO's separately licensed model weights.
- **AnySoftKeyboard and Keyman:** Both show the reach that data or language packages can create, and the compatibility burden that follows a long-lived plugin contract. SwiftFloris has the right small-base architecture. It should prove its current contract with the existing sample and blocked developer trust kit before widening the API.
- **fcitx5-android and Trime:** These projects are stronger references for typed, cancellable addon UI and full CJK runtime integration. Their native engines, schemas, and data footprints argue for an external provider boundary, not inclusion in the base APK.
- **Indic Keyboard, OpenBangla, Mozc, and Traditional T9:** Their specialization shows that script handling, transliteration, composition, and device form factors need language-specific engines and test vectors. SwiftFloris should continue to treat those as package or provider concerns and keep common editor transactions in the base app.
- **Thumb-Key, Unexpected Keyboard, FlickBoard, 8VIM, and CleverKeys:** Alternative layouts require learning material, visible gesture feedback, and a non-gesture fallback. SwiftFloris already ships honeycomb, split, one-handed, terminal, and imported layouts. A private practice lab is plausible, but no local report or usability measure yet shows that it should precede safety and distribution work.
- **Gboard:** Writing Tools, voice typing, spatial personalization, and federated language models show the quality ceiling. The Android editor opt-out and published spatial research transfer well. Server processing and uploaded learning do not fit SwiftFloris's base-app contract.
- **Microsoft SwiftKey:** Multilingual prediction, Flow, themes, clipboard tools, and account-backed backup remain the closest feature reference. Microsoft ended standalone SwiftKey Accounts on 2026-05-31 and moved backup to OneDrive. Recent community reports describe migrations that appeared enabled but did not restore predictions. Those reports support stronger recovery receipts and rehearsal, not account sync.
- **Samsung Keyboard:** Downloadable languages and writing assistance are presented in one discoverable settings path. SwiftFloris should match the clarity while keeping local processing and explicit providers.
- **Grammarly, QuillBot, and LanguageTool:** These products confirm demand for proofreading and rewrite, but their Android paths rely on accounts, network services, Accessibility overlays, or floating assistants. SwiftFloris already has the safer IME-native route and should finish consent and provider contracts before expanding features.
- **Bitwarden, Signal, and Firefox:** Bitwarden's portable encrypted export is a useful recovery pattern. Signal and Firefox produce support reports that separate environment metadata from sensitive content and explain exactly what is included. SwiftFloris already has environment reporting and crash formatting; raw logcat export still belongs behind the existing release-devtools item rather than a new diagnostics project.
- **Typewise and other commercial alternative layouts:** The market confirms that users will try non-QWERTY geometry when learning cost is handled. It does not establish demand for a SwiftFloris training surface. A short usability study should come before roadmap work.

## Reported Issues

- **Verified: no open issue or pull request exists in the SwiftFloris repository as of 2026-08-23.** Closed issue #1, the empty-emoji crash, and issue #9, the SymSpell memory and latency report, are fixed and documented. Neither should return to the active roadmap.
- **Verified: discussion #21 remains the only concrete repository user request.** The user asks how to install Portuguese. Portuguese data is bundled, but the language-pack screen does not make the subtype-creation route clear. The active P1 language-setup item remains the direct answer.
- **Verified: discussions #19 and #20 do not add demand.** One is an announcement and one asks for desired layouts or dictionaries. Neither has enough response evidence to justify a new item.
- **Likely: editor compatibility remains the largest ecosystem reliability risk.** FlorisBoard, HeliBoard, FUTO, and AnySoftKeyboard have current reports involving duplicate commits, cursor state, Backspace, Enter, or physical Space handling. SwiftFloris already has replay and invariant tests plus blocked real-app smoke coverage. Monitor upstream issue #3310 and #3313, but do not create a duplicate roadmap entry without a local reproduction.
- **Verified: migration failure is a repeated competitor complaint.** Two recent SwiftKey community reports describe backup or OneDrive migration that looked active before restore failed or predictions disappeared. They do not prove a SwiftFloris defect. They do show why a “write succeeded” toast is weaker than a verified, inspectable recovery artifact.
- **Verified: Android 17 candidate accessibility support is already present.** The input connection batch layer implements the current candidate accessibility path and has focused tests. A new roadmap item would be stale on arrival (EditorInputConnectionBatch.kt:198-220 and related tests).
- **Verified: morphology work already has an owner.** Portuguese contraction and compounding evidence strengthens the existing blocked compound-boundary item. It does not justify a second morphology or language-pack entry (Roadmap_Blocked.md).

## Security, Privacy, and Reliability

- **Verified: the canonical trust registry blocks a clean release.** The checker directly compares registry values with Gradle catalogs, and both the checker and its self-test fail on the two stale values. Updating prose alone cannot repair this. The registry and regression fixture must move together.
- **Verified: Android backup rules now have three independent modes.** SwiftFloris defines cloud backup and device transfer but no cross-platform transfer. Android states that a missing mode defaults to all eligible content. Because the app targets API 36 and compiles against 37, the 36.1 behavior is a current release concern, not a future platform note.
- **Needs live validation: the exact API 36.1 export set.** Official behavior makes the configuration gap actionable now, but the final fix must be exercised with the Android 16 QPR2 transport. The project must not invent iOS bundle or team identifiers merely to satisfy XML syntax. If no valid counterpart exists, persisted data needs a demonstrated fail-closed placement or backup must be disabled for that mode.
- **Verified: cloud backup lacks an encryption capability requirement.** The API 31+ `<cloud-backup>` element does not set `disableIfNoEncryptionCapabilities="true"`. The API 26-30 include rules do not use `requireFlags="clientSideEncryption"`. Android recommends conditional encryption for sensitive app data. Settings and layout state are less sensitive than learned text, but a privacy keyboard should make the choice explicit across every supported API.
- **Verified: the versioned backup resource can hide drift.** `res/xml-v31/backup_rules.xml` uses a `data-extraction-rules` root even though the manifest's `dataExtractionRules` points to a different resource and `fullBackupContent` points to `backup_rules`. Current parity checks focus on the base rules. Every resource variant must be parsed or removed so device selection cannot change policy unnoticed.
- **Verified: the no-network guarantee and Android cloud backup can both be true.** The app cannot transmit typing data itself, but Android can upload allowlisted app files. Public copy should say no app-owned network service, account, telemetry, or cloud learning. If Android-managed backup remains enabled, the UI should name it and show what is included.
- **Verified: adaptive-touch collection now respects private sessions.** v1.9.63 applies the same privacy policy used by suggestions before recording offsets. Password, incognito, and `IME_FLAG_NO_PERSONALIZED_LEARNING` sessions do not update the model. Existing focused tests cover these paths.
- **Verified: adaptive-touch persistence still escapes inventory and disclosure.** The shared preference file is neither named in `BackupDataInventory` nor accurately described in the privacy document. A future resource edit could expose it without a parity test noticing. The inventory should classify it as portable or sensitive-excluded and make the backup UI tell the same story.
- **Verified: interactive backup publication has no readback proof.** `writeFromFile` returning successfully is treated as completion. Storage providers can truncate, delay, or mishandle a document. The scheduled path already contains digest, size, sync, temporary-name, rename, and encrypted-document checks that can seed one shared verifier.
- **Likely: a recovery receipt will prevent false confidence.** Archive format version, selected sections, named omissions, encryption state, size, and SHA-256 are already available or cheap to derive. Showing and exporting that receipt makes backup support actionable without exposing content.
- **Verified: release devtools can reveal typed and copied content.** Raw clipboard, surrounding text, selection, composition, and spelling overlays are reachable from release Settings. The existing P0 item remains necessary.
- **Verified: Android 16 consent and protection hooks remain incomplete.** Rewrite routing does not observe `EditorInfo.isWritingToolsEnabled()`, and Advanced Protection is polled instead of subscribed. Existing P1 items have precise acceptance tests and should stay ahead of feature expansion.
- **Verified: final-APK native compatibility is not proven.** SQLCipher 4.18.0 contributes native libraries. Addon verification checks alignment, while release evidence does not inspect ZIP and ELF alignment in the final app APK. The existing 16 KB item remains valid.
- **Verified: current SQLCipher and Tink versions clear the reviewed advisories.** SQLCipher 4.18.0 includes SQLite 3.53.4 fixes. Tink 1.23.0 is beyond the affected ChunkedMac line, and no ChunkedMac call site exists in the app. No dependency change is justified from those advisories.
- **Verified: Kotlin 2.4.10 remains in the advisory range for unsafe build-cache metadata.** The repository does not enable an untrusted remote cache, which limits exposure. The patched 2.4.20 line is still prerelease, so the existing blocker to wait for a stable release remains correct.
- **Verified: Gradle 9.7.1 is on the patched line for the reviewed advisory.** Dependency verification remains useful defense in depth, but no evidence shows a current SwiftFloris regression that warrants another roadmap item.

## Architecture Assessment

- **Verified: module ownership is sound.** The base app owns the IME and Settings, benchmark code is isolated, a sample dictionary pack exercises packaging, and shared libraries are separate. Large language, speech, handwriting, or model runtimes should keep using typed provider boundaries.
- **Verified: `BackupDataInventory` is the right abstraction but not yet exhaustive.** It centralizes archive sections, Android domains, dispositions, and omission labels. Its own contract says a new persisted store cannot be silently forgotten. Adaptive touch proves that source discovery is not enforced. A fixture or static registry test must make the promise executable.
- **Verified: backup policy is spread across resource overlays and code.** Pre-31 XML, API 31+ XML, a second v31 resource, manual archive selection, scheduled publication, and omission UI all express parts of the same contract. The inventory should generate or exhaustively verify each representation, including new platform modes.
- **Verified: release truth has too many hand-copied pins.** Gradle catalogs, `trust-capabilities.json`, public docs, F-Droid metadata, and release scripts repeat versions and capabilities. The existing public-doc checker catches some drift. The failed trust gate and stale threat-model text show the remaining copies need one owner or complete derivation checks.
- **Verified: backup construction and publication are already separated.** `BackupArchiveBuilder` creates an app-private archive and SAF publication happens later. That is a good boundary. A shared publication verifier can serve manual and scheduled paths without rewriting archive construction.
- **Verified: coverage quantity is not yet a critical-path contract.** Kover is enabled without verification floors. Privacy, backup, migration, addon enrollment, editor transactions, and dictionary persistence need package or branch thresholds rather than a repository-wide percentage.
- **Verified: repository hygiene still permits generated Python bytecode.** Two `.pyc` files are tracked and the hygiene gate does not reject them. The active P2 item remains precise.
- **Verified: Room 3 research status changed, implementation risk did not.** Room 3.0.1 is stable, so `Roadmap_Blocked.md` is stale when it calls the line alpha. Migration still requires encrypted continuity, historical schema, DAO, performance, and rollback work. The existing blocked spike remains the right next step.
- **Verified: addon contracts are ahead of addon supply.** The repository has signature enrollment, metadata, provenance, mounting, and a sample pack. The blocked developer trust kit and unavailable release signer must land before a new production pack can be distributed credibly.
- **Likely: local quality remains the strongest differentiation.** Spatial personalization research, compounding research, and accessibility studies support lower touch-error rates through per-user geometry, language-specific test sets, visible errors, and reset controls. SwiftFloris already has most of that architecture. The immediate gap is trustworthy lifecycle management for the learned state.

## Rejected Ideas

- **Rejected now: cloud rewrite, translation, search, telemetry, account sync, or federated updates.** Each adds a network data path and weakens the product's clearest promise. Existing local providers and explicit exports cover the safer architecture.
- **Rejected: fake cross-platform identity values.** Android requires a real iOS bundle ID, team ID, and content version for cross-platform transfer. Placeholder values would create a false policy and an untestable trust claim.
- **Rejected now: a production addon pack.** The sample, verifier, and runtime contract exist, but the release signer and addon developer trust kit are blocked. Shipping a pack before its distribution and rollback path is verifiable would invert the dependency order.
- **Rejected: vendoring FUTO model weights.** The trace dataset is MIT and suitable for evaluation. The model weights use a separate license and should not enter the Apache-2.0 base artifact.
- **Rejected: embedding Rime, fcitx, Mozc, or another full input engine in `:app`.** These engines bring native code, large data, extra update cadence, and license obligations. The addon boundary exists for this reason.
- **Deferred: an alternative-layout practice lab.** Competitors show a real learning cost, but SwiftFloris has no user report, funnel measurement, or study showing where people abandon honeycomb or imported layouts. Run a small private usability study before assigning implementation priority.
- **Rejected: visually moving keys as personalization.** Spatial adaptation should remain in hit testing while labels and key geometry stay stable. Moving targets harm motor memory and accessibility.
- **Rejected now: post-quantum APK signing v3.2.** Platform support is emerging, while F-Droid and multi-signer compatibility remain uncertain. Current signing, reproducibility, and release availability are higher-confidence work.
- **Rejected: another generic editor-compatibility item.** Existing invariant tests and blocked foreground app testing already own the risk. Add a focused item only after a local reproduction identifies a missing contract.
- **Rejected: another Android 17 candidate-accessibility item.** The compatibility layer and tests are already present.
- **Rejected: a duplicate morphology roadmap item.** Current Portuguese and compounding evidence belongs in the existing blocked compound-boundary work.
- **Rejected: a new diagnostics project.** Environment and crash reports already exist. Raw content handling is part of the current release-devtools P0 item, and support export can be reassessed after that boundary is fixed.

## Sources

Repository and tracker:

- https://github.com/SysAdminDoc/SwiftFloris
- https://github.com/SysAdminDoc/SwiftFloris/releases/tag/v1.9.59
- https://github.com/SysAdminDoc/SwiftFloris/issues?q=is%3Aissue%20is%3Aopen
- https://github.com/SysAdminDoc/SwiftFloris/pulls?q=is%3Apr%20is%3Aopen
- https://github.com/SysAdminDoc/SwiftFloris/discussions/21
- https://github.com/SysAdminDoc/SwiftFloris/discussions/19
- https://github.com/SysAdminDoc/SwiftFloris/discussions/20
- https://github.com/SysAdminDoc/SwiftFloris/issues/1
- https://github.com/SysAdminDoc/SwiftFloris/issues/9

Open-source keyboards and input engines:

- https://github.com/florisboard/florisboard/releases/tag/v0.5.2
- https://github.com/florisboard/florisboard/issues/3310
- https://github.com/florisboard/florisboard/issues/3313
- https://github.com/florisboard/florisboard/issues/3328
- https://github.com/HeliBorg/HeliBoard/releases/tag/v4.0
- https://github.com/HeliBorg/HeliBoard/issues/1835
- https://github.com/HeliBorg/HeliBoard/issues/2702
- https://github.com/HeliBorg/HeliBoard/issues/2744
- https://github.com/futo-org/android-keyboard/releases/tag/0.1.30
- https://github.com/futo-org/android-keyboard/pull/1833
- https://github.com/futo-org/android-keyboard/pull/1892
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4812
- https://github.com/AnySoftKeyboard/LanguagePack
- https://github.com/openboard-team/openboard
- https://github.com/rkkr/simple-keyboard
- https://github.com/FossifyOrg/Keyboard
- https://github.com/Julow/Unexpected-Keyboard
- https://github.com/dessalines/thumb-key
- https://codeberg.org/natkr/flickboard
- https://github.com/8VIM/8VIM
- https://github.com/tribixbite/CleverKeys
- https://github.com/smc/Indic-Keyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/sspanak/tt9
- https://help.keyman.com/products/android/version-history/
- https://github.com/ElishaAz/Sayboard
- https://github.com/OpenBangla/OpenBangla-Keyboard
- https://github.com/google/mozc

Commercial products and adjacent recovery patterns:

- https://support.google.com/gboard/answer/16515540
- https://support.google.com/gboard/answer/11197787
- https://support.google.com/gboard/answer/12373137
- https://support.microsoft.com/en-us/swiftkey
- https://support.microsoft.com/en-us/swiftkey-keyboard/microsoft-swiftkey-keyboard-privacy-questions-and-your-data
- https://support.microsoft.com/en-us/swiftkey-keyboard/account
- https://www.samsung.com/us/support/answer/ANS10001592/
- https://www.samsung.com/us/support/answer/ANS10000943/
- https://support.grammarly.com/hc/en-us/articles/15606282682637-Grammarly-for-Android-user-guide
- https://help.quillbot.com/hc/en-us/articles/39335519701143-How-does-the-Quillbot-Keyboard-and-Writing-Assistant-work-on-Android
- https://help.languagetool.org/hc/en-us/articles/39254499343383-Where-can-I-access-the-LanguageTool-Writing-Assistant
- https://www.typewise.app/support
- https://bitwarden.com/help/encrypted-export/
- https://support.signal.org/hc/en-us/articles/360007318591-Debug-Logs-and-Crash-Reports
- https://support.mozilla.org/en-US/kb/use-troubleshooting-information-page-fix-firefox
- https://support.brave.com/hc/en-us/articles/9982188779405-What-does-Copy-clean-link-mean

Android platform, standards, and distribution:

- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/privacy-and-security/risks/backup-best-practices
- https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- https://developer.android.com/reference/android/view/inputmethod/InputMethodInfo
- https://developer.android.com/reference/android/app/LocaleManager
- https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager
- https://developer.android.com/privacy-and-security/advanced-protection-mode
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/identity/autofill/ime-autofill
- https://developer.android.com/identity/autofill/credential-manager-autofill
- https://developer.android.com/guide/topics/resources/string-resource#Plurals
- https://support.google.com/accessibility/android/answer/16800105
- https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html
- https://f-droid.org/en/docs/Reproducible_Builds/
- https://f-droid.org/en/docs/Build_Metadata_Reference/

Dependencies and security advisories:

- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://docs.gradle.org/9.7.1/release-notes.html
- https://docs.gradle.org/9.7.1/userguide/dependency_verification.html
- https://github.com/gradle/gradle/security/advisories/GHSA-mqwm-5m85-gmcv
- https://kotlinlang.org/docs/releases.html
- https://github.com/advisories/GHSA-r937-wjx7-w2jp
- https://developer.android.com/jetpack/androidx/releases/room3
- https://developer.android.com/training/data-storage/room/migration-2-to-3
- https://www.zetetic.net/blog/2026/08/18/sqlcipher-4.18.0-release/
- https://central.sonatype.com/artifact/net.zetetic/sqlcipher-android
- https://www2.sqlite.org/cves.html
- https://github.com/tink-crypto/tink-java/releases/tag/v1.23.0
- https://github.com/tink-crypto/tink-java/issues/75
- https://github.com/advisories/GHSA-xxmf-j3rw-f8p2
- https://kotlin.github.io/kotlinx-kover/gradle-plugin/

Research and technical literature:

- https://huggingface.co/datasets/futo-org/swipe.futo.org
- https://huggingface.co/futo-org/futo-swipe/blob/main/LICENSE.md
- https://arxiv.org/abs/2606.25247
- https://research.google/pubs/spatial-model-personalization-in-gboard/
- https://research.google/pubs/handling-compounding-in-mobile-keyboard-input/
- https://research.google/pubs/federated-learning-of-gboard-language-models-with-differential-privacy/
- https://research.google/pubs/synthesizing-and-adapting-error-correction-data-for-mobile-large-language-model-applications/
- https://www.usenix.org/system/files/conference/usenixsecurity15/sec15-paper-chen-jin.pdf
- https://citizenlab.ca/research/vulnerabilities-across-keyboard-apps-reveal-keystrokes-to-network-eavesdroppers/
- https://pmc.ncbi.nlm.nih.gov/articles/PMC7881442/
- https://pmc.ncbi.nlm.nih.gov/articles/PMC9589473/
- https://github.com/hunspell/hunspell

Community and curated lists:

- https://github.com/ideas-no996/awesome-android-keyboards
- https://github.com/pluja/awesome-privacy
- https://discuss.privacyguides.net/t/what-keyboard-are-you-using-on-android/15973
- https://discuss.privacyguides.net/t/heliboard-offline-keyboard-for-android/28093
- https://news.ycombinator.com/item?id=40831489
- https://forum.languagetool.org/t/languagetool-on-android/11647
- https://www.reddit.com/r/Swiftkey/comments/1vp13la/restore_keyboard_layout_languages_settings_and/
- https://www.reddit.com/r/Swiftkey/comments/1tylh00/migration_to_onedrive_failed_lost_predictions/

## Open Questions

None block prioritization. API 36.1 cross-platform transport behavior and manual-backup readback across several SAF providers are explicit acceptance tests for the new roadmap items, not prerequisites for adding them.
