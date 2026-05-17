# Prioritization Matrix — 2026-05-17

Scores every concrete item from [FEATURE_BACKLOG.md](FEATURE_BACKLOG.md)
on user impact (1–5), engineering cost (1–5), and migration-window urgency
(1–5 — items that close before 2026-05-31 score higher).

`Score = (Impact × 2 + Urgency) / Cost`. Higher is better. Items
with `Score ≥ 5.0` are recommended for the next slice.

Items that are **already in ROADMAP with updated evidence** are recorded
here so the next reviewer can see why they're being re-emphasized; the
**recommended new commitments** (Tier-1) are also written into
[ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md).

---

## Scoring rubric

| Dimension | 1 | 3 | 5 |
|---|---|---|---|
| **Impact** | small polish | meaningful improvement | reaches new user segment or closes a major gap |
| **Cost** | 1 file change, < 1 day | 1-2 weeks, 1 PR slice | 1+ month, multiple PRs or external dependencies |
| **Urgency** | "someday" | this release cycle | locked to a date in the next 6 weeks |

---

## Tier 1 — Recommended for immediate work (score ≥ 5.0)

| # | Item | Impact | Cost | Urg. | Score | Rationale / source |
|---|---|---|---|---|---|---|
| 1 | **2026-05-30 pinned GitHub release** (SwiftKey-cutoff day-of) with `docs/MIGRATE_FROM_SWIFTKEY.md` front-and-center | 5 | 1 | 5 | **15.0** | One-shot opportunity; ROADMAP §14 Risk Register named it. Concrete day-of checklist closes the gap |
| 2 | ✅ **Shipped v1.8.70:** README "Galaxy AI Writing Tools work with SwiftFloris" callout | 3 | 1 | 4 | **10.0** | One UI 7+ Writing Assist framed as optional Samsung selected-text layer above SwiftFloris |
| 3 | ✅ **Shipped v1.8.68:** replace `androidx-security-crypto:1.1.0-alpha06` with Tink + AndroidKeystore | 4 | 2 | 3 | **5.5** | API surface was deprecated; `PersonalDictionaryEncryptionTest` now pins the Tink wrapper + one-shot legacy migration |
| 4 | ✅ **Shipped v1.8.69:** Bump-batch A (kotlinx-coroutines 1.11.0, KSP 2.3.8, zxing 3.5.4, aboutlibraries 14.2.0; keep activity 1.13.0) | 3 | 1 | 3 | **9.0** | Low-risk patch/minor updates; Activity downgrade retired by fifth-pass evidence |
| 5 | ✅ **Shipped v1.8.70:** README release-hygiene maintenance (badge + Highlights table + Recent releases stay in lockstep) | 3 | 1 | 4 | **10.0** | README front door now current through v1.8.70 |
| 6 | ✅ **Local tag catch-up:** release tags `v1.8.41` … `v1.8.69` backfilled from matching version-bump commits | 3 | 1 | 3 | **9.0** | Required for Obtainium auto-update reliability; remaining step is pushing tags from the maintainer host |
| 7 | **First-run "AI features in this keyboard" explainer surface** (EU AI Act Article 50, due 2 Aug 2026) | 4 | 2 | 4 | **6.0** | Regulatory cutoff in ~10 weeks; small UI slice; reinforces wedge |
| 8 | **Settings → About re-openable "AI features" explainer** (same content, persistent) | 3 | 1 | 4 | **10.0** | Companion to #7; small |
| 9 | **HeliBoard NLnet slip-base-case plan** (move N1.1 from "wait-and-integrate" to "ship N1.3-quality classifier; integrate N1.1 when it lands") | 4 | 2 | 4 | **6.0** | Library probably slips; N1.3 statistical is already shipped — frame as the default, not the placeholder |
| 10 | ✅ **Shipped v1.8.70:** README "Grammarly Android keyboard discontinued" migration callout | 3 | 1 | 3 | **9.0** | Grammarly Android transition framed as compatible overlay; SwiftFloris remains no-network |
| 11 | **EU AI Act explainer copy in `docs/PRIVACY_AND_AI.md`** | 3 | 1 | 4 | **10.0** | Companion to #7/#8; one new doc |
| 12 | ✅ **Shipped v1.8.71:** Bump-batch B (Roborazzi 1.55→1.60, Robolectric 4.14.1→4.16.1) | 3 | 1 | 2 | **8.0** | Required before AGP 9.2 bump (Tier-2); enables baseline capture |
| 13 | **`PROJECT_CONTEXT.md` + `AGENTS.md` + `CLAUDE.md` pointer files** | 4 | 1 | 2 | **10.0** | Shipped by the research run; keep as the required onboarding pattern |
| 14 | **Move stale crash logs / replay logs out of repo root** (3.0+ MB across 3 files) | 2 | 1 | 2 | **6.0** | Repo hygiene; reduces fresh-clone bloat |
| 15 | **CI step: detect committed `hs_err_pid*.log` / `replay_pid*.log`** | 2 | 1 | 2 | **6.0** | Prevents recurrence |

