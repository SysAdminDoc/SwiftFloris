# SwiftFloris v1.8.76

**Release date:** 2026-05-17
**Type:** Hardware-keyboard runtime mapping

## What changed

- Added `HardwareKeyboardRuntimeMapper`, the runtime bridge for imported
  hardware-keyboard layouts.
- Layouts can now be bound to Android hardware keyboard `deviceId` values and
  pruned when `InputManager.getInputDeviceIds()` no longer reports a device.
- Runtime lookup resolves Android `KeyEvent` data through:
  - direct scan-code matches,
  - Android key-code matches,
  - common PC set-1 scan-code fallbacks for Windows KLC imports,
  - common macOS ANSI virtual-key fallbacks for `.keylayout` imports,
  - source virtual-key names such as `VK_B`.
- `KeyboardManager.onHardwareKeyDown(...)` now checks the mapper before the
  existing Space / Enter / Shift special cases and commits mapped printable
  characters through the normal editor path.

## Tests added

- `HardwareKeyboardRuntimeMapperTest` covers KLC fallbacks, macOS fallbacks,
  direct scan-code precedence, source-name fallback, Ctrl/Meta suppression, and
  detached-device pruning.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardRuntimeMapperTest`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
