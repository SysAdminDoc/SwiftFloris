# SwiftFloris v1.4.0 Gesture Typing Device Compatibility Report

**Version**: 1.4.0  
**Feature**: Gesture/Swipe Typing Stabilization (Phase 0.2)  
**Status**: Testing In Progress  
**Last Updated**: 2026-05-04  

---

## Testing Methodology

### Device Categories
1. **Flagship/High-End** (2024-2026): Snapdragon 8-Gen 3, 6GB+ RAM, 120+ Hz displays
2. **Mid-Range** (2022-2023): Snapdragon 6-Gen 1-2, 4-6GB RAM, 90-120 Hz displays
3. **Budget** (2020-2022): Snapdragon 4-6-Gen, 3-4GB RAM, 60 Hz displays
4. **Legacy** (pre-2020): Snapdragon 630 or older, 2-3GB RAM, 60 Hz displays

### Test Corpus
**Common English Words** (for baseline accuracy):
- THE, AND, FOR, ARE, BUT, NOT, YOU, ALL, CAN, HER, WAS, ONE, OUR, OUT
- HELLO, WORLD, TEST, QUICK, BROWN, JUMPS, OVER, LAZY, DOGS

**Special Cases**:
- Double letters: HELLO, BOOK, COFFEE
- Rare words: XYLOPHONE, RHYTHM, QUEUE
- Short words: THE, AND, IT, IS, AS
- Long words: EXTRAORDINARY, UNFORTUNATELY, DEVELOPMENT
- Language mixing: "hello" + "café" + numbers

---

## Device Test Results

### Test Template (Copy for each device)

**Device**: [Name/Model]  
**Android Version**: [OS Level]  
**SoC**: [Processor]  
**RAM/Storage**: [Specs]  
**Display**: [Size, Resolution, Refresh Rate]  
**Tester**: [Name]  
**Test Date**: [YYYY-MM-DD]  
**APK Version**: v1.4.0  

#### Gesture Accuracy Testing

| Word | Attempts | Success | Accuracy | Notes |
|------|----------|---------|----------|-------|
| THE | 5 | 5 | 100% | - |
| HELLO | 5 | 5 | 100% | - |
| WORLD | 5 | 5 | 100% | - |
| QUICK | 5 | 4 | 80% | One miss on fast gesture |
| BROWN | 5 | 5 | 100% | - |
| JUMPS | 5 | 5 | 100% | - |
| XYLOPHONE | 3 | 2 | 67% | Rare word, lower confidence |
| COFFEE | 5 | 4 | 80% | Double-L tricky |
| **Average** | | | **XX%** | |

#### Latency Testing

| Test | Metric | Result | Notes |
|------|--------|--------|-------|
| Gesture to Text Display | ms | - | Time from gesture end to suggestion shown |
| Suggestion to Insertion | ms | - | Time from accepting suggestion to text in field |
| Total E2E | ms | - | Full gesture → insertion pipeline |

#### False Positive Testing

| Scenario | Result | Notes |
|----------|--------|-------|
| Tap a single letter normally | No false gesture | - |
| Rapid tapping (e.g., "AAAA") | No unwanted gesture | - |
| Pinch-to-zoom gesture | No interference | - |
| Swipe up/down for space prediction | Works as intended | - |
| Game with touch events | No false triggers | - |

#### UI/Visual Testing

| Aspect | Status | Notes |
|--------|--------|-------|
| Trail visualization smooth? | ✅/⚠️/❌ | - |
| Animation frame rate consistent? | ✅/⚠️/❌ | - |
| Gesture preview updates real-time? | ✅/⚠️/❌ | - |
| No visible lag during gesture? | ✅/⚠️/❌ | - |

#### Performance Profiling

| Metric | Baseline | Notes |
|--------|----------|-------|
| CPU usage during gesture | %XX | Idle vs. active gesture |
| Memory overhead | +XX MB | Before/after gesture typing enabled |
| Battery impact (1 hour use) | -%X | Gesture vs. tap-only |
| Thermal status | Normal/Warm/Hot | Device temperature during heavy use |

#### Compatibility Issues Found

| # | Issue | Severity | Workaround | Status |
|---|-------|----------|-----------|--------|
| 1 | (TBD) | - | - | - |

---

## Summary Table: All Tested Devices

| Device | Android | SoC | Avg Accuracy | Latency | Issues | Status |
|--------|---------|-----|--------------|---------|--------|--------|
| (Pixel 4a - baseline) | 14 | SD 765G | - | - | - | (TBD) |
| (Samsung Galaxy A52) | 13 | SD 720G | - | - | - | (TBD) |
| (iPhone equivalent) | - | - | - | - | N/A (iOS only) | - |

---

## Known Limitations & Workarounds

### 1. Gesture Typing in Termux (Terminal Emulator)
**Status**: ⚠️ Reported
**Symptom**: Gesture typing does not work or triggers spuriously in Termux.
**Root Cause**: Termux uses custom input event handling; FlorisBoard's gesture detection may not be compatible.
**Workaround**: 
- Disable gesture typing in Termux (via Settings → Gestures toggle)
- Use tap-only input in terminal
- Consider using physical keyboard if available

