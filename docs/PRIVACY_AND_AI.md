# Privacy and AI in SwiftFloris

**Last updated:** 2026-05-17
**EU AI Act Article 50 compliance horizon:** 2 August 2026

This document explains every AI/ML surface in SwiftFloris, what it does,
where it runs, and what data it sees. It is the structured counterpart to
the [Threat Model](THREAT_MODEL.md) and [Security](SECURITY.md) docs.

The headline:

> **All AI/ML processing in SwiftFloris happens on this device. No data
> leaves the device. No vendor accounts. No telemetry. The
> [`verifyNoInternetPermission`](../app/build.gradle.kts) Gradle task
> fails the build if any `INTERNET`, `ACCESS_NETWORK_STATE`,
> `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, or `CHANGE_WIFI_STATE`
> permission is declared anywhere under `app/src/`.**

This is enforced by build gate, not just by marketing.

---

## 1. Why this document exists

Three forces converged on the need for a single explainer:

1. **EU AI Act Article 50** transparency duties apply from **2 August
   2026**. Any AI-assisted feature that interacts directly with users
   must inform the user at first interaction. SwiftFloris ships next-word
   prediction, glide-typing classification, on-device voice transcription,
   on-device translation, and a smart-compose ghost-text surface — every
   one of these is in scope.
2. **2026-05-31 SwiftKey account retirement** is funneling users who
   actively cared about their typing data to alternative keyboards.
   Those users want a concrete answer to "what does this keyboard do
   with my words?" — not a one-line "no telemetry" footer.
3. **Industry pattern** — Apple Intelligence, Samsung Galaxy AI, and
   Microsoft Copilot have all standardized on per-feature "AI
   processing disclosure" surfaces (App Store guideline 5.1.2(i) in
   November 2025 cemented this for iOS). Android keyboards are next.

This document is the persistent explainer surface; SwiftFloris's
first-run flow links here, and Settings → About → "AI features in this
keyboard" links here.

---

## 2. The AI/ML surfaces — per-feature inventory

Each row lists: **what runs**, **where it runs**, **what data it sees**,
**what it sends to anyone else**, **how to turn it off**.

### 2.1 Next-word and next-phrase prediction

- **What runs.** A heuristic ranker over the SCOWL English dictionary +
  117 k-word custom additions, plus personal-bigram and personal-trigram
  stores learned from your typing, plus an instant-remember overlay that
  promotes freshly-typed words.
- **Where.** On this device only. The ranker lives in
  [`ime/nlp/NlpManager.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt)
  and [`ime/nlp/latin/LatinLanguageProvider.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt).
- **Data seen.** The active text field's preceding words. Never
  password fields (gated by `KeyVariation.PASSWORD`), never editors
  flagged `IME_FLAG_NO_PERSONALIZED_LEARNING`.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Settings → Typing → Suggestions. The keyboard works
  without predictions.

### 2.2 Glide / swipe typing

- **What runs.** Statistical classifier over bounded EN/DE/ES/FR/IT/PT
  glide vocabularies (per-language; ~80+ frequency, ≤24 length, ≤120k
  words per language). The classifier in
  [`ime/text/gestures/StatisticalGlideTypingClassifier.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/StatisticalGlideTypingClassifier.kt).
- **Where.** On this device only. **No cloud lookup. No closed
  `libjni_latinimegoogle.so` swipe blob** (explicitly rejected — see
  [ROADMAP](../ROADMAP.md) §10).
- **Data seen.** Your finger's normalized x/y/t points on the keyboard
  surface during a glide.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Settings → Gestures → Glide typing.

### 2.3 Multilingual per-token language identification

- **What runs.** Compact per-language char-n-gram + common-word + prefix
  classifier across enrolled EN/ES/FR/DE/IT/PT subtypes (per
  [N2.1](../ROADMAP.md)). Feeds the SwiftKey-style three-slot prediction
  ranker so a bilingual sentence does not autocorrect into the wrong
  language.
