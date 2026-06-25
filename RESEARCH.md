# Research — SwiftFloris

## Executive Summary
SwiftFloris is the most feature-complete privacy-first Android keyboard in the FOSS space: no `INTERNET` permission (CI-enforced), SLSA Build L2 attestation, SPDX SBOM, reproducible-build verification, SQLCipher-encrypted dictionary, addon enrollment with signing-pin persistence, 63-script transliteration, bilingual prediction, Espanso snippet expansion, MCP daemon bridge, 21 bundled themes, and 998+ tests with Roborazzi visual gates. It ships features (autocorrect, undo, glide, split/floating/one-handed, inline autofill, spacebar cursor, terminal keys) that upstream FlorisBoard still lacks after six years.

The highest-value direction remains trust operationalization over novelty. The top actionable opportunities:

1. **P0** — Close the release-channel gap (v1.9.48 public vs v1.9.52+ source) and ship the SymSpell OOM fix to issue #9 reporters.
2. **P1** — Migrate deprecated `announceForAccessibility` to live-region accessibility pattern (Android 16+).
3. **P1** — Bump Tink Android 1.21.0 → 1.22.0 (security-relevant cryptographic dependency).
4. **P1** — Make public trust docs trackable, replace destructive clipboard Room migrations, parse data-extraction rules properly.
5. **P2** — Bump AGP 9.2.1 → 9.3.0 to unlock compileSdk 37 (prerequisite for Android 17 APIs).
6. **P2** — Add snippet management Settings UI (Espanso backend exists, no user-facing screen).
7. **P2** — Add TalkBack key echo mode for blind/low-vision users.
8. **P1** — Expose addon provenance export; update addon docs (docs now exist, fixture APK project remains).

## Product Map
- **Core workflows:** enable IME → type (tap/glide/hardware/voice handoff) → manage dictionaries and learned entries → customize themes/layouts/profiles → audit and back up local data → import from SwiftKey/Gboard/FlorisBoard.
- **User personas:** privacy-conscious SwiftKey/Gboard refugees (SwiftKey account retirement completed 2026-05-31), multilingual offline typists, power users (terminal keys, hardware keyboards, Espanso snippets), accessibility users (TalkBack, magnification), addon authors.
- **Platforms and distribution:** Android 8.0+ (minSdk 26, targetSdk 36), GitHub Releases/Obtainium (canonical), F-Droid metadata prepared, no Google Play by design.
- **Key integrations:** FUTO Voice Input handoff, Tasker intents, MCP daemon bridge, addon APK discovery, local dictionary sync envelopes, SwiftKey/Gboard/FlorisBoard dictionary imports, Espanso snippet expansion, Samsung Galaxy AI Writing Assist (works alongside any keyboard on One UI 7+), Grammarly for Android overlay (works alongside any keyboard).

## Competitive Landscape

### FUTO Keyboard
5,363 stars (fcitx5) / 2,755 stars. Offline keyboard with bundled Whisper voice input, RIME-based CJK (Pinyin), and FUTO Swipe — a custom ML swipe engine benchmarking **7.38% top-1 error rate** (vs Gboard 11.05%, iOS 10.82%). Apache-2.0 licensed. Learn from: on-device ML approach, public swipe benchmark methodology, Whisper bundling. Avoid: embedding RIME directly (bloat), source repo complexity (GitLab mirror).

### HeliBoard
5,492 stars. Strongest FOSS direct alternative with F-Droid channel. v4.0-beta1 (2026-06-24) shipped floating keyboard, image clipboard history, foldable display scaling, and touchpad-on-spacebar. NLnet-funded open-source gesture typing library in active development. Learn from: rapid user-request loop, NLnet funding model. Avoid: closed Google gesture library dependency, 731 open issues.

