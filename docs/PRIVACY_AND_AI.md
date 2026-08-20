# Privacy and AI in SwiftFloris

**Last updated:** 2026-06-04
**EU AI Act Article 50 compliance horizon:** 2 August 2026

This document explains every AI/ML surface in SwiftFloris, what it does,
where it runs, and what data it sees. It is the structured counterpart to
the [Threat Model](THREAT_MODEL.md) and [Security](SECURITY.md) docs.

The headline:

> **All AI/ML processing in SwiftFloris happens on this device. No data
> leaves the device. No vendor accounts. No telemetry. The
> [`verifyNoInternetPermission`](../app/build.gradle.kts) Gradle task
> fails the build if any manifest declares a permission outside the explicit
> enrollment allowlist in `app/src/main/config/trust-capabilities.json` or
> SwiftFloris's signature-protected permission namespace.**

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
  privacy policy).
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

- **What runs.** The live path is a hand-off to the external
  **FUTO Voice Input** app (Source-First licensed,
  voiceinput.futo.org) or another enabled Android voice keyboard. FUTO
  runs Whisper locally on your phone; SwiftFloris hands the dictation
  session over and receives final transcript text. The in-app
  Whisper/Vosk route selector and model catalog are preview-only until
  a local recognizer runtime ships.
- **Where.** FUTO runs recognition on this device. SwiftFloris itself
  does not request `RECORD_AUDIO`; the external voice keyboard owns
  microphone access and its own privacy boundary.
- **Data seen.** SwiftFloris does not see microphone audio. The
  external voice keyboard sees microphone audio for the duration of a
  dictation session.
- **Data sent.** SwiftFloris sends no audio or transcript to the
  network. External voice keyboards have their own privacy policy.
- **Off switch.** Remove the voice key/bottom-row preset or disable the
  external voice keyboard. SwiftFloris works without voice.

### 2.6 Inline translation

- **What runs.** Facade + cache + language-pack manager (in tree, at
  [`ime/translate/`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/translate)).
  No Bergamot runtime addon currently ships. The production registry stays on
  its unavailable provider, so the translation action cannot produce a
  translation until a compatible local runtime is implemented and enrolled.
- **Where.** The facade runs on this device only. There is no cloud translator
  and no delivered local translation engine.
- **Data seen.** The in-process facade receives the text fragment selected for
  translation; no external translation provider currently receives it.
- **Data sent.** Nothing leaves the device.
- **Off switch.** The keyboard's translation surface is already no-op while
  no runtime is bound.

### 2.7 Smart Compose (ghost-text continuation)

- **What runs.** The opt-in `HeuristicSmartComposeProvider` uses the local
  personal trigram/bigram stores and cold-start priors. The optional
  model-backed Smart Compose runtime does not currently ship; LiteRT-LM and
  model descriptors are contract surfaces only.
- **Where.** On this device only. **No cloud LLM (no GPT, no Gemini API,
  no Claude API, no Bing Copilot).**
- **Data seen.** When the heuristic is enabled, it reads the preceding local
  word context and composing prefix. No model runtime receives the focused
  editor package name because no model provider ships.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Settings → Typing → Smart Compose disables the local
  heuristic surface.

### 2.8 Tone / Rewrite (professional / casual / polite)

