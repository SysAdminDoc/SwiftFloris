# SwiftFloris v1.8.4 — 2026-05-15

Fourth autonomous slice. Ships **L9.2 honeycomb tessellation**,
**Next-3.1b KenLM vocabulary parser**, **L4.2 Nastaliq font scaffold**,
and the **P3-renderer split-row post-pass** ready for the
TextKeyboardLayout integration. **596 unit tests** at HEAD, 0
failures.

## L9.2 — Honeycomb hex tessellation

- New `assets/ime/keyboard/.../characters/honeycomb.json` ships a
  Typewise-style 5-row flat-top hexagonal letter layout.
- New `ime/text/keyboard/HoneycombTessellation` provides the geometry
  math the renderer + hit-tester will consume:
  - `keyRadius`-driven row stride (1.5 · r) and column stride (√3 · r).
  - Even-row half-offset alternation (the hex tessellation pattern).
  - `centerOf(row, col)` for layout.
  - `cellAt(px, py)` for touch hit-testing — brute-force across all
    cells (cheap on ≤ 40-key layouts).
  - `containsPoint` uses the two-trapezoid flat-top hex
    point-in-shape test.
- 8 unit tests pin the geometry contract.

## Next-3.1b — KenLM vocabulary string-arena parser

- New `ime/nlp/kenlm/KenLmVocabulary` reads the post-search-arena
  vocabulary block of a KenLM binary: `uint64 string_count` +
  `uint64 strings_byte_length` + concatenated `\0`-terminated UTF-8
  token bytes.
- `indexOf(token)` returns the vocabulary index or `UNK_INDEX = 0`
  when out-of-vocab.
- `contains(token)` distinguishes a known word from the `<unk>`
  sentinel.
- Hard caps: 8M tokens, 256 MB string arena — larger models move
  to the dictionary-pack addon path (`docs/addons/dictionary-pack-spec.md`).
- 7 unit tests cover happy path, malformed-input rejection, CJK
  UTF-8 round-trip, advertised-count-vs-actual-bytes mismatch.

## L4.2 — Nastaliq font scaffold

- New `ime/bidi/NastaliqFontProvider.bundledTypeface(context)` lazily
  loads Noto Nastaliq Urdu from
  `assets/fonts/NotoNastaliqUrdu-Regular.ttf` via
  `Typeface.createFromAsset`. Falls back to `Typeface.DEFAULT` when
  the asset is missing — IME still renders, Urdu just falls back to
  Naskh.
- `isAvailable(context)` predicate lets Snygg theme selectors skip
  the font-family override when the binary isn't present.
- New `docs/FONTS.md` documents the OFL-1.1 attribution + the CI
  download step (font binary stays out of git per ~480 KB binary-diff
  policy).

## P3-renderer — Split-keyboard row post-pass

- New `ime/text/keyboard/SplitGutterPostPass.apply(keyboard, gutterPx)`
  walks every row of a positioned `TextKeyboard` and shifts the
  right half of each row by `gutterPx` pixels.
- The gutter point per row comes from
  `SplitKeyboardLayoutCalculator.qwertyBoundary` (5+5 / 5+4 / 4+3
  for canonical QWERTY; `halfAndHalf` for other row sizes).
- Updates `touchBounds` + `visibleBounds` in lockstep so the
  renderer + hit-tester stay aligned.
- 9 unit tests cover canonical QWERTY 3-row split, non-canonical
  fallback, empty-row defensive path, zero-gutter no-op,
  negative-gutter rejection, multi-row consistency, and a
  `SplitRowSnapshot` helper that pins the post-shift gutter measure.
- The final `TextKeyboardLayout`-side call to this post-pass — read
  the active window mode + invoke `apply(keyboard, gutterPx)` after
  the existing `keyboard.layout(...)` — is the next slice. The math
  is pinned; the integration is a one-line addition once the
  call-site lands.

## Tests

596 unit tests at HEAD (was 572 in v1.8.3), 0 failures, 0 skipped.
24 net new tests across 4 new test classes.
