# SwiftFloris — Research and Feature Plan

> **The live open-work list is now [`TODO.md`](TODO.md).** The open `F#`/`EI#` items
> from this plan have been consolidated there with their current status; this file is
> preserved as the sourced research record. Closed items are in [`CHANGELOG.md`](CHANGELOG.md).

**Run date:** 2026-05-25
**HEAD:** `ad4d8ca` (v1.8.173, released 2026-05-18)
**Branch:** `master`, 178 commits ahead of origin (push from dev VM blocked, see [CLAUDE.md](CLAUDE.md))
**Reading order:** [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) → [AGENTS.md](AGENTS.md) → this file → [ROADMAP.md](ROADMAP.md) §6/§7/§8 → [IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md) workstreams 10/11/12/13/14/15.

This is the 2026-05-25 research-and-feature plan. It is **additive** to [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/) and to the live [`ROADMAP.md`](ROADMAP.md) external-work backlog (§10.5) and [`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md). It does **not** rewrite either. The mandate of this file is: surface the high-value gaps that the 2026-05-17 run (and the subsequent v1.8.41 → v1.8.173 implementation stream) did not close, plus the new evidence that has accrued since 2026-05-17.

The prompt that produced this file is the canonical "Autonomous Deep Research" prompt. The prior week's prompt produced [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/) — that run is the model for cadence and artifact shape. The next full research run goes in `.ai/research/2026-06-01/` per convention.

---

## Executive Summary

SwiftFloris at v1.8.173 is materially a Swiftkey-class privacy-first IME with a long, well-disciplined roadmap, a strict build-gate posture (no `INTERNET`, no closed `.so`, Apache-2.0 ceiling), a 21-theme Snygg surface, an addon-pack runtime (`AddonRegistry` + signing-pin trust store + dictionary-pack asset mounting), an MCP daemon bridge, encrypted personal dictionary (SQLCipher + Tink/AndroidKeystore), and 6 hot-path Macrobenchmark baselines committed against a Galaxy SM-S938B / Android 16 reference. The seventh-pass audit (`.ai/research/2026-05-17/SEVENTH_PASS_FINDINGS.md`) closed every previously-open privacy regression in v1.8.104 → v1.8.122. IMPROVEMENT_PLAN Workstreams 1–9 are complete; Workstream 10 (Product UX polish) is at one open task; Workstreams 11 (Keyboard Surface Polish), 13 (Privacy / Safety / Data Integrity), 15 (Manual QA / Release Evidence) remain Planned. The single largest **immediate** gap is not on the roadmap at all: **F-Droid metadata (fastlane) is severely stale — the listing would show `FlorisBoard` "currently in beta" with FlorisBoard v0.3.16-era changelogs**, which would torpedo the SwiftKey-migration-window outreach if a fdroiddata PR landed today. The migration window closes 2026-05-31 (six days from this run).

**Top 10 opportunities in priority order:**

1. **P0** — Fastlane / F-Droid metadata rewrite so the listing reads as SwiftFloris, not FlorisBoard 0.3.16 (Verified: `fastlane/metadata/android/en-US/title.txt` says `FlorisBoard`; latest `changelogs/86.txt` is FlorisBoard v0.3.16 content; SwiftFloris is on versionCode `1973`). Blocks the SwiftKey-refugee migration window.
2. **P0** — Repo-root hygiene closure: remove tracked `app-release-v1.5.2.apk` (9.7 MB) and `SwiftFloris_icon.png` (787 KB) from working tree + git history-trim under a one-time `release-only` branch, gate future tracked APKs via `scripts/check-repo-hygiene.sh`. Verified in tree as of 2026-05-25.
3. **P0** — IMPROVEMENT_PLAN Workstream 13 (Privacy, Safety, Data Integrity) — six Planned audit items remain; this is the workstream most directly tied to the wedge claim.
4. **P0** — IMPROVEMENT_PLAN Workstream 11 (Keyboard Surface Polish) — seven Planned items including candidate-row state audit, smartbar overflow, software-key states, layout variants, autocorrect-toggle placeholder feedback. Closes long-standing competitor-pain items COMM-A / COMM-D / COMM-E.
5. **P1** — Roborazzi baseline expansion from 3 surfaces to ≥21 themes × 4 surfaces (keyboard, smartbar, popup, settings preview) so visual regressions on every theme go through the hard gate.
6. **P1** — Glide trail theme test coverage + Roborazzi baseline (just shipped in v1.8.172; not visually pinned).
7. **P1** — SwiftKey migration-window UX: a `SwiftKey backup detected in Downloads/` first-run hint, plus a `paste-into-importer` clipboard hand-off; we ship the importer (v1.8.46 / N16.2) but the discovery surface is buried under Settings → Personal dictionary → Import. Window closes 2026-05-31.
8. **P1** — Address Workstream 14 remaining items (Room nullable DAO warning, Kotlin compiler flags, remaining synchronous toast calls in theme/devtools/keyboard/clipboard surfaces).
9. **P2** — F-Droid `Reproducible` tier submission — the reproducible-build self-check workflow exists (v1.8.67), pin matrix is documented, but the `fdroiddata` PR has not been submitted (Verified: no record in repo; `docs/REPRODUCIBLE_BUILDS.md` says the F-Droid side is the next step).
10. **P2** — CycloneDX SBOM + SLSA provenance attestation in `release.yml`. Currently OSV-Scanner runs in CI but no SBOM artifact attaches to releases. Closes the supply-chain story for F-Droid reviewers and for the SECURITY.md disclosure surface.

The plan below is exhaustive but practical. Sections cross-reference live files with line numbers wherever possible. Every claim is marked **Verified** / **Likely** / **Assumption** / **Needs validation**.

---

## Evidence Reviewed

### Local files and directories
- Root planning docs: [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) (766 lines), [ROADMAP.md](ROADMAP.md) (2,162 lines), [IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md) (516 lines), [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md), [ARCHITECTURE.md](ARCHITECTURE.md), [CONTRIBUTING.md](CONTRIBUTING.md), [README.md](README.md), [CHANGELOG.md](CHANGELOG.md) (top 400 lines)
- Research snapshot: every file in [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/), specifically `STATE_OF_REPO.md`, `MEMORY_CONSOLIDATION.md`, `SOURCE_REGISTER.md`, `RESEARCH_LOG.md`, `COMPETITOR_MATRIX.md`, `FEATURE_BACKLOG.md`, `PRIORITIZATION_MATRIX.md`, `SECURITY_AND_DEPENDENCY_REVIEW.md`, `DATASET_MODEL_INTEGRATION_REVIEW.md`, `CHANGESET_SUMMARY.md`, `FIFTH_PASS_FINDINGS.md`, `SIXTH_PASS_FINDINGS.md`, `SEVENTH_PASS_FINDINGS.md`
- Code: [app/build.gradle.kts](app/build.gradle.kts), [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml), [gradle.properties](gradle.properties), the IME package tree under [app/src/main/kotlin/dev/patrickgold/florisboard/ime/](app/src/main/kotlin/dev/patrickgold/florisboard/ime/) (36 subpackages), [app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/) (15 screen groups), [app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt)
- CI/release: [.github/workflows/](.github/workflows/) (8 workflows), [.github/dependabot.yml](.github/dependabot.yml), [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/), [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md), [scripts/](scripts/) (6 scripts), [tools/](tools/) (6 benchmark harnesses)
- Distribution metadata: [fastlane/metadata/android/en-US/](fastlane/metadata/android/en-US/) — title, full_description, short_description, changelogs/, images/, full layout
- Tests: 204 Kotlin test files under [app/src/test/kotlin/](app/src/test/kotlin/); 5 androidTest files; Roborazzi baselines at [app/src/test/snapshots/](app/src/test/snapshots/) (only 3 PNGs across two categories — `extension_maintainer_chip/` and `theme_and_addons/`)
- Docs surfaces: every file in [docs/](docs/) including `BENCHMARKS.md`, `PRIVACY_AND_AI.md`, `THREAT_MODEL.md`, `SECURITY.md`, `INLINE_AUTOFILL.md`, `LOCAL_VERIFICATION.md`, `MIGRATE_FROM_SWIFTKEY.md`, `REPRODUCIBLE_BUILDS.md`, `SQLCIPHER_PROVIDER_MIGRATION.md`, `AUTOCORRECT_LIFECYCLE.md`, `ACCESSIBILITY.md`, `SPLIT_KEYBOARD.md`, `DEPENDENCY_TRIAGE.md`, `REPO_HYGIENE.md`

### Git history
Commits `ad4d8ca` (HEAD, v1.8.173) back to `aca272c` (v1.8.41) on branch `master`. Recent slice: v1.8.171 retired the per-version `RELEASE_NOTES_v*.md` pattern in favour of a single `CHANGELOG.md` with `<a id="vX.Y.Z">` anchors; v1.8.172 shipped 7 selectable glide-trail themes (Accent, Rainbow, Fire, Ice, Aurora, Galaxy, Neon) + glide perf fixes (path-based stroke, classifier LRU cache, preview-job cancellation) + draw-gate bypass; v1.8.173 patched an `EmojiCompat.process()` "Not initialized yet" race that crashed the IME on emoji picker open.

### Build/test/docs/release artifacts
- Gradle gates: `verifyNoInternetPermission`, `verifyDataExtractionRules`, `verifyRoborazziDebug`, `lintDebug`, `assembleDebug`
- CI: `android.yml`, `release.yml`, `dependency-scan.yml`, `reproducible-build.yml`, `crowdin-upload.yml`, `emulator-smoke.yml`, `roborazzi-baseline.yml`, `validate-strings-no-translations.yml`
- Benchmarks: 6 baselines committed at [docs/benchmark-results/](docs/benchmark-results/) for first-render / first-suggestion / dictionary load / candidate-row recomposition / theme-switch / backup-restore, all on Galaxy SM-S938B / Android 16

### External research
Refreshed by the parallel `general-purpose` agent — see §[Competitive and Ecosystem Research](#competitive-and-ecosystem-research) below. Primary sources used: HeliBoard / FUTO / FlorisBoard / AnySoftKeyboard release pages, Microsoft SwiftKey retirement support page, F-Droid Reproducibility Status pages, LiteRT-LM Google docs, Android API 37 dev preview docs.

### Areas that could not be verified
- F-Droid `fdroiddata` PR status (Likely: not yet submitted — no maintainer note in repo confirms otherwise).
- Whether Obtainium auto-update has actually been smoke-tested with the unsigned-fork fallback path (Assumption: works, per [N6.5](ROADMAP.md) shipped notes).
- Reproducible-build workflow's actual byte-exact pass on a green main — last run history not inspected in this pass; only the workflow YAML and `scripts/verify-reproducible-apk.sh` were read (Needs live validation).
- Glide trail rendering performance on low-end devices < Galaxy A-series 8GB (v1.8.172 perf claims are against SM-S938B only — Needs live validation on Android Go-class hardware).
- Crowdin upload workflow's actual write-back path is wired (Assumption: yes, per `crowdin.yml` and `.github/workflows/crowdin-upload.yml`; not exercised this pass).

---

## Current Product Map

### Core workflows (user-facing)
1. **First-run setup** — `app/setup/SetupScreen.kt` runs the AI/ML transparency disclosure (N8.7, shipped v1.8.66), IME enablement step, default-keyboard selection step, optional notification permission, then exits to Settings home. Verified via [SetupScreen.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt) (301 lines).
2. **Daily typing** — Compose `TextKeyboardLayout` renders one of 21 bundled Snygg themes (SwiftKey Pure Light/Dark + M3E variants, SwiftKey High Contrast AAA, Aurora Animated, Floris Day/Night, Swift Glacier/Slate, M3E Nord light/dark, Tokyo Night, Dracula, Catppuccin Mocha, plus the honeycomb hex selectable layout). `KeyboardManager` dispatches to `EditorInstance` for commit, to `NlpManager` + `LatinLanguageProvider` for suggestions, to `GlideTypingManager` + `StatisticalGlideTypingClassifier` for swipes.
3. **Personal dictionary management** — Settings → Typing → Personal dictionary: add/edit/delete shortcuts + words, import/export (Gboard XML, FlorisBoard CSV, SwiftKey JSON, SwiftFloris encrypted blob — v1.8.54 / v1.8.65), forget-learned-word (N12.10), instant-remember overlay (v1.8.26 → v1.8.28).
4. **Voice input** — FUTO Voice Input handoff (live), Vosk-streaming fallback contract (facade only — Local recognizer runtime not yet bundled, gated `VoiceLocalRecognizerRuntime.AVAILABLE`), RAM-aware model selector, Settings catalog preview-only.
5. **Clipboard** — Room-backed local history with pinning + per-app source tag + sensitive-clip gates (cut/copy + EXTRA_IS_SENSITIVE) + media-clone reconciliation + filtered search.
6. **Emoji + stickers + media** — palette with search/history/pinned-groups (v1.8.127), 2 bundled sticker packs, user-imported sticker folder via SAF (v1.8.77), `commitContent` rich-content path.
7. **Calendar quick-insert** — `QuickAction.InsertCalendarEvent` (v1.8.64) reads `CalendarContract.Instances` for today + next 7 days with explicit `READ_CALENDAR` opt-in.
8. **Task quick-insert** — `QuickAction.InsertTask` (v1.8.58) sends selected text to user-chosen task / note apps via `Intent.ACTION_SEND` chooser.
9. **Addon enrolment** — Settings → Addons reads accepted/rejected addons via `AddonRegistry`, lets users rescan, trust changed certificates, or reset all pins. Asset-mounted dictionary packs feed `LatinDictionaryStore`.
10. **MCP daemon bridge** — Settings → MCP daemon bridge surfaces user-installed MCP daemons, per-daemon enable/disable, per-tool switches. AIDL binder transport; signature-protected; on-device only.
11. **Migration paths** — SwiftKey JSON cloud-export importer (v1.8.46), FlorisBoard CSV, Gboard XML, KLC / `.keylayout` / Keyman LDML / `.kmp` hardware-keyboard imports.
12. **CRDT personal-dictionary sync foundation** — `PersonalDictionaryCrdt`, `PersonalDictionaryCrdtMerger`, `PairingPayload`, `SyncChannel{Syncthing, LocalFolder, ManualExport, Disabled}`, Settings → Sync QR-pairing UI. Transport implementation pending Next-5.2a (libsodium sealed-box wrap).

### Existing features (high-level inventory)
- 21 themes, Snygg theme engine, M3 Expressive, animated themes, per-app accent (off by default)
- Autocorrect with SymSpell d1+d2, bigram + trigram, sentence-position priors, cold-start phrase priors, multilingual ranker, secondary-locale demotion, glide context rescore
- Smartbar with quick-actions (drag-and-drop reorder, per-app profiles for PASSWORD/CHAT/EMAIL/CODE), inline autofill (Bitwarden + KeePassDX + Proton Pass + 1Password + Aegis matrix), bottom-row presets (SwiftKey/Navigation/Programmer/etc.)
- Stylus handwriting facade (`StrokeRecognizer` contract + `prefs.keyboard.stylusHandwritingEnabled`); ML Kit recogniser ships in `addons/handwriting-mlkit/` (external, not in repo)
- WebAuthn passkey field detector (`PasskeyFieldDetector` + adapter contract)
- WordStyles facade (`WordStylesRenderer`); renderer implementation pending L12.1
- Tasker integration (`TaskerActionReceiver`, signature-protected, 4 actions)
- Smart compose / inline translation / CJK / Bergamot — all facades shipped, addons external
- Encrypted personal-dictionary export/import (AES-256-GCM + PBKDF2-HMAC-SHA-256)

### User personas
1. **SwiftKey refugee** — values multilingual + clipboard + cross-sentence prediction; needs migration plumbing and brand-identity reassurance. Window closes 2026-05-31.
2. **HeliBoard / FlorisBoard upgrader** — values FOSS auditability; wants better SwiftKey-parity ranker, encryption-at-rest, addon ecosystem.
3. **Privacy-first (Privacy Guides / r/PrivacyTools / r/degoogle)** — values no-INTERNET, no telemetry, reproducible builds, F-Droid verified tier.
4. **Multilingual writer (EU / Latin America / India)** — values per-token language identification, bilingual subtype presets, Indic transliteration, CJK once L3 ships.
5. **Power user / programmer / terminal / IDE on mobile** — values CODE bottom-row preset, hardware-keyboard layout import, snippet/Espanso, Tasker, MCP daemon.
6. **Accessibility user** — TalkBack labels, dynamic font scaling, theme contrast AAA, reduced motion, hardware switch access. Workstream 6 closed in v1.8.158.

### Platforms and distribution
- **GitHub Releases** (canonical) — keystore-signed APK + SHA-256 in release body
- **Obtainium** (recommended for auto-updates) — one-tap subscribe URL in README
- **F-Droid `Reproducible` tier target** — toolchain pin matrix in [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md); `fdroiddata` PR pending
- **IzzyOnDroid** mirror target — not yet listed
- **Accrescent** — out of scope unless a separately-signed mirror track ships
- **Not on Google Play** by design (forces target-SDK churn + Integrity-API entanglement)

### Important integrations, permissions, storage, or data flows
- Declared permissions: `VIBRATE`, `POST_NOTIFICATIONS`, `READ_CALENDAR`. Verified via [AndroidManifest.xml](app/src/main/AndroidManifest.xml).
- Custom permission: `dev.patrickgold.florisboard.permission.REGISTER_ADDON` (signature-protected).
- Encrypted local storage: SQLCipher 4.16.0 + Tink 1.21.0 + AndroidKeystore AES-256-GCM
- Backup exclusion: `backup_rules.xml` + `data_extraction_rules.xml` (the latter is `verifyDataExtractionRules`-gated)
- Content providers: `ClipboardMediaProvider`, `StickerMediaProvider`
- IPC: AIDL `IMcpDaemon` for MCP daemon bridge; Tasker receiver behind `REGISTER_ADDON` permission
- No `INTERNET` permission — gate-enforced via `verifyNoInternetPermission`

---

## Feature Inventory

The full feature inventory (with file paths and maturity status) is the live [`ROADMAP.md`](ROADMAP.md) §3 "Recently Shipped" through v1.8.84, augmented by every release entry in [`CHANGELOG.md`](CHANGELOG.md) under `## v1.8.85` → `## v1.8.173`. This file does not duplicate that catalog. Below is a delta — only the features whose **maturity has materially changed** since the 2026-05-17 research snapshot, or which have an **improvement opportunity** that the prior snapshot did not surface.

### Glide trail (v1.8.172)
- **User value:** Visual feedback during gesture typing; differentiator against HeliBoard's basic dotted-circle trail
- **Entry point:** Settings → Gestures → Glide Typing → Trail theme (7 themes: Accent, Rainbow, Fire, Ice, Aurora, Galaxy, Neon)
- **Main code locations:** [GlideTrailTheme.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/GlideTrailTheme.kt), [TextKeyboardLayout.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt), [GlideTypingManager.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/GlideTypingManager.kt), [StatisticalGlideTypingClassifier.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/glide/StatisticalGlideTypingClassifier.kt)
- **Maturity:** complete; visible to users
- **Tests/docs coverage:** Verified — no Roborazzi baseline; no unit test for `GlideTrailTheme` enum
- **Improvement opportunities:** (a) Roborazzi baseline per trail theme on a representative key sequence; (b) low-end-device perf evidence (current numbers are SM-S938B / Android 16 only); (c) reduced-motion gate verified for the per-pixel heat-shimmer animation in `Fire`; (d) document the trail-theme picker in `docs/ACCESSIBILITY.md` with the photosensitivity/seizure note for Rainbow/Aurora; (e) test the alpha-gradient guard (`alpha < 0.1 → Color.Green`) — Likely not covered.

### F-Droid distribution surface (versionCode 1973)
- **User value:** Primary FOSS distribution channel; verified-reproducible tier when submitted; F-Droid listing is the first surface most privacy-Android users see.
- **Entry point:** [fastlane/metadata/android/en-US/](fastlane/metadata/android/en-US/) read by `fdroiddata` build process.
- **Main code locations:** [fastlane/metadata/android/en-US/title.txt](fastlane/metadata/android/en-US/title.txt), [short_description.txt](fastlane/metadata/android/en-US/short_description.txt), [full_description.txt](fastlane/metadata/android/en-US/full_description.txt), [changelogs/](fastlane/metadata/android/en-US/changelogs/), [images/](fastlane/metadata/android/en-US/images/)
- **Maturity:** **broken — content is stale upstream FlorisBoard from v0.3.16 era**
  - `title.txt` reads `FlorisBoard` (Verified)
  - `short_description.txt` reads `An open-source keyboard which respects your privacy. Currently in beta.` (Verified)
  - `full_description.txt` opens with `<i>FlorisBoard</i> is an open-source keyboard…` (Verified)
  - Latest changelog file is `changelogs/86.txt`, with body `Detailed changelog: https://github.com/florisboard/florisboard/releases/tag/v0.3.16` (Verified)
  - SwiftFloris is on versionCode `1973` — there are no SwiftFloris-era changelog files at all
- **Tests/docs coverage:** none
- **Improvement opportunities:** P0 rewrite (see Roadmap below). Add a CI step that fails when `gradle.properties:projectVersionCode` is bumped without a matching `fastlane/metadata/android/en-US/changelogs/${projectVersionCode}.txt` file.

### Repo-root artifacts
- **User value:** none — pure repo bloat
- **Main code locations:** [app-release-v1.5.2.apk](app-release-v1.5.2.apk) (9.7 MB, Verified May 4 timestamp), [SwiftFloris_icon.png](SwiftFloris_icon.png) (787 KB, Verified May 4 timestamp), [ROADMAP.md.backup-v2](ROADMAP.md.backup-v2) (21 KB, Verified)
- **Maturity:** stale — `.gitignore` already declares `*.apk` (Verified line 1 of [.gitignore](.gitignore)); the APK was force-added before that rule. `ROADMAP.md.backup-v2` is a pre-rewrite backup that should live in `docs/archive/` if kept at all.
- **Tests/docs coverage:** [scripts/check-repo-hygiene.sh](scripts/check-repo-hygiene.sh) covers build/report tree but does not reject root-level large binaries.
- **Improvement opportunities:** (a) `git rm --cached app-release-v1.5.2.apk SwiftFloris_icon.png` and add a hygiene-script check for any top-level `*.apk` / `*.aab` / large PNG / `*.backup*`; (b) move the icon to `assets/icon.png` or `fastlane/metadata/android/en-US/images/icon.png` if it's actually referenced for branding.

### Roborazzi visual-regression baseline coverage
- **User value:** Pin every theme/component against accidental visual regressions; one of the load-bearing release gates per `IMPROVEMENT_PLAN.md`.
- **Entry point:** `:app:verifyRoborazziDebug` (hard CI gate per v1.8.123)
- **Main code locations:** [app/src/test/snapshots/extension_maintainer_chip/](app/src/test/snapshots/extension_maintainer_chip/) (3 baselines for one widget), [app/src/test/snapshots/theme_and_addons/](app/src/test/snapshots/theme_and_addons/) (3 baselines: `addons_settings_registry_surface.png`, `aurora_animated_keyboard_surface.png`, `swiftkey_high_contrast_keyboard_surface.png`)
- **Maturity:** partial — only 6 PNG baselines exist; the SwiftFloris brand promise is **21 themes** and at least 9 distinct keyboard surfaces (letters, symbols, smartbar, popup, candidate row, clipboard panel, emoji panel, addons settings, sync settings)
- **Tests/docs coverage:** Verified — Roborazzi 1.60.0 + Robolectric 4.16.1 wired in v1.8.71; capture workflow at `.github/workflows/roborazzi-baseline.yml`
- **Improvement opportunities:** Expand to a matrix of 21 themes × 4 surfaces (≈84 baselines), gated by a new `:app:recordRoborazziDebug` workflow run on the maintainer host. Without this, any theme stylesheet edit can silently regress 20 themes and slip CI.

### Voice input (FUTO handoff + preview-only local catalog)
- **User value:** Offline dictation via FUTO Voice Input handoff; local Whisper/Vosk catalog framed as preview-only since v1.8.120 because the actual on-device recognizer runtime has not landed.
- **Entry point:** Voice key on smartbar; Settings → Voice input
- **Main code locations:** [ime/voice/](app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/) — `VoiceInputManager.kt`, `VoiceModelSelector.kt`, `VoiceRecognitionEngineSelector.kt`, `StreamingVoiceTranscriptBuffer.kt`, `VoiceLocalRecognizerRuntime.kt`, `VoiceModelCatalog.kt`, `VoiceInputSetupActivity.kt`
- **Maturity:** partial — only FUTO handoff is wired; local recognizer route returns `AVAILABLE = false`; the Settings catalog shows downloads as disabled.
- **Tests/docs coverage:** Verified — [docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md](docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md), [docs/VOICE_COMMANDS.md](docs/VOICE_COMMANDS.md), 19 findings tracked in `.ai/research/2026-05-17/SEVENTH_PASS_FINDINGS.md` §2; 7 shipped, 12 still open
- **Improvement opportunities:** The product is **honest** about local-runtime absence (good). Specific opportunities: (a) ship a single bundled Vosk small-en-us-zamia model (40 MB) + JNI as the first real local recogniser to retire the "preview only" framing; (b) audit the "FUTO Voice Input not installed" empty-state copy in Settings against the 2026-05-31 SwiftKey-window users (most will not know what FUTO is); (c) ship a `RECORD_AUDIO` permission flow only when the local runtime is enabled (no permission creep in the base APK).

### Honeycomb hex layout (v1.8.79)
- **User value:** Typewise / hex-layout pattern, vacated when Typewise pivoted to enterprise AI in 2026.
- **Entry point:** Subtype → Character layout → `honeycomb`
- **Main code locations:** [ime/text/keyboard/HoneycombHexShape.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombHexShape.kt), `HoneycombHexButton.kt`, `HoneycombKeyboardRow.kt`, `HoneycombLayoutLoader.kt`, `TextKeyboardLayoutStyle.kt`, `TextKeyboard.layoutHoneycomb(...)`
- **Maturity:** complete shipment but **near-zero promotion** — no Roborazzi baseline (so theme regressions on honeycomb won't fire), no `docs/HONEYCOMB_LAYOUT.md`, no README/CHANGELOG honeycomb-tip-callout, no SwiftKey-window outreach hooking it. Verified.
- **Tests/docs coverage:** Likely some unit coverage; visual baseline absent.
- **Improvement opportunities:** (a) ship a `docs/HONEYCOMB_LAYOUT.md` with the design decisions (hex tessellation, gap rejection, accuracy claims) and the "Typewise's killer feature, FOSS"-style positioning; (b) add a honeycomb screenshot to README and to F-Droid fastlane images; (c) Roborazzi baseline for honeycomb in 4 themes.

### MCP daemon bridge
- **User value:** Local LLM tool surface (calendar, weather, SMS via Deskdrop pattern) without any cloud round-trip; differentiator vs every other OSS keyboard.
- **Entry point:** Settings → MCP daemon bridge
- **Main code locations:** [ime/mcp/](app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/) — `McpBridgeContract.kt`, `McpClient.kt`, `McpServiceConnectionManager.kt`, `McpAndroidDiscoverer.kt`, `McpServiceLifecycle.kt`, `McpDispatchRouter.kt`, `IMcpDaemon.aidl`, `McpToolDescriptor.kt`, `McpToolResult.kt`
- **Maturity:** complete end-to-end as of v1.8.40, gated by per-daemon enable/disable + per-tool allowlist (matrix #38)
- **Tests/docs coverage:** Verified contract tests; **no end-user docs** explaining what an MCP daemon is, how to install one, why a user would want to.
- **Improvement opportunities:** (a) write `docs/MCP_DAEMON_BRIDGE.md` — what it is, what an example daemon looks like (point at Deskdrop), how to install one, how the security model works (signature-protected `BIND_MCP` + per-tool allowlist + on-device-only); (b) ship a sample reference daemon APK source in `addons/mcp-daemon-reference/` so the surface has at least one concrete example a user can actually try; (c) Settings → MCP daemon bridge first-time empty state should explain the concept and link to `docs/MCP_DAEMON_BRIDGE.md`.

### Settings information architecture
- **User value:** Discoverability — settings density is now high; with 15 top-level screens + addons + MCP + sync (most of them deep), users hunt for switches.
- **Entry point:** [app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt)
- **Main code locations:** 15 sub-packages under `app/settings/`: `HomeScreen.kt`, `about/`, `addons/`, `advanced/`, `clipboard/`, `dictionary/`, `gestures/`, `keyboard/`, `localization/`, `mcp/`, `media/`, `smartbar/`, `sync/`, `theme/`, `typing/`, `voice/`
- **Maturity:** Workstream 10 polish closed empty-states + progress cards + busy-state gating; settings IA itself has not had a top-down audit.
- **Tests/docs coverage:** `FlorisScreenFocusOrderTest`, accessibility notes in `docs/ACCESSIBILITY.md`, partial coverage in `IMPROVEMENT_PLAN.md` Workstream 10.
- **Improvement opportunities:** (a) Settings → search; a settings search index is a near-universal expectation in 2026 and the project has 200+ user-facing prefs; (b) reorganise the home-screen hierarchy into a "Typing experience / Personalization / Privacy & data / Advanced / About" grouping — currently each manager screen is at the same depth; (c) Settings → About → "what's new in this release" inline excerpt of the latest CHANGELOG entry; (d) "Reset all settings" destructive action under About → Reset (with restore-from-backup as the recovery path).

### Sync (Settings → Sync; Next-5.1 → 5.3a)
- **User value:** P2P personal-dictionary sync between user's own devices over Syncthing / SAF folder / manual export; no vendor account; mirrors the SwiftKey cloud-sync use case
- **Entry point:** Settings → Sync
- **Main code locations:** [ime/sync/](app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/) — `PersonalDictionaryCrdt.kt`, `PersonalDictionaryCrdtMerger.kt`, `PairingPayload.kt`, `SyncChannel.kt`, `SyncSettingsScreen.kt`
- **Maturity:** **foundation only** — CRDT model + merger + channel taxonomy + QR pairing display + paired-device list shipped; libsodium sealed-box payload wrap pending (Next-5.2a); actual transport implementation pending; the Syncthing folder-watch path is not wired.
- **Tests/docs coverage:** Verified — 8 invariants in `PersonalDictionaryCrdtTest`, 7 in `SyncChannelTest`, sync-pair UI model tests.
- **Improvement opportunities:** (a) ship the libsodium sealed-box wrap so the on-disk pairing artefact is actually encrypted; (b) wire one concrete transport — Syncthing folder watcher is the cheapest path because Syncthing handles encryption + sync semantics; (c) document the sync threat model in `docs/SYNC_THREAT_MODEL.md` (when does a user with two SwiftFloris devices need to think about this?).

---

## Competitive and Ecosystem Research

The 2026-05-17 [COMPETITOR_MATRIX.md](.ai/research/2026-05-17/COMPETITOR_MATRIX.md) is the baseline. The deltas below are what has materially changed since then.

### Microsoft SwiftKey for Android — account retirement **2026-05-31** (Verified — re-confirmed in `.ai/research/2026-05-17/FIFTH_PASS_FINDINGS.md` §3)
- The `data.swiftkey.com` cloud-export endpoint shuts on 2026-05-31. Non-MS-account learned vocab + shortcuts + clipboard sync are permanently gone after that date.
- **What this project should learn:** the window is now **6 days** from this run (2026-05-25 → 2026-05-31). Every Tier-1 migration-window task in `PRIORITIZATION_MATRIX.md` ships before then or misses the audience.
- **What this project should avoid:** building features that look like SwiftKey Cloud (cloud-bound personalization). The whole point of the migration window is users explicitly choosing not to repeat that mistake.

### HeliBoard (Helium314/HeliBoard) — `v3.9` (2026-03-29) still latest
- NLnet open-glide project deadline 2026-06-01 — base-case slip past the deadline. Library + dataset still unreleased.
- Volunteer data collection running through 2026-11-30; passive mode "still tuning."
- **What this project should learn:** treat `swiftfloris-statistical` as production default (already done per v1.8.72); keep the additive-integration door open behind `prefs.glide.engine`.
- **What this project should avoid:** premature dependency on the NLnet drop; it may not land in 2026.

### FUTO Keyboard — `0.1.28` (2026-05-04)
- Adds Traditional + Simplified Chinese (Pinyin, fuzzy Pinyin, Double Pinyin, rudimentary stroke) + Vietnamese Telex/VNI.
- Removed transformer finetuning (was unstable).
- **What this project should learn:** the FOSS CJK gap is closing without SwiftFloris. If we want any SwiftFloris user share in CJK, promote L3 (librime JNI addon) ahead of L4 in the next planning pass.
- **What this project should avoid:** FUTO is Source-First (non-OSI); architecture-borrow only.

### FlorisBoard upstream — `v0.5.2` (2025-11-28), still frozen at v0.6.0-alpha02 (2025-01-23) on `main`
- SwiftFloris is lapping a stalled upstream, not drifting from a moving target (per `.ai/research/2026-05-17/FEATURE_BACKLOG.md` §1).
- **What this project should learn:** the rebase risk thesis has weakened materially. SwiftFloris can plan multi-quarter work without worrying about a heavy upstream merge.
- **What this project should avoid:** smug positioning. If upstream resumes, SwiftFloris's ranker / addons / no-INTERNET gate are the differentiators, not the fork itself.

### LeanType (LeanBitLab/LeanType) — `v3.7.9` (2026-05-17)
- HeliBoard fork, GPL-3.0.
- Standard / Offline / Offline Lite SKU pattern.
- **What this project should learn:** the "offline AI keyboard" market exists. SwiftFloris's response is *one* base APK + opt-in signed addon APKs; clearer architecturally than three flavours.
- **What this project should avoid:** GPL-3 code cannot enter `:app`. Architecture-borrow only.

### Gboard (Google) — Gboard Smart Compose, Smart Edit, Rambler streaming voice cleanup, image-paste behavior in apps that declare `EditorInfo.contentMimeTypes`
- The Rambler streaming-voice cleanup (Next-2.5 in ROADMAP) gates on L1.1a (LiteRT-LM addon).
- **What this project should learn:** Gboard's Smart Compose gray-text/swipe-space-to-accept is the design reference for L1.4.
- **What this project should avoid:** any cloud-bound "search inside keyboard" surface; explicitly REJECTED per ROADMAP §10.

### Samsung Keyboard (Galaxy AI Writing Assist, One UI 7+)
- Galaxy AI Writing Assist decouples from the Samsung Keyboard in One UI 7+ — works on any keyboard via Samsung's selected-text UI.
- **What this project should learn:** SwiftFloris can sit underneath as the no-network keyboard; this is already covered by the v1.8.70 README callout.

### Grammarly Keyboard for Android — discontinued; replaced by "Grammarly for Android" (any-keyboard integration)
- Smaller pool than SwiftKey but same privacy fatigue.
- **What this project should learn:** the v1.8.70 callout is the right shape.

### Apple Keyboard (iOS 18 Writing Tools) — cross-platform inspiration only
- The Writing Tools "Proofread / Rewrite / Summarize" surface is a useful UX reference for L1.x once Gemma 3 270M lands.
- **What this project should avoid:** any cloud Bing/Copilot/Gemini integration in core.

### Unexpected Keyboard / Thumb-Key / Simple Keyboard / Fossify Keyboard
- All FOSS, niche, no autocorrect on Unexpected; Thumb-Key 3x3 grid is mobile-foldable-only.
- **What this project should learn:** Thumb-Key's audience is real but small; do not dilute SwiftFloris by chasing this.

### Sayboard — Vosk-only voice IME; separate IME app
- ROADMAP §13 explicitly REJECTS shipping voice as a separate IME — voice belongs inside SwiftFloris.

### F-Droid Reproducibility Status pages — verified-tier badge live
- SwiftFloris targets the `Reproducible` tier (developer-signed + verified) once `fdroiddata` PR lands.
- **What this project should learn:** the F-Droid metadata problem (see Feature Inventory) blocks this submission.

### Android 16 (API 36) + Android 17 (API 37) dev preview
- API 37 gates wired per v1.8.45 (N13.2 IME-visibility restore) and v1.8.44 (N13.3 long-press popup guard on password fields). Re-audited 2026-05-17 — N13.2 and N13.3 confirmed shipped (per `docs/archive/`).
- **What this project should learn:** the `compileSdk / targetSdk = 36` choice is intentional until the Android 17 behavior-gate checklist is closed (per PROJECT_CONTEXT v1.8.74 note). No bump pressure yet.

### On-device ML availability snapshot (Verified via prior research)
- **LiteRT-LM** is the named successor to MediaPipe LLM Inference on Android (MediaPipe deprecated 2026-03-31). Gemma 3 1B int4 is the L1.1a model target.
- **Gemma 3 270M** is the smallest viable ghost-text model (~135 MB on disk).
- **FunctionGemma 270M** (shipped Jan 2026) is the function-calling variant — natural target for the MCP bridge's agentic tool-use path.
- **Bergamot offline NMT** — `browsermt/bergamot-translator` active fork; Apache-2.0; the Android port reference is `DavidVentura/offline-translator`.
- **librime** — BSD-3-Clause; `fcitx5-android` is the Android wrapper reference; L3 addon target.
- **ML Kit Digital Ink** — needs INTERNET at runtime via `RemoteModelManager.download(...)`; cannot link into `:app`; ships in `addons/handwriting-mlkit/` (external).
- **Vosk small-en-us-zamia** + **whisper.cpp** + **whisper.tflite** — Apache/MIT; can ship inside `:app` if model is fully shipped without a network download.

---

## Highest-Value New Features

These are **new** ideas surfaced by this research run. Items already tracked in [ROADMAP.md](ROADMAP.md) §6/§7/§8 are NOT repeated here — those have their own implementation prompts in [docs/AI_PROMPTS_EXTERNAL_WORK.md](docs/AI_PROMPTS_EXTERNAL_WORK.md). Below is the **delta** beyond the existing planning.

### F1. F-Droid metadata rewrite + version-tracker CI gate
- **User problem solved:** F-Droid listing currently advertises FlorisBoard v0.3.16, not SwiftFloris v1.8.173. Any user reaching SwiftFloris through F-Droid sees stale upstream FlorisBoard branding ("currently in beta"). This is **the** brand-identity bug.
- **Evidence:** Verified — [fastlane/metadata/android/en-US/title.txt](fastlane/metadata/android/en-US/title.txt) = `FlorisBoard`; [short_description.txt](fastlane/metadata/android/en-US/short_description.txt) opens with `An open-source keyboard which respects your privacy. Currently in beta.`; [full_description.txt](fastlane/metadata/android/en-US/full_description.txt) opens with `<i>FlorisBoard</i> is an open-source keyboard…`; latest [changelogs/86.txt](fastlane/metadata/android/en-US/changelogs/86.txt) body = `Detailed changelog: https://github.com/florisboard/florisboard/releases/tag/v0.3.16`; `gradle.properties` `projectVersionCode=1973`.
- **Proposed behavior:**
  - Rewrite `title.txt` to `SwiftFloris`.
  - Rewrite `short_description.txt` to mirror the README §1 wedge claim (≤80 chars) — e.g. `Privacy-first Android keyboard. Apache-2.0. No INTERNET. No accounts.`
  - Rewrite `full_description.txt` to reflect the v1.8.173 reality (link `docs/PRIVACY_AND_AI.md`, `docs/THREAT_MODEL.md`, the Obtainium subscribe URL).
  - Add one `changelogs/<projectVersionCode>.txt` per shipped release, ≤500 chars (F-Droid hard cap), extracted from the matching `## vX.Y.Z` section of `CHANGELOG.md`.
  - Add a new `scripts/check-fastlane-metadata.sh` script + CI step that fails if a `gradle.properties:projectVersionCode` bump is committed without a matching `fastlane/metadata/android/en-US/changelogs/${projectVersionCode}.txt` file.
  - Add a new `scripts/extract-fastlane-changelog.sh` that pulls the matching `CHANGELOG.md` section + truncates to 500 chars + writes it to the right path.
- **Implementation areas:** `fastlane/metadata/android/en-US/*`, `scripts/`, `.github/workflows/android.yml`
- **Data model / API / UI implications:** none — pure metadata + CI surface
- **Risks and edge cases:** F-Droid 500-char cap; emoji-character handling; localization (en-US only first; route other locales through Crowdin)
- **Verification plan:**
  - `cat fastlane/metadata/android/en-US/title.txt` outputs `SwiftFloris`
  - `cat fastlane/metadata/android/en-US/changelogs/1973.txt` exists and is ≤500 chars
  - `bash scripts/check-fastlane-metadata.sh` exits 0 at HEAD; exits 1 with a fresh `projectVersionCode` bump that has no matching changelog
  - F-Droid submission preview via [`fdroidserver`](https://gitlab.com/fdroid/fdroidserver) `rewritemeta` dry-run shows correct title + descriptions
- **Estimated complexity:** S
- **Priority:** **P0**

### F2. Repo-root binary purge + recurring hygiene gate
- **User problem solved:** Every fresh clone of SwiftFloris currently downloads 10+ MB of root-level artifacts that are unused (`app-release-v1.5.2.apk` = 9.7 MB, `SwiftFloris_icon.png` = 787 KB, `ROADMAP.md.backup-v2` = 21 KB). Contributors and CI runners pay the bandwidth + disk cost on every fetch; F-Droid reviewers see a sloppy tree.
- **Evidence:** Verified via `ls -la` against repo root; [.gitignore](.gitignore) already declares `*.apk` so the APK was force-added before that rule.
- **Proposed behavior:**
  - `git rm --cached app-release-v1.5.2.apk SwiftFloris_icon.png ROADMAP.md.backup-v2`
  - If the icon is referenced (it isn't, per a grep), move to `assets/branding/icon.png` for any maintainer-side use
  - Move `ROADMAP.md.backup-v2` to `docs/archive/ROADMAP-v2-snapshot.md` if anything actively needs it, else delete
  - Extend [scripts/check-repo-hygiene.sh](scripts/check-repo-hygiene.sh) to reject:
    - Any tracked `*.apk` / `*.aab` at the repo root
    - Any tracked `*.backup*` files outside `docs/archive/`
    - Any single PNG > 200 KB at the repo root
  - Document the rule in [docs/REPO_HYGIENE.md](docs/REPO_HYGIENE.md)
  - **Do not** rewrite git history. The 9.7 MB APK has lived in history since the v1.5.x line; trimming it requires a force-push to main, which violates the safety rules in `CLAUDE.md`. Track as `Larger Bets` if the maintainer wants to do a one-time cleanup branch.
- **Implementation areas:** `scripts/check-repo-hygiene.sh`, `docs/REPO_HYGIENE.md`, repo root
- **Risks and edge cases:** history-rewrite must NOT happen; sigstore / GitHub release artifacts are different from tracked APKs.
- **Verification plan:** `bash scripts/check-repo-hygiene.sh` passes at HEAD post-cleanup; a planted test case (touch `repo-root.apk`) fails the script with a clear message.
- **Estimated complexity:** S
- **Priority:** **P0**

### F3. SwiftKey-window first-run discovery — auto-detect `swiftkey-cloud.json` in Downloads
- **User problem solved:** SwiftFloris ships the SwiftKey JSON importer (v1.8.46 / N16.2), but the discovery path is **buried**: a SwiftKey refugee must (a) install SwiftFloris, (b) launch Settings, (c) navigate to Settings → Personal dictionary → Import, (d) pick the SwiftKey JSON file. The migration window is 6 days from this run. Surface this as a first-run hint.
- **Evidence:** Verified — [docs/MIGRATE_FROM_SWIFTKEY.md](docs/MIGRATE_FROM_SWIFTKEY.md) ships the migration walkthrough; [DictionaryImporter.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryImporter.kt) parses SwiftKey JSON (v1.8.46); but the Setup flow ([SetupScreen.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt)) makes no mention of importing from another keyboard.
- **Proposed behavior:**
  - After the IME-enablement + default-IME setup steps, add an optional "Import from another keyboard" card that scans `~/Downloads/` (and the Documents/Recent SAF tree if permission already granted) for files matching `swiftkey-cloud*.json` / `*PersonalDictionary*.xml*.zip` / `*.flbackup` / `*.fldic`.
  - If detected, surface a calm "We found a SwiftKey export from 2026-05-29 — import it?" card. One tap routes to the existing importer with the file pre-selected.
  - If nothing detected, show the migration-walkthrough link only (no fishing for permissions).
  - **Permission posture:** do NOT request `READ_EXTERNAL_STORAGE` / `READ_MEDIA_DOCUMENTS` for the scan. Use SAF's `OPEN_DOCUMENT` with the user-driven file picker as the path-of-detection — show the picker pre-filtered to `application/json` + `application/zip` and let the user pick. Never auto-read filesystem.
  - The card has a clear "Skip — I'll import later" path.
- **Implementation areas:** `app/setup/SetupScreen.kt`, new `app/setup/ImportFromAnotherKeyboardStep.kt`, plumbing into `DictionaryImporter`, `strings.xml` for Crowdin
- **Risks and edge cases:** never scan filesystem without a user action; never auto-import (always require confirmation); honour the existing `prefs.suggestion.incognitoMode` setting if the user has it FORCE_ON; do not break Setup-flow accessibility focus order (Workstream 6 already pinned it).
- **Verification plan:**
  - Fresh install on a Pixel; place a synthetic `swiftkey-cloud.json` in Downloads → run setup → card appears → tap import → personal dict shows the imported rows
  - Same flow with no file present → card does not appear
  - Same flow with TalkBack on → focus order respected
  - Unit test for the file-shape detector
- **Estimated complexity:** M
- **Priority:** **P0** (locked to 2026-05-31)

### F4. Settings → Search
- **User problem solved:** Settings density is now high (15 top-level screens, 200+ user-facing prefs, 21 themes, MCP daemons, addons, sync). Users hunt for switches and adjacent FOSS keyboards (HeliBoard, FlorisBoard upstream, OpenBoard) all lack settings search; Gboard / SwiftKey ship it. This is a power-user usability win that fits the "SwiftKey-class without the cloud" wedge.
- **Evidence:** Verified — [app/settings/](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/) has 15 sub-packages each with multiple screens; no search exists; `app/AppPrefs.kt` is 1,301 lines.
- **Proposed behavior:**
  - Add a search bar to [HomeScreen.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt) (top of the scaffold, persistent above section list).
  - Build a static index at compile time from JetPref preference metadata + a small `SettingsSearchIndex` data class enumerating screen route + title + section + keywords.
  - Matches show as result list cards routing to the destination screen with the matching preference scrolled into view and visually highlighted for 1.5 seconds.
  - Keyboard-only navigation (TalkBack + hardware keyboard).
  - Offline only (no telemetry).
- **Implementation areas:** `app/settings/HomeScreen.kt`, new `app/settings/SettingsSearchIndex.kt`, `app/settings/SettingsSearchBar.kt`, plumbing through every settings screen for the scroll-and-highlight (one extra param to each subscreen)
- **Risks and edge cases:** index goes stale when a screen adds a pref → tie to JetPref reflection or a compile-time-generated index; localization through Crowdin
- **Verification plan:**
  - Typing `glide trail theme` from the home screen surfaces the v1.8.172 setting, tapping navigates to Gestures → Glide Typing with the right field highlighted
  - Roborazzi baseline for the search-bar present + empty + active + result-list states
  - TalkBack traversal test for the search bar
- **Estimated complexity:** L
- **Priority:** **P1**

### F5. Inline ghost-text completion using existing trigram/bigram chain (no LLM required)
- **User problem solved:** SwiftKey-style inline gray suggestion is the single feature SwiftKey users mention most often during the migration window (per existing competitor matrix). The ROADMAP L1.4 (Gemma 3 270M Q4) puts this behind an addon. But — the existing `PersonalTrigramStore` + `PersonalBigramStore` + `ColdStartNextWordPriors` + `LatinDictionarySnapshot` already produce a top-1 next-word candidate confident enough to render as ghost text for high-evidence cases (sentence-position priors + ≥3-character trigram match), without any LLM.
- **Evidence:** Verified — [PersonalTrigramStore.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/PersonalTrigramStore.kt), [PersonalBigramStore.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/PersonalBigramStore.kt), [ColdStartNextWordPriors.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/ColdStartNextWordPriors.kt), [LatinLanguageProvider.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt) `nextWordSuggestions(...)` already exists. The infrastructure exists; only the inline render surface + accept-on-space gesture is missing.
- **Proposed behavior:**
  - When the suggestion strip's top candidate is from the trigram-tier with confidence ≥ 0.80 (or bigram-tier confidence ≥ 0.55 with sentence-position prior), render it as a gray ghost-text after the cursor inside the editor — using `InputConnection.setComposingText("ghost", 1)` with `SpannableString` ForegroundColorSpan (grey 60% opacity). This is exactly the technique Gboard Smart Compose uses without any LLM.
  - On space: ghost text → commit. On any other key: ghost text → cleared.
  - Reduced-motion: still works (no animation; only color).
  - Disabled by default; Settings → Typing → "Inline ghost-text completion" switch (preference name `prefs.suggestion.inlineGhostText`).
  - Suppress in PASSWORD / INCOGNITO / number / phone fields.
- **Implementation areas:** `ime/editor/EditorInstance.kt`, `ime/nlp/NlpManager.kt`, new `ime/nlp/InlineGhostTextRenderer.kt`, `ime/smartbar/CandidatesRow.kt` (small change — when ghost text is active, don't promote the same candidate to position 0 in the strip), `AppPrefs.kt` (new switch), `app/settings/typing/TypingScreen.kt`
- **Risks and edge cases:** the `InputConnection.setComposingText` path must not collide with the existing composing-region for the typed word — gate on `editorInstance.activeCursorPosition` being at a word boundary; not all editors honour `ForegroundColorSpan` (gmail / browser inputs sometimes ignore it — fallback gracefully); on some IMEs the ghost text accidentally triggers a search submit on hardware enter — bind to space only.
- **Verification plan:**
  - Unit test the ghost-text confidence gate (`InlineGhostTextRenderer`)
  - Manual QA in Signal / Gmail / Chrome / SMS / Telegram
  - Roborazzi baseline of the candidate-row state when ghost text is active
  - Property test: the ghost text never produces output for PASSWORD / number / phone fields
- **Estimated complexity:** M
- **Priority:** **P1**

### F6. Per-app accent **opt-in onboarding tip** + accent preview in Settings
- **User problem solved:** Per-app accent (Next-11.3 / Next-11.3a) is shipped and gated `off` by default for the privacy-by-default stance. Users who would love it never discover it.
- **Evidence:** Verified — [PerAppAccentController.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/PerAppAccentController.kt), Settings → Theme exposes the switch ("Tint to active app's icon"), but there's no visual preview and no onboarding hook.
- **Proposed behavior:**
  - Add a side-by-side preview in Settings → Theme showing the keyboard with and without per-app accent active for a small set of known apps (Slack purple, WhatsApp green, Discord blurple, Telegram cyan). The preview is purely illustrative — actual accent is computed live from the editor's app icon at runtime.
  - One-time inline hint in the smartbar (small expanded chip, like the v1.8.127 Pin sheet entry) the first time a user uses SwiftFloris in three different apps, suggesting "Tint to active app's icon? — Settings → Theme."
  - Hint is single-fire; user can dismiss permanently with one tap.
- **Implementation areas:** `app/settings/theme/ThemeScreen.kt`, `ime/theme/PerAppAccentPreview.kt`, new `ime/smartbar/PerAppAccentDiscoveryHint.kt`
- **Risks and edge cases:** must NOT escalate any permission (the editor's app package name is already available via the IME contract); must respect reduced-motion; must single-fire (`prefs.theme.perAppAccentDiscoveryHintShown`).
- **Verification plan:** Roborazzi baseline; preference round-trip test; manual QA across three apps in sequence
- **Estimated complexity:** S
- **Priority:** **P2**

### F7. Settings → "Local-only audit log" surface for addon / MCP invocations
- **User problem solved:** SwiftFloris's wedge depends on user trust. Trust is reinforced by **showing** the user every cross-process call the IME made on their behalf. `AddonInvocationAudit` already exists (v1.8.24 / matrix #36/#38) and `b39fd97` shipped a local-only audit export bundle. The UI surface for browsing this is partial — Settings → Addons + MCP screens don't expose the audit log directly.
- **Evidence:** Verified — [`b39fd97`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonInvocationAudit.kt), [`e10c774`] (subject field). Settings/UI surface is partial.
- **Proposed behavior:**
  - Add a top-level Settings → Privacy → "Local audit log" screen
  - Shows last N (configurable 50/200/1000) addon + MCP daemon + Tasker + voice-handoff invocations with timestamp + subject + target package + result (success/failure)
  - Each row expandable to show the cross-process payload (capped + redacted for PASSWORD fields)
  - "Export local bundle" + "Clear log" actions
  - Optional Tasker / quick-share path to copy the log into a single share-sheet target
  - Settings → About → trust posture cross-link
- **Implementation areas:** `app/settings/privacy/` (new package), `ime/addon/AddonInvocationAudit.kt`, surface across `mcp/`, `tasker/`, `voice/`
- **Risks and edge cases:** the log itself is sensitive — must NOT auto-export; must NOT collect anything not already collected; do NOT add an opt-in toggle that defaults on (this is for the existing audit data, not new collection).
- **Verification plan:** unit test for the row model; Roborazzi baseline; manual QA scenario across each invocation type
- **Estimated complexity:** M
- **Priority:** **P1**

### F8. Bundled local Vosk small-en-us recogniser (retires "preview-only" framing)
- **User problem solved:** Voice settings currently say "preview only" because `VoiceLocalRecognizerRuntime.AVAILABLE = false`. Users see a Whisper/Vosk catalog they cannot actually use. The honesty is good; the user value is zero.
- **Evidence:** Verified — `.ai/research/2026-05-17/SEVENTH_PASS_FINDINGS.md` §2 finding #6 + the v1.8.120 product-honesty branch.
- **Proposed behavior:**
  - Bundle the Vosk small-en-us-zamia model (40 MB) under `addons/voice-vosk-en/` as a signed sibling APK (not in `:app`, per §1 size + license posture)
  - Wire `VoiceLocalRecognizerRuntime` against Vosk's JNI in the addon
  - Add the `RECORD_AUDIO` permission only inside the addon's manifest, never `:app`
  - Settings → Voice input now shows two real routes: External (FUTO) and Local (Vosk small-en-us via addon)
  - Document install path in `docs/VOICE_COMMANDS.md` + `README.md`
- **Implementation areas:** out-of-tree `addons/voice-vosk-en/` repo + linking via the existing `AddonRegistry` route
- **Risks and edge cases:** 40 MB addon download — surface it as an explicit user choice; the existing FUTO handoff still works without the addon installed; no `RECORD_AUDIO` creep in `:app`.
- **Verification plan:** addon-install → Voice route auto-detected as available → dictation produces text in a real app
- **Estimated complexity:** L (mostly the JNI work in a sibling repo)
- **Priority:** **P2**

### F9. Glide trail theme Roborazzi baseline + low-end perf evidence
- **User problem solved:** v1.8.172 shipped 7 glide trail themes + a major glide perf overhaul, but the visual baselines aren't pinned and the perf numbers are SM-S938B-only. A theme regression or a low-end device regression would slip CI.
- **Evidence:** Verified — `app/src/test/snapshots/` has no glide-trail baselines; `docs/benchmark-results/` only contains SM-S938B data.
- **Proposed behavior:**
  - For each of the 7 trail themes, render a synthetic 6-point gesture trace through `GlideTrailTheme.colorAt(i, t)` + the new `Path`-based stroke and capture the resulting bitmap as a Roborazzi baseline.
  - Add a Macrobenchmark trace `swiftfloris.glide.trailDrawMs` to the per-frame draw block in `TextKeyboardLayout` (where the trail composables render).
  - Document target frame budgets for low-end (≤4 GB RAM) devices in `docs/BENCHMARKS.md` and capture at least one Pixel 4a / Galaxy A12-class baseline (target: <2 ms median trail draw).
- **Implementation areas:** `app/src/test/kotlin/.../GlideTrailThemeRoborazziTest.kt`, `ime/text/keyboard/TextKeyboardLayout.kt`, `docs/BENCHMARKS.md`
- **Risks and edge cases:** trace synthesis must be deterministic; reduced-motion gate must be honoured
- **Verification plan:** `gradlew :app:verifyRoborazziDebug` passes after baseline capture; `tools/benchmark-ime-candidate-row.ps1`-style harness adapted for trail draw on a low-end device
- **Estimated complexity:** M
- **Priority:** **P1**

### F10. CycloneDX SBOM + SLSA provenance attestation on every release
- **User problem solved:** F-Droid reviewers + privacy-conscious users want a machine-readable bill of materials. Currently `dependency-scan.yml` runs OSV-Scanner on the dep tree (which is great) but no SBOM artifact attaches to GitHub releases, and no SLSA provenance is generated.
- **Evidence:** Verified — `.github/workflows/dependency-scan.yml` runs OSV-Scanner and uploads the Gradle dep tree as an artifact; `release.yml` produces signed APK + SHA256SUMS only; no SBOM/SLSA artifact in any release.
- **Proposed behavior:**
  - Add a `cyclonedx-gradle-plugin` step in `release.yml` (output: `app/build/reports/bom.xml` CycloneDX-1.6)
  - Upload `bom.xml` to the GitHub release alongside the APK + SHA256SUMS
  - Add a SLSA provenance step via `slsa-framework/slsa-github-generator` (level 3, container-based)
  - Document SBOM consumer flow in `docs/SECURITY.md`
- **Implementation areas:** `.github/workflows/release.yml`, `app/build.gradle.kts`, `docs/SECURITY.md`
- **Risks and edge cases:** SLSA generator pinning; SBOM secret leakage (none expected — Gradle deps are public); F-Droid build-server compatibility (the SLSA step would not affect the reproducible-build hash since it runs after assembleRelease)
- **Verification plan:** a manual `workflow_dispatch` of `release.yml` against a test tag produces a release with `bom.xml` + provenance.json attached; OSV-Scanner consumes the new SBOM via `osv-scanner --sbom=bom.xml`
- **Estimated complexity:** M
- **Priority:** **P2**

### F11. GPG-signed release tags
- **User problem solved:** Supply-chain verification. The release APK is keystore-signed (good), but git tags themselves aren't signed; any maintainer-VM compromise could re-tag a malicious build, and downstream Obtainium / F-Droid would only catch it via APK signature mismatch after the fact.
- **Evidence:** Verified — `release.yml` produces signed APKs; no `git tag -s` step.
- **Proposed behavior:**
  - Maintainer's GPG key in `docs/SECURITY.md` (or `KEYS` file at repo root)
  - `release.yml` signs the release tag with GPG
  - README / docs/SECURITY.md document `git verify-tag vX.Y.Z` as the supply-chain check
- **Implementation areas:** `release.yml`, `docs/SECURITY.md`, `KEYS` at repo root, maintainer's GPG flow
- **Risks and edge cases:** maintainer GPG key must be safely held (Yubikey ideal); workflow GPG passphrase via secrets only
- **Verification plan:** post-release: `git verify-tag v1.8.174` returns "Good signature from … <maintainer>"; SECURITY.md displays the key fingerprint
- **Estimated complexity:** S
- **Priority:** **P2**

### F12. F-Droid `fdroiddata` submission PR (build server + verified-tier badge)
- **User problem solved:** Even with reproducible-build CI green and SHA-256 published, the project is **not yet on F-Droid**. The `Reproducible` verified-tier badge is the highest privacy-credibility signal in FOSS Android.
- **Evidence:** Verified — `docs/REPRODUCIBLE_BUILDS.md` documents the F-Droid Builds: stanza but the actual PR has not been submitted (no maintainer note confirms otherwise).
- **Proposed behavior:**
  - Compose a complete `metadata/dev.patrickgold.florisboard.yml` (or whatever package id F-Droid eventually accepts — the current FlorisBoard upstream sits at `dev.patrickgold.florisboard.beta`, so this needs disambiguation in conversation with F-Droid maintainers)
  - Submit to https://gitlab.com/fdroid/fdroiddata
  - Track verified-tier rebuild result
  - Add an F-Droid badge to README + fastlane image
- **Implementation areas:** external — `fdroiddata` GitLab repo; in-repo only the docs.
- **Risks and edge cases:** the `dev.patrickgold.florisboard` package id needs renaming or coexistence with upstream FlorisBoard's F-Droid build; F-Droid's review queue is multi-month; reproducible-build verification requires byte-exact match against the F-Droid build server (worth confirming through one dry run before submission).
- **Verification plan:** PR open at `fdroiddata` GitLab; verified-tier badge appears at f-droid.org/en/packages/<id>/
- **Estimated complexity:** M (mostly external)
- **Priority:** **P2** (was P0 in v5.7 context but the F-Droid metadata bug blocks it — that bug closes first)

### F13. Cross-platform desktop "SwiftFloris dictionary export" companion via existing CRDT pairing payload
- **User problem solved:** A power user has SwiftKey on Windows, BetterTouchTool / Espanso on Mac, and wants to share their personal dictionary with SwiftFloris on Android. The CRDT pairing payload exists; a no-network desktop import-export companion would close the loop.
- **Evidence:** Verified — [`SyncChannel.Manual`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/SyncChannel.kt), `PairingPayload`, `PersonalDictionaryCrdt`. Format is public + documented.
- **Proposed behavior:** out-of-tree small CLI (~150 LOC in Python or Rust) that:
  - Reads `swiftkey-cloud.json` + Espanso `~/.config/espanso/match/*.yml` + macOS `Text Substitutions` plist
  - Emits a `PersonalDictionaryCrdt` blob the Android user can scan via QR or share-sheet-receive
- **Implementation areas:** **out-of-tree** — sibling repo `swiftfloris-desktop-bridge/`
- **Risks and edge cases:** keep it tiny; no telemetry; Apache-2.0 only
- **Verification plan:** end-to-end smoke: Mac → QR → Android → personal dict shows imported rows
- **Estimated complexity:** L (mostly out-of-tree)
- **Priority:** **P3**

### F14. Settings → "What's new in v1.8.173" excerpt + "Rate the privacy posture" CTA
- **User problem solved:** Users who updated from v1.8.x don't know what changed. The README badge updates; the Settings app is silent.
- **Evidence:** Verified — Settings → About → Version pref exists; no "what's new" surface.
- **Proposed behavior:**
  - Settings → About → "What's new" — first 800 chars of the matching `CHANGELOG.md` section, sourced at compile time
  - "Compare privacy posture vs SwiftKey / Gboard / Grammarly" inline card linking to a new `docs/PRIVACY_POSTURE_TABLE.md`
  - "Open GitHub repo" / "Submit a bug" actions (Tasker-style intent dispatch)
- **Implementation areas:** `app/settings/about/AboutScreen.kt`, new `app/build/generated/changelog.kt` from a Gradle task that consumes `CHANGELOG.md`
- **Risks and edge cases:** keep the excerpt short; respect Crowdin (changelog is en-US only)
- **Verification plan:** Roborazzi baseline; manual QA across two release boundaries
- **Estimated complexity:** S
- **Priority:** **P2**

---

## Existing Feature Improvements

Items below are not **new** features — they are improvements to features that already shipped, surfaced by this research run.

### EI1. AppPrefs.kt is 1,301 lines — partition by feature area
- **Current behavior:** All ~200 user-facing preferences live in one Kotlin file at [app/AppPrefs.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt). Diffs against this file are noisy; conflicts during multi-line preference additions are common.
- **Problem or missed opportunity:** Easy to refactor; reduces merge conflicts; matches the package-per-feature convention used elsewhere
- **Recommended change:** Split into one-file-per-feature using the JetPref pattern: `app/prefs/KeyboardPrefs.kt`, `app/prefs/CorrectionPrefs.kt`, `app/prefs/ClipboardPrefs.kt`, etc. Re-export the merged `AppPrefs` object.
- **Code locations likely affected:** `app/AppPrefs.kt`, every consumer (`grep -rn "prefs\." app/src/main/kotlin`)
- **Backward compatibility concerns:** preference datastore keys must NOT change; the public API surface stays exactly the same
- **Verification plan:** golden test that `AppPrefs.allPrefs()` returns the same set of keys in the same default values before and after
- **Estimated complexity:** M
- **Priority:** **P2**

### EI2. Settings home screen — group by "Typing / Personalization / Privacy / Advanced / About"
- **Current behavior:** [HomeScreen.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt) lists 15 sub-screens at one level of depth.
- **Problem or missed opportunity:** Discoverability for SwiftKey refugees who don't yet know SwiftFloris's surface area.
- **Recommended change:** Five top-level groups:
  - **Typing experience** — Typing, Gestures, Smartbar, Keyboard, Voice input
  - **Personalization** — Theme, Media (Emoji + Stickers), Localization
  - **Privacy & data** — Clipboard, Dictionary, Sync, Audit log (new — F7), Backup & restore
  - **Advanced** — Addons, MCP daemon bridge, Tasker integration, Hardware keyboard
  - **About** — Version, AI features, Threat model, "What's new" (new — F14)
- **Code locations likely affected:** `app/settings/HomeScreen.kt`, `strings.xml`
- **Backward compatibility concerns:** none — pure UI; deep links into specific screens still resolve
- **Verification plan:** Roborazzi baseline of home screen; TalkBack traversal still pinned by `FlorisScreenFocusOrderTest`
- **Estimated complexity:** S-M
- **Priority:** **P1**

### EI3. Personal dictionary — bulk import preview before commit
- **Current behavior:** Settings → Personal dictionary → Import → file picker → instant commit with a count summary.
- **Problem or missed opportunity:** A user importing a SwiftKey cloud-export might want to preview the entry list and exclude N rows before committing. Currently impossible.
- **Recommended change:** Add a "Preview" intermediate step showing the first ~50 entries + total count + exclude-row checkboxes. After preview, the user confirms or cancels.
- **Code locations likely affected:** `ime/dictionary/DictionaryImporter.kt`, `app/settings/dictionary/UserDictionaryImportScreen.kt`
- **Backward compatibility concerns:** none — preview is opt-out via a "Skip preview" link
- **Verification plan:** unit test for the preview-row generator; manual QA against a 5,000-entry Gboard XML export
- **Estimated complexity:** M
- **Priority:** **P2**

### EI4. Glide trail Rainbow + Aurora photosensitivity disclosure
- **Current behavior:** v1.8.172 ships Rainbow (full 360° hue sweep) + Aurora (180° hue sweep, slow cycle) + Neon (sinusoidal brightness modulation 0.015 rad/ms). Settings → Gestures shows a label + summary; no a11y disclosure.
- **Problem or missed opportunity:** Photosensitive epilepsy / migraine-prone users can be triggered by high-frequency hue/brightness changes. `docs/ACCESSIBILITY.md` doesn't mention this.
- **Recommended change:**
  - Add a paragraph to `docs/ACCESSIBILITY.md` describing the per-theme animation rate + recommended reduced-motion fallback
  - Settings → Gestures → "Trail theme" picker adds a small "ⓘ" tooltip beside Rainbow / Aurora / Neon noting the time-based animation and the reduced-motion gate
  - Verify Rainbow/Aurora/Neon actually honour `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (which the project respects per N8.4 / v1.7.x)
- **Code locations likely affected:** `docs/ACCESSIBILITY.md`, `app/settings/gestures/GesturesScreen.kt`, possibly `ime/text/keyboard/GlideTrailTheme.kt` for the reduced-motion gate
- **Backward compatibility concerns:** none
- **Verification plan:** check `GlideTrailTheme.colorAt(t)` falls back to a static frame at t=0 when reduced-motion is on
- **Estimated complexity:** S
- **Priority:** **P1**

### EI5. EmojiCompat singleton race — pin with regression test
- **Current behavior:** v1.8.173 patched the EmojiCompat `Not initialized yet` race by constructing the managed instance via reflection so `sInstance` stays null until metadata load completes; Compose sees `isConfigured() == false` until then.
- **Problem or missed opportunity:** The fix is fragile (reflection against a package-private constructor); a future AndroidX emoji2 update could break the trick silently.
- **Recommended change:** Add a unit/instrumentation test that exercises the bind-before-init window — assert that opening the emoji picker between `EmojiCompat.get()` install and metadata load does NOT throw. Pin the reflection target with a runtime check in `FlorisEmojiCompat` that fails loudly if the constructor changes shape after an emoji2 bump.
- **Code locations likely affected:** `app/src/main/kotlin/.../FlorisEmojiCompat.kt`, new `app/src/test/kotlin/.../FlorisEmojiCompatRaceTest.kt`
- **Backward compatibility concerns:** none
- **Verification plan:** test passes at HEAD; planted regression (e.g. flipping the reflection-or-direct switch) fails
- **Estimated complexity:** S
- **Priority:** **P1**

### EI6. ClipboardManager startup reconciliation — exercise it under a property test
- **Current behavior:** v1.8.116 / v1.8.117 / v1.8.118 / v1.8.119 closed clipboard agent findings #2 / #3 / #4 / #5 / #6 / #7 around reconciliation, restore metadata, failed clones, and history maintenance serialisation.
- **Problem or missed opportunity:** The current tests are scenario-based. A property test could catch corner cases (e.g. a row with `provider_uri` to a now-deleted file colliding with a row carrying a `ClipboardFileInfo` ID for a re-created file).
- **Recommended change:** Add `ClipboardReconciliationPropertyTest` using Kotest property checking against an `arbitrary<ClipboardHistorySnapshot>` generator; assert that post-reconciliation state has no orphan provider files and no DB rows pointing at non-existent files.
- **Code locations likely affected:** `app/src/test/kotlin/.../clipboard/ClipboardReconciliationPropertyTest.kt`
- **Backward compatibility concerns:** none
- **Verification plan:** new property test passes; planted regression fails
- **Estimated complexity:** M
- **Priority:** **P2**

### EI7. Voice route empty state — explain "what is FUTO" to SwiftKey refugees
- **Current behavior:** Settings → Voice input → external voice IME route shows "FUTO Voice Input not installed" when FUTO isn't present.
- **Problem or missed opportunity:** Most SwiftKey refugees don't know what FUTO is. The empty-state copy assumes prior FUTO knowledge.
- **Recommended change:** Empty state copy reads "Offline voice typing requires a separate app. SwiftFloris recommends FUTO Voice Input — fully offline, no account. [Install FUTO]" with the link routing to the FUTO Voice Input GitHub releases page or Obtainium one-tap URL.
- **Code locations likely affected:** `app/settings/voice/VoiceScreen.kt`, `strings.xml`
- **Backward compatibility concerns:** Crowdin localization needed
- **Verification plan:** manual QA on a fresh install with FUTO not present; Roborazzi baseline of the empty state
- **Estimated complexity:** S
- **Priority:** **P1** (locked to migration window)

### EI8. Honeycomb layout — promotion in README + screenshots
- **Current behavior:** v1.8.79 shipped honeycomb hex layout; the README doesn't mention it.
- **Problem or missed opportunity:** Typewise pivoted to enterprise AI in 2026 — the niche is genuinely vacated. SwiftFloris is the only OSS keyboard with a production-quality honeycomb. The README badge and fastlane images don't capture this.
- **Recommended change:**
  - README "Highlights" table — add a "Honeycomb hex layout (Typewise-vacated niche, FOSS-only)" row
  - Add a screenshot of honeycomb to `fastlane/metadata/android/en-US/images/phoneScreenshots/` (image 6)
  - Add a `docs/HONEYCOMB_LAYOUT.md` describing the design + accuracy claims + how to enable
  - Mention in the next CHANGELOG entry's "Recently shipped" reminder section
- **Code locations likely affected:** `README.md`, `fastlane/metadata/android/en-US/images/phoneScreenshots/`, new `docs/HONEYCOMB_LAYOUT.md`
- **Backward compatibility concerns:** none
- **Verification plan:** manual review; F-Droid metadata-lint passes
- **Estimated complexity:** S
- **Priority:** **P2**

### EI9. Macrobenchmark trace baselines — add candidate-row recomposition target floor and trend regression
- **Current behavior:** 6 baselines committed on SM-S938B / Android 16, but no CI step compares a new baseline against the prior committed baseline.
- **Problem or missed opportunity:** A future change could silently regress candidate-row recomposition from 0.33 ms median to 2 ms median and slip CI.
- **Recommended change:**
  - In `.github/workflows/android.yml` (or a new `benchmark-regression.yml`), add a manual `workflow_dispatch` step that runs `tools/benchmark-ime-*.ps1` (Linux equivalent), diffs against `docs/benchmark-results/baseline-*.json`, and posts the delta as a PR comment
  - Define floor / target ranges per baseline in `docs/BENCHMARKS.md`
- **Code locations likely affected:** new `.github/workflows/benchmark-regression.yml`, `docs/BENCHMARKS.md`
- **Backward compatibility concerns:** workflow is dispatch-only; not blocking
- **Verification plan:** dispatch the workflow on a planted regression PR → comment shows the delta clearly
- **Estimated complexity:** M
- **Priority:** **P2**

### EI10. Lint baseline — `app/lint-baseline.xml` audit if any baseline entries are stale
- **Current behavior:** v1.8.165 fixed the lint DSL wiring (`lintConfig` for `app/lint.xml`, not baseline); added `scripts/run-lint-debug-with-baseline-check.sh` so stale baseline entries fail CI.
- **Problem or missed opportunity:** It's not clear from this pass whether the project actually carries a baseline file with entries. If so, every entry should be triaged.
- **Recommended change:** Inventory `app/lint-baseline.xml` (if exists) and either fix each entry or document why it's there.
- **Code locations likely affected:** `app/lint-baseline.xml` if present, `IMPROVEMENT_PLAN.md` Workstream 2
- **Backward compatibility concerns:** none
- **Verification plan:** `bash scripts/run-lint-debug-with-baseline-check.sh` exits 0 at HEAD
- **Estimated complexity:** S (or M if many entries exist — Needs validation)
- **Priority:** **P2**

### EI11. The `:lib:native` placeholder module — either ship a real native runtime or remove the placeholder
- **Current behavior:** `settings.gradle.kts` includes `:lib:native` but `PROJECT_CONTEXT.md` §4 says "commented out"; `ARCHITECTURE.md` §"Module Layout" says "remains present on disk but inactive."
- **Problem or missed opportunity:** A dormant placeholder module that ships in `settings.gradle.kts` for a future native-runtime use case is a yak-shave any future contributor will trip over. The first real native runtime (L1.1a LiteRT-LM, N1.2 ONNX, librime) will live in a sibling addon repo, not in `:lib:native`, per the addon-isolation policy.
- **Recommended change:** Remove the `:lib:native` module declaration from `settings.gradle.kts` and the `lib/native/` directory. Update `ARCHITECTURE.md` to reflect the addon-only path for native runtime.
- **Code locations likely affected:** `settings.gradle.kts`, `lib/native/`, `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`
- **Backward compatibility concerns:** none — placeholder was unused
- **Verification plan:** `:app:assembleDebug` still green; no consumer of `:lib:native` exists (verified via `grep -rn "lib:native"`)
- **Estimated complexity:** S
- **Priority:** **P3**

---

## Reliability, Security, Privacy, and Data Safety

### Bugs or risks found in this pass
None new beyond what's already tracked in `.ai/research/2026-05-17/SEVENTH_PASS_FINDINGS.md` or `IMPROVEMENT_PLAN.md` Workstreams 11–15. The seventh-pass audit was thorough; no fresh leaks surfaced this pass.

### Missing guardrails
1. **Repo-root binary scan in `scripts/check-repo-hygiene.sh`** (F2 above).
2. **Fastlane changelog-per-release CI gate** (F1 above).
3. **GPG-signed release tags** (F11 above).
4. **CycloneDX SBOM at release artifacts** (F10 above).

### Permission / network / file-system concerns
- No `INTERNET` — Verified gate-enforced.
- `READ_CALENDAR` only requested when user explicitly taps the Calendar quick action — Verified per v1.8.64 design.
- `RECORD_AUDIO` not declared — Verified consistent with voice handoff posture; F8 above would add it only inside the addon APK.
- Sticker imported folder uses SAF tree URI — Verified persistent + read-only.
- Backup exclusion via `backup_rules.xml` + `data_extraction_rules.xml` — Verified gate-enforced via `verifyDataExtractionRules` (v1.8.95).

### Recovery and rollback needs
- Reset-all-trust action for addon signing pins — Verified v1.8.124.
- Backup/restore recovery copy on every failure card — Verified Workstream 5 closed.
- Addon dictionary asset mounting is generation-invalidated on `AddonRegistryStore.generation()` change — Verified v1.8.125.
- One thing **not** present: an "Erase all on-device learning" Settings action with a confirmation flow that wipes `PersonalBigramStore`, `PersonalTrigramStore`, `AdaptiveTouchModel`, `CorrectionOutcomePriors`, and the personal dictionary in one motion. Currently each is a separate action. Add it under Settings → Privacy → Reset (recovery: nothing — by design) as **EI12** (priority P2, complexity S).

### Logging / diagnostics needs
- `docs/BENCHMARKS.md` reports trace section names; production code paths emit them via `androidx.tracing.Trace.beginSection`. No PII leakage.
- No third-party crash reporter. ACRA opt-in still tracked in `ROADMAP.md` §9 Under Consideration.
- The F7 audit-log surface above would close the user-visible diagnostic loop.

---

## UX, Accessibility, and Trust

### Onboarding gaps
- F3 (SwiftKey backup auto-detect) above is the headline.
- The setup-flow AI/ML explainer (v1.8.66) is well-built but is only 1 screen; consider a 2-screen split where screen 1 = "What SwiftFloris is" and screen 2 = "What it doesn't do (no INTERNET, no account, no telemetry)" — a more confident negative-space framing aimed at the migration-window audience.

### Empty / loading / error / disabled states
- Workstream 10 closed most of these in v1.8.169 / v1.8.170. Voice empty state is the most user-visible gap (EI7 above).

### Destructive or irreversible actions
- v1.8.167 added explicit confirmations for theme/extension draft-mutation actions.
- v1.8.124 / v1.8.149 added confirmation for addon trust resets + dictionary entry mutations.
- "Erase all on-device learning" (EI12 above) is the open hole.

### Settings clarity
- 200+ prefs at 15 sub-screens — see F4 (search) and EI2 (regrouping) above.

### Accessibility issues
- Workstream 6 (Accessibility Pass) is fully closed per v1.8.158.
- EI4 above flags the glide trail photosensitivity disclosure as the open item.

### Microcopy and trust-signal improvements
- EI7 (voice route empty state) above.
- F14 (Settings → What's new + posture-comparison table) above.
- Settings → About → AI features (v1.8.66) is already strong.

---

## Architecture and Maintainability

### Module / boundary improvements
- EI1 above — `AppPrefs.kt` partition by feature area.
- EI11 above — drop `:lib:native` placeholder.
- The addon facade pattern (smart-compose / translate / handwriting / passkey / wordstyles) is the right shape and should be preserved.

### Refactor candidates
- [TextKeyboardLayout.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt) — the upstream-inherited FIXME `// FIXME (when rewriting TextKeyboardLayout): constraints.maxWidth is not stable!` (line 287) suggests a known eventual rewrite. Not urgent in this pass.
- [HanShapeBasedLanguageProvider.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/han/HanShapeBasedLanguageProvider.kt) — two FIXMEs about language-pack type checking (lines 103 + 107). Han support is upstream-inherited and currently shape-based only. L3 librime addon will obsolete this path; no action required.
- [LanguagePackExtension.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/LanguagePackExtension.kt) — FIXMEs about multi-type pack support (lines 52, 77). Same disposition.

### Test gaps
- Glide trail theme Roborazzi (F9 above).
- Inline ghost-text completion test path once F5 lands.
- Property test for clipboard reconciliation (EI6 above).
- EmojiCompat race regression test (EI5 above).

### Documentation gaps
- `docs/HONEYCOMB_LAYOUT.md` (EI8 above).
- `docs/MCP_DAEMON_BRIDGE.md` (new — what is it, sample daemon, security model).
- `docs/SYNC_THREAT_MODEL.md` (new — when Next-5 sync lands, document the threat model explicitly).
- `docs/PRIVACY_POSTURE_TABLE.md` (new — side-by-side vs SwiftKey/Gboard/Grammarly).

### Release / build / deployment gaps
- F1 (fastlane metadata) above.
- F2 (repo-root hygiene script extension) above.
- F10 (CycloneDX SBOM) above.
- F11 (GPG-signed release tags) above.
- F12 (`fdroiddata` submission) above.

---

## Prioritized Roadmap

The roadmap below is **additive** to [`ROADMAP.md`](ROADMAP.md) and [`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md). Items already tracked there are NOT repeated. This is the **delta** that this research pass surfaces. Cross-references use the `F#` / `EI#` IDs above.

### Phase 1 — Migration window (now → 2026-05-31)

- [x] **P0** — F1 — Fastlane / F-Droid metadata rewrite + version-tracker CI gate — **shipped v1.8.175 (2026-05-25)**
  - Why: F-Droid listing was reading `FlorisBoard` `Currently in beta` with FlorisBoard v0.3.16-era changelogs through v1.8.173
  - Evidence: pre-v1.8.175 `fastlane/metadata/android/en-US/title.txt` = `FlorisBoard`; `short_description.txt` = "An open-source keyboard which respects your privacy. Currently in beta."; latest changelog file was `86.txt` pointing at FlorisBoard v0.3.16
  - Touches: `fastlane/metadata/android/en-US/title.txt|short_description.txt|full_description.txt`, `fastlane/metadata/android/en-US/changelogs/{1974,1975}.txt` (new), `scripts/check-fastlane-metadata.sh` (new), `.github/workflows/android.yml`
  - Acceptance: title=`SwiftFloris`; both v1.8.174 and v1.8.175 changelogs present (≤500 chars); CI gate rejects future versionCode bumps without a matching changelog, and rejects re-introduction of `FlorisBoard` in the title
  - Verify: `bash scripts/check-fastlane-metadata.sh` → `OK (versionCode 1975, title=11 chars, short=69 chars, changelog=496 chars)`; negative tests pass

- [x] **P0** — F2 — Repo-root binary purge + hygiene gate extension — **shipped v1.8.174 (2026-05-25)**
  - Why: 10+ MB of root-level stale artifacts (`app-release-v1.5.2.apk`, `SwiftFloris_icon.png`, `ROADMAP.md.backup-v2`)
  - Evidence: `ls -la` repo root + `git ls-files` cross-check (only `SwiftFloris_icon.png` and `ROADMAP.md.backup-v2` were actually tracked; `app-release-v1.5.2.apk` and `local.properties` were already gitignore-covered working-tree-only)
  - Touches: `scripts/check-repo-hygiene.sh`, `docs/REPO_HYGIENE.md`, repo root (untrack via `git rm --cached`)
  - Acceptance: tracked binaries gone (no history rewrite); hygiene script catches future regressions
  - Verify: `bash scripts/check-repo-hygiene.sh` → `OK`; planted `test-stub.apk` rejected with exit 1

- [ ] **P0** — F3 — SwiftKey backup auto-detect first-run hint (`swiftkey-cloud.json` in Downloads/SAF)
  - Why: Importer exists (v1.8.46) but discovery is buried; migration window closes 2026-05-31
  - Evidence: `app/setup/SetupScreen.kt`, `ime/dictionary/DictionaryImporter.kt`
  - Touches: `app/setup/*`, `DictionaryImporter.kt`, `strings.xml`
  - Acceptance: new "Import from another keyboard" optional step in Setup; doesn't request `READ_EXTERNAL_STORAGE`; user-driven SAF picker
  - Verify: manual QA on synthetic SwiftKey JSON; TalkBack traversal; refused without permission expansion

- [ ] **P0** — EI7 — Voice route empty state explains "what is FUTO" to SwiftKey refugees
  - Why: SwiftKey refugees don't know FUTO; the empty-state copy assumes prior knowledge
  - Evidence: `app/settings/voice/VoiceScreen.kt`
  - Touches: `app/settings/voice/*`, `strings.xml`
  - Acceptance: empty state names FUTO + links to install path
  - Verify: manual QA without FUTO installed

- [ ] **P0** — IMPROVEMENT_PLAN Workstream 11 first slice — candidate row state audit
  - Why: Workstream 11 (Keyboard Surface Polish) is Planned/P0 in IMPROVEMENT_PLAN; the candidate-row audit is the highest-leverage entry
  - Evidence: `IMPROVEMENT_PLAN.md` §11; `app/src/main/kotlin/.../ime/smartbar/CandidatesRow.kt`
  - Touches: `ime/smartbar/CandidatesRow.kt`, `app/src/test/kotlin/.../CandidatesRowStateTest.kt`
  - Acceptance: candidate row's selection / pressed / disabled / correction states are deterministic and tested
  - Verify: new JVM test passes; Roborazzi baseline for each state

### Phase 2 — Migration-window follow-on (2026-06-01 → 2026-06-30)

- [ ] **P1** — F4 — Settings → Search
  - Why: 200+ prefs / 15 screens; discoverability win for the migration audience that lands in week 1
  - Evidence: `app/settings/HomeScreen.kt`, `app/AppPrefs.kt`
  - Touches: `app/settings/SettingsSearchIndex.kt` (new), `HomeScreen.kt`, every settings sub-screen
  - Acceptance: typing a known pref name in the home-screen search bar surfaces a result; tap navigates with highlight
  - Verify: JVM test for the index builder; Roborazzi for the search-bar states

- [ ] **P1** — F5 — Inline ghost-text completion using existing trigram/bigram chain
  - Why: Single most-requested SwiftKey feature; doesn't need an LLM; infrastructure exists
  - Evidence: `ime/nlp/latin/LatinLanguageProvider.kt`, `ime/dictionary/PersonalTrigramStore.kt`
  - Touches: `ime/editor/EditorInstance.kt`, `ime/nlp/InlineGhostTextRenderer.kt` (new), `app/AppPrefs.kt`, `app/settings/typing/TypingScreen.kt`
  - Acceptance: when the strip's top candidate exceeds the confidence gate, ghost text renders inline; commits on space; cleared on any other key; suppressed in PASSWORD/INCOGNITO/numeric/phone
  - Verify: unit test for the confidence gate; manual QA in Signal/Gmail/Chrome; property test for sensitive-field suppression

- [ ] **P1** — F7 — Settings → Privacy → Local audit log
  - Why: User-trust reinforcement; `AddonInvocationAudit` exists but UI is partial
  - Evidence: `ime/addon/AddonInvocationAudit.kt`, the v1.8.24 / matrix #38 ship notes
  - Touches: `app/settings/privacy/` (new), `ime/addon/AddonInvocationAudit.kt`, `ime/mcp/`, `ime/tasker/`
  - Acceptance: every addon/MCP/Tasker/voice invocation appears in the log with timestamp + subject + target package + result; export + clear actions
  - Verify: manual QA scenario across each invocation type; Roborazzi baseline

- [ ] **P1** — F9 — Glide trail theme Roborazzi baseline + low-end perf evidence
  - Why: v1.8.172 shipped 7 themes + perf overhaul; not visually pinned and not perf-validated on low-end hardware
  - Evidence: `app/src/test/snapshots/` has no glide trail baselines; `docs/benchmark-results/` is SM-S938B only
  - Touches: `app/src/test/kotlin/.../GlideTrailThemeRoborazziTest.kt` (new), `ime/text/keyboard/TextKeyboardLayout.kt`, `docs/BENCHMARKS.md`
  - Acceptance: 7-theme Roborazzi baselines committed; trail draw trace section added; low-end (Pixel 4a or Galaxy A12) baseline in `docs/benchmark-results/`
  - Verify: `:app:verifyRoborazziDebug` green; new benchmark output committed

- [ ] **P1** — EI2 — Settings home screen — group by "Typing / Personalization / Privacy / Advanced / About"
  - Why: 15 flat sub-screens; SwiftKey refugees need IA grouping
  - Evidence: `app/settings/HomeScreen.kt`
  - Touches: `HomeScreen.kt`, `strings.xml`, deep-link route preservation
  - Acceptance: home screen shows five groups; deep links still resolve; TalkBack focus order pinned
  - Verify: Roborazzi baseline; `FlorisScreenFocusOrderTest`

- [x] **P1** — EI4 — Glide trail photosensitivity disclosure — **shipped v1.8.182 (2026-05-25)**
  - Why: Rainbow / Aurora / Neon involve time-based hue/brightness changes; trigger risk for some users
  - Evidence: `ime/text/keyboard/GlideTrailTheme.kt`; reduced-motion gate already wired at `TextKeyboardLayout.kt:177-178`
  - Touches: `docs/ACCESSIBILITY.md` — new "Glide trail themes and photosensitivity" section with per-theme animation-rate table, WCAG 2.3.2 framing, citation of the existing `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` kill-switch
  - Acceptance: docs-side disclosure shipped; existing reduced-motion gate already disables the trail entirely at scale=0
  - Settings-side ⓘ tooltip deferred (would require new Compose UI + Crowdin'd strings)

- [ ] **P1** — EI5 — EmojiCompat singleton race regression test
  - Why: v1.8.173 fix uses reflection against a package-private constructor; a future AndroidX bump could break it silently
  - Evidence: `app/src/main/kotlin/.../FlorisEmojiCompat.kt`, v1.8.173 release notes
  - Touches: `app/src/test/kotlin/.../FlorisEmojiCompatRaceTest.kt` (new), `FlorisEmojiCompat.kt`
  - Acceptance: planted regression fails; HEAD passes
  - Verify: new test passes

### Phase 3 — Sustainable engineering (2026-07-01 → 2026-09-30)

- [ ] **P1** — IMPROVEMENT_PLAN Workstream 11 remaining items — smartbar overflow, software-key states, layout variants, autocorrect-toggle placeholder feedback, manual override verification, QA scripts
  - Touches: `ime/smartbar/`, `ime/text/keyboard/`, `app/src/test/kotlin/`
  - Acceptance: every Workstream 11 task checkbox ticked
  - Verify: `IMPROVEMENT_PLAN.md` Workstream 11 closed; per-task Roborazzi baselines

- [ ] **P1** — IMPROVEMENT_PLAN Workstream 13 — Privacy / Safety / Data Integrity
  - Touches: `ime/dictionary/DictionaryImporter.kt`, `ime/clipboard/ClipboardManager.kt`, extension import path
  - Acceptance: Workstream 13 closed; new tests around path safety and import validation

- [ ] **P1** — IMPROVEMENT_PLAN Workstream 15 — Manual QA + Release Evidence checklist
  - Touches: `docs/MANUAL_QA_CHECKLIST.md` (new or refresh), `docs/LOCAL_VERIFICATION.md`
  - Acceptance: a future agent can repeat the QA pass from documented commands

- [ ] **P2** — F6 — Per-app accent opt-in discovery hint + preview
  - Touches: `ime/theme/`, `ime/smartbar/PerAppAccentDiscoveryHint.kt` (new), `app/settings/theme/ThemeScreen.kt`

- [ ] **P2** — F8 — Bundled local Vosk small-en-us recogniser (out-of-tree addon)
  - Touches: out-of-tree `addons/voice-vosk-en/`; in-tree `ime/voice/` lights up
  - Acceptance: Settings → Voice input shows Local route as available when the addon is installed

- [ ] **P2** — F10 — CycloneDX SBOM + SLSA provenance attestation
  - Touches: `.github/workflows/release.yml`, `app/build.gradle.kts`, `docs/SECURITY.md`

- [ ] **P2** — F11 — GPG-signed release tags
  - Touches: `.github/workflows/release.yml`, `docs/SECURITY.md`, `KEYS` (new)

- [ ] **P2** — F12 — F-Droid `fdroiddata` submission PR (verified-tier badge)
  - Touches: external `fdroiddata` GitLab repo
  - Acceptance: SwiftFloris listing live with `Reproducible` badge

- [ ] **P2** — F14 — Settings → About → "What's new" excerpt + privacy posture table
  - Touches: `app/settings/about/AboutScreen.kt`, `docs/PRIVACY_POSTURE_TABLE.md` (new), Gradle generate-from-CHANGELOG task

- [ ] **P2** — EI1 — Partition `AppPrefs.kt` by feature area
  - Touches: `app/AppPrefs.kt` → `app/prefs/*.kt`
  - Acceptance: same key set, same default values, all tests green

- [ ] **P2** — EI3 — Personal dictionary bulk import preview
  - Touches: `ime/dictionary/DictionaryImporter.kt`, `app/settings/dictionary/`

- [ ] **P2** — EI6 — Clipboard reconciliation property test
  - Touches: `app/src/test/kotlin/.../clipboard/ClipboardReconciliationPropertyTest.kt`

- [x] **P2** — EI8 — Honeycomb layout promotion (README + docs) — **shipped v1.8.181 (2026-05-25)**
  - Touches: `docs/HONEYCOMB_LAYOUT.md` (new), `README.md` Highlights table links to it
  - Deferred to a future slice: fastlane phoneScreenshots refresh (needs real device + capture flow; existing 5 PNGs are FlorisBoard-era and need a fresh-capture batch covering v1.8.181 themes incl. honeycomb)

- [ ] **P2** — EI9 — Macrobenchmark trend regression CI job
  - Touches: `.github/workflows/benchmark-regression.yml` (new), `docs/BENCHMARKS.md`

- [ ] **P2** — EI10 — Lint baseline audit (if any entries remain)
  - Touches: `app/lint-baseline.xml` if present

- [ ] **P2** — EI12 — Settings → Privacy → "Erase all on-device learning" combined action
  - Why: scattered reset actions exist (personal dict, bigram, trigram, adaptive touch, correction outcome); a single confirmed action is the privacy-trust completion
  - Touches: `app/settings/privacy/`, `ime/dictionary/`, `ime/nlp/`
  - Acceptance: one confirmed action clears all learned data; verified by post-action assertion of zero rows in each store

- [ ] **P3** — F13 — Cross-platform desktop "SwiftFloris dictionary export" CLI
  - Touches: out-of-tree `swiftfloris-desktop-bridge/`

- [x] **P3** — EI11 — Drop the `:lib:native` placeholder module — **shipped v1.8.185 (2026-05-25)**
  - Removed `lib/native/` (8 tracked files), `libnative/dummy/` (3 tracked files), the `//include(":lib:native")` line in `settings.gradle.kts`, and the dead `flogError { "native module disabled, skipping dummy test" }` cold-start log in `FlorisApplication.kt`. Updated ARCHITECTURE.md / PROJECT_CONTEXT.md / README.md / ROADMAP.md / THREAT_MODEL.md / REPRODUCIBLE_BUILDS.md / android.yml to reflect the addon-only path for native runtimes.

---

## Quick Wins

- **F1** Fastlane metadata rewrite (S, P0) — single-PR slice
- **F2** Repo-root binary purge (S, P0) — single-PR slice
- **EI7** Voice route empty state copy (S, P0) — single-PR slice
- **EI4** Glide trail photosensitivity disclosure (S, P1) — single-PR slice
- **EI5** EmojiCompat race regression test (S, P1) — single-PR slice
- **F14** Settings → About → "What's new" excerpt (S, P2) — single-PR slice
- **EI8** Honeycomb layout README/fastlane promotion (S, P2) — single-PR slice
- **EI11** Drop `:lib:native` placeholder (S, P3) — single-PR slice

## Larger Bets

- **F3** SwiftKey backup auto-detect first-run hint (M, P0) — multi-step Setup-flow integration + Crowdin + accessibility
- **F4** Settings → Search (L, P1) — index builder + every screen takes a scroll-and-highlight param
- **F5** Inline ghost-text completion (M, P1) — touches the editor, NLP, smartbar, prefs, settings
- **F7** Settings → Privacy → Local audit log (M, P1) — surfaces across addons/MCP/Tasker/voice
- **F9** Glide trail Roborazzi + low-end perf (M, P1) — needs a low-end reference device
- **F8** Bundled local Vosk small-en-us recogniser (L, P2) — out-of-tree JNI + signed addon
- **F10/F11/F12** F-Droid verified-tier path (SBOM + GPG-tags + submission) (M each, P2) — requires F1 first
- **IMPROVEMENT_PLAN Workstream 11/13/15** closure (L total, mixed P0/P1) — staged across Phase 3
- **EI1** AppPrefs partition (M, P2) — touches every consumer

## Explicit Non-Goals

- **Cloud-bound anything** — explicitly REJECTED per [ROADMAP.md](ROADMAP.md) §10. Restated.
- **Google Play distribution** — REJECTED per §10.
- **In-app self-update** — REJECTED per §10.
- **GPL/AGPL/LGPL/FUTO Source-First code in `:app`** — REJECTED per §10.
- **History-rewrite to trim the 9.7 MB v1.5.2 APK** — out (force-push to main violates safety rules in CLAUDE.md). F2 addresses the working-tree problem; the history footprint stays.
- **A separate Voice IME / Clipboard manager / spellcheck-only mode** — REJECTED per [ROADMAP.md](ROADMAP.md) §13.
- **Federated learning to vendor cloud** — REJECTED per §10.
- **Mandatory analytics opt-out toggle that defaults on** — REJECTED per §10.
- **Self-published F-Droid build server / Aurora Store integration** — out for now; Obtainium one-tap URL is the agreed-upon distribution path.
- **MediaPipe LLM Inference API on Android** — REJECTED (deprecated by Google) per `AGENTS.md`.

## Open Questions

These genuinely block prioritization or implementation and are NOT answerable by reading the repo or public sources:

1. **F-Droid package id collision** — FlorisBoard upstream uses `dev.patrickgold.florisboard.beta` on F-Droid. SwiftFloris currently shares the `dev.patrickgold.florisboard` namespace per [gradle.properties](gradle.properties) → [AndroidManifest.xml](app/src/main/AndroidManifest.xml) `package=` declaration. The F-Droid submission needs to coexist with upstream FlorisBoard or rename. Which does the maintainer want? (This blocks F12.)
2. **Vosk small-en-us-zamia bundling preference** — 40 MB addon download vs leaving voice as FUTO-handoff-only forever. Does the maintainer want F8 to ship in 2026, or is voice a 2027 problem? (This affects EI7 copy + the Voice route Settings shape.)
3. **GPG key for signed release tags** — does the maintainer hold a Yubikey-backed GPG key, or is the signing flow first-time setup? (Affects F11 timeline.)
4. **Push state from the dev VM** — `CLAUDE.md` says `git push` to `SysAdminDoc/SwiftFloris` returns 403 from this VM. Phase 1 items in this plan should be batched into a single push from the maintainer host; will the maintainer push them before or after 2026-05-31? (Affects whether F1/F2/F3/EI7 actually land before the SwiftKey window closes.)
5. **`fdroiddata` submission timing** — does the maintainer want to submit during the migration window (high-traffic but lower-quality reviews) or wait for the verified-tier badge process to complete on a quieter week? (Affects F12 phase placement.)

---

## Addendum — Background-Task Findings (2026-05-25)

Three parallel research agents ran during this synthesis pass — **Code/Subsystem reconnaissance**, **Build/CI/Release reconnaissance**, and **External landscape**. The findings below are net-new evidence the main pass did not already capture. Each item is graded with the same `F#` / `EI#` convention and slotted into the prioritized roadmap.

### New evidence from Code/Subsystem reconnaissance

1. **`KeyboardManager.kt` (1,221 LOC) has zero direct unit-test coverage.** Verified — pure policies extracted out (`ApostropheReturnGate`, `KeyboardAutoCommitFlushPolicy`, `QuoteAutoCloseGate`) are tested, but the central dispatch state machine inside `KeyboardManager` itself is not. This is the single highest-yield test gap.
2. **`TOGGLE_AUTOCORRECT` quick-action ships a placeholder long toast**: [KeyboardManager.kt:782](app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt#L782) — `"Autocorrect toggle is a placeholder and not yet implemented"`. The QuickAction is selectable from the smartbar reorder grid. IMPROVEMENT_PLAN Workstream 11 names this; concrete delivery is either wiring `prefs.correction.autoCorrect` or removing the action.
3. **`strings.xml:978` `pref__glide__engine__neural_coming_soon`** advertises a `Neural` glide engine the codebase does not yet ship. Either delete the enum entry/string or land the engine.
4. **`addons/` directory does NOT exist at repo root.** Verified — `Test-Path 'W:\repos\SwiftFloris\addons'` returns False. Every addon reference in facade comments (`addons/smart-compose-litert/`, `addons/handwriting-mlkit/`, `addons/translator-bergamot/`, `addons/cjk-librime/`, `addons/voice-vosk-en/`, `addons/passkey-adapter/`, `addons/handwriting-mlkit/`) points at empty space. `scripts/verify-addon-apk.sh` (264 LOC) exists with **no CI consumer**.
5. **`ime/security/TinkStringPreferenceCrypto.kt` has no test file** despite being a recent crypto path with two `Log.w` swallows. Adding a round-trip test covers the v1.8.68 Tink migration regression-proof.
6. **`AbstractEditorInstance.kt` has 13 `runBlocking` callsites** (lines 101, 176, 225, 317, 346, 364, 397, 422, 521, 542, 561 plus two more) — densest concentration in the codebase, all on the InputConnection event hot path. The Workstream 4 coroutine hardening pass closed the policy contract but did not re-architect coroutine usage. **Needs validation** under sustained typing load on a budget device that the blocking patterns don't cause ANR.
7. **`PhysicalKeyboardScreen.kt` is 58 LOC** — one switch plus a system-settings deep-link. Despite shipped Mac `.keylayout` / Keyman `.kmp` / KLC parsers + `HardwareKeyboardRuntimeMapper`, there is no in-app way to view, import, or pick a custom hardware layout. Engineering investment is stranded behind the Settings surface.
8. **`SmartbarScreen.kt:76-87`** carries an explicit TODO + a switch hardcoded to `enabledIf = { false }` showing `summary_locked` — vestigial UI for a deferred decision.
9. **`KeyboardMode.kt:22/30/32`** carries three `@Deprecated(message = "TODO: remove")` enum entries that still force `when` exhaustiveness everywhere; **`FlorisImeSizing.kt:116`** carries `@Deprecated("TODO: move logic fully into ImeWindow impl")`.
10. **`DictionaryManager.kt` carries 8 `Log.w/Log.e` swallows + 8 `@Suppress` annotations** — highest concentration in the app, on the SQLCipher migration path. Worth an audit pass.
11. **`TextKeyData.kt:637/656`** has `try { appendCodePoint(...) } catch (_: Throwable) { }` — silently drops malformed codepoints; should log via `flogWarning`.
12. **`:benchmark` module is wired into settings but no CI workflow invokes it** — no PR gate, no scheduled run. Trend regression invisible to CI today.
13. **`AdvancedProviders.kt` is the only file in `ime/nlp/advanced/`** despite two test files (`AdvancedPredictionEngineTest`, `AdvancedSpellingEngineTest`) — Likely the engines are partially implemented; verify or document.
14. **`ime/calendar/` package added since the 2026-05-17 snapshot** — `CalendarAgendaPickerPanel.kt`, `CalendarPermissionActivity.kt`, `CalendarQuickInsertManager.kt`. Only `CalendarAgendaFormatterTest` covers it; the permission Activity + Quick Insert manager are untested.
15. **`NlpManager.kt:290` ALREADY routes to `SmartComposeProviderRegistry.active.ghostText(...)`** for inline ghost-text rendering — the wire-up exists. The Default provider returns `NoSuggestion`. So **F5 above is partially built**: what's actually missing is a `Default` provider that consumes the existing `PersonalTrigramStore` + `PersonalBigramStore` + `ColdStartNextWordPriors` confidence chain instead of the no-op. Refactor F5 framing accordingly.
16. **Empty `try/catch (_: Throwable) {}` at `FlorisImeService.kt:758` (`getTextForImeAction`)** — silently falls back to super; should log.

### New evidence from Build/CI/Release reconnaissance

17. **7 of 8 workflows use floating major-tag pins** (e.g. `actions/checkout@v4`, `gradle/actions/setup-gradle@v4`, `reactivecircus/android-emulator-runner@v2`, `google/osv-scanner-action@v2.0.2`). Only `crowdin-upload.yml` (SHA-pinned `crowdin/github-action@8868a33591…`) and `validate-strings-no-translations.yml` (SHA-pinned `peter-evans/create-or-update-comment@71345be…`) SHA-pin third-party actions. **Supply-chain risk.**
18. **`release.yml:95` curl-downloads the `osv-scanner v2.0.2` binary by version-pinned URL but does NOT SHA-256 verify it.** A compromised GitHub releases CDN could swap the binary. Either SHA-pin the URL or migrate the release-time scan to use the SHA-pinned `google/osv-scanner-action`.
19. **`reproducible-build.yml` runs on push + PR to app/gradle/build-script paths, but NOT from the release flow itself.** A `release.yml` invocation may publish a release before the matching reproducibility signal lands. Hook `release.yml` to `workflow_call` the repro check or block release until the dispatched repro run is green.
20. **Roborazzi runs only on the debug variant** — `:app:verifyRoborazziDebug`. The release stylesheet (with R8 minify + shrink) is **never visually regressed**. ProGuard/R8 can rename Compose-generated semantics nodes or strip preview-only @Composables; nothing catches it.
21. **16 KB `zipalign -P 16` only runs against the debug variant** (`android.yml:103`). `release.yml` does NOT run it. Release builds carry no in-CI 16 KB alignment proof.
22. **`verifyDataExtractionRules` fires only transitively via `preBuild`** — not an explicit CI step. Adding an explicit step (one line in `android.yml`) makes the gate signal legible.
23. **`gradle/libs.versions.toml` has dead pins** for `coil = "3.4.0"` and `material-kolor = "4.1.1"` — declared but not used by `:app`. Clean up or wire in. PROJECT_CONTEXT.md §3 advertises them as if they were live.
24. **Hard-coded `testImplementation("...")` strings at `app/build.gradle.kts:498-500`** (`"androidx.compose.ui:ui-test-junit4"`, `"androidx.test:runner:1.7.0"`, `"junit:junit:4.13.2"`) bypass the catalog and bypass Dependabot.
25. **`local.properties` is tracked at repo root.** Verified by Agent 2 via `ls`. This is a developer-machine SDK path file; it should never be committed.
26. **`docs/THREAT_MODEL.md:151` "Last updated 2026-05-17 (v1.8.68)"** is 105 versions stale.
27. **`roborazzi-baseline.yml:17-19` stale comment** tells the maintainer to remove `continue-on-error: true` from `android.yml`'s Roborazzi step — already removed. Doc drift.
28. **`patrickgold-compose-tooltip = "0.2.0-rc02"`** is the only non-stable pin in the catalog (`libs.versions.toml:25`). RC pin without an explicit migration plan.

### New evidence from External landscape

29. **FUTO `swipe.futo.org` dataset is MIT-licensed, 1.04M rows, live on Hugging Face** (verified by direct repo fetch at `huggingface.co/datasets/futo-org/swipe.futo.org`). The 2026-05-17 baseline noted FUTO swipes as gated; the actual license is permissive. **This is the major external-research finding: SwiftFloris can train an Apache-2.0 glide model NOW without waiting for HeliBoard NLnet's June 1 2026 deadline.**
30. **FlorisBoard upstream `v0.6.0-alpha02` shipped 2026-01-23** with CLDR 48, Emoji 17 readiness, number-field fix, and the floating-window-mode foundation. SwiftFloris should rebase those four pieces; upstream is glacial but not dead.
31. **HeliBoard achieved F-Droid `Verified` tier**; FlorisBoard is `Not verified`. SwiftFloris targeting `Verified` from F-Droid day one is a meaningful differentiator vs FlorisBoard upstream.
32. **FUTO v0.1.28 (2026-05-04) integrates mozc Japanese** — proves the Android JNI path for mozc (BSD-3-Clause, Apache-compatible). De-risks SwiftFloris L3 Japanese deliverable; use mozc instead of OpenWnn / iWnn.
33. **FunctionGemma 270M ships at `litert-community/functiongemma-270m-ft-mobile-actions` on Hugging Face** under the Gemma Terms of Use. Function-calling on a 270M model is **directly relevant to SwiftFloris's MCP bridge** — chain it to existing MCP daemons → "remind me Tuesday 3pm" runs locally as a calendar tool-call via FunctionGemma + the existing AIDL MCP transport. New roadmap candidate.
34. **Gboard "Rambler" streaming-voice cleanup (Gemini Nano on-device) shipped 2026-05-12.** Sets a new bar for on-device voice typing; SwiftFloris's L1 Smart Compose can match Rambler on Gemma 3 270M without cloud.
35. **CleverKeys-ML repo: LICENSE file is not visible at root** via direct fetch — **Needs validation** before any dataset/model reuse. If unlicensed, treat as proprietary; if MIT/Apache, this is an L1-unblocker.
36. **Bergamot main repo (`mozilla/bergamot-translator`) is archived/INACTIVE.** Active fork at `browsermt/bergamot-translator` (MPL-2.0). The L2.1a addon should target the active fork; main repo is dead.
37. **AnySoftKeyboard v1.13.547 beta (2026-05-18)** — much more active than baseline implied. Re-baseline as active competitor, not dormant.
38. **Fleksy consumer app discontinued** — pivoted to B2B SDK + cybersecurity. Confirms commercial-keyboard attrition; SwiftFloris's wedge expands.
39. **Android 17 (API 37) developer preview**: `CONFIG_KEYBOARD` changes no longer restart activities by default; IME visibility is not restored after unhandled config change; new `show_passwords_physical` setting forces password masking on hardware keyboards. SwiftFloris's hardware-keyboard surface (Next-6.4 / L8) must respect the new mask. CJK IME accessibility events (`TextAttribute`) — relevant when L3 lands.
40. **None of the five mainstream press surfaces** (BGR, Android Authority, How-To Geek, MakeUseOf, AlternativeTo) names SwiftFloris as a SwiftKey alternative as of 2026-05-25. The 2026-05-17 outreach drafts at `docs/outreach/2026-05-17-swiftkey-migration/` have **not yet been posted** (Needs validation by maintainer). Six-day window left.

### Additional roadmap items (delta on top of the main roadmap)

The items above promote to:

- [x] **P0** — F15 — **Wire or hide `TOGGLE_AUTOCORRECT`** — **shipped v1.8.183 (2026-05-25)**. `handleToggleAutocorrect()` now flips the live `prefs.correction.autoCorrect` preference (mirroring `handleToggleIncognitoMode()`) and surfaces a Crowdin-routed toast (`autocorrect_toggle__toast_after_enabled/disabled`). NlpManager + spacebar candidate selection + touch-decoder gate already consume the same preference. The placeholder toast and the stale `showLongToastSync` import are gone.

- [ ] **P0** — F16 — **Delete `local.properties` from tracking and add to `check-repo-hygiene.sh`**. Touches: `git rm --cached local.properties`, `.gitignore` already covers it, `scripts/check-repo-hygiene.sh`. Acceptance: planted regression fails the hygiene script. Complexity: S.

- [x] **P0** — F17 — **Resolve "Neural coming soon" glide-engine entry** at `strings.xml:978`. Either ship the engine or delete the enum value + string. Touches: `strings.xml`, `ime/text/gestures/GlideTypingLanguageSupport.kt`, `app/settings/gestures/GesturesScreen.kt`. Acceptance: no UI shows an unwirable option. Complexity: S. **Shipped v1.8.179 (2026-05-25)** — deleted the enum value, the string, and the label-arm reference; no profile mapped to it, so no UI regression. Real-engine path (F21) tracks the FUTO MIT swipe-dataset training pipeline separately as XL out-of-tree work.

- [ ] **P1** — F18 — **F5 refactor**: ship a `Default SmartComposeProvider` that consumes the existing `PersonalTrigramStore` + `PersonalBigramStore` + `ColdStartNextWordPriors` chain (instead of returning `NoSuggestion`). The wire-up at `NlpManager.kt:290` already exists. This makes ghost-text work without an addon. Touches: new `ime/smartcompose/HeuristicSmartComposeProvider.kt`, `SmartComposeProviderRegistry`, `app/AppPrefs.kt` (gate), `app/settings/typing/`. Acceptance: with the new toggle on and confidence-gated, ghost text renders inline from the existing trigram chain. Replaces / refines F5. Complexity: S-M.

- [x] **P1** — F19 — **SHA-pin every third-party action**. Touches: every workflow under `.github/workflows/`. Acceptance: every `uses: foo/bar@vN` becomes `uses: foo/bar@<sha>` with a comment recording the version. Complexity: S. **Shipped v1.8.177 (2026-05-25)** — 9 distinct action references SHA-pinned across all 8 workflows; SHAs resolved via `gh api`; full mapping in `CHANGELOG.md#v1.8.177`.

- [x] **P1** — F20 — **SHA-256-pin the `osv-scanner v2.0.2` curl download** in `release.yml:95`. Acceptance: planted swap of the URL fails CI. Complexity: S. **Shipped v1.8.177 (2026-05-25)** — `OSV_BINARY_SHA256` env carries `3abcfd7126c453a00421487e721b296e0cb68085bd431d6cef60872774170fc8`; binary refuses to execute on mismatch.

- [ ] **P1** — F21 — **Train an Apache-2.0 glide model from the MIT-licensed FUTO swipe dataset**. The 1.04M-row dataset at `huggingface.co/datasets/futo-org/swipe.futo.org` is MIT. Training is off-device; model ships in `addons/swipe-model-swiftfloris/` or inside the existing `swiftfloris-statistical` engine as a quantized weights file. Acceptance: N1.4 replay benchmark shows accuracy improvement vs `StatisticalGlideTypingClassifier`. Complexity: XL (out-of-tree training + integration). Priority gated on someone with ML infra; can promote into ROADMAP §6 N1.1 alternative.

- [ ] **P1** — F22 — **Rebase against FlorisBoard upstream `v0.6.0-alpha02` for CLDR 48 + Emoji 17 readiness + number-field fix + floating-window-mode pieces.** Cherry-pick the four pieces, not the whole alpha. Acceptance: cherry-pick PRs merged, no regression in current SwiftFloris features. Complexity: M.

- [ ] **P1** — F23 — **Reproducible-build verification chained to `release.yml`**. Today `reproducible-build.yml` runs on push/PR paths but never on the release-tag flow. Either `workflow_call` from `release.yml` or block tag publish until repro is green. Complexity: M.

- [ ] **P1** — F24 — **Roborazzi for the release variant** (`:app:verifyRoborazziRelease`). R8/minify can rename Compose semantics nodes; nothing catches it today. Touches: `app/build.gradle.kts`, `android.yml`. Complexity: M.

- [x] **P1** — F25 — **`zipalign -P 16` on release variant** in `release.yml`. Touches: `release.yml`. Acceptance: planted misaligned `.so` in release fails the workflow. Complexity: S. **Shipped v1.8.178 (2026-05-25)**.

- [x] **P1** — F26 — **Explicit `verifyDataExtractionRules` CI step** alongside `verifyNoInternetPermission`. Touches: `android.yml`, `release.yml`. Complexity: S. **Shipped v1.8.178 (2026-05-25)** — explicit step added in both workflows; the gate's auto-fire from preBuild is preserved, the explicit call adds run-summary legibility.

- [ ] **P1** — F27 — **Add a `KeyboardManager` unit test set** for the dispatch + state machine. Sliver-mocked `EditorInstance` + scripted KeyData sequences. Touches: `app/src/test/kotlin/.../keyboard/KeyboardManagerStateMachineTest.kt`. Acceptance: at least 5 critical state-transition tests. Complexity: M.

- [ ] **P1** — F28 — **`TinkStringPreferenceCrypto` round-trip test**. Touches: `app/src/test/kotlin/.../security/TinkStringPreferenceCryptoTest.kt`. Complexity: S.

- [ ] **P1** — F29 — **Build out `PhysicalKeyboardScreen`** to actually expose the shipped Mac `.keylayout` / Keyman `.kmp` / KLC parsers + `HardwareKeyboardRuntimeMapper`. Custom-layout picker + Import button. Touches: `app/settings/advanced/PhysicalKeyboardScreen.kt`, plumbing into `ime/hardware/`. Complexity: M.

- [ ] **P2** — F30 — **FunctionGemma 270M MCP-bridge addon** (or in-tree facade if it fits the size budget). Chain on-device function-calling to existing MCP daemons. Touches: `ime/smartcompose/`, `ime/mcp/`, out-of-tree `addons/functiongemma-mcp/`. Complexity: L (mostly out-of-tree).

- [ ] **P2** — F31 — **Per-app language auto-switch via `LocaleManager.getApplicationLocales`** (opt-in). Touches: `ime/core/`, new `app/settings/localization/PerAppLanguageScreen.kt`. Complexity: M.

- [x] **P2** — F32 — **Empty `try/catch (_: Throwable)` audit** — **shipped v1.8.184 (2026-05-25)**. All three sites (`TextKeyData.kt:637`, `TextKeyData.kt:656`, `FlorisImeService.kt:758`) now log via `flogWarning` with the exception class name; the `catch` stays in place because each call site has a legitimate failure mode (malformed code point, missing AndroidInternalR string on OEM builds) — silent swallow is the bug, not the catch itself.

- [~] **P2** — F33 — **Delete dead catalog pins** `coil` and `material-kolor`. **Rejected on investigation (2026-05-25)**: both are actively consumed by sibling library modules — `lib/snygg/build.gradle.kts:103-104` uses `libs.coil.compose` + `libs.coil.gif`, and `lib/color/build.gradle.kts:60` uses `libs.material.kolor`. The original claim came from grepping only `app/` source; the catalog pins are live.

- [x] **P2** — F34 — **Move hard-coded `testImplementation` strings** at `app/build.gradle.kts:498-500` into the version catalog so Dependabot can update them. Complexity: S. **Shipped v1.8.180 (2026-05-25)** — promoted `androidx-test-runner`, `junit4`, and `androidx-compose-ui-test-junit4` (compose-BOM-managed, no version ref) into `libs.versions.toml`; `app/build.gradle.kts` consumes them via `libs.*` accessors.

- [x] **P2** — F35 — **Refresh `docs/THREAT_MODEL.md`** — currently 105 versions stale. Reflect v1.8.104-122 privacy regressions closed + addon trust pin store. Complexity: M. **Shipped v1.8.176 (2026-05-25)** — added a 20-bullet "What changed since the v1.8.68 baseline" audit-trail section covering v1.8.85 → v1.8.175.

- [x] **P2** — F36 — **Refresh `roborazzi-baseline.yml` header comment** — the "remove `continue-on-error`" instruction is stale. Complexity: S. **Shipped v1.8.176 (2026-05-25)** — also fixed the snapshot-path reference (`images/` no longer suffixed); `docs/LOCAL_VERIFICATION.md` now calls `check-fastlane-metadata.sh` alongside the existing repo-hygiene gate.

- [ ] **P2** — F37 — **`AdvancedProviders.kt` audit** — single file vs two named engines in tests. Either ship the engines or rename the tests / consolidate. Complexity: S.

- [ ] **P2** — F38 — **Tear out `KeyboardMode.kt:22/30/32` `@Deprecated TODO: remove` enum entries** and `FlorisImeSizing.kt:116` `@Deprecated TODO: move logic`. Complexity: M.

- [ ] **P2** — F39 — **Audit `DictionaryManager.kt`** for its 8 `Log.w/Log.e` swallows + 8 `@Suppress` annotations. Complexity: M.

- [ ] **P2** — F40 — **Roborazzi screen-level baselines for `AddonsSettingsScreen`, `McpSettingsScreen`, `TypingStatsScreen`, `SyncSettingsScreen`, `VoiceInputScreen`, `AiFeaturesScreen`**. Complexity: M.

- [x] **P3** — F41 — **Delete the `smartbar` `sharedActionsAutoExpandCollapse` locked-false switch** — **shipped v1.8.186 (2026-05-25)**. Removed the SwitchPreference + orphaned SideEffect + `SideEffect` import from SmartbarScreen.kt; removed the three unused English label strings. The `@Deprecated` AppPrefs entry stays for saved-value compat; the 24 translated `values-*/strings.xml` entries become unused-resource lint warnings until the next Crowdin sync drops them.

- [x] **P3** — F42 — **Update `addons/` references in facade docs** — **shipped v1.8.187 (2026-05-25)**. Rewrote four facade KDoc blocks (`CjkInputProvider`, `StrokeRecognizer`, `SmartComposeProvider`, `InlineTranslator`) to say "out-of-tree signed addon APK" distributed via GitHub Releases / Obtainium / F-Droid through the `AddonContract.Action.REGISTER_*` enrolment path. `ime/passkey/` and `ime/voice/` were also flagged but already used clean prose (verified by grep).

These items extend Phase 2 and Phase 3 of the prioritized roadmap above. The grand total is now **F1–F42 + EI1–EI12** = **54 net-new items** beyond the existing ROADMAP / IMPROVEMENT_PLAN tracking.

---

## Appendix — Files touched by this plan (verified, in tree)

- [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [ROADMAP.md](ROADMAP.md), [IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md), [CHANGELOG.md](CHANGELOG.md), [README.md](README.md), [ARCHITECTURE.md](ARCHITECTURE.md), [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md), [CONTRIBUTING.md](CONTRIBUTING.md)
- [app/build.gradle.kts](app/build.gradle.kts), [build.gradle.kts](build.gradle.kts), [settings.gradle.kts](settings.gradle.kts), [gradle.properties](gradle.properties)
- [.github/workflows/](.github/workflows/) (`android.yml`, `release.yml`, `dependency-scan.yml`, `reproducible-build.yml`, `crowdin-upload.yml`, `emulator-smoke.yml`, `roborazzi-baseline.yml`, `validate-strings-no-translations.yml`)
- [.github/dependabot.yml](.github/dependabot.yml), [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/), [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md)
- [scripts/](scripts/), [tools/](tools/)
- [fastlane/](fastlane/), [fastlane/metadata/android/en-US/](fastlane/metadata/android/en-US/)
- [docs/](docs/) (every file)
- [app/src/main/kotlin/dev/patrickgold/florisboard/](app/src/main/kotlin/dev/patrickgold/florisboard/) (36 IME sub-packages, 15 settings sub-packages, 1 setup screen)
- [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)
- [app/src/test/kotlin/](app/src/test/kotlin/) (204 test files), [app/src/test/snapshots/](app/src/test/snapshots/) (6 Roborazzi baselines)
- [app/src/androidTest/](app/src/androidTest/) (5 instrumentation test files)
- [.ai/research/2026-05-17/](.ai/research/2026-05-17/) (16 research artifacts, ~6,000 lines combined)

---

*End of research-and-feature plan.*
*The next full research run goes in `.ai/research/2026-06-01/` per convention.*
*This file does NOT supersede `ROADMAP.md` or `IMPROVEMENT_PLAN.md`; it is additive evidence + a delta plan.*
