# Dataset / Model / Integration Review — 2026-05-17

Where the ROADMAP touches datasets, models, or external integrations.
Reviews each against current upstream state and re-checks license fitness.

---

## 1. Datasets currently bundled or referenced

| Asset | License | Path / Pin | Status |
|---|---|---|---|
| SCOWL English wordlist (~117 k words) | BSD-like, Apache-compatible | `app/src/main/assets/ime/dict/data.json` (merged); LICENSES/SCOWL-Copyright.txt | Shipped; current |
| LDNOOBW profanity exclusion list | CC-BY-4.0 | Used in dictionary-generation pipeline; attributed in NOTICE | Shipped; current |
| Zipf English frequency table (~1,000 words from wordfreq) | CC-BY-SA-4.0 | `assets/freq/en.tsv` (Next-3.2) | Shipped; tiny seed; full SUBTLEX extraction deferred to Next-10.3 addon |
| ColdStart next-word priors (English) | Apache-2.0 (FlorisBoard-derived curation) | `ColdStartNextWordPriors` | Shipped; cs/de/es/fr/it/pt overlays planned in Phase B1 |
| SwiftKey replay trace fixtures | Repo-internal | `swiftkey/replay/trace_replay_cases.jsonl` | Synthetic + hand-curated; B5 plans field-trace expansion |
| Bundled fonts (Nastaliq + Naskh fallback) | Bundled-with-fonts licenses | `app/src/main/assets/.../fonts/` (per `docs/FONTS.md`) | Shipped |
| 63-script transliteration tables | Apache-2.0 (curated in-repo) | `app/src/main/kotlin/.../ime/{indic,geez,bidi}/`+ runtime tables | Shipped (L5, L6 facades + tables) |

## 2. Datasets referenced (not yet bundled)

