# SwiftKey Full-Parity Roadmap — 2026-05-17

**Audit date:** 2026-05-17
**SwiftFloris baseline:** v1.8.51
**SwiftKey baseline:** Microsoft SwiftKey AI Keyboard (Android), 2026 release line — features documented at support.microsoft.com and observed in the Play listing / 2024–2026 changelog coverage.
**Goal:** make SwiftFloris behaviorally indistinguishable from SwiftKey for end-users **without** importing Microsoft account dependencies, Copilot/GPT-4 cloud, OneDrive sync, DALL-E generation, or any feature requiring `INTERNET` permission.

This document supersedes the earlier `SWIFTKEY_PARITY_AUDIT.md`, `SWIFTKEY_PARITY_RESEARCH.md`, `SWIFTKEY_PARITY_BUILD_PLAN.md`, `SWIFTKEY_AI_RESEARCH.md`, and `SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md` from May 2026 by reconciling them with the v1.8.51 shipped state and fresh 2026 SwiftKey research, then organising remaining work as one prioritised, phased plan with explicit philosophical exclusions and reuse hooks back to the existing §6/§7/§8 roadmap items in `ROADMAP.md`.

---

## 0. Executive summary

SwiftFloris already covers **the overwhelming majority** of SwiftKey's typing surface: tap typing, glide/flow, multilingual hot-switch (bilingual presets + per-token language id), adaptive touch, instant-remember personal dictionary, three-slot prediction strip with rejection semantics, FUTO voice handoff + Vosk streaming, encrypted clipboard with shortcuts, emoji palette with predictions, SwiftKey-Pure themes, one-handed / floating window modes, accessibility WCAG AA-or-better contrast, signing-fingerprint trust UI, and a 2026-05-31-timed SwiftKey-cloud-JSON importer.

**Real remaining gaps fall into four buckets:**

1. **Decoder calibration** — the heuristic ranker has the right inputs (touch, n-gram, language posterior, rejection priors) but is hand-tuned, not trace-tuned; a local ONNX/TFLite reranker boundary exists (`NeuralCandidateReranker`) but no model is plugged in.
2. **AI surfaces (Copilot / Tone / Designer)** — SwiftKey ships GPT-4 Turbo cloud + DALL-E 3 inline; SwiftFloris will instead ship a local Gemma 3 270M Q4 + a local rewrite/tone router (already scaffolded), and Designer-style image generation stays out of scope for §1 reasons.
3. **Split renderer + tablet polish** — `SplitKeyboardLayoutCalculator` exists but the per-row gutter wire-up into `TextKeyboardLayout` is the documented `P3-renderer` follow-up.
4. **Trust UI / migration funnel** — typing stats screen and SwiftKey cloud JSON importer both ship, but the migration-window outreach (N16.1) and the resilient post-migration personal-dictionary handoff are still pending evidence.

Everything that needs `INTERNET` permission — Microsoft account sync, OneDrive backup, Copilot chat, DALL-E inline image creation, Cloud Clipboard, Bing search — is **explicitly out of scope** per §1 of the main `ROADMAP.md`. Several of those have an on-device replacement on the roadmap (Gemma 3 → SmartCompose, Bergamot → Translation, Syncthing CRDT → Sync), so users get the *capability* without the cloud round-trip.

---

## 1. SwiftKey 2026 feature inventory (research summary)

Sourced from Microsoft Support, the Play listing, and 2024–2026 press / blog coverage.

### 1.1 Typing engines

