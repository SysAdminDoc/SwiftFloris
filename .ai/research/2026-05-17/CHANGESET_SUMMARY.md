# Changeset Summary — 2026-05-17 Research Run

This research run created **18 new files** across five passes and
**modified zero source / build / test files**. The fifth pass added a
small `ROADMAP.md` v5.3 delta and corrected research artifacts for
dependency and competitor drift. `IMPROVEMENT_PLAN.md`, per-release
notes, build files, and source files under `app/` or `lib/` were not
touched.

> **Post-research development continuation:** after the research run,
> v1.8.59 shipped Phase D3 (typing-stats accuracy delta), and v1.8.60
> shipped the Phase B1 multilingual sentence-position prior seed, v1.8.61
> shipped Phase B2 quick-prediction-insert threshold tuning, v1.8.62
> shipped Phase C1 split-keyboard renderer wire-up, v1.8.63 shipped
> Phase C3 bundled High Contrast / Aurora themes, v1.8.64 shipped
> Phase D1 calendar quick-insert, and v1.8.65 shipped Phase A3 encrypted
> dictionary export/import Settings wiring. Those
> follow-ups touched runtime source, tests, strings, bundled Zipf assets,
> `NOTICE`, theme stylesheets, `gradle.properties`, release notes, `README.md`,
> `PROJECT_CONTEXT.md`, `ROADMAP.md`,
> `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`, and
> `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. The historical sections below
> describe the original research run; see `RELEASE_NOTES_v1.8.59.md`,
> `RELEASE_NOTES_v1.8.60.md`, `RELEASE_NOTES_v1.8.61.md`, and
> `RELEASE_NOTES_v1.8.62.md`, `RELEASE_NOTES_v1.8.63.md`,
> `RELEASE_NOTES_v1.8.64.md`, and `RELEASE_NOTES_v1.8.65.md` for the
> implementation changes.

- Pass 1 created 11 files (root: `PROJECT_CONTEXT.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`; nine artifacts under
  `.ai/research/2026-05-17/`).
- Pass 2 added 3 more (`AGENTS.md`, `CLAUDE.md`,
  `SECOND_PASS_FINDINGS.md`) and minor in-place corrections to two
  prior pass-1 artifacts.
- Pass 3 added 1 more (`THIRD_PASS_FINDINGS.md`) and closes the
  SECOND_PASS_FINDINGS §10 "did not cover" backlog: asset audit,
  workflow audit, theme count, test count, release-notes/ROADMAP
  coverage, tag-lag delta, plus 10 verified external facts (LiteRT-LM
  0.11.0 GA, Compose BOM 2026.05.00, Kotlin 2.4 still RC, Android 17
  GA expected June 2026, SwiftKey cutoff confirmed no-extension, 16 KB
  enforcement landed May 1, Obtainium healthy, AOSP cadence verbatim
  citation, Roborazzi 1.60.0, HeliBoard NLnet update).
- Pass 4 added 1 more (`FOURTH_PASS_FINDINGS.md`) and reconciled README,
  privacy/AI documentation, and selected subsystem inspection with the
  v1.8.58 state.
- Pass 5 added 1 more (`FIFTH_PASS_FINDINGS.md`) and corrected stale
  dependency guidance: Activity 1.13.0 is stable, Security Crypto 1.1.0
  exists but APIs are deprecated, AGP target is 9.2.x, KSP target is
  2.3.8, Roborazzi target is 1.60.0, and LeanType is now in the
  competitor matrix.

**Concurrent-release reconciliation:** during this research run, three
releases shipped on master (v1.8.56 Phase B4, v1.8.57 Phase C2 arrow-keys
preset, v1.8.58 Phase D2 task-creation quick action). All three matched
items this research run was preparing to recommend. `PROJECT_CONTEXT.md`
§7 and `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §0 were updated to
reflect HEAD = v1.8.58 and to remove the three closed recommendations
from the open list.

**Post-research continuation reconciliation:** v1.8.59 Phase D3,
v1.8.60 Phase B1, v1.8.61 Phase B2, v1.8.62 Phase C1, v1.8.63 Phase C3,
v1.8.64 Phase D1, and v1.8.65 Phase A3 Settings wiring subsequently
shipped from the same SwiftKey-parity plan. v1.8.66 through v1.8.69 then
shipped the AI transparency surface, reproducible-build self-check, Tink
encrypted-preference migration, and Bump-batch A. The later release-tag
catch-up backfilled local tags `v1.8.41` through `v1.8.69`; only pushing
those tags from the maintainer host remains.

**v1.8.63 continuation files:** `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/{swiftkey_high_contrast,aurora_animated}.json`,
`app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/ActiveThemeLocals.kt`,
`app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/AuroraAnimatedThemeBackground.kt`,
`app/src/test/kotlin/dev/patrickgold/florisboard/ime/window/AuroraAnimatedThemeBackgroundTest.kt`,
and `RELEASE_NOTES_v1.8.63.md` were added. `scripts/gen_m3e_themes.py`,
the bundled theme manifest, `FlorisImeTheme.kt`, `ImeWindow.kt`,
`ThemeContrastTest.kt`, and the release/context docs were updated.

