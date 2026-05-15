# SwiftFloris v1.8.7 — 2026-05-15

Seventh autonomous slice. **643 unit tests** at HEAD, 0 failures.

## Next-3.1d — KenLM `ArrayBhiksha` next-pointer decoder

New `ime/nlp/kenlm/BhikshaPointerDecoder` for the per-entry next-pointer
fields inside KenLM `TRIE` / `QUANT_TRIE` n-gram blocks. Implements:

- **Two-part decode.** Each pointer splits into a fixed-width
  `lowBitsWidth` lower half (packed head-to-tail in `lowBitsArena`)
  and an implicit upper half decoded from a monotone bitmap in
  `highBitsBitmap`. Bit position `(high + i)` is set for the `i`-th
  pointer; the decoder walks the bitmap once per lookup and recovers
  the original pointer as `(high << lowBitsWidth) | low`.
- **Symmetric `encode(LongArray, lowBitsWidth)`** companion helper
  produces a valid decoder from a sorted pointer array. Lets the
  search-arena navigator exercise the full encode→decode loop
  against synthetic fixtures without binding a real KenLM file.
- **Bounds + monotonicity checks.** Non-decreasing pointer
  precondition enforced at encode-time; constructor validates the
  arena is large enough for `entryCount × lowBitsWidth` bits.

7 unit tests: round-trip at `lowBitsWidth=8`, degenerate
`lowBitsWidth=0` (everything goes high), duplicates + long monotone
runs, randomized 200-array property test, non-monotone rejection,
out-of-range entry-index rejection, zero-entry decoder.

This is the second slice of the pure-Kotlin KenLM reader stack
(Next-3.1c probing-hash shipped in v1.8.6); together they cover the
PROBING and TRIE/QUANT_TRIE model types' navigation surface. The full
n-gram scoring path still arrives in Next-3.1b alongside the upstream
JNI bring-up.

## Next-9.4a — Pin-to-group long-press sheet + Pinned-groups palette row

Two new IME-side pieces wiring the existing `EmojiPinGroupStore`
into the emoji palette UX:

- **`PinToGroupSheetState`** — Compose-agnostic presenter for the
  bottom-sheet that opens when the user long-presses an emoji.
  Holds the emoji-being-pinned, snapshot of existing groups, new-group
  text input (capped to `MaxGroupNameLength`), and a `PinError`
  enum (`NoEmojiSelected` / `GroupNameBlank` / `TooManyGroups` /
  `GroupFull` / `AlreadyPinned`) so the UI can render targeted
  feedback. Fully unit-tested without Robolectric.
- **`PinnedGroupsPaletteRow`** — compact horizontal Compose row that
  renders each pinned group as an 8-dp-radius rectangular chip with
  name + 3-emoji preview + total count badge. Tap raises
  `onGroupTapped` for inline expansion; long-press raises
  `onGroupLongPressed` for the rename/unpin/delete sheet that lands in
  a follow-up. Backdrop radius adheres to the global no-pill rule.
- **`PinnedGroupChip.fromStoreSnapshot`** — pure converter from the
  `EmojiPinGroupStore.snapshot()` map to a render-ready chip list,
  preserving order, truncating preview to `PREVIEW_LIMIT = 3`.

11 unit tests across `PinToGroupSheetStateTest` (8) +
`PinnedGroupChipTest` (3) cover open / dismiss, pin-to-existing,
create-and-pin (with blank-name + too-many-groups + group-full +
already-pinned error paths), preview truncation, and stable ordering.

Integration into the live `EmojiPaletteView` Compose tree is the
remaining sub-slice — additive only, no behaviour change shipped yet.

## L5.x — Three more Brahmic-derived scripts (Burmese + Lao + Tibetan)

Extends transliteration coverage from 10 to **13 scripts** total.
Each table reuses `buildIndicMappings` even though the languages are
Tibeto-Burman (Burmese, Tibetan) and Tai-Kadai (Lao) rather than
strictly Indic — the Brahmic-derived structure (vowels + consonants
+ digits + anusvara + visarga) carries over cleanly:

- **Burmese / Myanmar** (U+1000 block) — native Myanmar digits
  U+1040..U+1049; aspirate-marker form for `kh` / `ch` etc.
- **Lao** (U+0E80 block) — Lao-native digits U+0ED0..U+0ED9; both
  anusvara + visarga map to U+0ECD (niggahita) which is the closest
  visual + phonetic analogue (Lao has no separately-marked visarga).
- **Tibetan / Bod-yig** (U+0F00 block) — native digits
  U+0F20..U+0F29; consonant inventory covers the Tibetan Brahmic base
  set. Syllable-final tsheg punctuation is intentionally not in the
  table; callers handle that at the segmenter layer.

5 unit tests cover the three new tables (`k` → first-consonant glyph,
digit round-trip, greedy two-letter digraph win for Tibetan `ng` → ང,
sane size assertions).

## Tests

643 unit tests at HEAD (was 620 at v1.8.6), 0 failures, 0 skipped.
23 net new tests across 4 new test classes (BhikshaPointerDecoderTest +
PinToGroupSheetStateTest + PinnedGroupChipTest + IndicScriptExtendedTest
extensions).
