# SwiftFloris

![Version](https://img.shields.io/badge/version-v1.2.0-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green) ![Platform](https://img.shields.io/badge/platform-Android%208.0+-orange)

**SwiftFloris** is an open-source Android keyboard built on FlorisBoard with intelligent typing features, multiple themes, and a clean Material Design 3 interface.

## About SwiftFloris

SwiftFloris focuses on what matters:
- **Smart Typing Logic**: Auto-capitalization, spell checking, and word suggestions that just work
- **Lightweight & Fast**: Built on FlorisBoard's proven, production-tested IME core
- **Clean Interface**: Material Design 3 with 4 curated theme options
- **Privacy-First**: Local spell checking and suggestions, no cloud dependency
- **Customizable**: Multiple themes and keyboard layouts

## Features

### Intelligent Typing
- ✨ **Auto-Capitalization**: Intelligently capitalizes the first letter of sentences and after punctuation (`.`, `!`, `?`)
- 🔤 **Spell Checking & Autocorrect**: Dictionary-based error detection with word suggestions
- 📚 **Word Suggestions**: Context-aware word predictions as you type
- ⌨️ Responsive, low-latency key detection
- 🌐 Multi-language support (inherited from FlorisBoard)
- ♿ Full accessibility support

### Customization
- 🎨 **4 Theme Packs**: Nord, Tokyo Night, Dracula, Catppuccin Mocha
- 🌓 Dark/light mode support
- 📋 **Clipboard History**: Quick access to recent copied items (encrypted storage)
- 🎯 Customizable keyboard layouts and settings
- 🔊 Haptic feedback (configurable strength)

### Design & UX
- Material Design 3 throughout
- Adaptive app icons
- Touch-optimized layouts
- Smooth animations and transitions

### Themes Included

1. **Nord** — Arctic, north-bluish color palette
2. **Tokyo Night** — Neon-inspired vibrant colors
3. **Dracula** — Dark, elegant purple and pink scheme
4. **Catppuccin Mocha** — Warm, modern aesthetic

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

See [CHANGELOG.md](CHANGELOG.md) for detailed release notes.

### v1.2.0 (2026-05-04)
- Auto-capitalization now works correctly for multiple consecutive sentences
- Fixed state machine bug preventing second-sentence capitalization
- All typing logic matches standard keyboard behavior

### v1.1.0 (2026-04-XX)
- SwiftFloris branding and custom app icon
- 4 theme packs (Nord, Tokyo Night, Dracula, Catppuccin)
- Encrypted clipboard history
- Enhanced haptic feedback
- Based on FlorisBoard v0.6.0-alpha02

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
