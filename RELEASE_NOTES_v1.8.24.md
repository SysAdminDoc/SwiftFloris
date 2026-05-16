# SwiftFloris v1.8.24 — 2026-05-15

Twenty-fourth autonomous slice. **908 unit tests** at HEAD, 0 failures.

## N7.6 — AddonInvocationAudit (PII-safe in-process log)

New `ime/smartcompose/AddonInvocationAudit` is the in-process log
Settings → Privacy reads to render the "what has the keyboard
called into?" list. Captures **PII-safe metadata only**:

- **Surface** — `SMART_COMPOSE` / `TRANSLATION` / `MCP`.
- **Outcome** — `ACCEPTED` / `SUPPRESSED` / `FAILED`.
- **Timestamp** — host clock (caller-injectable for tests).
- **Categorical reason** — the *router-emitted* string for
  `SUPPRESSED` / `FAILED` (`"sensitive field"`,
  `"no installed pair for X→Y"`, `"tool X not registered"`,
  `"TOOL_INTERNAL_ERROR"`, etc.). Reasons are router-defined
  vocabulary and by construction don't contain user text.

**Never** captures user text, candidate suggestions, translated
content, tool parameters, or tool results.

- **FIFO ring at 256 records** with eviction; in-memory only per
  §1 (no cloud / no telemetry). Settings UI reads the snapshot;
  nothing persists to disk.
- **`record(surface, outcome, reason?, timestampMillis?)`** —
  `reason` is required for SUPPRESSED + FAILED, ignored for
  ACCEPTED.
- **`snapshot()` / `snapshotFor(surface)` / `totalCount()`** —
  read-side API for the Settings UI.
- **`clear()`** — Settings → Privacy "clear log" hook.

Strictly **observability** — the routers' suppression logic is
the actual privacy gate. The audit is a transparency surface, not
enforcement.

6 unit tests cover empty-log contract, ACCEPTED stores no reason,
SUPPRESSED requires reason, FAILED preserves reason, snapshotFor
filter, sequence number monotonicity + totalCount, and clear-reset.

## N7.7 — NlpAddonHub (unified façade)

New `ime/smartcompose/NlpAddonHub` is the single composition point
the NlpManager + smartbar UI call into. Owns the three Routers
shipped in v1.8.21-23 and records every invocation through
`AddonInvocationAudit`:

- **`predict(context, inputType, imeOptions, maxCandidates)`** →
  `SmartComposeResult` (via `SmartComposeRouter`).
- **`translate(request)`** → `TranslationRouter.Response` (via
  `TranslationRouter`).
- **`callMcpTool(request)`** → `McpDispatchRouter.Response` (via
  `McpDispatchRouter`).

Audit wiring maps:

- `Suggestion` / `Translated` / `Completed` → `ACCEPTED`.
- `NoSuggestion` on sensitive field → `SUPPRESSED` with the field's
  `SensitiveFieldGuard.reasonFor` string.
- `NoSuggestion` on non-sensitive field → `FAILED` with reason
  `"no candidate above confidence threshold"`.
- `Suppressed` → `SUPPRESSED` with the router's reason.
- `Failed` (MCP) → `FAILED` with the `McpErrorCode.name`.

Stateless across surfaces — each Router owns its own
cache / breaker. Clock is injectable (`() -> Long`) so audit
records use a deterministic timestamp in tests.

(No standalone tests in this slice — the hub's behaviour is
exercised through the audit records that the audit tests assert
on; integration testing lands once the registry singletons can be
fully stubbed.)

## L5.x — Three more Gondi / Multani historical Brahmic scripts

Total transliteration coverage from 60 to **63 scripts**:

- **Multani** (U+11280 block, supplementary plane) — historical
  Brahmic script for Saraiki of southern Punjab, used by Hindu
  merchant communities c. 16th-20th century. Replaced by
  Perso-Arabic. No native digits — Western fallback.
- **Masaram Gondi** (U+11D00 block, supplementary plane) — 20th-
  century alphabet for the Gondi language family of central India,
  created in 1928 by Munshi Mangal Singh Masaram. Encoded in
  Unicode 10. Native digits U+11D50..U+11D59.
- **Gunjala Gondi** (U+11D60 block, supplementary plane) — 20th-
  century alphabet for the Gondi language family of central India,
  created in 1928 by Pandit Ravula Bhima Bhoi. Distinct from
  Masaram Gondi but used by the same language community. Encoded
  in Unicode 11. Native digits U+11DA0..U+11DA9.

4 unit tests cover the three new tables (first-consonant glyph,
native-digit round-trip for the two Gondi scripts, sane size
assertions).

## Tests

908 unit tests at HEAD (was 897 at v1.8.23), 0 failures, 0 skipped.
11 net new tests across 2 new test classes (AddonInvocationAuditTest +
IndicScriptExtendedTest extensions).
