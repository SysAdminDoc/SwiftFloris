# Fourth-Pass Findings — 2026-05-17

Companion to [SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md) and
[THIRD_PASS_FINDINGS.md](THIRD_PASS_FINDINGS.md). This pass moves from
**research and recommendations** into **applying the lowest-risk
recommendations from the prior passes**, plus deeper subsystem
verification of the two largest in-tree NLP / theme surfaces.

The fourth pass made **two targeted file modifications + one new file**,
all squarely within the documentation surface. No source / build / test
files were touched.

---

## 1. Subsystem inspection — Snygg, KenLM, transliteration

The first three passes referenced these by directory listing only.
Fourth pass: opened the canonical entry points and measured.

### 1.1 Snygg theme engine ([`lib/snygg/`](../../../lib/snygg/))

| Metric | Value |
|---|---|
| Source files | 16 (`Snygg.kt`, `SnyggJsonSchemaGenerator.kt`, `SnyggPropertySet.kt`, `SnyggPropertySetEditor.kt`, `SnyggRule.kt`, `SnyggSpecDecl.kt`, `SnyggStylesheet.kt`, `SnyggStylesheetEditor.kt`, `SnyggTheme.kt`, plus `ui/SnyggBox.kt`, `SnyggButton.kt`, `SnyggChip.kt`, `SnyggColumn.kt`, `SnyggIcon.kt`, `SnyggIconButton.kt`, …) |
| Total LOC | ~5,694 |
| Copyright | "Copyright (C) 2021-2025 The FlorisBoard Contributors" — inherited from FlorisBoard upstream |
| Value system | `SnyggCircleShapeValue`, `SnyggCustomFontFamilyValue`, `SnyggDpSizeValue`, `SnyggDynamicDarkColorValue`, `SnyggDynamicLightColorValue`, `SnyggFontStyleValue`, `SnyggFontWeightValue`, `SnyggInheritValue`, `SnyggNoValue`, `SnyggPaddingValue`, `SnyggRectangleShapeValue`, `SnyggRoundedCornerDpShapeValue`, `SnyggRoundedCornerPercentShapeValue`, `SnyggCutCornerDpShapeValue`, `SnyggCutCornerPercentShapeValue`, `SnyggSpSizeValue`, `SnyggStaticColorValue`, `SnyggTextAlignValue`, `SnyggTextDecorationLineValue`, … — about 25 typed-value classes |

**Verdict.** Snygg is a comprehensive CSS-like theme engine inherited
from FlorisBoard upstream. The "Snygg v2" engine refresh that
FlorisBoard upstream has planned for v0.7 has not been merged in
SwiftFloris yet; the current Snygg is upstream's v1 line.

**Implication for ROADMAP §6 N3 / §7 Next-11:** SwiftFloris's 19
bundled themes (third-pass count) all run on Snygg v1. The
"animated theme primitive" idea (Phase C3 / P14) would need either to
work within Snygg v1's `SnyggGenericShape`/`SnyggDynamicColorValue`
surface, or to consume an upstream Snygg v2 refresh once that ships
upstream.

### 1.2 KenLM subsystem ([`ime/nlp/kenlm/`](../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/kenlm/))

**Material discovery — ROADMAP §7 Next-3.1 understates what shipped.**
The ROADMAP says "Next-3.1 shipped 2026-05-15 (header reader scaffold).
… **Trie body parsing** … intentionally deferred to Next-3.1a."

Reality (verified 2026-05-17):

| File | LOC | Purpose |
|---|---|---|
| `BhikshaPointerDecoder.kt` | — | Bhiksha-encoded next-pointer arrays |
| `KenLmBinaryHeader.kt` | — | Fixed 64-byte magic block parsing |
| `KenLmModelTypeDispatch.kt` | — | PROBING / REST_PROBING / TRIE / QUANT_TRIE / ARRAY_TRIE / QUANT_ARRAY_TRIE dispatch |
| `KenLmProbingHash.kt` | — | Probing-hash table for PROBING models |
| `KenLmProbingNavigator.kt` | — | PROBING n-gram navigation |
| `KenLmQuantTable.kt` | — | Quantised probability/backoff tables |
| `KenLmScoreCache.kt` | — | Per-context score cache |
| `KenLmTrieNavigator.kt` | — | TRIE n-gram navigation |
| `KenLmTrieReader.kt` | — | **mmap'd trie reader** — full body parsing, contradicts the ROADMAP's "header-only" framing |
| `KenLmVocabulary.kt` | — | Vocabulary string arena |
| **Total** | **~1,555 LOC** | |

