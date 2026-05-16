# SwiftFloris v1.8.14 — 2026-05-15

Fourteenth autonomous slice. **757 unit tests** at HEAD, 0 failures.

## L4.7 — Visual ↔ logical text reorderer

New `ime/bidi/VisualLogicalReorderer` adds JVM-stdlib-backed
visual ↔ logical text reordering for surfaces that don't re-run the
Unicode Bidi algorithm at paint time:

- **`logicalToVisual(logical, baseIsRtl)`** — runs the logical-order
  input through `java.text.Bidi`, returns the visual-order
  rendering. Pure-LTR input returns unchanged.
- **`visualToLogical(visual, baseIsRtl)`** — inverse helper for
  legacy surfaces that persist text in visual order. Single-script
  RTL is reversed; mixed-direction visual input is left as identity
  because the inverse isn't well-defined without out-of-band run
  info.
- **`needsReordering(text, baseIsRtl)`** — cheap predicate that
  returns true when the paragraph would render differently under
  visual vs logical order. Lets callers skip the
  `logicalToVisual` allocation when nothing would change.

Uses `java.text.Bidi` (the same ICU-backed engine Android's
text-rendering layer uses internally) — no native dep, no library
add.

7 unit tests cover the pure-LTR no-op, pure-RTL Hebrew reordering,
the single-script RTL `visualToLogical` reverse, LTR `visualToLogical`
identity, empty-input passthrough, and mixed Hebrew+Latin
needsReordering detection.

## L5.x — Three more ancient Anatolian scripts

Total transliteration coverage from 30 to **33 scripts**:

- **Carian** (U+102A0 block, supplementary plane, RTL) — Indo-
  European Anatolian-language alphabet used in southwest Asia Minor
  c. 7th-3rd century BCE.
- **Lycian** (U+10280 block, supplementary plane, RTL) — Anatolian
  alphabet used predominantly on stone tomb inscriptions c. 5th-4th
  century BCE.
- **Lydian** (U+10920 block, supplementary plane, RTL) — Anatolian
  script used at Sardis c. 7th-3rd century BCE, historically
  boustrophedon (alternating direction per line).

4 unit tests cover the three new tables (first-letter glyph,
Lycian aspirated `th` digraph, Lydian `ng` digraph greedy match,
sane size assertions).

## Tests

757 unit tests at HEAD (was 746 at v1.8.13), 0 failures, 0 skipped.
11 net new tests across 2 new test classes (VisualLogicalReordererTest +
IndicScriptExtendedTest extensions).
