# SwiftFloris Research Feature Plan

Run date: 2026-06-05  
Repository: `C:\Users\--\repos\SwiftFloris`  
Branch and HEAD reviewed: `master` at `80f7bbc` (`docs: refresh cycle 17 research queue`)  
Current release declared by roadmap: v1.8.246, versionCode 2046  
Scope: research and planning only. No feature implementation was performed.

This report is additive to `ROADMAP.md` and `RESEARCH_REPORT.md`. It does not
replace those files as the live work queue. The goal is to consolidate current
repo evidence, current Android/FOSS-keyboard ecosystem evidence, and a practical
priority order for the next implementation passes.

## Executive Summary

SwiftFloris is already positioned as a privacy-first Android IME with a strict
base-app trust story: no network permission, no closed blobs in `:app`, local
dictionary learning, encrypted import/export, addon isolation for high-risk or
large capabilities, and increasingly strong QA/release documentation.

The highest-value next work is not a broad feature grab. It is a trust and
typing-quality consolidation pass:

1. Close the personal n-gram data-safety cluster first. `PersonalBigramStore`
   and `PersonalTrigramStore` still fall back to deleting the live TSV before a
   second rename attempt, and the open research queue already identifies token
   separator safety plus reset/stat serialization. These are core local-learning
   correctness issues.
2. Scope MCP tool dispatch by daemon before the MCP surface grows. Dispatch now
   resolves a flat `toolName` by global first-match order, while disabled-tool
   storage already models `(daemonPackage, toolName)` pairs. That mismatch is
   small today but becomes a trust-boundary issue as more daemons appear.
3. Finish the imported-sticker SAF allow-list proof. The roadmap already calls
   out forged encoded sticker URI rejection. Live code still opens stored
   `sourceUri` values directly through `StickerMediaProvider.openFile()`.
4. Turn device-gated visual, glide, backup/restore, accessibility, and Roborazzi
   work into release evidence. The repo has strong checklists; the remaining gap
   is captured proof on real devices and baseline images.
5. Treat FUTO Keyboard 0.1.29 as the new glide benchmark moment. FUTO now ships
   an open FUTO Swipe system and publishes public-test-set top-1/top-4 framing.
   SwiftFloris should benchmark against that public framing before integrating
   any in-tree glide model.
6. Keep the base app offline. Voice, local generative features, model-backed
   glide, and richer MCP work should stay in signed addon lanes unless a feature
   can satisfy the existing Apache-2.0/no-network/no-closed-blob invariants.

The most practical implementation path is: data integrity and trust-boundary
quick wins, then device-evidence capture, then upstream/FUTO/Emoji/API refresh,
then larger addon and release-provenance bets.

## Evidence Reviewed

### Local Repository Evidence

- Planning and project state: `ROADMAP.md`, `RESEARCH_REPORT.md`,
  `PROJECT_CONTEXT.md`, `README.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`,
  `COMPLETED.md`.
- Verification docs: `docs/LOCAL_VERIFICATION.md`, `docs/QA_CHECKLISTS.md`,
  `docs/THREAT_MODEL.md`, `docs/REPO_HYGIENE.md`,
  `docs/REPRODUCIBLE_BUILDS.md`, `docs/SECURITY.md`,
  `docs/PRIVACY_AND_AI.md`, `docs/ACCESSIBILITY.md`.
- Build and release gates: `.github/workflows/android.yml`,
  `.github/workflows/release.yml`, `.github/workflows/dependency-scan.yml`,
  `.github/workflows/reproducible-build.yml`,
  `.github/workflows/benchmark-regression.yml`,
  `.github/workflows/emulator-smoke.yml`,
  `.github/workflows/roborazzi-baseline.yml`, scripts under `scripts/`.
- Core app files: `app/build.gradle.kts`,
  `app/src/main/AndroidManifest.xml`, `gradle.properties`,
  `gradle/libs.versions.toml`, `gradle/tools.versions.toml`.
