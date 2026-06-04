# Cycle 14 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `857cfe0`
  (`docs: refresh cycle 13 research queue`), described as
  `v1.8.246-2-g857cfe0`.
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not duplicate R12-1. R12-1 targets safe file replacement after a flush;
  this cycle targets the token-safety precondition before flush writes TSV.
- Did not duplicate R13-1. R13-1 targets stats/reset serialization; this cycle
  targets write-time rejection of tokens that cannot be represented in the TSV
  format.
- Did not reopen v1.8.234 locale-scoped flush regression coverage. That release
  covers which locale is flushed, not whether token strings are TSV-safe.
- Did not propose an escaping or migration layer. Rejection is the narrower
  implementation shape for this app-private learned-token format.

## Local Evidence

- `PersonalBigramStore.kt:82-88` and `PersonalTrigramStore.kt:86-92` normalize
  learned words by trimming edge punctuation, rejecting digits, and requiring a
  letter, but they do not reject interior tab, newline, carriage-return, NUL, or
  other ISO control characters.
- `PersonalBigramStore.kt:101-111` and `PersonalTrigramStore.kt:107-119` reload
  persisted rows with `split('\t')`, so interior tabs change field counts.
- `PersonalBigramStore.kt:303-315` and `PersonalTrigramStore.kt:305-319` write
  raw token strings separated by tabs and terminated with newlines.
- `PersonalTrigramStore.kt:51` reserves `\u0000` as the in-memory context
  delimiter, so a committed NUL inside `prev2` or `prev1` can collide with
  context splitting.
- Existing dictionary source tests cover locale-scoped flush/reset contracts,
  but not control-character rejection before persistence.
- `docs/AUDIT_2026-05-28.md:66-68` records the tab/NUL corruption path.

## Roadmap Changes Fed

- R14-1: Reject control separators before personal n-gram TSV persistence. The
  implementation should reject tab, newline, carriage-return, NUL, and other
  ISO control characters in both bigram and trigram normalized tokens before the
  tokens can reach in-memory maps or flush rows, with focused coverage that
  fails if a token can alter TSV field counts or trigram context splitting.

## Non-Adds

- No source fix was made in this cycle.
- No new dictionary retention, export, permission, or network behavior was
  proposed.
- No TSV escaping/migration work proposed. Rejection is enough for the current
  app-private learned-token persistence format.