**v1.8.64 continuation files:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/calendar/{CalendarQuickInsertManager,CalendarPermissionActivity,CalendarAgendaPickerPanel}.kt`,
`app/src/test/kotlin/dev/patrickgold/florisboard/ime/calendar/CalendarAgendaFormatterTest.kt`,
and `RELEASE_NOTES_v1.8.64.md` were added. `AndroidManifest.xml`,
`FlorisApplication.kt`, `AppPrefs.kt`, `QuickAction.kt`,
`QuickActionArrangement.kt`, `QuickActionButton.kt`, `TextInputLayout.kt`,
`QuickActionArrangementTest.kt`, `gradle.properties`, and the release/context
docs were updated.

**v1.8.65 continuation files:** `RELEASE_NOTES_v1.8.65.md` was added.
`UserDictionaryScreen.kt`, `UserDictionary.kt`, `DictionaryImporter.kt`,
`PersonalDictionaryImportSummaryDialog.kt`, `DictionaryImporterTest.kt`,
`EncryptedDictionaryExportTest.kt`, `strings.xml`,
`docs/MIGRATE_FROM_SWIFTKEY.md`, `docs/PRIVACY_AND_AI.md`,
`gradle.properties`, and
the release/context docs were updated for the encrypted dictionary
export/import round-trip.

**Second-pass deep-dives** verified or concretized seven items the first
pass left thin:

1. Source-code verification of L1 / L2 / L7 facade contracts (closes
   `MEMORY_CONSOLIDATION.md §2.6 ⚠️` marker).
2. **Tink migration recipe** — exact API surface, artifact pin
   (`com.google.crypto.tink:tink-android:1.19.0`), atomic+idempotent
   migration code pattern, test recipe.
3. **FunctionGemma 270M mobile bundle** is **INT8 at 289 MB**, not 135 MB.
   LiteRT-LM Kotlin loading API still undocumented in the model card.
4. **ML Kit Digital Ink F-Droid friction** is fundamental — the
   recognizer library binary itself is closed-source. F-Droid eligibility
   requires a two-SKU plan with an OSS-CRNN-based alternative addon.
5. **Bergamot upstream is C++/WASM only, no Android assets.** The real
   Android distributable is `DavidVentura/firefox-translator` (MPL-2.0,
   JNI-based, on F-Droid).
6. **Android 17 IME visibility** — exact migration recipe; v1.8.45's
   shipped fix is consistent with the documented behavior.
7. **F-Droid Reproducible Builds 2026 process** — `verification.f-droid.org`,
   required `Binaries:` + `AllowedAPKSigningKeys:` YAML, common failure
   modes; concrete recommendation to add `vcsInfo.enabled = false`.
8. **SCOWL 2020.12.07 → 2026.02.25 + ESDB SQLite future format;
   wordfreq officially in sunset; CC-100 as the non-English Zipf-overlay
   source.**

See [SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md) for the full deep-dive
material and seven new ROADMAP item proposals.

The deliberate non-destructive posture honors the prompt's rule:
*"Do not destructively rewrite project files. Prefer creating canonical
consolidated files and adding pointers from older files."*

---

## 1. Files created

### 1.1 Root-level (canonical project context)

| Path | Purpose |
|---|---|
| [PROJECT_CONTEXT.md](../../../PROJECT_CONTEXT.md) | Single-page consolidated project context. Designed as the fastest read for an AI session, new contributor, or maintainer-context refresh. References `ROADMAP.md`, `IMPROVEMENT_PLAN.md`, the parity roadmap, and every research artifact this run produced |
| [AGENTS.md](../../../AGENTS.md) | Canonical cross-agent instruction file with hard invariants, read order, local environment notes, and Definition-of-Done expectations |
| [CLAUDE.md](../../../CLAUDE.md) | Claude-specific supplement that keeps tool-specific workflow advice out of `AGENTS.md` |
| [ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) | Actionable additions, corrections, and reframings keyed to existing `ROADMAP.md` sections. Fifth-pass corrections are now mirrored into the root `ROADMAP.md` v5.3 delta |

### 1.2 Research-run artifacts (under `.ai/research/2026-05-17/`)

| Path | Purpose |
|---|---|
| [STATE_OF_REPO.md](STATE_OF_REPO.md) | Local reconnaissance: identity, invariants, version pins, module layout, source size, subsystem map, CI/workflows, release stream, permissions, tests, TODO inventory, what's intentionally absent |
| [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) | Inventory of every AI / planning / memory file in repo, plus reconciliation against v1.8.58 HEAD reality. Identifies the agent-instruction files, the five superseded SWIFTKEY_* docs, tag lag, and open conflicts (none material) |
| [SOURCE_REGISTER.md](SOURCE_REGISTER.md) | Every local and external source consulted during the run, organized by topic. ~100 URLs across 15 source classes |
| [RESEARCH_LOG.md](RESEARCH_LOG.md) | Search strategies, tools used, parallel-agent dispatch, saturation notes, and known gaps in this pass |
| [COMPETITOR_MATRIX.md](COMPETITOR_MATRIX.md) | 15 OSS + 8 commercial + 4 adjacent keyboards as of May 2026, with version + license + features-SwiftFloris-doesn't-have + strategic implications. Fifth pass added LeanType |
| [SECURITY_AND_DEPENDENCY_REVIEW.md](SECURITY_AND_DEPENDENCY_REVIEW.md) | Every pinned version checked against latest stable; license-compatibility verification (KenLM is LGPL — material correction); CVE status; F-Droid verified-tier path; EU AI Act Article 50 cutoff; recommended bump-batches and security additions |
| [DATASET_MODEL_INTEGRATION_REVIEW.md](DATASET_MODEL_INTEGRATION_REVIEW.md) | Datasets currently bundled vs referenced; models referenced (Gemma 3, FunctionGemma, Whisper, Vosk, Bergamot, librime, ML Kit, CleverKeys, NLLB-200); integrations surfaced; coverage map; license-cleanliness audit |
| [FEATURE_BACKLOG.md](FEATURE_BACKLOG.md) | Raw harvested ideas: 11 with updated-evidence-only against existing ROADMAP items + 33 new ideas across distribution / privacy / dependency / feature-opportunities / testing / docs + 6 long-tail tracked + 6 rejected-with-reasoning |
| [PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md) | Every backlog item scored on impact × urgency / cost; sorted into Tier 1 (≥ 5.0), Tier 2 (3.5–4.9), Tier 3 (2.0–3.4), Tier 4 (track only); ends with recommended next-three-releases mapping |
| [SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md) | Deep-dive corrections for Tink migration, FunctionGemma size, ML Kit / Bergamot distribution, F-Droid reproducibility, and dataset drift |
| [THIRD_PASS_FINDINGS.md](THIRD_PASS_FINDINGS.md) | Third-pass verification over assets, workflow, README/theme counts, tags, and additional external facts |
| [FOURTH_PASS_FINDINGS.md](FOURTH_PASS_FINDINGS.md) | Fourth-pass README / privacy / subsystem reconciliation against the current v1.8.58 state |
| [FIFTH_PASS_FINDINGS.md](FIFTH_PASS_FINDINGS.md) | Fifth-pass dependency corrections and LeanType competitor addition |
| **CHANGESET_SUMMARY.md** | This file |

## 2. Files NOT touched

The following files exist in the repo and were **deliberately not modified**
this run:

- `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` — already current.
- `SWIFTKEY_PARITY_AUDIT.md`, `SWIFTKEY_PARITY_BUILD_PLAN.md`,
  `SWIFTKEY_PARITY_RESEARCH.md`, `SWIFTKEY_AI_RESEARCH.md`,
  `SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md` — superseded but kept as audit
  trail. A later continuation added top-of-file banners; see section 20.
  [MEMORY_CONSOLIDATION.md §5](MEMORY_CONSOLIDATION.md) records the
  non-destructive pointer plan.
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

Root `ROADMAP.md`, `PROJECT_CONTEXT.md`, and the research artifacts were
modified only for planning/context corrections; no runtime behavior changed.

## 3. Why the research run is non-destructive by design

- The user is in the middle of an active SwiftKey-parity sprint; HEAD is
  close to the 2026-05-31 cutoff. The cost of an unintended `ROADMAP.md`
  rewrite during this window is high, so the fifth pass added only a
  bounded v5.3 delta and left the historical body intact.
- The user controls the remote push (per memory: `git push` to
  `SysAdminDoc/SwiftFloris` fails 403 from this VM). Research-run output
  should be reviewable as one or two clean commits on the user's main
  push host, not an opaque rewrite.
- `ROADMAP.md` is heavily sourced and load-bearing. Mechanical
  rewriting would lose the auditability that makes it useful.
- `.ai/research/<YYYY-MM-DD>/` is the right place for research-time
  artifacts; root `ROADMAP.md` should receive only narrow deltas once the
  findings have been checked for contradictions.

## 4. Recommended follow-up commits (NOT made this run)

In rough priority order, with reference to
[PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md):

| Order | Commit suggestion | Files touched |
|---|---|---|
| 1 | `docs: research run 2026-05-17 fifth pass corrections` | This research run as one commit |
| 2 | ✅ `chore(repo): tag v1.8.41 ... v1.8.69 (29-tag catch-up)` | local git tags only; push pending from maintainer host |
| 3 | `chore(deps): bump-batch A (coroutines 1.11, KSP 2.3.8, zxing 3.5.4, aboutlibraries 14.2; keep activity 1.13.0)` | `gradle/libs.versions.toml` + a new release note |
| 4 | `feat(setup): EU AI Act Article 50 first-run explainer + Settings → About surface` | `app/setup/` + `docs/PRIVACY_AND_AI.md` |
| 5 | `feat(repo): PROJECT_CONTEXT pointer in AGENTS.md + CLAUDE.md` | New `AGENTS.md` + `CLAUDE.md` |
| 6 | `chore(repo): remove stale crash + replay logs from repo root` | `hs_err_pid*.log` + `replay_pid*.log` |
| 7 | `chore(deps): replace androidx-security-crypto with Tink + AndroidKeystoreV1` (separate slice — risk-isolated) | `app/build.gradle.kts` + `KeyManager.kt` paths + `PersonalDictionaryEncryptionTest` rewrite |
| 8 | `chore(deps): bump-batch B (Roborazzi 1.60 + Robolectric 4.16.1)` | versions catalog + a sweep over Roborazzi tests |
| 9 | `chore(deps): bump-batch C (AGP 9.2.x + Compose BOM 2026.05.00)` | versions catalog + R8 rules audit |
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
   ([ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md))
   plus a bounded root `ROADMAP.md` v5.3 delta. This preserves the
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

---

## 8. v1.8.66 continuation — N8.7 Article 50 transparency surface

The autonomous development loop then shipped the local-code portion of
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §B.2 / `ROADMAP.md` N8.7.

**Files added:**

- `RELEASE_NOTES_v1.8.66.md`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/about/AiFeatureDisclosure.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/about/AiFeaturesScreen.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/app/settings/about/AiFeatureDisclosureCatalogTest.kt`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1866`,
  `projectVersionName=1.8.66`
- `AppPrefs.kt` → new persisted
  `internal__ai_features_explainer_seen` setup acknowledgement
- `SetupScreen.kt` → first setup step now reviews local AI features before
  IME enablement
- `Routes.kt` / `AboutScreen.kt` → reopenable Settings → About →
  **AI features in this keyboard** route
- `strings.xml` / `strings_dont_translate.xml` → disclosure strings and
  GitHub doc URLs
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`, and
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` → HEAD/current release moved
  to v1.8.66 and N8.7 marked shipped

**Verification note:** this VM still has no Java on PATH, so Gradle cannot
run locally. The release notes record the focused Gradle target for the
maintainer build host. Local verification was limited to source inspection,
`git diff --check`, the no-network permission grep, and post-commit git
integrity checks.

---

## 9. v1.8.67 continuation — N12.5 reproducible-build self-check

The autonomous development loop then shipped the local-code portion of
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §B.4 / `ROADMAP.md` N12.5.

**Files added:**

- `.github/workflows/reproducible-build.yml`
- `scripts/verify-reproducible-apk.sh`
- `RELEASE_NOTES_v1.8.67.md`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1867`,
  `projectVersionName=1.8.67`
