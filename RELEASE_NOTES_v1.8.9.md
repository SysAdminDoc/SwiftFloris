# SwiftFloris v1.8.9 — 2026-05-15

Ninth autonomous slice. **675 unit tests** at HEAD, 0 failures.

## Next-3.1g — KenLM `TRIE` / `QUANT_TRIE` navigator

New `ime/nlp/kenlm/KenLmTrieNavigator` + `TrieOrderTable` + `TrieEntry`
close the pure-Kotlin KenLM reader stack. Sibling of
`KenLmProbingNavigator` but for trie-shaped search arenas (TRIE +
QUANT_TRIE model types):

- **`TrieEntry`** — one row in an order's entry table:
  `(entryIndex, parentEntryIndex, tailVocabIndex, logProb,
  logBackoff, nextPointerStart)`. The `nextPointerStart` field will
  feed `BhikshaPointerDecoder` in production; synthetic fixtures
  leave it `-1`.
- **`TrieOrderTable`** — one order's entry table indexed by
  `(parentEntryIndex, tailVocabIndex)`. The `find(parent, tail)`
  contract is the only required operation; tests use the
  `fromEntries(order, list)` builder.
- **`KenLmTrieNavigator.lookup(history, tail)`** — walks orders from
  longest matching context down to unigram, returning the matching
  `TrieEntry` or null.
- **`KenLmTrieNavigator.score(history, tail)`** — applies the same
  KenLM backoff math as the probing navigator
  (`logProb + Σ logBackoff(parent_context_of_skipped_order)`).
- **Parent-entry chain walk** — internal `traverseContext` resolves
  the order-`size` entry index for an arbitrary context, descending
  one order at a time and returning -1 when any link breaks.

5 unit tests cover the bigram-hit path, the bigram-miss-with-
unigram-fallback path (with parent backoff applied), the
absent-tail-returns-NEGATIVE_INFINITY path, the order-1-required
invariant, and a full trigram-chain walk.

Together with v1.8.6's `KenLmProbingHash`, v1.8.7's
`BhikshaPointerDecoder`, and v1.8.8's `KenLmQuantTable` /
`KenLmProbingNavigator`, the pure-Kotlin reader stack now covers
**all four** KenLM model types (PROBING / REST_PROBING / TRIE /
QUANT_TRIE) for navigation + scoring against synthetic fixtures.
Real-file plumbing (the byte-level Bhiksha encoding + the centroid
arrays + the entry table) feeds the same navigators in Next-3.1b's
production bring-up.

## L4.4 — Hebrew bidi run segmenter

New `ime/bidi/HebrewBidiSegmenter`:

- **`Direction`** classifies each character into one of:
  `HEBREW` (U+0590..U+05FF) / `LATIN` / `DIGITS` / `WHITESPACE`
  / `NEUTRAL` (punctuation + symbols).
- **`segment(text): List<Run>`** splits a string into contiguous
  same-direction runs. Surrogate pairs are honoured for the Latin
  + neutral runs.
- **`directionBefore(text, cursorIndex)`** returns the class of the
  character logically before the cursor — the standard query the
  layout engine asks when deciding caret affinity at a run boundary.
- **`dominantDirection(text)`** returns the direction of the longest
  non-whitespace, non-neutral run, used for smartbar single-word
  subtype hints.

8 unit tests cover pure Hebrew, mixed Hebrew + Latin, digit-only
strings, punctuation between Hebrew runs, cursor-position direction
query, empty-string edge cases, and dominant-direction picking
the longest letter run.

## L5.x — Three more Brahmic-derived scripts (Mongolian + Javanese + Sundanese)

Extends transliteration coverage from 15 to **18 scripts**:

- **Mongolian** (U+1800 block) — written historically vertically;
  Unicode block carries the consonant + vowel + digit inventory used
  for both vertical and Hudum Cyrillic transliteration; digits
  U+1810..U+1819; no native anusvara/visarga, slots collapse to the
  Mongolian "Sibe" delimiter U+1806.
- **Javanese** (U+A980 block) — Brahmic-derived Indonesian script;
  digits U+A9D0..U+A9D9; anusvara → Cecak (U+A981); visarga →
  Wignyan (U+A983).
- **Sundanese** (U+1B80 block) — Western-Javanese family; digits
  U+1BB0..U+1BB9; both anusvara + visarga collapse to the pamaaeh
  (U+1BAA, vowel-killer mark).

4 unit tests cover the three new tables (`a`/`k` → first-character
glyph, native-digit round-trips, sane size assertions).

## Tests

675 unit tests at HEAD (was 658 at v1.8.8), 0 failures, 0 skipped.
17 net new tests across 3 new test classes (KenLmTrieNavigatorTest +
HebrewBidiSegmenterTest + IndicScriptExtendedTest extensions).