- **What runs.** The rewrite router and no-op provider contract at
  [`ime/smartcompose/RewriteRouter.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/RewriteRouter.kt).
  No rewrite model provider currently ships.
- **Where.** On this device only.
- **Data seen.** The in-process router evaluates the request; the default
  provider returns unavailable and no model receives the selected text.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Rewrite remains unavailable while no provider is bound.

### 2.9 Adaptive emoji prediction

- **What runs.** `EmojiSuggestionProvider` blends bundled-keyword weight +
  custom-tag weight to surface emoji on relevant typed words. Learns
  your most-used emoji per word over time (Adaptive Emoji).
- **Where.** On this device only.
- **Data seen.** Which emoji you pick after which typed word.
- **Data sent.** Nothing leaves the device.
- **Off switch.** Settings → Media → Emoji predictions.

### 2.10 Stylus handwriting recognition

- **What runs.** Pen-down → pen-up polyline capture plus the default no-op
  stroke-recognizer facade
  ([`ime/handwriting/`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/handwriting/)).
  No handwriting recognizer addon currently ships.
- **Where.** Stroke capture runs on this device; there is no delivered
  recognizer engine.
- **Data seen.** The in-process facade receives pen-stroke coordinates and
  timing, then returns no recognition.
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
- **Discovery hint.** The one-time Smartbar hint counts distinct editor apps
  in memory only. SwiftFloris persists the hint state, not the package names.
- **Off switch.** Settings → Theme → "Tint to active app's icon"
  (default **off** — privacy-by-default even though no extra permission
  is required).

### 2.12 MCP daemon bridge

- **Not running in this release.** No keyboard action dispatches through MCP yet, so
  `FlorisImeService` starts no MCP lifecycle and pins both registries empty: starting
  the keyboard cannot discover or bind a daemon, and enabling one in Settings starts
  nothing. Settings → MCP daemon bridge says so on the screen itself. Trust decisions
  and per-daemon settings are still saved, and apply in the release that adds the first
  audited action. The rest of this section describes what the bridge does when it is
  live. `McpBindingPolicy` is the single flag that governs this.
- **What runs.** AIDL local-binder bridge to user-installed MCP (Model
  Context Protocol) daemons on the same device. The IME never invokes
  a network socket; it binds an Android service exported by the daemon app.
- **Where.** On this device only. **Local Android `bindService` +
  AIDL.** Per-daemon enable/disable in Settings → MCP daemon bridge.
  Per-tool allowlist gate in dispatch router.
- **Data seen.** Your selected text plus any context fields the
  invoked tool's JSON schema requires.
- **Data sent.** Sent **to the on-device daemon** the user explicitly
  installed and enabled. SwiftFloris screens a daemon package's requested
  permissions against an **allowlist** before certificate trust, registration,
  or binding, even when Android has not granted them: a daemon may hold only
  SwiftFloris's own signature permissions plus a short list of permissions that
  cannot move data off the device. Network permissions are rejected by name,
  and so is everything else outside the allowlist — including transports such
  as SMS, Bluetooth and nearby-devices that need no `INTERNET` permission, and
  any permission a future Android release adds. The separate
  daemon remains a privacy boundary for its other permissions and behavior,
  even though the keyboard-to-daemon transport is local Binder.
- **Off switch.** Settings → MCP daemon bridge → Disable.

### 2.13 Personal dictionary + learning

- **What runs.** Words you've typed are persisted in a SQLCipher-encrypted
  Room database, ranked into your future suggestions. Personal bigram +
  trigram stores feed n-gram completion.
- **Where.** On this device only. The database passphrase is generated locally
  with `SecureRandom` and stored in an app-private preferences file, encrypted
  with Tink `Aead` under an AES-256-GCM key that Android Keystore holds and
  will not export (`FlorisUserDictionaryEncryption.kt`,
  `TinkStringPreferenceCrypto.kt`). The passphrase itself is not in Keystore;
  the key that wraps it is.
- **Data seen.** Every word you type, except in password fields and
  `IME_FLAG_NO_PERSONALIZED_LEARNING` editors.
- **Data sent.** Nothing leaves the device. Backup rules exclude the
  encrypted DB from both Android cloud backup and device-to-device transfer
  because the Keystore-protected wrap key is non-exportable. To migrate
  learned words to a new device, use Settings → Personal dictionary → Export.
- **Off switch.** Settings → Typing → Learn from typing.

The optional Android system user dictionary is a separate shared provider. SwiftFloris
may read it for suggestions and show its entries in Settings, but the in-app system
dictionary screen is read-only: SwiftFloris never adds, edits, deletes, or imports
rows into that provider. Use Android's system dictionary settings for those changes.

---

## 3. The cross-cutting privacy contract

Every surface above is subject to:

- The **no-`INTERNET`** invariant (build gate).
- The **`SensitiveFieldGuard`** check at every addon dispatch site — sensitive
  fields (password / numeric-PIN / no-personalised-learning) return a safe
  no-result before any AI provider is asked.
- The **request-scoped suggestion privacy snapshot** — `NlpManager.suggest`
  freezes incognito, no-personalised-learning/editor sensitivity, suggestion
  enabled flags, offensive-content preference, and emoji candidate limits before
  async provider work starts, so delayed candidate generation cannot borrow
  privacy state from a later field or toggle.
- The **`FLAG_SECURE`** window flag on password / visible-password /
  web-password fields and while incognito is active. Dynamic incognito toggles
  re-apply the policy immediately, so the keyboard itself is excluded from
  screenshots and screen recordings during private typing.
- The **personal-dictionary isolation contract** is exercised on the attached
  Android device. `PersonalDictionaryManagerRuntimeTest` proves that the
  `learnWord` path writes only to the app-private Room store, leaves the system
  `UserDictionary` DAO absent, and honors the Settings preference, so shared
  provider writes cannot return silently.
- The **personal-dictionary backup exclusion** — encrypted DB is excluded
  from both cloud backup and device-to-device transfer (Android Keystore
  wrap key is non-portable).
- The **portable clipboard-backup boundary** — selecting any clipboard
  section requires a passphrase-encrypted, versioned AES-GCM `.sfbak`
  envelope. Authentication and archive validation complete before live data
  changes; failed or cancelled restores reapply an app-private recovery
  snapshot. Earlier plaintext ZIP archives remain an explicit warned
  migration path.

All of the above is pinned by tests and gates, not promises.

---

## 4. What SwiftFloris does NOT do

To prevent re-litigation, here is the explicit non-list (see
the privacy policy for the full rationale):

| What | Why no |
|---|---|
| Cloud sync of personal LM | §1 no-network |
| Microsoft / Google / any vendor account | §1 |
| Federated learning gradients uploaded anywhere | §1 |
| Cloud rewrite / Copilot / Gemini API / Bing | Cloud + account-bound |
| Cloud translator (MS / Google / DeepL) | Cloud — Bergamot addon is the local replacement |
| Tenor / Giphy GIF search | Cloud + telemetry — bundled local sticker packs are the offline equivalent |
| Cloud Clipboard sync via vendor | §1 — Next-5 CRDT over Syncthing is the local replacement |
| OneDrive learned-words backup | §1 — personal-dictionary export to plain CSV/combined-list or passphrase-encrypted `.sfexp` is the local replacement |
| In-keyboard ads / sponsored content | Trust posture |
| Closed-source `libjni_latinimegoogle.so` blob | Audit posture |
| MediaPipe LLM Inference (deprecated by Google) | Use LiteRT-LM addon path instead |
| Self-update (in-app APK download + install) | Supply-chain risk — Obtainium / F-Droid / IzzyOnDroid handle update orchestration |

---

## 5. Verifying the no-network claim yourself

Three independent ways to audit the no-network promise:

1. **`aapt dump permissions` against the installed APK** — should list
   `android.permission.VIBRATE`, `android.permission.POST_NOTIFICATIONS`,
   `android.permission.READ_CALENDAR`, and
   `io.github.sysadmindoc.swiftfloris.permission.BIND_MCP`. Crucially:
   no `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, or Wi-Fi
   network permission.
2. **The local release evidence log** — `scripts/release-evidence.ps1` runs
   `:app:verifyNoInternetPermission` and fails if any `AndroidManifest.xml`
   declares a permission outside the enrollment allowlist.
3. **The merged manifest** — build `:app:assembleRelease` and inspect
   `app/build/intermediates/merged_manifest/release/AndroidManifest.xml`.

---

## 6. EU AI Act Article 50 compliance notes

Article 50 of the EU AI Act (effective from **2 August 2026**) requires
that providers of AI systems intended to interact directly with natural
persons:

1. Inform users that they are interacting with an AI system, **at the
   first interaction**.
2. Mark AI-generated synthetic content (text/audio/image/video) in a
   machine-readable format.

SwiftFloris's response (shipped in the app UI in v1.8.66):

- This file is the **first-interaction explainer surface**. The
  first-run flow links here once; Settings → About → "AI features in
  this keyboard" links here always.
- No model-backed Smart Compose, rewrite, or translation runtime currently
  ships. The delivered heuristic Smart Compose path emits an explicitly
  labelled suggestion candidate and never commits without a tap or
  swipe-space action. A future model-backed provider must preserve that
  disclosure and explicit-commit contract.

For users in the EU, the on-device-only posture means no cross-border
data transfer. GDPR territorial scope therefore applies to the keyboard's
local processing only; nothing leaves the device.

---

## 7. Pointers

- [README](../README.md) — front door
- [Architecture & Stack](../README.md#architecture--stack) — stack and module map
- [ROADMAP.md](../ROADMAP.md) — full project plan
- [docs/THREAT_MODEL.md](THREAT_MODEL.md) — attacker scenarios + defenses
- [docs/SECURITY.md](SECURITY.md) — release-time security + dep scanning
- [docs/REPRODUCIBLE_BUILDS.md](REPRODUCIBLE_BUILDS.md) — toolchain pin matrix
- [README keyboard migration](../README.md#keyboard-migration) — 2026-05-31 migration paths
