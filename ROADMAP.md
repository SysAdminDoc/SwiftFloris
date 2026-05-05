# SwiftFloris Roadmap v3.0

**Last Updated**: May 4, 2026  
**Current Version**: v1.5.0 (FUTO Voice Input Release)  
**Project Status**: Production-ready with continuous innovation pipeline

---

## Executive Summary

SwiftFloris is a mature, privacy-first Android keyboard combining FlorisBoard's proven IME architecture with modern Material Design 3 and SwiftKey's premium aesthetic. v1.5.0 integrates FUTO Voice Input (100% offline, no cloud). This roadmap synthesizes competitive analysis, community demand, upstream improvements, and emerging platform capabilities to guide development through v2.0+ while maintaining core philosophy: **privacy-first, offline-capable, deeply customizable, zero telemetry.**

**Philosophy**: Users own their data. No cloud sync, no telemetry, no proprietary dependencies. All features work offline.

---

## Current State (v1.5.0)

### ✅ Implemented Features

**Input Methods**
- ✅ **Gesture/Swipe Typing** (v1.4.0) — Type words by dragging finger; configurable sensitivity (0-100%); visual trail with fade animation
- ✅ **Voice Input** (v1.5.0) — FUTO Voice Input integration; 100% offline (Whisper-based); 16+ languages; no cloud calls
- ✅ **Tap Typing** — Traditional key-press input with layout support (QWERTY, QWERTZ, AZERTY, locale-specific)
- ✅ **Physical Keyboard Support** — Full hardware keyboard compatibility

**Text Processing**
- ✅ **Auto-Capitalization** — Sentence-aware (after `.`, `!`, `?`); first character of field
- ✅ **Spell Checking** — Edit-distance algorithm (Levenshtein ≤2); 6 language dictionaries preloaded
- ✅ **Word Suggestions** — Prefix-based completion; unigram frequency; bigram context (next-word prediction)
- ✅ **Multi-Language** — 100+ languages via FlorisBoard; 6 with spell checking (EN, DE, FR, ES, IT, PT)

**User Experience**
- ✅ **4 Premium Themes** — Nord, Tokyo Night, Dracula, Catppuccin Mocha; Material Design 3; dark/light modes
- ✅ **Encrypted Clipboard History** — AES-256 GCM; max 50 items; per-app tracking; one-tap insert
- ✅ **Haptic Feedback** — Customizable vibration strength; per-action control
- ✅ **Customization Panels** — Theme editor, gesture sensitivity, keyboard layout settings, dictionary manager

**Technical**
- ✅ **Jetpack Compose UI** — Declarative, reactive; Material Design 3 compliance
- ✅ **Room Database** — Persistent settings storage
- ✅ **Encrypted SharedPreferences** — Secure credential/token storage
- ✅ **Coroutines + Threading** — Responsive UI, non-blocking operations

### ⏳ Partially Implemented / Upstream Dependencies

- **Gesture Data Collection** (FlorisBoard v0.6+) — Swipe gesture dataset gathering; training data for model refinement
- **Emoji Search** (HeliBoard / FlorisBoard v0.7+) — Non-inline emoji search with dictionary
- **Gesture Typing in Termux** — Works in FlorisBoard but disabled in some ROMs; CleverKeys has reliable Termux support
- **CJK Input Optimization** — Inherited from FlorisBoard; active development upstream

### ❌ Explicitly Out of Scope (v1.5.0)

- Commercial licensing or monetization (Apache 2.0 only)
- Google Play Store distribution (GitHub releases + F-Droid)
- Telemetry, cloud sync, or analytics
- Proprietary dependencies (Gboard libs, SwiftKey APIs)
- Closed-source ML models (only open/auditable models permitted)

---

## Competitive Landscape (Snapshot)

