# SwiftKey Parity Research

> **SUPERSEDED — 2026-05-17.**
> Retained for audit/history only.
> Canonical SwiftKey parity planning now lives in `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`.
> Use `ROADMAP.md` and `PROJECT_CONTEXT.md` for current implementation state.
> Do not treat this file as the active plan.

**Date:** 2026-05-09
**Goal:** Make SwiftFloris indistinguishable from SwiftKey for end-users, **without** importing Microsoft account dependencies, Copilot, cloud telemetry, or any non-Apache-2.0 code.

---

## 1. How SwiftKey actually works

Four engines work together. Each is described in public sources but the engineering details are proprietary.

### 1.1 Neural language model (LSTM + attention, on-device)

- Replaced the original n-gram engine in 2015 (SwiftKey Neural Alpha). The whole product uses neural prediction since 2016.
- Core architecture: quantized LSTM with attention gates. Each word has a learned embedding; words with similar **grammatical role** (not meaning) share embeddings — so "Meet" sits near "met / connect / speak / chat" because all five fit the same syntactic slots.
- Predicts phrases the model has never seen before because the embedding-space generalizes.
- Modern claim: sub-15 ms inference on mid-tier ARM Cortex-A76, 25-MB model on device, GPU not required.

### 1.2 Adaptive touch / spatial model

