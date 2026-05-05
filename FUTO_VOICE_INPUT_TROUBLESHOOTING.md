# FUTO Voice Input Troubleshooting

SwiftFloris delegates dictation to the external FUTO Voice Input keyboard. SwiftFloris does not record audio, store voice clips, run speech recognition, or choose the active FUTO model. It checks whether a voice IME is available, switches to it, and provides recovery actions when Android reports that FUTO is missing, disabled, or missing microphone access.

## Quick checks

Use this order when voice input does not work:

1. Install FUTO Voice Input from F-Droid or GitHub releases.
2. Enable FUTO Voice Input in Android keyboard settings.
3. Grant Microphone permission to FUTO Voice Input.
4. Open the FUTO app once and download/select the language model you want.
5. Return to SwiftFloris and use the keyboard voice action.

SwiftFloris Settings > Voice input shows the current FUTO readiness state and links to the relevant Android settings screen.

## FUTO is not installed

Symptoms:

- The voice action opens a setup dialog.
- Voice input settings show "FUTO Voice Input is not installed."
- Android never switches to a voice keyboard.

Fix:

1. Install FUTO Voice Input from F-Droid: https://f-droid.org/packages/org.futo.voiceinput/
2. If F-Droid is unavailable, use FUTO releases: https://github.com/FUTO-org/android-voice-input/releases
3. Open SwiftFloris Settings > Voice input and confirm that the status changes.

## FUTO is installed but not enabled

Symptoms:

- SwiftFloris detects FUTO but says it is not enabled.
- Tapping the voice action opens setup instead of dictation.

Fix:

1. Open SwiftFloris Settings > Voice input.
2. Tap Keyboard settings.
3. Enable FUTO Voice Input as an Android keyboard.
4. Return to SwiftFloris.

Android may show a standard keyboard warning when enabling any third-party input method. FUTO performs transcription locally; SwiftFloris only uses Android's IME switching path.

## Microphone permission is denied or revoked

Symptoms:

- FUTO is installed and enabled, but dictation does not start.
- SwiftFloris Settings > Voice input shows "Microphone permission needs attention."
- Voice input previously worked, then stopped after a device restore, permission cleanup, or manual denial.

Fix:

1. Open SwiftFloris Settings > Voice input.
2. Tap FUTO app permissions.
3. Open Permissions > Microphone.
4. Allow microphone access.
5. Return to SwiftFloris and try voice input again.

SwiftFloris does not request `RECORD_AUDIO` because FUTO owns audio capture. If another voice keyboard is enabled, SwiftFloris may fall back to that provider when FUTO cannot be used.

## Language or model is wrong

Symptoms:

- Dictation works but uses the wrong language.
- Accuracy is poor for the selected language.
- FUTO asks to download a model.

Fix:

1. Open SwiftFloris Settings > Voice input.
2. Tap Open FUTO language settings.
3. Select/download the language model inside FUTO.
4. Return to SwiftFloris and start dictation again.

Known limitation: Android's voice IME handoff does not let SwiftFloris force a FUTO language per tap. Language/model selection happens in FUTO.

## Voice action opens the wrong provider

Symptoms:

- A different voice keyboard opens.
- FUTO is installed but another provider handles dictation.

Fix:

1. Confirm FUTO is enabled in Android keyboard settings.
2. Confirm FUTO has microphone permission.
3. Disable other voice input keyboards if you want FUTO to be the only provider.

SwiftFloris prefers FUTO when it is enabled and microphone access is granted. If FUTO is unavailable but another voice IME is enabled, SwiftFloris can use the other provider instead of failing.

## Dictation starts but no text is inserted

Likely causes:

- FUTO did not complete recognition.
- The target app rejected the input connection update.
- The FUTO language model is missing or not loaded.
- Microphone permission changed while FUTO was open.

Fix:

1. Try dictation in a simple text field such as a notes app.
2. Open FUTO directly and confirm it can transcribe there.
3. Reopen SwiftFloris Settings > Voice input and verify the status.
4. Reboot the phone if Android IME switching appears stuck.

If only one app fails to receive text, include the app name and Android version in the bug report.

## Latency expectations

SwiftFloris measures the preflight path before handing control to the voice IME. Current local baselines:

| Target | FUTO state | p50 | p95 | max |
| --- | --- | ---: | ---: | ---: |
| Samsung SM-S938B, Android 16 / API 36 | Installed, enabled, microphone granted | 798us | 1131us | 1261us |
| Medium_Phone_API_36.1 emulator | FUTO missing, other voice provider enabled | 4069us | 8221us | 12239us |

These numbers cover SwiftFloris availability checks and handoff readiness only. They do not include FUTO model loading, speech capture, transcription, or insertion latency.

## Maintainer diagnostics

Useful commands:

```powershell
adb devices
adb shell pm list packages | Select-String -Pattern "futo|voice"
adb shell ime list -s
adb shell dumpsys package org.futo.voiceinput | Select-String -Pattern "RECORD_AUDIO|runtime permissions|granted="
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=dev.patrickgold.florisboard.ime.voice.VoiceInputHandoffLatencyProfileTest" --console=plain
```

The profile logs with tag `VoiceInputProfile` and records device metadata, FUTO install/enable/permission state, setup reason, and latency percentiles.

## Bug report checklist

Include:

- Phone model and Android version.
- SwiftFloris version/commit.
- FUTO Voice Input version.
- Whether FUTO is installed, enabled, and microphone permission is granted.
- Selected FUTO language/model.
- Whether another voice keyboard is enabled.
- Exact behavior after tapping the SwiftFloris voice action.
- Relevant `VoiceInputProfile` or `AndroidRuntime` logcat lines when available.
