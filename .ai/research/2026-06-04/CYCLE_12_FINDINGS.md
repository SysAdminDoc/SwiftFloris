# Cycle 12 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `8b68d3e`
  (`docs: refresh cycle 11 research queue`), described as
  `v1.8.238-1-g8b68d3e`.
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not duplicate v1.8.234 post-hotfix regression coverage. That release
  covers locale-scoped n-gram flush behavior and private-session traces; this
  cycle targets the file replacement primitive used by those flushes.
- Did not reopen the n-gram `ConcurrentHashMap` / pending-commit concurrency
  fixes from the 2026-06-02 audit summary.
- Did not duplicate R11-1. R11-1 is app startup preference loading; this cycle
  is personal n-gram persistence durability.
- Left the `totalEntryCount()` / `resetAndAwait()` race and trigram
  tab/control-character normalization audits for later cycles so this row stays
  focused on atomic file replacement.

## Local Evidence

- `PersonalBigramStore.kt:303-320` writes a `.tmp` file, attempts
  `tmp.renameTo(fileFor(localeTag))`, then deletes the destination and tries a
  second rename if the first rename fails.
- `PersonalTrigramStore.kt:305-324` uses the same fallback pattern for trigram
  TSV persistence.
- `PersonalNgramFlushIsolationTest.kt:28-50` covers locale-scoped bigram and
  trigram flush targeting.
- `PersonalNgramFlushIsolationTest.kt:53-69` inspects the flush body for
  per-locale table usage and broad reset cleanup, but it does not reject live
  destination deletion or require atomic/replace-existing file moves.
- `docs/AUDIT_2026-05-28.md:31-34` records the data-loss window: deleting the
  destination before a successful replacement can remove the last known-good
  personal n-gram file if the fallback rename also fails.

## Roadmap Changes Fed

- R12-1: Replace personal n-gram files atomically without deleting live data
  first. Implementation should use a shared safe replacement primitive or
  equivalent local helper for both bigram and trigram stores, prefer
  `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` where supported, and never
  remove the destination until the replacement is durable.

## Non-Adds

- No source fix was made in this cycle.
- No new dictionary retention, export, permission, or network behavior was
  proposed.
- No broad personal-dictionary refactor proposed. The target is the existing
  temp-file replacement path in the two n-gram stores.
