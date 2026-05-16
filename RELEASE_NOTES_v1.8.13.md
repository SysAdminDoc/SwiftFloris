# SwiftFloris v1.8.13 — 2026-05-15

Thirteenth autonomous slice. **746 unit tests** at HEAD, 0 failures.

## L7.3 — MCP tool-call envelope

New `ime/mcp/McpToolCallEnvelope` adds the wire-format types the
IME's `McpClient` uses to invoke daemon-exposed tools across the
AIDL bind (transport lands in L7.4):

- **`McpToolCallRequest(correlationId, toolName, parameterJson)`** —
  one async tool invocation. The correlation id lets responses
  arriving over a shared bind match back to their request.
- **`McpToolCallResponse(correlationId, toolName, payloadJson?,
  errorMessage?, errorCode)`** — either success (payloadJson
  populated, `errorCode = OK`) or failure (errorMessage populated,
  `errorCode != OK`). Built-in invariants reject both states.
- **`McpErrorCode`** — stable wire-value enum: `OK(0)`,
  `TOOL_NOT_FOUND(1)`, `INVALID_PARAMETERS(2)`,
  `TOOL_INTERNAL_ERROR(3)`, `TIMEOUT(4)`, `PAYLOAD_TOO_LARGE(5)`,
  `PERMISSION_DENIED(6)`, `UNKNOWN(99)`.  Numeric values stay stable
  across protocol versions — only append, never renumber.
- **`McpEnvelopeCodec`** — JSON round-trip helper using the same
  `kotlinx.serialization.json` codec the rest of the MCP stack uses.

Payload cap mirrors `McpBridgeContract.MAX_PAYLOAD_BYTES` (4 MB) and
is enforced in the request constructor so a runaway prompt never
reaches the daemon.

7 unit tests cover request round-trip, success-response round-trip,
error-response round-trip with `isError` flip, success-response
requires-payload invariant, error-response requires-error-message
invariant, blank-correlation-id rejection, and stable error-code
wire values.

## Next-3.1i — KenLM LRU score cache

New `ime/nlp/kenlm/KenLmScoreCache` wraps any `KenLmScorer` with
an access-order LRU cache:

- **`LinkedHashMap(accessOrder = true)`** with `removeEldestEntry`
  override evicts at `capacity` (default 4,096).
- **Thread-safe** via a single intrinsic lock — cache hits + misses
  are atomic from the caller's perspective.
- **Unit-separator-keyed lookup** — `(history, tail)` builds a cache
  key with `\u001F` joining history tokens and `\u001E` between
  history and tail, so `the cat | sat` and `the | cat sat` produce
  different cache entries (correctness over slight key bloat).
- **`hits` + `misses` counters** — exposed for diagnostics and for
  the eventual ranker observability dashboard.
- **`clear()`** drops everything when the underlying model swaps.

`KenLmScorer` is now a regular `interface` rather than a
`sealed interface` so tests can build a `CountingScorer` fixture
without living in the main source set. Existing
`KenLmModelTypeDispatch.build()` callers are unaffected.

8 unit tests cover repeat-lookup cache hit, distinct-tuple cache
miss, eviction at capacity, `clear()` reset, unit-separator key
disambiguation, capacity-≥-1 invariant, and delegate `modelType` /
`maxOrder` pass-through.

## L5.x — Three more ancient scripts: Phoenician + Imperial Aramaic + Avestan

Total transliteration coverage from 27 to **30 scripts**:

- **Phoenician** (U+10900 block, supplementary plane, RTL) — the
  22-letter consonantal alphabet that is the parent of every
  Western alphabet (Aramaic / Greek / Latin / Hebrew / Arabic /
  Cyrillic descend from it). No vowels — pure abjad.
- **Imperial Aramaic** (U+10840 block, supplementary plane, RTL) —
  the state script of the Achaemenid Empire and the lineal
  ancestor of Square Hebrew, Syriac, Arabic, and Mongolian. 22
  consonants matching Phoenician one-for-one.
- **Avestan** (U+10B00 block, supplementary plane, RTL) — the
  liturgical script of Zoroastrianism, used for Old / Middle
  Iranian Avestan texts (the Yasna). True alphabet — vowels and
  consonants both have dedicated characters, unlike the abjad
  parent scripts.

4 unit tests cover the three new tables (first-letter glyph, the
`sh` digraph greedy match across all three, Avestan aspirated `kh`
round-trip, sane size assertions).

## Tests

746 unit tests at HEAD (was 728 at v1.8.12), 0 failures, 0 skipped.
18 net new tests across 3 new test classes (McpToolCallEnvelopeTest +
KenLmScoreCacheTest + IndicScriptExtendedTest extensions).
