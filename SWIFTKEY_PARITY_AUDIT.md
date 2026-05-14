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
- Cold-start English next-word prediction now has a small local prior model for sentence starts and common short continuations, so empty fields and fresh installs no longer rely only on raw dictionary frequency.
- Multilingual suggestion merging exists for multiple locales on the same subtype and suppresses wrong-language autocorrect when the typed word is recognized by an enabled locale.
- Adaptive touch learning exists, trains on successful tap-up rather than raw touch-down, and now persists per-subtype touch offsets across restarts.
- Tap-up events now emit transient nearby-key evidence into the SwiftKey-style ranker, so adjacent-key mistakes can be corrected by spatial likelihood instead of only by resolved-key text.
- Candidate ranking now produces an explicit score object with role, spatial likelihood, source affinity, provider confidence, dictionary frequency, personal context probability, language confidence, rejection penalty, edit proximity, completion affinity, and length penalty, with replay tests covering the highest-risk SwiftKey-like behaviors.
- A disabled-by-default local trace recorder can capture scored candidate order, previous words, touch evidence, candidate source/index, auto-commit eligibility, and accepted/rejected autocorrect events to JSONL when `<filesDir>/swiftkey_trace.enabled` exists.
- A no-op `NeuralCandidateReranker` boundary now sits behind the scorer, giving a future local ONNX/TFLite model a safe integration point without changing the no-network baseline.

## Gaps That Still Matter

1. Spacebar semantics need to stay centered on the middle prediction.
   SwiftKey exposes three modes: insert space, complete current word, or always insert prediction. SwiftFloris now has all three user-facing modes, including Quick prediction insert, but still needs broader real-world tuning around empty fields and low-confidence next-word candidates.

2. Candidate ranking is still heuristic, but it now has the right decoder inputs.
   The app now combines dictionary frequency, personal phrase context, language confidence, rejection history, provider confidence, role, and spatial likelihood in one scored lattice, with a neural-reranker seam ready for a future local model. The remaining gap is calibration from real typing traces rather than hand-tuned weights.

3. Touch correction still resolves one key before NLP.
   Gap rescue, persisted adaptive offsets, and transient nearby-key evidence now help, but the touch model still needs stronger accepted/rejected-correction priors and multi-edit path scoring.

4. Next-word prediction needs richer context.
   Personal bigram/trigram prediction now has recency/frequency decay and English cold-start priors, but a SwiftKey-level feel still needs phrase-level continuation beyond three words and broader context priors.

5. Multilingual detection should operate per word across active languages.
   Current support works when a subtype carries multiple locales. The next step is language posterior scoring from recent words, plus safer suppression when a token is valid in any enabled language.

6. Flow needs rescoring after the next word starts.
   Current Flow can commit through space. SwiftKey-like glide should keep ambiguity open briefly, then rescore the previous glide candidate using the next word.

7. Trust UX is underbuilt.
   SwiftKey exposes personalization/back-up concepts. SwiftFloris intentionally avoids network sync, but should make local learning visible with reset controls for learned words, phrase history, and adaptive touch.

## Immediate Implementation Direction

- Keep the prediction strip in a SwiftKey-like three-slot mental model: left escape/alternative, middle action, right alternative.
- Treat a recognized typed word as the middle candidate, so space keeps it.
- Treat an unrecognized current word's middle candidate as the spacebar completion/correction.
- Keep typed literal visible so correction mistakes are recoverable.
- Continue to suppress rejected autocorrect pairs after backspace.
- Use nearby-key touch evidence as a conservative tiebreaker before generic fallback confidence.

## Next Build Slice

Expand the scorer/replay foundation before attaching an optional neural reranker:

- Add offset priors from accepted corrections and rejected corrections rather than successful taps only.
- Score multi-character edits from the spatial evidence instead of only equal-length adjacent replacements.
- Promote debug JSONL traces into checked-in replay fixtures so score weights can be tuned from accepted/rejected events.
- Add deterministic replay tests for row-gap taps, multi-edit typos, multilingual words, glide paths, and next-word prediction.
- Expand sentence-position priors and cold-start phrase priors beyond the first English seed set.
- Attach a local ONNX/NNAPI candidate rescoring implementation behind the existing reranker boundary once trace fixtures can prove it improves ordering.
