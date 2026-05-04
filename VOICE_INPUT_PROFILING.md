# Voice Input Latency Profiling — SwiftFloris v1.4.0

**Objective**: Measure and optimize voice input end-to-end latency (speak → text inserted) to achieve <5s target.

**Target Devices**: 
- Pixel 4a (reference flagship)
- Samsung Galaxy A51 or similar (mid-range)
- Moto G7 or similar (budget)

---

## Latency Breakdown Model

Expected pipeline (Android Speech Recognizer):

```
User speaks → Audio capture → Audio buffering → Network transmission
→ Server processing (Google/vendor speech engine) → Network response 
→ Confidence scoring → Text insertion in EditText
```

**Breakdown (estimated)**:
1. Audio capture + buffering: 500ms – 1000ms (user speaks)
2. Silence detection timeout: 0 – 3000ms (waiting for speech end)
3. Network transmission: 500ms – 2000ms (WiFi/LTE dependent)
4. Server processing: 1000ms – 2000ms (speech engine processing)
5. Response transmission: 100ms – 500ms
6. Confidence scoring + insertion: 100ms – 500ms

**Total estimate**: 2.2s – 9.0s depending on device and network

**Design target**: <5s (achievable with optimization)

---

## Measurement Methodology

### Phase 1: Baseline Profiling (3–4 hours)

**Setup**:
1. Build v1.4.0-debug APK
2. Install on Pixel 4a + mid-range device
3. Enable Android Profiler (or use Logcat timestamps)
4. Test corpus: 10 common English phrases (2–5 words each)

**Test Phrases**:
- "hello world"
- "new paragraph"
- "delete that"
- "undo"
- "hello there how are you"
- "the quick brown fox"
- "thanks for your help"
- "see you later"
- "good morning"
- "okay done"

**Procedure** (per phrase):
1. Start voice input recording (tap button)
2. Note timestamp (T0)
3. Speak phrase clearly
4. Wait for result
5. Note timestamp (T1) when text appears
6. Calculate latency: T1 - T0
7. Record network condition (WiFi/LTE, signal strength)
8. Record result accuracy (1st result correct?)

**Measurements to capture**:
- Total latency (speak → text insertion)
- Recognition state transitions (Listening → Processing → Ready)
- Intermediate latencies (partial results timing)
- Network latency (if measurable via Logcat)
- Confidence score per result
- Error rates (failures, timeouts, no-match)

### Phase 2: Identify Bottlenecks (1–2 hours)

Analyze results to determine which stage dominates latency:
- If latency is network-bound: speech engine call (Google/vendor) is slow
- If latency is local: audio buffering or silence detection timeout too long
- If latency is consistent: device-independent factors
- If latency varies by network: network latency is the bottleneck

**Tools**:
- Android Profiler (CPU, memory, network inspector)
- Logcat timestamps from VoiceInputManager state transitions
- System trace (if needed)

### Phase 3: Optimization Strategies (TBD based on bottlenecks)

If silence detection timeout is the bottleneck:
- Reduce `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` from 3000ms to 1500ms–2000ms
- Add early termination: user can tap "Stop" button immediately after speaking
- Measure accuracy impact (shorter timeout may increase false positives)

If network latency is the bottleneck:
- Check network condition (WiFi vs. LTE)
- Parallelize: start text insertion UI while waiting for confidence score
- Implement timeout bypass: insert text on partial results if final result takes >3s

If audio buffering is the bottleneck:
- Profile audio encoder/decoder streams
- Cache stream objects (avoid re-allocation per recognition)

---

## Instrumentation Points (Current Code)

**VoiceInputManager.kt**:
- Line 57 (`startListening`): T0 — recognition start
- Line 131 (`onReadyForSpeech`): Audio input ready
- Line 134 (`onBeginningOfSpeech`): User started speaking
- Line 146 (`onEndOfSpeech`): User stopped speaking (silence detected)
- Line 167 (`onResults`): T1 — final result received
- Line 188 (`onPartialResults`): Partial results available (real-time feedback)

