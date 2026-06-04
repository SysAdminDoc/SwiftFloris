# Cycle 15 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `2b300a4`
  (`docs: refresh cycle 14 research queue`), described as
  `v1.8.246-3-g2b300a4`.
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not reopen ZipUtils NUL/space findings. `docs/AUDIT_2026-05-29.md:101-106`
  marks those as verified-clean, and current `ZipUtils.kt:47-56` contains the
  NUL guard with no space rejection.
- Did not add an MCP daemon tool-name row in this cycle; the current focus is
  keyboard-layout diagnostics.
- Did not change the honeycomb parser's fail-safe design. The row preserves
  empty-list degradation and adds only diagnostic visibility.
- Did not duplicate visual/manual honeycomb follow-ups already tracked under
  broader keyboard surface and Roborazzi/device-proof rows.

## Local Evidence

- `HoneycombLayoutLoader.kt:39-43` documents the malformed-input policy: tolerate
  malformed input by returning an empty list rather than throwing.
- `HoneycombLayoutLoader.kt:59-79` wraps the parse in `try/catch`, catches
  `Exception`, and returns `emptyList()` without logging.
- `HoneycombLayoutLoaderTest.kt:152-156` verifies malformed JSON returns an
  empty list, but does not assert diagnostic visibility.
- `ZipfFrequencyTable.kt:109-117` is a nearby parser/degradation precedent that
  logs parse failures with `flogError` before returning a default result.
- `docs/AUDIT_2026-05-28.md:72-74` records the user-visible silent
  empty-keyboard diagnostic gap.

## Roadmap Changes Fed

- R15-1: Log Honeycomb layout parse failures before fail-safe empty layout. The
  implementation should keep malformed layouts non-fatal while emitting an
  actionable project-log diagnostic and adding focused coverage that fails if
  the catch path silently returns `emptyList()` again.

## Non-Adds

- No source fix was made in this cycle.
- No new parser strictness, crash behavior, permission, or network behavior was
  proposed.
- No visual baseline or device-proof expansion proposed; those stay under the
  existing keyboard-surface and Roborazzi/device-gated roadmap items.
