# FlorisKeyboard v1.0.0

**Professional Android Keyboard IME** — A modern, customized fork of [FlorisBoard](https://github.com/florisboard/florisboard).

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen)
![License](https://img.shields.io/badge/license-Apache%202.0-green)

## About

FlorisKeyboard is a production-ready Android input method editor (IME) built on the excellent FlorisBoard foundation. It provides:

- **Modern Material Design 3** UI with dynamic theming
- **Premium user experience** with smooth animations and intuitive gestures
- **Multi-language support** with 80+ layouts
- **Privacy-first design** — no telemetry, no cloud sync
- **Highly customizable** theme system
- **Gesture typing** (optional glide input)
- **Emoji picker** with organized categories

## Quick Start

### Build Locally
```bash
# Clone the repository
git clone https://github.com/SysAdminDoc/FlorisKeyboard.git
cd FlorisKeyboard

# Build APK (requires Android SDK 36+, API 26 minimum)
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk

# Or build and install directly
./gradlew installDebug
```

### Enable as Default Keyboard
1. Open **Settings** → **System** → **Languages & input**
2. Tap **Virtual keyboard** → **Manage keyboards**
3. Enable **FlorisKeyboard**
4. Return to **Virtual keyboard** and select **FlorisKeyboard**

## Architecture

- **Built on**: FlorisBoard v0.6.0-alpha02
- **Stack**: Kotlin, Jetpack Compose, Material Design 3
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 15)
- **Package**: `org.florisboard.ime`

### Build Configuration

The native Rust module (`lib:native`) is disabled for keyboard-only builds. The core IME functionality does not require it.

To enable native module (requires CMake 4.1.2+):
1. Uncomment `include(":lib:native")` in `settings.gradle.kts`
2. Uncomment native dependency in `app/build.gradle.kts`
3. Restore imports in `FlorisApplication.kt`

## File Structure

```
FlorisKeyboard/
├── app/                 # Main IME application
│   ├── src/main/kotlin/ # Kotlin source code
│   ├── src/main/res/    # Resources (layouts, strings, themes)
│   └── build.gradle.kts # App-level dependencies
├── lib/
│   ├── android/         # Android utilities
│   ├── color/           # Color manipulation
│   ├── compose/         # Compose components
│   ├── kotlin/          # Kotlin utilities
│   └── snygg/           # Theme system
├── settings.gradle.kts  # Gradle module configuration
└── build.gradle.kts     # Root-level build configuration
```

## Development

### Key Components

- **IME Service**: `FlorisInputMethodService` — Core input method lifecycle
- **Keyboard View**: Compose-based keyboard UI rendering
- **Theme Manager**: Dynamic Material You theming
- **Gesture Typing**: Glide decoding and prediction
- **Extensions**: Customizable layouts and keyboards

### Building for Release

```bash
# Build signed release APK
./gradlew bundleRelease

# Generate release notes for changelog
git log --oneline v0.0.0..HEAD > RELEASE_NOTES.txt
```

## License

FlorisKeyboard is licensed under the **Apache License 2.0** (inherited from FlorisBoard).

See [LICENSE](LICENSE) for full terms.

## Upstream

This project is a fork of [FlorisBoard](https://github.com/florisboard/florisboard) by Patrick Goldinger and contributors.

**Credits**:
- Original FlorisBoard project: https://github.com/florisboard/florisboard
- Jetpack Compose & Material Design 3 from Google
- Community translations via Crowdin

## Support

For bugs or feature requests related to **FlorisKeyboard** customizations:
- Open an issue on GitHub: https://github.com/SysAdminDoc/FlorisKeyboard/issues

For core FlorisBoard issues, refer to: https://github.com/florisboard/florisboard/issues