**Recommended additions for profiling**:

```kotlin
// In VoiceInputManager.kt
private var recognitionStartTime: Long = 0
private var resultReceiveTime: Long = 0

fun startListening() {
    recognitionStartTime = System.currentTimeMillis()
    // ... existing code ...
    Log.d("VoiceProfiler", "Recognition started: $recognitionStartTime")
}

override fun onResults(results: Bundle?) {
    resultReceiveTime = System.currentTimeMillis()
    val latency = resultReceiveTime - recognitionStartTime
    Log.d("VoiceProfiler", "Results received. Latency: ${latency}ms")
    // ... existing code ...
}

override fun onPartialResults(partialResults: Bundle?) {
    val latency = System.currentTimeMillis() - recognitionStartTime
    Log.d("VoiceProfiler", "Partial result at ${latency}ms")
    // ... existing code ...
}
```

---

## Expected Results & Success Criteria

**Baseline Latency** (before optimization):
- Likely: 3–6 seconds (network-bound most likely)
- Acceptable if: >80% results within 5s
- Unacceptable if: >50% results >5s

**Optimized Target** (after changes):
- Goal: >95% results within 5s
- Minimum acceptable: >80% within 5s

**Accuracy metric** (secondary):
- 1st result correct: >90% (common phrases)
- Confidence score >0.8: >85%
- No false positives (wrong text generated): <5%

---

## Device Test Log Template

```
Device: [Pixel 4a / Samsung A51 / Moto G7]
Network: [WiFi 5GHz / LTE / WiFi 2.4GHz]
Date: 2026-05-05
Tester: [Name]

| Phrase | Network | Latency (ms) | Correct | Confidence | Notes |
|--------|---------|--------------|---------|------------|-------|
| hello world | WiFi 5GHz | 2100 | Yes | 0.95 | Fast result |
| ... | ... | ... | ... | ... | ... |

**Summary**:
- Total tests: 10
- Average latency: 2500ms
- >5s results: 0%
- Accuracy: 100%
- Bottleneck: [Network/Device]
```

---

## Next Steps

1. **Prepare test environment** (this session):
   - Build debug APK ✅
   - Document instrumentation points ✓
   - Create test corpus ✓

2. **Device testing** (when device is available):
   - Install debug APK
   - Run profiling tests (3–4 hours)
   - Collect latency data

3. **Analysis** (1–2 hours):
   - Identify bottleneck
   - Decide optimization strategy

4. **Optimization** (1–2 weeks if needed):
   - Implement changes
   - Re-test
   - Release as v1.4.1 patch if significant improvement

5. **Documentation update** (1 hour):
   - Update VOICE_INPUT.md with latency metrics
   - Update ROADMAP.md Phase 4.1 status
   - Commit and release

---

## Appendix: Android Speech Recognizer References

**Key Intent Extras**:
- `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` — Silence timeout (default 3000ms) 
- `EXTRA_PARTIAL_RESULTS` — Enable partial results (true = real-time feedback)
- `EXTRA_MAX_RESULTS` — Max results to return (1 = top result only)
- `EXTRA_LANGUAGE_MODEL` — Model type (FREE_FORM = most flexible)

**RecognitionListener callbacks**:
- `onReadyForSpeech()` — System ready to accept audio
- `onBeginningOfSpeech()` — User started speaking
- `onRmsChanged()` — Audio level feedback
- `onBufferReceived()` — Audio buffer available
- `onEndOfSpeech()` — User stopped speaking
- `onError()` — Error occurred
- `onResults()` — Final results ready (T1)
- `onPartialResults()` — Intermediate results available
- `onEvent()` — Platform-specific events

**Known limitations**:
- Network timeout controlled by speech engine, not app
- Silence detection timeout varies by Android version
- Google Pixel devices often have faster recognizer than other OEMs
- LTE latency typically 2–3x WiFi latency

