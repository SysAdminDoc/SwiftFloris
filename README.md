# SwiftFloris

![Version](https://img.shields.io/badge/version-v1.8.19-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green) ![Platform](https://img.shields.io/badge/platform-Android%208.0+-orange)

**SwiftFloris** is a privacy-first Android keyboard app inspired by Microsoft SwiftKey, built on FlorisBoard's proven IME architecture with modern Material Design 3, offline gesture typing, and secure voice input.

> **Zero cloud processing. Zero telemetry. All features work offline.**

## Key Highlights

| Feature | Capability | Privacy |
|---------|-----------|---------|
| **Gesture Typing** | Drag to type entire words | 100% offline |
| **Voice Input** | Speech-to-text with FUTO | 100% offline, no cloud |
| **Spell Check** | Multi-language corrections | Local dictionary only |
| **Clipboard** | Encrypted history (50 items) | AES-256 GCM, on-device only |
| **Themes** | 4 professional color schemes | No telemetry |

## Features

### 🎤 Voice Input (v1.5.0)

Powered by **FUTO Voice Input** — a privacy-respecting, on-device speech recognizer:

- ✅ **100% Offline** — No internet required, no audio uploaded
- ✅ **16+ Languages** — EN, DE, FR, ES, IT, PT, JP, KR, ZH, RU, PL, SV, NL, TR, and more
- ✅ **Based on Whisper** — OpenAI's open-source speech model
- ✅ **Works on Debloated ROMs** — Independent of Google Play Services
- ✅ **Lightweight** — Efficient local processing on any Android device

**Setup**: [FUTO Voice Input Guide](FUTO_VOICE_INPUT.md)

### 🎯 Gesture/Swipe Typing (v1.4.0)

Type entire words by dragging your finger across the keyboard:

- ✅ **Live word prediction** during gesture motion
- ✅ **Configurable sensitivity** (0-100% adjustment)
- ✅ **Visual trail** with fade animation
- ✅ **Fully offline** — no API calls, completely private
- ✅ **Intelligent corrections** with automatic backspace handling

**Guide**: [Gesture Typing Best Practices](GESTURE_TYPING.md)

### ⌨️ Core Typing Features

- **Auto-Capitalization** — Intelligent sentence-aware capitalization after `.`, `!`, `?`
- **Spell Checking** — Edit-distance algorithm (Levenshtein distance ≤2)
- **Word Suggestions** — Context-aware predictions with bigram analysis
- **Multiple Layouts** — QWERTY, QWERTZ, AZERTY, and locale-specific
- **Haptic Feedback** — Enhanced vibration with user adjustment
- **Multi-Language** — Full support for 100+ languages via FlorisBoard

### 📋 Clipboard History

Encrypted, secure clipboard with quick-access:

- **AES-256 GCM encryption** — Military-grade protection
- **Per-app tracking** — See which app each clip came from
- **Max 50 items** — Auto-cleanup of old entries
- **One-tap insert** — Quickly paste recent clips
- **Local storage only** — No cloud sync, no network access

### 🎨 Themes (4 Included)

1. **Nord** — Arctic, north-bluish palette
2. **Tokyo Night** — Neon-inspired vibrant colors
3. **Dracula** — Dark, elegant purple and pink
4. **Catppuccin Mocha** — Warm, modern aesthetic

All themes support:
- Dark/light mode auto-switching
- Material Design 3 adaptive colors
- Custom accent colors
- Full theme editor for fine-tuning

### ⚙️ Customization

**Keyboard Settings**
- Keyboard layouts and subtypes
- Key size and spacing
- Sound and vibration feedback
- Gesture detection sensitivity
- Dictionary management

**Display & Appearance**
- 4 theme packs with variants
- Light/dark mode toggle
- Adaptive icon system
- Custom gesture trail duration

**Advanced**
- Clipboard history encryption
- Physical keyboard support
- Theme editor (create custom themes)
- Language pack manager
- Backup and restore (full settings export)

## Installation

### Option A — Obtainium (recommended for auto-updates)

[Obtainium](https://github.com/ImranR98/Obtainium) tracks GitHub Releases directly and notifies you the moment a new SwiftFloris APK ships — no Play Store, no F-Droid mirror lag, no manual polling.

**One-tap subscribe:**

```
obtainium://app/{"id":"dev.patrickgold.florisboard","url":"https://github.com/SysAdminDoc/SwiftFloris","author":"SysAdminDoc","name":"SwiftFloris","preferredApkIndex":0,"additionalSettings":"{\"includePrereleases\":false,\"fallbackToOlderReleases\":true,\"trackOnly\":false,\"versionDetection\":true,\"apkFilterRegEx\":\"app-release.*\\\\.apk\"}"}
```

Open the link above on a device with Obtainium installed (or paste it into Obtainium's "Add app from URL" field). Obtainium will subscribe to this repository's GitHub Releases feed and auto-prompt for installs on each new tag.

### Option B — GitHub Releases (manual)

1. Download the latest APK from [Releases](https://github.com/SysAdminDoc/SwiftFloris/releases)
2. Install on your Android device (Android 8.0+)
3. **Also install FUTO Voice Input** for voice input feature:
   - [Google Play Store](https://play.google.com/store/apps/details?id=org.futo.voiceinput)
   - [F-Droid](https://f-droid.org/app/org.futo.voiceinput)
   - [FUTO Website](https://voiceinput.futo.org/)

### Option C — Manual Build

```bash
git clone https://github.com/SysAdminDoc/SwiftFloris.git
cd SwiftFloris
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Enable as Default Keyboard

1. Open **Settings** → **System** → **Languages & input**
2. Tap **Virtual keyboard** (or **On-screen keyboard**)
3. Select **SwiftFloris**
4. Grant permissions as prompted

> **Tip**: Grant **RECORD_AUDIO** permission to use voice input with FUTO Voice Input

## Documentation

- **[FUTO Voice Input Setup](FUTO_VOICE_INPUT.md)** — Complete voice input guide with troubleshooting
- **[Gesture Typing Guide](GESTURE_TYPING.md)** — Swipe typing tips, best practices, FAQ
- **[Complete Features](FEATURES.md)** — Detailed feature inventory and implementation status
- **[Multilingual Support](MULTILINGUAL.md)** — Multi-language spell checking guide
- **[Changelog](CHANGELOG.md)** — Release notes for all versions

## Architecture & Stack

**Technology**
- **Jetpack Compose** — Declarative, reactive UI
- **Material Design 3** — Modern Android design system
- **Kotlin + Coroutines** — Async operations, type-safe
- **Room Database** — Settings and preferences persistence
- **Encrypted SharedPreferences** — Secure storage for sensitive data

**Minimum Requirements**
- **Android 8.0+ (SDK 26)**
- **Min Target: Android 15 (SDK 36)**
- **Gradle 8.x**, Java 17+, Kotlin 2.0+

## Building

### Prerequisites
```bash
# Install Android SDK 26+ (Android Studio)
# Install Java 17 or later
# Install Gradle 8.x (included with Android Studio)
```

### Build Commands
```bash
# Debug build (for testing)
./gradlew assembleDebug

# Release build (unsigned)
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run tests
./gradlew test
```

**Signed Release Build**
```bash
# Requires keystore.jks with proper alias and password
./gradlew assembleRelease -Pandroid.injected.signing.store.file=keystore.jks \
  -Pandroid.injected.signing.store.password=$STORE_PASS \
  -Pandroid.injected.signing.key.alias=$KEY_ALIAS \
  -Pandroid.injected.signing.key.password=$KEY_PASS
```

## Permissions

SwiftFloris requests these Android permissions:

| Permission | Purpose | Required? |
|-----------|---------|-----------|
| `INPUT_METHOD` | IME functionality | ✅ Yes |
| `VIBRATE` | Haptic feedback | Optional |
| `RECORD_AUDIO` | Voice input with FUTO | Optional (needed for voice) |
| `BIND_NOTIFICATION_LISTENER` | App-aware features | Optional |

> **Privacy note**: SwiftFloris never connects to the internet. All permissions are used locally on your device.

## Privacy & Security

### Data Handling
- ✅ **No cloud sync** — Everything stays on your device
- ✅ **No telemetry** — No usage tracking or analytics
- ✅ **No ads** — Open-source, no advertising
- ✅ **No accounts** — No login or registration needed
- ✅ **Offline-first** — Works without internet connection

### Encryption
- **Clipboard history** — AES-256 GCM encryption
- **Settings** — Encrypted SharedPreferences
- **Voice input** — Handled entirely by FUTO (offline processing)

### Open Source
- [GitHub](https://github.com/SysAdminDoc/SwiftFloris) — Full source code available
- [Apache License 2.0](LICENSE) — Permissive, community-friendly license
- Auditable — Security researchers can review the code

## Performance

Typical latencies on mid-range Android device:

| Operation | Latency | Notes |
|-----------|---------|-------|
| Auto-capitalization | <5ms | No I/O required |
| Spell check suggestion | <100ms | Dictionary cache |
| Word prediction | <50ms | LRU + bigram cache |
| Gesture recognition | <200ms | End-to-end |
| Voice transcription | 2-10s | Depends on FUTO model |
| Clipboard paste | <200ms | Depends on text size |

## Testing

### What's Tested
- ✅ Auto-capitalization (sentence-aware)
- ✅ Spell checking and suggestions
- ✅ Gesture typing (all directions)
- ✅ Voice input (with FUTO)
- ✅ Clipboard encryption/decryption
- ✅ Theme switching (all 4 themes)
- ✅ Haptic feedback

### Device Tested On
- **Android 16** (Samsung device, debloated ROM)
- **Google Pixel** (recommended for full compatibility)

## Project Structure

```
SwiftFloris/
├── app/
│   ├── src/main/
│   │   ├── kotlin/dev/patrickgold/florisboard/
│   │   │   ├── FlorisApplication.kt       (App entry point)
│   │   │   ├── FlorisImeService.kt        (IME service)
│   │   │   ├── ime/
│   │   │   │   ├── keyboard/              (Layout & rendering)
│   │   │   │   ├── input/                 (Input processing)
│   │   │   │   ├── clipboard/             (History feature)
│   │   │   │   ├── voice/                 (Voice input)
│   │   │   │   ├── nlp/                   (Spell check, predictions)
│   │   │   │   └── theme/                 (Theming system)
│   │   │   └── app/
│   │   │       ├── FlorisAppActivity.kt   (Settings activity)
│   │   │       └── settings/              (Settings screens)
│   │   └── res/
│   │       ├── drawable/                  (Icons, logos)
│   │       └── values/                    (Colors, strings, themes)
│   └── build.gradle.kts
├── settings.gradle.kts
├── CHANGELOG.md                            (Release notes)
├── FEATURES.md                             (Detailed features)
├── FUTO_VOICE_INPUT.md                     (Voice input guide)
├── GESTURE_TYPING.md                       (Gesture typing guide)
└── README.md                               (This file)
```

## Contributing

We welcome contributions! Areas for enhancement:

- 🎨 Additional themes and color schemes
- 🌍 New language support for spell checking
- 🔧 Performance optimizations
- 🐛 Bug fixes and stability improvements
- 📚 Documentation and guides
- ♿ Accessibility improvements

**How to contribute**:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Test on a real device
5. Submit a pull request with a clear description

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

## Troubleshooting

### Gesture typing not working?
→ See [Gesture Typing Guide](GESTURE_TYPING.md#troubleshooting)

### Voice input unavailable?
→ See [FUTO Voice Input Guide](FUTO_VOICE_INPUT.md#troubleshooting)
- Ensure FUTO Voice Input is installed from Play Store or F-Droid
- Check microphone permissions in Settings
- Verify your ROM hasn't removed the speech recognizer

### Keyboard crashes or locks up?
- Clear app cache: Settings → Apps → SwiftFloris → Storage → Clear Cache
- Reinstall the app
- Check device storage (need ~100MB free)

### Spell checking not working?
- Ensure dictionary is loaded: Settings → Languages → Dictionary Manager
- Check language is enabled for your locale
- Enable spell checking in Settings → Typing

### Theme changes not applying?
- Force close the keyboard app
- Open a new text field to reload the keyboard
- Try a different theme to test

## Changelog

**[v1.5.0](CHANGELOG.md#150--2026-05-04)** — May 4, 2026
- ✅ FUTO Voice Input integration (100% offline, 16+ languages)
- ✅ Removed Google Speech Services (no longer needed)
- ✅ Added comprehensive voice input documentation

**[v1.4.0](CHANGELOG.md#140--2026-05-05)** — Gesture Typing
- ✅ Gesture/swipe typing enabled by default
- ✅ Configurable sensitivity (0-100%)
- ✅ Visual trail with fade animation
- ✅ Comprehensive gesture typing guide

**[v1.3.0](CHANGELOG.md#130--2026-05-04)** — Voice Input & Multilingual
- ✅ Voice-to-text input integration
- ✅ Multi-language spell checking infrastructure
- ✅ Enhanced haptic feedback

**[v1.2.0](CHANGELOG.md#120--2026-05-04)** — Auto-Capitalization Fix
- ✅ Fixed auto-cap for subsequent sentences
- ✅ Proper SHIFTED_AUTOMATIC state handling
- ✅ Production-ready v1.2.0 release

**[v1.1.0](CHANGELOG.md#110--2026-01-05)** — Initial Release
- ✅ FlorisBoard foundation
- ✅ SwiftKey-inspired branding
- ✅ 4 premium themes
- ✅ Encrypted clipboard history

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

- **FlorisBoard** — Solid IME architecture and foundation
- **FUTO** — Privacy-first voice input technology
- **Jetpack Compose** and **Material Design 3** — Modern Android UI
- **OpenAI Whisper** — Speech recognition engine (used by FUTO)
- Open-source community — Inspiration and contributions

## Status

🚀 **Active Development** — SwiftFloris is actively maintained with regular updates.

**v1.5.0** is stable and ready for production use. We welcome feedback, bug reports, and contributions!

---

## Quick Links

| Resource | Link |
|----------|------|
| **GitHub** | https://github.com/SysAdminDoc/SwiftFloris |
| **Issues** | https://github.com/SysAdminDoc/SwiftFloris/issues |
| **Releases** | https://github.com/SysAdminDoc/SwiftFloris/releases |
| **FUTO Voice** | https://voiceinput.futo.org/ |
| **FlorisBoard** | https://github.com/florisboard/florisboard |

**Made with ❤️ for privacy and offline-first computing**