## Tier 2 — Recommended near-term (score 3.5 – 4.9)

| # | Item | Impact | Cost | Urg. | Score | Rationale |
|---|---|---|---|---|---|---|
| 16 | **AGP 9.0.0 → 9.2.x + Compose BOM 2026.03.01 → 2026.05.00** | 3 | 2 | 2 | **4.0** | After bump-batch B lands; needs R8 rules audit |
| 17 | **Dedicated arrow-keys row preset** (SwiftKey-parity P24) | 3 | 2 | 2 | **4.0** | Small UX gain; `BottomRowPreset.Programmer` already provides scaffolding |
| 18 | **Calendar quick-insert (P9)** — `QuickAction.InsertCalendarEvent` + `CalendarContract.Instances` | 4 | 3 | 1 | **3.0** | SwiftKey-parity; permission-gated |
| 19 | **Tasks quick-insert (P10)** — `QuickAction.InsertTask` + `Intent.ACTION_INSERT` | 3 | 2 | 1 | **3.5** | SwiftKey-parity; no permission |
| 20 | **Animated theme (P14)** — first Snygg animated-bg primitive ("Aurora Animated") | 3 | 2 | 1 | **3.5** | Polish; opens the door for community-contributed animated themes |
| 21 | **AAA high-contrast theme (P15)** — new Snygg sheet | 3 | 1 | 2 | **8.0** | Low cost; accessibility gain |
| 22 | **Phase B4 (Next-3 hardening)** — extend `TypingContextExtractor.previousWordListBeforeCurrentWord` 2→4 word context | 4 | 2 | 2 | **5.0** | Next slice in active Phase B sprint |
| 23 | **Phase C1 (P3 renderer)** — split-keyboard gutter wire-up into `TextKeyboardLayout.layout(...)` | 4 | 3 | 1 | **3.0** | Active sprint follow-on; tablet-only audience |
| 24 | **Lint baseline refresh** | 2 | 1 | 1 | **5.0** | IMPROVEMENT_PLAN counter from 2026-05-05 is stale |
| 25 | **Reproducible-build verification CI job** (build twice, diff APKs) | 3 | 2 | 1 | **3.5** | F-Droid verified-tier prep |
| 26 | **Add SUPERSEDED banners to 4 superseded `SWIFTKEY_*` docs** | 2 | 1 | 1 | **5.0** | Per MEMORY_CONSOLIDATION §5 |
| 27 | **macOS `.keylayout` (XML) parser** (Next-6.4a follow-up) | 3 | 2 | 1 | **3.5** | Closes the cross-format hardware-keyboard pipeline |
| 28 | **Hardware-keyboard runtime mapper (Next-6.4b)** — `InputManager` + `KeyEvent.getDeviceId(...)` | 4 | 3 | 1 | **3.0** | Adjacent to #27; makes the parsers actually useful at runtime |

## Tier 3 — Recommended later (score 2.0 – 3.4)

