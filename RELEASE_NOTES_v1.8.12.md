# SwiftFloris v1.8.12 — 2026-05-15

Twelfth autonomous slice. **728 unit tests** at HEAD, 0 failures.

## L7.2 — MCP daemon discovery pipeline

New `ime/mcp/McpDaemonDiscoverer` is the pure-Kotlin core of the
PackageManager → registry pipeline:

- **`discover(candidates: List<DiscoveryCandidate>)`** — produces the
  `Map<DaemonKey, DaemonEntry>` the registry's `setActive(Map)` wants.
  Preserves insertion order.
- **`DiscoveryCandidate(packageName, daemonClassName, protocolVersion, hasBindPermission, toolCatalogJson)`**
  — data carrier the Android-side shim builds from a `ResolveInfo` +
  `ServiceInfo` lookup. Lets the discovery logic stay testable with
  pure-JVM fixtures.
- **Silent-drop validation** — malformed candidates don't fail the
  whole scan. A daemon missing the BIND permission, declaring a
  protocol version above `SUPPORTED_PROTOCOL_VERSION`, shipping
  malformed JSON, or exposing an empty tools array gets dropped on
  the floor. Surviving daemons populate the registry.
- **Partial-catalog tolerance** — tool entries missing
  `description` or `parameterSchema` fall back to safe placeholders
  (`"(no description provided)"` + `{"type":"object"}`).
- **Payload cap** — JSON catalog larger than
  `McpBridgeContract.MAX_PAYLOAD_BYTES` (4 MB) is rejected as a
  runaway-tool guard.

8 unit tests cover happy path, missing-permission rejection,
protocol-version overflow rejection, malformed-JSON rejection,
empty-tools-array rejection, blank-name-tool skip-but-keep-rest,
safe-placeholder-supply for missing optional fields, and
insertion-order preservation.

## L4.6 — Arabic / Persian / Urdu digit conversion

New `ime/bidi/ArabicPersianNumeralConverter` handles all three
Unicode digit families needed for Arabic-locale typography:

- **`westernToArabicIndic(text)`** / **`arabicIndicToWestern(text)`**
  — `0..9` ↔ `٠..٩` (U+0660..U+0669) for Saudi / Egyptian / Levantine
  Arabic locales.
- **`westernToExtendedArabicIndic(text)`** /
  **`extendedArabicIndicToWestern(text)`** — `0..9` ↔ `۰..۹`
  (U+06F0..U+06F9) for Persian / Urdu / Pashto.
- **`normaliseToWestern(text)`** — collapses every digit family to
  Western form so the autocorrect feed doesn't see three flavours of
  the same digit semantically.
- **`isAnyDigit(codePoint)`** — predicate covering all three
  families.

7 unit tests cover the four pairwise converters, the all-families
normalise, non-digit passthrough, the predicate, and the empty-text
edge case.

## L5.x — Three more scripts: Samaritan + Mandaic + Old Permic

Total transliteration coverage from 24 to **27 scripts**:

- **Samaritan** (U+0800 block, RTL) — descendant of Paleo-Hebrew
  used by the Samaritan community for liturgical Hebrew.
- **Mandaic** (U+0840 block, RTL) — Mandaean liturgical script,
  historically used in southern Iraq + Iran.
- **Old Permic** (U+10350 block, supplementary plane) — 14th-century
  clergy alphabet for the Komi (Permic) language family. Modelled on
  Greek with ligature-style additions. Surrogate-pair handling reuses
  the digit-iteration fix from v1.8.10.

4 unit tests cover the three new tables (first-letter glyph, the
`sh` digraph greedy match for Samaritan + Mandaic, Old Permic
supplementary-plane `dz` round-trip, sane size assertions).

## Tests

728 unit tests at HEAD (was 708 at v1.8.11), 0 failures, 0 skipped.
20 net new tests across 3 new test classes (McpDaemonDiscovererTest +
ArabicPersianNumeralConverterTest + IndicScriptExtendedTest extensions).