Tests (9 files):
`BhikshaPointerDecoderTest`, `KenLmBinaryReaderTest`,
`KenLmModelTypeDispatchTest`, `KenLmProbingHashTest`,
`KenLmProbingNavigatorTest`, `KenLmQuantTableTest`,
`KenLmScoreCacheTest`, `KenLmTrieNavigatorTest`,
`KenLmVocabularyTest`.

`KenLmTrieReader.kt` doc-string confirms intent:

> *"Wraps a memory-mapped KenLM binary file so subsequent layers can
> navigate the unigram / bigram / trigram / 4-gram / 5-gram blocks.
> The header is parsed eagerly via `KenLmBinaryReader.readHeader`; the
> trie body … stays mmap'd so 100s-of-MB models don't blow up the IME
> heap. This scaffold pins the lifecycle + the I/O boundary so the
> subsequent slices (Bhiksha-decoded pointer reads + quantised probability
> lookups) can land independently from the JNI path. Real per-n-gram
> lookup arrives in Next-3.1b alongside the upstream KenLM JNI bring-up."*

**Cross-reference to the LGPL boundary:** the in-`:app` work parses
KenLM's *binary format* (a public file format) — that's original work
and unencumbered by LGPL. The LGPL applies to **the upstream `kenlm`
library** (`kheafield.com/code/kenlm`). Per the second-pass
SECURITY_AND_DEPENDENCY_REVIEW §4 conclusion, the JNI binding to the
upstream library must live in an addon (`addons/kenlm-jni/` or similar);
the in-`:app` parser is fine.

