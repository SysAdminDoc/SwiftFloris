# SwiftFloris Roadmap

> Single source of truth for all planned work. Items above the --- are existing plans; items below are research conducted 2026-06-03.

**Current release:** v1.8.204 (versionCode 2004). **Baseline green:** `:app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

Hard rules still apply (see `AGENTS.md`): no `INTERNET` permission in `:app`; Apache-2.0 ceiling on `:app`; no closed-source blobs; one logical change per commit; every shipped release bumps `gradle.properties` version, writes a `CHANGELOG.md` section, and adds a `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (draft <=480 chars for headroom).

Item IDs trace to their origin research: `F#`/`EI#` from the archived 2026-05-25 research feature plan; `R#`/`O#` from the 2026-05-25 second-pass findings; `WS#` from the archived improvement-plan workstreams; `N#`/`Next-#`/`L#` from the archived roadmap tiers. Shipped items and reframed/rejected items live in `COMPLETED.md`; full release detail in `CHANGELOG.md`. Historical strategy (tiered NOW/NEXT/LATER, sourced appendix) is preserved at `docs/archive/ROADMAP_v5.67_2026-05-18.md`.

## Existing Planned Work

### Settings & UX surfaces

- [ ] P1 — PhysicalKeyboardScreen build-out (F29)
  - Why: Shipped Mac `.keylayout` / Keyman `.kmp` / KLC parsers + `HardwareKeyboardRuntimeMapper` have no UI to reach them.
  - Touches: `PhysicalKeyboardScreen` (custom-layout picker + Import button).
  - Acceptance: user can import a custom hardware layout and see it applied through the runtime mapper.
  - Source: TODO.md A3 / research feature plan F29.
- [ ] P1 — "Remember keyboard language per app" (F31, reframed)
  - Why: Original per-app-language spec needed the privileged `READ_APP_SPECIFIC_LOCALES` permission, which conflicts with the clean-permission posture. Reframe to a permission-free remembered subtype.
  - Touches: `ime/core/` (persisted `editorPackage -> subtypeId` map + pure `PerAppSubtypeMemory` to unit-test), new `PerAppLanguageScreen`, `AppPrefs.localization` pref, `FlorisImeService.onStartInputView` hook (~line 569-587).
  - Acceptance: opt-in (default off); manual subtype switch is recorded per package and auto-restored on focus.
  - Source: TODO.md A3 / research feature plan F31.
- [ ] P1 — Voice route empty-state explainer (EI7)
  - Why: Empty voice route gives no guidance; should explain "what is FUTO" with an Install link as a generic recommendation (drop migration-window framing).
  - Touches: voice route empty state UI.
  - Acceptance: empty state shows FUTO explanation + Install link.
  - Source: TODO.md A3 / second-pass EI7.
- [ ] P0 — First-run "import from any keyboard" hint (F3, reframed)
  - Why: Discovery value of importing existing keyboard data is permanent even though the SwiftKey cloud-export window closed 2026-05-31.
  - Touches: detect SwiftKey JSON + Gboard XML + FlorisBoard CSV/.flbackup/.fldic in a SAF picker, route to existing `DictionaryImporter`.
  - Acceptance: first-run hint detects a supported export and routes it to the importer.
  - Source: TODO.md A3 / research feature plan F3.
- [ ] P2 — Personal dictionary bulk-import preview (EI3)
  - Why: Bulk import commits blind; users need to preview and exclude rows before commit. Touches the rollback flow — handle carefully.
  - Touches: import preview (first ~50 entries + total + exclude-row checkboxes) with a "Skip preview" opt-out.
  - Acceptance: preview shown before commit; excluded rows are not imported; opt-out persists.
  - Source: TODO.md A3 / research feature plan EI3.
- [ ] P2 — Per-app accent opt-in discovery hint + preview (F6)
  - Why: Per-app accent feature is undiscoverable.
  - Touches: single-fire discovery hint + Settings preview.
  - Acceptance: hint fires once; Settings shows a preview.
  - Source: TODO.md A3 / research feature plan F6.
- [ ] P3 — Settings home cosmetic re-bucket (EI2)
  - Why: Settings home is already grouped into four labelled sections (Essentials / Experience / Data / System); the research's "15 sub-screens at one level" premise is stale. Only residual value is a cosmetic re-bucket into the 5 research groups.
  - Touches: `HomeScreen.kt` grouping.
  - Acceptance: do only as low-value polish; no behavior change.
  - Source: TODO.md A3 / second-pass EI2.

### Keyboard surface & visual polish (device-gated)