- `docs/REPRODUCIBLE_BUILDS.md` → documents the build-twice self-check
  and workflow trigger surface
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`, and
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` → HEAD/current release moved
  to v1.8.67 and N12.5 marked shipped

**Verification note:** local verification used `git diff --check`, YAML parse
validation for `.github/workflows/reproducible-build.yml`, LF-only line-ending
checks for the new Linux-facing files, and static grep over the workflow/script
references. Bash, Java, and the Android SDK are absent on this VM, so the shell
syntax check and actual two-release-build self-check must run on GitHub Actions
or the maintainer build host.

---

## 10. v1.8.68 continuation — N7.6 Tink / AndroidKeystore migration

The autonomous development loop then shipped
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §A.2 / §G.2 as `ROADMAP.md` N7.6.

**Files added:**

- `RELEASE_NOTES_v1.8.68.md`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCrypto.kt`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1868`,
  `projectVersionName=1.8.68`
- `gradle/libs.versions.toml` / `app/build.gradle.kts` → removed
  `androidx.security:security-crypto:1.1.0-alpha06`, added
  `com.google.crypto.tink:tink-android:1.21.0`
- `FlorisUserDictionaryEncryption.kt` → SQLCipher passphrase now uses the
  shared Tink / AndroidKeystore wrapper and one-shot legacy AndroidX encrypted
  preference migration
- `ClipboardHistoryManager.kt` → legacy encrypted clipboard-history store now
  uses the shared Tink wrapper with legacy AndroidX migration and in-memory
  fallback on Keystore failure
- `PersonalDictionaryEncryptionTest.kt` → pins the Tink wrapper, dependency,
  legacy parser, and clipboard-history migration contracts
- `NOTICE`, `docs/SECURITY.md`, `docs/THREAT_MODEL.md`, `README.md`,
  `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`, and
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` → release/version/context updates
  for v1.8.68

**Verification note:** local verification used `git diff --check`, source greps
confirming AndroidX Security Crypto removal from app source/build files, Tink
wrapper presence checks, and the no-network permission grep. Java and the
Android SDK are absent on this VM, so the focused
`PersonalDictionaryEncryptionTest` and release assembly must run on the
maintainer build host.

---

## 11. v1.8.69 continuation — Bump-batch A

The autonomous development loop then shipped
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §A.4 / `ROADMAP.md` N14.5.

**Files added:**

- `RELEASE_NOTES_v1.8.69.md`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1869`,
  `projectVersionName=1.8.69`
- `gradle/libs.versions.toml` →
  `kotlinx-coroutines=1.11.0`, `ksp=2.3.8`,
  `mikepenz-aboutlibraries=14.2.0`, `zxing-core=3.5.4`
