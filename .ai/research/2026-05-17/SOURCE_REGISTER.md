# Source Register — 2026-05-17 Research Run

Every source consulted during this run, grouped by topic. Local paths and
external URLs separated. Where a claim in this research run depends on a
source, the citation is here.

---

## 1. Local sources (in-repo)

### 1.1 Strategy / planning

| File | Used for |
|---|---|
| [README.md](../../../README.md) | Initial v1.8.52 baseline plus later v1.8.58 catch-up; feature highlights; permissions; multilingual; MCP; Tasker; perf; tests |
| [ROADMAP.md](../../../ROADMAP.md) | §0 SwiftKey tracker, §1 philosophy, §2 state-of-repo, §3 shipped log, §4 thesis, §5 tier system, §6 NOW (N1-N17), §7 NEXT (Next-1 to Next-12), §8 LATER (L1-L12), §9 Under Consideration, §10 Rejected, §10.5 External-Work Backlog, §11 Cross-Cutting Concerns, §12 Cadence, §13 Adjacent Wins, §14 Risk Register, §15 Definition of Done, §16 Glossary, Appendix |
| [SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](../../../SWIFTKEY_PARITY_ROADMAP_2026-05-17.md) | Phase A/B/C/D/E plan, gap matrix P1-P26, cross-refs back to ROADMAP §6/§7/§8 |
| [IMPROVEMENT_PLAN.md](../../../IMPROVEMENT_PLAN.md) | 15 workstreams (test, lint, pure-core extraction, input hardening, trust states, a11y, perf, CI, repo hygiene, UX polish, keyboard polish, l10n, privacy/data integrity, build/dep hygiene, manual QA) |
| [SWIFTKEY_PARITY_AUDIT.md](../../../SWIFTKEY_PARITY_AUDIT.md) | Superseded; referenced for historical audit baseline |
| [SWIFTKEY_PARITY_BUILD_PLAN.md](../../../SWIFTKEY_PARITY_BUILD_PLAN.md) | Superseded; referenced for prior build sequencing |
| [SWIFTKEY_PARITY_RESEARCH.md](../../../SWIFTKEY_PARITY_RESEARCH.md) | Superseded; referenced for SwiftKey feature research baseline |
| [SWIFTKEY_AI_RESEARCH.md](../../../SWIFTKEY_AI_RESEARCH.md) | Superseded; referenced for SwiftKey AI surface inventory |
| [SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md](../../../SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md) | Superseded; referenced for feature implementation seam mapping |

### 1.2 Per-release notes (sampled)

| File | Used for |
|---|---|
| [RELEASE_NOTES_v1.8.58.md](../../../RELEASE_NOTES_v1.8.58.md) | Current HEAD release metadata — Phase D2 task-creation quick action |
| [RELEASE_NOTES_v1.8.55.md](../../../RELEASE_NOTES_v1.8.55.md) | Initial-pass HEAD release — Phase B3 shared-spelling bilingual handling |
| [RELEASE_NOTES_v1.8.54.md](../../../RELEASE_NOTES_v1.8.54.md) | Phase A3 codec primitive (AES-256-GCM + PBKDF2-HMAC-SHA-256) |
| [RELEASE_NOTES_v1.8.53.md](../../../RELEASE_NOTES_v1.8.53.md) | Phase A2 post-import confirmation + rollback |
| [RELEASE_NOTES_v1.8.52.md](../../../RELEASE_NOTES_v1.8.52.md) | Phase A1 migration outreach + README badge |

(80 per-release notes total in repo root — full list in
[STATE_OF_REPO.md](STATE_OF_REPO.md))

### 1.3 Build / config

| File | Used for |
|---|---|
| [gradle.properties](../../../gradle.properties) | versionCode/Name + min/target/compile SDK |
| [gradle/libs.versions.toml](../../../gradle/libs.versions.toml) | Every dependency pin |
| [gradle/tools.versions.toml](../../../gradle/tools.versions.toml) | NDK / build-tools / JDK / Rust / cmake pins |
| [settings.gradle.kts](../../../settings.gradle.kts) | Module includes (and the two commented-out modules) |
| [app/build.gradle.kts](../../../app/build.gradle.kts) | App-level config, signing config, `verifyNoInternetPermission` gate, ksp/Roborazzi/Kover wiring |
| [app/src/main/AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml) | Permissions, signature-protected permission, queries, services, receivers, providers |

