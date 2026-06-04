# SwiftFloris — TODO (single source of truth for open work)

**Current release:** v1.8.201 (versionCode 2001) · **Baseline:** `:app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` green.

This file is the **canonical open-work checklist.** It consolidates the open items
that were previously scattered across [`ROADMAP.md`](ROADMAP.md),
[`RESEARCH_FEATURE_PLAN_2026-05-25.md`](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md),
[`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md), and the research runs under
[`.ai/research/`](.ai/research/). Those files remain as **historical context and
sourced reasoning** — but the live "what needs doing" list is *here*.

- **Completed work** lives in [`CHANGELOG.md`](CHANGELOG.md) (one `## vX.Y.Z`
  section per release). When an item below ships, tick its box here and write the
  release section there. Do **not** re-expand a closed item in this file.
- **IDs** trace back to their origin: `F#`/`EI#` from `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`;
  `R#`/`O#` from `.ai/research/2026-05-25/SECOND_PASS_FINDINGS.md`; `WS#` from
  `IMPROVEMENT_PLAN.md` workstreams; `N#`/`Next-#`/`L#` from `ROADMAP.md` tiers.
- **Hard rules still apply** (see [`AGENTS.md`](AGENTS.md)): no `INTERNET` in
  `:app`; Apache-2.0 ceiling on `:app`; no closed-source blobs; one logical change
  per commit; bump `gradle.properties` version + write a `CHANGELOG.md` section +
  add a `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤500 chars,
  draft ≤480 for headroom) on every shipped release.

## Build / verify on this host

This working host **has** JDK 21 (Temurin) + Android SDK (platforms 34–36.1) +
Gradle 9.5.1. The historical "no gradle on this VM, defer to maintainer host" note
in `AGENTS.md`/`CLAUDE.md` does **not** apply here. To build/test:

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"   # env JAVA_HOME is stale; override it
./gradlew :app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`git push` to `origin` (`SysAdminDoc/SwiftFloris`) works from here (the old 403 was
the maintainer's other VM). Auto-commit-and-push per logical change.

---

## Tier A — implementable & locally verifiable (working queue)

### A1. Test coverage / correctness floor

- [x] **F18** (P1) — Heuristic `SmartComposeProvider` consuming the existing
  trigram/bigram/cold-start chain (replaces no-op `SmartComposeProvider.Default`).
  New `ime/smartcompose/HeuristicSmartComposeProvider.kt` + pref
  `correction.heuristicSmartCompose` (default off) + Settings toggle + registry
  wiring + JVM test. **Shipped v1.8.188 (2026-05-28).**
- [x] **F28 / O7** (P0) — `TinkStringPreferenceCrypto` round-trip + tamper test
  (encrypt→decrypt identity, AAD binding, tampered-ciphertext + cross-key
  failure, empty string). **Shipped v1.8.190 (2026-05-28)** via an `encodeEncrypted`
  /`decodeEncrypted` internal seam tested with a pure-JVM Tink AEAD.
- [x] **F27** (P1) — `KeyboardManager` SHIFT-key state machine extracted to a
  pure `ShiftStateMachine` + 10 JVM tests (double-tap, cycle, shift-up release
  rules). **Shipped v1.8.191 (2026-05-28).** Further dispatch surfaces remain
  candidates for the same extract-and-test pattern.
- [x] **R3** (P1) — Back-fill tests for v1.8.184 (malformed code point dropped
  without throwing). **Shipped v1.8.189 (2026-05-28).** The v1.8.183
  `TOGGLE_AUTOCORRECT` assertion is delivered with the F27 KeyboardManager
  harness (next), which it depends on.
- [x] **EI5** (P1) — `FlorisEmojiCompat` reflection-shape guard (loud `flogError`
  + graceful fallback) + `FlorisEmojiCompatReflectionGuardTest` CI sentinel that
  fails on an emoji2 constructor-shape bump. **Shipped v1.8.192 (2026-05-28).**
- [x] **O6** (P1) — Calendar permission privacy invariants test
  (`CalendarPermissionActivity` not exported + `READ_CALENDAR` declared).
  **Shipped v1.8.193 (2026-05-28).** Reframed: the activity *does* request on
  launch by design (it's launched only from the in-keyboard tap); the real
  invariant is that no third party can launch it.
- [x] **EI6** (P2) — Clipboard reconciliation property test (Kotest property):
  no dangling rows, no orphan files, single-pass convergence over randomised
  inputs. **Shipped v1.8.194 (2026-05-28).**
- [x] **F40 (test-class phase)** (P2) — Roborazzi screen-level test *classes* for
  `AddonsSettingsScreen`, `McpSettingsScreen`, `TypingStatsScreen`,
  `VoiceInputScreen`, `AiFeaturesScreen`, honeycomb keyboard surface, glide-trail
  themes. Existing `ThemeAndAddonsScreenshotTest` covers `AddonsSettingsScreen`;
  `PendingSettingsScreensScreenshotTest` and `PendingKeyboardSurfacesScreenshotTest`
  add the remaining compile-checked, baseline-pending capture targets. **Shipped
  v1.8.201 (2026-06-04).** (Baseline PNG *capture* remains Tier B — needs
  `recordRoborazzi` on device/emulator.)

### A2. Code cleanup / debt

- [x] **F38** (P2) — Removed the 3 dead `KeyboardMode` `@Deprecated` entries +
  their `LayoutManager` `when` arms; converted the misused `FlorisImeSizing`
  `ProvideKeyboardRowBaseHeight` `@Deprecated` to a KDoc/TODO (it's the current
  API). **Shipped v1.8.195 (2026-05-28).**
- [x] **F37** (P2) — `AdvancedProviders.kt` audited: engines already exist as pure
  `internal object`s targeted by the engine-named tests; providers are the
  stateful public classes. Architecture correct — added a clarifying doc note.
  **Shipped v1.8.196 (2026-05-28).**
- [x] **F39** (P2) — `DictionaryManager.kt` logging audit: 9 `Log.*` calls (no
  `@Suppress` found) routed through `flog*` under a new `LogTopic.DICTIONARY`;
  critical paths promoted to `flogError`. **Shipped v1.8.197 (2026-05-28).**
- [ ] **EI1** (P2) — Partition `AppPrefs.kt` (~1,301 LOC) by feature area
  (`app/prefs/*Prefs.kt`), re-exporting the merged `AppPrefs`. Golden test: same key
  set + defaults before/after. Datastore keys MUST NOT change.
- [ ] **WS14** — Replace remaining deprecated synchronous toast calls in Compose
  screens where a coroutine scope is available; review the Room nullable-DAO warning
  (fix or document); prune stale Kotlin compiler flags only when confirmed safe.

### A3. Settings / UX surfaces

- [x] **F14** (P2) — Settings → About → inline "What's new" excerpt
  (`BuildConfig.WHATS_NEW` from the matching `CHANGELOG.md` section at build time;
  scrollable dialog + "Full changelog" link). **Shipped v1.8.198 (2026-05-28).**
  (Privacy-posture comparison table deferred — separate doc, lower value.)
- [x] **EI12** (P2) — Settings → Typing stats → "Erase everything, including
  dictionary": confirmed combined wipe of all 4 learning stores + the personal
  dictionary (DB reset + overlay clear), behind a `JetPrefAlertDialog`. The gentler
  dictionary-preserving "Reset all typing learning" stays. **Shipped v1.8.199.**
- [x] **F7** (P1) — Settings → "Local audit log" screen over the existing
  `AddonInvocationAudit` ring (summary + recent-activity list + copy-as-JSON +
  clear). Display-only; records are PII-safe by construction (no redaction needed).
  **Shipped v1.8.200 (2026-05-28).**
- [ ] **EI3** (P2) — Personal dictionary bulk-import preview (first ~50 entries +
  total + exclude-row checkboxes) before commit; "Skip preview" opt-out.
- [ ] **F31** (P2) — Per-app language — **NEEDS REFRAME (do not implement as specced).**
  `LocaleManager.getApplicationLocales(packageName)` for *another* app requires the
  privileged `READ_APP_SPECIFIC_LOCALES` permission — not grantable to a normal IME,
  and it conflicts with the clean-permission posture. Reframe as permission-free
  **"remember keyboard language per app"**: on manual subtype switch, record
  `editorPackage → subtypeId`; on `onStartInputView`, auto-restore the remembered
  subtype for the focused package. Opt-in (default off). Touches `ime/core/`
  (a small persisted map + a pure `PerAppSubtypeMemory` to unit-test), a new
  `PerAppLanguageScreen`, an `AppPrefs.localization` pref, and the
  `FlorisImeService.onStartInputView` hook (~line 569-587).
- [ ] **F29** (P1) — Build out `PhysicalKeyboardScreen` to expose the shipped Mac
  `.keylayout` / Keyman `.kmp` / KLC parsers + `HardwareKeyboardRuntimeMapper`
  (custom-layout picker + Import button).
- [ ] **EI2** (P1→P3, low value) — Settings home is **already** grouped into four
  labelled sections (Essentials / Experience / Data / System) in `HomeScreen.kt` —
  the research's "15 sub-screens at one level" premise is stale. Remaining value is a
  cosmetic re-bucket into the 5 research groups; do only as polish. The new F7 audit-log
  entry already lives in the Data section.
- [ ] **F4** (P1) — Settings → Search (index builder + scroll-and-highlight per screen).
- [ ] **EI7** (P1) — Voice route empty state explains "what is FUTO" with an Install
  link (drop the migration-window framing; ship as a generic recommendation surface).
- [ ] **F3** (P0→reframed) — First-run "import from any keyboard" hint: detect
  SwiftKey JSON + Gboard XML + FlorisBoard CSV/.flbackup/.fldic in a SAF picker and
  route to the existing `DictionaryImporter`. (Migration window closed 2026-05-31;
  the discovery value is permanent.)
- [ ] **F6** (P2) — Per-app accent opt-in discovery hint + Settings preview (single-fire).
- [x] **O5** (P1) — Audited: `SyncSettingsScreen.kt` (443 LOC) **already exists**, is
  registered in `Routes.kt` (`@Deeplink settings/sync`), and is reachable from
  `HomeScreen`. The second-pass "screen missing" finding was **incorrect** — the
  ROADMAP §10.5 Next-5.3a "shipped" claim is right. No code change needed (verified 2026-05-28).

### A4. CI / build / release hardening

- [ ] **F23** (P1) — Chain reproducible-build verification to the release-tag flow
  (`workflow_call` from `release.yml`, or block tag publish until repro is green).
- [ ] **F24** (P1) — `:app:verifyRoborazziRelease` (R8/minify can rename Compose
  semantics nodes; nothing catches it today).
- [ ] **EI9** (P2) — Macrobenchmark trend-regression `workflow_dispatch` job diffing
  against `docs/benchmark-results/baseline-*.json`; floor/target ranges in `docs/BENCHMARKS.md`.

### A5. Docs / hygiene

- [ ] **R5** (P3) — Fastlane-changelog drafting guide (draft ≤480 chars) in
  `docs/LOCAL_VERIFICATION.md` / `docs/REPO_HYGIENE.md`.
- [ ] **O1** (P3) — Document `lib/<module>/build/` cache survival across
  `git rm --cached` in `docs/REPO_HYGIENE.md`.
- [ ] **EI10** (P2) — Confirm no `app/lint-baseline.xml` exists (research says it
  doesn't); add a one-line note / close. `bash scripts/run-lint-debug-with-baseline-check.sh` exits 0.
- [ ] **WS10 / WS15** — Visual-QA checklist (portrait/landscape/compact/floating/dark/
  high-font-scale) + manual-QA + release-evidence checklist docs.
- [ ] **WS12** — Localization content quality: review Turkish repeated-word lint with a
  native-safe approach; tighten vague/abrupt English source labels; standardize
  backup/restore/import/export failure + destructive-confirmation copy; document
  translation-safe cleanup rules.

---

## Tier B — needs a physical device / manual QA (cannot fully close on this host)

- [ ] **F9** (P1) — Glide-trail theme Roborazzi baselines + low-end (≤4 GB) perf
  evidence (Pixel 4a / Galaxy A12-class). Trace `swiftfloris.glide.trailDrawMs`.
- [ ] **F40 (capture phase)** — `:app:recordRoborazziDebug` to produce baseline PNGs
  for the A1 test classes, then remove the class-level `@Ignore` annotations from
  the pending F40 screenshot classes (run on a device/emulator).
- [ ] **WS11** (P0/P1) — Keyboard surface polish: candidate-row selection/pressed/
  disabled/correction states; smartbar ordering + overflow + long-label resilience;
  software-key pressed/held/disabled/gesture states; one-handed/floating/split/
  compact/landscape/tablet layouts; manual override verification in real fields.
- [ ] **WS13 (device portions)** — backup/restore overwrite-vs-merge, clipboard
  media missing-file/path-safety, extension-import path-traversal — the audits +
  unit tests are Tier A; the on-device confirmation is Tier B.
- [ ] **EI4 (residual)** — Verify Rainbow/Aurora/Neon glide trails honour
  `ANIMATOR_DURATION_SCALE == 0f` on-device + add the GesturesScreen "ⓘ" tooltip.
  (Doc disclosure already shipped v1.8.182.)

---

## Tier C — external-action-blocked / sibling-repo / XL (need a maintainer decision)

These are **genuine blockers**, not scope dodges — each needs an account, key,
sibling repo, ML infra, or a product decision the code can't make.

- [ ] **R1** (P0) — Crowdin sync of the v1.8.179 + v1.8.186 string drops (44 stale
  translated entries / 22 locales). *Blocker:* Crowdin web console is source of
  truth; needs a server-side sync/pull. (Lint `UnusedResources` until done.)
- [ ] **F8** (P2) — Bundled Vosk small-en-us recogniser. *Blocker:* sibling addon
  repo + JNI; `RECORD_AUDIO` only in the addon, never `:app`.
- [ ] **F10** (P2) — CycloneDX SBOM + SLSA provenance on release. *Blocker:* GitHub
  Attestations onboarding + release-tag dispatch.
- [ ] **F11** (P2) — GPG-signed release tags. *Blocker:* maintainer GPG key.
- [ ] **F12** (P2) — F-Droid `fdroiddata` submission. *Blocker:* `dev.patrickgold.
  florisboard(.beta)` package-id collision with upstream — needs a rename/coexistence
  decision (Open Question 1) + multi-month review queue.
- [ ] **F13** (P3) — Cross-platform desktop dictionary-export CLI. *Blocker:* sibling repo.
- [ ] **F21** (P1) — Apache-2.0 glide model trained on the MIT FUTO swipe dataset.
  *Blocker:* off-device ML training infra (XL, out-of-tree).
- [ ] **F30** (P2) — FunctionGemma 270M MCP-bridge addon. *Blocker:* sibling addon repo.
- [ ] **F22** (P1) — FlorisBoard `0.6.0-alpha02` cherry-picks (CLDR 48, Emoji 17,
  number-field fix, floating-window foundation). *Blocker:* conflict resolution needs
  iterative on-device builds; risk of regressing shipped features — needs a focused pass.

### Open questions blocking Tier C (maintainer decisions)

1. F-Droid package-id: coexist with upstream FlorisBoard `…beta` or rename? (blocks F12)
2. Vosk 40 MB addon in 2026, or voice stays FUTO-handoff-only? (affects F8 + EI7 copy)
3. Maintainer GPG key (Yubikey-backed?) for signed tags? (affects F11)
4. F-Droid submission timing — during migration spike or a quiet week? (affects F12)

---

## Recently closed (see `CHANGELOG.md` for detail)

v1.8.201 closed (autonomous session 2026-06-04): **F40 test-class phase**
(baseline-pending Roborazzi classes for the remaining Settings and keyboard/glide
surfaces; capture phase remains Tier B).

v1.8.188→v1.8.200 closed (autonomous session 2026-05-28): **F18** (heuristic
ghost-text), **R3** (malformed-codepoint test), **F28/O7** (Tink crypto test),
**F27** (ShiftStateMachine + tests), **EI5** (EmojiCompat reflection guard),
**O6** (calendar-permission test), **EI6** (clipboard reconciliation property
test), **F38** (dead KeyboardMode removal), **F37** (AdvancedProviders audit),
**F39** (DictionaryManager flog), **F14** (What's-new excerpt), **EI12** (erase-
everything), **F7** (local audit log), **O5** (verified Sync screen exists).

v1.8.174→v1.8.187 closed: **F1, F2, F15, F16, F17, F19, F20, F25, F26, F32, F34,
F35, F36, F41, F42, EI4 (doc), EI8, EI11** + rejected F33. IMPROVEMENT_PLAN
Workstreams 1, 3, 4, 5, 6 are complete; Workstream 2 (lint) is monotonically
decreasing. Full per-release detail is in `CHANGELOG.md`.

**Open Tier A queue (next pass):** F31 (reframed — see note), EI3 (import
preview — touches the rollback flow, handle carefully), F29 (PhysicalKeyboardScreen
build-out), F4 (Settings search), EI1 (AppPrefs partition — golden-test guarded,
risky), CI items (F23/F24/EI9), UX/strings (EI7/F3/F6),
hygiene/docs (WS14 incl. the stale `-Xwhen-guards` flag, R5/O1/EI10, WS10/WS15),
EI2 (low-value cosmetic). Tier C stays blocked (see §Tier C + open questions).