- [ ] P1 — Keyboard surface polish + manual-override verification (WS11)
  - Why: Candidate-row, smartbar, and software-key states plus the full layout matrix need real-field verification; cannot fully close without a device.
  - Touches: candidate-row selection/pressed/disabled/correction states; smartbar ordering + overflow + long-label resilience; software-key pressed/held/disabled/gesture states; one-handed/floating/split/compact/landscape/tablet layouts.
  - Acceptance: each state and layout verified in real input fields on a device.
  - Source: TODO.md B / improvement-plan WS11.
- [ ] P1 — Glide-trail theme baselines + low-end perf evidence (F9)
  - Why: Glide-trail themes lack Roborazzi baselines and low-end (<=4 GB) performance evidence.
  - Touches: Roborazzi baselines (device/emulator); trace `swiftfloris.glide.trailDrawMs` on Pixel 4a / Galaxy A12-class.
  - Acceptance: baselines recorded; trail-draw timing captured on low-end hardware.
  - Source: TODO.md B / research feature plan F9.
- [ ] P2 — F40 Roborazzi capture phase (F40 capture)
  - Why: Screen-level Roborazzi test classes ship baseline-pending (v1.8.201); the baseline PNGs still need on-device capture.
  - Touches: `:app:recordRoborazziDebug` for the A1 test classes, then remove the class-level `@Ignore` from the pending F40 screenshot classes.
  - Acceptance: baseline PNGs captured; `@Ignore` removed; gate green.
  - Source: TODO.md A1/B / research feature plan F40.
- [ ] P2 — Glide-trail reduced-animation + tooltip verification (EI4 residual)
  - Why: Confirm Rainbow/Aurora/Neon glide trails honour `ANIMATOR_DURATION_SCALE == 0f` on-device; the doc disclosure already shipped (v1.8.182).
  - Touches: GesturesScreen "i" tooltip + on-device animation-scale check.
  - Acceptance: trails respect zero animation scale; tooltip present.
  - Source: TODO.md B / second-pass EI4.

### Data safety, backup/restore & import (device-gated portions)

- [ ] P1 — Backup/restore + import path-safety device confirmation (WS13 device portions)
  - Why: Unit tests for these paths are Tier A and done; the on-device confirmation is still required.
  - Touches: backup/restore overwrite-vs-merge; clipboard media missing-file/path-safety; extension-import path-traversal.
  - Acceptance: overwrite/merge, missing-media, and traversal behaviors confirmed on-device.
  - Source: TODO.md B / improvement-plan WS13.

### CI, build & release hardening

- [ ] P1 — Chain reproducible-build verification to release-tag flow (F23)
  - Why: Reproducible-build verification is not gated on release.
  - Touches: `workflow_call` from `release.yml`, or block tag publish until repro is green.
  - Acceptance: a tag cannot publish unless reproducible-build verification passes.
  - Source: TODO.md A4 / research feature plan F23.
- [ ] P1 — `:app:verifyRoborazziRelease` gate (F24)
  - Why: R8/minify can rename Compose semantics nodes and nothing catches it today.
  - Touches: add a release-variant Roborazzi verification task.
  - Acceptance: release-variant screenshot gate runs and catches minify-induced semantics drift.
  - Source: TODO.md A4 / research feature plan F24.
- [ ] P2 — Macrobenchmark trend-regression job (EI9)
  - Why: No automated regression check against benchmark baselines.
  - Touches: `workflow_dispatch` job diffing against `docs/benchmark-results/baseline-*.json`; floor/target ranges documented in `docs/BENCHMARKS.md`.
  - Acceptance: job reports regressions against the baseline within documented ranges.
  - Source: TODO.md A4 / research feature plan EI9.

### Docs & hygiene

- [ ] P2 — Confirm absence of lint baseline / close EI10 (EI10)
  - Why: Research says no `app/lint-baseline.xml` exists; confirm and note.
  - Touches: one-line note; `bash scripts/run-lint-debug-with-baseline-check.sh` exits 0.
  - Acceptance: confirmed no baseline file; note added or item closed.
  - Source: TODO.md A5 / research feature plan EI10.
- [ ] P2 — Localization content-quality pass (WS12)
  - Why: Turkish repeated-word lint, vague/abrupt English source labels, and inconsistent failure/destructive copy need cleanup.
  - Touches: native-safe Turkish repeated-word review; tighten English source labels; standardize backup/restore/import/export failure + destructive-confirmation copy; document translation-safe cleanup rules.
  - Acceptance: lint warnings reviewed; copy standardized; rules documented.
  - Source: TODO.md A5 / improvement-plan WS12.
- [ ] P2 — Visual-QA + manual-QA + release-evidence checklists (WS10 / WS15)
  - Why: No standing checklists for the portrait/landscape/compact/floating/dark/high-font-scale matrix, manual QA, or release evidence.
  - Touches: docs for visual-QA matrix, manual-QA flow, and release-evidence capture.
  - Acceptance: three checklists exist and are referenced from the verification docs.
  - Source: TODO.md A5 / improvement-plan WS10/WS15.