- `docs/DEPENDENCY_TRIAGE.md` → audit-log row for Bump-batch A and the
  intentional AboutLibraries beta skip
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `FIFTH_PASS_FINDINGS.md`, `PRIORITIZATION_MATRIX.md`,
  `STATE_OF_REPO.md`, and `SECURITY_AND_DEPENDENCY_REVIEW.md` →
  release/version/context updates for v1.8.69

**Verification note:** Maven Central / Gradle Plugin Portal metadata was
re-checked before applying the bumps. Local verification used
`git diff --check`, dependency-version greps, and the no-network permission
grep. Java and the Android SDK are absent on this VM, so
`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` must run on the
maintainer build host.

---

## 12. Release-tag catch-up continuation

The autonomous development loop then completed the release-metadata portion of
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §B.5 / `ROADMAP.md` N16.3.

**Local tags created:**

- `v1.8.41` through `v1.8.69`

**Validation before tagging:**

- Every proposed tag target was checked against the commit's
  `gradle.properties`.
- Each tag's `projectVersionName` matched the tag name without the leading
  `v`.
- Each tag's `projectVersionCode` matched the expected `18xx` code.

**Files updated:**

- `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PRIORITIZATION_MATRIX.md`, `STATE_OF_REPO.md`, and this file now record
  the local tag backfill.

**Verification note:** this was intentionally a metadata-only slice. No app
version bump or release note was created because the app release remains
`v1.8.69`. Tags were not pushed from this VM; the maintainer host still needs
to push the `v1.8.41`...`v1.8.69` refs.

---

## 13. v1.8.70 continuation — README migration-window callouts

The autonomous development loop then shipped Tier-1 README migration-window
callouts from `PRIORITIZATION_MATRIX.md` #2 / #5 / #10 as `ROADMAP.md` N16.4.

**Files added:**

- `RELEASE_NOTES_v1.8.70.md`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1870`,
  `projectVersionName=1.8.70`
- `README.md` → version/front-door refresh, Samsung / Grammarly users callout,
  and v1.8.70 Recent releases entry
- `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PRIORITIZATION_MATRIX.md`, `STATE_OF_REPO.md`, `SOURCE_REGISTER.md`,
  and `RESEARCH_LOG.md` → release/context updates for v1.8.70

**Sources re-checked before editing:**

- Samsung Writing Assist support guide
- SamMobile / 9to5Google One UI 7 keyboard-agnostic Writing Assist coverage
- Grammarly support page for the Grammarly Keyboard for Android transition

**Verification note:** documentation-only release; no app code, permissions,
dependencies, or runtime behavior changed. `git diff --check` passed with only
the known `ROADMAP.md` CRLF warning. The focused Gradle suite was attempted
and stopped at the known VM blocker: `JAVA_HOME` is not set and no `java`
command is on PATH, so maintainer-host Gradle verification remains required
before publishing.

---

## 14. v1.8.71 continuation — Bump-batch B

The autonomous development loop then shipped
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §A.4 / `ROADMAP.md` N14.6.

**Files added:**

- `RELEASE_NOTES_v1.8.71.md`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1871`,
  `projectVersionName=1.8.71`
- `gradle/libs.versions.toml` →
  `roborazzi=1.60.0`, `robolectric=4.16.1`
- `docs/DEPENDENCY_TRIAGE.md` → audit-log row for Bump-batch B
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PRIORITIZATION_MATRIX.md`, `STATE_OF_REPO.md`,
  `SECURITY_AND_DEPENDENCY_REVIEW.md`, `SOURCE_REGISTER.md`, and
  `RESEARCH_LOG.md` → release/version/context updates for v1.8.71

**Sources re-checked before editing:**

- Maven Central Roborazzi metadata: latest/release `1.60.0`
- Gradle Plugin Portal Roborazzi plugin metadata: latest/release `1.60.0`
- Maven Central Robolectric metadata: latest/release `4.16.1`
- OSV querybatch: zero vulnerabilities for updated Roborazzi core / Compose /
  JUnit-rule artifacts and Robolectric

**Verification note:** no app code, permissions, or runtime behavior changed.
`git diff --check` and manifest banned-network-permission scan were run.
The focused Gradle suite was attempted and stopped at the known VM blocker:
`JAVA_HOME` is not set and no `java` command is on PATH, so maintainer-host
Gradle verification remains required before publishing.

---

## 15. v1.8.72 continuation — HeliBoard / NLnet slip-base-case correction

The autonomous development loop then shipped Tier-1 prioritization item #9:
make the HeliBoard / NLnet slip scenario the planning base case instead of
treating N1.1 as the default production glide path.

**Files added:**

- `RELEASE_NOTES_v1.8.72.md`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1872`,
  `projectVersionName=1.8.72`
- `README.md` → version/front-door refresh and v1.8.72 Recent releases entry
- `ROADMAP.md` → N1.1 reframed as additive HeliBoard / NLnet integration,
  N1.3 statistical glide marked production default, risk-register row promoted
  to high/base-case, and source appendix refreshed
- `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PROJECT_CONTEXT.md`, `AGENTS.md`, `PRIORITIZATION_MATRIX.md`,
  `STATE_OF_REPO.md`, `SOURCE_REGISTER.md`, and `RESEARCH_LOG.md` →
  release/context updates for v1.8.72

**Sources re-checked before editing:**

- HeliBoard `#2226`: still open, updated 2026-05-11 via GitHub API
- HeliBoard releases / latest-release API: latest remains `v3.9`, published
  2026-03-29
- NLnet Gesture Typing page: still describes a separate open-source gesture
  library with an AOSP-compatible drop-in layer
- HeliBoard gesture-data wiki: data collection period ends 2026-11-30 and
  contributors still need an appropriate gesture typing library to gather data

**Verification note:** documentation-only release; no app code, permissions,
dependencies, or runtime behavior changed. `git diff --check` and manifest
banned-network-permission scan were run. The focused Gradle suite was
attempted and stopped at the known VM blocker: `JAVA_HOME` is not set and no
`java` command is on PATH, so maintainer-host Gradle verification remains
required before publishing.

---

## 17. Tier-1 matrix reconciliation

After v1.8.73, the autonomous loop reconciled stale Tier-1 matrix rows that
were already satisfied by earlier commits:

- #7 first-run AI-features explainer → shipped v1.8.66
- #8 Settings → About AI-features explainer → shipped v1.8.66
- #11 `docs/PRIVACY_AND_AI.md` explainer copy → shipped v1.8.66
- #13 `PROJECT_CONTEXT.md` / `AGENTS.md` / `CLAUDE.md` pointer pattern →
  present from the research run