### 1.4 Documentation

| File | Used for |
|---|---|
| [docs/SECURITY.md](../../../docs/SECURITY.md) | OSV-scanner + dep-review + dated security appendix |
| [docs/THREAT_MODEL.md](../../../docs/THREAT_MODEL.md) | Threat actors, surfaces, mitigations |
| [docs/REPRODUCIBLE_BUILDS.md](../../../docs/REPRODUCIBLE_BUILDS.md) | Toolchain pin matrix, F-Droid verifier recipe |
| [docs/MIGRATE_FROM_SWIFTKEY.md](../../../docs/MIGRATE_FROM_SWIFTKEY.md) | Three migration paths |
| [docs/AI_PROMPTS_EXTERNAL_WORK.md](../../../docs/AI_PROMPTS_EXTERNAL_WORK.md) | External-work prompts |
| [docs/addons/dictionary-pack-spec.md](../../../docs/addons/dictionary-pack-spec.md) | Dictionary pack JSON schema |
| [docs/addons/apk-validation.md](../../../docs/addons/apk-validation.md) | Addon APK validation |
| [NOTICE](../../../NOTICE) | SCOWL + LDNOOBW + FlorisBoard attribution |
| [LICENSES/SCOWL-Copyright.txt](../../../LICENSES/SCOWL-Copyright.txt) | SCOWL license text |

### 1.5 Source code (sampled paths only — full tree in STATE_OF_REPO.md §5)

Sampled key paths verified for `ROADMAP.md` reconciliation:

- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/FlorisLocale.kt`

### 1.6 Git state

- `git log --oneline -30` — HEAD commit subjects
- `git tag --sort=-creatordate` — 56 tags, latest v1.8.40 (lags HEAD)
- `git remote -v` — `origin = https://github.com/SysAdminDoc/SwiftFloris.git`
- `git branch --show-current` — `master`, 40 ahead of `origin/master`

---

## 2. External sources (URLs)

### 2.1 FlorisBoard ecosystem

- https://github.com/florisboard/florisboard — repo
- https://github.com/florisboard/florisboard/releases — v0.5.2 (2025-11-28); v0.6.0-alpha02 (2025-01-23)
- https://github.com/florisboard/florisboard/blob/main/ROADMAP.md — upstream roadmap
- https://florisboard.org/legal/privacy/ — privacy policy
- https://docs.florisboard.org/extensions — extensions
- https://github.com/florisboard/florisboard/issues/2362 — "All keys invisible" (85 reactions)
- https://github.com/florisboard/florisboard/issues/3233 — k3lp Unicode-Keyboard-v3
- https://github.com/florisboard/florisboard/issues/3225 — PIN scrambling
- https://github.com/florisboard/florisboard/issues/3280 — Snygg v2
- https://github.com/florisboard/florisboard/issues/3234 — malicious closed fork "CleverType AI Keyboard"
- https://beta.addons.florisboard.org — FlorisBoard Addons marketplace

### 2.2 HeliBoard / NLnet

- https://github.com/Helium314/HeliBoard
- https://github.com/Helium314/HeliBoard/issues/2226 — NLnet open glide
- https://nlnet.nl/project/GestureTyping/ — NLnet funding page
- https://github.com/HeliBorg/HeliBoard/releases — v3.7 / v3.8 / v3.9 release dates

### 2.3 FUTO

- https://github.com/futo-org/android-keyboard
- https://github.com/futo-org/android-keyboard/releases — v0.1.28 (2026-05-04)
- https://docs.keyboard.futo.org/migration/swiftkeymigration — SwiftKey migration (Microsoft account export only; dict import "not yet supported")
- https://github.com/futo-org/voice-input — FUTO Voice Input
- https://github.com/futo-org/whisper-acft/issues/9 — whisper-acft v3 turbo issue

### 2.4 Other OSS keyboards

- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/AnySoftKeyboard/LanguagePack
- https://github.com/openboard-team/openboard
- https://github.com/Julow/Unexpected-Keyboard
- https://github.com/dessalines/thumb-key
- https://github.com/SimpleMobileTools/Simple-Keyboard
- https://github.com/FossifyOrg/Keyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/ElishaAz/Sayboard
- https://github.com/tribixbite/CleverKeys — v1.4.0 (2026-04-26), 13MB ONNX glide
- https://github.com/tribixbite/CleverKeys-ML — training repo
- https://cleverkeys.app/ — CleverKeys project site
- https://github.com/keymanapp/keyman — v18.0.249 (2026-03-27); v19.0 alphas in May 2026
- https://github.com/keymanapp/keyboards
- https://github.com/alex-vt/WhisperInput
- https://github.com/klausw/hackerskeyboard/issues/875 — Hacker's Keyboard stalled

### 2.5 Commercial keyboards / market events

- https://www.microsoft.com/en-us/swiftkey — SwiftKey official
- https://play.google.com/store/apps/details?id=com.touchtype.swiftkey — Play listing
- https://support.microsoft.com/en-us/swiftkey — Microsoft Support hub
- https://www.windowscentral.com/software-apps/swiftkey-will-soon-require-a-microsoft-account-data-to-be-moved-to-onedrive — retirement
- https://www.neowin.net/news/psa-microsoft-is-deleting-swiftkey-accounts-this-month-here-is-what-you-need-to-do/ — May 2026 PSA
- https://support.microsoft.com/en-us/swiftkey-keyboard/microsoft-swiftkey-keyboard-data-portal — `data.swiftkey.com` description
- https://9to5google.com/2026/05/12/gemini-intelligence-announcement/ — Gemini Intelligence wave (Gboard Rambler etc.)
- https://www.sammobile.com/news/one-ui-7-0-galaxy-ai-writing-tools-any-keyboard/ — Samsung One UI 7 decouple
- https://discuss.grapheneos.org/d/26041-google-disabled-voice-typing-on-gboard-without-network-access — Gboard offline-voice regression
- https://www.apkmirror.com/apk/typewise/typewise-keyboard-big-keys-privacy-swipe/typewise-custom-keyboard-4-4-44-release/ — Typewise v4.4.44
- https://www.clevertype.co/post/best-ai-keyboard-alternatives-to-grammarly-for-android-iphone — Grammarly keyboard Android discontinuation
- https://www.windowscentral.com/swiftkey-adds-location-and-calendar-sharing-its-toolbar-feature — SwiftKey toolbar history
- https://techcommunity.microsoft.com/t5/microsoft-to-do-blog/add-tasks-to-your-to-do-list-right-in-the-swiftkey-keyboard/ba-p/3143221 — SwiftKey + To Do
- https://appleinsider.com/articles/25/08/22/inside-ios-26-genmoji-tapbacks-smarter-ai-deeper-customization — Apple Genmoji

### 2.6 Android platform

- https://developer.android.com/about/versions/17/release-notes — API 37 release notes
- https://developer.android.com/about/versions/17/behavior-changes-17 — API 37 behavior changes
- https://developer.android.com/about/versions/17/behavior-changes-all — All apps
- https://developer.android.com/build/releases/agp-9-1-0-release-notes — AGP 9.1
- https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html — 16 KB pages
- https://android-developers.googleblog.com/2026/02/the-first-beta-of-android-17.html — Android 17 Beta 1
- https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html — Compose 2026.05.00
- https://source.android.com/docs/security/bulletin/2026/2026-04-01 — Android Security Bulletin

### 2.7 Compose / Jetpack / Kotlin