- **Where.** On this device only. `ime/nlp/MultilingualTokenScorer.kt`.
- **Data seen.** The current word + last 4 trailing words from the
  active text field.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Settings → Localization → use single-language subtypes
  only.

### 2.4 Adaptive touch

- **What runs.** Per-subtype Welford-online per-key offset learner
  (`AdaptiveTouchModel`). Updates after every key press to improve
  spatial prediction in your specific hand position and posture.
- **Where.** On this device only.
- **Data seen.** Tap coordinates of every key you press.
- **Data sent.** Nothing leaves the device. Persisted locally and
  cleared on Settings → Typing → Reset adaptive touch model.
- **Off switch.** Settings → Typing → Adaptive touch.

### 2.5 Voice input

- **What runs.** Two paths:
  - **Preferred:** hand-off to the external **FUTO Voice Input** app
    (Source-First licensed, voiceinput.futo.org). FUTO runs Whisper
    locally on your phone; SwiftFloris hands the dictation session over
    and receives final transcript text.
  - **Fallback:** **bundled Vosk** streaming engine + locally-installed
    Whisper / Vosk models. RAM-aware model selector picks tiny.en
    (~75 MB), base.en (~140 MB), or Large-v3-Turbo INT8 (~800 MB) by
    device class.
- **Where.** On this device only. **No audio ever leaves the device.**
  SwiftFloris itself does not request `RECORD_AUDIO`; the FUTO app or
  the bundled engine holds it.
- **Data seen.** Microphone audio for the duration of a dictation
  session.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Settings → Voice input → disable. The keyboard works
  without voice.

### 2.6 Inline translation

