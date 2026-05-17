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
| 7 | ✅ **Shipped v1.8.66:** First-run "AI features in this keyboard" explainer surface | 4 | 2 | 4 | **6.0** | `SetupScreen` starts with Review local AI features; `AiFeatureDisclosureCatalogTest` pins the surface |
| 8 | ✅ **Shipped v1.8.66:** Settings → About re-openable "AI features" explainer | 3 | 1 | 4 | **10.0** | Settings → About → AI features in this keyboard reopens the same local-only disclosure |
| 9 | ✅ **Shipped v1.8.72:** HeliBoard NLnet slip-base-case plan (N1.1 is additive; N1.3 statistical is production default) | 4 | 2 | 4 | **6.0** | Re-checked HeliBoard `#2226`, releases, NLnet page, and gesture-data wiki; no open library/dataset yet, data collection still active |
| 10 | ✅ **Shipped v1.8.70:** README "Grammarly Android keyboard discontinued" migration callout | 3 | 1 | 3 | **9.0** | Grammarly Android transition framed as compatible overlay; SwiftFloris remains no-network |
| 11 | ✅ **Shipped v1.8.66:** EU AI Act explainer copy in `docs/PRIVACY_AND_AI.md` | 3 | 1 | 4 | **10.0** | Canonical long-form disclosure exists and is linked from setup/About |
| 12 | ✅ **Shipped v1.8.71:** Bump-batch B (Roborazzi 1.55→1.60, Robolectric 4.14.1→4.16.1) | 3 | 1 | 2 | **8.0** | Required before AGP 9.2 bump (Tier-2); enables baseline capture |
| 13 | ✅ **Shipped by research run:** `PROJECT_CONTEXT.md` + `AGENTS.md` + `CLAUDE.md` pointer files | 4 | 1 | 2 | **10.0** | Root context + generic agent instructions + Claude-specific pointer file are present |
| 14 | ✅ **Shipped v1.8.73:** Move stale crash logs / replay logs out of repo root | 2 | 1 | 2 | **6.0** | Five ignored local logs moved to `.ai/local-crash-logs/2026-05-16/` |
| 15 | ✅ **Shipped v1.8.73:** CI step: detect committed `hs_err_pid*.log` / `replay_pid*.log` | 2 | 1 | 2 | **6.0** | `scripts/check-no-root-crash-logs.sh` runs in `android.yml` before Gradle setup |

## Tier 2 — Recommended near-term (score 3.5 – 4.9)

| # | Item | Impact | Cost | Urg. | Score | Rationale |
|---|---|---|---|---|---|---|
| 16 | ✅ **Shipped v1.8.74:** AGP 9.0.0 → 9.2.1 + Compose BOM 2026.03.01 → 2026.05.00 | 3 | 2 | 2 | **4.0** | Google Maven / Android Studio patch notes checked; R8 keepattributes audit required no rule changes |
| 17 | ✅ **Shipped v1.8.57:** Dedicated arrow-keys row preset (SwiftKey-parity P24) | 3 | 2 | 2 | **4.0** | `BottomRowPreset.Navigation` surfaces arrow keys from Settings → Keyboard |
| 18 | ✅ **Shipped v1.8.64:** Calendar quick-insert (P9) | 4 | 3 | 1 | **3.0** | `QuickAction.InsertCalendarEvent`, `CalendarContract.Instances`, explicit `READ_CALENDAR` grant, and IME-local picker |
| 19 | ✅ **Shipped v1.8.58:** Tasks quick-insert (P10) | 3 | 2 | 1 | **3.5** | `QuickAction.InsertTask` sends through the Android chooser and respects `SensitiveFieldGuard` |
| 20 | ✅ **Shipped v1.8.63:** Animated theme (P14) | 3 | 2 | 1 | **3.5** | `aurora_animated` uses the GenericShape background renderer and reduced-motion gate |
| 21 | ✅ **Shipped v1.8.63:** AAA high-contrast theme (P15) | 3 | 1 | 2 | **8.0** | `swiftkey_high_contrast` is registered and pinned by theme contrast tests |
| 22 | ✅ **Shipped v1.8.56:** Phase B4 same-sentence language-switch hardening | 4 | 2 | 2 | **5.0** | `TrailingContextLanguageBlend` replaced MAX-over-window scoring with geometric decay |
| 23 | ✅ **Shipped v1.8.62:** Phase C1 split-keyboard renderer | 4 | 3 | 1 | **3.0** | `TextKeyboardSplitLayout` + gutter-aware layout/touch routing shipped |
| 24 | **Lint baseline refresh** | 2 | 1 | 1 | **5.0** | Still open; attempted here but blocked by missing `JAVA_HOME` / `java` on PATH |
| 25 | ✅ **Shipped v1.8.67:** Reproducible-build verification CI job | 3 | 2 | 1 | **3.5** | `.github/workflows/reproducible-build.yml` builds twice and diffs release APKs |
| 26 | ✅ **Shipped docs-only:** Add SUPERSEDED banners to 5 superseded `SWIFTKEY_*` docs | 2 | 1 | 1 | **5.0** | Applied to every file named by `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` |
| 27 | ✅ **Shipped v1.8.75:** macOS `.keylayout` (XML) parser (Next-6.4a) | 3 | 2 | 1 | **3.5** | `MacKeylayoutParser` normalizes key maps and modifier maps into `HardwareKeyboardLayout` |
| 28 | ✅ **Shipped v1.8.76:** Hardware-keyboard runtime mapper (Next-6.4b) | 4 | 3 | 1 | **3.0** | `HardwareKeyboardRuntimeMapper` binds layouts by `deviceId` and dispatches mapped `KeyEvent`s through `KeyboardManager` |

