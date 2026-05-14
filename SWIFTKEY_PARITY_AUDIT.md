# SwiftKey Typing Parity Audit

Audited against current Microsoft SwiftKey support docs on prediction behavior, spacebar autocorrect, Flow, multilingual typing, and privacy/data handling.

Primary references:

- Prediction bar and word removal: https://support.microsoft.com/en-us/swiftkey-keyboard/how-does-the-microsoft-swiftkey-prediction-bar-work
- Spacebar autocorrect modes: https://support.microsoft.com/en-US/swiftkey-keyboard/how-does-the-spacebar-work-with-autocorrect-in-microsoft-swiftkey-keyboard
- Flow and Flow Through Space: https://support.microsoft.com/en-us/swiftkey-keyboard/what-is-flow-and-how-do-i-enable-it-with-microsoft-swiftkey-keyboard
- Multilingual typing: https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-microsoft-swiftkey-keyboard-with-more-than-one-language
- Data and backup/sync behavior: https://support.microsoft.com/en-us/swiftkey-keyboard/what-data-is-collected-sent-while-using-microsoft-swiftkey-keyboard

## Current Parity

- Default visual layout follows the SwiftKey-style keyboard surface: number row, dark key surfaces, bottom-row organization, large tap targets, and no language label on the spacebar.
- Candidate strip is in classic three-slot mode by default.
- Typed literal remains available as an escape hatch before correction.
- Spacebar autocorrect can accept a high-confidence correction and backspace can reject/suppress the accepted pair.
- Typing settings now expose SwiftKey's separate Quick prediction insert behavior, allowing space to insert middle next-word predictions instead of acting only as a plain space.
- Long-press candidate removal is wired through providers and learned stores.
- Flow-style glide typing exists, including Flow Through Space support.
- Personal dictionary learning, personal bigram/trigram next-word prediction, incognito gating, password-field learning suppression, and local-only learning all exist.
- Personal bigram/trigram prediction is now recency-aware: learned phrase files keep a last-seen timestamp, older files are migrated safely, and recent continuations can outrank stale high-count history.
- Cold-start English next-word prediction now has a small local prior model for sentence starts, common short continuations, and selected two-/three-word phrase continuations, and those priors now feed partial-word candidate scoring as well as blank next-word prediction.
- Multilingual suggestion merging exists for multiple locales on the same subtype and suppresses wrong-language autocorrect when the typed word is recognized by an enabled locale.
- Known-word detection and candidate language confidence now score across every active subtype locale, so a word recognized only by a secondary language can still occupy the middle literal slot and resist wrong-language correction.
- Candidate language confidence now also uses the previous two words as per-locale context evidence, allowing partial bilingual completions to follow the sentence language before the current token is itself recognizable.
- Adaptive touch learning exists, trains on successful tap-up rather than raw touch-down, and now persists per-subtype touch offsets across restarts.
- Tap-up events now emit transient nearby-key evidence into the SwiftKey-style ranker, so adjacent-key mistakes can be corrected by spatial likelihood instead of only by resolved-key text.
- Candidate ranking now produces an explicit score object with role, spatial likelihood, source affinity, provider confidence, dictionary frequency, personal context probability, language confidence, rejection penalty, edit proximity, completion affinity, and length penalty, with replay tests covering the highest-risk SwiftKey-like behaviors.
- Touch evidence now treats adjacent character transpositions such as "teh" -> "the" as a spatial/temporal correction signal, not just a generic edit-distance match.
- Flow now keeps short ambiguous glide commits recoverable for a bounded window and can retroactively replace the previous glide word from immediate next-word context before committing the next word.
- A small curated multi-word repair tier now handles common run-together English phrase typos such as `thankyou`, `alot`, and `ofcourse` before lower-confidence generic autocorrects.
- A disabled-by-default local trace recorder can capture scored candidate order, previous words, touch evidence, candidate source/index, auto-commit eligibility, and accepted/rejected autocorrect events to JSONL when `<filesDir>/swiftkey_trace.enabled` exists.
- An anonymized JSONL replay fixture and parser now make captured trace-like events runnable in plain JVM tests.
- A no-op `NeuralCandidateReranker` boundary now sits behind the scorer, giving a future local ONNX/TFLite model a safe integration point without changing the no-network baseline.

