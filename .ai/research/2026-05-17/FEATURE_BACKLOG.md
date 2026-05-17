# Feature Backlog — 2026-05-17

Raw harvested ideas from this research run, **before** prioritization.
Items that are already in `ROADMAP.md` are tagged with the existing
roadmap key in brackets and **excluded** from the final backlog unless
the research surfaced new evidence on scope, urgency, or feasibility.

The prioritized list lives in
[PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md); the items that
graduate into recommended next-slice work live in
[ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md).

---

## 1. Already in ROADMAP, updated evidence only

| Existing key | New evidence from this run |
|---|---|
| §6 N1.1 — HeliBoard NLnet open-glide | **Base-case slip past 2026-06-01 deadline.** v3.9 (2026-03-29) shipped data-gathering fixes; passive mode "still tuning"; no library / dataset published. Plan should treat slip as base case |
| §7 Next-3.1 KenLM JNI | **KenLM is LGPL-2.1+** — incompatible with `:app` ceiling. Must isolate to addon. Header parser in-`:app` is fine. **Material ROADMAP correction** |
| §8 L1 LiteRT-LM + Gemma 3 270M | **FunctionGemma 270M shipped Jan 2026** as the function-calling variant. Should be the named model target for any agentic tool-use Smart Compose |
| §8 L1 MediaPipe LLM Inference | **Officially deprecated by Google** on Android. ROADMAP should explicitly REJECT MediaPipe to prevent re-proposal |
| §4 "Upstream drift" thesis | **FlorisBoard frozen on v0.6.0-alpha02 since 2025-01-23**. SwiftFloris is **lapping a stalled upstream**, not drifting from a moving target. Update framing |
| §6 N4.2 / §7 P24 — Dedicated arrow-keys row | ✅ shipped v1.8.57 as `BottomRowPreset.Navigation` |
| §7 P9 — Calendar quick-insert | ✅ shipped v1.8.64 with `QuickAction.InsertCalendarEvent`, `CalendarContract.Instances`, explicit `READ_CALENDAR`, and IME-local picker |
| §7 P10 — Tasks quick-insert | ✅ shipped v1.8.58 with `QuickAction.InsertTask` chooser flow and `SensitiveFieldGuard` |
| §6 / §7 — Animated themes (P14) | ✅ shipped v1.8.63 as `aurora_animated` with GenericShape renderer and reduced-motion gate |
| §6 / §7 — AAA high-contrast theme (P15) | ✅ shipped v1.8.63 as `swiftkey_high_contrast` with WCAG AAA contrast tests |
| §6 N3.x — typing-stats accuracy-delta (P26) | New; compute "x % fewer corrections accepted this week" from `CorrectionOutcomePriors` |

## 2. New ideas not yet in ROADMAP

### 2.1 Distribution / migration window

| Idea | Source |
|---|---|
| **README "Galaxy AI Writing Tools work with SwiftFloris on Samsung One UI 7+"** talking-point | One UI 7 decouple verified 2026-05-12. Marketing slice; XS effort |
| **README "Grammarly Android keyboard discontinued"** migration callout | Grammarly support page confirms. Smaller pool than SwiftKey, same privacy fatigue |
| **2026-05-30 pinned GitHub release** with `docs/MIGRATE_FROM_SWIFTKEY.md` front and center | Last day before SwiftKey cutoff. ROADMAP §14 Risk Register notes the one-shot opportunity; concrete day-of-launch checklist would close it |
| **Reddit r/SwiftKey / r/PrivacyGuides / r/HeliBoard / r/fossandroid thread** | Same as above; ROADMAP §A1 has it as the marketing slice; ROADMAP carries no checklist for thread copy |

### 2.2 Privacy / regulatory

| Idea | Source |
|---|---|
| **EU AI Act Article 50 transparency first-run surface** (2 Aug 2026 cutoff) | Verified 2026-05-17 — applies to next-word, glide, voice, translate, smart-compose. Lives next to `app/setup/` |
| **In-app "AI features in this keyboard" explainer screen** under Settings → About | Companion to first-run surface; reopenable |
| **Privacy posture diff vs SwiftKey / Gboard / Grammarly side-by-side table in docs** | Reinforces wedge for migration-window comms |

### 2.3 Dependency / security

| Idea | Source |
|---|---|
| ✅ **Shipped v1.8.68:** replace `androidx-security-crypto:1.1.0-alpha06` with Tink + AndroidKeystore | AndroidX Security Crypto 1.1.0 exists, but APIs are deprecated. `PersonalDictionaryEncryptionTest` now pins the shared Tink wrapper and one-shot legacy migration contract |
| **Reproducible-build verification CI job** (build twice, compare APK checksums) | F-Droid verified-tier launch 2025-05; SwiftFloris can self-verify before fdroiddata submission |
| **R8 rules audit** before AGP 9.2.x bump | AGP 9.2 / R8 release notes |
| **OpenSSL/BoringSSL SQLCipher provider migration plan** | LibTomCrypt deprecation announced by Zetetic |
| **Lint baseline refresh** (IMPROVEMENT_PLAN counter from 2026-05-05 is stale) | Self-evident |

### 2.4 Feature opportunities (competitor delta)