| Asset | License | Where ROADMAP places it | 2026-05-17 status |
|---|---|---|---|
| HeliBoard NLnet **open-glide dataset** | TBD (Volunteer-contrib, MIT pattern in HeliBoard data-gathering) | §6 N1.1 gate | **Not yet released** ~14 days before NLnet deadline. Library not yet released either. Base-case: slips past 2026-06-01 |
| FUTO MIT swipe-trace corpus (~1M traces) | MIT | §6 N1.4 — imported via `SwipeTraceImporter`; benchmark harness in `SwipeTraceBenchmark` | Importer + harness shipped v1.8.47; **device-side dataset download + benchmark run is the open work** |
| KenLM 24-language pre-trained models (edugp/kenlm) | LGPL chain — model itself is data, but the KenLM runtime is LGPL | §7 Next-3.1 (header reader shipped) | KenLM runtime is **LGPL-2.1+ = INCOMPATIBLE with `:app`**. Resolution: keep header parser in-`:app` (parser is original code), pin scoring path to an addon. See [SECURITY_AND_DEPENDENCY_REVIEW.md §4](SECURITY_AND_DEPENDENCY_REVIEW.md#4-license-compatibility-verification) |
| fastText `lid.176` quantized | Creative Commons (CC-BY-SA-3.0) | §6 N2.1 candidate alt-path | Not adopted; current N2.1 uses a custom char-n-gram classifier (`Apache-2.0 compatible by construction`) |
| CLD3 language detector | Apache-2.0 | §6 N2.1 candidate alt-path | Not adopted; same reason |
| Polish 2025 baseline dictionary pack | TBD | §7 Next-10.3 first addon | Pipeline + schema shipped; corpus extraction is external work |
| SwiftKey cloud JSON export | (User's own data; no license) | §A1/A2 importer | Importer shipped v1.8.46; tolerant parser handles three envelope shapes; **schema itself is not publicly documented by Microsoft** — tolerant walker is permanent |

## 3. Models currently referenced

| Model | License | Where ROADMAP places it | 2026-05-17 status |
|---|---|---|---|
| **Gemma 3 270M Q4 INT4** (~135 MB) | Gemma Terms (research+limited commercial; LiteRT-LM redist via `litert-community/...`) | §8 L1.1a addon | Compatible at the **addon** layer (not `:app`). LiteRT-LM runtime ships in addon, model loads at runtime. |
| **FunctionGemma 270M** (`litert-community/functiongemma-270m-ft-mobile-actions`) | Gemma Terms | **Not yet in ROADMAP** | **NEW: Jan 2026 release.** Structured function-calling + unified action/chat. Mobile Actions fine-tuning bumped action-call accuracy 58 % → 85 %. **Should be added** to ROADMAP §L1 as the more relevant variant for any agentic / tool-use Smart Compose. |
| **Whisper tiny.en / base.en / Large-v3-Turbo Q8** | MIT (whisper.cpp), CC-BY-NC-4.0 (some model weights) | §7 Next-2.1/2.3 voice catalog | RAM-aware selector + catalog + downloader UI shipped; runtime activation pending Next-2.4 device-side wiring (which v1.7.9 closed for streaming). |
| **Vosk small EN/ES/FR/DE/IT/PT** | Apache-2.0 | §7 Next-2.2/2.3 streaming fallback | Same selector + catalog; route preview + command-mode wiring done v1.7.9 |
| **Bergamot EN+ES / EN+FR / EN+DE** | MPL-2.0 | §8 L2.1 addon | Compatible. L2 facade shipped; addon delivery is the open work |
| **librime schemas** (luna_pinyin / jyutping / bopomofo / etc.) | various (some are MIT, some BSD-3) | §8 L3 addon | Compatible at addon layer |
| **ML Kit Digital Ink** (stylus) | ML Kit Terms (not OSS) | §7 Next-4.2a addon | Compatible only if user opts into the addon; F-Droid friction. ROADMAP correctly isolates this in `addons/handwriting-mlkit/` |
| **CleverKeys glide ONNX** (13 MB transformer) | **GPL-3.0** | Architectural reference only | **Cannot link.** Could re-train an Apache-2.0 model against the same architecture once a permissive dataset lands (HeliBoard NLnet target). |
| **NLLB-200 distilled-600M** | CC-BY-NC-4.0 | Not in ROADMAP | Reference for the broader offline-NMT landscape (RTranslator uses it). NC license rules it out for SwiftFloris in any tier. |

## 4. Integrations currently surfaced

| Integration | Surface | License | Status |
|---|---|---|---|
| **FUTO Voice Input** (handoff) | `VoiceInputSetupActivity` + Settings → Voice | FUTO Source-First (separate app) | Shipped since v1.5.0; manifest declares `<queries><package org.futo.voiceinput /></queries>` |
| **Bitwarden / KeePassDX / Proton Pass / 1Password / Aegis** (inline autofill) | `Smartbar.shouldShowInlineSuggestionsUi` + `InlineSuggestionsUi` | (consumed via `InlinePresentationSpec`) | Shipped v1.7.9; `docs/INLINE_AUTOFILL.md` matrix maintained |
| **Tasker / automation senders** | `TaskerActionReceiver` signature-protected | n/a (intent contract only) | Shipped v1.8.x; `docs/TASKER_INTEGRATION.md` |
| **MCP daemon bridge** | `IMcpDaemon.aidl` + `AndroidMcpClient` + `McpServiceConnectionManager` + `McpAndroidDiscoverer` + `McpDispatchRouter` + Settings → MCP daemon bridge | n/a (binder contract only; daemons supply their own licenses) | Shipped v1.8.35-1.8.40; per-daemon enable/disable v1.8.40; per-tool switches v1.8.x |
| **Calendar / Tasks quick-insert** (P9 / P10) | `QuickAction.InsertCalendarEvent` + `QuickAction.InsertTask` proposed | n/a (intent contract only) | **Not shipped; new gap surfaced by SWIFTKEY_PARITY_ROADMAP_2026-05-17.md** |
| **WebAuthn passkey adapter** (L10) | `PasskeyAdapter` interface + `PasskeyFieldDetector` | (adapter; addon implements the ceremony) | Detector + adapter shipped v1.8.x; Activity-bound Credential Manager ceremony in `addons/passkey-adapter/` (external work) |
| **Hardware-keyboard layouts** | `KlcLayoutParser` + `KeymanLdmlParser` + `HardwareKeyboardLayout` | MIT (Keyman engine); KLC is just a format | KLC + LDML parsers shipped; runtime mapper for live HW keyboards (Next-6.4b) is external work |
| **Syncthing CRDT pairing** (N5) | `PersonalDictionaryCrdt` + `PairingPayload` + `SyncChannel` + Settings → Sync | (no external service binding; channel pluggable) | CRDT model + merger + pairing payload + channel taxonomy + SAF folder + manual export all shipped v1.8.x |
| **Tenor / Giphy** | (rejected) | n/a | §10 reject — local sticker packs only |
| **Bing / Copilot / Gemini API** | (rejected) | n/a | §10 reject — MCP daemon bridge is the opt-in escape valve |

## 5. Coverage of dataset / model / integration concerns

| Area | Covered? |
|---|---|
| English autocorrect (dictionary + bigram + trigram + SymSpell) | ✅ shipped; calibration ongoing (B5) |
| Non-English Latin scripts (ES/FR/DE/IT/PT) | ✅ shipped with dict + Zipf; phrase priors in B1 |
| Bilingual subtype presets (EN+ES/FR/DE) | ✅ shipped v1.7.x; multilingual ranker hardened through HEAD |
| Indic / Brahmic transliteration (8 scripts) | ✅ shipped (1763-LOC `IndicTransliterator.kt`) |
| RTL Arabic / Persian / Urdu / Hebrew | ✅ shipped v1.8.2; normaliser + shaper + BiDi |
| Ge'ez (Amharic / Tigrinya) | ✅ shipped v1.8.x SERA transliterator |
| CJK Pinyin / Jyutping / Zhuyin / Mozc / Hangul | ⚠️ facade only; gated on librime addon (L3) |
| Voice (offline) | ✅ handoff + streaming fallback + model catalog + RAM-aware selector |
| Stylus handwriting | ⚠️ facade + toggle; recogniser ships in addon (Next-4.2a) |
| Inline translation | ⚠️ facade + cache + router + language-pack manager; Bergamot WASM addon outstanding (L2.1a) |
| Smart Compose / Tone | ⚠️ facade + provider boundary; gated on L1 LiteRT-LM addon |
| Per-app accent / theme | ✅ shipped (`PerAppAccentResolver` + `PerAppAccentController`) |
| Per-app smartbar profile | ✅ shipped (`SmartbarActionProfiles`: CHAT / CODE / EMAIL / PASSWORD) |
| Hardware-keyboard layout import | ⚠️ parsers shipped; runtime mapper outstanding |
| Personal-dictionary E2EE sync between user's own devices | ⚠️ CRDT shipped; transport implementation outstanding |

## 6. License-cleanliness audit (one screen)

Bundled assets, ranked by `:app` linking compatibility:

| Status | Items |
|---|---|
| ✅ Apache-2.0 / BSD / MIT / CC-BY in `:app` today | SCOWL, LDNOOBW (CC-BY-4.0), Zipf seed, FlorisBoard-derived assets, all SwiftFloris-original code |
| ✅ Compatible as addon (MPL/BSD/Apache) | Bergamot (MPL-2.0), librime main (BSD-3), Varnam libvarnam (MPL), KLC parser (MIT engine), Keyman engine (MIT) |
| ⚠️ Addon only, vendor-terms restrictive | ML Kit Digital Ink (Google ML Kit Terms — F-Droid friction); Gemma family (Gemma Terms — research + limited commercial) |
| ❌ **CANNOT link into `:app`** | KenLM (LGPL-2.1+); CleverKeys glide (GPL-3.0); librime-legacy (GPL); varnam-fcitx5 (GPL); FUTO source code (Source-First non-commercial CLA) |

**Material correction:** ROADMAP §7 Next-3.1 must explicitly state KenLM
JNI bring-up is an **addon-side step**, not an `:app` step. The
in-`:app` header-only parser is fine (it's original work parsing a public
format). Captured as item 4 in
[ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md).

## 7. Why this file isn't thinner

The project has substantial dataset / model / integration territory:

- Three planned LLM-class additions (Gemma 3 270M Q4, Bergamot, librime).
- A `RAM-aware Whisper model selector` shipped, with a real downloader UI.
- Six on-device language identifiers / classifiers.
- Cross-format hardware-keyboard import pipeline.
- WebAuthn / passkey injection contract.

All of these gain or lose viability with each external upstream release.
This file is the single place to check that status without re-reading the
340 KB ROADMAP.