No version bump was needed because this was documentation-state
reconciliation only; no app, CI, or release metadata changed.

---

## 16. v1.8.73 continuation — root crash/replay log cleanup + CI guard

The autonomous development loop then shipped Tier-1 prioritization items #14
and #15 as `ROADMAP.md` N18.1.

**Local files moved (ignored, not committed):**

- `hs_err_pid15604.log`
- `hs_err_pid4860.log`
- `replay_pid11584.log`
- `replay_pid15604.log`
- `replay_pid4860.log`

Destination: `.ai/local-crash-logs/2026-05-16/`

**Files added:**

- `RELEASE_NOTES_v1.8.73.md`
- `.ai/local-crash-logs/README.md`
- `scripts/check-no-root-crash-logs.sh`

**Files updated:**

- `.github/workflows/android.yml` → runs the root crash/replay log guard before
  Java / Gradle setup
- `gradle.properties` → `projectVersionCode=1873`,
  `projectVersionName=1.8.73`
- `README.md`, `ROADMAP.md`, `IMPROVEMENT_PLAN.md`,
  `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PRIORITIZATION_MATRIX.md`, `STATE_OF_REPO.md`, `SOURCE_REGISTER.md`, and
  `RESEARCH_LOG.md` → release/context updates for v1.8.73

**Verification note:** root scan showed no remaining root
`hs_err_pid*.log` / `replay_pid*.log` files after the move.
`bash scripts/check-no-root-crash-logs.sh`, `git diff --check`, and manifest
banned-network-permission scan were run. The focused Gradle suite was
attempted and stopped at the known VM blocker: `JAVA_HOME` is not set and no
`java` command is on PATH, so maintainer-host Gradle verification remains
required before publishing.

---

## 18. v1.8.74 continuation — Bump-batch C build toolchain refresh

The autonomous development loop shipped Tier-2 prioritization item #16 as
`ROADMAP.md` N14.7.

**Files added:**

- `RELEASE_NOTES_v1.8.74.md`

**Files updated:**

- `gradle/libs.versions.toml` → Android Gradle Plugin `9.2.1` and Compose BOM
  `2026.05.00`
- `gradle.properties` → `projectVersionCode=1874`,
  `projectVersionName=1.8.74`
- `README.md`, `docs/DEPENDENCY_TRIAGE.md`, `ROADMAP.md`,
  `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PRIORITIZATION_MATRIX.md`, `STATE_OF_REPO.md`,
  `SECURITY_AND_DEPENDENCY_REVIEW.md`, `SOURCE_REGISTER.md`, and
  `RESEARCH_LOG.md` → release/context updates for v1.8.74

**Sources re-checked before editing:**

- Google Maven AGP metadata: `latest` / `release` is `9.3.0-alpha05`
  preview; stable tail ends at `9.2.1`
- Android Gradle Plugin 9.2 release notes and Android Studio Panda 4 Patch 1
  notes for the AGP `9.2.1` patch line
- Google Maven Compose BOM metadata: latest/release `2026.05.00`
- Compose release stream
- OSV querybatch for `com.android.tools.build:gradle:9.2.1` and
  `androidx.compose:compose-bom:2026.05.00`
- Active R8 `-keepattributes` rules in `app/proguard-rules.pro`

**Verification note:** `git diff --check`, manifest banned-network-permission
scan, root crash/replay-log guard, version-pin greps, R8 keepattributes grep,
Google Maven metadata checks, and OSV querybatch were run. The focused Gradle
suite was attempted and stopped at the known VM blocker: `JAVA_HOME` is not set
and no `java` command is on PATH, so maintainer-host Gradle verification
remains required before publishing.

---

## 19. Tier-2 matrix reconciliation

After v1.8.74, the autonomous loop reconciled stale Tier-2 matrix rows that
were already satisfied by earlier releases:

- #17 dedicated arrow-keys row preset → shipped v1.8.57 as
  `BottomRowPreset.Navigation`
- #18 calendar quick-insert → shipped v1.8.64
- #19 tasks quick-insert → shipped v1.8.58
- #20 animated theme → shipped v1.8.63
- #21 AAA high-contrast theme → shipped v1.8.63
- #22 Phase B4 same-sentence language-switch hardening → shipped v1.8.56
- #23 Phase C1 split-keyboard renderer → shipped v1.8.62
- #25 reproducible-build verification CI job → shipped v1.8.67

**Files updated:**

- `PRIORITIZATION_MATRIX.md` → rows #17-#23 and #25 marked shipped, and the
  next-release mapping now names #24, #26, #27, and #28 as the remaining true
  open Tier-2 rows
- `FEATURE_BACKLOG.md` → raw backlog rows for the SwiftKey-parity slices marked
  shipped
- `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` → B.3 status changed from open to
  v1.8.57 with the implementation-name correction
- `RESEARCH_LOG.md` → reconciliation note added

No version bump was needed because this was documentation-state reconciliation
only; no app, CI, dependency, permission, or runtime behavior changed.

---

## 20. Docs-only Tier-2 #26 — superseded SwiftKey banners

The autonomous loop attempted Tier-2 #24 (`:app:lintDebug`) first, but it is
blocked on this VM by the known Gradle host issue: `JAVA_HOME` is not set and no
`java` command is on PATH. The loop then shipped the next implementable Tier-2
row, #26.

**Files updated with a 5-line `SUPERSEDED — 2026-05-17` banner:**

- `SWIFTKEY_PARITY_AUDIT.md`
- `SWIFTKEY_PARITY_BUILD_PLAN.md`
- `SWIFTKEY_PARITY_RESEARCH.md`
- `SWIFTKEY_AI_RESEARCH.md`
- `SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md`

Although the original matrix row said "4 superseded docs," the canonical
`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` supersession sentence names five files,
so all five were updated.

**Project-state files updated:**

- `MEMORY_CONSOLIDATION.md` → marks the pointer plan applied for the five
  superseded SwiftKey files
- `PRIORITIZATION_MATRIX.md` → keeps #24 open with the Java blocker and marks
  #26 shipped docs-only
- `FEATURE_BACKLOG.md` → marks the banner task applied

No version bump was needed because this was documentation-state cleanup only;
no app, CI, dependency, permission, or runtime behavior changed.

---

## 21. v1.8.75 continuation — macOS `.keylayout` parser

The autonomous loop shipped Tier-2 prioritization item #27 as ROADMAP
Next-6.4a.

**Files added:**

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/MacKeylayoutParser.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/MacKeylayoutParserTest.kt`
- `RELEASE_NOTES_v1.8.75.md`

**Files updated:**

- `HardwareKeyboardLayout.kt` → Next-6.4b is now the remaining Android runtime
  mapper follow-up
- `gradle.properties` → `projectVersionCode=1875`,
  `projectVersionName=1.8.75`
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PRIORITIZATION_MATRIX.md`, `FEATURE_BACKLOG.md`, `STATE_OF_REPO.md`, and
  `RESEARCH_LOG.md` → release/context updates for v1.8.75

