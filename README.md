# SwiftFloris

![Version](https://img.shields.io/badge/version-v1.9.53-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green) ![Platform](https://img.shields.io/badge/platform-Android%208.0+-orange) ![Network](https://img.shields.io/badge/network-none-lightgrey) ![Dictionary imports](https://img.shields.io/badge/dictionary%20imports-local%20files-green)

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
> 2. **Any time later** — go to **Settings → Personal dictionary → Import** and pick the same local export files. SwiftFloris ingests SwiftKey JSON directly through the built-in import flow.
> 3. **If you missed the SwiftKey cutoff** — your learned words are gone from the cloud, but everything still in the on-device SwiftKey personal dictionary can still be re-typed; SwiftFloris's instant-remember overlay climbs the words back to the top of the prediction strip after a single use.
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

| Area | What's in v1.9.53 | Privacy posture |
|------|-------------------|-----------------|
| **Autocorrect / prediction** | SCOWL 117k English dictionary, heap-bounded SymSpell d1+d2, bigram + trigram next-word, capitalization-aware completions, contraction handling, instant-remember user-dictionary overlay | On-device |
| **Multilingual typing** | Bilingual subtype presets (EN+ES / EN+FR / EN+DE), per-token Latin language identification, top-two straddle guard, sentence-local context scoring, opt-in remembered keyboard language per app, and stale-id-safe manual subtype switching | On-device |
| **Scripts** | Devanagari + Bengali + Tamil + Telugu + ... (63-script transliteration coverage); RTL Arabic shaper, Persian / Urdu / Hebrew normalisers, bundled Noto Nastaliq Urdu rendering for Urdu subtype key text | On-device |
| **Gesture typing** | `StatisticalGlideTypingClassifier` over bounded EN / DE / ES / FR / IT / PT dictionaries with adaptive touch evidence | On-device |
| **Voice input** | FUTO Voice Input handoff (live path), FUTO install guidance when no voice keyboard is available, plus preview-only local Whisper/Vosk route selector and model catalog until a recognizer runtime ships | SwiftFloris itself does not record audio |
| **Emoji & stickers** | Emoji search/history/pinned groups with an in-keyboard pin-to-group sheet, bundled local sticker packs, share-to-sticker image copy-in, portable local sticker-pack import/export, and user-imported SAF sticker folders for PNG / WebP / JPEG / GIF files | App-private local files and local folder URI only |
| **Clipboard** | Room-backed history with pinning + per-app source tag, media/provider metadata, sensitive-item gates, startup/restore reconciliation, in-keyboard text search with type-filter composition, and TalkBack labels for image/video media history tiles | On-device |
| **Productivity** | Calendar quick-insert reads local agenda entries for today + next 7 days; task quick-insert sends selected text to user-chosen task / note apps | Calendar permission is explicit opt-in; no network |
| **Themes** | 21 bundled themes — SwiftKey Pure (Light/Dark + M3 Expressive), SwiftKey High Contrast (AAA), Aurora Animated, Floris Day/Night, Swift Glacier, Swift Slate, M3E Nord (light + dark), Tokyo Night, Dracula, Catppuccin Mocha; borderless variants where applicable; Snygg theme engine; per-app accent with Settings preview and one-time opt-in hint | No telemetry |
| **MCP daemon bridge** | AIDL bridge to user-installed MCP daemons with per-daemon enable / disable in Settings → MCP daemon bridge | Local-only binder, no network |
| **Addon packs** | Addon manifest/enumerator contracts, IME-startup registry reconciliation, Settings -> Addons status/rescan, explicit trust for non-co-signed addons, trust reset/changed-certificate controls, dictionary-pack catalog details, persisted signing-certificate pins, descriptor validation, provenance reports, typed dictionary-pack catalog, and addon APK dictionary asset mounting | No-network addon rejection |
| **Settings UX** | Five-bucket Settings home (Typing experience, Personalization, Privacy & data, Advanced, About), global Settings search with accent-insensitive matching, first-open focus, clear action, Search IME action, no-results path back to all settings, synonym hits for dark theme, haptic, trace, punctuation, and privacy queries, result-list scroll reset, TalkBack labels/live result-status/result-row context, and one-shot dismissible destination highlights; per-app keyboard profile editor for package-specific accent, incognito, clipboard, suggestion, and Smartbar gesture overrides; clearer empty states for voice setup, selected user-dictionary languages, extension categories, language packs, filtered clipboard history, and theme-manager recovery; user-dictionary back feedback during active save/delete/import/export work; surfaced keyboard preview field with ready/active state feedback | Local UI only |
| **Migration** | First-run local dictionary import hint; preview-before-save personal dictionary imports with row exclusion; Gboard / FlorisBoard / SwiftKey JSON export importer; passphrase-encrypted SwiftFloris dictionary export/import; Settings-based Keyman LDML / `.kmp` metadata + Windows KLC + macOS hardware-keyboard imports | All file-system based |
| **Sync scaffold** | Transport-neutral personal-dictionary sync model with QR pairing payloads, X25519/AES-GCM sealed-box v1 envelopes, CRDT merge tests, persisted sync identity, and Settings export/import actions for manual JSON files or picked local SAF folders | No network; user-chosen local file channel |
| **Editor reliability** | Expected-content generation for selection, text commit, composing finalize, and composing-region replacement paths now happens before `InputConnection` batch edits, with try/finally begin/end pairing and focused call-order tests | Local editor state only |
| **Alternative layouts** | Colemak / Dvorak / Workman from the FlorisBoard layout pack, plus selectable honeycomb hex layout with clipped hex keys and hex-aware hit testing (only FOSS Android keyboard shipping this — Typewise vacated the consumer market early 2026) | On-device |
| **AI transparency** | First-run AI/ML explainer plus Settings → About → AI features screen covering next-word, glide, voice, translation, and smart compose; async suggestion work consumes request-scoped privacy snapshots for incognito, no-personalized-learning, offensive-content, and ghost-text sensitivity gates | On-device, no account, no telemetry |
| **Local release evidence** | `scripts/release-evidence.ps1` runs the release-front-door, Fastlane metadata, backup/privacy copy, public-doc/F-Droid version-pin, repo-hygiene, root-crash-log, no-network, data-extraction, cache-disabled Gradle unit-test / lint / release-assemble gates, sample addon APK validation, OSV severity, and reproducible-APK gates into `build/release-evidence/<timestamp>/`; startup crash recovery routes through the local crash dialog; restore/crash diagnostics use project logging with safe fallback copy; tests cover settings-search resource/route drift, MIME helper aggregate contracts, NativeStr ByteBuffer slices, localization/copy contracts, Arabic shaping, Snygg imports, private trace suppression, and locale-scoped n-gram flushes; Roborazzi visual-regression checks use committed theme/Addons baselines; Macrobenchmark trace sections cover 6 hot paths; dependency freshness is pinned through Compose BOM 2026.06.00 / KSP 2.3.9 / AboutLibraries 15.0.3 / Roborazzi 1.64.0 | Audit-friendly |

## Distribution

SwiftFloris ships through GitHub Releases (canonical), Obtainium for GitHub-backed auto-updates, and a prepared F-Droid metadata track. It is **not** on Google Play by design — Play forces target-SDK churn and Integrity-API tradeoffs that conflict with the no-telemetry posture.

### Install trust

- **Canonical source:** https://github.com/SysAdminDoc/SwiftFloris
- **Package ID:** `io.github.sysadmindoc.swiftfloris`
- **GitHub / Obtainium channel:** APKs are built locally by the maintainer from the tagged source, signed with the SwiftFloris release key, and published with a `SHA256SUMS` manifest.
- **F-Droid channel:** prepared for fdroiddata submission with local reproducible-build and no-network gates. If accepted, the F-Droid build/signature is a separate Android install channel; stay on one channel unless you back up, uninstall, reinstall, and restore.
- **No-network proof:** the local release evidence command fails if the merged app manifest declares `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, or `CHANGE_WIFI_STATE`.
- **Reproducibility proof:** `scripts/release-evidence.ps1` runs the build-twice APK verifier and records APK hash evidence before publication.

### Option A — Obtainium (recommended for auto-updates)

[Obtainium](https://github.com/ImranR98/Obtainium) tracks GitHub Releases directly and notifies you the moment a new SwiftFloris APK ships — no Play Store, no F-Droid mirror lag, no manual polling.

**One-tap subscribe:**

```
obtainium://app/{"id":"io.github.sysadmindoc.swiftfloris","url":"https://github.com/SysAdminDoc/SwiftFloris","author":"SysAdminDoc","name":"SwiftFloris","preferredApkIndex":0,"additionalSettings":"{\"includePrereleases\":false,\"fallbackToOlderReleases\":true,\"trackOnly\":false,\"versionDetection\":true,\"apkFilterRegEx\":\"app-release.*\\\\.apk\"}"}
```

Open the link above on a device with Obtainium installed (or paste it into Obtainium's "Add app from URL" field). Obtainium will subscribe to this repository's GitHub Releases feed and auto-prompt for installs on each new tag.

### Option B — GitHub Releases (manual)

1. Download the latest APK from [Releases](https://github.com/SysAdminDoc/SwiftFloris/releases).
2. Download `SHA256SUMS` from the same release and verify the APK hash before installing when you want an audit trail.
3. Install on your Android device (Android 8.0+).
4. (Optional) Install [FUTO Voice Input](https://voiceinput.futo.org/) for offline dictation. SwiftFloris's in-app Whisper/Vosk catalog is preview-only until the local recognizer runtime ships.

### Option C — F-Droid

SwiftFloris is readying the fdroiddata submission under `io.github.sysadmindoc.swiftfloris`. Until the package appears in the official F-Droid client, use GitHub Releases or Obtainium. After F-Droid acceptance, do not install a GitHub-signed APK over an F-Droid-signed APK, or the reverse; Android intentionally rejects cross-signed upgrades.

### Option D — Manual Build

```bash
git clone https://github.com/SysAdminDoc/SwiftFloris.git
cd SwiftFloris
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Google developer verification (Sept 2026)

Google's [developer verification policy](https://keepandroidopen.org/) begins
enforcement in **September 2026** for Brazil, Indonesia, Thailand, and
Singapore, with global rollout expected in 2027. It requires registered
developers to provide a government ID, a one-time fee, and registration of
signing keys before apps can be installed on certified Android devices —
including, in the rolled-out form, sideloaded APKs. F-Droid has published an
[open letter opposing it](https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html).

**Current posture (reassess Q3 2026):** SwiftFloris does **not** plan to
pre-register under this scheme. The F-Droid track remains the primary
privacy-first distribution channel and F-Droid's own response will shape what,
if anything, packagers must do.

**If you are in a pilot region** (Brazil, Indonesia, Thailand, Singapore): after
September 2026, certified Android devices in your region may begin warning or
blocking sideloaded APKs from unregistered developers. Until then, GitHub and
Obtainium installs work normally. If blocking begins and no F-Droid workaround
exists, the project will reassess registration. Watch the release notes for
updates.

This is a reversible default: maintainers can override this stance at any time.
The release-front-door script checks that this section has been reviewed within
the current quarter; stale guidance blocks the release.

### Enable as Default Keyboard

1. Open **Settings → System → Languages & input**.
2. Tap **Virtual keyboard** (or **On-screen keyboard**).
3. Select **SwiftFloris** and grant permissions as prompted.

## Upgrading from v1.8.x (application-ID change)

As of **v1.9.0**, SwiftFloris installs under its own application ID
**`io.github.sysadmindoc.swiftfloris`** instead of the upstream
`dev.patrickgold.florisboard` it inherited as a fork. This ends the install
collision with upstream FlorisBoard and is a prerequisite for F-Droid
inclusion. Android treats the new ID as a different app, so the upgrade is a
**one-time reinstall with a data carry-over**:

1. On your existing install, open **Settings → Advanced → Backup** and export
   a backup archive (include the personal dictionary and any themes).
2. Install the new APK (it can sit next to the old one — they no longer
   conflict).
3. In the new install, open **Settings → Advanced → Restore** and pick the
   archive. Backups created under the old ID (and by upstream FlorisBoard)
   are accepted as same-vendor — no warning, full personal-dictionary,
   theme, and preference carry-over.
4. Enable the new keyboard (see *Enable as Default Keyboard* above), then
   uninstall the old app.
5. **Obtainium users:** re-add the app with the subscribe link above — the
   old subscription tracks the retired ID and will not see the new installs.

Nothing leaves your device during any of this; the backup archive is a local
file you can delete afterwards.

## Migrating from SwiftKey

SwiftFloris imports `swiftkey-cloud.json` through **Settings → Personal dictionary → Import**. The parser has cumulative-byte limits, post-import confirmation with rollback, and an encrypted SwiftFloris export/import round-trip for users moving their local dictionary between installs.

## Documentation

Public project information is available in this README, [Security](docs/SECURITY.md), [Reproducible builds](docs/REPRODUCIBLE_BUILDS.md), [GitHub Releases](https://github.com/SysAdminDoc/SwiftFloris/releases), and [GitHub Issues](https://github.com/SysAdminDoc/SwiftFloris/issues). Contributor-facing architecture and stack notes live in [Architecture & Stack](#architecture--stack). Maintainer-only planning, research, changelog, and verification notes stay local.

## Architecture & Stack

**Language and build**

- Kotlin 2.4.0, Compose BOM 2026.06.00, Material 3 + material-kolor.
- AGP 9.2.1, Gradle 9.6.1, JDK 21.
- KSP 2.3.9, Room 2.8.4, SQLCipher 4.16.0, Tink Android 1.22.0.
- Kotest 6.1.11 unit-test runner; Roborazzi 1.64.0 and Robolectric 4.16.1
  for screenshot/JVM Android regressions.
- minSdk **26** (Android 8.0); targetSdk **36** (Android 16); compileSdk **37** (Android 17 APIs available behind behavior gates).
- Crowdin pipeline for translations.
- No `INTERNET` permission in the manifest (local release gate enforced).

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
# Android SDK 36 target plus Android SDK 37 compile platform
# JDK 21+
# Gradle 9.6.1 (use the bundled wrapper)
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

# Roborazzi screenshot verify (visual-regression gate)
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

Published APKs are generated with pinned Gradle, Android Gradle Plugin, Kotlin, KSP, Compose, Roborazzi, and dependency-lock inputs so release fingerprints can be reproduced from the same toolchain set.

## Permissions

| Permission | Purpose | Required? |
|------------|---------|-----------|
| `INPUT_METHOD` | IME service binding | ✅ Yes |
| `VIBRATE` | Haptic feedback | Optional |
| `RECORD_AUDIO` | Not requested by SwiftFloris; the external voice keyboard owns microphone access | No |
| `BIND_NOTIFICATION_LISTENER` | App-aware smartbar features | Optional |

> **Privacy note:** SwiftFloris does not request `INTERNET`. The local release gate validates this before publication.

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
- **Personal dictionary backup:** excluded from both Android cloud backup and device-to-device transfer. The SQLCipher passphrase is wrapped by an Android Keystore key that is non-exportable, so the ciphertext is undecryptable on a new device. Use Settings → Personal dictionary → Export/Import for explicit local migration.

The public posture is simple: no network permission, no telemetry, no account binding, no cloud learning, and explicit user action before sensitive local data is exported or shared with another app.

### Security reports

Report suspected vulnerabilities through [GitHub Security Advisories](https://github.com/SysAdminDoc/SwiftFloris/security/advisories/new) when available. If the advisory form is unavailable, open a GitHub issue with a minimal description and ask for private follow-up before sharing exploit details, device logs, personal dictionary content, clipboard content, or private APKs. Coordinated disclosure is preferred; see [Security](docs/SECURITY.md) for supported scope and response expectations.

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

SwiftFloris exposes a Tasker intent contract for INSERT_TEXT / INSERT_CLIP / SWITCH_LAYOUT / TRIGGER_VOICE actions, guarded by the app's signature-level integration permission.

## Performance and benchmarks

Six Macrobenchmark trace sections are emitted from production code paths:

- `swiftfloris.ime.firstRender` (`FlorisImeService.onCreateInputView`)
- `swiftfloris.nlp.suggest` (`LatinLanguageProvider.suggest`)
- `swiftfloris.smartbar.candidates.recompose` (`CandidatesRow` body)
- `swiftfloris.theme.switch` (`ThemeManager.updateActiveTheme`)
- `swiftfloris.dict.load` (`loadSpecificDictionary`)
- `swiftfloris.nlp.symspell.build` (lazy index init)

Current SM-S938B / Android 16 baselines record `am start -W` first-render medians of `TotalTime` 31.0 ms and `WaitTime` 34.0 ms, benchmark-only `swiftfloris.ime.firstRenderMs` median 18.335469 ms, cold provider-direct `swiftfloris.nlp.firstSuggestionMs` median 1878.616249 ms for `teh`, dictionary cold-load / preload medians of 757.353333 ms / 772.080625 ms with lazy SymSpell d1/d2 index medians of 500.230156 ms / 532.298281 ms, candidate-row warm-typing recomposition median body / max / total of 0.326563 ms / 0.770365 ms / 4.069529 ms, theme-switch median body / max / total of 18.541197 ms / 19.587708 ms / 57.505571 ms with 0.2808075 ms cached warm switches, and backup/restore default-archive medians of 12.653698 ms backup create / 9.874167 ms restore total with 3/3 sections restored. The local benchmark trend checker compares candidate JSON against those baselines and fails watched medians above the documented +8 % window. The repository deliberately does not publish hand-wavy latency tables; numbers include the device, OS build, and trace section or log marker that produced them.

## Testing

- **Unit tests:** Kotest, run with `./gradlew test`. Last reported HEAD: 998+ tests (post-v1.8.40), expanding with each release. The v1.8.47 hardening pass added defensive tests around dictionary import limits, voice-model atomic install, theme asset traversal, and quick-action serializer fallback.
- **Visual regression:** Roborazzi 1.64.0, plugin alias active. Run `:app:verifyRoborazziDebug` for committed baselines and `:app:verifyRoborazziRelease` before APK publication. Baselines cover the maintainer chip, SwiftKey High Contrast, Aurora Animated, and Settings -> Addons surfaces.
- **Macrobenchmark:** `:benchmark` is wired for AndroidX trace/frame runs, and the adb harness scripts record repeatable IME first-render, first-suggestion, dictionary-load, candidate-row recomposition, theme-switch, and backup/restore baselines. `scripts/check-benchmark-trends.py` compares candidate JSON against the committed baseline set.
- **No-network gate:** the local Gradle gate verifies the absence of `INTERNET` permission on every release build.
- **Lint drift:** local lint can run through `scripts/run-lint-debug-with-baseline-check.sh`, which fails stale baseline entries instead of leaving them as console-only noise.
- **Device smoke:** run the IME enable -> type -> commit -> glide smoke on phone, tablet-sized API 36, and foldable-sized API 36 emulator/device lanes when that behavior changes. The tablet/foldable lanes force the documented `UNIVERSAL_RESIZABLE_BY_DEFAULT` compat behavior that becomes mandatory for API 37 targets.
- **Repo hygiene gate:** `scripts/release-evidence.ps1` runs `scripts/check-no-root-crash-logs.sh` so root
  `hs_err_pid*.log` / `replay_pid*.log` files cannot be committed or left ignored in the root, and
  `scripts/check-repo-hygiene.sh` rejects tracked generated build/report output.

## Recent releases

The full public release stream lives on [GitHub Releases](https://github.com/SysAdminDoc/SwiftFloris/releases).

- **v1.9.53** (2026-06-25) — Roadmap drain: release-channel freshness is now a blocking local gate, Android 17 CJK selected-candidate accessibility metadata, sample addon APK validation in local release evidence, Tink 1.22.0 (security), AboutLibraries 15.0.3, deprecated `announceForAccessibility` migrated to Compose live-region, data-extraction rules verified by XML-parsed domain/path pairs, public trust docs tracked in git, destructive clipboard Room migrations replaced with row-preserving, addon provenance tap-to-copy JSON, one-tap privacy proof export, EditorInfo sensitive-field replay tests, keyboard layout JSON validation, a new Snippet management Settings screen with Espanso YAML import, reduced-motion polish across Settings/setup transitions, safer snippet deletion, clipboard text-tile accessibility labels, unsaved-draft recovery in the custom layout editor, and bounded inline-autofill rendering that drops invalid host suggestions instead of crashing.
- **v1.9.52** (2026-06-16) — Premium Settings polish: the Settings home now opens with one status-aware overview card, compact Search / Import / Privacy quick actions, and local-trust checks for no-network releases, local imports, and verifiable builds. Settings search now uses a calmer Material 3 field treatment and shows result counts before the list.
- **v1.9.51** (2026-06-16) — Added spacebar touchpad cursor mode, migration assistant import guidance, source-code and release-verification links in Privacy posture, CLDR/Emoji version metadata gates, and the prepared F-Droid fdroiddata YAML recipe.
- **v1.9.50** (2026-06-14) — Added a Terminal bottom-row preset with Esc / Ctrl / Alt / Home / End / Tab keys and verified keyboard magnification accessibility on API 36 across text, emoji, and touch input surfaces.
- **v1.9.49** (2026-06-14) — Fixed stale backup/transfer privacy copy, added OSV severity release blocking, converted lazy plural strings to Android quantity resources, renamed the Gradle root project for reviewer clarity, wired Android 14+ stylus handwriting through the recognizer facade, and added bounded file-backed diagnostics.
- **v1.9.48** (2026-06-14) — Audit hardening pass: the exported share-to-clipboard handler now rejects `file://` URIs (closing a confused-deputy read of the app's own private files); MCP daemon discovery bounds untrusted catalog reads to prevent an out-of-memory denial; incognito `FLAG_SECURE` now reapplies reliably across keyboard restarts; corrected an inverted SERA vowel order in the Ge'ez/Tigrinya transliterators; and several settings polish fixes — a real empty-state message for the privacy audit log, dark/light theme accessibility labels, no more dangling empty headers in Learned entries, and clearer sync-import and update-check wording.
- **v1.9.47** (2026-06-14) — New one-tap "Full backup" action on the Backup screen ticks every section (preferences, layouts, themes, and all local clipboard items) and writes a dated archive in a single tap — no manual checkbox juggling before a reinstall or migration. Reuses the existing local, no-network backup flow.
- **v1.9.46** (2026-06-14) — New "Touch calibration" setting (Settings → Typing) exposes Conservative / Normal / Rescue-heavy profiles that tune gap-rescue dead zones and adaptive-touch neighbour correction. Normal reproduces the previously-hardcoded behaviour exactly, so the default is unchanged.
- **v1.9.45** (2026-06-14) — New optional "CJK mixed-script spacing" setting inserts a boundary space between Han characters and adjacent Latin words or digits (安装 App, 第 3 章). Preference-gated, off by default; the Han boundary requirement keeps Latin/digit-only typing untouched and existing whitespace is respected.
- **v1.9.44** (2026-06-13) — Locale script heuristics now use runtime Unicode character-property detection instead of hardcoded language lists, covering all scripts automatically. Correction: the remote SLSA/SBOM workflow claim was later retired; current artifact trust evidence is local-only through `scripts/release-evidence.ps1`.
- **v1.9.43** (2026-06-13) — Translate quick-action now reads the user's preferred target locale instead of hardcoding English. Sticker bitmap LRU cache now trims on system memory pressure. Composer WithRules case conversion now preserves character count under CAPS_LOCK.
- **v1.9.42** (2026-06-13) — Deep audit pass 2: sticker bitmap downsampling now keys on longest edge (prevents OOM on wide/tall stickers), space-bar swipe-up NO_ACTION no longer swallows the gesture, clipboard restore no longer crashes on image/video items with null URIs, numeric/phone mode keys no longer consume manual shift state.
- **v1.9.41** (2026-06-12) — Android 17 adaptive IME validation now covers sw600 foldable/tablet sizing, split/floating window clipping, no large-screen manifest opt-out, and phone/tablet/foldable emulator smoke lanes.
- **v1.9.40** (2026-06-12) — Pending F40 Roborazzi settings and keyboard-surface screenshots are now active visual gates, with committed baselines for AI features, voice input, MCP settings, typing stats, honeycomb, and glide trail surfaces.
- **v1.9.39** (2026-06-12) — Settings now includes a per-app keyboard profile editor with add/edit/delete flows, package-label fallback, malformed profile recovery, and direct Privacy/Smartbar/Search entry points.
- **v1.9.38** (2026-06-12) — Han shape-based language packs now drive real table-backed suggestions, word-list, and word-frequency lookups; placeholder spell results are gone and broken active Han tables surface as fail-closed language-pack status.
- **v1.9.37** (2026-06-12) — Raw local typing-trace sharing now requires a sensitive-content confirmation, replay fixture export is the recommended debugging path, and sanitized fixtures drop touch evidence alongside cursor context.
- **v1.9.36** (2026-06-12) — Settings -> Sync now routes Android 8-12 users to passphrase-encrypted dictionary export/import instead of exposing unsupported sealed device sync controls; Android 13+ sealed sync behavior is unchanged and covered by policy tests.
- **v1.9.35** (2026-06-12) — Public release metadata now matches the shipped version, GitHub issue templates point to SwiftFloris instead of upstream FlorisBoard, and localized app-name overrides no longer display FlorisBoard.
- **v1.9.34** (2026-06-12) — Input-path key press state, expected editor content lookup, and selection measurement helpers no longer block through coroutine `runBlocking`; regression tests cover synchronous key dispatcher state transitions.
- **v1.9.4** (2026-06-11) — Sticker search now covers bundled and user-imported packs, matching local filename tokens, GIF names, generated keywords, and imported folder names without network access.
- **v1.9.3** (2026-06-11) — Benchmark regression CI now self-tests the trend checker and requires the selected benchmark slice to produce the matching candidate JSON before passing.
- **v1.9.2** (2026-06-11) — SymSpell correction indexes now stop before crossing explicit delete-entry budgets, report partial builds in benchmark logs, and keep edit-distance-2 correction coverage bounded for IME heap safety.
- **v1.9.1** (2026-06-11) — Settings -> Sync now exports sealed personal-dictionary sync files to a picked SAF folder or manual JSON document, imports paired-device envelopes back into the local dictionary with insert/update/delete application, and surfaces progress, success, no-op, and failure states without adding network permissions.
- **v1.9.0** (2026-06-11) — SwiftFloris now installs under its own application ID `io.github.sysadmindoc.swiftfloris`, ending the install collision with upstream FlorisBoard and unblocking F-Droid inclusion. One-time reinstall with backup/restore data carry-over (old-ID and upstream backups accepted as same-vendor); addon/MCP/Tasker action+permission namespaces moved with it; Obtainium link updated. See *Upgrading from v1.8.x* above.
- **v1.8.248** (2026-06-11) — Password/PIN fields no longer run word suggestions or spacebar autocorrect (which could silently append a "correction" into masked input); precise delete/select swipes no longer crash mid mass-selection; personal next-word learning no longer pairs words across apps/fields; the suggestion-removal prompt and pinned emoji chips now follow the keyboard theme; missing TalkBack labels and translatable strings added across the IME and editors; MCP daemon discovery/binding manifest entries fixed; What's-new and GitHub Release notes now source from the tracked fastlane changelogs.
- **v1.8.247** (2026-06-05) — Manual subtype switching by id now treats stale chooser ids as no-ops instead of risking a forced-null crash.
- **v1.8.246** (2026-06-04) — Repo hygiene now explains that module `build/` caches can survive `git rm --cached` and should be treated as local ignored output.
- **v1.8.245** (2026-06-04) — Fastlane changelog drafting now documents the 480-character draft budget, store-facing summary rules, and evidence-backed wording expectations.
- **v1.8.244** (2026-06-04) — Visual-QA, manual-QA, and release-evidence checklists are now linked from the verification docs.
- **v1.8.243** (2026-06-04) — Localization copy now avoids Turkish repeated-word lint, uses clearer source labels, and standardizes trust-sensitive failure/destructive copy with focused resource tests.
- **v1.8.242** (2026-06-04) — `NativeStr.toJavaString()` now decodes only ByteBuffer remaining bytes across heap, sliced, direct, and read-only buffers without consuming caller position.
- **v1.8.241** (2026-06-04) — MIME helper aggregate semantics are now documented and covered, constructor stdout logging is removed, and legacy font wildcard matching is explicit.
- **v1.8.240** (2026-06-04) — Async preference-store init failures now stage a crash report, unblock the Settings splash wait, and redirect to recovery instead of hanging.
- **v1.8.239** (2026-06-04) — Editor start/selection content-generation jobs now cancel or supersede stale work before reset, finishInput, or field switches can republish old state.
- **v1.8.238** (2026-06-04) — Clipboard image/video history tiles now expose localized TalkBack labels with media type, history group, and copied-time context while keeping decorative thumbnail overlays hidden.
- **v1.8.237** (2026-06-04) — Settings search destination highlights are now one-shot and dismissible, so stale search-result cards do not reappear on later visits.
- **v1.8.236** (2026-06-04) — Suggestion candidate generation now snapshots incognito, no-personalized-learning, preference, and ghost-text sensitivity inputs before async provider work begins.
- **v1.8.235** (2026-06-04) — Settings search now exposes TalkBack field labels/state, polite result-status changes, and result rows with position, screen, title, and summary context.
- **v1.8.234** (2026-06-04) — Focused regression tests now pin Arabic combining-mark shaping, Snygg unknown selectors and `contentScale`, private-session trace suppression, and locale-scoped n-gram flush behavior.
- **v1.8.233** (2026-06-04) — Editor batch edits now wrap only synchronous `InputConnection` mutations while expected-content generation and queue pushes happen before the batch opens.
- **v1.8.232** (2026-06-04) — Settings -> Personal dictionary now explains blocked system-back gestures during active save, delete, import, or export work with operation-specific feedback.
- **v1.8.231** (2026-06-04) — Dynamic incognito toggles now immediately re-apply the IME window screen-capture guard for the active field.
- **v1.8.230** (2026-06-04) — Sync sealed-box envelopes now have fixed v1 schema constants, deterministic X25519/AES-GCM vector coverage, and documented compatibility policy before transport activation.
- **v1.8.229** (2026-06-04) — Non-co-signed addon APKs now require an explicit Settings trust action before enrollment; co-signed addons still load automatically.
- **v1.8.228** (2026-06-04) — Clipboard history search is now wired into the keyboard palette with a settings toggle, clear/no-results states, and query plus type-filter composition coverage.
- **v1.8.227** (2026-06-04) — Japanese locale capability gates now use the valid `ja` language subtag for no-capitalization and no-auto-space behavior, with focused JVM coverage.
- **v1.8.226** (2026-06-04) — Post-audit release ledger for pushed n-gram, thread-safety, crypto, trace-privacy, Arabic-shaping, Snygg selector/contentScale, and clipboard media fallback fixes.
- **v1.8.225** (2026-06-04) — Deep engineering audit hardening across IME core, clipboard, dictionary import, privacy backup rules, settings sliders, haptics, and CI release gates.
- **v1.8.224** (2026-06-04) — Settings search now resets populated result lists to the top when the query changes so stale scroll offsets do not hide the highest-ranked result.
- **v1.8.223** (2026-06-04) — Settings search now resolves high-traffic capability synonyms such as dark theme, haptic, trace, punctuation, and privacy to the intended settings destinations.
- **v1.8.222** (2026-06-04) — Settings search no-results states now include a one-tap Browse all settings action back to Settings Home.
- **v1.8.221** (2026-06-04) — Settings search now has a JVM/Robolectric drift guard for duplicate entry IDs, real string-resource resolution, and typed destination-route mapping.
- **v1.8.220** (2026-06-04) — Root onboarding docs now agree on the local-only planning, shipped-state, release-note, and archived-planning split.
- **v1.8.219** (2026-06-04) — Restore and crash diagnostic failures now use project logging, and restore toasts/cards use stable fallback copy when Android reports a null or blank throwable message.
- **v1.8.218** (2026-06-04) — Staged startup exceptions now persist to the local crash report store and open the crash dialog before Settings can hang behind the splash screen.
- **v1.8.170** (2026-05-18) — Keyboard preview field polish: settings preview fields now sit on a distinct bottom surface, expose ready/active feedback, preserve bottom-bar traversal, and use coroutine-safe feedback when Android cannot open the IME picker.
- **v1.8.169** (2026-05-18) — Empty-state UX polish: selected dictionary-language views, extension categories, language packs, filtered clipboard history, and the theme manager now explain blank states and route users toward add/import/filter-clear/recovery actions.
- **v1.8.168** (2026-05-18) — Addon scan progress: Addons Settings now shows a shared progress card while installed packages and dictionary-pack metadata are rescanned, and the touched preference state read uses the current `collectAsState` API.
- **v1.8.167** (2026-05-18) — Theme and extension destructive confirmations: draft file deletes plus theme rule/property deletes now require explicit confirmation and explain that installed extensions/themes remain unchanged until save.
- **v1.8.166** (2026-05-18) — Repo hygiene closure: CI now runs a repo-hygiene script, generated build/report output is guarded, legacy deleted markdown decisions are documented, and commit-scope/final-handoff rules are pinned.
- **v1.8.165** (2026-05-18) — CI quality gates: Android CI lint now fails stale baseline drift, Dependabot reviews Gradle and Actions updates weekly, a manual emulator settings-launch smoke exists, and local verification commands are documented.
- **v1.8.164** (2026-05-18) — Backup/restore baseline: benchmark-only representative archive generation measures preference plus keyboard/theme backup creation and merge restore timings under `docs/benchmark-results/`.
- **v1.8.163** (2026-05-18) — Theme-switch baseline: benchmark-only direct switch markers and an adb harness measure SwiftKey Pure / M3E theme swaps while the benchmark IME is visible, including cold and cached timings under `docs/benchmark-results/`.
- **v1.8.162** (2026-05-18) — Candidate row recomposition baseline: benchmark-only smartbar log markers and an adb harness measure warm typing recomposition counts/durations plus paired NLP suggestion timing under `docs/benchmark-results/`.
- **v1.8.161** (2026-05-18) — Dictionary load/preload baseline: a benchmark-only activity preloads the Latin dictionary, forces lazy SymSpell d1/d2 index construction with an invalid probe token, and records SM-S938B / Android 16 numbers under `docs/benchmark-results/`.
- **v1.8.160** (2026-05-18) — First suggestion latency baseline: a benchmark-only activity invokes the Latin suggestion provider against a real `EditorContent` snapshot and records cold provider-direct SM-S938B / Android 16 numbers under `docs/benchmark-results/`.
- **v1.8.159** (2026-05-18) — IME first-render benchmark baseline: `:benchmark` is active again, a benchmark-only input activity drives cold IME view creation, and SM-S938B / Android 16 first-render numbers are committed under `docs/benchmark-results/`.
- **v1.8.158** (2026-05-18) — Accessibility manual QA notes: contributor and accessibility docs now list TalkBack traversal, key-label, candidate-row, font-scale, non-color-state, and theme/layout checks.
- **v1.8.157** (2026-05-18) — Non-color state indicators: shared success/progress/neutral cards and extension-import row icons make readiness, progress, cancellation, and completion visible without relying on color alone.
- **v1.8.156** (2026-05-18) — Theme contrast audit: bundled keyboard/candidate/dialog styles and settings warning/error/dialog palettes now have selector-level AA coverage; low-contrast enter-key variants and card secondary text were tightened.
- **v1.8.155** (2026-05-18) — Dynamic font scaling: compact settings metadata, links, extension component headings, and theme-rule key previews now expand wrapping room or preview size at high font scale.
- **v1.8.154** (2026-05-18) — Keyboard key accessibility: semantic key targets now follow the real touch hitbox, expose an accessibility click action, and label common clipboard, voice, mode, layout, and smartbar-control keys explicitly.
- **v1.8.153** (2026-05-18) — Candidate and smartbar TalkBack labels: prediction-strip candidates now announce suggestion type, position, and text, while quick actions use a stable display-name/tooltip fallback policy.
- **v1.8.152** (2026-05-18) — Settings focus order: the shared settings scaffold now gives TalkBack and keyboard traversal a stable app bar -> content -> bottom actions -> floating action order.
- **v1.8.151** (2026-05-18) — Dictionary transfer busy states: user dictionary import/export now shows explicit progress cards, runs transfer work off the main thread, and blocks duplicate transfer/navigation/menu/entry actions while busy.
- **v1.8.150** (2026-05-18) — Trust-state recovery microcopy: backup, restore, extension, language-pack, archive-file, and manual dictionary failure cards now state what stayed unchanged and provide a retry/recovery path with the technical detail.
- **v1.8.149** (2026-05-18) — Dictionary entry trust states: manual add/update/delete now show progress/result cards, run DAO writes off the main thread, refresh affected suggestion overlays, and block duplicate entry actions while work is running.
- **v1.8.148** (2026-05-18) — Extension archive file trust states: archive file import/rename/delete now show progress/result cards, do file work off the main thread, and block duplicate actions while work is running.
- **v1.8.147** (2026-05-18) — Theme extension trust states: theme editing now shows save progress/failure cards, confirms component removal with draft-state feedback, and installed extension deletion now shows progress/failure cards while blocking duplicate actions.
- **v1.8.146** (2026-05-18) — Language pack trust states: extension import now shows file-reading/importing/cancel/failure states plus new/update/skipped counts, and language pack deletion now shows progress/success/failure cards while blocking duplicate actions.
- **v1.8.145** (2026-05-18) — Restore flow trust states: erase restores now require confirmation and show recovery-copy guidance, restore progress/cancellation/failure/partial-failure states stay visible, and section-level restore summaries prevent missing archive sections from silently erasing local data.
- **v1.8.144** (2026-05-18) — Backup flow trust states: backup progress, cancellation, share-sheet handoff, failure, and sensitive-clipboard exclusion now surface as explicit cards, with `BackupFlowNotice` policy coverage.
- **v1.8.143** (2026-05-18) — Autocorrect lifecycle contract: spacebar, punctuation, backspace, hardware, glide-delete, provider-notification, manual QA, and regression-test contracts are now defined; accepted provider notifications now wait for successful editor commits.
- **v1.8.142** (2026-05-18) — Theme rule edit policy extraction: `ThemeRuleEditPolicy` now owns add-rule selection validation, selector toggling, and key-code attribute parsing/replacement decisions for the theme editor.
- **v1.8.141** (2026-05-18) — Punctuation flush policy extraction: `KeyboardAutoCommitFlushPolicy` now owns software non-letter autocorrect flush decisions for media mode, alphabetic keys, punctuation, numeric keys, and numeric/phone layouts.
- **v1.8.140** (2026-05-18) — Candidate auto-commit policy extraction: `CandidateAutoCommitPolicy` now owns shortcut, phrase repair, active-strip, immediate fallback, quick-prediction, and rejected-correction gating decisions with focused JVM coverage.
- **v1.8.139** (2026-05-18) — Dependency warning review: Gradle is checksum-pinned to 9.5.1, Navigation Compose is on 2.9.8, and JUnit Vintage is centralized at 6.0.3 after official-release review, clearing the dependency-version lint warnings.
- **v1.8.138** (2026-05-18) — Conservative unused-resource cleanup: obsolete launcher/branding resources and dead legacy color tokens were removed after manifest/code/asset/test/dynamic lookup review, reducing lint from 289 warnings / 1 hint to 245 warnings / 1 hint.
- **v1.8.137** (2026-05-18) — Theme editor validation tests: theme component metadata now validates through `ThemeComponentMetaValidationPolicy`, with JVM coverage for valid apply normalization, invalid fields, duplicate IDs, and blank stylesheet fallback.
- **v1.8.136** (2026-05-18) — Subtype editor validation tests: editable subtype drafts now validate through `SubtypeEditorValidationPolicy`, with JVM coverage for default add-state missing fields, complete draft building, select-placeholder rejection, and edit-state preservation.
- **v1.8.135** (2026-05-18) — Language pack import/update tests: extension import readiness now lives in `ExtensionImportPolicy`, with JVM coverage for new installs, user-installed updates, bundled-core rejection, corrupted metadata, wrong extension type, unsupported files, and import button enablement.
- **v1.8.134** (2026-05-18) — Backup/restore policy tests: validation and operation-state decisions now live in `BackupRestorePolicy`, with JVM coverage for backup success/cancellation/failure, invalid archives, restore enablement, and partial-failure classification.
- **v1.8.133** (2026-05-18) — Incognito suggestion privacy policy tests: app-declared no-learning override, dynamic toggle availability, committed-word learning, and touch-decoder evidence gates now have focused JVM coverage.
- **v1.8.132** (2026-05-18) — Glide typing delete policy tests: immediate backspace word-delete escalation now lives in the editor input policy and is covered for enabled, disabled, inactive phantom-space, and explicit word-delete paths.
- **v1.8.131** (2026-05-18) — Spacing lifecycle state tests: auto-space and phantom-space state transitions now have focused JVM coverage for one-update grace, composing-region visibility, and candidate-for-revert cleanup.
- **v1.8.130** (2026-05-18) — Hardware keyboard input policy tests: hardware keydown/keyup routing now has focused JVM coverage for space, enter, delete pass-through, shift, mapped letters, mapped punctuation, and mapped punctuation flushing pending autocorrect before commit.
- **v1.8.129** (2026-05-18) — Editor input behavior policy extraction: autocorrect spacebar commits, rejected-correction protection, punctuation auto-spacing, phantom spacing, double-space period, and sentence-capitalization gates now have focused JVM coverage through a pure policy class.
- **v1.8.128** (2026-05-18) — Nastaliq Urdu font bundle: the official OFL-1.1 Noto Nastaliq Urdu TTF is now committed as an APK asset, Urdu subtype key labels and hints route Arabic-script text through it, and asset/license tests pin the bundle.
- **v1.8.127** (2026-05-18) — Emoji pinned-group sheet: long-pressing emoji can now pin them to named groups, and pinned-group chips commit the saved emoji sequence from the palette.
- **v1.8.126** (2026-05-18) — Addons dictionary catalog polish: Settings -> Addons now lists mounted dictionary packs with language, word count, dataset license, source, descriptor rejections, and updated install guidance.
- **v1.8.125** (2026-05-18) — Addons dictionary asset mounting: enrolled dictionary-pack APK assets now feed the Latin dictionary store through `PackageManager#getResourcesForApplication(...)`, merge with bundled baselines, and reload when the live addon registry generation changes.
- **v1.8.124** (2026-05-18) — Addons trust controls: Settings -> Addons can now reset all saved signing-certificate pins or trust a changed certificate after confirmation and rescan; the pin codec gained targeted package removal and the Addons Roborazzi baseline was refreshed.
- **v1.8.123** (2026-05-18) — Roborazzi baseline hard gate: committed screenshot baselines for the maintainer chip, SwiftKey High Contrast, Aurora Animated, and Settings -> Addons surfaces; CI now fails on visual-regression drift instead of using `continue-on-error`.
- **v1.8.104 – v1.8.122** (2026-05-17/18) — seventh-pass audit closure and follow-up slices: app-declared `IME_FLAG_NO_PERSONALIZED_LEARNING` and `EXTRA_IS_SENSITIVE` privacy flags are honoured, voice handoff refuses sensitive fields, checks every external voice IME's microphone grant, exposes a durable Listening state, and now gates the in-app Whisper/Vosk route selector and model catalog behind a preview-only local-runtime flag; dangerous voice remove commands were tightened, the voice setup activity is non-exported with a validated setup-intent contract, clipboard backup/clear-all leaks were closed, provider-backed clipboard media clones now cap image/video bytes, image preview decode rejects oversized dimensions before allocation, automatic clipboard history eviction now closes provider-backed media before deleting rows, sensitive clipboard text no longer feeds pin-popup description URL/email/phone classification, startup reconciliation removes missing-file history rows plus unreferenced provider files / metadata rows, media restore recreates provider metadata for restored image/video clips, failed foreign media URI clones no longer create phantom history entries, clipboard history maintenance no longer sorts or evicts on Main, the dead parallel Tink clipboard-history store has been removed so the Room-backed manager is the only live storage path, and the KenLM mmap reader now rejects header/pre-body offsets instead of aliasing them to trie-body bytes.
- **v1.8.85 – v1.8.103** (2026-05-17) — cross-subsystem hardening pass + 18 single-feature follow-up releases. v1.8.85 closed eleven privacy / security / reliability gaps (merged-manifest `verifyNoInternetPermission`, Android 12+ `data_extraction_rules.xml`, atomic `ZipUtils.unzip`, thread-safe `HardwareKeyboardRuntimeMapper`, sticker decoder OOM, sticker MIME spoof, addon enumerator size category-error, `verify-reproducible-apk.sh` payload-manifest pass criterion, CI workflow permissions, `pull_request_target` injection, AltGr); v1.8.86 – v1.8.102 then returned to per-PR scope and closed eleven of twelve F-roster items (FLAG_SECURE on numeric PIN + passphrase dialog, legacy-passphrase recovery, ZipUtils abort policy, SAF lost-grant UX, addon spec docs alignment, LDML `shift=` semantics, fastlane script hardening, SHA-pinned floating action tags, `release.yml` keystore hygiene, `verifyDataExtractionRules` build gate, sticker LRU + folder cap, `HardwareKeyEntry.longPressAlternates`); v1.8.103 closes the documentation half. The remaining F11 Roborazzi baseline item closed in v1.8.123.
- **v1.8.84** (2026-05-17) — Settings → Addons status surface: users can inspect accepted/rejected addon APKs, manually rescan through the startup reconciliation path, and review package/license/version/size/signing-fingerprint details.
- **v1.8.83** (2026-05-17) — Addon registry startup wiring: the IME now scans installed addon manifests at startup, reconciles them through persisted signing pins, publishes a process-wide registry, and cleans malformed stored pin lines.
- **v1.8.82** (2026-05-17) — Addon signing-pin persistence: `AddonSigningPinSet` safely parses/encodes addon package fingerprint pins and `prefs.addon.signingCertPins` gives the registry a durable trust store consumed by v1.8.83 startup wiring and v1.8.84 Settings status UI.
- **v1.8.81** (2026-05-17) — Addon catalog foundation: `AddonRegistry` now reconciles live addon state with signing-certificate pins, and `DictionaryPackCatalog` validates dictionary-pack descriptors plus provenance before Settings/Addons UI and asset mounting land.
- **v1.8.80** (2026-05-17) — SQLCipher provider migration plan: documented the current LibTomCrypt-based Android Community AAR state, OpenSSL proof-of-concept path, migration triggers, 16 KB page-size gates, and rollback rules without changing the runtime dependency.
- **v1.8.79** (2026-05-17) — Honeycomb hex layout wire-up: the bundled honeycomb character layout is registered for subtype selection, routed through `TextKeyboardLayoutStyle.Honeycomb`, clipped to `HoneycombHexShape`, and hit-tested against the actual hex instead of rectangular bounding boxes.
- **v1.8.78** (2026-05-17) — Keyman `.kmp` package import foundation: safe ZIP/package parser for `kmp.json`, keyboard/language/example metadata, LDML-in-package extraction, lexical-model classification, compiled-engine-required classification, and unsafe entry skipping.
- **v1.8.77** (2026-05-17) — User-imported sticker folder: Settings → Emoji & stickers can persist a local SAF folder URI, enumerate supported image files into an Imported sticker pack, preview them in the sticker grid, and commit them through the existing rich-content provider path.
- **v1.8.76** (2026-05-17) — Hardware-keyboard runtime mapping: imported layouts can bind to Android hardware `deviceId` values, resolve `KeyEvent` scan/key codes through KLC/macOS fallbacks, and commit mapped printable characters through `KeyboardManager`.
- **v1.8.75** (2026-05-17) — Hardware-keyboard import: added an XXE-hardened macOS `.keylayout` XML parser that normalizes key maps, modifier maps, and action-backed dead keys into `HardwareKeyboardLayout`.
- **v1.8.74** (2026-05-17) — Bump-batch C: Android Gradle Plugin `9.0.0` → `9.2.1` and Compose BOM `2026.03.01` → `2026.05.00`; R8 keepattributes audit required no rule changes.
- **v1.8.73** (2026-05-17) — Repo hygiene: local root JVM crash/replay logs moved to `.ai/local-crash-logs/2026-05-16/`, and CI now rejects committed root `hs_err_pid*.log` / `replay_pid*.log` files.
- **v1.8.72** (2026-05-17) — Roadmap correction: HeliBoard / NLnet open-glide integration is now treated as an additive future track, while SwiftFloris's shipped `StatisticalGlideTypingClassifier` remains the production glide path until a permissive open library and dataset are actually available.
- **v1.8.71** (2026-05-17) — Bump-batch B: Roborazzi `1.55.0` → `1.60.0` and Robolectric `4.14.1` → `4.16.1`; no app code, permissions, or runtime behavior changed.
- **v1.8.70** (2026-05-17) — README migration-window follow-up: Samsung / Grammarly keyboard-workflow callouts, Galaxy AI Writing Assist compatibility note for One UI 7+, Grammarly Keyboard replacement note, and release-front-door refresh.
- **v1.8.69** (2026-05-17) — Bump-batch A: coroutines `1.11.0`, KSP `2.3.8`, ZXing `3.5.4`, and AboutLibraries `14.2.0`; beta AboutLibraries `15.0.0-b01` intentionally skipped.
- **v1.8.68** (2026-05-17) — N7.6 Tink / AndroidKeystore migration: removed AndroidX Security Crypto, added shared Tink encrypted-preference wrapper, migrated SQLCipher passphrase and legacy clipboard-history payloads one time when old keysets remain readable.
- **v1.8.67** (2026-05-17) — N12.5 reproducible-build self-verification CI: new build-twice release APK workflow plus `scripts/verify-reproducible-apk.sh` clean-worktree byte comparison and drift manifests.
- **v1.8.66** (2026-05-17) — N8.7 Article 50 transparency surface: first-run **Review local AI features** setup step, reopenable Settings → About → **AI features in this keyboard** screen, docs links, and catalog test coverage for next-word / glide / voice / translation / smart-compose disclosures.
- **v1.8.65** (2026-05-17) — Phase A3 Settings wiring: **Export encrypted** passphrase dialog + `.sfexp` create-document flow, direct encrypt-then-write personal-dictionary export, `SFEXP1` import sniffing, passphrase decrypt, and `DictionaryImporter`/rollback-summary routing for decrypted SwiftFloris combined-list files.
- **v1.8.64** (2026-05-17) — Phase D1: calendar quick-insert (`QuickAction.InsertCalendarEvent`) reads local `CalendarContract.Instances` entries for today + next 7 days, opens an IME-local agenda picker, and inserts the selected event title + date/time. `READ_CALENDAR` is requested only after explicit tap.
- **v1.8.63** (2026-05-17) — Phase C3: bundled SwiftKey High Contrast (AAA) and Aurora Animated themes, with Snygg stylesheet tests and a reduced-motion-aware GenericShape aurora background.
- **v1.8.62** (2026-05-17) — Phase C1: split-keyboard renderer wire-up with gutter-aware layout, viability gating, and touch-hit suppression inside the gutter.
- **v1.8.61** (2026-05-17) — Phase B2: quick-prediction-insert threshold tuning with a configurable weighted-confidence floor and aligned plain-space suppression.
- **v1.8.60** (2026-05-17) — Phase B1: multilingual cold-start sentence/phrase priors plus top-1,000 Zipf seed overlays for CS/DE/ES/FR/IT/PT.
- **v1.8.59** (2026-05-17) — Phase D3: Typing Stats now shows current-week accepted corrections versus last week, backed by bounded weekly metadata in `CorrectionOutcomePriors`.
- **v1.8.58** (2026-05-17) — Phase D2: generic task-creation quick action (`QuickAction.InsertTask`). On-device replacement for SwiftKey's Microsoft-To-Do tile via `Intent.ACTION_SEND` chooser; works with Tasks.org / OpenTasks / Google Tasks / Joplin / Notion / Markor. `SensitiveFieldGuard` gate.
- **v1.8.57** (2026-05-17) — Phase C2: SwiftKey "Modes → Arrow keys" parity via new `BottomRowPreset.Navigation` (← ↑ space ↓ → enter).
- **v1.8.56** (2026-05-17) — Phase B4: same-sentence language-switch hardening via geometric-decay weighted blend in `TrailingContextLanguageBlend`.
- **v1.8.55** (2026-05-17) — Phase B3: shared-spelling bilingual handling — sub-floor `0.30` confidence on one-locale candidates overwriting shared typed words.
- **v1.8.54** (2026-05-17) — Phase A3 codec primitive: encrypted-blob personal-dictionary export envelope (AES-256-GCM + PBKDF2-HMAC-SHA-256 at OWASP-2025's 600 000 iterations).
- **v1.8.53** (2026-05-17) — Phase A2: post-import confirmation + rollback dialog + wired `DictionaryImporter` into Settings UI.
- **v1.8.52** (2026-05-17) — SwiftKey migration outreach push: README banner + opening pitch lead with the 2026-05-31 cutoff, badge promoted, parity-roadmap permalink linked.
- **v1.8.51** (2026-05-17) — N14.3 + N14.4 Compose BOM + Gradle wrapper dependency-pin audits, with a new dependency-triage audit log.
- **v1.8.50** (2026-05-17) — N17.1 emoji-picker crash triage; root-caused to `Paint.hasGlyph("")` and closed with three defensive filters.
- **v1.8.49** (2026-05-17) — N15.3 Smart Edit voice REMOVE_ITEM_FROM_LIST: new parameterised voice-command type that excises a named item from the dictated buffer mid-stream.
- **v1.8.48** (2026-05-17) — Adversarial-input + lifecycle hardening pass across the SwiftKey JSON importer, MCP daemon bridge, IME service teardown, voice-model install, ZIP extraction, and DB cursor handling.
- **v1.8.47** (2026-05-16) — N1.4 FUTO swipe-trace replay and benchmark harness.
- **v1.8.46** (2026-05-16) — SwiftKey `swiftkey-cloud.json` import parser ahead of the 2026-05-31 account retirement. New `DictionaryImportFormat.JSON` + tolerant `parseSwiftKeyJson`.
- **v1.8.45** (2026-05-16) — Android 17 IME-visibility restore across configuration changes.
- **v1.8.44** (2026-05-16) — Long-press popup guard on password fields (`KeyVariation.PASSWORD`).
- **v1.8.43** (2026-05-16) — Roborazzi plugin unblocked at 1.55.0; visual-regression CI step added.
- **v1.8.42** (2026-05-16) — Kotlin 2.3.20 → 2.3.21 bug-fix bump.
- **v1.8.41** (2026-05-16) — Auto-return to letter keyboard after apostrophe in symbols panel.
- **v1.8.40** (2026-05-16) — Per-daemon enable / disable for the MCP bridge in Settings.
- **v1.8.35–v1.8.39** — Full MCP daemon bridge: AIDL surface, AndroidMcpClient, per-daemon bind lifecycle, discoverer, IME-startup wire-up, Settings UI.
- **v1.8.34** — Macrobenchmark trace instrumentation across six production hot paths.
- **v1.8.31–v1.8.33, v1.8.79** — Honeycomb hex renderer foundation (`HoneycombHexShape` + `HoneycombHexButton` + `HoneycombKeyboardRow` + `HoneycombLayoutLoader`) and production `TextKeyboardLayout` wire-up.
- **v1.8.0–v1.8.30** — Smart-compose / inline-translation router stack, KenLM reader pipeline, 63-script transliteration build-out, addon scaffold sweep, SwiftKey-parity slices.
- **v1.7.x** — Multilingual hot-switch, bigram + trigram next-word, adaptive touch, SymSpell d1+d2, Flow Through Space, encrypted personal dictionary.
- **v1.6.0** — Personal-learning dictionary + 117k SCOWL English + SwiftKey design tokens.
- **v1.5.0** — FUTO Voice Input integration (replacing Google Speech Recognizer).

Older version details are summarized in the release bullets above and on GitHub Releases.

## Contributing

SwiftFloris welcomes focused contributions in themes, dictionary packs,
transliteration tables, performance work, bug fixes, accessibility, and docs.
Before opening a PR, keep the base-app invariants intact: no network permission, no telemetry, no account
binding, Apache-2.0-compatible `:app` code, and no closed-source blobs.

## Troubleshooting

### Gesture typing not working?

See Multilingual Gesture Typing. Gesture typing currently uses the bounded statistical engine for EN / DE / ES / FR / IT / PT; the neural / open-glide path is gated on the HeliBoard NLnet release.

### Voice input unavailable?

See FUTO Voice Input Troubleshooting. SwiftFloris does not record audio itself; live dictation hands off to the user-installed FUTO Voice Input app or another enabled external voice keyboard. The in-app Whisper/Vosk catalog is preview-only until the local recognizer runtime ships.

### Keyboard crashes on emoji insertion?

Root-caused in **v1.8.50** ([GitHub issue #1](https://github.com/SysAdminDoc/SwiftFloris/issues/1)). The trigger was `Paint.hasGlyph("")` aborting the palette render whenever an empty-value `Emoji` reached the initial filter pass. Three defensive filters landed at the palette, history-mapping, and asset-loader layers. If you still see this on v1.8.50+ please attach the device model, Android build, ROM, and a logcat capture to the issue.

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

🚀 **Active development.** Current release: **v1.9.53** (2026-06-25). The SwiftKey account export window closed on **2026-05-31**; local/on-device migration paths remain documented above.

---

## Quick Links

| Resource | Link |
|----------|------|
| **GitHub** | https://github.com/SysAdminDoc/SwiftFloris |
| **Issues** | https://github.com/SysAdminDoc/SwiftFloris/issues |
| **Releases** | https://github.com/SysAdminDoc/SwiftFloris/releases |
| **FUTO Voice** | https://voiceinput.futo.org/ |
| **FlorisBoard upstream** | https://github.com/florisboard/florisboard |

**Made for privacy and offline-first computing.**
