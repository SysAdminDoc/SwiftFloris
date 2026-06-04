# SwiftFloris

![Version](https://img.shields.io/badge/version-v1.8.241-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green) ![Platform](https://img.shields.io/badge/platform-Android%208.0+-orange) ![Network](https://img.shields.io/badge/network-none-lightgrey) ![Dictionary imports](https://img.shields.io/badge/dictionary%20imports-local%20files-green)

**SwiftFloris** is a privacy-first Android keyboard, forked from FlorisBoard and pushed toward SwiftKey-class multilingual typing without the cloud. It ships under Apache-2.0, holds no `INTERNET` permission, and binds zero accounts.

> **Zero cloud processing. Zero telemetry. Zero account. All features work offline.**

> ## Keyboard migration
>
> Microsoft retired standalone SwiftKey accounts and shut down the
> [`data.swiftkey.com`](https://data.swiftkey.com) export endpoint on 2026-05-31. Cloud exports from that endpoint are no longer available, but SwiftFloris still supports local import paths for files you already exported and for exports from other keyboards.
>
> **No-cloud import paths:**
>
> 1. **During setup** — use the first-run **Import your dictionary** step and choose a SwiftKey JSON, Gboard XML/ZIP, FlorisBoard CSV, `.flbackup`, `.fldic`, or SwiftFloris dictionary export.
> 2. **Any time later** — go to **Settings → Personal dictionary → Import** and pick the same local export files. SwiftFloris ingests SwiftKey JSON directly (see [v1.8.46 release notes](CHANGELOG.md#v1.8.46) and the [migration walk-through](docs/MIGRATE_FROM_SWIFTKEY.md)).
> 3. **If you missed the SwiftKey cutoff** — your learned words are gone from the cloud, but everything still in the on-device SwiftKey personal dictionary can still be re-typed; SwiftFloris's [instant-remember overlay](CHANGELOG.md#v1.8.26) climbs the words back to the top of the prediction strip after a single use.
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

| Area | What's in v1.8.241 | Privacy posture |
|------|-------------------|-----------------|
| **Autocorrect / prediction** | SCOWL 117k English dictionary, SymSpell d1+d2, bigram + trigram next-word, capitalization-aware completions, contraction handling, instant-remember user-dictionary overlay | On-device |
| **Multilingual typing** | Bilingual subtype presets (EN+ES / EN+FR / EN+DE), per-token Latin language identification, top-two straddle guard, sentence-local context scoring, and opt-in remembered keyboard language per app | On-device |
| **Scripts** | Devanagari + Bengali + Tamil + Telugu + ... (63-script transliteration coverage); RTL Arabic shaper, Persian / Urdu / Hebrew normalisers, bundled Noto Nastaliq Urdu rendering for Urdu subtype key text | On-device |
| **Gesture typing** | `StatisticalGlideTypingClassifier` over bounded EN / DE / ES / FR / IT / PT dictionaries with adaptive touch evidence | On-device |
| **Voice input** | FUTO Voice Input handoff (live path), FUTO install guidance when no voice keyboard is available, plus preview-only local Whisper/Vosk route selector and model catalog until a recognizer runtime ships | SwiftFloris itself does not record audio |
| **Emoji & stickers** | Emoji search/history/pinned groups with an in-keyboard pin-to-group sheet, bundled local sticker packs, and user-imported SAF sticker folders for PNG / WebP / JPEG / GIF files | Local folder URI only |
| **Clipboard** | Room-backed history with pinning + per-app source tag, media/provider metadata, sensitive-item gates, startup/restore reconciliation, in-keyboard text search with type-filter composition, and TalkBack labels for image/video media history tiles | On-device |
| **Productivity** | Calendar quick-insert reads local agenda entries for today + next 7 days; task quick-insert sends selected text to user-chosen task / note apps | Calendar permission is explicit opt-in; no network |
| **Themes** | 21 bundled themes — SwiftKey Pure (Light/Dark + M3 Expressive), SwiftKey High Contrast (AAA), Aurora Animated, Floris Day/Night, Swift Glacier, Swift Slate, M3E Nord (light + dark), Tokyo Night, Dracula, Catppuccin Mocha; borderless variants where applicable; Snygg theme engine; per-app accent with Settings preview and one-time opt-in hint | No telemetry |
| **MCP daemon bridge** | AIDL bridge to user-installed MCP daemons with per-daemon enable / disable in Settings → MCP daemon bridge | Local-only binder, no network |
| **Addon packs** | Addon manifest/enumerator contracts, IME-startup registry reconciliation, Settings -> Addons status/rescan, explicit trust for non-co-signed addons, trust reset/changed-certificate controls, dictionary-pack catalog details, persisted signing-certificate pins, descriptor validation, provenance reports, typed dictionary-pack catalog, and addon APK dictionary asset mounting | No-network addon rejection |
| **Settings UX** | Five-bucket Settings home (Typing experience, Personalization, Privacy & data, Advanced, About), global Settings search with accent-insensitive matching, first-open focus, clear action, Search IME action, no-results path back to all settings, synonym hits for dark theme, haptic, trace, punctuation, and privacy queries, result-list scroll reset, TalkBack labels/live result-status/result-row context, and one-shot dismissible destination highlights; clearer empty states for voice setup, selected user-dictionary languages, extension categories, language packs, filtered clipboard history, and theme-manager recovery; user-dictionary back feedback during active save/delete/import/export work; surfaced keyboard preview field with ready/active state feedback | Local UI only |
| **Migration** | First-run local dictionary import hint; preview-before-save personal dictionary imports with row exclusion; Gboard / FlorisBoard / SwiftKey JSON export importer; passphrase-encrypted SwiftFloris dictionary export/import; Settings-based Keyman LDML / `.kmp` metadata + Windows KLC + macOS hardware-keyboard imports | All file-system based |
| **Sync scaffold** | Transport-neutral personal-dictionary sync model with QR pairing payloads, X25519/AES-GCM sealed-box v1 envelope constants, deterministic fixed-key vectors, and CRDT merge tests | No network; user-chosen local transport |
| **Editor reliability** | Expected-content generation for selection, text commit, composing finalize, and composing-region replacement paths now happens before `InputConnection` batch edits, with try/finally begin/end pairing and focused call-order tests | Local editor state only |
| **Alternative layouts** | Colemak / Dvorak / Workman from the FlorisBoard layout pack, plus selectable honeycomb hex layout with clipped hex keys and hex-aware hit testing (only FOSS Android keyboard shipping this — Typewise vacated the consumer market early 2026; see [docs/HONEYCOMB_LAYOUT.md](docs/HONEYCOMB_LAYOUT.md)) | On-device |
| **AI transparency** | First-run AI/ML explainer plus Settings → About → AI features screen covering next-word, glide, voice, translation, and smart compose; async suggestion work consumes request-scoped privacy snapshots for incognito, no-personalized-learning, offensive-content, and ghost-text sensitivity gates | On-device, no account, no telemetry |
| **CI / build** | No-network gate, repo-hygiene gate, OSV dep scan, Dependabot version review, lint baseline-drift wrapper with no committed app lint baseline, startup crash recovery via the local crash dialog, restore/crash diagnostics routed through project logging with safe fallback copy, settings-search resource/route drift guard, MIME helper aggregate-contract tests, post-hotfix regression coverage for Arabic shaping, Snygg imports, private trace suppression, and locale-scoped n-gram flushes, manual emulator settings smoke, reproducible-build toolchain pins + build-twice APK self-check chained into release publication, Roborazzi visual-regression hard gate with committed theme/Addons baselines, Macrobenchmark trace sections in 6 hot paths, manual benchmark trend-regression report, and compatible dependency freshness through Compose BOM 2026.05.01 / KSP 2.3.9 / Roborazzi 1.63.0 | Audit-friendly |

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

Full step-by-step paths are in [`docs/MIGRATE_FROM_SWIFTKEY.md`](docs/MIGRATE_FROM_SWIFTKEY.md); the headline contract — `swiftkey-cloud.json` ingestion through **Settings → Personal dictionary → Import** — landed in [v1.8.46](CHANGELOG.md#v1.8.46), the cumulative-byte hardening of the JSON parser in [v1.8.48](CHANGELOG.md#v1.8.48), the post-import confirmation + rollback in [v1.8.53](CHANGELOG.md#v1.8.53), the encrypted-blob export codec primitive in [v1.8.54](CHANGELOG.md#v1.8.54), the Settings UI encrypted export/import round-trip in [v1.8.65](CHANGELOG.md#v1.8.65), and the parity-roadmap reference for the **2026-05-31** cutoff lives in [`docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`](docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md).

## Documentation

Project-internal docs all live in the repository:

- [`COMPLETED.md`](COMPLETED.md) — shipped-state summary.
- [`RESEARCH_REPORT.md`](RESEARCH_REPORT.md) — current research synthesis.
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — module, package, runtime, security-boundary, and CI architecture map.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — contributor setup, verification, privacy, and release expectations.
- [`docs/MIGRATE_FROM_SWIFTKEY.md`](docs/MIGRATE_FROM_SWIFTKEY.md) — SwiftKey account-retirement migration paths.
- [`docs/PRIVACY_AND_AI.md`](docs/PRIVACY_AND_AI.md) — AI/ML feature transparency and local-processing disclosure.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — privacy / security threat model and mitigations.
- [`docs/SQLCIPHER_PROVIDER_MIGRATION.md`](docs/SQLCIPHER_PROVIDER_MIGRATION.md) — SQLCipher crypto-provider migration triggers, OpenSSL proof-of-concept path, and 16 KB verification gates.
- [`docs/REPRODUCIBLE_BUILDS.md`](docs/REPRODUCIBLE_BUILDS.md) — pinned toolchain and F-Droid rebuild plan.
- [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md) — Macrobenchmark trace sections, workflow, and regression threshold contract.
- [`docs/LOCAL_VERIFICATION.md`](docs/LOCAL_VERIFICATION.md) — maintainer local test/build/lint/device commands.
- [`docs/REPO_HYGIENE.md`](docs/REPO_HYGIENE.md) — generated-output, deleted-doc, commit-scope, and handoff rules.
- [`docs/INLINE_AUTOFILL.md`](docs/INLINE_AUTOFILL.md) — inline-autofill matrix and password-manager verification.
- [`docs/TASKER_INTEGRATION.md`](docs/TASKER_INTEGRATION.md) — Tasker intent contract.
- [`docs/FONTS.md`](docs/FONTS.md) — bundled fonts (Nastaliq + Naskh fallback).
- [`docs/AUTOCORRECT_LIFECYCLE.md`](docs/AUTOCORRECT_LIFECYCLE.md) — autocorrect, spacebar, punctuation, backspace, provider-notification, and QA contract.
- [`docs/GESTURE_TYPING_MULTILINGUAL.md`](docs/GESTURE_TYPING_MULTILINGUAL.md) — multilingual gesture-typing guide.
- [`docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md`](docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md) — FUTO Voice Input setup + recovery actions.
- [`docs/VOICE_COMMANDS.md`](docs/VOICE_COMMANDS.md) — built-in and custom voice-command grammar reference.
- [`docs/addons/dictionary-pack-spec.md`](docs/addons/dictionary-pack-spec.md) — external dictionary-pack APK descriptor and validation contract.
- [`ROADMAP.md`](ROADMAP.md) — single source of truth for all planned work.
- [`docs/archive/ROADMAP_v5.67_2026-05-18.md`](docs/archive/ROADMAP_v5.67_2026-05-18.md) — archived historical tiered roadmap (v5.67).
- [`docs/archive/IMPROVEMENT_PLAN_2026-05-18.md`](docs/archive/IMPROVEMENT_PLAN_2026-05-18.md) — archived execution-focused quality / UX / a11y / perf / test / delivery plan.
- [`docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md) — archived 2026-05-25 research plan.
- [`CHANGELOG.md`](CHANGELOG.md) — full release history, one section per version (anchor: `#vX.Y.Z`).

## Architecture & Stack

**Language and build**

- Kotlin 2.3.21, Compose BOM 2026.05.01, Material 3 + material-kolor.
- AGP 9.2.1, Gradle 9.5.1, JDK 21.
- KSP 2.3.9, Room 2.8.4, SQLCipher 4.16.0, Tink Android 1.21.0.
- Kotest 6.1.11 unit-test runner; Roborazzi 1.63.0 and Robolectric 4.16.1
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
:benchmark                 — Macrobenchmark + adb benchmark harness (active in settings)
```

Native runtimes for optional capabilities (LiteRT-LM, Bergamot, librime, ML
Kit Digital Ink, Vosk) ship as out-of-tree signed addon APKs through the
addon enrolment contract, not as a `:lib:native` module in the base APK.

The IME's main work lives under `app/src/main/kotlin/dev/patrickgold/florisboard/ime/{keyboard,nlp,theme,ext,emoji,mcp,voice,bidi,dictionary,kenlm}`.

## Building

### Prerequisites

```bash
# Android SDK 36 (compile/target)
# JDK 21+
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

# Release-variant Roborazzi gate (run before publishing)
./gradlew :app:verifyRoborazziRelease
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
- **IME window:** `FLAG_SECURE` set on password/no-personalized-learning fields and while incognito is active, including mid-session dynamic incognito toggles, so the keyboard is excluded from screenshots and screen-recording overlays.
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

Real device-number collection is tracked in [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md). Current SM-S938B / Android 16 baselines record `am start -W` first-render medians of `TotalTime` 31.0 ms and `WaitTime` 34.0 ms, benchmark-only `swiftfloris.ime.firstRenderMs` median 18.335469 ms, cold provider-direct `swiftfloris.nlp.firstSuggestionMs` median 1878.616249 ms for `teh`, dictionary cold-load / preload medians of 757.353333 ms / 772.080625 ms with lazy SymSpell d1/d2 index medians of 500.230156 ms / 532.298281 ms, candidate-row warm-typing recomposition median body / max / total of 0.326563 ms / 0.770365 ms / 4.069529 ms, theme-switch median body / max / total of 18.541197 ms / 19.587708 ms / 57.505571 ms with 0.2808075 ms cached warm switches, and backup/restore default-archive medians of 12.653698 ms backup create / 9.874167 ms restore total with 3/3 sections restored. The manual Benchmark Regression workflow compares candidate JSON against those baselines and fails watched medians above the documented +8 % window. The repository deliberately does not publish hand-wavy latency tables; numbers go in the benchmark doc with the device, OS build, and trace section or log marker that produced them.

## Testing

- **Unit tests:** Kotest, run with `./gradlew test`. Last reported HEAD: 998+ tests (post-v1.8.40), expanding with each release. The v1.8.47 hardening pass added defensive tests around dictionary import limits, voice-model atomic install, theme asset traversal, and quick-action serializer fallback.
- **Visual regression:** Roborazzi 1.63.0, plugin alias active. CI runs `:app:verifyRoborazziDebug` on every push / PR as a hard gate, and the release workflow runs `:app:verifyRoborazziRelease` before APK publication. Baselines cover the maintainer chip, SwiftKey High Contrast, Aurora Animated, and Settings -> Addons surfaces.
- **Macrobenchmark:** `:benchmark` is wired for AndroidX trace/frame runs, and the adb harness scripts record repeatable IME first-render, first-suggestion, dictionary-load, candidate-row recomposition, theme-switch, and backup/restore baselines. The manual Benchmark Regression workflow runs the adb suite, uploads candidate JSON, and compares watched medians against the committed baseline set.
- **No-network gate:** CI verifies the absence of `INTERNET` permission on every build.
- **Lint drift:** CI lint runs through `scripts/run-lint-debug-with-baseline-check.sh`, which fails stale baseline entries instead of leaving them as console-only noise.
- **Emulator smoke:** The manual `Android Emulator Smoke` workflow builds the debug APK, launches the Settings app on an emulator, and uploads logcat for crash triage.
- **Repo hygiene gate:** CI runs `scripts/check-no-root-crash-logs.sh` so root
  `hs_err_pid*.log` / `replay_pid*.log` files cannot be committed, and
  `scripts/check-repo-hygiene.sh` rejects tracked generated build/report output.

## Recent releases

The full release stream lives in [`CHANGELOG.md`](CHANGELOG.md) and on [GitHub Releases](https://github.com/SysAdminDoc/SwiftFloris/releases).

- **v1.8.241** (2026-06-04) — MIME helper aggregate semantics are now documented and covered, constructor stdout logging is removed, and legacy font wildcard matching is explicit. ([notes](CHANGELOG.md#v1.8.241))
- **v1.8.240** (2026-06-04) — Async preference-store init failures now stage a crash report, unblock the Settings splash wait, and redirect to recovery instead of hanging. ([notes](CHANGELOG.md#v1.8.240))
- **v1.8.239** (2026-06-04) — Editor start/selection content-generation jobs now cancel or supersede stale work before reset, finishInput, or field switches can republish old state. ([notes](CHANGELOG.md#v1.8.239))
- **v1.8.238** (2026-06-04) — Clipboard image/video history tiles now expose localized TalkBack labels with media type, history group, and copied-time context while keeping decorative thumbnail overlays hidden. ([notes](CHANGELOG.md#v1.8.238))
- **v1.8.237** (2026-06-04) — Settings search destination highlights are now one-shot and dismissible, so stale search-result cards do not reappear on later visits. ([notes](CHANGELOG.md#v1.8.237))
- **v1.8.236** (2026-06-04) — Suggestion candidate generation now snapshots incognito, no-personalized-learning, preference, and ghost-text sensitivity inputs before async provider work begins. ([notes](CHANGELOG.md#v1.8.236))
- **v1.8.235** (2026-06-04) — Settings search now exposes TalkBack field labels/state, polite result-status changes, and result rows with position, screen, title, and summary context. ([notes](CHANGELOG.md#v1.8.235))
- **v1.8.234** (2026-06-04) — Focused regression tests now pin Arabic combining-mark shaping, Snygg unknown selectors and `contentScale`, private-session trace suppression, and locale-scoped n-gram flush behavior. ([notes](CHANGELOG.md#v1.8.234))
- **v1.8.233** (2026-06-04) — Editor batch edits now wrap only synchronous `InputConnection` mutations while expected-content generation and queue pushes happen before the batch opens. ([notes](CHANGELOG.md#v1.8.233))
- **v1.8.232** (2026-06-04) — Settings -> Personal dictionary now explains blocked system-back gestures during active save, delete, import, or export work with operation-specific feedback. ([notes](CHANGELOG.md#v1.8.232))
- **v1.8.231** (2026-06-04) — Dynamic incognito toggles now immediately re-apply the IME window screen-capture guard for the active field. ([notes](CHANGELOG.md#v1.8.231))
- **v1.8.230** (2026-06-04) — Sync sealed-box envelopes now have fixed v1 schema constants, deterministic X25519/AES-GCM vector coverage, and documented compatibility policy before transport activation. ([notes](CHANGELOG.md#v1.8.230))
- **v1.8.229** (2026-06-04) — Non-co-signed addon APKs now require an explicit Settings trust action before enrollment; co-signed addons still load automatically. ([notes](CHANGELOG.md#v1.8.229))
- **v1.8.228** (2026-06-04) — Clipboard history search is now wired into the keyboard palette with a settings toggle, clear/no-results states, and query plus type-filter composition coverage. ([notes](CHANGELOG.md#v1.8.228))
- **v1.8.227** (2026-06-04) — Japanese locale capability gates now use the valid `ja` language subtag for no-capitalization and no-auto-space behavior, with focused JVM coverage. ([notes](CHANGELOG.md#v1.8.227))
- **v1.8.226** (2026-06-04) — Post-audit release ledger for pushed n-gram, thread-safety, crypto, trace-privacy, Arabic-shaping, Snygg selector/contentScale, and clipboard media fallback fixes. ([notes](CHANGELOG.md#v1.8.226))
- **v1.8.225** (2026-06-04) — Deep engineering audit hardening across IME core, clipboard, dictionary import, privacy backup rules, settings sliders, haptics, and CI release gates. ([notes](CHANGELOG.md#v1.8.225))
- **v1.8.224** (2026-06-04) — Settings search now resets populated result lists to the top when the query changes so stale scroll offsets do not hide the highest-ranked result. ([notes](CHANGELOG.md#v1.8.224))
- **v1.8.223** (2026-06-04) — Settings search now resolves high-traffic capability synonyms such as dark theme, haptic, trace, punctuation, and privacy to the intended settings destinations. ([notes](CHANGELOG.md#v1.8.223))
- **v1.8.222** (2026-06-04) — Settings search no-results states now include a one-tap Browse all settings action back to Settings Home. ([notes](CHANGELOG.md#v1.8.222))
- **v1.8.221** (2026-06-04) — Settings search now has a JVM/Robolectric drift guard for duplicate entry IDs, real string-resource resolution, and typed destination-route mapping. ([notes](CHANGELOG.md#v1.8.221))
- **v1.8.220** (2026-06-04) — Root onboarding docs now agree that `ROADMAP.md` is the open-work source, `COMPLETED.md` summarizes shipped state, `CHANGELOG.md` is the release-note stream, and archived planning snapshots are historical context. ([notes](CHANGELOG.md#v1.8.220))
- **v1.8.219** (2026-06-04) — Restore and crash diagnostic failures now use project logging, and restore toasts/cards use stable fallback copy when Android reports a null or blank throwable message. ([notes](CHANGELOG.md#v1.8.219))
- **v1.8.218** (2026-06-04) — Staged startup exceptions now persist to the local crash report store and open the crash dialog before Settings can hang behind the splash screen. ([notes](CHANGELOG.md#v1.8.218))
- **v1.8.170** (2026-05-18) — Keyboard preview field polish: settings preview fields now sit on a distinct bottom surface, expose ready/active feedback, preserve bottom-bar traversal, and use coroutine-safe feedback when Android cannot open the IME picker. ([notes](CHANGELOG.md#v1.8.170))
- **v1.8.169** (2026-05-18) — Empty-state UX polish: selected dictionary-language views, extension categories, language packs, filtered clipboard history, and the theme manager now explain blank states and route users toward add/import/filter-clear/recovery actions. ([notes](CHANGELOG.md#v1.8.169))
- **v1.8.168** (2026-05-18) — Addon scan progress: Addons Settings now shows a shared progress card while installed packages and dictionary-pack metadata are rescanned, and the touched preference state read uses the current `collectAsState` API. ([notes](CHANGELOG.md#v1.8.168))
- **v1.8.167** (2026-05-18) — Theme and extension destructive confirmations: draft file deletes plus theme rule/property deletes now require explicit confirmation and explain that installed extensions/themes remain unchanged until save. ([notes](CHANGELOG.md#v1.8.167))
- **v1.8.166** (2026-05-18) — Repo hygiene closure: CI now runs a repo-hygiene script, generated build/report output is guarded, legacy deleted markdown decisions are documented, and commit-scope/final-handoff rules are pinned. ([notes](CHANGELOG.md#v1.8.166))
- **v1.8.165** (2026-05-18) — CI quality gates: Android CI lint now fails stale baseline drift, Dependabot reviews Gradle and Actions updates weekly, a manual emulator settings-launch smoke exists, and local verification commands are documented. ([notes](CHANGELOG.md#v1.8.165))
- **v1.8.164** (2026-05-18) — Backup/restore baseline: benchmark-only representative archive generation measures preference plus keyboard/theme backup creation and merge restore timings under `docs/benchmark-results/`. ([notes](CHANGELOG.md#v1.8.164))
- **v1.8.163** (2026-05-18) — Theme-switch baseline: benchmark-only direct switch markers and an adb harness measure SwiftKey Pure / M3E theme swaps while the benchmark IME is visible, including cold and cached timings under `docs/benchmark-results/`. ([notes](CHANGELOG.md#v1.8.163))
- **v1.8.162** (2026-05-18) — Candidate row recomposition baseline: benchmark-only smartbar log markers and an adb harness measure warm typing recomposition counts/durations plus paired NLP suggestion timing under `docs/benchmark-results/`. ([notes](CHANGELOG.md#v1.8.162))
- **v1.8.161** (2026-05-18) — Dictionary load/preload baseline: a benchmark-only activity preloads the Latin dictionary, forces lazy SymSpell d1/d2 index construction with an invalid probe token, and records SM-S938B / Android 16 numbers under `docs/benchmark-results/`. ([notes](CHANGELOG.md#v1.8.161))
- **v1.8.160** (2026-05-18) — First suggestion latency baseline: a benchmark-only activity invokes the Latin suggestion provider against a real `EditorContent` snapshot and records cold provider-direct SM-S938B / Android 16 numbers under `docs/benchmark-results/`. ([notes](CHANGELOG.md#v1.8.160))
- **v1.8.159** (2026-05-18) — IME first-render benchmark baseline: `:benchmark` is active again, a benchmark-only input activity drives cold IME view creation, and SM-S938B / Android 16 first-render numbers are committed under `docs/benchmark-results/`. ([notes](CHANGELOG.md#v1.8.159))
- **v1.8.158** (2026-05-18) — Accessibility manual QA notes: contributor and accessibility docs now list TalkBack traversal, key-label, candidate-row, font-scale, non-color-state, and theme/layout checks. ([notes](CHANGELOG.md#v1.8.158))
- **v1.8.157** (2026-05-18) — Non-color state indicators: shared success/progress/neutral cards and extension-import row icons make readiness, progress, cancellation, and completion visible without relying on color alone. ([notes](CHANGELOG.md#v1.8.157))
- **v1.8.156** (2026-05-18) — Theme contrast audit: bundled keyboard/candidate/dialog styles and settings warning/error/dialog palettes now have selector-level AA coverage; low-contrast enter-key variants and card secondary text were tightened. ([notes](CHANGELOG.md#v1.8.156))
- **v1.8.155** (2026-05-18) — Dynamic font scaling: compact settings metadata, links, extension component headings, and theme-rule key previews now expand wrapping room or preview size at high font scale. ([notes](CHANGELOG.md#v1.8.155))
- **v1.8.154** (2026-05-18) — Keyboard key accessibility: semantic key targets now follow the real touch hitbox, expose an accessibility click action, and label common clipboard, voice, mode, layout, and smartbar-control keys explicitly. ([notes](CHANGELOG.md#v1.8.154))
- **v1.8.153** (2026-05-18) — Candidate and smartbar TalkBack labels: prediction-strip candidates now announce suggestion type, position, and text, while quick actions use a stable display-name/tooltip fallback policy. ([notes](CHANGELOG.md#v1.8.153))
- **v1.8.152** (2026-05-18) — Settings focus order: the shared settings scaffold now gives TalkBack and keyboard traversal a stable app bar -> content -> bottom actions -> floating action order. ([notes](CHANGELOG.md#v1.8.152))
- **v1.8.151** (2026-05-18) — Dictionary transfer busy states: user dictionary import/export now shows explicit progress cards, runs transfer work off the main thread, and blocks duplicate transfer/navigation/menu/entry actions while busy. ([notes](CHANGELOG.md#v1.8.151))
- **v1.8.150** (2026-05-18) — Trust-state recovery microcopy: backup, restore, extension, language-pack, archive-file, and manual dictionary failure cards now state what stayed unchanged and provide a retry/recovery path with the technical detail. ([notes](CHANGELOG.md#v1.8.150))
- **v1.8.149** (2026-05-18) — Dictionary entry trust states: manual add/update/delete now show progress/result cards, run DAO writes off the main thread, refresh affected suggestion overlays, and block duplicate entry actions while work is running. ([notes](CHANGELOG.md#v1.8.149))
- **v1.8.148** (2026-05-18) — Extension archive file trust states: archive file import/rename/delete now show progress/result cards, do file work off the main thread, and block duplicate actions while work is running. ([notes](CHANGELOG.md#v1.8.148))
- **v1.8.147** (2026-05-18) — Theme extension trust states: theme editing now shows save progress/failure cards, confirms component removal with draft-state feedback, and installed extension deletion now shows progress/failure cards while blocking duplicate actions. ([notes](CHANGELOG.md#v1.8.147))
- **v1.8.146** (2026-05-18) — Language pack trust states: extension import now shows file-reading/importing/cancel/failure states plus new/update/skipped counts, and language pack deletion now shows progress/success/failure cards while blocking duplicate actions. ([notes](CHANGELOG.md#v1.8.146))
- **v1.8.145** (2026-05-18) — Restore flow trust states: erase restores now require confirmation and show recovery-copy guidance, restore progress/cancellation/failure/partial-failure states stay visible, and section-level restore summaries prevent missing archive sections from silently erasing local data. ([notes](CHANGELOG.md#v1.8.145))
- **v1.8.144** (2026-05-18) — Backup flow trust states: backup progress, cancellation, share-sheet handoff, failure, and sensitive-clipboard exclusion now surface as explicit cards, with `BackupFlowNotice` policy coverage. ([notes](CHANGELOG.md#v1.8.144))
- **v1.8.143** (2026-05-18) — Autocorrect lifecycle contract: `docs/AUTOCORRECT_LIFECYCLE.md` now defines spacebar, punctuation, backspace, hardware, glide-delete, provider-notification, manual QA, and regression-test contracts; accepted provider notifications now wait for successful editor commits. ([notes](CHANGELOG.md#v1.8.143))
- **v1.8.142** (2026-05-18) — Theme rule edit policy extraction: `ThemeRuleEditPolicy` now owns add-rule selection validation, selector toggling, and key-code attribute parsing/replacement decisions for the theme editor. ([notes](CHANGELOG.md#v1.8.142))
- **v1.8.141** (2026-05-18) — Punctuation flush policy extraction: `KeyboardAutoCommitFlushPolicy` now owns software non-letter autocorrect flush decisions for media mode, alphabetic keys, punctuation, numeric keys, and numeric/phone layouts. ([notes](CHANGELOG.md#v1.8.141))
- **v1.8.140** (2026-05-18) — Candidate auto-commit policy extraction: `CandidateAutoCommitPolicy` now owns shortcut, phrase repair, active-strip, immediate fallback, quick-prediction, and rejected-correction gating decisions with focused JVM coverage. ([notes](CHANGELOG.md#v1.8.140))
- **v1.8.139** (2026-05-18) — Dependency warning review: Gradle is checksum-pinned to 9.5.1, Navigation Compose is on 2.9.8, and JUnit Vintage is centralized at 6.0.3 after official-release review, clearing the dependency-version lint warnings. ([notes](CHANGELOG.md#v1.8.139))
- **v1.8.138** (2026-05-18) — Conservative unused-resource cleanup: obsolete launcher/branding resources and dead legacy color tokens were removed after manifest/code/asset/test/dynamic lookup review, reducing lint from 289 warnings / 1 hint to 245 warnings / 1 hint. ([notes](CHANGELOG.md#v1.8.138))
- **v1.8.137** (2026-05-18) — Theme editor validation tests: theme component metadata now validates through `ThemeComponentMetaValidationPolicy`, with JVM coverage for valid apply normalization, invalid fields, duplicate IDs, and blank stylesheet fallback. ([notes](CHANGELOG.md#v1.8.137))
- **v1.8.136** (2026-05-18) — Subtype editor validation tests: editable subtype drafts now validate through `SubtypeEditorValidationPolicy`, with JVM coverage for default add-state missing fields, complete draft building, select-placeholder rejection, and edit-state preservation. ([notes](CHANGELOG.md#v1.8.136))
- **v1.8.135** (2026-05-18) — Language pack import/update tests: extension import readiness now lives in `ExtensionImportPolicy`, with JVM coverage for new installs, user-installed updates, bundled-core rejection, corrupted metadata, wrong extension type, unsupported files, and import button enablement. ([notes](CHANGELOG.md#v1.8.135))
- **v1.8.134** (2026-05-18) — Backup/restore policy tests: validation and operation-state decisions now live in `BackupRestorePolicy`, with JVM coverage for backup success/cancellation/failure, invalid archives, restore enablement, and partial-failure classification. ([notes](CHANGELOG.md#v1.8.134))
- **v1.8.133** (2026-05-18) — Incognito suggestion privacy policy tests: app-declared no-learning override, dynamic toggle availability, committed-word learning, and touch-decoder evidence gates now have focused JVM coverage. ([notes](CHANGELOG.md#v1.8.133))
- **v1.8.132** (2026-05-18) — Glide typing delete policy tests: immediate backspace word-delete escalation now lives in the editor input policy and is covered for enabled, disabled, inactive phantom-space, and explicit word-delete paths. ([notes](CHANGELOG.md#v1.8.132))
- **v1.8.131** (2026-05-18) — Spacing lifecycle state tests: auto-space and phantom-space state transitions now have focused JVM coverage for one-update grace, composing-region visibility, and candidate-for-revert cleanup. ([notes](CHANGELOG.md#v1.8.131))
- **v1.8.130** (2026-05-18) — Hardware keyboard input policy tests: hardware keydown/keyup routing now has focused JVM coverage for space, enter, delete pass-through, shift, mapped letters, mapped punctuation, and mapped punctuation flushing pending autocorrect before commit. ([notes](CHANGELOG.md#v1.8.130))
- **v1.8.129** (2026-05-18) — Editor input behavior policy extraction: autocorrect spacebar commits, rejected-correction protection, punctuation auto-spacing, phantom spacing, double-space period, and sentence-capitalization gates now have focused JVM coverage through a pure policy class. ([notes](CHANGELOG.md#v1.8.129))
- **v1.8.128** (2026-05-18) — Nastaliq Urdu font bundle: the official OFL-1.1 Noto Nastaliq Urdu TTF is now committed as an APK asset, Urdu subtype key labels and hints route Arabic-script text through it, and asset/license tests pin the bundle. ([notes](CHANGELOG.md#v1.8.128))
- **v1.8.127** (2026-05-18) — Emoji pinned-group sheet: long-pressing emoji can now pin them to named groups, and pinned-group chips commit the saved emoji sequence from the palette. ([notes](CHANGELOG.md#v1.8.127))
- **v1.8.126** (2026-05-18) — Addons dictionary catalog polish: Settings -> Addons now lists mounted dictionary packs with language, word count, dataset license, source, descriptor rejections, and updated install guidance. ([notes](CHANGELOG.md#v1.8.126))
- **v1.8.125** (2026-05-18) — Addons dictionary asset mounting: enrolled dictionary-pack APK assets now feed the Latin dictionary store through `PackageManager#getResourcesForApplication(...)`, merge with bundled baselines, and reload when the live addon registry generation changes. ([notes](CHANGELOG.md#v1.8.125))
- **v1.8.124** (2026-05-18) — Addons trust controls: Settings -> Addons can now reset all saved signing-certificate pins or trust a changed certificate after confirmation and rescan; the pin codec gained targeted package removal and the Addons Roborazzi baseline was refreshed. ([notes](CHANGELOG.md#v1.8.124))
- **v1.8.123** (2026-05-18) — Roborazzi baseline hard gate: committed screenshot baselines for the maintainer chip, SwiftKey High Contrast, Aurora Animated, and Settings -> Addons surfaces; CI now fails on visual-regression drift instead of using `continue-on-error`. ([notes](CHANGELOG.md#v1.8.123))
- **v1.8.104 – v1.8.122** (2026-05-17/18) — seventh-pass audit closure and follow-up slices: app-declared `IME_FLAG_NO_PERSONALIZED_LEARNING` and `EXTRA_IS_SENSITIVE` privacy flags are honoured, voice handoff refuses sensitive fields, checks every external voice IME's microphone grant, exposes a durable Listening state, and now gates the in-app Whisper/Vosk route selector and model catalog behind a preview-only local-runtime flag; dangerous voice remove commands were tightened, the voice setup activity is non-exported with a validated setup-intent contract, clipboard backup/clear-all leaks were closed, provider-backed clipboard media clones now cap image/video bytes, image preview decode rejects oversized dimensions before allocation, automatic clipboard history eviction now closes provider-backed media before deleting rows, sensitive clipboard text no longer feeds pin-popup description URL/email/phone classification, startup reconciliation removes missing-file history rows plus unreferenced provider files / metadata rows, media restore recreates provider metadata for restored image/video clips, failed foreign media URI clones no longer create phantom history entries, clipboard history maintenance no longer sorts or evicts on Main, the dead parallel Tink clipboard-history store has been removed so the Room-backed manager is the only live storage path, and the KenLM mmap reader now rejects header/pre-body offsets instead of aliasing them to trie-body bytes. ([latest notes](CHANGELOG.md#v1.8.122))
- **v1.8.85 – v1.8.103** (2026-05-17) — cross-subsystem hardening pass + 18 single-feature follow-up releases. v1.8.85 was an explicit AGENTS.md §6 one-time deviation that closed eleven privacy / security / reliability gaps (merged-manifest `verifyNoInternetPermission`, Android 12+ `data_extraction_rules.xml`, atomic `ZipUtils.unzip`, thread-safe `HardwareKeyboardRuntimeMapper`, sticker decoder OOM, sticker MIME spoof, addon enumerator size category-error, `verify-reproducible-apk.sh` payload-manifest pass criterion, CI workflow permissions, `pull_request_target` injection, AltGr); v1.8.86 – v1.8.102 then returned to per-PR scope and closed eleven of twelve F-roster items (FLAG_SECURE on numeric PIN + passphrase dialog, legacy-passphrase recovery, ZipUtils abort policy, SAF lost-grant UX, addon spec docs alignment, LDML `shift=` semantics, fastlane script hardening, SHA-pinned floating action tags, `release.yml` keystore hygiene, `verifyDataExtractionRules` build gate, sticker LRU + folder cap, `HardwareKeyEntry.longPressAlternates`); v1.8.103 closes the documentation half (README + PROJECT_CONTEXT version refresh, master index of the session's commits). The remaining F11 Roborazzi baseline item closed in v1.8.123. ([master index](CHANGELOG.md#v1.8.103))
- **v1.8.84** (2026-05-17) — Settings → Addons status surface: users can inspect accepted/rejected addon APKs, manually rescan through the startup reconciliation path, and review package/license/version/size/signing-fingerprint details. ([notes](CHANGELOG.md#v1.8.84))
- **v1.8.83** (2026-05-17) — Addon registry startup wiring: the IME now scans installed addon manifests at startup, reconciles them through persisted signing pins, publishes a process-wide registry, and cleans malformed stored pin lines. ([notes](CHANGELOG.md#v1.8.83))
- **v1.8.82** (2026-05-17) — Addon signing-pin persistence: `AddonSigningPinSet` safely parses/encodes addon package fingerprint pins and `prefs.addon.signingCertPins` gives the registry a durable trust store consumed by v1.8.83 startup wiring and v1.8.84 Settings status UI. ([notes](CHANGELOG.md#v1.8.82))
- **v1.8.81** (2026-05-17) — Addon catalog foundation: `AddonRegistry` now reconciles live addon state with signing-certificate pins, and `DictionaryPackCatalog` validates dictionary-pack descriptors plus provenance before Settings/Addons UI and asset mounting land. ([notes](CHANGELOG.md#v1.8.81))
- **v1.8.80** (2026-05-17) — SQLCipher provider migration plan: documented the current LibTomCrypt-based Android Community AAR state, OpenSSL proof-of-concept path, migration triggers, 16 KB page-size gates, and rollback rules without changing the runtime dependency. ([notes](CHANGELOG.md#v1.8.80))
- **v1.8.79** (2026-05-17) — Honeycomb hex layout wire-up: the bundled honeycomb character layout is registered for subtype selection, routed through `TextKeyboardLayoutStyle.Honeycomb`, clipped to `HoneycombHexShape`, and hit-tested against the actual hex instead of rectangular bounding boxes. ([notes](CHANGELOG.md#v1.8.79))
- **v1.8.78** (2026-05-17) — Keyman `.kmp` package import foundation: safe ZIP/package parser for `kmp.json`, keyboard/language/example metadata, LDML-in-package extraction, lexical-model classification, compiled-engine-required classification, and unsafe entry skipping. ([notes](CHANGELOG.md#v1.8.78))
- **v1.8.77** (2026-05-17) — User-imported sticker folder: Settings → Emoji & stickers can persist a local SAF folder URI, enumerate supported image files into an Imported sticker pack, preview them in the sticker grid, and commit them through the existing rich-content provider path. ([notes](CHANGELOG.md#v1.8.77))
- **v1.8.76** (2026-05-17) — Hardware-keyboard runtime mapping: imported layouts can bind to Android hardware `deviceId` values, resolve `KeyEvent` scan/key codes through KLC/macOS fallbacks, and commit mapped printable characters through `KeyboardManager`. ([notes](CHANGELOG.md#v1.8.76))
- **v1.8.75** (2026-05-17) — Hardware-keyboard import: added an XXE-hardened macOS `.keylayout` XML parser that normalizes key maps, modifier maps, and action-backed dead keys into `HardwareKeyboardLayout`. ([notes](CHANGELOG.md#v1.8.75))
- **v1.8.74** (2026-05-17) — Bump-batch C: Android Gradle Plugin `9.0.0` → `9.2.1` and Compose BOM `2026.03.01` → `2026.05.00`; R8 keepattributes audit required no rule changes. ([notes](CHANGELOG.md#v1.8.74))
- **v1.8.73** (2026-05-17) — Repo hygiene: local root JVM crash/replay logs moved to `.ai/local-crash-logs/2026-05-16/`, and CI now rejects committed root `hs_err_pid*.log` / `replay_pid*.log` files. ([notes](CHANGELOG.md#v1.8.73))
- **v1.8.72** (2026-05-17) — Roadmap correction: HeliBoard / NLnet open-glide integration is now treated as an additive future track, while SwiftFloris's shipped `StatisticalGlideTypingClassifier` remains the production glide path until a permissive open library and dataset are actually available. ([notes](CHANGELOG.md#v1.8.72))
- **v1.8.71** (2026-05-17) — Bump-batch B: Roborazzi `1.55.0` → `1.60.0` and Robolectric `4.14.1` → `4.16.1`; no app code, permissions, or runtime behavior changed. ([notes](CHANGELOG.md#v1.8.71))
- **v1.8.70** (2026-05-17) — README migration-window follow-up: Samsung / Grammarly keyboard-workflow callouts, Galaxy AI Writing Assist compatibility note for One UI 7+, Grammarly Keyboard replacement note, and release-front-door refresh. ([notes](CHANGELOG.md#v1.8.70))
- **v1.8.69** (2026-05-17) — Bump-batch A: coroutines `1.11.0`, KSP `2.3.8`, ZXing `3.5.4`, and AboutLibraries `14.2.0`; beta AboutLibraries `15.0.0-b01` intentionally skipped. ([notes](CHANGELOG.md#v1.8.69))
- **v1.8.68** (2026-05-17) — N7.6 Tink / AndroidKeystore migration: removed AndroidX Security Crypto, added shared Tink encrypted-preference wrapper, migrated SQLCipher passphrase and legacy clipboard-history payloads one time when old keysets remain readable. ([notes](CHANGELOG.md#v1.8.68))
- **v1.8.67** (2026-05-17) — N12.5 reproducible-build self-verification CI: new build-twice release APK workflow plus `scripts/verify-reproducible-apk.sh` clean-worktree byte comparison and drift manifests. ([notes](CHANGELOG.md#v1.8.67))
- **v1.8.66** (2026-05-17) — N8.7 Article 50 transparency surface: first-run **Review local AI features** setup step, reopenable Settings → About → **AI features in this keyboard** screen, docs links, and catalog test coverage for next-word / glide / voice / translation / smart-compose disclosures. ([notes](CHANGELOG.md#v1.8.66))
- **v1.8.65** (2026-05-17) — Phase A3 Settings wiring: **Export encrypted** passphrase dialog + `.sfexp` create-document flow, direct encrypt-then-write personal-dictionary export, `SFEXP1` import sniffing, passphrase decrypt, and `DictionaryImporter`/rollback-summary routing for decrypted SwiftFloris combined-list files. ([notes](CHANGELOG.md#v1.8.65))
- **v1.8.64** (2026-05-17) — Phase D1: calendar quick-insert (`QuickAction.InsertCalendarEvent`) reads local `CalendarContract.Instances` entries for today + next 7 days, opens an IME-local agenda picker, and inserts the selected event title + date/time. `READ_CALENDAR` is requested only after explicit tap. ([notes](CHANGELOG.md#v1.8.64))
- **v1.8.63** (2026-05-17) — Phase C3: bundled SwiftKey High Contrast (AAA) and Aurora Animated themes, with Snygg stylesheet tests and a reduced-motion-aware GenericShape aurora background. ([notes](CHANGELOG.md#v1.8.63))
- **v1.8.62** (2026-05-17) — Phase C1: split-keyboard renderer wire-up with gutter-aware layout, viability gating, and touch-hit suppression inside the gutter. ([notes](CHANGELOG.md#v1.8.62))
- **v1.8.61** (2026-05-17) — Phase B2: quick-prediction-insert threshold tuning with a configurable weighted-confidence floor and aligned plain-space suppression. ([notes](CHANGELOG.md#v1.8.61))
- **v1.8.60** (2026-05-17) — Phase B1: multilingual cold-start sentence/phrase priors plus top-1,000 Zipf seed overlays for CS/DE/ES/FR/IT/PT. ([notes](CHANGELOG.md#v1.8.60))
- **v1.8.59** (2026-05-17) — Phase D3: Typing Stats now shows current-week accepted corrections versus last week, backed by bounded weekly metadata in `CorrectionOutcomePriors`. ([notes](CHANGELOG.md#v1.8.59))
- **v1.8.58** (2026-05-17) — Phase D2: generic task-creation quick action (`QuickAction.InsertTask`). On-device replacement for SwiftKey's Microsoft-To-Do tile via `Intent.ACTION_SEND` chooser; works with Tasks.org / OpenTasks / Google Tasks / Joplin / Notion / Markor. `SensitiveFieldGuard` gate. ([notes](CHANGELOG.md#v1.8.58))
- **v1.8.57** (2026-05-17) — Phase C2: SwiftKey "Modes → Arrow keys" parity via new `BottomRowPreset.Navigation` (← ↑ space ↓ → enter). ([notes](CHANGELOG.md#v1.8.57))
- **v1.8.56** (2026-05-17) — Phase B4: same-sentence language-switch hardening via geometric-decay weighted blend in `TrailingContextLanguageBlend`. ([notes](CHANGELOG.md#v1.8.56))
- **v1.8.55** (2026-05-17) — Phase B3: shared-spelling bilingual handling — sub-floor `0.30` confidence on one-locale candidates overwriting shared typed words. ([notes](CHANGELOG.md#v1.8.55))
- **v1.8.54** (2026-05-17) — Phase A3 codec primitive: encrypted-blob personal-dictionary export envelope (AES-256-GCM + PBKDF2-HMAC-SHA-256 at OWASP-2025's 600 000 iterations). ([notes](CHANGELOG.md#v1.8.54))
- **v1.8.53** (2026-05-17) — Phase A2: post-import confirmation + rollback dialog + wired `DictionaryImporter` into Settings UI. ([notes](CHANGELOG.md#v1.8.53))
- **v1.8.52** (2026-05-17) — SwiftKey migration outreach push: README banner + opening pitch lead with the 2026-05-31 cutoff, badge promoted, parity-roadmap permalink linked. ([notes](CHANGELOG.md#v1.8.52))
- **v1.8.51** (2026-05-17) — N14.3 + N14.4 Compose BOM + Gradle wrapper dependency-pin audits. New audit-log table in `docs/DEPENDENCY_TRIAGE.md`. ([notes](CHANGELOG.md#v1.8.51))
- **v1.8.50** (2026-05-17) — N17.1 emoji-picker crash triage; root-caused to `Paint.hasGlyph("")` and closed with three defensive filters. ([notes](CHANGELOG.md#v1.8.50))
- **v1.8.49** (2026-05-17) — N15.3 Smart Edit voice REMOVE_ITEM_FROM_LIST: new parameterised voice-command type that excises a named item from the dictated buffer mid-stream. ([notes](CHANGELOG.md#v1.8.49))
- **v1.8.48** (2026-05-17) — Adversarial-input + lifecycle hardening pass across the SwiftKey JSON importer, MCP daemon bridge, IME service teardown, voice-model install, ZIP extraction, and DB cursor handling. ([notes](CHANGELOG.md#v1.8.48))
- **v1.8.47** (2026-05-16) — N1.4 FUTO swipe-trace replay and benchmark harness. ([notes](CHANGELOG.md#v1.8.47))
- **v1.8.46** (2026-05-16) — SwiftKey `swiftkey-cloud.json` import parser ahead of the 2026-05-31 account retirement. New `DictionaryImportFormat.JSON` + tolerant `parseSwiftKeyJson`. ([notes](CHANGELOG.md#v1.8.46))
- **v1.8.45** (2026-05-16) — Android 17 IME-visibility restore across configuration changes. ([notes](CHANGELOG.md#v1.8.45))
- **v1.8.44** (2026-05-16) — Long-press popup guard on password fields (`KeyVariation.PASSWORD`). ([notes](CHANGELOG.md#v1.8.44))
- **v1.8.43** (2026-05-16) — Roborazzi plugin unblocked at 1.55.0; visual-regression CI step added. ([notes](CHANGELOG.md#v1.8.43))
- **v1.8.42** (2026-05-16) — Kotlin 2.3.20 → 2.3.21 bug-fix bump. ([notes](CHANGELOG.md#v1.8.42))
- **v1.8.41** (2026-05-16) — Auto-return to letter keyboard after apostrophe in symbols panel. ([notes](CHANGELOG.md#v1.8.41))
- **v1.8.40** (2026-05-16) — Per-daemon enable / disable for the MCP bridge in Settings. ([notes](CHANGELOG.md#v1.8.40))
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

Root-caused in **v1.8.50** (ROADMAP §6 N17.1, [release notes](CHANGELOG.md#v1.8.50), [GitHub issue #1](https://github.com/SysAdminDoc/SwiftFloris/issues/1)). The trigger was `Paint.hasGlyph("")` aborting the palette render whenever an empty-value `Emoji` reached the initial filter pass. Three defensive filters landed at the palette, history-mapping, and asset-loader layers. If you still see this on v1.8.50+ please attach the device model, Android build, ROM, and a logcat capture to the issue.

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

🚀 **Active development.** Current release: **v1.8.241** (2026-06-04). The SwiftKey account export window closed on **2026-05-31**; local/on-device migration paths remain documented above.

---

## Quick Links

| Resource | Link |
|----------|------|
| **GitHub** | https://github.com/SysAdminDoc/SwiftFloris |
| **Issues** | https://github.com/SysAdminDoc/SwiftFloris/issues |
| **Releases** | https://github.com/SysAdminDoc/SwiftFloris/releases |
| **Roadmap** | [ROADMAP.md](ROADMAP.md) |
| **Completed work** | [COMPLETED.md](COMPLETED.md) |
| **Research report** | [RESEARCH_REPORT.md](RESEARCH_REPORT.md) |
| **SwiftKey migration** | [docs/MIGRATE_FROM_SWIFTKEY.md](docs/MIGRATE_FROM_SWIFTKEY.md) |
| **Privacy and AI** | [docs/PRIVACY_AND_AI.md](docs/PRIVACY_AND_AI.md) |
| **Threat model** | [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) |
| **FUTO Voice** | https://voiceinput.futo.org/ |
| **FlorisBoard upstream** | https://github.com/florisboard/florisboard |

**Made for privacy and offline-first computing.**