- Key code paths reviewed:
  - MCP: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/`
  - Subtypes: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/core/SubtypeManager.kt`
  - Honeycomb layout loading: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombLayoutLoader.kt`
  - Personal n-grams: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/PersonalBigramStore.kt` and `PersonalTrigramStore.kt`
  - Stickers: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerMediaProvider.kt` and `UserStickerRepository.kt`
- Tests reviewed:
  - MCP discoverer/registry/router tests under `app/src/test/kotlin/dev/patrickgold/florisboard/ime/mcp/`
  - `PersonalNgramFlushIsolationTest.kt`
  - `HoneycombLayoutLoaderTest.kt`
  - `UserStickerRepositoryTest.kt`

### Repository Facts That Matter

- `ROADMAP.md` declares v1.8.246, versionCode 2046, and keeps the strict rules:
  no `INTERNET` permission in `:app`, Apache-2.0 ceiling, no closed-source blobs,
  release docs/changelog/fastlane sync.
- `app/build.gradle.kts:258` defines a banned network-permission list, including
  `INTERNET`, `ACCESS_NETWORK_STATE`, Wi-Fi/network state, and network-change
  permissions.
- `app/build.gradle.kts:290` registers `verifyNoInternetPermission`;
  `app/build.gradle.kts:344` registers merged-manifest network-permission checks;
  `app/build.gradle.kts:400` registers `verifyDataExtractionRules`.
- `app/src/main/AndroidManifest.xml:6`, `:9`, and `:13` declare only
  `VIBRATE`, `POST_NOTIFICATIONS`, and `READ_CALENDAR` from platform
  permissions in the reviewed manifest.
- `app/src/main/AndroidManifest.xml:21` defines the signature permission
  `dev.patrickgold.florisboard.permission.REGISTER_ADDON`.
- `app/src/main/AndroidManifest.xml:75` declares the IME service;
  `:180` declares the Tasker receiver behind the signature permission;
  `:202` declares the sticker media provider.
- File inventory excluding build outputs is broad for an IME fork:
  approximately 1,537 repository files, including 826 Kotlin files and 232 unit
  test files.
- Current main IME package areas include addon, bidi, calendar, cjk, clipboard,
  core, dictionary, editor, handwriting, hardware, indic, input, keyboard,
  mcp, media, nlp, passkey, smartbar, smartcompose, snippet, sync, tasker,
  translate, voice, window, and wordstyles.

### External Research Sources

- Microsoft SwiftKey support account page:
  https://support.microsoft.com/en-us/topic/account-a3c38581-903f-4d22-a388-cc13c7debf0e
- FUTO Keyboard 0.1.29 release:
  https://github.com/futo-org/android-keyboard/releases/tag/0.1.29
- FUTO Swipe dataset:
  https://huggingface.co/datasets/futo-org/swipe.futo.org
- FUTO Swipe project:
  https://swipe.futo.tech/
- FlorisBoard releases:
  https://github.com/florisboard/florisboard/releases
- FlorisBoard repository:
  https://github.com/florisboard/florisboard
- HeliBoard releases:
  https://github.com/HeliBorg/HeliBoard/releases
- AnySoftKeyboard releases:
  https://github.com/AnySoftKeyboard/AnySoftKeyboard/releases
- Android `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` docs:
  https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING
- Android package visibility docs:
  https://developer.android.com/training/package-visibility/declaring
- Android Auto Backup and data-extraction docs:
  https://developer.android.com/identity/data/autobackup
- Android 16 KB page-size compatibility docs:
  https://developer.android.com/guide/practices/page-sizes
- GitHub artifact attestations docs:
  https://docs.github.com/en/actions/concepts/security/artifact-attestations
- F-Droid reproducible-build docs:
  https://f-droid.org/docs/Reproducible_Builds/

### Evidence Limits

- This pass did not run Gradle builds or Android emulator/device tests because
  the requested output was a research report only.
- GitHub release facts were refreshed through GitHub API/pages during the pass.
  The FOSS keyboard ecosystem changes quickly, so these should be rechecked
  before implementation begins.
- F-Droid submission status was inferred from repository docs and live roadmap,
  not from the fdroiddata repository.
- Device-gated items remain device-gated. This report should not be used as
  evidence that visual, glide, backup/restore, or accessibility behavior passed
  on hardware.

## Current Product Map

### Product Position

SwiftFloris is a SwiftKey-class Android keyboard focused on local privacy,
auditability, and user-controlled extension points. It is stronger than most
FOSS keyboard forks on trust posture and roadmap discipline, and it is catching
up on the pieces that users expect from commercial keyboards: glide typing,
multilingual prediction, clipboard/media, migration, high-quality themes,
accessibility, and backup/restore.

The product should keep leaning into three differentiators:

- Offline by default: no network permission in the base app and no telemetry.
- Local learning with user control: personal dictionary, learned n-grams,
  import/export, forget/reset operations, and field privacy flags.
- Extensible without contaminating the base trust model: signed addons,
  dictionary packs, MCP daemons, voice, handwriting, and future model features.

### Core User Workflows

- First-run setup: enable IME, select SwiftFloris, show privacy/AI disclosure,
  request optional notification permission where needed.
- Daily typing: layouts, themes, candidate row, autocorrect, glide typing,
  multilingual suggestions, popup keys, gestures, split/floating/landscape
  surfaces.
- Personalization: personal dictionary entries, shortcut entries, imports from
  Gboard/FlorisBoard/SwiftKey/SwiftFloris, encrypted export, learned n-gram
  reset/forget controls.
- Clipboard and media: clipboard history, sensitive-clip handling, media clone
  reconciliation, stickers, rich-content commit path.
- Smartbar and actions: configurable quick actions, per-app profiles, calendar
  insertion, task/note share action, snippets, inline autofill.
- Addons and trust: signed addon discovery, trust pins, dictionary pack
  mounting, MCP daemon bridge, future voice/model features outside `:app`.
- Verification and release: no-network manifest gates, data-extraction gates,
  release metadata rules, repo hygiene scripts, benchmark and Roborazzi
  workflows.

## Feature Inventory

### Typing Core

Status: mature but still has risk in edge-field behavior and visual/manual QA.

- Layout engine, popup keys, one-handed/floating/split/landscape/tablet layouts.
- Autocorrect and suggestion stack with SymSpell, language-provider integration,
  personal bigram/trigram learning, multilingual ranking, glide context rescore.
- Glide typing with current theme/trail work, but low-end hardware evidence and
  public-test-set benchmarking remain open.
- Field privacy should honor `IME_FLAG_NO_PERSONALIZED_LEARNING` and incognito
  style flows. Android documents that this flag asks IMEs not to update
  personalized typing history or language models for that editor.

Improvement focus:

- Prioritize personal n-gram data safety before adding more ranking features.
- Benchmark glide against FUTO's public test-set framing before model work.
- Capture real-device state coverage for candidate row, software-key states,
  smartbar overflow, layout variants, and reduced animation.

### Themes and Visual Surface

Status: high user value; verification is the main gap.

- Multiple bundled themes, Snygg surface, high-contrast theme, animated/reduced
  animation considerations, glide-trail themes.
- Roborazzi exists, and the roadmap calls out baseline capture work.

Improvement focus:

- Finish baseline capture and remove pending ignores once baselines are real.
- Add glide-trail theme baselines and low-end performance traces.
- Keep UI controls dense and stable; avoid marketing-like surfaces inside the
  keyboard/settings app.

### Personal Dictionary, Imports, and Local Learning

Status: strategically central; most important current risk cluster.

- SwiftKey JSON, FlorisBoard CSV, Gboard XML, and encrypted SwiftFloris paths
  are documented in the repository history and roadmap.
- Personal bigram/trigram stores persist locale-scoped TSV data.
- `PersonalBigramStore.kt:317-319` and `PersonalTrigramStore.kt:321-323` still
  use a rename fallback that deletes the destination before a second rename
  attempt.
- `PersonalTrigramStore.kt:51` uses NUL as a context delimiter, while current
  research queue notes token separator safety for tab, newline, carriage return,
  NUL, and other control separators.
- `PersonalBigramStore.kt:224` and `PersonalTrigramStore.kt:229` expose
  `totalEntryCount()` flows that should not reload stale locales around reset.

Improvement focus:

- Implement atomic replace semantics without destination deletion.
- Add token sanitization before persistence, not only during TSV parsing.
- Serialize stats counting and reset cleanup.
- Prefer focused, behavior-level tests over source-string-only tests where
  feasible.

### Clipboard, Stickers, and Rich Content

Status: broad feature coverage; imported sticker path needs trust proof.

- Manifest declares `StickerMediaProvider`.
- `StickerMediaProvider.kt:94` implements `openFile()`.
- `StickerMediaProvider.kt:99-101` opens a user sticker's stored `sourceUri`
  directly through the content resolver.
- `StickerMediaProvider.kt:123` resolves imported stickers from encoded
  document URIs.
- `UserStickerRepository.kt:69-76` checks persisted tree grants when loading
  packs.
- `UserStickerRepository.kt:100` exposes `stickerForEncodedDocument()`, which
  reconstructs a sticker from an encoded URI.

Improvement focus:

- Reject forged encoded sticker URIs that are not children of a currently
  trusted SAF tree grant.
- Keep legitimate user-picked folders working after process restart and after
  permission revocation.
- Add provider-level tests around `openFile()` behavior, not only repository
  enumeration tests.

### Addons, MCP, and Automation

Status: strong architecture; dispatch identity should be tightened now.

- Addon registration is signature-protected.
- Tasker receiver is signature-permission protected.
- MCP disabled-tool persistence already uses `(daemonPackage, toolName)` pairs.
- `McpDaemonRegistry.kt:86-93` resolves a tool by flat name across active
  daemons and returns the first match.
- `McpDispatchRouter.kt:62-82` accepts a request with only `toolName`, resolves
  it globally, checks disabled state against the resolved daemon, and calls the
  client with the same flat name.
- `McpDispatchRouter.kt:95` models `McpDispatchRequest` with no daemon key.
- `McpDaemonDiscovererTest.kt:85` covers blank tool-name skipping; current tests
  do not pin duplicate tool names across daemons.

Improvement focus:

- Add explicit daemon identity to dispatch requests and UI commands.
- Reject or namespace duplicate advertised tool names in user-facing surfaces.
- Constrain advertised tool-name shape early so future daemons cannot create UI
  or persistence ambiguity.
- Keep MCP local-only and permission-scoped.

### Voice, Handwriting, Translation, Smart Compose, CJK, and Model Features

Status: many facades exist; runtime should remain addon-gated.

- Voice should remain FUTO handoff or signed addon; do not add `RECORD_AUDIO` to
  the base app.
- CJK and model-backed features are large, high-value bets but should not block
  data-safety and device-proof work.
- Local generative or translation features can be valuable for power users only
  if the trust boundary is explicit and the base app remains offline.

Improvement focus:

- Improve user-facing "not installed" and "addon required" flows.
- Add sample signed addon fixtures and developer docs before expanding runtime
  feature scope.
- Keep size, license, and permission budgets visible in every proposal.

### Release, CI, and Supply Chain

Status: stronger than typical Android forks; provenance/F-Droid work remains.

- Build gates already cover no-network permission and data-extraction rules.
- Release docs and fastlane changelog guidance have been recently improved.
- F-Droid reproducibility and GitHub artifact attestations are natural next
  supply-chain milestones.
- GitHub artifact attestations provide signed provenance and map to SLSA Build
  Level 2 according to GitHub's docs.
- F-Droid reproducible builds verify developer-signed APKs by rebuilding and
  checking that the upstream binary matches the published source/build recipe.

Improvement focus:

- Attach SBOM and provenance to releases.
- Keep reproducible-build self-check green and document exact environment.
- Decide package-id strategy before fdroiddata submission.

## Competitive And Ecosystem Research

### SwiftKey Market Timing

Microsoft's SwiftKey support page says SwiftKey Accounts were retired on
31 May 2026 and that backup/sync now continues by signing into a Microsoft
Account using OneDrive storage. As of this report date, 2026-06-05, that date
has passed.

Implication for SwiftFloris:

- The pitch should shift from "prepare before the deadline" to "leave the
  account/cloud lock-in path now."
- Migration UX still matters because users may have exported before the date,
  moved through Microsoft/OneDrive reluctantly, or want a keyboard with local
  learning and no base-app network permission.
- Messaging should avoid overpromising recovery of deleted SwiftKey account
  data. The safe product promise is import from files the user still controls.

### FUTO Keyboard

Live GitHub release check found latest FUTO Keyboard release `0.1.29`, published
2026-06-01. It introduced FUTO Swipe, points to a 1 million QWERTY English
swipe dataset, publishes a public-test-set evaluation frame, and shows 1
accepted word plus 3 alternatives after swiping.

Strategic implications:

- FUTO has moved the swipe conversation from "private keyboard vendors have the
  data" to "public test set and open-source swipe system exist."
- SwiftFloris should stop treating glide model work as speculative until it has
  a benchmark harness against the public filtered test set.
- The roadmap's F21 item is still right, but the acceptance should include
  FUTO-style top-1 and top-4 error reporting before UI or model integration.
- Candidate-row policy after glide should be explicit: accepted word only,
  accepted word plus alternatives, or user-configurable behavior.

### FlorisBoard

Live GitHub data found latest stable release `v0.5.2` and newer tags through
`v0.6.0-alpha02`. The alpha work includes key spacing/font scaling, display
scaling fixes, number-field composing-region fixes, sensitive clipboard
deduplication, RTL settings layout fixes, LiveData-to-Flow migration, and
cleanup.

Strategic implications:

- FlorisBoard remains the upstream source for IME surface fixes and platform
  adaptation.
- SwiftFloris should cherry-pick surgically, with tests around number fields,
  clipboard sensitivity, display scaling, and RTL settings surfaces.
- Broad upstream merges are less attractive than targeted patch lanes because
  SwiftFloris now has many fork-specific trust and addon constraints.

### HeliBoard

Live GitHub release check found latest HeliBoard release `v3.9`, published
2026-03-29. Recent releases emphasize practical keyboard behavior such as
incognito icon responsiveness and backup-restore gesture-data behavior, while
the broader release stream includes emoji search, per-app subtype memory,
haptics, clipboard behavior, layout editing, and hardware-key fixes.

Strategic implications:

- HeliBoard is a benchmark for pragmatic FOSS keyboard polish and low-friction
  user settings.
- SwiftFloris should watch HeliBoard's per-app subtype, backup/restore, emoji,
  haptics, and hardware-keyboard behavior, but should differentiate on strict
  no-network base-app posture, addon trust, encrypted import/export, and richer
  prediction.

### AnySoftKeyboard

Live GitHub release check found latest AnySoftKeyboard release `1.13-r1`,
published 2026-02-08. Release notes mention gesture-typing accuracy and
performance, Android 15 16 KB page support, Android 15+ emoji updates,
edge-to-edge support, API 23 minimum, and translations.

Strategic implications:

- AnySoftKeyboard validates that old, mature FOSS keyboards are now responding
  to 16 KB page-size, edge-to-edge, emoji, and gesture-typing pressures.
- SwiftFloris should keep API/page-size compatibility as a release-readiness
  lane, even if its base code is mostly Kotlin/Java. Dependencies and addons can
  still introduce native compatibility risk.

### Android Platform Pressure

- Package visibility: Android 11+ filters many app queries by default. SwiftFloris
  should keep integrations explicit and avoid broad package visibility.
- Personalized learning privacy: `IME_FLAG_NO_PERSONALIZED_LEARNING` should
  be treated as a hard stop for learned n-grams and personal dictionary writes
  in that editor context.
- Backup/data extraction: Android 12+ data-extraction rules matter even when
  cloud backup is disabled, because device-to-device transfer behavior can vary.
- 16 KB page sizes: Google Play requires support for apps targeting Android 15+
  on affected submissions from 2025-11-01. The base app should stay clear, but
  every native addon/dependency lane needs compatibility proof.

## Highest-Value New Features

### 1. Public Glide Benchmark Harness

Priority: P1  
Type: user-visible quality, research infrastructure  
Evidence: FUTO Keyboard 0.1.29 and FUTO Swipe dataset  

Build a non-shipping harness that evaluates SwiftFloris glide output against
the public FUTO test-set framing. Report at least:

- Top-1 error for accepted word.
- Top-4 error if SwiftFloris exposes alternatives.
- Runtime per swipe on low-end and reference devices.
- Language/layout scope, starting with English QWERTY only.
- Exact exclusions and filtering rules.

Why it matters:

- Glide quality is hard to judge by anecdote.
- FUTO has created a current external benchmark users and maintainers can
  understand.
- A harness can be useful before any model is integrated and after every model
  or ranking change.

Acceptance:

- CLI or Gradle task can run against a pinned dataset subset.
- Output lands in `docs/benchmark-results/` or a similarly documented location.
- Report clearly labels local-only, benchmark-only, and shipping behavior.

### 2. Addon Developer Trust Kit

Priority: P2  
Type: ecosystem, maintainability, trust  
Evidence: signed addon architecture and MCP/tool surfaces  

Create a compact developer kit for signed addons:

- Minimal dictionary pack example.
- Minimal MCP daemon example.
- Signature/trust enrollment walkthrough.
- Negative test fixtures for changed cert, missing catalog, malformed catalog,
  duplicate tool names, and overlarge payloads.
- Verification script that maintainers can run before recommending an addon.

Why it matters:

- The addon architecture is one of SwiftFloris's best differentiators.
- Without sample artifacts, extension behavior is hard to review and easy to
  regress.
- A trust kit keeps large features out of `:app` while making addons practical.

### 3. Migration Recovery Assistant

Priority: P2  
Type: UX and retention  
Evidence: SwiftKey account retirement date has passed  

Add a local-only migration assistant that guides users through files they still
control:

- SwiftKey JSON import.
- Gboard XML import.
- FlorisBoard CSV import.
- SwiftFloris encrypted import.
- Clipboard/paste fallback for small word lists.
- Clear explanation that deleted cloud-account data cannot be recovered by the
  keyboard.

Why it matters:

- The old pre-deadline migration window has passed.
- Users who are now uncomfortable with Microsoft Account/OneDrive sync still
  need a clear local path.

### 4. Privacy Evidence Dashboard In Settings

Priority: P3  
Type: trust UX  
Evidence: strong no-network gates and privacy docs  

Add a compact local "privacy evidence" screen in Settings:

- Base app network permissions: absent, with last build-gate name.
- Local learning controls: on/off, reset, export.
- Addon permissions: shown separately from base app.
- Backup/data extraction policy summary.
- Source/release verification links.

Why it matters:

- SwiftFloris has strong privacy evidence, but much of it lives in docs and CI.
- A local evidence screen can convert technical trust posture into user trust
  without telemetry or network calls.

## Existing Feature Improvements

### Data Integrity And Learning Controls

Priority: P0  
Impacted files:

- `PersonalBigramStore.kt`
- `PersonalTrigramStore.kt`
- `PersonalNgramFlushIsolationTest.kt`

Actions:

- Replace TSV files atomically without deleting the live file first.
- Validate learned tokens before they enter TSV persistence.
- Serialize stats counting and reset cleanup.
- Add tests that simulate failed replacement and reset/stat races where
  possible.

Expected user value:

- Lower chance of losing learned local language data.
- More credible privacy/local-learning claim.

### MCP Tool Identity

Priority: P1  
Impacted files:

- `McpDaemonRegistry.kt`
- `McpDispatchRouter.kt`
- `McpDaemonDiscoverer.kt`
- MCP settings UI and tests

Actions:

- Add daemon identity to dispatch requests.
- Require UI commands to carry `(daemonPackage, daemonClass, toolName)` or an
  equivalent stable key.
- Reject or namespace duplicate tool names.
- Constrain tool names to a safe shape.
- Add duplicate-name tests across two daemon entries.

Expected user value:

- Predictable local tool execution.
- Less risk that enabling or installing a new daemon changes which tool a
  command invokes.

### Imported Sticker Path Safety

Priority: P1  
Impacted files:

- `StickerMediaProvider.kt`
- `UserStickerRepository.kt`
- `UserStickerRepositoryTest.kt`

Actions:

- Validate that decoded document URIs belong to a currently trusted persisted
  SAF tree before creating or serving an imported sticker.
- Reject forged URI paths without crashing.
- Preserve legitimate imported folders and provider `openFile()` behavior.
- Add device and provider-level tests.

Expected user value:

- Rich-content import remains useful without weakening file-access trust.

### Subtype Switch Hardening

Priority: P2  
Impacted file: `SubtypeManager.kt`

Actions:

- Collapse `switchToSubtypeById()` to a single nullable lookup.
- Replace the second snapshot and forced unwrap at `SubtypeManager.kt:404`.
- Add a test for a disappearing subtype between check and activation if the
  current architecture can simulate it.

Expected user value:

- Removes a small but avoidable crash edge in subtype switching.

### Honeycomb Diagnostics

Priority: P2  
Impacted files:

- `HoneycombLayoutLoader.kt`
- `HoneycombLayoutLoaderTest.kt`

Actions:

- Preserve fail-safe `emptyList()` behavior for malformed JSON.
- Log a structured diagnostic on parse failure.
- Keep logs non-sensitive: layout id/source and exception type are useful;
  user text is not.

Expected user value:

- Maintainers can diagnose layout-pack problems without breaking the keyboard.

### Visual, Accessibility, And Device Evidence

Priority: P1  
Impacted files/docs:

- Roborazzi screenshot tests and snapshots.
- `docs/QA_CHECKLISTS.md`
- `docs/LOCAL_VERIFICATION.md`
- `docs/ACCESSIBILITY.md`

Actions:

- Capture pending Roborazzi baselines.
- Verify candidate row, smartbar, software-key states, layout variants, high
  contrast, high font scale, reduced motion, TalkBack labels, and hardware
  keyboard paths on real devices.
- Add evidence artifacts or concise references under the existing verification
  docs.

Expected user value:

- Fewer visual regressions in the keyboard surface where users spend nearly all
  of their time.

## Reliability, Security, Privacy, And Data Safety

### Reliability Risks

- Personal n-gram file replacement can delete the live destination before a
  successful replacement exists.
- Subtype switching contains an avoidable forced unwrap after a prior snapshot.
- Honeycomb malformed layout failures intentionally return empty lists, but
  currently lack support diagnostics.
- Device-gated flows remain unproven on the hardware matrix even when unit tests
  are strong.

### Security And Privacy Risks

- MCP tool-name dispatch by global first match can become a local trust-boundary
  ambiguity.
- Imported sticker encoded URI serving needs SAF tree allow-list proof.
- Android `IME_FLAG_NO_PERSONALIZED_LEARNING` behavior should remain a testable
  invariant across every async suggestion/personalization boundary.
- Addon capabilities must stay visibly separate from the base app's permission
  posture.

### Data-Safety Priorities

1. Do not lose personal learning data on failed file replacement.
2. Do not write unsafe token separators into TSV persistence.
3. Do not report stale personal n-gram stats after reset.
4. Do not serve imported sticker files outside trusted user-picked roots.
5. Do not update personalized learning data for editors that request no
   personalized learning.

## UX, Accessibility, And Trust

### UX Priorities

- Make migration paths easier to discover now that the SwiftKey account date has
  passed.
- Keep settings dense and task-oriented; the product is a keyboard utility, not
  a marketing site.
- Make addon-required states explicit, especially for voice, handwriting,
  translation, and model-backed capabilities.
- Keep destructive actions and reset/import flows clear and consistent.

### Accessibility Priorities

- Verify TalkBack labels for keyboard keys, smartbar actions, candidate row,
  sticker/media controls, and settings flows.
- Verify high-contrast and high font-scale states on device.
- Ensure reduced-animation settings affect glide trails and animated themes.
- Keep hardware-keyboard and switch-access paths in the manual QA matrix.

### Trust Priorities

- Show users the base app's no-network status without requiring them to inspect
  Android manifests or CI logs.
- Show addon permissions and signing trust separately from base app behavior.
- Label benchmark and device evidence honestly. Use "Needs live validation"
  where a claim is not backed by hardware proof.

## Architecture And Maintainability

### Keep

- Strict base-app invariants: no network permission, Apache-2.0 ceiling, no
  closed-source blobs.
- Addon architecture for large, risky, native, model, voice, or network-adjacent
  features.
- Focused roadmap IDs and completion history.
- Build-time gates for manifest, data extraction, repo hygiene, and release
  metadata.
- Small logical commits with docs/changelog sync when implementing code.

### Improve

- Prefer behavior tests to source-string tests for critical data-safety paths.
- Reduce global lookups where identity should be explicit, especially MCP tools
  and imported content.
- Keep fork-specific constraints visible when cherry-picking upstream.
- Create reusable fixtures for addon trust, MCP daemon catalogs, SAF imported
  folders, and personal n-gram persistence.
- Treat Android API/page-size/edge-to-edge changes as recurring release
  readiness work, not one-off dependency bumps.

## Prioritized Roadmap

### P0 - Next Implementation Pass

1. Personal n-gram atomic replace.
   - Why: direct data-loss prevention.
   - Acceptance: failed replacement preserves existing live file; tests cover
     success and failure paths.
2. Personal n-gram token separator validation.
   - Why: prevents TSV corruption and delimiter ambiguity.
   - Acceptance: tab, newline, carriage return, NUL, and control separators
     cannot enter persisted learned tokens.
3. Personal n-gram stats/reset serialization.
   - Why: prevents stale learned-data counts after reset.
   - Acceptance: stats refresh cannot reload locales that reset just removed.

### P1 - Trust And Device Proof

1. MCP daemon-scoped dispatch.
   - Acceptance: duplicate tool names across daemons do not resolve by global
     first-match order.
2. Imported sticker SAF allow-list validation.
   - Acceptance: forged encoded sticker URIs are rejected; legitimate picked
     folders still serve.
3. Device proof pack for keyboard surface, backup/restore/import, accessibility,
   and reduced animation.
   - Acceptance: evidence added under existing QA/local verification docs.
4. Glide-trail baselines and low-end performance evidence.
   - Acceptance: Roborazzi baselines plus timing traces on low-end hardware.
5. FUTO public glide benchmark harness.
   - Acceptance: top-1/top-4 reporting against a pinned public subset.

### P2 - Product Polish And Ecosystem

1. Subtype switch-by-id nullable lookup.
2. Honeycomb layout parse diagnostics.
3. Migration recovery assistant for local import files.
4. Addon developer trust kit.
5. Targeted FlorisBoard alpha cherry-picks for numeric fields, scaling,
   sensitive clipboard deduplication, and RTL settings.
6. Release provenance: SBOM plus GitHub artifact attestation on release
   artifacts.

### P3 - Larger Strategic Bets

1. F-Droid reproducible submission after package-id decision.
2. Vosk or other local voice addon, keeping `RECORD_AUDIO` out of `:app`.
3. Apache-2.0-clean glide model trained/evaluated externally.
4. FunctionGemma or local model MCP addon.
5. Cross-platform dictionary export CLI.
6. Privacy evidence dashboard in Settings.

## Quick Wins

- Collapse `switchToSubtypeById()` to one nullable lookup.
- Add Honeycomb parse diagnostics while preserving fallback behavior.
- Add duplicate MCP tool-name tests before larger dispatch changes.
- Add source-shape validation for MCP tool names.
- Add tests for forged encoded sticker document URIs.
- Add a short "as of 2026-06-05" SwiftKey migration note to migration docs.
- Add a FUTO 0.1.29 benchmark note to the glide model roadmap row.
- Add a local verification checklist item for 16 KB page-size dependency/addon
  review.

## Larger Bets

### Public Glide Model Lane

The public FUTO dataset makes this realistic, but it should remain a larger bet:

- Build benchmark harness first.
- Train/evaluate outside `:app`.
- Verify license cleanliness.
- Keep model size and runtime budget explicit.
- Add UI policy for top-4 alternatives only after benchmark data supports it.

### Addon Ecosystem

Voice, handwriting, CJK, translation, local models, and MCP integrations can
make SwiftFloris feel larger than a keyboard fork without weakening `:app`.
That only works if the trust model is highly visible:

- Signed addon fixtures.
- Permission-separated docs and UI.
- Catalog validation.
- Strong failure modes when addon trust changes.

### F-Droid And Release Provenance

F-Droid and provenance are not pure engineering tasks because package identity,
signing keys, and maintainer process matter. They are still worth the effort:

- F-Droid validates the no-network FOSS audience.
- Reproducible builds validate release artifacts.
- Attestations make GitHub release provenance easier for users and reviewers to
  verify.

## Explicit Non-Goals

- Do not add `INTERNET`, network-state, or Wi-Fi-state permissions to `:app`.
- Do not add closed-source blobs to `:app`.
- Do not add voice recording permission to `:app`; voice belongs in handoff or a
  signed addon.
- Do not merge broad upstream FlorisBoard changes without fork-specific trust,
  privacy, and behavior review.
- Do not promise recovery of deleted SwiftKey account data.
- Do not ship benchmark claims without the dataset slice, device/emulator, app
  version, and metric definitions.
- Do not treat external addon permissions as base-app permissions.
- Do not use device-gated roadmap items as release evidence until hardware proof
  exists.

## Open Questions

1. What package-id strategy should SwiftFloris use for F-Droid coexistence with
   upstream FlorisBoard?
2. Should the next glide UI expose accepted word plus 3 alternatives if the
   benchmark shows top-4 value, or should that remain optional?
3. Which low-end reference devices are acceptable for glide and keyboard-surface
   evidence: Pixel 4a, Galaxy A12-class, Android Go-class, or a different
   maintained matrix?
4. What is the maintainer-approved signing and key-management plan for GPG tags,
   addon examples, and release provenance?
5. Should the migration assistant be a first-run card, a settings card, or both
   now that the SwiftKey account date has passed?
6. How much user-facing MCP should ship before the addon developer trust kit is
   complete?
7. Should the privacy evidence dashboard be implemented before or after F-Droid
   submission, so the F-Droid listing can point to a matching in-app surface?

## Recommended Next Commit Sequence

1. `fix: make personal n-gram persistence replacement atomic`
2. `fix: reject unsafe personal n-gram tokens before persistence`
3. `fix: serialize personal n-gram reset and stats refresh`
4. `fix: scope MCP tool dispatch by daemon`
5. `fix: validate imported sticker URIs against trusted SAF roots`
6. `test: add glide public benchmark harness`
7. `test: capture keyboard visual baselines`
8. `docs: document post-retirement SwiftKey migration paths`
9. `docs: add addon developer trust kit`
10. `ci: attach SBOM and provenance to release artifacts`

This sequence intentionally starts with correctness and trust before larger
feature expansion.