## Gaps That Still Matter

1. Spacebar semantics need to stay centered on the middle prediction.
   SwiftKey exposes three modes: insert space, complete current word, or always insert prediction. SwiftFloris now has all three user-facing modes, including Quick prediction insert, but still needs broader real-world tuning around empty fields and low-confidence next-word candidates.

2. Candidate ranking is still heuristic, but it now has the right decoder inputs.
   The app now combines dictionary frequency, personal phrase context, language confidence, rejection history, provider confidence, role, spatial likelihood, and a first curated multi-word repair tier, with a neural-reranker seam ready for a future local model. The remaining gap is calibration from real typing traces rather than hand-tuned weights.

3. Touch correction still needs broader trace calibration.
   Gap rescue, persisted adaptive offsets, transient nearby-key evidence, adjacent transposition scoring, bounded insertion/deletion path scoring, accepted/rejected outcome priors, and first-pass short-glide context rescue now help. The remaining touch gap is broader real-trace calibration plus richer gesture fixtures.

4. Next-word prediction needs broader phrase coverage.
   Personal bigram/trigram prediction now has recency/frequency decay and English cold-start priors for sentence starts, one-word continuations, and selected phrase continuations. Cold-start phrase context now promotes typed completions like `know` after `Let me kn` before personal history exists. A SwiftKey-level feel still needs broader phrase coverage, domain adaptation, and trace-calibrated ranking.

5. Multilingual detection should keep adding context.
   Current support now checks the current token across active locales, lowers wrong-language correction confidence when a token is valid elsewhere, and uses trailing-word locale evidence to steer partial completions toward the active sentence language. The next step is shared-spelling handling, active-locale switching edge cases, and broader bilingual replay fixtures from real traces.

6. Flow needs broader real-world tuning.
   Current Flow can commit through space and now has conservative following-context rescue for short ambiguous words. The new glide replay fixture guards both successful context rescues and no-op safety cases, and `GlideContextTuning` makes the rescue constants testable before default changes. SwiftKey-like glide still needs richer real path fixtures, adaptive-touch weighting inside the gesture candidate list, and neural/beam-search-style rescoring to match SwiftKey on longer or multilingual swipes.

7. Trust UX is improving but still needs more real-world traces.
   SwiftKey exposes personalization/back-up concepts. SwiftFloris intentionally avoids network sync, and Typing stats now makes local phrase history, correction memory, adaptive touch, local trace capture, and sanitized replay-fixture export visible by user action. Replay tests now expose aggregate accuracy metrics plus bilingual/glide category guards, and `SwiftKeyCandidateTuning` makes scoring changes measurable; remaining trust work is larger fixture coverage from real local traces.

## Immediate Implementation Direction

- Keep the prediction strip in a SwiftKey-like three-slot mental model: left escape/alternative, middle action, right alternative.
- Treat a recognized typed word as the middle candidate, so space keeps it.
- Treat an unrecognized current word's middle candidate as the spacebar completion/correction.
- Keep typed literal visible so correction mistakes are recoverable.
- Continue to suppress rejected autocorrect pairs after backspace.
- Use nearby-key touch evidence as a conservative tiebreaker before generic fallback confidence.

## Next Build Slice

Expand the scorer/replay foundation before attaching an optional neural reranker:

- Keep tuning offset priors from accepted corrections and rejected corrections rather than successful taps only.
- Keep expanding bounded multi-edit scoring from checked-in trace evidence rather than hand-tuning weights.
- Keep promoting debug JSONL traces into checked-in replay fixtures so score weights can be tuned from accepted/rejected events.
- Add deterministic replay tests for active-locale switching edge cases, shared-spelling bilingual words, and more multi-word repairs; partial phrase completions, bilingual context completions, a first multi-word repair, and longer Flow contraction rescue are now covered.
- Expand sentence-position priors and cold-start phrase priors beyond the first English seed set.
- Attach a local ONNX/NNAPI candidate rescoring implementation behind the existing reranker boundary once trace fixtures can prove it improves ordering.