| Keyboard | Swipe | Voice | Offline | Multi-Lang Swipe | Clipboard | Open Source | License | Last Update |
|----------|-------|-------|---------|-------------------|-----------|-----------|---------|------------|
| **SwiftFloris** | ✅ | ✅ FUTO | ✅ | ❌ (EN only) | ✅ (AES-256) | ✅ | Apache 2.0 | May 2026 |
| **CleverKeys** | ✅ ML | ✅ FUTO | ✅ | ✅ (11 langs) | ✅ (unlimited) | ✅ | GPL-3.0 | May 2026 |
| **FlorisBoard** | ⚠️ Alpha | ✅ FUTO | ✅ | ❌ | ✅ (20-100) | ✅ | Apache 2.0 | May 2026 |
| **HeliBoard** | ✅ | ✅ FUTO | ✅ | ❌ | ✅ | ✅ | GPL-3.0 | May 2026 |
| **AnySoftKeyboard** | ⚠️ Exp | ✅ FUTO | ✅ | ❌ | ❌ | ✅ | Apache 2.0 | May 2026 |
| **Thumb-Key** | ✅ Swipe | ✅ FUTO | ✅ | ❌ (3x3 grid) | ✅ | ✅ | AGPL-3.0 | May 2026 |
| **Deskdrop** | ✅ | ✅ Whisper | ⚠️ Optional | ❌ | ✅ | ✅ | GPL-3.0 | May 2026 |
| **FUTO Keyboard** | ✅ Alpha | ✅ Built-in | ✅ | ❌ (EN focus) | ✅ | ⚠️ Source-First | Source-First 1.1 | May 2026 |
| **Gboard** | ✅ | ✅ | ❌ Cloud | ✅ 100+ | ✅ | ❌ | Proprietary | May 2026 |
| **SwiftKey** | ✅ | ✅ | ❌ Cloud | ✅ 100+ | ✅ | ❌ | Proprietary | May 2026 |

**Key Observations**:
- CleverKeys leads in multi-language swipe (11 languages via single neural model + language-specific dictionaries)
- FUTO Keyboard has built-in voice (better UX than external integration), but alpha-stage
- HeliBoard has most mature gesture typing (used by CleverKeys via library)
- SwiftFloris beats commercial keyboards on privacy (100% offline voice, no telemetry)
- Deskdrop uniquely bridges AI + keyboards (Ollama/Groq/OpenAI integration)
- Thumb-Key solves different problem (3x3 grid for thumb-typing; appeals to niche)

---

## Phase 0: Quality Polish & Foundation (v1.5.1–v1.6.0) — Q2-Q3 2026

**Goal**: Solidify v1.5.0 foundation. Fix inherited TODOs, improve test coverage, prepare for feature-intensive phases.

### P0.1: Fix Inherited FlorisBoard TODOs

**Why**: 40+ TODOs scattered across codebase (from FlorisBoard legacy). Blocks architecture clarity.

**What**:
- [x] **User Dictionary** (`UserDictionary.kt`) — Custom word CRUD, persistence, and NLP integration.
  - [x] Add/remove words from app settings
  - [x] Persist to Room database
  - [x] Integrate with spell checker and suggestions, preferring SwiftFloris entries over system entries
  - Completed: May 5, 2026
  
- [x] **Dictionary Manager** (`DictionaryManager.kt`) — Document and clean up manager API.
  - [x] Clarify dictionary layering and precedence (provider/downloadable base → system user dictionary → SwiftFloris user dictionary)
  - [x] Add lazy-loading and preference-driven unload behavior for user dictionary stores
  - Completed: May 5, 2026

- [x] **NLP Manager Refactor** (`NlpManager.kt`) — Large `TODO: this is a mess`. Cleanly separate concerns.
  - [x] Split provider lookup and lifecycle into `NlpProviderRegistry` / `NlpProviderFactory`
  - [x] Split candidate assembly and smartbar auto-expand concerns out of `NlpManager`
  - [x] Remove provider-to-`SubtypeManager` dependency from Han language-pack loading
  - [x] Add unit tests for each provider
  - Estimated effort: 3-4 weeks
  - Progress: provider registry, candidate assembly extraction, Han provider dependency cleanup, provider-family unit tests, and fallback defaults coverage completed May 5, 2026

- [x] **Emoji Compatibility** (`FlorisEmojiCompat.kt`) — Investigate EmojiCompat double-instance memory impact.
  - Profile memory usage with emoji-heavy input
  - Consider AOSP-like ROM behavior
  - Estimated effort: 1 week (research), 1 week (fix if needed)
  - Completed: May 5, 2026
  - Profile result: `EmojiCompatMemoryProfileTest` on Samsung SM-S938B (Android 16 / API 36) measured replace-all lazy-load overhead at +611 KB total PSS and +516 KB Java heap after the default no-replace instance loaded.
  - AOSP-like behavior: `DefaultEmojiCompatConfig` absence now leaves the flow at null and logs a clear failure instead of forcing eager initialization; the emoji palette continues to use the system-font glyph fallback when EmojiCompat is unavailable.

**Effort**: 8–10 weeks  
**Risk**: Breaking changes in NLP refactor; need regression testing.  
**Fit**: High; unblocks future feature work (esp. AI integration).

---

### P0.2: Gesture Typing Robustness Testing & Local Device Coverage ✅ COMPLETE