- **What runs.** Facade + cache + language-pack manager (in tree, at
  [`ime/translate/`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/translate)).
  The actual translator is the **Bergamot WASM runtime** delivered as a
  separately-installed user-opt-in addon (L2.1a) using
  [`DavidVentura/firefox-translator`](https://github.com/DavidVentura/firefox-translator)
  as the JNI reference. Bergamot is MPL-2.0; models are Mozilla's
  Firefox translation pairs.
- **Where.** On this device only. **No cloud translator (no Microsoft
  Translator, no Google Translate, no DeepL).**
- **Data seen.** The text fragment you ask to translate.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Don't install the addon, or remove it. The keyboard's
  translation surface is no-op until an addon binds.

### 2.7 Smart Compose (ghost-text continuation)

- **What runs.** Facade + provider registry (in tree, at
  [`ime/smartcompose/`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/)).
  The actual completion engine is **Gemma 3 270M Q4 / FunctionGemma
  270M INT8** via **LiteRT-LM** delivered as a separately-installed
  user-opt-in addon (L1.1a). Default behavior with no addon installed:
  no completion suggestion ever appears.
- **Where.** On this device only. **No cloud LLM (no GPT, no Gemini API,
  no Claude API, no Bing Copilot).** LiteRT-LM is Google's deprecation
  successor to MediaPipe LLM Inference, the orchestration layer Gemini
  Nano uses on Chrome and Pixel Watch.
- **Data seen.** Your typing context (preceding text + composing prefix
  + focused-editor package name for per-app LoRA hot-swap).
- **Data sent.** Nothing leaves the device.
- **Off switch.** Don't install the addon, or remove it. Settings →
  Typing → Smart Compose toggles the surface even when the addon is
  installed.

### 2.8 Tone / Rewrite (professional / casual / polite)

- **What runs.** Same Gemma 3 instance as Smart Compose, invoked through
  the rewrite router at [`ime/smartcompose/RewriteRouter.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/RewriteRouter.kt).
  Gated on L1.1a.
- **Where.** On this device only.
- **Data seen.** Your selected text plus the tone-target prompt.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Same as Smart Compose.

### 2.9 Adaptive emoji prediction

- **What runs.** `EmojiSuggestionProvider` blends bundled-keyword weight +
  custom-tag weight to surface emoji on relevant typed words. Learns
  your most-used emoji per word over time (Adaptive Emoji).
- **Where.** On this device only.
- **Data seen.** Which emoji you pick after which typed word.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Settings → Media → Emoji predictions.

### 2.10 Stylus handwriting recognition

- **What runs.** Pen-down → pen-up polyline capture + stroke recognizer
  facade ([`ime/handwriting/`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/handwriting/)).
  Recognizer engine is delivered as a separately-installed user-opt-in
  addon. **Two SKU plan** (see SECOND_PASS_FINDINGS): Play-Store-only
  `addons/handwriting-mlkit/` using Google ML Kit Digital Ink, and
  F-Droid-eligible `addons/handwriting-tflite/` using an OSS CRNN.
- **Where.** On this device only.
- **Data seen.** Your pen-stroke coordinates and timing during a
  handwriting session.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Settings → Keyboard → Stylus handwriting (default off).

### 2.11 Per-app accent

- **What runs.** Extracts the dominant accent color from the active
  editor's app icon (`PerAppAccentResolver`) and applies it to keyboard
  surface elements.
- **Where.** On this device only.
- **Data seen.** The package name of the focused editor (the standard
  IME contract) and that app's icon bitmap.
- **Data sent.** Nothing leaves the device. **No `PACKAGE_USAGE_STATS`
  permission required** — the package name comes from the IME contract.
- **Off switch.** Settings → Theme → "Tint to active app's icon"
  (default **off** — privacy-by-default even though no extra permission
  is required).

### 2.12 MCP daemon bridge

- **What runs.** AIDL local-binder bridge to user-installed MCP (Model
  Context Protocol) daemons on the same device. The IME never invokes
  a network socket; daemons must declare local binding only.
- **Where.** On this device only. **Local Android `bindService` +
  AIDL.** Per-daemon enable/disable in Settings → MCP daemon bridge.
  Per-tool allowlist gate in dispatch router.
- **Data seen.** Your selected text plus any context fields the
  invoked tool's JSON schema requires.
- **Data sent.** Sent **to the on-device daemon** the user explicitly
  installed and enabled. Daemons themselves must be locally bound —
  they cannot themselves declare `INTERNET` and remain enrollable
  through the addon-enumerator's network-permission hard reject.
- **Off switch.** Settings → MCP daemon bridge → Disable.

### 2.13 Personal dictionary + learning

- **What runs.** Words you've typed are persisted in a SQLCipher-encrypted
  Room database, ranked into your future suggestions. Personal bigram +
  trigram stores feed n-gram completion.
- **Where.** On this device only. The encryption key is generated locally
  and held in Android Keystore.
- **Data seen.** Every word you type, except in password fields and
  `IME_FLAG_NO_PERSONALIZED_LEARNING` editors.
- **Data sent.** Nothing leaves the device. Backup rules exclude the
  encrypted DB from Android's cloud-backup paths because the Keystore-protected
  key is intentionally non-portable. Device-to-device transfer is allowed.
- **Off switch.** Settings → Typing → Learn from typing.

---

## 3. The cross-cutting privacy contract

Every surface above is subject to:

- The **no-`INTERNET`** invariant (build gate).
- The **`SensitiveFieldGuard`** check at every addon dispatch site — sensitive
  fields (password / numeric-PIN / no-personalised-learning) return a safe
  no-result before any AI provider is asked.
- The **`FLAG_SECURE`** window flag on password / visible-password / web-password
  fields — the keyboard itself is excluded from screenshots and screen
  recordings, so screen-recorder OCR cannot snipe credentials from the
  long-press alt-glyph popup.
- The **personal-dictionary isolation contract** — the `learnWord` path
  never references the system `UserDictionary.Words`. The
  `PersonalDictionaryIsolationTest` will fail if a future contributor
  breaks this.
- The **personal-dictionary backup exclusion** — encrypted DB cannot
  cross-device-transfer through Google's cloud backup.

All of the above is pinned by tests and gates, not promises.

---

## 4. What SwiftFloris does NOT do

To prevent re-litigation, here is the explicit non-list (see
[ROADMAP.md](../ROADMAP.md) §10 for the full rationale):

| What | Why no |
|---|---|
| Cloud sync of personal LM | §1 no-network |
| Microsoft / Google / any vendor account | §1 |
| Federated learning gradients uploaded anywhere | §1 |
| Cloud rewrite / Copilot / Gemini API / Bing | Cloud + account-bound |
| Cloud translator (MS / Google / DeepL) | Cloud — Bergamot addon is the local replacement |
| Tenor / Giphy GIF search | Cloud + telemetry — bundled local sticker packs are the offline equivalent |
| Cloud Clipboard sync via vendor | §1 — Next-5 CRDT over Syncthing is the local replacement |
| OneDrive learned-words backup | §1 — personal-dictionary export to CSV/JSON is the local replacement |
| In-keyboard ads / sponsored content | Trust posture |
| Closed-source `libjni_latinimegoogle.so` blob | Audit posture |
| MediaPipe LLM Inference (deprecated by Google) | Use LiteRT-LM addon path instead |
| Self-update (in-app APK download + install) | Supply-chain risk — Obtainium / F-Droid / IzzyOnDroid handle update orchestration |

---

## 5. Verifying the no-network claim yourself

Three independent ways to audit the no-network promise:

1. **`aapt dump permissions` against the installed APK** — should list
   only `VIBRATE` + `POST_NOTIFICATIONS` (and optionally
   `BIND_NOTIFICATION_LISTENER` if you've enabled the app-aware smartbar).
   Crucially: no `INTERNET`, no `ACCESS_NETWORK_STATE`, no `WiFi`.
2. **The CI build log** — every push runs `:app:verifyNoInternetPermission`
   and fails if any `AndroidManifest.xml` declares a network permission.
   GitHub Actions log is public.
3. **OSV-Scanner weekly cron** — runs against the full transitive
   dependency tree. If any dependency would silently bring in a network
   capability, the scan picks it up.

---

## 6. EU AI Act Article 50 compliance notes

Article 50 of the EU AI Act (effective from **2 August 2026**) requires
that providers of AI systems intended to interact directly with natural
persons:

1. Inform users that they are interacting with an AI system, **at the
   first interaction**.
2. Mark AI-generated synthetic content (text/audio/image/video) in a
   machine-readable format.

SwiftFloris's response:

- This file is the **first-interaction explainer surface**. The
  first-run flow links here once; Settings → About → "AI features in
  this keyboard" links here always.
- AI-generated synthetic content marking is **scoped only to the smart-compose
  addon path** (L1) — when an installed addon synthesizes a completion,
  the IME marks it as a "suggestion" candidate (visually distinct from
  literal typed text). The synthesized text is **never auto-committed**
  without an explicit user action (swipe-space or tap).
- The Bergamot translator addon (L2) treats the translated text as
  user-generated (the user is the source of the input fragment); the
  translation output is offered as a candidate, not a substitution.

For users in the EU, the on-device-only posture means no cross-border
data transfer. GDPR territorial scope therefore applies to the keyboard's
local processing only; nothing leaves the device.

---

## 7. Pointers

- [README](../README.md) — front door
- [PROJECT_CONTEXT.md](../PROJECT_CONTEXT.md) — fast onboarding
- [ROADMAP.md](../ROADMAP.md) — full project plan
- [docs/THREAT_MODEL.md](THREAT_MODEL.md) — attacker scenarios + defenses
- [docs/SECURITY.md](SECURITY.md) — release-time security + dep scanning
- [docs/REPRODUCIBLE_BUILDS.md](REPRODUCIBLE_BUILDS.md) — toolchain pin matrix
- [docs/MIGRATE_FROM_SWIFTKEY.md](MIGRATE_FROM_SWIFTKEY.md) — 2026-05-31 migration paths