| # | Item | Impact | Cost | Urg. | Score | Rationale |
|---|---|---|---|---|---|---|
| 29 | **User-imported sticker folder** | 4 | 2 | 1 | **4.5** | High value × low cost; no competitor offers it — but no immediate urgency |
| 30 | **Roborazzi baseline capture for all 13 bundled themes** | 3 | 2 | 1 | **3.5** | After bump-batch B |
| 31 | **`ARCHITECTURE.md`** consolidating per-package docs | 3 | 2 | 1 | **3.5** | ROADMAP §11 outstanding |
| 32 | **`CONTRIBUTING.md`** | 2 | 1 | 1 | **5.0** | Small; convention |
| 33 | **Consolidate root-level `*MULTILINGUAL.md` / `VOICE_*.md` / `FUTO_*.md` into `docs/`** | 2 | 2 | 1 | **2.5** | Repo hygiene; ROADMAP §11 flagged |
| 34 | **`.kmp` Keyman package runtime** | 4 | 4 | 1 | **2.25** | High value (1,000+ minority-language layouts) but large engineering cost |
| 35 | **Honeycomb-hex layout wire-up** | 3 | 4 | 1 | **1.75** | Components in tree but geometry rework is expensive |
| 36 | **OpenSSL/BoringSSL SQLCipher provider migration plan** | 3 | 3 | 1 | **2.33** | Not urgent; Zetetic has not announced LibTomCrypt removal release |
| 37 | **Self-hosted ACRA opt-in endpoint** | 2 | 3 | 1 | **1.67** | §9 Under Consideration; demand-gated |
| 38 | **HeliBoard dictionary downloader UI pattern** | 4 | 3 | 1 | **3.0** | Strategic; depends on Next-10.3 addon catalog landing first |

## Tier 4 — Track but don't commit (score < 2.0)

| # | Item | Reason for low score |
|---|---|---|
| 39 | Apertium offline NMT | Bergamot path is already chosen; Apertium duplicates without unique value |
| 40 | navigation3 evaluation | Navigation lib is small surface; payoff < churn cost right now |
| 41 | SentencePiece as KenLM alternative | KenLM-in-addon path is fine; evaluate only if addon proves heavy |
| 42 | Federated learning across user's own devices | Needs L1 to land; downstream of multi-month work |
| 43 | Hinglish / Spanglish first-class support | Bilingual subtype + per-token langid covers 80% today; long tail |
| 44 | macOS-only competitor adjacencies (Apple Genmoji) | Cannot run on Android; informational only |
| 45 | 3×3 thumb-grid layout (Thumb-Key parity) | Different audience; not the migration target |
| 46 | TapStrap chord input | Wearable hardware; niche |

---

## Recommended next-three-releases mapping

Based on Tier-1 items + current Phase A/B SwiftKey-parity sprint:

### v1.8.56 (this week — wraps Phase B / opens Phase C)

- **Phase B4** (#22): same-sentence language switch hardening — extend trailing context 2→4 words; alpha-blend on per-locale evidence.
- **`PROJECT_CONTEXT.md` + `AGENTS.md` + `CLAUDE.md` pointer files** (#13).
- **Bump-batch A** (#4) low-risk dep bumps; shipped v1.8.69. Keep Activity 1.13.0.
- **README release-hygiene maintenance** (#5; shipped through v1.8.70, then keep current).

### v1.8.57 (next week — migration-window prep)

- **EU AI Act Article 50 first-run + Settings → About explainer** (#7, #8, #11).
- **Tag catch-up** (#6; 29 tags) — local tags `v1.8.41` through `v1.8.69`
  were backfilled; push tags from the maintainer host.
- **README "Galaxy AI" + "Grammarly discontinued" callouts** (#2, #10) —
  shipped v1.8.70.

### v1.8.58–v1.8.60 (the migration window itself — 2026-05-28 to 2026-05-31)

- **2026-05-30 pinned release** (#1) with `docs/MIGRATE_FROM_SWIFTKEY.md` link + Obtainium URL above the fold.
- **Tink migration of androidx-security-crypto** (#3) — shipped v1.8.68.
- **HeliBoard NLnet slip-base-case plan** (#9) — update ROADMAP §6 N1.1 framing.

### v1.8.61+ (post-migration window — Phase C / D)

- **Bump-batch B** (#12) shipped v1.8.71; AGP 9.2.x remains #16.
- **Phase C1 (split renderer)** (#23) — opens Phase C.
- **Calendar / Tasks quick-insert** (#18, #19).
- **AAA high-contrast theme + Animated theme** (#21, #20) — wraps Phase C.

This sequence respects the SwiftKey 2026-05-31 cutoff as the highest
external-clock anchor.