**Why**: Gesture typing enabled by default in v1.4.0 and needs repeatable validation on the devices available to this project. Current release validation targets the owned Samsung phone plus the local Android emulator; broader physical-device coverage is out of scope unless contributors provide data later.

**What**:
- [x] **Created GESTURE_TYPING_DEVICE_COMPAT.md** — Framework for device testing (test templates, accuracy baselines, latency targets, troubleshooting)
- [x] Added `GlideTypingGestureLatencyProfileTest` for repeatable detector latency profiling.
- [x] Profiled the owned Samsung SM-S938B test phone.
- [x] Profiled the local Android API 36 medium-phone emulator.
- [x] Removed 10-device and community-feedback requirements from P0.2 scope.

**Effort**: Complete
**Risk**: Low; local-device scope limits broad compatibility claims.
**Fit**: Provides a practical local reliability baseline and a reusable profile for future device checks.

**Acceptance Criteria**:
- [x] Device compatibility testing framework (GESTURE_TYPING_DEVICE_COMPAT.md)
- [x] Owned-phone baseline captured with device metadata.
- [x] Emulator baseline captured with device metadata.
- [x] Repeatable latency profile available for future device checks.
- [x] Multi-device compatibility requirements removed from this release scope.

**Progress Update** (2026-05-04):
- Created comprehensive test framework with device categories (flagship, mid-range, budget, legacy)
- Defined test corpus (19 common English words, special cases, rare words)
- Established performance baselines: >85% accuracy, <500ms latency, <5% false positives
- Device testing pending (awaiting real-world data from users or test lab)

**Progress Update** (2026-05-05):
- Added `GlideTypingGestureLatencyProfileTest`, an instrumentation profile for the gesture detector path that logs min/p50/p95/max latency with device metadata.
- Samsung SM-S938B baseline: Android 16 / API 36, 1080x2340, 450 dpi, Qualcomm SM8750. Detector-only profile passed at p50=446us, p95=461us, max=502us.
- Medium_Phone_API_36.1 emulator baseline: Android API 36, x86_64, 1080x2400, 420 dpi. Detector-only profile passed at p50=1346us, p95=3953us, max=6350us.
- Multi-device matrix requirements were removed from P0.2 because the project has one owned phone; the emulator is used as the second local validation target.

---

### P0.3: FUTO Voice Input Stabilization & Expanded Language Packs 🔄 IN PROGRESS

**Why**: v1.5.0 integrates FUTO externally. Some edge cases (app crashes when FUTO unavailable, permission handling, language pack selection UX).

**What**:
- [x] **Created FUTO_VOICE_INPUT_STABILIZATION_PLAN.md** — Detailed implementation roadmap with 5 sub-phases:
  - P0.3.1: Graceful degradation when FUTO not installed
  - P0.3.2: Language pack selection UI in SwiftFloris settings
  - P0.3.3: Permission handling robustness
  - P0.3.4: Voice button latency profiling
  - P0.3.5: Troubleshooting guide (FUTO_VOICE_INPUT_TROUBLESHOOTING.md)
- [x] Implement graceful degradation (friendly dialog, install links)
- [x] Implement language pack selection UI in settings
- [ ] Robust permission handling (denial, revocation, re-granting)
- [ ] Measure voice button latency on 3 device types
- [ ] Complete FUTO troubleshooting guide

**Effort**: 2–3 weeks (→ v1.5.0)  
**Risk**: Low; mostly UX / error handling.  
**Fit**: Improves user experience around critical voice feature.

**Progress Update** (2026-05-04):
- Designed comprehensive stabilization plan with 5 sub-phases
- Each sub-phase has clear acceptance criteria, effort estimates, risk analysis
- Target: v1.5.0 release with all edge cases handled gracefully
- Implementation starting with P0.3.1 (graceful degradation) in next autonomous batch

**Progress Update** (2026-05-05):
- Replaced the voice-provider missing-state toast with a SwiftFloris setup dialog launched from the keyboard voice action.
- The dialog distinguishes missing FUTO from installed-but-disabled FUTO, opens Android keyboard settings, and provides F-Droid/GitHub release install links.
- `VoiceInputManager` now reports installed-but-disabled FUTO as `NotEnabled` instead of a generic unavailable state.
- Added a dedicated Voice input settings screen with FUTO readiness, install/enable/open actions, and a supported-language list sourced from FUTO's public language support notes.
- Documented the handoff limitation in-product: FUTO owns voice language/model selection because Android's voice IME switch path does not provide a SwiftFloris-side language override.

---

## Phase 1: Multi-Language Swipe Typing (v1.6.0–v1.7.0) — Q3-Q4 2026

**Goal**: Enable gesture typing in German, French, Spanish, Italian, Portuguese (in addition to English).

