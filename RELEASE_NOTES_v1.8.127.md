# SwiftFloris v1.8.127

Released: 2026-05-18

## Emoji pinned-group sheet

- Completed the `Next-9.4a` pinned emoji-group UI: long-pressing an emoji can now open an in-keyboard "Pin to group" sheet instead of stopping at the prior placeholder state.
- Added a reusable `PinToGroupSheet` composable with existing-group rows, new-group creation, inline validation errors, and the existing `EmojiPinGroupStore` caps.
- Wired pinned-group chips to commit the full saved emoji sequence through the keyboard input dispatcher and refresh local emoji history.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
