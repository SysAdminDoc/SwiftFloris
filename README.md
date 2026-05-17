# SwiftFloris

![Version](https://img.shields.io/badge/version-v1.8.68-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green) ![Platform](https://img.shields.io/badge/platform-Android%208.0+-orange) ![Network](https://img.shields.io/badge/network-none-lightgrey) ![SwiftKey migration](https://img.shields.io/badge/SwiftKey%20migration-window%20closes%202026--05--31-red)

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

## Highlights

| Area | What's in v1.8.68 | Privacy posture |
|------|-------------------|-----------------|
| **Autocorrect / prediction** | SCOWL 117k English dictionary, SymSpell d1+d2, bigram + trigram next-word, capitalization-aware completions, contraction handling, instant-remember user-dictionary overlay | On-device |
| **Multilingual typing** | Bilingual subtype presets (EN+ES / EN+FR / EN+DE), per-token Latin language identification, top-two straddle guard, sentence-local context scoring | On-device |
| **Scripts** | Devanagari + Bengali + Tamil + Telugu + ... (63-script transliteration coverage); RTL Arabic shaper, Persian / Urdu / Hebrew normalisers | On-device |
| **Gesture typing** | `StatisticalGlideTypingClassifier` over bounded EN / DE / ES / FR / IT / PT dictionaries with adaptive touch evidence | On-device |
| **Voice input** | FUTO Voice Input handoff (preferred), Vosk streaming fallback, RAM-aware model selector, local Whisper/Vosk model manager | No audio leaves the device |
| **Clipboard** | History with pinning + per-app source tag; Tink / AndroidKeystore-wrapped legacy history; SQLCipher-encrypted personal dictionary | On-device |
| **Productivity** | Calendar quick-insert reads local agenda entries for today + next 7 days; task quick-insert sends selected text to user-chosen task / note apps | Calendar permission is explicit opt-in; no network |
| **Themes** | 21 bundled themes — SwiftKey Pure (Light/Dark + M3 Expressive), SwiftKey High Contrast (AAA), Aurora Animated, Floris Day/Night, Swift Glacier, Swift Slate, M3E Nord (light + dark), Tokyo Night, Dracula, Catppuccin Mocha; borderless variants where applicable; Snygg theme engine; per-app accent | No telemetry |
| **MCP daemon bridge** | AIDL bridge to user-installed MCP daemons with per-daemon enable / disable in Settings → MCP daemon bridge | Local-only binder, no network |
| **Migration** | Gboard / FlorisBoard / SwiftKey JSON export importer; passphrase-encrypted SwiftFloris dictionary export/import; Keyman LDML + Windows KLC hardware-keyboard imports | All file-system based |
| **AI transparency** | First-run AI/ML explainer plus Settings → About → AI features screen covering next-word, glide, voice, translation, and smart compose | On-device, no account, no telemetry |
| **CI / build** | No-network gate, OSV dep scan, reproducible-build toolchain pins + build-twice APK self-check, Roborazzi visual-regression scaffold, Macrobenchmark trace sections in 6 hot paths | Audit-friendly |

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
3. (Optional) Install [FUTO Voice Input](https://voiceinput.futo.org/) for the preferred offline dictation path. SwiftFloris also ships a Vosk fallback that works without FUTO.

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

- [`docs/MIGRATE_FROM_SWIFTKEY.md`](docs/MIGRATE_FROM_SWIFTKEY.md) — SwiftKey account-retirement migration paths.
- [`docs/PRIVACY_AND_AI.md`](docs/PRIVACY_AND_AI.md) — AI/ML feature transparency and local-processing disclosure.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — privacy / security threat model and mitigations.
- [`docs/REPRODUCIBLE_BUILDS.md`](docs/REPRODUCIBLE_BUILDS.md) — pinned toolchain and F-Droid rebuild plan.
- [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md) — Macrobenchmark trace sections and regression threshold contract.
- [`docs/INLINE_AUTOFILL.md`](docs/INLINE_AUTOFILL.md) — inline-autofill matrix and password-manager verification.
- [`docs/TASKER_INTEGRATION.md`](docs/TASKER_INTEGRATION.md) — Tasker intent contract.
- [`docs/FONTS.md`](docs/FONTS.md) — bundled fonts (Nastaliq + Naskh fallback).
- [`GESTURE_TYPING_MULTILINGUAL.md`](GESTURE_TYPING_MULTILINGUAL.md) — multilingual gesture-typing guide.
- [`FUTO_VOICE_INPUT_TROUBLESHOOTING.md`](FUTO_VOICE_INPUT_TROUBLESHOOTING.md) — FUTO Voice Input setup + recovery actions.
- [`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md) — execution-focused quality / UX / a11y / perf / test / delivery plan.
- [`ROADMAP.md`](ROADMAP.md) — current and historical roadmap (v5.3).
- `RELEASE_NOTES_v*.md` — per-release notes, one file per version, in the repository root.

## Architecture & Stack

**Language and build**

- Kotlin 2.3.21, Compose BOM 2026.03.01, Material 3 + material-kolor.
- AGP 9.0.0, Gradle 9.4.1, JDK 17.
- KSP, Room 2.8.4, SQLCipher 4.16.0, Tink Android 1.21.0.
- Kotest 6.1.11 unit-test runner; Roborazzi 1.55.0 for screenshot regressions.
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
# Gradle 9.4.1 (use the bundled wrapper)
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
| `RECORD_AUDIO` | Voice input (FUTO handoff or Vosk fallback) | Optional |
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
- **Visual regression:** Roborazzi 1.55.0, plugin alias active. CI runs `:app:verifyRoborazziDebug` on every push / PR with `continue-on-error: true` during the bootstrap window; baseline PNG capture is in progress.
- **Macrobenchmark:** trace sections wired in production hot paths; device-number capture is tracked separately.
- **No-network gate:** CI verifies the absence of `INTERNET` permission on every build.

## Recent releases

The full release stream lives in the `RELEASE_NOTES_v*.md` files in the repository root, and on [GitHub Releases](https://github.com/SysAdminDoc/SwiftFloris/releases).

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
- **v1.8.31–v1.8.33** — Honeycomb hex renderer (`HoneycombHexShape` + `HoneycombHexButton` + `HoneycombKeyboardRow` + `HoneycombLayoutLoader`).
- **v1.8.0–v1.8.30** — Smart-compose / inline-translation router stack, KenLM reader pipeline, 63-script transliteration build-out, addon scaffold sweep, SwiftKey-parity slices.
- **v1.7.x** — Multilingual hot-switch, bigram + trigram next-word, adaptive touch, SymSpell d1+d2, Flow Through Space, encrypted personal dictionary.
- **v1.6.0** — Personal-learning dictionary + 117k SCOWL English + SwiftKey design tokens.
- **v1.5.0** — FUTO Voice Input integration (replacing Google Speech Recognizer).

See [`ROADMAP.md`](ROADMAP.md) §3 for the full reconciled version table back to v1.1.0.

## Contributing

SwiftFloris welcomes contributions in:

- 🎨 Themes and design tokens (Snygg engine).
- 🌍 Dictionary packs and transliteration tables for additional scripts.
- 🔧 Performance work (Macrobenchmark device-number capture, glide-typing engine).
- 🐛 Bug fixes; especially anything in [GitHub Issues](https://github.com/SysAdminDoc/SwiftFloris/issues).
- 📚 Docs and migration guides.
- ♿ Accessibility improvements.

**How to contribute**

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/my-feature`).
3. Make your changes; keep them within the philosophy in [`ROADMAP.md`](ROADMAP.md) §1 (no network, no telemetry, Apache-2.0 main app, no closed blobs).
4. Run `./gradlew test`. Add tests for new behavior.
5. Submit a pull request with a clear description.

## Troubleshooting

### Gesture typing not working?

See [Multilingual Gesture Typing](GESTURE_TYPING_MULTILINGUAL.md). Gesture typing currently uses the bounded statistical engine for EN / DE / ES / FR / IT / PT; the neural / open-glide path is gated on the HeliBoard NLnet release.

### Voice input unavailable?

See [FUTO Voice Input Troubleshooting](FUTO_VOICE_INPUT_TROUBLESHOOTING.md). SwiftFloris does not record audio itself; it hands off to the user-installed FUTO Voice Input app or to the bundled Vosk streaming engine. Make sure microphone permission is granted on whichever IME you've configured for dictation.

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

🚀 **Active development.** Current release: **v1.8.68** (2026-05-17). Migration window for SwiftKey users closes **2026-05-31** — 14 days from this release.

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
