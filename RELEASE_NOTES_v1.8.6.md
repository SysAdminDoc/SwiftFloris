# SwiftFloris v1.8.6 — 2026-05-15

Sixth autonomous slice. **620 unit tests** at HEAD, 0 failures.

## Next-3.1c — KenLM probing-hash search arena

New `ime/nlp/kenlm/KenLmProbingHash` reader for the KenLM PROBING /
REST_PROBING model types. Implements:

- 16-byte bucket layout (`uint64 key + float prob + float backoff`).
- `EMPTY_KEY = 0xFFFF_FFFF_FFFF_FFFF` sentinel.
- Linear-probing collision resolution with `MAX_PROBE_DEPTH = 256`.
- `MurmurHash64A` implementation matching `util/murmur_hash.cc`.
- `packKey(tailVocabIndex, parentEntryIndex)` per the canonical
  KenLM key shape: `(tail << 32) | parent`.
- Per-bucket reads use position save/restore for thread safety.

6 unit tests pin the contract: pack/unpack round-trip, empty-key
lookup, hit lookup, miss lookup, linear-probe collision chain,
deterministic MurmurHash64A. Per-n-gram score traversal across
the order-by-order tables is the next slice (Next-3.1d).

## L4.3 — Hebrew Niqqud normalizer

New `ime/bidi/HebrewNiqqudNormalizer`:

- `normalize(text, stripNiqqud, useGereshGershayim)` — strips Niqqud
  + cantillation marks in U+0591..U+05C7 when the toggle is on; rewrites
  ASCII `'` / `"` to U+05F3 Geresh / U+05F4 Gershayim when the
  Hebrew-abbreviation rule is enabled.
- `isNiqqud(char)` cheap predicate for the IME's "Strip Niqqud" toggle.
- `niqqudCount(text)` lets the toggle decide whether to bother
  running the full pass on commit.

7 unit tests cover happy path, ASCII pass-through, edge values of
the Niqqud range, the count predicate, and the Geresh/Gershayim
substitution.

## L5.x — Three more Indic scripts (Malayalam + Odia + Sinhala)

Extends the Indic-transliteration coverage from 7 to **10 scripts**.
Each table follows the `buildIndicMappings` shape (vowels +
consonants + digits + anusvara + visarga + danda):

- **Malayalam** (U+0D00 block) — includes the `L → ള` mapping that's
  the precursor for chillu-aware composition.
- **Odia** (U+0B00 block) — formerly known as Oriya in older Unicode
  literature.
- **Sinhala** (U+0D80 block) — uses Western-Arabic digit fallback per
  current Unicode (no script-native digit code points yet).

6 unit tests across the three tables.

## Tests

620 unit tests at HEAD (was 601 at v1.8.5), 0 failures, 0 skipped.
19 net new tests across 3 new test classes (KenLmProbingHashTest +
HebrewNiqqudNormalizerTest + IndicScriptExtendedTest).
