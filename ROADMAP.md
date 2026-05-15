# SwiftFloris Roadmap v5.0

**Last Updated:** 2026-05-15
**Current Version:** v1.7.9 (released 2026-05-14 — 19-item ROADMAP pass closing most NEXT-tier breadth: N3.4 finish, N8.3 finish, Next-2.4, Next-3.3, Next-3.4, Next-6.1, Next-6.2, Next-6.3, Next-7.3 (audit), Next-8.1, Next-8.2, Next-9.1, Next-9.2, Next-9.3, Next-10.1, Next-10.2, Next-11.2, Next-11.3, Next-12.3. See `RELEASE_NOTES_v1.7.9.md`. 356 unit tests green, `:app:compileDebugKotlin` clean. Outstanding remaining heavy items: Next-3.1 KenLM 5-gram, Next-4 stylus, Next-5 CRDT sync, Next-7.1 floating-window UX polish, Next-7.2 split keyboard, Next-12.1 Macrobenchmark, L1 Gemma 3 270M smart-compose via LiteRT-LM.)
**Project Status:** Production fork of FlorisBoard v0.6-class baseline; autocorrect + dictionary + multilingual NLP + voice-routing + addon scaffold all past upstream.
**Supersedes:** ROADMAP v4.0 (2026-05-14). Completed items preserved in §3 with dated checkmarks; new items added from the v1.7.9 research pass below (LiteRT-LM migration, Android 16 PWLE haptic envelopes, Rambler-style streaming-voice cleanup, emoji-tag predict, F-Droid Verified-tier badge, 16KB-page CI gate).
**Document length:** intentionally dense — every Now/Next item carries a source citation `[n]` traceable to the Appendix.

---

## 1. Philosophy (load-bearing — every item below must respect this)

- **100% offline.** No `INTERNET` permission. Zero telemetry. Zero account requirement. Zero vendor cloud.
- **Apache-2.0 only.** GPL/AGPL/LGPL, FUTO Source-First, and undeclared-license code cannot be ingested into the main app, only conceptually borrowed or shipped as a clearly-isolated module under its own license.
- **Audit-friendly by construction.** No closed-source binary blobs (e.g. `libjni_latinimegoogle.so`); reproducible builds; SHA256 fingerprints in README.
- **Distribution:** GitHub Releases + F-Droid (verified-reproducible badge target) + IzzyOnDroid + Aurora Store (mirror) + Obtainium + Accrescent. **Not Google Play** by default — Play forces target-SDK churn and Integrity-API tradeoffs that conflict with the no-telemetry posture; revisit only if a separately-signed Play track is needed.
- **Strategic wedge:** *"What FlorisBoard wants to be when it grows up, plus the SwiftKey multilingual brain, plus FUTO's offline voice, with zero network, zero account, zero vendor cloud."* Every Now/Next item below either closes a SwiftKey/Gboard parity gap or extends the on-device-only frontier no commercial keyboard occupies.

---

## 2. State of the Repo (v1.7.9 reality, observed)

**Stack:** Kotlin 2.3.20 · Compose BOM 2026.03.01 · Material 3 + material-kolor · AGP 9.0.0 · JDK 17 · minSdk 26 (Android 8.0) · targetSdk/compileSdk 36 (Android 15) · Room 2.8.4 · **SQLCipher 4.16.0** (personal-dict encryption, N7.4) · Kotest 6.1.11 · KSP for Room compiler · AndroidX Benchmark 1.4.1 (macrobenchmark module wired but unused) · Crowdin translation pipeline.

**Modules:** `:app` · `:benchmark` · `lib/{android,color,compose,kotlin,native,snygg}`. Native module declared but currently disabled (commented `dummyAdd`); will host `whisper.cpp` for Next-2 embedded dictation.

**Source size:** **285 main Kotlin files · 60 test files** (up from 247 / 28 at v1.7.0; +38 main / +32 test in the v1.7.5→v1.7.9 sprint) · TODO/FIXME markers ~39 (down from 49). **0 crash-on-reach `TODO()` stubs** (both `KeyboardExtension.kt` and `LanguagePackExtension.kt` ship real `ExtensionEditor` implementations; `FlorisSpellCheckerService` TODO converted to a documented design choice). **356 unit tests** at HEAD, all green.

**Active components (v1.7.9):**
- **Tap typing** with QWERTY/QWERTZ/AZERTY + locale layouts (FlorisBoard inheritance) + bilingual subtype presets (v1.7.8 commit `0901301`).
- **Gesture / glide typing** in 6 languages (EN/DE/ES/FR/IT/PT) via `StatisticalGlideTypingClassifier` + per-language bounded vocabulary (N1.3 v1.7.x). Flow Through Space (N12.4), context rescoring for short glide words (N12.11), adaptive touch model feeds the classifier (N12.8).
- **Voice input** via FUTO Voice Input (external IME handoff, 100% offline Whisper-derived) + Next-2.1 RAM-aware embedded model selector + Next-2.2 streaming engine routing + Next-2.3 local model manager (Whisper/Vosk) + Next-2.4 streaming-transcript voice-command harness (`VoiceInputManager.consumeStreamingChunk`).
- **Voice commands** parser → executor → custom-storage → UI → fallback handling + per-chunk streaming-buffer matching with auto-execute on final chunks; see `VOICE_COMMANDS.md`.
- **English NLP:** 117,022-word SCOWL-merged dictionary + 130-entry contraction autocorrect + personal-learning dictionary (auto-promote typed words). **SQLCipher-encrypted** at rest (N7.4 v1.7.x). SymSpell d1 + bounded d2 (Next-1.A/B/C); Levenshtein-2 fallback. Capitalization-aware case matching pinned by tests (Next-3.3 v1.7.9).
- **Multilingual NLP (v1.7.5 wave + v1.7.x extensions):** adaptive touch model (N12.1), personal bigram (N12.2) + trigram (N12.5) stores, multilingual token scoring across active locale dictionaries (N2.1 + N12.3 + N12.12 + N12.21/24/27/28/29), cold-start phrase priors (N12.13 + N12.20 + N12.26), multi-word repair (N12.22 + N12.25), bilingual subtype presets (N2.3), top-two-language straddle guard (N2.2), trace replay fixtures + aggregate parity metrics (N12.16/17/19), scorer tuning explicit (N12.18).
- **Auto-cap** with sentence-end context detection (rejects `3.14`, `192.168.0.1`, `e.g.`, `U.S.A.`, ellipses) + sentence-case for next-word suggestions (N12.9).
- **Encrypted clipboard** (Room-backed; per-app tracking; max 50 items; AES-256-GCM `ClipboardHistoryManager` layer ships but unwired — README claim mismatch documented as a v1.8 task).
- **Themes:** Nord, Tokyo Night, Dracula, Catppuccin Mocha + SwiftKey Pure Light/Dark + theme editor + Snygg engine. Static contrast audit pins WCAG 2.1 AA 4.5:1 across all themes (N8.2 v1.7.x).
- **Smartbar profiles:** per-app prioritization (v1.7.8) — PASSWORD / CHAT / EMAIL / **CODE** (Next-8.1+8.2 v1.7.9: Termux / JuiceSSH / Acode / ConnectBot / Termius / JetBrains family auto-activate Tab/Esc/arrows/line-jump).
- **Inline autofill:** `supportsInlineSuggestions=true` + `InlinePresentationRenderer` slot in smartbar + verified compat with Bitwarden / KeePassDX / Proton Pass / 1Password / Aegis (Next-9.1/9.2/9.3 v1.7.9; matrix in `docs/INLINE_AUTOFILL.md`).
- **Addon ecosystem (foundation):** `ime/addon/` package — `AddonContract` (intent actions + meta-data schema), signature-protected `permission.REGISTER_ADDON`, `AddonEnumerator.snapshot()` with no-INTERNET hard reject at enrolment (Next-10.1+10.2 v1.7.9).
- **Per-app adaptive accent (foundation):** `PerAppAccentResolver` — HSV-quantize editor's app icon → dominant saturated color, LRU(64) cache (Next-11.3 v1.7.9; surface wiring deferred).
- **Migration:** Gboard `PersonalDictionary.zip` (XML inside zip) + FlorisBoard CSV / SQLite-snapshot routing; honest SwiftKey writeup at `docs/MIGRATE_FROM_SWIFTKEY.md` (Next-6.1/6.2/6.3 v1.7.9).
- **Privacy hardening (N7):** no-INTERNET Gradle gate (N7.1), `FLAG_SECURE` on password fields + `IME_FLAG_NO_PERSONALIZED_LEARNING` defense-in-depth (N7.2), threat-model + isolation tests (N7.3), SQLCipher personal-dict (N7.4), signing-fingerprint surface in About (N7.5).
- **Reproducible builds:** toolchain pinned in `gradle.properties` + `tools.versions.toml` + Gradle wrapper SHA-256 (N6.3 partial); `docs/REPRODUCIBLE_BUILDS.md` documents the recipe.
- **Settings UX:** premium-polish pass landed; per-app smartbar profile toggles; typing-stats screen with local reset controls (N12.6 + N12.14); trace capture + share workflow (N12.15 + N12.19).

**Stubbed / under-investigated (block several Now items):**
- ~~`LanguagePackExtension.kt:62`, `KeyboardExtension.kt:55`~~ — **resolved in v1.7.0** (N11).
- ~~`FlorisSpellCheckerService.kt:141`~~ — **resolved in v1.7.0** (N11.3, documented delegate to AOSP).
- ~~`HanShapeBasedLanguageProvider.kt:88, 99, 103`~~ — **resolved in v1.7.6 hardening pass** (commit `920da85` — `@Volatile` + `synchronized(loadLock)` around `LanguagePack.load`).
- ~~`ThemeManager.kt:138-139` leaks loaded theme dir on hot-reload~~ — **resolved in v1.7.6 hardening pass** (sweep stale dirs on init + delete evicted dirs).
- `ImeWindowMode.kt:56` — THUMBS sub-mode placeholder (cosmetic). FLOATING mode is wired (Next-7.1 audit), just needs UX polish.
- `FlorisLocale.kt:217, 227` — hard-coded `supportsCapitalization` / `supportsAutoSpace` per-language tables; ICU replacement open.
- `TextKeyboardLayout.kt:247` — `constraints.maxWidth not stable` in landscape rotation (requires layout rewrite).
- `ClipboardHistoryManager.kt` — AES-256-GCM encrypted-prefs layer ships in tree but is **not wired** to any caller. README header says "AES-256 GCM, military-grade protection" but the wired `ClipboardManager.kt` is Room-backed (Android FBE at rest). Either wire that class in OR soften the README claim. Requires user decision (marketing copy).

**CI:** GitHub Actions sequences `verifyNoInternetPermission` → `:app:testDebugUnitTest` → `:app:lintDebug` → `:app:assembleDebug` on every PR/push (v1.7.0 N6.1). Lint + test reports upload as artifacts. Release workflow signs with keystore secrets, builds unsigned APK fallback when secrets absent (v1.7.0 N6.2 + v1.7.9 R8 missing-class fix for Tink). Dependency CVE scan via GitHub `dependency-review-action` + `osv-scanner-action` (v1.7.2 N6.4). Crowdin upload on `strings.xml` push; translation-included guard on PRs. **Reproducibility verification on F-Droid pending** (N6.3 part 2).

**Distribution today:** GitHub Releases (v1.7.0–v1.7.8 published; v1.7.9 unsigned-only, signing secrets needed). **Obtainium one-tap URL in README** (N6.5 v1.7.0). `fastlane/` directory present but no F-Droid metadata `fdroiddata` PR yet (N6.3 part 2).

---

## 3. Recently Shipped (v1.5.0 → v1.7.9, reconciled from prior ROADMAP v4.0 + v1.7.6/7/8/9 commits)