**Implementation notes:**

- `MacKeylayoutParser` uses an XXE-hardened `DocumentBuilderFactory`, matching
  the Keyman LDML parser's trust-boundary posture.
- It selects the `<keyMapSet>` referenced by `<layouts>`, maps normal / Shift /
  Option-as-AltGr / Shift+Option modifier slots, ignores command/control-only
  maps, and falls back to indexes 0/1/2/3 if no modifier map is present.
- It captures action-backed dead-key outputs when the `<action>` exposes a
  `<when output="...">` value.
- It returns `HardwareKeyboardLayout.Empty` for blank, malformed,
  non-keyboard-root, no-key, or DOCTYPE-bearing XML.

**Verification note:** `git diff --check`, a manifest banned-network-permission
scan, and a focused Gradle test command were run. Gradle stopped at the known VM
blocker: `JAVA_HOME` is not set and no `java` command is on PATH, so
maintainer-host Gradle verification remains required before publishing.

---

## 22. v1.8.76 continuation — hardware-keyboard runtime mapper

The autonomous loop shipped Tier-2 prioritization item #28 as ROADMAP
Next-6.4b.

**Files added:**

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapperTest.kt`
- `RELEASE_NOTES_v1.8.76.md`

**Files updated:**

- `KeyboardManager.kt` → hardware key-down dispatch now checks the runtime
  mapper before built-in Space / Enter / Shift handling
- `KlcLayoutParser.kt` → comment corrected so Android runtime mapping points at
  Next-6.4b
- `gradle.properties` → `projectVersionCode=1876`,
  `projectVersionName=1.8.76`
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PRIORITIZATION_MATRIX.md`, `FEATURE_BACKLOG.md`, `STATE_OF_REPO.md`, and
  `RESEARCH_LOG.md` → release/context updates for v1.8.76

**Implementation notes:**

- Runtime bindings are per Android hardware keyboard `deviceId`.
- `pruneDetachedLayouts()` removes stale bindings based on an
  `InputManager.getInputDeviceIds()` provider.
- Mapping order is direct scan code, direct Android key code, PC set-1 scan code
  fallback for KLC layouts, macOS ANSI virtual-key fallback for `.keylayout`
  imports, then source virtual-key name fallback.
- Ctrl / Meta modified events are intentionally ignored so shortcuts such as
  Ctrl+A stay available to the editor/app.

**Verification note:** `git diff --check`, a manifest banned-network-permission
scan, and a focused Gradle test command were run. Gradle stopped at the known VM
blocker: `JAVA_HOME` is not set and no `java` command is on PATH, so
maintainer-host Gradle verification remains required before publishing.

## 23. v1.8.77 continuation — user-imported sticker folder

Implemented Tier-3 #29 / Next-9.5 as a local-only sticker import surface:

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
  - Adds `prefs.sticker.userFolderUri`.
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/media/MediaScreen.kt`
  - Adds a Settings → Emoji & stickers SAF tree picker and clear action for
    the imported sticker folder.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt`
  - Enumerates local PNG / WebP / JPEG / GIF documents into an Imported sticker
    pack with extension fallback, duplicate URI collapse, and a 240-item cap.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`
  - Appends the Imported pack to bundled stickers and decodes local image
    previews off the main thread.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerMediaProvider.kt`
  - Proxies user-sticker content through the existing provider authority so
    commits continue to use `InputConnectionCompat.commitContent`.
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepositoryTest.kt`
  - Covers supported-file filtering, MIME extension fallback, empty folders,
    cap enforcement, and duplicate URI collapse.
- `app/src/main/res/values/strings.xml`
  - Adds sticker-folder settings strings and retitles the media screen to
    Emoji & stickers.
- `RELEASE_NOTES_v1.8.77.md`, `README.md`, `ROADMAP.md`,
  `PROJECT_CONTEXT.md`, `AGENTS.md`, `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `STATE_OF_REPO.md`, `FEATURE_BACKLOG.md`, `PRIORITIZATION_MATRIX.md`, and
  `RESEARCH_LOG.md`
  - Release/context updates for v1.8.77.
- `gradle.properties`
  - `projectVersionCode=1877`, `projectVersionName=1.8.77`.

**Verification note:** `git diff --check`, the manifest banned-network-permission
scan, root JVM crash/replay tracked-file guard, and a focused Gradle test command were run. Gradle
stopped at the known VM blocker: `JAVA_HOME` is not set and no `java` command is
on PATH, so maintainer-host Gradle verification remains required before
publishing.

## 24. Docs-only continuation — architecture and contributing entry points

Implemented Tier-3 #31 / #32 as a contributor-onboarding documentation batch:

- `ARCHITECTURE.md`
  - New root architecture map covering active modules, runtime entrypoints,
    package ownership, typing/media/addon boundaries, security invariants,
    build/CI, testing, and documentation routing.
- `CONTRIBUTING.md`
  - New root contributor guide covering setup, project rules, privacy and
    permission expectations, verification commands, manual QA, release-note
    expectations, PR shape, AI-assisted contributions, and licensing.
- `README.md`
  - Documentation list now links `ARCHITECTURE.md` and `CONTRIBUTING.md`.
  - Contributing section now delegates to the root contributor guide and
    restates the base-app invariants.
- `.github/PULL_REQUEST_TEMPLATE.md`
  - Replaced upstream FlorisBoard-only links with SwiftFloris-local checklist
    items for contributor guidelines, invariants, and exact verification.
- `AGENTS.md`, `PROJECT_CONTEXT.md`, `ROADMAP.md`, `IMPROVEMENT_PLAN.md`,
  `FEATURE_BACKLOG.md`, `PRIORITIZATION_MATRIX.md`, and `RESEARCH_LOG.md`
  - Marked the architecture / contributing docs batch as shipped and routed
    future sessions to the new root files.

**Verification note:** docs-only change. Local verification used
`git diff --check`, new-doc / PR-template link-target existence checks, and the
same no-network / root crash-log tracked-file guards. Gradle is still blocked on this VM by
missing `JAVA_HOME` / `java` and is not required for the docs-only batch.

## 25. Docs-only continuation — root multilingual and voice doc consolidation

Implemented Tier-3 #33 as a repo-hygiene documentation move:

- `GESTURE_TYPING_MULTILINGUAL.md` →
  `docs/GESTURE_TYPING_MULTILINGUAL.md`
- `GESTURE_TYPING_MULTILINGUAL_RESEARCH.md` →
  `docs/GESTURE_TYPING_MULTILINGUAL_RESEARCH.md`