- [ ] P3 — Fastlane changelog drafting guide (R5)
  - Why: No documented guidance on drafting the <=480-char fastlane changelog.
  - Touches: add the guide to `docs/LOCAL_VERIFICATION.md` / `docs/REPO_HYGIENE.md`.
  - Acceptance: guide present with the character-budget rule.
  - Source: TODO.md A5 / second-pass R5.
- [ ] P3 — Document module build-cache survival (O1)
  - Why: `lib/<module>/build/` cache survives `git rm --cached`; this surprises contributors.
  - Touches: note in `docs/REPO_HYGIENE.md`.
  - Acceptance: behavior documented.
  - Source: TODO.md A5 / second-pass O1.

### External-action-blocked / sibling-repo / XL (maintainer decision required)

These are genuine blockers — each needs an account, key, sibling repo, ML infra, or a product decision the code cannot make.

- [ ] P0 — Crowdin sync of v1.8.179 + v1.8.186 string drops (R1)
  - Why: 44 stale translated entries across 22 locales; Crowdin web console is source of truth (lint `UnusedResources` until done).
  - Touches: server-side Crowdin sync/pull.
  - Acceptance: translations synced; stale-entry lint clears.
  - Source: TODO.md C / second-pass R1.
- [ ] P1 — FlorisBoard `0.6.0-alpha02` cherry-picks (F22)
  - Why: Upstream CLDR 48, Emoji 17, number-field fix, and floating-window foundation are worth picking up; conflict resolution needs iterative on-device builds and risks regressing shipped features.
  - Touches: cherry-pick + conflict resolution across input/emoji/layout.
  - Acceptance: picks merged without regressing shipped features; on-device verified.
  - Source: TODO.md C / research feature plan F22.
- [ ] P1 — Apache-2.0 glide model trained on the MIT FUTO swipe dataset (F21)
  - Why: A licensed in-tree glide model needs off-device ML training infra (XL, out-of-tree).
  - Touches: external training pipeline + model integration.
  - Acceptance: Apache-2.0-clean model trained and integrated.
  - Source: TODO.md C / research feature plan F21.
- [ ] P2 — Bundled Vosk small-en-us recognizer addon (F8)
  - Why: Needs a sibling addon repo + JNI; `RECORD_AUDIO` only in the addon, never `:app`.
  - Touches: sibling addon repo, JNI binding.
  - Acceptance: recognizer ships as a signed addon; `:app` stays permission-clean.
  - Source: TODO.md C / research feature plan F8.
- [ ] P2 — CycloneDX SBOM + SLSA provenance on release (F10)
  - Why: Needs GitHub Attestations onboarding + release-tag dispatch.
  - Touches: release workflow attestation step.
  - Acceptance: SBOM + provenance attached to releases.
  - Source: TODO.md C / research feature plan F10.
- [ ] P2 — GPG-signed release tags (F11)
  - Why: Needs a maintainer GPG key.
  - Touches: release-tag signing.
  - Acceptance: tags are GPG-signed and verifiable.
  - Source: TODO.md C / research feature plan F11.
- [ ] P2 — F-Droid `fdroiddata` submission (F12)
  - Why: `dev.patrickgold.florisboard(.beta)` package-id collides with upstream; needs a rename/coexistence decision plus a multi-month review queue.
  - Touches: fdroiddata metadata + package-id decision.
  - Acceptance: submission accepted into the F-Droid queue.
  - Source: TODO.md C / research feature plan F12.
- [ ] P2 — FunctionGemma 270M MCP-bridge addon (F30)
  - Why: Needs a sibling addon repo.
  - Touches: sibling addon repo + MCP bridge.
  - Acceptance: addon bridges FunctionGemma over MCP without linking into `:app`.
  - Source: TODO.md C / research feature plan F30.
- [ ] P3 — Cross-platform desktop dictionary-export CLI (F13)
  - Why: Needs a sibling repo.
  - Touches: standalone CLI project.
  - Acceptance: CLI exports the dictionary format cross-platform.
  - Source: TODO.md C / research feature plan F13.

#### Open questions blocking the external-action items (maintainer decisions)

1. F-Droid package-id: coexist with upstream FlorisBoard `.beta` or rename? (blocks F12)
2. Vosk 40 MB addon in 2026, or voice stays FUTO-handoff-only? (affects F8 + EI7 copy)
3. Maintainer GPG key (Yubikey-backed?) for signed tags? (affects F11)
4. F-Droid submission timing — during the migration spike or a quiet week? (affects F12)

---

## Research-Driven Additions

<!-- populated by the research pass -->