## Tier 3 — Recommended later (score 2.0 – 3.4)

| # | Item | Impact | Cost | Urg. | Score | Rationale |
|---|---|---|---|---|---|---|
| 29 | ✅ **Shipped v1.8.77:** User-imported sticker folder | 4 | 2 | 1 | **4.5** | Settings → Emoji & stickers persists a SAF folder URI; `UserStickerRepository` enumerates local image files into an Imported pack; `StickerMediaProvider` proxies rich-content commits |
| 30 | **Roborazzi baseline capture for all 13 bundled themes** | 3 | 2 | 1 | **3.5** | After bump-batch B |
| 31 | ✅ **Shipped docs-only 2026-05-17:** `ARCHITECTURE.md` consolidating per-package docs | 3 | 2 | 1 | **3.5** | Root architecture map now covers modules, runtime entrypoints, package ownership, media/addon boundaries, security, CI, and tests |
| 32 | ✅ **Shipped docs-only 2026-05-17:** `CONTRIBUTING.md` | 2 | 1 | 1 | **5.0** | Root contributor guide now covers setup, project rules, verification, release notes, PRs, AI-assisted work, and licensing |
| 33 | ✅ **Shipped docs-only 2026-05-17:** Consolidate root-level `*MULTILINGUAL.md` / `VOICE_*.md` / `FUTO_*.md` into `docs/` | 2 | 2 | 1 | **2.5** | Multilingual gesture, multilingual research, FUTO voice troubleshooting, and voice-command docs moved under `docs/` with internal links updated |
| 34 | ✅ **Partially shipped v1.8.78:** `.kmp` Keyman package import foundation | 4 | 4 | 1 | **2.25** | `KeymanPackageParser` now covers safe `.kmp` ZIP intake, `kmp.json` metadata, LDML-in-package extraction, and package-status classification; compiled `.kmx` / `.js` runtime remains the large future addon task |
| 35 | ✅ **Shipped v1.8.79:** Honeycomb-hex layout wire-up | 3 | 4 | 1 | **1.75** | Bundled `honeycomb` layout now selectable; production `TextKeyboardLayout` clips Snygg keys to `HoneycombHexShape`; `TextKeyboard` uses hex geometry and hex-aware hit testing |
| 36 | ✅ **Shipped v1.8.80:** OpenSSL/BoringSSL SQLCipher provider migration plan | 3 | 3 | 1 | **2.33** | `docs/SQLCIPHER_PROVIDER_MIGRATION.md` now records the corrected provider state, OpenSSL proof-of-concept path, triggers, 16 KB gates, and rollback rules |
| 37 | **Self-hosted ACRA opt-in endpoint** | 2 | 3 | 1 | **1.67** | §9 Under Consideration; demand-gated |
| 38 | **Partially shipped v1.8.81-v1.8.82:** HeliBoard dictionary downloader UI pattern | 4 | 3 | 1 | **3.0** | Strategic; Next-10.3a addon catalog foundation landed (`AddonRegistry` + `DictionaryPackCatalog`), and v1.8.82 adds persisted signing pins; startup persistence wiring, Settings UI / install-hint list, and asset mounting remain |

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
- **`PROJECT_CONTEXT.md` + `AGENTS.md` + `CLAUDE.md` pointer files** (#13) are present and reconciled.
- **Bump-batch A** (#4) low-risk dep bumps; shipped v1.8.69. Keep Activity 1.13.0.
- **README release-hygiene maintenance** (#5; shipped through v1.8.70, then keep current).

### v1.8.57 (next week — migration-window prep)

- **EU AI Act Article 50 first-run + Settings → About explainer** (#7, #8, #11) shipped v1.8.66.
- **Tag catch-up** (#6; 29 tags) — local tags `v1.8.41` through `v1.8.69`
  were backfilled; push tags from the maintainer host.
- **README "Galaxy AI" + "Grammarly discontinued" callouts** (#2, #10) —
  shipped v1.8.70.

### v1.8.58–v1.8.60 (the migration window itself — 2026-05-28 to 2026-05-31)

- **2026-05-30 pinned release** (#1) with `docs/MIGRATE_FROM_SWIFTKEY.md` link + Obtainium URL above the fold.
- **Tink migration of androidx-security-crypto** (#3) — shipped v1.8.68.
- **HeliBoard NLnet slip-base-case plan** (#9) shipped v1.8.72 — ROADMAP §6 N1.1 now treats `swiftfloris-statistical` as production default and HeliBoard open-glide as additive.

### v1.8.61+ (post-migration window — Phase C / D)

- **Bump-batch C** (#16) shipped v1.8.74; Android 17 compile/target SDK remains separate from the AGP / BOM bump.
- **Phase C1 (split renderer)** (#23) shipped v1.8.62.
- **Calendar / Tasks quick-insert** (#18, #19) shipped v1.8.64 and v1.8.58.
- **AAA high-contrast theme + Animated theme** (#21, #20) shipped v1.8.63.
- Rows #17-#23 and #25 were reconciled as already shipped by the concurrent
  v1.8.56-v1.8.67 implementation stream. Remaining true open Tier-2 rows:
  #24 lint baseline refresh (maintainer host with Java required).
- Tier-3 #29 shipped in v1.8.77 as the read-only SAF user-sticker folder
  foundation. Delete-from-folder / explicit sticker-pack manifests remain
  future polish, not blockers for the imported-pack surface.
- Tier-3 #31 and #32 shipped as a docs-only contributor-onboarding batch:
  root `ARCHITECTURE.md`, root `CONTRIBUTING.md`, README documentation links,
  and the PR-template checklist now point contributors at SwiftFloris-specific
  invariants instead of upstream FlorisBoard-only guidance.
- Tier-3 #33 shipped as a docs-only repo-hygiene move: root multilingual and
  voice guides now live under `docs/`, and root pattern scans find no remaining
  `*MULTILINGUAL*.md`, `VOICE_*.md`, or `FUTO_*.md` files.
- Tier-3 #34 partially shipped in v1.8.78 as the safe Keyman `.kmp` package
  intake/classifier layer. The compiled Keyman runtime remains future addon work.
- Tier-3 #35 shipped in v1.8.79 as the honeycomb hex production wire-up. The
  remaining visual-baseline work is Roborazzi/device evidence, not the layout
  selection or hit-testing path.
- Tier-3 #36 shipped in v1.8.80 as a docs/security planning slice. The current
  SQLCipher Community AAR remains in place; the OpenSSL build path is now
  documented and trigger-gated.
- Tier-3 #38 / Next-10.3a partially shipped in v1.8.81 as the addon catalog
  foundation: live-state signing-pin reconciliation plus dictionary-pack
  descriptor/provenance validation. The downloader UI remains open until the
  Settings surface and APK asset mounting land.
- Next-10.3b shipped in v1.8.82 as the persisted signing-pin foundation:
  `AddonSigningPinSet` plus `prefs.addon.signingCertPins`. The remaining gap is
  wiring startup scans through the persisted key, then building the Settings
  UI/install-hint and asset-mounting slices.

This sequence respects the SwiftKey 2026-05-31 cutoff as the highest
external-clock anchor.
