# Cycle 9 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `c566b73`
  (`docs: refresh cycle 8 research queue`), described as
  `v1.8.230-1-gc566b73`.
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not duplicate R7-1. R7-1 is window capture policy for the dynamic
  incognito toggle; this cycle is request-scoped suggestion privacy.
- Did not re-open the existing incognito policy tests. `SuggestionPrivacyPolicy`
  already covers app-declared no-personalized-learning, fixed/dynamic
  incognito, committed-word learning, and touch-decoder recording decisions.
- Did not duplicate R3-3. Sealed-box vector/schema coverage shipped in
  v1.8.230 and is unrelated to local candidate-generation privacy.
- Did not duplicate R5-1. Explicit addon trust shipped in v1.8.229.
- Left the async editor content-generation cancellation audit for a later cycle:
  R6-1 already covers batch-edit critical sections, while this cycle focuses
  only on NLP request privacy inputs.

## Local Evidence

- `NlpManager.kt:211-214` captures `content`, `subtype`, and a `requestId`
  before launching async suggestion work.
- `NlpManager.kt:216-244` reads live suggestion/emoji prefs and
  `keyboardManager.activeState.isIncognitoMode` inside the launched coroutine
  before invoking emoji and word providers.
- `NlpManager.kt:277-286` records typing-trace evidence using the live
  incognito flag from `keyboardManager.activeState`.
- `NlpManager.kt:295` calls `buildGhostTextCandidate(...)` from inside the
  coroutine, and `NlpManager.kt:322-324` lets that path read
  `editorInstance.activeInfo` after the async boundary to decide whether the
  field is sensitive.
- `NlpManager.kt:299-306` guards final candidate publication by request id, but
  only after provider calls and typing-trace recording have already run.
- `EditorInstance.kt:151-155` resolves `activeState.isIncognitoMode` from the
  current field's `IME_FLAG_NO_PERSONALIZED_LEARNING`, incognito preference,
  and dynamic-incognito preference.
- `KeyboardManager.kt:739-741` mutates the same live
  `activeState.isIncognitoMode` for the smartbar dynamic-incognito toggle.
- `SuggestionPrivacyPolicyTest.kt:24-113` covers policy decisions, but there is
  no request-boundary test proving `NlpManager.suggest` passes an immutable
  privacy snapshot through delayed providers, typing traces, and ghost-text
  gating.
- `docs/THREAT_MODEL.md:30-31` and `docs/PRIVACY_AND_AI.md:60-64` document the
  project contract that app-declared no-personalized-learning fields force
  private/incognito handling.

## External Evidence

- Android `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` reference:
  `https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING`.
  The platform flag asks IMEs not to update personalized data such as typing
  history or personalized language models for the current editor.

## Roadmap Changes Fed

- R9-1: Snapshot suggestion privacy inputs before async candidate generation.
  Implementation should create a request-scoped snapshot before the coroutine
  calls providers, records traces, or gates ghost text. The snapshot should
  carry the request's private-session decision, offensive-content setting,
  enabled flags, and active editor sensitivity so a later field switch or
  incognito toggle cannot change the privacy meaning of already-captured text.

## Non-Adds

- No source fix was made in this cycle.
- No broad NLP refactor proposed. The target is request-scoped state in
  `NlpManager.suggest` and the direct provider/trace/ghost-text consumers.
- No new permission or network row added.
