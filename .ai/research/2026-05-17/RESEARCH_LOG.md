# Research Log — 2026-05-17

A running log of search strategies, tools used, source classes covered,
failed searches, and saturation notes for this autonomous research run.

---

## 1. Phases

### Phase 0 — Local recon (~30 min)

1. Listed repo root → discovered 80+ release notes, 340 KB ROADMAP.md, no
   AGENTS/CLAUDE files, no .ai/ or .claude/ folders.
2. `git status -uno`, `git log --oneline -30`, `git tag --sort=-creatordate`
   → confirmed HEAD v1.8.55 on `master`, 40 commits ahead of origin (per
   memory: push to SysAdminDoc/SwiftFloris fails 403 from this VM).
3. Module + source tree mapped via `find ... -type d` + `wc -l` on Kotlin
   files. 396 app/main + 159 app/test + 97 lib = 652 .kt files. Top-15
   largest sources captured.
4. Read [README.md](../../../README.md), [IMPROVEMENT_PLAN.md](../../../IMPROVEMENT_PLAN.md),
   [SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](../../../SWIFTKEY_PARITY_ROADMAP_2026-05-17.md),
   [RELEASE_NOTES_v1.8.55.md](../../../RELEASE_NOTES_v1.8.55.md),
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
| 3 | Dependency + security review (every pin) | ✅ | androidx-activity 1.13.0 is RC not stable; AGP 9.1.1 / Roborazzi 1.59.0 / Robolectric 4.16.1 all behind; KenLM is LGPL (not Apache-2.0-compatible — material ROADMAP correction); androidx-security-crypto dead-end; EU AI Act Article 50 due 2 Aug 2026 |

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
- `androidx-activity 1.13.0 stable` — RC vs stable status
- `AGP 9.1 R8 repackageclasses` — bump implications
- `Roborazzi 1.59 AGP 9` — visual-regression upgrade path
- `androidx-security-crypto deprecation 2025` — JetSec status
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
A second pass would only re-fetch the unverified dates above and check
whether AGP 9.1.1 vs 9.1.0 patch number is current. Not justified by
expected information yield.

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
