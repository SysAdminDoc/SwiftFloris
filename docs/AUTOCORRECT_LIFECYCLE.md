# Autocorrect Lifecycle Contract

Last updated: 2026-05-18

This document is the source-of-truth contract for SwiftFloris autocorrect,
spacebar prediction insertion, punctuation-triggered commits, backspace
rejection, and provider notifications.

## Owners

| Owner | Responsibility |
|---|---|
| `NlpManager` | Collect editor state, assemble active candidates, own `AutoCommitSuppression`, record correction priors and trace events. |
| `CandidateAutoCommitPolicy` | Select auto-commit and spacebar candidates from current candidates, shortcuts, phrase repairs, immediate fallbacks, language-confidence signals, and rejection state. |
| `KeyboardManager` | Execute software and hardware key actions, commit candidates, notify providers, and dispatch delete / enter / punctuation behavior. |
| `KeyboardAutoCommitFlushPolicy` | Decide whether a software text commit flushes a pending autocorrect candidate first. |
| `HardwareKeyboardInputPolicy` | Decide whether mapped hardware-key text commits flush a pending autocorrect candidate first. |
| `EditorInputBehaviorPolicy` | Decide auto-space, phantom-space, double-space period, sentence capitalization, plain-space insertion, and glide backspace escalation. |
| `AutoCommitSuppression` | Remember the last accepted correction, detect immediate rejection, suppress the same correction in the same word slot, and expose rejection priors once. |

## Lifecycle

1. `NlpManager.suggest(...)` updates `activeCandidates` and language-confidence
   signals from the active subtype and `EditorContent`.
2. A commit trigger asks for a candidate:
   - Spacebar uses `CandidateAutoCommitPolicy.selectSpacebarCandidate(...)`.
   - Software punctuation / non-letter commits use
     `KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(...)` before asking
     `NlpManager.getAutoCommitCandidate()`.
   - Mapped hardware punctuation uses
     `HardwareKeyboardInputPolicy.keyDownAction(...)` and flushes before commit
     when the mapped output is not alphabetic.
3. `CandidateAutoCommitPolicy` applies the priority order:
   - User-dictionary shortcut.
   - Immediate phrase repair.
   - Active strip candidate that is auto-commit eligible and passes
     language-confidence gates.
   - Immediate fallback candidate.
4. If `AutoCommitSuppression` says the typed literal must be preserved, no
   source may reapply the rejected correction in that word slot.
5. `KeyboardManager.commitCandidate(...)` is the only candidate-commit entry
   point. Provider acceptance notification and personal-dictionary learning are
   side effects of a successful editor commit, not pre-commit signals.
6. Auto-committed candidates call
   `NlpManager.rememberAcceptedAutoCommit(contentBeforeCommit, candidate)` so a
   later immediate delete can be classified as a rejection.
7. Backward and forward delete call `revertPreviouslyAcceptedCandidate()` before
   deleting editor text. That method:
   - Lets `AutoCommitSuppression` classify rejection and record correction
     priors / trace events.
   - Notifies a source provider only when the accepted candidate is still the
     tracked `phantomSpace.candidateForRevert`.
8. `AutoCommitSuppression` keeps the rejected pair active while the user edits
   back to the original word in the same word slot, then clears it when the word
   is completed or the slot changes.

## Spacebar

- If autocorrect and quick prediction insert are both disabled, spacebar never
  accepts a candidate.
- If autocorrect is enabled, explicit shortcuts and phrase repairs outrank
  quick prediction.
- If quick prediction insert is enabled and the current word is blank, the
  spacebar may insert a next-word candidate and suppress the plain space.
- `EditorInputBehaviorPolicy.shouldCommitPlainSpaceAfterSpacebar(...)` prevents
  duplicate spaces for no-auto-space locales and prediction insertion.

## Punctuation And Non-Letter Commits

- Software text commits in media mode flush a pending autocorrect candidate
  first.
- Character-mode non-letter commits flush a pending autocorrect candidate first.
  This includes punctuation and numeric keys on a character keyboard.
