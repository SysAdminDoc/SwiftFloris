# SwiftFloris Changelog

All notable changes to SwiftFloris are documented in this file.

## [1.3.0] — 2026-05-04

### 🎤 Voice Input & Multilingual Features

**Added**
- **Voice-to-Text Input**: Full speech recognition integration
  - Android Speech Recognizer API (no API key required)
  - Works offline on compatible devices
  - Real-time transcription with confidence scores
  - Animated UI with pulse effect during recording
  - Support for all 6 languages (EN, DE, FR, ES, IT, PT)
  - Comprehensive error handling with user-friendly messages

- **Documentation & Testing**
  - MULTILINGUAL.md — Testing guide for spell checking across 6 languages
  - VOICE_INPUT.md — Voice feature guide with troubleshooting
  - Updated README with voice input feature description

**Technical**
- `VoiceInputManager` — Lifecycle management and state handling
- `VoiceInputUI` — Compose components (full button + FAB) with animations
- Permission: `RECORD_AUDIO` added to manifest
- Automatic language detection from keyboard subtype

**Status**: Ready for device testing

## [1.2.0] — 2026-05-04

### 🎯 Typing Logic Parity with SwiftKey

**Added**
- **Smart Auto-Capitalization**: Intelligent sentence capitalization that matches SwiftKey's behavior
  - Automatically capitalizes the first letter of sentences after `.`, `!`, and `?`
  - Properly handles multiple sentences in succession
  - Respects user's auto-cap settings
  - Fixed state machine to prevent capitalization loss after sentence-ending punctuation

**Fixed**
- Auto-capitalization now works correctly for second and subsequent sentences
  - Previously, only the first sentence would capitalize; subsequent sentences would fail
  - Root cause: `reevaluateInputShiftState()` was resetting the `SHIFTED_AUTOMATIC` state during the async character commit flow
  - Solution: Preserve `SHIFTED_AUTOMATIC` state during re-evaluation; let it be consumed naturally after character commit

**Technical Details**
- Modified `KeyboardManager.reevaluateInputShiftState()` to preserve `SHIFTED_AUTOMATIC` state
- This prevents async callbacks from interfering with the shift state machine
- State is now correctly consumed in `onInputKey()` after a letter is typed with auto-cap active

### 🎨 Branding & Visual Polish
- Professional SwiftFloris icon throughout the app
- Consistent Material Design 3 theming
- Haptic feedback enhanced (70% strength, 65ms duration)

### 📱 Build & Release
- Version: 1.2.0 (versionCode 120)
- Target Android SDK: 36 (Android 15)
- Min SDK: 26 (Android 8.0 Oreo)

---

## [1.1.0] — 2026-04-XX

### ✨ Complete Visual Overhaul & Customization

**Added**
- **Premium Branding**: Professional SwiftFloris logo and icon assets
- **Four Custom Themes**:
  - Nord (Arctic, north-bluish palette)
  - Tokyo Night (Neon-inspired vibrant colors)
  - Dracula (Dark, elegant purple/pink)
  - Catppuccin Mocha (Warm, modern aesthetic)
- **Encrypted Clipboard History**: Secure storage of recent pastes with Material 3 UI
- **Enhanced Haptic Feedback**: Stronger vibration on keypresses (configurable)

**Improved**
- Comprehensive app name and icon updates throughout codebase
- Splash screen displays correct branding
- All Material Design 3 components properly themed

---

## Base: FlorisBoard v0.6.0-alpha02

SwiftFloris is built on FlorisBoard's proven, production-tested IME service, inheriting:
- Multi-language support
- Full accessibility features
- Jetpack Compose architecture
- Material Design 3 framework
- Secure input handling