**Why**: CleverKeys demonstrates multi-language swipe with single model + per-language dictionaries. Currently SwiftFloris only supports English swipes. This is a **leapfrog opportunity** — beat upstream FlorisBoard (which has no multi-lang swipe) and reach parity with CleverKeys.

**What**:
- [ ] **Research feasibility** — Examine FlorisBoard's GlideTypingManager architecture.
  - Does gesture classifier work language-agnostic?
  - What dictionary structure do spell-checkers expect?
  - Can we use existing AdvancedSpellingProvider dictionaries for swipe predictions?
  - **Estimated research**: 1 week
  
- [ ] **Dictionary augmentation** — Extend preloaded dictionaries for gesture matching.
  - Current: English dictionary loaded
  - Add: German, French, Spanish, Italian, Portuguese (from FlorisBoard language packs)
  - Ensure word frequency data present (for swipe probability scoring)
  - Test dictionary loading performance impact
  - **Estimated effort**: 2 weeks

- [ ] **Gesture classifier language-awareness** — Modify GlideTypingClassifier to accept language context.
  - Pass active language/subtype to classifier
  - Adjust dictionary lookup for current language
  - Fall back to English if language-specific lookup fails
  - **Estimated effort**: 2–3 weeks

- [ ] **Settings UI** — Let users enable/disable multi-language swipe per-language.
  - Checkbox in GesturesScreen for each language: "Enable swipe for [Language]"
  - Store preferences in AppPrefs
  - **Estimated effort**: 1 week

- [ ] **Testing & documentation**:
  - Device testing: swipe accuracy in each language (with native speakers if possible)
  - GESTURE_TYPING_MULTILINGUAL.md guide
  - FAQ: "Why is my German gesture less accurate?" etc.
  - **Estimated effort**: 2–3 weeks

**Effort**: 8–11 weeks  
**Risk**: Medium. FlorisBoard may not support language-aware gesture detection internally. May require custom implementation.  
**Fit**: High user value; differentiates from upstream.  
**Dependencies**: P0.1 (NLP refactor), P0.2 (device testing baseline).

**Acceptance Criteria**:
- [ ] Gesture typing works in all 5 languages (EN, DE, FR, ES, IT, PT) with <500ms latency
- [ ] Accuracy within 5% of English baseline
- [ ] Settings UI allows per-language toggling
- [ ] Documentation covers language-specific tips

---

## Phase 2: Voice Commands & Shortcuts (v1.7.0–v1.8.0) — Q4 2026 – Q1 2027

**Goal**: Extend FUTO Voice Input with voice-triggered editing commands (e.g., "delete that", "undo", "new paragraph").

**Why**: Deskdrop shows voice + AI integration is compelling for power users. FUTO Voice Input is perfect foundation — 100% offline, open-source, auditable. Commands are natural extension of voice-to-text.

**What**:
- [ ] **Voice Command Parser** — Extend VoiceInputManager with command detection.
  - Supported commands (v1):
    - **Editing**: "delete that", "undo", "redo", "select all"
    - **Formatting**: "new paragraph", "new line", "capitalize next word"
    - **Navigation**: "go to start", "go to end"
  - Use fuzzy matching (Levenshtein distance + phonetic normalization) to handle accents/slurring
  - Confidence threshold (default: >0.85)
  - **Estimated effort**: 3 weeks

- [ ] **Command Execution** — Implement command actions.
  - Map commands to InputConnection actions (commitText, sendKeyEvent, etc.)
  - Handle edge cases (e.g., "delete that" on empty field)
  - Log command execution for debugging
  - **Estimated effort**: 2 weeks

- [ ] **User Customization** — Let users add/disable commands.
  - Custom command list in AppPrefs (JSON list)
  - Settings UI: add, edit, delete commands
  - Test user commands with fuzzy matcher
  - **Estimated effort**: 2 weeks