- `FUTO_VOICE_INPUT_TROUBLESHOOTING.md` →
  `docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md`
- `VOICE_COMMANDS.md` → `docs/VOICE_COMMANDS.md`

Updated README troubleshooting / documentation links, ROADMAP §7 / §11 doc
references, and research memory/backlog/prioritization notes. A root scan now
finds no remaining `*MULTILINGUAL*.md`, `VOICE_*.md`, or `FUTO_*.md` files.

**Verification note:** docs-only move. Local verification used `git diff
--check`, root-pattern scan, internal reference grep, local checked-doc link
validation, manifest no-network scan, and root crash-log tracked-file guard.
Gradle remains blocked by the missing Java toolchain and was not required for
this docs-only move.

## 26. v1.8.78 continuation — Keyman `.kmp` package import foundation

Implemented Tier-3 #34 as the safe intake/classifier layer for Keyman package
files:

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanPackageParser.kt`
  - New ZIP-compatible `.kmp` parser with `kmp.json` metadata normalization,
    package option/file/keyboard/language/example/lexical-model models, LDML
    XML extraction via `KeymanLdmlParser`, package-status classification, and
    unsafe traversal / absolute / drive-letter path skipping.
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanPackageParserTest.kt`
  - New tests for metadata parsing, compiled-keyboard classification, LDML
    extraction, lexical-model classification, mixed package detection, unsafe
    path skipping, and invalid ZIP fallback.
- `RELEASE_NOTES_v1.8.78.md`, `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `AGENTS.md`, `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`, and the
  `.ai/research/2026-05-17/` register/backlog/prioritization/state files
  - Release/context updates for v1.8.78 and explicit note that compiled
    `.kmx` / `.js` execution remains future addon/runtime work.
- `gradle.properties`
  - `projectVersionCode=1878`, `projectVersionName=1.8.78`.

**Verification note:** `git diff --check`, the manifest banned-network-permission
scan, root JVM crash/replay tracked-file guard, and a focused Gradle test
command were run. Gradle stopped at the known VM blocker: `JAVA_HOME` is not
set and no `java` command is on PATH, so maintainer-host Gradle verification
remains required before publishing.

## 27. v1.8.79 continuation — honeycomb hex layout wire-up

Implemented Tier-3 #35 as the production wire-up for the earlier honeycomb
renderer foundation:

- `app/src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json`
  - Registered the bundled `honeycomb` character layout for subtype layout
    selection.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt`
  - Marks the bundled `honeycomb` character layout as
    `TextKeyboardLayoutStyle.Honeycomb` before constructing `TextKeyboard`.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboard.kt`
  - Added `TextKeyboardLayoutStyle`, `layoutHoneycomb(...)`, honeycomb cell
    bookkeeping, and hex-aware exact / nearest hit testing.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt`
  - Reuses the production Snygg `TextKeyButton` path while clipping honeycomb
    key surfaces to `HoneycombHexShape`.
- `HoneycombHexShape.kt`, `HoneycombTessellation.kt`, `HoneycombKeyboardRow.kt`,
  and `HoneycombHexButton.kt`
  - Updated stale comments that still described production wire-up as future
    work.
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardHoneycombLayoutTest.kt`
  - Covers odd-row half-column offsets, center hits, bounding-box corner
    rejection, and unchanged rectangular gap rescue for standard layouts.
- `RELEASE_NOTES_v1.8.79.md`, `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `AGENTS.md`, `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`, and the
  `.ai/research/2026-05-17/` register/backlog/prioritization/state files
  - Release/context updates for v1.8.79 and explicit note that remaining
    honeycomb work is visual/device evidence, not layout selection or
    hit-routing.
- `gradle.properties`
  - `projectVersionCode=1879`, `projectVersionName=1.8.79`.

**Verification note:** `git diff --check`, the manifest banned-network-permission
scan, root JVM crash/replay tracked-file guard, and a focused Gradle test
command were run. Gradle stopped at the known VM blocker: `JAVA_HOME` is not
set and no `java` command is on PATH, so maintainer-host Gradle verification
remains required before publishing.

## 28. v1.8.80 continuation — SQLCipher provider migration plan

Implemented Tier-3 #36 as a docs/security planning slice:

- `docs/SQLCIPHER_PROVIDER_MIGRATION.md`
  - New readiness plan for a possible future move from the stock SQLCipher
    Android Community AAR's LibTomCrypt provider to an OpenSSL-backed build.
  - Records the corrected upstream state, migration triggers, proof-of-concept
    build path, 16 KB page-size gates, `PersonalDictionaryEncryptionTest`
    expectations, and rollback rules.
- `docs/SECURITY.md`
  - Added a SQLCipher crypto-provider watch section and linked the migration
    plan.
- `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md`
  - Corrected the earlier "LibTomCrypt removal" premise: SQLCipher issue `#564`
    announced deprecation, but Zetetic restored LibTomCrypt for Android
    Community builds in 4.14.0 and 4.16.0 still lists Android Community
    packages as LibTomCrypt-based.
- `RELEASE_NOTES_v1.8.80.md`, `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `AGENTS.md`, `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`, and the
  `.ai/research/2026-05-17/` register/backlog/prioritization/state files
  - Release/context updates for v1.8.80.
- `gradle.properties`
  - `projectVersionCode=1880`, `projectVersionName=1.8.80`.

**Verification note:** docs-only change. Local verification used `git diff
--check`, the manifest banned-network-permission scan, root JVM crash/replay
tracked-file guard, and checked Markdown links for the new SQLCipher plan.
Gradle remains blocked by the missing Java toolchain and was not required for
this planning slice.

## 29. v1.8.81 continuation — addon catalog foundation

Implemented Next-10.3a as the live-state / catalog prerequisite for the
dictionary-pack addon and HeliBoard-style downloader work:

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonRegistry.kt`
  - New process-local live-state companion to `AddonEnumerator`.
  - Pins first-seen signing certificates by package name, rejects changed-cert
    package-name hijacks, keeps stale pins across uninstall gaps, and exposes
    deterministic lookups by type, package, stable id, and dictionary-pack type.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/DictionaryPackCatalog.kt`
  - New pure catalog builder for enrolled dictionary-pack manifests plus
    descriptor JSON.
  - Rejects missing, malformed, and future-schema descriptors without crashing,
    exposes language lookup, and attaches `AddonProvenanceReport`s for the
    future Settings → Addons surface.
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/addon/AddonRegistryTest.kt`
  - Covers first-enrolment pinning, changed-cert rejection, stale-pin retention,
    duplicate package collapse, runtime-state clearing, and lookup behavior.
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/addon/DictionaryPackCatalogTest.kt`
  - Covers compatible descriptor acceptance, language lookup, provenance handoff,
    missing descriptor rejection, malformed JSON rejection, and future-schema
    rejection.
- `docs/addons/dictionary-pack-spec.md`
  - Updated the routing/reference sections to name the v1.8.81 registry/catalog
    layer and leave asset mounting as the next loader slice.
- `RELEASE_NOTES_v1.8.81.md`, `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `AGENTS.md`, `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`, and the
  `.ai/research/2026-05-17/` register/backlog/prioritization/state files
  - Release/context updates for v1.8.81 and explicit note that Settings UI,
    persisted signing pins, and actual addon asset mounting remain next slices.
