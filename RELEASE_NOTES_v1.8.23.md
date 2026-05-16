# SwiftFloris v1.8.23 — 2026-05-15

Twenty-third autonomous slice. **897 unit tests** at HEAD, 0 failures.

## L7.6 — McpDispatchRouter (end-to-end composition)

New `ime/mcp/McpDispatchRouter` is the third sibling of
`SmartComposeRouter` (v1.8.21) + `TranslationRouter` (v1.8.22). All
three Router types now follow the same shape: a single
`dispatch(Request)` → `Response` entry point, structured response
with categorised failure reasons, `View` interfaces for test
injection over the singletons.

Pipeline:

  1. `SensitiveFieldGuard` — short-circuit on password / PIN /
     no-learn fields.
  2. `RegistryView.findTool(toolName)` — resolve to
     `(daemon, tool)` across active daemons. Production view backs
     onto `McpDaemonRegistry.findTool`; tests inject fakes.
  3. Payload-size cap — refuses `parameterJson` over
     `McpBridgeContract.MAX_PAYLOAD_BYTES`.
  4. Underlying `McpClient` — usually `McpClientRegistry.active`,
     itself often wrapped by `McpTimeoutClient` (v1.8.19) in
     production.

API:

- **`Request(toolName, parameterJson, inputType, imeOptions, timeoutMillis)`** — input.
- **`Response.Completed(callResponse, daemon)`** — successful call
  with the daemon that handled it.
- **`Response.Failed(callResponse)`** — call reached a daemon but
  the daemon returned an error code.
- **`Response.Suppressed(reason)`** — categorised refusal:
  `"sensitive field"`, `"blank tool name"`, `"parameterJson exceeds
  MAX_PAYLOAD_BYTES"`, `"tool X not registered"`.

**No internal cache** — MCP tool calls are by definition side-
effecting (calendar lookups, contact searches, clipboard manipulation)
so caching would be semantically wrong. The analogous "don't run
forever" guard is the underlying `McpTimeoutClient` wrap, applied
once at registry bind.

7 unit tests cover password-field suppression, blank-tool-name
rejection, missing-from-registry rejection, oversized-parameterJson
rejection, happy-path Completed response, delegate-error → Failed
wrapping, and `IME_FLAG_NO_PERSONALIZED_LEARNING` suppression.

## L5.x — Three more historical Brahmic scripts

Total transliteration coverage from 57 to **60 scripts**:

- **Kaithi** (U+11080 block, supplementary plane) — historical
  script for Bhojpuri / Magahi / Maithili / Awadhi / Bagheli in
  north-central India c. 16th-20th century. Replaced by
  Devanagari.
- **Mahajani** (U+11150 block, supplementary plane) — historical
  script used by north-Indian merchant communities for account-
  keeping + commercial correspondence c. 19th-20th century.
  Replaced by Devanagari. No native digits in current Unicode —
  Western fallback.
- **Khojki** (U+11200 block, supplementary plane) — historical
  script of the Khoja Muslim community of Sindh + Gujarat, used
  for Sindhi + Gujarati religious literature c. 16th-20th century.

4 unit tests cover the three new tables (first-consonant glyph,
Mahajani-Western-digit-fallback round-trip, sane size assertions).

## Tests

897 unit tests at HEAD (was 886 at v1.8.22), 0 failures, 0 skipped.
11 net new tests across 2 new test classes (McpDispatchRouterTest +
IndicScriptExtendedTest extensions).
