# Phase 4.1: Voice Input Latency Optimization — Implementation Plan

**Status**: ✅ Instrumentation complete, ready for device testing  
**Version**: v1.4.0-profiling (debug APK with logging)  
**Target**: <5s end-to-end voice input latency  

---

## What Was Done (This Session)

### 1. Created Profiling Documentation
- **VOICE_INPUT_PROFILING.md** — Comprehensive guide with:
  - Latency breakdown model (6 stages)
  - Measurement methodology (3 phases)
  - Test corpus (10 common phrases)
  - Expected results and success criteria
  - Device test log template
  - Reference documentation for Android Speech Recognizer

### 2. Added Profiling Instrumentation to VoiceInputManager
- Added 3 profiling fields: `recognitionStartTime`, `partialResultTime`, `resultReceiveTime`
- Added Log.d() calls at key lifecycle points:
  - `startListening()` — T0 timestamp
  - `onReadyForSpeech()` — Audio system ready
  - `onBeginningOfSpeech()` — User started speaking
  - `onEndOfSpeech()` — User stopped speaking (silence detected)
  - `onPartialResults()` — Intermediate results (real-time feedback latency)
  - `onResults()` — T1 timestamp + total latency calculation + result text + confidence

### 3. Built Debug APK
- Compiled successfully with profiling instrumentation
- APK ready: `app/build/outputs/apk/debug/app-debug.apk`
- Build time: 5 seconds (fast incremental build)

---

## Next Steps (Device Testing Phase)

### Immediate (When Device Available)
1. Install debug APK on device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. Enable Logcat filtering:
   ```bash
   adb logcat | grep VoiceProfiler
   ```

3. Run profiling tests (5–10 minutes per device):
   - Test with 10 common phrases from VOICE_INPUT_PROFILING.md
   - Test on WiFi and LTE (if available)
   - Record latencies, accuracy, network conditions
   - Use device test log template from profiling guide

4. Analyze results:
   - Average latency
   - Bottleneck identification (network, device, silence timeout)
   - Success rate (% results within 5s target)
   - Accuracy metrics

### Analysis Phase (1–2 hours)
- Review Logcat output for latency breakdown
- Determine if optimization is needed
- Decide strategy (silence timeout reduction, early termination, etc.)

### Optimization Phase (If Needed)
Depending on bottleneck:

**If silence detection timeout is bottleneck**:
- Modify `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` from 3000ms to 1500–2000ms
- Add manual "Stop" button for early termination
- Re-test accuracy impact

**If network is bottleneck**:
- Parallelize UI updates while waiting for final confidence
- Implement timeout bypass (use partial result if no final result after 3s)
- Add network quality indicator to UI

**If device/audio buffer is bottleneck**:
- Cache audio stream objects
- Profile with Android Profiler (CPU, memory)
- Optimize dictation/encoding parameters

### Release Plan (If Changes Made)
1. Implement optimizations
2. Re-test on device
3. Update VOICE_INPUT.md with new latency metrics
4. Update ROADMAP.md Phase 4.1 status
5. Release as v1.4.1 patch (if improvements are significant, >10% latency reduction)
6. Commit and push

---

## Success Criteria

**Baseline measurement** (this session):
- ✅ Instrumentation in place
- ✅ Debug APK built and ready
- ⏳ Device testing pending

**Device testing success**:
- ✅ Captures latency data without crashes
- ✅ Logcat output is clear and parseable
- ✅ Results collected for all test phrases

**Optimization success** (post-testing):
- >95% of results within 5s (or match baseline if already <5s)
- >85% results with confidence >0.8
- <5% false positive rate
- Accuracy maintained or improved

---

## Files Modified This Session

| File | Change | Lines |
|------|--------|-------|
| VoiceInputManager.kt | Added profiling instrumentation | +30 |
| (new) VOICE_INPUT_PROFILING.md | Comprehensive profiling guide | 230 lines |
| (session plan) plan.md | Updated Phase 4.1 entry | — |

---

## Logcat Output Example (Expected)

```
2026-05-05 10:23:45.123  D/VoiceProfiler: Recognition started at 1746439425123
2026-05-05 10:23:45.456  D/VoiceProfiler: onReadyForSpeech called at 333ms
2026-05-05 10:23:45.789  D/VoiceProfiler: User started speaking at 666ms
2026-05-05 10:23:47.234  D/VoiceProfiler: User stopped speaking at 2111ms
2026-05-05 10:23:48.100  D/VoiceProfiler: Partial result received at 2977ms
2026-05-05 10:23:48.100  D/VoiceProfiler: Partial text: 'hello'
2026-05-05 10:23:49.567  D/VoiceProfiler: Results received. Total latency: 4444ms, Start: 1746439425123, End: 1746439429567
2026-05-05 10:23:49.567  D/VoiceProfiler: Result text: 'hello world' (confidence: 0.92)
```

**Analysis**: 4444ms total latency (well within 5s target)
- User spoke at 666ms, stopped at 2111ms (spoke for ~1.4 seconds)
- Silence detection added ~0.8s (2.9s until partial)
- Network/processing added ~1.5s (partial at 2.9s, final at 4.4s)
- Bottleneck: Network/server processing (typical for speech recognizer)

---

## Notes for Continuation

**If I'm not available when device testing happens:**
- Run the profiling tests yourself using VOICE_INPUT_PROFILING.md
- Collect Logcat output and save to file
- Share results for analysis
- I can then determine if optimization is needed and implement accordingly

**If multiple devices can be tested:**
- Priority: Pixel 4a (reference), then Samsung Galaxy A51 or similar (mid-range)
- Compare latencies across devices (indicates device-specific vs. network-bound bottlenecks)
- If Pixel is fast but Samsung is slow: device bottleneck
- If both are slow: network bottleneck

**If latency is already <5s on baseline:**
- No optimization needed for v1.4.0
- Mark Phase 4.1 as VERIFIED instead of OPTIMIZED
- Focus next effort on Phase 5.1 (F-Droid release) or Phase 1.3 (Voice commands)

