# Tasker Integration

SwiftFloris exposes a small signature-protected broadcast surface for trusted
automation senders such as Tasker companion profiles or same-signed addons.
The receiver is declared with
`dev.patrickgold.florisboard.permission.REGISTER_ADDON`, so arbitrary apps
cannot inject text into the active editor.

## Actions

All actions use Android broadcast intents.

| Action | Extras | Effect |
|---|---|---|
| `swiftfloris.action.INSERT_TEXT` | `text` string, optional `appendSpace` boolean | Inserts text at the current cursor. |
| `swiftfloris.action.INSERT_CLIP` | none | Pastes the current primary clipboard item. |
| `swiftfloris.action.SWITCH_LAYOUT` | `layoutId` string, for example `dvorak` | Switches the active subtype's character layout. |
| `swiftfloris.action.TRIGGER_VOICE` | optional `mode` string: `dictation` or `command` | Hands off to the configured voice input method. |

## Examples

Insert a canned reply:

```shell
adb shell am broadcast -a swiftfloris.action.INSERT_TEXT --es text "On my way" --ez appendSpace true
```

Paste the clipboard:

```shell
adb shell am broadcast -a swiftfloris.action.INSERT_CLIP
```

Switch to Dvorak when a Tasker location profile detects home:

```shell
adb shell am broadcast -a swiftfloris.action.SWITCH_LAYOUT --es layoutId dvorak
```

Trigger voice input from a shake gesture:

```shell
adb shell am broadcast -a swiftfloris.action.TRIGGER_VOICE --es mode dictation
```

## Notes

The IME must be active and focused in an editor for insert, paste, or voice
handoff to have a visible effect. The receiver validates all payloads before
dispatch: inserted text is capped at 4096 characters, layout IDs are restricted
to lowercase letters, digits, and underscores, and voice mode accepts only
`dictation` or `command`.