**Reference**: CleverKeys has reliable Termux support (compare architecture)

### 2. Gesture Accuracy with High DPI Scaling
**Status**: ⚠️ Potential Issue
**Symptom**: Device set to 125% or 150% display scaling may experience gesture jitter.
**Root Cause**: Gesture classifier trained on standard DPI; scaled coordinates may deviate.
**Workaround**:
- Reduce display scaling to 100% for optimal gesture accuracy
- Or reduce gesture preview refresh rate (lowers CPU overhead)

### 3. Gesture Typing Disabled in Some Input Fields
**Status**: ⚠️ Field-Specific
**Symptom**: Some apps (e.g., banking apps, games) may restrict gesture input for security.
**Root Cause**: App-level IME restrictions; not a SwiftFloris issue.
**Workaround**: Disable gesture typing globally or per-field

---

## Language-Specific Notes

Currently v1.4.0 has **English-only gesture support** (inherited from FlorisBoard v0.6.0-alpha02).

**Planned** (Phase 1 — Multi-Language Swipe):
- German, French, Spanish, Italian, Portuguese gesture dictionaries
- Language-aware gesture classifier
- Per-language accuracy tuning

---

## Device Testing Checklist

### Must-Test Devices (Critical for Release)
- [ ] Pixel 4a / 5 / 6 (Google flagship reference)
- [ ] Samsung Galaxy A52 / S21 (mid-range + flagship)
- [ ] OnePlus 11 / 12 (flagship alternative)
- [ ] Xiaomi Redmi Note 12 / 13 (budget)
- [ ] Older device (Snapdragon 630 or equivalent)

### Nice-to-Have Devices
- [ ] Foldable (Galaxy Z Fold 5)
- [ ] Tablet (iPad Air or Android tablet)
- [ ] Device with 90 Hz display
- [ ] Device with 120+ Hz display

---

## Performance Baselines

**Target Metrics** (Phase 0.2 Goals):
- **Gesture Accuracy**: >85% on common words (THE, HELLO, WORLD, etc.)
- **Latency**: <500ms from gesture end to text insertion
- **False Positives**: <5% of normal tap usage
- **CPU Usage**: <15% during active gesture typing
- **Memory**: <50 MB overhead when gesture typing enabled

---

## FAQ & Troubleshooting

### Q: Why doesn't gesture work in [App Name]?
**A**: Some apps restrict IME access to text input fields. Verify:
1. App text field is focusable (try tapping it)
2. Gesture typing is enabled in SwiftFloris Settings
3. Try disabling Gesture Typing temporarily to verify fallback to tap-only

### Q: My gesture accuracy is low (<70%). What should I do?
**A**: 
1. Review GESTURE_TYPING.md best practices (smooth movement, don't lift finger)
2. Enable "Show Trail" to visualize your gesture path
3. Try reducing gesture preview refresh rate (Settings → Advanced)
4. Test on different device if possible (may be device-specific)
5. Report issue with device specs to GitHub Issues

### Q: Why is latency slow on my device?
**A**: 
1. Profile CPU/memory usage (use Android Studio Profiler)
2. Reduce "Preview Refresh Delay" (lower values = higher CPU but faster feedback)
3. Close background apps consuming CPU
4. Try on a fresher Android build (reboot and clear cache if possible)

### Q: Does gesture typing work in CJK (Chinese/Japanese/Korean)?
**A**: No (v1.4.0). Planned for Phase 5. For now, use tap-only input or pen input if available.

---

## Next Steps

### For v1.4.0 Final QA
1. **Test on 5+ devices** (flagship, mid-range, budget, legacy)
2. **Document results** using the template above
3. **File issues** for any accuracy <80% or latency >500ms
4. **Update GESTURE_TYPING.md** with device-specific tips

### For v1.4.1 (Hotfix, if needed)
- Address high-priority accuracy or latency issues
- Add per-device tuning parameters (if root cause identified)

### For Phase 1 (v1.6.0+ Multi-Language Swipe)
- Use this compatibility data to inform language-specific gesture training
- Prioritize devices with known issues for regression testing

---

## Sign-Off

**Phase 0.2 Status**: 🔄 Testing In Progress  
**Lead Tester**: Autonomous Development Agent  
**Test Execution Date Range**: 2026-05-04 — 2026-05-18 (estimated)  

---

## Appendix: Device Specification Reference

### Device Testing Recommendations

For comprehensive gesture typing coverage, test on a minimum of:
1. **One flagship** (Pixel/Samsung Galaxy S-series)
2. **One mid-range** (Redmi Note/Galaxy A-series)
3. **One budget** (<$150 device)
4. **One legacy** (3+ years old)

This covers the range of device capabilities likely to be used by SwiftFloris users.

---

**Document Version**: 1.0  
**Last Updated**: 2026-05-04  
**Next Review**: After first device testing round
