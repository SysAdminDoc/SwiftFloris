# SwiftFloris Roadmap v2.0

**Last Updated**: May 4, 2026  
**Current Version**: v1.3.0 (Voice Input Release)  
**Project Status**: Stable, feature-complete keyboard with ongoing innovation

---

## Executive Summary

SwiftFloris is a mature, privacy-first Android keyboard combining FlorisBoard's proven backend with SwiftKey's premium aesthetic. v1.3.0 adds voice-to-text capability. This roadmap charts the path to v2.0+ by synthesizing competitive analysis, community demand, and emerging platform capabilities.

**Philosophy**: Privacy-first, offline-capable, deeply customizable, no telemetry.

---

## Current State (v1.3.0)

### ✅ Implemented

- **Core Typing**: Auto-capitalization (sentence-aware), spell checking (6 languages), word prediction, clipboard history
- **Voice Input**: Speech-to-text via Android Speech Recognizer API, real-time confidence scoring, offline support
- **UI/Themes**: Material Design 3, 4 premium themes (Nord, Tokyo Night, Dracula, Catppuccin), dark/light modes
- **Customization**: 6 language support (EN, DE, FR, ES, IT, PT), encrypted clipboard history
- **Accessibility**: Multi-touch support, haptic feedback (70% strength)
- **Polish**: Professional branding, stable v1.3.0 release on GitHub

### ⏳ Planned (FlorisBoard upstream)

- Gesture/swipe typing (FlorisBoard v0.6+ feature, currently disabled/alpha in SwiftFloris)
- Physical keyboard support (FlorisBoard v0.6+ in development)
- CJK input improvements (inherited from upstream)
- Emoji search and picker rework
- Language pack system refinement

### ❌ Explicitly Not Planned (Out of Scope)

- Commercial licensing or monetization (Apache 2.0 only)
- Google Play distribution (APK via GitHub)
- Telemetry or cloud sync
- Proprietary dependencies (Gboard libs, etc.)

---

## Phase 1: Gesture Typing & Input Diversity (v1.4.0 – 2026 H2)

**Goal**: Add gesture-based input as a complement to traditional tap typing. Enablement + refinement.

### P1.1: Enable & Stabilize Gesture Typing (HIGH IMPACT)

**Why**: Gesture/swipe typing is table-stakes in modern keyboards (Gboard, SwiftKey, HeliBoard, CleverKeys). FlorisBoard has the feature (alpha); SwiftFloris inherits it but it's disabled.

**What**:
- Enable gesture typing in FlorisBoard config
- Test and verify accuracy across device types (Pixel, Samsung, mid-range)
- Document limitations vs. upstream FlorisBoard
- Publish v1.4.0-beta with gesture typing enabled (opt-in toggle in settings)
- Gather user feedback: latency, accuracy, false positives

**Effort**: 2–3 weeks (testing + iteration)  
**Risk**: FlorisBoard's gesture engine is algorithm-based (not ML), may lag commercial keyboards; user expectations high.  
**Fit**: Core feature for feature parity. Aligns with philosophy (offline, no proprietary libs).

**Acceptance Criteria**:
- [ ] Gesture typing works without false triggers
- [ ] <100ms latency from gesture end to commit
- [ ] Accurate for 80%+ common words (tested on corpus)
- [ ] Works in Termux (unlike some competitors)
- [ ] Settings UI provides on/off + sensitivity toggle

---

### P1.2: Multi-Language Gesture Typing (MEDIUM IMPACT, Deferred)

**Why**: CleverKeys supports 11 languages with a single neural model + language-specific dictionaries. Most keyboards support English-only swipe.

**What**:
- Audit FlorisBoard gesture typing architecture for multi-language support
- Identify ML model limitations
- If feasible: extend gesture typing to German, French, Spanish (other 3 included dictionaries)
- If not feasible: document architectural constraints for future

**Effort**: 4–6 weeks (high uncertainty)  
**Risk**: May require ML model retraining (out of scope). FlorisBoard may lack multi-lang gesture support by design.  
**Fit**: Nice-to-have; lower priority than gesture refinement. Deferred to v1.5+ if research shows viability.

**Dependencies**: P1.1 completion (gesture typing stable)

---

### P1.3: Voice Commands & Shortcuts (MEDIUM IMPACT)

**Why**: Deskdrop, FUTO, and commercial keyboards support voice-triggered actions (e.g., "delete that", "new paragraph", "undo"). Complements voice-to-text.

**What**:
- Add voice command layer on top of v1.3.0 voice input
- Supported commands:
  - Editing: "delete that", "undo", "redo", "select all", "capitalize next word"
  - Formatting: "new paragraph", "new line", "backspace line"
  - Navigation: "go to start", "go to end"
- User can customize command list (settings panel)
- Confidence threshold for command execution (default: >0.85)

**Technical**:
- Extend `VoiceInputManager.kt` with command parser
- Use Levenshtein distance for fuzzy command matching (account for accents/slurring)
- Add command history/learning (if command misrecognized, user can correct)

**Effort**: 3–4 weeks  
**Risk**: Reliability (false positives on command execution); user expectations (high bar).  
**Fit**: High user value; extends voice input philosophy. Privacy-first (all on-device).

**Acceptance Criteria**:
- [ ] 10+ voice commands recognized reliably
- [ ] Command recognition >90% accurate on test corpus (native speaker)
- [ ] Users can add custom commands in settings
- [ ] Command execution logs visible for debugging

---

## Phase 2: AI Integration & Smart Features (v1.5.0+ – 2026 H2+)

**Goal**: Add optional, local-first AI capabilities to enhance typing (tone adjustment, grammar, summarization).

### P2.1: On-Device Tone Adjustment (MEDIUM IMPACT, Deferred)

**Why**: Deskdrop (AI keyboard) supports rewrite, translate, summarize, tone shift. Users value fast text refinement without leaving the app.

**What**:
- Partner with on-device inference library (ONNX Runtime, TensorFlow Lite) for T5-small or equivalent
- Add "tone bar" UI: users select text → tap tone button (formal, casual, friendly, professional) → see suggestion
- Optional: integrate with system-wide text selection menu for use in any app (like Android's spell-check)
- No cloud sync; all local

**Technical**:
- Ship pre-trained T5-small model (lightweight, ~250MB)
- Model stored in app assets, lazy-loaded on first use
- Inference on CPU (no GPU required)
- Timeout: 2 seconds (user-acceptable latency)

**Effort**: 6–8 weeks (model integration + UI + testing)  
**Risk**: Model size (250MB APK bloat); latency; accuracy of tone transfer.  
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
