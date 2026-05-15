# SwiftFloris v1.8.8 — 2026-05-15

Eighth autonomous slice. **658 unit tests** at HEAD, 0 failures.

## Next-3.1e — KenLM `SeparatelyQuantize` codec

New `ime/nlp/kenlm/KenLmQuantTable` + `KenLmQuantTableSet` for the
quantized log-probability and log-backoff codebooks that `QUANT_TRIE`
model files use to keep n-gram payloads compact. Implements:

- **`KenLmQuantTable.withBackoff(probBits, backoffBits, prob, backoff)`**
  — non-highest-order tables carry both prob + backoff codebooks
  (sizes `2^probBits` and `2^backoffBits`).
- **`KenLmQuantTable.highestOrder(probBits, prob)`** — highest-order
  tables carry only the prob codebook; `decodeBackoff()` throws when
  called on this variant, matching upstream KenLM semantics.
- **`KenLmQuantTableSet(order, tables)`** — 1-indexed `tableFor(k)`
  accessor; constructor enforces "exactly one highest-order table at
  the end" + "every non-highest order has a backoff codebook".
- **`parseTableSet(ByteBuffer, order, probBits, backoffBits)`** —
  reads the on-disk centroid block immediately past
  `KenLmBinaryHeader` for QUANT_TRIE files. Little-endian float32
  centroids in the exact layout `lm/quantize.hh::SetupMemory`
  produces (prob block then backoff block per order, no backoff
  block at the highest order).

6 unit tests cover round-trip for both variants, size-mismatch
rejection, table-set 1-indexing, highest-order-no-backoff invariant
violation, and the parse path against a hand-built little-endian
buffer.

## Next-3.1f — KenLM PROBING-model search-arena navigator

New `ime/nlp/kenlm/KenLmProbingNavigator` joins the three pure-Kotlin
readers (`KenLmVocabulary`, `KenLmProbingHash`, `KenLmBinaryHeader`)
into a single API the IME can drive without knowing the on-disk
layout:

- **`lookup(history, tail)`** — walks orders from longest matching
  context down to unigram, returning the matching `ProbingEntry`. If
  even the unigram is missing it falls back to the `<unk>` slot.
- **`score(history, tail)`** — returns the log-probability under the
  standard KenLM backoff chain: `logProb(matched_order) +
  Σ logBackoff(parent_context_of_skipped_order)`. Returns
  `Float.NEGATIVE_INFINITY` when neither the n-gram nor its tail
  unigram is in the model.
- **Parent-entry recursion** — internal `parentEntryIndexFor` walks
  the context chain order-by-order so the navigator works for an
  arbitrary `maxOrder`, not just bigrams.
- **Backoff accumulation** — internal `sumSkippedBackoffs` adds the
  log-backoff weights of every parent context whose order we'd have
  preferred to match at but couldn't.

5 unit tests build synthetic vocabularies + populate probing-hash
buckets using a shared `buildProbingHash(bucketCount, entries)`
fixture helper (which probes through the same MurmurHash64A the
production reader uses). Tests cover the bigram-hit path, the
fall-back-to-unigram-with-parent-backoff path, the unknown-token
collapse to `<unk>`, the no-entry returns `NEGATIVE_INFINITY` path,
and the order-1-missing-from-config rejection.

This is the pure-Kotlin scoring path for KenLM PROBING models; the
TRIE / QUANT_TRIE variant gets a sibling navigator once Next-3.1d's
Bhiksha decoder and Next-3.1e's quant codec are wired into a
trie-walking facade.

## L5.x — Two more Brahmic-derived scripts (Khmer + Thai)

Extends transliteration coverage from 13 to **15 scripts** total:

- **Khmer / Cambodian** (U+1780 block) — Brahmic-derived with Pali /
  Sanskrit liturgical pedigree; native Khmer digits U+17E0..U+17E9;
  visarga maps to the Khmer reah-muk (U+17C7); anusvara maps to the
  niggahita (U+17C6).
- **Thai** (U+0E00 block) — sister-script to the Lao table shipped
  in v1.8.6/1.8.7; native Thai digits U+0E50..U+0E59; tone-marker
  conventions are intentionally caller-handled, not in the table.
  Anusvara + visarga collapse to the Thai niggahita (U+0E4D).

4 unit tests cover the two new tables (digit + first-consonant
round-trips, Khmer two-letter `ng` digraph greedy match, sane size
assertions).

## Tests

658 unit tests at HEAD (was 643 at v1.8.7), 0 failures, 0 skipped.
15 net new tests across 3 new test classes (KenLmQuantTableTest +
KenLmProbingNavigatorTest + IndicScriptExtendedTest extensions).
