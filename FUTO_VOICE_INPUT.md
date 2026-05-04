# FUTO Voice Input Integration

SwiftFloris now uses **FUTO Voice Input** for on-device, privacy-respecting speech-to-text.

## What is FUTO Voice Input?

FUTO Voice Input is an open-source speech recognition engine built on OpenAI's Whisper model. It:

- ✅ **Runs entirely on-device** — no internet connection needed
- ✅ **100% offline** — no data is sent to servers
- ✅ **Privacy-first** — completely private transcription
- ✅ **Lightweight** — efficient local models (TinyWhisper, Whisper.cpp)
- ✅ **16+ languages** — supports English, Chinese, German, Spanish, French, Italian, Dutch, etc.

## Installation

### Step 1: Install FUTO Voice Input

Choose one:

**Option A: Google Play Store (easiest)**
- Open [FUTO Voice Input on Play Store](https://play.google.com/store/apps/details?id=org.futo.voiceinput)
- Tap **Install**
- Done!

**Option B: F-Droid (privacy-friendly)**
- Add F-Droid repository if you haven't: https://f-droid.org/
- Search for "FUTO Voice Input"
- Tap **Install**

**Option C: Manual APK (direct download)**
- Download from [FUTO Voice Input website](https://voiceinput.futo.org/)
- Install via ADB: `adb install org.futo.voiceinput.apk`

### Step 2: Use the Microphone Button

1. Open any text field in SwiftFloris
2. Tap the **microphone button** (purple circle, bottom-right of keyboard)
3. FUTO Voice Input launches automatically
4. Speak clearly — wait for the transcription to complete
5. Tap **Insert** to add the text to your message

## Supported Languages

- **English, Mandarin, German, Spanish, Russian, French**
- **Portuguese, Korean, Japanese, Turkish, Polish, Italian**
- **Swedish, Dutch, Catalan, Finnish, Indonesian**

(More languages available in FUTO Voice Input settings)

## Troubleshooting

### "Voice Input Unavailable"
**Cause:** FUTO Voice Input is not installed.
**Fix:** Install it from Play Store (see Installation above).

### Microphone button is greyed out
**Cause:** Device doesn't have a microphone or audio is disabled.
**Fix:** Check device settings → Apps → Permissions → Microphone access.

### Poor transcription quality
**Tip:** FUTO Voice Input works best with:
- Clear, natural speech (not shouting)
- Quiet background
- One speaker at a time
- Pausing between sentences

### FUTO Voice Input crashes
- Update to the latest version from Play Store
- Report issues: [FUTO Voice Input GitHub](https://github.com/futo-org/voice-input/issues)

## How It Works

1. **Tap the mic button** → SwiftFloris opens FUTO Voice Input
2. **Speak** → FUTO transcribes locally using Whisper.cpp
3. **Result is pasted** → Text is automatically inserted into your text field
4. **No cloud calls** → Everything stays on your device

## FAQ

**Q: Does it require internet?**
A: No. FUTO Voice Input is entirely offline. All processing happens on your phone.

**Q: Where does my audio go?**
A: Nowhere. Audio is processed locally and never stored or transmitted.

**Q: Does it support my language?**
A: FUTO supports 16+ languages based on OpenAI Whisper. Check FUTO settings for your language.

**Q: Can I use this with other keyboards?**
A: Yes! FUTO Voice Input works with FlorisBoard, HeliBoard, AnySoftKeyboard, and others that support the IME voice subtype.

**Q: Is it free?**
A: Yes! FUTO Voice Input is free and open-source. You can optionally donate to support development.

## Architecture

### Integration Method

SwiftFloris uses FUTO Voice Input via the **IME voice subtype mode**:

```xml
<!-- method.xml -->
<subtype
    android:label="@string/app_name"
    android:imeSubtypeMode="voice"
    android:isAsciiCapable="true"/>
```

This tells Android that FlorisBoard supports voice input via an external provider.

### Code Changes

- **VoiceInputManager.kt** — Detects FUTO availability and launches it via intent
- **VoiceInputButton.kt** — Purple microphone button that opens FUTO Voice Input
- **method.xml** — Added voice subtype declaration to AndroidManifest configuration

### Why FUTO Instead of Google Speech Recognizer?

1. **Privacy** — No internet, no tracking
2. **Reliability** — Works offline, independent of Google Play Services
3. **Simplicity** — Delegates to a dedicated app, no complex API integration
4. **User choice** — Users can upgrade/switch FUTO without app updates

## Links

- **FUTO Voice Input:** https://voiceinput.futo.org/
- **GitHub:** https://github.com/futo-org/voice-input
- **SwiftFloris Repo:** https://github.com/SysAdminDoc/SwiftFloris

---

**Version:** SwiftFloris v1.5.0+  
**Last Updated:** May 4, 2025
