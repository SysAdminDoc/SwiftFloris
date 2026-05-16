# SwiftFloris v1.8.10 — 2026-05-15

Tenth autonomous slice. **692 unit tests** at HEAD, 0 failures.

## Next-3.1h — KenLM model-type dispatch facade

New `ime/nlp/kenlm/KenLmModelTypeDispatch` caps the pure-Kotlin
reader stack with a single entry-point:

- **`sealed interface KenLmScorer`** — uniform contract
  (`modelType`, `maxOrder`, `score(history, tail)`) that the caller
  (NlpManager / ranker) drives without knowing the on-disk layout.
- **`KenLmModelTypeDispatch.build(modelType, vocabulary, probingPath?, triePath?)`**
  — picks `KenLmProbingNavigator` for `PROBING` / `REST_PROBING` and
  `KenLmTrieNavigator` for `TRIE` / `QUANT_TRIE` / `ARRAY_TRIE` /
  `QUANT_ARRAY_TRIE`. `UNKNOWN` throws `IllegalArgumentException` so
  the caller can fall back safely to the existing bigram chain.
- **`ProbingInputs` / `TrieInputs`** — typed data carriers for the
  per-order tables; using the wrong one for a model type throws.

5 unit tests cover both PROBING and TRIE dispatch paths, QUANT_TRIE
routing to the trie navigator, UNKNOWN-type rejection, and the
missing-inputs rejection.

This closes the pure-Kotlin KenLM reader stack started in v1.8.6:
header (Next-3.1) + vocabulary (Next-3.1b) + probing-hash arena
(Next-3.1c) + Bhiksha pointer decoder (Next-3.1d) + quant codec
(Next-3.1e) + probing navigator (Next-3.1f) + trie navigator
(Next-3.1g) + this dispatcher (Next-3.1h). All four KenLM model
types are now navigable + scoreable end-to-end against synthetic
fixtures with no JNI dependency.

## L6.x — Tigrinya / Tigre / Blin SERA transliterator

New `ime/geez/TigrinyaSeraTransliterator` layers the Tigrinya-
distinctive glyph inventory on top of the shared `GeezSeraTransliterator`:

- **qhe series** (U+1250..U+1256, ቐ ቑ ቒ ቓ ቔ ቕ ቖ) — emphatic /q'/
  retained in Tigrinya / Tigre orthography that Amharic collapses
  into ቀ.
- **xa series** (U+1280..U+1286, ኀ ኁ ኂ ኃ ኄ ኅ ኆ) — historical `ḫa`
  retained in Tigrinya / Tigre.
- **Labio-velars** — `kWa` → ኳ (U+12B3), `gWa` → ጓ (U+1313).

Composition seam added to `GeezSeraTransliterator`:
**`transliterateWith(latin, otherTable)`** runs the same greedy
longest-match loop against any caller-supplied lookup table, so
dialect subclasses don't re-derive the radical × vowel grid. The
shared `table` is now `internal` rather than `private`.

7 unit tests: Tigrinya-specific qhe / xa / labio-velar mappings, the
shared "slam" (Amharic SERA for "peace" — ሰላም) round-trip, longest-
match priority of multi-char Tigrinya keys, and unmapped-passthrough.

## L5.x — Three more scripts: Adlam + N'Ko + Cherokee

Total transliteration coverage from 18 to **21 scripts**:

- **Adlam** (U+1E900 block, supplementary plane) — West African
  alphabetic script for Pulaar / Fulani; native digits
  U+1E950..U+1E959.  Required a fix to `buildIndicMappings`'
  digit-iteration loop — old code iterated by `Char` (16-bit unit)
  and split surrogate pairs; new code iterates by code point via
  `codePointAt + Character.charCount`.
- **N'Ko** (U+07C0 block) — West African alphabetic script for the
  Manding family, runs right-to-left; native digits U+07C0..U+07C9.
  Subtype routes through the existing `RtlBidiResolver` when active.
- **Cherokee** (U+13A0 block) — US-indigenous syllabary (only one in
  mainstream Unicode use). Each glyph represents a CV syllable, so
  the table maps Romanised syllables (`ga`, `tla`, `qua` etc.) to
  single Cherokee characters. Falls outside `buildIndicMappings`
  (no anusvara / visarga concept) — uses a hand-built map.

5 unit tests cover the three new tables (first-consonant glyph,
native-digit round-trips, greedy longest-match for Cherokee
multi-character syllables, sane size assertions).

## Tests

692 unit tests at HEAD (was 675 at v1.8.9), 0 failures, 0 skipped.
17 net new tests across 3 new test classes (KenLmModelTypeDispatchTest +
TigrinyaSeraTransliteratorTest + IndicScriptExtendedTest extensions).
