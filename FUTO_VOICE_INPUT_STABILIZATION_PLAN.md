# SwiftFloris FUTO Voice Input Stabilization (Phase 0.3)

**Version**: v1.5.0 (Target)  
**Feature**: FUTO Voice Input Graceful Degradation & UX Improvement  
**Status**: Planning  
**Last Updated**: 2026-05-04  

---

## Overview

v1.4.0 integrated FUTO Voice Input successfully. v1.5.0 Phase 0.3 focuses on:
1. **Graceful degradation** when FUTO is not installed
2. **Language pack selection UI** in SwiftFloris settings
3. **Permission handling robustness**
4. **Voice button latency profiling**
5. **Troubleshooting documentation**

---

## Deliverables

### P0.3.1: Graceful Degradation When FUTO Not Installed

**Current State**: Touching the voice button without FUTO installed may crash or show cryptic error.

**Goal**: Friendly UX with installation prompt.

**Implementation Plan**:
```kotlin
// In VoiceInputManager.kt
fun onVoiceButtonPressed() {
    if (!isFutoVoiceInputAvailable()) {
        showFutoNotInstalledDialog()  // New
        return
    }
    // ... proceed with voice input
}

private fun showFutoNotInstalledDialog() {
    // Dialog with:
    // - Friendly message: "FUTO Voice Input not found"
    // - Two buttons: "Install" (opens Play Store/GitHub)
    //              "Dismiss" (close dialog)
    // - Option: "Don't ask again" (store preference)
}
```

**Acceptance Criteria**:
- [ ] Tapping voice button without FUTO → friendly dialog (not crash)
- [ ] Dialog includes install link (F-Droid or GitHub releases)
- [ ] User can dismiss without installing
- [ ] No crashes or ANRs
- [ ] Dialog text is localized (EN, DE, FR, ES, IT, PT at minimum)

**Effort**: 1 week

---

### P0.3.2: Language Pack Selection UI

**Current State**: Users must select language in FUTO app (not in SwiftFloris).

**Goal**: Mirrored language selection in SwiftFloris Settings → Voice Input.

**Implementation Plan**:
```kotlin
// In VoiceInputScreen (Compose preferences)
@Composable
fun VoiceLanguageSelection() {
    // Fetch available FUTO languages via IPC
    val languages = voiceInputManager.getAvailableLanguages()
    
    PreferenceGroup(title = "Voice Input Language") {
        languages.forEach { lang ->
            PreferenceCheckbox(
                title = lang.displayName,  // e.g., "English (US)", "Deutsch"
                state = voiceInputManager.isLanguageEnabled(lang.code),
                onCheckedChange = { enabled ->
                    voiceInputManager.setLanguageEnabled(lang.code, enabled)
                }
            )
        }
    }
}
```

**Acceptance Criteria**:
- [ ] Voice Input Settings screen shows available FUTO languages
- [ ] Users can enable/disable languages per preference
- [ ] Selected languages persist across app restarts
- [ ] Changes reflected in FUTO app (if possible via IPC) or documented limitation
- [ ] Localized language names (not just codes like "en-US")

**Effort**: 1-2 weeks

**Risk**: FUTO IPC protocol not fully documented; may require reverse-engineering or direct collaboration with FUTO team.

---

### P0.3.3: Permission Handling Robustness

**Current State**: RECORD_AUDIO permission handling exists but edge cases untested.

**Goal**: Graceful handling of permission denial, revocation, and re-granting.

**Implementation Plan**:
```kotlin
// In VoiceInputManager.kt
fun onVoiceButtonPressed() {
    when {
        !isFutoInstalled() -> showFutoNotInstalledDialog()
        !hasRecordAudioPermission() -> {
            requestRecordAudioPermission {
                // Callback when permission granted/denied
                if (hasRecordAudioPermission()) {
                    startVoiceInput()
                } else {
                    showPermissionDeniedDialog()
                }
            }
        }
        else -> startVoiceInput()
    }
}

private fun showPermissionDeniedDialog() {
    // Dialog: "Microphone permission required"
    // - Explain: "SwiftFloris needs mic access for voice input"
    // - Buttons: "Allow" (open Settings), "Cancel"
}
```

**Acceptance Criteria**:
- [ ] First tap without permission → system permission prompt
- [ ] If denied, tapping again → friendly explanation dialog
- [ ] Dialog includes "Open Settings" button to grant permission manually
- [ ] Permission revoked in Settings → graceful fallback to tap-only
- [ ] No crashes if permission revoked mid-recording
- [ ] All dialogs localized (EN, DE, FR, ES, IT, PT)

**Effort**: 1 week

---

### P0.3.4: Voice Button Latency Profiling

**Current State**: Voice button tapped → FUTO UI appears (latency unmeasured).

**Goal**: Measure and document end-to-end latency; optimize if >3 seconds.

**Implementation Plan**:
```kotlin
// In VoiceInputManager.kt (add profiling)
fun onVoiceButtonPressed() {
    val startTime = SystemClock.elapsedRealtime()
    
    // ... permission checks, FUTO check, etc.
    
    startFutoVoiceInputActivity()
    
    // Log latency (remove in release build)
    val elapsed = SystemClock.elapsedRealtime() - startTime
    Log.i("VoiceInput", "Voice button tap to FUTO UI: ${elapsed}ms")
}
```

**Measurement Plan**:
1. Tap voice button 10 times
2. Measure time from tap to FUTO UI visible
3. Average the results
4. Profile on 3 device types (flagship, mid-range, budget)
5. Document bottleneck (IPC overhead, FUTO startup, permission dialog, etc.)

