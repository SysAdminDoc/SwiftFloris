# Cycle 11 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `31cfa44`
  (`docs: refresh cycle 10 research queue`), described as
  `v1.8.237-1-g31cfa44`.
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not duplicate R2-1. R2-1 handles synchronous exceptions caught by
  `FlorisApplication.onCreate()` before Settings installs the splash keep
  condition; this cycle covers failures inside the launched
  `FlorisApplication.init()` coroutine after that precheck has already run.
- Did not duplicate R10-1. R10-1 scopes editor content-generation jobs to an
  active input session; this cycle scopes app preference-store initialization
  to a recoverable startup state.
- Did not propose a generic coroutine-scope refactor. The target is the
  preference-init path that gates Settings UI rendering through
  `preferenceStoreLoaded`.
- Left broader native-library logging and other low startup polish items for a
  later cycle; they do not cause the splash wait to hang.

## Local Evidence

- `FlorisApplication.kt:82` creates `CoroutineScope(Dispatchers.Default)`
  without a `SupervisorJob`.
- `FlorisApplication.kt:161-170` launches `FlorisPreferenceStore.initAndroid`
  and sets `preferenceStoreLoaded.value = true` only after successful
  initialization and logging. There is no `try/catch`, `finally`, or failure
  state in that coroutine.
- `FlorisApplication.kt:155-158` stages only synchronous exceptions thrown
  before `init()` returns.
- `FlorisAppActivity.kt:102-115` checks for already-staged startup exceptions
  before installing the splash keep condition
  `!appContext.preferenceStoreLoaded.value`.
- `FlorisAppActivity.kt:155-167` defers `setContent` until
  `preferenceStoreLoaded` becomes true.
- `FlorisAppActivity.kt:313-319` consumes only pre-existing staged exceptions,
  so an async failure after the initial check does not automatically reopen the
  crash-dialog path.
- `StartupCrashRecoveryTest.kt:50-78` covers staged exception persistence and
  redirect behavior, but it does not simulate a failing preference initializer.
- `docs/AUDIT_2026-05-28.md:127-129` records this async preference-init failure
  as distinct from the synchronous staged-startup exception issue.

## Roadmap Changes Fed

- R11-1: Guard async preference-store init failures before the splash wait.
  Implementation should make the preference-init coroutine supervised and
  error-guarded, log and route failures to a recovery surface or deliberately
  degraded startup state, and guarantee the Settings splash condition cannot
  wait forever on `preferenceStoreLoaded == false`.

## Non-Adds

- No source fix was made in this cycle.
- No new permission, storage, or network row added.
- No product decision proposed. The user-visible behavior is recovery from a
  failed startup prerequisite, not a new setting or workflow.
