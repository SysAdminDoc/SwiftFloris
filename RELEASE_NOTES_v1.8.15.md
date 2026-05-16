# SwiftFloris v1.8.15 — 2026-05-15

Fifteenth autonomous slice. **774 unit tests** at HEAD, 0 failures.

## L4.8 — Combined RTL text pipeline

New `ime/bidi/RtlTextPipeline` composes the five-step RTL stack
behind one entry point so commit-path callers don't re-derive the
correct ordering each time:

1. Hebrew Niqqud strip + Geresh/Gershayim rewrite.
2. Persian/Urdu Yeh/Kaf folding.
3. Arabic FE70-FEFC connected-form shaping.
4. Western ↔ Arabic-Indic ↔ Extended Arabic-Indic numeral conversion.

- **`RtlTextPipeline.process(input, options)`** — runs the
  pipeline. Defaults to everything off so callers must opt-in.
- **`Options(stripHebrewNiqqud, useGereshGershayim, normalisePersianUrdu, shapeArabic, numeralTarget)`**
  — per-transform toggle data class.
- **`Options.isNoOp`** — predicate the call site uses to skip the
  pipeline allocation when nothing is requested.
- **Built-in profiles**: `ARABIC_DEFAULT` (shape + Arabic-Indic
  digits), `PERSIAN_URDU_DEFAULT` (Persian/Urdu normalise + shape +
  Extended Arabic-Indic digits), `HEBREW_DEFAULT` (Niqqud strip +
  Geresh/Gershayim).
- **`NumeralTarget`** enum: `LEAVE_UNCHANGED` / `WESTERN` /
  `ARABIC_INDIC` / `EXTENDED_ARABIC_INDIC`.

8 unit tests cover `isNoOp` predicate, no-op passthrough, all three
default profiles, custom Options with only numeral conversion,
mixed-family normaliseToWestern, and empty-input passthrough.

## L7.4 — McpClient facade + registry

New `ime/mcp/McpClient` + `NoOpMcpClient` + `McpClientRegistry`
add the IME-side contract for daemon-bound MCP tool dispatch:

- **`McpClient.callTool(daemonKey, toolName, parameterJson, timeoutMillis)`**
  — single entry point for invoking a daemon-exposed tool. Returns
  a structured `McpToolCallResponse` rather than throwing; failure
  modes are visible through `McpErrorCode`.
- **`McpClient.nextCorrelationId()`** — per-process unique id
  generator so the daemon doesn't need to track correlation state.
- **`NoOpMcpClient`** — pre-bind fallback that returns
  `TOOL_NOT_FOUND` for every call and `PAYLOAD_TOO_LARGE` when the
  caller exceeds `McpBridgeContract.MAX_PAYLOAD_BYTES`. The
  NlpManager smart-compose path can call into the registry without
  knowing whether a real daemon is bound yet.
- **`McpClientRegistry.setActive(client)` / `active()`** — mirrors
  the `SmartComposeRegistry` / `InlineTranslatorRegistry` lifecycle
  so the Android-bound implementation plugs in as a drop-in
  replacement.

5 unit tests cover NoOpMcpClient `TOOL_NOT_FOUND` default,
payload-too-large rejection, strictly-increasing correlation ids,
registry initialisation, and setActive/resetForTest lifecycle.

## L5.x — Three more scripts: Caucasian Albanian + Elbasan + Vai

Total transliteration coverage from 33 to **36 scripts**:

- **Caucasian Albanian** (U+10530 block, supplementary plane) —
  4th-7th century alphabet for the Udi language family (Caucasus
  region; ancestor of modern Udi). 52 letters.
- **Elbasan** (U+10500 block, supplementary plane) — 18th-century
  Albanian alphabet used briefly for Christian liturgical texts
  before being replaced by the modern Latin-based alphabet.
- **Vai** (U+A500 block) — West African syllabary used in Liberia +
  Sierra Leone for the Vai language (Mande family). 200+ syllable
  glyphs; this table ships representative CV-syllable combinations
  while the long tail is handled by the IME's syllable-input mode.

4 unit tests cover the three new tables (first-letter / first-
syllable glyph, Caucasian Albanian `sh` digraph, Vai `pa` syllable
greedy match, sane size assertions).

## Tests

774 unit tests at HEAD (was 757 at v1.8.14), 0 failures, 0 skipped.
17 net new tests across 3 new test classes (RtlTextPipelineTest +
McpClientTest + IndicScriptExtendedTest extensions).
