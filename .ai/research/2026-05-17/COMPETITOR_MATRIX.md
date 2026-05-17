# Competitor Matrix — Android Keyboards (May 2026)

**Research date:** 2026-05-17
**Method:** WebSearch + WebFetch over each project's repo, release page, and
2025-2026 press coverage, with a fifth-pass refresh for LeanType and
dependency-adjacent competitor updates. Items marked **unverified** where
one source returned a year that could not be cross-confirmed.

This matrix exists to keep SwiftFloris's positioning honest: every gap
SwiftFloris closes against SwiftKey/Gboard should be cross-checked against
the OSS field so we don't claim "only OSS keyboard with X" when X already
ships somewhere else.

---

## 1. OSS competitors

### 1.1 HeliBoard (Helium314/HeliBoard)

- **License:** GPL-3.0 (with Apache-2.0 / CC-BY-SA components)
- **Latest:** v3.9, 2026-03-29 ([Releases](https://github.com/Helium314/HeliBoard/releases))
- **Activity:** ~2,351 commits, 684 open issues, 39 open PRs, 5.2k stars; **very active**
- **Distribution:** F-Droid, IzzyOnDroid, GitHub Releases (no Play)
- **NLnet open-glide status (issue #2226):** R&D funded Jun 2025 → Jun 1 2026. v3.7 shipped active gesture-data gathering; v3.8/v3.9 added bug fixes + restore. **Passive mode "mostly ready, still tuning,"** library + dataset still unreleased as of 2026-04-25.
- **Features SwiftFloris doesn't have:**
  - User-supplied n-gram dictionary downloader UI (HeliBoard's killer ecosystem feature)
  - HCESAR Brazilian layout (niche but shipped)
  - Predictive back-gesture full support
  - Gesture-data-gathering opt-in research feed (Active + passive modes)
  - Multiple already-imported n-gram packs (currently must be side-loaded into SwiftFloris)
- **Strategic implication:** HeliBoard is the closest OSS peer and the de-facto AOSP-LatinIME successor. SwiftFloris differentiation is the SwiftKey-parity ranker stack, the encrypted personal dict, the MCP bridge, and the no-INTERNET CI gate. The NLnet swipe library is **the** integration target — and the base-case timeline now slips past 2026-06-01.

### 1.1a LeanType (LeanBitLab/LeanType)

- **License:** GPL-3.0 — cannot vendor into `:app`
- **Latest:** v3.7.9, 2026-05-17 ([Releases](https://github.com/LeanBitLab/LeanType/releases/tag/v3.7.9))
- **Positioning:** Active HeliBoard fork with AI-assisted proofreading /
  translation packaging.
- **Distribution model:** Standard APK (cloud providers + `INTERNET`),
  Offline APK (no `INTERNET`, manual offline ONNX model setup), Offline
  Lite APK (no `INTERNET`, no AI).
- **Features SwiftFloris should study:**
  - Offline AI model onboarding and user expectations.
  - How users understand "offline" vs "offline lite" capability splits.
  - Proofreading / translation surfaces that stay useful without a
    vendor account.
- **Strategic implication:** LeanType validates the market demand for an
  "offline AI keyboard" even though its GPL code cannot be reused. The
  SwiftFloris response should be stronger: keep the base APK no-network
  and express AI capability through optional signed addon APKs instead
  of separate `INTERNET`/no-`INTERNET` base flavors.

### 1.2 AnySoftKeyboard

- **License:** Apache-2.0
- **Latest:** v1.13-r1, **2025-02-08** (last 2026 release year is unverified; treat as 2025)
- **Activity:** Active CI; 3.3k stars, 921 forks; Alpha/Beta/Stable channels
- **Distribution:** Play, F-Droid, GitHub
- **Features SwiftFloris doesn't have:**
  - Mature external **language-pack APK distribution model** (each language is its own Play/F-Droid app — directly informs SwiftFloris's Next-10.3 addon spec)
  - 17 years of layout & i18n contribution history
- **Strategic implication:** SwiftFloris's Next-10 plugin/addon spec is consciously informed by AnySoftKeyboard. Their voice path uses Google's `RecognizerIntent` (cloud-bound on most builds) — SwiftFloris's offline voice handoff is a real differentiator.

### 1.3 FUTO Keyboard (futo-org/android-keyboard)

- **License:** **FUTO Source First 1.1** — non-OSI, source-available, non-commercial, requires CLA. Cannot vendor into `:app`.
- **Latest:** v0.1.28, **2026-05-04**
- **Activity:** 2.5k stars, 658 open issues
- **Distribution:** keyboard.futo.org direct, GitHub mirror (no Play, no F-Droid main repo)
- **Features SwiftFloris doesn't have:**
  - On-device transformer next-word/correction model (TFLite)
  - SwiftKey migration doc on `docs.keyboard.futo.org/migration/swiftkeymigration` — but dictionary import from SwiftKey is "**not yet supported, in queue**" (verified 2026-05-17). SwiftFloris's v1.8.46+ importer is genuinely ahead here.
  - Pontoon localization platform
  - Traditional + Simplified Chinese (Pinyin / fuzzy / Double Pinyin / rudimentary stroke) — landed v0.1.28
  - Vietnamese Telex/VNI — landed v0.1.28
- **Strategic implication:** FUTO is the closest **AI** peer (transformer correction). Their license forbids vendoring; SwiftFloris must train its own or use the LiteRT-LM / Gemma path. FUTO's CJK landing tightens the urgency on SwiftFloris L3.

### 1.4 OpenBoard

- **License:** Apache-2.0
- **Latest:** v1.4.5, 2022-08-05 — **dormant** (403 open issues, no release in ~4 years)
- **Successor:** HeliBoard absorbed mindshare
- **Strategic implication:** None remaining; reference historically only

### 1.5 Unexpected Keyboard (Julow/Unexpected-Keyboard)

- **License:** GPL-3.0
- **Latest:** v2.0.3, 2026-05-06
- **Distribution:** F-Droid, IzzyOnDroid, GitHub
- **Features SwiftFloris doesn't have:**
  - Corner-swipe-per-key **chording layout** (4-direction swipe per key = 5 chars/key — power-user / Termux audience)
  - Excellent foldable support
  - Spell-check + autocorrect (newly added 2.0.0)
- **Strategic implication:** Different audience (terminal power users); not a direct competitor for SwiftKey defectors. Useful inspiration for the L9 "alt-layouts" tier.

### 1.6 Thumb-Key (dessalines/thumb-key)

- **License:** AGPL-3.0 — cannot vendor
- **Latest:** v5.1.8, 2026-05-01
- **Distribution:** F-Droid, IzzyOnDroid, Play, GitHub
- **Features SwiftFloris doesn't have:**
  - **3×3 grid swipe layout** (MessagEase-style)
  - Jetpack Compose-native rendering (SwiftFloris uses Compose too, but for Settings; the keyboard surface still has the FlorisBoard imperative-Canvas backbone)
  - Configurable per-direction swipe actions
  - Private clipboard separated from history
- **Strategic implication:** Reference for the "one-handed / foldable" Next-7 tier. Different audience.

### 1.7 Fossify Keyboard

- **License:** GPL-3.0
- **Latest:** v1.9.1, 2026-02-02
- **Strategic implication:** Minimalist Simple-Keyboard successor; fewer features than SwiftFloris. Not a real competitor.

### 1.8 fcitx5-android

- **License:** LGPL-2.1
- **Latest:** v0.1.2, 2025-11-01
- **Distribution:** Play, F-Droid, GitHub, Jenkins CI
- **Features SwiftFloris doesn't have:**
  - **Runtime plugin architecture for input methods** — reference for SwiftFloris Next-10 / L7
  - **Floating candidate panel for physical-keyboard mode** — reference for Next-6.4 hardware-keyboard work
  - Pinyin / Wubi / Cangjie / Japanese / Korean / Vietnamese / Thai engines
  - Material You dynamic color (Android 12+)
- **Strategic implication:** The architectural reference for L3 (librime JNI). SwiftFloris's `ime/cjk/` facade already mirrors fcitx5's surface.

### 1.9 Trime (osfans/trime)

- **License:** GPL-3.0
- **Latest:** v3.3.10, 2026-05-01
- **Features SwiftFloris doesn't have:** full librime engine, OpenCC traditional/simplified, dialect preservation packs (Wu, Cantonese, Min, ...)
- **Strategic implication:** SwiftFloris has **zero** CJK story today (facade only, L3 not bound). Trime is the live OSS reference.

### 1.10 CleverKeys (tribixbite/CleverKeys)

- **License:** GPL-3.0 — cannot vendor
- **Latest:** v1.4.0, 2026-04-26
- **Distribution:** F-Droid, GitHub, Obtainium
- **Features SwiftFloris doesn't have:**
  - **13 MB custom transformer ONNX glide model** with sub-200 ms latency on XNNPACK
  - **208 short-swipe actions** (gesture → action mapping)
  - Offline GIF browser
  - 35+ themes
  - Public training code + dataset (CleverKeys-ML)
  - Regex search in clipboard
  - Todos / tags on clipboard items
- **Strategic implication:** **CleverKeys is doing what SwiftFloris N1 is gated on.** The roadmap currently treats it as "architecture reference"; reality is that the working artifact is in F-Droid. SwiftFloris cannot link it (GPL), but can train an Apache-2.0 equivalent against the same architecture once HeliBoard NLnet dataset (or any permissive set) lands.

### 1.11 FlorisBoard upstream (florisboard/florisboard)

- **License:** Apache-2.0
- **Latest:** **v0.6.0-alpha02, 2025-01-23.** No alpha03 in 16+ months. Last stable v0.5.2 (2025-11-28).
- **Status:** v0.6 milestone re-scoped — glide + predictions pushed to v0.7 (public beta target)
- **Features SwiftFloris doesn't have:**
  - **FlorisBoard Addons marketplace** (`beta.addons.florisboard.org`) — the only OSS keyboard addon store
  - Snygg v2 engine refresh (alpha02)
  - CLDR 48 / Emoji 17 bump
  - Stylus fix for text keys
- **Strategic implication:** SwiftFloris is **lapping a stalled upstream**, not drifting from a moving target. The "Upstream drift" framing in `ROADMAP.md` §4 should be softened to reflect this. The Addons marketplace is a strategic prize — SwiftFloris's Next-10.3 addon spec is already Floris-compatible.

### 1.12 Simple Keyboard / Sayboard / WhisperInput

- Simple Keyboard: minimalist; not a competitor.
- Sayboard: voice-only, last release Aug 2024. SwiftFloris's FUTO handoff + Vosk fallback covers this.
- WhisperInput: voice-only build-it-yourself; not a real product.

---

## 2. Commercial competitors

### 2.1 Microsoft SwiftKey (Android)

- **Status:** standalone accounts retire **2026-05-31** — backup/sync moves to Microsoft Account + OneDrive; non-MS-account data deleted after that date.
- `data.swiftkey.com` portal exports a JSON whose **schema is not publicly documented** — Microsoft Support describes contents semantically only. SwiftFloris's tolerant `parseSwiftKeyJson` walker is the right approach (and likely permanent, not provisional).
- **Features SwiftFloris will never match:** Microsoft account sync, OneDrive backup, Bing/Copilot inline (the latter was already removed in 2025).
- **Strategic implication:** the cutoff is the migration window. Phase A of `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` is sized for it.

### 2.2 Google Gboard

- Rolling Play updates; **Gemini Intelligence wave** rolled 2026-05-12.
- **Features SwiftFloris doesn't have:**
  - **Rambler** — Gemini-powered voice clean-up (filler removal, restructure, code-switching). Already in ROADMAP §7 Next-2.5; gated on L1.
  - **Smart Compose** sentence completion (ROADMAP L1.4)
  - **Tone Suggestions** (ROADMAP P4 / L1.3)
  - Real-time Grammar Check
  - **Scan Text** OCR-from-keyboard
  - Emoji Kitchen (~1,500 sticker combos)
  - Tenor / GIPHY / Google Images unified GIF search (rejected by ROADMAP §10)
  - On-device Gemini Nano v3 writing tools (Pixel 10 / S26, 12 GB RAM — out of SwiftFloris's reasonable target tier; ROADMAP L1 targets Gemma 3 270M)
  - Handwriting input (ROADMAP Next-4)
  - Cross-device clipboard via Google account (rejected; replaced by N5 CRDT)
- **Strategic implication:** Gboard is unmatched on AI surfaces but dependent on Google services. Privacy-aware users defect to OSS keyboards on every Gboard feature that gets cloud-tightened.

### 2.3 Samsung Keyboard / Galaxy AI

- **One UI 7+ decouples Writing Assist from Samsung Keyboard** — Galaxy AI Writing Tools now work with **any keyboard** (good news: SwiftFloris users on Samsung devices can keep SwiftFloris and still get system-level rewrite).
- **Now Nudge** — context-aware suggestions in active chat (calendar entries, facts, actions).
- Galaxy S26 + Perplexity + Gemini multi-agent (Feb 2026).
- **Strategic implication:** **lowered moat for vendor-keyboard lock-in on Samsung** — explicit talking-point for the migration push to add to README.

### 2.4 Apple Keyboard / Writing Tools

- iOS-only; informs user expectations but not platform-comparable.
- Genmoji is the headline feature. SwiftFloris cannot replicate without an on-device generative-image model bigger than the §1 footprint budget.

### 2.5 Typewise

- v4.4.44, 2026-04-21
- **Features SwiftFloris doesn't have:**
  - **Hexagonal honeycomb key layout** — claims 70 % larger keys, 4× fewer typos (CES Innovation 2021+2022)
  - One-click translate / proofread / tone / shorten / extend / inclusive-language rewriter
  - 40-language coverage
- ROADMAP L9 already lists "honeycomb-hex"; the existing `HoneycombHexShape` + `HoneycombHexButton` + `HoneycombKeyboardRow` components in tree (v1.8.31-33) are the foundation; layout-engine wire-up is the open slice.

### 2.6 Chrooma Keyboard

- **Discontinued.** Last release helium-5.1.1 (June 2020). The Chameleon per-app-accent pattern lives on in SwiftFloris's `PerAppAccentResolver` (Next-11.3a).

### 2.7 Grammarly Mobile Keyboard

- **Android keyboard discontinued.** iOS keyboard still ships. Functionality migrating into the main Grammarly app + Samsung Keyboard integration.
- **Strategic implication:** another migration window. Grammarly defectors are a smaller pool than SwiftKey defectors but with the same privacy/account fatigue.

### 2.8 Tap Strap 2 / TapXR

- Hardware wearable; vendor IME for Android. Third-party `ScribbleJ/TapStrapApp` activity unverified for 2026.
- Different modality; not a real competitor.

---

## 3. Adjacent / niche

| Project | License | Why it matters | SwiftFloris stance |
|---|---|---|---|
| **Joplin Voice Typing** (in-app) | AGPL-3.0 (Joplin) | Per-context custom Whisper model + glossary | Reference for ROADMAP Next-2.3 model manager |
| **Keyman** (keymanapp/keyman) | MIT | **1,000+ minority-language layouts** in public Keyman cloud catalog. `.kmp` package installer | SwiftFloris already imports LDML (L8). `.kmp` runtime would extend reach dramatically |
| **Trinity / WhisperInput stand-alone keyboard** | mixed | Voice-only IME | SwiftFloris integrates voice inside the keyboard (Next-2) |

---

## 4. Cross-cutting findings

### 4.1 Features SwiftFloris has that no surveyed competitor offers

1. **SQLCipher-encrypted personal dictionary at rest** (`PersonalDictionaryEncryptionTest`-pinned).
2. **AIDL Model Context Protocol daemon bridge** with per-daemon switches.
3. **Purpose-built `swiftkey-cloud.json` importer** (v1.8.46+) sized for the 2026-05-31 cutoff.
4. **No-INTERNET-permission CI gate** enforced in Gradle, not just in marketing.
5. **Instant-remember overlay** — freshly-typed word climbs to top of predictions on next use.
6. **Roborazzi visual-regression + Macrobenchmark trace sections in 6 production hot paths** as part of CI.
7. **Cross-format hardware-keyboard import pipeline** — Keyman LDML + Windows KLC parsers (L8 + Next-6.4) — Gboard / SwiftKey rely on system keymaps.
8. **WebAuthn passkey injection from IME** (L10 detector + adapter contract) — first OSS keyboard to ship this surface.

### 4.2 Features all (or nearly all) major competitors ship that SwiftFloris does not

| Feature | Where competitors ship it | SwiftFloris plan |
|---|---|---|
| GIF/sticker search panel | Gboard, SwiftKey, Samsung, CleverKeys (offline) | ROADMAP §10 rejects Tenor/Giphy; bundled local sticker packs only (already shipped) |
| On-device LLM rewrite / proofread / tone | Gboard, Samsung, Apple, Typewise, Grammarly, FUTO | ROADMAP L1 (Gemma 3 270M / LiteRT-LM) gated on addon APK |
| Cross-device clipboard sync | Gboard (Google), SwiftKey (OneDrive), Samsung Cloud, Apple Universal Clipboard | ROADMAP §10 rejects vendor cloud; N5 CRDT over Syncthing is the local replacement |
| Stylus / handwriting input | Gboard, Samsung, Apple | ROADMAP Next-4 — facade + toggle shipped; recogniser ships in addon |
| Generative emoji (Genmoji / Emoji Kitchen) | Apple Genmoji, Gboard | Not on roadmap — too far from §1 footprint budget |
| Calendar / contact / task auto-insert | Samsung Now Nudge, Gboard | Open: SwiftKey-parity P9 (calendar) and P10 (tasks) — both small, both local, neither currently in any tier |
| User-supplied dictionary downloader UI | HeliBoard | Open: Next-10.3 addon spec covers dict packs; downloader UI not yet scoped |
| Dedicated arrow-keys row | SwiftKey | SwiftKey-parity P24 — `BottomRowPreset.Programmer` ships some of this; dedicated row preset not yet wired |

### 4.3 Ten feature opportunities ordered by impact ÷ cost

| # | Feature | Impact | Eng cost | Precedent / status |
|---|---|---|---|---|
| 1 | **User-imported sticker folder** (`~/Pictures/Stickers/*.webp` / `*.png` → inline via `commitContent`) | High | Low | No OSS keyboard offers this. Reuses `StickerMediaProvider` already in tree |
| 2 | **Offline GIF browser** (bundled Tenor cache via APK split + user-imported folder) | High | Med | CleverKeys ships offline GIF; no network required |
| 3 | **`.kmp` Keyman package runtime** (extend Next-6.4 / L8 importer) | Med–High (linguistic minorities) | Med | Keyman MIT engine; SwiftFloris already has LDML pathway |
| 4 | **Calendar quick-insert (P9)** and **Tasks quick-insert (P10)** | Med | S each | `CalendarContract.Instances` + `Intent.ACTION_INSERT` against Tasks.org / Google Tasks |
| 5 | **Dedicated arrow-keys row preset (P24)** | Med | S | New `BottomRowPreset.ArrowsRow` |
| 6 | **3×3 thumb-grid alt layout** (Thumb-Key parity, optional) | Med | Med | Layout-engine work; valuable for one-handed/foldable |
| 7 | **Hardware-keyboard floating candidate panel** for transliteration engine | Med | Med | fcitx5-android pattern; engine already in tree |
| 8 | **Local sticker / emoji-pack import via SAF** (`Settings → Media → Import sticker pack`) | Med | S | Builds on Next-9.4 emoji pin groups |
| 9 | **README "Galaxy AI Writing Tools work with SwiftFloris"** talking-point | Low (marketing) | XS | Samsung One UI 7 decouple — recent verified fact |
| 10 | **Honeycomb-hex layout wire-up** (HoneycombHex* components already in tree) | Med | Med-High | ROADMAP L9.1; geometry rework |

---

## 5. Reverification list

Items to re-verify on the next research pass (release-year ambiguity from
this pass):

- HeliBoard v3.9 release date (one source returned 2025-03-29, another
  2026-03-29; latter is more plausible given v3.7 (2026-02-22) reference
  in `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`).
- AnySoftKeyboard latest release year (1.13-r1 = 2025-02-08 most likely).
- Sayboard v4.2.1 release year (returned as "23 Aug" without year — treated
  as 2024).
- WhisperInput / Tap Strap Android IME (ScribbleJ) 2026 activity status.
- FunctionGemma availability via `litert-community/functiongemma-270m-ft-mobile-actions`
  on HuggingFace — direct re-fetch.
