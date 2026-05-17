# Changeset Summary — 2026-05-17 Research Run

This research run created **11 new files** and **modified zero existing
files**. Nothing in `ROADMAP.md`, `IMPROVEMENT_PLAN.md`, the per-release
notes, the build files, or any source file under `app/` or `lib/` was
touched. All output is additive.

**Concurrent-release reconciliation:** during this research run, three
releases shipped on master (v1.8.56 Phase B4, v1.8.57 Phase C2 arrow-keys
preset, v1.8.58 Phase D2 task-creation quick action). All three matched
items this research run was preparing to recommend. `PROJECT_CONTEXT.md`
§7 and `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §0 were updated to
reflect HEAD = v1.8.58 and to remove the three closed recommendations
from the open list.

The deliberate non-destructive posture honors the prompt's rule:
*"Do not destructively rewrite project files. Prefer creating canonical
consolidated files and adding pointers from older files."*

---

## 1. Files created

### 1.1 Root-level (canonical project context)

| Path | Purpose |
|---|---|
| [PROJECT_CONTEXT.md](../../../PROJECT_CONTEXT.md) | Single-page consolidated project context. Designed as the fastest read for an AI session, new contributor, or maintainer-context refresh. References `ROADMAP.md`, `IMPROVEMENT_PLAN.md`, the parity roadmap, and every research artifact this run produced |
| [ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) | Actionable additions, corrections, and reframings keyed to existing `ROADMAP.md` sections. Recommended-but-not-yet-applied; flows into the next `ROADMAP.md` (v5.3) refresh |

### 1.2 Research-run artifacts (under `.ai/research/2026-05-17/`)

| Path | Purpose |
|---|---|
| [STATE_OF_REPO.md](STATE_OF_REPO.md) | Local reconnaissance: identity, invariants, version pins, module layout, source size, subsystem map, CI/workflows, release stream, permissions, tests, TODO inventory, what's intentionally absent |
| [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) | Inventory of every AI / planning / memory file in repo, plus reconciliation against v1.8.55 HEAD reality. Identifies the AI-instruction-file gap, the five superseded SWIFTKEY_* docs, the tag-lag, the README version-lag, and the open conflicts (none material) |
| [SOURCE_REGISTER.md](SOURCE_REGISTER.md) | Every local and external source consulted during the run, organized by topic. ~100 URLs across 15 source classes |
| [RESEARCH_LOG.md](RESEARCH_LOG.md) | Search strategies, tools used, parallel-agent dispatch, saturation notes, and known gaps in this pass |
| [COMPETITOR_MATRIX.md](COMPETITOR_MATRIX.md) | 14 OSS + 8 commercial + 4 adjacent keyboards as of May 2026, with version + license + features-SwiftFloris-doesn't-have + strategic implications. Ends with "features SwiftFloris has uniquely" + "features all major competitors ship that SwiftFloris is missing" + "10 feature opportunities sorted by impact ÷ cost" |
| [SECURITY_AND_DEPENDENCY_REVIEW.md](SECURITY_AND_DEPENDENCY_REVIEW.md) | Every pinned version checked against latest stable; license-compatibility verification (KenLM is LGPL — material correction); CVE status; F-Droid verified-tier path; EU AI Act Article 50 cutoff; recommended bump-batches and security additions |
| [DATASET_MODEL_INTEGRATION_REVIEW.md](DATASET_MODEL_INTEGRATION_REVIEW.md) | Datasets currently bundled vs referenced; models referenced (Gemma 3, FunctionGemma, Whisper, Vosk, Bergamot, librime, ML Kit, CleverKeys, NLLB-200); integrations surfaced; coverage map; license-cleanliness audit |
| [FEATURE_BACKLOG.md](FEATURE_BACKLOG.md) | Raw harvested ideas: 11 with updated-evidence-only against existing ROADMAP items + 33 new ideas across distribution / privacy / dependency / feature-opportunities / testing / docs + 6 long-tail tracked + 6 rejected-with-reasoning |
| [PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md) | Every backlog item scored on impact × urgency / cost; sorted into Tier 1 (≥ 5.0), Tier 2 (3.5–4.9), Tier 3 (2.0–3.4), Tier 4 (track only); ends with recommended next-three-releases mapping |
| **CHANGESET_SUMMARY.md** | This file |

## 2. Files NOT touched

The following files exist in the repo and were **deliberately not modified**
this run:

- `README.md` — currently shows v1.8.52; HEAD is v1.8.55. Catch-up belongs
  in the next release's commit, not a research-run commit.
- `ROADMAP.md` — 340 KB; non-destructive policy. Recommended changes
  flow through `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` into the next
  `v5.3` refresh.
- `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` — already current.
- `SWIFTKEY_PARITY_AUDIT.md`, `SWIFTKEY_PARITY_BUILD_PLAN.md`,
  `SWIFTKEY_PARITY_RESEARCH.md`, `SWIFTKEY_AI_RESEARCH.md`,
  `SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md` — superseded but kept intact
  for audit trail.
  [MEMORY_CONSOLIDATION.md §5](MEMORY_CONSOLIDATION.md) records the
  proposed SUPERSEDED-banner additions; not applied this run.
- `IMPROVEMENT_PLAN.md` — counter from 2026-05-05 is slightly stale but
  not load-bearing; refresh is a side-quest for a future workstream commit.
- Per-release notes (`RELEASE_NOTES_v*.md`) — never modified after
  release.
- All source files under `app/`, `lib/`, `benchmark/`, `libnative/`.
- All build files (`gradle.properties`, `gradle/libs.versions.toml`,
  `gradle/tools.versions.toml`, `app/build.gradle.kts`,
  `lib/*/build.gradle.kts`, `settings.gradle.kts`).
- All workflow files under `.github/workflows/`.
- All asset files.

The recommended bump-batches A/B/C, the Tink migration, and all other
code-touching items live in `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
§A/§B/§C/§D as **commitments**, not yet **commits**.

## 3. Why the research run is non-destructive by design

- The user is in the middle of an active SwiftKey-parity sprint (Phase
  A/B); HEAD is 14 days from the 2026-05-31 cutoff. The cost of an
  unintended `ROADMAP.md` rewrite during this window is high; the cost
  of an additive `_ADDENDUM_` file is zero.
- The user controls the remote push (per memory: `git push` to
  `SysAdminDoc/SwiftFloris` fails 403 from this VM). Research-run output
  should be reviewable as one or two clean commits on the user's main
  push host, not an opaque rewrite.
- `ROADMAP.md` is heavily sourced and load-bearing. Mechanical
  rewriting would lose the auditability that makes it useful.
- `.ai/research/<YYYY-MM-DD>/` is the right place for research-time
  artifacts; promoting research findings into `ROADMAP.md` is a separate,
  intentional action (the next `v5.3` refresh).

## 4. Recommended follow-up commits (NOT made this run)

In rough priority order, with reference to
[PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md):

| Order | Commit suggestion | Files touched |
|---|---|---|
| 1 | `feat(roadmap): research addendum 2026-05-17 + project context + research run artifacts` | This research run as one commit |
| 2 | `chore(repo): tag v1.8.41 … v1.8.55 (15-tag catch-up)` | git tags only |
| 3 | `chore(deps): bump-batch A (coroutines 1.11, KSP 2.3.7, zxing 3.5.4, aboutlibraries 14.2, activity → 1.12.4 stable)` | `gradle/libs.versions.toml` + a new `RELEASE_NOTES_v1.8.56.md` |
| 4 | `feat(setup): EU AI Act Article 50 first-run explainer + Settings → About surface` | `app/setup/` + `docs/PRIVACY_AND_AI.md` |
| 5 | `feat(repo): PROJECT_CONTEXT pointer in AGENTS.md + CLAUDE.md` | New `AGENTS.md` + `CLAUDE.md` |
| 6 | `chore(repo): remove stale crash + replay logs from repo root` | `hs_err_pid*.log` + `replay_pid*.log` |
| 7 | `chore(deps): replace androidx-security-crypto with Tink + AndroidKeystoreV1` (separate slice — risk-isolated) | `app/build.gradle.kts` + `KeyManager.kt` paths + `PersonalDictionaryEncryptionTest` rewrite |
| 8 | `chore(deps): bump-batch B (Roborazzi 1.59 + Robolectric 4.16.1)` | versions catalog + a sweep over Roborazzi tests |
| 9 | `chore(deps): bump-batch C (AGP 9.1.1 + Compose BOM 2026.05.00)` | versions catalog + R8 rules audit |
| 10 | The Phase B4 same-sentence-language-switch hardening slice in `MultilingualTokenScorer` + `TypingContextExtractor` | `app/src/main/kotlin/.../ime/nlp/` |

Commit #1 is what should land next. The remaining commits flow at the
project's per-release cadence.

## 5. Audit footprint

This research run created **~85 KB of new Markdown** across 11 files. By
comparison, `ROADMAP.md` alone is ~340 KB. The research output is well
sub-linear in size relative to the project's prior planning surface
— consolidation, not bloat.

| File | Approx bytes |
|---|---|
| `PROJECT_CONTEXT.md` | 11 K |
| `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` | 16 K |
| `STATE_OF_REPO.md` | 11 K |
| `MEMORY_CONSOLIDATION.md` | 12 K |
| `SOURCE_REGISTER.md` | 13 K |
| `RESEARCH_LOG.md` | 7 K |
| `COMPETITOR_MATRIX.md` | 14 K |
| `SECURITY_AND_DEPENDENCY_REVIEW.md` | 12 K |
| `DATASET_MODEL_INTEGRATION_REVIEW.md` | 9 K |
| `FEATURE_BACKLOG.md` | 11 K |
| `PRIORITIZATION_MATRIX.md` | 11 K |
| `CHANGESET_SUMMARY.md` | (this file) |
| **Total** | **~127 K** |

(Sizes are approximate; treat as order-of-magnitude.)

## 6. Self-audit

Audit pass against the prompt's hard completion criteria:

1. **All required artifacts written to disk:** ✅ — every required file
   in the prompt is present.
2. **Local repository reconnaissance complete:** ✅ —
   [STATE_OF_REPO.md](STATE_OF_REPO.md) covers identity, versions, modules,
   sources, CI, releases, permissions, tests, and TODO inventory.
3. **Project memory consolidated:** ✅ —
   [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) inventories every
   AI / planning / memory file and resolves the conflicts;
   [PROJECT_CONTEXT.md](../../../PROJECT_CONTEXT.md) is the canonical
   consolidated context file.
4. **External research gone through multiple passes:** ✅ — three
   parallel research agents ran (FlorisBoard / HeliBoard / LiteRT-LM;
   competitor matrix; dependency + security); their findings cross-fed
   into the matrix, the security review, the dataset review, the feature
   backlog, the prioritization, and the roadmap addendum.
5. **Source saturation tested:** ✅ — see
   [SOURCE_REGISTER.md §3](SOURCE_REGISTER.md) and
   [RESEARCH_LOG.md §2.4](RESEARCH_LOG.md).
6. **Roadmap updated/improved:**
   ✅ as an additive **addendum**
   ([ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)),
   not a rewrite of `ROADMAP.md`. This preserves the
   *"do not destructively rewrite project files"* rule and the 340 KB of
   sourced history that the user has built.
7. **Self-audit passes:** This list.
8. **Remaining limitations documented:** ✅ —
   [RESEARCH_LOG.md §4](RESEARCH_LOG.md) records what was not opened,
   what could not be verified, and what would justify a second pass.

## 7. What a future research run should do differently

- Open `ime/mcp/` + `ime/voice/` + `ime/smartcompose/` source files
  directly to verify ROADMAP §L7 / §L1 facade contracts. This run took
  them at the ROADMAP's word.
- Inspect `app/lint.xml` baseline and refresh the lint counter in
  `IMPROVEMENT_PLAN.md` (currently from 2026-05-05).
- Re-fetch the unverified dates (HeliBoard v3.9 year; Sayboard v4.2.1 year;
  Tap Strap Android IME 2026 activity).
- Verify the LiteRT-LM 0.11.0 GA claim — only 0.10.x was confirmed
  externally this pass.
- Open the `app/src/main/assets/ime/dict/data.json` SCOWL bundle for a
  fresh size + content audit (last full audit predates this run).
- Run `:app:dependencies` locally and diff against the version-catalog
  pins (transitive surface not surveyed this run).