- https://developer.android.com/jetpack/androidx/releases/activity — fifth-pass correction: Activity 1.13.0 is stable
- https://developer.android.com/jetpack/androidx/releases/security — security-crypto 1.1.0 exists, but APIs are deprecated
- https://developer.android.com/jetpack/androidx/releases/navigation3 — navigation3 1.1.0
- https://mvnrepository.com/artifact/androidx.compose/compose-bom/versions — BOM versions
- https://github.com/JetBrains/kotlin/releases/tag/v2.3.21 — Kotlin 2.3.21
- https://kotlinlang.org/docs/whatsnew-eap.html — Kotlin 2.4 EAP
- https://github.com/google/ksp/releases — KSP 2.3.x line (fifth-pass Maven metadata target: 2.3.8)
- https://github.com/Kotlin/kotlinx.coroutines/releases — coroutines 1.11.0
- https://github.com/takahirom/roborazzi/releases — Roborazzi line (fifth-pass Maven metadata target: 1.60.0)
- https://github.com/robolectric/robolectric/releases — Robolectric 4.16.1
- https://github.com/mikepenz/AboutLibraries/releases — aboutlibraries 14.2.0
- https://github.com/zxing/zxing/releases — zxing-core 3.5.4

### 2.8 SQLCipher / EncryptedSharedPreferences

- https://www.zetetic.net/blog/2026/05/12/sqlcipher-4.16.0-release/ — release notes
- https://github.com/sqlcipher/sqlcipher/issues/564 — LibTomCrypt deprecation
- https://discuss.zetetic.net/t/new-cve-2025-29087reported-is-sqlcipher-effected/6892 — SQLite CVE-2025-29087 not exploitable in SQLCipher
- https://github.com/ed-george/encrypted-shared-preferences — maintained fork
- https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a — migration guide
- https://github.com/tink-crypto/tink-java — Tink

### 2.8a Fifth-pass dependency metadata corrections

- https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml — AGP metadata target 9.2.1 / 9.2.x line
- https://developer.android.com/build/releases/gradle-plugin — Android Gradle Plugin release notes
- https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml — Compose BOM 2026.05.00
- https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml — Activity release 1.13.0
- https://dl.google.com/dl/android/maven2/androidx/security/security-crypto/maven-metadata.xml — Security Crypto stable 1.1.0 exists
- https://repo.maven.apache.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml — KSP 2.3.8
- https://repo.maven.apache.org/maven2/io/github/takahirom/roborazzi/roborazzi/maven-metadata.xml — Roborazzi 1.60.0
- https://repo.maven.apache.org/maven2/org/robolectric/robolectric/maven-metadata.xml — Robolectric 4.16.1
- https://repo.maven.apache.org/maven2/com/google/crypto/tink/tink-android/maven-metadata.xml — Tink Android 1.21.0
- https://github.com/LeanBitLab/LeanType — LeanType active HeliBoard fork
- https://github.com/LeanBitLab/LeanType/releases/tag/v3.7.9 — LeanType v3.7.9 release

### 2.9 On-device LLM / LiteRT-LM / Gemma

- https://ai.google.dev/edge/litert-lm/overview — LiteRT-LM
- https://blog.google/innovation-and-ai/technology/developers-tools/functiongemma/ — FunctionGemma (Jan 2026)
- https://www.infoq.com/news/2026/01/functiongemma-edge-function-call/ — FunctionGemma coverage
- https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android — MediaPipe LLM Inference (deprecated path)
- https://developers.googleblog.com/en/introducing-gemma-3-270m/ — Gemma 3 270M intro
- https://www.datacamp.com/tutorial/gemma-3-270m — Gemma 3 demo
- https://v-chandra.github.io/on-device-llms/ — On-device LLMs state of the union
- https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions — model card
- https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm — gemma-4 E2B
- https://huggingface.co/edugp/kenlm — KenLM 24-lang pretrained

### 2.10 Bergamot / offline translation

- https://browser.mt/ — browsermt project
- https://github.com/browsermt/bergamot-translator — active fork
- https://github.com/DavidVentura/offline-translator — v0.5.2 (2026-05-10)
- https://github.com/apertium/apertium-android — Apertium Android example
- https://github.com/niedev/RTranslator — NLLB-200 distilled Android

### 2.11 NLP / language

