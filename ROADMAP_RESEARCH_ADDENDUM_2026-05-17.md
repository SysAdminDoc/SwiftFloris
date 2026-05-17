# ROADMAP Research Addendum — 2026-05-17

This file is the **actionable output** of the autonomous research run at
[`.ai/research/2026-05-17/`](.ai/research/2026-05-17/). It does **not**
rewrite [ROADMAP.md](ROADMAP.md); it adds new commitments, corrections,
and reframings keyed to existing roadmap sections.

The convention is: every item here either (a) inserts a new line in an
existing `ROADMAP.md` table, (b) corrects an existing claim that the
research run verified to be stale, or (c) promotes/demotes/reframes an
existing item. When the next ROADMAP refresh (`v5.3`) lands, the items
here either flow into the relevant section or are explicitly retired with
reasoning.

**HEAD at write time:** v1.8.59 — Phase D3 typing-stats accuracy delta.
(The research run started at v1.8.55; v1.8.56-59 shipped concurrently in the
same release window, implementing Phase B4 + Phase C2 + Phase D2 + Phase D3.)

---

## 0. Reconciliation with concurrent v1.8.56-59 releases

While this research run was in flight, four releases landed that
implemented several recommendations:

| Recommendation in this addendum | Shipped as |
|---|---|
| Phase B4 same-sentence language-switch hardening (PRIORITIZATION_MATRIX Tier-1 #22) | ✅ **v1.8.56** — geometric-decay (`decay = 0.7`) weighted blend in new `TrailingContextLanguageBlend`; 8 new tests |
| §B.3 — Dedicated arrow-keys row preset (P24) | ✅ **v1.8.57 — Phase C2** — labeled `BottomRowPreset.Navigation` (with ARROW_LEFT / ARROW_UP / SPACE / ARROW_DOWN / ARROW_RIGHT / ENTER); equivalent to my N4.4 proposal modulo naming |
| §C.2 — Tasks quick-insert (P10) (PRIORITIZATION_MATRIX Tier-2 #19) | ✅ **v1.8.58 — Phase D2** — `QuickAction.InsertTask` via `Intent.ACTION_SEND` chooser; `SensitiveFieldGuard` gate; works with Tasks.org / OpenTasks / Google Tasks / Joplin / Notion / Markor |
| §C.4 — Personalization stats delta (P26) | ✅ **v1.8.59 — Phase D3** — `CorrectionOutcomePriors.accuracyDelta()` and Settings → Typing stats row for current-week accepted corrections versus last week |

These four are removed from this addendum's open commitments. The
remaining items below are still open and still recommended.

---

## A. Material corrections to existing ROADMAP claims

### A.1 KenLM is LGPL — incompatible with `:app`

**Where:** ROADMAP §7 Next-3.1 (lines 511 ff. — KenLM binary header reader scaffold).

**Issue:** KenLM is licensed **LGPL-2.1+**. Per ROADMAP §1 the `:app`
ceiling is Apache-2.0; LGPL cannot link into `:app`.

**Resolution:** the in-`:app` `KenLmBinaryReader.readHeader` parser is
**fine** — it parses a public binary format and is original work. The
**JNI bring-up against the KenLM library itself** must move to a
separate addon APK. Add explicit text to Next-3.1:

> Implementation note: the KenLM **runtime** is LGPL-2.1+ and therefore
> cannot link into `:app` (see §1 Apache-2.0 ceiling). The in-`:app`
> header parser is original code parsing a public format and is unaffected.
> Runtime scoring ships in `addons/kenlm-jni/` (a sibling-repo addon APK)
> at the same trust boundary as the librime / Bergamot / handwriting-mlkit
> addons.

If KenLM-in-addon proves heavy, the Apache-2.0 alternative is
**SentencePiece** (`google/sentencepiece`), which the project could adopt
in-`:app`. Cited in [.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md §4](.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md#4-license-compatibility-verification).

### A.2 `androidx-security-crypto:1.1.0-alpha06` is a deprecated-API artifact

**Where:** [app/build.gradle.kts](app/build.gradle.kts#L299) inline pin.

**Issue:** fifth-pass verification corrected the earlier wording:
`androidx.security:security-crypto:1.1.0` did ship, but the AndroidX
release notes deprecate the APIs in favor of platform APIs and direct
Android Keystore use. SwiftFloris is still pinned to older
`1.1.0-alpha06`; `EncryptedSharedPreferences` remains the wrong long-term
primitive for SQLCipher-passphrase wrapping.

**Resolution:** add a new ROADMAP item:

> **N7.6 (NEW)** Replace `androidx-security-crypto` with Google Tink
> (`com.google.crypto.tink:tink-android`, Apache-2.0). Wrap the SQLCipher
> passphrase via Tink `Aead`; protect the wrapping key through
> `AndroidKeystoreV1` KMS. Migrate existing on-disk passphrase shape via
> one-shot detection (same pattern as the v1.7.4 plaintext-DB →
> encrypted-DB migration). Update `PersonalDictionaryEncryptionTest` to
> pin the new contract. Effort: ~1 day. Reference:
> https://github.com/tink-crypto/tink-java

This is captured as Tier-1 #3 in
[.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md).

### A.3 `androidx-activity 1.13.0` is stable — downgrade retired

**Where:** [gradle/libs.versions.toml](gradle/libs.versions.toml#L4)
`androidx-activity = "1.13.0"`.

**Correction:** fifth-pass verification against AndroidX release notes
and Google Maven metadata shows `1.13.0` is the stable release. The
first-pass recommendation to downgrade to `1.12.4` is wrong.

**Resolution:** keep `androidx-activity = "1.13.0"` unless tests reveal a
SwiftFloris-specific regression. Bump-batch A no longer includes Activity.

### A.4 Multiple deps materially behind

**Where:** [gradle/libs.versions.toml](gradle/libs.versions.toml).

**Issue:** the following pins are materially behind 2026-05-17 latest:

| Pin | Current | Latest | Action |
|---|---|---|---|
| AGP | 9.0.0 | 9.2.x / 9.2.1 metadata | bump after dep + Roborazzi bumps and R8 audit |
| Compose BOM | 2026.03.01 | 2026.05.00 | bump alongside AGP |
| kotlinx-coroutines | 1.10.2 | 1.11.0 | bump alongside Kotlin |
| KSP | 2.3.5 | 2.3.8 | bump alongside Kotlin |
| Roborazzi | 1.55.0 | 1.60.0 | bump before AGP 9.2 |
| Robolectric | 4.14.1 | 4.16.1 | bump for SDK 36 / JDK 21 fidelity |
| aboutlibraries | 14.0.1 | 14.2.0 | bump |
| zxing-core | 3.5.3 | 3.5.4 | bump |

**Resolution:** added Tier-1 #4 (Bump-batch A: low risk),
Tier-1 #12 (Bump-batch B: visual-regression infrastructure), and Tier-2 #16
(Bump-batch C: build toolchain) to
[PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md).

### A.5 FlorisBoard upstream is frozen on v0.6.0-alpha02

**Where:** ROADMAP §4 Strategic Thesis — "Upstream drift. FlorisBoard
v0.6-alpha targets glide typing, predictions, floating mode, and Snygg
v2 themes."

**Issue:** v0.6.0-alpha02 shipped **2025-01-23**. **No alpha03 in 16+
months.** v0.6 milestone was re-scoped — glide + predictions were pushed
to v0.7 (the public-beta milestone). Last upstream stable is v0.5.2
(2025-11-28).

**Resolution:** SwiftFloris is **lapping a stalled upstream**, not
drifting from a moving target. Update §4 framing:

> 1. **Upstream drift, now upstream-lap.** FlorisBoard v0.6 alpha milestone
>    has been re-scoped — the v0.6 alpha02 (2025-01-23) is the latest
>    upstream alpha and ships Snygg v2 + floating + S-Pen-text-input
>    fixes; glide typing and word suggestions were pushed to v0.7's
>    public-beta milestone, which has no published date as of
>    2026-05-17. SwiftFloris is ahead of upstream on autocorrect,
>    dictionary, multilingual ranking, SwiftKey-parity surface polish,
>    voice handoff, MCP bridge, and encrypted personal dict; the
>    remaining upstream-only items SwiftFloris should consider absorbing
>    are Snygg v2 engine refresh and the CLDR 48 / Emoji 17 bumps.

### A.6 MediaPipe LLM Inference on Android is deprecated

**Where:** ROADMAP §8 L1 + Appendix `[STD-LITERT-LM]`.

**Issue:** Google officially deprecated the MediaPipe LLM Inference API
on Android (docs touched 2026-03-31). LiteRT-LM is the named successor.
ROADMAP already targets LiteRT-LM, but **does not explicitly REJECT**
MediaPipe — so a future contributor could re-propose it.

**Resolution:** add to ROADMAP §10 (Explicitly Rejected):

> | MediaPipe LLM Inference API on Android | Officially deprecated by Google as of 2026-03-31. LiteRT-LM is the named successor and the project's L1 target. Listed here so MediaPipe doesn't get re-proposed |

### A.7 FunctionGemma 270M shipped Jan 2026 — should be named in L1

**Where:** ROADMAP §8 L1 + §C.3.

**Issue:** ROADMAP §8 L1 names "Gemma 3 270M Q4 INT4 (~135 MB)" as the
target. In **January 2026** Google released **FunctionGemma**, a 270M
function-calling variant of Gemma 3 fine-tuned on Mobile Actions
(`litert-community/functiongemma-270m-ft-mobile-actions` on HuggingFace).
Action-call accuracy 58 % → 85 %. This is the more relevant model for
**any agentic / tool-use Smart Compose** that talks to the MCP daemon
bridge.

**Resolution:** add to §8 L1:

> **L1.1b (NEW)** FunctionGemma 270M as the named model target for the
> agentic / tool-use Smart Compose path. Same `.litertlm` packaging as
> Gemma 3 270M Q4; structured `tools/call` + natural-language
> explanation in one prompt. Pairs with the MCP daemon bridge
> (Settings → MCP) so user-installed daemons (calendar, weather, SMS
> tools) become first-class call targets.

---

## B. New ROADMAP items (NOW tier)

### B.1 N6.6 — Migration-window outreach checklist (full)

**Where:** ROADMAP §6 N6 (CI + release engineering hardening) or §6
new N16-extension.

**Why now:** SwiftKey cutoff is 14 days from HEAD; ROADMAP §14 Risk
Register already names this as the one-shot opportunity but has no
checklist for the marketing slice.

**Body:**

> - 2026-05-25 — soft pre-launch comms: README banner already up;
>   add a "see you on 5/30" pin to the GitHub repo description.
> - 2026-05-28 — short Reddit posts to r/SwiftKey, r/PrivacyGuides,
>   r/HeliBoard, r/fossandroid (link `docs/MIGRATE_FROM_SWIFTKEY.md`
>   + the Obtainium one-tap URL above the fold). Cap one post per
>   subreddit per week; no spam.
> - 2026-05-30 — pinned GitHub release on the day before the cutoff.
>   Release body opens with the migration story, the Obtainium URL,
>   and the `swiftkey-cloud.json` instructions. SHA-256 + signing
>   fingerprint published per N6.2 / N7.5.
> - 2026-05-31 — repo description updated to "SwiftKey migration
>   importer now in v1.8.<x>" if Phase A4 (still-open marketing
>   surface) is ready.
> - 2026-06-07 — one-week-after retrospective in a new
>   `docs/MIGRATION_RETRO_2026-06-07.md` recording how many
>   `swiftkey-cloud.json` imports succeeded vs failed in the
>   `DictionaryImporter` audit log, and how the parser was tuned
>   if anything broke.

### B.2 N8.7 — EU AI Act Article 50 transparency surface (2 Aug 2026)

**Where:** ROADMAP §6 N8 (Accessibility scoped pass — accessibility is
the closest existing surface, though this is privacy/regulatory not a11y;
could also land in §11 Privacy Hardening).

**Why now:** EU AI Act Article 50 transparency duties apply from
**2 August 2026**. Any AI-assisted feature interacting directly with users
must inform the user at first interaction.

**Body:**

> - **N8.7.1** First-run AI-features explainer screen lives next to
>   `app/setup/`. Lists the AI/ML surfaces in the IME
>   (next-word, glide, voice, translate, smart-compose), states
>   plainly "all processing on this device, no data leaves the device,
>   no vendor accounts," and links to `docs/THREAT_MODEL.md` +
>   `PROJECT_CONTEXT.md`. Cost: S.
> - **N8.7.2** Re-openable Settings → About → "AI features in this
>   keyboard" screen with the same content. Cost: XS.
> - **N8.7.3** New `docs/PRIVACY_AND_AI.md` writes up the privacy
>   posture diff vs SwiftKey / Gboard / Grammarly side-by-side. Cost: S.
> - Acceptance: `verifyNoInternetPermission` still passes; first-run
>   surface is keyboard-disabled-safe; explainer reopenable.

### B.3 N4.4 — Dedicated arrow-keys row preset (P24)

**Where:** ROADMAP §6 N4 (Customizable bottom row + smartbar).

**Why now:** SwiftKey-parity P24 still open. `BottomRowPreset.Programmer`
already provides the scaffolding for a separate `BottomRowPreset.ArrowsRow`.

**Body:**

> **N4.4 (NEW)** New `BottomRowPreset.ArrowsRow` selectable in Settings
> → Keyboard → Bottom-row preset → "Arrows row." Surfaces ← → ↑ ↓ +
> Home / End cluster in the main letter view. Closes SwiftKey-parity
> P24. Cost: S; reuses Next-8.1a `BottomRowKey` shape.

### B.4 N12.5 — Reproducible-build self-verification CI

**Where:** ROADMAP §6 N12 (Performance instrumentation + Roborazzi).

**Why now:** F-Droid verified-tier badge launched 2025-05; ~21 % of main
repo apps reproducible; SwiftFloris's pin matrix is in place but
`fdroiddata` submission has not happened.

**Body:**

> **N12.5 (NEW)** Local "build twice, compare APK checksums" CI job:
> assembleRelease in a clean checkout twice, diff the APK signing
> blocks, fail if non-determinism beyond the expected
> embedded-commit-hash region. Catches reproducibility regressions
> before F-Droid's rebuilder does. Cost: S.

### B.5 N16.2 — Tag every shipped release (catch-up)

**Where:** ROADMAP §6 N16 (the existing migration-related cluster — or
§12 Operating Cadence).

**Why now:** latest tag `v1.8.40`; HEAD `v1.8.59`. **19 missing tags**
since v1.8.40. Obtainium auto-update keys off GitHub Releases, but
release.yml triggers on `workflow_dispatch` not on tag-push, so the
release stream is decoupled from tags. Tags are still the canonical
shipped-commit anchor for forks / audit.

**Body:**

> **N16.2 (NEW)** Tag every shipped release v1.8.41 through v1.8.59
> from its corresponding `gradle.properties`-bumping commit. Tags push
> only on the user's main host (push to `SysAdminDoc/SwiftFloris` is
> blocked from the dev VM per the established workflow). Establish a
> per-release "tag concurrently with the release notes commit" rule
> going forward.

## C. New ROADMAP items (NEXT tier)

### C.1 Next-9.5 — User-imported sticker folder

**Where:** ROADMAP §7 Next-9 (Inline `commitContent()` for sticker / GIF
/ image insertion).

**Why now:** No surveyed keyboard offers user-imported sticker libraries.
The `StickerMediaProvider` already in tree handles the URI + permission
grant. Inserting a SAF document tree as the stickers source closes the
last open piece.

**Body:**

> **Next-9.5 (NEW)** Settings → Media → "Import sticker folder…" opens
> SAF tree picker; selected URI persisted via `prefs.media.stickerFolder`;
> `StickerMediaProvider` walks the folder for `.webp` / `.png` / `.gif`
> and surfaces each as a sticker in the media panel. Long-press →
> "Remove from this folder" (deletes the file via SAF). Reuses the
> existing `commitContent(InputContentInfoCompat)` rich-content path.
> Cost: M; pure surface-area work over existing primitives.

### C.2 Next-10.4 — HeliBoard-style dictionary downloader UI

**Where:** ROADMAP §7 Next-10 (Plugin / addon APK loading).

**Why now:** HeliBoard's killer ecosystem feature is the in-app
dictionary catalog + download UI. SwiftFloris's Next-10.3 already
schemas dictionary-pack addon APKs; the runtime list + install UI is the
missing piece.

**Body:**

> **Next-10.4 (NEW)** Settings → Addons → Dictionary packs screen lists
> installed addon APKs that declare
> `dev.patrickgold.florisboard.action.REGISTER_DICTIONARY_PACK`,
> and (when the FlorisBoard Addons marketplace exposes a no-network
> directory file or a curated bundled JSON in `assets/`) lists
> available-not-yet-installed packs with a "Get from F-Droid /
> IzzyOnDroid" link. **No in-app download** (would require INTERNET);
> just discoverability + install-hint. Cost: M.

### C.3 Next-12.6 — Roborazzi baseline for every bundled theme

**Where:** ROADMAP §7 Next-12 (Performance instrumentation + Roborazzi).

**Body:**

> **Next-12.6 (NEW)** Capture Roborazzi baseline PNGs for each of the
> 13 bundled themes against (a) the QWERTY letter keyboard,
> (b) the suggestion strip with 3 candidates, (c) the smartbar with
> the CODE profile active, and (d) the emoji palette. Removes the
> `continue-on-error: true` from `:app:verifyRoborazziDebug` in
> CI. Cost: M.

## D. New ROADMAP items (LATER tier)

### D.1 L13 — CleverKeys-architecture Apache-2.0 reimplementation

**Where:** ROADMAP §8 (LATER) — new tier-13 item.

**Why now:** CleverKeys (GPL-3.0) is shipping a working 13 MB transformer
encoder/decoder glide model with sub-200 ms latency on Pixel 7 via
XNNPACK in F-Droid as of v1.4.0 (2026-04-26). SwiftFloris cannot link it
but could train an Apache-2.0 model against the same architecture once
the HeliBoard NLnet swipe dataset (or another permissive set) lands.

**Body:**

> **L13. CleverKeys-architecture Apache-2.0 glide reranker**
>
> - L13.1 — train an encoder/decoder transformer (~5 MB encoder + ~8 MB
>   decoder ONNX, XNNPACK / ONNX Runtime Mobile) against a permissive
>   swipe-trace corpus. Architecture reference: `tribixbite/CleverKeys-ML`.
> - L13.2 — plug into the existing `NeuralCandidateReranker` boundary as
>   a glide-specific path; heuristic ranker stays the default.
> - L13.3 — share the dataset upstream under a permissive license so the
>   FOSS keyboard field benefits.
>
> **Gated on:** HeliBoard NLnet open-glide dataset release (or any
> permissively-licensed corpus). Slip-base-case framing in §6 N1.1 applies.

## E. Items in §9 (Under Consideration) to promote

### E.1 Promote: Per-app "tone profile" (KenLM weight swap by package)

**Why now:** Per-app profile detection plumbing already shipped
(Next-4.3). The tone-profile angle is one line of preference + one new
Snygg "active package" hook. Promote from Under Consideration → §7 Next.

**Body suggestion:** Promote to **Next-13 (NEW)** with a "depends on
addon-side KenLM bring-up via the §A.1 path" gate.

## F. ROADMAP §10 (Explicitly Rejected) — additions

The following should be added with reasoning to prevent re-litigation:

| New rejected item | Why |
|---|---|
| **MediaPipe LLM Inference API on Android** | Officially deprecated 2026-03-31; LiteRT-LM is the successor (already targeted in §8 L1) |
| **NLLB-200 (offline NMT)** | CC-BY-NC-4.0 license — non-commercial conflicts with §1 audit-friendly distribution. Bergamot (MPL-2.0) is the right path |
| **CleverKeys glide model (as-shipped)** | GPL-3.0; cannot link. Architecture is the reference (see new §8 L13). Restated for completeness |
| **KenLM library linked into `:app`** | LGPL-2.1+; cannot link. Header parser in-`:app` is fine; runtime ships in addon (see §A.1). Restated for completeness |

## G. ROADMAP §14 (Risk Register) — updated rows

### G.1 HeliBoard NLnet slip risk — promote to "High"

**Current row:**

> | HeliBoard's NLnet glide drop ships first and becomes the de facto OSS swipe lib | High | Low (good for users; we adopt it; positive outcome) | Plan N1.1 as the default path |

**Updated:**

> | HeliBoard NLnet open-glide library slips past 2026-06-01 deadline | **High (now base case)** | Medium (delays N1.1; keeps SwiftFloris on the bounded statistical classifier) | Reframe N1.3 statistical as the *production* default, not the placeholder. Plan N1.1 integration as additive once the library lands. Gesture-data accrual via HeliBoard's data-gathering feed already started; whether the dataset gets a permissive release is the second-order risk |

### G.2 New row — `androidx-security-crypto` deprecated API surface

| New risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `androidx-security-crypto:1.1.0-alpha06` keeps SQLCipher-passphrase wrapping on deprecated AndroidX Security APIs even though 1.1.0 stable exists | High (already true) | Medium (no crash, but security hygiene + key-rotation issues + F-Droid review smell) | Migrate to Google Tink + AndroidKeystoreV1 (see §A.2 / new N7.6 item) |

### G.3 New row — EU AI Act Article 50 cutoff

| New risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| EU AI Act Article 50 transparency duties (2 Aug 2026) apply to next-word / glide / voice / translate / smart-compose | Certainty | Low if surface added on time; Medium if not (regulatory review smell from EU users / F-Droid editors) | Ship first-run explainer + Settings → About → "AI features" screen + `docs/PRIVACY_AND_AI.md` (see new N8.7 item) |

## H. ROADMAP §13 (Out-of-Scope Adjacent Wins) — note

The Samsung One UI 7 decoupling of Galaxy AI Writing Assist from Samsung
Keyboard is a **good-news adjacency**, not an out-of-scope item. It
removes the vendor-keyboard lock-in for users on Samsung S25/S26. Worth
calling out in README (Tier-1 #2 in
[PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md))
but does not require a roadmap change.

---

## Status legend

🟢 = new commitment ready to ship in next slice
🟡 = new commitment, gated on listed dependency
🔄 = correction / reframing to existing item
🔴 = new risk-register entry, action required

| Item | Status |
|---|---|
| §A.1 KenLM license boundary | 🔄 |
| §A.2 Tink migration (new N7.6) | 🟢 |
| §A.3 activity 1.13.0 downgrade retired | 🔄 |
| §A.4 Bump-batches A/B/C | 🟢 (A); 🟢 (B); 🟡 on B (C) |
| §A.5 Upstream-lap framing | 🔄 |
| §A.6 MediaPipe rejection | 🔄 |
| §A.7 FunctionGemma named target | 🔄 |
| §B.1 Migration outreach checklist | 🟢 |
| §B.2 EU AI Act surface (N8.7) | 🟢 |
| §B.3 Arrows-row preset (N4.4) | 🟢 |
| §B.4 Reproducible-build CI (N12.5) | 🟢 |
| §B.5 Tag catch-up (N16.2) | 🟢 |
| §C.1 User-imported sticker folder (Next-9.5) | 🟢 |
| §C.2 Dictionary downloader UI (Next-10.4) | 🟡 on Next-10.3 marketplace |
| §C.3 Roborazzi per-theme baseline (Next-12.6) | 🟡 on Bump-batch B |
| §D.1 L13 CleverKeys-arch Apache-2.0 | 🟡 on dataset |
| §E.1 Per-app tone profile promotion | 🟡 on addon-side KenLM |
| §F new §10 rejections | 🔄 |
| §G.1 HeliBoard slip-risk promote | 🔴 |
| §G.2 Tink-migration risk | 🔴 |
| §G.3 EU AI Act risk | 🔴 |
