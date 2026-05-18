# SwiftFloris

![Version](https://img.shields.io/badge/version-v1.8.144-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green) ![Platform](https://img.shields.io/badge/platform-Android%208.0+-orange) ![Network](https://img.shields.io/badge/network-none-lightgrey) ![SwiftKey migration](https://img.shields.io/badge/SwiftKey%20migration-window%20closes%202026--05--31-red)

**SwiftFloris** is a privacy-first Android keyboard, forked from FlorisBoard and pushed toward SwiftKey-class multilingual typing without the cloud. It ships under Apache-2.0, holds no `INTERNET` permission, and binds zero accounts.

> **Zero cloud processing. Zero telemetry. Zero account. All features work offline.**

> ## ⚠️ SwiftKey users — your account-backed data is being deleted on **2026-05-31**
>
> Microsoft is retiring standalone SwiftKey accounts and shutting down the
> [`data.swiftkey.com`](https://data.swiftkey.com) export endpoint on 2026-05-31. After that date your
> non-Microsoft-account learned vocabulary, shortcuts, and clipboard sync are permanently gone.
>
> **Two no-cloud paths off SwiftKey:**
>
> 1. **Right now** (before the cutoff) — export `swiftkey-cloud.json` from
>    [`data.swiftkey.com`](https://data.swiftkey.com), install SwiftFloris via the [Obtainium one-tap link](#option-a--obtainium-recommended-for-auto-updates) below,
>    then in SwiftFloris go to **Settings → Personal dictionary → Import** and pick the file.
>    SwiftFloris ingests the JSON shape directly (see [v1.8.46 release notes](RELEASE_NOTES_v1.8.46.md)
>    and the [migration walk-through](docs/MIGRATE_FROM_SWIFTKEY.md)).
> 2. **If you missed the cutoff** — your learned words are gone from the cloud but everything still
>    in the on-device SwiftKey personal dictionary can still be re-typed; SwiftFloris's
>    [instant-remember overlay](RELEASE_NOTES_v1.8.26.md) climbs the words back to the top of the
>    prediction strip after a single use.
>
> SwiftFloris **never** binds your data to a Microsoft (or any other vendor) account, so the next
> account-retirement notice that lands in your inbox won't include this app.

> ## Samsung / Grammarly users
>
> Galaxy users on One UI 7+ can keep SwiftFloris as the default keyboard and
> invoke Galaxy AI Writing Assist from Samsung's selected-text UI when they
> intentionally want that separate Samsung layer. Samsung documents Writing
> Assist availability and features in its [support guide](https://www.samsung.com/us/support/answer/ANS10000943/);
> [SamMobile's One UI 7 coverage](https://www.sammobile.com/news/one-ui-7-0-galaxy-ai-writing-tools-any-keyboard/)
> documents the keyboard-agnostic flow.
>
> [Grammarly's Android support docs](https://support.grammarly.com/hc/en-us/articles/25028519116429-Error-Grammarly-Assistant-is-not-enabled-right-now)
> say the old Grammarly Keyboard for Android
> is being discontinued and replaced by Grammarly for Android, which integrates
> with any keyboard. SwiftFloris can stay underneath as the no-network keyboard;
> SwiftFloris itself does not send text to Grammarly or any other service.

## Highlights

| Area | What's in v1.8.144 | Privacy posture |
|------|-------------------|-----------------|
| **Autocorrect / prediction** | SCOWL 117k English dictionary, SymSpell d1+d2, bigram + trigram next-word, capitalization-aware completions, contraction handling, instant-remember user-dictionary overlay | On-device |
| **Multilingual typing** | Bilingual subtype presets (EN+ES / EN+FR / EN+DE), per-token Latin language identification, top-two straddle guard, sentence-local context scoring | On-device |
| **Scripts** | Devanagari + Bengali + Tamil + Telugu + ... (63-script transliteration coverage); RTL Arabic shaper, Persian / Urdu / Hebrew normalisers, bundled Noto Nastaliq Urdu rendering for Urdu subtype key text | On-device |
| **Gesture typing** | `StatisticalGlideTypingClassifier` over bounded EN / DE / ES / FR / IT / PT dictionaries with adaptive touch evidence | On-device |
| **Voice input** | FUTO Voice Input handoff (live path), plus preview-only local Whisper/Vosk route selector and model catalog until a recognizer runtime ships | SwiftFloris itself does not record audio |
| **Emoji & stickers** | Emoji search/history/pinned groups with an in-keyboard pin-to-group sheet, bundled local sticker packs, and user-imported SAF sticker folders for PNG / WebP / JPEG / GIF files | Local folder URI only |
| **Clipboard** | Room-backed history with pinning + per-app source tag, media/provider metadata, sensitive-item gates, and startup/restore reconciliation | On-device |
| **Productivity** | Calendar quick-insert reads local agenda entries for today + next 7 days; task quick-insert sends selected text to user-chosen task / note apps | Calendar permission is explicit opt-in; no network |
| **Themes** | 21 bundled themes — SwiftKey Pure (Light/Dark + M3 Expressive), SwiftKey High Contrast (AAA), Aurora Animated, Floris Day/Night, Swift Glacier, Swift Slate, M3E Nord (light + dark), Tokyo Night, Dracula, Catppuccin Mocha; borderless variants where applicable; Snygg theme engine; per-app accent | No telemetry |
| **MCP daemon bridge** | AIDL bridge to user-installed MCP daemons with per-daemon enable / disable in Settings → MCP daemon bridge | Local-only binder, no network |
| **Addon packs** | Addon manifest/enumerator contracts, IME-startup registry reconciliation, Settings -> Addons status/rescan, trust reset/changed-certificate controls, dictionary-pack catalog details, persisted signing-certificate pins, descriptor validation, provenance reports, typed dictionary-pack catalog, and addon APK dictionary asset mounting | No-network addon rejection |
| **Migration** | Gboard / FlorisBoard / SwiftKey JSON export importer; passphrase-encrypted SwiftFloris dictionary export/import; Keyman LDML / `.kmp` metadata + Windows KLC + macOS hardware-keyboard imports | All file-system based |
| **Alternative layouts** | Colemak / Dvorak / Workman from the FlorisBoard layout pack, plus selectable honeycomb hex layout with clipped hex keys and hex-aware hit testing | On-device |
| **AI transparency** | First-run AI/ML explainer plus Settings → About → AI features screen covering next-word, glide, voice, translation, and smart compose | On-device, no account, no telemetry |
| **CI / build** | No-network gate, OSV dep scan, reproducible-build toolchain pins + build-twice APK self-check, Roborazzi visual-regression hard gate with committed theme/Addons baselines, Macrobenchmark trace sections in 6 hot paths | Audit-friendly |

## Distribution

SwiftFloris ships through GitHub Releases (canonical), and is targeted at F-Droid (reproducible-build verification in progress) and Obtainium for auto-updates. It is **not** on Google Play by design — Play forces target-SDK churn and Integrity-API tradeoffs that conflict with the no-telemetry posture.

### Option A — Obtainium (recommended for auto-updates)

[Obtainium](https://github.com/ImranR98/Obtainium) tracks GitHub Releases directly and notifies you the moment a new SwiftFloris APK ships — no Play Store, no F-Droid mirror lag, no manual polling.

**One-tap subscribe:**

```
obtainium://app/{"id":"dev.patrickgold.florisboard","url":"https://github.com/SysAdminDoc/SwiftFloris","author":"SysAdminDoc","name":"SwiftFloris","preferredApkIndex":0,"additionalSettings":"{\"includePrereleases\":false,\"fallbackToOlderReleases\":true,\"trackOnly\":false,\"versionDetection\":true,\"apkFilterRegEx\":\"app-release.*\\\\.apk\"}"}
```

Open the link above on a device with Obtainium installed (or paste it into Obtainium's "Add app from URL" field). Obtainium will subscribe to this repository's GitHub Releases feed and auto-prompt for installs on each new tag.

### Option B — GitHub Releases (manual)

1. Download the latest APK from [Releases](https://github.com/SysAdminDoc/SwiftFloris/releases).
2. Install on your Android device (Android 8.0+).
3. (Optional) Install [FUTO Voice Input](https://voiceinput.futo.org/) for offline dictation. SwiftFloris's in-app Whisper/Vosk catalog is preview-only until the local recognizer runtime ships.

### Option C — Manual Build

```bash
git clone https://github.com/SysAdminDoc/SwiftFloris.git
cd SwiftFloris
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Enable as Default Keyboard

1. Open **Settings → System → Languages & input**.
2. Tap **Virtual keyboard** (or **On-screen keyboard**).
3. Select **SwiftFloris** and grant permissions as prompted.

## Migrating from SwiftKey

Full step-by-step paths are in [`docs/MIGRATE_FROM_SWIFTKEY.md`](docs/MIGRATE_FROM_SWIFTKEY.md); the headline contract — `swiftkey-cloud.json` ingestion through **Settings → Personal dictionary → Import** — landed in [v1.8.46](RELEASE_NOTES_v1.8.46.md), the cumulative-byte hardening of the JSON parser in [v1.8.48](RELEASE_NOTES_v1.8.48.md), the post-import confirmation + rollback in [v1.8.53](RELEASE_NOTES_v1.8.53.md), the encrypted-blob export codec primitive in [v1.8.54](RELEASE_NOTES_v1.8.54.md), the Settings UI encrypted export/import round-trip in [v1.8.65](RELEASE_NOTES_v1.8.65.md), and the parity-roadmap reference for the **2026-05-31** cutoff lives in [`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`](SWIFTKEY_PARITY_ROADMAP_2026-05-17.md).

## Documentation

Project-internal docs all live in the repository:

- [`ARCHITECTURE.md`](ARCHITECTURE.md) — module, package, runtime, security-boundary, and CI architecture map.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — contributor setup, verification, privacy, and release expectations.
- [`docs/MIGRATE_FROM_SWIFTKEY.md`](docs/MIGRATE_FROM_SWIFTKEY.md) — SwiftKey account-retirement migration paths.
- [`docs/PRIVACY_AND_AI.md`](docs/PRIVACY_AND_AI.md) — AI/ML feature transparency and local-processing disclosure.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — privacy / security threat model and mitigations.
- [`docs/SQLCIPHER_PROVIDER_MIGRATION.md`](docs/SQLCIPHER_PROVIDER_MIGRATION.md) — SQLCipher crypto-provider migration triggers, OpenSSL proof-of-concept path, and 16 KB verification gates.
- [`docs/REPRODUCIBLE_BUILDS.md`](docs/REPRODUCIBLE_BUILDS.md) — pinned toolchain and F-Droid rebuild plan.
- [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md) — Macrobenchmark trace sections and regression threshold contract.
- [`docs/INLINE_AUTOFILL.md`](docs/INLINE_AUTOFILL.md) — inline-autofill matrix and password-manager verification.
- [`docs/TASKER_INTEGRATION.md`](docs/TASKER_INTEGRATION.md) — Tasker intent contract.
- [`docs/FONTS.md`](docs/FONTS.md) — bundled fonts (Nastaliq + Naskh fallback).
- [`docs/AUTOCORRECT_LIFECYCLE.md`](docs/AUTOCORRECT_LIFECYCLE.md) — autocorrect, spacebar, punctuation, backspace, provider-notification, and QA contract.
- [`docs/GESTURE_TYPING_MULTILINGUAL.md`](docs/GESTURE_TYPING_MULTILINGUAL.md) — multilingual gesture-typing guide.
- [`docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md`](docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md) — FUTO Voice Input setup + recovery actions.
- [`docs/VOICE_COMMANDS.md`](docs/VOICE_COMMANDS.md) — built-in and custom voice-command grammar reference.
- [`docs/addons/dictionary-pack-spec.md`](docs/addons/dictionary-pack-spec.md) — external dictionary-pack APK descriptor and validation contract.
- [`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md) — execution-focused quality / UX / a11y / perf / test / delivery plan.
- [`ROADMAP.md`](ROADMAP.md) — current and historical roadmap (v5.41).
- `RELEASE_NOTES_v*.md` — per-release notes, one file per version, in the repository root.

## Architecture & Stack

**Language and build**

- Kotlin 2.3.21, Compose BOM 2026.05.00, Material 3 + material-kolor.
- AGP 9.2.1, Gradle 9.5.1, JDK 17.
- KSP 2.3.8, Room 2.8.4, SQLCipher 4.16.0, Tink Android 1.21.0.
- Kotest 6.1.11 unit-test runner; Roborazzi 1.60.0 and Robolectric 4.16.1
  for screenshot/JVM Android regressions.
- minSdk **26** (Android 8.0); targetSdk / compileSdk **36** (Android 16, with Android 17 / API 37 behavior gates wired).
- Crowdin pipeline for translations.
- No `INTERNET` permission in the manifest (CI-enforced).

**Module layout**

```
:app                       — IME + Settings UI + addon facades
lib/android                — Android utility extensions
lib/color                  — color math
lib/compose                — Compose helpers
lib/kotlin                 — pure-Kotlin utilities
lib/snygg                  — Snygg theme engine
:benchmark                 — Macrobenchmark module (present on disk, not yet wired in settings)
:lib:native                — placeholder for future native add-ons (commented out)
```

The IME's main work lives under `app/src/main/kotlin/dev/patrickgold/florisboard/ime/{keyboard,nlp,theme,ext,emoji,mcp,voice,bidi,dictionary,kenlm}`.

## Building

### Prerequisites

```bash
# Android SDK 36 (compile/target)
# JDK 17+
# Gradle 9.5.1 (use the bundled wrapper)
```

### Build commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (unsigned)
./gradlew assembleRelease

# Clean
./gradlew clean

# Unit tests (Kotest)
./gradlew test

# Roborazzi screenshot verify (visual-regression CI)
./gradlew :app:verifyRoborazziDebug
```

**Signed release build**

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=keystore.jks \
  -Pandroid.injected.signing.store.password=$STORE_PASS \
  -Pandroid.injected.signing.key.alias=$KEY_ALIAS \
  -Pandroid.injected.signing.key.password=$KEY_PASS
```

See [`docs/REPRODUCIBLE_BUILDS.md`](docs/REPRODUCIBLE_BUILDS.md) for the toolchain pins that match the published APK fingerprints.

## Permissions

| Permission | Purpose | Required? |
|------------|---------|-----------|
| `INPUT_METHOD` | IME service binding | ✅ Yes |
| `VIBRATE` | Haptic feedback | Optional |
| `RECORD_AUDIO` | Not requested by SwiftFloris; the external voice keyboard owns microphone access | No |
| `BIND_NOTIFICATION_LISTENER` | App-aware smartbar features | Optional |

> **Privacy note:** SwiftFloris does not request `INTERNET`. CI validates this on every build.

## Privacy & Security

### Posture

- **No `INTERNET` permission**, no cloud sync, no account, no telemetry, no ads.
- **Apache-2.0 only** in the main app. GPL / AGPL / Source-First code cannot be linked into `:app`; it can only ship as a clearly-isolated optional addon under its own license.
- **No closed-source blobs.** No `libjni_latinimegoogle.so`-style glide binaries. Reproducible builds with toolchain pinning.

### Encryption and sensitive-field handling

- **Personal dictionary:** SQLCipher-encrypted Room database, with the
  SQLCipher passphrase wrapped by Tink / AndroidKeystore.
- **IME window:** `FLAG_SECURE` set on password fields so the keyboard is excluded from screenshots and screen-recording overlays.
- **Long-press popups:** suppressed on every `KeyVariation.PASSWORD` (Android 17 password-visibility behavior closed on the IME side as of v1.8.44).
- **Personalized learning:** clipboard write / dictionary learn paths skip password and `IME_FLAG_NO_PERSONALIZED_LEARNING` fields.
- **Opt-in addon surfaces (smart-compose, translation, MCP):** every invocation runs through `SensitiveFieldGuard` first; sensitive fields short-circuit to a safe no-result.
- **Personal dictionary backup:** excluded from cloud-backup paths; device-transfer kept.

Full posture: [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md).

### Open Source

- [GitHub](https://github.com/SysAdminDoc/SwiftFloris) — Full source code available.
- [Apache License 2.0](LICENSE) — Permissive, audit-friendly.

## Multilingual support

SwiftFloris ships first-class **bilingual subtype presets** for SwiftKey-style EN+ES / EN+FR / EN+DE typing, plus per-token language identification over Latin-script subtypes (EN / ES / FR / DE / IT / PT). The multilingual ranker refuses to autocommit when the two strongest plausible replacement candidates come from different enrolled languages, so cross-language autocorrects stop bleeding into the wrong sentence.

For non-Latin scripts, the transliteration layer currently covers **63 scripts** ranging from Devanagari, Bengali, Tamil, Telugu, Gujarati, Gurmukhi, Kannada, Malayalam, Odia, Sinhala (Indic) through Khmer, Burmese, Lao, Thai, Tibetan, Mongolian, Javanese, Sundanese (Brahmic) into Arabic with FE70-FEFC connected-form shaping, Persian / Urdu / Hebrew normalisers, and historical scripts such as Phoenician, Imperial Aramaic, Avestan, and 20th-century constructed alphabets (Adlam, N'Ko, Cherokee, Vai, Bassa Vah, Mende Kikakui, Pahawh Hmong, Nyiakeng Puachue Hmong, Wancho, Medefaidrin).

Inline translation has the cache + language detector + sentence tokenizer + language-pack manager + router shipped on the IME side; the Bergamot WASM runtime addon is the outstanding piece tracked as L2.1a in the roadmap.

## MCP daemon bridge

SwiftFloris is the first FOSS Android keyboard to ship an end-to-end MCP (Model Context Protocol) daemon bridge. It binds **local-only** to MCP daemons advertised by other apps on the device (no network, no cloud), surfaces them in Settings → MCP daemon bridge, and lets users enable / disable individual daemons. The bridge is opt-in by construction: tool invocations route through the same `SensitiveFieldGuard` as smart-compose and translation, so password fields cannot trigger remote tool calls.

The full bridge spans `IMcpDaemon.aidl` (Binder surface), `AndroidMcpClient` (JSON envelope translation), `McpServiceConnectionManager` (per-daemon bind lifecycle), `McpAndroidDiscoverer` (PackageManager discovery), `McpDispatchRouter` (registry → guard → tool → response), and the Settings screen that lists bound daemons + per-daemon switches.

## Tasker integration

SwiftFloris exposes a Tasker intent contract for INSERT_TEXT / INSERT_CLIP / SWITCH_LAYOUT / TRIGGER_VOICE actions. See [`docs/TASKER_INTEGRATION.md`](docs/TASKER_INTEGRATION.md).

## Performance and benchmarks

Six Macrobenchmark trace sections are emitted from production code paths:

- `swiftfloris.ime.firstRender` (`FlorisImeService.onCreateInputView`)
- `swiftfloris.nlp.suggest` (`LatinLanguageProvider.suggest`)
- `swiftfloris.smartbar.candidates.recompose` (`CandidatesRow` body)
- `swiftfloris.theme.switch` (`ThemeManager.updateActiveTheme`)
- `swiftfloris.dict.load` (`loadSpecificDictionary`)
- `swiftfloris.nlp.symspell.build` (lazy index init)

Real device-number collection is tracked in [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md). The repository deliberately does not publish hand-wavy latency tables; numbers go in the benchmark doc with the device, OS build, and trace section that produced them.

## Testing

- **Unit tests:** Kotest, run with `./gradlew test`. Last reported HEAD: 998+ tests (post-v1.8.40), expanding with each release. The v1.8.47 hardening pass added defensive tests around dictionary import limits, voice-model atomic install, theme asset traversal, and quick-action serializer fallback.
- **Visual regression:** Roborazzi 1.60.0, plugin alias active. CI runs `:app:verifyRoborazziDebug` on every push / PR as a hard gate, backed by committed baselines for the maintainer chip, SwiftKey High Contrast, Aurora Animated, and Settings -> Addons surfaces.
- **Macrobenchmark:** trace sections wired in production hot paths; device-number capture is tracked separately.
- **No-network gate:** CI verifies the absence of `INTERNET` permission on every build.
- **Repo hygiene gate:** CI runs `scripts/check-no-root-crash-logs.sh` so root
  `hs_err_pid*.log` / `replay_pid*.log` files cannot be committed.

## Recent releases

The full release stream lives in the `RELEASE_NOTES_v*.md` files in the repository root, and on [GitHub Releases](https://github.com/SysAdminDoc/SwiftFloris/releases).

- **v1.8.144** (2026-05-18) — Backup flow trust states: backup progress, cancellation, share-sheet handoff, failure, and sensitive-clipboard exclusion now surface as explicit cards, with `BackupFlowNotice` policy coverage. ([notes](RELEASE_NOTES_v1.8.144.md))
- **v1.8.143** (2026-05-18) — Autocorrect lifecycle contract: `docs/AUTOCORRECT_LIFECYCLE.md` now defines spacebar, punctuation, backspace, hardware, glide-delete, provider-notification, manual QA, and regression-test contracts; accepted provider notifications now wait for successful editor commits. ([notes](RELEASE_NOTES_v1.8.143.md))
- **v1.8.142** (2026-05-18) — Theme rule edit policy extraction: `ThemeRuleEditPolicy` now owns add-rule selection validation, selector toggling, and key-code attribute parsing/replacement decisions for the theme editor. ([notes](RELEASE_NOTES_v1.8.142.md))
- **v1.8.141** (2026-05-18) — Punctuation flush policy extraction: `KeyboardAutoCommitFlushPolicy` now owns software non-letter autocorrect flush decisions for media mode, alphabetic keys, punctuation, numeric keys, and numeric/phone layouts. ([notes](RELEASE_NOTES_v1.8.141.md))
- **v1.8.140** (2026-05-18) — Candidate auto-commit policy extraction: `CandidateAutoCommitPolicy` now owns shortcut, phrase repair, active-strip, immediate fallback, quick-prediction, and rejected-correction gating decisions with focused JVM coverage. ([notes](RELEASE_NOTES_v1.8.140.md))
- **v1.8.139** (2026-05-18) — Dependency warning review: Gradle is checksum-pinned to 9.5.1, Navigation Compose is on 2.9.8, and JUnit Vintage is centralized at 6.0.3 after official-release review, clearing the dependency-version lint warnings. ([notes](RELEASE_NOTES_v1.8.139.md))
- **v1.8.138** (2026-05-18) — Conservative unused-resource cleanup: obsolete launcher/branding resources and dead legacy color tokens were removed after manifest/code/asset/test/dynamic lookup review, reducing lint from 289 warnings / 1 hint to 245 warnings / 1 hint. ([notes](RELEASE_NOTES_v1.8.138.md))
- **v1.8.137** (2026-05-18) — Theme editor validation tests: theme component metadata now validates through `ThemeComponentMetaValidationPolicy`, with JVM coverage for valid apply normalization, invalid fields, duplicate IDs, and blank stylesheet fallback. ([notes](RELEASE_NOTES_v1.8.137.md))
- **v1.8.136** (2026-05-18) — Subtype editor validation tests: editable subtype drafts now validate through `SubtypeEditorValidationPolicy`, with JVM coverage for default add-state missing fields, complete draft building, select-placeholder rejection, and edit-state preservation. ([notes](RELEASE_NOTES_v1.8.136.md))
- **v1.8.135** (2026-05-18) — Language pack import/update tests: extension import readiness now lives in `ExtensionImportPolicy`, with JVM coverage for new installs, user-installed updates, bundled-core rejection, corrupted metadata, wrong extension type, unsupported files, and import button enablement. ([notes](RELEASE_NOTES_v1.8.135.md))
- **v1.8.134** (2026-05-18) — Backup/restore policy tests: validation and operation-state decisions now live in `BackupRestorePolicy`, with JVM coverage for backup success/cancellation/failure, invalid archives, restore enablement, and partial-failure classification. ([notes](RELEASE_NOTES_v1.8.134.md))
- **v1.8.133** (2026-05-18) — Incognito suggestion privacy policy tests: app-declared no-learning override, dynamic toggle availability, committed-word learning, and touch-decoder evidence gates now have focused JVM coverage. ([notes](RELEASE_NOTES_v1.8.133.md))
- **v1.8.132** (2026-05-18) — Glide typing delete policy tests: immediate backspace word-delete escalation now lives in the editor input policy and is covered for enabled, disabled, inactive phantom-space, and explicit word-delete paths. ([notes](RELEASE_NOTES_v1.8.132.md))
- **v1.8.131** (2026-05-18) — Spacing lifecycle state tests: auto-space and phantom-space state transitions now have focused JVM coverage for one-update grace, composing-region visibility, and candidate-for-revert cleanup. ([notes](RELEASE_NOTES_v1.8.131.md))
- **v1.8.130** (2026-05-18) — Hardware keyboard input policy tests: hardware keydown/keyup routing now has focused JVM coverage for space, enter, delete pass-through, shift, mapped letters, mapped punctuation, and mapped punctuation flushing pending autocorrect before commit. ([notes](RELEASE_NOTES_v1.8.130.md))
- **v1.8.129** (2026-05-18) — Editor input behavior policy extraction: autocorrect spacebar commits, rejected-correction protection, punctuation auto-spacing, phantom spacing, double-space period, and sentence-capitalization gates now have focused JVM coverage through a pure policy class. ([notes](RELEASE_NOTES_v1.8.129.md))
- **v1.8.128** (2026-05-18) — Nastaliq Urdu font bundle: the official OFL-1.1 Noto Nastaliq Urdu TTF is now committed as an APK asset, Urdu subtype key labels and hints route Arabic-script text through it, and asset/license tests pin the bundle. ([notes](RELEASE_NOTES_v1.8.128.md))
- **v1.8.127** (2026-05-18) — Emoji pinned-group sheet: long-pressing emoji can now pin them to named groups, and pinned-group chips commit the saved emoji sequence from the palette. ([notes](RELEASE_NOTES_v1.8.127.md))
- **v1.8.126** (2026-05-18) — Addons dictionary catalog polish: Settings -> Addons now lists mounted dictionary packs with language, word count, dataset license, source, descriptor rejections, and updated install guidance. ([notes](RELEASE_NOTES_v1.8.126.md))
- **v1.8.125** (2026-05-18) — Addons dictionary asset mounting: enrolled dictionary-pack APK assets now feed the Latin dictionary store through `PackageManager#getResourcesForApplication(...)`, merge with bundled baselines, and reload when the live addon registry generation changes. ([notes](RELEASE_NOTES_v1.8.125.md))
- **v1.8.124** (2026-05-18) — Addons trust controls: Settings -> Addons can now reset all saved signing-certificate pins or trust a changed certificate after confirmation and rescan; the pin codec gained targeted package removal and the Addons Roborazzi baseline was refreshed. ([notes](RELEASE_NOTES_v1.8.124.md))
- **v1.8.123** (2026-05-18) — Roborazzi baseline hard gate: committed screenshot baselines for the maintainer chip, SwiftKey High Contrast, Aurora Animated, and Settings -> Addons surfaces; CI now fails on visual-regression drift instead of using `continue-on-error`. ([notes](RELEASE_NOTES_v1.8.123.md))
- **v1.8.104 – v1.8.122** (2026-05-17/18) — seventh-pass audit closure and follow-up slices: app-declared `IME_FLAG_NO_PERSONALIZED_LEARNING` and `EXTRA_IS_SENSITIVE` privacy flags are honoured, voice handoff refuses sensitive fields, checks every external voice IME's microphone grant, exposes a durable Listening state, and now gates the in-app Whisper/Vosk route selector and model catalog behind a preview-only local-runtime flag; dangerous voice remove commands were tightened, the voice setup activity is non-exported with a validated setup-intent contract, clipboard backup/clear-all leaks were closed, provider-backed clipboard media clones now cap image/video bytes, image preview decode rejects oversized dimensions before allocation, automatic clipboard history eviction now closes provider-backed media before deleting rows, sensitive clipboard text no longer feeds pin-popup description URL/email/phone classification, startup reconciliation removes missing-file history rows plus unreferenced provider files / metadata rows, media restore recreates provider metadata for restored image/video clips, failed foreign media URI clones no longer create phantom history entries, clipboard history maintenance no longer sorts or evicts on Main, the dead parallel Tink clipboard-history store has been removed so the Room-backed manager is the only live storage path, and the KenLM mmap reader now rejects header/pre-body offsets instead of aliasing them to trie-body bytes. ([latest notes](RELEASE_NOTES_v1.8.122.md))
- **v1.8.85 – v1.8.103** (2026-05-17) — cross-subsystem hardening pass + 18 single-feature follow-up releases. v1.8.85 was an explicit AGENTS.md §6 one-time deviation that closed eleven privacy / security / reliability gaps (merged-manifest `verifyNoInternetPermission`, Android 12+ `data_extraction_rules.xml`, atomic `ZipUtils.unzip`, thread-safe `HardwareKeyboardRuntimeMapper`, sticker decoder OOM, sticker MIME spoof, addon enumerator size category-error, `verify-reproducible-apk.sh` payload-manifest pass criterion, CI workflow permissions, `pull_request_target` injection, AltGr); v1.8.86 – v1.8.102 then returned to per-PR scope and closed eleven of twelve F-roster items (FLAG_SECURE on numeric PIN + passphrase dialog, legacy-passphrase recovery, ZipUtils abort policy, SAF lost-grant UX, addon spec docs alignment, LDML `shift=` semantics, fastlane script hardening, SHA-pinned floating action tags, `release.yml` keystore hygiene, `verifyDataExtractionRules` build gate, sticker LRU + folder cap, `HardwareKeyEntry.longPressAlternates`); v1.8.103 closes the documentation half (README + PROJECT_CONTEXT version refresh, master index of the session's commits). The remaining F11 Roborazzi baseline item closed in v1.8.123. ([master index](RELEASE_NOTES_v1.8.103.md))
- **v1.8.84** (2026-05-17) — Settings → Addons status surface: users can inspect accepted/rejected addon APKs, manually rescan through the startup reconciliation path, and review package/license/version/size/signing-fingerprint details. ([notes](RELEASE_NOTES_v1.8.84.md))
- **v1.8.83** (2026-05-17) — Addon registry startup wiring: the IME now scans installed addon manifests at startup, reconciles them through persisted signing pins, publishes a process-wide registry, and cleans malformed stored pin lines. ([notes](RELEASE_NOTES_v1.8.83.md))
- **v1.8.82** (2026-05-17) — Addon signing-pin persistence: `AddonSigningPinSet` safely parses/encodes addon package fingerprint pins and `prefs.addon.signingCertPins` gives the registry a durable trust store consumed by v1.8.83 startup wiring and v1.8.84 Settings status UI. ([notes](RELEASE_NOTES_v1.8.82.md))
- **v1.8.81** (2026-05-17) — Addon catalog foundation: `AddonRegistry` now reconciles live addon state with signing-certificate pins, and `DictionaryPackCatalog` validates dictionary-pack descriptors plus provenance before Settings/Addons UI and asset mounting land. ([notes](RELEASE_NOTES_v1.8.81.md))
- **v1.8.80** (2026-05-17) — SQLCipher provider migration plan: documented the current LibTomCrypt-based Android Community AAR state, OpenSSL proof-of-concept path, migration triggers, 16 KB page-size gates, and rollback rules without changing the runtime dependency. ([notes](RELEASE_NOTES_v1.8.80.md))
- **v1.8.79** (2026-05-17) — Honeycomb hex layout wire-up: the bundled honeycomb character layout is registered for subtype selection, routed through `TextKeyboardLayoutStyle.Honeycomb`, clipped to `HoneycombHexShape`, and hit-tested against the actual hex instead of rectangular bounding boxes. ([notes](RELEASE_NOTES_v1.8.79.md))
- **v1.8.78** (2026-05-17) — Keyman `.kmp` package import foundation: safe ZIP/package parser for `kmp.json`, keyboard/language/example metadata, LDML-in-package extraction, lexical-model classification, compiled-engine-required classification, and unsafe entry skipping. ([notes](RELEASE_NOTES_v1.8.78.md))
- **v1.8.77** (2026-05-17) — User-imported sticker folder: Settings → Emoji & stickers can persist a local SAF folder URI, enumerate supported image files into an Imported sticker pack, preview them in the sticker grid, and commit them through the existing rich-content provider path. ([notes](RELEASE_NOTES_v1.8.77.md))
- **v1.8.76** (2026-05-17) — Hardware-keyboard runtime mapping: imported layouts can bind to Android hardware `deviceId` values, resolve `KeyEvent` scan/key codes through KLC/macOS fallbacks, and commit mapped printable characters through `KeyboardManager`. ([notes](RELEASE_NOTES_v1.8.76.md))
- **v1.8.75** (2026-05-17) — Hardware-keyboard import: added an XXE-hardened macOS `.keylayout` XML parser that normalizes key maps, modifier maps, and action-backed dead keys into `HardwareKeyboardLayout`. ([notes](RELEASE_NOTES_v1.8.75.md))
- **v1.8.74** (2026-05-17) — Bump-batch C: Android Gradle Plugin `9.0.0` → `9.2.1` and Compose BOM `2026.03.01` → `2026.05.00`; R8 keepattributes audit required no rule changes. ([notes](RELEASE_NOTES_v1.8.74.md))
- **v1.8.73** (2026-05-17) — Repo hygiene: local root JVM crash/replay logs moved to `.ai/local-crash-logs/2026-05-16/`, and CI now rejects committed root `hs_err_pid*.log` / `replay_pid*.log` files. ([notes](RELEASE_NOTES_v1.8.73.md))
- **v1.8.72** (2026-05-17) — Roadmap correction: HeliBoard / NLnet open-glide integration is now treated as an additive future track, while SwiftFloris's shipped `StatisticalGlideTypingClassifier` remains the production glide path until a permissive open library and dataset are actually available. ([notes](RELEASE_NOTES_v1.8.72.md))
- **v1.8.71** (2026-05-17) — Bump-batch B: Roborazzi `1.55.0` → `1.60.0` and Robolectric `4.14.1` → `4.16.1`; no app code, permissions, or runtime behavior changed. ([notes](RELEASE_NOTES_v1.8.71.md))
- **v1.8.70** (2026-05-17) — README migration-window follow-up: Samsung / Grammarly keyboard-workflow callouts, Galaxy AI Writing Assist compatibility note for One UI 7+, Grammarly Keyboard replacement note, and release-front-door refresh. ([notes](RELEASE_NOTES_v1.8.70.md))
- **v1.8.69** (2026-05-17) — Bump-batch A: coroutines `1.11.0`, KSP `2.3.8`, ZXing `3.5.4`, and AboutLibraries `14.2.0`; beta AboutLibraries `15.0.0-b01` intentionally skipped. ([notes](RELEASE_NOTES_v1.8.69.md))
- **v1.8.68** (2026-05-17) — N7.6 Tink / AndroidKeystore migration: removed AndroidX Security Crypto, added shared Tink encrypted-preference wrapper, migrated SQLCipher passphrase and legacy clipboard-history payloads one time when old keysets remain readable. ([notes](RELEASE_NOTES_v1.8.68.md))
- **v1.8.67** (2026-05-17) — N12.5 reproducible-build self-verification CI: new build-twice release APK workflow plus `scripts/verify-reproducible-apk.sh` clean-worktree byte comparison and drift manifests. ([notes](RELEASE_NOTES_v1.8.67.md))
- **v1.8.66** (2026-05-17) — N8.7 Article 50 transparency surface: first-run **Review local AI features** setup step, reopenable Settings → About → **AI features in this keyboard** screen, docs links, and catalog test coverage for next-word / glide / voice / translation / smart-compose disclosures. ([notes](RELEASE_NOTES_v1.8.66.md))
- **v1.8.65** (2026-05-17) — Phase A3 Settings wiring: **Export encrypted** passphrase dialog + `.sfexp` create-document flow, direct encrypt-then-write personal-dictionary export, `SFEXP1` import sniffing, passphrase decrypt, and `DictionaryImporter`/rollback-summary routing for decrypted SwiftFloris combined-list files. ([notes](RELEASE_NOTES_v1.8.65.md))
- **v1.8.64** (2026-05-17) — Phase D1: calendar quick-insert (`QuickAction.InsertCalendarEvent`) reads local `CalendarContract.Instances` entries for today + next 7 days, opens an IME-local agenda picker, and inserts the selected event title + date/time. `READ_CALENDAR` is requested only after explicit tap. ([notes](RELEASE_NOTES_v1.8.64.md))
- **v1.8.63** (2026-05-17) — Phase C3: bundled SwiftKey High Contrast (AAA) and Aurora Animated themes, with Snygg stylesheet tests and a reduced-motion-aware GenericShape aurora background. ([notes](RELEASE_NOTES_v1.8.63.md))
- **v1.8.62** (2026-05-17) — Phase C1: split-keyboard renderer wire-up with gutter-aware layout, viability gating, and touch-hit suppression inside the gutter. ([notes](RELEASE_NOTES_v1.8.62.md))
- **v1.8.61** (2026-05-17) — Phase B2: quick-prediction-insert threshold tuning with a configurable weighted-confidence floor and aligned plain-space suppression. ([notes](RELEASE_NOTES_v1.8.61.md))
- **v1.8.60** (2026-05-17) — Phase B1: multilingual cold-start sentence/phrase priors plus top-1,000 Zipf seed overlays for CS/DE/ES/FR/IT/PT. ([notes](RELEASE_NOTES_v1.8.60.md))
- **v1.8.59** (2026-05-17) — Phase D3: Typing Stats now shows current-week accepted corrections versus last week, backed by bounded weekly metadata in `CorrectionOutcomePriors`. ([notes](RELEASE_NOTES_v1.8.59.md))
- **v1.8.58** (2026-05-17) — Phase D2: generic task-creation quick action (`QuickAction.InsertTask`). On-device replacement for SwiftKey's Microsoft-To-Do tile via `Intent.ACTION_SEND` chooser; works with Tasks.org / OpenTasks / Google Tasks / Joplin / Notion / Markor. `SensitiveFieldGuard` gate. ([notes](RELEASE_NOTES_v1.8.58.md))
- **v1.8.57** (2026-05-17) — Phase C2: SwiftKey "Modes → Arrow keys" parity via new `BottomRowPreset.Navigation` (← ↑ space ↓ → enter). ([notes](RELEASE_NOTES_v1.8.57.md))
- **v1.8.56** (2026-05-17) — Phase B4: same-sentence language-switch hardening via geometric-decay weighted blend in `TrailingContextLanguageBlend`. ([notes](RELEASE_NOTES_v1.8.56.md))
- **v1.8.55** (2026-05-17) — Phase B3: shared-spelling bilingual handling — sub-floor `0.30` confidence on one-locale candidates overwriting shared typed words. ([notes](RELEASE_NOTES_v1.8.55.md))
- **v1.8.54** (2026-05-17) — Phase A3 codec primitive: encrypted-blob personal-dictionary export envelope (AES-256-GCM + PBKDF2-HMAC-SHA-256 at OWASP-2025's 600 000 iterations). ([notes](RELEASE_NOTES_v1.8.54.md))
- **v1.8.53** (2026-05-17) — Phase A2: post-import confirmation + rollback dialog + wired `DictionaryImporter` into Settings UI. ([notes](RELEASE_NOTES_v1.8.53.md))
- **v1.8.52** (2026-05-17) — SwiftKey migration outreach push: README banner + opening pitch lead with the 2026-05-31 cutoff, badge promoted, parity-roadmap permalink linked. ([notes](RELEASE_NOTES_v1.8.52.md))
- **v1.8.51** (2026-05-17) — N14.3 + N14.4 Compose BOM + Gradle wrapper dependency-pin audits. New audit-log table in `docs/DEPENDENCY_TRIAGE.md`. ([notes](RELEASE_NOTES_v1.8.51.md))
- **v1.8.50** (2026-05-17) — N17.1 emoji-picker crash triage; root-caused to `Paint.hasGlyph("")` and closed with three defensive filters. ([notes](RELEASE_NOTES_v1.8.50.md))
- **v1.8.49** (2026-05-17) — N15.3 Smart Edit voice REMOVE_ITEM_FROM_LIST: new parameterised voice-command type that excises a named item from the dictated buffer mid-stream. ([notes](RELEASE_NOTES_v1.8.49.md))
- **v1.8.48** (2026-05-17) — Adversarial-input + lifecycle hardening pass across the SwiftKey JSON importer, MCP daemon bridge, IME service teardown, voice-model install, ZIP extraction, and DB cursor handling. ([notes](RELEASE_NOTES_v1.8.48.md))
- **v1.8.47** (2026-05-16) — N1.4 FUTO swipe-trace replay and benchmark harness. ([notes](RELEASE_NOTES_v1.8.47.md))
- **v1.8.46** (2026-05-16) — SwiftKey `swiftkey-cloud.json` import parser ahead of the 2026-05-31 account retirement. New `DictionaryImportFormat.JSON` + tolerant `parseSwiftKeyJson`. ([notes](RELEASE_NOTES_v1.8.46.md))
- **v1.8.45** (2026-05-16) — Android 17 IME-visibility restore across configuration changes. ([notes](RELEASE_NOTES_v1.8.45.md))
- **v1.8.44** (2026-05-16) — Long-press popup guard on password fields (`KeyVariation.PASSWORD`). ([notes](RELEASE_NOTES_v1.8.44.md))
- **v1.8.43** (2026-05-16) — Roborazzi plugin unblocked at 1.55.0; visual-regression CI step added. ([notes](RELEASE_NOTES_v1.8.43.md))
- **v1.8.42** (2026-05-16) — Kotlin 2.3.20 → 2.3.21 bug-fix bump. ([notes](RELEASE_NOTES_v1.8.42.md))
- **v1.8.41** (2026-05-16) — Auto-return to letter keyboard after apostrophe in symbols panel. ([notes](RELEASE_NOTES_v1.8.41.md))
- **v1.8.40** (2026-05-16) — Per-daemon enable / disable for the MCP bridge in Settings. ([notes](RELEASE_NOTES_v1.8.40.md))
- **v1.8.35–v1.8.39** — Full MCP daemon bridge: AIDL surface, AndroidMcpClient, per-daemon bind lifecycle, discoverer, IME-startup wire-up, Settings UI.
- **v1.8.34** — Macrobenchmark trace instrumentation across six production hot paths.
- **v1.8.31–v1.8.33, v1.8.79** — Honeycomb hex renderer foundation (`HoneycombHexShape` + `HoneycombHexButton` + `HoneycombKeyboardRow` + `HoneycombLayoutLoader`) and production `TextKeyboardLayout` wire-up.
- **v1.8.0–v1.8.30** — Smart-compose / inline-translation router stack, KenLM reader pipeline, 63-script transliteration build-out, addon scaffold sweep, SwiftKey-parity slices.
- **v1.7.x** — Multilingual hot-switch, bigram + trigram next-word, adaptive touch, SymSpell d1+d2, Flow Through Space, encrypted personal dictionary.
- **v1.6.0** — Personal-learning dictionary + 117k SCOWL English + SwiftKey design tokens.
- **v1.5.0** — FUTO Voice Input integration (replacing Google Speech Recognizer).

See [`ROADMAP.md`](ROADMAP.md) §3 for the full reconciled version table back to v1.1.0.

## Contributing

SwiftFloris welcomes focused contributions in themes, dictionary packs,
transliteration tables, performance work, bug fixes, accessibility, and docs.
Before opening a PR, read [`CONTRIBUTING.md`](CONTRIBUTING.md) and keep the
base-app invariants intact: no network permission, no telemetry, no account
binding, Apache-2.0-compatible `:app` code, and no closed-source blobs.

## Troubleshooting

### Gesture typing not working?

See [Multilingual Gesture Typing](docs/GESTURE_TYPING_MULTILINGUAL.md). Gesture typing currently uses the bounded statistical engine for EN / DE / ES / FR / IT / PT; the neural / open-glide path is gated on the HeliBoard NLnet release.

### Voice input unavailable?

See [FUTO Voice Input Troubleshooting](docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md). SwiftFloris does not record audio itself; live dictation hands off to the user-installed FUTO Voice Input app or another enabled external voice keyboard. The in-app Whisper/Vosk catalog is preview-only until the local recognizer runtime ships.

### Keyboard crashes on emoji insertion?

Root-caused in **v1.8.50** (ROADMAP §6 N17.1, [release notes](RELEASE_NOTES_v1.8.50.md), [GitHub issue #1](https://github.com/SysAdminDoc/SwiftFloris/issues/1)). The trigger was `Paint.hasGlyph("")` aborting the palette render whenever an empty-value `Emoji` reached the initial filter pass. Three defensive filters landed at the palette, history-mapping, and asset-loader layers. If you still see this on v1.8.50+ please attach the device model, Android build, ROM, and a logcat capture to the issue.

### Theme changes not applying?

Force-close the keyboard via Settings → Apps → SwiftFloris → Force stop, then re-open a text field. If a theme imported from an extension package fails, check Settings → Extensions for the asset's status — the asset resolver canonicalises paths and rejects anything outside the loaded theme directory.

## License

```
Copyright 2026 SwiftFloris Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

- **FlorisBoard** — Solid IME architecture and Snygg theme engine.
- **FUTO** — Privacy-first voice input, available as a separate app.
- **SCOWL** — English word list.
- **Bergamot, librime, KenLM, LiteRT-LM** — Open-source language stacks that the addon scaffolds target.
- **HeliBoard, AnySoftKeyboard, Unexpected Keyboard, Thumb-Key, fcitx5-android, Trime, OpenBoard** — Adjacent open-source keyboards that this project learns from.
- **Jetpack Compose** and **Material Design 3** — Modern Android UI.

## Status

🚀 **Active development.** Current release: **v1.8.144** (2026-05-18). Migration window for SwiftKey users closes **2026-05-31** — 13 days from this release.

---

## Quick Links

| Resource | Link |
|----------|------|
| **GitHub** | https://github.com/SysAdminDoc/SwiftFloris |
| **Issues** | https://github.com/SysAdminDoc/SwiftFloris/issues |
| **Releases** | https://github.com/SysAdminDoc/SwiftFloris/releases |
| **Roadmap** | [ROADMAP.md](ROADMAP.md) |
| **SwiftKey migration** | [docs/MIGRATE_FROM_SWIFTKEY.md](docs/MIGRATE_FROM_SWIFTKEY.md) |
| **Privacy and AI** | [docs/PRIVACY_AND_AI.md](docs/PRIVACY_AND_AI.md) |
| **Threat model** | [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) |
| **FUTO Voice** | https://voiceinput.futo.org/ |
| **FlorisBoard upstream** | https://github.com/florisboard/florisboard |

**Made for privacy and offline-first computing.**
