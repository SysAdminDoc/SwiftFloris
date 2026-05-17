# SwiftFloris v1.8.57 — 2026-05-17

Phase C2 — Arrow-keys bottom-row preset (SwiftKey "Modes → Arrow
keys" parity).

## Why ship this now

Phase C2 of `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. SwiftKey ships
a Modes → Arrow keys affordance that swaps the standard bottom row
for ← ↑ ↓ → so cursor navigation doesn't need the space-bar
trackpad gesture or a hardware-keyboard handoff. The space bar
shrinks but stays present because the user still needs to type
spaces between navigation hops.

Small slice — pure JSON + enum addition + Settings UI entry — no
new dependencies, no Android-specific paths beyond the existing
arrow-key `TextKeyData` constants.

## What changed

### `BottomRowPreset.Navigation` (new preset)

Surfaces ARROW_LEFT / ARROW_UP / SPACE / ARROW_DOWN / ARROW_RIGHT /
ENTER. Period and symbols-view are dropped to fit the four arrows
on a typical-width keyboard; ENTER stays so commit-after-navigation
still works without flipping to a different layout. Space bar
shrinks proportionally.

### `BottomRowKey` enum (4 new values)

ARROW_LEFT, ARROW_UP, ARROW_DOWN, ARROW_RIGHT join the existing
TAB / ESCAPE / SLASH (programmer-mode) values. Each maps to the
matching predefined `TextKeyData.ARROW_*` constant — the runtime
cursor-movement path is already wired
(`KeyboardManager.onInputKeyUp` dispatches `ARROW_*` codes).

### `BottomRowPreset.Presets` registry

Updated to include `Navigation` so the Settings → Keyboard →
Bottom-row preset picker surfaces it alongside the existing
presets. New test pins this registration so a future contributor
that adds a preset without updating the registry gets caught.

### Settings UI

Settings → Keyboard → Bottom-row preset gains an "Arrow keys"
entry between the existing "Programmer" entry and the implicit
end of the list. The label is hard-coded English in this slice
(consistent with the existing "Programmer" entry) — a Crowdin
string resource follows in a localization sweep.

## Tests

`BottomRowPresetTest` (3 new cases):
- `Navigation` preset emits the expected ARROW_LEFT / ARROW_UP /
  SPACE / ARROW_DOWN / ARROW_RIGHT / ENTER key codes in order.
- `Navigation` preset round-trips through the JSON override codec
  (encode → decode produces the same preset).
- `Navigation` preset is registered in the public `Presets` list
  (the registry the Settings picker iterates).

## Versioning

- `gradle.properties`: `projectVersionCode=1857`,
  `projectVersionName=1.8.57`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

Phase C1 (split-keyboard renderer wire-up inside
`TextKeyboardLayout`) and Phase C3 (High-Contrast AAA theme +
animated theme) are the remaining Phase C items; either could land
next depending on whether the autonomous loop prefers a code-side
slice (C1) or an asset-side one (C3). Phase D (calendar / tasks
quick-actions, typing-stats accuracy-delta) is the next functional
parity push.
