# SwiftFloris v1.8.56 — 2026-05-17

Phase B4 — Same-sentence language-switch hardening:
geometric-decay weighted blend of trailing-word language evidence.

## Why ship this now

Phase B4 of `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. The trailing
context window was already 4 words (`MaxLanguageContextWords = 4`
shipped earlier), but the per-locale scoring took the **MAX**
frequency across that window — so a single early trailing word in
any locale locked in the signal and the next three words couldn't
shift it. Real bilingual sentences mid-switch (`"hello mi amigo
cómo …"`) need the recent words to weigh more so the active
language tracks the user's writing without flipping on the first
recognised word.

## What changed

### New `TrailingContextLanguageBlend` (pure-Kotlin helper)

Pulls the per-locale blend math out of `NlpManager.candidateSignals`
into a focused helper that's unit-testable independent of Android
plumbing.

- `score(contextWordsOldestFirst, freqLookup, decay = 0.7)` returns
  the geometric-decay weighted average frequency.
- The most-recent word weighs 1.0; each word further back is scaled
  by `decay` per step (default 0.7).
- `decay = 1.0` collapses to a flat arithmetic mean; `decay = 0.0`
  collapses to "only the most-recent word counts".
- Empty context returns 0.0. Decay outside `[0.0, 1.0]` rejected
  with `IllegalArgumentException`.

### `NlpManager.candidateSignals` (refactored)

The `contextLanguageScores` map is rebuilt by routing each active
locale through `TrailingContextLanguageBlend.score(...)` with the
new `TrailingContextDecay = 0.7` constant. Reading the same
`languageContextWords` list as before — no signature or threading
changes; the rest of the candidate-signal pipeline is unaffected.

Weight schedule on the default 4-word window:

```
  weight[0] (most recent)    = 1.0
  weight[1]                  = 0.7
  weight[2]                  = 0.49
  weight[3] (4 back, oldest) = 0.343
```

Roughly a 3× preference for the most-recent word over the oldest
— enough to smoothly track a mid-sentence language switch.

## Tests

`TrailingContextLanguageBlendTest` (8 cases):
- empty context returns 0.0;
- single-word context collapses to the raw frequency;
- all-same-locale window returns 1.0 regardless of decay (sanity);
- recent in-locale word outweighs older out-of-locale words
  (`["the", "old", "house", "hola"]` → blended ≈ 0.355 for ES);
- older in-locale word matters less than recent out-of-locale
  window (`["hola", "the", "old", "house"]` → blended ≈ 0.122 for
  ES);
- recent-Spanish case strictly > older-Spanish case (the whole
  point of B4);
- `decay = 1.0` collapses to arithmetic mean (pinned);
- `decay = 0.0` collapses to most-recent-only (pinned);
- decay outside `[0.0, 1.0]` rejected;
- regression vs. previous MAX behaviour — a single ES word in a
  4-word EN window no longer dominates (blended << 1.0 and even
  << 0.5).

## Replay-fixture compatibility

The trace-replay fixtures supply pre-computed `languageConfidence`
signals directly to the ranker; they don't re-run
`candidateSignals`. So existing fixtures stay valid. Future
fixture regeneration (B5 follow-up) will pick up the new blended
scores naturally when device captures land.

## Versioning

- `gradle.properties`: `projectVersionCode=1856`,
  `projectVersionName=1.8.56`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

Phase B1 / B2 are the remaining Phase B items (sentence-position
priors expansion across non-English Zipf overlays, quick-prediction
threshold sweep). Both are larger-asset / property-test work that
benefits from the device-captured trace fixtures landing first, so
the next slice may pivot to Phase C (split-keyboard renderer
wire-up) or Phase D (calendar / tasks quick-actions) depending on
which yields the most reviewable scope without external blockers.