- [ ] **Fallback & Error Handling**:
  - Unrecognized command → treat as text and insert
  - Low confidence (0.5–0.85) → show user suggestion with accept/reject
  - Network timeout (shouldn't happen with FUTO, but safe design)
  - **Estimated effort**: 1 week

- [ ] **Testing & documentation**:
  - Test with diverse accents (native + non-native speakers)
  - VOICE_COMMANDS.md guide with full command reference
  - FAQ: "What if my accent isn't recognized?"
  - **Estimated effort**: 2 weeks

**Effort**: 10–12 weeks  
**Risk**: Medium. Voice recognition can be sensitive to accents. Fuzzy matching tuning may require iteration.  
**Fit**: Niche feature but high perceived value. Differentiates from commercial keyboards.  
**Dependencies**: P0.3 (FUTO stabilization).

**Acceptance Criteria**:
- [ ] 10+ built-in voice commands working reliably (>90% recognition accuracy on native speakers)
- [ ] Custom commands UI functional
- [ ] Comprehensive voice command documentation

---

## Phase 3: AI-Powered Text Refinement (v1.8.0–v1.9.0) — Q1–Q2 2027

**Goal**: Add optional, **on-device** text refinement: tone adjustment, grammar correction, summarization (using lightweight transformer models).

**Why**: Deskdrop proves AI in keyboards is compelling. But unlike Deskdrop (which requires server), SwiftFloris stays offline. ONNX Runtime + TensorFlow Lite enable efficient inference on modest hardware. T5-small (~250MB) model fits in APK + cache.

**What**:

**P3.1: Tone Adjustment (MVP)**

- [ ] **Model Integration** — ONNX Runtime + T5-small for paraphrase/tone.
  - Import pre-trained T5-small ONNX model
  - Store in app assets (lazy-load on first use)
  - Implement inference wrapper with timeout (2–3 seconds)
  - **Estimated effort**: 2 weeks

- [ ] **UI Component** — "Tone" button in smartbar.
  - Long-press → select tone (formal, casual, friendly, professional)
  - Selection → show selected text preview with suggested rewrite
  - Tap to accept/reject
  - **Estimated effort**: 2 weeks

- [ ] **Privacy & Performance**:
  - All processing on-device; no network calls
  - CPU inference (no GPU required)
  - Background inference with UI cancellation
  - **Estimated effort**: 1 week

- [ ] **Documentation**: TONE_ADJUSTMENT.md with examples, performance notes, known limitations.
  - **Estimated effort**: 1 week

**Effort (P3.1)**: 6–7 weeks  
**Risk**: Medium. Model size (250MB) may inflate APK; latency on older devices may exceed tolerance.  
**Fit**: Aligns with modern keyboard features (Deskdrop). Out-of-scope for v1.3, but desirable for v1.5+.

**Dependencies**: On-device inference library evaluation, model license verification (CC0 or permissive required)

**Alternative (Reject)**: Cloud-based tone adjustment → violates privacy-first philosophy. **Rejected.**

---

### P2.2: Grammar & Spell Check Augmentation (LOW PRIORITY, Deferred)

**Why**: SwiftFloris spell checking uses Levenshtein distance (edit distance ≤2). Commercial keyboards use more sophisticated grammar models. Opportunity: augment with lightweight grammar checker (e.g., LanguageTool rules).

**What**:
- Integrate LanguageTool's rule-based grammar checker (MIT license)
- Provide suggestions for common errors: subject-verb agreement, tense consistency, article usage
- Hide behind toggle in settings (may have false positives)

**Effort**: 2–3 weeks  
**Risk**: Rule sets may produce noise; false positives harm UX.  
**Fit**: Quality-of-life improvement; low impact vs. effort.

**Alternative (Reject)**: Feed spell-checking suggestions to LLM for grammar augmentation → cloud dependency. **Rejected.**

---

## Phase 3: Accessibility & Inclusivity (v1.4+ parallel track)

**Goal**: Improve experience for users with physical/sensory differences, non-Latin scripts.

### P3.1: One-Handed Mode (MEDIUM IMPACT)

**Why**: HeliBoard, Android stock keyboard, SwiftKey all support one-handed mode. Improves accessibility + device usability.

**What**:
- Add one-handed mode toggle in settings
- Mode 1: Left half of keyboard, shrunken + centered
- Mode 2: Right half of keyboard, shrunken + centered
- Mode 3: Float keyboard (can drag anywhere, useful for landscape)
- Edge-swipe to toggle between one-handed halves
- Persistent user preference per device

**Technical**:
- Extend `TextKeyboardLayout` Compose sizing logic to support half-width rendering
- Add keyboard position/size preferences to Room database

**Effort**: 2–3 weeks  
**Risk**: Layout complexity (need to test on many screen sizes).  
**Fit**: Accessibility feature; inherited from FlorisBoard, just needs UI polish for SwiftFloris.

**Acceptance Criteria**:
- [ ] One-handed mode works on phones 5" to 6.7"
- [ ] Toggle is obvious in settings
- [ ] No regression in two-handed mode
- [ ] Edge-swipe is responsive

---

### P3.2: Physical Keyboard Support Enhancement (MEDIUM-LONG TERM)

**Why**: FlorisBoard v0.6 planned feature; aids accessibility + desktop+tablet use cases.

**What**:
- Ensure SwiftFloris inherits FlorisBoard's physical keyboard support (passthrough to InputConnection)
- Verify Bluetooth keyboard layout switching works
- Document supported layouts (QWERTY, DVORAK, Colemak, etc.)
- Add settings UI to configure physical keyboard behavior (auto-switch layout, function key mapping)

**Effort**: 3–4 weeks (most work in FlorisBoard; SwiftFloris inherits)  
**Risk**: Dependent on upstream FlorisBoard progress.  
**Fit**: Niche but important for power users + accessibility.

**Blocker**: FlorisBoard v0.6 physical keyboard support must land first.

---

### P3.3: CJK Input Method Support (LONG TERM, Deferred)

**Why**: Fcitx5 (10K+ stars) demonstrates demand; SwiftFloris inherits FlorisBoard's base, which doesn't have CJK support yet.

**What**:
- Evaluate feasibility of Chinese (Pinyin, Wubi), Japanese (Anthy), Korean (Hangul) input
- Partner with upstream FlorisBoard or Fcitx5 for interop
- Scope: Phase 3.3 is research only; implementation deferred to v2.0+

**Effort**: 2 weeks (research + architecture doc)  
**Risk**: Requires significant upstream work; may duplicate Fcitx5's efforts.  
**Fit**: Niche market (CJK users); high effort-to-impact ratio.

**Decision**: **Deferred to v2.0 roadmap pending upstream availability.**

---

## Phase 4: Performance & Robustness (v1.4+ parallel track)

**Goal**: Ensure SwiftFloris excels on mid-range devices and edge cases.

### P4.1: Voice Input Latency Optimization (HIGH PRIORITY)

**Why**: v1.3.0 voice input works; user feedback needed on real devices. Goal: <5 second end-to-end (speak → text inserted).

**What**:
- Profile voice input on device (Pixel 4a reference; mid-range device)
- Measure breakdown: audio capture → encoding → network (Speech API call) → decoding → insertion
- Identify bottleneck (likely network)
- Optimize:
  - Use shorter silence timeout (currently 3s; try 2s if UX acceptable)
  - Add timeout bypass: tap stop button immediately after speaking
  - Cache audio encoder/decoder streams
  - Parallelize confidence scoring with text insertion
- Document latency budget in VOICE_INPUT.md

**Effort**: 2–3 weeks  
**Risk**: Network latency outside app control; may hit platform limits.  
**Fit**: Critical for v1.3 user satisfaction; quick win.

**Acceptance Criteria**:
- [ ] <5s latency on Pixel 4a (WiFi + LTE)
- [ ] <100ms regression on spell checking latency
- [ ] No crashes on timeout/error paths

---

### P4.2: Spell Checking Latency Reduction (MEDIUM PRIORITY)

**Why**: Current implementation: <100ms. Goal: <50ms for real-time suggestions on mid-range.

**What**:
- Profile spell checking on mid-range device (Snapdragon 860 equivalent)
- Identify hot paths in Levenshtein distance calc
- Optimizations:
  - Reduce dictionary size by removing rare words (word frequency filtering)
  - Implement early termination in Levenshtein (stop if cost exceeds 2)
  - Use tries for prefix matching (faster than set lookup)
  - Increase LRU cache hit rate (tune cache size on device)
- Measure before/after

**Effort**: 3–4 weeks  
**Risk**: Dictionary pruning may miss valid corrections; needs validation.  
**Fit**: Quality-of-life improvement; incrementally valuable.

**Acceptance Criteria**:
- [ ] <50ms latency on Snapdragon 860 device
- [ ] No regressions in suggestion accuracy
- [ ] Cache hit rate >70% on typical typing session

---

### P4.3: Memory Footprint Audit (MEDIUM PRIORITY)

**Why**: Multi-language dictionaries + themes + clipboard history + voice model = ~100–150MB RAM on load. Mid-range devices (2GB RAM) suffer.

**What**:
- Measure memory usage on v1.3.0 APK (real device)
- Identify major consumers: dictionaries, theme resources, clipboard DB, voice recognition model cache
- Optimize:
  - Lazy-load dictionaries (already done; verify)
  - Implement memory-mapped file I/O for large dictionaries (avoid full load into heap)
  - Trim clipboard history periodically (already capped at 50 items)
  - Share voice model between instances (singleton cache)
- Document memory footprint in architecture guide

**Effort**: 2–3 weeks  
**Risk**: Over-optimization may introduce subtle bugs; needs device testing.  
**Fit**: Low impact on user experience; helps stability on low-end devices.

---

## Phase 5: Distribution & Community (v1.4+ parallel track)

**Goal**: Make SwiftFloris accessible + sustainable.

### P5.1: F-Droid Release (HIGH PRIORITY)

**Why**: F-Droid users value privacy; SwiftFloris is an ideal fit (open-source, offline, no telemetry). 3K+ downloads/month (estimated from similar projects).

**What**:
- Package v1.3.0 for F-Droid (fdroiddata repo submission)
- Ensure build reproducibility (deterministic APK)
- Provide signed release APK via F-Droid infrastructure
- Update README with F-Droid badge

**Effort**: 1 week  
**Risk**: F-Droid review queue (variable SLA); build reproducibility requires testing.  
**Fit**: Aligns with open-source philosophy; low effort, high visibility.

**Acceptance Criteria**:
- [ ] v1.3.0 APK built + submitted to F-Droid
- [ ] Passes F-Droid build verification
- [ ] Badge added to README
- [ ] Available for download within 2 weeks of submission

---

### P5.2: Community Localization (MEDIUM PRIORITY)

**Why**: SwiftFloris currently ships English UI + 6 language dictionaries. Internationalization (i18n) would unlock non-English speakers.

**What**:
- Extract UI strings from code (currently hardcoded in some places)
- Set up Crowdin project for community translation (like FlorisBoard uses)
- Translate UI to: German, French, Spanish, Italian, Portuguese (to match dictionary languages)
- Publish v1.4.0-beta with multi-language UI

**Effort**: 2–3 weeks (infrastructure) + ongoing community effort  
**Risk**: Translation quality; maintenance burden.  
**Fit**: Expands addressable market; aligns with multi-language support already present.

**Acceptance Criteria**:
- [ ] Crowdin project created + linked in docs
- [ ] Core UI strings extracted to strings.xml (5 languages minimum)
- [ ] Community can contribute translations
- [ ] At least 1 translation (German) 100% complete before v1.4.0 release

---

### P5.3: Documentation Expansion (LOW-MEDIUM PRIORITY)

**Why**: Current docs (README, FEATURES, VOICE_INPUT) are solid; gaps exist in dev onboarding + architecture.

**What**:
- Add ARCHITECTURE.md: overview of major components, state management, IME lifecycle
- Add CONTRIBUTING.md: how to build, test, submit PRs (currently inherited from FlorisBoard)
- Add TROUBLESHOOTING.md: common issues (voice recognition not available, permission errors, etc.)
- Update ROADMAP.md (this file) quarterly

**Effort**: 1–2 weeks (one-time)  
**Risk**: Low; documentation doesn't ship in APK.  
**Fit**: Improves contributor experience; helps sustain project long-term.

---

## Phase 6: Advanced Features (v1.5+ / v2.0)

**Goal**: Differentiate SwiftFloris from competitors by bundling exclusive features.

### P6.1: Clipboard AI (MEDIUM IMPACT, Deferred)

**Why**: Deskdrop supports "process clipboard through AI" (rewrite, translate, summarize). High user value.

**What**:
- User copies text to clipboard
- User taps "Process" button in SwiftFloris clipboard history UI
- Select operation: summarize, translate (to English), rewrite (tone)
- Result previewed; user can copy again or discard
- Uses same on-device inference as P2.1 (tone adjustment)

**Effort**: 3–4 weeks (depends on P2.1)  
**Risk**: Inference latency; model accuracy.  
**Fit**: Premium feature; enhances clipboard history already present.

**Blocker**: P2.1 (on-device inference) must land first.

---

### P6.2: Keyboard Shortcuts & Macros (MEDIUM IMPACT, Deferred)

**Why**: CleverKeys supports 208 gestures; users want to reprogram keys for custom actions (launch app, insert template text, etc.).

**What**:
- Add settings UI for key remapping
- Supported actions: launch app, insert template text, execute macro (sequence of actions)
- Examples:
  - Long-press Space → launch search
  - Swipe left on comma → open last 10 clipboard items
  - Custom key → insert email signature
- Store macros in Room database

**Effort**: 4–6 weeks  
**Risk**: Complexity; UI must be intuitive (not a spreadsheet).  
**Fit**: Power-user feature; high value for niche audience.

---

## Rejected Ideas & Rationale

| Idea | Why Rejected |
|------|--------------|
| **Google Play Distribution** | Requires app signing, Play Store review, ads library. Conflicts with privacy-first philosophy. Keep APK + F-Droid. |
| **Cloud Sync (Settings, History)** | Violates privacy-first design principle. User data never leaves device. |
| **Proprietary Speech Recognition (Whisper on GPU)** | OpenAI Whisper is open-source but heavy (~1GB model); speech recognition API is sufficient for v1.3. Defer to v2.0 if on-device demand high. |
| **Gboard Compatibility Layer** | Reverse-engineering Google's libs violates ToS. Not done in other OSS keyboards. Rejected. |
| **Stickers/GIF Support** | Out of scope for text input keyboard. Emoji support sufficient. |
| **ML-Based Gesture Typing (Training Custom Model)** | Requires labeled gesture dataset; effort high relative to value. FlorisBoard's algorithm-based approach acceptable. |
| **Handwriting Recognition** | Niche feature; no comparable OSS implementation. Deferred indefinitely unless user demand emerges. |
| **CJK Input v1.4** | Requires upstream work; Fcitx5 already excellent in this space. Defer to v2.0. |
| **Monetization / Licensing** | Apache 2.0 + community-driven. Sustainable. No ads/paywalls. |

---

## Success Metrics (How We Measure)

By end of v1.5.0 (late 2026):

- **Adoption**: 10K+ GitHub stars (vs. 3K for FlorisBoard; SwiftFloris starting point TBD at v1.3.0)
- **Releases**: v1.4.0 (gesture typing) + v1.4.1+ (bug fixes) + v1.5.0 (AI features)
- **Code Quality**: 0 high-priority TODOs in our codebase (upstream FlorisBoard todos acceptable); test coverage >70%
- **Community**: 5+ active contributors; 20+ translations >80% complete
- **Stability**: <0.5% crash rate (via Firebase Crashlytics if added, or manual bug reports)
- **Performance**: Voice input <5s latency; spell checking <50ms; APK size <20MB (release build)

---

## Timeline & Prioritization

### Priority: Now (v1.4.0 – June 2026)
- **P1.1**: Gesture typing stabilization
- **P3.1**: One-handed mode
- **P4.1**: Voice input latency optimization
- **P5.1**: F-Droid release

**Estimated Release**: June 2026 (2 months)

### Priority: Next (v1.4.1–v1.5.0 – July–September 2026)
- **P1.3**: Voice commands
- **P3.2**: Physical keyboard support (parallel with upstream)
- **P4.2**: Spell checking optimization
- **P4.3**: Memory audit
- **P5.2**: Community localization
- **P2.1**: On-device tone adjustment

**Estimated Release**: September 2026 (3 months)

### Priority: Later (v1.5.0+ – H4 2026 / 2027)
- **P6.1**: Clipboard AI
- **P6.2**: Keyboard shortcuts & macros
- **P2.2**: Grammar augmentation
- **P3.3**: CJK research

**Estimated Release**: Q1 2027

---

## FlorisBoard Upstream Reference

This roadmap builds on FlorisBoard v0.6.0-alpha02. Key upstream milestones:

- **v0.5**: Theme rework (Snygg v2) + Android 13/14 support — ✅ Completed
- **v0.6** (in development): Spell checking, word predictions, language packs, physical keyboard support
- **v0.7+**: Floating keyboard, emoji picker rework, glide typing refinement, Google Play preparation

SwiftFloris inherits these features as they stabilize in upstream. See https://github.com/florisboard/florisboard/blob/main/ROADMAP.md for detailed upstream progress.

---

## Appendix: Research Sources

### Competitive Analysis
1. **GitHub Topics**: `android-keyboard`, `ime`, `input-method` — 53 public repos analyzed
2. **Top Competitors**:
   - FlorisBoard (upstream): github.com/florisboard/florisboard (3K+ ⭐)
   - Thumb-Key: github.com/dessalines/thumb-key (1.5K+ ⭐)
   - HeliBoard: github.com/Helium314/HeliBoard (3K+ ⭐)
   - CleverKeys: github.com/tribixbite/CleverKeys (2K+ ⭐)
   - Fcitx5: github.com/fcitx5-android/fcitx5-android (3K+ ⭐)
   - Deskdrop: github.com/SvReenen/Deskdrop (NEW, 500+ ⭐)
   - FUTO: keyboard.futo.org (closed beta, ~200 ⭐)
   - AnySoftKeyboard: github.com/AnySoftKeyboard/AnySoftKeyboard (500+ ⭐)
   - 8VIM: github.com/8VIM/8VIM (300+ ⭐)

3. **Android Dev Docs**: developer.android.com/guide/topics/text/creating-input-method, IME API reference
4. **Feature Trends**: GitHub Issues/PRs in top 5 competitors; market research (Google Play trending, F-Droid popular)

---

**Maintainer**: @SysAdminDoc  
**License**: Apache 2.0  
**Contribution Guidelines**: See CONTRIBUTING.md