- Alphabetic software key commits do not flush autocorrect.
- Numeric and phone keyboard modes do not flush autocorrect before punctuation.
  This preserves values such as phone numbers, dates, and decimals.
- Mapped hardware letters commit without flushing. Mapped hardware punctuation
  flushes before commit. Unmapped hardware delete passes through to the host
  editor path.

## Delete And Glide

- Software backward and forward delete both run the autocorrect rejection hook
  before deleting text.
- `immediateBackspaceDeletesWord` escalates a character delete to a word delete
  only when all of these are true:
  - The requested operation is `OperationUnit.CHARACTERS`.
  - Phantom space is active from the committed glide word.
  - The current word is valid.
  - The preference is enabled.
- Explicit word deletes, inactive phantom-space state, invalid current words,
  and disabled preference keep the normal delete behavior.

## Provider Notification Contract

- `notifySuggestionAccepted(...)` is best-effort, asynchronous, and sent only
  after the editor commit reports success.
- Clipboard candidates may notify their provider after a successful commit, but
  they are not learned into the personal dictionary.
- Non-clipboard candidates may be learned after a successful commit through
  `learnIfAllowed(...)`, which still honors incognito and sensitive-field gates.
- `notifySuggestionReverted(...)` is advisory. The IME-owned
  `AutoCommitSuppression` state is the source of truth for blocking repeated
  autocorrect in the same word slot.
- Providers must tolerate missing acceptance / rejection callbacks. They may use
  callbacks for ranking, accounting, or local model updates, but must not use
  them to mutate the active editor transaction.

## Manual QA

Run the normal release gate first:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Useful adb setup commands:

```powershell
adb devices
adb shell ime list -s
adb shell ime set <swiftfloris-ime-id-from-ime-list>
adb shell am start -n dev.patrickgold.florisboard.debug/dev.patrickgold.florisboard.SettingsLauncherAlias
adb logcat -c
```

Manual scenarios:

- Normal text field: type a known correction such as `teh`, accept with
  spacebar, then verify `the ` is committed once and not double-spaced.
- Rejection: after the accepted correction, delete immediately, restore the
  original typed word in the same slot, and verify the same correction is not
  re-applied on the next space / punctuation.
- Software punctuation: on the character keyboard, type an auto-commit-eligible
  typo followed by `.`, `,`, `?`, and `!`; verify the correction flushes before
  punctuation and spacing remains correct.
- Numeric / phone modes: type decimal, date, or phone-like values and verify
  punctuation does not trigger word autocorrect.
- Hardware keyboard: with a physical or mapped emulator keyboard, verify mapped
  letters do not flush autocorrect, mapped punctuation does, and delete behaves
  like the software delete path for rejection.
- Glide delete: with `immediateBackspaceDeletesWord` enabled, commit a glide
  word and press backspace once; verify the committed glide word is removed.
  Disable the setting and verify backspace deletes normally.
- Sensitive fields: repeat the acceptance and learning checks in a password
  field and a field with suggestions disabled; verify no learning or private
  suggestions are exposed.

## Regression Coverage Matrix

| Contract area | JVM coverage |
|---|---|
| Auto-commit suppression and rejection lifetime | `AutoCommitSuppressionTest`, `EditorInputBehaviorPolicyTest` |
| Candidate ordering, shortcuts, phrase repairs, language confidence, quick prediction, plain-space suppression | `CandidateAutoCommitPolicyTest` |
| Provider notification and learning side-effect gates | `CandidateCommitSideEffectPolicyTest` |
| Software punctuation / non-letter flush policy | `KeyboardAutoCommitFlushPolicyTest` |
| Hardware space / enter / delete / mapped letter / mapped punctuation routing | `HardwareKeyboardInputPolicyTest` |
| Auto-space, phantom-space, double-space period, sentence capitalization, glide delete escalation | `EditorInputBehaviorPolicyTest`, `EditorSpacingLifecycleStateTest` |
| Locale capitalization and auto-space capability gates | `FlorisLocaleTest` |
| Multilingual confidence and bilingual literal protection | `SwiftKeyCandidateRankerTest`, `SwiftKeyTypingReplayHarnessTest`, `SwiftKeyTraceReplayFixtureTest` |