- https://github.com/wolfgarbe/SymSpell — SymSpell
- https://medium.com/data-science/symspell-vs-bk-tree-100x-faster-fuzzy-string-search-spell-checking-c4f10d80a078 — SymSpell vs BK-tree
- https://aclanthology.org/2020.lrec-1.228.pdf — 14-tool spell-correction comparison
- https://kheafield.com/code/kenlm/ — KenLM toolkit (LGPL — verified incompatible with `:app` ceiling)
- https://kheafield.com/code/kenlm/estimation/ — KenLM estimation
- https://fasttext.cc/docs/en/crawl-vectors.html — fastText 157-lang
- https://wordlist.aspell.net/scowl-readme/ — SCOWL
- https://github.com/rime/librime — librime (BSD-3-Clause, compatible)
- https://github.com/varnamproject/libvarnam — Varnam (MPL)
- https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words — profanity list

### 2.12 Voice

- https://github.com/ggml-org/whisper.cpp — whisper.cpp
- https://www.sinologic.net/en/2026-05/vosk-vs-whisper-local-the-ultimate-2026-guide-to-self-hosted-speech-recognition-stt.html — Vosk vs Whisper 2026
- https://github.com/futo-org/voice-input — FUTO voice
- https://github.com/futo-org/whisper-acft/issues/9 — Whisper v3 turbo
- https://github.com/alex-vt/WhisperInput — WhisperInput
- https://joplinapp.org/help/dev/spec/voice_typing/ — Joplin voice spec
- https://alphacephei.com/vosk/android — Vosk Android
- https://github.com/alphacep/vosk-api — Vosk API

### 2.13 F-Droid / distribution

- https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html — verified-tier launch
- https://f-droid.org/en/2026/01/23/fdroid-in-2025-strengthening-our-foundations-in-a-changing-mobile-landscape.html — NLnet rebuild farm
- https://github.com/ImranR98/Obtainium — Obtainium

### 2.14 Regulatory

- https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai — EU AI Act
- https://www.legalnodes.com/article/eu-ai-act-2026-updates-compliance-requirements-and-business-risks — AI Act Article 50 (2 Aug 2026)
- https://www.apple.com/legal/privacy/data/en/intelligence-engine/ — Apple Intelligence disclosure
- https://techcrunch.com/2025/11/13/apples-new-app-review-guidelines-clamp-down-on-apps-sharing-personal-data-with-third-party-ai/ — App Store guideline 5.1.2(i)

### 2.15 Misc

- https://crowdin.com/blog/android-app-localization-tutorial — l10n best practices
- https://crowdin.com/blog/best-practices-for-ui-localization — UI localization
- https://pmc.ncbi.nlm.nih.gov/articles/PMC12723528/ — LLM-Powered Text Entry Decoding
- https://dl.acm.org/doi/10.1145/2470654.2481384 — Adaptive touchscreen keyboards (ACM CHI 2013)
- https://www.scss.tcd.ie/Doug.Leith/pubs/gboard_kamil.pdf — Trinity Gboard audio paper

---

## 3. Source-class coverage

| Class | Hits | Saturation? |
|---|---|---|
| Direct competitor repos | 14 | Yes — every OSS Android keyboard with non-trivial mindshare covered |
| Commercial competitor docs | 8 | Yes — SwiftKey, Gboard, Samsung, Apple, Typewise, Chrooma, Grammarly, Tap |
| Android platform docs | 6 | Yes — API 37 + AGP + Security Bulletin + Compose |
| Jetpack/library release pages | 11 | Yes — every pinned dep checked |
| LLM / on-device-AI | 6 | Reasonably saturated; FunctionGemma + LiteRT-LM 0.10/0.11 status verified |
| NLP / language | 8 | KenLM license verified (LGPL) — material correction to ROADMAP |
| Voice | 5 | Yes — whisper.cpp, Vosk, FUTO covered |
| F-Droid / distribution | 2 | Reasonable; verified-tier launch covered |
| Regulatory / privacy | 3 | EU AI Act Article 50 cutoff (2 Aug 2026) confirmed |
| Academic | 3 | Light — keyboard research isn't roadmap-load-bearing this pass |

The combination is saturating for a one-day research pass against a project
that already has a heavily-sourced ROADMAP. Diminishing returns kick in
after the dep + competitor + upstream-status passes converge.
