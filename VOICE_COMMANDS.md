# SwiftFloris Voice Commands

SwiftFloris stores voice command definitions locally and can parse command-like transcripts with fuzzy matching. Dictation is currently handed to FUTO Voice Input as an external Android voice keyboard, so live command execution depends on a future transcript handoff path from that external IME.

## Built-In Commands

| Spoken phrase | Aliases | Action |
| --- | --- | --- |
| `delete that` | `delete it`, `remove that` | Deletes the previous word, or the active selection when the editor provides one. |
| `undo` | | Requests undo from the active editor. |
| `redo` | | Requests redo from the active editor. |
| `select all` | `select everything` | Selects all editable text in the active field. |
| `clear text` | `clear field`, `delete all` | Selects the active field and clears its text. |
| `new paragraph` | `next paragraph` | Inserts a blank line between paragraphs. |
| `new line` | `line break` | Inserts a line break. |
| `capitalize next word` | `capitalize next` | Enables one-shot capitalization for the next typed word. |
| `go to start` | `go to beginning`, `start of field` | Moves the cursor to the beginning of the field. |
| `go to end` | `end of field` | Moves the cursor to the end of the field. |

## Recognition Behavior

SwiftFloris normalizes command phrases before matching:

- Case and punctuation are ignored.
- Diacritics are ignored, so accented recognition output can still match English commands.
- Leading or trailing `please` is ignored.
- Small recognition errors are tolerated through fuzzy matching.

Confidence handling:

- `0.85` and above: execute the matched command.
- `0.50` to `0.85`: hold a suggestion so the UI can offer accept or reject.
- Below `0.50`: treat the transcript as normal dictated text and insert it.

Unrecognized command-like speech is intentionally inserted as text. This avoids destructive false positives when a user dictates ordinary sentences such as `delete the old note after lunch`.

## Custom Commands

Custom commands are managed in Settings > Voice input > Voice commands.

Each custom command has:

- A spoken phrase.
- A mapped command action.
- An enabled or disabled state.

Custom phrases are stored on device in SwiftFloris preferences. Disabled commands are ignored by the parser and can be re-enabled later.

## Errors And Recovery

FUTO Voice Input is offline-first, but SwiftFloris still models timeout and failure paths so future transcript integrations can fail safely.

| Condition | SwiftFloris behavior |
| --- | --- |
| No transcript or no match | Keeps voice input ready and inserts nothing. |
| Unrecognized transcript | Inserts the transcript as normal text. |
| Low-confidence command | Returns a pending suggestion for accept or reject. |
| Timeout or recognizer busy | Marks the attempt retryable without deleting or inserting text. |
| FUTO missing, disabled, or missing microphone permission | Shows setup-oriented unavailable state. |

## Accent FAQ

### What if my accent is not recognized?

Start by checking the phrase shown by FUTO. If FUTO hears a close variant, SwiftFloris fuzzy matching may still detect the command. If FUTO consistently hears a different phrase, add that phrase as a custom command in Voice input settings.

### Can I use non-English command phrases?

Yes, as custom commands. Built-in command phrases are currently English, but custom commands can map any phrase FUTO can transcribe to a supported action.

### Why did my command get typed instead of executed?

SwiftFloris only executes high-confidence command matches. Low-confidence matches should be confirmed by the user, and weak matches are inserted as text to avoid accidental destructive actions.

### Why do commands not run immediately after FUTO dictation?

SwiftFloris currently launches FUTO as a separate Android voice keyboard. Android does not provide SwiftFloris with FUTO's dictated transcript during that handoff, so parser and executor support is ready but live command execution still needs a transcript integration path.

## Validation Status

The parser, custom-command storage, command executor, fallback handler, and settings UI are covered by unit tests. Human validation across diverse accents is still required before claiming production recognition accuracy.
