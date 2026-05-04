# Voice Input Feature — SwiftFloris v1.3.0+

## Overview

SwiftFloris now includes **voice-to-text input** powered by Android's built-in Speech Recognizer API. No internet required for offline devices with speech recognition capabilities.

## Features

- ✅ **No API Key Required** — Uses Android's system speech recognizer
- ✅ **Multilingual** — Supports all 6 languages (EN, DE, FR, ES, IT, PT)
- ✅ **Real-time Feedback** — Shows partial results as you speak
- ✅ **Confidence Scores** — Visual indicator of recognition confidence
- ✅ **Error Handling** — Clear feedback for network, permission, and audio issues
- ✅ **Material Design 3** — Fully integrated with SwiftFloris UI
- ✅ **Offline Support** — Works on devices with offline speech recognition

## How to Use

### Enable Voice Input

1. **Open Settings** → SwiftFloris Settings
2. **Microphone Permissions** — Grant microphone access when prompted
3. **Activate Mic Button** — The voice input button appears in the keyboard toolbar

### Voice Input States

| State | Meaning |
|-------|---------|
| 🎤 **Idle** | Ready to record (button shows microphone icon) |
| 🔴 **Recording** | Currently listening (red button, pulse animation) |
| ⏳ **Processing** | Analyzing recorded speech |
| ✅ **Ready** | Text recognized, ready to insert |
| ❌ **Error** | Issue occurred (see error message) |

### Recording a Message

1. **Tap the Mic Button** to start recording
2. **Speak Clearly** into the microphone
3. **Wait for silence** (3 seconds) — system auto-stops
   - Or manually **tap again** to stop
4. **See Recognized Text** — appears in the transcription field
5. **Check Confidence** — green (>70%), amber (40-70%), red (<40%)
6. **Tap "Insert Text"** to add recognized text to your message

## Supported Languages

The voice input system automatically detects your keyboard language setting:

| Language | Code | Status |
|----------|------|--------|
| English | en-US | ✅ Full support |
| German | de-DE | ✅ Full support |
| French | fr-FR | ✅ Full support |
| Spanish | es-ES | ✅ Full support |
| Italian | it-IT | ✅ Full support |
| Portuguese | pt-PT / pt-BR | ✅ Full support |

**Accuracy varies by:**
- Device speech recognition engine (usually Google)
- Microphone quality
- Accent and pronunciation
- Background noise levels

## Error Messages & Troubleshooting

### "Microphone permission denied"
- **Fix**: Settings → Apps → SwiftFloris → Permissions → Grant Microphone
- Some Android devices may have app-level mic restrictions

### "Voice input not available"
- **Fix**: Restart your device
- On very old Android (<5.0), speech recognition may not be available
- Some custom ROMs disable speech recognition

### "No speech detected"
- **Cause**: Microphone was inactive during timeout
- **Fix**: Make sure microphone is working (test in Voice Recorder app)
- Try again, speak immediately after tapping mic

### "Network timeout"
- **Cause**: Device lost connection to speech recognition service
- **Fix**: Check internet connectivity (voice recognition works offline on Android 5+, but requires internet for initial setup)
- Try again in a few seconds

### "Audio error"
- **Cause**: Microphone hardware issue or exclusive access denied
- **Fix**: 
  - Close other apps using the microphone
  - Restart SwiftFloris
  - Check if microphone works in other apps

## Performance Notes

| Metric | Value |
|--------|-------|
| Recognition latency | 2-10 seconds (depends on speech duration) |
| Confidence score range | 0-100% |
| Max silence timeout | 3 seconds (auto-stop after silence) |
| Supported audio format | PCM 16-bit 16kHz |

## Implementation Details

### Architecture

```
VoiceInputManager
├── SpeechRecognizer (Android framework)
├── RecognitionListener (events)
├── TranscriptionState (state flow)
├── VoiceError (error handling)
└── Locale support (6 languages)

VoiceInputUI (Compose)
├── VoiceInputButton (full UI)
├── VoiceInputFAB (toolbar button)
└── Real-time animations
```

### Core Classes

**VoiceInputManager** (`ime/voice/VoiceInputManager.kt`)
- Wraps Android `SpeechRecognizer`
- Manages lifecycle (initialize, start, stop, destroy)
- Provides `StateFlow` for reactive UI updates
- Supports language switching

**VoiceInputUI** (`ime/voice/VoiceInputUI.kt`)
- `VoiceInputButton()` — Full UI with transcription display
- `VoiceInputFAB()` — Minimal toolbar button
- Material Design 3 integration
- Animated pulse during recording

### Permissions Required

Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
```

The system prompts users for permission on first use (Android 6+).

## Future Improvements

- [ ] **Voice Commands** — "delete that", "new paragraph", "undo"
- [ ] **Dictation Modes** — Formal, casual, SMS
- [ ] **Voice Feedback** — Audio confirmation of actions
- [ ] **Waveform Visualization** — Show audio levels during recording
- [ ] **Custom Wake Word** — "Hey SwiftFloris"
- [ ] **Offline Models** — On-device speech recognition for privacy

## Privacy & Security

- **No data upload** — Speech is processed locally or via Google's speech API
- **No storage** — Recognized text stays in the input field only
- **Microphone-only** — No camera or other sensor access
- **User control** — Can be disabled in settings

## Testing Checklist

- [ ] Voice input button visible and responsive
- [ ] Recording starts and stops properly
- [ ] Recognized text appears in transcription field
- [ ] Confidence score displays correctly
- [ ] Text inserts into the message field
- [ ] Error messages display appropriately
- [ ] Works across all 6 supported languages
- [ ] Handles no-match scenarios gracefully
- [ ] Respects microphone permissions
- [ ] Works offline (where supported)

## Contributing

Found a bug or have a feature request?
1. Test on multiple devices and Android versions
2. Report at: https://github.com/SysAdminDoc/SwiftFloris/issues
3. Include: Device model, Android version, language, error message

---

**First Introduced**: SwiftFloris v1.3.0
**Last Updated**: 2026-05-04