- Neural language model (LSTM + attention, on-device since 2016; modern claim sub-15 ms inference, ~25 MB model).
- Adaptive touch — per-key per-user offset learning + posture-aware spatial backoff.
- SHARK2 trace decoder + LM rescoring for Flow.
- Flow Through Space — gesture continues across the space bar; LM retroactively re-ranks the previous short word when the next starts.
- Auto-correction with recoverable backspace-rejects-pair semantics.
- Three-slot prediction bar: left alternative, middle (default spacebar action), right alternative.
- Spacebar modes: plain space / complete current word / Quick prediction insert.
- Long-press for accented characters; explicit Accented Characters toggle in Layout & Keys.
- Up to **5 simultaneously-active languages** (Microsoft account locked-in for full sync, but local detection works without it).
- Emoji predictions (learns user's emoji-per-word pattern).
- Adaptive emoji keyboard (favourite/learned emoji surface in recent tab).
- Federated personalization — local LSTM fine-tunes on anonymised typing, ships encrypted DP-noised gradients to Microsoft's aggregator.

### 1.2 Toolbar (the visible AI / productivity strip)

- **Copilot** — chat, rewrite, summarise, brainstorm, Q&A inline. Powered by GPT-4 Turbo (cloud).
- **Tone** — rephrase highlighted text as professional / casual / polite.
- **Image creator / Designer** — DALL-E 3 inside the emoji tab, generates custom images.
- **Translator** — Microsoft Translator, cloud, in-place text translation.
- **Clipboard** — history with pinning + Shortcuts (auto-replace strings).
- **GIFs** — Tenor-backed search.
- **Stickers** — static + animated, including iMessage-style packs and Designer-generated.
- **Calendar** — pick an appointment from agenda UI, drop event details into the field.
- **Microsoft To Do** — add tasks to a chosen list from the keyboard.
- **Voice** — Microsoft voice recognizer (cloud-tied on Android).
- **Location** — share current location.
- **Number row** toggle.
- **Search** — Bing search inline in the toolbar (variously rebranded as Copilot search).
- **Themes** — gallery + animated themes (Cogs, Zig Zag, Snowy Sky, Shooting Stars, Bubble Gum on iOS; static gallery on Android).
- **Cloud Clipboard** — syncs copied items across signed-in devices via Microsoft account.

### 1.3 Window / layout modes

- Full (docked), one-handed, floating/undocked, thumb/split, tablet.
- Resize sliders in Layout & Keys → Resize.
- Number row toggle.
- Dedicated arrow-keys row (long-press to enable via Modes).

### 1.4 Personalization / trust / data

- Account-backed backup of learned words + typing model (OneDrive after 2026-05-31).
- Stats screen — words learned, typing accuracy, top emoji, top apps.
- Personal dictionary editor.
- Per-account learning reset.
- All non-account data stays local.

### 1.5 Accessibility

- High-contrast theme (WCAG 2.0 AAA contrast).
- TalkBack support, including slide-and-wait for the accented-character popup.
- VoiceOver support on iOS.
- Resize keyboard for low-vision users.
- "Explore by Touch" support.

### 1.6 Migration / cloud

- Microsoft account sync (mandatory after 2026-05-31).
- OneDrive-backed learned-words store.
- `data.swiftkey.com` portal for exporting your data (retires 2026-05-31).

---

## 2. SwiftFloris 2026-05-17 inventory (v1.8.51)

Mapped against the same SwiftKey buckets so the gap analysis is direct.

### 2.1 Typing engines — SHIPPED

| SwiftKey engine | SwiftFloris status |
|---|---|
| Neural LM (LSTM + attention) | Heuristic decoder with `NeuralCandidateReranker` boundary in `ime/nlp/NeuralCandidateReranker.kt`; no neural model plugged in yet (L1 dependency). |
| Adaptive touch | `AdaptiveTouchModel` per-subtype Welford-online; persisted across restarts; per-key offsets feed `findNClosestKeys` + `Pruner.generateIdealGestures`. |
| SHARK2 + Flow | `StatisticalGlideTypingClassifier` over bounded EN/DE/ES/FR/IT/PT dictionaries. |
| Flow Through Space | `GlideTypingGesture.Detector.signalWordBoundary()` + `GlideContextRescorer` covering `in` → `I'm`, etc. Pref `glide.flowThroughSpace` (default on). |
| Three-slot prediction bar with rejection-after-backspace | `SwiftKeyCandidateRanker` + `AutoCommitSuppression`; replay fixtures pin the behavior. |
| Spacebar modes (plain / complete / Quick prediction insert) | All three present; Quick prediction insert is the explicit Typing setting added in the v1.7.x slice. |
| Long-press accented characters | Inherited FlorisBoard upstream; works on every Latin layout. |
| Up to 5 simultaneous languages | Bilingual subtype presets (EN+ES / EN+FR / EN+DE) shipped; `MultilingualTokenScorer` evaluates every active subtype locale; cross-locale candidate frequency feeds the ranker. |
| Emoji predictions | `EmojiSuggestionProvider` blends bundled keyword weight + custom-tag weight; v1.8.29 wired the pinned-groups palette row. |
| Adaptive emoji (favourites learned) | `EmojiHistory` with pin/unpin + auto/manual sort strategies. |
| Federated personalization | Out by §1 (no aggregator). **Local-only** equivalent ships via `PersonalBigramStore` + `PersonalTrigramStore` + `UserDictionaryOverlay` + correction outcome priors. |

### 2.2 Toolbar / smart surfaces

| SwiftKey toolbar item | SwiftFloris status |
|---|---|
| Copilot chat / rewrite | **Local-only path scaffolded:** `ime/smartcompose/RewriteProvider.kt` + `RewriteRouter.kt`. Inline ghost-text candidate type exists (`GhostTextSuggestionCandidate`). Provider stays no-op until L1.1a Gemma addon ships. |
| Tone (professional/casual/polite) | `RewriteProvider` shape supports tone-tagged rewrites; UI surface deferred to the addon. |
| Image creator / Designer (DALL-E 3) | **Out of scope** (§1 — cloud, GPL/non-commercial models on-device too large + ethically loaded). |
| Translator | `ime/translate/InlineTranslator` + `TranslationRouter` + `TranslationCache` + `LanguageDetector` + `SentenceTokenizer` + `TranslationLanguagePackManager` + `QuickAction.TranslateSelection` smartbar action. Bergamot WASM runtime addon (L2.1a) outstanding. |
| Clipboard with pinning + shortcuts | SQLCipher-encrypted clipboard with history + pinning + per-app source tag (`ClipboardManager`); shortcut auto-replace via personal-dictionary `shortcut` column wired in `NlpManager.getAutoCommitCandidate`. |
| GIFs | **Out of scope** (Tenor/Giphy needs network; explicit rejection in §10 of ROADMAP.md). Local sticker packs cover the static-reaction case. |
| Stickers | `StickerRenderer` + `StickerMediaProvider` + bundled "Swift reactions" / "Quick replies" packs; commit via `commitContent(InputContentInfoCompat)`. Animated stickers via `coil-gif` decoder. |
| Calendar | **Not shipped.** New gap. |
| Microsoft To Do / generic tasks | **Not shipped.** New gap. |
| Voice | FUTO Voice Input handoff (preferred) + Vosk streaming (fallback) + RAM-aware model selector + local Whisper/Vosk model manager. |
| Location | **Not shipped.** Privacy-sensitive — gate carefully. |
| Number row toggle | Yes — present in keyboard settings. |
| Search | **Out of scope** (Bing = cloud; local search has no semantic anchor). |
| Themes gallery | 13 bundled themes including SwiftKey Pure (Light/Dark/M3 Expressive), Nord, Tokyo Night, Dracula, Catppuccin Mocha. Snygg theme engine accepts user-imported themes. Animated themes **not shipped**. |
| Cloud Clipboard | **Replaced by** Next-5 CRDT personal-dictionary sync over Syncthing (E2EE, no vendor). Clipboard sync ride-along not yet wired. |

### 2.3 Window / layout modes

| SwiftKey mode | SwiftFloris status |
|---|---|
| Full / docked | Default. |
| One-handed | `ImeWindow.OneHandedPanel` with chevron flip-side, drag-resize, zoom controls. |
| Floating / undocked | `ImeWindowEditorHandles` + `prefs.keyboard.startInFloatingMode` + first-entry onboarding overlay (Next-7.1a). |
| Thumb / split | **Preference + window mode shipped, renderer pending.** `prefs.keyboard.splitKeyboardEnabled` + `ImeWindowMode.Fixed.SPLIT` + `Fixed.Split` constraint class + `SplitKeyboardLayoutCalculator`. The per-row gutter emission inside `TextKeyboardLayout` (the **P3-renderer** follow-up) is the outstanding work. |
| Resize sliders | Font-size slider (50–150 %) + height slider (50–150 %) under Settings → Keyboard. |
| Arrow-keys row | Bottom-row preset "Programmer" includes Tab/Esc/arrows; per-app smartbar profile "CODE" surfaces arrows in the smartbar. |

### 2.4 Personalization / trust / data

| SwiftKey surface | SwiftFloris status |
|---|---|
| Account-backed backup | **Out by §1.** Replaced by local-export + CRDT sync. |
| Stats screen | `TypingStatsScreen` — learned dictionary words, learned phrase pairs + disk usage, learned trigrams + disk, correction outcome prior count, adaptive-touch sample count, with reset buttons + trace-share. |
| Personal dictionary editor | `UserDictionaryScreen` with Add/Edit/Delete + Word/Freq/Shortcut/Locale fields + validation rules. |
| Learning reset | Per-category reset in TypingStats; "Forget word" via long-press on a candidate (`Next-3.4`). |

### 2.5 Accessibility

| SwiftKey item | SwiftFloris status |
|---|---|
| High-contrast theme | `ThemeContrastTest` pins Catppuccin Mocha + Tokyo Night to WCAG 2.1 AA 4.5:1; SwiftKey Pure light + dark pass. **No dedicated AAA high-contrast theme yet.** |
| TalkBack support including alternative-char announce | `keyContentDescription(context, code, label, hintedLabel)` resolves through Crowdin-routed `R.string.a11y__key__*` strings; appends `", alternative: <hint>"` for hinted alt-glyphs. |
| 48dp WCAG touch-target floor | `TouchTargetWcagTest` pins WCAG 2.5.5 AAA for portrait, 24dp WCAG 2.5.8 AA for landscape. |
| Resize keyboard | Height slider + floating mode drag/resize handles. |
| Switch Access / "Explore by Touch" | `android:supportsSwitchingToNextInputMethod="true"` declared; standard subtype-cycle gesture works. |

### 2.6 Migration / cloud

| SwiftKey path | SwiftFloris status |
|---|---|
| Microsoft account sync | Out by §1. |
| OneDrive backup | Out by §1. |
| `data.swiftkey.com` export (retires 2026-05-31) | **`DictionaryImporter.parseSwiftKeyJson` shipped v1.8.46.** Three envelope shapes handled. |
| `docs/MIGRATE_FROM_SWIFTKEY.md` | Shipped — three migration paths documented. |

---

## 3. Gap matrix — what's left for full parity

Each row is one gap with effort estimate, philosophy gate, and reuse hook. Status legend: 🟢 minor / 🟡 medium / 🔴 large / ⛔ out by §1.

| # | SwiftKey feature | SwiftFloris gap | Effort | Status | Reuse hook |
|---|---|---|---|---|---|
| P1 | **Neural LM** — sub-15 ms on-device next-word / phrase prediction | Heuristic ranker has the right inputs; need an actual local model behind `NeuralCandidateReranker`. | 🔴 | Large | ROADMAP §8 L1 (Gemma 3 270M Q4 INT4 via LiteRT-LM addon). Boundary already exists at `NeuralCandidateReranker`. |
| P2 | **Smart Compose ghost-text** — gray inline phrase continuation, swipe-space-to-accept | IME-side surface shipped v1.8.3 (`GhostTextSuggestionCandidate`); provider is no-op until P1 lands. | 🟢 (gated on P1) | Gated | `SmartComposeProviderRegistry`. |
| P3 | **Split keyboard renderer** — per-row gutter, per-side key rects, gutter-aware touch routing | Preference + window mode + constraint class shipped v1.8.3; renderer wire-up inside `TextKeyboardLayout` is the documented `P3-renderer` follow-up. | 🟡 | In-progress | `SplitKeyboardLayoutCalculator` + `SplitGutterPostPass`. |
| P4 | **Tone / Rewrite toolbar action** (professional / casual / polite) | `RewriteProvider` + `RewriteRouter` scaffolded; UI integration + ghost-text preview surface not wired. | 🟡 (gated on P1) | Gated | `ime/smartcompose/RewriteRouter`. |
| P5 | **Designer image creator (DALL-E 3)** | Cloud + GPL/non-commercial model territory. | ⛔ | Out by §1 | n/a |
| P6 | **Translator** — inline text translation | Facade + cache + router shipped; Bergamot WASM addon (L2.1a) outstanding. | 🟡 | Gated | `ime/translate/InlineTranslator` + L2.1a addon. |
| P7 | **GIFs (Tenor)** | Network-bound by definition. | ⛔ | Rejected in §10 | Local sticker packs cover the static case. |
| P8 | **Bing search inline** | Cloud. | ⛔ | Rejected in §10 | n/a |
| P9 | **Calendar quick-insert** — pick agenda entry, drop event details | No Android-side hook today. CalendarContract.Instances + a smartbar quick-action would do this all-local. | 🟢 | New | New `QuickAction.InsertCalendarEvent` + Android CalendarContract reader. Requires READ_CALENDAR permission — gate behind opt-in. |
| P10 | **Microsoft To Do** | Microsoft-specific. **Generic task manager intent** (Tasks.org / OpenTasks / Google Tasks intents) would be a local equivalent. | 🟢 | New | Tasks.org has a public broadcast contract; Tasker-style intent endpoint pattern (L11.1) already shipped. |
| P11 | **Cloud Clipboard** (cross-device clipboard sync) | Out by §1 as a Microsoft-account feature. Local-only replacement = ride-along on Next-5 CRDT sync. | 🟡 | Roadmap re-use | Next-5 CRDT sync over Syncthing. |
| P12 | **OneDrive learned-words backup** | Out by §1. Local-only replacement = personal-dictionary export to plain CSV/JSON + Syncthing sync. | 🟢 | Partly shipped | Already exports via `UserDictionaryDatabase.exportCombinedList`; needs encrypted-blob option for Syncthing carry. |
| P13 | **Decoder calibration from real traces** | `SwiftKeyTypingTraceRecorder` + replay fixtures + `SwiftKeyCandidateTuning` + `GlideContextTuning` exist; need actual field traces to tune the constants. | 🟡 | In-progress | `SwiftKeyTraceFixtureExporter` ships traces from device. |
| P14 | **Animated themes** (Cogs, Zig Zag, Snowy Sky, etc.) | Static SwiftKey Pure + M3 Expressive themes shipped. Snygg theme engine doesn't have animated-bg primitive. | 🟢 | New | Snygg engine extension; pure asset work. |
| P15 | **AAA high-contrast theme** | WCAG AA met; no explicit AAA-targeted theme like SwiftKey's High Contrast. | 🟢 | New | New Snygg theme sheet. |
| P16 | **Outreach push for SwiftKey migration window** (cutoff 2026-05-31) | Migration importer + doc shipped; the Reddit / GH-Releases pin / README badge is the marketing slice. | 🟢 | New | N16.1 in §6 of ROADMAP. |
| P17 | **Resilient post-migration personal-dictionary handoff** | Imported SwiftKey words land in personal-dict, but no UX confirms "X words imported from your SwiftKey export" + no rollback. | 🟢 | New | `PersonalDictionaryImportSummary` + Settings → Personal dictionary → Import result page. |
| P18 | **"Always insert prediction" mode parity audit on empty fields** | Quick prediction insert shipped, but real-world tuning on empty fields + low-confidence next-word candidates still flagged in audit. | 🟢 | In-progress | Gate calibration on P13. |
| P19 | **Touch model — shared-spelling bilingual handling** | Multilingual scorer handles known-word protection; same-prefix / shared-spelling cases noted as "next step" in the audit. | 🟢 | In-progress | `MultilingualTokenScorer` + replay fixtures. |
| P20 | **Same-sentence language switches** | Mid-sentence language flip mostly handled by trailing-word evidence; multi-locale bigram seeds + extended context window still pending. | 🟢 | In-progress | `TypingContextExtractor.previousWordListBeforeCurrentWord`. |
| P21 | **Emoji prediction in suggestion strip** (learns user's emoji-per-word pattern) | `EmojiSuggestionProvider` exists; tested via parallel-stream candidate scoring. Real-world tuning vs SwiftKey's pattern remaining. | 🟢 | Shipped, calibrate | `EmojiSuggestionProvider`. |
| P22 | **Long-press alt-character popup TalkBack contract parity** | `keyContentDescription` includes `", alternative: <hint>"`; SwiftKey announces "alternative characters available" + slide-and-wait. Verify our Talkback emission matches semantics. | 🟢 | In-progress | N8.3 / N8.3a. |
| P23 | **Number row toggle reachable from settings** | Yes — already a settings toggle. | ✅ | Done | — |
| P24 | **Dedicated arrow-keys row** | Not in a default profile; covered by Programmer bottom-row preset + CODE smartbar profile. | 🟢 | New | New `BottomRowPreset.ArrowsRow` or smartbar layer toggle. |
| P25 | **Themes — animated** | See P14. | (same as P14) | New | (same as P14) |
| P26 | **Personalization "Stats" — typing accuracy delta** | TypingStats covers learned counts + disk; SwiftKey shows an accuracy-improvement number. Compute locally from `CorrectionOutcomePriors`. | 🟢 | New | `CorrectionOutcomePriors` + `SwiftKeyTypingTraceRecorder`. |

---

## 4. Phased plan

Each phase is bounded so a contributor can ship the whole phase as one slice and roll it back independently. Versioning lines up with the existing `v1.8.X` release cadence (one phase ≈ one release).

### Phase A — Trust-window close (target: by 2026-05-31)

The SwiftKey-account cutoff is the only hard deadline this roadmap has; everything in Phase A protects the migration funnel.

- ⏳ **A1 (P16)** — SwiftKey migration outreach checklist. **README-side shipped 2026-05-17 (v1.8.52):** Shields badge captioned "window closes 2026-05-31" in red, banner block above Highlights table walking visitors through both no-cloud migration paths, status line bumps to v1.8.52 with the 14-days-remaining countdown. **Marketing-side still open** (Reddit thread + 2026-05-30 release pin) — see [v1.8.52 release notes](RELEASE_NOTES_v1.8.52.md). Cost: XS.
- ✅ **A2 (P17)** shipped 2026-05-17 (v1.8.53). New pure-Kotlin `PersonalDictionaryImportBatch` orchestrator implements a snapshot-and-diff pattern: snapshots the DAO's known ids before the insert pass, then re-reads after to identify exactly which rows are new (rollback-eligible) vs. updated in place (NOT rollback-eligible because the old freq / shortcut is gone). `PersonalDictionaryImportResult` carries the rollback id list + updated + skipped + total counts + detected source format. New `PersonalDictionaryImportSummaryDialog` Compose surface renders "Added N new words / Updated M existing / Skipped K malformed" with `Keep imported words` (primary) + `Undo import` (secondary, hidden when not rollback-eligible). **Also closes the long-standing wiring gap** between `DictionaryImporter.parseSwiftKeyJson` (shipped v1.8.46) and the Settings UI — the import flow now byte-sniffs to the modular parser first, falling back to the legacy `importCombinedList` only on `DictionaryImportException`. 10 new tests via in-memory `FakeUserDictionaryDao` covering empty input, fresh inserts, in-place updates, blank-word skip, freq clamping, rollback diff, idempotent rollback, no-op rollback, shortcut+locale round-trip, and malformed-locale-tag tolerance.
- ⏳ **A3 (P12)** — codec primitive shipped 2026-05-17 (v1.8.54). New pure-Kotlin `EncryptedDictionaryExport` envelope codec uses AES-256-GCM keyed by PBKDF2-HMAC-SHA-256 at the OWASP-2025 600 000-iteration count, with the iteration count baked into the 44-byte header so future bumps decrypt old exports unchanged. 16-byte random PBKDF2 salt + 12-byte random AES-GCM nonce per export. Cryptographic-indistinguishability collapse: wrong passphrase and tampered ciphertext both surface as `BAD_PASSPHRASE` so the UI can't accidentally leak which case it was. Header validation rejects oversized / truncated / unsupported-version / corrupt-header blobs before touching the cipher — defends against a malicious envelope claiming a 1 GiB plaintext to OOM the destination. `isEncryptedEnvelope(candidate)` byte-sniff predicate lets the import flow ask for a passphrase only when the file is actually encrypted. `EncryptedDictionaryException` + `FailureReason` enum (6 reasons) keeps the call site's `when` exhaustive. 15 new tests cover round-trip, every failure mode, bound rejection, and the sniff predicate against CSV / JSON / XML / too-short inputs. **Settings UI wiring (passphrase dialog + file-create launcher + encrypt-then-write loop) is the follow-up slice.**

### Phase B — Touch & decoder calibration (v1.8.52 – v1.8.55)

Close the heuristic-to-neural transition path while keeping the heuristic decoder a stable fallback. None of these need external models or addons.

- **B1 (P13)** — Sentence-position priors expansion. Extend `ColdStartNextWordPriors` past the first English seed set: capture three-word phrase continuations from a curated CC-licensed phrase corpus (still small enough to bundle); add `cs.tsv` / `de.tsv` / `es.tsv` / `fr.tsv` / `it.tsv` / `pt.tsv` Zipf overlays to mirror `freq/en.tsv`. Cost: M, asset + parser work.
- **B2 (P18)** — Quick-prediction-insert tuning on empty fields. Property-based test sweeping context length × candidate confidence × user-recency to derive the threshold that fires on (a) cold-start (b) post-period (c) post-newline only when the top candidate's confidence × recency-weight ≥ a configurable floor. Cost: M, no shipping behavior changes — just settles the threshold.
- **B3 (P19)** — Shared-spelling bilingual handling. Extend `MultilingualTokenScorer` to detect when the typed word is known in ≥ 2 active locales and dampen one-language correction unless candidate ≡ typed word. Replay fixture: `no` (EN/ES shared) must surface both `no` and `on/so` with `no` literal-protected. Cost: S, scorer + 4 replay cases.
- **B4 (P20)** — Same-sentence language switch hardening. Expand `TypingContextExtractor.previousWordListBeforeCurrentWord` from 2-word to 4-word trailing context, and add an alpha-blend on the per-locale evidence so a switch midway through a sentence transitions smoothly. Cost: S.
- **B5 (P13 continued)** — Decoder field calibration. Convert ≥ 50 captured-locally `swiftkey_trace.jsonl` rows into the checked-in fixture set via `SwiftKeyTraceFixtureExporter`; use the new fixtures to validate any `SwiftKeyCandidateTuning` constant move. Cost: M (mostly fixture curation).

### Phase C — Split renderer (v1.8.56)

Closes the last SwiftKey-only visual feature SwiftFloris still doesn't ship fully.

- **C1 (P3)** — Wire `SplitKeyboardLayoutCalculator` + `SplitGutterPostPass.apply(keyboard, gutterPx)` into `TextKeyboardLayout.layout(...)` so the per-row gutter renders on tablets when `prefs.keyboard.splitKeyboardEnabled` AND `Fixed.Split.isViable`. Touch hit-test gets the same gutter-aware adjustment so taps in the gutter zone don't land on the nearest key on either side. Cost: M.
- **C2 (P24)** — Optional dedicated arrow-keys row as a new `BottomRowPreset.ArrowsRow` selectable in Settings → Keyboard → Bottom-row preset. Cost: S.
- **C3 (P14 + P15)** — Two new bundled themes: **SwiftKey High Contrast (AAA)** (4.5:1 + alt-glyph outline) and **Aurora Animated** (the first Snygg animated-bg theme; pure GenericShape morph, no extra dep). Cost: S each.

### Phase D — Productivity surfaces (v1.8.57 – v1.8.58)

Adds the SwiftKey-toolbar-style productivity tiles that don't need network. Each lands behind an explicit user opt-in (the `OptInAddonDispatcher` pattern shipped v1.8.18 is the seam).

- **D1 (P9)** — Calendar quick-insert. New `QuickAction.InsertCalendarEvent` smartbar action reads from `CalendarContract.Instances` (today + next 7 days), shows an agenda picker, and inserts the event title + date/time at the cursor. Permission: `READ_CALENDAR`, requested only when the action is enabled. Cost: M.
- **D2 (P10)** — Generic tasks intent endpoint. New `QuickAction.InsertTask` opens an Android `Intent.ACTION_INSERT` against `Tasks.org` / OpenTasks / Google Tasks (whichever is installed) with the user-typed text pre-filled. No new permission. Cost: S, builds on the existing Tasker intent pattern (L11.1).
- **D3 (P26)** — Typing-stats accuracy-delta number. Compute "x % fewer corrections accepted this week vs last" from `CorrectionOutcomePriors` and surface in `TypingStatsScreen`. Cost: S.

### Phase E — Neural surfaces (post v2.0)

Phase E is gated on the upstream L1 LiteRT-LM bring-up. Until L1 ships, the surfaces below stay as no-op providers and the heuristic decoder remains the default.

- **E1 (P1 + P2)** — L1.1a LiteRT-LM addon shipping Gemma 3 270M Q4 INT4 (~135 MB on disk). Plug into `SmartComposeProviderRegistry`. Ghost-text candidate becomes visible.
- **E2 (P4)** — Tone toolbar action wired to the same Gemma instance via `RewriteRouter`. Three preset prompts (professional / casual / polite) + custom prompt slot. Cost: S once E1 lands.
- **E3 (P6)** — L2.1a Bergamot WASM addon shipping the EN+ES / EN+FR / EN+DE pairs. Plug into `InlineTranslatorRegistry`. `QuickAction.TranslateSelection` becomes useful.
- **E4 (P11)** — CRDT clipboard ride-along on the existing Next-5 personal-dictionary sync.
- **E5 (P21 calibration)** — Emoji-prediction tuning vs real device traces. Likely needs the L1 model as a fallback reranker on emoji candidates.

### Phase F — Explicitly out of scope (with reasoning)

These stay rejected and the rejection is documented so the question doesn't get re-litigated.

- **F1 (P5 — Designer / DALL-E 3 image creator)** — cloud-bound; the locally-runnable equivalents (SDXL Lightning, FLUX.1-schnell) are 4 – 12 GB and either restrict commercial use or carry attribution clauses incompatible with the Apache-2.0 main app. Out by §1 + §10.
- **F2 (P7 — GIFs / Tenor)** — fundamentally network. Out by §10 of `ROADMAP.md`.
- **F3 (P8 — Bing search inline)** — cloud. Out by §10.
- **F4 (account-backed sync / OneDrive)** — explicit anti-feature in §1 ("zero account requirement, zero vendor cloud").
- **F5 (federated personalization gradients)** — the *server* leg violates §1; the *local fine-tune* leg is what `PersonalBigramStore` + `PersonalTrigramStore` + `CorrectionOutcomePriors` + `AdaptiveTouchModel` already do, just without uploading the gradient.

---

## 5. Acceptance criteria

Each phase ships with these go/no-go gates:

- **Phase A**: SwiftKey JSON imports round-trip through `DictionaryImporter` for at least three real user exports (collected on the team's own devices before 2026-05-31). README badge + pin live by 2026-05-30. No new crash signature surfaces in the post-import flow.
- **Phase B**: New replay fixtures all green; aggregate metrics from `SwiftKeyTraceReplayFixtureTest` show ≥ 95 % full-ranking hit rate and ≥ 99 % typed-literal protection rate. No regression in `GlideContextRescorerTest` aggregate metrics.
- **Phase C**: Split-keyboard tap on a 10-inch tablet hits the visually-targeted key (manual Roborazzi capture of left + right half + gutter). High Contrast theme passes WCAG 2.0 AAA contrast against every key-class background pair in `ThemeContrastTest`. Animated theme renders at ≥ 30 fps on a Pixel 6.
- **Phase D**: Calendar quick-insert works on at least two AOSP calendar apps + Google Calendar. Tasks quick-insert resolves on Tasks.org and Google Tasks. Accuracy-delta number computes deterministically from a synthetic `CorrectionOutcomePriors` fixture.
- **Phase E**: L1.1a addon installs cleanly; smart-compose ghost text fires for at least three test phrases; Bergamot translation pair resolves a 100-char EN→ES sentence in < 250 ms on a Pixel 7. Both addons keep the base APK's no-`INTERNET` posture verified by the existing `verifyNoInternetPermission` Gradle gate.

---

## 6. Cross-reference back into the main roadmap

| This doc | `ROADMAP.md` equivalent |
|---|---|
| P1 | §8 L1 |
| P2 | §0 SwiftKey parity tracker → ✅ shipped v1.8.3 (provider gated on P1) |
| P3 | §0 P3-renderer + §7 Next-7.2a |
| P4 | §7 Next-2.5 follow-up + §8 L1.3 |
| P5 | §10 EXPLICITLY REJECTED |
| P6 | §8 L2.1a |
| P9 | New §7 Next-N quick-action |
| P10 | New §7 Next-N quick-action |
| P11 | §7 Next-5.x ride-along |
| P12 | §7 Next-6.5 extension of existing export path |
| P13 | §6 N12.13 – N12.21 (mostly shipped); broader trace calibration ongoing |
| P14 / P15 | §6 N3.x theme follow-ups |
| P16 / P17 | §6 N16.1 / N16.2-follow-up |
| P19 / P20 | §6 N12.27 / N12.21 already partial, expand to full |
| P24 | §6 N4.2 extension |
| P26 | §6 N12.6 / N12.14 extension |

---

## 7. What this roadmap deliberately does NOT do

1. **Force a Microsoft account on anyone.** The whole point of SwiftFloris is the opposite.
2. **Ship a cloud round-trip for any feature**, including translation, voice, smart-compose, and image generation. Each one has an on-device equivalent on the §8 LATER tier or is rejected outright.
3. **Promise the Gemma 3 270M model lands in a specific release.** L1 is committed but the upstream LiteRT-LM tooling has its own cadence; the smart-compose ghost-text surface is **shipped and visible** today, just powered by a no-op provider until the model addon installs.
4. **Replace the heuristic decoder.** The `NeuralCandidateReranker` boundary is additive — the heuristic ranker stays the fallback so the IME is usable on devices that don't install the addon.
5. **Reach feature parity on AI surfaces SwiftKey added in 2024–2026** that depend on closed cloud APIs (Copilot chat, Designer images, Cloud Clipboard, Bing search). These have either local-only replacements (smart compose, sticker packs, CRDT sync) or are explicitly rejected.

---

## Sources

- [Microsoft SwiftKey overview](https://www.microsoft.com/en-us/swiftkey)
- [Microsoft SwiftKey Keyboard help & learning](https://support.microsoft.com/en-us/swiftkey)
- [Microsoft SwiftKey AI Keyboard — Play listing](https://play.google.com/store/apps/details?id=com.touchtype.swiftkey)
- [How does the SwiftKey prediction bar work — Microsoft Support](https://support.microsoft.com/en-us/swiftkey-keyboard/how-does-the-microsoft-swiftkey-prediction-bar-work)
- [How does the spacebar work with autocorrect — Microsoft Support](https://support.microsoft.com/en-US/swiftkey-keyboard/how-does-the-spacebar-work-with-autocorrect-in-microsoft-swiftkey-keyboard)
- [What is Flow — Microsoft Support](https://support.microsoft.com/en-us/swiftkey-keyboard/what-is-flow-and-how-do-i-enable-it-with-microsoft-swiftkey-keyboard)
- [Multilingual typing — Microsoft Support](https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-microsoft-swiftkey-keyboard-with-more-than-one-language)
- [Accessibility in Microsoft SwiftKey — Microsoft Support](https://support.microsoft.com/en-us/topic/accessibility-in-microsoft-swiftkey-keyboard-a3b12d18-61ba-4e2c-82bc-c42e8f12c62c)
- [Toolbar access — Microsoft Support](https://support.microsoft.com/en-us/topic/how-do-i-access-toolbar-in-microsoft-swiftkey-keyboard-62c63625-ff2a-4e97-bb9b-0004d8a5b6b5)
- [Keyboard modes — Microsoft Support](https://support.microsoft.com/en-us/topic/how-to-change-your-keyboard-mode-on-microsoft-swiftkey-keyboard-e3feab3a-80c7-4a76-8ea7-4249c96ec3b1)
- [Sounds & vibrations — Microsoft Support](https://support.microsoft.com/en-us/swiftkey-keyboard/how-do-i-change-the-sounds-or-vibrations-that-my-microsoft-swiftkey-keyboard-makes)
- [Themes — Microsoft Support](https://support.microsoft.com/en-us/swiftkey-keyboard/which-themes-are-available-for-my-microsoft-swiftkey-keyboard)
- [Accented characters — Microsoft Support](https://support.microsoft.com/en-us/topic/how-do-i-insert-accented-characters-in-microsoft-swiftkey-keyboard-561fb6ff-ccc0-4ab8-b62e-06047d62cb20)
- [Emoji predictions — Microsoft Support](https://support.microsoft.com/en-us/topic/how-to-use-emoji-with-microsoft-swiftkey-keyboard-4eaa5946-775c-409f-b4c4-3026080179f5)
- [SwiftKey adds calendar and location sharing — Windows Central](https://www.windowscentral.com/swiftkey-adds-location-and-calendar-sharing-its-toolbar-feature)
- [Microsoft To Do integration — Microsoft Tech Community](https://techcommunity.microsoft.com/t5/microsoft-to-do-blog/add-tasks-to-your-to-do-list-right-in-the-swiftkey-keyboard/ba-p/3143221)
- [SwiftKey ditches Bing for Copilot + GPT-4 Turbo — Windows Central](https://www.windowscentral.com/software-apps/microsoft-swiftkey-ditches-bing-for-copilot)
- [Copilot DALL-E 3 Designer in SwiftKey — Windows Report](https://windowsreport.com/a-new-version-of-swiftkey-will-bring-copilot-dall-e-3-designer-to-android/)
- [SwiftKey account retirement (2026-05-31) — Windows Central](https://www.windowscentral.com/software-apps/swiftkey-will-soon-require-a-microsoft-account-data-to-be-moved-to-onedrive)
- [SwiftKey accounts officially retiring — Yahoo Tech](https://tech.yahoo.com/apps/articles/swiftkey-accounts-officially-retiring-hapen-114613697.html)
- [Microsoft SwiftKey — Wikipedia](https://en.wikipedia.org/wiki/Microsoft_SwiftKey)
- [LLM-Powered Text Entry Decoding (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC12723528/)
- [Making touchscreen keyboards adaptive (ACM CHI 2013)](https://dl.acm.org/doi/10.1145/2470654.2481384)
