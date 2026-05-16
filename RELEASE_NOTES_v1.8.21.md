# SwiftFloris v1.8.21 — 2026-05-15

Twenty-first autonomous slice. **872 unit tests** at HEAD, 0 failures.

## L1.1e — SmartCompose candidate post-processor

New `ime/smartcompose/SmartComposeResultFilter` is the pure-function
chain that runs against every addon-returned `SmartComposeResult`
before the candidates reach the ghost-text overlay:

- **`filter(input, minConfidence = 0.30, maxCandidates = 3)`** —
  applies the full chain. `NoSuggestion` passes through unchanged.
- **Drop low-confidence** — anything below `minConfidence` is noise.
- **Drop blank/whitespace-only** — would render as phantom space.
- **Normalise internal whitespace** — collapse runs of spaces,
  trim leading/trailing.
- **De-duplicate** — collisions collapse to the highest-confidence
  variant.
- **Sort descending by confidence** — top-ranked first for tap-to-
  accept priority.
- **Clamp to `maxCandidates`** — bounded output even when the
  addon ignored its hint.
- **Empty-after-filter → NoSuggestion** — overlay disappears
  cleanly instead of rendering a blank box.

9 unit tests cover the eight transforms + the `minConfidence` range
invariant.

## L1.1f — SmartComposeRouter (end-to-end composition)

New `ime/smartcompose/SmartComposeRouter` is the single composition
point the NlpManager smart-compose path calls into. Layers every
v1.8.x building block in the right order:

  1. `SensitiveFieldGuard` — short-circuit on password / PIN /
     no-learn fields.
  2. `SmartComposeContextWindow` — sentence-aware truncation.
  3. `SmartComposeCache` — internal LRU (configurable capacity,
     `bypassCache = true` skips it for benchmarks).
  4. Underlying provider.
  5. `SmartComposeResultFilter` — drop noise / normalise / sort /
     clamp.

- **`predict(context, inputType, imeOptions, maxCandidates)`** —
  end-to-end entry point.
- **`clearCache()`** — flushes the LRU on language switch / addon
  rebind.

6 unit tests cover the password-field short-circuit, the plain-
text happy path with filtering, context truncation forwarding,
cache de-duplication of repeat predictions, `bypassCache` re-asks
every call, and the `IME_FLAG_NO_PERSONALIZED_LEARNING` suppression.

## L5.x — Three more Brahmic SE-Asian + Indian scripts

Total transliteration coverage from 51 to **54 scripts**:

- **Saurashtra** (U+A880 block) — Brahmic-derived script for the
  Saurashtra language of Tamil Nadu, India. Active in modern
  community publishing. Native digits U+A8D0..U+A8D9.
- **Kayah Li** (U+A900 block) — Brahmic-derived script for the
  Kayah / Karen languages of Myanmar + Thailand. Native digits
  U+A900..U+A909.
- **Rejang** (U+A930 block) — Brahmic-derived script for the
  Rejang language of Sumatra, Indonesia. Includes pre-nasalised
  consonant clusters (`mb`, `ngg`, `nd`, `nyj`).

4 unit tests cover the three new tables (first-consonant glyph,
Saurashtra Tamil-Indic digit round-trip, Kayah Li `ng` digraph
greedy, Rejang `ngg` three-char digraph greedy, sane size
assertions).

## Tests

872 unit tests at HEAD (was 853 at v1.8.20), 0 failures, 0 skipped.
19 net new tests across 3 new test classes (SmartComposeResultFilterTest +
SmartComposeRouterTest + IndicScriptExtendedTest extensions).
