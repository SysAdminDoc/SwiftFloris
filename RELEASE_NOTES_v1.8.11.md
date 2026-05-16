# SwiftFloris v1.8.11 — 2026-05-15

Eleventh autonomous slice. **708 unit tests** at HEAD, 0 failures.

## L7.1 — MCP daemon registry

New `ime/mcp/McpDaemonRegistry` adds the IME-side live snapshot of
on-device MCP daemons (the `L7` "Deskdrop-style local MCP bridge"
surface).  Sits next to the existing `McpBridgeContract` types and
mirrors the `setActive(Map)` pattern of `SmartComposeRegistry`,
`InlineTranslatorRegistry`, and `CjkInputProviderRegistry` so the
addon lifecycle stays uniform across heavy-runtime surfaces:

- **`McpDaemonRegistry.setActive(entries)`** — atomic replacement of
  the whole snapshot from the discovery pipeline (L7.2 lands in a
  follow-up).
- **`active()` / `size()` / `get(key)`** — atomic snapshot reads.
- **`listAllTools()`** — flattened view across every daemon, stable
  daemon order followed by stable tool order.
- **`findTool(toolName)`** — first-match resolver across daemons,
  returning a `ResolvedTool(daemon, tool)` pair.
- **`DaemonKey` / `DaemonEntry`** — data carriers with built-in
  invariants (blank-component rejection, protocol-version cap at
  `McpBridgeContract.SUPPORTED_PROTOCOL_VERSION`).

6 unit tests cover the empty-registry contract, single-daemon
populate-and-read, multi-daemon `findTool` walk, `listAllTools`
flattening, protocol-version cap enforcement, and `DaemonKey`
blank-component rejection.

## L4.5 — Yiddish bidi run segmenter

New `ime/bidi/YiddishBidiSegmenter` sits next to `HebrewBidiSegmenter`
with Yiddish-specific awareness:

- **`classify(codePoint)`** — same five-class scheme as Hebrew, but
  the Yiddish digraph block (U+05F0..U+05F2 — DOUBLE VAV, VAV YOD,
  DOUBLE YOD) is treated as `HEBREW` direction.
- **`isYiddishDigraph(codePoint)`** — predicate for the three Yiddish-
  only code points. Used by the autocorrect engine to route
  Yiddish-only spelling candidates.
- **`yiddishDigraphCount(text)`** — cheap pass for "should we route
  this commit through the Yiddish dictionary?" decision.
- **`segment(text)`** — produces the same `HebrewBidiSegmenter.Run`-
  shaped output as Hebrew for symmetry; mixed Yiddish + Latin still
  splits into alternating direction runs.

6 unit tests cover the digraph-classification path, the predicate
+ count helpers, mixed-script segmentation, pure-Yiddish-as-one-run,
and the empty-text edge case.

## L5.x — Three more scripts: Coptic + Georgian Mkhedruli + Glagolitic

Total transliteration coverage from 21 to **24 scripts**:

- **Coptic** (U+2C80 block) — liturgical script of the Coptic
  Orthodox Church + the only non-Greek/Cyrillic descendant of the
  Greek alphabet in Unicode. Includes the Coptic-only `sh` → ϣ,
  `F` → ϥ, `kj` → ϫ, `hh` → ϩ, `ti` → ϯ extras that have no Greek
  precursor.
- **Georgian Mkhedruli** (U+10D0 block) — the 33-letter modern
  civilian alphabet. Case-sensitive SERA-style transliteration
  (capital `T` → თ, `J` → ჟ, `S` → შ, etc.) so the Latin keyboard
  shift-state maps to the Mkhedruli distinct-letter inventory.
- **Glagolitic** (U+2C00 block) — pre-Cyrillic Slavonic script still
  used in limited Croatian + Old Church Slavonic liturgy. Lowercase
  letters; uppercase comes through the IME's existing shift-state.

5 unit tests cover the three new tables (first-letter glyph, the
Coptic-extra `sh` → ϣ, Georgian case-sensitivity, Glagolitic greedy
digraph `sh` → ⱎ, sane size assertions).

## Tests

708 unit tests at HEAD (was 692 at v1.8.10), 0 failures, 0 skipped.
16 net new tests across 3 new test classes (McpDaemonRegistryTest +
YiddishBidiSegmenterTest + IndicScriptExtendedTest extensions).
