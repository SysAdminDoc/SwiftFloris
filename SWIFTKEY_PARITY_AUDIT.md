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
- Long-press candidate removal is wired through providers and learned stores.
- Flow-style glide typing exists, including Flow Through Space support.
- Personal dictionary learning, personal bigram/trigram next-word prediction, incognito gating, password-field learning suppression, and local-only learning all exist.
- Multilingual suggestion merging exists for multiple locales on the same subtype and suppresses wrong-language autocorrect when the typed word is recognized by an enabled locale.
- Adaptive touch learning exists and now trains on successful tap-up rather than raw touch-down.
- Tap-up events now emit transient nearby-key evidence into the SwiftKey-style ranker, so adjacent-key mistakes can be corrected by spatial likelihood instead of only by resolved-key text.

## Gaps That Still Matter

1. Spacebar semantics need to stay centered on the middle prediction.
   SwiftKey exposes three modes: insert space, complete current word, or always insert prediction. SwiftFloris has the first two concepts, but does not yet expose "always insert prediction" for next-word insertion. This pass adds current-word middle-prediction behavior.

2. Candidate ranking is still heuristic, not a unified decoder.
   The app now has a central ranker and a first spatial evidence signal, but it does not yet combine dictionary frequency, personal phrase frequency, language prior, and context probability in one scored lattice.

3. Touch correction still resolves one key before NLP.
   Gap rescue, adaptive offsets, and transient nearby-key evidence now help, but the touch model still needs persistent per-subtype statistics, stronger offset priors, and multi-edit path scoring.

4. Next-word prediction needs richer context.
   Personal bigram/trigram prediction exists, but a SwiftKey-level feel needs phrase-level continuation, recency/frequency decay, and stronger cold-start language-model priors.

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

Persist and tune the `TouchDecoderEvidence` path:

- Persist per-subtype adaptive touch stats so the model survives process death, with reset and incognito guards.
- Add offset priors from accepted corrections and rejected corrections rather than successful taps only.
- Score multi-character edits from the spatial evidence instead of only equal-length adjacent replacements.
- Fold dictionary frequency and personal phrase probability into the same decoder score.
- Add end-to-end instrumentation for adjacent-key typos such as `gello -> hello`, row-gap taps, and rejected corrections.