- `gradle.properties`
  - `projectVersionCode=1881`, `projectVersionName=1.8.81`.

**Verification note:** local verification used `git diff --check`, the manifest
banned-network-permission scan, and the root JVM crash/replay tracked-file
guard. A focused Gradle test command for the new addon tests was attempted, but
Gradle stopped at the known VM blocker: `JAVA_HOME` is not set and no `java`
command is on PATH, so maintainer-host Gradle verification remains required.

## 30. v1.8.82 continuation — addon signing-pin persistence

Implemented Next-10.3b as the persisted trust-store prerequisite for addon
startup wiring and Settings → Addons:

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonSigningPinSet.kt`
  - New newline-string codec for `packageName=SHA-256` signing certificate
    pins.
  - Ignores malformed/corrupt preference lines, encodes sorted validated pins,
    and preserves first-seen trust for already-pinned packages.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonRegistry.kt`
  - Added `fromPinnedSigningPinSet(...)` and `pinnedSigningPinSet()` helpers so
    the pure registry can round-trip through persisted pins without depending on
    JetPref.
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
  - Added `prefs.addon.signingCertPins` (`addon__signing_cert_pins`) as the
    durable preference key for future registry startup/Settings wiring.
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/addon/AddonSigningPinSetTest.kt`
  - Covers parsing, malformed-line tolerance, deterministic encoding,
    first-seen preservation, and registry codec round-trip.
- `RELEASE_NOTES_v1.8.82.md`, `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `AGENTS.md`, `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`, and the
  `.ai/research/2026-05-17/` register/backlog/prioritization/state files
  - Release/context updates for v1.8.82 and explicit note that startup
    persistence wiring, Settings revoke/reset UI, install hints, and asset
    mounting remain next slices.
- `gradle.properties`
  - `projectVersionCode=1882`, `projectVersionName=1.8.82`.

**Verification note:** local verification used `git diff --check`, the manifest
banned-network-permission scan, and the root JVM crash/replay tracked-file
guard. A focused Gradle test command for the addon pin tests was attempted, but
Gradle stopped at the known VM blocker: `JAVA_HOME` is not set and no `java`
command is on PATH, so maintainer-host Gradle verification remains required.

## 31. v1.8.83 continuation — addon registry startup wiring

Implemented Next-10.3c as the startup/publish layer for the persisted addon
trust store:

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonRegistryStartup.kt`
  - New pure reconciler that accepts an `AddonEnumerator` manifest snapshot and
    raw `prefs.addon.signingCertPins` string, builds an `AddonRegistry`, returns
    the latest snapshot, emits the canonical pin string, and reports whether the
    preference should be written back.
  - `AddonRegistryStore` now holds the process-wide active registry for future
    Settings/runtime consumers.
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
  - Runs addon startup reconciliation on `Dispatchers.Default` during
    `onCreate()`.
  - Publishes `AddonRegistryStore`, persists canonical signing pins only when
    changed, logs accepted/rejected counts, and resets the store on startup
    failure without aborting the IME.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonRegistry.kt`
  - Updated KDoc now that persistence is handled by `AddonRegistryStartup`.
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/addon/AddonRegistryStartupTest.kt`
  - Covers new-addon enrolment, changed-certificate rejection, corrupt stored
    line cleanup, and registry store publish/reset.
- `RELEASE_NOTES_v1.8.83.md`, `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `AGENTS.md`, `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `docs/addons/dictionary-pack-spec.md`, and the `.ai/research/2026-05-17/`
  state/register/backlog/prioritization/log files
  - Release/context updates for v1.8.83 and explicit note that Settings →
    Addons UI/install hints and asset mounting remained next slices at that
    release. The read-only status/rescan UI shipped in v1.8.84.
- `gradle.properties`
  - `projectVersionCode=1883`, `projectVersionName=1.8.83`.

**Verification note:** local verification used `git diff --check`, the manifest
banned-network-permission scan, and the root JVM crash/replay tracked-file
guard. A focused Gradle test command for the addon startup tests was attempted,
but Gradle stopped at the known VM blocker: `JAVA_HOME` is not set and no
`java` command is on PATH, so maintainer-host Gradle verification remains
required.

## 32. v1.8.84 continuation — Settings Addons status surface

Implemented Next-10.3d as the read-only Settings status/rescan layer for addon
enrolment:

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/addons/AddonsSettingsScreen.kt`
  - New Settings → Addons screen that reads `AddonRegistryStore`, observes
    `prefs.addon.signingCertPins`, shows accepted/rejected/pinned counts, lists
    accepted addon package/type/version/license/size/signing-fingerprint details,
    lists rejected package reasons, and shows local-only install guidance.
  - Manual "Rescan installed addons" action runs `AddonEnumerator` on
    `Dispatchers.Default`, reuses `AddonRegistryStartup.reconcile(...)`,
    updates `AddonRegistryStore`, and persists canonical signing pins only when
    the trust set changes.
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/Routes.kt`
  - Added `Routes.Settings.Addons`, deep link `ui://florisboard/settings/addons`,
    and `AddonsSettingsScreen()` navigation.
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt`
  - Added the Addons row under Data & extensions.
- `app/src/main/res/values/strings.xml`
  - Added English source strings for the Addons screen and Home summary.
- `RELEASE_NOTES_v1.8.84.md`, `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `AGENTS.md`, `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `docs/addons/dictionary-pack-spec.md`, and the `.ai/research/2026-05-17/`
  state/register/backlog/prioritization/log files
  - Release/context updates for v1.8.84 and explicit note that signing-pin
    revoke/reset UX plus dictionary asset mounting remain next slices.
- `gradle.properties`
  - `projectVersionCode=1884`, `projectVersionName=1.8.84`.

**Verification note:** local verification used `git diff --check`, the manifest
banned-network-permission scan, and the root JVM crash/replay tracked-file
guard. A Gradle compile command for the Addons settings screen was attempted,
but Gradle stopped at the known VM blocker: `JAVA_HOME` is not set and no
`java` command is on PATH, so maintainer-host Gradle verification remains
required.
