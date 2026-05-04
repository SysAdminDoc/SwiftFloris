# SwiftFloris

![Version](https://img.shields.io/badge/version-v1.5.0-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green) ![Platform](https://img.shields.io/badge/platform-Android%208.0+-orange)

**SwiftFloris** is a SwiftKey-inspired Android keyboard with a powerful, proven backend and modern Material Design 3 interface. Built on FlorisBoard's solid IME foundation and customized with SwiftKey's aesthetic and experience philosophy.

## About SwiftFloris

SwiftFloris combines the best of both worlds:
- **FlorisBoard Backend**: Production-tested, reliable IME service with excellent performance
- **SwiftKey Aesthetic**: Clean, modern interface inspired by Microsoft's SwiftKey keyboard
- **Premium Customization**: Four curated theme packs (Nord, Tokyo Night, Dracula, Catppuccin)
- **Secure Features**: Encrypted clipboard history, privacy-respecting design
- **Material Design 3**: Latest Android design standards with adaptive theming

## Features

### Core Keyboard
- ✨ Full Material Design 3 support
- 🎨 4 premium theme packs with multiple color variants
- ⌨️ Responsive, low-latency key detection with **SwiftKey-parity typing logic**
- 🌐 Multi-language support (inherited from FlorisBoard)
- ♿ Full accessibility support
- 🔤 **Advanced Auto-Capitalization**: Intelligently capitalizes the first letter of sentences after `.`, `!`, and `?`

### Premium Features
- 📋 **Secure Clipboard History**: Encrypted storage of recent clipboard entries
  - Quick access to recent pastes
  - Per-app tracking
  - Auto-cleanup (max 50 items)
  - Touch-to-insert functionality

- 🎯 **Advanced Customization**: 
  - 4 premium themes: Nord, Tokyo Night, Dracula, Catppuccin
  - Dark/light mode support
  - Custom accent colors
  - Adaptive icon system

- ✏️ **Smart Typing**:
  - Automatic capitalization at text field start
  - Sentence-aware capitalization (after punctuation)
  - Proper state management for natural typing flow

- 🎤 **Voice Input** (since v1.3.0):
  - Speech-to-text conversion
  - Supports all 6 languages (EN, DE, FR, ES, IT, PT)
  - Confidence scoring and real-time feedback
  - No API key required, works offline

- 🎯 **Gesture/Swipe Typing** (NEW in v1.4.0):
  - Type entire words by dragging finger across keyboard
  - Intelligent word prediction with spell checking
  - Configurable sensitivity and trail visualization
  - Fast, offline, fully private
  - See [Gesture Typing Guide](GESTURE_TYPING.md) for best practices

### Themes Included

1. **Nord** — Arctic, north-bluish color palette
2. **Tokyo Night** — Neon-inspired vibrant colors
3. **Dracula** — Dark, elegant purple and pink scheme
4. **Catppuccin Mocha** — Warm, cohesive modern aesthetic

## Installation

### From GitHub Releases
Download the latest APK from the [Releases](https://github.com/SysAdminDoc/SwiftFloris/releases) page and install on your Android device (Android 8.0+).

### Manual Build
```bash
git clone https://github.com/SysAdminDoc/SwiftFloris.git
cd SwiftFloris
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Enable as Default Keyboard
1. Open **Settings** → **System** → **Languages & input**
2. Tap **Virtual keyboard**
3. Select **SwiftFloris**
4. Grant necessary permissions

## Documentation

- **[Features Overview](FEATURES.md)** — Complete feature inventory with implementation status
- **[Multilingual Support](MULTILINGUAL.md)** — Testing guide for spell checking across 6 languages  
- **[FUTO Voice Input Guide](FUTO_VOICE_INPUT.md)** — Setup, languages, troubleshooting (NEW v1.5.0)
- **[Gesture Typing Guide](GESTURE_TYPING.md)** — Swipe typing feature guide with best practices and troubleshooting
- **[Changelog](CHANGELOG.md)** — Release history and version notes

## Architecture

**Stack**:
- Jetpack Compose (declarative UI)
- Material Design 3 (modern design system)
- Kotlin + Coroutines (async operations)
- Encrypted SharedPreferences (secure storage)
- Room Database (settings persistence)

**Min SDK**: 26 (Android 8.0 Oreo)  
**Target SDK**: 36 (Android 15)

## Project Structure

```
SwiftFloris/
├── app/src/main/
│   ├── kotlin/
│   │   └── dev/patrickgold/florisboard/
│   │       ├── ime/
│   │       │   ├── keyboard/        (keyboard layout & rendering)
│   │       │   ├── clipboard/       (clipboard history feature)
│   │       │   └── input/           (input processing)
│   │       └── FlorisImeService.kt  (IME service)
│   └── res/
│       ├── drawable/                (logo & icons)
│       └── values/                  (colors, strings, themes)
├── app/build.gradle.kts             (build config)
└── settings.gradle.kts              (gradle modules)
```

## Building

### Requirements
- Android SDK 26+ (Android 8.0)
- Gradle 8.x
- Java 17+
- Kotlin 2.0+

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build (unsigned)
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean build
./gradlew clean
```

## Contributing

We welcome contributions! Areas for improvement:
- Additional themes
- Keyboard layout customization
- Voice input integration
- Gesture support
- Performance optimizations

Feel free to open issues and pull requests.

## Permissions

SwiftFloris requests the following permissions:
- **INPUT_METHOD**: Required for IME functionality
- **VIBRATE**: Optional haptic feedback
- **INTERNET**: Optional for future cloud sync features
- **BIND_NOTIFICATION_LISTENER_SERVICE**: For app-aware features

## Changelog

### v1.1.0 (2026-01-05)
- Initial release based on FlorisBoard v0.6.0-alpha02
- SwiftKey-inspired Material Design 3 branding
- 4 premium theme packs (Nord, Tokyo Night, Dracula, Catppuccin)
- Encrypted clipboard history feature
- Custom logo and adaptive icons

See [CHANGELOG.md](CHANGELOG.md) for detailed release notes.

## License

```
Copyright 2026 SwiftFloris Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

- **FlorisBoard** team for the solid IME foundation and architecture
- **Jetpack Compose** and **Material Design 3** teams for modern Android UI
- Open-source community for inspiration and contributions

## Status

🚀 **Active Development** — SwiftFloris is under active development. Contributions and feedback are welcome!
