# Cycle 13 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `3df1e5b`
  (`docs: refresh cycle 12 research queue`), described as
  `v1.8.246-1-g3df1e5b`.
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not duplicate R12-1. R12-1 targets the temp-file replacement primitive;
  this cycle targets read/reset interleaving in the stats count path.
- Did not duplicate v1.8.234 post-hotfix regression coverage. That release
  covers locale-scoped flush behavior, not `totalEntryCount()` serialization
  with reset cleanup.
- Did not reopen the broader personal n-gram `ConcurrentHashMap` and
  pending-commit fixes from the pushed pass-2 audit commits.
- Left the trigram tab/control-character normalization audit for a later cycle
  so this row stays focused on stats/reset consistency.

## Local Evidence

- `PersonalBigramStore.kt:224-242` builds a locale-tag set from persisted
  `personal_bigrams_*.tsv` files and `tablesByLocale.keys`, then calls
  `ensureLoaded(localeTag)` for each tag without holding `loadGuard`.
- `PersonalBigramStore.kt:367-376` clears in-memory bigram state and deletes
  `personal_bigrams_*` files under `loadGuard`.
- `PersonalTrigramStore.kt:229-245` and `PersonalTrigramStore.kt:368-375`
  repeat the same count/reset shape for persisted trigram files.
- `TypingStatsScreen.kt:137-143` displays the personal bigram and trigram
  counts in Settings, making a stale or resurrected count user-visible.
- `PersonalNgramFlushIsolationTest.kt:64-68` checks that reset is the only broad
  cleanup path, but it does not require `totalEntryCount()` to share the reset
  serialization boundary.
- `docs/AUDIT_2026-05-28.md:58-60` records the race between
  `totalEntryCount()` and `resetAndAwait()`.

## Roadmap Changes Fed

- R13-1: Serialize personal n-gram stats counting with reset cleanup. The
  implementation should make file enumeration, loaded-key collection, and
  `ensureLoaded()` run under the same reset-safe boundary in both stores or
  compute from a reset-safe snapshot, with focused coverage that fails if stats
  counting can reload a locale around reset deletion.

## Non-Adds

- No source fix was made in this cycle.
- No new dictionary retention, export, permission, or network behavior was
  proposed.
- No broad personal-dictionary or typing-stats refactor proposed. The target is
  the existing `totalEntryCount()` / `resetAndAwait()` consistency contract in
  the two personal n-gram stores.