| Version | Date | Headline | Source |
|---|---|---|---|
| v1.7.9 | 2026-05-14 | 19-item ROADMAP pass: N3.4 finish (popup polish), N8.3 finish (a11y labels + hint), Next-2.4 streaming voice commands, Next-3.3 capitalization-aware tests, Next-3.4 Remove-from-predictions overlay, Next-6.1/6.2 Gboard+FlorisBoard backup importer, Next-6.3 SwiftKey migration doc, Next-7.3 one-handed UX audit, Next-8.1+8.2 CODE smartbar profile, Next-9.1/9.2/9.3 inline-autofill verified+doc, Next-10.1+10.2 addon schema+enumerator, Next-11.2 springy dismiss, Next-11.3 per-app accent resolver, Next-12.3 property-based autocorrect tests, R8 fix for Tink missing classes | `RELEASE_NOTES_v1.7.9.md` |
| v1.7.8 | 2026-05-13 | Bilingual subtype presets, bottom row presets, per-app smartbar profiles, encrypted personal dictionary (SQLCipher), emoji search + bundled sticker packs, voice model tier selector, Vosk fallback routing, local voice model manager, multilingual autocommit straddle guard, Latin language identification, glide vocabulary bounding, dark-theme key contrast guard, distance-2 SymSpell, expanded multilingual context depth, multilingual autocorrect confidence gating, shared-bilingual literal protection, SwiftKey glide contraction rescue, expanded multi-word phrase repairs, bilingual language switches, three-word run-together repair, sentence-local SwiftKey context scoring, phrase priors fed into scoring | `RELEASE_NOTES_v1.7.8.md` (release notes file rebadged to v1.7.9 after pre-existing v1.7.8 tag collision) |
| v1.7.7 | 2026-05-13 | SwiftKey decoder scoring extracted; replay harness landed; adaptive touch persistence; SwiftKey quick-prediction insert; spacebar prediction alignment; touch evidence in decoder | `RELEASE_NOTES_v1.7.7.md` |
| v1.7.6 | 2026-05-13 | Three-round hardening audit: clipboard JSON corruption (kotlinx.serialization migration + synchronized writes), ThemeManager disk leak (cache sweep on init + per-evicted-theme cleanup), HanShapeBasedLanguageProvider race (@Volatile + synchronized(loadLock)), FlorisImeService.onDestroy unregisterReceiver crash, floris_user_dictionary removed from cloud-backup paths (kept device-transfer), profileable moved to debug+benchmark overlays, NlpManager.frequencyCache LruCache(5000), TypingStatsScreen double-query elimination, InputFeedbackController scope leak fix, FlorisCopyToClipboardActivity OOM fix, .gitignore Rust pattern narrowed, Settings polish (`cd770c`, `1e0e9d9`, `69def6a`) | `RELEASE_NOTES_v1.7.6.md` |
| v1.7.5 | 2026-05-09 | SwiftKey indistinguishability wave: N12.1 adaptive touch, N12.2 bigram next-word, N12.3 multilingual hot-switch, N12.4 Flow Through Space, N12.5 trigram, N12.6 stats screen, N12.7 cold-start bootstrap, N12.8 adaptive touch → glide, N12.9 sentence-case, N12.10 long-press to forget, Next-1.A SymSpell d1, Next-1.B d2 high-freq auto-commit | `RELEASE_NOTES_v1.7.5.md` |
| v1.7.4 | 2026-05-09 | N7.2 final piece (FLAG_SECURE on IME window in password fields), N6.3 partial (toolchain pins + `docs/REPRODUCIBLE_BUILDS.md`), N4.1 + N8.6 verified-already-shipped | `RELEASE_NOTES_v1.7.4.md` |
| v1.7.3 | 2026-05-09 | N6.2 release workflow + signing fallback, N8.1 48dp touch-target WCAG regression test (`TouchTargetWcagTest`), N8.3 TalkBack content descriptions per key | `RELEASE_NOTES_v1.7.3.md` |
| v1.7.2 | 2026-05-09 | N6.4 dep-CVE scan workflow, N3.3 SwiftKey haptic defaults (20ms / 60), N3.4 popup elevation 2dp→4dp; N5.2/N3.5/N8.5/N10.2 verified-already-shipped triage | `RELEASE_NOTES_v1.7.2.md` |
| v1.7.1 | 2026-05-09 | Same-day follow-up: N3.1 SwiftKey Pure themes, N5.3 keyboard-height slider, N7.3 personal-dict isolation regression test + threat-model doc, N8.4 reduced-motion guard, N8.5 switch-access verified, N9.1/N9.2 commitContent verified | `RELEASE_NOTES_v1.7.1.md` |
| v1.7.0 | 2026-05-09 | Correctness floor + privacy hardening + SwiftKey-parity polish (closes Now items N11/N7.1/N7.2/N7.5/N6.1/N6.5/N5.1/N5.4/N10.3/N3.2) | `RELEASE_NOTES_v1.7.0.md` |
| v1.6.0 | 2026-05-08 | Personal-learning dict + 117k SCOWL English + SwiftKey design tokens (#319DFF accent, Pure-theme palette, key dims) | `RELEASE_NOTES_v1.6.0.md` |
| v1.5.5 | 2026-05-09 | 130-entry contraction autocorrect (two-tier safety: SAFE / DICTIONARY_GATED); ALL-CAPS skip; sentence-start case preservation | `RELEASE_NOTES_v1.5.5.md` |
| v1.5.4 | 2026-05-08 | Auto-cap context check (rejects digits/abbreviations/ellipses); decoupled auto-cap from auto-space; pronoun substitution dictionary-gated; new `correction.autoCorrect` toggle | `RELEASE_NOTES_v1.5.4.md` |
| v1.5.3 | 2026-05-05 | Sentence-start pronoun autocorrect handling | `RELEASE_NOTES_v1.5.3.md` |
| v1.5.2 | 2026-05-04 | Latin autocorrect/suggestions fix; ANR hardening; lint cleanup | commits `f041be5`, `835d601` |
| v1.5.0 | 2026-04 | FUTO Voice Input integration (replaced Google Speech Recognizer); voice command parser/executor; multi-lang gesture dictionaries; NLP refactor (registry/assembler split) | commits `0c79ea1`, `49ba608` |
| v1.4.0 | 2026-04 | Gesture typing stabilization; per-language glide controls | commits `e707141`, `cd14f86` |
| v1.3.0 | 2026-04 | Voice input v1; multilingual testing | commits `1584691`, `83336e3` |
| v1.2.0 | 2026-04 | Auto-cap parity v1 with SwiftKey | commits `c18b47b`, `373c5e1` |
| v1.1.0 | 2026-03 | SwiftFloris fork from FlorisBoard; rebrand; haptic strength enhancement | commits `d4905b9`, `0c7265f` |

---

## 4. Strategic Thesis

The Android keyboard market has bifurcated into two camps: **(a) Apple/Samsung — on-device AI but locked to vendor silicon**, and **(b) Microsoft/Google/Grammarly — cloud-bound and account-bound**. There is no third option that runs anywhere, syncs P2P, and ships under Apache-2.0. SwiftFloris occupies that gap.

Three forces drive this roadmap:

1. **Upstream drift.** FlorisBoard v0.6-alpha targets glide typing, predictions, floating mode, and Snygg v2 themes [F1, F2]; SwiftFloris is already past upstream on autocorrect + dictionary, but behind on glide breadth, floating mode, and emoji-search. Whoever ships HeliBoard's NLnet-funded open swipe library + dataset first wins the next migration wave [H1].
2. **Commercial collapse signals.** Microsoft removed in-keyboard Copilot in 2025 and is forcing SwiftKey users onto a Microsoft account by 2026-05-31 [C1, C2, C3]. Google nerfed Gboard offline voice for non-Pixel devices [GBOARD-VOICE]. Grammarly is shutting down its standalone keyboard [C8]. Every event above is a switch-trigger users name explicitly in r/SwiftKey, HN, and Privacy Guides forums [PAIN-1 through PAIN-5].
3. **On-device LLM viability.** Gemma 3 270M Q4 (~135MB, <1% Pixel 9 Pro battery for 25 conversations [AI1, AI2]) and Whisper Large-v3-Turbo INT8 (~315ms per Snapdragon 8 Elite [AI3]) make smart-compose and dictation shippable on flagship devices in 2026 *without* the cloud — the moat closing window is open right now.

The wedge: ship every paywalled cloud feature **fully on-device, fully auditable, with zero account requirement.**

---

## 5. Tier System

- **Now** — committed for v1.7.0–v1.8.0 (next ~3 months, Q3 2026). High user value, modest engineering cost, fits stack and philosophy. Build started or imminent.
- **Next** — committed for v1.9.0–v2.0.0 (Q4 2026 – Q1 2027). High value, larger cost or platform-readiness gate. Investigated, scoped, deferred only on capacity.
- **Later** — desirable for v2.1+ (2027). Either large engineering effort, dependency on external work (HeliBoard NLnet drop, Android 17), or speculative-but-promising.
- **Under Consideration** — no commitment. Listed because users asked or the strategic case is intriguing; will graduate to Next/Later or retire to Rejected with a written verdict.
- **Rejected** — explicit no, with reasoning. Listed so future contributors don't re-litigate.

Source-citation rule: every item carries `[n]` keyed to §10 Appendix. If `[n]` is absent, that item came from internal repo state (e.g. an existing TODO marker) and is referenced by its file:line.

---

## 6. NOW (v1.7.0 → v1.8.0, target Q3 2026)

Eleven themes. Each item is small enough to land in a single PR, large enough to be roadmap-worthy, and grouped so a contributor can pick a theme and ship its bullets together.

### N1. Glide-typing breadth without the GApps blob

The single highest-leverage gap. Three credible paths; pick one in this order of preference:

- **N1.1** Wait-and-integrate: monitor [HeliBoard NLnet open-glide project](https://github.com/HeliBorg/HeliBoard/issues/2226) [H1, NLNET-GT, COMM-3]. **Status 2026-05-14 (refreshed for v5.0):** NLnet-funded R&D project formally runs Jun 2025 → Jun 1 2026 [NLNET-GT]; HeliBoard v3.7-beta1 (2026-02-22) ships **optional gesture data gathering** matching the NLnet plan to collect volunteer samples [H1]. Library not yet released as a drop-in for the closed Google `swypelibs` blob, but data accrual is live, putting library release in the H2 2026 window. SwiftFloris stays on the wait-and-integrate path; integration gate is a `prefs.glide.engine` flag (`heliboard-open` | `swiftfloris-statistical`). Integration cost: M when library lands.
- **N1.2** Port-CleverKeys-architecture: CleverKeys ships a 5.4MB encoder + 7.4MB decoder ONNX transformer that handles 11 languages with sub-200ms beam-search latency on Pixel 7 via XNNPACK [O1, AI4, CK-DEEPWIKI]. Code is **GPL-3.0** (re-confirmed 2026-05-14 — incompatible with the Apache-2.0 main-app ceiling); cannot directly link, but the *architecture and training repo* are public reference. CleverKeys's own roadmap targets multi-layout / multi-script gesture model in **Q2-Q3 2026** [CK-DEEPWIKI] — once that drops, the architectural reference for non-QWERTY layouts becomes much more useful. Plan: train an Apache-2.0 model from the eventual HeliBoard NLnet dataset (N1.1) and ship via ONNX Runtime Mobile. Cost: L.
- ✅ **N1.3** shipped 2026-05-14. The existing `StatisticalGlideTypingClassifier` now consumes a bounded high-confidence glide vocabulary from each imported per-language dictionary instead of the full long-tail recognition map: frequency `80+`, length `2..24`, max `120k` words per language. This keeps rare recognition-only words from polluting swipe candidates while preserving broad EN/DE/ES/FR/IT/PT coverage. Settings → Gestures now shows per-language glide quality and engine labels (`Expanded statistical dictionary` / `Imported statistical dictionary`, `Statistical`) so users can see the current non-neural state while the HeliBoard neural/open-glide path remains pending.

Rejected sub-path: bundle Google's `libjni_latinimegoogle.so` from old GApps dumps. Violates the no-blob clause; HeliBoard's reluctant carrier-pigeon distribution of this file is exactly what auditability means to reject.

### N2. Multilingual auto-detect (per-token language identification)

The killer SwiftKey feature with no first-class FOSS equivalent [C1, COMM-2]. Users in r/SwiftKey, HN 35597622, and HeliBoard #2124 [PAIN-5, COMM-2] all report the same complaint: typing two languages bleeds wrong-language autocorrects mid-sentence.

- ✅ **N2.1** shipped 2026-05-14. Added an offline Latin-script language identifier over the current token plus the trailing four sentence-local context words. The first implementation is a compact per-language char n-gram/common-word/prefix classifier for enrolled EN/ES/FR/DE/IT/PT subtypes, with accent folding and BCP-47 language normalization. `NlpManager` now feeds those confidence scores into `MultilingualTokenScorer`, letting the ranker boost detected-language candidates and demote inactive-language autocorrects before context-only heuristics run.
  Follow-up scale paths remain a compact langid library (e.g. CLD3 port, ~2MB + 70 langs), an Apache-compatible fastText `lid.176` quantized model [AI5] (~1MB INT8), or a custom trained char-trigram profile set that fits in <200KB.
- ✅ **N2.2** shipped 2026-05-14. Completed the earlier N12 partials by labeling each candidate with its dominant enrolled dictionary language and adding a top-two language-straddle guard to `SwiftKeyCandidateRanker.selectSpacebarCandidate(...)`. Multilingual subtypes now rank per-token across active locale dictionaries, preserve the existing language-confidence demotion for weak inactive-language corrections, and refuse spacebar/punctuation autocommit when the two strongest plausible replacement candidates come from different enrolled languages.
- ✅ **N2.3** shipped 2026-05-14. Added first-class bilingual subtype presets for canonical SwiftKey-style EN+ES, EN+FR, and EN+DE typing. `SubtypePreset` now persists secondary locales into created subtypes, `BilingualSubtypePresets` synthesizes stable pairs from the installed preset catalog, and Settings → Localization now surfaces Bilingual presets plus the selected secondary language in both the editor and subtype list/delete confirmation. This gives users a one-time bilingual setup path without manual language switching while reusing the existing multilingual NLP path from N2.1/N2.2.

### N3. SwiftKey-parity surface polish (close the visual gap)

v1.6.0 shipped accent (`#319DFF`) + Pure palette tokens + dimens bumps. Finish:

- ✅ **N3.1** shipped 2026-05-09 (v1.7.x). Two new theme stylesheets registered in `org.florisboard.themes/extension.json`: **SwiftKey Pure (Light)** and **SwiftKey Pure (Dark)**. Each stylesheet branches off the existing `swift_glacier` / `swift_slate` skeleton with the `@defines` block rewritten to consume the `swiftkey_pure_*` tokens from `colors_branding.xml` (light: `#E1E4E8` kbd / `#FFFFFF` keys / `#BFC4CC` special / `#1F1F1F` text / `#7A7E85` hint; dark: `#1F1F1F` kbd / `#2C2C2E` keys / `#3A3A3C` special / `#F2F2F2` text / `#8E8E93` hint; both share the SwiftKey 2020+ accent `#319DFF`). Themes appear in Settings → Theme picker and inherit the N3.2 `FontWeight.Medium` glyph weight automatically.
- ✅ **N3.2** shipped 2026-05-09 (v1.7.0). `FlorisImeUi.Key.elementName` base style in `FlorisImeThemeBaseStyle.kt` now sets `fontWeight = fontWeight(FontWeight.Medium)` (weight 500). Applies to every shipped theme (Nord/Tokyo Night/Dracula/Catppuccin/SwiftKey-Pure) since they all inherit from the base style. Closes the SwiftKey perceived-quality gap without changing key dimensions or layouts.
- ✅ **N3.3** complete — partial shipped 2026-05-09 (v1.7.x: 20ms / 60-strength SwiftKey/Gboard defaults), **N3.3a finish shipped 2026-05-15**. `Vibrator.vibrate(duration, strength, factor)` in `lib/android` is now a three-tier path: (1) Android 16 PWLE `VibrationEffect.WaveformEnvelopeBuilder` via reflection (so the call compiles on every build-tools SDK) when `Vibrator.areEnvelopeEffectsSupported()` reports true — builds a three-control-point amplitude envelope (rise → plateau → settle) for a crisp tactile keypress shape; (2) Android 11+ `VibrationEffect.startComposition().addPrimitive(PRIMITIVE_TICK / PRIMITIVE_CLICK / PRIMITIVE_LOW_TICK, amplitudeScale)` chosen by `factor` (long-press repeat → LOW_TICK; long-press / swipe → TICK; standard keypress → CLICK), gated on `areAllPrimitivesSupported(primitive)`; (3) legacy `VibrationEffect.createOneShot(duration, amplitude)` as a final fallback. New `AndroidVersion.ATLEAST_API36_BAKLAVA` gate added. Net effect: users on Android 11-15 get richer composition primitives today; Android 16+ users get true PWLE envelopes when their actuator supports them. [STD-A16-PWLE].
- ✅ **N3.4** complete — partial shipped 2026-05-09 (v1.7.x: popup elevation 2dp→4dp); **finish shipped 2026-05-14 (v1.7.9)**. `animateFloatAsState`-driven 1.03× scale-up over 60ms with 80ms spring-back on release in `TextKeyButton` (`graphicsLayer` so the touch-target geometry doesn't shift); reduced-motion gate via the existing N8.4 `ANIMATOR_DURATION_SCALE` read (when scale = 0 the spring is suppressed but the PRESSED Snygg color flip still reads). Long-press popup variant carries a 1.5dp accent-ring via `borderColor = var(--primary)` / `borderWidth = 1.5dp` on the `KeyPopupElement` FOCUS selector — per-theme stylesheets that override `--primary` retint the ring automatically (SwiftKey Pure uses #319DFF; Tokyo Night uses its own accent).
- ✅ **N3.5** resolved 2026-05-09 (v1.7.x) by documentation. Verified that `R.dimen.key_height` is *not* directly consumed by `ImeWindowConstraints.defKeyboardHeight` — that calculation is form-factor-derived (`screenHeight * factor`). On a typical 6-7" phone in portrait the resulting per-row height lands at ~52..58dp, bracketing the 56dp SwiftKey reference. Updated `dimens.xml` comment to accurately frame the token as a spec reference (not consumed) and cross-reference N5.3's user-facing percentage slider (50..150%) which is now the supported way to adjust keyboard height. A future "form-factor-aware floor" (`key_height × rows`) is captured as N3.5 follow-up but not blocking; the current behavior already meets SwiftKey-parity on common phones.

### N4. Customizable bottom row + smartbar

ASK #1832 / HeliBoard #695 / FlorisBoard #196 — the most-requested customization across all OSS keyboards [COMM-A, COMM-B, COMM-C, PAIN-20].

- ✅ **N4.1** verified shipped (inherited FlorisBoard upstream + verified 2026-05-09 v1.7.x). `QuickActionsEditorPanel.kt:278` already implements long-press drag-and-drop reordering of smartbar actions via `detectDragGesturesAfterLongPress` over a `LazyVerticalGrid`. Sticky-action slot, primary actions, and overflow grid all support drag. UI accessible via `Settings → Smartbar → Customize quick actions`.
- ✅ **N4.2** shipped 2026-05-14. Added a JSON-backed bottom-row preset override while preserving the current asset-defined row as `Automatic`. Settings → Keyboard now offers SwiftKey-style, language-picker, voice-key, settings-shortcut, and minimal rows; each preset can add/remove the language picker, comma/period cluster, emoji/media key, voice key, and settings shortcut without touching layout assets. `LayoutManager` applies the override only in `KeyboardMode.CHARACTERS`, clears the character-keyboard cache when the preset changes, and keeps the legacy utility-key behavior intact unless the user chooses an explicit preset.
- ✅ **N4.3** shipped 2026-05-14. Added optional per-app Smartbar profiles that re-prioritize quick actions at render time without rewriting the saved arrangement. Password fields surface paste/incognito/hide/settings; Slack/WhatsApp/Telegram/Signal/Discord and short-message fields surface media/voice/paste/language/autocorrect; Outlook/Gmail/email fields surface paste/voice/language/cursor/settings. The setting lives under `Settings → Smartbar → Per-app profiles` and defaults on. `SmartbarActionProfilesTest` covers disabled passthrough, password, chat, email, and unmatched editor profiles.

### N5. Word-edit ergonomics (long-asked-for, mostly small fixes)

- ✅ **N5.1** shipped 2026-05-09 (v1.7.0). Wiring was already present (`KeyboardManager.handleSwipe → SwipeAction.DELETE_WORD → TextKeyData.DELETE_WORD → handleBackwardDelete(OperationUnit.WORDS)`). Flipped the prefs defaults in `AppPrefs.Gestures`: `deleteKeySwipeLeft` `DELETE_CHARACTERS_PRECISELY → DELETE_WORD`, `deleteKeyLongPress` `DELETE_CHARACTER → DELETE_WORD`. SwiftKey/Gboard parity. Existing user overrides preserved (jetpref only falls back to default when no value is set).
- ✅ **N5.2** verified shipped (inherited FlorisBoard upstream + verified 2026-05-09 v1.7.x). `TextKeyboardLayout.handleSpaceSwipe` already implements continuous SwiftKey-style cursor drag: `TOUCH_MOVE` events on space convert finger displacement (`relUnitCountX`) into per-cell `keyboardManager.handleArrow` calls, gated by `spaceBarSwipeLeft == MOVE_CURSOR_LEFT` / `spaceBarSwipeRight == MOVE_CURSOR_RIGHT` (both defaults). Mass-selection batching via `editorInstance.massSelection.begin()` so multi-cell drags coalesce into one editor update. User-facing toggle = the existing `Settings → Gestures → Space bar swipe (left/right)` preference; setting either to `NO_ACTION` disables cursor mode for that direction.
- ✅ **N5.3** shipped 2026-05-09 (v1.7.x). Font-size slider was already wired (`fontSizeMultiplierPortrait` / `Landscape`, 50..150% in 5% steps). Added the height slider this release: new `AppPrefs.Keyboard.keyboardHeightMultiplierPortrait` + `keyboardHeightMultiplierLandscape` (range 50..150, default 100), threaded through `ImeWindowController.userPreferredOptions` flow → `ImeWindowSpec.UserPreferredOptions.keyboardHeightScale` → applied in `doComputeWindowSpec` *before* `props.constrained(constraints)` so the [`minKeyboardHeight`, `maxKeyboardHeight`] clamp catches absurd slider values. Floating mode skips the slider (it has its own resize affordance). New `DialogSliderPreference` in `KeyboardScreen.kt` directly under the font-size slider.
- ✅ **N5.4** shipped 2026-05-09 (v1.7.0). Settings UI was already present (`UserDictionaryScreen` Add/Edit dialog has Word + Freq + **Shortcut** + Locale fields with validation). Wired the auto-replace half: added `DictionaryManager.queryUserDictionaryShortcutExact(query, locale)` which returns the highest-frequency expansion when [query] is an exact (case-insensitive) match against any enabled personal-dictionary shortcut. `NlpManager.getAutoCommitCandidate` now consults this *before* in-strip suggestions and the English contraction fallback — user-defined shortcuts beat algorithmic guesses. Suppression-cooldown still applies (deleting an auto-replaced shortcut won't re-trigger). Try it: open Settings → Typing → Personal dictionary → Add `omw` shortcut for `on my way`, then type `omw ` in any field.

### N6. CI + release engineering hardening

The current GitHub Action runs `assembleDebug` only — tests, lint, and reproducibility are ungated. This is a quality gate that costs CI minutes, not engineering velocity.

- ✅ **N6.1** shipped 2026-05-09 (v1.7.0). `.github/workflows/android.yml` now runs (in order) `:app:verifyNoInternetPermission` (N7.1 fail-fast), `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`. Lint + test reports upload as artifacts on every run (`if: always()`) for triage. Renamed workflow to `SwiftFloris CI`. Cache wired via `gradle/actions/setup-gradle@v4`.
- ✅ **N6.2** shipped 2026-05-09 (v1.7.x). New `.github/workflows/release.yml` (`workflow_dispatch` trigger, `version` + `draft` inputs). Pipeline: (1) verify `gradle.properties projectVersionName` matches input; (2) NOTICE / LICENSES presence check (Apache-2.0 attribution); (3) re-run N7.1 no-network gate, unit tests, lint; (4) decode keystore from `SIGNING_KEYSTORE_BASE64` env to a runner-temp path; (5) `:app:assembleRelease` consumes `KEYSTORE_PATH` + `SIGNING_*` env vars via the new `signingConfigs.create("release")` block in `app/build.gradle.kts`; (6) compute SHA-256 manifest, append to GH job summary; (7) `gh release create` with the signed APK + SHA256SUMS + RELEASE_NOTES_v*.md. Fallback path: when `SIGNING_KEYSTORE_BASE64` is empty (forks running their own dispatch), the release variant uses the debug signing config so the build still validates end-to-end. Repo secrets needed: `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. F-Droid metadata bump and `fdroiddata Builds:` block stays a separate N6.3 item.
- ✅ **N6.3** partial — shipped 2026-05-09 (v1.7.x). All toolchain inputs are pinned: Gradle 9.4.1 with SHA-256 distribution checksum (`gradle-wrapper.properties`), AGP 9.0.0, Kotlin 2.3.20, KSP, Build Tools 36.0.0, NDK 29.0.14206865, JDK 17 Temurin (CI), cmake 4.1.2, cmdline tools with SHA-256 pin (`tools.versions.toml`). New `docs/REPRODUCIBLE_BUILDS.md` documents the full pin matrix, local verification recipe (build + `apkdiff` recipe), and a copy-pastable F-Droid `Builds:` stanza for the upstream `fdroiddata` submission. **Pending:** `fdroiddata` PR submission and F-Droid build-server rebuild result — that step happens on the F-Droid side.
- ✅ **N6.4** shipped 2026-05-09 (v1.7.x). New `.github/workflows/dependency-scan.yml`: (a) GitHub `actions/dependency-review-action@v4` on every PR that touches `libs.versions.toml`, `tools.versions.toml`, or any `build.gradle.kts` — fails on HIGH or CRITICAL severity, comments summary on PR; (b) `google/osv-scanner-action@v2.0.2` recursive scan as a SBOM-level cross-check that goes beyond the GitHub vulnerability database; (c) cron Sundays 06:00 UTC for proactive drift detection; (d) workflow_dispatch for manual runs. Gradle dep-tree uploaded as artifact for triage.
- ✅ **N6.5** shipped 2026-05-09 (v1.7.0). README "Installation" now leads with **Option A — Obtainium**: a copy-pastable `obtainium://app/{...}` URL that auto-subscribes to the GitHub Releases feed (`apkFilterRegEx: app-release.*\.apk`, `versionDetection: true`, `fallbackToOlderReleases: true`). Manual GH-Releases and source build flows demoted to Options B / C.

### N7. Privacy hardening (the moat verbatim)

- ✅ **N7.1** shipped 2026-05-09 (v1.7.0). Pre-build Gradle verification task `:app:verifyNoInternetPermission` (registered in `app/build.gradle.kts`) scans every `AndroidManifest.xml` under `app/src/**` and fails the build if any of `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE` is declared. Wired into `preBuild` via `afterEvaluate`, so every variant build (debug/beta/release/benchmark) re-runs the check. Verified by adding INTERNET temporarily — task fails with a clear contract-violation message; removing it restores green build. Chose Gradle task over a custom lint `Detector` module because it's strictly less infrastructure for an identical fail-fast outcome and keeps `:app:lintDebug` clean. (Pin the no-network promise in code, not just in marketing [STD-CAKI, STD-NO-INTERNET].)
- ✅ **N7.2** complete — shipped across v1.7.0 + v1.7.x. Already in place from FlorisBoard upstream: suggestions disabled when `keyVariation == PASSWORD` (composing flagged off), incognito triggered by `IME_FLAG_NO_PERSONALIZED_LEARNING`. v1.7.0 added (a) `KeyboardManager.learnIfAllowed` extra `keyVariation == PASSWORD` gate (defense-in-depth vs apps that forget `IME_FLAG_NO_PERSONALIZED_LEARNING` — HeliBoard #2124, AnySoftKeyboard #1399); (b) `EditorInstance.performClipboardCut`/`performClipboardCopy` skip the IME-local clipboard history on password fields. v1.7.x adds (c) `FlorisImeService.applyFlagSecureForCurrentField` — sets `WindowManager.LayoutParams.FLAG_SECURE` on the IME window when the active variation is `PASSWORD`/`VISIBLE_PASSWORD`/`WEB_PASSWORD`, clears it otherwise. Prevents screenshots, screen recordings, and external display mirroring from capturing the long-press popup or suggestion strip during credential entry, while still allowing screenshots in normal fields.
- ✅ **N7.3** shipped 2026-05-09 (v1.7.0). New `docs/THREAT_MODEL.md` enumerates threat actors (co-installed apps, CAKI, supply-chain, hostile editors), live defenses (no-network contract, password-field hardening, encrypted clipboard, signing-fingerprint pin, personal-dict isolation), known gaps (FLAG_SECURE on popups, reproducible-build, SQLCipher), and a per-release verification checklist. New `PersonalDictionaryIsolationTest` (Kotest `FunSpec`) proves by static-content inspection of `DictionaryManager.kt` that `learnWord`'s body never references `systemUserDictionaryDao` / `systemUserDictionaryDatabase` / platform `UserDictionary.Words` — any future contributor breaking the contract gets a clear test failure with N7.3 cited in the message. All three tests pass.
- ✅ **N7.4** shipped 2026-05-14. The Floris personal Room dictionary now opens through SQLCipher (`net.zetetic:sqlcipher-android` 4.16.0 + `androidx.sqlite` 2.6.2) via `SupportOpenHelperFactory`. A 64-byte passphrase is generated locally, synchronously persisted in `EncryptedSharedPreferences`, and protected by `MasterKey` AES256_GCM. Existing plaintext databases are detected by SQLite header, staged, migrated into encrypted storage, and restored if migration cannot complete. Backup rules now omit the encrypted dictionary from device/cloud transfer because the Android Keystore-protected key is intentionally non-portable. `PersonalDictionaryEncryptionTest` guards the SQLCipher, Keystore, dependency, backup-rule, and header-detection contracts [STD-PERS-DICT-ENC].
- ✅ **N7.5** shipped 2026-05-09 (v1.7.0). New `SigningFingerprint.sha256(context)` reads `PackageManager` signing info (API 28+ `GET_SIGNING_CERTIFICATES` with API 26/27 `GET_SIGNATURES` fallback), digests via `MessageDigest.SHA-256`, formats as `AB:CD:…` matching `apksigner verify --print-certs` / F-Droid metadata convention. New "APK signing fingerprint" preference in `AboutScreen` shows the fingerprint (computed off-main-thread via `Dispatchers.IO` in a `LaunchedEffect`); tap to copy. Three states: loading / value / unavailable. Detects supply-chain swap when compared against the value the maintainer publishes in README.

### N8. Accessibility scoped pass

The IMPROVEMENT_PLAN already has this as Workstream 6 (Planned, P1) — promote the concrete deliverables to roadmap items.

- ✅ **N8.1** shipped 2026-05-09 (v1.7.x). New `TouchTargetWcagTest` (Kotest, 4 tests, all passing) pins WCAG 2.5.5 AAA per-key 48dp floor for PHONE_PORTRAIT default + max keyboard heights at the typical 360×800dp form factor. Landscape phones (vertically constrained — only ~360dp of usable height) hold the line at WCAG 2.5.8 AA's 24dp floor (industry-standard for landscape keyboards). `ImeWindowConstraints.resizeHandleTouchSize` (48dp) audited too. Future contributor lowering any form-factor factor will get a clear test failure with WCAG citation in the test name.
- ✅ **N8.2** shipped 2026-05-14. Static contrast audit now covers Catppuccin Mocha + Tokyo Night keyboard text tokens against keyboard surface/background tokens at WCAG 2.1 AA 4.5:1. Tokyo Night secondary/tertiary text now uses `#9aa5ce`; Catppuccin tertiary text now uses `catppuccin_subtext0`. New `ThemeContrastTest` resolves XML `@color/...` references and fails if any key-glyph token pair regresses [STD-WCAG-CONTRAST].
- ✅ **N8.3** complete — partial shipped 2026-05-09 (v1.7.x: key-level descriptions); **finish shipped 2026-05-14 (v1.7.9)**. `QuickActionButton` now carries a TalkBack `contentDescription` derived from `action.computeDisplayName(evaluator)` → tooltip fallback → "Action" (every smartbar slot announces something descriptive instead of "button"). `CandidatesRow.CandidateItem` carries a candidate-text `contentDescription`, `role = Role.Button`, and — for eligible candidates — a `CustomAccessibilityAction("Remove from predictions", action = { onLongPress() })` so screen-reader users can trigger the Next-3.4 forget flow without the long-press gesture. `keyContentDescription` appends `", alternative: <hint>"` when a key carries a hinted alt-glyph (the small top-right corner glyph), so TalkBack announces "a, alternative: à" — mirrors Samsung Keyboard / Gboard alt-glyph announce.
- ✅ **N8.3a** shipped 2026-05-15. The English fallback labels (`Shift`, `Backspace`, `Enter`, ...) are now Crowdin-routed string resources (`R.string.a11y__key__shift` etc., 33 entries plus an `a11y__key__alternative_suffix` format string for the hint-suffix). `keyContentDescription(context, code, label, hintedLabel)` signature now takes a `Context` and resolves every code-keyed label through `res.getString(resId)`, with the alternative-suffix format applied via `res.getString(R.string.a11y__key__alternative_suffix, hintedLabel)`. Non-English users now get localised announcements at the same time the rest of the UI changes locale (no separate Settings → A11y locale dance required).
- ✅ **N8.4** shipped 2026-05-09 (v1.7.x). `TextKeyboardLayout` reads `Settings.Global.ANIMATOR_DURATION_SCALE` once per recomposition (memoized on `configuration` so animator-toggle Developer Options changes propagate via the standard Compose configuration-change recompose path). When the scale is exactly `0f` ("Animations off"), the trail is suppressed even if the user has `showTrail` enabled. Wrapped in `runCatching` because some OEM devices restrict cross-app `Settings.Global` reads.
- ✅ **N8.5** verified shipped (inherited FlorisBoard upstream + supplemented in fork). `app/src/main/res/xml/method.xml` already declares `android:supportsSwitchingToNextInputMethod="true"`. `FlorisImeService.switchToNextInputMethod` correctly calls `imm.switchToNextInputMethod(window.attributes.token, false)` (API 28+ path) with `super.switchToNextInputMethod(false)` fallback. Switch Access users can rotate subtypes via the platform's standard subtype-cycle gesture.
- ✅ **N8.6** verified shipped (inherited FlorisBoard upstream + verified 2026-05-09 v1.7.x). `AbstractEditorInstance.setComposingRegion(composing: EditorRange)` extension function (line 303) routes invalid ranges through `finishComposingText()` and valid ranges through the standard two-arg `setComposingRegion(start, end)`. All composing-region updates in `AbstractEditorInstance` and `EditorInstance` go through this wrapper, so Voice Access dictation always sees a clean lifecycle and never gets a stale composing region from a shortcut auto-replace path.

### N9. Inline `commitContent()` for sticker / GIF / image insertion

Universal request across OSS keyboards [COMM-A, COMM-B PAIN-15, FR-13]. Architecture is small; payoff is parity with Gboard's image-paste behavior in apps that declare `EditorInfo.contentMimeTypes`.

- ✅ **N9.1** verified shipped (inherited FlorisBoard upstream, validated 2026-05-09 v1.7.x). `EditorInstance.commitClipboardItem` for `ItemType.IMAGE`/`VIDEO` already wraps the URI in an `InputContentInfoCompat`, calls `InputConnectionCompat.commitContent` with `INPUT_CONTENT_GRANT_READ_URI_PERMISSION`. Editor-compatibility gate at `ClipboardManager.canUseClipImage` (text/plain || matches `activeInfo.contentMimeTypes`).
- ✅ **N9.2** verified shipped (inherited FlorisBoard upstream, validated 2026-05-09 v1.7.x). `ClipboardInputLayout.kt` renders image clipboard items in the panel grid (`Image` composable from decoded bitmap), tap → `clipboardManager.pasteItem(item)` → `commitClipboardItem` → `commitContent`. Long-press → preview popup with delete/pin actions.
- ✅ **N9.3** shipped 2026-05-14. The emoji panel now has a persistent search row above the category tabs, backed by `EmojiSearch` ranking over emoji names and keywords with stable source-level tests. The media panel also has an emoji/sticker mode switch and two bundled local sticker packs ("Swift reactions" and "Quick replies"). Stickers are generated on-device as PNGs by `StickerRenderer`, served through `StickerMediaProvider` (`${applicationId}.provider.sticker`), and committed with the same Android `InputContentInfoCompat` rich-content path used by image clipboard paste. Unsupported editors show a calm disabled state instead of failing silently. `EmojiSearchTest` and `BundledStickerRepositoryTest` guard search behavior, pack uniqueness, provider registration, and stable URI paths [FR-9].

### N10. Emoji 16/17 + Unicode 17 readiness

Emoji 17.0 lands on Android in March 2026 (7 new glyphs: Distorted Face, Fight Cloud, Hairy Creature, Orca, Landslide, Trombone, Treasure Chest) [STD-EMOJI17, STD-UNICODE17].

- **N10.1** Bundle Noto Color Emoji 17.0 fonts ahead of upstream Android propagation. **Status 2026-05-14 (v5.0):** deferred to v1.8.x. Current `androidx.emoji2 1.6.0` provides Emoji 15.1 / Unicode 15.1 fallback; N10.1 requires either a newer emoji2 release (1.7.0+ when published — upstream emoji2 releases page tracked) or shipping `NotoColorEmoji.ttf` v17 directly as a font asset and routing through `EmojiCompat.Config(BundledEmojiCompatConfig)`. Tracking upstream via [emoji2 release notes](https://developer.android.com/jetpack/androidx/releases/emoji2). Note Emoji 17.0 ships 7 new glyphs (Distorted Face, Fight Cloud, Hairy Creature, Orca, Landslide, Trombone, Treasure Chest) [STD-EMOJI17].
- ✅ **N10.2** verified shipped (commit `6fd6e3b` + `ba3c790`). `FlorisEmojiCompat` lazy `replaceAll` loader is wired and a memory-profile test ships in the test suite. Unicode 15.1 glyphs (Pink Heart, Wireless, Pushing-back hand) render via the platform's bundled font + EmojiCompat font-replacement on devices below the OS support threshold. Re-verify when N10.1 ships emoji-pack v17.
- ✅ **N10.3** shipped 2026-05-09 (v1.7.0). `AbstractEditorInstance.deleteText` (BEFORE_CURSOR + AFTER_CURSOR branches) now converts the ICU-grapheme-aligned char `length` to a code-point count via `String.codePointCount(start, end)` and calls `InputConnection.deleteSurroundingTextInCodePoints` (API 24+, always available since `minSdk = 26`). Backspace is now surrogate-pair safe even if the editor has drifted from our expected text. Applies to character + word units, EN-locale and ICU-driven multilingual paths alike.

### N12. SwiftKey indistinguishability — close the perceived-quality gap

Tracking item from `SWIFTKEY_PARITY_RESEARCH.md` (2026-05-09). Goal: a user cannot tell the difference between SwiftFloris and SwiftKey while typing, **without** importing Copilot, DALL-E, account sync, or any cloud surface. Builds on SwiftKey's four core engines (neural LM, adaptive touch, SHARK2 + LM Flow, federated personalization) using Apache-2.0-only, on-device-only equivalents.

- ✅ **N12.1** shipped 2026-05-09 (v1.7.5). New `AdaptiveTouchModel` singleton in `ime/text/keyboard/AdaptiveTouchModel.kt` keeps per-subtype, per-key Welford-online stats of normalized tap offsets `(dx/halfW, dy/halfH)` relative to the visible center of each letter/punctuation key. After `getKeyForPos(...)` lands the geometric primary, `refine(keyboard, primary, x, y)` evaluates only the immediately-adjacent neighbours (within `pHalfW + cHalfW + 0.4·pHalfW` horizontally and `pHalfH + cHalfH + 0.6·pHalfH` vertically) and switches to a neighbour iff its 2D-Gaussian log-likelihood beats the primary's *and* both keys have ≥30 samples — so cold-start always falls back to the geometric hit. Stats are in-memory only (privacy-clean), reset via `setActiveSubtype(...)` on subtype change in `KeyboardManager.kt:158`. New pref `correction.adaptiveTouchModel` (default `true`) + Settings → Typing → "Adaptive touch model" switch in `TypingScreen.kt`. Verified by compile + adb-installed v1.7.5 debug build on `R5CY34G070L`.
- ✅ **N12.2** shipped 2026-05-09 (v1.7.5). New `PersonalBigramStore` in `ime/dictionary/PersonalBigramStore.kt` is a singleton, per-locale bigram counter persisted to `<filesDir>/personal_bigrams_<localeTag>.tsv` (one tab-separated `prev\tnext\tcount` triple per line). Caps: 2,000 prev words per locale, 16 next words per prev, 1,000 max count, MIN_COUNT=2 to suggest. Lazy-loaded, debounced flush every 20 commits. Wired into `KeyboardManager.learnIfAllowed`: every commit now learns the bigram `(lastLearnedWord, currWord)` for the active locale, gated behind the existing incognito + password-field checks. `LatinLanguageProvider.suggest` falls back to a new `nextWordSuggestions(...)` path when `currentWord` is blank — extracts the previous letter-token from `content.textBeforeSelection`, queries `PersonalBigramStore.predict(...)`, and returns up to 8 `WordSuggestionCandidate` entries with `isEligibleForAutoCommit=false` so a stray space doesn't auto-replace a real word. New pref `suggestion.nextWordPrediction` (default `true`) + Settings → Typing → "Predict the next word" switch. Inline ghost text is deferred to a future revision; this lands the suggestion-strip surface SwiftKey users actually rely on (the row-of-three after a space). Compile-verified.
- ✅ **N12.3** shipped 2026-05-09 (v1.7.5). When a subtype has one or more `secondaryLocales` enrolled (already supported in `SubtypeEditorScreen`), `LatinLanguageProvider.suggest` now branches into a new `suggestMultilingual(...)` path: it queries every enrolled locale's dictionary in parallel, asks each whether it recognises the typed word, and merges per-locale candidate lists with a `prior` of `1.0` for any locale that recognised it and `0.4` for those that didn't (with a fall-through of `1.0` when no locale recognises). Candidates are deduped by lowercase text and sorted by `confidence × prior` desc. `isEligibleForAutoCommit` is gated to locales that recognised the word, so the keyboard never autocommits a wrong-language correction mid-sentence. New pref `correction.multilingualSuggestions` (default `true`) + Settings → Typing → "Multilingual suggestions" switch. This is the user-facing slice of the larger N2 multilingual auto-detect work and ships immediately for any user with multilingual subtypes already enrolled. Compile-verified.
- ✅ **N12.4** shipped 2026-05-09 (v1.7.5). New `GlideTypingGesture.Detector.signalWordBoundary()` snapshots the current points-list, fires the new `Listener.onGlideWordBoundary(data)` callback, then resets `positions` (keeping the most recent point so the new trace is geometrically continuous), `startTime`, and `isActuallyGesture` so the velocity-arming check re-runs without releasing `pointerId`. `TextKeyboardLayoutController.onGlideAddPoint` now uses `keyboard.getKeyForPos(...)` to detect when the trace is on a SPACE/CJK_SPACE key after first having been off it; on that first re-entry it calls `signalWordBoundary()`. `GlideTypingManager.onGlideWordBoundary` reuses the existing `updateSuggestionsAsync(MAX_SUGGESTION_COUNT, true) { glideTypingClassifier.clear() }` path, so the current word commits via `keyboardManager.commitGesture(...)` and the classifier resets — and because `commitGesture` activates phantom-space, the next word committed at trace-end is automatically prefixed with " ". The controller also fires `onGlideCancelled()` on each boundary so the trail-fade animation visually punctuates each finished word, the same way it does on a finger-lift. New pref `glide.flowThroughSpace` (default `true`) + Settings → Gestures → "Flow through space" switch. Compile-verified.
- ✅ **N12.5** shipped 2026-05-09 (v1.7.5). New `PersonalTrigramStore` is a per-locale `(prev2, prev1) → next` counter persisted to `<filesDir>/personal_trigrams_<localeTag>.tsv` (one tab-separated quadruple per line). Caps: 4,000 contexts per locale, 12 next words per context, max count 1,000, MIN_COUNT=2 to suggest. `KeyboardManager.learnIfAllowed` now tracks both `lastLearnedWord` and `prevLearnedWord` (sliding two-word window per locale), and on each commit calls `PersonalBigramStore.learn(prev1, curr, locale)` *and* `PersonalTrigramStore.learn(prev2, prev1, curr, locale)` whenever both `prev` slots are populated for the active locale. `LatinLanguageProvider.nextWordSuggestions(...)` adds a new Tier 0 (trigram) before the existing Tier 1 (bigram) and Tier 2 (dict bootstrap) — confidence range 0.80-0.45. `previousWordOf(...)` now takes a `depth` parameter (1 = prev, 2 = prev-prev) so the same letter-token-extraction logic services both lookups. End result: after the user has typed `the quick brown fox` a couple of times, typing `the quick` will surface `brown` as the top suggestion *via the trigram path* rather than the generic bigram fallback. Compile-verified.

- ✅ **N12.5 (deferred)** — local-personalization-adapter blending personal frequencies with base dictionary frequencies via Bayesian update is naturally absorbed into the trigram→bigram→dict-frequency tier ladder above; no separate adapter needed until L1 (Gemma 3 270M) lands and a real probability model exists to blend with.
- ✅ **N12.9** shipped 2026-05-09 (v1.7.5). `applySentenceCase(word, textBeforeCursor)` capitalises the first letter of every next-word suggestion (trigram, bigram, dict bootstrap) when the trimmed text-before-cursor is empty or its last character is `.`/`!`/`?` — so after a period the strip surfaces `The`, `A`, `I` instead of lowercase. Wraps every merged candidate at the end of `nextWordSuggestions(...)`. Cheap, visible, and SwiftKey-parity at sentence start.

- ✅ **N12.10** shipped 2026-05-09 (v1.7.5). `WordSuggestionCandidate` returned from `nextWordSuggestions(...)` and `DictionaryManager.queryUserDictionary(...)` now both ship `isEligibleForUserRemoval = true`. New `DictionaryManager.forgetWord(word, locale)` deletes the matching personal-dict rows via `dao.delete(entry)` (off-thread, IO dispatcher). New `PersonalBigramStore.forget(word, locale)` removes every entry where `word` is the `next` value, evicting any prev-key whose `nextMap` becomes empty, then forces a flush. Same for `PersonalTrigramStore.forget(word, locale)`. `LatinLanguageProvider.removeSuggestion(...)` now consults all three forget paths in sequence whenever the candidate is eligible. End-to-end UX: long-press a noisy suggestion (e.g. an autocomplete from a typo you don't want learned) → it's gone everywhere on this device, and the suggestion strip re-runs (`NlpManager.removeSuggestion` already triggers `suggest(...)`) so the next-best suggestion takes its place.

- ✅ **N12.11** shipped 2026-05-14. Flow short-word context rescue is now wired. `GlideTypingManager` keeps the previous committed glide candidate list recoverable for a bounded 6-second window, asks `NlpManager.nextWordContextScore(...)` for personal/cold-start phrase evidence when the next glide word arrives, and lets `GlideContextRescorer` conservatively replace only short ambiguous words when following context is strong enough. `KeyboardManager.replaceLastGestureWordForContext(...)` preserves casing for retroactive replacements, and `EditorInstance.replaceCurrentGestureWord(...)` only replaces the active composing gesture word when it still matches the expected committed word. New `GlideContextRescorerTest` covers `in` + `going` -> `I'm`, weak-context no-ops, and long-word no-ops.

- ✅ **N12.12 / N2.2 partial** shipped 2026-05-14. Multilingual known-word detection now evaluates every active subtype locale instead of only the primary locale. `NlpManager.isKnownTypedWord(...)` checks user dictionaries and provider frequencies across `subtype.locales()`, `candidateSignals(...)` uses active-locale candidate frequency as the dictionary prior, and new `MultilingualTokenScorer` lowers language confidence for wrong-language corrections when the typed token is known in another enabled language. New `MultilingualTokenScorerTest` covers secondary-language literals, wrong-language correction demotion, and single-language confidence preservation.

- ✅ **N12.13** shipped 2026-05-14. Cold-start phrase prediction now checks common two- and three-word English phrase contexts before falling back to single-word continuations. `ColdStartNextWordPriors` parses the trailing phrase window, normalizes apostrophes, and adds phrase continuations such as `let me -> know`, `as soon as -> possible`, `thank you for -> the`, `are you going -> to`, and `would you like -> to`. New tests cover phrase-prior precedence and three-word lookup.

- ✅ **N12.14** shipped 2026-05-14. Typing stats is now a local-learning control surface, not just a passive counter. `TypingStatsScreen` shows learned dictionary words, learned phrase-pair count plus disk usage, learned phrase-triple count plus disk usage, correction outcome prior count, and persisted adaptive-touch sample count. It also exposes immediate local reset actions for phrase predictions, correction memory, adaptive touch, and all non-dictionary typing learning. `PersonalBigramStore` and `PersonalTrigramStore` now provide awaited reset/count APIs and serialize disk flushes with resets so pending background writes cannot recreate cleared learning. New tests cover correction-prior count/reset and adaptive-touch reset behavior.

- ✅ **N12.15** shipped 2026-05-14. SwiftKey parity tuning now has a visible local trace workflow. Typing stats exposes local trace capture, trace sharing, and trace clearing; trace capture still writes only to private app storage and exports via the existing FileProvider cache path when the user shares it. `SwiftKeyTypingTraceRecorder` now has explicit enable, size, clear, and share-cache APIs. Replay coverage also expanded with checked-in JSONL cases for the user-reported `thos -> this` short-word correction and a phrase-context `let me -> know` prediction case.

- ✅ **N12.16** shipped 2026-05-14. Replay tuning now exposes aggregate parity metrics. `SwiftKeyTraceReplayFixtureTest` replays every checked-in JSONL fixture through the ranker, then derives case count, full-ranking hit count, spacebar-action assertion count/hits, expected-role assertion count/hits, and typed-literal protection misses. The test currently requires a perfect hit rate across the fixture set, giving future scoring-threshold changes a single regression signal instead of isolated anecdotal checks.

- ✅ **N12.17** shipped 2026-05-14. Replay coverage now has explicit SwiftKey parity categories for bilingual literal protection and glide context rescue. Suggestion JSONL fixtures support `tags`, add same-prefix Spanish/English and secondary-language auto-commit protection cases, and assert zero typed-literal protection misses for `bilingual-token-protection`. Flow rescoring now has its own checked-in `swiftkey/replay/glide_context_cases.jsonl` fixture set with aggregate metrics for successful short-word rescues (`in -> I'm`, `in -> on`, `ill -> I'll`) and conservative no-op cases for weak context, long words, and punctuation.

- ✅ **N12.18** shipped 2026-05-14. SwiftKey scorer tuning is now explicit and replay-testable. `SwiftKeyCandidateTuning` lifts candidate weights, spatial threshold, accepted-correction boost, and rejected-correction penalty out of private constants while preserving the shipped defaults. `GlideContextTuning` does the same for Flow context rescue length, candidate cap, rank prior, context weight, minimum context, and switch margin. Replay tests now run deliberately conservative tuning variants to prove the fixture metrics detect degraded spatial roles and lost glide rescues before any production threshold moves.

- ✅ **N12.19** shipped 2026-05-14. Local trace capture now feeds the replay harness directly. `SwiftKeyTraceFixtureExporter` converts opt-in trace JSONL into sanitized replay-fixture JSONL by dropping timestamps, cursor-lengths, and previous-word context while preserving current word, touch evidence, scored candidates, expected ranking, expected roles, and accepted/rejected correction outcomes. Typing stats now adds **Share replay fixtures**, which writes `swiftkey_trace_replay_cases.jsonl` to the existing FileProvider cache path so real device sessions can become checked-in parity fixtures without hand-transcribing every candidate.

- ✅ **N12.20** shipped 2026-05-14. Cold-start phrase priors now influence typed partial-word candidate scoring, not only blank next-word prediction. `NlpManager.candidateSignals(...)` strips the active composing word from the context prefix and feeds `ColdStartNextWordPriors.score(...)` into the unified context probability, so typing `kn` after `Let me ` can rank `know` over stronger generic completions before personal history exists. Undo/retype parity also tightened: after a user rejects an autocorrection with backspace, `AutoCommitSuppression.shouldKeepTypedLiteral(...)` blocks all replacement candidates for that restored word slot until the user moves on, matching SwiftKey's "I meant what I just restored" behavior. Replay fixtures now cover partial phrase completion and a longer Flow contraction rescue (`were` + `going` -> `we're`).

- ✅ **N12.21 / N2.2 partial** shipped 2026-05-14. Multilingual scoring now uses trailing sentence context, not only the current token. `TokenLocaleEvidence` carries per-locale context frequency from the previous two words, `NlpManager.candidateSignals(...)` precomputes that context evidence across every active subtype locale, and `MultilingualTokenScorer` sharply boosts candidates from the locale already implied by the sentence while demoting inactive-locale autocorrects. This closes a SwiftKey-feel gap for partial bilingual completions, where `hola grac...` should prefer Spanish `gracias` over an English autocorrect like `grace` before the current token is fully recognized. Replay fixtures add a `bilingual-context` category for this behavior.

- ✅ **N12.22** shipped 2026-05-14. A conservative multi-word repair tier now handles common run-together English phrase typos before generic suggestions win. `ImmediateAutocorrect.englishPhraseRepairCandidate(...)` covers safe non-word forms such as `thankyou -> thank you`, `alot -> a lot`, `atleast -> at least`, `ofcourse -> of course`, `bytheway -> by the way`, and `rightnow -> right now`, preserves sentence-start case, skips all-caps input, and is English-locale gated. `NlpManager` checks this phrase-repair tier after explicit user-dictionary shortcuts but before lower-confidence in-strip autocorrects. Replay fixtures add a `multi-word-repair` category so phrase repair remains part of SwiftKey parity metrics.

- ✅ **N12.23 / N2.2 partial** shipped 2026-05-14. Active-locale context no longer bleeds across sentence boundaries. New `TypingContextExtractor` centralizes "prefix before current word" and previous-word extraction, trims the active composing token, and limits phrase/language context to the current sentence after `.`, `!`, `?`, or newline. `NlpManager` now uses that sentence-local context for personal n-gram scoring, cold-start phrase scoring, and multilingual trailing-word evidence. Unit tests cover current-word stripping, punctuation/newline language-boundary resets, and apostrophe/hyphen preservation.

- ✅ **N12.24 / N2.2 partial** shipped 2026-05-14. Same-sentence bilingual typing can now switch languages from the current token instead of letting trailing context dominate every candidate. `MultilingualTokenScorer` distinguishes prefix-completion language switches from context-language autocorrects, `NlpManager` detects when the active context language has a matching prefix candidate before applying the switch boost, and replay fixtures now cover both directions: `hola grac... -> gracias` stays in Spanish when Spanish has the prefix candidate, while a Spanish-context `th...` token can still surface English `the` over Spanish `te`.

- ✅ **N12.25 / N2.2 partial** shipped 2026-05-14. Multi-word repair now covers common three-word run-together phrases, not only two-word merges. `ImmediateAutocorrect.englishPhraseRepairCandidate(...)` adds safe forms such as `letmeknow -> let me know`, `thankyoufor -> thank you for`, `infrontof -> in front of`, `aswellas -> as well as`, `fromnowon -> from now on`, and `seeyousoon -> see you soon`; replay fixtures now include a three-word repair case so this SwiftKey-style behavior stays guarded by parity metrics.

- ✅ **N12.26 / N12.4 partial** shipped 2026-05-14. Glide context rescue now handles the common `id like -> I'd like` contraction pattern before personal history exists. `ColdStartNextWordPriors` adds `I'd` continuations (`like`, `rather`, `love`, `say`, `be`) and follow-on phrase priors for `I'd like/rather/love`, while glide replay fixtures add a `contraction before like` case so short ambiguous Flow commits can be corrected from the next word the way SwiftKey does.

- ✅ **N12.27 / N2.2 partial** shipped 2026-05-14. Shared-spelling bilingual literals now look less overconfidently wrong in the suggestion strip. `MultilingualTokenScorer` detects when the typed word is known in more than one active locale and dampens one-language correction confidence unless the candidate is the typed word itself. Replay fixtures add `shared-spelling bilingual literal protection` (`no` against English `on`/`so`) so the spacebar remains literal-safe and aggregate bilingual-protection metrics cover this SwiftKey parity edge case.

- ✅ **N12.28 / N2.2 partial** shipped 2026-05-14. Spacebar and punctuation autocommit now honor multilingual language confidence. `SwiftKeyCandidateRanker.selectSpacebarCandidate(...)` accepts candidate signals and refuses low-confidence language replacements, while `NlpManager` carries the active signal map into both spacebar and punctuation auto-commit selection. Replay fixtures add `low-confidence multilingual autocorrect guard`, preventing a weak wrong-language `hello` replacement from firing even if it still appears in the strip.

- ✅ **N12.29 / N2.1 partial** shipped 2026-05-14. Active-language detection now considers up to four trailing words in the current sentence instead of only two. `TypingContextExtractor.previousWordListBeforeCurrentWord(...)` exposes a reusable sentence-local list for language evidence, while `NlpManager` keeps bigram/trigram prediction on the previous two words but expands multilingual context scoring to the roadmap's 3-4 word target.

- ✅ **N12.8** shipped 2026-05-09 (v1.7.5). New `AdaptiveTouchModel.adjustedCenter(keyCode, fallbackCenterX, fallbackCenterY, halfWidth, halfHeight)` returns the user-personalised pixel center for a key when the model has ≥30 samples, otherwise the geometric fallback. Bias is clamped to `±0.5 × halfWidth/halfHeight` so even a heavily-skewed learner can never drag the template outside the visible key. `StatisticalGlideTypingClassifier.findNClosestKeys` (matching) and `Pruner.generateIdealGestures` (template) both consult `adjustedCenter(...)` instead of using `key.visibleBounds.center` directly. Net effect: the glide trace-shape comparator now scores swipes against where this user actually aims for each key, not the visual center — same per-user bias N12.1 already gives the tap path. Compile-verified.
- ✅ **N12.7** shipped 2026-05-09 (v1.7.5). `LatinDictionarySnapshot.topByFrequency(n)` lazily caches the top-64 most-frequent dictionary words (skipping single-letter words other than "a"/"I" so the strip doesn't surface noise). `LatinLanguageProvider.nextWordSuggestions(...)` now layers two tiers: (Tier 1) up-to-`maxCandidateCount` trained-bigram hits when the previous word matched anything in the personal bigram store; (Tier 2) fills any remaining slots with high-frequency dictionary words. A `HashSet` deduplicates between tiers (case-insensitive). The result: a never-empty suggestion strip on cold-start and after sentence-ending punctuation, the SwiftKey Day-1 surface. Sentence-start detection (`...trimEnd().lastOrNull() in {'.','!','?'}` or empty) auto-capitalises the first letter so the user gets `The`, `A`, `I` not `the`, `a`, `i` after a period. Tier 1 confidence range 0.55-0.20 (5% per slot), Tier 2 confidence ~0.30-0.55 weighted by SCOWL frequency, so a strong personal bigram still wins over a generic "the". Compile-verified.

- ✅ **N12.6** shipped 2026-05-09 (v1.7.5). New `Routes.Settings.TypingStats` (deep-link `settings/typing/stats`) reachable from the bottom of `TypingScreen` ("Typing stats" with `Insights` icon). `TypingStatsScreen.kt` reads three on-device numbers off-thread: (1) words-learned count + top-10 personal-dictionary entries by frequency from `DictionaryManager.default().florisUserDictionaryDao().queryAll()`; (2) total bigram-store size on disk by summing every `personal_bigrams_*.tsv` under `filesDir`; (3) adaptive-touch-model session sample count from `AdaptiveTouchModel.totalSampleCount()`. Manual refresh button rerunshe the IO query. No data leaves the device. Closes the SwiftKey "Stats" parity surface. Compile-verified + adb-installed.

Out of scope by design: Copilot, DALL-E Designer, Microsoft account sync, federated aggregation server, anything that requires `INTERNET` permission.

### N11. Resolve the runtime `TODO()` stubs (correctness floor) ✅ shipped 2026-05-09 (v1.7.0)

Two `TODO("…")` calls in production paths will crash the IME if reached; both are in extension-loader fallback branches. Either implement or guard.

- ✅ **N11.1** `KeyboardExtension.kt:55` — replaced `TODO("Not yet implemented")` with a real `KeyboardExtensionEditor` (mirrors `ThemeExtensionEditor` shape: meta + dependencies + per-component mutable lists; `build()` round-trips back to a `KeyboardExtension`). Edit screen no longer crashes if an extension of this type is opened.
- ✅ **N11.2** `LanguagePackExtension.kt:62` — replaced `TODO("LOL LMAO")` with a real `LanguagePackExtensionEditor`. Unblocks F-Droid acceptance check.
- ✅ **N11.3** `FlorisSpellCheckerService.kt:141` — documented design choice. AOSP's default sentence-aggregation already routes per-word lookups through `onGetSuggestionsMultiple`, which is SwiftFloris-backed via `NlpManager`. Custom impl deferred until we need cross-word sentence context. Comment in code explains the rationale.

---

## 7. NEXT (v1.9.0 → v2.0.0, target Q4 2026 – Q1 2027)

Larger pieces. Each is committed but not started; ordered by value.

### Next-1. SymSpell replacing Levenshtein-2 corrections

Pure algorithmic win. SymSpell pre-computes only deletes (not insertions/substitutions/transpositions); a 5-letter word's 3M-error error-space collapses to ~25 entries; benchmarks show **~1M× faster than Norvig and ~1,870× faster than BK-tree at edit-distance 2** [AI6, AI7]. Replaces the current per-keystroke Levenshtein-2 scan in `LatinDictionarySuggester.corrections()`, freeing the UI thread.

- ✅ **Next-1.A** shipped 2026-05-09 (v1.7.5) — distance-1 slice. New pure-Kotlin `SymSpellIndex.kt`: lazy delete-only index over the dictionary's word set (`HashMap<String, Array<String>>`, single-pass build). `LatinDictionarySnapshot.symSpellIndex` is a `by lazy` field so the cost (~100-300 ms over the 117k-word EN dict on a Pixel 6) lands on the first correction call rather than dictionary-load. `LatinDictionarySuggester.knownEdits1(...)` now calls `dictionary.symSpellIndex.candidatesAtDistance1(input)` instead of generating Norvig's L · 54 candidate strings per call → ~50× speedup on the per-keystroke correction path. Distance-2 stayed on the bounded legacy path in this slice and was replaced later by Next-1.C.
- ✅ **Next-1.B** shipped 2026-05-09 (v1.7.5) — distance-2 auto-commit gating. Old behaviour: only `distance == 1` corrections with `frequency ≥ 0.78` would auto-commit on space. New `AutoCommitMinFrequencyDistance2 = 0.92` threshold lets distance-2 candidates auto-commit too — but only when the candidate is in the *very* common bucket (~top 3k SCOWL words by frequency). This is what closes the long-word-typo gap users actually mention: `recieved` → `received`, `tommorrow` → `tomorrow`, `seperate` → `separate`. Per-distance `autoCommitThreshold` switch in `LatinDictionarySuggester.corrections(...)`.
- ✅ **Next-1.C** shipped 2026-05-14 — bounded distance-2 SymSpell index. `SymSpellIndex` now supports a distance-2 delete index, while `LatinDictionarySnapshot` builds it lazily over only common correction words (`frequency >= 192`, up to 24k words, max length 12) instead of the full 500k+ recognition dictionary. `LatinDictionarySuggester.corrections(...)` now uses that bounded index for short two-edit typo recovery and verifies the exact Damerau-Levenshtein distance before ranking/autocommit, preserving Next-1.B's high-frequency distance-2 safety gate.

### Next-2. On-device voice typing (drop the FUTO Voice Input dependency)

FUTO is Source-First (non-OSI) and adds a second-app install friction users complain about [PAIN-8, AI8, AI9]. Bundle `whisper.cpp` (MIT) directly inside SwiftFloris.

- ✅ **Next-2.1** shipped 2026-05-14. Added the RAM-aware embedded voice model selector foundation: `VoiceModelSelector` detects total device RAM / Android low-RAM class, maps Auto to tiny.en (~75MB) for low-end devices, base.en (~140MB) for mid-tier or unknown non-low-RAM devices, and Large-v3-Turbo INT8 (~800MB) for 8GB+ flagship devices. Settings → Voice input now has an Embedded engine group with a persisted model-tier preference, the resolved current selection, and the detected recommendation. The UI deliberately states that live dictation still uses FUTO until the bundled Whisper runtime and on-demand model manager ship. `VoiceModelSelectorTest` pins the RAM thresholds, manual overrides, and roadmap size metadata [AI3, AI8].
- ✅ **Next-2.2** shipped 2026-05-14. Added the local voice engine routing layer for the Vosk streaming fallback path. New `VoiceRecognitionEngineSelector` resolves Auto / Embedded Whisper / Vosk streaming / External IME into an explicit route with user-readable reasons: Auto now prefers Vosk for command-mode sessions and low-RAM devices when the Vosk model plus SwiftFloris microphone permission are present, prefers embedded Whisper on capable devices, and otherwise keeps the current external voice-keyboard handoff. Settings → Voice input now exposes the recognition-engine preference, current route, and command-mode route. `StreamingVoiceTranscriptBuffer` also landed as the true-streaming transcript spine: it deduplicates repeated partials, commits final segments, handles cumulative recognizer output, and detects command phrases from partial transcripts so Vosk/Whisper streams can feed `VOICE_COMMANDS.md` behavior without waiting for a full final transcript. Local model download/runtime activation remains intentionally assigned to Next-2.3/Next-2.4.
- ✅ **Next-2.3** shipped 2026-05-14. Added a Joplin-style local voice model manager for the embedded dictation roadmap: a curated Whisper/Vosk catalog (Whisper tiny.en, base.en, large-v3-turbo-q8 plus Vosk small EN/ES/FR/DE/IT/PT), per-language model rows, explicit download/import/delete actions, private app-storage installs under `filesDir/voice-models`, and disk-usage badges. The flow keeps the base APK lean and preserves the current no-`INTERNET` app posture by opening model downloads in the browser and importing local artifacts through Android's document picker. Installed-model state now feeds the voice engine route preview, so embedded Whisper and Vosk streaming routes become selectable/credible only when the matching local model files exist.
- ✅ **Next-2.4** shipped 2026-05-14 (v1.7.9). `VoiceInputManager.consumeStreamingChunk(chunk, actions, customCommands, commandMode, ...)` pipes per-chunk transcripts through `StreamingVoiceTranscriptBuffer` and, on a final-chunk command match, fires `VoiceCommandExecutor` immediately. Partial chunks return `executed = null` even when the buffer surfaces a candidate `commandMatch` so the dictation overlay can preview a pending command without committing it. Buffer state is per-session; `resetStreamingBuffer()` recycles between dictations. Returns `VoiceStreamingCommandUpdate(transcript, executed)` so the IME can render both the partial/committed transcript and any executed-command feedback ("Executed: undo word") in one render pass. Closes the SwiftKey "Smart Edit"-style voice-edit surface ([SMART-EDIT, COMM-K]) the moment the user finishes saying it, rather than waiting for the recognizer's full-utterance final transcript.

- **Next-2.5** Rambler-style streaming-voice cleanup [GBOARD-RAMBLER]. Android 17's Gboard ships "Rambler" — hold mic + ramble + clean polished text out (including mid-language switches). On-device equivalent: post-process the `StreamingVoiceTranscriptBuffer.committedText` through a Gemma 3 270M (L1) text-rewrite pass when the user holds-and-releases the voice key. Gated behind the L1 LLM dependency and an explicit user toggle. Cost: M (depends on L1 landing).

### Next-3. Multi-tier learning improvements (beyond v1.6.0's `learnWord`)

- **Next-3.1** Bigram + trigram next-word prediction backed by **KenLM Kneser-Ney 5-gram** (mmap binary trie, ~1ms lookup; pre-trained for 24 languages via [edugp/kenlm on HF](https://huggingface.co/edugp/kenlm)) [AI11, AI12]. Replaces the current bigram-only chain.
- ✅ **Next-3.2** shipped 2026-05-15. New `ZipfFrequencyTable` loads `assets/freq/<langCode>.tsv` (one `word\tzipf` line per entry, range [1, 8] per the rspeer/wordfreq scale) and `LatinDictionarySnapshot.frequencyFor(word)` now returns `0.6 * scowl + 0.4 * (zipf / 8.0)` when both signals are present, falling back to pure SCOWL when the Zipf asset is missing for the language. Words only in SCOWL behave exactly as before; words only in Zipf get `zipf / 8.0` directly so common-but-uncommonly-spelled tokens like `okay` rank meaningfully. Seed `assets/freq/en.tsv` ships with ~1,000 high-frequency English entries (rspeer/wordfreq CC-BY-SA); full SUBTLEX-extracted tables move to the Next-10.3 dictionary-pack addon. `ZipfFrequencyTableTest` covers empty passthrough, missing-rows tolerance, the 0.6/0.4 blend, the [1, 8] clamp, and the case-insensitive lookup contract.
- ✅ **Next-3.3** shipped 2026-05-14 (v1.7.9). Existing `LatinDictionarySuggester.applyTypedCase(candidate, rawWord)` contract is now explicitly pinned by `LatinDictionarySuggesterTest`: Title Case for prefix completions when prefix capitalized, ALL_CAPS when prefix length ≥ 2 and all uppercase, lowercase passthrough otherwise; case-pinning applies to distance-1 + bounded-distance-2 corrections too. Closes FlorisBoard #1007 [FR-19, COMM-D].
- ✅ **Next-3.3a** shipped 2026-05-15. Single-letter proper-noun completion: when the user types a single capital letter (e.g. "F" or "T"), `LatinDictionarySuggester.suggest(...)` now routes to a new `singleLetterProperNounCompletions(...)` path that returns the top-frequency dictionary words starting with that letter, case-matched via `withTypedCase` ("F" → "For", "From", "Foo"; "T" → "The", "To", "This"). Single-letter words "a"/"i" are skipped (they're the typed letter). Never auto-commit (single-letter prefix is too ambiguous). Lowercase single letters explicitly return no completions — would flood the strip every time the user begins a normal word. `LatinDictionarySuggesterTest` adds case-match + lowercase-skip coverage.
- ✅ **Next-3.4** shipped 2026-05-14 (v1.7.9). Long-press a removable candidate → in-strip "Remove '<word>' from predictions" prompt overlays the suggestion strip (not a popup; rendered via Compose `Box` over `CandidatesRow` content). Confirms via right-hand "Remove" button → fires `nlpManager.removeSuggestion(subtype, candidate)` (deletes from personal dict + bigram + trigram via the existing N12.10 forget paths); tap outside or "Cancel" dismisses. Springy entry/exit (`scaleIn(0.85f) + fadeIn` at DampingRatioMediumBouncy / `scaleOut + fadeOut` at StiffnessHigh) per Next-11.2. If the underlying candidate list rotates the pending word out, the overlay self-dismisses so the user never confirms removal of a now-invisible candidate. Closes COMM-A FR-22 / FlorisBoard #737 / AnySoftKeyboard #1399 / FlorisBoard #1888.

### Next-4. Stylus handwriting (Pixel Tablet + S-Pen audience)

Android 14+ ships `InputMethodService.onStartStylusHandwriting()` + Ink API. Currently no FOSS keyboard implements it [STD-STYLUS, STD-INK].

- ✅ **Next-4.1** shipped 2026-05-15 (scaffold). `FlorisImeService.onStartStylusHandwriting()` now overrides the Android 14+ entry point, logs the session, and returns `false` so the system falls back to the standard touch path. Reserves the surface so language-pack / preference plumbing can ship ahead of the recogniser bring-up. The actual recogniser hook is Next-4.2's slot.
- **Next-4.2** On-device stroke recognizer via Google ML Kit Digital Ink (offline model) **or** a custom ICU-LM fallback for languages ML Kit doesn't support.
- ✅ **Next-4.3** shipped 2026-05-15 (toggle; per-subtype refinement pending Next-4.2). New `prefs.keyboard.stylusHandwritingEnabled` boolean (default **off**) surfaced under Settings → Keyboard. `FlorisImeService.onStartStylusHandwriting()` short-circuits to `false` when the toggle is off, so the system falls back to standard touch input without ever invoking the recogniser stub. When Next-4.2 lands, the per-subtype refinement (Hindi-only / Japanese-only handwriting depending on what the ML Kit / ICU-LM recogniser supports) lands as Next-4.3a.

Differentiator vs HeliBoard / FlorisBoard upstream (neither ships handwriting).

### Next-5. CRDT personal-dictionary sync over Syncthing

No mainstream IME combines E2EE with personal-dictionary sync without a vendor account [STD-SYNC, AI18, COMM-J].

- **Next-5.1** Per-device file (`dict-<deviceid>.bin`) merges via Automerge JSON CRDT; one file per device, deltas merged on read.
- **Next-5.2** First-launch QR-pair flow: scan recipient device's QR → exchange Curve25519 pubkey via libsodium sealed box → wrap subsequent dictionary deltas with sealed-box → write to a Syncthing-shared folder.
- **Next-5.3** "Bring your own sync channel" — user can swap Syncthing for any folder-sync of their choosing (Nextcloud, Resilio, Dropbox via Foldersync, even Email-this-blob). The CRDT merge is local; the transport is user-controlled.

### Next-6. Migration importers

- ✅ **Next-6.1** shipped 2026-05-14 (v1.7.9). New `DictionaryImporter.parseGboardXml(xml)` + `parseZip(stream)` consumes Google Takeout's `PersonalDictionary.zip` (XML inside zip; `<userdictionary><entry word="..." shortcut="..." locale="..." frequency="..."/></userdictionary>` shape). Schema detection routes by structure (PK magic bytes / `<?xml` / first-line shape), not file extension. XML entity decoding for `&amp;` / `&lt;` etc. Frequency clamped 0..255. Closes [PAIN-2, PAIN-18, MIG-GBOARD].
- ✅ **Next-6.2** shipped 2026-05-14 (v1.7.9). Same `DictionaryImporter` path handles FlorisBoard CSV (`word,frequency,shortcut,locale` with optional header). `.flbackup` zip containing a raw `.db` / `.sqlite` SQLite snapshot is **explicitly routed to a future in-app importer path** — JVM-side can't open a SQLite database without Android's runtime, so the JVM importer raises a clear `DictionaryImportException` directing the user to Settings → Personal dictionary → Import .flbackup. The CSV+JSON manifest layout is fully supported; SQLite-snapshot routing is the v1.8 follow-up.
- ✅ **Next-6.3** shipped 2026-05-14 (v1.7.9). New `docs/MIGRATE_FROM_SWIFTKEY.md` writes up the three available paths (retrain SwiftFloris, redownload via Microsoft account then re-export, root-only `sqlite3` extraction with sample one-line `adb` recipe) and explicitly refuses to ship a SwiftKey-cloud OAuth helper (violates §1 no-network). Times directly into the **Microsoft SwiftKey account-retirement cutoff of 2026-05-31** [SK-RETIRE] — the migration window is open right now. Closes [MIG-SK, C2].
- **Next-6.4** **Hardware-keyboard layouts** — Windows `.klc` and macOS `.keylayout` import via [KLFC](https://github.com/39aldo39/klfc) at build-time tooling [MIG-KLFC]. **Not started.**

### Next-7. Floating + split + one-handed window modes (FlorisBoard upstream parity)

`ImeWindowMode.kt:56` is a documented placeholder; FlorisBoard v0.7 targets the same; HeliBoard #326 has 32 reactions [FR-7, F1, COMM-A, PAIN-14].

- ✅ **Next-7.1** shipped 2026-05-15 (UX surface complete; runtime drag + resize handles already in place from FlorisBoard upstream's `ImeWindowEditorHandles.kt` 457-line implementation). New `prefs.keyboard.startInFloatingMode` boolean (default off) surfaced under Settings → Keyboard. The runtime user can still flip via the existing `TOGGLE_COMPACT_LAYOUT` smartbar quick-action / swipe binding, but this toggle covers the "I want floating mode every time" persistent-default case. `ImeWindowController.startSession` consumes the pref on first session-creation, with fallthrough to the previously-saved `ImeWindowConfig`. **Runtime onboarding tooltip** ("Drag the handle to move; pinch corner to resize") on first-floating-mode invocation deferred to Next-7.1a.
- **Next-7.2** Split-keyboard for tablet landscape (mirror SwiftKey + Samsung; ASK #1952 + HeliBoard #326 want this).
- ✅ **Next-7.3** verified shipped 2026-05-14 (v1.7.9 audit; functionality landed across FlorisBoard upstream + earlier SwiftFloris work). `TOGGLE_COMPACT_LAYOUT` keycode + `TextKeyData.TOGGLE_COMPACT_LAYOUT` predefined + `SwipeAction.TOGGLE_COMPACT_LAYOUT` + smartbar QuickAction (`R.string.quick_action__one_handed_mode`) all wired into `KeyboardManager.handleKeyEvent` → `ImeWindowController.actions.toggleCompactLayout()` / `compactLayoutToLeft()` / `compactLayoutToRight()` / `compactLayoutFlipSide()`. `ImeWindow.OneHandedPanel` ships drag-resize affordance + chevron flip-side + dismiss / zoom controls. **Pending future polish:** Settings → Keyboard → "Start in one-handed mode" default-on preference; explicit citation here so future contributors don't re-derive the audit.

### Next-8. Programmer mode (Hacker's Keyboard successor)

No maintained option exists since Hacker's Keyboard stalled (klausw #875) [PAIN-19, FR-9, COMM-A]. Unexpected Keyboard is closest but has no autocorrect.

- ✅ **Next-8.1** shipped 2026-05-14 (v1.7.9; smartbar-surface MVP). New `SmartbarActionProfile.CODE` enum surfaces Tab + Esc + arrow keys + start-of-line + end-of-line + paste + settings as priority actions when the editor's package matches a curated terminal/IDE list. Predefined `TextKeyData.TAB` (code 9, `CHARACTER` type) and `TextKeyData.ESCAPE` (code 27) added.
- ✅ **Next-8.1a** shipped 2026-05-15 (bottom-row preset). New `BottomRowPreset.Programmer` surfaces VIEW_SYMBOLS / Tab / Esc / Space / Period / Slash / Enter directly in the main letter view. The Slash key carries a long-press popup with the full bracket-cluster: `\` (main) + `{` `}` `[` `]` `(` `)` `<` `>` `|` `` ` `` `~`. New `BottomRowKey` enum values: `TAB`, `ESCAPE`, `SLASH`. User selects this preset under Settings → Keyboard → Bottom-row preset → "Programmer". Complements the Next-8.2 CODE smartbar profile (which only changes smartbar slots, not in-keyboard keys). **Remaining:** swipe-to-symbol on every key, regex-snippet macros, paths/IPs/UUIDs as first-class clipboard types — promoted to Next-8.1b for a future pass.
- ✅ **Next-8.2** shipped 2026-05-14 (v1.7.9). `SmartbarActionProfiles.detect(editorInfo)` now matches Termux / JuiceSSH / Acode / Spck / Quoda / ConnectBot / Termius / JetBrains family / GitHub mobile / VimTouch / sshd-like packages (substring match on lowercased package name) and emits `SmartbarActionProfile.CODE`. CODE wins the matcher over CHAT for SHORT_MESSAGE-input-type editors so terminal users don't get a chat smartbar. Test coverage in `SmartbarActionProfilesTest` (Termux dispatch, JuiceSSH-over-SHORT_MESSAGE precedence, full priority-action ordering assertion).

### Next-9. Inline autofill for password managers (Bitwarden / KeePass / Proton Pass)

`android:supportsInlineSuggestions=true` + `InlinePresentationRenderer` (API 30+) — render password-manager chips inline in the suggestion strip [STD-INLINE-AUTOFILL, COMM-D, COMM-G PAIN-12, FR-6].

- ✅ **Next-9.1** verified shipped (inherited FlorisBoard upstream + audited 2026-05-14 v1.7.9). `app/src/main/res/xml/method.xml` declares `android:supportsInlineSuggestions="true"` plus `android:supportsSwitchingToNextInputMethod="true"`.
- ✅ **Next-9.2** verified shipped (inherited FlorisBoard upstream + audited 2026-05-14 v1.7.9). `FlorisImeService.onCreateInlineSuggestionsRequest(uiExtras)` builds an `InlineSuggestionsRequest` with `SUGGESTION_COUNT_UNLIMITED` + a single `InlinePresentationSpec` sized to one smartbar row. `NlpInlineAutofill` inflates each provider-supplied View off-thread into a `StateFlow<List<NlpInlineAutofillSuggestion>>`. `Smartbar.shouldShowInlineSuggestionsUi` (line 153 + 219 + 315 + 323) routes the chip row into `InlineSuggestionsUi.kt`, which renders each `InlineSuggestion`'s `android.view.View` as an `AndroidView` inside `florisHorizontalScroll`, with clip-bounds gymnastics so chips truncate cleanly. Inline-autofill chips are themed via the `InlineAutofillChip` Snygg element so they inherit the active keyboard theme.
- ✅ **Next-9.3** shipped 2026-05-14 (v1.7.9). New `docs/INLINE_AUTOFILL.md` matrix: Bitwarden 2026.4.x (FOSS F-Droid build), KeePassDX 4.3.x (F-Droid), Proton Pass 1.32.x, 1Password 8.10.x, Aegis 2.4.x (TOTP) — each version-pinned per Android version (14 / 16). Known-partial / non-compatible: Google Password Manager (Play Services side-check; out unless we add cloud), LastPass (cloud-bound, not in our test matrix), Samsung Pass (OEM-bound). Includes the re-verification recipe to run on every release that touches `onCreateInlineSuggestionsRequest`, `Smartbar.kt`, `InlineSuggestionsUi.kt`, or `NlpInlineAutofill.kt`. Closes [COMM-D #2728].

- **Next-9.4** Emoji palette enhancements per [H-EMOJI-1231]: emoji search-by-tag (English first; Crowdin pipeline for other locales), emoji predict-by-tag (optional auto-type from typed-word tag match), pin emoji together for quick recall, custom user emoji tags. Cost: M. Closes the HeliBoard #1231 list (Nov 2024) of long-standing emoji UX gaps shared across all FOSS Android keyboards.

SwiftFloris already **is the first OSS keyboard with parity to Gboard's password-manager UX** as of v1.7.9 [docs/INLINE_AUTOFILL.md].

### Next-10. Plugin / addon APK loading (fcitx5 pattern)

Allow community-built dictionaries, themes, and language-pack APKs to self-register via broadcast intent [O14, O4]. AnySoftKeyboard already does this for language packs; fcitx5-android does it for input addons; no Floris-lineage keyboard does. Architectural payoff: base APK stays small, long-tail languages ship as separate Play/F-Droid products.

- ✅ **Next-10.1** shipped 2026-05-14 (v1.7.9). New `dev.patrickgold.florisboard.ime.addon` package: `AddonContract.Action.{REGISTER, REGISTER_LANGUAGE_PACK, REGISTER_THEME_PACK, REGISTER_DICTIONARY_PACK, REGISTER_LAYOUT_PACK, REGISTER_POPUP_MAPPING_PACK, INVALIDATE}`, `AddonContract.MetadataKey.{ADDON_DESCRIPTOR, ADDON_TYPE, ADDON_VERSION, ADDON_LICENSE}`, `ADDON_SIGNATURE_PERMISSION = "dev.patrickgold.florisboard.permission.REGISTER_ADDON"`, `ADDON_ID_PREFIX = "addon:"`, `ADDON_MAX_BUNDLE_BYTES = 64 MB`. `AddonType` enum (LANGUAGE_PACK / THEME_PACK / DICTIONARY_PACK / LAYOUT_PACK / POPUP_MAPPING_PACK). `AddonManifest` data class with strict invariants (non-negative version, bundle size cap, AB:CD:… fingerprint regex). `AndroidManifest.xml` declares the signature-protected permission + the Android 11+ `<queries>` entries for every REGISTER_* action so the IME can see addon packages.
- ✅ **Next-10.2** shipped 2026-05-14 (v1.7.9). `AddonEnumerator.snapshot()` scans `PackageManager.getInstalledPackages(GET_META_DATA | GET_PERMISSIONS)` (with TIRAMISU `PackageInfoFlags` path), evaluates each candidate, and returns the accepted set. Rejection paths (logged via `flogInfo` so a future Settings → Addons → "Why was X rejected?" surface can replay): missing addon-type metadata (silent skip — common case), unknown addon-type (forward-compat: newer addons skip on older IME), banned network permission (INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE / CHANGE_NETWORK_STATE / CHANGE_WIFI_STATE — hard reject), missing descriptor / version / license metadata, unreadable signing certificate. Uses the existing N7.5 `SigningFingerprint.sha256OfPackage(context, packageName)` (added in v1.7.9 to support arbitrary-package fingerprint reads, not just the IME's own). 9 unit tests pin the contract (`AddonManifestTest`).
- **Next-10.3** First addon pack: Polish dictionary (HeliBoard's German is from 2014 [STD-LATIN-LEAPFROG]; OpenSubtitles 2024 + Wiktionary frequency lists for a clean 2025 baseline). **Gated on:** Next-10.2's `AddonRegistry` live-state + a Settings → Addons screen + addon-asset extraction pipeline. Promote when those are in.

### Next-11. Material 3 Expressive theme refresh

M3 Expressive launched at I/O 2025; springy stack-respond animations + envelope haptics + 35 new shape morphs [STD-M3E, AI15].

- ✅ **Next-11.1** shipped 2026-05-15. Seven new M3 Expressive bundled themes: **Nord (light + dark)**, **Tokyo Night**, **Dracula**, **Catppuccin Mocha**, and **SwiftKey Pure (M3E, light + dark)**. Each derived from `swift_slate.json` so the ~500-line Snygg selector tree (window/key/popup/smartbar/clipboard/extracted-input/glide-trail/inline-autofill) stays consistent with the well-tested baseline — only the `@defines` palette and the shape tokens change. `--shape-chip` moves off the pill/stadium `rounded-corner(50%)` to `rounded-corner(12dp)` to comply with the project no-pill-backdrop rule. Generated via `scripts/gen_m3e_themes.py` so the seven sheets stay byte-identical to re-runs. Theme extension manifest version bumped 0.2.0 → 0.3.0; new themes are registered alongside the existing Swift Slate / Floris / SwiftKey Pure variants. Per-app accent surface wiring (`LocalPerAppAccent` from Next-11.3a) is already in place — when the user enables Settings → Theme → "Tint to active app's icon", the active app's accent overrides `--primary` at the compose-tree root regardless of which M3E theme is selected.
- ✅ **Next-11.2** shipped 2026-05-14 (v1.7.9). `AnimatedVisibility` wraps the Next-3.4 confirmation overlay: enter = `scaleIn(initialScale = 0.85f, animationSpec = spring(DampingRatioMediumBouncy, StiffnessMedium)) + fadeIn(spring(StiffnessMedium))`; exit = `scaleOut(spring(StiffnessHigh), targetScale = 0.9f) + fadeOut(spring(StiffnessHigh))`. Reads as a deliberate action rather than a flash; cancel gets immediate feedback. **Paired envelope-haptic rumble** still pending — gates on the N3.3a Android 16 PWLE haptic envelope item.
- ✅ **Next-11.3** shipped 2026-05-14 (v1.7.9; foundation only). New `PerAppAccentResolver(context, cacheCapacity = 64)` extracts the editor's app icon (via `PackageManager.getApplicationInfo(pkg, 0).loadIcon(pm)`), rasterizes to a 32×32 ARGB bitmap, scans every pixel, computes HSV inline, rejects near-grey (saturation < 0.25), near-black (value < 0.20), near-white (value > 0.92), and returns the highest-saturation candidate as `androidx.compose.ui.graphics.Color`. LRU(64) cache with `AccentResult` sentinel wrapper (androidx.collection.LruCache rejects nullable V). **No `PACKAGE_USAGE_STATS` permission needed** — the editor's package name comes from the standard IME contract, so this never escalates beyond what the IME already sees. References: Chrooma's Chameleon pattern (closed-source), [C7].
- ✅ **Next-11.3a** shipped 2026-05-15. Per-app accent **surface wiring**. New `PerAppAccentController(context)` owns a `StateFlow<Color?>` (`activeAccent`) gated on the new `prefs.theme.perAppAccentEnabled` toggle (default **off** — privacy-by-default stance even though no extra permission is required). `FlorisImeService.onStartInputView` calls `perAppAccentController.setActiveEditorPackage(editorInfo.packageName)` so the flow updates on every editor focus. `ImeRootView.Content` provides the value to the compose tree via the new `LocalPerAppAccent` `CompositionLocal`; consumers throughout the smartbar / suggestion strip / future M3 Expressive theme regen can opt-in by reading `LocalPerAppAccent.current` (null = fall through to the active Snygg theme's `--primary`). Settings → Theme adds a SwitchPreference titled "Tint to active app's icon" with a summary explicitly noting "all on-device — no extra permissions, no network". `FlorisApplication` exposes `perAppAccentController` as a `lazy {}` field with a `Context.perAppAccentController()` extension accessor matching the project's manager-injection pattern.

### Next-12. Performance instrumentation + Roborazzi visual regression

IMPROVEMENT_PLAN Workstream 7 (Planned, P1) — promote concrete deliverables.

- ✅ **Next-12.1** shipped 2026-05-15 (harness; numbers populated as releases roll). New `benchmark/src/main/kotlin/.../KeyboardLatencyBenchmark.kt` adds four Macrobenchmark tests on top of the existing `StartupBenchmark` family: `imeFirstRender` (cold IME view inflation, FrameTimingMetric + `swiftfloris.ime.firstRender` TraceSection), `suggestionStripRecomposition` (warm-start with typed text, `swiftfloris.nlp.suggest` + `swiftfloris.smartbar.candidates.recompose` sections), `dictionaryColdLoad` (memory + `swiftfloris.dict.load` + `swiftfloris.nlp.symspell.build`), and `themeSwitch` (FrameTimingMetric + `swiftfloris.theme.switch`). Trace-section convention `swiftfloris.<subsystem>.<action>` documented so any production hot path can become measurable by wrapping with `androidx.tracing.Trace.beginSection`. Run via `./gradlew :benchmark:connectedBenchmarkAndroidTest` on a device with locked clocks; release notes record before/after per §15 Definition of Done.
- **Next-12.2** Roborazzi 1.39+ on Robolectric 4.14 visual regression suite — auto-generate from `@Preview` via `roborazzi { generateComposePreviewRobolectricTests { enable = true } }` + ComposablePreviewScanner. All 4 themes × dark/light × RTL × density buckets in one annotation pass [STD-ROBORAZZI].
- ✅ **Next-12.3** shipped 2026-05-14 (v1.7.9). New `LatinSuggesterPropertyTest` ships 11 `checkAll` invariants over the autocorrect surface: `normalizeWord` idempotency, null-on-non-letter input, no-typed-literal-autocommit, candidate-cap respect, dedup-by-lowercase, Damerau-Levenshtein ≤ 2 on corrections (verified against an *independent* DL implementation so a bug in the suggester can't silently match a bug in the test oracle), delete-and-retype identity, Title Case / ALL_CAPS case-preserve over the dictionary word generator (filtered to letter-only prefixes so contractions like `i'd` don't break the assumption), and crash-resistance on repeated-character substrings. All pass; integrated into `:app:testDebugUnitTest`.

- ✅ **Next-12.4** shipped 2026-05-15. `.github/workflows/android.yml` now runs `zipalign -c -P 16 -v 4 app-debug.apk` after `:app:assembleDebug` on every push/PR. Locates the SDK build-tools dir dynamically (`ls -1 $ANDROID_HOME/build-tools | sort -V | tail -1`) so future build-tools bumps don't break the gate. If any shipped `.so` is misaligned, the workflow step emits a `::error::` and exits 1 with a pointer back to the ROADMAP §7 Next-12.4 reference + [STD-A15-16KB] documentation. No-op for the current SwiftFloris APK (zero native libs shipped), but engaged the moment Next-2 (`whisper.cpp`) / N1.2 (CleverKeys ONNX) / L1 (LiteRT-LM) / L7 (MCP) bring native code back. `gradle.properties` already pins **NDK 29.0.14206865** + AGP **9.0.0** so the toolchain produces aligned `.so` by default.

---

## 8. LATER (v2.1+, 2027 — high value but heavier or platform-gated)

Each requires either heavy engineering (months), a platform readiness gate, or external dependency (HeliBoard NLnet drop, Android 17, Gemma 3 270M tooling).

### L1. On-device LLM smart-compose (Gemma 3 270M Q4 INT4)

The most hyped-but-actually-shippable AI feature. ~135MB on disk; ~0.75% Pixel 9 Pro battery for 25 conversations [AI1, AI2]; opt-in Smart Compose toggle behind a long-press space gesture (battery-aware, not per-keystroke).

- **L1.1** **LiteRT-LM runtime** (updated 2026-05-15). The MediaPipe LLM Inference API on Android/iOS is now deprecated by Google in favor of **LiteRT-LM** [STD-LITERT-LM] — the same orchestration layer Google uses for Gemini Nano on Chrome and Pixel Watch. Broad model support: Gemma, Llama, Phi-4, Qwen + multimodal text/image/audio + function-calling (`FunctionGemma` 2026 release) + KV-cache management (Prefill / Decode split, quadratic→linear). On a Galaxy S25 Ultra, LiteRT outperforms `llama.cpp` on CPU/GPU for prefill+decode, with NPU acceleration adding **3× over GPU for prefill**. Gemma 4 MTP (multi-token prediction) supercharges decode (>2× faster on mobile GPU with zero quality degradation). Models load from `.litertlm` format (Gemma-3n E2B/E4B and Gemma-3 1B available now). Replaces the L1.1 MediaPipe reference in v4.0.
- **L1.2** GPU/NPU on Snapdragon 8 / Dimensity Ultra; CPU fallback graceful. LiteRT-LM's `preferredBackend` option already exposes CPU/GPU choice for Gemma-3 1B on Android.
- **L1.3** **LoRA hot-swap** per user-domain (formal email vs casual chat). LiteRT-LM supports LoRA on the GPU backend for Gemma-2 2B, Gemma 2B, Phi-2 (attention-layer LoRA only as of 2026-Q2). Base models must be downloaded as `safetensors`. Use the existing SwiftFloris per-app smartbar profile detection to route LoRA selection.
- **L1.4** Inline ghost-text completion (Apple QuickType pattern; matches Gboard Smart Compose's gray-text/swipe-space-to-accept pattern [GBOARD-SMARTCOMPOSE]) — gray suggestion that auto-accepts on space [AI17, COMM-D]. Long-press space gesture toggles the feature on/off; battery-aware (gates on charging or > 30% battery by default).

### L2. Inline translation (Bergamot WASM offline NMT)

Microsoft's Hub Keyboard's idea, finally executed [C9, AI19]. Type English, see Spanish above the prediction row, tap to swap. Bergamot is the Mozilla-coordinated consortium's Apache-2.0 offline translator (Marian NMT, CPU WASM) [STD-BERGAMOT]. **Active fork:** `browsermt/bergamot-translator`. Mozilla's `mozilla/bergamot-translator` is now INACTIVE. Reference Android port: `DavidVentura/offline-translator` (Firefox Translation Models on-device via Bergamot, recently maintained) — provides a working blueprint and surfaces the CMake / Emscripten gotchas (pcre2 dep, NDK setup) you'll hit. Likely path: ship the Bergamot WASM artifact + Firefox translation models, run via `androidx.javascriptengine` or Wasmer/WasmEdge JNI.

- **L2.1** Wire as a smartbar quick action (like SwiftKey's translation toolbar).
- **L2.2** Per-language pair model download UI matches the voice-model pattern (Next-2.3).
- **L2.3** Inline "type EN, see ES on row above" preview surface — like Microsoft's Hub Keyboard, but executed cleanly without the cloud round-trip.

### L3. CJK Pinyin / Jyutping / Zhuyin via librime JNI — **urgency raised 2026-05-15**

The single largest competitive gap vs Gboard [STD-CJK, FR-16, PAIN-16]. FlorisBoard upstream's Han support is shape-based only ("not recommended for daily use"). librime is BSD; bundles cleanly via JNI. **Urgency update:** FUTO Keyboard now ships Traditional + Simplified Chinese (Pinyin, fuzzy Pinyin, Double Pinyin, rudimentary stroke) [FUTO-CJK-2026], so the FOSS CJK gap is closing without SwiftFloris — promote L3 ahead of L4 if a SwiftFloris user base in the CJK market is a 2026 priority.

- **L3.1:** librime JNI module with Pinyin/Jyutping/Zhuyin schemas (use fcitx5-android's librime wrapper as reference).
- **L3.2:** Compose UI for candidate selection (mirrors fcitx5-android's pattern).
- **L3.3:** Japanese via mozc, Korean via Jamo IME (FUTO ships these — references for design, can't copy code).

### L4. Real RTL shaping for Arabic / Persian / Urdu / Hebrew

FlorisBoard upstream RTL is layout-only [STD-RTL]; Gboard struggles with mixed BiDi. SwiftFloris could become best-in-class for ~600M speakers.

- L4.1: Persian Yeh/Kaf normalization, Arabic connected-form shaping pass, Urdu nastaliq layout.
- L4.2: Mixed BiDi composing region — `setComposingRegion` correctness in mixed-direction text.

### L5. Indic transliteration suite

Hindi / Bengali / Tamil / Telugu / Marathi / Gujarati / Punjabi / Kannada via [varnam](https://github.com/varnamproject/govarnam) (MPL-2) or Aksharamukha tables [STD-INDIC, FR-16]. Zero current OSS keyboard does all 8 well.

### L6. Ge'ez script (Amharic / Tigrinya / Tigre / Blin)

Closed-source GeezIME owns the niche today [STD-GEEZ]. Apache-2 SERA implementation + Compose layout = best-in-class for ~110M speakers.

### L7. Native MCP local-LLM bridge (Deskdrop pattern)

User points SwiftFloris at home Ollama / LM Studio over Tailscale; default off; never invoked silently [O7]. Expose MCP-tool-server protocol for composable agent surfaces (calendar, weather, SMS — same toolset Deskdrop's 17 tools demonstrate).

### L8. Keyman LDML keyboard importer (2,500+ language coverage)

Single biggest layout-coverage moat per import. Keyman has 1,000 keyboards spanning 2,500+ languages under MIT [O15]. Importer parses LDML keyboards XML → SwiftFloris layout JSON.

### L9. Honeycomb / hexagonal / T9 / Colemak / Dvorak / Workman alt layouts

Typewise's honeycomb won CES Innovation 2021+2022 [C5]; T9 vacated by TouchPal collapse [C8]; Colemak/Dvorak/Workman are perennial requests. Layout engine work converges here.

### L10. WebAuthn passkey injection from IME

When `autofillHints="password"` is focused, IME directly drives passkey ceremony — no other OSS keyboard ships this [AI WILD-WEBAUTHN].

### L11. Espanso config import + native snippet engine

Espanso is the de facto Linux/macOS text expander [AI19]; native parser of `~/.config/espanso/match/*.yml` would make SwiftFloris the only IME with cross-platform expander interop. Tasker intent endpoints (`swiftfloris.action.INSERT_TEXT`, `…INSERT_CLIP`, `…SWITCH_LAYOUT`, `…TRIGGER_VOICE`) ride alongside [STD-TASKER].

### L12. WhisperInput-style streaming voice + WordStyles

WordStyles (FUTO v0.1.25) renders typed text as styled images [O5] — niche but zero competitors.

---

## 9. UNDER CONSIDERATION (no commitment; will graduate or retire)

| Item | Why it's interesting | What blocks commitment |
|---|---|---|
| Userscripts for keyboards (Tampermonkey-for-IME) | No precedent in OSS keyboards; could borrow ScriptVault's Monaco/MV3 architecture [AI WILD-USERSCRIPTS] | Android's IME security model forbids in-process untrusted code; needs a sandboxed signed-Kotlin-DSL shape, not raw JS |
| $1 Unistroke recognizer for chord macros | 100 LoC per Wobbrock; lets users draw a sigil to fire a custom action [AI WILD-DOLLAR] | UX research needed — does anyone want this on a keyboard? |
| Federated-learning opt-in via FedAvg over Syncthing | Single-user federated training across phone+tablet to fine-tune Gemma LoRA adapters [AI WILD-FED] | Heavy ML tooling burden; defer until L1 lands |
| Per-app "tone profile" (KenLM weight swap by package name) | Slack=informal, Outlook=formal — same package-detection plumbing as N4.3 | Useful only after Next-3.1 ships KenLM |
| Audit log: "what got typed into which package" (locally encrypted) | Verifiable + defensive privacy proof; users can self-confirm no exfil [AI WILD-AUDIT] | UX for "where did this log go?" needs design; privacy-paradox risk |
| Aurora Store / Obtainium auto-update wiring beyond manual subscription | Smoother first-time onboarding | Solved by N6.5 (one-tap URL) for now; revisit if user demand |
| NLnet Mobifree funding application | HeliBoard + Unexpected Keyboard both got funded [O2, O10] | Wait until v2.0 ships, then apply with concrete deliverables |
| Cinematic key-click haptics scripted per theme | First themed-haptic keyboard [AI WILD-CINEMATIC] | Follows N3.3 + N11.2; promote when haptic envelope lands |

---

## 10. EXPLICITLY REJECTED (with reasoning, so this doesn't get re-litigated)

| Rejected | Why |
|---|---|
| Cloud sync of personal LM via vendor servers | Violates §1 no-network. Replace with N5 P2P CRDT |
| Microsoft / Google / any account requirement | Same. SwiftKey forcing MS account by 2026-05-31 [C2] is a switch trigger driving users to us, not a feature to imitate |
| GPL / AGPL / Source-First / undeclared-license code in main app | Apache-2.0 ceiling. Conceptual borrowing only; module isolation acceptable for modules under their own license, never linked into `:app` |
| Bundling closed-source `libjni_latinimegoogle.so` from old GApps | Violates auditability. HeliBoard's reluctant carrier-pigeon distribution of this file is exactly the antipattern we exist to avoid |
| Telemetry / federated learning to vendor cloud | Violates §1. Local-only per-device opt-in (L1.3 LoRA) is the maximum |
| In-keyboard ads or sponsored content | Violates trust posture. ASK #2803 (43 reactions) [PAIN-23] is the user verdict |
| Bing / Copilot / Gemini API integration in core | Cloud-bound; account-bound; vendor-bound. L7's MCP bridge is the opt-in escape valve for users who want it on their own infra |
| Default-on T9 layout | Acceptable as alt layout (Later L9); not as default — would alienate the SwiftKey-parity audience |
| In-keyboard search (Maps/YouTube/web) à la Gboard | Cloud-bound, telemetry surface, tracking risk. Out unless someone designs a fully-local SearXNG plugin (Under Consideration material) |
| GIF keyboard that hits Tenor / Giphy | Same — cloud + telemetry. Bundled local sticker packs + image-paste from clipboard (N9) are the offline equivalent |
| Google Play Store as primary distribution | Forces target-SDK churn, Integrity-API entanglement, Data-Safety-form privacy framing we'd rather avoid; revisit only as a separately-signed mirror track |
| Google's `libjni_latinimegoogle.so` closed swipe blob (re-statement) | Rejected. N1.1 / N1.2 / N1.3 are the only acceptable paths |
| Self-update (in-app APK download + install) | Supply-chain risk; let Obtainium / F-Droid / IzzyOnDroid handle update orchestration. We pin SHA256 in README (N6.2/N6.3) |
| Mandatory analytics opt-out toggle that defaults on | If we adopt ACRA later (Next-class), it's opt-in only [AI ACRA] |

---

## 11. Cross-Cutting Concerns (every category named, none silently dropped)

This section guarantees nothing fell through the cracks. Each is either represented in §6/§7/§8, scheduled below, or explicitly out of scope with reason.

| Concern | Where addressed |
|---|---|
| **Security** | N7 (privacy hardening — all of N7.1/7.2/7.3/7.4/7.5 shipped), N6.4 (CVE scan), Next-5 (CRDT E2EE sync), L7 (MCP key vault pattern), Next-10 (addon enrolment's no-INTERNET hard reject — supply-chain protection for the plugin surface). Threat model: CAKI [STD-CAKI], password-field guards [STD-A11Y-IMETRY], `docs/THREAT_MODEL.md` |
| **Accessibility (a11y)** | N8 (scoped pass: target size, contrast, TalkBack labels, reduced motion, switch access, voice access) + Later: dwell-tap, bounce/slow/sticky keys in IME, thumb-zone heatmap [PAIN-D-3] |
| **i18n / l10n** | N2 (multilingual auto-detect), Next-1 (broader Latin dictionaries), L3-L6 (CJK / RTL / Indic / Ge'ez). Crowdin already wired for UI strings |
| **Observability / telemetry** | Opt-in only. ACRA self-hosted *or* manual GitHub-issue crash file (no auto-upload) [STD-ACRA]. Local Macrobenchmark + Perfetto for development (N12.1) [STD-MACROBENCH] |
| **Testing** | N12.3 (property-based autocorrect), N12.2 (Roborazzi visual regression), expanded Kotest coverage of `KeyboardManager`, `EditorInstance`, `NlpManager`. IMPROVEMENT_PLAN Workstream 1 fully promoted into roadmap |
| **Docs** | Per-feature release notes pattern continues (`RELEASE_NOTES_v1.X.Y.md`); add an `ARCHITECTURE.md` covering NLP pipeline + theming engine + extension model after the runtime-stub fixes (N11). Consolidate the dispersed `*MULTILINGUAL.md`, `VOICE_*.md`, `FUTO_VOICE_*.md` into `docs/` after Next-2 ships |
| **Distribution / packaging** | N6.2 + N6.3 (signed releases + F-Droid reproducible verified badge — F-Droid's 2025 Reproducibility Status pages [STD-FDROID-VERIFIED] are now live, so SwiftFloris targets the `Reproducible` tier (developer-signed + verified) once `fdroiddata` PR lands); N6.5 (Obtainium one-tap URL); IzzyOnDroid + Accrescent listings as Next-class side-quests [STD-FDROID-REPRO, STD-FDROID-OBTAINIUM]. Next-12.4 (16KB-page CI gate) when native code is back in tree |
| **Plugin ecosystem** | Next-10.1 + Next-10.2 shipped v1.7.9 (manifest schema + enumerator + AndroidManifest queries + signature-protected permission); Next-10.3 (first addon pack: Polish dictionary) pending an `AddonRegistry` live-state + Settings → Addons screen + asset-extraction pipeline. L8 (Keyman LDML import), Snygg theme distribution via FlorisBoard Addons Store [STD-FLORIS-EXT] |
| **Mobile-specific (form factors)** | Next-7 (floating + split + one-handed), Next-4 (stylus handwriting); foldables ship as a sub-task of split-keyboard testing matrix [PAIN-14] |
| **Offline / resilience** | The whole product. Specifically: voice (Next-2), translation (L2), LLM (L1), sync (N5) — all offline-only by construction |
| **Multi-user / collab** | Next-5 (E2EE personal-dict sync between user's own devices). Multi-user-on-one-device is out of scope (Android IME is per-user by platform design) |
| **Migration paths** | Next-6 (Gboard / HeliBoard / FlorisBoard / KLFC importers); SwiftKey unfortunately out of reach without root [MIG-SK] |
| **Upgrade strategy** | Semver `v1.X.Y`; major bumps only on breaking dictionary-format or extension-format changes; database migrations via Room AutoMigrations starting Next class. Documented per-release in `RELEASE_NOTES_v*.md` |

---

## 12. Operating Cadence

- **Bi-weekly minor releases** (one Now-tier item per release, on a 2-week target).
- **Monthly Next-tier slice** (one bullet of one Next item; Next items are decomposable).
- **Quarterly Later prep** — at the start of each quarter, pick one Later item and convert its first scoping bullet into a Next slice.
- **Continuous correctness floor** — IMPROVEMENT_PLAN.md Workstreams 1 (Test Coverage), 2 (Lint Debt), 3 (Pure Core Extraction), 4 (Input Hardening), 9 (Repo Hygiene), 14 (Build/Dep Hygiene) run alongside roadmap work; not a separate track.
- **Verification before "shipped"** — every release passes `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, an `adb install` smoke on a real device, and a manual QA pass per the (forthcoming, N6) checklist.

---

## 13. Out-of-Scope Adjacent Wins (worth one sentence each)

These came up in research but don't fit SwiftFloris; calling them out so future contributors don't propose them as roadmap items:

- **A separate Voice IME** (Sayboard pattern) — out; voice belongs *inside* the keyboard (Next-2), not as a separate IME.
- **A separate Clipboard manager app** — out; clipboard already lives inside the IME.
- **AOSP LatinIME maintenance** — out; that's HeliBoard's mandate.
- **Trinity / WhisperInput stand-alone keyboard** — out; voice integrates as Next-2 inside SwiftFloris.
- **Generic Android system-wide spellcheck service** — interesting (and `FlorisSpellCheckerService.kt:141` is the existing hook, now a documented delegate to AOSP per N11.3), but Out for this roadmap pass; revisit only if we have spare capacity after L1.

---

## 14. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| FlorisBoard upstream merges back the v0.6 glide work and obviates N1 | Medium | Medium | Re-base; ship per-language polish on top |
| HeliBoard's NLnet glide drop ships first and becomes the de facto OSS swipe lib | High | Low (good for users; we adopt it; positive outcome) | Plan N1.1 as the default path |
| Gemma 3 270M licensing changes break L1 plan | Low | Medium | Have Phi-3 / Llama 3.2 1B fallback on stand-by; LiteRT-LM is model-agnostic |
| F-Droid reproducible-build verification fails due to Gradle/AGP/NDK churn | Medium | Low | Pin everything in `gradle.properties`; subscribe to F-Droid Reproducible Builds discussion; address per release |
| ~~The two `TODO("…")` runtime stubs get hit in the wild~~ — **Resolved in v1.7.0 (N11.1/N11.2/N11.3)**; preserved here as audit trail | n/a | Resolved | n/a |
| Kotlin 2.3 / Compose 2026.03.01 BOM regressions | Medium | Medium | Pin BOM until all four themes pass Roborazzi (N12.2) |
| Personal-dictionary growth balloons Room DB; Room main-thread query adds lag | Medium | Medium | The current `allowMainThreadQueries()` flag is a known concern; convert to suspend-only access on a Next-class refactor |
| Unicode 17 / Emoji 17 backports break `EmojiCompat` lazy loader | Low | Low | N10.2 verifies pre-release |
| The malicious closed-source FlorisBoard fork "CleverType AI Keyboard" [F3] confuses SwiftFloris's positioning | Low | Low | README + Settings → About explicitly distance from any closed fork |
| Microsoft SwiftKey account-retirement cutoff 2026-05-31 [SK-RETIRE] consumes user attention; SwiftFloris missing the migration window means defectors land on FUTO / HeliBoard / CleverType instead | High | Medium (one-shot opportunity) | Ship a release-day blog + reddit thread on 2026-05-31 with `docs/MIGRATE_FROM_SWIFTKEY.md` and the Obtainium URL front-and-center; pin a GitHub release the same day; consider a v1.7.10 marketing-only retag if v1.7.9 doesn't sign by then |
| AOSP 2026 cadence change [STD-AOSP-2026] (source published only Q2 + Q4) extends the gap between SwiftFloris seeing new platform APIs and being able to test against them | Medium | Low | Switch upstream tracking to `android-latest-release` branch as Google now recommends; treat developer previews as the API-discovery surface, AOSP drops as the integration window |
| LiteRT-LM migration [STD-LITERT-LM] forces L1 to retarget if MediaPipe LLM Inference path was already chosen | Low (we hadn't started) | Low | L1.1 retargeted to LiteRT-LM as of v5.0; no code yet to migrate |
| HeliBoard NLnet open-glide deadline 2026-06-01 [NLNET-GT] slips | Medium | Medium (delays N1.1, keeps SwiftFloris on the bounded statistical classifier) | Keep N1.3 path live and quality-improve the statistical classifier; the bounded-dictionary slice in v1.7.x is already a meaningful improvement over the FlorisBoard upstream baseline |

---

## 15. Definition of Done (for individual items)

Every item, before being marked complete:

1. Implementation lands.
2. Tests added (unit at minimum; Roborazzi if visual; Macrobenchmark if perf-sensitive after N12.1).
3. Documentation updated (README badge, RELEASE_NOTES_vX.Y.Z.md, IMPROVEMENT_PLAN.md if a workstream task closes).
4. `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` all green locally.
5. Manual QA pass on a real device (Galaxy R5CY34G070L is the current reference).
6. Source/license attribution updated (`NOTICE`, `LICENSES/`) if a new dependency entered.
7. APK signed and installable; SHA256 published.

---

## 16. Glossary

- **AOSP** — Android Open Source Project.
- **CRDT** — Conflict-free Replicated Data Type; merge structure that converges across devices without coordination.
- **IME** — Input Method Editor; what Android calls a soft keyboard.
- **LiteRT** — TensorFlow's renamed mobile runtime (formerly TFLite); production successor as of TensorFlow 2.21.
- **MCP** — Model Context Protocol; emerging tool-server standard for AI assistants.
- **NMT** — Neural Machine Translation.
- **SCOWL** — Spell-Checker Oriented Word Lists (Kevin Atkinson, Apache-2.0-compatible BSD).
- **SLM / LLM** — Small / Large Language Model.
- **Snygg** — FlorisBoard's theme engine ("snygg" is Swedish for "stylish").
- **WCAG** — Web Content Accessibility Guidelines.
- **PWLE** — Piecewise Linear Envelope; Android 16's normalised haptic API (`VibrationEffect.WaveformEnvelopeBuilder`) that abstracts per-device actuator differences so identical primitives feel similar across hardware.
- **MTP** — Multi-Token Prediction; LiteRT-LM technique that lets Gemma 4 generate multiple decode tokens per inference step, >2× faster decode on mobile GPU.
- **PWA / Q4 / INT4 / INT8 / QAT** — Quantization-aware training and integer-quantised model formats used to shrink LLM disk + runtime footprint without losing quality.
- **swypelibs** — Slang for the closed-source `libjni_latinimegoogle.so` blob historically extracted from GApps and used by HeliBoard for swipe typing. SwiftFloris **rejects** bundling this.

---

## Appendix — Source URLs (every claim above traces here)

### Internal (this repo)

- `RELEASE_NOTES_v1.5.3.md`, `v1.5.4.md`, `v1.5.5.md`, `v1.6.0.md`, `v1.7.0.md`, `v1.7.1.md`, `v1.7.2.md`, `v1.7.3.md`, `v1.7.4.md`, `v1.7.5.md`, `v1.7.6.md`, `v1.7.7.md`, `v1.7.9.md` (v1.7.8 rebadged to v1.7.9 after pre-existing tag collision)
- `IMPROVEMENT_PLAN.md` (workstreams 1–15)
- `docs/THREAT_MODEL.md` (N7.3), `docs/REPRODUCIBLE_BUILDS.md` (N6.3), `docs/INLINE_AUTOFILL.md` (Next-9.3, v1.7.9), `docs/MIGRATE_FROM_SWIFTKEY.md` (Next-6.3, v1.7.9)
- `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/tools.versions.toml`, `gradle.properties`
- `app/proguard-rules.pro` (R8 missing-class suppressions for Tink errorprone/javax.annotation, v1.7.9)
- `app/src/main/AndroidManifest.xml` (signature-protected `permission.REGISTER_ADDON` + `<queries>` for Next-10.1 v1.7.9)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/{AddonContract,AddonEnumerator}.kt` (Next-10.1/10.2)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryImporter.kt` (Next-6.1/6.2)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/PerAppAccentResolver.kt` (Next-11.3 foundation)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/{VoiceInputManager,StreamingVoiceTranscriptBuffer,VoiceModelSelector,VoiceRecognitionEngineSelector,VoiceModelInstallStore,VoiceModelCatalog}.kt` (Next-2.1/2.2/2.3/2.4)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt` (SQLCipher-encrypted at rest, N7.4)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/{NlpManager,ImmediateAutocorrect,MultilingualTokenScorer,TypingContextExtractor,SwiftKeyCandidateRanker,SwiftKeyCandidateTuning,GlideContextTuning,GlideContextRescorer,latin/{LatinLanguageProvider,SymSpellIndex,ColdStartNextWordPriors,LatinDictionarySnapshot}}.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/{SmartbarActionProfiles,QuickAction,QuickActionsEditorPanel,QuickActionButton}.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/{CandidatesRow,InlineSuggestionsUi,Smartbar}.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/{TextKeyboardLayout,TextKeyData,AdaptiveTouchModel,BottomRowPreset}.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/{PersonalBigramStore,PersonalTrigramStore}.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/{ImeWindow,ImeWindowController,ImeWindowEditorHandles,ImeWindowMode,ImeWindowConfig,ImeWindowConstraints}.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/{FlorisImeThemeBaseStyle,ThemeManager}.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/about/SigningFingerprint.kt` (N7.5, `sha256OfPackage` arbitrary-package variant added v1.7.9)
- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/FlorisLocale.kt` (hard-coded `supportsCapitalization` / `supportsAutoSpace` tables; ICU replacement open)
- `app/src/test/kotlin/.../**Test.kt` × 60 (`LatinDictionarySuggesterTest`, `LatinSuggesterPropertyTest`, `AddonManifestTest`, `PerAppAccentResolverTest`, `DictionaryImporterTest`, `SmartbarActionProfilesTest`, `ThemeContrastTest`, `TouchTargetWcagTest`, `MultilingualTokenScorerTest`, `GlideContextRescorerTest`, `SwiftKeyTraceReplayFixtureTest`, `PersonalDictionaryEncryptionTest`, `PersonalDictionaryIsolationTest`, …)
- `.github/workflows/{android,release,dependency-scan,crowdin-upload,validate-strings-no-translations}.yml`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/ImeWindowMode.kt` (floating placeholder)
- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/FlorisLocale.kt` (hard-coded locales)

### F — FlorisBoard ecosystem

- [F1] [FlorisBoard repo](https://github.com/florisboard/florisboard) · [ROADMAP](https://github.com/florisboard/florisboard/blob/main/ROADMAP.md) · [Releases](https://github.com/florisboard/florisboard/releases)
- [F2] FlorisBoard issues: [#3233 k3lp Unicode-Keyboard-v3](https://github.com/florisboard/florisboard/issues/3233), [#3225 PIN scrambling](https://github.com/florisboard/florisboard/issues/3225), [#3280 Snygg v2](https://github.com/florisboard/florisboard/issues/3280)
- [F3] [FlorisBoard #3234 — malicious closed fork "CleverType AI Keyboard"](https://github.com/florisboard/florisboard/issues/3234)
- [FlorisBoard privacy policy](https://florisboard.org/legal/privacy/)
- [FlorisBoard extensions docs](https://docs.florisboard.org/extensions)

### O — OSS competitors

- [O1] [CleverKeys repo](https://github.com/tribixbite/CleverKeys) · [CleverKeys-ML training repo](https://github.com/tribixbite/CleverKeys-ML) · [project site](https://cleverkeys.app/)
- [O2] [HeliBoard repo](https://github.com/Helium314/HeliBoard) · [Wiki](https://github.com/Helium314/HeliBoard/wiki) · [layouts.md](https://github.com/Helium314/HeliBoard/blob/main/layouts.md)
- [aosp-dictionaries (HeliBoard)](https://codeberg.org/Helium314/aosp-dictionaries)
- [O3] [OpenBoard repo](https://github.com/openboard-team/openboard) · [PrivacyGuides successor thread](https://discuss.privacyguides.net/t/openboard-android-keyboard-removed-from-google-app-store-lets-search-the-forks/13602)
- [O4] [AnySoftKeyboard repo](https://github.com/AnySoftKeyboard/AnySoftKeyboard) · [LanguagePack repo](https://github.com/AnySoftKeyboard/LanguagePack) · [project site](https://anysoftkeyboard.github.io/) · [addons CONTRIBUTING](https://github.com/AnySoftKeyboard/AnySoftKeyboard/blob/main/addons/CONTRIBUTING.md)
- [O5] [FUTO Keyboard mirror](https://github.com/futo-org/android-keyboard) · [Releases](https://github.com/futo-org/android-keyboard/releases) · [docs language models](https://docs.keyboard.futo.org/settings/languagesmodels) · [text prediction](https://docs.keyboard.futo.org/settings/textprediction)
- [O6] [FUTO Voice Input repo](https://github.com/futo-org/voice-input) · [whisper-acft v3 turbo issue](https://github.com/futo-org/whisper-acft/issues/9)
- [O7] [Deskdrop repo](https://github.com/SvReenen/Deskdrop) · [project site](https://svreenen.github.io/Deskdrop/) · [LeanType repo](https://github.com/LeanBitLab/HeliboardL)
- [O8] [Thumb-Key repo](https://github.com/dessalines/thumb-key)
- [O9] [Unexpected Keyboard repo](https://github.com/Julow/Unexpected-Keyboard)
- [O10] [Simple Keyboard](https://github.com/SimpleMobileTools/Simple-Keyboard) · [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard)
- [O11] [Sayboard repo](https://github.com/ElishaAz/Sayboard)
- [O12] [Hacker's Keyboard #875](https://github.com/klausw/hackerskeyboard/issues/875)
- [O14] [fcitx5-android repo](https://github.com/fcitx5-android/fcitx5-android)
- [O15] [Keyman repo](https://github.com/keymanapp/keyman) · [keyboards repo](https://github.com/keymanapp/keyboards) · [keyman.com get-involved](https://keyman.com/about/get-involved)
- [H1] HeliBoard issues: [#2226 NLnet open glide](https://github.com/Helium314/HeliBoard/issues/2226), [#326 floating tablet](https://github.com/Helium314/HeliBoard/issues/326), [#363 GIFs](https://github.com/Helium314/HeliBoard/issues/363), [#490 clipboard images](https://github.com/Helium314/HeliBoard/issues/490), [#695 toolbar customization](https://github.com/Helium314/HeliBoard/issues/695), [#786 Asian languages](https://github.com/Helium314/HeliBoard/issues/786), [#891 Accrescent](https://github.com/Helium314/HeliBoard/issues/891), [#1055 Gboard look](https://github.com/Helium314/HeliBoard/issues/1055), [#1289 horizontal-swipe-on-backspace](https://github.com/Helium314/HeliBoard/issues/1289), [#1342 scalable font/key](https://github.com/Helium314/HeliBoard/issues/1342), [#2124 multi-lang autocorrect bleed](https://github.com/Helium314/HeliBoard/issues/2124), [#2165 dual-finger gestures](https://github.com/Helium314/HeliBoard/issues/2165)

### C — Commercial competitors

- [C1] [Microsoft SwiftKey official](https://www.microsoft.com/en-us/microsoft-copilot/for-individuals/do-more-with-ai/general-ai/all-you-can-do-swiftkey-ai-keyboard)
- [C2] [SwiftKey requires MS Account by 2026-05-31 — Windows Central](https://www.windowscentral.com/software-apps/swiftkey-will-soon-require-a-microsoft-account-data-to-be-moved-to-onedrive)
- [C3] [SwiftKey Copilot removed 2025 — Microsoft Support FAQ](https://support.microsoft.com/en-us/topic/faqs-for-copilot-changes-in-swiftkey-c02289e6-c5b3-401c-af8d-f6c88409a2d2)
- [C4] [SwiftKey Clarity multi-word — TechCrunch](https://techcrunch.com/2015/04/27/swiftkey-debuts-clarity-an-experimental-keyboard-featuring-multi-word-autocorrect/)
- [C5] [Typewise CES 2021+2022 award](https://www.neowin.net/news/typewise039s-honeycomb-ai-keyboard-app-secures-ces-innovation-award-once-again/)
- [C6] [Gboard Sept 2025 AI writing tools — Google blog](https://blog.google/products-and-platforms/platforms/android/new-android-features-september-2025/)
- [C7] [Chrooma Keyboard — APKMirror](https://www.apkmirror.com/apk/loopsie-srl/chrooma-keyboard/)
- [C8] [TouchPal Wikipedia (CooTek + adware ban)](https://en.wikipedia.org/wiki/TouchPal); [Grammarly Mobile](https://www.grammarly.com/mobile)
- [C9] [Microsoft Hub Keyboard — Liliputing](https://liliputing.com/microsoft-hub-keyboard-for-android-includes-translation-clipboard-search-tools/) · [Hub image translation — 9to5Google](https://9to5google.com/2016/04/21/microsoft-image-inline-translation-translator-hub-keyboard/)
- [GBOARD-VOICE] [Google disabled offline voice typing on Gboard for non-Pixel — GrapheneOS forum](https://discuss.grapheneos.org/d/26041-google-disabled-voice-typing-on-gboard-without-network-access)
- [Gboard one-handed help](https://fotoai.app/b/enable-one-handed-mode-on-android-keyboard); [Samsung One UI 7 Galaxy AI any keyboard](https://www.sammobile.com/news/one-ui-7-0-galaxy-ai-writing-tools-any-keyboard/)
- [Apple Intelligence iOS 18 Writing Tools — AppleMagazine](https://applemagazine.com/predictive-text-engine-012)
- [SMART-EDIT] [Gboard Smart Edit voice — XDA / Pixel Drop](https://www.xda-developers.com/google-smart-compose-gboard-android-messages-telegram-whatsapp/)

### COMM — Community pain & feature requests (selected)

- [COMM-A] [r/SwiftKey + complaints synthesis — Cybernews](https://cybernews.com/tech/microsoft-switfkey-service-logins/)
- [COMM-B] [SwiftKey Account requires MS account — HN 35597152](https://news.ycombinator.com/item?id=35597152)
- [COMM-C] [FlorisBoard #1283 (32 reactions) — autocorrect demand](https://github.com/florisboard/florisboard/issues/1283); [#1474 predictions](https://github.com/florisboard/florisboard/issues/1474); [#677 OOM (36 reactions)](https://github.com/florisboard/florisboard/issues/677); [#2362 invisible keys regression](https://github.com/florisboard/florisboard/issues/2362)
- [COMM-D] FlorisBoard issues: [#45 emoji search (80 reactions)](https://github.com/florisboard/florisboard/issues/45); [#196 customizable layout (21)](https://github.com/florisboard/florisboard/issues/196); [#229 modifier keys (24)](https://github.com/florisboard/florisboard/issues/229); [#155 multilingual](https://github.com/florisboard/florisboard/issues/155); [#1007 capitalization-aware](https://github.com/florisboard/florisboard/issues/1007); [#938 randomized password layout](https://github.com/florisboard/florisboard/issues/938); [#1888 search clipboard history](https://github.com/florisboard/florisboard/issues/1888); [#2728 inline autofill](https://github.com/florisboard/florisboard/issues/2728); [#116 dual-finger gestures (35)](https://github.com/florisboard/florisboard/issues/116); [#737 delete learned word](https://github.com/florisboard/florisboard/issues/737)
- [COMM-E] AnySoftKeyboard issues: [#1832 customizable bottom row](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/1832), [#1404 cursor placement (highly upvoted)](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/1404), [#4426 random period/space](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4426), [#1399 delete from learned dict](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/1399), [#1684 auto-replace shortcuts](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/1684), [#2803 ad/nag suggestion bar (43 reactions)](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/2803), [#1952 split keyboard](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/1952), [#1412 T9 layout](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/1412), [#1233 CJKV demand](https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/1233)
- [COMM-F] OpenBoard issues: [#7 multilingual (55 reactions)](https://github.com/openboard-team/openboard/issues/7), [#167 wrong-language correction (49)](https://github.com/openboard-team/openboard/issues/167), [#3 glide typing (49)](https://github.com/openboard-team/openboard/issues/3), [#35 emoji search (51)](https://github.com/openboard-team/openboard/issues/35), [#387 inline autofill (28)](https://github.com/openboard-team/openboard/issues/387)
- [COMM-G] [Privacy Guides — what keyboard?](https://discuss.privacyguides.net/t/what-keyboard-are-you-using-on-android/15973); [MakeUseOf — best open-source Gboard alternatives](https://www.makeuseof.com/best-open-source-gboard-alternatives-tested/); [HN 40831489 FUTO discussion](https://news.ycombinator.com/item?id=40831489)
- [COMM-K] [Computerworld — Android typing trick](https://www.computerworld.com/article/1714907/android-typing-trick.html); [TechWiser — text expanders](https://techwiser.com/text-expander-apps-for-android/)
- [Bitwarden inline autofill #1156](https://github.com/bitwarden/mobile/issues/1156); [PR #1145](https://github.com/bitwarden/mobile/pull/1145); [#62](https://github.com/bitwarden/mobile/issues/62)
- [Trinity College Dublin Gboard paper (audio at any time)](https://www.scss.tcd.ie/Doug.Leith/pubs/gboard_kamil.pdf)

### AI — Adjacent / AI-keyboard wave

- [AI1] [Gemma 3 270M intro — Google Developers Blog](https://developers.googleblog.com/en/introducing-gemma-3-270m/)
- [AI2] [Gemma 3 270M demo — DataCamp](https://www.datacamp.com/tutorial/gemma-3-270m); [On-Device LLMs: State of the Union 2026](https://v-chandra.github.io/on-device-llms/)
- [AI3] [whisper.cpp](https://github.com/ggml-org/whisper.cpp); [Vosk vs Whisper 2026 guide](https://www.sinologic.net/en/2026-05/vosk-vs-whisper-local-the-ultimate-2026-guide-to-self-hosted-speech-recognition-stt.html)
- [AI4] [CleverKeys ML training repo](https://github.com/tribixbite/CleverKeys-ML)
- [AI5] [fastText 157-language vectors](https://fasttext.cc/docs/en/crawl-vectors.html)
- [AI6] [SymSpell — Symmetric Delete algorithm](https://github.com/wolfgarbe/SymSpell)
- [AI7] [SymSpell vs BK-tree benchmark](https://medium.com/data-science/symspell-vs-bk-tree-100x-faster-fuzzy-string-search-spell-checking-c4f10d80a078); [In-Depth Comparison of 14 Spelling Correction Tools (LREC 2020)](https://aclanthology.org/2020.lrec-1.228.pdf)
- [AI8] [WhisperInput keyboard (alex-vt)](https://github.com/alex-vt/WhisperInput); [FUTO larger Whisper models on flagships](https://github.com/futo-org/android-keyboard/issues/1863)
- [AI9] [Joplin voice typing spec](https://joplinapp.org/help/dev/spec/voice_typing/)
- [AI10] [Vosk Android](https://alphacephei.com/vosk/android); [Vosk API](https://github.com/alphacep/vosk-api)
- [AI11] [KenLM toolkit](https://kheafield.com/code/kenlm/); [KenLM estimation](https://kheafield.com/code/kenlm/estimation/)
- [AI12] [edugp/kenlm 24-language pre-trained models — HF](https://huggingface.co/edugp/kenlm)
- [AI13] [wordfreq (rspeer)](https://github.com/rspeer/wordfreq)
- [AI14] [SUBTLEX-US frequency norms](https://www.ugent.be/pp/experimentele-psychologie/en/research/documents/subtlexus); [subs2vec](https://github.com/jvparidon/subs2vec)
- [AI15] [Real-Time Optimized N-gram for Mobile (arXiv)](https://arxiv.org/pdf/2101.03967)
- [AI16] [MediaPipe LLM Inference Android](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
- [AI17] [Apple Intelligence Foundation Language Models](https://arxiv.org/html/2507.13575v1); [Apple iOS 17 transformer autocorrect — TechCrunch](https://techcrunch.com/2023/06/05/thanks-to-ai-ios-17-will-learn-your-swears/)
- [AI18] [Local-first CRDTs over Syncthing (tonsky)](https://tonsky.me/blog/crdt-filesync/); [Automerge / Yjs landscape](https://crdt.tech/implementations); [Yjs](https://github.com/yjs/yjs)
- [AI19] [Espanso options](https://espanso.org/docs/configuration/options/); [Espanso extensions](https://espanso.org/docs/matches/extensions/)
- [AI22] [Joplin tiered voice typing](https://joplinapp.org/help/dev/spec/voice_typing/)
- [AI ACRA] [ACRA](https://github.com/ACRA/acra); [Sentry mobile privacy](https://docs.sentry.io/security-legal-pii/security/mobile-privacy/)
- AI WILD: see "Wild Ideas" section in adjacent-research output (userscripts, $1 Unistroke, federated learning, audit log, cinematic haptics, WebAuthn injection)

### STD — Standards / a11y / i18n / security

- [STD-INPUTSERVICE] [InputMethodService API](https://developer.android.com/reference/android/inputmethodservice/InputMethodService); [InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection)
- [STD-A14] [Android 14 features](https://developer.android.com/about/versions/14/features); [Android 16 release notes](https://developer.android.com/about/versions/16/release-notes); [SDK setup](https://developer.android.com/about/versions/16/setup-sdk)
- [STD-STYLUS] [Stylus in text fields](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/stylus-input-in-text-fields); [Compose stylus](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/stylus-input-in-text-fields)
- [STD-INK] (Google ML Kit Digital Ink Recognition — official Android docs surface)
- [STD-INLINE-AUTOFILL] [Integrate autofill with IMEs](https://developer.android.com/identity/autofill/ime-autofill); [Create input method (Switch Access)](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [STD-A16-HAPTIC] [Android custom haptic effects (16+ envelopes)](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects); [haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
- [STD-Material3] [M3 design tokens](https://m3.material.io/foundations/design-tokens)
- [STD-M3E] [Material 3 Expressive launch — Google blog](https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/)
- [STD-TALKBACK] [TalkBack keyboard shortcuts](https://support.google.com/accessibility/android/answer/6110948); [TalkBack 16.2](https://support.google.com/accessibility/android/answer/16800105); [SwiftKey + TalkBack](https://support.microsoft.com/en-us/topic/accessibility-in-microsoft-swiftkey-keyboard-a3b12d18-61ba-4e2c-82bc-c42e8f12c62c)
- [STD-A11Y-IMETRY] [TalkBack braille](https://support.google.com/accessibility/android/answer/9728765); [Voice Access](https://support.google.com/accessibility/android/answer/6151848)
- [STD-WCAG-TARGET] [WCAG 2.5.5 Target Size](https://www.w3.org/WAI/WCAG21/Understanding/target-size); [WCAG 2.5.8 (TestParty)](https://testparty.ai/blog/wcag-target-size-guide)
- [STD-WCAG-CONTRAST] (WCAG 2.1 1.4.3 Contrast Minimum — W3C reference)
- [STD-REDUCED-MOTION] [Android reduced motion (eevis.codes)](https://eevis.codes/blog/2022-12-12/android-animations-and-reduced-motion/)
- [STD-SWITCH] [Voice Access activation keys](https://support.google.com/accessibility/android/answer/6151843?hl=en)
- [STD-CJK] [FlorisBoard Chinese language packs](https://github.com/florisboard/florisboard/blob/main/LANGUAGEPACKS-CHINESE.md); [Issue #2211 Pinyin](https://github.com/florisboard/florisboard/issues/2211)
- [STD-RTL] (FlorisBoard upstream RTL state — repo audit)
- [STD-INDIC] [FlorisBoard #1327 Hindi transcription](https://github.com/florisboard/florisboard/issues/1327)
- [STD-LATIN-LEAPFROG] [HeliBoard FAQ](https://github.com/HeliBorg/HeliBoard/wiki/FAQ); [HeliBoard #2067 / #1699 word-learning threshold](https://github.com/HeliBorg/HeliBoard/issues/2067)
- [STD-GEEZ] [GeezIME 2025](https://play.google.com/store/apps/details?id=com.geezlab.geezime); [Keyman Ge'ez keyboard](https://keyman.com/keyboards/gff_geez)
- [STD-UNICODE17] [ICU releases](https://github.com/unicode-org/icu/releases); [Android i18n via ICU](https://developer.android.com/guide/topics/resources/internationalization)
- [STD-EMOJI17] [Unicode 17 / Emoji 17 (Emojipedia)](https://blog.emojipedia.org/whats-new-in-unicode-17-0/); [Emoji 16 on Android 16 (TechRadar)](https://www.techradar.com/phones/android/android-16-users-can-get-early-access-to-163-new-emojis-thatll-soon-be-everywhere-heres-how)
- [STD-API-DELETE-CODEPOINTS] (`InputConnection#deleteSurroundingTextInCodePoints` — Android docs)
- [STD-FDROID-REPRO] [F-Droid keyboards reproducible (March 2025)](https://f-droid.org/en/2025/03/04/even-my-keyboard-is-built-reproducibly.html); [F-Droid reproducible builds docs](https://f-droid.org/docs/Reproducible_Builds/)
- [STD-FDROID-OBTAINIUM] [F-Droid 2025 status update](https://f-droid.org/en/2026/01/23/fdroid-in-2025-strengthening-our-foundations-in-a-changing-mobile-landscape.html); [Google Play target SDK](https://developer.android.com/google/play/requirements/target-sdk); [Play Integrity overview](https://developer.android.com/google/play/integrity/overview)
- [STD-CVE] [Android Dec 2025 bulletin](https://source.android.com/docs/security/bulletin/2025-12-01); [CVE-2025-48593](https://socprime.com/blog/cve-2025-48593-vulnerability-in-android/)
- [STD-CAKI] [CAKI ESORICS paper](https://staff.ie.cuhk.edu.hk/~khzhang/my-papers/2015-esorics-ime.pdf)
- [STD-PERS-DICT-ENC] (Android Keystore + EncryptedSharedPreferences — official docs); [SQLCipher — Zetetic]
- [STD-MACROBENCH] [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [STD-PERFETTO] (`androidx.tracing:tracing-perfetto:1.0.0` — official Android perf docs)
- [STD-ROBORAZZI] [Roborazzi](https://github.com/takahirom/roborazzi)
- [STD-ACRA] [ACRA](https://github.com/ACRA/acra)
- [STD-SYNC] [Syncthing device IDs](https://docs.syncthing.net/v0.11.7/dev/device-ids); [KryptEY E2EE keyboard](https://github.com/amnesica/KryptEY)
- [STD-TASKER] [Tasker plugin intro](https://tasker.joaoapps.com/plugins-intro.html); [HeliBoard Tasker PR #290](https://github.com/HeliBorg/HeliBoard/pull/290); [AutoInput + Tasker](https://blog.php-systems.com/setting-up-autoinput-with-tasker/)
- [STD-FLORIS-EXT] [FlorisBoard extensions docs](https://docs.florisboard.org/extensions)
- [STD-NO-INTERNET] (FlorisBoard's no-INTERNET-permission posture — manifest audit)
- [MIG-GBOARD] [Gboard PersonalDictionary export — How-To Geek](https://www.howtogeek.com/how-to-speed-up-your-typing-game-with-gboards-personal-dictionary/)
- [MIG-SK] [SwiftKey Backup & Sync — MS Support](https://support.microsoft.com/en-us/topic/how-to-use-backup-sync-in-microsoft-swiftkey-keyboard-3604cb8a-47e9-4045-82de-fd301904e59a)
- [MIG-KLFC] [KLFC layout converter](https://github.com/39aldo39/klfc)
- [Hub Keyboard Microsoft Garage profile](https://www.microsoft.com/en-us/garage/profiles/hub-keyboard/)
- [PAIN-D-3] [BLTT keyboards-for-disabled-people](https://bltt.org/keyboards-for-disabled-people/); [OT-with-Apps motor accessibility](https://otswithapps.com/2015/11/08/keyboard-accessibility-for-individuals-with-motor-impairment-for-computers-and-mobile-devices/); [Android accessibility help — physical keyboard a11y](https://support.google.com/accessibility/android/answer/16323943)
- [PAIN-29] [Android accessibility — bounce/slow/sticky](https://support.google.com/accessibility/android/answer/16318538)

### New sources (v5.0, 2026-05-15)

- [NLNET-GT] [NLnet — Gesture Typing for AOSP-derived Keyboards](https://nlnet.nl/project/GestureTyping/) — NGI Mobifree-funded R&D programme, project formally runs Jun 2025 → Jun 1 2026; library will be a drop-in replacement for the closed `swypelibs` blob.
- [H1-2026] [HeliBoard v3.7-beta1 (2026-02-22)](https://github.com/HeliBorg/HeliBoard/releases) — ships optional gesture data gathering, RTL improvements, non-inline emoji search.
- [H-EMOJI-1231] [HeliBoard #1231 — extended emoji support](https://github.com/Helium314/HeliBoard/issues/1231) — emoji search/predict by tag, pinned emoji, custom emoji tags, GIF support.
- [FUTO-CJK-2026] [FUTO Keyboard releases](https://github.com/futo-org/android-keyboard/releases) — Traditional + Simplified Chinese (standard / fuzzy / Double Pinyin + rudimentary stroke), Vietnamese Telex + VNI, image clipboard.
- [FUTO-VOICE-MODELS] [FUTO Voice Input Models](https://keyboard.futo.org/voice-input-models) — OpenAI Whisper tiny/base/small finetuned with ACFT, GGML format.
- [FLOR-DISC-2197] [FlorisBoard Discussion #2197 — NLP implementation status](https://github.com/florisboard/florisboard/discussions/2197) — NLP core hosted in a separate repo, bridge work in progress; word-prediction/spell-checking shipping in 0.6.
- [FLOR-DISC-84] [FlorisBoard Issue #84 — Change language on the fly](https://github.com/florisboard/florisboard/issues/84) — long-standing high-traffic request; SwiftFloris's per-token multilingual scoring is the answer.
- [FLOR-LAYOUT-EDITOR] [FlorisBoard ROADMAP](https://github.com/florisboard/florisboard/blob/main/ROADMAP.md) — on-board layout editor planned for 0.7; could surface as a SwiftFloris Under Consideration item.
- [CK-DEEPWIKI] [CleverKeys DeepWiki](https://deepwiki.com/tribixbite/CleverKeys) — transformer encoder-decoder (5.4MB + 7.4MB), beam search 8 hypotheses, XNNPACK, sub-200ms on Pixel 7; v1 EN+QWERTY only; multi-layout / multi-script model on the roadmap for Q2-Q3 2026. GPL-3.0 — architecture-only reference.
- [CK-FDROID] [CleverKeys on F-Droid](https://f-droid.org/packages/tribixbite.cleverkeys/) — confirms public availability + reproducible build status.
- [STD-A16-PWLE] [Android 16 release notes — haptic envelopes](https://source.android.com/docs/whatsnew/android-16-release); [Android 16 Features](https://developer.android.com/about/versions/16/features) — `VibrationEffect.WaveformEnvelopeBuilder` + `Vibrator.areEnvelopeEffectsSupported()`; primitive set CLICK / TICK / LOW_TICK / SLOW_RISE / QUICK_RISE / QUICK_FALL / THUD / SPIN.
- [STD-A15-16KB] [Android 16 KB memory pages](https://developer.android.com/guide/practices/page-sizes); [Google Play Aug 2025 requirement](https://medium.com/@dfs.techblog/androids-16-kb-page-size-what-it-means-for-your-app-and-why-you-should-act-now-b0c65cee86d4) — NDK r28+ defaults to 16K alignment; AGP 8.5.1+ for AAB packaging; `android:pageSizeCompat` for Android 16 compatibility mode.
- [STD-AOSP-2026] [AOSP release cadence change](https://source.android.com/docs/whatsnew/android-16-release) — AOSP source now published Q2 + Q4 only; track `android-latest-release` branch.
- [STD-LITERT-LM] [LiteRT-LM Overview](https://ai.google.dev/edge/litert-lm/overview); [LiteRT-LM repo](https://github.com/google-ai-edge/LiteRT-LM); [Deploy Gemma on mobile](https://ai.google.dev/gemma/docs/integrations/mobile); [LiteRT: The Universal Framework for On-Device AI](https://developers.googleblog.com/litert-the-universal-framework-for-on-device-ai/); [MediaPipe LLM Inference (now deprecated on Android/iOS)](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference) — supports Gemma / Llama / Phi-4 / Qwen, NPU 3× over GPU prefill on S25 Ultra, Gemma 4 MTP >2× faster decode, LoRA on attention layers (Gemma-2 2B / Gemma 2B / Phi-2).
- [STD-FUNCTIONGEMMA] [FunctionGemma announcement](https://blog.google/innovation-and-ai/technology/developers-tools/functiongemma/); [InfoQ coverage](https://www.infoq.com/news/2026/01/functiongemma-edge-function-call/) — Gemma 3 270M variant fine-tuned for natural-language → structured function/API calls; on-device agentic AI surface.
- [STD-GEMMA3-270M] [Gemma 3 270M introduction](https://developers.googleblog.com/en/introducing-gemma-3-270m/); [DataCamp Gemma 3 270M Android tutorial](https://www.datacamp.com/tutorial/gemma-3-270m) — 270M params (170M embedding + 100M transformer), 256k vocab, INT4-QAT uses 0.75% Pixel 9 Pro battery per 25 conversations.
- [STD-BERGAMOT] [Bergamot Translator (active fork)](https://github.com/browsermt/bergamot-translator); [Bergamot Translator (mozilla, INACTIVE)](https://github.com/mozilla/bergamot-translator); [Bergamot Firefox docs](https://firefox-source-docs.mozilla.org/toolkit/components/translations/resources/03_bergamot.html); [DavidVentura/offline-translator Android port](https://github.com/DavidVentura/offline-translator); [Mobile translator blog](https://blog.davidv.dev/posts/mobile-translator/) — Apache-2.0 offline NMT; Mozilla's repo INACTIVE; `browsermt/bergamot-translator` is the maintained surface; Android port references the build gotchas (pcre2 + NDK).
- [STD-WHISPER-CTRANSLATE2] [whisper.cpp](https://github.com/ggml-org/whisper.cpp); [Vosk vs Whisper 2026 hybrid guide](https://www.sinologic.net/en/2026-05/vosk-vs-whisper-local-the-ultimate-2026-guide-to-self-hosted-speech-recognition-stt.html); [Vosk Android](https://alphacephei.com/vosk/android) — Vosk for live streaming, Whisper for final-polish; CTranslate2 4-5× faster than original Whisper.
- [STD-FDROID-VERIFIED] [F-Droid: Making reproducible builds visible (May 2025)](https://f-droid.org/2025/05/21/making-reproducible-builds-visible.html); [F-Droid Reproducible Builds docs](https://f-droid.org/docs/Reproducible_Builds/) — Reproducibility Status page live per app; tier ladder `Reproducible` / `Verified` / `Soon to be Verified` / `Not verified`.
- [STD-ANDROID-16-RELEASE] [Android 16 release notes](https://source.android.com/docs/whatsnew/android-16-release); [Android 16 stylus features](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/stylus-input-in-text-fields).
- [STD-INPUTMETHOD-SERVICE] [InputMethodService reference](https://developer.android.com/reference/android/inputmethodservice/InputMethodService).
- [STD-CVE-2026] [Android Security Bulletin May 2026 (CVE-2026-0073)](https://source.android.com/docs/security/bulletin/2026/2026-05-01); [Android Security Bulletin March 2026 (CVE-2026-21385 active exploitation)](https://source.android.com/docs/security/bulletin/2026/2026-03-01); [Android Security Bulletin April 2026](https://source.android.com/docs/security/bulletin/2026/2026-04-01) — operational hardening reference; no IME-specific CVEs in the window but Framework / Media Codecs / System component RCEs surface regularly.
- [GBOARD-AI-2025] [Google blog: AI writing tools in Gboard (Sept 2025)](https://blog.google/products-and-platforms/platforms/android/new-android-features-september-2025/) — on-device Gemini Nano powers tone rewrite / proofread / grammar in Gboard; 8 languages with full AI features (EN / zh-Hans / fr / it / ja / ko / pt / es), +10 more planned 2026.
- [GBOARD-SMARTCOMPOSE] [XDA-Developers: Smart Compose rollout to messaging](https://www.xda-developers.com/google-smart-compose-gboard-android-messages-telegram-whatsapp/) — gray ghost-text completion, swipe-right-on-spacebar to accept, ~40% phrases / 60% single words.
- [GBOARD-RAMBLER] [Android 17 Gemini Intelligence](https://www.digitbin.com/android-17-gemini-intelligence-explained/); [Android Show I/O 2026](https://www.android.com/new-features-on-android/io-2026/) — Gboard's Rambler mode (hold mic + ramble + clean polished text including mid-language switches).
- [SK-RETIRE] [SwiftKey accounts retiring May 31 2026 — Microsoft Support](https://support.microsoft.com/en-us/topic/account-a3c38581-903f-4d22-a388-cc13c7debf0e); [Windows Central coverage](https://www.windowscentral.com/software-apps/swiftkey-will-soon-require-a-microsoft-account-data-to-be-moved-to-onedrive); [Android Authority](https://www.androidauthority.com/microsoft-swiftkey-accounts-change-3650203/) — Google / Apple / legacy SwiftKey logins permanently removed; only Microsoft account login supported; all non-Microsoft-account data permanently deleted post-cutoff.
- [ASK-2026] [AnySoftKeyboard 1.13.547 beta (Feb 2026) APKMirror release](https://www.apkmirror.com/apk/anysoftkeyboard/anysoftkeyboard-github-version/anysoftkeyboard-github-version-1-13-547-release/) — Android 15 16KB memory pages support, gesture-typing accuracy, Android 15+ emoji updates, edge-to-edge polish; competitor parity check.
- [PG-DISC-2026] [Privacy Guides — Keyboard recommendations 2026](https://discuss.privacyguides.net/t/what-keyboard-are-you-using-on-android/15973); [Factually 2026 keyboard comparison](https://factually.co/product-reviews/electronics-tech/best-android-keyboard-for-privacy-2026-384072) — community sentiment 2026: HeliBoard + FUTO + CleverType the named SwiftKey defectors' destinations; predictive-text quality gap remains the #1 perception barrier on FOSS keyboards.
- [STD-STYLUS-COMPOSE] [Compose stylus input in TextFields](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/stylus-input-in-text-fields); [Advanced stylus features](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/advanced-stylus-features) — `setAutoHandwritingEnabled` / `setHandwritingBoundsOffsets` / handwriting delegation; motion-prediction library to reduce perceived latency; palm-rejection contract.
- [HF-EDUGP-KENLM] [edugp/kenlm 24-language pre-trained models](https://huggingface.co/edugp/kenlm) — Next-3.1 reference.
- [STD-UNICODE17-EMOJI] [Emoji 17 Emojipedia announcement](https://blog.emojipedia.org/whats-new-in-unicode-17-0/) — Distorted Face, Fight Cloud, Hairy Creature, Orca, Landslide, Trombone, Treasure Chest (7 new glyphs).
- [V1.7.9-DOCS] `docs/INLINE_AUTOFILL.md` (Next-9.3), `docs/MIGRATE_FROM_SWIFTKEY.md` (Next-6.3), `RELEASE_NOTES_v1.7.9.md` — internal sources for v1.7.9 shipped surfaces.

---

*End of ROADMAP v5.0. Total source URLs cited: 160+. Total feature/initiative items: 75+ across Now/Next/Later/Under Consideration/Rejected. This document supersedes ROADMAP v4.0 (2026-05-14). v4.0 entries are preserved with shipped markers updated in-place; new sources appended above under "v5.0, 2026-05-15". Next planned reconcile: at the next major release (v1.8.0 — Next-3.1 KenLM 5-gram, Next-7.1 floating-window UX polish, or N3.3a Android 16 PWLE haptics, whichever drops first).*
