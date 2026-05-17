# Research Log — 2026-05-17

A running log of search strategies, tools used, source classes covered,
failed searches, and saturation notes for this autonomous research run.

---

## 1. Phases

### Phase 0 — Local recon (~30 min)

1. Listed repo root → discovered 80+ release notes, 340 KB ROADMAP.md, no
   AGENTS/CLAUDE files, no .ai/ or .claude/ folders.
2. `git status -uno`, `git log --oneline -30`, `git tag --sort=-creatordate`
   → initial pass confirmed HEAD v1.8.55 on `master`, 40 commits ahead of
   origin. Later same-day passes observed HEAD v1.8.58, 47 commits ahead.
3. Module + source tree mapped via `find ... -type d` + `wc -l` on Kotlin
   files. 396 app/main + 159 app/test + 97 lib = 652 .kt files. Top-15
   largest sources captured.
4. Read [README.md](../../../README.md), [IMPROVEMENT_PLAN.md](../../../IMPROVEMENT_PLAN.md),
   [SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](../../../SWIFTKEY_PARITY_ROADMAP_2026-05-17.md),
   [RELEASE_NOTES_v1.8.55.md](../../../RELEASE_NOTES_v1.8.55.md)
   (later passes also reconciled v1.8.56-v1.8.58),
   [app/build.gradle.kts](../../../app/build.gradle.kts),
   [gradle.properties](../../../gradle.properties),
   [gradle/libs.versions.toml](../../../gradle/libs.versions.toml),
   [app/src/main/AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml).
5. ROADMAP.md sampled by section (Read tool 25 K-token cap → individual
   sections 1, 2, 3 sample, 4, 5, 6 sample, 7 sample, 8 sample, 11–16,
   appendix). Section headings extracted via Grep.
6. TODO/FIXME inventory via Grep — 37 markers across 24 files; no `TODO()`
   crash stubs in production paths.

### Phase 1 — External research dispatch (parallel agents)

Three `general-purpose` Agent calls launched in parallel:

| Agent | Topic | Status | Notable findings |
|---|---|---|---|
| 1 | FlorisBoard upstream + HeliBoard NLnet + LiteRT-LM | ✅ | FlorisBoard frozen on v0.6.0-alpha02 since 2025-01-23; HeliBoard NLnet library not yet released ~14 days before deadline; MediaPipe LLM Inference deprecated; FunctionGemma 270M shipped Jan 2026 |
| 2 | Competitor matrix (14 OSS + 8 commercial + adjacent) | ✅ | CleverKeys neural-swipe shipping on F-Droid; Samsung Galaxy AI decoupled from Samsung Keyboard; Grammarly Android keyboard discontinued; FUTO v0.1.28 added CJK |
| 3 | Dependency + security review (every pin) | ✅ | Initial pass found AGP / Roborazzi / Robolectric drift, KenLM LGPL incompatibility, AndroidX Security Crypto migration need, and EU AI Act Article 50 due 2 Aug 2026. Fifth pass corrected the Activity and dependency-target details: Activity 1.13.0 is stable, AGP target is 9.2.x, Roborazzi target is 1.60.0, KSP target is 2.3.8. |

All three agents finished within ~4 minutes of dispatch, in parallel.

### Phase 2 — Synthesis and write-out

While agents ran:

1. Wrote [STATE_OF_REPO.md](STATE_OF_REPO.md) (local reconnaissance).
2. Wrote [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) (file inventory + conflict resolution).

After agents returned:

3. Wrote [COMPETITOR_MATRIX.md](COMPETITOR_MATRIX.md).
4. Wrote [SOURCE_REGISTER.md](SOURCE_REGISTER.md).
5. Wrote this log.
6. Will write [SECURITY_AND_DEPENDENCY_REVIEW.md](SECURITY_AND_DEPENDENCY_REVIEW.md),
   [DATASET_MODEL_INTEGRATION_REVIEW.md](DATASET_MODEL_INTEGRATION_REVIEW.md),
   [FEATURE_BACKLOG.md](FEATURE_BACKLOG.md),
   [PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md),
   [`PROJECT_CONTEXT.md`](../../../PROJECT_CONTEXT.md),
   [`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md),
   [CHANGESET_SUMMARY.md](CHANGESET_SUMMARY.md).

### Phase 3 — Same-day fifth-pass correction

The fifth pass re-opened the drift-prone sources most likely to have
changed or been misread: AndroidX Activity, AndroidX Security Crypto,
Google Maven metadata for AGP / Compose / Activity, Maven Central
metadata for KSP / Roborazzi / Robolectric / coroutines /
AboutLibraries / ZXing / Tink, and current competitor release pages.

Corrections recorded in
[FIFTH_PASS_FINDINGS.md](FIFTH_PASS_FINDINGS.md):

- Activity 1.13.0 is stable; remove the downgrade recommendation.
- Security Crypto 1.1.0 exists, but APIs are deprecated; keep the Tink /
  Android Keystore migration for the right reason.
- Dependency batches update to AGP 9.2.x, KSP 2.3.8, Roborazzi 1.60.0,
  Robolectric 4.16.1, Compose BOM 2026.05.00, coroutines 1.11.0,
  AboutLibraries 14.2.0, ZXing 3.5.4, and Tink Android 1.21.0.
- LeanType was added to the competitor matrix as an active HeliBoard fork
  with Standard / Offline / Offline Lite APK lines.

---

## 2. Search strategies used

### 2.1 Targeted queries (external agents)

- `"FlorisBoard" "v0.6" 2026` — confirm upstream activity
- `"HeliBoard" "NLnet" gesture data` — verify NLnet status
- `"LiteRT-LM" Gemma Android 2026` — current AI runtime
- `"FunctionGemma" 270M HuggingFace` — function-calling variant
- `Bergamot translator Android 2026` — offline NMT alternatives
- `data.swiftkey.com export JSON 2026-05-31` — migration cutoff
- `Android 17 IME InputMethodService API 37` — platform changes
- `CleverKeys ONNX glide` — neural-swipe state of art
- `FUTO keyboard v0.1.28 Chinese Pinyin` — competitor feature drop
- `Samsung One UI 7 Galaxy AI keyboard` — decoupling confirmation
- `Grammarly Android keyboard discontinued` — market exit
- `Samsung One UI 7 Galaxy AI writing tools any keyboard` — v1.8.70 README
  callout source refresh
- `Grammarly Android keyboard discontinued official support` — v1.8.70 README
  callout source refresh
- `androidx-activity 1.13.0 stable` — RC vs stable status
- `AGP 9.1 R8 repackageclasses` — bump implications
- `Roborazzi 1.59 AGP 9` — visual-regression upgrade path
- `Roborazzi 1.60.0 Maven metadata Robolectric 4.16.1 Maven metadata OSV` —
  v1.8.71 Bump-batch B source refresh
- `androidx-security-crypto deprecation 2025` — JetSec status
- `LeanBitLab LeanType v3.7.9 offline ONNX keyboard` — fifth-pass
  competitor addition
- `SQLCipher 4.16 LibTomCrypt deprecation` — crypto provider drift
- `KenLM license LGPL Apache-2.0 compatible` — license verification
- `EU AI Act Article 50 transparency 2026` — regulatory cutoff
- `F-Droid Reproducible Builds verified 2026` — distribution path

### 2.2 Patterns followed

- For each pin in `libs.versions.toml`, the dep-research agent searched
  the canonical release page and the latest stable. This is the
  "saturation by enumeration" pattern for a finite set.
- For each competitor, the matrix agent fetched the repo + releases page,
  then cross-checked with one secondary source (F-Droid listing or press
  coverage) where a date was ambiguous. This is the
  "saturation by triangulation" pattern.
- For the upstream agent, the strategy was depth-first into the four
  open issues with the highest engagement, plus the NLnet project page
  itself.

### 2.3 What did not work

- **GitHub issue-reaction counts beyond ~20** for FlorisBoard 2025-2026
  open issues could not be cleanly enumerated via search-result snippets
  alone; the upstream agent flagged this as unverified.
- **HeliBoard v3.9 release year (2025 vs 2026)** returned inconsistent
  dates across page fetches; treated as 2026-03-29 because v3.7
  (2026-02-22) is independently referenced in
  `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` (line 291).
- **Sayboard release year** — page returned "23 Aug" without explicit
  year; treated as 2024 based on adjacent context.
- **Tap Strap Android IME activity** — `ScribbleJ/TapStrapApp` activity
  for 2026 not verifiable in this pass; left as "unverified."
- **MTP (multi-token prediction) public API on LiteRT-LM** — claimed in
  ROADMAP §C.3; no public API doc surfaced in this pass. Marked as
  "say so explicitly" by the upstream agent.

### 2.4 Saturation signal

Saturation across the three external passes is reached when:

- Every pinned dep in `libs.versions.toml` has been checked against a
  canonical release page → dep agent achieved this.
- Every direct OSS competitor with ≥1k stars has been opened and read →
  competitor agent achieved this.
- The two single-most-important external timelines (HeliBoard NLnet
  2026-06-01; SwiftKey cutoff 2026-05-31) have triangulated dates →
  upstream agent achieved this.

For one-pass research against a heavily-sourced project, this is enough.
A fifth pass did re-fetch those drift-prone dependency targets. The
remaining saturation signal is now stronger: new findings were corrective
or one-competitor additions, not a new roadmap pillar.

---

## 3. Tools / techniques used

| Tool | When |
|---|---|
| `Read` | Targeted file reads (README, ROADMAP sections, build files) |
| `Grep` | Section-heading extraction, TODO/FIXME inventory, content search |
| `Glob` | Directory tree mapping |
| `Bash` (POSIX) | `git status`, `find`, `wc -l`, `git tag`, `git log` |
| `PowerShell` | Initial directory listing (Windows-native) |
| `Agent` (general-purpose, ×3) | Parallel external research |
| `WebSearch` / `WebFetch` | (Inside agents only) — current-state verification |
| `Write` | All research-run artifacts under `.ai/research/2026-05-17/` |
| `TodoWrite` | Progress tracking across the multi-step plan |

---

## 4. Limits / known gaps in this pass

- **v1.8.72 continuation re-check:** Before shipping the HeliBoard / NLnet
  slip-base-case roadmap correction, the autonomous loop re-opened the NLnet
  Gesture Typing page, HeliBoard `#2226`, HeliBoard releases, and the
  gesture-data contribution wiki. A GitHub API check reported latest release
  `v3.9` (`published_at=2026-03-29T10:21:58Z`) and issue `#2226` still open
  (`updated_at=2026-05-11T18:48:16Z`). This confirmed the planning change:
  `swiftfloris-statistical` is the production glide path; HeliBoard open-glide
  is additive until a permissive library and dataset actually land.
- **v1.8.73 continuation repo-hygiene check:** Root scan found five ignored
  local JVM crash/replay logs (`hs_err_pid15604.log`, `hs_err_pid4860.log`,
  `replay_pid11584.log`, `replay_pid15604.log`, `replay_pid4860.log`). They
  were moved to `.ai/local-crash-logs/2026-05-16/`, and a tracked CI guard was
  added so force-added root logs fail future pushes / PRs.
- **v1.8.74 continuation dependency metadata check:** The autonomous loop
  re-fetched Google Maven metadata for AGP and Compose BOM, checked Android
  Studio Panda 4 Patch 1 notes, opened the AGP 9.2 release notes and Compose
  release stream, ran OSV querybatch for the two target coordinates, and
  audited active R8 `-keepattributes` rules before shipping Bump-batch C. This
  confirmed AGP `9.2.1` and Compose BOM `2026.05.00` as stable targets while
  leaving AGP `9.3.0-alpha05` on the preview side of the line.
- **Tier-2 matrix reconciliation after v1.8.74:** Rows #17-#23 and #25 were
  compared against `ROADMAP.md`, `PROJECT_CONTEXT.md`, and the top-level
  reconciliation table in `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`. Code work
  had already shipped in v1.8.56-v1.8.67, so the matrix/backlog/addendum were
  updated instead of reimplementing those slices.
- **Tier-2 #24/#26 continuation:** `:app:lintDebug` was attempted for the lint
  baseline refresh and failed at the known missing-Java host blocker. The loop
  then moved to #26 and applied superseded-document banners to all five
  SwiftKey files named by `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`.
- **v1.8.75 / Tier-2 #27 implementation:** Next-6.4a was implemented by reading
  the existing `HardwareKeyboardLayout`, `KlcLayoutParser`, `KeymanLdmlParser`,
  and their tests, then adding a matching XXE-hardened macOS `.keylayout`
  parser plus focused Kotest coverage. Gradle verification was attempted and
  remains blocked by missing `JAVA_HOME` / `java` on PATH.
- **v1.8.76 / Tier-2 #28 implementation:** Next-6.4b was implemented by adding
  `HardwareKeyboardRuntimeMapper`, pure event-info tests for KLC/macOS/source
  fallbacks, and a `KeyboardManager.onHardwareKeyDown(...)` dispatch hook that
  checks mapped printable keys before built-in Space / Enter / Shift handling.
- **v1.8.77 / Tier-3 #29 implementation:** Next-9.5 user-imported sticker
  folders were implemented by adding `prefs.sticker.userFolderUri`,
  `UserStickerRepository`, Settings → Emoji & stickers SAF picker / clear
  action, imported-pack preview decoding in `StickerPaletteView`, and
  `StickerMediaProvider` proxy reads for user sticker documents. Focused Gradle
  verification was attempted and stopped at the known VM blocker (`JAVA_HOME` /
  `java` missing).
- ROADMAP.md was sampled by section, not read end-to-end (340 KB exceeds
  the 25 K-token Read cap). The companion
  [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) records what could
  and could not be reconciled.
- Several `ime/` subsystems (mcp, voice, smartcompose, sync) were not
  opened beyond their directory listings; the L7 MCP-bridge claim is
  reconciled "⚠️ unverified" in MEMORY_CONSOLIDATION.md §2.6.
- Native code (`libnative/`) is a `dummy/` placeholder and was not
  inspected beyond confirming the placeholder shape.
- `app/lint.xml` baseline was not opened; the lint counter in
  IMPROVEMENT_PLAN.md is from 2026-05-05 and likely drifted.
- The 80 RELEASE_NOTES files were sampled by reading v1.8.55 and quoting
  v1.8.52 from ROADMAP §0/§3. Full per-release inventory was not built;
  the per-release file pattern itself is the inventory.
- No external research was performed on academic / paper citations beyond
  the three already in ROADMAP Appendix (PMC, ACM CHI 2013, Trinity
  Gboard). These are not roadmap-load-bearing for the next slice.