**Acceptance Criteria**:
- [ ] Baseline latency measured on 3 device types
- [ ] Results documented in VOICE_INPUT.md with target (<2 seconds)
- [ ] Optimization identified (if latency >2 seconds)
- [ ] Profiling instrumentation in code (can be disabled with build flag)

**Effort**: 1 week

**Target Latency**: <2 seconds (tap to FUTO UI visible)

---

### P0.3.5: FUTO Voice Input Troubleshooting Guide

**Current State**: FUTO integration documented in FUTO_VOICE_INPUT.md.

**Goal**: Comprehensive troubleshooting for common issues.

**Implementation Plan**:
Create new file: `FUTO_VOICE_INPUT_TROUBLESHOOTING.md`

```markdown
# FUTO Voice Input Troubleshooting

## Issue: "Failed to find voice IME"
Symptom: Tapping voice button shows error "Failed to find voice IME"
Cause: FUTO Voice Input not installed or not registered as active IME
Fix:
1. Install FUTO Voice Input from F-Droid or GitHub
2. Go to Settings → Languages & Input → Virtual Keyboard
3. Ensure "FUTO Voice Input" is listed and enabled
4. Select FUTO Voice Input as the active input method

## Issue: Microphone Not Working
Symptom: FUTO UI appears but no speech is captured
Cause: Microphone permission not granted, or device microphone offline
Fix:
1. Check Settings → Apps → SwiftFloris → Permissions → Microphone
2. Grant "Allow" permission
3. Test microphone in another app (e.g., Google Assistant)
4. If mic works elsewhere, try restarting SwiftFloris

## Issue: Latency > 5 seconds
Symptom: Speaking takes forever to transcribe
Cause: Network latency, device load, silence timeout too long
Fix:
1. Check internet connection (if using cloud Whisper)
2. Close background apps (Settings → Running apps)
3. Check FUTO settings for silence timeout (reduce if too long)
4. Try shorter phrases first

## Issue: Language Not Recognized
Symptom: Selected language not transcribing correctly
Cause: Language pack not installed, or language not selected
Fix:
1. In FUTO app, download language pack (Settings → Languages)
2. In SwiftFloris, ensure language is selected (Settings → Voice Input → Language)
3. Restart FUTO and SwiftFloris
4. Try in a different app to isolate issue

## Issue: Crash When Tapping Voice Button
Symptom: SwiftFloris crashes when tapping voice button
Cause: FUTO not installed, or IPC error
Fix:
1. Verify FUTO Voice Input is installed
2. Reinstall FUTO Voice Input (clear data if needed)
3. Uninstall and reinstall SwiftFloris
4. Report issue with logcat output to GitHub Issues
```

**Acceptance Criteria**:
- [ ] All 5+ common issues documented with symptoms, causes, fixes
- [ ] Troubleshooting guide linked from README.md and FUTO_VOICE_INPUT.md
- [ ] User feedback loop established (GitHub Discussions for questions)
- [ ] Periodic updates as users report new issues

**Effort**: 1 week

---

## Implementation Timeline

| Phase | Task | Effort | Dependencies | Status |
|-------|------|--------|--------------|--------|
| P0.3.1 | Graceful degradation | 1 wk | None | ⏳ Pending |
| P0.3.2 | Language pack UI | 1-2 wks | P0.3.1 | ⏳ Pending |
| P0.3.3 | Permission handling | 1 wk | None | ⏳ Pending |
| P0.3.4 | Latency profiling | 1 wk | None | ⏳ Pending |
| P0.3.5 | Troubleshooting guide | 1 wk | P0.3.1-4 | ⏳ Pending |
| **Total** | **Phase 0.3** | **5-6 wks** | | **→ v1.5.0** |

---

## Success Metrics

**v1.5.0 acceptance criteria**:
- [ ] FUTO not installed → friendly dialog with install link (not crash)
- [ ] Users can select languages in SwiftFloris Settings
- [ ] Permission denied → graceful fallback to tap-only
- [ ] Voice button latency <2 seconds (baseline measured)
- [ ] Troubleshooting guide answers 5+ common issues
- [ ] No regressions in existing voice input functionality
- [ ] All dialogs localized (EN, DE, FR, ES, IT, PT minimum)

---

## Risk Mitigation

| Risk | Severity | Mitigation |
|------|----------|-----------|
| FUTO IPC protocol undocumented | Medium | Contact FUTO team; use Intent-based fallback |
| Permission handling edge cases | Low | Comprehensive testing on multiple Android versions |
| Latency optimization difficult | Low | May accept measured baseline if <2 seconds |
| User feedback loops not established | Medium | Create GitHub Discussions category for voice input |

---

## Related Documents

- **FUTO_VOICE_INPUT.md** — Integration guide (user-facing)
- **VOICE_INPUT.md** — Voice input overview (technical)
- **GESTURE_TYPING_DEVICE_COMPAT.md** — Device testing report
- **ROADMAP.md** — Project-level roadmap (Phase 0.3 section)

---

## Sign-Off

**Status**: Planning Complete  
**Lead**: Autonomous Development Agent  
**Target Release**: v1.5.0 (Q2 2026, pending Phase 0.2 completion)  
**Next Step**: Execute P0.3.1 (Graceful Degradation)

---

**Document Version**: 1.0  
**Last Updated**: 2026-05-04  
**Created**: 2026-05-04