- The single biggest reason SwiftKey *feels* accurate.
- For each user, the keyboard learns the actual tap distribution per key (mean offset, covariance) and **dynamically resizes the invisible hit-zones** so tapping where you actually tap maps to the key you intend.
- Combined with a **spatial backoff model** (ACM CHI 2013 paper from the SwiftKey team's contemporaries): falls back to a generic posture-aware model if there's not enough per-user data, then to a fully generic model if posture isn't identifiable.
- Position-based, not pressure-based — capacitive touchscreens give position + contact area, not force.

### 1.3 SHARK2 trace decoder + LM rescoring (Flow)

- For every dictionary word, an "ideal trace" is precomputed: a poly-line through the centers of its letter-keys.
- User's swipe trajectory is compared to ideal traces; top-N candidates by shape similarity are passed to the LM, which rescores them using prior words.
- **Flow Through Space:** the user can continue gliding *across* the space bar without lifting. When the next word starts, the LM has more context and can retroactively re-rank ambiguous short words (`is` → `it's` if the next word is `working`).
- Double-letters: pause briefly over the letter.
- Mode-free: tapping and gliding interleave without a setting flip.

### 1.4 Federated personalization

- Local fine-tuning on anonymized typing sessions (under 500 chars/session, deleted after 24h on-device).
- Encrypted, differential-privacy-noised gradients sent to Microsoft's aggregator; merged into the global model.
- This is the part that requires a Microsoft account post-2026-05-31 (the deadline that's pushing users off SwiftKey).

### 1.5 Other notable surface features

- Up to **5 simultaneously-active languages**, auto-detected per word from dictionary scoring; no manual switch.
- Microsoft account sync of learned vocab + clipboard.
- **Stats screen:** words learned, top emoji, accuracy delta.
- DALL-E "Designer" sticker creator (cloud).
- Copilot button (cloud, removed in 2025 then re-added in a different form).

---

## 2. What SwiftFloris already has

| Feature | Source |
|---|---|
| Tap typing, multi-layout (QWERTY/AZERTY/QWERTZ + locale) | Inherited FlorisBoard |
| Glide / gesture typing in 6 languages (EN/DE/ES/FR/IT/PT) | `StatisticalGlideTypingClassifier` |
| 117k SCOWL English dictionary (curated 50k freq 128–255 + 67k SCOWL freq 80–127, profanity-filtered via LDNOOBW) | v1.6.0 |
| 130-entry contraction autocorrect (two-tier SAFE / DICTIONARY_GATED) | v1.5.5 |
| Personal-learning dictionary (Room-backed, off-thread, incognito-gated) | v1.6.0 |
| Levenshtein-2 corrections (autocommit threshold 0.78) | v1.5.x |
| Auto-cap with sentence-end context (rejects `3.14`, `e.g.`, `U.S.A.`, ellipses) | v1.5.4 |
| Auto-space after punctuation (default-on) | v1.7.5 |
| SwiftKey-parity haptics (20 ms / 60 strength), Pure-theme palette, FontWeight.Medium glyphs | v1.7.x |
| Suggestion-tap haptic | v1.7.5 |
| Encrypted clipboard (AES-256-GCM) | upstream |
| FUTO Voice Input integration | v1.5.0 |
| Voice commands parser/executor | v1.5.x |
| 100 % offline, no INTERNET permission, build-time `verifyNoInternetPermission` gate | v1.7.0 |
| Word-edit ergonomics: swipe-left = delete word, space-bar swipe = cursor drag, height slider | v1.7.x |
| 48dp WCAG touch-target floor, FLAG_SECURE on password fields, signing fingerprint shown | v1.7.x |

---

## 3. Gap analysis

| # | Feature | Effort | Why it matters | Status |
|---|---|---|---|---|
| **A** | **Adaptive touch model** — track per-key tap offsets, resize hit-zones, with backoff to global model until enough data | Medium | The single biggest reason SwiftKey *feels* accurate. Cheap to ship; pure on-device. | **Building (v1.7.5)** |
| **B** | **Flow Through Space** — gesture continues across space bar; post-LM rescores ambiguous short words once next word starts | Medium | Closes most-named gap vs SwiftKey Flow. | Roadmap N12.4 |
| **C** | **Inline next-phrase prediction** (gray ghost-text) — show 2–3 word continuation, tab/swipe-right to accept | Medium | Smart Compose parity. Even an n-gram backbone delivers 60% of the perceived value before Gemma. | Roadmap N12.2 |
| **D** | **Multi-language hot-switch** — up to 5 active subtypes, auto-detected per word from dictionary scoring | Medium | SwiftKey's most-loved multilingual trick. | Roadmap N12.3 (overlaps N2 already on Now-tier) |
| **E** | **On-device neural LM** (Gemma 3 270M Q4 / RWKV-tiny via llama.cpp NDK) | Large | The big one. Already L1 in roadmap. | Roadmap L1 |
| **F** | **Local "federated-style" personalization** — train tiny adapter on personal-learning dict + recent n-gram counts | Medium | Federated server is off-thesis (offline-only) but the *local* half is. | Roadmap N12.5 |
| **G** | **Per-user typing stats screen** | Small | SwiftKey "Stats" parity, all-local. | Roadmap N12.6 |
| **H** | **Spatial-model debug overlay** (dev mode) — heatmap of taps vs key centers | Small | Self-tuning A. | Roadmap N12.1 (folded into A) |

---

## 4. Build order

1. **A — Adaptive touch model** (this release). No external deps; biggest perceived-quality jump per LOC; sets up data for B/F.
2. **C — Inline phrase prediction (ghost-text)** (this release). Reuses existing `LatinDictionarySuggester` n-gram for next-word; no new model needed.
3. **D — Multi-language hot-switch** (this release). Per-word language scoring; classifier already has per-language dictionaries.
4. **G — Stats screen** (this release). All data already exists in personal-dict + clipboard repositories; just a new Settings screen.
5. **B — Flow Through Space** (next release). Touches glide decoder; do after A so we have a better spatial prior.
6. **F — Local personalization adapter** (next release).
7. **E — Gemma 3 270M Q4** (later). Wait until 1–6 land so n-gram fallback is stable.

**Out of scope (explicit):** Copilot, DALL-E sticker creator, Microsoft account sync, federated server, any feature requiring `INTERNET` permission.

---

## 5. Sources

- [SwiftKey debuts world's first smartphone keyboard powered by neural networks (SwiftKey blog)](https://blog.swiftkey.com/swiftkey-debuts-worlds-first-smartphone-keyboard-powered-by-neural-networks/)
- [How does SwiftKey predict your next keystrokes? (Medium)](https://medium.com/@curiousNupur/how-does-swiftkey-predict-your-next-keystrokes-b048ef67267d)
- [SwiftKey improves Android predictions with neural networks (TNW)](https://thenextweb.com/news/swiftkey-improves-its-android-keyboard-predictions-with-neural-networks)
- [What is Flow and how do I enable it (Microsoft Support)](https://support.microsoft.com/en-us/topic/what-is-flow-and-how-do-i-enable-it-with-microsoft-swiftkey-keyboard-3dd7b33e-4caf-4e05-afc1-74cf82df80ba)
- [LLM-Powered Text Entry Decoding and Flexible Typing on Smartphones (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC12723528/)
- [Microsoft SwiftKey (Wikipedia)](https://en.wikipedia.org/wiki/Microsoft_SwiftKey)
- [Making touchscreen keyboards adaptive to keys, hand postures, and individuals (ACM CHI 2013)](https://dl.acm.org/doi/10.1145/2470654.2481384)
- [Personalize typing with Microsoft SwiftKey (Microsoft Support)](https://support.microsoft.com/en-us/swiftkey-keyboard/how-do-i-personalize-my-typing-with-microsoft-swiftkey-keyboard)
- [SwiftKey vs Gboard vs FlorisBoard 2025 (AirDroid)](https://www.airdroid.com/file-transfer/gboard-vs-swiftkey/)
- [FlorisBoard GitHub](https://github.com/florisboard/florisboard)