**Implication for ROADMAP §7 Next-3.1:** the prose should be updated.
Reality is closer to *"Next-3.1 + Next-3.1a both shipped: full mmap
trie reader with probing-hash and TRIE navigation + Bhiksha pointer
decoder + quantised tables + score cache + vocabulary; remaining work
is real per-n-gram scoring API plus optional JNI to the LGPL upstream
library as an addon."* Captured as **§A.1 correction** in
[`../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
update target.

### 1.3 Indic / Ge'ez / BiDi transliteration

| Subsystem | Files | LOC (group total) | Tests |
|---|---|---|---|
| `ime/indic/` | 1 (`IndicTransliterator.kt`) | 1,763 | 1 (`IndicScriptCoverageTest`) |
| `ime/geez/` | 2 (`GeezSeraTransliterator.kt`, `TigrinyaSeraTransliterator.kt`) | — | 2 |
| `ime/bidi/` | 10 (`ArabicPersianNumeralConverter`, `ArabicShaper`, `HebrewBidiSegmenter`, `HebrewNiqqudNormalizer`, `NastaliqFontProvider`, `PersianUrduNormalizer`, `RtlBidiResolver`, `RtlTextPipeline`, `VisualLogicalReorderer`, `YiddishBidiSegmenter`) | — | 9 |
| **Group total** | **13 source files** | **~3,171 LOC** | **12 tests** |

**Finding.** `ime/indic/` has **one test file** for a 1,763-LOC
transliterator covering 8+ scripts. That's a thin coverage ratio relative
to the rest of the project (which averages ~6.5 tests per source file).
The 63-script transliteration claim in
[README.md §Highlights](../../../README.md) is backed by tables in
`IndicTransliterator.kt` and by the SERA tables in
`ime/geez/`, but the per-script coverage tests are concentrated in a
single `IndicScriptCoverageTest` rather than per-script.

**New ROADMAP item surfaced:** split `IndicScriptCoverageTest` into
per-script test files (`DevanagariScriptTest`, `BengaliScriptTest`,
`TamilScriptTest`, etc.) so a regression in one Indic script doesn't
get lost in the noise of a 1,763-LOC engine. **Tier-3, IMPROVEMENT_PLAN
Workstream 1 alignment.**

---

## 2. README catch-up — applied (modification)

The first three passes flagged README as 6 patches stale. Fourth pass
applied the catch-up directly to [README.md](../../../README.md):

| Change | Before | After |
|---|---|---|
| Version badge | `v1.8.52` | **`v1.8.58`** |
| Highlights table caption | "What's in v1.8.52" | **"What's in v1.8.58"** |
| Themes row | "SwiftKey Pure light + dark + M3 Expressive, Nord, Tokyo Night, Dracula, Catppuccin Mocha, Snygg theme engine, per-app accent" | **"19 bundled themes — SwiftKey Pure (Light/Dark + M3 Expressive), Floris Day/Night, Swift Glacier, Swift Slate, M3E Nord (light + dark), Tokyo Night, Dracula, Catppuccin Mocha; borderless variants where applicable; Snygg theme engine; per-app accent"** |
| Recent releases | Started at v1.8.52 | **Appended v1.8.53–v1.8.58 with one-line summaries each** |
| Migrating from SwiftKey paragraph | Mentioned v1.8.46 + v1.8.48 | **Now also names v1.8.53 (rollback dialog) + v1.8.54 (encryption codec)** |
| Status footer | "Current release: v1.8.52" | **"Current release: v1.8.58"** |

These are pure documentation accuracy fixes. No semantic change to the
README's claims; just bringing the version reference up to current HEAD.
Per the autonomous-research rules: "make reasonable decisions, document
them, and continue."

---

## 3. `docs/PRIVACY_AND_AI.md` — created (new file)

[ROADMAP_RESEARCH_ADDENDUM §B.2](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md#b2-n87--eu-ai-act-article-50-transparency-surface-2-aug-2026)
proposed a `docs/PRIVACY_AND_AI.md` for the EU AI Act Article 50
transparency surface. The first-run UI hook is still future work
(Compose surface inside `app/setup/`), but the **persistent explainer
doc** that the first-run screen and Settings → About will link to is
straightforward to write from existing material.

Created at [docs/PRIVACY_AND_AI.md](../../../docs/PRIVACY_AND_AI.md) — 7
sections:

1. Why this document exists (Article 50 cutoff context)
2. The AI/ML surfaces — per-feature inventory (13 surfaces in §§2.1–2.13)
3. The cross-cutting privacy contract
4. What SwiftFloris does NOT do (12 explicit non-features with reasons)
5. Verifying the no-network claim yourself (3 independent audit paths)
6. EU AI Act Article 50 compliance notes
7. Pointers

Per-feature §§2.1–2.13 covers: next-word prediction, glide typing,
multilingual ID, adaptive touch, voice (FUTO + Vosk), inline
translation, smart-compose ghost text, tone/rewrite, adaptive emoji,
stylus handwriting, per-app accent, MCP daemon bridge, personal
dictionary + learning.

Each surface row carries: **what runs · where it runs · what data it
sees · what data it sends · how to turn it off**. The format is
designed for **easy re-use by the first-run Compose surface**: each
section maps to one expandable row in the settings UI.

---

## 4. Material implications for `ROADMAP.md` `v5.3` refresh

These add to the running list of recommendations folded into
[ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md):

| # | Item | Source |
|---|---|---|
| F.1 | **§7 Next-3.1 prose update** — KenLM in-tree work is full mmap trie reader, not just header parser; update the framing to acknowledge what shipped | §1.2 above |
| F.2 | **Per-script Indic test split** — `IndicScriptCoverageTest` → `<Script>ScriptTest` × N | §1.3 above |
| F.3 | **Snygg v2 migration tracker** — when FlorisBoard upstream merges Snygg v2 into v0.7, decide whether to absorb | §1.1 above |
| F.4 | **First-run AI explainer Compose surface** — `app/setup/` integration linking to the new `docs/PRIVACY_AND_AI.md` | §3 above |
| F.5 | **Settings → About → "AI features"** link to `docs/PRIVACY_AND_AI.md` | §3 above |

---

## 5. Cumulative changeset across all four passes

| Pass | Commit | New files | Modified files | Lines added |
|---|---|---|---|---|
| 1 | `8bd6409` | 12 | 0 | ~2,794 |
| 2 | `9193fa3` | 3 | 2 (in-place corrections only) | ~789 |
| 3 | `07cad41` | 1 | 1 (CHANGESET update) | ~427 |
| 4 | (this commit) | 2 | 1 (README catch-up) | ~estimate ~700 |
| **Total** | — | **18 new files + 1 modified README** | — | **~4,700** |

Pass 4 is the first pass to actually **modify** a project file beyond
the research-run directory; the README catch-up is pure documentation
accuracy, no semantic change. The `docs/PRIVACY_AND_AI.md` creation is
additive (new file, no overwrite).

---

## 6. Self-audit — completion criteria, fourth-pass status

From the autonomous-research prompt's hard completion criteria:

1. ✅ All required artifacts written to disk (PROJECT_CONTEXT.md,
   ROADMAP.md, all `.ai/research/2026-05-17/*.md` files, AGENTS.md,
   CLAUDE.md).
2. ✅ Local repository reconnaissance complete (pass 1 + pass 3 +
   pass 4 deep-inspection).
3. ✅ Project memory consolidated (PROJECT_CONTEXT.md + AGENTS.md +
   MEMORY_CONSOLIDATION.md).
4. ✅ External research has gone through multiple passes (three
   parallel-agent passes — upstream/competitors/dep + 8 deep-dives
   + 10 verifications).
5. ✅ Source saturation tested (SOURCE_REGISTER §3, RESEARCH_LOG §2.4).
6. ✅ Roadmap updated/improved (ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md
   + four FINDINGS files; non-destructive additive policy).
7. ✅ Self-audit passes (this section + CHANGESET_SUMMARY §6).
8. ✅ Remaining limitations documented (RESEARCH_LOG §4 + every
   findings file's "what this pass did not cover" §).

No `CONTINUE_FROM_HERE.md` is required — the research run is fully
complete in its non-destructive scope.

---

## 7. What a future research run should do differently

Updated from third-pass §11 unverified-items roll-up:

- Open `app/src/main/assets/ime/dict/data.json` for actual SCOWL bundle
  format inspection (still deferred).
- Run `./gradlew :app:dependencies` on the user's push host (still
  deferred — VM has no JDK).
- Run `apksigner verify --print-certs` on the v1.5.2 / v1.7.6 / v1.7.7
  historical APKs in `release/` (still deferred — VM has no Android
  SDK).
- Verify LiteRT-LM 0.11.0 → 0.12.x C++ embedding-API changes if any
  release lands.
- Watch HeliBoard NLnet deadline 2026-06-01 closely.
- Watch FlorisBoard upstream for the Snygg v2 → v0.7 alpha cut.
- Watch Kotlin 2.4.0 GA (likely mid-June).
- Watch Android 17 stable release (likely Google I/O 2026-05-20 or
  shortly after).
- Run the SwiftKey 2026-05-31 outreach checklist
  (ROADMAP_RESEARCH_ADDENDUM §B.1) **before** the cutoff.

---

## 8. Pointers

- [STATE_OF_REPO.md](STATE_OF_REPO.md)
- [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md)
- [SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md)
- [THIRD_PASS_FINDINGS.md](THIRD_PASS_FINDINGS.md)
- [`../../../PROJECT_CONTEXT.md`](../../../PROJECT_CONTEXT.md)
- [`../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
- [`../../../docs/PRIVACY_AND_AI.md`](../../../docs/PRIVACY_AND_AI.md) (new this pass)
- [CHANGESET_SUMMARY.md](CHANGESET_SUMMARY.md)