### FlorisBoard (upstream)
8,437 stars. Compose/Snygg theme engine foundation. v0.6.0-alpha02 (2026-01-23). **Still no autocorrect/spell-check after 6 years** — top request (#1283, 33 thumbs up). Emoji search (#45, 80 reactions) and custom layout editor (#196, 22 thumbs up) are the next most-requested features. Learn from: Snygg v2 theming, floating window. Avoid: repeating long-lived "coming soon" NLP promises. SwiftFloris already surpasses upstream on the NLP/autocorrect gap.

### AnySoftKeyboard
3,324 stars, 1,100 open issues. Mature no-network keyboard with separate-APK language packs. v1.13-r1 (2026-02-08) after a 3.5-year release gap. Java codebase, no Kotlin/Compose migration. Learn from: long-standing language-pack distribution model. Avoid: the friction of separate-APK language packs (SwiftFloris's addon model is better).

### Unexpected Keyboard & Thumb-Key
Niche alternatives. Unexpected Keyboard (3,093 stars): 8-direction swipe-per-key, built-in Ctrl/Alt/Esc/Tab for terminal/SSH. Thumb-Key (1,496 stars): 3x3 grid optimized for one-handed thumb typing. Neither is directly competitive, but Ctrl/Alt/Tab modifier keys are requested across multiple keyboards (FUTO #25, FlorisBoard #229). SwiftFloris v1.9.50 shipped a Terminal bottom-row preset addressing this.

### fcitx5-android & Trime
5,363 / 4,422 stars. CJK specialists. fcitx5-android's plugin-as-APK model is the gold standard for input engine isolation. RIME's schema system is the gold standard for CJK customization. Learn from: explicit engine/plugin boundaries. Avoid: importing CJK complexity into the base app while data sourcing and licensing remain unresolved.

### Gboard (Google)
Gemini-powered "Rambler" voice dictation (I/O 2026) with filler-word removal and cross-language code-switching. On-device AI writing tools (tone rewrite, proofreading, grammar) shipped Sept 2025. Virtual trackpad cursor (spacebar hold). These features require cloud/account infrastructure that conflicts with SwiftFloris's posture. SwiftFloris's spacebar cursor mode (v1.9.51) matches the trackpad feature locally.

### SwiftKey (Microsoft)
Account retirement completed 2026-05-31. Cloud dictionary sync shut down. Copilot in-keyboard UI partially removed — users redirected to standalone app. The account churn is actively driving migrations. SwiftFloris already has the SwiftKey JSON import path and migration documentation.

### Samsung Keyboard
One UI 7+ Galaxy AI Writing Assist decoupled from Samsung Keyboard — works with any IME via text-selection popup. Six tools (spelling/grammar, writing style, summarize, bullet points, table, composer). This is positive for SwiftFloris on Galaxy devices: users get AI writing tools without switching keyboards.

## Security, Privacy, and Reliability

### Verified findings (updated 2026-06-25)
- **[Verified] Release-channel gap persists.** GitHub Releases latest is `v1.9.48` (2026-06-14). Source claims `v1.9.52` in `gradle.properties` and README. 9 commits since last release include the SymSpell OOM fix, Espanso snippet expansion, TalkBack glide announcements, and shift-to-toggle-case. Obtainium users are still on the crashing version.
- **[Verified] Issue #9 OOM fix unreleased.** Commit `2790dbdc4` fixes the SymSpell OOM on TECNO LI9 / 5.5 GiB RAM device. Users cannot receive it until the release channel advances.
- **[Verified] `announceForAccessibility` deprecated.** `KeyboardManager.kt:523` uses `announceForAccessibility()` for glide-word TalkBack announcements. This API is deprecated in Android 16 (compileSdk 36, the current target). The `SettingsSearchScreen.kt` already uses the replacement `setAccessibilityLiveRegion(POLITE)` pattern.
- **[Verified] Tink 1.22.0 available.** Current pinned version is 1.21.0. Tink wraps the AndroidKeystore key for SQLCipher passphrase — a security-critical dependency path.
- **[Verified] AGP 9.3.0 available.** Unlocks compileSdk 37 (Android 17) for CJKV `TextAttribute` accessibility, physical keyboard password behavior, and IME visibility behavioral changes.
- **[Verified] Compose BOM 2026.06.00 likely available.** The Roadmap_Blocked.md item for this may have cleared — it was blocked as of 2026-06-16 but the June BOM is now published to Google Maven.
- **[Verified] SQLCipher low-severity CVE.** Defensive-mode bypass in `sqlcipher_export` (CVSS 2.1). Not exploitable in SwiftFloris — no `sqlcipher_export` usage found in the codebase.
- **[Verified] Addon docs now exist.** Previous research noted `docs/addons/apk-validation.md` was missing. It now exists (commit `5be219400`) along with `docs/addons/dictionary-pack-spec.md` (commit `9a0a94a9f`). The remaining gap is the fixture APK project, not the docs.
- **[Verified] Trust doc staleness persists.** `docs/THREAT_MODEL.md` still references v1.8.231 and deleted `PROJECT_CONTEXT.md`. `.gitignore` still excludes these docs from normal tracking.
- **[Verified] Clipboard destructive migration persists.** `ClipboardDatabase.kt:377,437` still calls `fallbackToDestructiveMigration()` in both history and files database builders.
- **[Verified] 30+ `runBlocking` calls in production.** Sites include `AbstractEditorInstance.kt` (keystroke path), `FlorisSpellCheckerService.kt`, `NlpManager.kt`, `NlpProviderRegistry.kt`, and `TextKeyboardCache.kt`. The editor path is documented as CPU-only (invariant comment at line 355), but the breadth of `runBlocking` usage across 12+ files is a latent ANR risk.
- **[Verified] No snippet management UI.** `SnippetManager.kt` supports Espanso YAML import/removal/listing and `SnippetExpansionPolicy` handles expansion, but no Settings screen exists for managing snippets. Users can't discover this feature.
- **[Verified] No TalkBack key echo mode.** No `keyEcho`, `spokenFeedback`, or `spokenKey` code found. Commercial keyboards (Gboard, SwiftKey) offer character/word/both spoken feedback modes for blind users.

### Existing guardrails (strong)
Merged-manifest no-network CI check, `data_extraction_rules.xml` with comprehensive excludes, encrypted dictionary exports (AES-256-GCM/PBKDF2 at OWASP-2025 600K iterations), staged crash reports, OSV high/critical release blocking, release Roborazzi hard gate, 16 KB native alignment guard, SLSA Build L2 attestation, SPDX SBOM, reproducible APK check, SHA-pinned CI action tags, Crowdin token isolation, `pull_request_target` script-injection hardening, per-app keyboard profiles with incognito/sensitivity overrides, `FLAG_SECURE` on password fields, `SensitiveFieldGuard` across all addon surfaces.

## Architecture Assessment

### Boundaries needing attention
- **Release/publication:** The advisory-only GitHub Release check in `scripts/check-release-front-door.sh` must become a blocking gate on normal CI. v1.9.49–v1.9.52 features are committed but not published, leaving issue #9 reporters on the crashing version.
- **Accessibility:** `announceForAccessibility` usage needs migration before it stops working on new Android versions. TalkBack key echo mode (character/word/both) is a standard accessibility feature absent from the codebase.
- **Snippet discovery:** The Espanso expansion pipeline (`SnippetManager` → `SnippetExpansionPolicy` → `KeyboardManager`) works but has no Settings entry point. Users who import Espanso YAML via the filesystem get expansion; everyone else doesn't know the feature exists.
- **Data-safety:** Clipboard Room destructive migration and substring-based data-extraction verification remain unresolved from the previous research pass.
- **Dependency freshness:** Tink 1.21.0 → 1.22.0 (security), AGP 9.2.1 → 9.3.0 (platform unlock), and Compose BOM 2026.05.01 → 2026.06.00 (bug fixes) are available safe bumps.
- **`runBlocking` breadth:** 30+ production `runBlocking` calls across 12+ files. The editor path is documented CPU-only, but `CacheManager.kt`, `NlpProviderRegistry.kt`, `SmartComposeProvider.kt`, and `QuickActionsEditorPanel.kt` use `runBlocking` in less clearly bounded contexts. Not an immediate ANR risk but a latent concern that should be audited before adding more coroutine-heavy paths.

### Strengths
- 274 unit test files (998+ tests), 4 Roborazzi screenshot test suites, 7 androidTest files.
- 8,108-line baseline profile for AOT compilation.
- 17 scripts covering release front-door, benchmark trends, typing quality scorecard, reproducible APK, addon validation, lint drift, repo hygiene, OSV release gating, and fork identity.
- 9 CI workflows covering build, lint, Roborazzi, benchmark regression, emulator smoke, Crowdin, dependency scan, reproducible build, and string validation.
- 44 language translations via Crowdin.
- 200 keyboard layout JSON assets.
- Comprehensive `data_extraction_rules.xml` with documented privacy rationale.
- `SensitiveFieldGuard` gates across 13 source files covering MCP, smart-compose, translation, snippets, and Tasker surfaces.

## Rejected Ideas
- **Cloud sync / GIF search:** No `INTERNET` permission; Citizen Lab research makes no-network proof a strategic differentiator.
- **Bundling closed-source swipe libraries:** Undermines trust promise; HeliBoard's dependency on Google's closed gesture library is a cautionary example.
- **FUTO Swipe / NLnet gesture library now:** Externally gated; already in `Roadmap_Blocked.md`.
- **Full CJK/Rime/fcitx engine in base app:** Runtime size and native complexity conflict with lightweight base; data licensing unresolved.
- **Cloud AI grammar/rewrite:** Samsung Writing Assist already provides this alongside any keyboard on Galaxy devices; network/account conflicts with SwiftFloris posture.
- **In-app self-updater:** Obtainium/F-Droid/GitHub Releases cover updates; self-update increases supply-chain risk.
- **Smart reply / ML-powered canned responses:** Requires on-device NLP model in the base app; the Espanso snippet + quick phrase path is the realistic offline equivalent. ML belongs in addons.
- **Formal security audit now:** No keyboard has had one (GrapheneOS noted this gap). High value but requires external engagement (OSTIF-style), not code work. Track as an operational goal.
- **`runBlocking` mass migration:** The 30+ sites are a latent concern but not an immediate ANR risk; the editor path is documented CPU-only. A blanket migration is high-churn/low-impact. Audit individual sites only when adding new coroutine-heavy paths.
- **Compose BOM 2026.06.x as active roadmap:** Already tracked in `Roadmap_Blocked.md`; blocker may have cleared — check Maven and move back if so.
- **Room 3.0 migration now:** Still alpha; Room 2.8.4 is the latest stable 2.x; already tracked as blocked.
- **AGP 10.0 migration now:** Not released; already tracked as blocked.
- **Active glide cap tuning:** Hard-coded `MAX_SIZE = 500` TODO exists but the public glide benchmark harness and FUTO dataset evaluation are in `Roadmap_Blocked.md`.
- **Han language-pack database locking:** `loadLock` already serializes loads.
- **Backup-rule rewrite:** Rules are already allowlist/exclude based; needed work is verifier precision only.
- **Switch Access scanning:** Requires device validation; already in blocked planning.
- **Kotlin 2.4 collection literals:** Experimental; already in blocked planning.

## Sources

### OSS Competitors
- https://github.com/florisboard/florisboard
- https://github.com/HeliBorg/HeliBoard
- https://github.com/futo-org/android-keyboard
- https://keyboard.futo.org/
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/Julow/Unexpected-Keyboard
- https://github.com/dessalines/thumb-key
- https://github.com/keymanapp/keyman
- https://github.com/espanso/espanso

### Commercial and Community
- https://9to5google.com/2026/05/12/gemini-intelligence-announcement/
- https://support.microsoft.com/en-us/topic/faqs-for-copilot-changes-in-swiftkey-c02289e6-c5b3-401c-af8d-f6c88409a2d2
- https://www.sammobile.com/news/one-ui-7-0-galaxy-ai-writing-tools-any-keyboard/
- https://support.grammarly.com/hc/en-us/articles/15606282682637-Grammarly-for-Android-user-guide
- https://citizenlab.ca/2024/04/chinese-keyboard-app-vulnerabilities-explained/
- https://citizenlab.ca/wp-content/uploads/2024/05/Report175-keyboardvuln-050824.pdf
- https://www.androidpolice.com/spent-years-switching-android-keyboards-this-one-changed-everything/
- https://www.howtogeek.com/open-source-android-keyboards-that-rival-gboard/
- https://grapheneos.social/@GrapheneOS/113444555346758882

### Platform, Standards, and Dependencies
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/16/features
- https://developer.android.com/developer-verification
- https://f-droid.org/en/2026/02/24/open-letter-opposing-developer-verification.html
- https://f-droid.org/en/docs/Reproducible_Builds/
- https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html
- https://developer.android.com/identity/autofill/credential-manager-autofill
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/training/data-storage/room/migrating-db-versions
- https://developer.android.com/privacy-and-security/risks/backup-best-practices
- https://developer.android.com/guide/topics/ui/accessibility/principles
- https://www.w3.org/TR/wcag2mobile-22/

## Open Questions
- Was the v1.9.49–v1.9.52 public release lag intentional, or did the release workflow fail? Should the release-channel freshness gate fail on normal CI?
- Should `docs/THREAT_MODEL.md` and `docs/PRIVACY_AND_AI.md` become tracked public docs, or should README/CI stop treating them as public trust surfaces?
- Has Compose BOM `2026.06.00` been published to Google Maven? If so, move the blocked item back to `ROADMAP.md`.
- Should developer verification registration happen before or after F-Droid acceptance? The registration costs $25 and a government ID; F-Droid's response may shape the decision.
- Is the `runBlocking` usage in `NlpProviderRegistry.kt:63` and `SmartComposeProvider.kt:89` bounded enough to be safe, or should those sites be migrated to suspend-based patterns?
