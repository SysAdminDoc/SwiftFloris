# SwiftFloris v1.8.61 — 2026-05-17

Phase B2 — quick-prediction-insert threshold tuning.

## Why ship this now

`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` calls out that the
quick-prediction-insert path needed calibration before the cutoff sprint
could move on to visual and productivity work. The old path accepted any
word candidate when the current word was blank, and the keyboard plain-space
suppression path only checked whether any word candidate existed. That made
low-confidence blank-field predictions too eager.

No new permissions, assets, dependencies, network surfaces, or background
jobs are added.

## What changed

### Configurable quick-insert floor

`SwiftKeyCandidateRanker.selectSpacebarCandidate(...)` now accepts
`QuickPredictionInsertTuning`, with the default floor set to:

- `minWeightedConfidence = 0.40`
- `maxContextRecencyBoost = 0.35`

For blank-current-word quick prediction insertion, the center/default word
candidate is accepted only when:

```text
candidate.confidence * (1.0 + contextProbability * maxContextRecencyBoost)
  >= minWeightedConfidence
```

The trigger contexts are intentionally narrow:

- cold start / empty field;
- after `.`, `!`, or `?`;
- after a newline.

High-confidence candidates after commas, semicolons, or plain word-boundary
spaces are left as normal spacebar inserts rather than silently replacing the
space with a prediction.

### Space suppression aligned with selection

`NlpManager.shouldSuppressPlainSpaceForPrediction()` now calls the same
ranker selection path as `getSpacebarCandidate()`. If the tuned floor rejects
the prediction, the keyboard no longer suppresses the user's plain space just
because a word candidate exists in the strip.

## Tests

Added / updated unit coverage for:

- low-confidence blank-context rejection;
- cold-start, post-period, post-exclamation, post-question, and post-newline
  acceptance above the floor;
- strong context-recency lifting a borderline candidate above the floor;
- explicit custom-floor rejection;
- property-style sweep across trigger context, candidate confidence, and
  recency signal;
- non-boundary punctuation preserving normal spacebar behavior.

## Versioning

- `gradle.properties`: `projectVersionCode=1861`,
  `projectVersionName=1.8.61`.

## Verification

Local non-Java check:

```powershell
git diff --check
```

This VM still has no JDK / Android SDK on the path, so run before merge on
the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test target once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.SwiftKeyCandidateRankerTest
```

## What's next

The remaining unblocked SwiftKey-parity work is C1 split-keyboard renderer
wire-up, C3 High-Contrast / animated themes, and D1 calendar quick-insert.
B5 still needs human-captured local trace fixtures before decoder constants
should move.
