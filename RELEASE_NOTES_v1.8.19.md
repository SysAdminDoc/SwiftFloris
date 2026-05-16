# SwiftFloris v1.8.19 — 2026-05-15

Nineteenth autonomous slice. **842 unit tests** at HEAD, 0 failures.

## L1.1d — Sentence-aware smart-compose context window

New `ime/smartcompose/SmartComposeContextWindow` is the helper the
NlpManager / OptInAddonDispatcher pipeline runs against
`SmartComposeContext.precedingText` before dispatching to an addon:

- **`truncate(precedingText, maxChars = 1024)`** — takes the
  trailing `maxChars` characters as a hard cap, then snaps backward
  to the nearest sentence boundary inside that window so the
  provider sees coherent grammar instead of a mid-sentence cut.
- **`truncate(context, maxChars)`** — convenience overload that
  copies a whole `SmartComposeContext` with `precedingText`
  truncated.
- **Multi-script terminator support** — same set as
  `SentenceTokenizer` (`.` / `!` / `?` + Arabic / Devanagari / CJK /
  Ethiopic).
- **Hard-cap fallback** — when no terminator exists in the trailing
  window the helper returns the raw `maxChars`-character substring
  so the model still sees coherent UTF-8.
- **`maxChars` minimum 16** — anything smaller has no realistic
  sentence to feed.

Default cap 1,024 chars (≈200-250 English tokens). Larger context
windows are wasted IPC + cache pressure + a privacy footprint that
grows unboundedly with editor scrollback.

7 unit tests cover unchanged-passthrough, boundary-snap, no-
boundary-hard-cap fallback, convenience overload behaviour,
multi-script terminator support, and the `maxChars`-≥-16
invariant.

## L7.5 — MCP timeout budget breaker

New `ime/mcp/McpTimeoutClient` is a sliding-window budget breaker
wrapping any `McpClient`. Prevents a misbehaving (slow-but-not-
hung) tool from degrading typing performance:

- **`budgetMillis` / `windowMillis`** — default 10 s of cumulative
  dispatch time per 60 s sliding window. Once exhausted, calls
  short-circuit with `errorCode = TIMEOUT` until the window rolls
  forward.
- **Injectable `Clock` interface** — `Clock.System` for production
  (`System.currentTimeMillis()`); tests inject a deterministic
  `Clock` that advances on demand.
- **`totalDispatchMillis` + `breakerTrips`** counters — diagnostics
  for the future telemetry dashboard.
- **`budgetMillis < windowMillis` invariant** — enforced at ctor
  so a misconfigured breaker that never trips can't ship.

5 unit tests cover pass-through-within-budget, budget-exhausted-
trips, window-rollover-resets, cumulative `totalDispatchMillis`
accounting across rollovers, and the budget-vs-window invariant.

## L5.x — Three more Philippine Brahmic scripts (completing the Hanunoo family)

Total transliteration coverage from 45 to **48 scripts**:

- **Tagbanwa** (U+1760 block) — still in active use by the
  Tagbanwa people of Palawan, Philippines.
- **Buhid** (U+1740 block) — second of the four Philippine Brahmic
  scripts, still used by the Buhid Mangyan people of Mindoro.
- **Baybayin / Tagalog** (U+1700 block) — historical script of the
  Tagalog language (pre-Spanish-colonial Philippines), undergoing
  cultural revival in modern Philippines.

Together with Hanunoo (shipped in v1.8.17) this completes the four
Brahmic-derived Philippine scripts on the Indic family ROADMAP.

4 unit tests cover the three new tables (first-letter glyph,
`nga` CV-syllable greedy match for Tagbanwa + Baybayin, `ka` for
Buhid, sane size assertions).

## Tests

842 unit tests at HEAD (was 826 at v1.8.18), 0 failures, 0 skipped.
16 net new tests across 3 new test classes (SmartComposeContextWindowTest +
McpTimeoutClientTest + IndicScriptExtendedTest extensions).