| Idea | Source / competitor that ships it |
|---|---|
| **User-imported sticker folder** (`~/Pictures/Stickers/*.webp` → inline via `commitContent`) | No keyboard surveyed offers this. High value × low cost |
| **Local sticker / emoji-pack import via SAF** | Builds on Next-9.4 emoji pin groups |
| **`.kmp` Keyman package runtime** (extend Next-6.4 / L8 importer) | Keyman engine MIT; 1,000+ minority-language layouts |
| **CleverKeys-architecture re-train, Apache-2.0** (once a permissive glide dataset lands) | CleverKeys is in F-Droid working; architecture is public on `tribixbite/CleverKeys-ML` |
| **HeliBoard dictionary downloader UI** for user-supplied n-gram packs | HeliBoard's killer ecosystem feature; closest peer pattern |
| **LeanType-style Offline / Offline Lite onboarding split** without separate base-APK flavors | LeanType proves demand for offline AI keyboard SKUs; SwiftFloris should express the same choice through signed addon onboarding, not `INTERNET` in `:app` |
| **Hardware-keyboard floating candidate panel** for transliteration engine | fcitx5-android pattern |
| **Hardware-keyboard runtime mapper** (Next-6.4b: `InputManager` / `KeyEvent.getDeviceId(...)` mapping) | Already in ROADMAP but not in current Now/Next; promote |
| **3×3 thumb-grid alt layout** (Thumb-Key parity) | Optional, niche but useful for foldable / one-handed |
| **Honeycomb-hex layout wire-up** | `HoneycombHex*` components in tree; geometry wire-up is the open slice |
| **macOS `.keylayout` (XML) parser** (Next-6.4a) | Already in ROADMAP, captured here for tracking |
| **First-run "import from device backup" hint** if a SwiftKey backup file is present in `~/Downloads/` | Reduces friction during the migration window |
| **"Snygg theme tester" in-app preview screen** | Theme contributors currently have to install the theme to see it |
| **Per-language glide-classifier debug overlay** | Surfaces the N1.4 benchmark numbers on-device |

### 2.5 Testing / quality

| Idea | Source |
|---|---|
| **Roborazzi baseline capture for each of the 13 bundled themes** | ROADMAP §6 N12.2 mentions; specific theme list is concrete |
| **Property-test coverage for `NlpManager.getAutoCommitCandidate`** | Behavior surface; IMPROVEMENT_PLAN W1 |
| **Macrobenchmark numbers from a reference device, recorded in `docs/BENCHMARKS.md`** | ROADMAP §6 N12.1 outstanding |
| **Self-hosted ACRA endpoint** (opt-in only; documented in §11 already as Under Consideration) | Promote from Under Consideration once user demand surfaces |
| **CI: detect and fail on stray `hs_err_pid*.log` / `replay_pid*.log` in repo root** | Three of these are currently committed (8+ MB), should be removed and CI-gated |

### 2.6 Docs / contributor experience

| Idea | Source |
|---|---|
| **`PROJECT_CONTEXT.md` at root** | None today; this research run is producing it |
| **`AGENTS.md` and/or `CLAUDE.md` pointer files** to `PROJECT_CONTEXT.md` + ROADMAP | None today; future AI sessions read these by convention |
| **`ARCHITECTURE.md`** consolidating the per-package documentation | ROADMAP §11 flags it; outstanding |
| **`CONTRIBUTING.md`** — README §Contributing covers the basics but a separate file is the convention | Outstanding |
| **Consolidate root-level `*MULTILINGUAL.md`, `VOICE_*.md`, `FUTO_VOICE_*.md` into `docs/`** | ROADMAP §11 flagged; outstanding |
| **Move `app-release-v1.5.2.apk` (9.7 MB) and `hs_err_pid*.log` / `replay_pid*.log` (8+ MB) out of repo root** | Currently in root; bloat for fresh clones |
| **Add SUPERSEDED banners to the four superseded `SWIFTKEY_*` docs** | Per MEMORY_CONSOLIDATION §5 pointer plan |
| **Tag every shipped release** (catch-up: v1.8.41 … v1.8.58 → 18 tags missing) | `git tag --sort=-creatordate` shows v1.8.40 as the most recent tag |

### 2.7 Long-tail (track but no commit needed yet)

| Idea | Source |
|---|---|
| **Apertium offline NMT** as Bergamot alternative | `apertium/apertium-android` 1.03 (2026-04-25) — interesting but Bergamot has more language pairs |
| **navigation3 1.1.0 evaluation** | Apr 2026 stable; lower runtime cost than nav2 for Settings UI |
| **SentencePiece (Apache-2.0)** as KenLM alternative for n-gram-like scoring in `:app` | License-clean path if KenLM-in-addon proves heavy |
| **PWLE haptic envelope expansion** (Next-11.2 paired-rumble pending) | Already in ROADMAP, called out for completeness |
| **Federated learning across user's own devices** | ROADMAP §9 Under Consideration; needs L1 to land first |
| **First-class Hinglish / Spanglish support** beyond the bilingual subtype mechanism | Long tail; would extend N2 |

---

## 3. Items rejected by this research run

These were considered and rejected during synthesis, with reasoning:

| Idea | Rejected because |
|---|---|
| **Cloud-backed sticker / emoji packs** | Violates §1 no-network |
| **MediaPipe LLM Inference path** | Google deprecated it on Android (LiteRT-LM is the successor — ROADMAP already targets the right one, but should explicitly REJECT MediaPipe so it's not re-proposed) |
| **KenLM linked directly into `:app`** | LGPL incompatibility with `:app` ceiling (header parser in-`:app` is fine — that's parsing a public format, not linking the runtime) |
| **NLLB-200 distilled-600M for offline NMT** | CC-BY-NC-4.0 — non-commercial restriction conflicts with §1 audit-friendly distribution |
| **CleverKeys-as-shipped (GPL-3.0) drop-in** | GPL forbidden in `:app`. Architecture is the reference |
| **Federated learning to vendor cloud** | Already rejected in §10; restated for completeness |

---

## 4. Inventory summary

- **In ROADMAP, updated evidence:** 11
- **New ideas:** 33 across §2.1-§2.6
- **Long-tail tracked, not committed:** 6
- **Rejected with reasoning:** 6

Promotion plan: see
[PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md).
