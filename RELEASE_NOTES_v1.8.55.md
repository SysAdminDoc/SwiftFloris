# SwiftFloris v1.8.55 — 2026-05-17

Phase B3 — Shared-spelling bilingual handling: tighter scoring when
a one-locale candidate would overwrite a shared-spelling literal.

## Why ship this now

Phase B3 of `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. The existing
shared-spelling dampening (`typedKnownLocaleCount > 1` → 0.52
language confidence) lives above the
`SwiftKeyCandidateRanker.MinAutoCommitLanguageConfidence` floor
of **0.40**, so a one-language autocorrect (e.g. EN's `on` when the
user typed `no` in an EN+ES subtype) could still take the spacebar
slot. SwiftKey itself protects this case aggressively — the user's
literal `no` should stay literal.

The fix is asymmetric: when typed is shared across multiple locales
**and** the candidate is recognised in only one, push the score
below the autocommit floor (0.30). When typed is shared **and** the
candidate is also shared, keep the existing 0.52 because the
candidate could plausibly fit either side of the bilingual
sentence.

## What changed

### `MultilingualTokenScorer` (modified)

- New branch in the `when` ordering, fired before the existing
  generic shared-typed-word dampening:
  ```
  typedKnownLocaleCount > 1 && candidateKnown && candidateKnownLocaleCount == 1
    → SharedSpellingOneLocaleCandidateConfidence  (0.30)
  ```
- New internal constant
  `SharedSpellingOneLocaleCandidateConfidence = 0.30`, intentionally
  sub-floor relative to `MinAutoCommitLanguageConfidence = 0.40` so
  spacebar autocommit refuses the wrong-language overwrite.
- The both-shared case still falls to the existing 0.52 dampening
  one branch lower; existing literal-protection
  (`candidateMatchesTypedWord && typedWordKnown → 1.0`) is
  unchanged.

### Tests

- Existing `shared-spelling typed words damp one-language
  corrections` test reframed as
  `shared typed word with a single-locale candidate falls below the
  autocommit floor (B3)`; expected `languageConfidence` updated to
  `SharedSpellingOneLocaleCandidateConfidence`, with an additional
  `shouldBeLessThan 0.40` assertion to pin the relationship to the
  ranker's autocommit floor.
- New test `shared typed word with a shared candidate keeps
  moderate dampening (both could fit)` exercises the both-shared
  branch and pins it at 0.52.
- New test `shared typed word with a matching candidate stays at
  full confidence (literal protection)` exercises the
  `candidateMatchesTypedWord` short-circuit in a shared-spelling
  setup.

## Replay fixture compatibility

The checked-in `swiftkey/replay/trace_replay_cases.jsonl`
`shared-spelling bilingual literal protection` row supplies a
pre-computed `languageConfidence: 0.52` (captured before this
slice). The ranker's `expectedSpacebarText: null` assertion still
holds — 0.30 is even more below the autocommit floor than 0.52 was
— so the fixture continues to pass without modification. A
follow-up "B5 trace-based field calibration" slice can regenerate
the fixture from the live scorer when device-side captures are
collected.

## Versioning

- `gradle.properties`: `projectVersionCode=1855`,
  `projectVersionName=1.8.55`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

Phase B4 (v1.8.56) — same-sentence language switch hardening:
expand `TypingContextExtractor.previousWordListBeforeCurrentWord`
trailing context window from 2-word to 4-word and add an
alpha-blend on the per-locale evidence so a mid-sentence language
switch transitions smoothly instead of flipping on the first
recognised word.
